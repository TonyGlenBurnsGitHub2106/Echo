(function () {
    'use strict';

    // Questions and Decisions (Story 5695, Echo first). From the Solutions deep review of 11/02: "Questions and
    // decisions live apart ... The charset question was settled in log entry 505 on October 23. The items
    // 'Decide 837/835 charset' and 'Review charset findings' are still to do." So a question is a record that
    // stays open until it is answered, and a decision is a record that can CLOSE things -- the questions and
    // items linked to it.
    //
    // No new tab: both are views of the Log tab (the journal is where decisions were already being written),
    // switched by Journal | Questions | Decisions on its tool row (solution-log.js asks this file to draw).
    // Each record has a sepia View (Edit, Delete, and for a decision Replace with a newer one and Close what it
    // settles) and the shared Linked section. The lists page, count and search on the server; each card folds,
    // remembered in the database (PicassoGroupState).
    //
    //   SolutionDecisions.show(kind)        draw the Questions ('question') or Decisions ('decision') list into pane-log
    //   SolutionDecisions.openQuestion(id)  /  openDecision(id)   the record's View
    //   SolutionDecisions.ask(linkTo)       /  decide(linkTo)     the editor for a new one, linked to linkTo [{kind,id}]
    //   SolutionDecisions.fromLog(logId, kind)                    a journal entry becomes a question or a decision

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PAGE_SIZE = 50, TYPING_PAUSE_MS = 500;
    var STATUS = {
        question: { open: ['Open', 'wait'], answered: ['Answered', 'resolved'], dropped: ['Dropped', ''] },
        decision: { proposed: ['Proposed', 'wait'], accepted: ['Accepted', 'resolved'], superseded: ['Replaced', ''] }
    };
    var FILTERS = {
        question: [{ value: 'open', label: 'Open' }, { value: 'answered', label: 'Answered' }, { value: 'dropped', label: 'Dropped' }, { value: '', label: 'Every question' }],
        decision: [{ value: 'current', label: 'Current' }, { value: 'proposed', label: 'Proposed' }, { value: 'accepted', label: 'Accepted' },
                   { value: 'superseded', label: 'Replaced' }, { value: '', label: 'Every decision' }]
    };
    var NOUN = { question: ['question', 'questions'], decision: ['decision', 'decisions'] };

    var mKind = 'question';
    var mList = { question: fresh(), decision: fresh() };
    var mFolds = {}, mSynced = {};
    var mView = null;          // { kind, rec } -- the record in the View sheet
    var mEdit = null;          // { kind, id, supersedes, linkTo }
    var mTimer = null;

    function fresh() { return { rows: [], counts: {}, offset: 0, more: false, ask: 0, loading: false }; }

    function folds(kind) {
        var key = 'leo.sl.' + kind + 's.' + Page.id;
        if (!mFolds[key]) mFolds[key] = window.PicassoGroupState.create({ key: key, defaultOpen: true });
        return mFolds[key];
    }

    function statusChip(kind, code) {
        var s = STATUS[kind][code] || [code, ''];
        return '<span class="sl-chip' + (s[1] ? ' ' + s[1] : '') + '" data-testid="dq-status">' +
            (code === 'answered' || code === 'accepted' ? '&#10003; ' : '') + esc(s[0]) + '</span>';
    }

    function filterKey(kind) { return 'sl.' + kind + 's.filter.' + Page.id; }

    function readFilter(kind) {
        try { var s = JSON.parse(sessionStorage.getItem(filterKey(kind)) || 'null'); if (s) return s; } catch (e) { /* private mode */ }
        return { status: FILTERS[kind][0].value, search: '' };
    }

    function saveFilter(kind) {
        try { sessionStorage.setItem(filterKey(kind), JSON.stringify({ status: $('fDqStatus').value, search: $('fDqSearch').value })); }
        catch (e) { /* private mode */ }
    }

    // ---- the list -----------------------------------------------------------------------------

    async function show(kind) {
        mKind = kind;
        var f = readFilter(kind);
        SL.fillSelect($('fDqStatus'), FILTERS[kind], f.status);
        $('fDqSearch').value = f.search || '';
        $('fDqSearch').placeholder = kind === 'question' ? 'Search the questions' : 'Search the decisions';
        $('btnDqAdd').textContent = kind === 'question' ? '+ Question' : '+ Decision';
        $('pane-log').innerHTML =
            '<div id="dqList" data-testid="dq-list"><div class="sl-empty">Loading&hellip;</div></div>' +
            '<div class="sl-more" id="dqMore" hidden><button type="button" class="sl-btn" id="btnDqMore" data-testid="dq-more-btn">Load more</button></div>';
        $('btnDqMore').addEventListener('click', function () { if (!mList[mKind].loading) loadPage(mKind); });
        $('dqList').addEventListener('click', onListClick);
        if (!mSynced[kind + Page.id]) { mSynced[kind + Page.id] = true; try { await folds(kind).sync(); } catch (e) { /* the device copy stands */ } }
        await loadFirst(kind);
    }

    async function loadFirst(kind) {
        mList[kind] = fresh();
        await loadPage(kind);
    }

    async function loadPage(kind) {
        var l = mList[kind], ask = ++l.ask;
        l.loading = true;
        var status = $('fDqStatus') ? $('fDqStatus').value : '', q = $('fDqSearch') ? $('fDqSearch').value.trim() : '';
        var r = await SL.get('/api/solutions/' + Page.id + '/' + kind + 's?offset=' + l.offset + '&pageSize=' + PAGE_SIZE +
            (status ? '&status=' + encodeURIComponent(status) : '') + (q ? '&search=' + encodeURIComponent(q) : ''));
        if (ask !== l.ask) return;
        l.loading = false;
        if (!r || !r.ok || !r.data) {
            if ($('dqList')) $('dqList').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the ' + NOUN[kind][1] + '.')) + '</div>';
            return;
        }
        var page = r.data[kind + 's'] || [];
        l.rows = l.rows.concat(page);
        l.counts = r.data.counts || {};
        l.offset += page.length;
        l.more = l.rows.length < (r.data.total || 0);
        if (mKind === kind) draw(kind);
    }

    function draw(kind) {
        var list = $('dqList');
        if (!list) return;
        var l = mList[kind], total = l.counts.total || 0, all = l.counts.all || 0;
        list.innerHTML = l.rows.length ? l.rows.map(function (rec) { return card(kind, rec); }).join('')
            : '<div class="sl-empty">' + (all ? 'Nothing here matches.' : (kind === 'question' ? 'No questions yet. + Question asks one.' : 'No decisions yet. + Decision records one.')) + '</div>';
        $('dqMore').hidden = !l.more;
        SL.status(total + ' ' + NOUN[kind][total === 1 ? 0 : 1] + (all !== total ? ' shown (' + all + ' in all)' : ''));
    }

    function card(kind, rec) {
        var open = folds(kind).isOpen(String(rec.id));
        var title = kind === 'question' ? rec.text : rec.title;
        var head = '<div class="sl-topic-head">' +
            '<button type="button" class="sl-topic-name" data-act="fold" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="dq-card-toggle">' +
                '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span>' + statusChip(kind, rec.statusCode) +
                '<span class="sl-dq-title" data-testid="dq-card-title">' + esc(SL.plain(title || '', 200)) + '</span></button>' +
            '<span class="tools">' +
                '<button type="button" class="sl-iconbtn" data-act="view" title="View" aria-label="View" data-testid="dq-view-btn">&#128065;</button>' +
                '<button type="button" class="sl-iconbtn" data-act="edit" title="Edit" aria-label="Edit" data-testid="dq-edit-btn">&#9998;</button>' +
                '<button type="button" class="sl-iconbtn" data-act="delete" title="Delete" aria-label="Delete" data-testid="dq-delete-btn">&#10005;</button>' +
            '</span></div>';
        var body = '';
        if (open) {
            var text = kind === 'question' ? rec.detail : rec.outcome;
            body = '<div class="sl-topic-body">' +
                (text ? '<div class="sl-md" data-testid="dq-card-text">' + SL.md(text, rec.truncated && kind === 'question') + '</div>' : '') +
                '<div class="sl-muted" data-testid="dq-card-meta">' + esc(metaLine(kind, rec)) + '</div></div>';
        }
        var cls = (rec.statusCode === 'answered' || rec.statusCode === 'accepted') ? ' sl-dq-done' : (rec.statusCode === 'dropped' || rec.statusCode === 'superseded' ? ' sl-dq-quiet' : '');
        return '<div class="sl-topic sl-dq' + cls + (open ? '' : ' folded') + '" data-dq="' + rec.id + '" data-testid="dq-card">' + head + body + '</div>';
    }

    function metaLine(kind, rec) {
        var bits = [];
        if (kind === 'question') {
            bits.push('Asked' + (rec.askedByName ? ' by ' + rec.askedByName : '') + ' ' + SL.date(rec.createdDate));
            if (rec.ownerName) bits.push('for ' + rec.ownerName);
            if (rec.answeredByTitle) bits.push('Answered by: ' + SL.plain(rec.answeredByTitle, 80) + (rec.answeredDate ? ' ' + SL.date(rec.answeredDate) : ''));
            else if (rec.statusCode === 'answered' && rec.answeredDate) bits.push('Answered ' + SL.date(rec.answeredDate));
        } else {
            if (rec.decidedDate) bits.push('Decided ' + SL.date(rec.decidedDate) + (rec.decidedByName ? ' by ' + rec.decidedByName : ''));
            else bits.push('Proposed' + (rec.decidedByName ? ' by ' + rec.decidedByName : '') + ' ' + SL.date(rec.createdDate));
            if (rec.answeredCount) bits.push('answered ' + rec.answeredCount + (rec.answeredCount === 1 ? ' question' : ' questions'));
            if (rec.supersededByTitle) bits.push('Replaced by: ' + SL.plain(rec.supersededByTitle, 80));
        }
        if (rec.logSequenceNumber) bits.push('Log #' + rec.logSequenceNumber);
        if (rec.linkCount) bits.push(rec.linkCount + (rec.linkCount === 1 ? ' link' : ' links'));
        return bits.join(' | ');
    }

    function rowById(kind, id) { return mList[kind].rows.filter(function (r) { return r.id === id; })[0] || null; }

    function onListClick(e) {
        var b = e.target.closest('[data-act]');
        if (!b) return;
        var id = +b.closest('[data-dq]').getAttribute('data-dq');
        var act = b.getAttribute('data-act');
        if (act === 'fold') { folds(mKind).toggle(String(id)); draw(mKind); }
        else if (act === 'view') openRecord(mKind, id);
        else if (act === 'edit') openEditor(mKind, id);
        else if (act === 'delete') remove(mKind, id);
    }

    function refreshList() {
        if (Page.active === 'log' && $('dqList')) loadFirst(mKind);
        if (Page.tabs.log && Page.tabs.log.paintBadge) Page.tabs.log.paintBadge();
    }

    // ---- the View ----------------------------------------------------------------------------

    async function fetchRec(kind, id) {
        var r = await SL.get('/api/solutions/' + kind + 's/' + id);
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'That ' + NOUN[kind][0] + ' could not be opened.')); return null; }
        return r.data;
    }

    async function openRecord(kind, id) {
        var rec = await fetchRec(kind, id);
        if (rec) showView(kind, rec);
    }

    function section(title, markdown, testid) {
        if (!markdown) return '';
        return '<h3 class="sl-sepia-h">' + esc(title) + '</h3><div class="sl-md" data-testid="' + testid + '">' + SL.md(markdown) + '</div>';
    }

    function showView(kind, rec) {
        mView = { kind: kind, rec: rec };
        $('dqViewTitle').textContent = kind === 'question' ? rec.text : rec.title;
        $('dqViewMeta').innerHTML = statusChip(kind, rec.statusCode) + ' ' + esc(metaLine(kind, rec));
        var body = '';
        if (kind === 'question') {
            body = rec.detail ? '<div class="sl-md" data-testid="dq-view-detail">' + SL.md(rec.detail) + '</div>' : '<p class="sl-sepia-empty">Nothing more written about it.</p>';
            if (rec.answeredByDecisionId) body += '<h3 class="sl-sepia-h">Answered by</h3><button type="button" class="sl-link-open" data-dq-go="decision" data-dq-id="' +
                rec.answeredByDecisionId + '" data-testid="dq-view-answered-by"><span class="sl-kind decision">Decision</span><span class="sl-link-title">' +
                esc(SL.plain(rec.answeredByTitle || '', 160)) + '</span></button>';
        } else {
            body = section('Context', rec.context, 'dq-view-context') + section('Options weighed', rec.options, 'dq-view-options') +
                section('Outcome', rec.outcome, 'dq-view-outcome');
            if (!body) body = '<p class="sl-sepia-empty">Only the title is written.</p>';
            if (rec.supersedesId) body += '<h3 class="sl-sepia-h">Replaces</h3>' + goButton('decision', rec.supersedesId, rec.supersedesTitle, 'dq-view-replaces');
            if (rec.supersededById) body += '<h3 class="sl-sepia-h">Replaced by</h3>' + goButton('decision', rec.supersededById, rec.supersededByTitle, 'dq-view-replaced-by');
        }
        $('dqViewBody').innerHTML = body;
        $('dqViewOpen').innerHTML = '';
        $('dqViewSupersede').hidden = kind !== 'decision' || rec.statusCode === 'superseded';
        $('dqViewAnswer').hidden = kind !== 'question' || rec.statusCode !== 'open';
        $('dqViewDrop').hidden = kind !== 'question';
        $('dqViewDrop').textContent = rec.statusCode === 'open' ? 'Drop' : 'Reopen';
        SL.open('dqViewOverlay');
        $('dqViewScroll').scrollTop = 0;
        window.SolutionLinks.mount($('dqViewLinks'), kind, rec.id, kind === 'question' ? rec.text : rec.title);
        if (kind === 'decision' && rec.statusCode === 'accepted') drawOpenLinks(rec);
    }

    function goButton(kind, id, title, testid) {
        return '<button type="button" class="sl-link-open" data-dq-go="' + kind + '" data-dq-id="' + id + '" data-testid="' + testid + '">' +
            '<span class="sl-kind ' + kind + '">' + (kind === 'decision' ? 'Decision' : 'Question') + '</span>' +
            '<span class="sl-link-title">' + esc(SL.plain(title || '', 160)) + '</span></button>';
    }

    function closeView() { SL.close('dqViewOverlay'); mView = null; }

    // "Still open": what an accepted decision could close, each with a tick, and Close them.
    async function drawOpenLinks(rec) {
        var r = await SL.get('/api/solutions/decisions/' + rec.id + '/open-links');
        if (!mView || mView.rec.id !== rec.id) return;
        var rows = r && r.ok && Array.isArray(r.data) ? r.data : [];
        if (!rows.length) { $('dqViewOpen').innerHTML = ''; return; }
        $('dqViewOpen').innerHTML = '<div class="sl-dq-open" data-testid="dq-open-links">' +
            '<div class="sl-dq-open-head"><strong>Still open</strong> <span class="sl-muted">(linked to this decision)</span></div>' +
            rows.map(function (o) {
                return '<label class="sl-dq-open-row" data-testid="dq-open-row"><input type="checkbox" checked data-open-kind="' + esc(o.kind) + '" data-open-id="' + o.id + '" data-testid="dq-open-check">' +
                    '<span class="sl-kind ' + esc(o.kind) + '">' + (o.kind === 'question' ? 'Question' : 'Item') + '</span>' +
                    '<span class="sl-link-title">' + esc(SL.plain(o.title || '', 160)) + '</span>' +
                    (o.sub && o.kind === 'item' ? '<span class="sl-link-sub">' + esc(o.sub) + '</span>' : '') + '</label>';
            }).join('') +
            '<div class="sl-dq-open-foot"><button type="button" class="sl-btn primary small" id="btnDqSettle" data-testid="dq-settle-btn">Close them</button></div></div>';
        $('btnDqSettle').addEventListener('click', function () { SL.guard($('btnDqSettle'), function () { return settle(rec, rows); }); });
    }

    async function settle(rec, rows) {
        var picked = Array.prototype.filter.call(document.querySelectorAll('[data-open-kind]'), function (b) { return b.checked; }).map(function (b) {
            var id = +b.getAttribute('data-open-id'), kind = b.getAttribute('data-open-kind');
            return rows.filter(function (o) { return o.kind === kind && o.id === id; })[0];
        }).filter(Boolean);
        if (!picked.length) { SL.toast('Tick what this decision closes first.'); return; }
        var q = picked.filter(function (o) { return o.kind === 'question'; }), it = picked.filter(function (o) { return o.kind === 'item'; });
        var lines = picked.map(function (o) { return (o.kind === 'question' ? 'Question: ' : 'Item: ') + SL.plain(o.title, 90); }).join('\n');
        var ok = await window.Picasso.yesNo(
            'The ' + (q.length ? q.length + (q.length === 1 ? ' question is' : ' questions are') + ' marked answered by it' : '') +
            (q.length && it.length ? ', and the ' : '') + (it.length ? it.length + (it.length === 1 ? ' item is' : ' items are') + ' marked done' : '') +
            '. One log entry records it.\n\n' + lines,
            { title: 'Close ' + picked.length + (picked.length === 1 ? ' record' : ' records') + ' with the decision: ' + SL.plain(rec.title, 70) + '?' });
        if (!ok) return;
        var r = await SL.post('/api/solutions/decisions/' + rec.id + '/settle', {
            questionIds: q.map(function (o) { return o.id; }), itemIds: it.map(function (o) { return o.id; }) });
        if (!r || !r.ok || !r.data) { window.Picasso.error(SL.failText(r, 'Nothing could be closed.')); return; }
        showView('decision', r.data.decision);
        refreshList();
        await Page.reload();
    }

    // ---- add / edit / replace -------------------------------------------------------------------

    function memberOptions(blank, keepCode) { return Page.memberOptions(blank, keepCode); }   // Story 5653 (5766: keep an inactive owner)

    /** kind 'question' or 'decision'; id 0 = new; opts: { linkTo: [{kind,id}], supersedes: decision, prefill: {} } */
    async function openEditor(kind, id, opts) {
        opts = opts || {};
        var rec = null;
        if (id) { rec = await fetchRec(kind, id); if (!rec) return; }
        mEdit = { kind: kind, id: id || 0, supersedes: opts.supersedes || null, linkTo: opts.linkTo || [] };
        var pre = opts.prefill || {};
        if (kind === 'question') {
            SL.warn('questionWarn', '');
            $('questionTitleBar').textContent = id ? 'Edit question' : 'New question';
            $('qText').value = rec ? rec.text : (pre.text || '');
            $('qDetail').value = rec ? (rec.detail || '') : (pre.detail || '');
            SL.fillSelect($('qOwner'), memberOptions('Anyone', rec ? rec.ownerCode : null), rec ? (rec.ownerCode || '') : '');
            $('questionContext').textContent = contextLine(opts);
            SL.open('questionOverlay');
            if (window.Picasso.autosizeTextarea) window.Picasso.autosizeTextarea($('qDetail'));
            $('qText').focus();
        } else {
            SL.warn('decisionWarn', '');
            $('decisionTitleBar').textContent = id ? 'Edit decision' : (opts.supersedes ? 'Replace a decision' : 'New decision');
            $('decTitle').value = rec ? rec.title : (pre.title || '');
            $('decContext').value = rec ? (rec.context || '') : (pre.context || '');
            $('decOptions').value = rec ? (rec.options || '') : (pre.options || '');
            $('decOutcome').value = rec ? (rec.outcome || '') : (pre.outcome || '');
            var statuses = [{ value: 'accepted', label: 'Accepted: it is decided' }, { value: 'proposed', label: 'Proposed: still to be agreed' }];
            SL.fillSelect($('decStatus'), statuses, rec ? (rec.statusCode === 'superseded' ? 'accepted' : rec.statusCode) : 'accepted');
            $('decStatusFld').hidden = !!opts.supersedes || (rec && rec.statusCode === 'superseded');
            $('decisionContext').textContent = contextLine(opts);
            SL.open('decisionOverlay');
            ['decContext', 'decOptions', 'decOutcome'].forEach(function (f) { if (window.Picasso.autosizeTextarea) window.Picasso.autosizeTextarea($(f)); });
            $('decTitle').focus();
        }
    }

    function contextLine(opts) {
        if (opts.supersedes) return 'Replaces: ' + SL.plain(opts.supersedes.title, 140) + '. The old one is kept, marked replaced.';
        if (opts.linkTo && opts.linkTo.length && opts.linkTitle) return 'Linked to: ' + SL.plain(opts.linkTitle, 140);
        return '';
    }

    async function saveQuestion() {
        var text = $('qText').value.trim();
        if (!text) { SL.warn('questionWarn', 'Write the question.'); $('qText').focus(); return; }
        var body = { text: text, detail: $('qDetail').value, ownerCode: $('qOwner').value };
        var r;
        if (mEdit.id) r = await SL.put('/api/solutions/questions/' + mEdit.id, body);
        else { body.links = mEdit.linkTo; r = await SL.post('/api/solutions/' + Page.id + '/questions', body); }
        if (!r || !r.ok || !r.data) { SL.warn('questionWarn', SL.failText(r, 'The question could not be saved.')); return; }
        SL.close('questionOverlay');
        afterSave('question', r.data);
    }

    async function saveDecision() {
        var title = $('decTitle').value.trim();
        if (!title) { SL.warn('decisionWarn', 'Say what was decided, in a line.'); $('decTitle').focus(); return; }
        var body = { title: title, context: $('decContext').value, options: $('decOptions').value, outcome: $('decOutcome').value };
        var r;
        if (mEdit.id) { body.statusCode = $('decStatus').value; r = await SL.put('/api/solutions/decisions/' + mEdit.id, body); }
        else if (mEdit.supersedes) { body.links = mEdit.linkTo; r = await SL.post('/api/solutions/decisions/' + mEdit.supersedes.id + '/supersede', body); }
        else { body.statusCode = $('decStatus').value; body.links = mEdit.linkTo; r = await SL.post('/api/solutions/' + Page.id + '/decisions', body); }
        if (!r || !r.ok || !r.data) { SL.warn('decisionWarn', SL.failText(r, 'The decision could not be saved.')); return; }
        SL.close('decisionOverlay');
        afterSave('decision', r.data);
    }

    async function afterSave(kind, rec) {
        refreshList();
        if (window.SolutionLinks) window.SolutionLinks.refresh();
        // A new decision opens straight away, so what it can close is in front of the reader.
        if (kind === 'decision' || (mView && mView.rec.id === rec.id)) showView(kind, rec);
        await Page.reload();
    }

    async function remove(kind, id) {
        var rec = rowById(kind, id) || (mView && mView.kind === kind && mView.rec.id === id ? mView.rec : null);
        if (!rec) rec = await fetchRec(kind, id);
        if (!rec) return;
        var what = kind === 'question'
            ? 'Its links go with it. A decision that answered it stays.'
            : 'Its links go with it. ' + (rec.answeredCount ? 'The ' + rec.answeredCount + (rec.answeredCount === 1 ? ' question it answered opens' : ' questions it answered open') + ' again. ' : '') +
              (rec.supersedesId ? 'The decision it replaced becomes the current one again. ' : '') + 'Its log entry stays.';
        var ok = await window.Picasso.confirmDelete(what + '\n\n' + (STATUS[kind][rec.statusCode] || [rec.statusCode])[0] + ', ' + SL.date(rec.createdDate) +
            '\n\nThis cannot be undone.', { title: 'Delete ' + (kind === 'question' ? 'Question' : 'Decision') + ': ' + SL.plain(kind === 'question' ? rec.text : rec.title, 80) + '?' });
        if (!ok) return;
        var r = await SL.del('/api/solutions/' + kind + 's/' + id);
        if (!r || !r.ok) { window.Picasso.error(SL.failText(r, 'It could not be deleted.')); return; }
        if (mView && mView.kind === kind && mView.rec.id === id) closeView();
        refreshList();
        await Page.reload();
    }

    async function dropOrReopen() {
        if (!mView || mView.kind !== 'question') return;
        var q = mView.rec, next = q.statusCode === 'open' ? 'dropped' : 'open';
        var ok = await window.Picasso.yesNo(next === 'dropped' ? 'It stays in the list, marked dropped, and you can reopen it.' : 'It goes back on the open list' +
            (q.answeredByTitle ? ', and no longer counts as answered by "' + SL.plain(q.answeredByTitle, 60) + '"' : '') + '.',
            { title: (next === 'dropped' ? 'Drop' : 'Reopen') + ' Question: ' + SL.plain(q.text, 80) + '?' });
        if (!ok) return;
        var r = await SL.post('/api/solutions/questions/' + q.id + '/status', { statusCode: next });
        if (!r || !r.ok || !r.data) { window.Picasso.error(SL.failText(r, 'The question could not be changed.')); return; }
        showView('question', r.data);
        refreshList();
    }

    /** A journal entry becomes a question or a decision, in place (the entry is not changed). */
    async function fromLog(logId, kind) {
        var r = await SL.post('/api/solutions/log/' + logId + '/' + kind, {});
        if (!r || !r.ok || !r.data) { window.Picasso.error(SL.failText(r, 'The log entry could not be made a ' + NOUN[kind][0] + '.')); return null; }
        showView(kind, r.data);
        return r.data;
    }

    // ---- wiring ---------------------------------------------------------------------------------

    function init() {
        $('fDqStatus').addEventListener('change', function () { saveFilter(mKind); loadFirst(mKind); });
        $('fDqSearch').addEventListener('input', function () { clearTimeout(mTimer); mTimer = setTimeout(function () { saveFilter(mKind); loadFirst(mKind); }, TYPING_PAUSE_MS); });
        $('fDqSearch').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); clearTimeout(mTimer); saveFilter(mKind); loadFirst(mKind); } });
        $('btnDqAdd').addEventListener('click', function () { openEditor(mKind, 0); });

        $('questionCancel').addEventListener('click', function () { SL.close('questionOverlay'); });
        $('questionSave').addEventListener('click', function () { SL.guard($('questionSave'), saveQuestion); });
        $('decisionCancel').addEventListener('click', function () { SL.close('decisionOverlay'); });
        $('decisionSave').addEventListener('click', function () { SL.guard($('decisionSave'), saveDecision); });

        $('dqViewClose').addEventListener('click', closeView);
        $('dqViewEdit').addEventListener('click', function () { if (mView) { var v = mView; closeView(); openEditor(v.kind, v.rec.id); } });
        $('dqViewDelete').addEventListener('click', function () { if (mView) remove(mView.kind, mView.rec.id); });
        $('dqViewDrop').addEventListener('click', function () { SL.guard($('dqViewDrop'), dropOrReopen); });
        $('dqViewSupersede').addEventListener('click', function () {
            if (!mView || mView.kind !== 'decision') return;
            var old = mView.rec; closeView();
            openEditor('decision', 0, { supersedes: old, prefill: { context: old.context || '', options: old.options || '' } });
        });
        $('dqViewAnswer').addEventListener('click', function () {
            if (!mView || mView.kind !== 'question') return;
            var q = mView.rec; closeView();
            openEditor('decision', 0, { linkTo: [{ kind: 'question', id: q.id }], linkTitle: 'Question: ' + q.text, prefill: { context: q.detail || '' } });
        });
        $('dqViewBody').addEventListener('click', function (e) {
            var b = e.target.closest('[data-dq-go]');
            if (b) openRecord(b.getAttribute('data-dq-go'), +b.getAttribute('data-dq-id'));
        });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            if ($('linkOverlay').classList.contains('open')) return;           // the picker closes first
            if ($('questionOverlay').classList.contains('open')) SL.close('questionOverlay');
            else if ($('decisionOverlay').classList.contains('open')) SL.close('decisionOverlay');
            else if ($('dqViewOverlay').classList.contains('open')) closeView();
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
    else init();

    window.SolutionDecisions = {
        show: show,
        openQuestion: function (id) { return openRecord('question', id); },
        openDecision: function (id) { return openRecord('decision', id); },
        ask: function (linkTo, linkTitle) { return openEditor('question', 0, { linkTo: linkTo, linkTitle: linkTitle }); },
        decide: function (linkTo, linkTitle) { return openEditor('decision', 0, { linkTo: linkTo, linkTitle: linkTitle }); },
        fromLog: fromLog,
        refresh: refreshList
    };
})();
