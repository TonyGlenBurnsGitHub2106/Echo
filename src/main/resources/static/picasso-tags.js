/* PicassoTags -- the standard LeonardoWeb MULTI-select chip picker.
 *
 * The many-value sibling of PicassoPicker: a field that holds SEVERAL values as
 * removable chips, each chosen from an ID-based lookup list (topics, tags,
 * categories) with find-or-create for a brand-new value. This exists so a
 * multi-value pick-list is NEVER a comma-separated free-text box and NEVER a
 * bare native <select multiple> (both fail on the phone).
 *
 * Markup the page provides:
 *   <div class="pt-wrap">
 *     <div class="pt-box">
 *       <span class="pt-chips"></span>
 *       <input class="pt-input" type="text" autocomplete="off">
 *     </div>
 *     <div class="pt-dropdown"></div>
 *   </div>
 *
 * Usage:
 *   var t = PicassoTags.attach({
 *       input:      document.querySelector('.pt-input'),
 *       chips:      document.querySelector('.pt-chips'),
 *       dropdown:   document.querySelector('.pt-dropdown'),
 *       getOptions: function () { return mCats; },   // [{id,name}] or ["a","b"]
 *       values:     ['Sonnets'],                     // optional initial chips
 *       placeholder:'Pick a topic or type a new one' // optional
 *   });
 *   t.getValues();          // -> ['Sonnets', ...]  (chip names, in order)
 *   t.setValues([...]);     // replace the chips
 *
 * Values are the chosen / typed NAMES; a name not in the list is kept as a new
 * entry (the caller find-or-creates it on save). Options are the ID-based lookup
 * entities, so the dropdown is "of ids" even though the wire value is the name.
 *
 * Optional, all off unless given (Story 5575, the Gmail Client's To / Cc / Bcc):
 *   search:     function (text) -> Promise of options -- ask the SERVER instead of a local
 *               list, after the typing pauses (searchDelay ms, default 250). An option may be
 *               {name, label, sub}: name is the chip value, label what the row shows, sub a
 *               smaller second line.
 *   validate:   function (name) -> true when the value is good; a bad chip is marked.
 *   split:      a RegExp; typed or pasted text is cut on it into several chips (e.g. /[,;]+/).
 *   noun:       the word in "No matching ..." / "All ... added" (default 'topic').
 *   addLabel:   function (text) -> the add row's caption (default '+ Add "text"').
 *   testid:     the chip test-id prefix (default 'poem-topic').
 *   chipLabel:  function (name) -> what the chip shows (the full name is its tooltip).
 *   pickOnly:   true -- a FILTER, not a field: only the listed options can be chosen. No
 *               "+ Add" row; typed text becomes the option it names, or the first one it
 *               matches, or nothing (Story 5608, the Gmail Client's flag filter).
 *   t.commit()  turns any text still in the box into chips and returns the values.
 */
(function () {
    'use strict';
    if (window.PicassoTags) return;

    function escHtml(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
    function escAttr(s) { return escHtml(s).replace(/'/g, '&#39;'); }

    function normalize(raw) {
        var out = [];
        (raw || []).forEach(function (x) {
            if (x == null) return;
            if (typeof x === 'string') { if (x.trim()) out.push(x.trim()); }
            else if (x.name != null && String(x.name).trim()) out.push(String(x.name).trim());
        });
        return out;
    }

    function attach(opts) {
        var input = opts.input, dd = opts.dropdown, chipsEl = opts.chips;
        if (!input || !dd || !chipsEl) return null;
        var getOptions = opts.getOptions || function () { return []; };
        var maxShown = opts.maxShown || 40;
        var noun = opts.noun || 'topic';
        var tid = opts.testid || 'poem-topic';
        var validate = typeof opts.validate === 'function' ? opts.validate : null;
        var searchFn = typeof opts.search === 'function' ? opts.search : null;
        var found = [];        // search mode: the server's latest answer, as {name, label, sub}
        var searchTimer = 0, searchSeq = 0;
        if (opts.placeholder) input.setAttribute('placeholder', opts.placeholder);

        var selected = normalize(opts.values);   // chip names, in order

        function hasSel(name) {
            var lc = name.toLowerCase();
            return selected.some(function (s) { return s.toLowerCase() === lc; });
        }

        function renderChips() {
            chipsEl.innerHTML = selected.map(function (name) {
                var bad = validate && !validate(name);
                var shown = typeof opts.chipLabel === 'function' ? opts.chipLabel(name) : name;
                return '<span class="pt-chip' + (bad ? ' pt-bad' : '') + '" data-name="' + escAttr(name) + '"' +
                    (bad ? ' title="This does not look right"' : (shown !== name ? ' title="' + escAttr(name) + '"' : '')) +
                    ' data-testid="' + escAttr(tid) + '-chip">' + escHtml(shown) +
                    '<button type="button" class="pt-x" data-remove="1" data-testid="' + escAttr(tid) + '-remove" ' +
                    'aria-label="Remove ' + escAttr(name) + '">&times;</button></span>';
            }).join('');
        }
        /** The options as {name, label, sub}: the server's answer in search mode, else the local list. */
        function optionList() {
            var src = searchFn ? found : (getOptions() || []);
            var out = [];
            src.forEach(function (x) {
                if (x == null) return;
                if (typeof x === 'string') { if (x.trim()) out.push({ name: x.trim(), label: x.trim(), sub: '' }); }
                else if (x.name != null && String(x.name).trim()) {
                    var n = String(x.name).trim();
                    out.push({ name: n, label: x.label ? String(x.label) : n, sub: x.sub ? String(x.sub) : '' });
                }
            });
            return out;
        }

        // The typed text is trimmed before matching (options are already trimmed
        // by normalize). A phone keyboard appends a space when it accepts a word
        // suggestion, and matching the raw text turned that invisible space into
        // "No matching topic". (Story 2456.)
        function filter(needle) {
            var all = optionList().filter(function (o) { return !hasSel(o.name); });
            var lc = (needle || '').trim().toLowerCase();
            if (!lc || searchFn) return all.slice(0, maxShown);   // the server already matched
            return all.filter(function (o) {
                return o.name.toLowerCase().indexOf(lc) >= 0 || o.label.toLowerCase().indexOf(lc) >= 0;
            }).slice(0, maxShown);
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
        function renderDropdown(needle) {
            var matches = filter(needle);
            var trimmed = (needle || '').trim();
            var html = '';
            // The create action comes FIRST while typing a new topic, so adding a
            // brand-new tag is a visible tap, not hidden knowledge.
            if (trimmed && !hasSel(trimmed) && !opts.pickOnly) {
                var exists = optionList().some(function (o) { return o.name.toLowerCase() === trimmed.toLowerCase(); });
                if (!exists) {
                    html += '<div class="pt-option pt-add" data-add="1" data-name="' + escAttr(trimmed) + '">' +
                        (opts.addLabel ? escHtml(opts.addLabel(trimmed)) : '+ Add "' + escHtml(trimmed) + '"') + '</div>';
                }
            }
            if (matches.length) {
                html += matches.map(function (o) {
                    return '<div class="pt-option" data-name="' + escAttr(o.name) + '">' + highlight(o.label, needle) +
                        (o.sub ? '<span class="pt-sub">' + highlight(o.sub, needle) + '</span>' : '') + '</div>';
                }).join('');
            } else if (!html) {
                html = '<div class="pt-option no-match">' +
                    (trimmed ? 'No matching ' + escHtml(noun) : 'All ' + escHtml(noun) + 's added') + '</div>';
            }
            dd.innerHTML = html;
            dd.classList.add('open');
        }

        function addTag(name) {
            if (opts.split && opts.split.test(name || '')) {
                (name || '').split(opts.split).forEach(function (part) {
                    part = part.trim();
                    if (part && !hasSel(part)) selected.push(part);
                });
                input.value = '';
                renderChips();
                renderDropdown('');
                if (typeof opts.onChange === 'function') opts.onChange(getValues());
                return;
            }
            name = (name || '').trim();
            if (name && opts.pickOnly) name = listedName(name);
            if (!name || hasSel(name)) { input.value = ''; renderDropdown(''); return; }
            selected.push(name);
            input.value = '';
            renderChips();
            renderDropdown('');
            if (typeof opts.onChange === 'function') opts.onChange(getValues());
            input.focus();
        }
        /** pickOnly: the option text names -- exactly, else the first it matches -- or '' for none. */
        function listedName(text) {
            var lc = text.toLowerCase();
            var all = optionList();
            var exact = all.filter(function (o) { return o.name.toLowerCase() === lc; })[0];
            if (exact) return exact.name;
            var first = filter(text)[0];
            return first ? first.name : '';
        }
        function removeTag(name) {
            var lc = name.toLowerCase();
            selected = selected.filter(function (s) { return s.toLowerCase() !== lc; });
            renderChips();
            if (dd.classList.contains('open')) renderDropdown(input.value);
            if (typeof opts.onChange === 'function') opts.onChange(getValues());
        }
        function close() { dd.classList.remove('open'); }
        function getValues() { return selected.slice(); }

        /** Search mode: ask the server once the typing pauses; a stale answer is dropped. */
        function askServer(text) {
            if (!searchFn) return;
            clearTimeout(searchTimer);
            var seq = ++searchSeq;
            searchTimer = setTimeout(function () {
                Promise.resolve(searchFn((text || '').trim())).then(function (list) {
                    if (seq !== searchSeq) return;
                    found = Array.isArray(list) ? list : [];
                    if (dd.classList.contains('open') || document.activeElement === input) renderDropdown(input.value);
                }, function () { /* keep the last answer */ });
            }, opts.searchDelay == null ? 250 : opts.searchDelay);
        }

        input.addEventListener('input', function () { renderDropdown(this.value); askServer(this.value); });
        input.addEventListener('focus', function () { renderDropdown(this.value); askServer(this.value); });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { close(); return; }
            if (e.key === 'Backspace' && !input.value && selected.length) {
                removeTag(selected[selected.length - 1]); e.preventDefault(); return;
            }
            var list = dd.querySelectorAll('.pt-option:not(.no-match)');
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                if (!list.length) { renderDropdown(input.value); return; }
                var cur = dd.querySelector('.pt-option.active');
                var idx = cur ? Array.prototype.indexOf.call(list, cur) : -1;
                if (cur) cur.classList.remove('active');
                idx = e.key === 'ArrowDown' ? Math.min(idx + 1, list.length - 1) : Math.max(idx - 1, 0);
                list[idx].classList.add('active');
                list[idx].scrollIntoView({ block: 'nearest' });
                e.preventDefault();
            } else if (e.key === 'Enter' || e.key === ',' || (opts.split && e.key === ';')) {
                e.preventDefault();
                var active = dd.querySelector('.pt-option.active');
                if (active) chooseEl(active);
                else if (input.value.trim()) addTag(input.value);
            }
        });
        dd.addEventListener('mousedown', function (e) {
            var opt = e.target.closest ? e.target.closest('.pt-option') : null;
            if (opt && !opt.classList.contains('no-match')) { chooseEl(opt); e.preventDefault(); }
        });
        chipsEl.addEventListener('click', function (e) {
            var x = e.target.closest ? e.target.closest('[data-remove]') : null;
            if (!x) return;
            var chip = x.closest('.pt-chip');
            if (chip) removeTag(chip.getAttribute('data-name'));
        });
        document.addEventListener('click', function (e) {
            if (!input.contains(e.target) && !dd.contains(e.target) && !chipsEl.contains(e.target)) close();
        });

        function chooseEl(el) {
            if (el.getAttribute('data-add')) addTag(el.getAttribute('data-name'));
            else addTag(el.getAttribute('data-name'));
        }

        renderChips();

        return {
            getValues: getValues,
            setValues: function (v) { selected = normalize(v); input.value = ''; renderChips(); if (dd.classList.contains('open')) renderDropdown(''); },
            refresh: function () { if (dd.classList.contains('open')) renderDropdown(input.value); },
            commit: function () { if (input.value.trim()) addTag(input.value); close(); return getValues(); },
            focus: function () { input.focus(); },
            close: close
        };
    }

    window.PicassoTags = { attach: attach };
})();
