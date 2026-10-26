/* picasso-split.js -- the Bay Window (Leonardo Term "Preview Split"): a list with a preview
 * of the picked item beside it or below it.
 *
 *   - Preview on or off (the page owns the button and calls setOn).
 *   - The list on the LEFT or on TOP (toggleLayout), each kind of screen (desk / phone,
 *     split at deskMinWidth) remembering its own choice.
 *   - A draggable bar between them (mouse, finger, or arrow keys; double-click resets),
 *     its size remembered per kind of screen and per layout.
 *   - The list minimizes to a narrow strip on the left (or across the top), giving the
 *     preview the whole width; tapping the strip brings it back. Remembered per screen.
 *   - Opt in with opts.previewLabel and the PREVIEW minimizes the same way: it rolls away to a
 *     strip on the left (or across the top), giving the list the whole width. Only one side
 *     is ever minimized -- minimizing one brings the other back. Remembered per screen.
 *
 * Everything is remembered for the USER in the database through PicassoViewState (load
 * /picasso-view-state.js first, or call PicassoSplit.ensureViewState(prefix) and wait).
 * Keys, with opts.key as the prefix (e.g. 'leo.gmailclient.'):
 *     <key>layout.<desk|phone>            left | top
 *     <key>split.<desk|phone>.<layout>    the list's share in percent
 *     <key>listMin.<desk|phone>           '1' when the list is minimized
 *     <key>pvMin.<desk|phone>             '1' when the preview is minimized (previewLabel only)
 *
 *   var s = PicassoSplit.create({
 *       root: el, list: listScroller, preview: previewPane,   // list and preview are children of root
 *       key: 'leo.notes.', testid: 'entity-note', listLabel: 'Notes',
 *       deskMinWidth: 900, min: 20, max: 75, step: 2,
 *       previewLabel: 'Preview',           // optional: the preview can minimize too
 *       defaultLayout: function (kind) { return kind === 'desk' ? 'left' : 'top'; },
 *       onChange: function (s) { ... }      // after on / layout / minimize / screen-kind changes
 *   });
 *   s.setOn(true); s.isOn(); s.layout(); s.toggleLayout(); s.minimized(); s.setMinimized(b);
 *   s.previewMinimized(); s.setPreviewMinimized(b);
 *
 * The component adds the bar (<testid>-splitter), the minimize button in a slim bar at the
 * top of the list (<testid>-list-minimize) and the strip (<testid>-list-restore); with
 * previewLabel also a slim bar atop the preview (<testid>-preview-minimize) and its strip
 * (<testid>-preview-restore). Styling is
 * picasso-split.css; the page sizes the root (it must have a height when the preview is on).
 * Used by: Gmail Client (with previewLabel), the Notes tab on every record page.
 */
(function () {
    'use strict';
    if (window.PicassoSplit) return;

    var memory = {};   // used only when PicassoViewState is not on the page

    function vs() { return window.PicassoViewState || null; }
    function get(k) { var v = vs(); return v ? v.get(k) : (memory[k] == null ? null : memory[k]); }
    function set(k, val) { var v = vs(); if (v) v.set(k, val); else memory[k] = val; }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function create(opts) {
        var root = opts.root, list = opts.list, preview = opts.preview;
        if (root && root._picassoSplit) return root._picassoSplit;   // already a Bay Window
        if (!root || !list || !preview || list.parentNode !== root || preview.parentNode !== root) {
            throw new Error('PicassoSplit: list and preview must be direct children of root.');
        }
        var key = opts.key || 'leo.split.';
        var tid = opts.testid || 'split';
        var DESK = opts.deskMinWidth || 900;
        var MIN = opts.min || 20, MAX = opts.max || 75, STEP = opts.step || 2;
        var defaultLayout = opts.defaultLayout || function (kind) { return kind === 'desk' ? 'left' : 'top'; };
        var label = opts.listLabel || 'List';
        var pvLabel = opts.previewLabel || '';   // blank: the preview never minimizes
        var on = false;

        root.classList.add('ps-split');
        list.classList.add('ps-list');
        preview.classList.add('ps-preview');

        var rail = document.createElement('button');
        rail.type = 'button';
        rail.className = 'ps-rail';
        rail.setAttribute('data-testid', tid + '-list-restore');
        rail.innerHTML = '<span class="ps-rail-arrow" aria-hidden="true"></span><span class="ps-rail-text">' + esc(label) + '</span>';
        root.insertBefore(rail, list);

        var bar = document.createElement('div');
        bar.className = 'ps-listbar';
        bar.setAttribute('data-testid', tid + '-listbar');
        bar.innerHTML = '<span class="ps-listbar-text">' + esc(label) + '</span>'
            + '<button type="button" class="ps-min" data-testid="' + esc(tid) + '-list-minimize"></button>';
        list.insertBefore(bar, list.firstChild);
        var minBtn = bar.querySelector('.ps-min');

        // The preview's own minimize (opt-in): a slim bar atop the preview, and a strip that sits
        // on the left (or across the top), before the list.
        var pvRail = null, pvMinBtn = null;
        if (pvLabel) {
            pvRail = document.createElement('button');
            pvRail.type = 'button';
            pvRail.className = 'ps-rail ps-pvrail';
            pvRail.setAttribute('data-testid', tid + '-preview-restore');
            pvRail.innerHTML = '<span class="ps-rail-arrow" aria-hidden="true"></span><span class="ps-rail-text">' + esc(pvLabel) + '</span>';
            root.insertBefore(pvRail, root.firstChild);
            var pvBar = document.createElement('div');
            pvBar.className = 'ps-pvbar';
            pvBar.setAttribute('data-testid', tid + '-preview-bar');
            pvBar.innerHTML = '<span class="ps-listbar-text">' + esc(pvLabel) + '</span>'
                + '<button type="button" class="ps-min" data-testid="' + esc(tid) + '-preview-minimize"></button>';
            preview.insertBefore(pvBar, preview.firstChild);
            pvMinBtn = pvBar.querySelector('.ps-min');
        }

        var splitter = document.createElement('div');
        splitter.className = 'ps-splitter';
        splitter.setAttribute('role', 'separator');
        splitter.setAttribute('tabindex', '0');
        splitter.setAttribute('aria-label', 'Drag to make the list or the preview bigger');
        splitter.title = 'Drag to resize (double-click to reset)';
        splitter.setAttribute('data-testid', tid + '-splitter');
        root.insertBefore(splitter, preview);

        function kind() {
            return window.matchMedia && window.matchMedia('(min-width: ' + DESK + 'px)').matches ? 'desk' : 'phone';
        }
        function layout() {
            var v = get(key + 'layout.' + kind());
            return v === 'left' || v === 'top' ? v : defaultLayout(kind());
        }
        function splitKey() { return key + 'split.' + kind() + '.' + layout(); }
        function minimized() { return get(key + 'listMin.' + kind()) === '1'; }
        function pvMinimized() { return !!pvLabel && !minimized() && get(key + 'pvMin.' + kind()) === '1'; }
        function clamp(n) { return Math.max(MIN, Math.min(MAX, n)); }

        /** The list's share (%) across or down; blank goes back to the usual size. */
        function applySize(pct) {
            var n = parseFloat(pct);
            root.style.removeProperty('--ps-list-w');
            root.style.removeProperty('--ps-list-h');
            if (!(n >= MIN && n <= MAX)) return null;
            root.style.setProperty(layout() === 'top' ? '--ps-list-h' : '--ps-list-w', n.toFixed(1) + '%');
            splitter.setAttribute('aria-valuenow', String(Math.round(n)));
            return n;
        }

        function listPct() {
            var box = root.getBoundingClientRect(), l = list.getBoundingClientRect();
            if (layout() === 'top') return box.height ? l.height / box.height * 100 : 40;
            return box.width ? l.width / box.width * 100 : 40;
        }

        function apply() {
            var top = layout() === 'top', min = on && minimized();
            root.classList.toggle('ps-on', on);
            root.classList.toggle('ps-top', on && top);
            root.classList.toggle('ps-min', min);
            root.classList.toggle('ps-pvmin', on && pvMinimized());
            splitter.setAttribute('aria-orientation', top ? 'horizontal' : 'vertical');
            minBtn.innerHTML = top ? '&#9650;' : '&#9664;';
            minBtn.setAttribute('aria-label', 'Minimize the ' + label.toLowerCase());
            minBtn.title = minBtn.getAttribute('aria-label');
            rail.querySelector('.ps-rail-arrow').innerHTML = top ? '&#9660;' : '&#9654;';
            rail.setAttribute('aria-label', 'Show the ' + label.toLowerCase());
            rail.title = rail.getAttribute('aria-label');
            if (pvLabel) {
                pvMinBtn.innerHTML = top ? '&#9650;' : '&#9664;';
                pvMinBtn.setAttribute('aria-label', 'Minimize the ' + pvLabel.toLowerCase());
                pvMinBtn.title = pvMinBtn.getAttribute('aria-label');
                pvRail.querySelector('.ps-rail-arrow').innerHTML = top ? '&#9660;' : '&#9654;';
                pvRail.setAttribute('aria-label', 'Show the ' + pvLabel.toLowerCase());
                pvRail.title = pvRail.getAttribute('aria-label');
            }
            if (on) applySize(get(splitKey())); else applySize('');
        }

        function changed() { apply(); if (opts.onChange) opts.onChange(api); }

        function onDown(down) {
            if (!on) return;
            down.preventDefault();
            var box = root.getBoundingClientRect(), top = layout() === 'top';
            var pct = listPct();
            root.classList.add('ps-resizing');
            document.documentElement.classList.add(top ? 'ps-dragging-y' : 'ps-dragging-x');
            function move(ev) {
                pct = clamp(top ? (ev.clientY - box.top) / box.height * 100 : (ev.clientX - box.left) / box.width * 100);
                applySize(pct);
            }
            function up() {
                document.removeEventListener('pointermove', move);
                document.removeEventListener('pointerup', up);
                document.removeEventListener('pointercancel', up);
                root.classList.remove('ps-resizing');
                document.documentElement.classList.remove('ps-dragging-x', 'ps-dragging-y');
                set(splitKey(), pct.toFixed(1));
            }
            document.addEventListener('pointermove', move);
            document.addEventListener('pointerup', up);
            document.addEventListener('pointercancel', up);
        }

        splitter.addEventListener('pointerdown', onDown);
        splitter.addEventListener('dblclick', function () { applySize(''); set(splitKey(), ''); });
        splitter.addEventListener('keydown', function (e) {
            var top = layout() === 'top';
            var grow = top ? 'ArrowDown' : 'ArrowRight', shrink = top ? 'ArrowUp' : 'ArrowLeft';
            if (e.key !== grow && e.key !== shrink) return;
            e.preventDefault();
            e.stopPropagation();   // the bar's arrows resize; they must not walk the list
            var n = applySize(clamp(listPct() + (e.key === grow ? STEP : -STEP)));
            if (n) set(splitKey(), n.toFixed(1));
        });
        minBtn.addEventListener('click', function (e) { e.stopPropagation(); api.setMinimized(true); rail.focus(); });
        rail.addEventListener('click', function (e) { e.stopPropagation(); api.setMinimized(false); minBtn.focus(); });
        if (pvLabel) {
            pvMinBtn.addEventListener('click', function (e) { e.stopPropagation(); api.setPreviewMinimized(true); pvRail.focus(); });
            pvRail.addEventListener('click', function (e) { e.stopPropagation(); api.setPreviewMinimized(false); pvMinBtn.focus(); });
        }

        // Crossing between desk and phone widths switches to that screen's own layout, size and minimize.
        var lastKind = kind();
        window.addEventListener('resize', function () {
            var k = kind();
            if (k === lastKind) return;
            lastKind = k;
            if (on) changed();
        });

        var api = {
            setOn: function (b) { on = !!b; changed(); },
            isOn: function () { return on; },
            layout: layout,
            screenKind: kind,
            toggleLayout: function () { set(key + 'layout.' + kind(), layout() === 'top' ? 'left' : 'top'); changed(); },
            minimized: function () { return on && minimized(); },
            setMinimized: function (b) {
                set(key + 'listMin.' + kind(), b ? '1' : '');
                if (b && pvLabel) set(key + 'pvMin.' + kind(), '');   // only one side minimized at a time
                changed();
            },
            previewMinimized: function () { return on && pvMinimized(); },
            setPreviewMinimized: function (b) {
                if (!pvLabel) return;
                set(key + 'pvMin.' + kind(), b ? '1' : '');
                if (b) set(key + 'listMin.' + kind(), '');
                changed();
            },
            apply: apply,
            splitter: splitter
        };
        apply();
        root._picassoSplit = api;
        return api;
    }

    /** Make sure PicassoViewState is on the page and synced for this prefix. Resolves either way. */
    function ensureViewState(prefix) {
        function sync() {
            var v = vs();
            return v ? Promise.resolve(v.sync(prefix)).catch(function () { return false; }) : Promise.resolve(false);
        }
        if (vs()) return sync();
        return new Promise(function (resolve) {
            var s = document.createElement('script');
            s.src = '/picasso-view-state.js?v=4';
            s.onload = function () { sync().then(resolve); };
            s.onerror = function () { resolve(false); };
            document.head.appendChild(s);
        });
    }

    window.PicassoSplit = { create: create, ensureViewState: ensureViewState };
})();
