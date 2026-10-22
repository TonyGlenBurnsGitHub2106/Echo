/*
 * picasso-context-menu.js -- the Leonardo Context Menu (Story 5619).
 *
 * A small menu of actions for one row or thing on a page. Tony's rule: a
 * right-click menu ALWAYS has a second way in that works in the PWA on a phone,
 * because a phone has no right mouse button. This file gives every page three
 * doors to the SAME items:
 *
 *   1. Right-click (desktop browsers; also the keyboard's Menu key / Shift+F10).
 *   2. Long-press on a touch screen (iOS never raises contextmenu in a PWA, so
 *      this is our own 500 ms timer, not the browser's).
 *   3. A visible More button on the row -- PicassoContextMenu.moreButton().
 *      This is the door that is always there and always discoverable; a page
 *      that offers a right-click menu must show it (or another visible button
 *      that reaches the same actions, as Linked Entities does).
 *
 * A page registers what its menu holds:
 *
 *   PicassoContextMenu.attach({
 *       root:     document.getElementById('myList'),  // optional, default document
 *       selector: '.my-row',                          // optional: the row element
 *       items:    function (row, target) {            // null / [] = no menu here
 *           return [
 *               { label: 'Open',   testId: 'my-ctx-open',   action: function () { ... } },
 *               '-',                                   // a separator
 *               { label: 'Delete', testId: 'my-ctx-delete', danger: true, action: ... }
 *           ];
 *       }
 *   });
 *
 * Several registrations may answer the same spot; their items are shown
 * together, split by separators (a record row can carry its page's actions AND
 * Linked Entities). A right-click on a field, editable text, or with text
 * selected keeps the browser's own menu, so Copy and Paste still work.
 *
 * It is a menu, not a modal: a tap or click anywhere else, scrolling, or Escape
 * closes it (menus are exempt from the modal rule). Loads its own CSS.
 */
(function () {
    'use strict';
    if (window.PicassoContextMenu) return;

    var CSS_HREF = '/picasso-context-menu.css?v=1';
    var LONG_PRESS_MS = 500;
    var MOVE_TOLERANCE_PX = 10;

    var regs = [];          // attach() registrations, in order
    var current = null;     // items on show, while the menu is open

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function ensureCss() {
        if (document.querySelector('link[data-picasso-context-menu-css]')) return;
        var l = document.createElement('link');
        l.rel = 'stylesheet';
        l.href = CSS_HREF;
        l.setAttribute('data-picasso-context-menu-css', '1');
        document.head.appendChild(l);
    }

    // ---- what the menu holds at a spot ---------------------------------------

    function itemsAt(target) {
        if (!target || !target.closest) return [];
        if (target.closest('.pcm-menu')) return [];
        var all = [];
        regs.forEach(function (r) {
            var root = r.root || document;
            if (root !== document && !root.contains(target)) return;
            var row = r.selector ? target.closest(r.selector) : target;
            if (!row || (root !== document && !root.contains(row))) return;
            var got;
            try { got = r.items(row, target); } catch (e) { got = null; }
            if (!got || !got.length) return;
            if (all.length) all.push('-');
            all = all.concat(got);
        });
        // No separator at either end, never two in a row.
        return all.filter(function (it, i, a) {
            if (it !== '-') return true;
            return i > 0 && i < a.length - 1 && a[i - 1] !== '-';
        });
    }

    function keepsBrowserMenu(target) {
        if (target.closest && target.closest('input, textarea, select, [contenteditable="true"], [contenteditable=""]')) return true;
        var s = window.getSelection ? window.getSelection() : null;
        return !!(s && String(s).trim().length);
    }

    // ---- the menu ------------------------------------------------------------

    function ensureMenu() {
        var m = document.getElementById('pcmMenu');
        if (m) return m;
        ensureCss();
        m = document.createElement('div');
        m.id = 'pcmMenu';
        m.className = 'pcm-menu';
        m.setAttribute('role', 'menu');
        m.setAttribute('data-testid', 'context-menu');
        document.body.appendChild(m);
        m.addEventListener('click', function (e) {
            var b = e.target.closest('[data-pcm-index]');
            if (!b || b.disabled) return;
            var it = current && current[parseInt(b.getAttribute('data-pcm-index'), 10)];
            hide();
            if (it && typeof it.action === 'function') it.action();
        });
        m.addEventListener('keydown', function (e) {
            if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
            e.preventDefault();
            var btns = Array.prototype.slice.call(m.querySelectorAll('.pcm-item:not([disabled])'));
            var i = btns.indexOf(document.activeElement);
            i = e.key === 'ArrowDown' ? (i + 1) % btns.length : (i - 1 + btns.length) % btns.length;
            if (btns[i]) btns[i].focus();
        });
        return m;
    }

    function isOpen() {
        var m = document.getElementById('pcmMenu');
        return !!(m && m.classList.contains('open'));
    }

    /** Show items at a screen point. Items: {label, action, testId, danger, disabled} or '-'. */
    function show(x, y, items) {
        if (!items || !items.length) return;
        var m = ensureMenu();
        current = items;
        m.innerHTML = items.map(function (it, i) {
            if (it === '-') return '<div class="pcm-sep" role="separator"></div>';
            return '<button type="button" class="pcm-item' + (it.danger ? ' danger' : '') + '" role="menuitem"'
                 + ' data-pcm-index="' + i + '"'
                 + ' data-testid="' + esc(it.testId || 'context-menu-item') + '"'
                 + (it.disabled ? ' disabled' : '') + '>' + esc(it.label) + '</button>';
        }).join('');
        m.classList.add('open');
        var w = m.offsetWidth, h = m.offsetHeight;
        m.style.left = Math.max(4, Math.min(x, window.innerWidth  - w - 4)) + 'px';
        m.style.top  = Math.max(4, Math.min(y, window.innerHeight - h - 4)) + 'px';
        var first = m.querySelector('.pcm-item:not([disabled])');
        if (first) first.focus({ preventScroll: true });
    }

    function hide() {
        var m = document.getElementById('pcmMenu');
        if (m) m.classList.remove('open');
        current = null;
    }

    function showAt(target, x, y) {
        var items = itemsAt(target);
        if (!items.length) return false;
        show(x, y, items);
        return true;
    }

    // ---- door 1: right-click (and the keyboard Menu key) ----------------------

    var touch = { timer: 0, x: 0, y: 0, target: null, firedAt: 0, pressFired: false };

    function firedRecently() { return Date.now() - touch.firedAt < 800; }

    document.addEventListener('contextmenu', function (e) {
        // Android raises contextmenu on a long press too. If our timer is still
        // running, open now (Android may cancel the touch right after); if it
        // already opened the menu, just keep the browser's own menu away.
        if (touch.timer) {
            clearTouch();
            touch.firedAt = Date.now();
            touch.pressFired = true;
            if (showAt(touch.target, touch.x, touch.y)) e.preventDefault();
            return;
        }
        if (firedRecently()) {
            if (itemsAt(e.target).length) e.preventDefault();
            return;
        }
        if (keepsBrowserMenu(e.target)) return;
        var x = e.clientX, y = e.clientY;
        if (!x && !y && e.target.getBoundingClientRect) {     // keyboard: no pointer
            var r = e.target.getBoundingClientRect();
            x = r.left + 12; y = r.bottom;
        }
        if (showAt(e.target, x, y)) e.preventDefault();
    });

    // ---- door 2: long-press on a touch screen --------------------------------

    function clearTouch() {
        if (touch.timer) clearTimeout(touch.timer);
        touch.timer = 0;
    }

    document.addEventListener('touchstart', function (e) {
        clearTouch();
        touch.pressFired = false;
        if (!e.touches || e.touches.length !== 1) return;
        var t = e.target;
        if (keepsBrowserMenu(t) || !itemsAt(t).length) return;
        touch.x = e.touches[0].clientX;
        touch.y = e.touches[0].clientY;
        touch.target = t;
        touch.timer = setTimeout(function () {
            touch.timer = 0;
            touch.firedAt = Date.now();
            touch.pressFired = true;
            showAt(touch.target, touch.x, touch.y);
        }, LONG_PRESS_MS);
    }, { passive: true });

    document.addEventListener('touchmove', function (e) {
        if (!touch.timer || !e.touches || !e.touches[0]) return;
        if (Math.abs(e.touches[0].clientX - touch.x) > MOVE_TOLERANCE_PX
         || Math.abs(e.touches[0].clientY - touch.y) > MOVE_TOLERANCE_PX) clearTouch();
    }, { passive: true });

    document.addEventListener('touchend', function (e) {
        clearTouch();
        // The tap that ends a long press must not also open the row.
        if (touch.pressFired) {
            touch.pressFired = false;
            touch.firedAt = Date.now();      // starts the window for the stray click
            if (e.cancelable) e.preventDefault();
        }
    });
    document.addEventListener('touchcancel', clearTouch);

    // ---- door 3: the visible More button -------------------------------------

    /** HTML for a row's More button: opens the same menu the row's right-click does. */
    function moreButton(opts) {
        opts = opts || {};
        return '<button type="button" class="pcm-more' + (opts.className ? ' ' + esc(opts.className) : '') + '" data-pcm-more="1"'
             + ' title="' + esc(opts.title || 'More actions') + '" aria-label="' + esc(opts.title || 'More actions') + '"'
             + ' aria-haspopup="menu" data-testid="' + esc(opts.testId || 'context-more-btn') + '">&#8943;</button>';
    }

    document.addEventListener('click', function (e) {
        var b = e.target.closest && e.target.closest('[data-pcm-more]');
        if (b) {
            e.preventDefault();
            e.stopPropagation();
            if (isOpen()) { hide(); return; }
            var r = b.getBoundingClientRect();
            showAt(b, r.left, r.bottom + 2);
            return;
        }
        // The synthetic click a long press leaves behind: swallow it, keep the menu.
        if (firedRecently() && (!e.target.closest || !e.target.closest('.pcm-menu'))) {
            touch.firedAt = 0;
            e.preventDefault();
            e.stopPropagation();
            return;
        }
        if (isOpen() && (!e.target.closest || !e.target.closest('.pcm-menu'))) hide();
    }, true);

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && isOpen()) { e.stopPropagation(); hide(); }
    }, true);

    window.addEventListener('scroll', function (e) {
        if (e.target && e.target.closest && e.target.closest('.pcm-menu')) return;
        hide();
    }, true);
    window.addEventListener('resize', hide);

    /** Register what the menu holds. Returns a function that removes the registration. */
    function attach(opts) {
        if (!opts || typeof opts.items !== 'function') return function () {};
        var r = { root: opts.root || document, selector: opts.selector || '', items: opts.items };
        regs.push(r);
        return function () { regs = regs.filter(function (x) { return x !== r; }); };
    }

    window.PicassoContextMenu = {
        attach: attach,
        show: show,
        hide: hide,
        isOpen: isOpen,
        moreButton: moreButton,
        itemsAt: itemsAt
    };
})();
