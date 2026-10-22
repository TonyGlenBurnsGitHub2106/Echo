/*
 * PicassoQuickAdd -- add a missing pick-list value WITHOUT leaving the page
 * (Story 3550).
 *
 * The problem it solves: you are halfway through a form (an Entity IV record, a
 * GL account, a Quick Entry), the Vendor / Type / Account you need does not
 * exist yet, and the only way to make it used to be to leave, set it up
 * somewhere else, come back and start over. This opens a small form ON TOP of
 * whatever you are in -- a record dialog, the Quick Entry sheet -- saves the new
 * value, and hands it back so the caller can drop it straight into the
 * dropdown it came from.
 *
 * Load both files (?v=N like every asset):
 *     <link rel="stylesheet" href="/picasso-quick-add.css?v=1">
 *     <script src="/picasso-quick-add.js?v=2"></script>
 * Selects inside the form are ordinary <select>s, so load picasso-select too
 * and they become Picasso dropdowns automatically.
 *
 * API
 *
 *   PicassoQuickAdd.open({
 *       title:  'New vendor',
 *       fields: [ { key, label, type, value, options, required, maxlength,
 *                   placeholder, addLabel, onAdd } ],
 *       onSave: async function (data) { ...; return result; }   // throw Error(msg) to refuse
 *   })  -> Promise resolving to onSave's result, or null on Cancel.
 *
 *     type      'text' (default) | 'textarea' | 'number' | 'select' | 'checkbox'
 *     options   select only: [{id, name}]
 *     addLabel  select only: offer the green "+ Add" row in that dropdown too
 *     onAdd     select only: async function (typedText) -> {id, name} | null,
 *               called when that row is tapped; the result is added and selected.
 *
 *   PicassoQuickAdd.simple({ typed, title, label, maxlength, create })
 *       The one-field case. With a typed name, create(typed) runs at once --
 *       the add IS the tap. With nothing typed, a one-field form asks for it.
 *       -> Promise {id, name} | null.
 *
 *   PicassoQuickAdd.addLookup(slug, name)
 *       Find-or-create one simple type (vendor-types, address-types, ...) via
 *       POST /api/lookups/{slug}. -> Promise {id, name, created}; throws Error.
 *
 * It stacks above the entity-detail modal (z 1000/1100) and the Quick Entry
 * sheet (z 200), and below the PicassoSelect popup (2000) and the typeahead
 * dropdowns (5000), so a dropdown opened INSIDE it still shows.
 */
(function () {
    'use strict';

    if (window.PicassoQuickAdd) return;

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Every sheet opened is its own element, so one can open from inside
    // another (a new vendor whose type is new too) and each closes on its own.
    function open(cfg) {
        return new Promise(function (resolve) {
            var fields = cfg.fields || [];
            var bg = document.createElement('div');
            bg.className = 'pqa-bg';
            bg.setAttribute('data-testid', 'pqa-backdrop');

            var html = '<div class="pqa-sheet" role="dialog" aria-modal="true" data-testid="pqa-sheet">'
                     + '<h2 class="pqa-title">' + esc(cfg.title || 'Add') + '</h2>'
                     + '<form class="pqa-form" autocomplete="off">';
            fields.forEach(function (f, i) {
                var id = 'pqa_' + Date.now() + '_' + i;
                var val = f.value == null ? '' : f.value;
                var tid = ' data-testid="pqa-field-' + esc(f.key) + '"';
                var req = f.required ? ' <span class="pqa-req">*</span>' : '';
                if (f.type === 'checkbox') {
                    html += '<div class="pqa-check"><input type="checkbox" id="' + id + '" name="' + esc(f.key) + '"'
                          + (val ? ' checked' : '') + tid + '><label for="' + id + '">' + esc(f.label) + '</label></div>';
                    return;
                }
                html += '<div class="pqa-field"><label for="' + id + '">' + esc(f.label) + req + '</label>';
                if (f.type === 'select') {
                    html += '<select id="' + id + '" name="' + esc(f.key) + '"' + tid
                          + ' data-ps-placeholder="' + esc(f.placeholder || 'Select') + '"'
                          + (f.addLabel ? ' data-ps-add="' + esc(f.addLabel) + '"' : '') + '>'
                          + '<option value=""></option>'
                          + (f.options || []).map(function (o) {
                                return '<option value="' + esc(o.id) + '"' + (String(o.id) === String(val) ? ' selected' : '')
                                     + '>' + esc(o.name) + '</option>';
                            }).join('')
                          + '</select>';
                } else if (f.type === 'textarea') {
                    html += '<textarea id="' + id + '" name="' + esc(f.key) + '"' + tid + ' rows="3">' + esc(val) + '</textarea>';
                } else {
                    // Numbers are text + a decimal keypad: a number input refuses commas.
                    html += '<input type="text"' + (f.type === 'number' ? ' inputmode="decimal"' : '')
                          + ' id="' + id + '" name="' + esc(f.key) + '"' + tid
                          + ' value="' + esc(val) + '"'
                          + (f.maxlength ? ' maxlength="' + f.maxlength + '"' : '')
                          + (f.placeholder ? ' placeholder="' + esc(f.placeholder) + '"' : '') + '>';
                }
                html += '</div>';
            });
            html += '</form>'
                  + '<div class="pqa-err" data-testid="pqa-error"></div>'
                  + '<div class="pqa-actions">'
                  +   '<button type="button" class="pqa-btn primary" data-testid="pqa-save-btn">Save</button>'
                  +   '<button type="button" class="pqa-btn" data-testid="pqa-cancel-btn">Cancel</button>'
                  + '</div></div>';
            bg.innerHTML = html;
            document.body.appendChild(bg);

            var form    = bg.querySelector('.pqa-form');
            var errEl   = bg.querySelector('.pqa-err');
            var saveBtn = bg.querySelector('[data-testid="pqa-save-btn"]');
            var done    = false;

            function finish(result) {
                if (done) return;
                done = true;
                document.removeEventListener('keydown', onKey, true);
                if (bg.parentNode) bg.parentNode.removeChild(bg);
                resolve(result);
            }
            function onKey(e) {
                // Only the top-most sheet answers Escape.
                var sheets = document.querySelectorAll('.pqa-bg');
                if (sheets[sheets.length - 1] !== bg) return;
                if (e.key === 'Escape' && !document.querySelector('.ps-popup')) { e.preventDefault(); finish(null); }
            }
            document.addEventListener('keydown', onKey, true);
            bg.querySelector('[data-testid="pqa-cancel-btn"]').addEventListener('click', function () { finish(null); });
            // A tap outside the sheet does NOT close it: on a phone that is a
            // fat-finger away from losing what was typed.

            // Nested add inside a select of this form.
            fields.forEach(function (f) {
                if (f.type !== 'select' || typeof f.onAdd !== 'function') return;
                var sel = form.querySelector('[name="' + f.key + '"]');
                sel.addEventListener('ps-add', async function (e) {
                    errEl.textContent = '';
                    try {
                        var item = await f.onAdd((e.detail && e.detail.text) || '');
                        if (!item) return;
                        if (f.options && !f.options.some(function (o) { return String(o.id) === String(item.id); })) {
                            f.options.push(item);
                        }
                        if (window.PicassoSelect && window.PicassoSelect.addOption) {
                            window.PicassoSelect.addOption(sel, item.id, item.name);
                        }
                    } catch (err) {
                        errEl.textContent = (err && err.message) || 'Could not add that.';
                    }
                });
            });

            function read() {
                var data = {};
                fields.forEach(function (f) {
                    var el = form.querySelector('[name="' + f.key + '"]');
                    data[f.key] = f.type === 'checkbox' ? !!el.checked : el.value;
                });
                return data;
            }

            saveBtn.addEventListener('click', async function () {
                errEl.textContent = '';
                var data = read();
                for (var i = 0; i < fields.length; i++) {
                    var f = fields[i];
                    if (f.required && f.type !== 'checkbox' && !String(data[f.key] || '').trim()) {
                        errEl.textContent = f.label + ' is required.';
                        return;
                    }
                }
                saveBtn.disabled = true;
                try {
                    var result = await cfg.onSave(data);
                    finish(result == null ? true : result);
                } catch (err) {
                    errEl.textContent = (err && err.message) || 'Save failed';
                } finally {
                    saveBtn.disabled = false;
                }
            });
            form.addEventListener('submit', function (e) { e.preventDefault(); });

            // Land in the first empty field, else the first field.
            var inputs = form.querySelectorAll('input[type="text"], textarea');
            var target = null;
            for (var k = 0; k < inputs.length; k++) { if (!inputs[k].value) { target = inputs[k]; break; } }
            if (!target && inputs.length) target = inputs[0];
            if (target) setTimeout(function () { try { target.focus(); } catch (e) {} }, 30);
        });
    }

    async function simple(opts) {
        var typed = String(opts.typed || '').trim();
        if (typed) return await opts.create(typed);
        return await open({
            title: opts.title || 'Add',
            fields: [{ key: 'name', label: opts.label || 'Name', type: 'text', required: true,
                       maxlength: opts.maxlength || 50 }],
            onSave: function (d) { return opts.create(String(d.name || '').trim()); }
        });
    }

    async function addLookup(slug, name) {
        var r = await window.LWAuth.apiPost('/api/lookups/' + encodeURIComponent(slug), { name: name });
        if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Could not add that.'));
        return r.data;
    }

    window.PicassoQuickAdd = { open: open, simple: simple, addLookup: addLookup };
})();
