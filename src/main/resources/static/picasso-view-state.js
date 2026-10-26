/* ------------------------------------------------------------------------
   PicassoViewState -- remembered view state that follows the USER, not the
   device. Open / collapsed groups, Hide checked, Add to top ... used to live
   only in localStorage, so Chrome on the desk and the phone PWA each kept
   their own copy and never saw each other's choices.

   The DATABASE (UserViewState, via /api/view-state) is now the truth;
   localStorage is kept as a same-device cache so a page still draws
   instantly and still works offline.

       PicassoViewState.get(key)        -> string or null (sync, from the cache)
       PicassoViewState.set(key, value) -> cache now, server shortly after
       PicassoViewState.sync(prefix)    -> Promise<boolean>: pull the server's
                                           keys under prefix into the cache;
                                           true when anything changed

   sync() rules:
     - A key the server has wins over the cache.
     - A key only the cache has is pushed up. That is the one-time migration
       of what each device remembered before this existed.
     - A key set() on this page while the GET was in flight is left alone --
       the user's newer tap must not be undone by an older answer.

   Keys keep the names pages already used ('leo.cl.hideChecked.16'), so a
   device's existing memory carries straight over.

   USER PLUS DEVICE. Most pages remember per user. A page that must remember
   per user AND per device (the Gmail Client: the phone and the PC each keep
   their own tab, folds and preview pane) puts its keys under

       PicassoViewState.deviceKey('gmailclient.')
           -> 'leo.device.<deviceId>.gmailclient.'

   and syncs that prefix like any other. The rows are still in the database,
   one set per device. The device id is a random name this browser makes once
   and keeps under 'picasso.deviceId'. It is an identity, not remembered
   state, so it is the one thing that has to live on the device itself.
   An installed PWA keeps its own storage, so it counts as its own device.

   AUTOMATION NEVER SAVES (2026-10-22). A browser driven by Playwright -- the
   nightly smoke, demo videos, a session's probe -- signs in as Tony, so every
   tap it made was saved as his: the Gmail Client topic's Unread-then-All
   turned his Unread filter into All every night, and 26 other choices (Think
   Tank and checklist folds, Files tools, Health chart tabs ...) went the same
   way. When navigator.webdriver is true the page still READS the server (so it
   starts from Tony's real choices) and still remembers in its own cache for
   the rest of that browser's life, but nothing is ever sent up. A probe that
   must test the server save itself sets window.__LEO_VIEWSTATE_LIVE = true in
   an init script. PicassoViewState.automated says which mode the page is in.
   ------------------------------------------------------------------------ */
(function () {
    'use strict';

    var API = '/api/view-state';
    var FLUSH_MS = 400;

    var pending = {};            // key -> value waiting to be PUT
    var flushTimer = null;
    var dirtySeq = {};           // key -> sequence number of its last set()
    var savedSeq = {};           // key -> sequence number the server has confirmed holding
    var seq = 0;
    var AUTOMATED = !!(window.navigator && window.navigator.webdriver) && !window.__LEO_VIEWSTATE_LIVE;

    function cacheGet(key) {
        try { return window.localStorage ? window.localStorage.getItem(key) : null; }
        catch (e) { return null; }
    }
    function cacheSet(key, value) {
        try { if (window.localStorage) window.localStorage.setItem(key, value); }
        catch (e) { /* private mode / quota -- the server copy still holds */ }
    }
    function cacheKeys(prefix) {
        var out = [];
        try {
            var ls = window.localStorage;
            if (!ls) return out;
            for (var i = 0; i < ls.length; i++) {
                var k = ls.key(i);
                if (k && k.indexOf(prefix) === 0) out.push(k);
            }
        } catch (e) { /* ignore */ }
        return out;
    }

    function api() { return window.LWAuth || null; }

    function flush() {
        flushTimer = null;
        var batch = pending;
        pending = {};
        var keys = Object.keys(batch);
        if (!keys.length || !api() || AUTOMATED) return;   // automation keeps its taps to itself
        // The server takes at most 50 keys per save.
        for (var i = 0; i < keys.length; i += 50) {
            var part = {}, sent = {};
            keys.slice(i, i + 50).forEach(function (k) { part[k] = batch[k]; sent[k] = dirtySeq[k] || 0; });
            api().apiPost(API, { values: part }, 'PUT').then(function (r) {
                if (!r || r.ok === false) return;
                Object.keys(sent).forEach(function (k) { if (sent[k] > (savedSeq[k] || 0)) savedSeq[k] = sent[k]; });
            }).catch(function () { /* cache still holds */ });
        }
    }
    function queue(key, value) {
        pending[key] = value;
        if (!flushTimer) flushTimer = setTimeout(flush, FLUSH_MS);
    }

    // A tap just before the page closes must still reach the server.
    window.addEventListener('pagehide', function () {
        if (flushTimer) { clearTimeout(flushTimer); flush(); }
    });

    function get(key) { return cacheGet(key); }

    function set(key, value) {
        var v = (value === null || value === undefined) ? '' : String(value);
        dirtySeq[key] = ++seq;
        cacheSet(key, v);
        // Always sent, even when the cache already held v: the server may hold
        // another device's value. The save proc skips a write that changes nothing.
        queue(key, v);
    }

    async function sync(prefix) {
        if (!prefix || !api()) return false;
        var startSeq = seq;
        // A tap the server has not confirmed yet (still queued, or its save still on the wire) is
        // newer than anything this read can bring back: Year then Type tapped quickly used to have
        // the read return Year and overwrite Type.
        var unsaved = {};
        Object.keys(dirtySeq).forEach(function (k) { if (dirtySeq[k] > (savedSeq[k] || 0)) unsaved[k] = true; });
        var r;
        try { r = await api().apiGet(API + '?prefix=' + encodeURIComponent(prefix)); }
        catch (e) { return false; }
        if (!r || !r.ok || !r.data || !r.data.values) return false;

        var server = r.data.values;
        var changed = false;
        Object.keys(server).forEach(function (k) {
            if ((dirtySeq[k] || 0) > startSeq || unsaved[k]) return;   // newer local tap wins
            var v = server[k] === null ? '' : String(server[k]);
            if (cacheGet(k) !== v) { cacheSet(k, v); changed = true; }
        });
        cacheKeys(prefix).forEach(function (k) {
            if (!Object.prototype.hasOwnProperty.call(server, k)) queue(k, cacheGet(k));
        });
        return changed;
    }

    var DEVICE_ID_KEY = 'picasso.deviceId';   // deliberately outside 'leo.' so no sync ever pushes it up
    var deviceCache = null;

    function newDeviceId() {
        var hex = '';
        try {
            var b = new Uint8Array(8);
            window.crypto.getRandomValues(b);
            for (var i = 0; i < b.length; i++) hex += ('0' + b[i].toString(16)).slice(-2);
        } catch (e) {
            while (hex.length < 16) hex += Math.floor(Math.random() * 16).toString(16);
        }
        return hex;
    }

    /** This browser's device id: made once, then kept. Falls back to the session when storage is blocked. */
    function deviceId() {
        if (deviceCache) return deviceCache;
        var id = cacheGet(DEVICE_ID_KEY);
        if (!/^[0-9a-f]{16}$/.test(id || '')) {
            try { id = window.sessionStorage ? window.sessionStorage.getItem(DEVICE_ID_KEY) : null; } catch (e) { id = null; }
            if (!/^[0-9a-f]{16}$/.test(id || '')) id = newDeviceId();
            cacheSet(DEVICE_ID_KEY, id);
            try { if (window.sessionStorage) window.sessionStorage.setItem(DEVICE_ID_KEY, id); } catch (e) { /* memory only */ }
        }
        deviceCache = id;
        return id;
    }

    /** 'gmailclient.' -> 'leo.device.<deviceId>.gmailclient.' -- a key (or prefix) for this user on this device. */
    function deviceKey(name) { return 'leo.device.' + deviceId() + '.' + (name || ''); }

    window.PicassoViewState = { get: get, set: set, sync: sync, deviceId: deviceId, deviceKey: deviceKey, automated: AUTOMATED };
})();
