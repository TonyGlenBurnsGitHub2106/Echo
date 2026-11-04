(function () {
    'use strict';

    // Close out (Story 5695, Echo first, Release 6). From the deep review of 11/02: a solution is never finished on
    // purpose; it just stops. Close out (the Overview's tool row) lists everything still open, by group: projects,
    // items, questions, proposed decisions, parts, assumptions still marked holding, threads waiting for an answer.
    // Tap one to go to it. Mark the solution Done asks first, saying how much is still open.
    //
    //   SolutionCloseOut.open()

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;
    var GROUP = { project: 'Projects not closed', item: 'Items still open', question: 'Open questions', decision: 'Decisions not yet agreed',
                  part: 'Parts not done', assumption: 'Assumptions still marked holding', thread: 'Threads waiting for an answer' };
    var mData = null;

    async function open() {
        $('coList').innerHTML = '<div class="sl-empty">Reading&hellip;</div>';
        $('coTitle').textContent = 'Close out ' + (Page.solution ? Page.solution.name : '');
        SL.open('closeOutOverlay');
        var r = await SL.get('/api/solutions/' + Page.id + '/close-out');
        if (!r || !r.ok || !r.data) { $('coList').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not read what is open.')) + '</div>'; return; }
        mData = r.data;
        draw();
    }

    function draw() {
        var d = mData, done = Page.solution && Page.solution.statusCode === 'done';
        $('coSummary').textContent = d.total ? d.total + (d.total === 1 ? ' thing is' : ' things are') + ' still open.' : 'Nothing is open. It is ready to be called done.';
        $('coSummary').className = 'sl-co-summary' + (d.total ? '' : ' clear');
        $('coDone').hidden = done;
        $('coDone').textContent = 'Mark the solution Done';
        var groups = Object.keys(d.counts).filter(function (g) { return d.counts[g] > 0; });
        $('coList').innerHTML = groups.map(function (g) {
            var rows = d.open.filter(function (t) { return t.group === g; });
            return '<div class="sl-co-group" data-testid="close-out-group" data-co-group="' + g + '">' +
                '<div class="sl-co-head"><span>' + esc(GROUP[g] || g) + '</span><span class="sl-kind-count" data-testid="close-out-count">' + rows.length + '</span></div>' +
                rows.map(function (t) {
                    return '<button type="button" class="sl-ov-row" data-co-kind="' + esc(t.kind) + '" data-co-id="' + t.id + '" data-testid="close-out-row">' +
                        '<span class="sl-ov-main">' + esc(SL.plain(t.title || '', 140)) + '</span>' +
                        '<span class="sl-ov-sub">' + esc([t.sub, SL.dateTime(t.changedDate)].filter(Boolean).join(' | ')) + '</span></button>';
                }).join('') + '</div>';
        }).join('');
    }

    function onRow(e) {
        var b = e.target.closest('[data-co-kind]');
        if (!b) return;
        var kind = b.getAttribute('data-co-kind'), id = +b.getAttribute('data-co-id');
        SL.close('closeOutOverlay');
        if (kind === 'project') { Page.tabs.items.openProject(id); return; }
        window.SolutionLinks.openRecord(kind, id);
    }

    async function markDone() {
        var n = mData ? mData.total : 0, name = Page.solution ? Page.solution.name : 'this solution';
        var ok = await window.Picasso.yesNo((n ? n + (n === 1 ? ' thing is' : ' things are') + ' still open and stay as they are. ' : 'Nothing is open. ') +
            'The solution is marked Done. You can change its status again from Edit.', { title: 'Mark Solution: ' + name + ' as Done?' });
        if (!ok) return;
        var r = await SL.put('/api/solutions/' + Page.id, { statusCode: 'done' });
        if (!r || !r.ok) { window.Picasso.error(SL.failText(r, 'The solution was not changed.')); return; }
        SL.close('closeOutOverlay');
        await Page.reload();
        Page.go('overview');
    }

    function init() {
        $('btnCloseOut').addEventListener('click', open);
        $('coList').addEventListener('click', onRow);
        $('coClose').addEventListener('click', function () { SL.close('closeOutOverlay'); });
        $('coDone').addEventListener('click', function () { SL.guard($('coDone'), markDone); });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && $('closeOutOverlay').classList.contains('open')) SL.close('closeOutOverlay');
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
    else init();

    window.SolutionCloseOut = { open: open };
})();
