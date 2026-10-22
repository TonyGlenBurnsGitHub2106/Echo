/* PicassoPicker -- the standard LeonardoWeb typeahead dropdown.
 *
 * Behavioural + visual twin of the Find Transaction account/vendor picker,
 * extracted into one shared component so every new pick-list looks and works
 * the same. This is the DEFAULT dropdown for new programming. (The richer
 * Quick Entry search picker stays where it is already used -- do not replace
 * it.)
 *
 * Markup the page provides:
 *   <div class="picker-wrap">
 *     <input type="text" class="picker-input" id="myInput" autocomplete="off">
 *     <div class="picker-dropdown" id="myDropdown"></div>
 *   </div>
 *
 * Usage:
 *   var p = PicassoPicker.attach({
 *       input:    document.getElementById('myInput'),
 *       dropdown: document.getElementById('myDropdown'),
 *       getItems: function () { return mAuthors; },   // [{id,name}] or ["a","b"]
 *       hidden:   document.getElementById('myHiddenId'), // optional id sink
 *       clearLabel: '(none)',     // optional first option that empties the field
 *       allowNew: true,           // optional find-or-create: typing a name not in
 *                                 // the list shows a tappable  + Add "name"  row
 *                                 // (onSelect gets created:true); an empty list
 *                                 // shows a type-to-add hint instead of nothing
 *       newLabel: 'Add',          // optional verb for the add row
 *       onSelect: function (item) { ... }                // optional
 *   });
 *   // value is simply input.value (the chosen / typed name); p.refresh() re-renders.
 *
 * Free text is allowed: typing a value that is not in the list keeps that text
 * (so callers can create a brand-new author/topic/category). Selecting from the
 * dropdown fills the name (and the hidden id, if provided).
 */
(function () {
    'use strict';
    if (window.PicassoPicker) return;

    function escHtml(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
    function escAttr(s) { return escHtml(s).replace(/'/g, '&#39;'); }

    // Every name is TRIMMED on the way into the list, and blank names are
    // dropped. An option carrying a stray leading / trailing space is a value
    // that cannot be typed back: the user picks it, the field now holds
    // "Poetry " and nothing matches it again until the space is backspaced
    // away. Trim at the door so the option text, the field value and the saved
    // value are always the same string. (Story 2456.)
    function normalize(raw) {
        var out = [];
        (raw || []).forEach(function (x) {
            if (x == null) return;
            var isStr = (typeof x === 'string');
            var name = isStr ? x : (x.name != null ? String(x.name) : String(x));
            name = name.trim();
            if (!name) return;
            out.push({ id: (isStr ? 0 : (x.id != null ? x.id : 0)), name: name });
        });
        return out;
    }

    function attach(opts) {
        var input = opts.input, dd = opts.dropdown;
        if (!input || !dd) return null;
        var getItems = opts.getItems || function () { return []; };
        var hidden = opts.hidden || null;
        var clearLabel = opts.clearLabel || null;
        var maxShown = opts.maxShown || 40;
        // allowNew: the picker is find-OR-CREATE. Typing a name that is not in
        // the list shows an explicit, tappable  + Add "name"  row (and an empty
        // list shows a type-to-add hint) so creating a new entry is a visible
        // action, not hidden knowledge. Opt-in so existing pages are unchanged.
        var allowNew = !!opts.allowNew;
        var newLabel = opts.newLabel || 'Add';

        // The typed text is ALSO trimmed before it is matched. A phone keyboard
        // appends a space the moment it accepts a word suggestion, and matching
        // on the raw text turned that invisible space into "No matches".
        function filter(needle) {
            var items = normalize(getItems());
            var lc = (needle || '').trim().toLowerCase();
            if (!lc) return items.slice(0, maxShown);
            return items.filter(function (a) { return a.name.toLowerCase().indexOf(lc) >= 0; }).slice(0, maxShown);
        }
        function highlight(str, needle) {
            var q = (needle || '').trim();
            if (!q) return escHtml(str);
            var idx = str.toLowerCase().indexOf(q.toLowerCase());
            if (idx < 0) return escHtml(str);
            return escHtml(str.substring(0, idx)) + '<mark>' +
                escHtml(str.substring(idx, idx + q.length)) + '</mark>' +
                escHtml(str.substring(idx + q.length));
        }
        function render(needle) {
            var matches = filter(needle);
            var trimmed = (needle || '').trim();
            var html = '';
            // The create action comes FIRST while the user is typing a new name,
            // so the primary affordance is never buried under the clear row.
            if (allowNew && trimmed) {
                var lcT = trimmed.toLowerCase();
                var exact = normalize(getItems()).some(function (a) { return a.name.toLowerCase() === lcT; });
                if (!exact) {
                    html += '<div class="picker-option picker-add" data-add="1" data-name="' + escAttr(trimmed) + '">' +
                        '+ ' + escHtml(newLabel) + ' "' + escHtml(trimmed) + '"</div>';
                }
            }
            if (clearLabel) html += '<div class="picker-option picker-clear" data-clear="1">' + escHtml(clearLabel) + '</div>';
            if (!matches.length) {
                if (allowNew && !trimmed) {
                    html += '<div class="picker-option no-match">Type a new name to add it</div>';
                } else if (!clearLabel && !html) {
                    dd.innerHTML = '<div class="picker-option no-match">No matches</div>'; dd.classList.add('open'); return;
                }
            } else {
                html += matches.map(function (a) {
                    return '<div class="picker-option" data-id="' + escAttr(a.id) + '" data-name="' + escAttr(a.name) + '">' +
                        highlight(a.name, needle) + '</div>';
                }).join('');
            }
            dd.innerHTML = html;
            dd.classList.add('open');
        }
        function selectItem(name, id, isClear, isNew) {
            // Trim on the way out too, so a free-text entry the user typed with a
            // trailing space is saved as the same string the list already holds
            // (otherwise find-or-create quietly makes a near-duplicate lookup row).
            input.value = isClear ? '' : String(name == null ? '' : name).trim();
            if (hidden) hidden.value = isClear ? '' : id;
            dd.classList.remove('open');
            if (typeof opts.onSelect === 'function') opts.onSelect({ id: id, name: input.value, cleared: !!isClear, created: !!isNew });
        }
        function close() { dd.classList.remove('open'); }

        input.addEventListener('input', function () { if (hidden) hidden.value = ''; render(this.value); });
        // On focus show the FULL list (needle '') -- a preset value must not hide
        // the other options; the text is selected so typing still replaces it, and
        // the 'input' handler below filters as the user types.
        input.addEventListener('focus', function () { try { this.select(); } catch (e) {} render(''); });
        // Free text the user typed and never picked gets trimmed when the field
        // is left, so the value the page saves matches the list exactly.
        input.addEventListener('blur', function () {
            var t = (this.value || '').trim();
            if (t !== this.value) this.value = t;
        });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { close(); return; }
            var optsList = dd.querySelectorAll('.picker-option:not(.no-match)');
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                if (!optsList.length) { render(input.value); return; }
                var cur = dd.querySelector('.picker-option.active');
                var idx = cur ? Array.prototype.indexOf.call(optsList, cur) : -1;
                if (cur) cur.classList.remove('active');
                idx = e.key === 'ArrowDown' ? Math.min(idx + 1, optsList.length - 1) : Math.max(idx - 1, 0);
                optsList[idx].classList.add('active');
                optsList[idx].scrollIntoView({ block: 'nearest' });
                e.preventDefault();
            } else if (e.key === 'Enter') {
                var active = dd.querySelector('.picker-option.active');
                if (active) { e.preventDefault(); chooseEl(active); }
                else close();
            }
        });
        dd.addEventListener('mousedown', function (e) {
            var opt = e.target.closest ? e.target.closest('.picker-option') : null;
            if (opt && !opt.classList.contains('no-match')) { chooseEl(opt); e.preventDefault(); }
        });
        document.addEventListener('click', function (e) {
            if (!input.contains(e.target) && !dd.contains(e.target)) close();
        });

        function chooseEl(el) {
            if (el.getAttribute('data-clear')) selectItem('', '', true);
            else if (el.getAttribute('data-add')) selectItem(el.getAttribute('data-name'), 0, false, true);
            else selectItem(el.getAttribute('data-name'), el.getAttribute('data-id'), false);
        }

        return {
            close: close,
            open: function () { render(input.value); },
            refresh: function () { if (dd.classList.contains('open')) render(input.value); }
        };
    }

    window.PicassoPicker = { attach: attach };
})();
