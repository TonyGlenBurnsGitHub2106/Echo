/*
 * picasso-multi-pick.js -- the shared "pick one or more" list for adding files
 * (Tony, 2026-10-29: "I need to be able to select multiples to add all at once").
 *
 * One copy, so every place that adds files from the Reading Nook or Leonardo
 * Files picks the same way: tap rows to tick them, the filter narrows the list,
 * ticks survive a change of filter (filter "DMV", tick two, filter "tax", tick
 * one, Save adds all three), Select All Shown ticks what is on screen.
 *
 *   PicassoMultiPick.open(opts) -> Promise<rows[] | null>
 *
 *   opts.title          heading
 *   opts.hint           optional line under the heading
 *   opts.rows           a fixed list, filtered here by name, OR
 *   opts.search(needle) -> Promise<rows[]>  asked again as the typing pauses
 *   opts.rowCap         most rows drawn at once (default 200); the rest are
 *                       reached by typing
 *   opts.placeholder    filter box text
 *   opts.testid         data-testid prefix (default 'multi-pick')
 *
 *   A row is { key, name, sub }. key must be unique; the rest of the row is
 *   handed back untouched, so callers keep whatever they need on it.
 *
 * Resolves with the ticked rows in the order they were ticked, or null on
 * Cancel / Escape. Closes only by its own buttons or Escape -- never a tap
 * outside. Loads picasso-multi-pick.css itself.
 *
 * Users: the Add Files menu on every record's Files tab, Stories and Solutions
 * (contacts/entity-detail.js), a Research project's Add file menu, and the
 * Gmail Client's Attach from the Reading Nook.
 */
(function () {
    'use strict';
    if (window.PicassoMultiPick) return;

    var CSS_HREF = '/picasso-multi-pick.css?v=1';
    var SEARCH_PAUSE_MS = 250;

    function ensureCss() {
        if (document.querySelector('link[data-pmp-css]')) return;
        var l = document.createElement('link');
        l.rel = 'stylesheet';
        l.href = CSS_HREF;
        l.setAttribute('data-pmp-css', '1');
        document.head.appendChild(l);
    }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function open(opts) {
        opts = opts || {};
        ensureCss();
        var tid = opts.testid || 'multi-pick';
        var cap = opts.rowCap || 200;
        var picked = [];          // ticked rows, in tick order
        var pickedKeys = {};      // key -> true
        var shown = [];           // rows on screen now
        var shownMore = 0;        // how many matches the cap left off screen
        var searchSeq = 0, searchTimer = 0;

        var bd = document.createElement('div');
        bd.className = 'pmp-backdrop';
        bd.setAttribute('data-testid', tid);
        bd.innerHTML = ''
            + '<div class="pmp-sheet" role="dialog" aria-modal="true">'
            +   '<div class="pmp-title">' + esc(opts.title || 'Pick files') + '</div>'
            +   (opts.hint ? '<div class="pmp-hint">' + esc(opts.hint) + '</div>' : '')
            +   '<input type="search" class="pmp-filter" autocomplete="off" data-voice-skip="true"'
            +     ' placeholder="' + esc(opts.placeholder || 'Filter') + '" data-testid="' + tid + '-filter">'
            +   '<div class="pmp-bar">'
            +     '<span class="pmp-count" data-testid="' + tid + '-count">None picked</span>'
            +     '<button type="button" class="pmp-link" data-act="all" data-testid="' + tid + '-all">Select All Shown</button>'
            +     '<button type="button" class="pmp-link" data-act="none" data-testid="' + tid + '-none">Clear</button>'
            +   '</div>'
            +   '<div class="pmp-list" data-testid="' + tid + '-list"></div>'
            +   '<div class="pmp-actions">'
            +     '<button type="button" class="pmp-btn" data-act="cancel" data-testid="' + tid + '-cancel">Cancel</button>'
            +     '<button type="button" class="pmp-btn pmp-primary" data-act="save" data-testid="' + tid + '-save" disabled>Save</button>'
            +   '</div>'
            + '</div>';
        document.body.appendChild(bd);

        var filter = bd.querySelector('.pmp-filter');
        var list = bd.querySelector('.pmp-list');
        var count = bd.querySelector('.pmp-count');
        var saveBtn = bd.querySelector('[data-act="save"]');

        return new Promise(function (resolve) {
            function close(result) {
                window.removeEventListener('keydown', onKey, true);
                clearTimeout(searchTimer);
                if (bd.parentNode) bd.parentNode.removeChild(bd);
                resolve(result);
            }
            function onKey(e) {
                if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(null); }
            }
            // On the window, in the capture phase, so the page's own Escape handling
            // (Gmail closes the compose window) never sees the Escape meant for this list.
            window.addEventListener('keydown', onKey, true);

            function paintCount() {
                var n = picked.length;
                count.textContent = n === 0 ? 'None picked' : (n === 1 ? '1 picked' : n + ' picked');
                saveBtn.disabled = n === 0;
            }

            function draw(rows, more) {
                shown = rows;
                shownMore = more;
                if (!rows.length) {
                    list.innerHTML = '<div class="pmp-empty">No matches.</div>';
                    return;
                }
                list.innerHTML = rows.map(function (r, i) {
                    var on = !!pickedKeys[r.key];
                    return '<div class="pmp-row' + (on ? ' is-on' : '') + '" data-i="' + i + '" role="checkbox"'
                         +   ' aria-checked="' + on + '" tabindex="0" data-testid="' + tid + '-row">'
                         +   '<span class="pmp-box" aria-hidden="true">' + (on ? '&#10003;' : '') + '</span>'
                         +   '<span class="pmp-text"><span class="pmp-name">' + esc(r.name) + '</span>'
                         +     (r.sub ? '<span class="pmp-sub">' + esc(r.sub) + '</span>' : '')
                         +   '</span>'
                         + '</div>';
                }).join('') + (more > 0
                    ? '<div class="pmp-empty">' + more + ' more -- keep typing to narrow</div>'
                    : '');
            }

            function toggle(r) {
                if (pickedKeys[r.key]) {
                    delete pickedKeys[r.key];
                    picked = picked.filter(function (p) { return p.key !== r.key; });
                } else {
                    pickedKeys[r.key] = true;
                    picked.push(r);
                }
            }

            function refresh() {
                var needle = filter.value.trim();
                if (opts.search) {
                    var seq = ++searchSeq;
                    list.innerHTML = '<div class="pmp-empty">Looking</div>';
                    Promise.resolve(opts.search(needle)).then(function (rows) {
                        if (seq !== searchSeq) return;
                        rows = Array.isArray(rows) ? rows : [];
                        draw(rows.slice(0, cap), rows.length - cap);
                    }, function (e) {
                        if (seq !== searchSeq) return;
                        list.innerHTML = '<div class="pmp-empty">' + esc(e && e.message ? e.message : 'Could not look.') + '</div>';
                    });
                    return;
                }
                var lc = needle.toLowerCase();
                var subset = lc
                    ? (opts.rows || []).filter(function (r) { return String(r.name || '').toLowerCase().indexOf(lc) >= 0; })
                    : (opts.rows || []);
                draw(subset.slice(0, cap), subset.length - cap);
            }

            list.addEventListener('click', function (e) {
                var rowEl = e.target.closest('.pmp-row');
                if (!rowEl) return;
                var r = shown[parseInt(rowEl.getAttribute('data-i'), 10)];
                if (!r) return;
                toggle(r);
                var on = !!pickedKeys[r.key];
                rowEl.classList.toggle('is-on', on);
                rowEl.setAttribute('aria-checked', String(on));
                rowEl.querySelector('.pmp-box').innerHTML = on ? '&#10003;' : '';
                paintCount();
            });
            list.addEventListener('keydown', function (e) {
                if ((e.key === ' ' || e.key === 'Enter') && e.target.classList.contains('pmp-row')) {
                    e.preventDefault();
                    e.target.click();
                }
            });
            bd.querySelector('[data-act="all"]').addEventListener('click', function () {
                shown.forEach(function (r) { if (!pickedKeys[r.key]) toggle(r); });
                draw(shown, shownMore);
                paintCount();
            });
            bd.querySelector('[data-act="none"]').addEventListener('click', function () {
                picked = []; pickedKeys = {};
                draw(shown, shownMore);
                paintCount();
            });
            bd.querySelector('[data-act="cancel"]').addEventListener('click', function () { close(null); });
            saveBtn.addEventListener('click', function () { if (picked.length) close(picked.slice()); });
            filter.addEventListener('input', function () {
                if (!opts.search) { refresh(); return; }
                clearTimeout(searchTimer);
                searchTimer = setTimeout(refresh, SEARCH_PAUSE_MS);
            });

            refresh();
            paintCount();
            setTimeout(function () { filter.focus(); }, 30);
        });
    }

    window.PicassoMultiPick = { open: open };
})();
