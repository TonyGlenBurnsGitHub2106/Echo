/* Shared helpers for every Picasso browser report. Each report's app.js imports
   what it needs. No framework, no build step -- works in any modern browser. */
(function () {
    'use strict';

    const Picasso = window.Picasso = window.Picasso || {};

    // ---- LeoDiag: per-device diagnostic-mode flag ---------------------------
    // One toggle in Global Settings; every diagnostic-only feature checks it.
    // Stored in localStorage so the choice survives page reloads but stays
    // device-local (Tony tests on one phone, doesn't want diag visuals on his
    // desktop browser session). Off by default.
    //
    // To use it in any new feature:
    //   if (window.LeoDiag && window.LeoDiag.enabled) { ...render diag UI... }
    //
    // To toggle it:
    //   window.LeoDiag.set(true) / set(false)
    //
    // Both `enabled` and `set` are safe to call before DOMContentLoaded.
    window.LeoDiag = window.LeoDiag || (function () {
        var KEY = 'leo.diagMode';
        var on = false;
        try {
            on = (window.localStorage && window.localStorage.getItem(KEY) === '1');
        } catch (_) { /* private browsing etc - default off */ }
        return {
            enabled: on,
            set: function (v) {
                this.enabled = !!v;
                try {
                    if (window.localStorage) {
                        window.localStorage.setItem(KEY, this.enabled ? '1' : '0');
                    }
                } catch (_) {}
            }
        };
    })();

    // ---- LeoUrlBar: per-device "show the page URL" flag ---------------------
    // Same shape as LeoDiag above, different purpose and different key. When
    // on, quick-launch.js renders a read-only, copyable address row inside the
    // Leonardo Main Menu Tool Bar Strip on every page, so the URL of whatever
    // page you are looking at can be read and copied from inside the PWA
    // (which has no browser address bar of its own). Device-local, off by
    // default. Toggle lives in Admin -> Global Settings -> Page URL.
    //
    //   if (window.LeoUrlBar && window.LeoUrlBar.enabled) { ... }
    //   window.LeoUrlBar.set(true) / set(false)
    window.LeoUrlBar = window.LeoUrlBar || (function () {
        var KEY = 'leo.showUrl';
        var on = false;
        try {
            on = (window.localStorage && window.localStorage.getItem(KEY) === '1');
        } catch (_) { /* private browsing etc - default off */ }
        return {
            key: KEY,
            enabled: on,
            set: function (v) {
                this.enabled = !!v;
                try {
                    if (window.localStorage) {
                        window.localStorage.setItem(KEY, this.enabled ? '1' : '0');
                    }
                } catch (_) {}
            }
        };
    })();

    // ---- LeoServerBrowser: "this browser is on the Renaissance server" ------
    // A request to https://leonardo-web.com from a browser ON the server goes
    // out to Cloudflare and back through the tunnel, so it looks exactly like a
    // phone on the home Wi-Fi and gets the full passkey step-up (Writing Filing
    // Cabinet, Folder Shortcuts). Only http://localhost:8743 is recognised as
    // genuinely local (LocalTrust.java). This per-device flag sends a browser
    // that Tony has marked as "on the server" to the local address instead.
    //
    // It is a device identity, like picasso.deviceId -- not remembered UI
    // state -- and it grants nothing: the redirect only changes the address.
    // On any other machine, localhost:8743 is that machine, not Renaissance,
    // and the security check happens on the server either way.
    //
    // Escape hatch: ?stayRemote=1 on any leonardo-web.com page turns the flag
    // off and stays put (once it is on, the remote Global Settings page
    // redirects before its checkbox could be unticked).
    // Toggle lives in Admin -> Global Settings -> This Is The Server.
    window.LeoServerBrowser = window.LeoServerBrowser || (function () {
        var KEY = 'leo.thisIsServer';
        var LOCAL_ORIGIN = 'http://localhost:8743';
        var on = false;
        try {
            on = (window.localStorage && window.localStorage.getItem(KEY) === '1');
        } catch (_) { /* private browsing etc - default off */ }
        function isLocalHost() {
            var h = (window.location.hostname || '').toLowerCase();
            return h === 'localhost' || h === '127.0.0.1' || h === '::1' || h === '[::1]';
        }
        var api = {
            key: KEY,
            enabled: on,
            localOrigin: LOCAL_ORIGIN,
            isLocalHost: isLocalHost,
            set: function (v) {
                this.enabled = !!v;
                try {
                    if (window.localStorage) {
                        window.localStorage.setItem(KEY, this.enabled ? '1' : '0');
                    }
                } catch (_) {}
            },
            localUrl: function () {
                var l = window.location;
                return LOCAL_ORIGIN + l.pathname + l.search + l.hash;
            }
        };
        try {
            var params = new URLSearchParams(window.location.search);
            if (params.get('stayRemote') === '1') {
                api.set(false);
            } else if (api.enabled && !isLocalHost()) {
                window.location.replace(api.localUrl());
            }
        } catch (_) { /* never let the redirect break a page */ }
        return api;
    })();

    // ---- DEPLOY MARKER (gated behind LeoDiag) -----------------------------
    // Tiny fixed-position badge that confirms a fresh build reached the
    // browser. Only renders when diagnostics are on - normal users never
    // see it. Change the color/label on each redeploy so Tony can verify
    // visually which build he has.
    var PICASSO_DEPLOY_COLOR = '#F1C40F';      // gold
    var PICASSO_DEPLOY_LABEL = 'build 18:30 gold';
    function ensureDeployBadge() {
        if (!(window.LeoDiag && window.LeoDiag.enabled)) return;   // diagnostics off
        if (document.getElementById('picassoDeployMarker')) return;
        if (!document.body) {
            document.addEventListener('DOMContentLoaded', ensureDeployBadge);
            return;
        }
        var b = document.createElement('div');
        b.id = 'picassoDeployMarker';
        b.textContent = PICASSO_DEPLOY_LABEL;
        b.title = 'LeonardoWeb deploy marker - tap to dismiss';
        b.style.cssText =
            'position: fixed !important;' +
            'top: 8px !important; right: 8px !important;' +
            'z-index: 2147483646 !important;' +
            'background: ' + PICASSO_DEPLOY_COLOR + ' !important;' +
            'color: #fff !important;' +
            'font: 600 10pt "Segoe UI", system-ui, sans-serif !important;' +
            'padding: 4px 10px !important;' +
            'border-radius: 12px !important;' +
            'box-shadow: 0 2px 6px rgba(0,0,0,0.3) !important;' +
            'cursor: pointer !important;' +
            'pointer-events: auto !important;';
        b.addEventListener('click', function () {
            try { b.parentNode.removeChild(b); } catch (e) {}
        });
        document.body.appendChild(b);
    }
    if (document.readyState !== 'loading') ensureDeployBadge();
    else document.addEventListener('DOMContentLoaded', ensureDeployBadge);

    // ---- PAGE TIMING (gated behind LeoDiag) -------------------------------
    // Answers "where did the ten seconds go?" from the device that felt them
    // (Story 3475, 2026-09-10). A few seconds after the page loads, a small
    // card lists what the browser measured for THIS load: time to first paint,
    // DOMContentLoaded, load, when the last request finished, how many
    // requests and bytes, how many were API calls, the slowest ten, and the
    // phone's own view of its network (effective type, round-trip estimate).
    // "Send to Felix" drops the whole report into Reading Nook -> Notifications
    // so it can be read from the desk. Diagnostics off = nothing runs.
    function pageTimingReport() {
        var nav = (performance.getEntriesByType && performance.getEntriesByType('navigation')[0]) || null;
        var res = (performance.getEntriesByType && performance.getEntriesByType('resource')) || [];
        var paint = (performance.getEntriesByType && performance.getEntriesByType('paint')) || [];
        function ms(v) { return Math.round(v || 0); }
        function name(u) { return String(u).replace(location.origin, '').split('?')[0]; }
        var lastEnd = 0, bytes = 0, api = 0;
        res.forEach(function (r) {
            if (r.responseEnd > lastEnd) lastEnd = r.responseEnd;
            bytes += r.transferSize || 0;
            if (String(r.name).indexOf('/api/') >= 0) api++;
        });
        var slow = res.slice().sort(function (a, b) { return b.duration - a.duration; }).slice(0, 10);
        var fcp = paint.filter(function (p) { return p.name === 'first-contentful-paint'; })[0];
        var conn = navigator.connection || {};
        var lines = [];
        lines.push('Page: ' + location.pathname + location.search);
        lines.push('Device: ' + navigator.userAgent);
        lines.push('Network: ' + (conn.effectiveType || '?') + ', rtt ' + (conn.rtt != null ? conn.rtt + ' ms' : '?')
                 + ', downlink ' + (conn.downlink != null ? conn.downlink + ' Mbps' : '?') + (conn.saveData ? ', data saver' : ''));
        if (nav) {
            lines.push('Server wait (first byte): ' + ms(nav.responseStart - nav.requestStart) + ' ms');
            lines.push('HTML arrived: ' + ms(nav.responseEnd) + ' ms');
            lines.push('DOMContentLoaded: ' + ms(nav.domContentLoadedEventEnd) + ' ms');
            lines.push('load: ' + ms(nav.loadEventEnd) + ' ms');
        }
        if (fcp) lines.push('First paint: ' + ms(fcp.startTime) + ' ms');
        lines.push('Last request finished: ' + ms(lastEnd) + ' ms');
        lines.push('Requests: ' + res.length + ' (' + api + ' API), ' + Math.round(bytes / 1024) + ' KB on the wire'
                 + (bytes === 0 ? ' (transfer sizes hidden: cross-origin or cached)' : ''));
        lines.push('Slowest:');
        slow.forEach(function (r) {
            lines.push('  ' + ms(r.duration) + ' ms  start ' + ms(r.startTime) + '  ' + Math.round((r.transferSize || 0) / 1024) + ' KB  ' + name(r.name));
        });
        return lines.join('\n');
    }

    function ensurePageTimingCard() {
        if (!(window.LeoDiag && window.LeoDiag.enabled)) return;
        if (document.getElementById('picassoPageTiming')) return;
        var card = document.createElement('div');
        card.id = 'picassoPageTiming';
        card.setAttribute('data-testid', 'diag-page-timing');
        card.style.cssText =
            'position: fixed !important; left: 8px !important; bottom: 8px !important;' +
            'max-width: calc(100vw - 16px) !important; max-height: 45vh !important; overflow: auto !important;' +
            'z-index: 2147483645 !important; background: rgba(20,20,28,0.94) !important; color: #eee !important;' +
            'font: 11px/1.35 Consolas, monospace !important; padding: 8px 10px !important;' +
            'border-radius: 8px !important; box-shadow: 0 2px 8px rgba(0,0,0,0.4) !important; white-space: pre !important;';
        var text = pageTimingReport();
        var pre = document.createElement('div');
        pre.textContent = text;
        var bar = document.createElement('div');
        bar.style.cssText = 'margin-top: 6px !important; display: flex !important; gap: 8px !important; white-space: normal !important;';
        function btn(label, testid, onClick) {
            var b = document.createElement('button');
            b.type = 'button';
            b.textContent = label;
            b.setAttribute('data-testid', testid);
            b.style.cssText = 'font: 600 12px system-ui, sans-serif !important; padding: 4px 10px !important; border-radius: 6px !important; border: 0 !important; cursor: pointer !important; background: #F1C40F !important; color: #222 !important;';
            b.addEventListener('click', onClick);
            return b;
        }
        bar.appendChild(btn('Send to Felix', 'diag-timing-send', function () {
            if (!(window.LWAuth && window.LWAuth.apiPost)) { pre.textContent = text + '\n\n(no API helper on this page)'; return; }
            window.LWAuth.apiPost('/api/notifications', {
                title:  'Page timing: ' + location.pathname,
                body:   '```\n' + pageTimingReport() + '\n```',
                source: 'LeoDiag'
            }, 'POST').then(function (r) {
                pre.textContent = text + '\n\n' + (r && r.ok ? 'Sent to Reading Nook -> Notifications.' : 'Send failed.');
            });
        }));
        bar.appendChild(btn('Close', 'diag-timing-close', function () {
            try { card.parentNode.removeChild(card); } catch (e) {}
        }));
        card.appendChild(pre);
        card.appendChild(bar);
        document.body.appendChild(card);
    }
    // Wait for the page's own API calls to settle (they start after auth, well
    // after `load`), so the card describes the whole open, not the first half.
    function schedulePageTiming() {
        if (!(window.LeoDiag && window.LeoDiag.enabled)) return;
        setTimeout(ensurePageTimingCard, 4000);
    }
    if (document.readyState === 'complete') schedulePageTiming();
    else window.addEventListener('load', schedulePageTiming);

    // ---- Busy / wait overlay -------------------------------------------------
    //
    // Picasso.busy(label) / Picasso.idle() wraps any noticeable async operation
    // with a centered spinner + dark-tinted backdrop + body.cursor=wait. Calls
    // nest: every busy() increments a counter, every idle() decrements it, and
    // the overlay hides only when the counter reaches zero. The overlay fades
    // in after a 250 ms delay so quick operations (typical 50-200 ms API hops)
    // don't flash a spinner. The label is always the most recent caller's, so
    // a long-running outer "Uploading..." overrides any quick inner work.
    //
    // Wire any flow whose worst-case latency is "more than a heartbeat" -- file
    // upload, file open (blob fetch), PDF render, anything that hits disk on
    // a multi-megabyte payload.

    let busyCount   = 0;
    let busyTimer   = null;
    let busyOverlay = null;

    function ensureBusyOverlay() {
        if (busyOverlay) return busyOverlay;

        const styleTag = document.createElement('style');
        styleTag.textContent =
            'body.picasso-busy, body.picasso-busy * { cursor: wait !important; }' +
            '.picasso-busy-overlay {' +
              'position: fixed; inset: 0;' +
              'background: rgba(0,0,0,0.35);' +
              'display: flex; align-items: center; justify-content: center;' +
              'z-index: 99999;' +
              'opacity: 0; pointer-events: none;' +
              'transition: opacity 180ms ease-out;' +
            '}' +
            '.picasso-busy-overlay.show {' +
              'opacity: 1; pointer-events: auto;' +
            '}' +
            '.picasso-busy-card {' +
              'background: #fff; border-radius: 10px;' +
              'padding: 22px 28px;' +
              'display: flex; align-items: center; gap: 16px;' +
              'box-shadow: 0 6px 24px rgba(0,0,0,0.25);' +
              'font: 600 12pt "Segoe UI", system-ui, -apple-system, sans-serif;' +
              'color: #1F2329; max-width: 86vw; box-sizing: border-box;' +
            '}' +
            '.picasso-busy-spinner {' +
              'width: 26px; height: 26px; flex: 0 0 auto;' +
              'border: 3px solid #E0E0E2; border-top-color: #1A7A3C;' +
              'border-radius: 50%;' +
              'animation: picasso-busy-spin 800ms linear infinite;' +
            '}' +
            '.picasso-busy-label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 320px; }' +
            '@keyframes picasso-busy-spin { to { transform: rotate(360deg); } }';
        document.head.appendChild(styleTag);

        busyOverlay = document.createElement('div');
        busyOverlay.className = 'picasso-busy-overlay';
        busyOverlay.setAttribute('aria-live', 'polite');
        busyOverlay.setAttribute('aria-busy', 'true');
        busyOverlay.innerHTML =
            '<div class="picasso-busy-card">' +
              '<div class="picasso-busy-spinner"></div>' +
              '<span class="picasso-busy-label">Working\u2026</span>' +
            '</div>';
        document.body.appendChild(busyOverlay);
        return busyOverlay;
    }

    Picasso.busy = function (label) {
        busyCount++;
        const overlay = ensureBusyOverlay();
        overlay.querySelector('.picasso-busy-label').textContent = label || 'Working\u2026';
        document.body.classList.add('picasso-busy');
        // Defer the visual show by 250 ms -- fast ops never see the spinner.
        if (busyTimer) return;
        busyTimer = setTimeout(function () {
            busyTimer = null;
            if (busyCount > 0) overlay.classList.add('show');
        }, 250);
    };

    Picasso.idle = function () {
        busyCount = Math.max(0, busyCount - 1);
        if (busyCount > 0) return;
        if (busyTimer) { clearTimeout(busyTimer); busyTimer = null; }
        if (busyOverlay) busyOverlay.classList.remove('show');
        document.body.classList.remove('picasso-busy');
    };

    // ---- Formatting ------------------------------------------------------------

    const fmtCurrency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
    const fmtNumber2  = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const fmtMonthYr  = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' });
    const fmtMonShort = new Intl.DateTimeFormat('en-US', { month: 'short', year: 'numeric' });
    const fmtFullDate = new Intl.DateTimeFormat('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
    const fmtShortDate= new Intl.DateTimeFormat('en-US');

    Picasso.fmtCurrency = function (n) { return n == null ? '' : fmtCurrency.format(n); };
    Picasso.fmtMonthYear= function (d) { return d == null ? '' : fmtMonthYr.format(d); };
    Picasso.fmtShortDate= function (d) { return d == null ? '' : fmtShortDate.format(d); };

    // Build a Date out of an ISO yyyy-mm-dd string. Anchor to local midnight so the
    // calendar day matches the wire value regardless of viewer's timezone.
    Picasso.parseISO = function (s) {
        if (s == null) return null;
        if (s instanceof Date) return s;
        const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
        if (!m) return null;
        return new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10));
    };

    // ---- Search-key port (mirror of cPicassoTableSearch.AppendFormatted) -------
    //
    // For each row, build a single lowercased string that contains every value in
    // every format the user is likely to type -- typing "1500" matches "$1,500.00",
    // typing "March 2026" matches "3/15/2026". The key is computed once per row
    // after fetch and cached on the row's DOM element via dataset.searchKey.

    Picasso.buildSearchKey = function (row, cols) {
        const parts = [];
        for (let i = 0; i < cols.length; i++) {
            const v = row[i];
            if (v == null) continue;
            const t = cols[i].type;
            if (t === 'decimal') {
                const n = Number(v);
                parts.push(n.toFixed(2));                              // "1500.00"
                parts.push(fmtNumber2.format(n));                      // "1,500.00"
                parts.push(fmtCurrency.format(n));                     // "$1,500.00"
                parts.push(String(v));                                 // "1500"
            } else if (t === 'date') {
                const d = Picasso.parseISO(v);
                if (d) {
                    parts.push(fmtShortDate.format(d));                // "3/15/2026"
                    parts.push(fmtFullDate.format(d));                 // "March 15, 2026"
                    parts.push(fmtMonthYr.format(d));                  // "March 2026"
                    parts.push(fmtMonShort.format(d));                 // "Mar 2026"
                    parts.push(String(d.getFullYear()));               // "2026"
                }
            } else if (t === 'int') {
                parts.push(String(v));
            } else {
                parts.push(String(v));
            }
        }
        return parts.join(' ').toLowerCase();
    };

    // ---- 3-state column sort ---------------------------------------------------
    //
    // Click cycle per header: ascending -> descending -> unsorted. Stable.
    // Sort produces a permutation array (originalIndex -> displayPosition); call
    // applyOrder(rowsArray, order) to get the rendered row list.

    Picasso.computeOrder = function (rows, cols, sortColIndex, sortDir) {
        const indexes = rows.map(function (_, i) { return i; });
        if (sortColIndex < 0 || !sortDir) return indexes;
        const t = cols[sortColIndex].type;
        const sgn = sortDir === 'asc' ? 1 : -1;
        const valOf = function (i) { return rows[i][sortColIndex]; };

        indexes.sort(function (a, b) {
            const va = valOf(a), vb = valOf(b);
            // Nulls sink to the bottom regardless of direction.
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            let cmp;
            if (t === 'decimal' || t === 'int') {
                cmp = Number(va) - Number(vb);
            } else if (t === 'date') {
                const da = Picasso.parseISO(va), db = Picasso.parseISO(vb);
                cmp = (da && db) ? (da - db) : 0;
            } else {
                cmp = String(va).localeCompare(String(vb), undefined, { numeric: true, sensitivity: 'base' });
            }
            if (cmp !== 0) return sgn * cmp;
            return a - b;   // stable: fall back to original order
        });
        return indexes;
    };

    Picasso.nextSortDir = function (current) {
        if (current === null || current === undefined) return 'asc';
        if (current === 'asc') return 'desc';
        return null;   // off
    };

    Picasso.sortArrow = function (dir) {
        if (dir === 'asc')  return '\u25B2';
        if (dir === 'desc') return '\u25BC';
        return '';
    };

    // ---- CSV export ------------------------------------------------------------

    Picasso.csvEscape = function (s) {
        if (s == null) return '';
        const t = String(s);
        if (/[",\r\n]/.test(t)) return '"' + t.replace(/"/g, '""') + '"';
        return t;
    };

    Picasso.exportCsv = function (filename, headers, rows) {
        const lines = [];
        lines.push(headers.map(Picasso.csvEscape).join(','));
        for (const r of rows) lines.push(r.map(Picasso.csvEscape).join(','));
        const blob = new Blob([lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 0);
    };

    // ---- Clipboard -------------------------------------------------------------

    Picasso.copyText = function (text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            return navigator.clipboard.writeText(text);
        }
        // Fallback for older browsers that don't expose async clipboard.
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); } catch (e) { /* nothing else to try */ }
        ta.remove();
        return Promise.resolve();
    };

    // ---- Messages and questions (Picasso Dialog, Story 5606) --------------------
    // A message is never dismissed by a timer -- the user closes it, which is the only way
    // we know it was read (Tony, 2026-10-08). Picasso.toast used to fade out after 1.6s;
    // it now opens a Picasso Dialog that waits for OK. picasso-dialog.js is loaded on
    // first use, so every page that loads this file gets it with no extra tag.

    const DIALOG_SRC = '/picasso-dialog.js?v=3';
    let mdialogLoad = null;

    /** Resolves to window.PicassoDialog, loading picasso-dialog.js the first time. */
    Picasso.dialog = function () {
        if (window.PicassoDialog) return Promise.resolve(window.PicassoDialog);
        if (!mdialogLoad) {
            mdialogLoad = new Promise(function (resolve, reject) {
                const s = document.createElement('script');
                s.src = DIALOG_SRC;
                s.onload = function () { window.PicassoDialog ? resolve(window.PicassoDialog) : reject(new Error('picasso-dialog.js did not load')); };
                s.onerror = function () { mdialogLoad = null; reject(new Error('picasso-dialog.js did not load')); };
                (document.head || document.documentElement).appendChild(s);
            });
        }
        return mdialogLoad;
    };

    // Each returns a Promise of the answer -- see picasso-dialog.js for the full list.
    // If the dialog script cannot load, the browser's own alert / confirm stand in: still
    // a message the user must close.
    function viaDialog(name, fallback) {
        return function () {
            const args = arguments;
            return Picasso.dialog().then(
                function (d) { return d[name].apply(d, args); },
                function () { return fallback.apply(null, args); });
        };
    }
    const nativeAlert   = function (text) { window.alert(String(text == null ? '' : text)); return 'ok'; };
    const nativeConfirm = function (text) { return window.confirm(String(text == null ? '' : text)); };

    Picasso.toast         = viaDialog('toast', nativeAlert);
    Picasso.message       = viaDialog('message', nativeAlert);
    Picasso.info          = viaDialog('info', nativeAlert);
    Picasso.success       = viaDialog('success', nativeAlert);
    Picasso.warning       = viaDialog('warning', nativeAlert);
    Picasso.error         = viaDialog('error', nativeAlert);
    Picasso.yesNo         = viaDialog('yesNo', nativeConfirm);
    Picasso.okCancel      = viaDialog('okCancel', nativeConfirm);
    Picasso.confirmDelete = viaDialog('confirmDelete', nativeConfirm);
    Picasso.saveCancel    = viaDialog('saveCancel', function (t) { return nativeConfirm(t) ? 'save' : 'cancel'; });

    // ---- Fetch wrapper ---------------------------------------------------------

    Picasso.fetchJson = async function (url) {
        const res = await fetch(url, { cache: 'no-store' });
        if (!res.ok) {
            let msg = 'HTTP ' + res.status;
            try { const err = await res.json(); if (err && err.error) msg = err.error; } catch { /* body wasn't json */ }
            throw new Error(msg);
        }
        return await res.json();
    };

    Picasso.postJson = async function (url) {
        const res = await fetch(url, { method: 'POST', cache: 'no-store' });
        if (!res.ok) {
            let msg = 'HTTP ' + res.status;
            try { const err = await res.json(); if (err && err.error) msg = err.error; } catch { /* body wasn't json */ }
            throw new Error(msg);
        }
        return await res.json();
    };

    // ---- Tiny markdown renderer (for in-browser help modal) --------------------
    //
    // Supports the subset Leonardo's help docs actually use: # / ## / ### headings,
    // - / * bullet lists, 1. 2. ordered lists, **bold**, *italic*, `code`, ```pre```,
    // [text](url), and pipe tables. Not a full CommonMark implementation -- just
    // enough to render BillTracking.md / Paycheck.md cleanly.

    Picasso.renderMarkdown = function (md) {
        const lines = md.replace(/\r\n/g, '\n').split('\n');
        const out = [];
        let i = 0;

        const escapeHtml = function (s) {
            return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        };
        const inline = function (s) {
            s = escapeHtml(s);
            // code first so the underscore/star inside backticks isn't touched
            s = s.replace(/`([^`]+)`/g, function (_, c) { return '<code>' + c + '</code>'; });
            s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
            s = s.replace(/\*([^*]+)\*/g, '<em>$1</em>');
            s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
            return s;
        };

        const isTableSep = function (line) { return /^\s*\|?\s*[:\-]+(\s*\|\s*[:\-]+)+\s*\|?\s*$/.test(line); };
        const splitRow   = function (line) {
            return line.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map(function (c) { return c.trim(); });
        };

        while (i < lines.length) {
            let line = lines[i];

            // Headings
            const h = /^(#{1,6})\s+(.*)$/.exec(line);
            if (h) {
                const level = h[1].length;
                out.push('<h' + level + '>' + inline(h[2]) + '</h' + level + '>');
                i++;
                continue;
            }

            // Fenced code blocks
            if (/^```/.test(line)) {
                const buf = [];
                i++;
                while (i < lines.length && !/^```/.test(lines[i])) { buf.push(lines[i]); i++; }
                if (i < lines.length) i++; // closing fence
                out.push('<pre><code>' + escapeHtml(buf.join('\n')) + '</code></pre>');
                continue;
            }

            // Pipe tables: header row followed by separator row
            if (/^\s*\|?.+\|.+/.test(line) && i + 1 < lines.length && isTableSep(lines[i + 1])) {
                const header = splitRow(line);
                i += 2;
                const body = [];
                while (i < lines.length && /\|/.test(lines[i]) && lines[i].trim() !== '') {
                    body.push(splitRow(lines[i]));
                    i++;
                }
                let html = '<table><thead><tr>';
                for (const h of header) html += '<th>' + inline(h) + '</th>';
                html += '</tr></thead><tbody>';
                for (const row of body) {
                    html += '<tr>';
                    for (const cell of row) html += '<td>' + inline(cell) + '</td>';
                    html += '</tr>';
                }
                html += '</tbody></table>';
                out.push(html);
                continue;
            }

            // Unordered list
            if (/^\s*[-*]\s+/.test(line)) {
                let html = '<ul>';
                while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
                    html += '<li>' + inline(lines[i].replace(/^\s*[-*]\s+/, '')) + '</li>';
                    i++;
                }
                html += '</ul>';
                out.push(html);
                continue;
            }

            // Ordered list
            if (/^\s*\d+\.\s+/.test(line)) {
                let html = '<ol>';
                while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
                    html += '<li>' + inline(lines[i].replace(/^\s*\d+\.\s+/, '')) + '</li>';
                    i++;
                }
                html += '</ol>';
                out.push(html);
                continue;
            }

            // Blank line -- paragraph break
            if (line.trim() === '') { i++; continue; }

            // Paragraph: gather consecutive non-blank, non-special lines
            const para = [];
            while (i < lines.length && lines[i].trim() !== ''
                   && !/^(#{1,6})\s+/.test(lines[i])
                   && !/^\s*[-*]\s+/.test(lines[i])
                   && !/^\s*\d+\.\s+/.test(lines[i])
                   && !/^```/.test(lines[i])
                   && !(/^\s*\|?.+\|.+/.test(lines[i]) && i + 1 < lines.length && isTableSep(lines[i + 1]))) {
                para.push(lines[i]);
                i++;
            }
            if (para.length) out.push('<p>' + inline(para.join(' ')) + '</p>');
        }

        return out.join('\n');
    };

    // ---- Textarea auto-grow ----------------------------------------------------
    //
    // Resize a textarea to fit its content so a long note doesn't end up behind
    // a scrollbar. Respects the textarea's CSS min-height and caps growth at
    // 60vh so a runaway paste can't push the form's Save button off-screen.
    // A textarea marked data-autosize="full" is never capped -- it shows all
    // its text; its container must scroll and keep Save reachable (the shared
    // edit modal does both -- Story 3490).
    // Idempotent -- safe to call on every keystroke.

    Picasso.autosizeTextarea = function (el) {
        if (!el || el.tagName !== 'TEXTAREA') return;
        // Keep the container's scroll position: collapsing to 'auto' for a
        // moment shrinks the scrolling modal, which would jump it to the top.
        const sc   = el.closest ? el.closest('.ed-modal') : null;
        const keep = sc ? sc.scrollTop : 0;
        el.style.height = 'auto';
        const cs   = getComputedStyle(el);
        const minH = parseFloat(cs.minHeight) || 0;
        const full = el.getAttribute('data-autosize') === 'full';
        const maxH = full ? Infinity : Math.round(window.innerHeight * 0.6);
        const want = Math.max(minH, Math.min(el.scrollHeight, maxH));
        el.style.height = want + 'px';
        el.style.overflowY = (el.scrollHeight > maxH) ? 'auto' : 'hidden';
        if (sc) sc.scrollTop = keep;
    };

    // Delegated input listener -- covers every textarea on every page, including
    // those added dynamically by modals. Use capture so we still fire even if
    // a handler downstream stops propagation.
    document.addEventListener('input', function (ev) {
        const t = ev.target;
        if (t && t.tagName === 'TEXTAREA') Picasso.autosizeTextarea(t);
    }, true);

    // ---- Print-to-PDF picker ---------------------------------------------------
    //
    // Two calling conventions:
    //
    //   Kind/id (per-entity render -- server has a renderer):
    //     Picasso.printPagePdf({ kind: 'paycheck', id: 142, mode: 'detail' })
    //     POSTs /api/pdf/{kind}/{id} with { mode, destination }.
    //
    //   Generic (caller supplies the HTML -- used by Help, future "print this
    //   page" buttons, anything that doesn't have a server-side renderer):
    //     Picasso.printPagePdf({ kind: 'generic', html, filename,
    //                             subject?, body?, title? })
    //     POSTs /api/pdf/generic with { html, filename, destination, subject?, body? }.
    //
    // The picker modal is injected once on first call, then reused. Mirrors the
    // desktop fPdfPrintDestination dialog: Print Only and Display Only sit on
    // top as the ephemeral destinations (no save, no email), then the three
    // persistent destinations with optional "+ open in viewer" variants.

    // The most-recent opts. The picker is a singleton so we just stash it here
    // rather than serializing on the modal.
    let mCurrentOpts = null;

    // Convert HTML5 void elements (<hr>, <br>, <img>, etc.) to self-closing
    // form so openhtmltopdf's strict XHTML parser accepts them. marked.parse
    // (and any innerHTML-scraped browser markup) emits HTML5 short form, and
    // the server rejects it with errors like
    //   "the element <hr> must be terminated by </hr>"
    // Every caller that posts caller-rendered HTML to /api/pdf/generic should
    // run their body through this first.
    Picasso.toXhtmlVoid = function (html) {
        if (html == null) return '';
        return String(html).replace(
            /<(br|hr|img|input|meta|link|area|base|col|embed|param|source|track|wbr)(\s[^>]*?)?\s*\/?>/gi,
            function (_, tag, attrs) {
                return '<' + tag.toLowerCase() + (attrs || '') + '/>';
            }
        );
    };

    // The catch-all "print whatever this page is showing right now" helper.
    // Any page that doesn't have a server-side renderer can wire its Print
    // button to this -- see help.js and paychecks.html for the specialized
    // alternatives.
    //
    // opts:
    //   selector       -- CSS selector for the content to print. Defaults to
    //                    'main' if it exists in the doc, then 'body'.
    //   stripSelector  -- CSS selector for descendants to *remove* from the
    //                    cloned content before sending. Use to drop chrome
    //                    like the .lw-nav, status bars, and hidden modal
    //                    backdrops. Comma-separated list allowed.
    //   title          -- displayed as the PDF's <h1> and the picker title.
    //                    Defaults to document.title (with any " -- LeonardoWeb"
    //                    suffix stripped).
    //   filename       -- suggested download filename. Defaults to
    //                    "Leonardo_<title>.pdf" with non-filename chars replaced.
    //   extraCss       -- optional extra CSS appended to the standard print
    //                    stylesheet.
    Picasso.printThisPage = function (opts) {
        opts = opts || {};
        var selector = opts.selector;
        var el;
        if (selector) {
            el = document.querySelector(selector);
        }
        if (!el) el = document.querySelector('main');
        if (!el) el = document.body;
        if (!el) { Picasso.toast('Print: nothing to print on this page'); return; }

        var rawTitle = opts.title || document.title || 'Echo';
        // "Foo -- LeonardoWeb" -> "Foo"
        rawTitle = rawTitle.replace(/\s*[\u2014\-]\s*LeonardoWeb\s*$/, '');
        var filename = opts.filename || ('Echo_' + rawTitle.replace(/[^A-Za-z0-9_-]/g, '_') + '.pdf');

        // Clone the target so removing descendants for the print doesn't
        // mutate the live DOM. Then drop anything matching stripSelector
        // before snapshotting innerHTML.
        var clone = el.cloneNode(true);
        // cloneNode copies attributes, not what the user (or the page's JS)
        // put in a box, so an edit form printed as a page of empty boxes
        // (Story 3557, Demographics). Pair live and cloned controls BEFORE
        // stripping, while the two trees still line up.
        printFormValues(el, clone);
        if (opts.stripSelector) {
            try {
                var nodes = clone.querySelectorAll(opts.stripSelector);
                for (var i = 0; i < nodes.length; i++) {
                    if (nodes[i].parentNode) nodes[i].parentNode.removeChild(nodes[i]);
                }
            } catch (e) { /* ignore -- bad selector just means nothing stripped */ }
        }
        // Strip comment nodes from the clone before snapshotting. An HTML
        // comment whose body contains "--" (e.g. "<!-- add -- foo -->") is
        // illegal in the XHTML the server-side PDF renderer (openhtmltopdf)
        // parses, and makes the whole render fail with 500 "The string '--' is
        // not permitted within comments". Comments never carry visible print
        // content, so dropping them all is safe and defuses the whole class.
        stripCommentNodes(clone);
        var bodyHtml = clone.innerHTML || '';
        if (!bodyHtml) { Picasso.toast('Print: page is empty'); return; }
        try { bodyHtml = Picasso.toXhtmlVoid(bodyHtml); } catch (e) {
            Picasso.toast('Print: html cleanup failed \u2014 ' + (e && e.message));
            return;
        }

        var css =
            'body{font-family:"Segoe UI","Helvetica Neue",sans-serif;font-size:11pt;' +
            '  line-height:1.55;margin:24px;color:#1a1a1a;}' +
            'h1{color:#0A66C2;border-bottom:2px solid #0A66C2;padding-bottom:6px;' +
            '  margin:0 0 14px;font-size:20pt;}' +
            'h2{color:#1A1A1A;margin:18px 0 6px;font-size:14pt;}' +
            'h3{color:#1A1A1A;margin:14px 0 4px;font-size:12pt;}' +
            'p{margin:8px 0;}' +
            'ul,ol{padding-left:24px;margin:8px 0;}' +
            'li{margin:4px 0;}' +
            'code{background:#F0F0F4;padding:1px 5px;border-radius:3px;' +
            '  font:9.5pt Consolas,monospace;border:1px solid #DEDEE4;}' +
            'pre{background:#F0F0F4;padding:10px 14px;border-radius:6px;' +
            '  border:1px solid #DEDEE4;overflow:auto;margin:12px 0;}' +
            'pre code{background:transparent;border:none;padding:0;}' +
            'table{border-collapse:collapse;margin:12px 0;width:100%;}' +
            'th,td{border:1px solid #DDD;padding:6px 10px;text-align:left;' +
            '  vertical-align:top;}' +
            'th{background:#F5F5F7;}' +
            '.print-value{font-weight:600;}';
        if (opts.extraCss) css += opts.extraCss;

        var html = '<!DOCTYPE html><html><head><meta charset="utf-8"/>' +
            '<title>' + escapeHtmlAttr(rawTitle) + '</title>' +
            '<style>' + css + '</style></head><body>' +
            bodyHtml +
            '</body></html>';

        Picasso.printPagePdf({
            kind:     'generic',
            html:     html,
            filename: filename,
            title:    'Print \u2014 ' + rawTitle,
            subject:  'Echo \u2014 ' + rawTitle,
            body:     'Attached: ' + filename
        });
    };

    // Replace each text-like input, textarea and select in the (detached)
    // clone with a plain span holding the LIVE control's current value, so
    // the PDF shows the answers. Hidden inputs are dropped; buttons,
    // checkboxes, radios and the like are left alone.
    var PRINT_SKIP_TYPES = { hidden: 1, button: 1, submit: 1, reset: 1, image: 1,
                             file: 1, checkbox: 1, radio: 1, range: 1, color: 1 };
    function printFormValues(live, clone) {
        var sel = 'input, textarea, select';
        var a = live.querySelectorAll(sel);
        var b = clone.querySelectorAll(sel);
        if (a.length !== b.length) return;
        for (var i = 0; i < b.length; i++) {
            var src = a[i], dst = b[i], tag = dst.tagName.toLowerCase();
            var type = (dst.getAttribute('type') || 'text').toLowerCase();
            if (!dst.parentNode) continue;
            if (tag === 'input' && type === 'hidden') { dst.parentNode.removeChild(dst); continue; }
            if (tag === 'input' && PRINT_SKIP_TYPES[type]) continue;
            var text;
            if (tag === 'select') {
                var o = src.options && src.selectedIndex >= 0 ? src.options[src.selectedIndex] : null;
                text = o ? o.text : '';
            } else if (type === 'password') {
                text = src.value ? '********' : '';
            } else {
                text = src.value || '';
            }
            var span = document.createElement('span');
            span.className = 'print-value';
            if (tag === 'textarea') span.style.whiteSpace = 'pre-wrap';
            span.textContent = text;
            dst.parentNode.replaceChild(span, dst);
        }
    }

    // Recursively remove Comment nodes (nodeType 8) from a (cloned, detached)
    // subtree in place. Plain DOM recursion -- no TreeWalker / NodeFilter -- so
    // it works on every browser and on a detached clone. See printThisPage for
    // why: the XHTML PDF renderer rejects "--" inside a comment.
    function stripCommentNodes(node) {
        if (!node) return;
        var child = node.firstChild;
        while (child) {
            var next = child.nextSibling;
            if (child.nodeType === 8) {          // Comment
                node.removeChild(child);
            } else if (child.nodeType === 1) {   // Element -- recurse into it
                stripCommentNodes(child);
            }
            child = next;
        }
    }

    function escapeHtmlAttr(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // Drops a Print link into the shared .lw-nav header. The link calls
    // Picasso.printThisPage with sensible defaults: print the page-shell minus
    // the nav itself, status bars, and any hidden modal backdrops. Pages with
    // tighter requirements pass their own opts (typically a narrower
    // `selector` like '#resBody') -- last call wins, so a page can override
    // the auto-install default at any time.
    //
    // Returns true if there's a .lw-nav on the page (link added or its click
    // handler updated), false otherwise.
    // True when the page already supplies its own Print control -- either
    // still sitting in the nav strip, or already relocated into the icon band
    // by quick-launch.js. Auto-installing #lwNavPrint alongside it is what put
    // TWO print icons in the Leonardo Main Menu Tool Bar Strip (Story 2388).
    function navAlreadyHasPrint(nav) {
        if (document.querySelector('[data-lw-band-print]')) return true;
        var els = nav.querySelectorAll('a, button');
        for (var i = 0; i < els.length; i++) {
            if (els[i].id === 'lwNavPrint') continue;
            var letters = (els[i].textContent || '').replace(/[^a-z]/gi, '').toLowerCase();
            if (letters === 'print' || letters === 'printpdf' || letters === 'printtopdf') return true;
        }
        return false;
    }

    Picasso.installPrintInNav = function (opts) {
        var nav = document.querySelector('.lw-nav');
        if (!nav) return false;
        // The page's own Print button wins -- it knows what to print.
        if (!document.getElementById('lwNavPrint') && navAlreadyHasPrint(nav)) return true;

        var defaults = {
            selector:      '.page-shell',
            // script / style MUST be stripped: the cloned body's inline JS
            // contains literal characters that openhtmltopdf's XML parser
            // (SAX) treats as malformed entity references - e.g. the regex
            // `/&/g` looks to SAX like `&/g` which is not a valid entity
            // name. With scripts stripped, the renderer only sees the
            // rendered DOM content, which esc()/escapeHtml already cleaned.
            stripSelector: 'script, style, .lw-nav, .status-bar, .modal-backdrop, #lwNavPrint'
        };

        // A call to installPrintInNav with NO opts is treated as "use the
        // last specified opts if any". This prevents the DOMContentLoaded
        // auto-install from overwriting opts that a page set earlier with
        // a custom selector. Pages can still override later by passing
        // explicit opts.
        var a = document.getElementById('lwNavPrint');
        var stashed = nav._picassoPrintOpts;
        var merged;
        if (opts) {
            merged = {};
            for (var k in defaults) merged[k] = defaults[k];
            for (var k2 in opts) merged[k2] = opts[k2];
            nav._picassoPrintOpts = merged;
        } else if (stashed) {
            merged = stashed;          // honor the page's earlier choice
        } else {
            merged = defaults;
            nav._picassoPrintOpts = merged;
        }

        var fresh = !a;
        if (!a) {
            a = document.createElement('a');
            a.id = 'lwNavPrint';
            a.href = '#';
            a.textContent = 'Print';
            a.title = 'Print this page to PDF';
        }
        // Read merged at click time (closure on `nav` so we always get the
        // latest stash; setView() / page toggles can call installPrintInNav
        // again with fresh opts and the click handler will pick them up).
        a.onclick = function (e) {
            e.preventDefault();
            Picasso.printThisPage(nav._picassoPrintOpts || defaults);
        };
        if (fresh) nav.appendChild(a);
        return true;
    };

    // Auto-install the Print link on every page that uses .lw-nav. Pages
    // without one (help, voice, contacts, vendors, lists, admin) are
    // unaffected. Pages that want a narrower selector can call
    // installPrintInNav({selector: '#resBody'}) any time after this fires --
    // it'll update the same link's click handler in place.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { Picasso.installPrintInNav(); });
    } else {
        // Already past DOMContentLoaded (script was loaded late) -- install now.
        Picasso.installPrintInNav();
    }

    Picasso.printPagePdf = function (opts) {
        if (!opts) { Picasso.toast('Print: no options'); return; }
        if (opts.kind === 'generic') {
            if (!opts.html) { Picasso.toast('Print: missing html'); return; }
        } else {
            if (!opts.kind || !opts.id) { Picasso.toast('Print: missing kind or id'); return; }
        }
        // Mint one idempotency key per picker-open. Every email dispatch in
        // this picker session carries it, so a bad-internet retry (lost
        // response, user taps again) collapses to a single EmailSent row
        // server-side instead of firing a duplicate attachment email.
        // (Story 2068.)
        opts._idemKey = ppUuid();
        mCurrentOpts = opts;
        showPdfPicker(opts);
    };

    // RFC4122-v4 UUID. Uses crypto.randomUUID when available, else a Math.random
    // fallback for older / privacy-mode browsers that strip it.
    function ppUuid() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            var r = (Math.random() * 16) | 0;
            var v = c === 'x' ? r : (r & 0x3) | 0x8;
            return v.toString(16);
        });
    }
    // Shared so other surfaces (e.g. the Reading Nook email buttons) can mint
    // the same kind of idempotency key without duplicating the fallback.
    Picasso.uuid = ppUuid;

    /**
     * Roll Up Tools -- "Roller Blind" in Leonardo Terms (Story 3528; shared Story 3535).
     * A head-row button (holding an .ef-caret and usually the summary) rolls every tool in
     * the pane's .ef-tools up out of the way; the choice is remembered under `key`. The look
     * lives in contacts/entity-files.css (.ef-tools-toggle, .ef-tools-collapsed).
     *
     * Remembered in the DATABASE when the page loads picasso-view-state.js (Tony's rule: nothing
     * durable in localStorage), else on this device as before. PicassoViewState caches under the
     * same key, so a choice made before 2026-10-12 carries straight over.
     *
     *   Picasso.rollup.isCollapsed(key)                  -- for drawing the initial markup
     *   Picasso.rollup.caret(collapsed)                  -- the caret entity for that markup
     *   Picasso.rollup.set(key, pane, toggle, collapsed) -- remember and paint
     *   Picasso.rollup.attach({ key, pane, toggle, onChange }) -- paint now, wire the click, and
     *       repaint once the server's copy (another device's choice) arrives
     */
    Picasso.rollup = (function () {
        function store() { return window.PicassoViewState || null; }
        function isCollapsed(key) {
            if (store()) return store().get(key) === '1';
            try { return localStorage.getItem(key) === '1'; } catch (e) { return false; }
        }
        function caret(collapsed) { return collapsed ? '&#9656;' : '&#9662;'; }
        function paint(pane, toggle, collapsed) {
            pane.classList.toggle('ef-tools-collapsed', collapsed);
            if (!toggle) return;
            toggle.setAttribute('aria-expanded', String(!collapsed));
            var c = toggle.querySelector('.ef-caret');
            if (c) c.innerHTML = caret(collapsed);
        }
        function set(key, pane, toggle, collapsed) {
            if (store()) store().set(key, collapsed ? '1' : '0');
            else { try { localStorage.setItem(key, collapsed ? '1' : '0'); } catch (e) { /* private window: paint only */ } }
            paint(pane, toggle, collapsed);
        }
        function attach(o) {
            paint(o.pane, o.toggle, isCollapsed(o.key));
            o.toggle.addEventListener('click', function () {
                var next = !isCollapsed(o.key);
                set(o.key, o.pane, o.toggle, next);
                if (o.onChange) o.onChange(next);
            });
            if (store()) {
                store().sync(o.key).then(function (changed) {
                    if (!changed) return;
                    paint(o.pane, o.toggle, isCollapsed(o.key));
                    if (o.onChange) o.onChange(isCollapsed(o.key));
                }).catch(function () { /* the device copy stands */ });
            }
        }
        return { isCollapsed: isCollapsed, caret: caret, set: set, attach: attach };
    })();

    function ensurePdfPickerStyles() {
        // Re-inject every time so a CSS revision survives even when the picker
        // singleton was created with the old stylesheet. Cheap and keeps cache-
        // bust pain off the critical path.
        var old = document.getElementById('picassoPdfPickerStyles');
        if (old) old.parentNode.removeChild(old);
        var style = document.createElement('style');
        style.id = 'picassoPdfPickerStyles';
        style.textContent = pdfPickerCss();
        document.head.appendChild(style);
    }

    function pdfPickerCss() {
        // Critical layout (modal-bd position:fixed, sheet position:absolute and
        // dimensions) is set as INLINE styles on the elements themselves at
        // creation time -- see ensurePdfPickerModal. That makes the modal
        // bulletproof against page-level CSS / topic-content interference.
        // This stylesheet only carries cosmetics (colors, hovers, typography)
        // and the button width:100% / text-wrap rules.
        return '.pp-pick-title { font: 600 14pt "Segoe UI",system-ui,sans-serif; margin: 0 0 4px; ' +
                'overflow-wrap: break-word; word-break: break-word; }' +
            '.pp-pick-sub   { font-size: 11px; color: #888; margin: 0 0 12px; ' +
                'overflow-wrap: break-word; }' +
            '.pp-pick-btn   { display: block; width: 100%; box-sizing: border-box; ' +
                'padding: 10px 14px; margin: 5px 0; border-radius: 4px; border: none; ' +
                'cursor: pointer; font: 600 11pt "Segoe UI",system-ui,sans-serif; ' +
                'background: #3C3C3C; color: #fff; text-align: left; ' +
                'white-space: normal; overflow-wrap: break-word; word-break: break-word; }' +
            '.pp-pick-btn:hover  { background: #5A5A5A; }' +
            '.pp-pick-btn.blue   { background: #0A66C2; }' +
            '.pp-pick-btn.blue:hover { background: #0E7AE0; }' +
            '.pp-pick-btn.green  { background: #1A7A3C; }' +
            '.pp-pick-btn.green:hover { background: #236B40; }' +
            '.pp-pick-btn.cancel { background: #fff; color: #333; ' +
                'border: 1px solid #C9C9CC; text-align: center; }' +
            '.pp-pick-btn.cancel:hover { background: #F0F0F2; }' +
            '.pp-pick-btn[disabled] { opacity: 0.5; cursor: wait; }' +
            '.pp-pick-btn-center { text-align: center !important; }' +
            '.pp-pick-hr { border: none; border-top: 1px solid #E0E0E2; margin: 8px 0 6px; }' +
            '.pp-pick-also { font-size: 11px; color: #888; margin: 0 0 4px; }' +
            '.pp-pick-search { display: block; width: 100%; box-sizing: border-box; ' +
                'padding: 9px 12px; margin: 4px 0 8px; border-radius: 4px; ' +
                'border: 1px solid #C9C9CC; font: 400 11pt "Segoe UI",system-ui,sans-serif; }' +
            '.pp-pick-list  { max-height: 320px; overflow-y: auto; margin: 0 0 8px; ' +
                'border: 1px solid #E0E0E2; border-radius: 4px; background: #FAFAFB; }' +
            '.pp-pick-row   { display: block; width: 100%; box-sizing: border-box; ' +
                'padding: 10px 14px; border: none; border-bottom: 1px solid #E8E8EA; ' +
                'background: #fff; text-align: left; cursor: pointer; ' +
                'font: 500 11pt "Segoe UI",system-ui,sans-serif; color: #222; }' +
            '.pp-pick-row:last-child { border-bottom: none; }' +
            '.pp-pick-row:hover { background: #F0F6FF; }' +
            '.pp-pick-row .pp-pick-row-sub { display: block; font-size: 10pt; ' +
                'color: #777; margin-top: 2px; font-weight: 400; }' +
            '.pp-pick-empty { padding: 16px; text-align: center; color: #999; ' +
                'font: 400 11pt "Segoe UI",system-ui,sans-serif; }' +
            '.pp-compose-label { font-size: 10pt; color: #555; font-weight: 600; ' +
                'margin: 8px 0 2px; }' +
            '.pp-compose-to    { padding: 8px 12px; background: #F2F4F8; border-radius: 4px; ' +
                'margin: 2px 0 4px; font: 400 11pt "Segoe UI",system-ui,sans-serif; ' +
                'word-break: break-word; }' +
            '.pp-compose-body  { display: block; width: 100%; box-sizing: border-box; ' +
                'padding: 9px 12px; margin: 2px 0 6px; border-radius: 4px; ' +
                'border: 1px solid #C9C9CC; font: 400 11pt "Segoe UI",system-ui,sans-serif; ' +
                'resize: vertical; }';
    }

    function ensurePdfPickerModal() {
        // Rebuild every time. If a deploy lands new button labels or sheet
        // markup, a reused modal would keep the old HTML -- making "I cleared
        // the cache" advice unreliable. Construction is cheap.
        var existing = document.getElementById('picassoPdfPicker');
        if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
        let m;
        ensurePdfPickerStyles();
        m = document.createElement('div');
        m.id = 'picassoPdfPicker';
        m.className = 'pp-pick-bd';
        // Critical layout pinned via INLINE styles so nothing in the topic
        // content (raw <details> blocks, page-level CSS, ancestor transforms
        // creating new containing blocks) can knock the modal out of fixed
        // positioning or push it into normal flow. `display` is managed
        // separately by show/hide() so we don't fight ourselves with !important.
        m.style.cssText =
            'position: fixed !important;' +
            'top: 0 !important; left: 0 !important;' +
            'right: 0 !important; bottom: 0 !important;' +
            'width: 100vw !important; height: 100vh !important;' +
            'height: 100dvh !important;' +
            'background: rgba(0,0,0,0.5);' +
            'z-index: 2147483600;';   // near max -- beat any topic z-index
        m.style.display = 'none';
        m.innerHTML = renderPickerSheet();
        m.addEventListener('click', function (e) {
            if (e.target === m) { hidePicker(m); return; }
            const btn = e.target.closest('button[data-dest]');
            if (!btn) return;
            const dest = btn.dataset.dest;
            if (!dest) { hidePicker(m); return; }
            if (!mCurrentOpts) { hidePicker(m); return; }
            if (dest === 'email-contact') {
                showContactPickStep(m, mCurrentOpts);
                return;
            }
            if (dest === 'link-to-contact') {
                showLinkContactPickStep(m, mCurrentOpts);
                return;
            }
            if (dest === 'print-server') {
                showPrinterPickStep(m, mCurrentOpts);
                return;
            }
            if (dest === 'back-to-destinations') {
                showDestinationStep(m, mCurrentOpts);
                return;
            }
            // Persisting destinations (save / nook): ask for a base filename
            // first so the file lands under the typed name. Modal pre-selects
            // its contents so typing replaces them on every browser. The
            // link-to-record destination has its own filename prompt inside
            // onLinkContactPicked (it has to happen AFTER the record pick).
            const persistsFile = dest === 'save' || dest === 'nook';
            if (persistsFile) {
                const suggested = stripPdfExt(defaultFilename(mCurrentOpts));
                Picasso.promptRename(suggested, function (cleaned) {
                    if (cleaned == null) return;     // cancelled
                    dispatchPdf(m, mCurrentOpts, dest, { filename: cleaned + '.pdf' });
                });
                return;
            }
            dispatchPdf(m, mCurrentOpts, dest);
        });
        document.body.appendChild(m);

        // Same defensive-inline treatment for the sheet -- see pinSheet.
        pinSheet(m);
        return m;
    }

    /**
     * The Leonardo Print Menu as a picture (Story 3535): the destination sheet's own markup
     * and stylesheet, built by the same functions the live menu uses -- so a picture of the
     * menu can never drift from the menu. Inert: no handlers, nothing added to the page.
     * Returns { html, css }.
     */
    Picasso.printMenuPreview = function () {
        var html = renderPickerSheet().replace('<div class="pp-pick-sheet">',
            '<div class="pp-pick-sheet" style="' + PP_SHEET_LOOK + 'max-width: 520px; margin: 0 auto;">');
        return { html: html, css: pdfPickerCss() };
    };

    function showPicker(m) { m.style.display = 'block'; }
    function hidePicker(m) { m.style.display = 'none'; }

    function renderPickerSheet() {
        return '<div class="pp-pick-sheet">' +
            '<div class="pp-pick-title">Print to PDF</div>' +
            '<div class="pp-pick-sub" id="ppPickSub">Pick a destination</div>' +
            // Echo (Story 5653): the destinations that hand the PDF back to this browser. LeonardoWeb's
            // others -- the home printer, Notifications, the Reading Nook, email, a record -- are house ones.
            '<button class="pp-pick-btn blue" data-dest="print">Print on this device&hellip;</button>' +
            '<button class="pp-pick-btn blue" data-dest="display">Display</button>' +
            '<hr class="pp-pick-hr"/>' +
            '<button class="pp-pick-btn" data-dest="download">Download</button>' +
            '<button class="pp-pick-btn cancel" data-dest="">Cancel</button>' +
            '</div>';
    }

    // Pin the sheet dead-centre of the (fixed) backdrop, both axes (Story
    // 3501 -- it used to hug the bottom edge). translate(-50%,-50%) centres
    // it whatever its height; the 16px side gutter keeps it off the edges on a
    // phone. 100dvh after 100vh so the phone's URL bar does not push the
    // centre below the visible screen; browsers without dvh keep the vh line.
    var PP_SHEET_LOOK =
        'box-sizing: border-box;' +
        'background: #fff;' +
        'border-radius: 12px;' +
        'box-shadow: 0 12px 40px rgba(0,0,0,0.35);' +
        'padding: 18px 16px 22px;';

    function pinSheet(m) {
        var sheet = m.querySelector('.pp-pick-sheet');
        if (!sheet) return;
        sheet.style.cssText =
            'position: absolute !important;' +
            'top: 50% !important; left: 50% !important;' +
            'right: auto !important; bottom: auto !important;' +
            'transform: translate(-50%, -50%) !important;' +
            'margin: 0 !important;' +
            'width: calc(100% - 32px) !important;' +
            'max-width: 520px !important;' +
            PP_SHEET_LOOK +
            'box-sizing: border-box !important;' +
            'max-height: 90vh;' +
            'max-height: 90dvh;' +
            'overflow-y: auto;' +
            'overflow-x: hidden;';
    }

    function showDestinationStep(m, opts) {
        m.innerHTML = renderPickerSheet();
        pinSheet(m);
        const title = opts.title || 'Print to PDF';
        const sub = (opts.kind === 'generic')
            ? 'Pick a destination'
            : ('Pick a destination  \u00B7  mode: ' + (opts.mode || 'detail'));
        m.querySelector('.pp-pick-title').textContent = title;
        m.querySelector('#ppPickSub').textContent = sub;
        const buttons = m.querySelectorAll('button[data-dest]');
        buttons.forEach(function (b) { b.disabled = false; });
    }

    function showPdfPicker(opts) {
        const m = ensurePdfPickerModal();
        showDestinationStep(m, opts);
        showPicker(m);
    }

    // Render step 2: contact search. Loads /api/contacts on demand, lets the
    // user filter by name, then on click either jumps to email pick (if 2+
    // emails) or fires the send immediately (1 email) or toasts (0 emails).
    function showContactPickStep(m, opts) {
        const title = opts.title || 'Print to PDF';
        m.innerHTML =
            '<div class="pp-pick-sheet">' +
            '<div class="pp-pick-title">' + escapeHtml(title) + '</div>' +
            '<div class="pp-pick-sub">Email to contact &middot; pick a contact</div>' +
            '<input type="text" id="ppContactSearch" class="pp-pick-search" ' +
                'placeholder="Type a name&hellip;" autocomplete="off"/>' +
            '<div id="ppContactList" class="pp-pick-list">' +
                '<div class="pp-pick-empty">Loading contacts&hellip;</div>' +
            '</div>' +
            '<button class="pp-pick-btn cancel" data-dest="">Cancel</button>' +
            '</div>';
        pinSheet(m);
        const input = m.querySelector('#ppContactSearch');
        const listEl = m.querySelector('#ppContactList');

        loadContacts().then(function (contacts) {
            renderContactList(listEl, contacts, '', function (c) { onContactPicked(m, opts, c); });
            input.addEventListener('input', function () {
                renderContactList(listEl, contacts, input.value, function (c) { onContactPicked(m, opts, c); });
            });
            // Autofocus only on desktop -- focusing an input on mobile pops the
            // keyboard which feels aggressive when the user just opened the picker.
            if (window.matchMedia && !window.matchMedia('(pointer: coarse)').matches) {
                setTimeout(function () { try { input.focus(); } catch (e) {} }, 50);
            }
        }).catch(function (e) {
            listEl.innerHTML = '<div class="pp-pick-empty">Failed to load contacts</div>';
        });
    }

    // Link-to-record pick step. Searches every kind of record Entity Manager IV
    // holds -- contacts, vendors, matters, solutions, user-defined entities,
    // call logs, ... -- through the same paged, server-side search Entity IV
    // uses (/api/entity-browse, type 0 = all kinds). The saved PDF lands on the
    // picked record's Files tab. The wire destination keeps its historical
    // name 'link-to-contact'; the server links to any EntityID.
    const LINK_PAGE_LINK_TYPE_ID = 220;   // a page link is a bookmark, not a home for files
    const LINK_SEARCH_PAGE_SIZE  = 50;

    function showLinkContactPickStep(m, opts) {
        const title = opts.title || 'Print to PDF';
        m.innerHTML =
            '<div class="pp-pick-sheet">' +
            '<div class="pp-pick-title">' + escapeHtml(title) + '</div>' +
            '<div class="pp-pick-sub">Link to a record &middot; contact, vendor, matter, solution and more</div>' +
            '<input type="text" id="ppLinkSearch" class="pp-pick-search" data-testid="print-link-search" ' +
                'placeholder="Type a name&hellip;" autocomplete="off"/>' +
            '<div id="ppLinkList" class="pp-pick-list" data-testid="print-link-list">' +
                '<div class="pp-pick-empty">Loading records&hellip;</div>' +
            '</div>' +
            '<button class="pp-pick-btn cancel" data-dest="">Cancel</button>' +
            '</div>';
        pinSheet(m);
        const input  = m.querySelector('#ppLinkSearch');
        const listEl = m.querySelector('#ppLinkList');
        const state  = { seq: 0, timer: null };

        const run = function () { searchLinkRecords(m, opts, listEl, input.value, state); };
        input.addEventListener('input', function () {
            clearTimeout(state.timer);
            state.timer = setTimeout(run, 200);
        });
        run();
        if (window.matchMedia && !window.matchMedia('(pointer: coarse)').matches) {
            setTimeout(function () { try { input.focus(); } catch (e) {} }, 50);
        }
    }

    async function searchLinkRecords(m, opts, listEl, needle, state) {
        if (!window.LWAuth) { listEl.innerHTML = '<div class="pp-pick-empty">Auth not ready</div>'; return; }
        const seq = ++state.seq;
        const q = (needle || '').trim();
        const r = await window.LWAuth.apiGet('/api/entity-browse?type=0&includeHealth=true&pageSize=' + LINK_SEARCH_PAGE_SIZE +
            '&q=' + encodeURIComponent(q));
        if (seq !== state.seq) return;     // a newer keystroke took over
        if (!r || !r.ok || !r.data || !Array.isArray(r.data.rows)) {
            listEl.innerHTML = '<div class="pp-pick-empty">' +
                escapeHtml(window.LWAuth.errorText(r, 'Could not search records')) + '</div>';
            return;
        }
        const rows = r.data.rows.filter(function (x) { return x.entityTypeId !== LINK_PAGE_LINK_TYPE_ID; });
        renderLinkRecordList(listEl, rows, Number(r.data.total) || rows.length, q, function (rec) {
            onLinkContactPicked(m, opts, rec);
        });
    }

    function renderLinkRecordList(listEl, rows, total, q, onPick) {
        if (rows.length === 0) {
            listEl.innerHTML = '<div class="pp-pick-empty">' +
                (q ? 'No matching records' : 'No records on file') + '</div>';
            return;
        }
        let html = '';
        for (let i = 0; i < rows.length; i++) {
            const x = rows[i];
            html += '<button class="pp-pick-row" data-testid="print-link-row" data-entity-id="' +
                Number(x.entityId) + '">' + escapeHtml(x.displayName || '(no name)') +
                (x.entityTypeName
                    ? ' <span style="color:#888;font-size:0.85em;">(' + escapeHtml(x.entityTypeName) + ')</span>'
                    : '') +
                '</button>';
        }
        if (total > rows.length) {
            html += '<div class="pp-pick-empty">' + (total - rows.length) +
                ' more&hellip; keep typing to narrow</div>';
        }
        listEl.innerHTML = html;
        listEl.querySelectorAll('.pp-pick-row').forEach(function (row) {
            row.addEventListener('click', function () {
                const id = Number(row.dataset.entityId);
                const rec = rows.find(function (x) { return Number(x.entityId) === id; });
                if (rec) onPick(rec);
            });
        });
    }

    function onLinkContactPicked(m, opts, rec) {
        if (!rec || !rec.entityId) {
            Picasso.error('That record has no entity id, so the PDF cannot be linked to it.');
            return;
        }
        const suggested = stripPdfExt(defaultFilename(opts));
        Picasso.promptRename(suggested, function (cleaned) {
            if (cleaned == null) return;     // user cancelled the filename modal
            dispatchPdf(m, opts, 'link-to-contact', {
                filename:       cleaned + '.pdf',
                entityId:       rec.entityId,
                recipientLabel: (rec.displayName || ('Entity #' + rec.entityId)) +
                                (rec.entityTypeName ? ' (' + rec.entityTypeName + ')' : '')
            });
        });
    }

    let mContactsCache = null;
    async function loadContacts() {
        if (mContactsCache) return mContactsCache;
        if (!window.LWAuth) return [];
        // The print picker only opens this contact list for the Email
        // destination, so filter to contacts that have at least one email
        // address on file (hasEmail=true). Contacts without an email can't
        // receive what we're sending anyway.
        const r = await window.LWAuth.apiGet('/api/contacts?hasEmail=true');
        if (!r || !r.ok || !Array.isArray(r.data)) return [];
        mContactsCache = r.data.slice().sort(function (a, b) {
            const an = (a.fullName || '').toLowerCase();
            const bn = (b.fullName || '').toLowerCase();
            return an < bn ? -1 : an > bn ? 1 : 0;
        });
        return mContactsCache;
    }

    function renderContactList(listEl, contacts, query, onPick) {
        const q = (query || '').trim().toLowerCase();
        const filtered = q
            ? contacts.filter(function (c) {
                const fn = (c.fullName || '').toLowerCase();
                return fn.indexOf(q) !== -1;
              })
            : contacts;
        if (filtered.length === 0) {
            listEl.innerHTML = '<div class="pp-pick-empty">' +
                (q ? 'No matching contacts' : 'No contacts on file') + '</div>';
            return;
        }
        // Cap rendered rows so a 500-contact list doesn't ship 500 buttons of
        // markup every keystroke. The filter keeps narrowing as the user types.
        const cap = 100;
        const shown = filtered.slice(0, cap);
        let html = '';
        for (let i = 0; i < shown.length; i++) {
            const c = shown[i];
            html += '<button class="pp-pick-row" data-contact-id="' +
                Number(c.contactId) + '">' + escapeHtml(c.fullName || '(no name)') + '</button>';
        }
        if (filtered.length > cap) {
            html += '<div class="pp-pick-empty">' + (filtered.length - cap) +
                ' more&hellip; keep typing to narrow</div>';
        }
        listEl.innerHTML = html;
        listEl.querySelectorAll('.pp-pick-row').forEach(function (row) {
            row.addEventListener('click', function () {
                const id = Number(row.dataset.contactId);
                const c = shown.find(function (x) { return Number(x.contactId) === id; });
                if (c) onPick(c);
            });
        });
    }

    async function onContactPicked(m, opts, contact) {
        if (!window.LWAuth) { Picasso.toast('Auth not ready'); return; }
        const listEl = m.querySelector('#ppContactList');
        if (listEl) listEl.innerHTML = '<div class="pp-pick-empty">Loading emails&hellip;</div>';
        const r = await window.LWAuth.apiGet('/api/contacts/' + contact.contactId + '/emails');
        if (!r || !r.ok || !Array.isArray(r.data)) {
            Picasso.toast(window.LWAuth.errorText(r, 'Could not load emails'));
            return;
        }
        const emails = r.data;
        if (emails.length === 0) {
            Picasso.toast('No emails on file for ' + (contact.fullName || 'this contact'));
            // Stay on contact-pick step so user can try another.
            const input = m.querySelector('#ppContactSearch');
            renderContactList(listEl, mContactsCache || [], (input && input.value) || '',
                function (c) { onContactPicked(m, opts, c); });
            return;
        }
        if (emails.length === 1) {
            showComposeStep(m, opts, contact, emails[0], /* backDest */ 'email-contact');
            return;
        }
        showEmailPickStep(m, opts, contact, emails);
    }

    function showEmailPickStep(m, opts, contact, emails) {
        const title = opts.title || 'Print to PDF';
        let listHtml = '';
        for (let i = 0; i < emails.length; i++) {
            const e = emails[i];
            const lab = e.name && e.name.trim() ? (e.name + '  \u00B7  ' + e.address) : e.address;
            listHtml += '<button class="pp-pick-row" data-wa-id="' +
                Number(e.webAddressId) + '">' + escapeHtml(lab) + '</button>';
        }
        m.innerHTML =
            '<div class="pp-pick-sheet">' +
            '<div class="pp-pick-title">' + escapeHtml(title) + '</div>' +
            '<div class="pp-pick-sub">Pick an email for ' + escapeHtml(contact.fullName || 'contact') + '</div>' +
            '<div class="pp-pick-list">' + listHtml + '</div>' +
            '<button class="pp-pick-btn cancel" data-dest="">Cancel</button>' +
            '</div>';
        pinSheet(m);
        m.querySelectorAll('.pp-pick-row').forEach(function (row) {
            row.addEventListener('click', function () {
                const waId = Number(row.dataset.waId);
                const e = emails.find(function (x) { return Number(x.webAddressId) === waId; });
                if (!e) return;
                // Back from compose goes to email-pick when there were multiple
                // options; with a single email the back step has to be the
                // contact picker since there's no email-pick screen in that path.
                showComposeStep(m, opts, contact, e, /* backStep */ {
                    label: 'Back to emails',
                    fn: function () { showEmailPickStep(m, opts, contact, emails); }
                });
            });
        });
    }

    // Defaults used to pre-fill the compose form when the caller didn't supply
    // subject/body via opts. Generic prints usually carry these already; per-
    // kind prints (e.g. paycheck) don't, so we derive something sensible.
    function defaultComposeFields(opts) {
        const filename = (opts.kind === 'generic')
            ? (opts.filename || 'leonardo_print.pdf')
            : (opts.kind + '_' + opts.id + '.pdf');
        const baseSubject = (opts.kind === 'paycheck')
            ? ('Paystub ' + filename.replace(/\.pdf$/i, ''))
            : (opts.kind === 'generic' ? 'Leonardo print' : ('Leonardo: ' + opts.kind));
        const baseBody = 'Attached: ' + filename + '\n\nGenerated by LeonardoWeb.';
        return {
            subject: opts.subject || baseSubject,
            body:    opts.body    || baseBody
        };
    }

    // Step 4: editable email compose. `backStep` was a leftover from the
    // earlier multi-step Back-nav design -- Tony asked to remove the Back
    // button because it was indistinguishable from Cancel. The parameter
    // stays in the signature so callers don't have to change, but it's
    // unused. The bottom button is now always a plain "Cancel" that closes
    // the picker (data-dest="" routes through the main click handler ->
    // hidePicker).
    function showComposeStep(m, opts, contact, email /*, backStep */) {
        const defs = defaultComposeFields(opts);
        const title = opts.title || 'Print to PDF';
        const recipient = emailLabel(contact, email);
        m.innerHTML =
            '<div class="pp-pick-sheet">' +
            '<div class="pp-pick-title">' + escapeHtml(title) + '</div>' +
            '<div class="pp-pick-sub">Review and send</div>' +
            '<div class="pp-compose-label">To</div>' +
            '<div class="pp-compose-to">' + escapeHtml(recipient) + '</div>' +
            '<div class="pp-compose-label">Subject</div>' +
            '<input type="text" id="ppComposeSubject" class="pp-pick-search" ' +
                'value="' + escapeHtml(defs.subject) + '"/>' +
            '<div class="pp-compose-label">Message</div>' +
            '<textarea id="ppComposeBody" class="pp-compose-body" rows="6">' +
                escapeHtml(defs.body) + '</textarea>' +
            '<button class="pp-pick-btn green pp-pick-btn-center" id="ppComposeSend">Send</button>' +
            '<button class="pp-pick-btn cancel" data-dest="">Cancel</button>' +
            '</div>';
        pinSheet(m);
        m.querySelector('#ppComposeSend').addEventListener('click', function (ev) {
            ev.stopPropagation();
            const subj = m.querySelector('#ppComposeSubject').value || '';
            const bod  = m.querySelector('#ppComposeBody').value    || '';
            // External-send guard. Two-step confirm so a thumb-tap on Email
            // when the user meant Link to record doesn't quietly send the
            // file out of Leonardo.
            Picasso.confirmExternalSend({
                recipient:   emailLabel(contact, email),
                subjectHint: subj || (opts.title || 'this document'),
                linkLabel:   'Link to record'
            }, function (ok) {
                if (!ok) return;
                dispatchPdf(m, opts, 'email', {
                    toWebAddressId: email.webAddressId,
                    recipientLabel: emailLabel(contact, email),
                    subject:        subj,
                    body:           bod
                });
            });
        });
    }

    function emailLabel(contact, email) {
        const who = (contact && contact.fullName) ? contact.fullName : '';
        const addr = email && email.address ? email.address : '';
        return who ? (who + ' <' + addr + '>') : addr;
    }

    // Printer-picker step. Tapping a printer is the confirm -- we never silent-
    // send to "the default" without asking, because on a phone you can't see
    // which physical printer is hooked up at home.
    async function showPrinterPickStep(m, opts) {
        const title = opts.title || 'Print to PDF';
        m.innerHTML =
            '<div class="pp-pick-sheet">' +
            '<div class="pp-pick-title">' + escapeHtml(title) + '</div>' +
            '<div class="pp-pick-sub">Pick a printer &middot; tap to send</div>' +
            '<div id="ppPrinterList" class="pp-pick-list">' +
                '<div class="pp-pick-empty">Loading printers&hellip;</div>' +
            '</div>' +
            '<button class="pp-pick-btn cancel" data-dest="">Cancel</button>' +
            '</div>';
        pinSheet(m);
        const listEl = m.querySelector('#ppPrinterList');
        if (!window.LWAuth) { listEl.innerHTML = '<div class="pp-pick-empty">Auth not ready</div>'; return; }
        const r = await window.LWAuth.apiGet('/api/pdf/printers');
        if (!r || !r.ok || !r.data || !Array.isArray(r.data.printers)) {
            listEl.innerHTML = '<div class="pp-pick-empty">Could not load printers</div>';
            return;
        }
        const printers = r.data.printers;
        if (printers.length === 0) {
            listEl.innerHTML = '<div class="pp-pick-empty">No printers visible to the host</div>';
            return;
        }
        let html = '';
        for (let i = 0; i < printers.length; i++) {
            const name = printers[i];
            html += '<button class="pp-pick-row" data-printer="' +
                escapeHtml(name) + '">' + escapeHtml(name) + '</button>';
        }
        listEl.innerHTML = html;
        listEl.querySelectorAll('.pp-pick-row').forEach(function (row) {
            row.addEventListener('click', function () {
                const name = row.dataset.printer;
                if (!name) return;
                dispatchPdf(m, opts, 'print-server', { printerName: name });
            });
        });
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // Build the URL + base body for the server call. Kind/id mode posts to
    // /api/pdf/{kind}/{id} with { mode, destination }; generic mode posts to
    // /api/pdf/generic with { html, filename, destination, subject?, body? }.
    function buildRequest(opts, destination, extra) {
        // extra.subject / extra.body win over opts.subject / opts.body so the
        // user-edited values from the compose form override caller defaults.
        const effSubject = (extra && extra.subject) || opts.subject;
        const effBody    = (extra && extra.body)    || opts.body;
        // extra.filename is the user-renamed file for persist destinations
        // (save / nook). Wins over opts.filename and the per-kind auto name.
        const effFilename = (extra && extra.filename) || opts.filename;
        if (opts.kind === 'generic') {
            const body = {
                html:        opts.html,
                filename:    effFilename || 'leonardo_print.pdf',
                destination: destination
            };
            if (effSubject) body.subject = effSubject;
            if (effBody)    body.body    = effBody;
            if (extra && extra.toWebAddressId) body.toWebAddressId = extra.toWebAddressId;
            if (extra && extra.printerName)    body.printerName    = extra.printerName;
            if (extra && extra.entityId)       body.entityId       = extra.entityId;
            if (destination === 'email' && opts._idemKey) body.idempotencyKey = opts._idemKey;
            return { url: '/api/pdf/generic', body: body };
        }
        const body = { mode: opts.mode || 'detail', destination: destination };
        if (effFilename) body.filename = effFilename;
        if (effSubject)  body.subject  = effSubject;
        if (effBody)     body.body     = effBody;
        if (extra && extra.toWebAddressId) body.toWebAddressId = extra.toWebAddressId;
        if (extra && extra.printerName)    body.printerName    = extra.printerName;
        if (extra && extra.entityId)       body.entityId       = extra.entityId;
        if (destination === 'email' && opts._idemKey) body.idempotencyKey = opts._idemKey;
        return {
            url:  '/api/pdf/' + opts.kind + '/' + opts.id,
            body: body
        };
    }

    function defaultFilename(opts) {
        if (opts.kind === 'generic') return opts.filename || 'leonardo_print.pdf';
        return (opts.kind + '_' + opts.id + '.pdf');
    }

    function stripPdfExt(name) {
        if (!name) return '';
        return /\.pdf$/i.test(name) ? name.replace(/\.pdf$/i, '') : name;
    }

    function sanitizeBasename(s) {
        if (s == null) return '';
        return String(s)
            .replace(/[\\/:*?"<>|\r\n\t]+/g, '_')
            .replace(/\s+/g, ' ')
            .trim()
            .slice(0, 120);
    }

    // Two-step "are you really sure" confirm used for every external send
    // (email-to-contact, email to a free-form address -- anything that
    // leaves Leonardo). Mistakenly tapping Email instead of Link to record
    // would send a private file out of the building, so we ask twice with
    // language that pushes the user toward Link if that's what they meant.
    //
    // opts:
    //   recipient   -- label of who's being sent to (used in step 2)
    //   subjectHint -- short description of what's being sent (e.g. file name
    //                 or message subject)
    //   linkLabel   -- verb shown in step 1, defaults to "Link to record"
    //                 (for the print-PDF picker), can be overridden when the
    //                 calling surface uses a different verb
    //   sendLabel   -- verb in step 2 confirm button, defaults to "Send"
    //
    // cb is invoked with `true` only if the user confirms BOTH steps;
    // otherwise `false`. Two native confirm()s -- same affordance Tony's
    // delete prompts already use, no custom modal needed.
    Picasso.confirmExternalSend = function (opts, cb) {
        opts = opts || {};
        var recipient   = opts.recipient   || 'an external contact';
        var subjectHint = opts.subjectHint || 'this file';
        var linkLabel   = opts.linkLabel   || 'Link to record';
        var step1 = 'Are you sure you want to EMAIL this \u2014 not ' + linkLabel + '?\n\n' +
                    'Link files the document inside Leonardo (on the record\'s ' +
                    'Files tab). Email actually sends it out to ' + recipient + '.\n\n' +
                    'Tap OK to continue with email, Cancel to go back.';
        if (!window.confirm(step1)) { cb(false); return; }

        var step2 = 'WARNING \u2014 outgoing email\n\n' +
                    'You are about to send "' + subjectHint + '" to ' + recipient + '. ' +
                    'This will leave Leonardo and arrive in their mailbox.\n\n' +
                    'Are you ABSOLUTELY sure this is what you want to do?';
        if (!window.confirm(step2)) { cb(false); return; }

        cb(true);
    };

    // Replacement for window.prompt -- reliable text pre-selection on every
    // browser (window.prompt's auto-select is honored on Safari but Chrome
    // for Android leaves the cursor at the end). Invokes cb(string) on
    // confirm or cb(null) on cancel.
    Picasso.promptRename = function (suggested, cb) {
        var bd = document.createElement('div');
        bd.style.cssText =
            'position: fixed; inset: 0; background: rgba(0,0,0,0.5); ' +
            'z-index: 2147483647; display: flex; align-items: center; justify-content: center;';
        bd.innerHTML =
            '<div style="background:#fff;border-radius:8px;padding:20px 22px;max-width:480px;width:92vw;' +
                'box-shadow:0 10px 40px rgba(0,0,0,0.3);box-sizing:border-box">' +
              '<div style="font:600 13pt \'Segoe UI\',system-ui,sans-serif;margin-bottom:6px">Save as</div>' +
              '<div style="font-size:10pt;color:#666;margin-bottom:10px">Base name \u2014 no extension. The .pdf is added for you.</div>' +
              '<input type="text" id="ppRenameInput" autocomplete="off"' +
                ' style="width:100%;padding:10px 12px;font:14px \'Segoe UI\',system-ui,sans-serif;' +
                ' border:1px solid #C9C9CC;border-radius:4px;box-sizing:border-box">' +
              '<div style="display:flex;gap:8px;margin-top:14px;justify-content:flex-end">' +
                '<button type="button" id="ppRenameCancel" style="background:#fff;color:#333;' +
                  'border:1px solid #C9C9CC;border-radius:4px;padding:8px 16px;cursor:pointer;' +
                  'font:11pt \'Segoe UI\',system-ui,sans-serif">Cancel</button>' +
                '<button type="button" id="ppRenameSave" style="background:#1A7A3C;color:#fff;' +
                  'border:none;border-radius:4px;padding:8px 18px;cursor:pointer;' +
                  'font:600 11pt \'Segoe UI\',system-ui,sans-serif">Save</button>' +
              '</div>' +
            '</div>';
        document.body.appendChild(bd);
        var input  = bd.querySelector('#ppRenameInput');
        var btnOk  = bd.querySelector('#ppRenameSave');
        var btnNo  = bd.querySelector('#ppRenameCancel');
        var defaultVal = stripPdfExt(suggested || '');
        input.value = defaultVal;

        function done(val) {
            try { bd.parentNode.removeChild(bd); } catch (e) {}
            cb(val);
        }
        btnOk.addEventListener('click', function () {
            var clean = sanitizeBasename(input.value) || defaultVal;
            done(clean);
        });
        btnNo.addEventListener('click', function () { done(null); });
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter')  { e.preventDefault(); btnOk.click(); }
            if (e.key === 'Escape') { e.preventDefault(); done(null); }
        });
        // Focus + select after the browser has painted the modal -- required
        // for select() to actually take effect on iOS Safari and Chrome Android.
        setTimeout(function () {
            input.focus();
            try { input.setSelectionRange(0, input.value.length); }
            catch (e) { input.select(); }
        }, 60);
    };

    // Pop-up blockers (iOS Safari especially) reject window.open when the
    // call doesn't sit directly inside a user-gesture event handler -- and an
    // `await` between the click and the open breaks that chain. So for any
    // destination that needs a new window we pre-open it SYNCHRONOUSLY before
    // the fetch, then redirect that placeholder once the blob is ready.
    function preOpenViewerWindow() {
        var w;
        try { w = window.open('', '_blank'); } catch (e) { return null; }
        if (!w) return null;
        try {
            w.document.write(
                '<!DOCTYPE html><html><head><title>Loading PDF&hellip;</title>' +
                '<meta name="viewport" content="width=device-width,initial-scale=1"/>' +
                '<style>body{font-family:"Segoe UI",sans-serif;padding:32px;' +
                'color:#666;text-align:center}</style></head>' +
                '<body>Loading PDF&hellip;</body></html>');
            w.document.close();
        } catch (e) { /* OK \u2014 some browsers disallow document.write into about:blank */ }
        return w;
    }

    // Once the blob is ready, point the pre-opened window at it. If we have
    // no pre-opened window (caller didn't pre-open, or the pop-up was blocked
    // when we tried), fall back to a fresh window.open here -- this may itself
    // be blocked, in which case we toast.
    function showBlobInWindow(preOpened, blobUrl, autoPrint) {
        var w = preOpened;
        if (!w) {
            try { w = window.open(blobUrl, '_blank'); } catch (e) { w = null; }
            if (!w) {
                Picasso.toast('Pop-up blocked \u2014 allow pop-ups to view the PDF');
                return false;
            }
        } else {
            // Redirect the placeholder. Use location.replace so the "Loading..."
            // page doesn't litter the back stack.
            try { w.location.replace(blobUrl); }
            catch (e) { try { w.location.href = blobUrl; } catch (e2) {} }
        }
        if (autoPrint) {
            // PDF viewers vary on when onload fires (Chrome/Edge native viewer
            // fires after the inner iframe renders; mobile Safari may never).
            // Best-effort: hook load + a short polling fallback. If neither
            // triggers, the user has the visible PDF and can hit Ctrl+P.
            try {
                w.addEventListener('load', function () {
                    try { w.focus(); w.print(); } catch (e) { /* fall back to manual */ }
                });
            } catch (e) { /* same */ }
            setTimeout(function () {
                try { w.focus(); w.print(); } catch (e) { /* user prints manually */ }
            }, 1200);
        }
        return true;
    }

    async function dispatchPdf(modal, opts, dest, extra) {
        if (!window.LWAuth) { Picasso.toast('Auth not ready'); return; }

        // CRITICAL -- pre-open the viewer window SYNCHRONOUSLY (before any
        // await) so iOS Safari and other strict pop-up blockers see this as
        // a user-gesture action. Any destination that ends up showing the
        // PDF in a new tab gets a placeholder window now; we redirect it
        // once the blob is ready.
        const needsViewer = dest === 'display' || dest === 'print' || dest.endsWith('-open');
        const preOpened = needsViewer ? preOpenViewerWindow() : null;

        const buttons = modal.querySelectorAll('button[data-dest]');
        buttons.forEach(function (b) { b.disabled = true; });

        // PDF render can take several seconds on big reports -- show the
        // global busy spinner so the user sees that something is happening
        // even after the picker modal closes (hidePicker runs in `finally`
        // before the spinner clears).
        const busyLabel =
            dest === 'nook'             ? 'Rendering PDF for Reading Nook\u2026' :
            dest === 'save'             ? 'Rendering & saving PDF\u2026' :
            dest === 'link-to-contact'  ? 'Rendering & linking PDF to record\u2026' :
            dest.startsWith('email')    ? 'Rendering & emailing PDF\u2026' :
            dest === 'print-server'     ? 'Rendering & printing PDF\u2026' :
                                          'Rendering PDF\u2026';
        Picasso.busy(busyLabel);

        try {
            // Print on this device -- fetch the PDF, redirect the pre-opened
            // window at it, then auto-fire the browser's print dialog. This
            // is the native-device print path: print works on whatever device
            // the user is on right now (desktop browser, AirPrint, etc.) and
            // the dialog the system shows is the OS's own. Reliability varies
            // on mobile -- Display + manual print is the fallback. No
            // persistence either way.
            if (dest === 'print') {
                const req = buildRequest(opts, 'download');
                const r = await window.LWAuth.apiPostForBlob(req.url, req.body);
                if (!r || !r.ok) {
                    if (preOpened) try { preOpened.close(); } catch (e) {}
                    Picasso.toast('Print failed' + (r && r.status ? ' (HTTP ' + r.status + ')' : ''));
                    return;
                }
                const blobUrl = URL.createObjectURL(r.blob);
                // No "opened" message -- the viewer opening is the news.
                showBlobInWindow(preOpened, blobUrl, true);
                setTimeout(function () { URL.revokeObjectURL(blobUrl); }, 120000);
                return;
            }

            // Display Only -- fetch the PDF binary, redirect the pre-opened
            // window at it. No persistence: no Leonardo save, no email, no
            // local download. User can hit Ctrl+P / phone share-print from
            // the viewer if they want a paper copy on their device.
            if (dest === 'display') {
                const req = buildRequest(opts, 'download');
                const r = await window.LWAuth.apiPostForBlob(req.url, req.body);
                if (!r || !r.ok) {
                    if (preOpened) try { preOpened.close(); } catch (e) {}
                    Picasso.toast('Display failed' + (r && r.status ? ' (HTTP ' + r.status + ')' : ''));
                    return;
                }
                const blobUrl = URL.createObjectURL(r.blob);
                // No "Opened in viewer" message -- Tony can see it opened.
                showBlobInWindow(preOpened, blobUrl, false);
                setTimeout(function () { URL.revokeObjectURL(blobUrl); }, 120000);
                return;
            }

            // Server-side print is a JSON-response action like save/email but
            // doesn't pair with the "+ open" pattern, so handle it directly.
            // The picker click handler routes 'print-server' to the printer
            // picker first -- by the time we get here, `extra.printerName`
            // should be set with the user's explicit choice.
            if (dest === 'print-server') {
                const req = buildRequest(opts, 'print-server', extra);
                const r = await window.LWAuth.apiPost(req.url, req.body);
                if (preOpened) try { preOpened.close(); } catch (e) {}
                if (!r || !r.ok) {
                    const err = (r && r.data && r.data.error) ? r.data.error : 'Print failed';
                    Picasso.toast(err);
                    return;
                }
                if (r.data && r.data.printed) {
                    const where = extra && extra.printerName ? (' to ' + extra.printerName) : '';
                    Picasso.toast('Sent' + where);
                } else if (r.data && r.data.error) {
                    Picasso.toast('Print: ' + r.data.error);
                } else {
                    Picasso.toast('Print response received');
                }
                return;
            }

            const openAfter = dest.endsWith('-open');
            const baseDest  = openAfter ? dest.slice(0, -5) : dest;

            if (baseDest === 'download') {
                const req = buildRequest(opts, 'download');
                const r = await window.LWAuth.apiPostForBlob(req.url, req.body);
                if (!r || !r.ok) {
                    if (preOpened) try { preOpened.close(); } catch (e) {}
                    Picasso.toast('Print failed' + (r && r.status ? ' (HTTP ' + r.status + ')' : ''));
                    return;
                }
                const blobUrl = URL.createObjectURL(r.blob);
                const filename = r.filename || defaultFilename(opts);
                if (openAfter) {
                    showBlobInWindow(preOpened, blobUrl, false);
                } else {
                    if (preOpened) try { preOpened.close(); } catch (e) {}
                    const a = document.createElement('a');
                    a.href = blobUrl;
                    a.download = filename;
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    Picasso.toast('Downloaded');
                }
                setTimeout(function () { URL.revokeObjectURL(blobUrl); }, openAfter ? 120000 : 0);
                return;
            }

            // save / email -- JSON response, optionally followed by a viewer
            // open via a second download call.
            const req = buildRequest(opts, baseDest, extra);
            const r = await window.LWAuth.apiPost(req.url, req.body);
            if (!r || !r.ok) {
                if (preOpened) try { preOpened.close(); } catch (e) {}
                const err = (r && r.data && r.data.error) ? r.data.error : 'Print failed';
                Picasso.toast(err);
                return;
            }
            if (baseDest === 'save' && r.data && r.data.saved) {
                Picasso.toast('Saved: ' + (r.data.filename || ''));
            } else if (baseDest === 'nook' && r.data && r.data.saved) {
                Picasso.toast('Sent to Reading Nook: ' + (r.data.filename || ''));
            } else if (baseDest === 'notifications' && r.data && r.data.saved) {
                Picasso.toast('Sent to Notifications: ' + (r.data.filename || ''));
            } else if (baseDest === 'link-to-contact' && r.data && r.data.linked) {
                const who = extra && extra.recipientLabel ? extra.recipientLabel : ('entity #' + r.data.entityId);
                Picasso.toast('Linked to ' + who);
            } else if (baseDest === 'email' && r.data && r.data.sent) {
                const who = extra && extra.recipientLabel ? (' to ' + extra.recipientLabel) : '';
                Picasso.toast('Email sent' + who);
            } else if (baseDest === 'email' && r.data && r.data.error) {
                if (preOpened) try { preOpened.close(); } catch (e) {}
                Picasso.toast('Email: ' + r.data.error);
                return;
            } else {
                Picasso.toast('Done');
            }
            if (openAfter) {
                const reqV = buildRequest(opts, 'download');
                const rv = await window.LWAuth.apiPostForBlob(reqV.url, reqV.body);
                if (rv && rv.ok) {
                    const blobUrl = URL.createObjectURL(rv.blob);
                    showBlobInWindow(preOpened, blobUrl, false);
                    setTimeout(function () { URL.revokeObjectURL(blobUrl); }, 120000);
                } else {
                    if (preOpened) try { preOpened.close(); } catch (e) {}
                    Picasso.toast('Could not open PDF for review');
                }
            } else if (preOpened) {
                // Wasn't actually needed (save / email without open-after).
                try { preOpened.close(); } catch (e) {}
            }
        } catch (e) {
            if (preOpened) try { preOpened.close(); } catch (e2) {}
            Picasso.toast('Print failed: ' + (e && e.message ? e.message : 'error'));
        } finally {
            Picasso.idle();
            hidePicker(modal);
        }
    }

    // ---- Escape closes the topmost modal (Tony, 2026-10-05) ----------------------
    // A Leonardo modal closes only by its own buttons or Escape -- never a tap outside.
    // Pages whose show()/hide() toggle a 'show' class register each overlay here as it
    // opens; Escape then hides the most recently opened one that is still showing.
    // Escape is left to an open typeahead list first, so it closes the list, not the form.
    var modalStack = [];
    Picasso.modalOpened = function (el) {
        if (!el) return;
        var i = modalStack.indexOf(el);
        if (i >= 0) modalStack.splice(i, 1);
        modalStack.push(el);
    };
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape' || e.defaultPrevented) return;
        if (document.querySelector('.picker-dropdown.open')) return;
        while (modalStack.length) {
            var top = modalStack[modalStack.length - 1];
            if (top.isConnected && top.classList.contains('show')) {
                top.classList.remove('show');
                modalStack.pop();
                e.preventDefault();
                return;
            }
            modalStack.pop();
        }
    });
})();
