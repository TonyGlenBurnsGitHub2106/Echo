(function () {
    'use strict';

    // The Now / Next / Waiting lens (Story 5695, Echo first). From the Solutions deep review of 11/02: "0 due
    // dates, 326 items unassigned ... a ten-day silence that nothing flagged." The Items tab has two views on its
    // tool row, Projects (the tree, as always) and Now. Now groups every OPEN item, decided on the server:
    //   Now      in progress, or due within a week (overdue first; mine before others)
    //   Waiting  blocked, or waits on an item that is still open
    //   Next     everything else still open
    // A Now item untouched for a week says so ("idle 9 days"): the weekly review. Tap a row to see it in the
    // tree. Each group folds (remembered in the database) and loads more on demand.
    //
    //   SolutionLens.show(host)   draw the lens into the Items pane

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;
    var GROUPS = [{ key: 'now', title: 'Now' }, { key: 'waiting', title: 'Waiting' }, { key: 'next', title: 'Next' }];
    var PAGE = 50;

    var mHost = null, mRows = {}, mCounts = {}, mFolds = null, mSynced = false, mAsk = 0;

    function folds() {
        if (!mFolds) mFolds = window.PicassoGroupState.create({ key: 'leo.sl.lens.' + Page.id, defaultOpen: true });
        return mFolds;
    }

    async function show(host) {
        mHost = host;
        host.innerHTML = '<div class="sl-empty">Loading&hellip;</div>';
        if (!mSynced) { mSynced = true; try { await folds().sync(); } catch (e) { /* the device copy stands */ } }
        var ask = ++mAsk;
        var r = await SL.get('/api/solutions/' + Page.id + '/lens?pageSize=' + PAGE);
        if (ask !== mAsk || !mHost) return;
        if (!r || !r.ok || !r.data) { host.innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not read the open items.')) + '</div>'; return; }
        mRows = { now: [], waiting: [], next: [] };
        (r.data.items || []).forEach(function (i) { (mRows[i.bucket] = mRows[i.bucket] || []).push(i); });
        mCounts = r.data.counts || {};
        draw();
    }

    async function more(bucket) {
        var r = await SL.get('/api/solutions/' + Page.id + '/lens?bucket=' + bucket + '&offset=' + mRows[bucket].length + '&pageSize=' + PAGE);
        if (!r || !r.ok || !r.data) { window.Picasso.error(SL.failText(r, 'Could not read more.')); return; }
        mRows[bucket] = mRows[bucket].concat(r.data.items || []);
        mCounts = r.data.counts || mCounts;
        draw();
    }

    function draw() {
        if (!mHost || !mHost.isConnected) return;
        var total = (mCounts.now || 0) + (mCounts.waiting || 0) + (mCounts.next || 0);
        if (!total) { mHost.innerHTML = '<div class="sl-empty">Nothing is open. Every item is closed.</div>'; SL.status('No open items'); return; }
        mHost.innerHTML = GROUPS.map(function (g) {
            var rows = mRows[g.key] || [], n = mCounts[g.key] || 0, open = folds().isOpen(g.key);
            if (!n) return '';
            var idle = g.key === 'now' ? rows.filter(function (i) { return i.idle; }).length : 0;
            return '<div class="sl-tgroup' + (open ? '' : ' folded') + '" data-lens-group="' + g.key + '" data-testid="lens-group">' +
                '<button type="button" class="sl-tgroup-head" data-lens-fold="' + g.key + '" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="lens-group-toggle">' +
                    '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span><span class="sl-tgroup-title">' + g.title + '</span>' +
                    '<span class="sl-kind-count" data-testid="lens-group-count">' + n + '</span>' +
                    (idle ? '<span class="sl-chip wait" data-testid="lens-idle-count">' + idle + ' idle</span>' : '') + '</button>' +
                (open ? '<div class="sl-tgroup-body">' + rows.map(row).join('') +
                    (rows.length < n ? '<button type="button" class="sl-ov-more" data-lens-more="' + g.key + '" data-testid="lens-more">' + (n - rows.length) + ' more</button>' : '') +
                    '</div>' : '') + '</div>';
        }).join('');
        Array.prototype.forEach.call(mHost.querySelectorAll('[data-lens-fold]'), function (b) {
            b.addEventListener('click', function () { folds().toggle(b.getAttribute('data-lens-fold')); draw(); });
        });
        Array.prototype.forEach.call(mHost.querySelectorAll('[data-lens-more]'), function (b) {
            b.addEventListener('click', function () { SL.guard(b, function () { return more(b.getAttribute('data-lens-more')); }); });
        });
        Array.prototype.forEach.call(mHost.querySelectorAll('[data-lens-item]'), function (b) {
            b.addEventListener('click', function () { Page.tabs.items.showInTree(+b.getAttribute('data-lens-item')); });
        });
        SL.status((mCounts.now || 0) + ' now | ' + (mCounts.waiting || 0) + ' waiting | ' + (mCounts.next || 0) + ' next');
    }

    function row(i) {
        var chips = [];
        if (i.dueDate) chips.push('<span class="sl-chip' + (i.overdue ? ' warn' : '') + '" data-testid="lens-due">' + (i.overdue ? 'Overdue ' : 'Due ') + SL.date(i.dueDate) + '</span>');
        if (i.assigneeName) chips.push(Page.personChip(i.assigneeCode, i.assigneeName));
        if (i.openWaits) chips.push('<span class="sl-chip waits" data-testid="lens-waits">waits on ' + i.openWaits + '</span>');
        if (i.idle) chips.push('<span class="sl-chip wait" title="Not changed for ' + i.idleDays + ' days" data-testid="lens-idle">idle ' + i.idleDays + ' days</span>');
        if (i.priority && i.priority < 3) chips.push('<span class="sl-chip warn">P' + i.priority + '</span>');
        return '<button type="button" class="sl-ov-row sl-lens-row" data-lens-item="' + i.id + '" data-testid="lens-row">' +
            '<span class="sl-ov-main">' + SL.pill(i.statusName, i.colorHex) + ' ' + esc(i.title) + '</span>' +
            '<span class="sl-ov-sub">' + esc(i.projectName || '') + (chips.length ? ' ' + chips.join(' ') : '') + '</span></button>';
    }

    window.SolutionLens = { show: show, hide: function () { mHost = null; ++mAsk; } };
})();
