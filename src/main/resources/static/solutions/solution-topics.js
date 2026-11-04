(function () {
    'use strict';

    // The Topics tab (2026-11-02). A topic is a named subject that runs across the solution --
    // "Charset", "Remit dupe", "Cathy" -- with where it stands now (its summary). Records join a topic
    // by being linked to it, so a topic's View is its summary followed by every record linked to it,
    // newest first. The list is one card per topic, A to Z; each card folds (remembered in the
    // database), and carries View, Edit and Delete. Search, paging and counting happen on the server.
    //
    // Story 5695 (Echo): a topic has a KIND, and the tab groups them:
    //   - the PARTS of what the solution is made of (the spine): one group per kind of part -- Chapters,
    //     Characters, Accounts, Environments -- in their own order (Arrange drags them), each with a status
    //     and an optional number ("4,200 words");
    //   - ASSUMPTIONS the plan rests on, each holding or proved false. Proved false is the premise switch:
    //     its View lists the open items linked to it and cancels the chosen ones with one reason;
    //   - SUBJECTS (what every topic was before), and TAGS (Important, Risk, Idea ... -- quick flags).
    // Every group folds and is remembered in the database; Show narrows the list to one kind.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PAGE_SIZE = 50, TYPING_PAUSE_MS = 600;
    var KIND_LABEL = { subject: 'Subjects', tag: 'Tags', part: 'Parts', assumption: 'Assumptions' };
    var KIND_ONE = { subject: 'Subject', tag: 'Tag', part: 'Part', assumption: 'Assumption' };

    var mTopics = [], mCounts = {}, mOffset = 0, mMore = false, mLoading = false, mAsk = 0;
    var mTypes = [];
    var mEditId = 0, mView = null, mTimer = null, mFolds = null, mSynced = false, mPremise = null;

    function folds() {
        if (!mFolds) mFolds = window.PicassoGroupState.create({ key: 'leo.sl.topics.' + Page.id, defaultOpen: true });
        return mFolds;
    }

    function filterKey() { return 'sl.topics.filter.' + Page.id; }

    function readFilter() {
        try { var s = JSON.parse(sessionStorage.getItem(filterKey()) || 'null'); if (s && typeof s === 'object') return s; } catch (e) { /* private mode, or an old plain-text value */ }
        return { search: '', kind: '' };
    }

    // ---- load ----------------------------------------------------------------------------------

    async function loadFirst() {
        mTopics = []; mOffset = 0; mMore = false;
        try { sessionStorage.setItem(filterKey(), JSON.stringify({ search: $('fTopicSearch').value, kind: $('fTopicKind').value })); } catch (e) { /* private mode */ }
        await Promise.all([loadTypes(), loadPage()]);
    }

    async function loadTypes() {
        var r = await SL.get('/api/solutions/' + Page.id + '/topic-types');
        mTypes = r && r.ok && Array.isArray(r.data) ? r.data : [];
    }

    async function loadPage() {
        var ask = ++mAsk;
        mLoading = true;
        var q = $('fTopicSearch').value.trim(), kind = $('fTopicKind').value;
        var r = await SL.get('/api/solutions/' + Page.id + '/topics?offset=' + mOffset + '&pageSize=' + PAGE_SIZE +
            (q ? '&search=' + encodeURIComponent(q) : '') + (kind ? '&kind=' + encodeURIComponent(kind) : ''));
        if (ask !== mAsk) return;
        mLoading = false;
        if (!r || !r.ok || !r.data) {
            $('topicList').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the topics.')) + '</div>';
            return;
        }
        var page = r.data.topics || [];
        mTopics = mTopics.concat(page);
        mCounts = r.data.counts || {};
        mOffset += page.length;
        mMore = mTopics.length < (r.data.total || 0);
        draw();
    }

    // ---- draw ----------------------------------------------------------------------------------

    function drawShell() {
        $('pane-topics').innerHTML =
            '<div id="topicList" data-testid="topics-list"><div class="sl-empty">Loading&hellip;</div></div>' +
            '<div class="sl-more" id="topicMore" hidden><button type="button" class="sl-btn" id="btnTopicMore" data-testid="topics-more-btn">Load more</button></div>';
        $('btnTopicMore').addEventListener('click', function () { if (!mLoading) loadPage(); });
        $('topicList').addEventListener('click', onListClick);
    }

    /** The groups, in the server's order: each kind of part, then assumptions, subjects, tags. */
    function groups() {
        var out = [], byKey = {};
        mTopics.forEach(function (t) {
            var key = t.kindCode === 'part' ? 'part:' + (t.typeId || 0) : t.kindCode;
            if (!byKey[key]) {
                var title = t.kindCode === 'part' ? plural(t.typeName || 'Part') : KIND_LABEL[t.kindCode] || 'Topics';
                byKey[key] = { key: key, kind: t.kindCode, typeId: t.typeId || 0, title: title, topics: [] };
                out.push(byKey[key]);
            }
            byKey[key].topics.push(t);
        });
        return out;
    }

    function plural(name) {
        var n = String(name || '');
        if (/[^aeiou]y$/i.test(n)) return n.slice(0, -1) + 'ies';
        if (/(s|x|ch|sh)$/i.test(n)) return n + 'es';
        return n + 's';
    }

    function draw() {
        var list = $('topicList');
        if (!list) return;
        var all = mCounts.all || 0, total = mCounts.total || 0;
        var gs = groups();
        // One group (or a narrowed list) is drawn without a group head: nothing to tell apart.
        var heads = gs.length > 1;
        list.innerHTML = mTopics.length ? gs.map(function (g) { return group(g, heads); }).join('')
            : '<div class="sl-empty">' + (all ? 'No topic matches.' : 'No topics yet.') + '</div>';
        $('topicMore').hidden = !mMore;
        drawFoldButton(gs);
        var filtered = $('fTopicSearch').value.trim() || $('fTopicKind').value;
        $('topicToolsSummary').textContent = total + (total === 1 ? ' topic' : ' topics') + (filtered ? ' shown (' + all + ' in all)' : '');
        SL.status(total + (total === 1 ? ' topic' : ' topics'));
        Array.prototype.forEach.call(list.querySelectorAll('[data-topic-grip]'), function (g) {
            Page.wireProjectGrip(g, '[data-topic-card="' + g.getAttribute('data-topic-grip') + '"]', 'data-topic', null, function (ids) { reorder(ids); });
        });
    }

    function group(g, head) {
        var open = !head || folds().isOpen('grp:' + g.key);
        var html = head
            ? '<div class="sl-tgroup' + (open ? '' : ' folded') + '" data-tgroup="' + esc(g.key) + '" data-testid="topics-group">' +
              '<button type="button" class="sl-tgroup-head" data-act="group" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="topics-group-toggle">' +
                '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span><span class="sl-tgroup-title">' + esc(g.title) + '</span>' +
                '<span class="sl-kind-count" data-testid="topics-group-count">' + groupCount(g) + '</span></button>'
            : '<div class="sl-tgroup sl-tgroup-flat" data-tgroup="' + esc(g.key) + '" data-testid="topics-group">';
        if (open) html += '<div class="sl-tgroup-body">' + g.topics.map(function (t) { return card(t, g); }).join('') + '</div>';
        return html + '</div>';
    }

    /** A group's count comes from the server, never from what this page happens to have loaded so far:
        a kind of part counts its parts, any other group the kind's count under the search. */
    function groupCount(g) {
        if (g.kind === 'part' && !$('fTopicSearch').value.trim()) {
            var y = mTypes.filter(function (x) { return x.id === g.typeId; })[0];
            if (y) return y.partCount;
        }
        var n = mCounts['kind:' + g.kind];
        return g.kind !== 'part' && n != null ? n : g.topics.length + (mMore ? '+' : '');
    }

    function figureText(t) {
        if (t.figure == null) return '';
        var n = Number(t.figure), s = n % 1 === 0 ? n.toLocaleString('en-US') : n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        return s + (t.figureLabel ? ' ' + t.figureLabel : '');
    }

    function badges(t) {
        if (t.kindCode === 'part') {
            return (t.statusName ? SL.pill(t.statusName, t.colorHex) : '') +
                (t.figure != null ? '<span class="sl-chip" data-testid="topic-card-figure">' + esc(figureText(t)) + '</span>' : '');
        }
        if (t.kindCode === 'assumption') {
            return t.holds === false ? '<span class="sl-chip warn" data-testid="topic-card-holds">Proved false</span>'
                                     : '<span class="sl-chip resolved" data-testid="topic-card-holds">Holds</span>';
        }
        if (t.kindCode === 'tag') return '<span class="sl-chip" data-testid="topic-card-tag">Tag</span>';
        return '';
    }

    function card(t, g) {
        var open = folds().isOpen(String(t.id));
        var meta = t.memberCount + (t.memberCount === 1 ? ' record' : ' records') +
            (t.latestDate ? ' | newest ' + SL.date(t.latestDate) : '') +
            ' | changed ' + SL.date(t.modifiedDate || t.createdDate);
        var grip = t.kindCode === 'part'
            ? '<span class="sl-grip small sl-arrange-only" data-topic-grip="' + esc(g.key) + '" title="Drag to move this part" aria-label="Drag to move ' + esc(t.name) + '" data-testid="topic-grip">&#8942;&#8942;</span>' : '';
        return '<div class="sl-topic' + (open ? '' : ' folded') + (t.closed || t.holds === false ? ' sl-topic-closed' : '') + '" data-topic="' + t.id + '"' +
                (t.kindCode === 'part' ? ' data-topic-card="' + esc(g.key) + '"' : '') + ' data-testid="topic-card">' +
            '<div class="sl-topic-head">' + grip +
                '<button type="button" class="sl-topic-name" data-act="fold" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="topic-card-toggle">' +
                    '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span><span data-testid="topic-card-name">' + esc(t.name) + '</span>' +
                    '<span class="sl-chip" data-testid="topic-card-count">' + t.memberCount + '</span>' + badges(t) + '</button>' +
                '<span class="tools">' +
                    '<button type="button" class="sl-iconbtn" data-act="view" title="View" aria-label="View" data-testid="topic-view-btn">&#128065;</button>' +
                    '<button type="button" class="sl-iconbtn" data-act="edit" title="Edit" aria-label="Edit" data-testid="topic-edit-btn">&#9998;</button>' +
                    '<button type="button" class="sl-iconbtn" data-act="delete" title="Delete" aria-label="Delete" data-testid="topic-delete-btn">&#10005;</button>' +
                '</span>' +
            '</div>' +
            (open ? '<div class="sl-topic-body">' +
                (t.summary ? '<div class="sl-md" data-testid="topic-card-summary">' + SL.md(t.summary, t.truncated) + '</div>' : '') +
                '<div class="sl-muted" data-testid="topic-card-meta">' + esc(meta) + '</div></div>' : '') +
        '</div>';
    }

    function topicById(id) { return mTopics.filter(function (t) { return t.id === id; })[0] || null; }

    function onListClick(e) {
        var b = e.target.closest('[data-act]');
        if (!b) return;
        var act = b.getAttribute('data-act');
        if (act === 'group') { folds().toggle('grp:' + b.closest('[data-tgroup]').getAttribute('data-tgroup')); draw(); return; }
        var id = +b.closest('[data-topic]').getAttribute('data-topic');
        if (act === 'fold') { folds().toggle(String(id)); draw(); }
        else if (act === 'view') openTopic(id);
        else if (act === 'edit') openEdit(id);
        else if (act === 'delete') remove(id);
    }

    function drawFoldButton(gs) {
        var b = $('btnTopicsFold');
        var keys = gs.length > 1 ? gs.map(function (g) { return 'grp:' + g.key; }) : mTopics.map(function (t) { return String(t.id); });
        b.hidden = keys.length < 2;
        var anyOpen = keys.some(function (k) { return folds().isOpen(k); });
        b.textContent = anyOpen ? 'Collapse all' : 'Expand all';
    }

    function foldAll() {
        if ($('btnTopicsFold').textContent === 'Collapse all') folds().collapseAll(); else folds().expandAll();
        draw();
    }

    /** Arrange: the parts of one kind in their new order. */
    async function reorder(ids) {
        var r = await SL.put('/api/solutions/' + Page.id + '/topics/order', { ids: ids });
        if (!r || !r.ok) window.Picasso.error(SL.failText(r, 'The new order could not be saved.'));
        await loadFirst();
    }

    // ---- view: the sepia page, its summary, then every record linked to it -----------------------

    async function openTopic(id) {
        var r = await SL.get('/api/solutions/topics/' + id);
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'That topic could not be opened.')); return; }
        showView(r.data);
    }

    function viewMeta(t) {
        var bits = [];
        if (t.kindCode === 'part') bits.push((t.typeName || 'Part') + (t.statusName ? ', ' + t.statusName : '') + (t.figure != null ? ', ' + figureText(t) : ''));
        else if (t.kindCode === 'assumption') bits.push(t.holds === false ? 'Assumption, proved false' : 'Assumption, holds');
        else if (t.kindCode === 'tag') bits.push('Tag');
        bits.push(t.memberCount + (t.memberCount === 1 ? ' record' : ' records'));
        bits.push('made by ' + (t.authorName || t.authorCode || 'unknown') + ' ' + SL.date(t.createdDate) +
            (t.modifiedDate ? ', changed ' + SL.date(t.modifiedDate) + (t.modifiedBy ? ' by ' + t.modifiedBy : '') : ''));
        return bits.join(' | ');
    }

    function showView(t) {
        mView = t;
        $('topicViewTitle').textContent = t.name;
        $('topicViewMeta').textContent = viewMeta(t);
        if (t.summary) SL.renderMarkdown($('topicViewBody'), t.summary);
        else $('topicViewBody').innerHTML = '<p class="sl-sepia-empty">' + (t.kindCode === 'assumption' ? 'What it rests on is not written yet.' : 'Where it stands is not written yet.') + '</p>';
        $('topicViewFalse').hidden = !(t.kindCode === 'assumption' && t.holds !== false);
        $('topicViewHolds').hidden = !(t.kindCode === 'assumption' && t.holds === false);
        SL.open('topicViewOverlay');
        $('topicViewScroll').scrollTop = 0;
        window.SolutionLinks.mount($('topicViewLinks'), 'topic', t.id, t.name);
    }

    function closeView() { SL.close('topicViewOverlay'); mView = null; }

    // ---- the premise switch -----------------------------------------------------------------------

    async function openPremise() {
        if (!mView) return;
        var t = mView;
        var r = await SL.get('/api/solutions/topics/' + t.id + '/open-items');
        if (!r || !r.ok || !Array.isArray(r.data)) { window.Picasso.error(SL.failText(r, 'The items resting on it could not be read.')); return; }
        mPremise = { topic: t, items: r.data };
        $('premiseTitle').textContent = 'Proved false: ' + t.name;
        SL.warn('premiseWarn', '');
        $('premiseReason').value = '';
        $('premiseItems').innerHTML = r.data.length
            ? '<div class="sl-muted" style="margin-bottom:4px">These open items are linked to it. The ticked ones are cancelled (Skipped).</div>' +
              r.data.map(function (i) {
                  return '<label class="sl-dq-open-row" data-testid="premise-item"><input type="checkbox" checked data-premise-item="' + i.id + '" data-testid="premise-item-check">' +
                      '<span class="sl-kind item">Item</span><span class="sl-link-title">' + esc(SL.plain(i.title || '', 160)) + '</span>' +
                      (i.sub ? '<span class="sl-link-sub">' + esc(i.sub) + '</span>' : '') + '</label>';
              }).join('')
            : '<div class="sl-muted" data-testid="premise-none">No open item is linked to it. Link the items that rest on it first, or mark it false on its own.</div>';
        SL.open('premiseOverlay');
        $('premiseReason').focus();
    }

    async function savePremise() {
        if (!mPremise) return;
        var why = $('premiseReason').value.trim();
        if (!why) { SL.warn('premiseWarn', 'Say why it no longer holds. The journal keeps the reason.'); $('premiseReason').focus(); return; }
        var ids = Array.prototype.filter.call(document.querySelectorAll('[data-premise-item]'), function (b) { return b.checked; })
            .map(function (b) { return +b.getAttribute('data-premise-item'); });
        var t = mPremise.topic;
        var ok = await window.Picasso.yesNo((ids.length ? ids.length + (ids.length === 1 ? ' item is' : ' items are') + ' cancelled (Skipped). ' : 'No item is cancelled. ') +
            'One log entry records the reason.', { title: 'Mark Assumption: ' + SL.plain(t.name, 80) + ' as proved false?' });
        if (!ok) return;
        var r = await SL.post('/api/solutions/topics/' + t.id + '/proved-false', { reason: why, itemIds: ids });
        if (!r || !r.ok || !r.data) { SL.warn('premiseWarn', SL.failText(r, 'Nothing was changed.')); return; }
        SL.close('premiseOverlay');
        mPremise = null;
        showView(r.data.topic);
        if (Page.active === 'topics') loadFirst();
        await Page.reload();
    }

    async function holdsAgain() {
        if (!mView) return;
        var t = mView;
        var ok = await window.Picasso.yesNo('Items cancelled when it was proved false stay as they are.', { title: 'Mark Assumption: ' + SL.plain(t.name, 80) + ' as holding again?' });
        if (!ok) return;
        var r = await SL.put('/api/solutions/topics/' + t.id, { holds: true });
        if (!r || !r.ok || !r.data) { window.Picasso.error(SL.failText(r, 'Nothing was changed.')); return; }
        showView(r.data);
        if (Page.active === 'topics') loadFirst();
    }

    // ---- add / edit / delete ----------------------------------------------------------------------

    function showKindFields() {
        var kind = $('tpKind').value;
        $('tpPartFields').hidden = kind !== 'part';
        var type = mTypes.filter(function (y) { return y.name.toLowerCase() === $('tpType').value.trim().toLowerCase(); })[0];
        $('tpFigureLabel').textContent = type && type.figureLabel ? 'Number (' + type.figureLabel + ')' : 'Number';
    }

    function prepareEditor(t, kind) {
        SL.warn('topicWarn', '');
        $('topicTitleBar').textContent = t ? 'Edit topic' : 'New topic';
        $('tpName').value = t ? t.name : '';
        $('tpSummary').value = t ? (t.summary || '') : '';
        SL.fillSelect($('tpKind'), [{ value: 'subject', label: 'Subject: one subject that runs across the solution' },
                                    { value: 'part', label: 'Part: a piece of what the solution is made of' },
                                    { value: 'assumption', label: 'Assumption: something the plan rests on' },
                                    { value: 'tag', label: 'Tag: a quick flag, like Important or Risk' }],
            t ? t.kindCode : (kind || 'subject'));
        $('tpType').value = t && t.typeName ? t.typeName : '';
        SL.fillSelect($('tpStatus'), [{ value: '', label: 'No status' }].concat(Page.statusOptions()), t ? (t.statusCode || '') : '');
        $('tpFigure').value = t && t.figure != null ? String(t.figure) : '';
        showKindFields();
        SL.open('topicOverlay');
        if (window.Picasso && window.Picasso.autosizeTextarea) window.Picasso.autosizeTextarea($('tpSummary'));
        $('tpName').focus();
    }

    function openAdd() { mEditId = 0; prepareEditor(null, $('fTopicKind').value); }

    async function openEdit(id) {
        var r = await SL.get('/api/solutions/topics/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That topic could not be opened.')); return; }
        mEditId = id;
        prepareEditor(r.data);
    }

    async function save() {
        var name = $('tpName').value.trim();
        if (!name) { SL.warn('topicWarn', 'A topic needs a name.'); $('tpName').focus(); return; }
        var kind = $('tpKind').value;
        var body = { name: name, summary: $('tpSummary').value, kind: kind };
        if (kind === 'part') {
            var fig = $('tpFigure').value.replace(/[$,\s]/g, '');
            if (fig && !/^-?\d+(\.\d+)?$/.test(fig)) { SL.warn('topicWarn', 'The number should look like 4200 or 1250.50.'); $('tpFigure').focus(); return; }
            body.typeName = $('tpType').value.trim();
            body.statusCode = $('tpStatus').value;
            if (fig) body.figure = +fig; else if (mEditId) body.clearFigure = true;
        }
        var r = mEditId > 0 ? await SL.put('/api/solutions/topics/' + mEditId, body)
                            : await SL.post('/api/solutions/' + Page.id + '/topics', body);
        if (!r || !r.ok) { SL.warn('topicWarn', SL.failText(r, 'The topic could not be saved.')); return; }
        SL.close('topicOverlay');
        if (mView && mView.id === r.data.id && $('topicViewOverlay').classList.contains('open')) showView(r.data);
        if (Page.active === 'topics') await loadFirst();
    }

    async function remove(id) {
        var t = topicById(id) || (mView && mView.id === id ? mView : null);
        if (!t) return;
        var n = t.memberCount || 0;
        var ok = await window.Picasso.confirmDelete(
            (n ? 'Its ' + n + (n === 1 ? ' link goes' : ' links go') + ' with it. The records stay.' : 'Nothing is linked to it.') +
            (t.summary ? '\n"' + SL.plain(t.summary, 120) + '"' : '') + '\n\nThis cannot be undone.',
            { title: 'Delete ' + (KIND_ONE[t.kindCode] === 'Subject' ? 'Topic' : KIND_ONE[t.kindCode] || 'Topic') + ': ' + t.name + '?' });
        if (!ok) return;
        var r = await SL.del('/api/solutions/topics/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The topic could not be deleted.')); return; }
        if (mView && mView.id === id) closeView();
        await loadFirst();
    }

    // ---- the tab ----------------------------------------------------------------------------------

    Page.registerTab('topics', {
        init: function () {
            $('btnAddTopic').addEventListener('click', openAdd);
            $('btnTopicsFold').addEventListener('click', foldAll);
            $('fTopicSearch').addEventListener('input', function () { clearTimeout(mTimer); mTimer = setTimeout(loadFirst, TYPING_PAUSE_MS); });
            $('fTopicSearch').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); clearTimeout(mTimer); loadFirst(); } });
            $('fTopicKind').addEventListener('change', loadFirst);
            window.Picasso.rollup.attach({ key: 'leo.sl.topics.tools.collapsed', pane: $('toolbar-topics'), toggle: $('topicToolsToggle') });

            $('tpKind').addEventListener('change', showKindFields);
            $('tpType').addEventListener('input', showKindFields);
            if (window.PicassoPicker) window.PicassoPicker.attach({ input: $('tpType'), dropdown: $('tpTypeDD'), allowNew: true, newLabel: 'Add',
                getItems: function () { return mTypes.map(function (y) { return y.name; }); }, onSelect: showKindFields });
            $('topicCancel').addEventListener('click', function () { SL.close('topicOverlay'); });
            $('topicSave').addEventListener('click', function () { SL.guard($('topicSave'), save); });
            $('topicViewClose').addEventListener('click', closeView);
            $('topicViewEdit').addEventListener('click', function () { if (mView) { var id = mView.id; closeView(); openEdit(id); } });
            $('topicViewDelete').addEventListener('click', function () { if (mView) remove(mView.id); });
            $('topicViewFalse').addEventListener('click', function () { SL.guard($('topicViewFalse'), openPremise); });
            $('topicViewHolds').addEventListener('click', function () { SL.guard($('topicViewHolds'), holdsAgain); });
            $('premiseCancel').addEventListener('click', function () { SL.close('premiseOverlay'); mPremise = null; });
            $('premiseSave').addEventListener('click', function () { SL.guard($('premiseSave'), savePremise); });
            document.addEventListener('keydown', function (e) {
                if (e.key !== 'Escape') return;
                if ($('linkOverlay').classList.contains('open')) return;   // the picker closes first
                if ($('premiseOverlay').classList.contains('open')) { SL.close('premiseOverlay'); mPremise = null; }
                else if ($('topicOverlay').classList.contains('open')) SL.close('topicOverlay');
                else if ($('topicViewOverlay').classList.contains('open')) closeView();
            });
        },
        show: async function () {
            drawShell();
            var f = readFilter();
            $('fTopicSearch').value = f.search || '';
            SL.fillSelect($('fTopicKind'), [{ value: '', label: 'Every kind' }, { value: 'part', label: 'Parts' }, { value: 'assumption', label: 'Assumptions' },
                                            { value: 'subject', label: 'Subjects' }, { value: 'tag', label: 'Tags' }], f.kind || '');
            if (!mSynced) { mSynced = true; try { await folds().sync(); } catch (e) { /* the device copy stands */ } }
            await loadFirst();
        },
        /** A deep link (?topic=), the Overview or a link elsewhere: show one topic. */
        openTopic: openTopic,
        /** A link was made or taken away somewhere on the page: the counts may have moved. */
        changed: function () { if (Page.active === 'topics') loadFirst(); }
    });
})();
