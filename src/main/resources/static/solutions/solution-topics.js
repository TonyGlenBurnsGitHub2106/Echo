(function () {
    'use strict';

    // The Topics tab (2026-11-02). A topic is a named subject that runs across the solution --
    // "Charset", "Remit dupe", "Cathy" -- with where it stands now (its summary). Records join a topic
    // by being linked to it, so a topic's View is its summary followed by every record linked to it,
    // newest first. The list is one card per topic, A to Z; each card folds (remembered in the
    // database), and carries View, Edit and Delete. Search, paging and counting happen on the server.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PAGE_SIZE = 50, TYPING_PAUSE_MS = 600;

    var mTopics = [], mCounts = {}, mOffset = 0, mMore = false, mLoading = false, mAsk = 0;
    var mEditId = 0, mView = null, mTimer = null, mFolds = null, mSynced = false;

    function folds() {
        if (!mFolds) mFolds = window.PicassoGroupState.create({ key: 'leo.sl.topics.' + Page.id, defaultOpen: true });
        return mFolds;
    }

    function filterKey() { return 'sl.topics.filter.' + Page.id; }

    // ---- load ----------------------------------------------------------------------------------

    async function loadFirst() {
        mTopics = []; mOffset = 0; mMore = false;
        try { sessionStorage.setItem(filterKey(), $('fTopicSearch').value); } catch (e) { /* private mode */ }
        await loadPage();
    }

    async function loadPage() {
        var ask = ++mAsk;
        mLoading = true;
        var q = $('fTopicSearch').value.trim();
        var r = await SL.get('/api/solutions/' + Page.id + '/topics?offset=' + mOffset + '&pageSize=' + PAGE_SIZE +
            (q ? '&search=' + encodeURIComponent(q) : ''));
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

    function draw() {
        var list = $('topicList');
        if (!list) return;
        var all = mCounts.all || 0, total = mCounts.total || 0;
        list.innerHTML = mTopics.length ? mTopics.map(card).join('')
            : '<div class="sl-empty">' + (all ? 'No topic matches the search.' : 'No topics yet.') + '</div>';
        $('topicMore').hidden = !mMore;
        drawFoldButton();
        $('topicToolsSummary').textContent = total + (total === 1 ? ' topic' : ' topics') + ($('fTopicSearch').value.trim() ? ' match (' + all + ' in all)' : '');
        SL.status(total + (total === 1 ? ' topic' : ' topics'));
    }

    function card(t) {
        var open = folds().isOpen(String(t.id));
        var meta = t.memberCount + (t.memberCount === 1 ? ' record' : ' records') +
            (t.latestDate ? ' | newest ' + SL.date(t.latestDate) : '') +
            ' | changed ' + SL.date(t.modifiedDate || t.createdDate);
        return '<div class="sl-topic' + (open ? '' : ' folded') + '" data-topic="' + t.id + '" data-testid="topic-card">' +
            '<div class="sl-topic-head">' +
                '<button type="button" class="sl-topic-name" data-act="fold" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="topic-card-toggle">' +
                    '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span><span data-testid="topic-card-name">' + esc(t.name) + '</span>' +
                    '<span class="sl-chip" data-testid="topic-card-count">' + t.memberCount + '</span></button>' +
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
        var id = +b.closest('[data-topic]').getAttribute('data-topic');
        var act = b.getAttribute('data-act');
        if (act === 'fold') { folds().toggle(String(id)); draw(); }
        else if (act === 'view') openTopic(id);
        else if (act === 'edit') openEdit(id);
        else if (act === 'delete') remove(id);
    }

    function drawFoldButton() {
        var b = $('btnTopicsFold');
        b.hidden = mTopics.length < 2;
        var anyOpen = mTopics.some(function (t) { return folds().isOpen(String(t.id)); });
        b.textContent = anyOpen ? 'Collapse all' : 'Expand all';
    }

    function foldAll() {
        if ($('btnTopicsFold').textContent === 'Collapse all') folds().collapseAll(); else folds().expandAll();
        draw();
    }

    // ---- view: the sepia page, its summary, then every record linked to it -----------------------

    async function openTopic(id) {
        var r = await SL.get('/api/solutions/topics/' + id);
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'That topic could not be opened.')); return; }
        showView(r.data);
    }

    function showView(t) {
        mView = t;
        $('topicViewTitle').textContent = t.name;
        $('topicViewMeta').textContent = t.memberCount + (t.memberCount === 1 ? ' record' : ' records') + ' | ' +
            'made by ' + (t.authorName || t.authorCode || 'unknown') + ' ' + SL.date(t.createdDate) +
            (t.modifiedDate ? ', changed ' + SL.date(t.modifiedDate) + (t.modifiedBy ? ' by ' + t.modifiedBy : '') : '');
        if (t.summary) SL.renderMarkdown($('topicViewBody'), t.summary);
        else $('topicViewBody').innerHTML = '<p class="sl-sepia-empty">Where it stands is not written yet.</p>';
        SL.open('topicViewOverlay');
        $('topicViewScroll').scrollTop = 0;
        window.SolutionLinks.mount($('topicViewLinks'), 'topic', t.id, t.name);
    }

    function closeView() { SL.close('topicViewOverlay'); mView = null; }

    // ---- add / edit / delete ----------------------------------------------------------------------

    function prepareEditor(t) {
        SL.warn('topicWarn', '');
        $('topicTitleBar').textContent = t ? 'Edit topic' : 'New topic';
        $('tpName').value = t ? t.name : '';
        $('tpSummary').value = t ? (t.summary || '') : '';
        SL.open('topicOverlay');
        if (window.Picasso && window.Picasso.autosizeTextarea) window.Picasso.autosizeTextarea($('tpSummary'));
        $('tpName').focus();
    }

    function openAdd() { mEditId = 0; prepareEditor(null); }

    async function openEdit(id) {
        var r = await SL.get('/api/solutions/topics/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That topic could not be opened.')); return; }
        mEditId = id;
        prepareEditor(r.data);
    }

    async function save() {
        var name = $('tpName').value.trim();
        if (!name) { SL.warn('topicWarn', 'A topic needs a name.'); $('tpName').focus(); return; }
        var body = { name: name, summary: $('tpSummary').value };
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
            { title: 'Delete Topic: ' + t.name + '?' });
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
            window.Picasso.rollup.attach({ key: 'leo.sl.topics.tools.collapsed', pane: $('toolbar-topics'), toggle: $('topicToolsToggle') });

            $('topicCancel').addEventListener('click', function () { SL.close('topicOverlay'); });
            $('topicSave').addEventListener('click', function () { SL.guard($('topicSave'), save); });
            $('topicViewClose').addEventListener('click', closeView);
            $('topicViewEdit').addEventListener('click', function () { if (mView) { var id = mView.id; closeView(); openEdit(id); } });
            $('topicViewDelete').addEventListener('click', function () { if (mView) remove(mView.id); });
            document.addEventListener('keydown', function (e) {
                if (e.key !== 'Escape') return;
                if ($('linkOverlay').classList.contains('open')) return;   // the picker closes first
                if ($('topicOverlay').classList.contains('open')) SL.close('topicOverlay');
                else if ($('topicViewOverlay').classList.contains('open')) closeView();
            });
        },
        show: async function () {
            drawShell();
            try { $('fTopicSearch').value = sessionStorage.getItem(filterKey()) || ''; } catch (e) { /* private mode */ }
            if (!mSynced) { mSynced = true; try { await folds().sync(); } catch (e) { /* the device copy stands */ } }
            await loadFirst();
        },
        /** A deep link (?topic=), the Overview or a link elsewhere: show one topic. */
        openTopic: openTopic,
        /** A link was made or taken away somewhere on the page: the counts may have moved. */
        changed: function () { if (Page.active === 'topics') loadFirst(); }
    });
})();
