(function () {
    'use strict';

    // The Log tab of one solution (Story 3493) -- "how did we do it". An append box on top, the
    // journal below it, one page at a time. Filtering, ordering and counting all happen on the
    // server, so "showing 50 of 212" is true while only page one is here; when a filter changes
    // the list goes back to the server rather than filtering the rows already loaded.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PAGE_SIZE = 50;
    var mEntries = [], mTotal = 0, mOffset = 0, mLoading = false;
    var mEditId = 0, mPromoteLogId = 0, mSearchTimer = null;
    var mFlashId = 0;              // an entry the Search tab (2026-10-16) asked to scroll to and flash
    var mItemsForPicker = [];

    // ---- load ----------------------------------------------------------------------

    function query(offset) {
        var q = '/api/solutions/' + Page.id + '/log?offset=' + offset + '&pageSize=' + PAGE_SIZE;
        if ($('fLogType').value)    q += '&type=' + encodeURIComponent($('fLogType').value);
        if ($('fLogAuthor').value)  q += '&author=' + encodeURIComponent($('fLogAuthor').value);
        if ($('fLogProject').value) q += '&projectId=' + encodeURIComponent($('fLogProject').value);
        if ($('fLogSearch').value.trim()) q += '&search=' + encodeURIComponent($('fLogSearch').value.trim());
        if ($('fLogOrder').value === 'asc') q += '&ascending=true';
        return q;
    }

    async function loadFirst() {
        mEntries = []; mOffset = 0;
        await loadPage();
    }

    async function loadPage() {
        if (mLoading) return;
        mLoading = true;
        var r = await SL.get(query(mOffset));
        mLoading = false;
        if (!r || !r.ok || !r.data) {
            $('logList').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the log.')) + '</div>';
            return;
        }
        var page = Array.isArray(r.data.entries) ? r.data.entries : [];
        mEntries = mEntries.concat(page);
        mTotal = r.data.total || 0;
        mOffset += page.length;
        drawList();
    }

    // ---- draw ----------------------------------------------------------------------

    function drawShell() {
        $('pane-log').innerHTML =
            '<div class="sl-compose" data-testid="log-compose">' +
                '<div class="fields">' +
                    '<div><label for="nLogType">Kind</label><div class="picker-wrap"><input type="text" id="nLogType" class="picker-input" data-testid="log-new-type" placeholder="Pick a kind or type a new one" autocomplete="off" data-voice-skip="true"><div class="picker-dropdown" id="nLogTypeDD" data-testid="log-new-type-list"></div></div></div>' +
                    '<div><label for="nLogItem">About</label><select id="nLogItem" data-ps-placeholder="The solution as a whole" data-testid="log-new-item"></select></div>' +
                '</div>' +
                '<textarea id="nLogText" data-testid="log-new-text" placeholder="What was done. One entry per action, in the order it happened."></textarea>' +
                '<div class="foot"><button type="button" class="sl-btn primary" id="nLogSave" data-testid="log-new-save">Save</button></div>' +
            '</div>' +
            '<div id="logList" data-testid="log-list"><div class="sl-empty">Loading&hellip;</div></div>' +
            '<div class="sl-more" id="logMore" hidden><button type="button" class="sl-btn" id="btnLogMore" data-testid="log-more-btn">Load more</button></div>';
        $('nLogType').value = Page.lookups.logTypes.length ? Page.lookups.logTypes[0].name : '';
        attachKindPicker('nLogType');
        $('nLogSave').addEventListener('click', function () { SL.guard($('nLogSave'), append); });
        $('btnLogMore').addEventListener('click', loadPage);
        loadItemPicker();
    }

    /** A Kind field is find-or-create: a name that is not on the list yet is made when the entry is saved. */
    function attachKindPicker(id) {
        if (!window.PicassoPicker) return;
        window.PicassoPicker.attach({ input: $(id), dropdown: $(id + 'DD'), allowNew: true, newLabel: 'Add',
            getItems: function () { return Page.kindNames(Page.lookups.logTypes); } });
    }

    function typeOptions() { return Page.lookups.logTypes.map(function (t) { return { value: t.code, label: t.name }; }); }

    /** The filter's kinds, kept current when a save adds one. */
    function refreshKindFilter() {
        SL.fillSelect($('fLogType'), [{ value: '', label: 'Every kind' }].concat(typeOptions()), $('fLogType').value);
    }

    /** The "About" picker: every open item, indented by depth, so an entry can name what it was for. */
    async function loadItemPicker() {
        var r = await SL.get('/api/solutions/' + Page.id + '/items');
        mItemsForPicker = (r && r.ok && Array.isArray(r.data)) ? r.data : [];
        var depth = {};
        mItemsForPicker.forEach(function (i) { depth[i.id] = i.parentItemId ? (depth[i.parentItemId] || 0) + 1 : 0; });
        var options = [{ value: '', label: 'The solution as a whole' }].concat(
            mItemsForPicker.filter(function (i) { return !i.closed; }).map(function (i) {
                return { value: String(i.id), label: new Array(depth[i.id] + 1).join('  ') + i.projectName + ' / ' + i.title };
            }));
        SL.fillSelect($('nLogItem'), options, '');
    }

    function drawList() {
        if (!mEntries.length) {
            $('logList').innerHTML = '<div class="sl-empty">Nothing logged' + (filtered() ? ' that matches.' : ' yet.') + '</div>';
        } else {
            $('logList').innerHTML = mEntries.map(entry).join('');
            bind();
        }
        $('logMore').hidden = mEntries.length >= mTotal;
        SL.status(mEntries.length + ' of ' + mTotal + ' log entries');
    }

    function filtered() {
        return !!($('fLogType').value || $('fLogAuthor').value || $('fLogProject').value || $('fLogSearch').value.trim());
    }

    /** A long entry starts folded to its first line; a short one starts open. Clicking its header flips it. */
    function folded(e) { return Page.fold().isCollapsed('log:' + e.id, SL.isLong(e.text)); }

    function entry(e) {
        var where = e.itemTitle ? (e.projectName ? e.projectName + ' / ' : '') + e.itemTitle : (e.projectName || '');
        var isFolded = folded(e);
        if (isFolded) {
            return '<div class="sl-entry folded" id="log-' + e.id + '" data-testid="log-entry">' +
                '<div class="head" data-log-fold="' + e.id + '" data-testid="log-entry-head"><span class="chev folded">&#9660;</span><span class="seq">#' + e.sequenceNumber + '</span>' +
                    '<span class="sl-chip">' + esc(e.typeName) + '</span>' + Page.personChip(e.authorCode, e.authorName) +
                    '<span class="sl-muted">' + SL.dateTime(e.logDate) + '</span></div>' +
                '<div class="text oneline">' + esc(e.typeCode === 'command' ? SL.firstLine(e.text, 160) : SL.plain(e.text, 160)) + ' &hellip;</div></div>';
        }
        return '<div class="sl-entry ' + esc(e.typeCode) + '" id="log-' + e.id + '" data-testid="log-entry">' +
            '<div class="head" data-log-fold="' + e.id + '" data-testid="log-entry-head"><span class="chev">&#9660;</span><span class="seq">#' + e.sequenceNumber + '</span>' +
                '<span class="sl-chip">' + esc(e.typeName) + '</span>' + Page.personChip(e.authorCode, e.authorName) +
                '<span class="sl-muted">' + SL.dateTime(e.logDate) + '</span>' +
                (e.modifiedDate ? '<span class="sl-muted">corrected</span>' : '') +
                (e.stepCount ? '<span class="sl-chip agent">in a runbook</span>' : '') + '</div>' +
            (where ? '<div class="sl-muted" style="margin-bottom:6px">' + esc(where) + '</div>' : '') +
            (e.typeCode === 'command'              // a command is literal: shown exactly as typed
                ? '<div class="text">' + esc(e.text) + (e.truncated ? '&hellip;' : '') + '</div>'
                : '<div class="text sl-md" data-testid="log-entry-text">' + SL.md(e.text, e.truncated) + '</div>') +
            '<div class="foot">' +
                (e.truncated ? '<button type="button" class="sl-btn small" data-full="' + e.id + '" data-testid="log-full-btn">Show all of it</button>' : '') +
                '<button type="button" class="sl-btn small" data-promote="' + e.id + '" data-testid="log-promote-btn">Make a runbook step</button>' +
                '<button type="button" class="sl-btn small" data-correct="' + e.id + '" data-testid="log-correct-btn">Correct</button>' +
                '<button type="button" class="sl-btn small danger" data-remove="' + e.id + '" data-testid="log-delete-btn">Delete</button>' +
            '</div></div>';
    }

    function each(selector, fn) { Array.prototype.forEach.call(document.querySelectorAll(selector), fn); }

    function foldAll(collapsed) {
        // Tony's rule (2026-09-24): remembered, and reaches entries not yet loaded or written.
        Page.fold().setAllForPrefix('log:', collapsed);
        drawList();
    }

    function bind() {
        each('[data-log-fold]', function (h) {
            h.addEventListener('click', function () {
                var id = +h.getAttribute('data-log-fold');
                var e = mEntries.filter(function (x) { return x.id === id; })[0];
                if (!e) return;
                Page.fold().toggle('log:' + id, SL.isLong(e.text));
                drawList();
            });
        });
        each('[data-full]',    function (b) { b.addEventListener('click', function () { showFull(+b.getAttribute('data-full')); }); });
        each('[data-promote]', function (b) { b.addEventListener('click', function () { openPromote(+b.getAttribute('data-promote')); }); });
        each('[data-correct]', function (b) { b.addEventListener('click', function () { openCorrect(+b.getAttribute('data-correct')); }); });
        each('[data-remove]',  function (b) { b.addEventListener('click', function () { remove(+b.getAttribute('data-remove')); }); });
    }

    // ---- append, correct, delete -------------------------------------------------------

    async function append() {
        var text = $('nLogText').value.trim();
        if (!text) { SL.toast('A log entry needs some text.'); return; }
        var body = { text: text, typeName: $('nLogType').value.trim() };
        if ($('nLogItem').value) body.itemId = +$('nLogItem').value;
        var r = await SL.post('/api/solutions/' + Page.id + '/log', body);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The entry could not be saved.')); return; }
        $('nLogText').value = '';
        SL.toast('Logged as #' + r.data.sequenceNumber);
        await Page.loadLookups();
        refreshKindFilter();
        await Promise.all([loadFirst(), Page.reload()]);
    }

    async function showFull(id) {
        var r = await SL.get('/api/solutions/log/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That entry could not be opened.')); return; }
        mEntries = mEntries.map(function (e) { return e.id === id ? r.data : e; });
        drawList();
    }

    async function openCorrect(id) {
        var r = await SL.get('/api/solutions/log/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That entry could not be opened.')); return; }
        mEditId = id;
        SL.warn('logWarn', '');
        $('logTitle').textContent = 'Correct log entry #' + r.data.sequenceNumber;
        $('lType').value = r.data.typeName || Page.kindName(Page.lookups.logTypes, r.data.typeCode);
        $('lText').value = r.data.text || '';
        SL.open('logOverlay');
    }

    async function saveCorrection() {
        var text = $('lText').value.trim();
        if (!text) { SL.warn('logWarn', 'A log entry needs some text.'); return; }
        var r = await SL.put('/api/solutions/log/' + mEditId, { text: text, typeName: $('lType').value.trim() });
        if (!r || !r.ok) { SL.warn('logWarn', SL.failText(r, 'The entry could not be saved.')); return; }
        SL.close('logOverlay');
        await Page.loadLookups();
        refreshKindFilter();
        mEntries = mEntries.map(function (e) { return e.id === mEditId ? r.data : e; });
        drawList();
    }

    async function remove(id) {
        var e = mEntries.filter(function (x) { return x.id === id; })[0];
        if (!e) return;
        var quote = (e.text || '').replace(/\s+/g, ' ').slice(0, 80);
        var question = 'Delete log entry #' + e.sequenceNumber + ' (' + e.typeName + ', ' + (e.authorName || 'unknown') + ', ' + SL.dateTime(e.logDate) +
            ')?\n\n"' + quote + (e.text && e.text.length > 80 ? '...' : '') + '"\n\nIts number is never reused. This cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/log/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The entry could not be deleted.')); return; }
        await Promise.all([loadFirst(), Page.reload()]);
    }

    // ---- promote to a runbook step ---------------------------------------------------------

    async function openPromote(logId) {
        var r = await SL.get('/api/solutions/' + Page.id + '/notes?type=runbook');
        var books = (r && r.ok && Array.isArray(r.data)) ? r.data : [];
        if (!books.length) { SL.toast('Create a runbook on the Notes tab first.'); return; }
        mPromoteLogId = logId;
        SL.warn('promoteWarn', '');
        SL.fillSelect($('promoteNote'), books.map(function (d) { return { value: String(d.id), label: d.title }; }), String(books[0].id));
        SL.open('promoteOverlay');
    }

    async function savePromote() {
        var noteId = +$('promoteNote').value;
        var r = await SL.post('/api/solutions/notes/' + noteId + '/steps/from-log', { logId: mPromoteLogId });
        if (!r || !r.ok) { SL.warn('promoteWarn', SL.failText(r, 'The step could not be added.')); return; }
        SL.close('promoteOverlay');
        SL.toast('Added as step ' + r.data.steps.length + ' of "' + r.data.title + '"');
        await loadFirst();
    }

    // ---- the tab -------------------------------------------------------------------------------

    Page.registerTab('log', {
        init: function () {
            ['fLogType', 'fLogAuthor', 'fLogProject', 'fLogOrder'].forEach(function (id) { $(id).addEventListener('change', loadFirst); });
            $('fLogSearch').addEventListener('input', function () {
                clearTimeout(mSearchTimer);
                mSearchTimer = setTimeout(loadFirst, 350);
            });
            $('btnLogExpand').addEventListener('click', function () { foldAll(false); });
            $('btnLogCollapse').addEventListener('click', function () { foldAll(true); });
            $('logCancel').addEventListener('click', function () { SL.close('logOverlay'); });
            $('logSave').addEventListener('click', function () { SL.guard($('logSave'), saveCorrection); });
            attachKindPicker('lType');
            $('promoteCancel').addEventListener('click', function () { SL.close('promoteOverlay'); });
            $('promoteSave').addEventListener('click', function () { SL.guard($('promoteSave'), savePromote); });
        },
        show: function () {
            refreshKindFilter();
            SL.fillSelect($('fLogAuthor'), Page.memberOptions('Anyone'), $('fLogAuthor').value);
            SL.fillSelect($('fLogProject'), Page.projectOptions('All projects'), $('fLogProject').value);
            drawShell();
            // A flash must be able to reach the entry: no filter may hide it.
            if (mFlashId) ['fLogType', 'fLogAuthor', 'fLogProject', 'fLogSearch'].forEach(function (id) { $(id).value = ''; });
            loadFirst().then(function () { if (mFlashId) flashNow(); });
        },
        /** From the Search tab: scroll to one entry, open it and flash it; call before the tab is shown. */
        flash: function (logId) { mFlashId = logId; }
    });

    /** Page on until the entry is here (it may be far down the journal), then open it and flash it. */
    async function flashNow() {
        var id = mFlashId, pages = 0;
        mFlashId = 0;
        function here() { return mEntries.some(function (e) { return e.id === id; }); }
        while (!here() && mEntries.length < mTotal && pages < 40) { pages++; await loadPage(); }
        if (!here()) return;
        Page.fold().set('log:' + id, false);
        drawList();
        var el = $('log-' + id);
        if (!el) return;
        el.classList.add('flash');
        el.scrollIntoView({ block: 'center' });
    }
})();
