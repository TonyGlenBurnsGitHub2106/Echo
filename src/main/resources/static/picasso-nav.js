/*
 * picasso-nav.js -- Leonardo's true Back / Forward call stack.
 *
 * Story 3482 ("Traffic Confusion"). Tony: "I am on a matter in entity IV. I got
 * to a checklist. I click back and it takes me to the checklist main page. This
 * happens constantly. Why can't back and forward be a true callstack and always
 * take me back where I came from?"
 *
 * WHY THE OLD APPROACH FAILED. Detail pages each carried their own copy of a
 * guard that read document.referrer and, only when it looked right, called
 * history.back(); otherwise the anchor's hard-coded href (the LIST page) won.
 * document.referrer is empty or stale in a pile of ordinary situations -- a
 * page that reloaded itself, a link opened from the PWA shell, a redirect, a
 * restored tab -- so the fallback fired at random. That is exactly the
 * "works sometimes" symptom Tony reported.
 *
 * WHAT THIS DOES INSTEAD. It keeps an explicit, per-tab navigation stack in
 * sessionStorage: an ordered list of the pages visited plus a cursor into it.
 * Back walks the cursor down, Forward walks it up, and neither ever guesses.
 * Because the stack is ours, it survives missing referrers, page reloads, and
 * same-page query changes (an entity tab, a drill-down level), which the
 * browser's own history exposes but does not let a page read.
 *
 * The browser's history is still used to MOVE when we can prove the previous
 * browser entry is the one we want (same path, or a matching referrer), so
 * scroll position and the back-forward cache keep working. Otherwise we
 * navigate outright. Either way the destination is the same.
 *
 * Loaded lazily by auth.js on every authenticated page.
 */
(function () {
    'use strict';

    if (window.LeoNav) return;

    var STACK_KEY = 'leoNavStack';   // { e: [ { u, t } ], i: cursor }
    var MOVE_KEY  = 'leoNavMove';    // 'back' | 'fwd', set just before we navigate
    var MAX       = 60;              // plenty deep; keeps sessionStorage small

    // ---- storage helpers -------------------------------------------------
    // Every read and write is wrapped: sessionStorage throws in private
    // windows and when a browser is set to block site data, and a nav stack
    // is never worth taking a page down over.

    function readState() {
        try {
            var raw = window.sessionStorage.getItem(STACK_KEY);
            if (!raw) return { e: [], i: -1 };
            var s = JSON.parse(raw);
            if (!s || !(s.e instanceof Array) || typeof s.i !== 'number') return { e: [], i: -1 };
            if (s.i < -1 || s.i >= s.e.length) return { e: [], i: -1 };
            return s;
        } catch (err) {
            return { e: [], i: -1 };
        }
    }

    function writeState(s) {
        try { window.sessionStorage.setItem(STACK_KEY, JSON.stringify(s)); } catch (err) { /* ignore */ }
    }

    function takeMove() {
        try {
            var m = window.sessionStorage.getItem(MOVE_KEY);
            window.sessionStorage.removeItem(MOVE_KEY);
            return m;
        } catch (err) { return null; }
    }

    function setMove(m) {
        try { window.sessionStorage.setItem(MOVE_KEY, m); } catch (err) { /* ignore */ }
    }

    // ---- url helpers -----------------------------------------------------

    function here() {
        return window.location.pathname + window.location.search;
    }

    // Path + query of the referring page, or '' when there is no usable one.
    // Only same-origin referrers count; a referrer from anywhere else tells us
    // nothing about our own stack.
    function referrerPath() {
        var ref = document.referrer || '';
        if (!ref) return '';
        try {
            var u = new URL(ref, window.location.href);
            if (u.origin !== window.location.origin) return '';
            return u.pathname + u.search;
        } catch (err) { return ''; }
    }

    function pathOnly(url) {
        var q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    // ---- the stack -------------------------------------------------------

    var mState = readState();

    function entryAt(i) {
        return (i >= 0 && i < mState.e.length) ? mState.e[i] : null;
    }

    function urlAt(i) {
        var e = entryAt(i);
        return e ? e.u : null;
    }

    // Reconcile the stack with the page we are actually on. Called on load,
    // on popstate (the user pressed the browser's own Back), and on a
    // back-forward-cache restore.
    function sync() {
        var cur  = here();
        var move = takeMove();

        if (move === 'back' && urlAt(mState.i - 1) === cur) {
            mState.i -= 1;
        } else if (move === 'fwd' && urlAt(mState.i + 1) === cur) {
            mState.i += 1;
        } else if (urlAt(mState.i) === cur) {
            // A reload, or a hash-only change. Nothing moves.
        } else if (urlAt(mState.i - 1) === cur) {
            mState.i -= 1;                       // browser Back / swipe back
        } else if (urlAt(mState.i + 1) === cur) {
            mState.i += 1;                       // browser Forward
        } else {
            push(cur);
            return;
        }
        touchTitle();
        writeState(mState);
    }

    // A genuinely new destination truncates anything ahead of the cursor --
    // the same thing a browser does, and the thing that makes Forward mean
    // something.
    function push(url) {
        mState.e = mState.e.slice(0, mState.i + 1);
        mState.e.push({ u: url, t: document.title || '' });
        mState.i = mState.e.length - 1;
        if (mState.e.length > MAX) {
            var drop = mState.e.length - MAX;
            mState.e = mState.e.slice(drop);
            mState.i -= drop;
        }
        writeState(mState);
    }

    // Pages set their real title after they load their record, so re-read it
    // rather than freezing whatever the static markup said.
    function touchTitle() {
        var e = entryAt(mState.i);
        if (e && document.title) e.t = document.title;
    }

    // ---- moving ----------------------------------------------------------

    // Prefer the browser's own history when we can prove its previous entry is
    // the one we want -- that restores scroll position and can come out of the
    // back-forward cache. "Same path, different query" covers an entity tab or
    // a drill-down level; a matching referrer covers an ordinary link.
    function go(target, direction) {
        setMove(direction);
        var canUseHistory = window.history.length > 1
            && direction === 'back'
            && (pathOnly(target) === pathOnly(here()) || referrerPath() === target);
        if (canUseHistory) window.history.back();
        else window.location.href = target;
    }

    var LeoNav = {
        canBack:    function () { return mState.i > 0; },
        canForward: function () { return mState.i >= 0 && mState.i < mState.e.length - 1; },

        // Where Back would land, as a path+query, or null. Handy for a tooltip.
        backTarget:    function () { return urlAt(mState.i - 1); },
        forwardTarget: function () { return urlAt(mState.i + 1); },

        // Walk one step back. Returns false when there is nowhere to go, so a
        // caller can fall through to its own hard-coded landing page.
        back: function () {
            var t = urlAt(mState.i - 1);
            if (!t) return false;
            go(t, 'back');
            return true;
        },

        forward: function () {
            var t = urlAt(mState.i + 1);
            if (!t) return false;
            go(t, 'fwd');
            return true;
        },

        // Read-only view of the stack, oldest first, for debugging.
        stack: function () { return { entries: mState.e.slice(), index: mState.i }; }
    };

    window.LeoNav = LeoNav;

    // ---- keeping up with pages that rewrite their own URL ----------------
    // Several pages sync state into the query string without a page load --
    // the entity tabs, the report drill-downs, the reading nook view. Patch
    // the two history calls so the stack sees those moves instead of silently
    // going stale.

    (function patchHistory() {
        var origReplace = window.history.replaceState;
        var origPush    = window.history.pushState;
        if (typeof origReplace === 'function') {
            window.history.replaceState = function () {
                var r = origReplace.apply(window.history, arguments);
                var e = entryAt(mState.i);
                if (e) { e.u = here(); e.t = document.title || e.t; writeState(mState); }
                return r;
            };
        }
        if (typeof origPush === 'function') {
            window.history.pushState = function () {
                var r = origPush.apply(window.history, arguments);
                if (urlAt(mState.i) !== here()) push(here());
                return r;
            };
        }
    })();

    window.addEventListener('popstate', function () { sync(); });
    window.addEventListener('pageshow', function (ev) { if (ev && ev.persisted) sync(); });

    // ---- the page's own back arrow ---------------------------------------
    // Detail pages carry a "<<" arrow in their header whose href is the list
    // page. That href is the CORRECT cold-open fallback -- land on a checklist
    // from a bookmark and the checklist list is where you want the arrow to
    // take you -- so it stays. It just stops being the answer whenever the
    // stack knows where the user actually came from.
    //
    // Delegated, so it covers arrows that pages render after load.
    document.addEventListener('click', function (ev) {
        var a = ev.target && ev.target.closest
            ? ev.target.closest('a[data-lw-back], a[aria-label="Back"], a[data-testid="nav-back"]')
            : null;
        if (!a) return;
        if (a.closest('#lwIconBand')) return;   // the band wires its own buttons
        if (ev.defaultPrevented) return;
        if (!LeoNav.canBack()) return;          // cold open -- let the href win
        ev.preventDefault();
        LeoNav.back();
    });

    // ---- start -----------------------------------------------------------

    sync();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { touchTitle(); writeState(mState); });
    }
    // Records set their title after their fetch resolves.
    setTimeout(function () { touchTitle(); writeState(mState); }, 1500);
})();
