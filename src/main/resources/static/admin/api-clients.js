(function () {
    'use strict';

    // Admin -> API Clients (Story 3493). An API client is an outside agent that may reach ONLY
    // the paths on its allowlist, and inside those only the solutions it has been given. This
    // page creates them, issues and revokes their keys, switches them off, decides which
    // solutions they see, hands over the brief, and shows every request they have made.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc;

    var mClients = [], mSolutions = [];
    var mEditId = 0, mLinksId = 0, mTab = 'clients';

    function client(id) { return mClients.filter(function (c) { return c.id === id; })[0]; }
    function each(selector, fn) { Array.prototype.forEach.call(document.querySelectorAll(selector), fn); }

    // ---- clients ------------------------------------------------------------------

    async function load() {
        var r = await SL.get('/api/api-clients');
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $('pane-clients').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the clients.')) + '</div>';
            return;
        }
        mClients = r.data;
        draw();
    }

    function draw() {
        var refused = mClients.reduce(function (n, c) { return n + (c.refusedLastDay || 0); }, 0);
        $('badgeRefused').textContent = refused ? String(refused) : '';
        if (!mClients.length) {
            $('pane-clients').innerHTML = '<div class="sl-empty">No API clients. Nothing outside Echo can reach it.</div>';
            SL.status('0 clients');
            return;
        }
        $('pane-clients').innerHTML = '<div class="sl-cards">' + mClients.map(card).join('') + '</div>';
        each('[data-act]', function (b) {
            b.addEventListener('click', function () { act(b.getAttribute('data-act'), +b.getAttribute('data-id')); });
        });
        SL.status(mClients.length + (mClients.length === 1 ? ' client' : ' clients'));
    }

    function card(c) {
        var state = !c.enabled ? '<span class="sl-chip warn">Switched off</span>'
                  : !c.hasKey  ? '<span class="sl-chip wait">No key yet</span>'
                  :              '<span class="sl-pill" style="background:#16A34A">Live</span>';
        function btn(action, label, cls) {
            return '<button type="button" class="sl-btn small' + (cls ? ' ' + cls : '') + '" data-act="' + action + '" data-id="' + c.id +
                '" data-testid="api-clients-' + action + '-btn">' + label + '</button>';
        }
        return '<div class="sl-card" data-testid="api-clients-card">' +
            '<h2>' + esc(c.clientName) + '</h2>' +
            '<div class="row">' + state + '<span class="sl-chip agent">' + esc(c.clientCode) + '</span>' +
                (c.hasKey ? '<span class="sl-muted">key ending ' + esc(c.keyHint || '') + '</span>' : '') + '</div>' +
            '<div class="sl-muted" style="margin-bottom:6px">May reach: <span class="sl-mono" style="padding:1px 5px">' + esc(c.allowedPathPrefixes) + '</span></div>' +
            '<div class="row" style="margin-bottom:4px"><span class="sl-chip">' + c.solutionCount + (c.solutionCount === 1 ? ' solution' : ' solutions') + '</span>' +
                '<span class="sl-chip">' + c.requestsLastDay + ' requests today</span>' +
                (c.refusedLastDay ? '<span class="sl-chip warn">' + c.refusedLastDay + ' refused</span>' : '') + '</div>' +
            '<div class="sl-muted">' + (c.lastUsedDate ? 'Last used ' + SL.dateTime(c.lastUsedDate) : 'Never used') + '</div>' +
            (c.clientNotes ? '<div class="summary">' + esc(c.clientNotes) + '</div>' : '') +
            '<div class="actions">' + btn('links', 'Solutions') + btn('brief', 'Brief') + btn('key', c.hasKey ? 'New key' : 'Issue key', 'primary') +
                btn('toggle', c.enabled ? 'Switch off' : 'Switch on') + btn('edit', 'Edit') +
                (c.hasKey ? btn('revoke', 'Revoke key', 'danger') : '') + btn('delete', 'Delete', 'danger') + '</div></div>';
    }

    function act(action, id) {
        if (action === 'edit')   return openEdit(id);
        if (action === 'key')    return issueKey(id);
        if (action === 'revoke') return revokeKey(id);
        if (action === 'toggle') return toggle(id);
        if (action === 'delete') return remove(id);
        if (action === 'links')  return openLinks(id);
        if (action === 'brief')  return openBrief(id);
    }

    // ---- add / edit -----------------------------------------------------------------

    function openEdit(id) {
        mEditId = id || 0;
        var c = id ? client(id) : { clientName: '', clientCode: '', allowedPathPrefixes: '/api/solutions/', clientNotes: '' };
        SL.warn('clientWarn', '');
        $('clientTitle').textContent = id ? 'Edit client' : 'New client';
        $('cName').value = c.clientName || '';
        $('cCode').value = c.clientCode || '';
        $('cPrefixes').value = c.allowedPathPrefixes || '/api/solutions/';
        $('cNotes').value = c.clientNotes || '';
        SL.open('clientOverlay');
        $('cName').focus();
    }

    async function save() {
        var existing = mEditId ? client(mEditId) : null;
        var body = { clientName: $('cName').value, clientCode: $('cCode').value, allowedPathPrefixes: $('cPrefixes').value,
                     clientNotes: $('cNotes').value, enabled: existing ? existing.enabled : true };
        var r = mEditId ? await SL.put('/api/api-clients/' + mEditId, body) : await SL.post('/api/api-clients', body);
        if (!r || !r.ok) { SL.warn('clientWarn', SL.failText(r, 'The client could not be saved.')); return; }
        SL.close('clientOverlay');
        await load();
    }

    // ---- keys, the kill switch, delete -------------------------------------------------

    async function issueKey(id) {
        var c = client(id);
        if (c.hasKey && !window.confirm('Issue a new key for ' + c.clientName + '?\n\nIts current key (ending ' + (c.keyHint || '?') +
            ') stops working at once, and the agent is locked out until it has the new one.')) return;
        var r = await SL.post('/api/api-clients/' + id + '/key', {});
        if (!r || !r.ok || !r.data.key) { SL.toast(SL.failText(r, 'A key could not be issued.')); return; }
        $('keyTitle').textContent = 'Key for ' + c.clientName;
        $('keyValue').textContent = r.data.key;
        mKeyClientId = id;
        SL.open('keyOverlay');
        await load();
    }

    // The one moment the key exists on this side: fold it into the brief, in the browser, so the
    // whole hand-off is a single paste. The key line is found by its header name, so the slot
    // text lives in one place only (ApiClientBrief).
    var mKeyClientId = 0;

    async function copyBriefWithKey() {
        var key = $('keyValue').textContent;
        if (!mKeyClientId || !key) return;
        var r = await window.LWAuth.apiGetText('/api/api-clients/' + mKeyClientId + '/brief');
        if (!r || !r.ok) { SL.toast(window.LWAuth.errorText(r, 'The brief could not be loaded.')); return; }
        copyText(String(r.data).replace(/(X-Leo-Client-Key:[ \t]*).*/, function (m, head) { return head + key; }), 'Instructions with the key');
    }

    async function revokeKey(id) {
        var c = client(id);
        if (!window.confirm('Revoke the key of ' + c.clientName + ' (ending ' + (c.keyHint || '?') + ')?\n\nThe agent is locked out at once. You can issue a new key later.')) return;
        var r = await SL.del('/api/api-clients/' + id + '/key');
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The key could not be revoked.')); return; }
        await load();
    }

    async function toggle(id) {
        var c = client(id);
        var question = c.enabled
            ? 'Switch off ' + c.clientName + '?\n\nEvery request it makes is refused from this moment. Its key is kept, so switching it back on restores access.'
            : 'Switch ' + c.clientName + ' back on?\n\nIt can reach ' + c.allowedPathPrefixes + ' again with its existing key.';
        if (!window.confirm(question)) return;
        var r = await SL.post('/api/api-clients/' + id + '/enabled', { enabled: !c.enabled });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That did not work.')); return; }
        await load();
    }

    async function remove(id) {
        var c = client(id);
        if (!window.confirm('Delete the API client ' + c.clientName + ' (' + c.clientCode + ')?\n\nIts key and its access to ' + c.solutionCount +
            (c.solutionCount === 1 ? ' solution' : ' solutions') + ' go with it. What it wrote, and the record of its requests, are kept.\n\nThis cannot be undone.')) return;
        var r = await SL.del('/api/api-clients/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The client could not be deleted.')); return; }
        await load();
    }

    // ---- which solutions it sees -----------------------------------------------------------

    async function openLinks(id) {
        mLinksId = id;
        var c = client(id);
        var results = await Promise.all([SL.get('/api/solutions?includeArchived=true'), SL.get('/api/api-clients/' + id + '/solutions')]);
        mSolutions = (results[0] && results[0].ok && Array.isArray(results[0].data)) ? results[0].data : [];
        var linked = {};
        ((results[1] && results[1].ok && Array.isArray(results[1].data)) ? results[1].data : []).forEach(function (l) { linked[l.solutionId] = true; });
        $('linksTitle').textContent = 'What ' + c.clientName + ' may see';
        $('linksList').innerHTML = mSolutions.length ? mSolutions.map(function (s) {
            return '<li><label class="grow" style="display:flex;gap:10px;align-items:center;cursor:pointer">' +
                '<input type="checkbox" data-solution="' + s.id + '"' + (linked[s.id] ? ' checked' : '') + ' data-testid="api-clients-link-check">' +
                '<span>' + esc(s.name) + (s.archived ? ' <span class="sl-muted">(archived)</span>' : '') + '</span></label></li>';
        }).join('') : '<li><span class="sl-muted">There are no solutions yet.</span></li>';
        each('[data-solution]', function (box) {
            box.addEventListener('change', function () { setLink(+box.getAttribute('data-solution'), box.checked, box); });
        });
        SL.open('linksOverlay');
    }

    async function setLink(solutionId, linked, box) {
        var r = await SL.post('/api/api-clients/' + mLinksId + '/solutions', { solutionId: solutionId, linked: linked });
        if (!r || !r.ok) { box.checked = !linked; SL.toast(SL.failText(r, 'That did not work.')); return; }
        SL.toast(linked ? 'It can see that solution now' : 'It can no longer see that solution');
    }

    // ---- the brief ---------------------------------------------------------------------------

    async function openBrief(id) {
        var r = await window.LWAuth.apiGetText('/api/api-clients/' + id + '/brief');
        if (!r || !r.ok) { SL.toast(window.LWAuth.errorText(r, 'The brief could not be loaded.')); return; }
        $('briefTitle').textContent = 'Brief for ' + client(id).clientName;
        $('briefText').textContent = r.data;
        SL.open('briefOverlay');
    }

    async function copyText(text, what) {
        try { await navigator.clipboard.writeText(text); SL.toast(what + ' copied'); }
        catch (e) { SL.toast('Select the text and copy it by hand'); }
    }

    // ---- the access log ------------------------------------------------------------------------

    async function loadAccess() {
        SL.fillSelect($('fClient'), [{ value: '', label: 'Every client' }].concat(
            mClients.map(function (c) { return { value: String(c.id), label: c.clientName }; })), $('fClient').value);
        var q = '/api/api-clients/access-log?pageSize=200';
        if ($('fClient').value) q += '&clientId=' + encodeURIComponent($('fClient').value);
        if ($('fRefused').checked) q += '&refusedOnly=true';
        var r = await SL.get(q);
        var rows = (r && r.ok && Array.isArray(r.data)) ? r.data : [];
        if (!rows.length) { $('pane-access').innerHTML = '<div class="sl-empty">No requests' + ($('fRefused').checked ? ' were refused.' : ' yet.') + '</div>'; SL.status('0 requests'); return; }
        $('pane-access').innerHTML = '<div class="sl-scroll-x"><table class="sl-table" data-testid="api-clients-access-table"><thead><tr>' +
            '<th>When</th><th>Client</th><th>Request</th><th>Answer</th><th>From</th></tr></thead><tbody>' + rows.map(function (a) {
                return '<tr data-testid="api-clients-access-row"><td>' + SL.dateTime(a.createdDate) + '</td><td>' + esc(a.clientCode || '(unknown)') + '</td>' +
                    '<td>' + esc(a.httpMethod) + ' ' + esc(a.requestPath) + '</td>' +
                    '<td>' + (a.allowed ? '<span class="sl-chip">' + a.statusCode + '</span>' : '<span class="sl-chip warn">' + a.statusCode + ' refused</span>') + '</td>' +
                    '<td>' + esc(a.clientIp || '') + '</td></tr>';
            }).join('') + '</tbody></table></div>';
        SL.status(rows.length + ' most recent requests');
    }

    // ---- tabs, wire, boot -------------------------------------------------------------------------

    function go(tab) {
        mTab = tab;
        each('.sl-tab', function (t) { t.classList.toggle('active', t.getAttribute('data-tab') === tab); });
        ['clients', 'access'].forEach(function (n) { $('pane-' + n).hidden = n !== tab; $('toolbar-' + n).hidden = n !== tab; });
        if (tab === 'access') loadAccess(); else load();
    }

    function wire() {
        each('.sl-tab', function (t) { t.addEventListener('click', function () { go(t.getAttribute('data-tab')); }); });
        $('btnAdd').addEventListener('click', function () { openEdit(0); });
        $('clientCancel').addEventListener('click', function () { SL.close('clientOverlay'); });
        $('clientSave').addEventListener('click', function () { SL.guard($('clientSave'), save); });
        $('keyCopy').addEventListener('click', function () { copyText($('keyValue').textContent, 'Key'); });
        $('keyClose').addEventListener('click', function () { $('keyValue').textContent = ''; mKeyClientId = 0; SL.close('keyOverlay'); });
        $('keyCopyBrief').addEventListener('click', copyBriefWithKey);
        $('linksClose').addEventListener('click', function () { SL.close('linksOverlay'); load(); });
        $('briefCopy').addEventListener('click', function () { copyText($('briefText').textContent, 'Brief'); });
        $('briefClose').addEventListener('click', function () { SL.close('briefOverlay'); });
        $('fClient').addEventListener('change', loadAccess);
        $('fRefused').addEventListener('change', loadAccess);
        $('btnPrint').addEventListener('click', function (e) {
            e.preventDefault();
            window.Picasso.printThisPage({ selector: '#pane-' + mTab, title: 'API Clients', stripSelector: 'button, .actions, script, style' });
        });
    }

    (async function () {
        await window.LWAuth.ensureAuth();
        // The password again first: this page hands out the keys to Echo's one outside door.
        var allowed = await window.StepUpGate.requireFeature('ApiClients');
        if (!allowed) return;
        wire();
        await load();
    })();
})();
