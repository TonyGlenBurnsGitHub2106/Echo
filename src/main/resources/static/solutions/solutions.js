(function () {
    'use strict';

    // Solutions list (Story 3493). One card per solution; the headline numbers on a card
    // are counted by the server, never from what happens to be loaded here.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc;

    var mLookups = { statuses: [], members: [] };
    var mShowArchived = false;
    try { mShowArchived = localStorage.getItem('sl.showArchived') === '1'; } catch (e) { /* private mode */ }

    // ---- load ---------------------------------------------------------------

    async function loadLookups() {
        var r = await SL.get('/api/solutions/lookups');
        if (r && r.ok && r.data) mLookups = r.data;
    }

    async function loadList() {
        var r = await SL.get('/api/solutions' + (mShowArchived ? '?includeArchived=true' : ''));
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $('cards').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the solutions.')) + '</div>';
            return;
        }
        render(r.data);
    }

    // ---- render -------------------------------------------------------------

    function render(list) {
        if (!list.length) {
            $('cards').innerHTML = '<div class="sl-empty">No solutions yet.</div>';
            SL.status('Echo | Solutions');
            return;
        }
        $('cards').innerHTML = list.map(card).join('');
        Array.prototype.forEach.call(document.querySelectorAll('[data-open]'), function (c) {
            c.addEventListener('click', function () {
                window.location.href = '/solutions/solution.html?id=' + c.getAttribute('data-open');
            });
        });
        var waiting = list.reduce(function (n, s) { return n + (s.awaitingTonyCount || 0); }, 0);
        SL.status(list.length + (list.length === 1 ? ' solution' : ' solutions') +
            (waiting ? ' | ' + waiting + (waiting === 1 ? ' message is' : ' messages are') + ' waiting on you' : ''));
    }

    function card(s) {
        var chips = [
            '<span class="sl-chip">' + s.projectCount + (s.projectCount === 1 ? ' project' : ' projects') + '</span>',
            '<span class="sl-chip">' + s.logCount + ' log</span>',
            '<span class="sl-chip">' + s.noteCount + (s.noteCount === 1 ? ' note' : ' notes') + '</span>'
        ];
        if (s.blockedItemCount) chips.push('<span class="sl-chip warn">' + s.blockedItemCount + ' blocked</span>');
        if (s.awaitingTonyCount) chips.push('<span class="sl-chip wait">' + s.awaitingTonyCount + ' waiting on you</span>');
        return '<div class="sl-card link' + (s.archived ? ' archived' : '') + '" data-open="' + s.id + '" data-testid="solutions-card">' +
            '<h2>' + esc(s.name) + '</h2>' +
            '<div class="row">' + SL.pill(s.statusName, s.colorHex) +
                (s.ownerName ? '<span class="sl-chip">' + esc(s.ownerName) + '</span>' : '') +
                (s.targetDate ? '<span class="sl-muted">Target ' + SL.date(s.targetDate) + '</span>' : '') +
                (s.archived ? '<span class="sl-chip">Archived</span>' : '') + '</div>' +
            (s.statusSummary ? '<div class="summary sl-md">' + SL.md(s.statusSummary) + '</div>'
                             : (s.descriptionPreview ? '<div class="summary sl-md">' + SL.md(s.descriptionPreview, s.descriptionPreview.length >= 300) + '</div>' : '')) +
            SL.progress(s.closedItemCount, s.itemCount) +
            '<div class="row" style="margin-top:10px;margin-bottom:0">' + chips.join('') + '</div>' +
            (s.lastLogDate ? '<div class="sl-muted" style="margin-top:6px">Last logged ' + SL.dateTime(s.lastLogDate) + '</div>' : '') +
            '</div>';
    }

    // ---- add ----------------------------------------------------------------

    function openAdd() {
        SL.warn('solutionWarn', '');
        $('fldName').value = '';
        $('fldDescription').value = '';
        $('fldTarget').value = '';
        SL.fillSelect($('fldStatus'), mLookups.statuses.map(function (s) { return { value: s.code, label: s.name }; }),
            mLookups.statuses.length ? mLookups.statuses[0].code : '');
        SL.fillSelect($('fldOwner'), [{ value: '', label: 'Nobody yet' }].concat(
            mLookups.members.filter(function (m) { return m.active; }).map(function (m) { return { value: m.code, label: m.name }; })), '');
        SL.open('solutionOverlay');
        $('fldName').focus();
    }

    async function saveNew() {
        var name = $('fldName').value.trim();
        if (!name) { SL.warn('solutionWarn', 'A solution needs a name.'); return; }
        var target = SL.toIsoDate($('fldTarget').value);
        if (target === null) { SL.warn('solutionWarn', 'The target date should look like 09/17/2026.'); return; }
        var r = await SL.post('/api/solutions', {
            name: name, description: $('fldDescription').value, statusCode: $('fldStatus').value,
            ownerCode: $('fldOwner').value, targetDate: target
        });
        if (!r || !r.ok) { SL.warn('solutionWarn', SL.failText(r, 'The solution could not be saved.')); return; }
        window.location.href = '/solutions/solution.html?id=' + r.data.id;
    }

    // ---- wire ---------------------------------------------------------------

    function wire() {
        $('btnAdd').addEventListener('click', openAdd);
        $('solutionCancel').addEventListener('click', function () { SL.close('solutionOverlay'); });
        $('solutionSave').addEventListener('click', function () { SL.guard($('solutionSave'), saveNew); });
        $('chkArchived').checked = mShowArchived;
        $('chkArchived').addEventListener('change', function () {
            mShowArchived = $('chkArchived').checked;
            try { localStorage.setItem('sl.showArchived', mShowArchived ? '1' : '0'); } catch (e) { /* private mode */ }
            loadList();
        });
        $('btnPrint').addEventListener('click', function (e) {
            e.preventDefault();
            window.Picasso.printThisPage({ selector: '#slBody', title: 'Solutions', stripSelector: 'button, script, style' });
        });
    }

    (async function () {
        await window.LWAuth.ensureAuth();
        wire();
        await loadLookups();
        await loadList();
    })();
})();
