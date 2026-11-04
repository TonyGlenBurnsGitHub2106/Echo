(function () {
    'use strict';

    // The activity feed and "where I left off" (Story 5695, Echo first). From the Solutions deep review of
    // 11/02: "what happened this week ... it is what the weekly logs were trying to be", and the Status note,
    // Felix Next Steps and the weekly HTML logs "are all people doing this by hand".
    //
    //   The ACTIVITY sheet: every change to the solution, EVERYONE's (yours too), newest first, narrowed by who,
    //   what kind of record, how far back and topic. Opened from the Overview's Activity band (Show all).
    //   The NOTE FOR NEXT TIME: one note per person per solution ("Next time, start with ..."), shown first on the
    //   Overview when there is one. Written from the Overview's tool row.
    //
    //   SolutionActivity.openFeed()    the sheet
    //   SolutionActivity.editNote()    the note dialog
    //   SolutionActivity.note()        the note (cached for this page), for the Overview's band

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;
    var KIND = { item: 'Item', log: 'Log', note: 'Note', file: 'File', message: 'Message', term: 'Term', topic: 'Topic',
                 question: 'Question', decision: 'Decision' };
    var PAGE = 50;

    var mRows = [], mTotal = 0, mAsk = 0, mNote = null, mNoteFor = 0;

    // ---- the feed --------------------------------------------------------------------------------

    async function openFeed() {
        SL.fillSelect($('actWho'), Page.memberOptions('Everyone'), $('actWho').value || '');
        SL.fillSelect($('actKind'), [{ value: '', label: 'Every kind' }].concat(Object.keys(KIND).map(function (k) { return { value: k, label: KIND[k] }; })), $('actKind').value || '');
        SL.fillSelect($('actDays'), [{ value: '7', label: 'The last week' }, { value: '30', label: 'The last 30 days' },
                                     { value: '90', label: 'The last 90 days' }, { value: '365', label: 'The last year' }], $('actDays').value || '7');
        var t = await SL.get('/api/solutions/' + Page.id + '/topics?pageSize=200');
        var topics = t && t.ok && t.data ? t.data.topics || [] : [];
        SL.fillSelect($('actTopic'), [{ value: '', label: 'Any topic' }].concat(topics.map(function (x) { return { value: String(x.id), label: x.name }; })), $('actTopic').value || '');
        SL.open('activityOverlay');
        await load(false);
    }

    function query(offset) {
        var q = '/api/solutions/' + Page.id + '/activity?offset=' + offset + '&pageSize=' + PAGE + '&days=' + encodeURIComponent($('actDays').value || '7');
        if ($('actWho').value) q += '&who=' + encodeURIComponent($('actWho').value);
        if ($('actKind').value) q += '&kind=' + encodeURIComponent($('actKind').value);
        if ($('actTopic').value) q += '&topicId=' + encodeURIComponent($('actTopic').value);
        return q;
    }

    async function load(more) {
        var ask = ++mAsk;
        if (!more) { mRows = []; $('actList').innerHTML = '<div class="sl-empty">Reading&hellip;</div>'; }
        var r = await SL.get(query(more ? mRows.length : 0));
        if (ask !== mAsk) return;
        if (!r || !r.ok || !r.data) { $('actList').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not read the activity.')) + '</div>'; return; }
        mRows = mRows.concat(r.data.changes || []);
        mTotal = r.data.total || 0;
        draw();
    }

    function draw() {
        $('actCount').textContent = mTotal + (mTotal === 1 ? ' change' : ' changes');
        $('actList').innerHTML = mRows.length ? mRows.map(function (c) {
            return '<button type="button" class="sl-ov-row sl-since-row" data-act-kind="' + esc(c.kind) + '" data-act-id="' + c.id + '" data-testid="activity-row">' +
                '<span class="sl-kind ' + esc(c.kind) + '">' + esc(KIND[c.kind] || c.kind) + '</span>' +
                '<span class="sl-ov-main">' + esc(SL.plain(c.title || '', 140)) + '</span>' +
                '<span class="sl-ov-sub">' + esc([(c.whoName || c.who || 'Someone') + ' ' + c.action, SL.dateTime(c.changedDate)].join(' | ')) + '</span></button>';
        }).join('') + (mRows.length < mTotal ? '<button type="button" class="sl-ov-more" id="actMore" data-testid="activity-more">' + (mTotal - mRows.length) + ' more</button>' : '')
            : '<div class="sl-empty" data-testid="activity-none">Nothing changed in that time.</div>';
        if ($('actMore')) $('actMore').addEventListener('click', function () { SL.guard($('actMore'), function () { return load(true); }); });
    }

    function onRow(e) {
        var b = e.target.closest('[data-act-kind]');
        if (!b) return;
        SL.close('activityOverlay');
        window.SolutionLinks.openRecord(b.getAttribute('data-act-kind'), +b.getAttribute('data-act-id'));
    }

    // ---- the note for next time ----------------------------------------------------------------------

    async function note() {
        if (mNoteFor === Page.id && mNote) return mNote;
        var r = await SL.get('/api/solutions/' + Page.id + '/left-off');
        mNote = r && r.ok && r.data ? r.data : { note: null };
        mNoteFor = Page.id;
        return mNote;
    }

    async function editNote() {
        var n = await note();
        SL.warn('leftOffWarn', '');
        $('leftOffText').value = n.note || '';
        SL.open('leftOffOverlay');
        if (window.Picasso.autosizeTextarea) window.Picasso.autosizeTextarea($('leftOffText'));
        $('leftOffText').focus();
    }

    async function saveNote() {
        var text = $('leftOffText').value;
        if (text.trim().length > 1000) { SL.warn('leftOffWarn', 'A note for next time can be at most 1,000 characters.'); return; }
        var r = await SL.put('/api/solutions/' + Page.id + '/left-off', { note: text });
        if (!r || !r.ok || !r.data) { SL.warn('leftOffWarn', SL.failText(r, 'The note could not be saved.')); return; }
        mNote = r.data; mNoteFor = Page.id;
        SL.close('leftOffOverlay');
        if (Page.active === 'overview') Page.go('overview');
    }

    function init() {
        ['actWho', 'actKind', 'actDays', 'actTopic'].forEach(function (id) { $(id).addEventListener('change', function () { load(false); }); });
        $('actList').addEventListener('click', onRow);
        $('actClose').addEventListener('click', function () { SL.close('activityOverlay'); });
        $('btnLeftOff').addEventListener('click', editNote);
        $('leftOffCancel').addEventListener('click', function () { SL.close('leftOffOverlay'); });
        $('leftOffSave').addEventListener('click', function () { SL.guard($('leftOffSave'), saveNote); });
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            if ($('leftOffOverlay').classList.contains('open')) SL.close('leftOffOverlay');
            else if ($('activityOverlay').classList.contains('open')) SL.close('activityOverlay');
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
    else init();

    window.SolutionActivity = { openFeed: openFeed, editNote: editNote, note: note };
})();
