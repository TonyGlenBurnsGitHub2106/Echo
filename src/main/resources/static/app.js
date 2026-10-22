/* Echo (Story 5653): LeonardoWeb's app.js -- the sign-in card and the main menu -- less the
   passkey sign-in, the landing page and demo mode, which Echo does not have. */
(function () {
    'use strict';

    let accessToken = null;

    // ---- API helpers -----------------------------------------------------------

    async function apiPost(url, body) {
        const opts = { method: 'POST', cache: 'no-store', credentials: 'same-origin' };
        if (body !== undefined) {
            opts.headers = { 'Content-Type': 'application/json' };
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(url, opts);
        const json = await res.json().catch(function () { return {}; });
        return { ok: res.ok, status: res.status, data: json };
    }

    async function apiGet(url) {
        const headers = {};
        if (accessToken) headers['Authorization'] = 'Bearer ' + accessToken;
        const res = await fetch(url, { cache: 'no-store', credentials: 'same-origin', headers: headers });
        const json = await res.json().catch(function () { return {}; });
        return { ok: res.ok, status: res.status, data: json };
    }

    // ---- Auth ------------------------------------------------------------------

    // Delegate to auth.js instead of fetching /api/auth/refresh ourselves.
    // This page loads BOTH scripts, so two independent refreshes used to fire
    // on every dashboard load. Each one rotates the shared refresh cookie, and
    // the loser of that race arrived a generation behind -- which the server
    // read as a stolen token and answered by killing every session on every
    // device. Going through LWAuth means one refresh per browser, coordinated
    // across tabs as well as across the two scripts on this page.
    async function tryRefresh() {
        if (!(window.LWAuth && window.LWAuth.refreshToken)) return false;
        const token = await window.LWAuth.refreshToken(4000);
        if (!token) return false;
        accessToken = token;
        scheduleRefresh();
        if (window.LWAuth.installIdleWatcher) window.LWAuth.installIdleWatcher();
        return true;
    }

    function scheduleRefresh() {
        setTimeout(async function () {
            const ok = await tryRefresh();
            if (!ok) showLogin('Session expired \u2014 please sign in again.');
        }, 14 * 60 * 1000);
    }

    // ---- Post-auth redirect ----------------------------------------------------
    // When a deep page (e.g. /conversations/conversations.html, the Stewart
    // PWA start URL) was the user's original destination, auth.js's ensureAuth
    // sets ?next=<path> on the redirect to "/". After a successful sign-in we
    // honor that. Only same-origin paths are accepted to prevent open-redirect.
    function safeNextPath() {
        const next = new URLSearchParams(window.location.search).get('next');
        if (!next) return null;
        if (!next.startsWith('/')) return null;     // require absolute path
        if (next.startsWith('//')) return null;     // protocol-relative URL
        if (next.startsWith('/\\')) return null;    // backslash-escape trick
        return next;
    }

    // Neither the sign-in card nor the main menu may stay on screen while we
    // decide where to go -- whichever is showing reads as "it went to the wrong
    // page first" right before the jump. Park on a plain Opening screen.
    function showOpening(label) {
        const login = document.getElementById('loginView');
        const dash  = document.getElementById('dashView');
        const open  = document.getElementById('openingView');
        if (login) login.style.display = 'none';
        if (dash)  dash.style.display  = 'none';
        if (!open) return;
        const text = document.getElementById('openingText');
        if (text) text.textContent = label ? ('Opening ' + label + '\u2026') : 'Opening\u2026';
        open.style.display = 'flex';
    }

    async function handlePostAuth(pingData, freshLogin) {
        const next = safeNextPath();
        if (next) {
            showOpening('');
            window.location.replace(next);
            return;
        }
        showDashboard(pingData);
    }

    // ---- Views -----------------------------------------------------------------

    function hideOpening() {
        const open = document.getElementById('openingView');
        if (open) open.style.display = 'none';
    }

    function showLogin(errorMsg) {
        hideOpening();
        document.getElementById('dashView').style.display  = 'none';
        document.getElementById('loginView').style.display = 'flex';
        document.getElementById('loginError').textContent = errorMsg || '';
        document.getElementById('password').value = '';
        const user = document.getElementById('username');
        user.value = (window.LWAuth && window.LWAuth.lastUsername) ? (window.LWAuth.lastUsername() || '') : '';
        setTimeout(function () { (user.value ? document.getElementById('password') : user).focus(); }, 30);
    }

    function showDashboard(pingData) {
        hideOpening();
        document.getElementById('loginView').style.display = 'none';
        document.getElementById('dashView').style.display  = 'flex';
        var headerUser = document.getElementById('headerUser');
        if (headerUser) headerUser.textContent = pingData.username;
    }

    function renderPing(data) {
        // The server-time line was wasting real estate on the dashboard. The
        // Refresh button confirms success with a toast instead; keep this a
        // no-op (clear any prior content) so nothing is shown here.
        var el = document.getElementById('pingResult');
        if (el) el.innerHTML = '';
    }

    // ---- Handlers --------------------------------------------------------------

    async function onLogin(e) {
        if (e) e.preventDefault();
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        const btn      = document.getElementById('loginBtn');
        const err      = document.getElementById('loginError');

        if (!username || !password) { err.textContent = 'Enter username and password.'; return; }

        btn.disabled = true;
        err.textContent = '';

        try {
            const r = await apiPost('/api/auth/login', { username: username, password: password });
            if (r.ok && r.data.accessToken) {
                accessToken = r.data.accessToken;
                scheduleRefresh();
                if (window.LWAuth && window.LWAuth.installIdleWatcher) window.LWAuth.installIdleWatcher();
                if (window.LWAuth && window.LWAuth.setToken) window.LWAuth.setToken(accessToken);
                // Remembered so the in-place "session expired" panel on any
                // other page can pre-fill the username instead of making the
                // user retype it mid-save. (Story 2291.)
                if (window.LWAuth && window.LWAuth.rememberUsername) window.LWAuth.rememberUsername(username);
                const ping = await apiGet('/api/ping');
                if (ping.ok) {
                    await handlePostAuth(ping.data, true);
                } else {
                    err.textContent = 'Signed in but server check failed (HTTP ' + ping.status + ').';
                }
            } else {
                err.textContent = r.data.error || 'Sign-in failed. Check your username and password.';
            }
        } catch (ex) {
            err.textContent = 'Error: ' + ex.message;
        } finally {
            btn.disabled = false;
        }
    }

    async function onLogout() {
        await apiPost('/api/auth/logout');
        accessToken = null;
        showLogin();
    }

    async function onRefreshPing() {
        const btn = document.getElementById('pingBtn');
        btn.disabled = true;
        const r = await apiGet('/api/ping');
        if (r.ok) {
            renderPing(r.data);
            Picasso.toast('Refreshed');
        } else if (r.status === 401) {
            showLogin('Session expired \u2014 please sign in again.');
        }
        btn.disabled = false;
    }

    // ---- Init ------------------------------------------------------------------

    async function init() {
        const refreshed = await tryRefresh();
        if (refreshed) {
            const ping = await apiGet('/api/ping');
            if (ping.ok) { await handlePostAuth(ping.data, false); return; }
        }
        const params = new URLSearchParams(window.location.search);
        const reason = params.get('reason');
        const msg = (reason === 'idle')
            ? 'You were signed out after a period of inactivity. Please sign in again.'
            : '';
        showLogin(msg);
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.getElementById('loginBtn').addEventListener('click', onLogin);
        document.getElementById('logoutBtn').addEventListener('click', onLogout);
        document.getElementById('pingBtn').addEventListener('click', onRefreshPing);
        document.getElementById('password').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') onLogin(e);
        });
        document.getElementById('username').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); document.getElementById('password').focus(); }
        });
        init();
    });
})();
