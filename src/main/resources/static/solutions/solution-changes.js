(function () {
    'use strict';

    // Release 5 of the Solutions upgrade (Story 5695, Echo first). From the deep review of 11/02:
    //   CHANGE MANY AT ONCE. The Items tab's Select picks rows; the bar under the tool row changes one thing on all
    //   of them (status, project, who has it, due date, priority, a topic), with a reason that goes in the one log
    //   entry it writes, linked to every item. "The dead 837 path was cancelled by hand in 20 items."
    //   MAKE INTO. A thread message, a log entry, a note or an item becomes another kind of record without retyping;
    //   the source stays as it was and the two are linked.
    //   SAVED VIEWS. The Items tab's filters (and Projects or Now), named and kept per person: one tap.
    //   FILE VERSIONS. A file's View can take a New version: the file keeps its links and title, the old bytes stay
    //   downloadable under Earlier versions.
    //
    //   SolutionChanges.selecting()            true while the Items tab is picking rows
    //   SolutionChanges.toggleSelect()         Select on or off
    //   SolutionChanges.promote(kind, id, to)  the Make into sheet ('to' preselects item | note | question)
    //   SolutionChanges.loadViews()            fill the Views dropdown for this solution
    //   SolutionChanges.fileVersions(fileId, host)   the Earlier versions section of a file View
    //   SolutionChanges.newVersion(fileId)     upload a new version

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;
    var FIELDS = [{ value: 'status', label: 'Status' }, { value: 'project', label: 'Project' }, { value: 'assignee', label: 'Who has it' },
                  { value: 'due', label: 'Due date' }, { value: 'priority', label: 'Priority' }, { value: 'topic', label: 'Topic' }];
    var TARGETS = { message: ['item', 'note', 'question'], log: ['item', 'note'], note: ['item'], item: ['note'] };
    var TARGET_NAME = { item: 'An item', note: 'A note', question: 'A question' };
    var SOURCE_NAME = { message: 'this message', log: 'this log entry', note: 'this note', item: 'this item' };

    var mSelecting = false, mPicked = {}, mTopics = null, mMake = null, mViews = [], mViewsFor = 0, mVersionFile = 0;

    // ---- change many at once ---------------------------------------------------------------------

    function selecting() { return mSelecting; }
    function isPicked(id) { return !!mPicked[id]; }

    function toggleSelect(on) {
        mSelecting = on == null ? !mSelecting : !!on;
        if (!mSelecting) mPicked = {};
        $('btnItemsSelect').classList.toggle('on', mSelecting);
        $('btnItemsSelect').setAttribute('aria-pressed', mSelecting ? 'true' : 'false');
        $('itemBulkBar').hidden = !mSelecting;
        if (mSelecting) { SL.fillSelect($('bulkField'), FIELDS, $('bulkField').value || 'status'); fillValue(); }
        Page.tabs.items.redraw();
        count();
    }

    function picked() {
        return Object.keys(mPicked).map(Number);
    }

    function count() {
        var n = picked().length;
        $('bulkCount').textContent = n + ' chosen';
        $('bulkGo').disabled = n === 0;
    }

    async function fillValue() {
        var f = $('bulkField').value, host = $('bulkValueHost');
        if (f === 'due') {
            host.innerHTML = '<input type="date" id="bulkValue" data-testid="bulk-value-date" aria-label="The due date (empty takes it off)">';
            return;
        }
        host.innerHTML = '<select id="bulkValue" data-testid="bulk-value" aria-label="Change it to"></select>';
        var opts = [];
        if (f === 'status') opts = Page.statusOptions();
        if (f === 'project') opts = Page.projectOptions(null);
        if (f === 'assignee') opts = Page.memberOptions('Nobody');
        if (f === 'priority') opts = [1, 2, 3, 4, 5].map(function (p) { return { value: String(p), label: 'P' + p + (p === 3 ? ' (the usual)' : '') }; });
        if (f === 'topic') {
            if (!mTopics || mTopics.sid !== Page.id) {
                var r = await SL.get('/api/solutions/' + Page.id + '/topics?pageSize=500');
                mTopics = { sid: Page.id, rows: r && r.ok && r.data ? r.data.topics || [] : [] };
            }
            opts = mTopics.rows.map(function (t) { return { value: String(t.id), label: t.name }; });
            if (!opts.length) { host.innerHTML = '<span class="sl-muted">This solution has no topics yet.</span>'; return; }
        }
        SL.fillSelect($('bulkValue'), opts, opts.length ? opts[0].value : '');
    }

    function describe(field) {
        var el = $('bulkValue');
        if (!el) return null;
        if (field === 'due') return el.value ? 'due ' + el.value : 'their due date taken off';
        var o = el.options[el.selectedIndex];
        var label = o ? o.textContent : '';
        if (field === 'assignee') return el.value ? 'given to ' + label : 'given to nobody';
        if (field === 'status') return 'set to ' + label;
        if (field === 'project') return 'moved to the project ' + label + ' (sub-items go with them)';
        if (field === 'priority') return 'set to ' + label;
        return 'linked to the topic ' + label;
    }

    async function change() {
        var ids = picked(), field = $('bulkField').value, why = $('bulkReason').value.trim();
        SL.warn('bulkWarn', '');
        if (!ids.length) { SL.warn('bulkWarn', 'Tick the items to change first.'); return; }
        if (!$('bulkValue')) { SL.warn('bulkWarn', 'There is nothing to change it to.'); return; }
        if (!why) { SL.warn('bulkWarn', 'Say why. The reason goes in the log.'); $('bulkReason').focus(); return; }
        var ok = await window.Picasso.yesNo('One log entry records it, with your reason, linked to every item changed.',
            { title: 'Change ' + ids.length + (ids.length === 1 ? ' item' : ' items') + ': ' + describe(field) + '?' });
        if (!ok) return;
        var r = await SL.post('/api/solutions/' + Page.id + '/items/bulk', { itemIds: ids, field: field, value: $('bulkValue').value, reason: why });
        if (!r || !r.ok || !r.data) { SL.warn('bulkWarn', SL.failText(r, 'Nothing was changed.')); return; }
        $('bulkReason').value = '';
        toggleSelect(false);
        await Page.reload();
        Page.tabs.items.show();
        window.Picasso.message(r.data.itemCount
            ? 'Changed ' + r.data.itemCount + (r.data.itemCount === 1 ? ' item' : ' items') + '. The log says what and why.'
            : 'Every item chosen was already that way, so nothing was changed.');
    }

    // ---- make into --------------------------------------------------------------------------------

    function promote(kind, id, to) {
        var targets = TARGETS[kind] || [];
        if (!targets.length) return;
        mMake = { kind: kind, id: id, to: targets.indexOf(to) >= 0 ? to : targets[0] };
        $('makeTitleHead').textContent = 'Make ' + SOURCE_NAME[kind] + ' into';
        $('makeTargets').innerHTML = targets.map(function (t) {
            return '<button type="button" role="tab" data-make-to="' + t + '" data-testid="make-to-' + t + '">' + TARGET_NAME[t] + '</button>';
        }).join('');
        Array.prototype.forEach.call(document.querySelectorAll('[data-make-to]'), function (b) {
            b.addEventListener('click', function () { mMake.to = b.getAttribute('data-make-to'); paintMake(); });
        });
        $('makeName').value = '';
        SL.warn('makeWarn', '');
        paintMake();
        SL.open('makeOverlay');
    }

    function paintMake() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-make-to]'), function (b) {
            var on = b.getAttribute('data-make-to') === mMake.to;
            b.classList.toggle('on', on); b.setAttribute('aria-selected', on ? 'true' : 'false');
        });
        var needsProject = mMake.to === 'item', mayProject = mMake.to !== 'question';
        $('makeProjectField').hidden = !mayProject;
        SL.fillSelect($('makeProject'), Page.projectOptions(needsProject && mMake.kind === 'message' ? null : (needsProject ? 'Its own project' : 'No project')),
            $('makeProject').value || '');
        $('makeHint').textContent = mMake.to === 'question'
            ? 'A question, open, with the whole text as its detail.'
            : 'The whole text goes in the new ' + (mMake.to === 'item' ? 'item\'s detail' : 'note') + '. ' +
              (mMake.kind === 'message' ? 'It says who wrote the message and when.' : 'The two are linked.');
    }

    async function saveMake() {
        var body = { sourceKind: mMake.kind, sourceId: mMake.id, targetKind: mMake.to, title: $('makeName').value.trim() || null };
        if (mMake.to !== 'question' && $('makeProject').value) body.projectId = +$('makeProject').value;
        var r = await SL.post('/api/solutions/' + Page.id + '/promote', body);
        if (!r || !r.ok || !r.data) { SL.warn('makeWarn', SL.failText(r, 'Nothing was added.')); return; }
        SL.close('makeOverlay');
        await Page.reload();
        window.SolutionLinks.openRecord(r.data.kind, r.data.id);
    }

    // ---- saved views ------------------------------------------------------------------------------

    async function loadViews() {
        var r = await SL.get('/api/solutions/' + Page.id + '/views?scope=items');
        mViews = r && r.ok && Array.isArray(r.data) ? r.data : [];
        mViewsFor = Page.id;
        paintViews('');
    }

    function paintViews(chosen) {
        SL.fillSelect($('fItemView'), [{ value: '', label: 'Views' }].concat(mViews.map(function (v) { return { value: String(v.id), label: v.name }; })), chosen);
        $('btnItemsViewDelete').hidden = !chosen;
    }

    function applyView() {
        var id = +$('fItemView').value;
        $('btnItemsViewDelete').hidden = !id;
        var v = mViews.filter(function (x) { return x.id === id; })[0];
        if (!v) return;
        var f = {};
        try { f = JSON.parse(v.filter) || {}; } catch (e) { window.Picasso.error('That view could not be read.'); return; }
        Page.tabs.items.applyView(f);
    }

    function openSaveView() {
        var chosen = mViews.filter(function (x) { return x.id === +$('fItemView').value; })[0];
        $('svName').value = chosen ? chosen.name : '';
        SL.warn('svWarn', '');
        SL.open('saveViewOverlay');
        $('svName').focus();
    }

    async function saveView() {
        var name = $('svName').value.trim();
        if (!name) { SL.warn('svWarn', 'A view needs a name.'); $('svName').focus(); return; }
        var r = await SL.post('/api/solutions/' + Page.id + '/views', { scope: 'items', name: name, filter: JSON.stringify(Page.tabs.items.viewState()) });
        if (!r || !r.ok || !Array.isArray(r.data)) { SL.warn('svWarn', SL.failText(r, 'The view was not saved.')); return; }
        SL.close('saveViewOverlay');
        mViews = r.data;
        var mine = mViews.filter(function (v) { return v.name.toLowerCase() === name.toLowerCase(); })[0];
        paintViews(mine ? String(mine.id) : '');
    }

    async function deleteView() {
        var id = +$('fItemView').value;
        var v = mViews.filter(function (x) { return x.id === id; })[0];
        if (!v) return;
        var ok = await window.Picasso.confirmDelete('Your saved view goes. The items are not touched.\n\nThis cannot be undone.',
            { title: 'Delete View: ' + v.name + '?' });
        if (!ok) return;
        var r = await SL.del('/api/solutions/views/' + id);
        if (!r || !r.ok) { window.Picasso.error(SL.failText(r, 'The view was not deleted.')); return; }
        await loadViews();
    }

    // ---- file versions ----------------------------------------------------------------------------

    async function fileVersions(fileId, host) {
        host.innerHTML = '';
        var r = await SL.get('/api/solutions/files/' + fileId + '/versions');
        if (!r || !r.ok || !r.data || !host.isConnected) return;
        var d = r.data;
        host.innerHTML = '<div class="sl-fv-versions" data-testid="file-view-versions">' +
            '<div class="sl-fv-versions-head"><strong>Version ' + d.currentVersion + '</strong>' +
            (d.versions.length ? ' <span class="sl-muted">| ' + d.versions.length + (d.versions.length === 1 ? ' earlier version' : ' earlier versions') + '</span>' : '') + '</div>' +
            d.versions.map(function (v) {
                return '<div class="sl-fv-version" data-testid="file-version-row">' +
                    '<a href="#" data-version-get="' + v.id + '" data-version-name="' + esc(v.originalFileName || 'file') + '" data-testid="file-version-download">Version ' + v.versionNumber + '</a> ' +
                    '<span class="sl-muted">' + esc([v.originalFileName, size(v.sizeBytes), SL.dateTime(v.uploadedDate), v.uploadedByName].filter(Boolean).join(' | ')) + '</span>' +
                    (v.changeNote ? '<div class="sl-fv-change">Then: ' + esc(v.changeNote) + '</div>' : '') + '</div>';
            }).join('') + '</div>';
        Array.prototype.forEach.call(host.querySelectorAll('[data-version-get]'), function (a) {
            a.addEventListener('click', function (e) { e.preventDefault(); download(+a.getAttribute('data-version-get'), a.getAttribute('data-version-name')); });
        });
    }

    function size(n) { return n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : n >= 1024 ? Math.round(n / 1024) + ' KB' : (n || 0) + ' bytes'; }

    async function download(versionId, name) {
        var r = await window.LWAuth.apiGetForBlob('/api/solutions/files/versions/' + versionId + '/content');
        if (!r || !r.ok || !r.blob) { window.Picasso.error('That version could not be downloaded.'); return; }
        var url = URL.createObjectURL(r.blob);
        var a = document.createElement('a'); a.href = url; a.download = name || 'file';
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 30000);
    }

    function newVersion(fileId) {
        mVersionFile = fileId;
        $('nvFile').value = ''; $('nvNote').value = '';
        SL.warn('nvWarn', '');
        SL.open('newVersionOverlay');
    }

    async function saveVersion() {
        var file = $('nvFile').files && $('nvFile').files[0];
        if (!file) { SL.warn('nvWarn', 'Choose the file.'); return; }
        var fd = new FormData();
        fd.append('file', file);
        if ($('nvNote').value.trim()) fd.append('changeNote', $('nvNote').value.trim());
        var r = await window.LWAuth.apiPostForm('/api/solutions/files/' + mVersionFile + '/versions', fd);
        if (!r || !r.ok) { SL.warn('nvWarn', SL.failText(r, 'The new version was not saved.')); return; }
        SL.close('newVersionOverlay');
        if (Page.tabs.files && Page.tabs.files.reopen) await Page.tabs.files.reopen(mVersionFile);
    }

    // ---- wiring -----------------------------------------------------------------------------------

    function init() {
        $('btnItemsSelect').addEventListener('click', function () { toggleSelect(); });
        $('bulkField').addEventListener('change', fillValue);
        $('bulkAll').addEventListener('click', function () {
            Array.prototype.forEach.call(document.querySelectorAll('[data-bulk-pick]'), function (b) {
                if (b.offsetParent !== null) { b.checked = true; mPicked[+b.getAttribute('data-bulk-pick')] = true; }
            });
            count();
        });
        $('bulkNone').addEventListener('click', function () {
            Array.prototype.forEach.call(document.querySelectorAll('[data-bulk-pick]'), function (b) { b.checked = false; });
            mPicked = {};
            count();
        });
        $('bulkGo').addEventListener('click', function () { SL.guard($('bulkGo'), change); });
        $('bulkDone').addEventListener('click', function () { toggleSelect(false); });
        document.addEventListener('change', function (e) {
            if (!e.target.matches || !e.target.matches('[data-bulk-pick]')) return;
            var id = +e.target.getAttribute('data-bulk-pick');
            if (e.target.checked) mPicked[id] = true; else delete mPicked[id];
            count();
        });
        document.addEventListener('click', function (e) { if (e.target.matches && e.target.matches('[data-bulk-pick]')) e.stopPropagation(); }, true);

        $('makeCancel').addEventListener('click', function () { SL.close('makeOverlay'); });
        $('makeSave').addEventListener('click', function () { SL.guard($('makeSave'), saveMake); });

        $('fItemView').addEventListener('change', applyView);
        $('btnItemsViewSave').addEventListener('click', openSaveView);
        $('svCancel').addEventListener('click', function () { SL.close('saveViewOverlay'); });
        $('svSave').addEventListener('click', function () { SL.guard($('svSave'), saveView); });
        $('btnItemsViewDelete').addEventListener('click', deleteView);

        $('nvCancel').addEventListener('click', function () { SL.close('newVersionOverlay'); });
        $('nvSave').addEventListener('click', function () { SL.guard($('nvSave'), saveVersion); });

        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            if ($('makeOverlay').classList.contains('open')) SL.close('makeOverlay');
            else if ($('newVersionOverlay').classList.contains('open')) SL.close('newVersionOverlay');
            else if ($('saveViewOverlay').classList.contains('open')) SL.close('saveViewOverlay');
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
    else init();

    window.SolutionChanges = { selecting: selecting, isPicked: isPicked, toggleSelect: toggleSelect, promote: promote, loadViews: loadViews,
                               viewsLoadedFor: function () { return mViewsFor; }, fileVersions: fileVersions, newVersion: newVersion };
})();
