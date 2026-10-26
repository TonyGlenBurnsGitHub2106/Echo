/*
 * picasso-linked.js -- the Linked Entities window, for ANY record, from any row.
 *
 * "What is this linked to?" for a web address, a phone, a note, a file, a list,
 * a matter, a call log ... not only for the contact or vendor a page is about
 * (2026-10-11; the window itself began on Entity Manager IV, Story 2431).
 *
 * Two ways in, because a phone has no right-click:
 *
 *   1. A visible "Linked" button on the row -- PicassoLinked.button(kind, id).
 *      Record-page rows get it beside their Pin button (contacts/entity-detail.js).
 *   2. Right-click (or a long press on a phone) anywhere on a row -> the shared
 *      Leonardo Context Menu (picasso-context-menu.js, load it first) with
 *      "Linked Entities". The row
 *      is found from the click: the nearest element holding exactly one Linked
 *      button, or an element carrying data-linked-entity / data-linked-type.
 *      A right-click while text is selected keeps the browser's own menu, so
 *      Copy still works.
 *
 * One read answers it all: GET /api/entity-links -- the row's record plus its
 * links. "Linked Entities" on a row inside the window walks to THAT record;
 * Back returns. The window closes only by Close or Escape (house modal rule).
 *
 * Needs auth.js (window.LWAuth) and picasso-context-menu.js. Loads its own CSS.
 */
(function () {
    'use strict';
    if (window.PicassoLinked) return;

    var CSS_HREF = '/picasso-linked.css?v=2';

    // tblEntityType ids (the same numbers the desktop's ENUM_ENTITY_TYPE carries).
    var T = {
        VENDOR: 1, CONTACT: 5, ADDRESS: 6, WEB_ADDRESS: 7, PHONE: 8, EVENT: 9,
        LIST_HEADER: 21, TASK_HEADER: 24, USER_DEFINED_ENTITY: 30, LOGIN_SETUP: 204,
        LEONARDO_FILE: 211, MATTER: 217, CALL_LOG: 219, PAGE_LINK: 220,
        RECORD_DASHBOARD: 221, SOLUTION: 222, CODEX_PAGE: 223, CODEX_SUITE: 224,
        VEHICLE: 225, GMAIL_MESSAGE: 229,
        HEALTH_RECORD: 230, HEALTH_VISIT: 231, HEALTH_ISSUE: 232,
        INSURANCE_PLAN: 233, INSURANCE_EOB: 234
    };

    // A record-page row kind (the Pin kinds, plus vehicle / gmail) -> its type,
    // and whether the row's id is the record's own id or its cross-reference id.
    var KINDS = {
        address:    { type: T.ADDRESS,             by: 'xref' },
        phone:      { type: T.PHONE,               by: 'xref' },
        web:        { type: T.WEB_ADDRESS,         by: 'xref' },
        login:      { type: T.LOGIN_SETUP,         by: 'xref' },
        file:       { type: T.LEONARDO_FILE,       by: 'xref' },
        vendor:     { type: T.VENDOR,              by: 'id' },
        contact:    { type: T.CONTACT,             by: 'id' },
        userentity: { type: T.USER_DEFINED_ENTITY, by: 'id' },
        event:      { type: T.EVENT,               by: 'id' },
        list:       { type: T.LIST_HEADER,         by: 'id' },
        task:       { type: T.TASK_HEADER,         by: 'id' },
        matter:     { type: T.MATTER,              by: 'id' },
        calllog:    { type: T.CALL_LOG,            by: 'id' },
        pagelink:   { type: T.PAGE_LINK,           by: 'id' },
        dashboard:  { type: T.RECORD_DASHBOARD,    by: 'id' },
        solution:   { type: T.SOLUTION,            by: 'id' },
        codexpage:  { type: T.CODEX_PAGE,          by: 'id' },
        codexsuite: { type: T.CODEX_SUITE,         by: 'id' },
        vehicle:    { type: T.VEHICLE,             by: 'id' },
        gmail:      { type: T.GMAIL_MESSAGE,       by: 'id' },
        insuranceplan: { type: T.INSURANCE_PLAN,    by: 'id' },
        eob:        { type: T.INSURANCE_EOB,       by: 'id' }
    };

    // Where a linked record opens, by its OWN id (never its EntityID).
    var PAGE = {};
    PAGE[T.CONTACT]             = '/contacts/contact.html?id=';
    PAGE[T.VENDOR]              = '/vendors/vendor.html?id=';
    PAGE[T.USER_DEFINED_ENTITY] = '/userentities/userentity.html?id=';
    PAGE[T.MATTER]              = '/matters/matter.html?id=';
    PAGE[T.CALL_LOG]            = '/call-logs/call-log.html?id=';
    PAGE[T.PAGE_LINK]           = '/entity-manager-iv/entity-manager-iv.html?type=links&id=';
    PAGE[T.RECORD_DASHBOARD]    = '/dashboards/dashboard.html?id=';
    PAGE[T.CODEX_PAGE]          = '/codex/space.html?page=';
    PAGE[T.CODEX_SUITE]         = '/codex/suite.html?id=';
    PAGE[T.VEHICLE]             = '/vehicles/vehicle.html?id=';
    PAGE[T.SOLUTION]            = '/solutions/solution.html?id=';
    PAGE[T.GMAIL_MESSAGE]       = '/gmail-client/gmail-client.html?focus=';
    PAGE[T.HEALTH_RECORD]       = '/health-ii/person.html?id=';
    PAGE[T.HEALTH_VISIT]        = '/health/health-visit.html?id=';
    PAGE[T.HEALTH_ISSUE]        = '/health-ii/issue.html?id=';
    PAGE[T.INSURANCE_PLAN]      = '/insurance/insurance-plan.html?id=';
    PAGE[T.INSURANCE_EOB]       = '/insurance/insurance-eob.html?id=';

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function ensureCss() {
        if (document.querySelector('link[data-picasso-linked-css]')) return;
        var l = document.createElement('link');
        l.rel = 'stylesheet';
        l.href = CSS_HREF;
        l.setAttribute('data-picasso-linked-css', '1');
        document.head.appendChild(l);
    }

    // ---- requests -----------------------------------------------------------
    // A request names a record the way a row can: {entityTypeId, identityId},
    // {entityTypeId, xrefId} or {entityId}.

    function requestForKind(kind, id) {
        var k = KINDS[kind];
        id = parseInt(id, 10) || 0;
        if (!k || id <= 0) return null;
        return k.by === 'xref' ? { entityTypeId: k.type, xrefId: id } : { entityTypeId: k.type, identityId: id };
    }

    function query(req) {
        var parts = [];
        ['entityTypeId', 'identityId', 'xrefId', 'entityId'].forEach(function (k) {
            var v = parseInt(req[k], 10) || 0;
            if (v > 0) parts.push(k + '=' + v);
        });
        return '/api/entity-links?' + parts.join('&');
    }

    // ---- the window -----------------------------------------------------------

    var stack = [];   // requests walked through, newest last

    function ensureModal() {
        var bg = document.getElementById('plBg');
        if (bg) return bg;
        ensureCss();
        bg = document.createElement('div');
        bg.id = 'plBg';
        bg.className = 'pl-bg';
        bg.innerHTML =
            '<div class="pl-modal" role="dialog" aria-modal="true" aria-labelledby="plTitle" data-testid="linked-modal">'
          +   '<div class="pl-head">'
          +     '<button type="button" class="pl-btn pl-back" id="plBack" data-testid="linked-back-btn" hidden>Back</button>'
          +     '<h2 id="plTitle">Linked Entities</h2>'
          +   '</div>'
          +   '<div class="pl-sub" id="plSub" data-testid="linked-sub"></div>'
          +   '<div class="pl-body" id="plBody" data-testid="linked-body"></div>'
          +   '<div class="pl-actions">'
          +     '<button type="button" class="pl-btn primary" id="plClose" data-testid="linked-close-btn">Close</button>'
          +   '</div>'
          + '</div>';
        document.body.appendChild(bg);
        document.getElementById('plClose').addEventListener('click', close);
        document.getElementById('plBack').addEventListener('click', function () {
            if (stack.length > 1) { stack.pop(); load(stack[stack.length - 1]); }
        });
        document.getElementById('plBody').addEventListener('click', function (e) {
            var b = e.target.closest('[data-pl-walk]');
            if (!b) return;
            var req = JSON.parse(b.getAttribute('data-pl-walk'));
            stack.push(req);
            load(req);
        });
        return bg;
    }

    function isOpen() {
        var bg = document.getElementById('plBg');
        return !!(bg && bg.classList.contains('open'));
    }

    function close() {
        var bg = document.getElementById('plBg');
        if (bg) bg.classList.remove('open');
        stack = [];
    }

    function open(req) {
        if (!req) return;
        var bg = ensureModal();
        stack = [req];
        bg.classList.add('open');
        load(req);
        setTimeout(function () { var c = document.getElementById('plClose'); if (c) c.focus(); }, 30);
    }

    async function load(req) {
        var sub  = document.getElementById('plSub');
        var body = document.getElementById('plBody');
        document.getElementById('plBack').hidden = stack.length <= 1;
        sub.textContent = '';
        body.innerHTML = '<div class="pl-empty">Loading...</div>';
        var r = await window.LWAuth.apiGet(query(req));
        if (stack[stack.length - 1] !== req) return;   // walked on meanwhile
        if (!r || !r.ok) {
            body.innerHTML = '<div class="pl-empty">' + window.LWAuth.errorHtml(r, 'Could not read the links') + '</div>';
            return;
        }
        render(r.data || {});
    }

    function render(data) {
        var t = data.target || {};
        var rows = data.links || [];
        var sub  = document.getElementById('plSub');
        var body = document.getElementById('plBody');
        var title = (t.entityTypeName ? t.entityTypeName + ': ' : '') + (t.displayName || '(no name)');

        if (!rows.length) {
            sub.innerHTML = '<span class="pl-target">' + esc(title) + '</span>';
            body.innerHTML = '<div class="pl-empty" data-testid="linked-empty">Nothing is linked to this record.</div>';
            return;
        }

        // Rows arrive ordered by type then name, so a group starts wherever the type changes.
        var groups = [];
        rows.forEach(function (row) {
            var name = row.entityTypeName || 'Other';
            var last = groups[groups.length - 1];
            if (last && last.name === name) last.rows.push(row);
            else groups.push({ name: name, rows: [row] });
        });

        sub.innerHTML = '<span class="pl-target">' + esc(title) + '</span>'
                      + '<span class="pl-count">' + rows.length + (rows.length === 1 ? ' linked record' : ' linked records')
                      + ' | ' + groups.length + (groups.length === 1 ? ' type' : ' types') + '</span>';

        body.innerHTML = groups.map(function (g) {
            return '<div class="pl-group" data-testid="linked-group">'
                 +   '<div class="pl-grouphead">' + esc(g.name) + ' (' + g.rows.length + ')</div>'
                 +   g.rows.map(rowHtml).join('')
                 + '</div>';
        }).join('');
    }

    function rowHtml(row) {
        var id    = parseInt(row.linkedIdentityId, 10) || 0;
        var ent   = parseInt(row.linkedEntityId, 10) || 0;
        var type  = parseInt(row.entityTypeId, 10) || 0;
        var page  = PAGE[type];
        var label = esc(row.displayName || '(no name)');
        var name  = page && id
            ? '<a class="pl-name" href="' + page + id + '" data-testid="linked-row-open">' + label + '</a>'
            : '<span class="pl-name">' + label + '</span>';
        // Walk on when the service can list THAT record's links: it is an entity,
        // or it is a Leonardo File (whose links live in the Files catalog).
        var walk = ent > 0 ? { entityId: ent }
                 : (type === T.LEONARDO_FILE && id > 0 ? { entityTypeId: type, identityId: id } : null);
        return '<div class="pl-row" data-testid="linked-row">'
             +   name
             +   (walk ? '<button type="button" class="pl-btn small" data-pl-walk="' + esc(JSON.stringify(walk)) + '"'
                       + ' data-testid="linked-row-walk">Linked</button>' : '')
             + '</div>';
    }

    // ---- the row button -------------------------------------------------------

    /** The visible "Linked" button for one record-page row (the phone's way in). */
    function button(kind, id) {
        var req = requestForKind(kind, id);
        if (!req) return '';
        return '<button type="button" class="ed-btn pl-row-btn" data-linked-btn="' + esc(JSON.stringify(req)) + '"'
             + ' title="Every record this one is linked to" data-testid="entity-linked-btn">Linked</button>';
    }

    function requestOfButton(b) {
        try { return JSON.parse(b.getAttribute('data-linked-btn')); } catch (e) { return null; }
    }

    // The request a right-click on el stands for, or null.
    function requestAt(el) {
        if (!el || !el.closest) return null;
        if (el.closest('.pl-modal')) return null;
        var tagged = el.closest('[data-linked-entity], [data-linked-type]');
        if (tagged) {
            var ent = parseInt(tagged.getAttribute('data-linked-entity'), 10) || 0;
            if (ent > 0) return { entityId: ent };
            return requestForKind(tagged.getAttribute('data-linked-type'), tagged.getAttribute('data-linked-id'));
        }
        // The row: the nearest ancestor holding exactly one Linked button. Once an
        // ancestor holds several, the click was between rows -- no row.
        for (var n = el; n && n !== document.body; n = n.parentElement) {
            var btns = n.querySelectorAll('[data-linked-btn]');
            if (btns.length === 1) return requestOfButton(btns[0]);
            if (btns.length > 1) return null;
        }
        return null;
    }

    // ---- the right-click menu -------------------------------------------------
    // The shared Leonardo Context Menu (picasso-context-menu.js) carries the menu:
    // right-click, long-press, and keyboard. The Linked button above is this
    // feature's always-visible phone door, so rows need no More button too.

    if (window.PicassoContextMenu) {
        window.PicassoContextMenu.attach({
            items: function (row, target) {
                if (isOpen()) return null;
                var req = requestAt(target);
                if (!req) return null;
                return [{ label: 'Linked Entities', testId: 'linked-menu-item', action: function () { open(req); } }];
            }
        });
    }

    document.addEventListener('click', function (e) {
        var b = e.target.closest && e.target.closest('[data-linked-btn]');
        if (b) { e.preventDefault(); e.stopPropagation(); open(requestOfButton(b)); }
    }, true);

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && isOpen()) { e.stopPropagation(); close(); }
    }, true);

    window.PicassoLinked = {
        open: open,
        close: close,
        button: button,
        requestForKind: requestForKind,
        KINDS: KINDS,
        TYPES: T
    };
})();
