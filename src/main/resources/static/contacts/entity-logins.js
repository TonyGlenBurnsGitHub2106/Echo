/* entity-logins.js -- the Logins tab on record pages (Story 5629).

   Two folding bands under a head row that rolls the tools up:

     On this record      -- the logins linked straight to the record (Add / Link /
                            Edit / Delete, as before).
     From web addresses  -- every login on any of the record's web addresses, each
                            login ONCE however many web addresses share it, with
                            the web addresses that use it as chips.

   Band folds, card folds and the rolled-up tools are remembered in the database
   (PicassoViewState). Fold All folds the bands; cards keep their own state.

   ED (window.LWED) owns the login edit / delete / link / panel actions; this file
   only draws the tab and wires it. Without this file ED.renderLogins falls back to
   its plain card list. */
(function () {
    'use strict';

    const VS_PREFIX = 'leo.logins.';
    const VS_TOOLS  = 'leo.logins.tools';
    const BAND_RECORD = 'record', BAND_WEB = 'web';

    function ED()  { return window.LWED; }
    function esc(s) { return ED().escapeHtml(s == null ? '' : String(s)); }
    function plural(n, one, many) { return n + ' ' + (n === 1 ? one : (many || one + 's')); }
    function caret(open) { return open ? '&#9662;' : '&#9656;'; }

    // ---- remembered state (database, through PicassoViewState) ---------------------

    let _vsReady = null;

    /** Loads picasso-view-state.js once and pulls this tab's keys from the server. */
    function viewStateReady() {
        if (_vsReady) return _vsReady;
        _vsReady = new Promise(function (resolve) {
            function sync() {
                Promise.resolve(window.PicassoViewState.sync(VS_PREFIX))
                    .catch(function () { return false; })
                    .then(function () { resolve(true); });
            }
            if (window.PicassoViewState) { sync(); return; }
            const s = document.createElement('script');
            s.src = '/picasso-view-state.js?v=4';
            s.onload = sync;
            s.onerror = function () { resolve(false); };
            document.head.appendChild(s);
        });
        return _vsReady;
    }

    function bandState(entityId) {
        return window.PicassoGroupState.create({ key: VS_PREFIX + 'bands.' + entityId, defaultOpen: true });
    }
    function cardState(entityId) {
        return window.PicassoGroupState.create({ key: VS_PREFIX + 'cards.' + entityId, defaultOpen: true });
    }

    // ---- markup --------------------------------------------------------------------

    function shellHtml(toolsOpen) {
        return ''
            + '<button type="button" class="ef-tools-toggle" data-el="tools" aria-expanded="' + toolsOpen + '"'
            +   ' title="Show or hide the tools" data-testid="entity-login-tools-toggle">'
            +   '<span class="ef-caret">' + caret(toolsOpen) + '</span>'
            +   '<span class="ef-summary" data-testid="entity-login-summary"></span>'
            + '</button>'
            + '<div class="ef-tools" data-testid="entity-login-tools">'
            +   '<div class="ed-pane-actions ef-actions">'
            +     '<button type="button" class="ed-btn primary" data-el="add" data-testid="entity-login-add-btn">+ Add Login</button>'
            +     '<button type="button" class="ed-btn" data-el="link" data-testid="entity-login-link-btn">+ Link Existing Login</button>'
            +     '<button type="button" class="ed-btn ef-foldall el-foldall" data-el="fold-all"'
            +       ' data-testid="entity-login-fold-all"></button>'
            +   '</div>'
            + '</div>'
            + '<div class="el-bands" data-testid="entity-login-bands"></div>';
    }

    function bandHtml(key, label, count, open, bodyHtml, extraClass) {
        return '<section class="ef-band el-band' + (extraClass ? ' ' + extraClass : '') + (open ? '' : ' collapsed') + '"'
             +   ' data-band="' + key + '" data-testid="entity-login-band-' + key + '">'
             +   '<div class="ef-band-head">'
             +     '<button type="button" class="ef-band-toggle" data-el="band" aria-expanded="' + open + '"'
             +       ' data-testid="entity-login-band-toggle-' + key + '">'
             +       '<span class="ef-caret">' + caret(open) + '</span>'
             +       '<span class="ef-band-label">' + esc(label) + '</span>'
             +       '<span class="ef-band-count">' + esc(count) + '</span>'
             +     '</button>'
             +   '</div>'
             +   '<div class="ef-band-body">' + bodyHtml + '</div>'
             + '</section>';
    }

    /** The head line of a login card -- the part that stays when the card folds. */
    function cardHeadHtml(l, open, extraHtml) {
        return '<div class="head el-card-head" data-el="card" role="button" tabindex="0"'
             +   ' aria-expanded="' + open + '" data-testid="entity-login-card-toggle">'
             +   '<span class="ef-caret">' + caret(open) + '</span>'
             +   '<span class="badge">' + esc(l.owningUser || 'Login') + '</span>'
             +   '<span class="name">' + esc(l.loginName || '(unnamed)') + '</span>'
             +   (extraHtml || '')
             +   '<span class="dates">' + esc(ED().formatDateRange(l.effectiveDate, l.throughDate)) + '</span>'
             + '</div>';
    }

    function credentialsHtml(l) {
        return '<div class="body">'
             +   (l.loginUserName ? '<div class="row">User: <strong>' + esc(l.loginUserName) + '</strong></div>' : '')
             +   (l.password ? '<div class="row">Pass: <span class="pw masked" title="Tap to reveal">'
                              + esc(l.password) + '</span></div>' : '')
             + '</div>'
             + (l.notes ? '<div class="notes">' + esc(l.notes) + '</div>' : '');
    }

    function recordCardHtml(l, idx, cards) {
        const open = cards.isOpen('r' + l.loginSetupId);
        return '<div class="ed-card el-card' + (open ? '' : ' collapsed') + '" data-idx="' + idx + '"'
             +   ' data-card="r' + esc(l.loginSetupId) + '" data-testid="entity-login-row">'
             +   cardHeadHtml(l, open)
             +   credentialsHtml(l)
             +   '<div class="ed-card-actions">'
             +     ED().pinButton('login', l.entityLoginSetupXRefId, l.loginName || '(unnamed)',
                                  l.loginUserName ? 'User: ' + l.loginUserName : '')
             +     ED().loginCopyButtonHtml(l, 'entity-login-copy-btn')
             +     '<button type="button" class="ed-btn" data-el="edit" data-testid="entity-login-edit-btn">Edit</button>'
             +     '<button type="button" class="ed-btn danger" data-el="del" data-testid="entity-login-del-btn">Delete</button>'
             +   '</div>'
             + '</div>';
    }

    function siteHref(w) {
        const url = (w.webAddress || '').trim();
        if (!url) return '';
        if (/^mailto:/i.test(url)) return url;
        if (/^[^\s@\/]+@[^\s@\/]+$/.test(url)) return 'mailto:' + url;
        return /^[a-z][a-z0-9+.-]*:\/\//i.test(url) ? url : 'https://' + url;
    }

    function webChipHtml(w) {
        const label = w.webAddressName || w.webAddress || 'Web address';
        const href = siteHref(w);
        return href
            ? '<a class="el-chip" href="' + esc(href) + '" target="_blank" rel="noopener"'
              + ' title="' + esc(w.webAddress || '') + '" data-testid="entity-login-web-chip">' + esc(label) + '</a>'
            : '<span class="el-chip" data-testid="entity-login-web-chip">' + esc(label) + '</span>';
    }

    function webCardHtml(l, idx, cards) {
        const open = cards.isOpen('w' + l.loginSetupId);
        const was = l.webAddresses || [];
        const alsoHere = l.isOnRecord
            ? '<span class="el-also" title="This login is also linked straight to this record">Also on this record</span>'
            : '';
        return '<div class="ed-card el-card el-web-card' + (open ? '' : ' collapsed') + '" data-idx="' + idx + '"'
             +   ' data-card="w' + esc(l.loginSetupId) + '" data-testid="entity-login-web-row">'
             +   cardHeadHtml(l, open, alsoHere)
             +   credentialsHtml(l)
             +   '<div class="el-uses">'
             +     '<span class="el-uses-label">' + (was.length === 1 ? 'Web address' : plural(was.length, 'web address', 'web addresses')) + '</span>'
             +     was.map(webChipHtml).join('')
             +   '</div>'
             +   '<div class="ed-card-actions">'
             +     ED().loginCopyButtonHtml(l, 'entity-login-web-copy-btn')
             +     '<button type="button" class="ed-btn" data-el="manage" data-testid="entity-login-web-manage-btn">Manage</button>'
             +   '</div>'
             + '</div>';
    }

    // ---- draw ------------------------------------------------------------------------

    function drawSummary(st) {
        const el = st.pane.querySelector('.ef-summary');
        const web = st.webRows == null
            ? 'checking web addresses'
            : plural(st.webRows.length, 'login') + ' from web addresses';
        el.textContent = plural(st.rows.length, 'login') + ' on this record, ' + web;
        // The line leads with the record's own count, so the tab's real total -- each login
        // once, web-address ones included -- rides in data-count for counters such as Entity
        // Manager V's bands. Empty while the web addresses are still being read (2026-10-22:
        // a record whose only logins came from web addresses hid its Logins band).
        el.setAttribute('data-count', st.webRows == null ? '' : String(totalCount(st)));
    }

    function totalCount(st) {
        const fromWeb = (st.webRows || []).filter(function (l) { return !l.isOnRecord; }).length;
        return st.rows.length + fromWeb;
    }

    function drawBands(st) {
        const host = st.pane.querySelector('.el-bands');
        const recordBody = st.rows.length
            ? st.rows.map(function (l, i) { return recordCardHtml(l, i, st.cards); }).join('')
            : '<div class="el-none">No logins linked straight to this record.</div>';
        let webBody, webCount;
        if (st.webRows == null) {
            webBody = '<div class="el-none">Loading...</div>';
            webCount = '...';
        } else if (st.webError) {
            webBody = '<div class="ed-error">' + esc(st.webError) + '</div>';
            webCount = '!';
        } else {
            webBody = st.webRows.length
                ? st.webRows.map(function (l, i) { return webCardHtml(l, i, st.cards); }).join('')
                : '<div class="el-none">None of this record\'s web addresses has a login.</div>';
            webCount = String(st.webRows.length);
        }
        host.innerHTML =
              bandHtml(BAND_RECORD, 'On this record', String(st.rows.length), st.bands.isOpen(BAND_RECORD), recordBody)
            + bandHtml(BAND_WEB, 'From web addresses', webCount, st.bands.isOpen(BAND_WEB), webBody, 'el-band-web');
        drawSummary(st);
        syncFoldAll(st);
        wireBands(st);
    }

    function syncFoldAll(st) {
        const btn = st.pane.querySelector('[data-el="fold-all"]');
        const anyOpen = st.bands.isOpen(BAND_RECORD) || st.bands.isOpen(BAND_WEB);
        btn.innerHTML = anyOpen ? '&#9656;&#9656;' : '&#9662;&#9662;';
        btn.setAttribute('aria-label', anyOpen ? 'Collapse both sections' : 'Expand both sections');
        btn.title = anyOpen ? 'Collapse all' : 'Expand all';
    }

    // ---- wiring ----------------------------------------------------------------------

    function wireShell(st) {
        const pane = st.pane;
        window.Picasso.rollup.attach({
            key: VS_TOOLS, pane: pane, toggle: pane.querySelector('[data-el="tools"]')
        });
        pane.querySelector('[data-el="add"]').addEventListener('click', function () {
            ED().editLogin(st.entityId, null, st.refresh);
        });
        pane.querySelector('[data-el="link"]').addEventListener('click', function () {
            ED().linkLogin(st.entityId, st.refresh);
        });
        pane.querySelector('[data-el="fold-all"]').addEventListener('click', function () {
            if (st.bands.isOpen(BAND_RECORD) || st.bands.isOpen(BAND_WEB)) st.bands.collapseAll();
            else st.bands.expandAll();
            drawBands(st);
        });
    }

    function wireCardFolds(st, bandEl) {
        bandEl.querySelectorAll('[data-el="card"]').forEach(function (head) {
            function toggle(e) {
                // The reveal-on-tap password and the buttons keep their own taps.
                if (e.target.closest('a, button, .pw')) return;
                const card = head.closest('.el-card');
                const open = st.cards.toggle(card.getAttribute('data-card'));
                card.classList.toggle('collapsed', !open);
                head.setAttribute('aria-expanded', String(open));
                head.querySelector('.ef-caret').innerHTML = caret(open);
            }
            head.addEventListener('click', toggle);
            head.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(e); }
            });
        });
        bandEl.querySelectorAll('.pw').forEach(function (el) {
            el.addEventListener('click', function () { el.classList.toggle('masked'); });
        });
    }

    function wireBands(st) {
        st.pane.querySelectorAll('.el-band').forEach(function (bandEl) {
            const key = bandEl.getAttribute('data-band');
            bandEl.querySelector('[data-el="band"]').addEventListener('click', function () {
                st.bands.toggle(key);
                drawBands(st);
            });
            wireCardFolds(st, bandEl);
            const rows = key === BAND_RECORD ? st.rows : (st.webRows || []);
            ED().wireLoginCopyButtons(bandEl, rows);
            bandEl.querySelectorAll('.el-card').forEach(function (card) {
                const l = rows[parseInt(card.getAttribute('data-idx'), 10)];
                if (!l) return;
                const on = function (act, fn) {
                    const b = card.querySelector('[data-el="' + act + '"]');
                    if (b) b.addEventListener('click', fn);
                };
                on('edit',   function () { ED().editLogin(st.entityId, l, st.refresh); });
                on('del',    function () { ED().deleteLogin(st.entityId, l, st.refresh); });
                // Any web address that uses the login opens the same shared login,
                // so the first one's panel edits it. The whole tab re-reads on close:
                // the same login may also sit in On this record.
                on('manage', function () {
                    const w = (l.webAddresses || [])[0];
                    if (w) ED().openLoginPanel(w, st.refresh);
                });
            });
        });
    }

    // ---- data ------------------------------------------------------------------------

    async function loadWeb(st) {
        const seq = ++st.webSeq;
        const r = await window.LWAuth.apiGet('/api/entities/' + st.entityId + '/logins/from-web-addresses');
        // A newer render (or a newer read) has taken the pane -- drop this answer.
        if (seq !== st.webSeq || st.pane._el !== st || !st.pane.isConnected) return;
        st.webError = (r && r.ok) ? '' : window.LWAuth.errorText(r, 'Loading the web address logins failed');
        st.webRows = (r && r.ok && Array.isArray(r.data)) ? r.data : [];
        drawBands(st);
    }

    /** Called by ED.renderLogins with the logins linked straight to the record. */
    async function render(pane, rows, entityId, refresh) {
        entityId = parseInt(entityId, 10) || 0;
        await viewStateReady();
        const prev = pane._el;
        const st = {
            pane: pane, entityId: entityId, refresh: refresh,
            rows: Array.isArray(rows) ? rows : [],
            // Keep the last web list on a refresh of the same record so the band
            // does not flash Loading while it re-reads.
            webRows: prev && prev.entityId === entityId ? prev.webRows : null,
            webError: '', webSeq: prev ? prev.webSeq : 0,
            bands: bandState(entityId), cards: cardState(entityId)
        };
        pane._el = st;
        pane.classList.add('ef-pane', 'el-pane');
        pane.innerHTML = shellHtml(!window.Picasso.rollup.isCollapsed(VS_TOOLS));
        wireShell(st);
        drawBands(st);
        loadWeb(st);
    }

    window.EntityLogins = { render: render };
})();
