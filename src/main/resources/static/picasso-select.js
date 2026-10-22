(function () {
    'use strict';

    if (window.PicassoSelect) return;

    var SHEET_MAX_WIDTH = 600;
    var SEARCH_THRESHOLD = 8;

    function escHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function findSelectedLabel(sourceSelect) {
        var sel = sourceSelect.options[sourceSelect.selectedIndex];
        if (!sel) return '';
        return sel.text;
    }

    function enhance(sourceSelect) {
        if (!sourceSelect || sourceSelect.tagName !== 'SELECT') return;
        if (sourceSelect.dataset.psEnhanced === '1') return;
        sourceSelect.dataset.psEnhanced = '1';
        sourceSelect.style.display = 'none';

        var srcTestId = sourceSelect.getAttribute('data-testid') || '';
        var placeholderText = sourceSelect.dataset.psPlaceholder || 'Select';

        var trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'ps-select';
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');
        if (srcTestId) trigger.setAttribute('data-testid', srcTestId + '-trigger');

        var labelSpan = document.createElement('span');
        labelSpan.className = 'ps-label';
        trigger.appendChild(labelSpan);

        var chev = document.createElement('span');
        chev.className = 'ps-chev';
        chev.textContent = String.fromCharCode(0x25BE);
        trigger.appendChild(chev);

        sourceSelect.insertAdjacentElement('afterend', trigger);

        function refreshTrigger() {
            var label = findSelectedLabel(sourceSelect);
            if (!label || sourceSelect.value === '') {
                labelSpan.textContent = placeholderText;
                labelSpan.classList.add('ps-placeholder');
            } else {
                labelSpan.textContent = label;
                labelSpan.classList.remove('ps-placeholder');
            }
        }
        refreshTrigger();

        // A programmatic assignment -- sel.value = x / sel.selectedIndex = n -- changes no
        // attribute and no child node, so the MutationObserver below never fires and the
        // trigger would keep showing the placeholder while the hidden select holds a real
        // value. Wrap the native accessors on this element so any assignment repaints the
        // trigger. Reads and the native semantics are untouched, and no change event is
        // synthesized (the native setter does not fire one either).
        interceptProgrammaticSet('value');
        interceptProgrammaticSet('selectedIndex');

        function interceptProgrammaticSet(prop) {
            var desc = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, prop);
            if (!desc || !desc.set || !desc.get) return;
            Object.defineProperty(sourceSelect, prop, {
                configurable: true,
                enumerable: false,
                get: function () { return desc.get.call(this); },
                set: function (v) { desc.set.call(this, v); refreshTrigger(); }
            });
        }

        var srcObs = new MutationObserver(function () { refreshTrigger(); });
        srcObs.observe(sourceSelect, { childList: true, attributes: true, attributeFilter: ['value'] });

        var openState = null;

        function closePopup() {
            if (!openState) return;
            openState.popup.parentNode.removeChild(openState.popup);
            if (openState.backdrop) openState.backdrop.parentNode.removeChild(openState.backdrop);
            document.removeEventListener('keydown', openState.keyHandler, true);
            document.removeEventListener('mousedown', openState.outsideClick, true);
            document.removeEventListener('touchstart', openState.outsideClick, true);
            window.removeEventListener('resize', openState.layoutHandler);
            window.removeEventListener('scroll', openState.layoutHandler, true);
            trigger.setAttribute('aria-expanded', 'false');
            trigger.classList.remove('is-open');
            openState = null;
            trigger.focus();
        }

        function selectValue(value) {
            sourceSelect.value = value;
            sourceSelect.dispatchEvent(new Event('change', { bubbles: true }));
            refreshTrigger();
            closePopup();
        }

        function openPopup() {
            if (openState) return;

            var opts = [];
            for (var i = 0; i < sourceSelect.options.length; i++) {
                var o = sourceSelect.options[i];
                if (o.value === '') continue;
                opts.push({ value: o.value, label: o.text, disabled: o.disabled });
            }

            var isSheet = window.innerWidth <= SHEET_MAX_WIDTH;
            // Add-in-place (Story 3550): a select carrying data-ps-add="<label>"
            // offers a green "+ Add" row. Tapping it closes the popup and fires a
            // 'ps-add' event on the select with detail.text = whatever was typed
            // in the search box; the page creates the value and calls
            // PicassoSelect.addOption. The search box always shows on such a
            // select, because it is also where the new name is typed.
            var addLabel = sourceSelect.dataset.psAdd || '';
            var needsSearch = opts.length >= SEARCH_THRESHOLD || !!addLabel;

            var popup = document.createElement('div');
            popup.className = 'ps-popup' + (isSheet ? ' is-sheet' : '');
            popup.setAttribute('role', 'listbox');
            if (srcTestId) popup.setAttribute('data-testid', srcTestId + '-popup');

            var html = '';
            if (needsSearch) {
                html += '<div class="ps-search-wrap"><input type="text" class="ps-search"' +
                        ' placeholder="' + (addLabel ? 'Type to filter or add' : 'Type to filter') + '"' +
                        (srcTestId ? ' data-testid="' + escHtml(srcTestId) + '-search"' : '') +
                        ' autocomplete="off"></div>';
            }
            html += '<div class="ps-list" role="presentation">';
            for (var j = 0; j < opts.length; j++) {
                var op = opts[j];
                var isSel = String(sourceSelect.value) === String(op.value);
                html += '<div class="ps-option' + (isSel ? ' is-selected' : '') + '"' +
                        ' role="option" data-value="' + escHtml(op.value) + '"' +
                        ' aria-selected="' + (isSel ? 'true' : 'false') + '"' +
                        (op.disabled ? ' aria-disabled="true"' : '') +
                        '>' + escHtml(op.label) + '</div>';
            }
            html += '</div>';
            html += '<div class="ps-empty" hidden>No matches</div>';
            if (addLabel) {
                html += '<div class="ps-add" role="option"' +
                        (srcTestId ? ' data-testid="' + escHtml(srcTestId) + '-add"' : '') +
                        '>+ ' + escHtml(addLabel) + '</div>';
            }
            popup.innerHTML = html;

            var backdrop = null;
            if (isSheet) {
                backdrop = document.createElement('div');
                backdrop.className = 'ps-backdrop';
                document.body.appendChild(backdrop);
            }
            document.body.appendChild(popup);

            function layout() {
                if (isSheet) return;
                var r = trigger.getBoundingClientRect();
                var vh = window.innerHeight;
                popup.style.left = (window.scrollX + r.left) + 'px';
                popup.style.width = r.width + 'px';
                var roomBelow = vh - r.bottom;
                var roomAbove = r.top;
                var maxH = Math.min(Math.floor(vh * 0.5), 420);
                if (maxH < 200) maxH = 200;
                var openDown = roomBelow >= 220 || roomBelow >= roomAbove;
                if (openDown) {
                    popup.style.top = (window.scrollY + r.bottom + 2) + 'px';
                    popup.style.maxHeight = Math.min(maxH, roomBelow - 8) + 'px';
                    popup.style.removeProperty('bottom');
                } else {
                    var h = Math.min(maxH, roomAbove - 8);
                    popup.style.top = (window.scrollY + r.top - 2 - h) + 'px';
                    popup.style.maxHeight = h + 'px';
                }
            }
            layout();

            var list = popup.querySelector('.ps-list');
            var emptyEl = popup.querySelector('.ps-empty');
            list.addEventListener('click', function (e) {
                var opt = e.target.closest('.ps-option');
                if (!opt || opt.hasAttribute('aria-disabled')) return;
                selectValue(opt.getAttribute('data-value'));
            });

            function visibleOptions() {
                return list.querySelectorAll('.ps-option:not([hidden])');
            }
            var activeIdx = -1;
            function setActive(idx) {
                var nodes = visibleOptions();
                if (!nodes.length) { activeIdx = -1; return; }
                if (idx < 0) idx = 0;
                if (idx >= nodes.length) idx = nodes.length - 1;
                for (var n = 0; n < nodes.length; n++) nodes[n].classList.remove('is-active');
                nodes[idx].classList.add('is-active');
                nodes[idx].scrollIntoView({ block: 'nearest' });
                activeIdx = idx;
            }
            var startNodes = visibleOptions();
            for (var k = 0; k < startNodes.length; k++) {
                if (startNodes[k].classList.contains('is-selected')) { setActive(k); break; }
            }

            var searchInput = popup.querySelector('.ps-search');
            var addEl = popup.querySelector('.ps-add');

            // The add row names what was typed ("+ Add "Kennel"") and hides when
            // the typed text already IS an option -- pick that one instead.
            function paintAddRow(typed) {
                if (!addEl) return;
                var t = typed.trim();
                var exact = false;
                for (var x = 0; x < opts.length; x++) {
                    if (opts[x].label.trim().toLowerCase() === t.toLowerCase()) { exact = true; break; }
                }
                addEl.hidden = !!t && exact;
                addEl.textContent = '+ ' + addLabel + (t ? ' "' + t + '"' : '');
            }
            function requestAdd() {
                var t = searchInput ? searchInput.value.trim() : '';
                closePopup();
                sourceSelect.dispatchEvent(new CustomEvent('ps-add', { bubbles: true, detail: { text: t } }));
            }
            if (addEl) {
                paintAddRow('');
                addEl.addEventListener('click', requestAdd);
            }

            if (searchInput) {
                searchInput.addEventListener('input', function () {
                    var q = searchInput.value.trim().toLowerCase();
                    var any = false;
                    var optNodes = list.querySelectorAll('.ps-option');
                    for (var m = 0; m < optNodes.length; m++) {
                        var match = !q || optNodes[m].textContent.toLowerCase().indexOf(q) !== -1;
                        optNodes[m].hidden = !match;
                        if (match) any = true;
                    }
                    // With an add row there is always something to do, so no
                    // "No matches" dead end.
                    emptyEl.hidden = any || !!addEl;
                    paintAddRow(searchInput.value);
                    activeIdx = -1;
                    if (any) setActive(0);
                });
                setTimeout(function () { searchInput.focus(); }, 0);
            }

            function outsideClick(e) {
                if (popup.contains(e.target)) return;
                if (trigger.contains(e.target)) return;
                closePopup();
            }
            setTimeout(function () {
                document.addEventListener('mousedown', outsideClick, true);
                document.addEventListener('touchstart', outsideClick, true);
            }, 0);

            function keyHandler(e) {
                if (e.key === 'Escape') { e.preventDefault(); closePopup(); return; }
                if (e.key === 'ArrowDown') { e.preventDefault(); setActive(activeIdx + 1); return; }
                if (e.key === 'ArrowUp')   { e.preventDefault(); setActive(activeIdx - 1); return; }
                if (e.key === 'Home')      { e.preventDefault(); setActive(0); return; }
                if (e.key === 'End')       { e.preventDefault(); setActive(99999); return; }
                if (e.key === 'Enter') {
                    var nodes2 = visibleOptions();
                    if (activeIdx >= 0 && nodes2[activeIdx]) {
                        e.preventDefault();
                        selectValue(nodes2[activeIdx].getAttribute('data-value'));
                    } else if (addEl && !addEl.hidden && searchInput && searchInput.value.trim()) {
                        e.preventDefault();
                        requestAdd();
                    }
                }
            }
            document.addEventListener('keydown', keyHandler, true);
            window.addEventListener('resize', layout);
            window.addEventListener('scroll', layout, true);

            trigger.setAttribute('aria-expanded', 'true');
            trigger.classList.add('is-open');

            openState = {
                popup: popup,
                backdrop: backdrop,
                keyHandler: keyHandler,
                layoutHandler: layout,
                outsideClick: outsideClick
            };
        }

        trigger.addEventListener('click', function (e) {
            e.preventDefault();
            if (openState) closePopup(); else openPopup();
        });
        trigger.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
                e.preventDefault();
                if (!openState) openPopup();
            }
        });

        sourceSelect.addEventListener('change', refreshTrigger);
    }

    function shouldEnhance(s) {
        if (!s || s.tagName !== 'SELECT') return false;
        if (s.dataset.psEnhanced === '1') return false;
        if (s.hasAttribute('data-ps-skip')) return false;
        if (s.multiple) return false;
        var sz = parseInt(s.getAttribute('size'), 10);
        if (sz > 1) return false;
        return true;
    }

    function scanAndEnhance(root) {
        var scope = (root && root.querySelectorAll) ? root : document;
        var selects = scope.querySelectorAll('select');
        for (var i = 0; i < selects.length; i++) {
            if (shouldEnhance(selects[i])) enhance(selects[i]);
        }
    }

    function refresh(elem) {
        if (!elem || elem.tagName !== 'SELECT') return;
        if (elem.dataset.psEnhanced !== '1') return;
        elem.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function startAutoMode() {
        scanAndEnhance(document);
        if (window.__psObs) return;
        window.__psObs = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                    var n = added[j];
                    if (!n || n.nodeType !== 1) continue;
                    if (n.tagName === 'SELECT') {
                        if (shouldEnhance(n)) enhance(n);
                    } else if (n.querySelectorAll) {
                        var sels = n.querySelectorAll('select');
                        for (var k = 0; k < sels.length; k++) {
                            if (shouldEnhance(sels[k])) enhance(sels[k]);
                        }
                    }
                }
            }
        });
        window.__psObs.observe(document.body, { childList: true, subtree: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startAutoMode);
    } else {
        startAutoMode();
    }

    // Append one option (or reuse the one with that value) and select it -- the
    // second half of an add-in-place (Story 3550). Fires 'change' like a user
    // pick would, so dirty tracking and dependent fields react.
    function addOption(elem, value, label) {
        if (!elem || elem.tagName !== 'SELECT') return;
        var v = String(value);
        var found = null;
        for (var i = 0; i < elem.options.length; i++) {
            if (elem.options[i].value === v) { found = elem.options[i]; break; }
        }
        if (!found) {
            found = document.createElement('option');
            found.value = v;
            found.textContent = label;
            elem.appendChild(found);
        }
        elem.value = v;
        elem.dispatchEvent(new Event('change', { bubbles: true }));
    }

    window.PicassoSelect = {
        enhance: enhance,
        autoEnhance: scanAndEnhance,
        refresh: refresh,
        addOption: addOption
    };
})();
