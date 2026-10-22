(function () {
    'use strict';

    const Auth = window.LWAuth = {};
    let _token = null;
    // 'jwt' once a real signed-in session is established, 'demo' / 'smoke' for
    // the two escape hatches. Only 'jwt' pages run the expired-session
    // recovery below; in demo/smoke a 403 is always a business rule, never an
    // expiry, and popping a sign-in panel there would be wrong.
    let _authMode = null;

    Auth.ensureAuth = async function () {
        // Smoke / Playwrite-with-service-key escape hatch. The runner's init
        // script sets window.__LEO_SMOKE_AUTH so the X-Leo-Key it injects on
        // every fetch can act as the auth source -- the cookie-based refresh
        // dance is not relevant in that environment. Without this, ensureAuth
        // fails the refresh and bounces to "/" before any page test ID can
        // render. The header still does the real auth on every API call.
        //
        // Returns the truthy sentinel "smoke" instead of null so every page's
        // standard `if (!token) return;` guard short-circuits to FALSE and the
        // page's data-loading path runs. _token stays null so apiGet just
        // sends no Bearer header (X-Leo-Key is enough).
        if (window.__LEO_SMOKE_AUTH) {
            _authMode = 'smoke';
            installIdleWatcher();
            injectLogoutLink();
            injectQuickLaunchScript();
            return 'smoke';
        }
        var t = await refreshOnce(4000);
        if (t) {
            _authMode = 'jwt';
            installIdleWatcher();
            injectLogoutLink();
            injectQuickLaunchScript();
            return _token;
        }
        // In demo mode, SQL Server is stopped so JWT auth cannot work.
        // SecurityConfig opens all /api/** to permitAll; no token needed.
        try {
            var demoRes = await fetch('/api/system/demo/status', { cache: 'no-store' });
            if (demoRes.ok) {
                var demoData = await demoRes.json();
                if (demoData && demoData.demoEnabled) {
                    _authMode = 'demo';
                    injectQuickLaunchScript();
                    return 'demo';
                }
            }
        } catch (_) {}
        // Preserve the page the user was trying to reach so the login screen
        // can bounce them back after they sign in. Critical for PWAs whose
        // start_url is a deep page (e.g. the Stewart PWA opens directly to
        // /conversations/conversations.html).
        const here = window.location.pathname + window.location.search;
        if (here && here !== '/' && !here.startsWith('/?')) {
            window.location.href = '/?next=' + encodeURIComponent(here);
        } else {
            window.location.href = '/';
        }
        return null;
    };

    // ---- Silent token refresh + retry ------------------------------------
    // The access token is short-lived (15 min) and held only in _token. The
    // refresh cookie outlives it -- 14 days by default, but cut down to the
    // Session Inactivity minutes from Global Settings whenever that is set
    // (30 today), so it is NOT a guaranteed safety net for a long-idle page.
    // Re-mint the access token from the
    // refresh cookie so a page left open past the access-token expiry can
    // keep working instead of bouncing to login and losing unsaved edits.
    // Returns the new token string, or null if the refresh cookie is dead
    // (or demo mode, where the endpoint reports not-authenticated).
    // timeoutMs is optional: the first-load call bounds the wait so the login
    // screen isn't held hostage by a slow network; the retry path omits it.
    //
    // A 429 is the server saying "slow down", NOT "you are signed out". It
    // used to fall through to null like any other failure, which put up the
    // login screen in the middle of brisk multi-tab browsing (Story 3494). So
    // a throttled refresh waits for the bucket to refill and asks again.
    var REFRESH_THROTTLE_WAITS_MS = [2000, 4000];

    async function doTokenRefresh(timeoutMs) {
        for (var attempt = 0; ; attempt++) {
            var r = await tryTokenRefresh(timeoutMs);
            if (r !== 'throttled') return r;
            if (attempt >= REFRESH_THROTTLE_WAITS_MS.length) return null;
            await new Promise(function (done) { setTimeout(done, REFRESH_THROTTLE_WAITS_MS[attempt]); });
        }
    }

    // One attempt: the token, null when the session is gone, or 'throttled'.
    async function tryTokenRefresh(timeoutMs) {
        try {
            var opts = { method: 'POST', cache: 'no-store', credentials: 'same-origin' };
            // Always bound the wait. Beyond keeping the login screen
            // responsive, this call is made while holding a cross-tab lock
            // (see below), so an un-timed hang here would freeze refreshes in
            // every other tab too.
            var ac = new AbortController();
            var tid = setTimeout(function () { ac.abort(); }, timeoutMs || 15000);
            opts.signal = ac.signal;
            var res = await fetch('/api/auth/refresh', opts);
            clearTimeout(tid);
            if (res.status === 429) return 'throttled';
            var json = await res.json().catch(function () { return {}; });
            if (res.ok && json.accessToken) { _token = json.accessToken; return _token; }
        } catch (_) {}
        return null;
    }

    // ---- Cross-tab refresh coordination ----------------------------------
    // Every tab of the same browser shares ONE refresh cookie. Each refresh
    // rotates that cookie, and the server treats a token one generation
    // behind as a stolen-token replay -- it kills every session on every
    // device. So two tabs refreshing at the same moment log Tony out of
    // everything, which is exactly what was happening with four or five tabs
    // open (about 202 forced logouts).
    //
    // The per-page single-flight below cannot see across tabs, so it never
    // stood a chance. Two mechanisms fix that:
    //
    //   Web Locks   -- serialize refreshes across every tab, so two rotations
    //                  are never in flight against the same cookie.
    //   BroadcastChannel -- the tab that did refresh hands the resulting
    //                  access token to its siblings, so they skip the network
    //                  entirely instead of queueing up their own rotations.
    //
    // The token is passed in memory only and never written to storage; an
    // access token in localStorage would be readable by any injected script.
    var REFRESH_LOCK_NAME = 'leo-auth-refresh';
    var SHARED_TOKEN_MAX_AGE_MS = 60 * 1000;

    let _sharedToken   = null;
    let _sharedTokenAt = 0;
    let _authChannel   = null;
    let _channelTried  = false;

    function authChannel() {
        if (!_channelTried) {
            _channelTried = true;
            if (typeof BroadcastChannel === 'function') {
                try {
                    _authChannel = new BroadcastChannel('leo-auth');
                    _authChannel.onmessage = function (ev) {
                        var m = ev.data;
                        if (!m || m.type !== 'token' || !m.token) return;
                        // A sibling refreshed. Adopt its token: ours is about
                        // to be stale anyway, and using it saves this tab a
                        // rotation it would otherwise race someone for.
                        _sharedToken   = m.token;
                        _sharedTokenAt = Date.now();
                        _token         = m.token;
                        _authMode      = 'jwt';
                    };
                } catch (_) { _authChannel = null; }
            }
        }
        return _authChannel;
    }

    function publishToken(token) {
        _sharedToken   = token;
        _sharedTokenAt = Date.now();
        var ch = authChannel();
        if (ch) { try { ch.postMessage({ type: 'token', token: token }); } catch (_) {} }
    }

    function recentSharedToken() {
        if (_sharedToken && (Date.now() - _sharedTokenAt) <= SHARED_TOKEN_MAX_AGE_MS) {
            return _sharedToken;
        }
        return null;
    }

    async function coordinatedRefresh(timeoutMs) {
        // A sibling refreshed moments ago and told us. No network at all.
        var shared = recentSharedToken();
        if (shared) { _token = shared; return shared; }

        var locks = (typeof navigator !== 'undefined') ? navigator.locks : null;
        if (!locks || typeof locks.request !== 'function') {
            // No Web Locks (older browser). The per-page single-flight and
            // the server's race-grace window are the remaining protection.
            var plain = await doTokenRefresh(timeoutMs);
            if (plain) publishToken(plain);
            return plain;
        }

        return locks.request(REFRESH_LOCK_NAME, async function () {
            // Re-check inside the lock. While waiting our turn the tab ahead
            // of us may have refreshed and broadcast the result, in which
            // case rotating again would be pure waste.
            var fresh = recentSharedToken();
            if (fresh) { _token = fresh; return fresh; }
            var t = await doTokenRefresh(timeoutMs);
            if (t) publishToken(t);
            return t;
        });
    }

    // Single-flight wrapper around doTokenRefresh. Several API calls on a page
    // routinely fail auth at the same instant (a screen that loads its list,
    // its lookups and its counts in parallel). Each refresh ROTATES the refresh
    // cookie and advances a generation counter server-side, so two refreshes
    // racing on the same cookie make the loser present a stale generation --
    // which the server reads as token replay and poisons the whole chain,
    // killing the session outright. Funnelling every caller through one
    // in-flight promise keeps rotation strictly sequential.
    let _refreshInFlight = null;
    function refreshOnce(timeoutMs) {
        if (_refreshInFlight) return _refreshInFlight;
        _refreshInFlight = coordinatedRefresh(timeoutMs).then(function (t) {
            _refreshInFlight = null;
            return t;
        }, function () {
            _refreshInFlight = null;
            return null;
        });
        return _refreshInFlight;
    }

    // One fetch with the Bearer header attached, plus expired-session recovery.
    //
    // Recovery has two steps and NEVER navigates away, so whatever the user has
    // typed stays on screen:
    //   1. Silent. The access token lives 15 minutes; when it has merely
    //      expired we re-mint it from the refresh cookie and replay the SAME
    //      request. The user sees nothing.
    //   2. Visible but in-place. If the refresh cookie is dead too (the whole
    //      session timed out) we show a sign-in panel over the page, and on a
    //      successful sign-in replay the request. The save then completes and
    //      the form is untouched.
    //
    // Both 401 and 403 are treated as possible auth failures. 403 is included
    // because a genuine business refusal is told apart by the outcome: if the
    // refresh SUCCEEDED and the replay still refuses, the caller really is not
    // allowed to do this, and the response is handed back untouched.
    async function authedFetch(url, opts) {
        opts = opts || {};
        function build() {
            var headers = Object.assign({}, opts.headers || {});
            if (_token) headers['Authorization'] = 'Bearer ' + _token;
            return {
                method: opts.method || 'GET',
                cache: 'no-store',
                credentials: 'same-origin',
                headers: headers,
                body: opts.body
            };
        }
        var res = await fetch(url, build());
        if (!isAuthFailure(res) || _authMode !== 'jwt') return res;

        var refreshed = await refreshOnce();
        if (refreshed) {
            res = await fetch(url, build());
            // Refresh worked, so anything still refused is a real business
            // rule, not an expiry. Hand it back as-is.
            return res;
        }

        var signedIn = await requestReauth();
        if (signedIn) return await fetch(url, build());
        // The user waved the sign-in panel away. Flag it so the wrappers can
        // return null (their long-standing "could not run this" signal, which
        // every page already handles by simply doing nothing) rather than a
        // failed result that pages would mistake for a server error.
        res.__lwAuthExpired = true;
        return res;
    }

    function isAuthFailure(res) {
        return res.status === 401 || res.status === 403;
    }

    // ---- In-place re-sign-in panel ---------------------------------------
    // The whole point of Story 2291: when the session dies while a form is
    // filled in, do NOT bounce to the login page (which throws the work away).
    // Overlay a sign-in panel on the current page instead, and when it
    // succeeds, quietly replay whatever was being saved.
    //
    // Resolves true when the user is signed back in, false if they dismiss it.
    // One shared promise so a burst of failed calls raises exactly one panel.
    let _reauthPromise = null;

    function requestReauth() {
        if (_reauthPromise) return _reauthPromise;
        _reauthPromise = new Promise(function (resolve) {
            buildReauthPanel(function (ok) {
                _reauthPromise = null;
                resolve(ok);
            });
        });
        return _reauthPromise;
    }

    function buildReauthPanel(done) {
        var overlay = document.createElement('div');
        overlay.id = 'lwReauthOverlay';
        overlay.setAttribute('data-testid', 'reauth-overlay');
        overlay.setAttribute('style',
            'position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,.72);' +
            'display:flex;align-items:center;justify-content:center;padding:16px;');

        var card = document.createElement('div');
        card.setAttribute('style',
            'background:#fff;color:#222;border-radius:12px;max-width:420px;width:100%;' +
            'padding:20px;box-shadow:0 10px 40px rgba(0,0,0,.5);' +
            'font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;');

        var h = document.createElement('div');
        h.textContent = 'Session expired';
        h.setAttribute('style', 'font-size:19px;font-weight:700;margin-bottom:6px;');

        var p = document.createElement('div');
        p.textContent = 'Sign in to finish saving. Nothing you have typed has been lost -- '
                      + 'this page stays exactly as it is.';
        p.setAttribute('style', 'font-size:14px;line-height:1.45;margin-bottom:14px;color:#444;');

        var err = document.createElement('div');
        err.setAttribute('style', 'color:#b00020;font-size:13px;min-height:18px;margin-bottom:8px;');

        var btnStyle = 'width:100%;padding:12px;border-radius:8px;border:0;font-size:15px;'
                     + 'font-weight:600;cursor:pointer;margin-bottom:10px;';

        var hasPasskey = false;   // Echo (Story 5653): passwords only -- there are no passkeys to offer

        var passkeyBtn = document.createElement('button');
        passkeyBtn.type = 'button';
        passkeyBtn.textContent = 'Sign in with passkey';
        passkeyBtn.setAttribute('data-testid', 'reauth-passkey-btn');
        passkeyBtn.setAttribute('style', btnStyle + 'background:#1a73e8;color:#fff;');

        // Passkey is the offered way in; the password fields stay hidden behind
        // this link until the user asks for them. Never auto-present a password
        // box when a biometric is available. (Checklist item 1711.)
        var showPassLink = document.createElement('a');
        showPassLink.href = '#';
        showPassLink.textContent = 'Use password instead';
        showPassLink.setAttribute('data-testid', 'reauth-show-password');
        showPassLink.setAttribute('style',
            'display:block;text-align:center;font-size:13px;color:#1a73e8;' +
            'margin-bottom:12px;text-decoration:none;');

        var passArea = document.createElement('div');
        if (hasPasskey) passArea.style.display = 'none';

        var user = document.createElement('input');
        user.type = 'text';
        user.placeholder = 'Username';
        user.autocomplete = 'username';
        user.value = readLastUsername();
        user.setAttribute('data-testid', 'reauth-username');
        user.setAttribute('style',
            'width:100%;padding:11px;border:1px solid #ccc;border-radius:8px;' +
            'font-size:15px;margin-bottom:8px;box-sizing:border-box;');

        var pass = document.createElement('input');
        pass.type = 'password';
        pass.placeholder = 'Password';
        pass.autocomplete = 'current-password';
        pass.setAttribute('data-testid', 'reauth-password');
        pass.setAttribute('style',
            'width:100%;padding:11px;border:1px solid #ccc;border-radius:8px;' +
            'font-size:15px;margin-bottom:10px;box-sizing:border-box;');

        var signInBtn = document.createElement('button');
        signInBtn.type = 'button';
        signInBtn.textContent = 'Sign in';
        signInBtn.setAttribute('data-testid', 'reauth-signin-btn');
        signInBtn.setAttribute('style', btnStyle + 'background:#2e7d32;color:#fff;');

        var laterBtn = document.createElement('button');
        laterBtn.type = 'button';
        laterBtn.textContent = 'Not now';
        laterBtn.setAttribute('data-testid', 'reauth-later-btn');
        laterBtn.setAttribute('style', btnStyle + 'background:#eee;color:#333;margin-bottom:0;');

        function close(ok) {
            if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
            done(ok);
        }
        function busy(on) {
            passkeyBtn.disabled = on;
            signInBtn.disabled  = on;
        }

        passkeyBtn.addEventListener('click', async function () {
            err.textContent = '';
            busy(true);
            var ok = await passkeySignIn(function (m) { err.textContent = m; });
            busy(false);
            if (ok) close(true);
        });

        signInBtn.addEventListener('click', async function () {
            err.textContent = '';
            if (!user.value.trim() || !pass.value) {
                err.textContent = 'Username and password are required.';
                return;
            }
            busy(true);
            var ok = await passwordSignIn(user.value.trim(), pass.value,
                                          function (m) { err.textContent = m; });
            busy(false);
            if (ok) { writeLastUsername(user.value.trim()); close(true); }
        });

        pass.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') signInBtn.click();
        });

        laterBtn.addEventListener('click', function () { close(false); });

        showPassLink.addEventListener('click', function (e) {
            e.preventDefault();
            passArea.style.display = '';
            showPassLink.style.display = 'none';
            user.focus();
        });

        card.appendChild(h);
        card.appendChild(p);
        card.appendChild(err);
        if (hasPasskey) {
            card.appendChild(passkeyBtn);
            card.appendChild(showPassLink);
        }
        passArea.appendChild(user);
        passArea.appendChild(pass);
        passArea.appendChild(signInBtn);
        card.appendChild(passArea);
        card.appendChild(laterBtn);
        overlay.appendChild(card);
        document.body.appendChild(overlay);
        setTimeout(function () { (hasPasskey ? passkeyBtn : pass).focus(); }, 0);
    }

    function readLastUsername() {
        try { return window.localStorage.getItem('lwLastUsername') || ''; } catch (_) { return ''; }
    }
    function writeLastUsername(name) {
        try { window.localStorage.setItem('lwLastUsername', name); } catch (_) {}
    }
    Auth.rememberUsername = writeLastUsername;
    Auth.lastUsername = readLastUsername;

    async function passwordSignIn(username, password, onError) {
        try {
            var res = await fetch('/api/auth/login', {
                method: 'POST',
                cache: 'no-store',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: username, password: password })
            });
            var json = await res.json().catch(function () { return {}; });
            if (res.ok && json.accessToken) { _token = json.accessToken; return true; }
            onError(json.error || 'Sign in failed.');
        } catch (_) {
            onError('Could not reach the server.');
        }
        return false;
    }

    // Passkey re-sign-in. Same handshake the login page uses, minus the
    // dashboard bookkeeping -- all we want back is a fresh access token.
    async function passkeySignIn(onError) {
        try {
            var beginRes = await fetch('/api/auth/webauthn/authenticate/begin', {
                method: 'POST', cache: 'no-store', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' }, body: '{}'
            });
            var opts = await beginRes.json().catch(function () { return {}; });
            if (!beginRes.ok || !opts.challenge) { onError(window.LWAuth.errorText(beginRes, 'Could not start passkey sign in.')); return false; }
            var cred = await navigator.credentials.get({
                publicKey: {
                    challenge: b64UrlToBuf(opts.challenge),
                    rpId: opts.rpId,
                    userVerification: opts.userVerification || 'preferred',
                    timeout: 60000
                },
                mediation: 'optional'
            });
            if (!cred) { onError('Passkey prompt was cancelled.'); return false; }
            var finishRes = await fetch('/api/auth/webauthn/authenticate/finish', {
                method: 'POST', cache: 'no-store', credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionToken:      opts.sessionToken,
                    credentialId:      bufToB64Url(cred.rawId),
                    authenticatorData: bufToB64Url(cred.response.authenticatorData),
                    clientDataJSON:    bufToB64Url(cred.response.clientDataJSON),
                    signature:         bufToB64Url(cred.response.signature),
                    userHandle:        cred.response.userHandle ? bufToB64Url(cred.response.userHandle) : null
                })
            });
            var fin = await finishRes.json().catch(function () { return {}; });
            if (finishRes.ok && fin.accessToken) { _token = fin.accessToken; return true; }
            onError(fin.error || 'Passkey sign in failed.');
        } catch (ex) {
            onError('Passkey error: ' + (ex.message || ex));
        }
        return false;
    }

    function b64UrlToBuf(s) {
        var b64 = s.replace(/-/g, '+').replace(/_/g, '/');
        while (b64.length % 4) b64 += '=';
        var bin = atob(b64);
        var buf = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
        return buf.buffer;
    }

    function bufToB64Url(buf) {
        var bytes = new Uint8Array(buf);
        var bin = '';
        for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
        return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    // ---- Logout link in the top nav --------------------------------------
    // Every authenticated page has either a `.lw-nav` slim breadcrumb bar
    // (calendar, bills, tasks, ...) or a `.ct-header` / `.ed-header` (contacts
    // list, contact detail, vendor detail, ...). Inject a right-aligned Logout
    // link into whichever one is present so the user can sign out from any
    // page without going home first.
    function injectLogoutLink() {
        if (document.getElementById('lwLogoutLink')) return;
        // The icon band (quick-launch.js) now carries a logout icon on every
        // content page, so the text "Logout" link is only needed on pages
        // that opt out of the band (ql-no-pin: the dashboard, which has its
        // own Sign out button, and Manage Quick Launch).
        if (!(document.body && document.body.classList.contains('ql-no-pin'))) return;
        const selectors = ['.lw-nav', '.ct-header', '.ed-header', '.pk-header', '.hp-header', '.qe-header'];
        let nav = null;
        for (const sel of selectors) {
            nav = document.querySelector(sel);
            if (nav) break;
        }
        if (!nav) return;
        // Force flex if needed so margin-left:auto pushes us to the right.
        const cs = window.getComputedStyle(nav);
        if (cs.display !== 'flex') {
            nav.style.display       = 'flex';
            nav.style.alignItems    = 'center';
        }
        const link = document.createElement('a');
        link.id          = 'lwLogoutLink';
        link.href        = '#';
        link.textContent = 'Logout';
        link.style.marginLeft  = 'auto';
        link.style.marginRight = '0';
        link.addEventListener('click', async function (e) {
            e.preventDefault();
            try { await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch (_) {}
            _token = null;
            window.location.href = '/';
        });
        nav.appendChild(link);
    }

    // Expose for pages that bypass ensureAuth (e.g. the dashboard which uses
    // app.js -- once it confirms a session, it can call this.)
    Auth.injectLogoutLink = injectLogoutLink;

    // ---- Quick Launch -- universal "Pin this page" button -----------------
    // Loaded lazily after auth so the script tag isn't a dependency every
    // page has to remember to include. quick-launch.js wires its own
    // DOMContentLoaded handler and injects the Pin Pin button into whichever
    // nav strip is present.
    // ---- Navigation stack -- true Back / Forward (Story 3482) -------------
    // picasso-nav.js keeps the per-tab call stack the icon band's Back and
    // Forward arrows walk. Injected alongside Quick Launch but WITHOUT the
    // ql-no-pin opt-out: a page that hides the Pin star still has to be
    // recorded, or Back skips over it.
    function injectNavScript() {
        if (document.getElementById('lwNavScript')) return;
        var s = document.createElement('script');
        s.id  = 'lwNavScript';
        s.src = '/picasso-nav.js?v=1';
        document.head.appendChild(s);
    }
    Auth.injectNavScript = injectNavScript;

    function injectQuickLaunchScript() {
        injectNavScript();
        if (document.getElementById('lwQuickLaunchScript')) return;
        if (document.body && document.body.classList.contains('ql-no-pin')) return;
        var s = document.createElement('script');
        s.id  = 'lwQuickLaunchScript';
        s.src = '/quick-launch/quick-launch.js?v=46';
        s.defer = true;
        document.head.appendChild(s);
    }
    Auth.injectQuickLaunchScript = injectQuickLaunchScript;

    // ---- Inactivity sign-out ----------------------------------------------
    // Polls /api/system/config once, then watches for user activity.
    // If the configured minutes pass with no activity, calls logout and
    // redirects to "/" (which forces the login screen on next page load).
    // 0 means never time out -- which is what the server answers for a
    // computer by default; a phone gets the strict setting (Story 3494).
    //
    // "No activity" means none in ANY tab of this browser. Every tab shares
    // one session cookie, so a tab that signs out signs them all out. When
    // each tab timed only itself, a tab left in the background for half an
    // hour logged Tony out of the tab he was typing in. Activity is therefore
    // stamped into localStorage (a timestamp, nothing sensitive) and an
    // expiring tab checks that shared stamp before it does anything.

    var IDLE_STAMP_KEY   = 'lwLastActivityMs';
    var IDLE_STAMP_EVERY = 15000;   // write the shared stamp at most this often
    let _idleStampedAt   = 0;

    function stampActivity() {
        var now = Date.now();
        if (now - _idleStampedAt < IDLE_STAMP_EVERY) return;
        _idleStampedAt = now;
        try { window.localStorage.setItem(IDLE_STAMP_KEY, String(now)); } catch (_) {}
    }

    function lastActivityAnywhere() {
        try { return parseInt(window.localStorage.getItem(IDLE_STAMP_KEY), 10) || 0; }
        catch (_) { return 0; }
    }

    let _idleTimerId  = null;
    let _idleMinutes  = 0;
    let _idleInstalled = false;

    function installIdleWatcher() {
        if (_idleInstalled) return;
        _idleInstalled = true;
        fetch('/api/system/config', { cache: 'no-store' })
            .then(function (r) { return r.ok ? r.json() : {}; })
            .catch(function () { return {}; })
            .then(function (cfg) {
                _idleMinutes = Math.max(0, parseInt(cfg.sessionInactivityMinutes || 0, 10));
                if (_idleMinutes <= 0) return; // never time out
                ['mousemove','mousedown','keydown','touchstart','scroll'].forEach(function (ev) {
                    window.addEventListener(ev, resetIdleTimer, { passive: true });
                });
                resetIdleTimer();
            });
    }

    function resetIdleTimer() {
        stampActivity();
        armIdleTimer(_idleMinutes * 60 * 1000);
    }

    function armIdleTimer(ms) {
        if (_idleTimerId) clearTimeout(_idleTimerId);
        if (_idleMinutes <= 0) return;
        _idleTimerId = setTimeout(onIdleExpired, ms);
    }

    async function onIdleExpired() {
        // Another tab may have been in use the whole time. Wait out whatever
        // is left of ITS allowance instead of signing the browser out.
        var remaining = _idleMinutes * 60 * 1000 - (Date.now() - lastActivityAnywhere());
        if (remaining > 1000) { armIdleTimer(remaining); return; }
        try {
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
        } catch (_) {}
        _token = null;
        window.location.href = '/?reason=idle';
    }

    // Hook for app.js (the login page) to start the watcher after first login,
    // since app.js doesn't go through ensureAuth.
    Auth.installIdleWatcher = installIdleWatcher;

    // Allow the dashboard's bespoke auth flow (app.js) to share its token
    // with LWAuth so LWAuth.apiPost / apiGet work from the dashboard too.
    Auth.setToken = function (t) { _token = t; if (t) _authMode = 'jwt'; };

    /** The one refresh path for the whole app. app.js (the dashboard) calls
     *  this rather than fetching /api/auth/refresh itself -- two scripts on
     *  one page firing their own rotations was the second half of the
     *  logged-out-constantly bug. Resolves to the new access token, or null. */
    Auth.refreshToken = async function (timeoutMs) {
        var t = await refreshOnce(timeoutMs);
        if (t) _authMode = 'jwt';
        return t;
    };

    /** Returns { Authorization: 'Bearer <token>' } when a token is present,
     *  or {} otherwise. Lets callers compose their own fetch options
     *  without duplicating the token-bookkeeping in this module. Used by
     *  the clipboard endpoints (text/plain body, not JSON) where apiPost
     *  would force JSON. */
    Auth.bearerHeader = function () {
        return _token ? { 'Authorization': 'Bearer ' + _token } : {};
    };

    /* Story 3486. The server gives a terminal failure an opaque correlation id and one sentence
     * saying what to do with it. Almost every page in LeonardoWeb renders `data.error` and
     * nothing else, so folding the sentence into that string is what makes the id actually reach
     * a human -- the alternative was editing a hundred call sites to read two more fields.
     *
     * Nothing is invented here: if the payload carries no errorId the string is untouched. */
    function withNextAction(json) {
        if (!json || typeof json !== 'object') return json;
        if (!json.errorId || !json.error) return json;
        if (json.nextAction && String(json.error).indexOf(json.errorId) === -1) {
            // Close the first sentence before starting the second, or the two run together as
            // "An error occurred Look up errorId ...".
            var head = String(json.error).replace(/\s+$/, '');
            if (!/[.!?:]$/.test(head)) head += '.';
            json.error = head + ' ' + String(json.nextAction);
        }
        return json;
    }

    // ---- A refusal always says why (Story 3497) ----------------------------
    // Pages report a failed call as (r.data.error || 'Save failed'). That only
    // works when the refusal is LeonardoWeb's own JSON. A request can also be
    // stopped before it ever reaches LeonardoWeb -- by the Cloudflare firewall
    // in front of it, by the tunnel when the service is down, by the network
    // -- and then the body is an HTML page or nothing at all, the JSON parse
    // came back empty, and the user was told 'Save failed' with no reason.
    // Tony lost a note that way and nothing on either side recorded why. So
    // when a refused response carries no error text of its own, one is written
    // here from what the response actually was.
    async function readJsonOrReason(res) {
        var text = await res.text().catch(function () { return ''; });
        var json = {};
        try { json = text ? JSON.parse(text) : {}; } catch (_) { json = {}; }
        if (res.ok) return json;
        if (!json || typeof json !== 'object' || Array.isArray(json)) json = {};
        if (!json.error) json.error = describeRefusal(res, text);
        return json;
    }

    function describeRefusal(res, text) {
        var status = res.status;
        var ray = '';
        try { ray = res.headers.get('cf-ray') || ''; } catch (_) {}
        var fromLeonardo = /^\s*[{[]/.test(text || '');
        var looksLikeFirewall = !fromLeonardo && (status === 403 || status === 429 || status === 503)
            && /cloudflare|attention required|you have been blocked|cf-error|challenge/i.test(text || '');

        if (looksLikeFirewall) {
            return 'Blocked before it reached Echo. Something in front of Echo refused this request'
                 + ' (HTTP ' + status + (ray ? ', Ray ID ' + ray : '') + '), so nothing was saved.'
                 + ' It usually objects to something in the text itself, most often a pasted web address.'
                 + ' Try saving without the links to confirm, then add them back one at a time.';
        }
        if (status === 413) return 'Too large to send (HTTP 413). Nothing was saved. Shorten the text or remove an attachment.';
        if (status === 502 || status === 503 || status === 504 || (status >= 520 && status <= 530)) {
            return 'Echo did not answer (HTTP ' + status + '). The service may be restarting. Nothing was saved. Wait a moment and try again.';
        }
        if (status === 401 || status === 403) return 'Echo refused this as not permitted (HTTP ' + status + '). Nothing was saved.';
        if (status === 404) return 'Echo has no such record or address (HTTP 404). Nothing was saved.';
        if (status === 429) return 'Too many requests in a short time (HTTP 429). Nothing was saved. Wait a few seconds and try again.';
        var hint = String(text || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 140);
        return 'The request was refused with no explanation (HTTP ' + status + '). Nothing was saved.'
             + (hint ? ' The response began: ' + hint : '');
    }

    // fetch() itself rejects when nothing answered at all: offline, DNS, a
    // dropped connection. That used to surface as an unhandled exception.
    function unreachable(method, url) {
        return { ok: false, status: 0, method: method, url: url, data: { error:
            'Could not reach Echo. The connection failed before any answer came back -- the service may be'
            + ' restarting, or the network dropped -- so nothing was saved. Check the network and try again.' } };
    }

    // ---- The whole story of a failed call (Story 5570) ---------------------
    // "Add failed." told Tony nothing: not what the server said, not whether the
    // server was even reached. Every failure message on a page goes through
    // this, so it always says what was being done, why it failed (the server's
    // own words, or that nothing answered), and where: the HTTP status, the
    // address and the errorId to look up in Admin -> Logs.
    //
    //   alert(LWAuth.errorText(r, 'Add failed'))
    //   throw new Error(LWAuth.errorText(r, 'Save failed'))
    //
    // r is what apiGet / apiPost / apiPostForm returned (null when a sign-in was
    // declined). It also works on an ok response that lacked what the page
    // needed. what is the page's own short phrase; a trailing period is dropped.
    Auth.errorText = function (r, what) {
        var head = String(what || 'That did not work').replace(/[\s.]+$/, '');
        if (!r) return head + ': you were asked to sign in again and did not, so nothing was sent.';
        var d = r.data;
        var reason = '';
        if (d && typeof d === 'object') reason = d.error || d.message || '';
        else if (typeof d === 'string') reason = d.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 300);
        if (!reason) {
            reason = r.ok
                ? 'the server answered, but its reply did not have what this page needed'
                : (r.status ? 'the server answered HTTP ' + r.status + ' with no explanation' : 'no answer came back');
        }
        var where = [];
        if (r.status) where.push('HTTP ' + r.status);
        var call = ((r.method || '') + ' ' + (r.url || '')).trim();
        if (call) where.push(call);
        if (d && typeof d === 'object' && d.errorId && String(reason).indexOf(d.errorId) === -1) where.push('errorId ' + d.errorId);
        return head + ': ' + String(reason).replace(/\s+$/, '') + (where.length ? '\n\n(' + where.join(' | ') + ')' : '');
    };

    /** errorText, escaped for innerHTML, with the line breaks kept. For an error drawn into the page. */
    Auth.errorHtml = function (r, what) {
        return String(Auth.errorText(r, what)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/\n/g, '<br>');
    };

    Auth.apiGet = async function (url) {
        let res;
        try { res = await authedFetch(url, { method: 'GET' }); } catch (_) { return unreachable('GET', url); }
        if (res.__lwAuthExpired) return null;   // sign-in declined; stay put
        const json = await readJsonOrReason(res);
        return { ok: res.ok, status: res.status, method: 'GET', url: url, data: withNextAction(json) };
    };

    /** GET that returns the raw response body as text instead of parsing JSON. */
    Auth.apiGetText = async function (url) {
        const res = await authedFetch(url, { method: 'GET' });
        if (res.__lwAuthExpired) return null;   // sign-in declined; stay put
        const text = await res.text().catch(function () { return ''; });
        return { ok: res.ok, status: res.status, data: text };
    };

    Auth.apiPost = async function (url, body, method) {
        const headers = {};
        if (body !== null && body !== undefined) headers['Content-Type'] = 'application/json';
        let res;
        try {
            res = await authedFetch(url, {
                method: method || 'POST',
                headers: headers,
                body: (body !== null && body !== undefined) ? JSON.stringify(body) : undefined
            });
        } catch (_) { return unreachable(method || 'POST', url); }
        if (res.__lwAuthExpired) return null;   // sign-in declined; stay put
        const json = await readJsonOrReason(res);
        return { ok: res.ok, status: res.status, method: method || 'POST', url: url, data: withNextAction(json) };
    };

    /** POST that expects a binary response body (PDF download, etc).
     *  Returns { ok, status, blob, filename } where filename comes from the
     *  Content-Disposition header when present. */
    Auth.apiPostForBlob = async function (url, body) {
        const headers = {};
        if (body !== null && body !== undefined) headers['Content-Type'] = 'application/json';
        let res;
        try {
            res = await authedFetch(url, {
                method: 'POST',
                headers: headers,
                body: (body !== null && body !== undefined) ? JSON.stringify(body) : undefined
            });
        } catch (_) { return Object.assign(unreachable('POST', url), { blob: null, filename: null }); }
        if (res.__lwAuthExpired) return null;   // sign-in declined; stay put
        // Story 5570: a refused download carries the server's reason, so errorText can say it.
        if (!res.ok) return { ok: false, status: res.status, method: 'POST', url: url, blob: null, filename: null,
                              data: withNextAction(await readJsonOrReason(res)) };
        const blob = await res.blob();
        let filename = null;
        const cd = res.headers.get('Content-Disposition') || '';
        const m = /filename="([^"]+)"/.exec(cd);
        if (m) filename = m[1];
        return { ok: true, status: res.status, blob: blob, filename: filename };
    };

    /** POST with a `FormData` body -- for multipart file uploads.
     *  Do NOT set Content-Type; the browser fills it with the boundary
     *  string. Returns the same { ok, status, data } shape as apiPost. */
    Auth.apiPostForm = async function (url, formData) {
        let res;
        try { res = await authedFetch(url, { method: 'POST', body: formData }); } catch (_) { return unreachable('POST', url); }
        if (res.__lwAuthExpired) return null;   // sign-in declined; stay put
        // Story 5570: a refused upload says why, the same way apiPost does.
        const json = await readJsonOrReason(res);
        return { ok: res.ok, status: res.status, method: 'POST', url: url, data: withNextAction(json) };
    };

    /** GET that expects a binary response body (file download / inline view).
     *  Mirrors apiPostForBlob -- used by the Reading Nook to fetch user-dropped
     *  files with the JWT header attached, since browser-driven tab navigation
     *  can't add Authorization headers. Returns { ok, status, blob, filename,
     *  contentType }. */
    Auth.apiGetForBlob = async function (url) {
        let res;
        try { res = await authedFetch(url, { method: 'GET' }); }
        catch (_) { return Object.assign(unreachable('GET', url), { blob: null, filename: null, contentType: null }); }
        if (res.__lwAuthExpired) return null;   // sign-in declined; stay put
        if (!res.ok) return { ok: false, status: res.status, method: 'GET', url: url, blob: null, filename: null,
                              contentType: null, data: withNextAction(await readJsonOrReason(res)) };
        const blob = await res.blob();
        let filename = null;
        const cd = res.headers.get('Content-Disposition') || '';
        const m = /filename="([^"]+)"/.exec(cd);
        if (m) filename = m[1];
        return {
            ok: true, status: res.status, blob: blob, filename: filename,
            contentType: res.headers.get('Content-Type')
        };
    };
})();
