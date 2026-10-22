(function () {
    'use strict';

    // The Testing tab of one solution (2026-09-21): testing checklists. A checklist is a grid --
    // test cases in groups down the side (drag a case or a group by its grip to reorder), levels of testing across the top (Manual and Unit tested to
    // begin with; + Column adds more, e.g. End to end). Every cell ticks and unticks
    // with one tap, and the counters (per column, per group, overall) come from the server.
    //
    // Generic on purpose: nothing here knows what any one checklist is about.
    //
    // A Print tick on each case (on by default) says whether it appears on the printed checklist -- untick
    // the technical ones before printing for QA. It is saved on the case as soon as it changes.
    //
    // Every write answers with the whole checklist, so the grid is always redrawn from the server's
    // state. Ticks show at once and are sent one after another; the grid is redrawn once the last
    // one has been answered, so quick taps never flicker back.
    //
    // A red circle after the tick columns marks a test FAILING (2026-09-25), and each test has a note icon
    // for what is failing: tap it to edit the note, tap the eye beside it to read the note in the sepia
    // view. Marking a test failing when it has no note yet opens the note straight away. That note is the
    // RESULT note; the case's own note (set in the case dialog) is how to run the test.
    //
    // A cell can also be SKIPPED (Story 3534): a test deliberately not run at that level. One tap cycles a cell
    // blank -> ticked -> skipped (grayed out) -> blank. A skipped cell is neither a pass nor a fail, so every
    // "N of M" leaves it out of M. Untick all and Clear column leave skips alone -- a skip is a decision about
    // the test, not a result.
    //
    // TWO TABS, ONE GRID (2026-10-15). The same code draws the Testing tab (dev checklists: Manual and
    // Unit tested) and the QA tab (qa checklists: what a tester does from the outside, grouped by area --
    // 835, 837, deposits -- with one column, Passed, and printed as a plain list: a round box and the test). makeTab(cfg) is one tab; each keeps its own checklists and tool row, and the
    // dialogs are shared -- they answer to whichever tab is showing.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var gActive = null;               // the tab that is showing; the shared dialogs act on it

    function makeTab(cfg) {
    var T = cfg.testid, PANE = 'pane-' + cfg.tab, IS_QA = cfg.kind === 'qa';
    function B(name) { return $('toolbar-' + cfg.tab).querySelector('[data-tp="' + name + '"]'); }

    var mPlans = [], mPlan = null, mPlanId = 0;
    var mEdit = null;                 // what the open dialog is editing: {kind, id, ...}
    var mTickChain = Promise.resolve(), mTicksPending = 0, mLatest = null, mAfterFlush = [];

    var TICK = '&#10003;', SKIP = '&#8211;', CROSS = '&#10007;', NOTE_ICON = '&#128221;', EYE_ICON = '&#128065;';
    var mResultCaseId = 0;            // the case whose result note is open (edit dialog or sepia view)
    var mFlashCase = 0;               // a test the Search tab (2026-10-16) asked to scroll to and flash

    // ---- which checklist is open, remembered for this browser session ------------------------

    function planKey() { return 'sl.' + cfg.tab + '.plan.' + Page.id; }
    function rememberPlan(id) { try { sessionStorage.setItem(planKey(), String(id)); } catch (e) { /* private mode */ } }
    function rememberedPlan() { try { return +sessionStorage.getItem(planKey()) || 0; } catch (e) { return 0; } }

    // ---- load -----------------------------------------------------------------------------

    async function load() {
        gActive = api;
        var r = await SL.get('/api/solutions/' + Page.id + '/test-plans?kind=' + cfg.kind);
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $(PANE).innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the testing checklists.')) + '</div>';
            return;
        }
        mPlans = r.data;
        if (!mPlans.length) { mPlan = null; mPlanId = 0; fillPicker(); draw(); return; }
        var want = mPlanId || rememberedPlan();
        if (!mPlans.some(function (p) { return p.id === want; })) want = mPlans[0].id;
        await openPlan(want);
    }

    async function openPlan(id) {
        var r = await SL.get('/api/solutions/test-plans/' + id);
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'That checklist could not be opened.')); return; }
        usePlan(r.data);
    }

    /** Take the server's copy of a checklist and redraw everything from it. */
    function usePlan(plan) {
        mPlan = plan;
        mPlanId = plan.id;
        rememberPlan(plan.id);
        var known = false;
        mPlans.forEach(function (p) {
            if (p.id !== plan.id) return;
            known = true;
            p.title = plan.title;
            p.caseCount = plan.caseCount;
            p.checkedCount = plan.checkedCount;
            p.skippedCount = plan.skippedCount;
            p.cellCount = plan.cellCount;
        });
        if (!known) mPlans.push({ id: plan.id, title: plan.title, caseCount: plan.caseCount, checkedCount: plan.checkedCount,
            skippedCount: plan.skippedCount, cellCount: plan.cellCount });
        fillPicker();
        draw();
    }

    function fillPicker() {
        B('plan-field').hidden = mPlans.length < 2;
        SL.fillSelect(B('plan-select'), mPlans.map(function (p) {
            return { value: String(p.id), label: p.title + ' (' + p.checkedCount + ' of ' + toRun(p.cellCount, p.skippedCount) + ')' };
        }), mPlanId || '');
        var has = !!mPlan;
        ['edit-plan', 'add-group', 'add-column', 'expand', 'collapse', 'clear', 'delete']
            .forEach(function (name) { B(name).hidden = !has; });
    }

    // ---- draw -----------------------------------------------------------------------------

    function pct(done, total) { return total > 0 ? Math.round(done * 100 / total) : 0; }

    /** How many checks are left to run: every cell minus the skipped ones (Story 3534). */
    function toRun(cells, skipped) { return Math.max(0, (cells || 0) - (skipped || 0)); }

    /** " | 2 skipped", or nothing. */
    function skippedText(n) { return n ? ' | ' + n + ' skipped' : ''; }

    function bar(label, done, total, testid, skipped) {
        return '<div class="sl-tp-bar" data-testid="' + testid + '"><span class="name">' + esc(label) + '</span>' +
            '<span class="track"><span style="width:' + pct(done, total) + '%"></span></span>' +
            '<span class="num">' + done + ' of ' + total + esc(skippedText(skipped)) + '</span></div>';
    }

    // The top card folds (tap its title) to one line -- the title and how many checks are ticked -- so the
    // groups get the room. Open by default; the choice is kept per checklist on this device.
    function summary(p) {
        var folded = Page.fold().isCollapsed('thero:' + p.id, false);
        var head = '<div class="sl-herohead sl-foldable" id="' + cfg.tab + 'HeroFold" role="button" tabindex="0" aria-expanded="' + (folded ? 'false' : 'true') +
                '" data-testid="' + T + '-summary-toggle"' + (folded ? '' : ' style="margin-bottom:6px"') + '>' +
            '<span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span><strong class="sl-heroname" data-testid="' + T + '-plan-title">' + esc(p.title) + '</strong>' +
            (folded
                ? '<span class="sl-muted" data-testid="' + T + '-summary-folded">' + p.checkedCount + ' of ' + toRun(p.cellCount, p.skippedCount) +
                    ' checks ticked' + esc(skippedText(p.skippedCount)) + '</span>'
                : '<span class="sl-muted">' + p.caseCount + (p.caseCount === 1 ? ' test case' : ' test cases') + ' | ' + p.columns.length +
                    (p.columns.length === 1 ? ' column' : ' columns') + '</span>') +
            failingBadge(p.failingCount, T + '-summary-failing') + '</div>';
        if (folded) return '<div class="sl-hero folded" data-testid="' + T + '-summary">' + head + '</div>';
        var perColumn = p.columns.map(function (c) {
            return bar(c.title, c.checkedCount, toRun(p.caseCount, c.skippedCount), T + '-column-bar', c.skippedCount);
        }).join('');
        return '<div class="sl-hero" data-testid="' + T + '-summary">' + head +
            (p.description ? '<div class="desc sl-md" style="margin-top:0">' + SL.md(p.description) + '</div>' : '') +
            '<div class="sl-tp-bars">' + bar('All checks', p.checkedCount, toRun(p.cellCount, p.skippedCount), T + '-total-bar', p.skippedCount) +
                perColumn + '</div>' +
            '</div>';
    }

    /** " | 2 failing" in red, or nothing when no test is failing. */
    function failingBadge(n, testid) {
        return n ? '<span class="sl-tp-failcount" data-testid="' + testid + '">&nbsp;| ' + n + ' failing</span>' : '';
    }

    function tickOf(c, columnId) {
        return (c.checks || []).filter(function (k) { return k.columnId === columnId && k.checked; })[0] || null;
    }

    function skipOf(c, columnId) {
        return (c.skips || []).filter(function (s) { return s.columnId === columnId; })[0] || null;
    }

    /** One tap cycles a cell: blank -> ticked -> skipped -> blank. The title says what the next tap does. */
    function tickTitle(k, s, columnTitle) {
        if (s) return columnTitle + ': skipped' + (s.skippedByName ? ' by ' + s.skippedByName : '') +
            (s.skippedDate ? ' ' + SL.dateTime(s.skippedDate) : '') + ' -- tap to clear';
        if (!k) return 'Tick ' + columnTitle;
        return columnTitle + ': ticked' + (k.checkedByName ? ' by ' + k.checkedByName : '') + (k.checkedDate ? ' ' + SL.dateTime(k.checkedDate) : '') +
            ' -- tap to skip';
    }

    function caseRow(c, cols) {
        // Done: every column ticked or skipped, and at least one ticked. Every column skipped: grayed out.
        var anyTick = cols.some(function (col) { return !!tickOf(c, col.id); });
        var allSettled = cols.length > 0 && cols.every(function (col) { return !!tickOf(c, col.id) || !!skipOf(c, col.id); });
        var done = anyTick && allSettled, allSkipped = !anyTick && allSettled;
        var cells = cols.map(function (col) {
            var k = tickOf(c, col.id), s = k ? null : skipOf(c, col.id);
            var state = k ? 'on' : (s ? 'skip' : '');
            return '<div class="sl-tp-cell"><button type="button" class="sl-tick' + (state ? ' ' + state : '') + '" data-tick-case="' + c.id + '" data-tick-col="' + col.id +
                '" data-state="' + state + '" role="checkbox" aria-checked="' + (k ? 'true' : (s ? 'mixed' : 'false')) +
                '" aria-label="' + esc(tickTitle(k, s, col.title)) + '" title="' + esc(tickTitle(k, s, col.title)) +
                '" data-testid="' + T + '-tick">' + (k ? TICK : (s ? SKIP : '')) + '</button></div>';
        }).join('');
        var failTitle = c.failing ? 'Failing' + (c.failingDate ? ' since ' + SL.dateTime(c.failingDate) : '') + ' -- tap to clear' : 'Mark this test failing';
        var failCell = '<div class="sl-tp-cell"><button type="button" class="sl-tick sl-fail' + (c.failing ? ' on' : '') + '" data-fail-case="' + c.id +
            '" role="checkbox" aria-checked="' + (c.failing ? 'true' : 'false') + '" aria-label="' + esc(failTitle) + '" title="' + esc(failTitle) +
            '" data-testid="' + T + '-fail">' + (c.failing ? CROSS : '') + '</button></div>';
        var noteIcons = '<span class="sl-tp-noteicons">' +
            '<button type="button" class="sl-iconbtn sl-tp-note ' + (c.resultNote ? 'has' : 'empty') + '" data-result-edit="' + c.id + '" title="' +
                (c.resultNote ? 'Edit the note: ' + esc(SL.plain(c.resultNote, 80)) : 'Add a note: what is failing') +
                '" aria-label="' + (c.resultNote ? 'Edit the note' : 'Add a note') + '" data-testid="' + T + '-result-icon">' + NOTE_ICON + '</button>' +
            (c.resultNote ? '<button type="button" class="sl-iconbtn" data-result-view="' + c.id + '" title="Read the note" aria-label="Read the note" data-testid="' + T + '-result-view-icon">' +
                EYE_ICON + '</button>' : '') + '</span>';
        var printCell = '<div class="sl-tp-cell sl-tp-printcell"><input type="checkbox" data-print-case="' + c.id + '"' + (c.includeInPrint ? ' checked' : '') +
            ' title="' + (c.includeInPrint ? 'On the printed checklist -- untick to leave it off' : 'Left off the printed checklist -- tick to print it') +
            '" aria-label="Print this test" data-testid="' + T + '-print"></div>';
        return '<div class="sl-tp-row' + (done ? ' done' : '') + (allSkipped ? ' skipped' : '') + (c.failing ? ' failing' : '') + (c.includeInPrint ? '' : ' noprint') + '" data-case-row="' + c.id + '" data-testid="' + T + '-case-row">' +
            '<div class="sl-tp-text sl-tp-hasgrip">' +
                '<span class="sl-grip sl-tp-grip" data-case-grip="' + c.id + '" data-case-group="' + c.groupId + '" title="Drag to move this test up or down" aria-label="Drag to move this test" data-testid="' + T + '-case-grip">&#8942;&#8942;</span>' +
                '<div class="sl-tp-words" data-case-edit="' + c.id + '" role="button" tabindex="0" title="Tap to edit" data-testid="' + T + '-case-text">' +
                '<div class="sl-md">' + SL.md(c.text) + '</div>' +
                (c.note ? '<div class="note sl-md">' + SL.md(c.note) + '</div>' : '') + '</div>' + noteIcons + '</div>' +
            cells + failCell + printCell + '</div>';
    }

    function group(g, cols) {
        var folded = Page.fold().isCollapsed('tgroup:' + g.id, false, groupScope());
        var total = toRun(g.caseCount * cols.length, g.skippedCount);
        var head = '<div class="sl-cardhead sl-tp-grouphead">' +
            '<span class="sl-grip" data-group-grip="' + g.id + '" title="Drag to move this group up or down" aria-label="Drag to move ' + esc(g.title) + '" data-testid="' + T + '-group-grip">&#8942;&#8942;</span>' +
            '<div class="sl-cardtitle" data-tfold="' + g.id + '" data-testid="' + T + '-group-title">' +
                '<span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span> <h3>' + esc(g.title) + '</h3></div>' +
            '<span class="sl-muted sl-count" data-testid="' + T + '-group-count">' + g.checkedCount + ' of ' + total + esc(skippedText(g.skippedCount)) +
                failingBadge(g.failingCount, T + '-group-failing') + '</span>' +
            '<button type="button" class="sl-btn small" data-add-case="' + g.id + '" data-testid="' + T + '-add-case">+ Case</button>' +
            '<button type="button" class="sl-iconbtn" data-group-edit="' + g.id + '" title="Rename or delete this group" aria-label="Edit group ' + esc(g.title) +
                '" data-testid="' + T + '-group-edit">&#9998;</button>' +
            '</div>';
        if (folded) return '<div class="sl-card folded sl-tp-group" data-tgroup="' + g.id + '" data-testid="' + T + '-group">' + head + '</div>';

        var headRow = '<div class="sl-tp-row head"><div class="sl-tp-text head">Test</div>' + cols.map(function (col) {
            return '<button type="button" class="sl-tp-colhead" data-col-edit="' + col.id + '" title="Rename, untick or delete this column. ' +
                'Tap a cell below to tick it, again to skip it, again to clear it." data-testid="' + T + '-column-head">' +
                esc(col.title) + '</button>';
        }).join('') + '<div class="sl-tp-colhead sl-tp-failhead" title="Mark a test failing, then say what is failing in its note">Failing</div>' +
            '<div class="sl-tp-colhead sl-tp-printhead" title="Untick a test to leave it off the printed checklist">Print</div></div>';
        var rows = g.cases.length
            ? g.cases.map(function (c) { return caseRow(c, cols); }).join('')
            : '<div class="sl-muted" style="padding:6px">No test cases in this group yet.</div>';
        var foot = g.cases.length ? '<div class="sl-tp-row foot"><div class="sl-tp-text">Ticked</div>' + cols.map(function (col) {
            var n = (g.checkedByColumn && g.checkedByColumn[col.id]) || 0;
            var of = toRun(g.caseCount, g.skippedByColumn && g.skippedByColumn[col.id]);
            return '<div class="sl-tp-cell' + (n === of && of > 0 ? ' all' : '') + '">' + n + '/' + of + '</div>';
        }).join('') + '<div class="sl-tp-cell sl-tp-failcount">' + (g.failingCount || '') + '</div><div class="sl-tp-cell"></div></div>' : '';
        return '<div class="sl-card sl-tp-group" data-tgroup="' + g.id + '" data-testid="' + T + '-group">' + head +
            '<div class="sl-tp-grid" style="--tp-n:' + cols.length + '">' + headRow + rows + foot + '</div></div>';
    }

    /** The roll-up head row: the open checklist and how far along it is, so it still says something folded. */
    function toolsSummary() {
        var p = mPlan;
        B('tools-summary').textContent = p
            ? p.title + ' | ' + p.checkedCount + ' of ' + toRun(p.cellCount, p.skippedCount) + ' ticked' + (p.failingCount ? ' | ' + p.failingCount + ' failing' : '')
            : (mPlans.length ? mPlans.length : 'No') + ' ' + cfg.nounPlural;
    }

    function draw() {
        toolsSummary();
        if (!mPlan) {
            $(PANE).innerHTML = '<div class="sl-empty">No ' + cfg.nounPlural + ' yet.</div>';
            SL.status('0 ' + cfg.nounPlural);
            return;
        }
        var p = mPlan;
        $(PANE).innerHTML = summary(p) +
            (p.groups.length ? p.groups.map(function (g) { return group(g, p.columns); }).join('') : '<div class="sl-empty">No groups yet.</div>');

        var heroFold = $(cfg.tab + 'HeroFold');
        if (heroFold) {
            var flipHero = function () { Page.fold().toggle('thero:' + p.id, false); draw(); };
            heroFold.addEventListener('click', flipHero);
            heroFold.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flipHero(); } });
        }
        each('[data-tfold]', function (h) {
            h.addEventListener('click', function () { Page.fold().toggle('tgroup:' + h.getAttribute('data-tfold'), false, groupScope()); draw(); });
        });
        each('[data-add-case]', function (b) { b.addEventListener('click', function () { openCase(0, +b.getAttribute('data-add-case')); }); });
        each('[data-group-edit]', function (b) { b.addEventListener('click', function () { openName('group', +b.getAttribute('data-group-edit')); }); });
        each('[data-col-edit]', function (b) { b.addEventListener('click', function () { openName('column', +b.getAttribute('data-col-edit')); }); });
        each('[data-case-edit]', function (t) {
            t.addEventListener('click', function () { openCase(+t.getAttribute('data-case-edit'), 0); });
            t.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openCase(+t.getAttribute('data-case-edit'), 0); } });
        });
        each('[data-tick-case]', function (b) { b.addEventListener('click', function () { tick(b); }); });
        each('[data-fail-case]', function (b) { b.addEventListener('click', function () { markFailing(b); }); });
        each('[data-result-edit]', function (b) { b.addEventListener('click', function () { openResult(+b.getAttribute('data-result-edit'), false); }); });
        each('[data-result-view]', function (b) { b.addEventListener('click', function () { viewResult(+b.getAttribute('data-result-view')); }); });
        each('[data-print-case]', function (box) { box.addEventListener('change', function () { setPrint(box); }); });
        each('[data-case-grip]', function (g) {
            var groupId = +g.getAttribute('data-case-group');
            Page.wireProjectGrip(g, '[data-case-row]', 'data-case-row', draw, function (ids) { saveOrder('/api/solutions/test-plans/groups/' + groupId + '/case-order', ids); });
        });
        each('[data-group-grip]', function (g) {
            Page.wireProjectGrip(g, '[data-tgroup]', 'data-tgroup', draw, function (ids) { saveOrder('/api/solutions/test-plans/' + mPlanId + '/group-order', ids); });
        });

        SL.status(p.groups.length + (p.groups.length === 1 ? ' group' : ' groups') + ' | ' + p.caseCount +
            (p.caseCount === 1 ? ' test case' : ' test cases') + ' | ' + p.checkedCount + ' of ' + toRun(p.cellCount, p.skippedCount) + ' checks ticked' +
            skippedText(p.skippedCount) +
            (p.failingCount ? ' | ' + p.failingCount + ' failing' : ''));
        if (mFlashCase) flashCaseNow();
    }

    /** The Search tab named a test: open its group if it is folded, scroll to its row and flash it. */
    function flashCaseNow() {
        var id = mFlashCase;
        mFlashCase = 0;
        var row = $(PANE).querySelector('[data-case-row="' + id + '"]');
        if (!row) {
            (mPlan && mPlan.groups || []).forEach(function (g) {
                if ((g.cases || []).some(function (c) { return c.id === id; })) Page.fold().set('tgroup:' + g.id, false);
            });
            draw();
            row = $(PANE).querySelector('[data-case-row="' + id + '"]');
            if (!row) return;
        }
        row.classList.add('flash');
        row.scrollIntoView({ block: 'center' });
    }

    function each(selector, fn) { Array.prototype.forEach.call($(PANE).querySelectorAll(selector), fn); }

    // ---- Print: whether a case appears on the printed checklist -- saved as soon as it changes ------

    async function setPrint(box) {
        var id = +box.getAttribute('data-print-case'), on = box.checked;
        box.disabled = true;
        var r = await SL.put('/api/solutions/test-plans/cases/' + id, { includeInPrint: on });
        if (!r || !r.ok || !r.data) { box.checked = !on; box.disabled = false; SL.toast(SL.failText(r, 'That could not be saved.')); return; }
        usePlan(r.data);
    }

    // ---- order: drag a case (within its group) or a group by its grip ---------------------------

    async function saveOrder(url, ids) {
        var r = await SL.put(url, { ids: ids });
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'The new order could not be saved.')); await openPlan(mPlanId); return; }
        usePlan(r.data);
    }

    // ---- ticks --------------------------------------------------------------------------------

    /** One tap cycles the cell: blank -> ticked -> skipped (Story 3534) -> blank. Shown at once, saved in the queue. */
    function tick(button) {
        var caseId = +button.getAttribute('data-tick-case'), colId = +button.getAttribute('data-tick-col');
        var was = button.getAttribute('data-state') || '';
        var next = was === '' ? 'on' : (was === 'on' ? 'skip' : '');
        button.setAttribute('data-state', next);
        button.classList.toggle('on', next === 'on');
        button.classList.toggle('skip', next === 'skip');
        button.innerHTML = next === 'on' ? TICK : (next === 'skip' ? SKIP : '');
        button.setAttribute('aria-checked', next === 'on' ? 'true' : (next === 'skip' ? 'mixed' : 'false'));
        var body = next === 'on' ? { checked: true } : (next === 'skip' ? { skipped: true } : { skipped: false });
        send('/api/solutions/test-plans/cases/' + caseId + '/checks/' + colId, body, 'That could not be saved.', null);
    }

    /** The red circle: mark a test failing or not. Failing with no note yet opens the note, to say what is failing. */
    function markFailing(button) {
        var caseId = +button.getAttribute('data-fail-case');
        var on = !button.classList.contains('on');
        button.classList.toggle('on', on);
        button.innerHTML = on ? CROSS : '';
        button.setAttribute('aria-checked', on ? 'true' : 'false');
        var row = button.closest('[data-case-row]');
        if (row) row.classList.toggle('failing', on);
        var found = caseById(caseId);
        send('/api/solutions/test-plans/cases/' + caseId + '/result', { failing: on }, 'That could not be saved.',
            on && found && !found.item.resultNote ? function () { openResult(caseId, true); } : null);
    }

    /** One write in the queue: sent after the ones before it; the grid redraws once the last is answered. */
    function send(url, body, failText, afterOk) {
        mTicksPending++;
        mTickChain = mTickChain.then(async function () {
            var r = await SL.put(url, body);
            if (r && r.ok && r.data) { mLatest = r.data; if (afterOk) mAfterFlush.push(afterOk); }
            else SL.toast(SL.failText(r, failText));
        }).catch(function () { SL.toast(failText); }).then(function () {
            mTicksPending--;
            if (mTicksPending > 0) return;
            if (mLatest) { usePlan(mLatest); mLatest = null; } else openPlan(mPlanId);
            var after = mAfterFlush; mAfterFlush = [];
            after.forEach(function (fn) { fn(); });
        });
    }

    // ---- a test's note: what is failing (edit dialog), and the sepia reading view ---------------

    function openResult(caseId, fromFailing) {
        var found = caseById(caseId);
        if (!found) return;
        var c = found.item;
        mResultCaseId = caseId;
        SL.warn('trWarn', '');
        $('trHeading').textContent = fromFailing ? 'What is failing' : (c.resultNote ? 'Edit note' : 'Add a note');
        $('trContext').textContent = SL.plain(c.text, 200);
        $('trFailing').checked = !!c.failing;
        $('trNote').value = c.resultNote || '';
        SL.open('testResultOverlay');
        setTimeout(function () { $('trNote').focus(); }, 30);
    }

    async function saveResult() {
        var r = await SL.put('/api/solutions/test-plans/cases/' + mResultCaseId + '/result', { failing: $('trFailing').checked, note: $('trNote').value });
        if (!r || !r.ok || !r.data) { SL.warn('trWarn', SL.failText(r, 'The note could not be saved.')); return; }
        SL.close('testResultOverlay');
        usePlan(r.data);
    }

    /** The sepia view. {@code text} (optional) is the note as typed in the open dialog, not yet saved. */
    function viewResult(caseId, text) {
        var found = caseById(caseId);
        if (!found) return;
        var c = found.item, note = text == null ? c.resultNote : text;
        mResultCaseId = caseId;
        $('trViewTitle').textContent = SL.plain(c.text, 300);
        var bits = [found.group.title];
        bits.push(c.failing ? 'Failing' + (c.failingDate ? ' since ' + SL.dateTime(c.failingDate) : '') : 'Not failing');
        if (text == null && c.resultDate) bits.push('Note by ' + (c.resultByName || 'someone') + ' ' + SL.dateTime(c.resultDate));
        $('trViewMeta').innerHTML = bits.map(esc).join(' | ');
        if (note && note.trim()) SL.renderMarkdown($('trViewBody'), note);
        else $('trViewBody').innerHTML = '<p class="sl-sepia-empty">Nothing written here.</p>';
        SL.open('testResultViewOverlay');
        $('trViewScroll').scrollTop = 0;
    }

    function closeResultView() { SL.close('testResultViewOverlay'); }

    /** Edit from the sepia view: back to the dialog if it is already open underneath, else open it. */
    function editFromResultView() {
        closeResultView();
        if (!$('testResultOverlay').classList.contains('open')) openResult(mResultCaseId, false);
    }

    // ---- lookups inside the open checklist ----------------------------------------------------

    function groupById(id) { return mPlan.groups.filter(function (g) { return g.id === id; })[0] || null; }
    function columnById(id) { return mPlan.columns.filter(function (c) { return c.id === id; })[0] || null; }
    function caseById(id) {
        for (var i = 0; i < mPlan.groups.length; i++) {
            var hit = mPlan.groups[i].cases.filter(function (c) { return c.id === id; })[0];
            if (hit) return { item: hit, group: mPlan.groups[i] };
        }
        return null;
    }
    function ticksInColumn(id) { var c = columnById(id); return c ? c.checkedCount : 0; }
    function shortText(text) { return SL.plain(text, 120); }

    // ---- the checklist itself -----------------------------------------------------------------

    function openPlanDialog(isNew) {
        mEdit = { kind: 'plan', id: isNew ? 0 : mPlanId };
        SL.warn('tpWarn', '');
        $('tpHeading').textContent = (isNew ? 'New ' : 'Edit ') + cfg.noun;
        $('tpTitle').value = isNew ? '' : mPlan.title;
        $('tpDescription').value = isNew ? '' : (mPlan.description || '');
        SL.open('testPlanOverlay');
        setTimeout(function () { $('tpTitle').focus(); }, 30);
    }

    async function savePlanDialog() {
        var title = $('tpTitle').value.trim();
        if (!title) { SL.warn('tpWarn', 'A testing checklist needs a title.'); return; }
        var body = { title: title, description: $('tpDescription').value };
        if (!mEdit.id) body.kind = cfg.kind;
        var r = mEdit.id
            ? await SL.put('/api/solutions/test-plans/' + mEdit.id, body)
            : await SL.post('/api/solutions/' + Page.id + '/test-plans', body);
        if (!r || !r.ok || !r.data) { SL.warn('tpWarn', SL.failText(r, 'The checklist could not be saved.')); return; }
        SL.close('testPlanOverlay');
        usePlan(r.data);
    }

    async function deletePlan() {
        var p = mPlan;
        if (!p) return;
        var question = 'Delete the ' + cfg.noun + ' "' + p.title + '"?\n\n' + p.groups.length + (p.groups.length === 1 ? ' group, ' : ' groups, ') +
            p.caseCount + (p.caseCount === 1 ? ' test case' : ' test cases') + ' and ' + p.checkedCount + (p.checkedCount === 1 ? ' tick' : ' ticks') +
            ' go with it. This cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/test-plans/' + p.id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The checklist could not be deleted.')); return; }
        mPlans = mPlans.filter(function (x) { return x.id !== p.id; });
        mPlan = null; mPlanId = 0;
        await load();
    }

    async function clearAll() {
        var p = mPlan;
        if (!p) return;
        if (!p.checkedCount && !p.failingCount) { SL.toast('Nothing is ticked or failing.'); return; }
        var what = [];
        if (p.checkedCount) what.push('untick all ' + p.checkedCount + (p.checkedCount === 1 ? ' tick' : ' ticks'));
        if (p.failingCount) what.push('clear ' + p.failingCount + (p.failingCount === 1 ? ' failing mark' : ' failing marks'));
        var ask = what.join(' and ');
        if (!window.confirm(ask.charAt(0).toUpperCase() + ask.slice(1) + ' in "' + p.title + '"?\n\nUse this to run the tests again. ' +
            'The test cases, groups, columns and notes stay' + (p.skippedCount ? ', and the ' + p.skippedCount +
            (p.skippedCount === 1 ? ' skipped check stays skipped' : ' skipped checks stay skipped') : '') + '.')) return;
        var r = await SL.post('/api/solutions/test-plans/' + p.id + '/clear', {});
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'The ticks could not be cleared.')); return; }
        usePlan(r.data);
    }

    // One checklist's groups share a remembered default (its scope), so Collapse all also folds the
    // groups added to it later -- Tony's rule (2026-09-24) -- without touching other checklists.
    function groupScope() { return mPlan ? 'tgroupof:' + mPlan.id + ':' : ''; }

    function foldAll(collapsed) {
        if (!mPlan) return;
        Page.fold().setAllForPrefix(groupScope(), collapsed, mPlan.groups.map(function (g) { return 'tgroup:' + g.id; }));
        draw();
    }

    // ---- a group or a column: a name, plus that thing's own actions ----------------------------

    function openName(kind, id) {
        var isNew = !id;
        var thing = kind === 'group' ? (isNew ? null : groupById(id)) : (isNew ? null : columnById(id));
        mEdit = { kind: kind, id: id || 0 };
        SL.warn('tnWarn', '');
        $('tnHeading').textContent = (isNew ? 'New ' : 'Edit ') + (kind === 'group' ? 'group' : 'column');
        $('tnLabel').textContent = kind === 'group' ? 'Group name' : 'Column name';
        $('tnName').placeholder = kind === 'group' ? 'For example: Starting a batch' : 'For example: End to end';
        $('tnName').value = thing ? thing.title : '';
        $('tnDelete').hidden = isNew;
        $('tnDelete').textContent = kind === 'group' ? 'Delete group' : 'Delete column';
        $('tnClear').hidden = isNew || kind !== 'column';
        SL.open('testNameOverlay');
        setTimeout(function () { $('tnName').focus(); }, 30);
    }

    async function saveName() {
        var name = $('tnName').value.trim();
        if (!name) { SL.warn('tnWarn', mEdit.kind === 'group' ? 'A group needs a name.' : 'A column needs a name.'); return; }
        var base = '/api/solutions/test-plans/';
        var r;
        if (mEdit.kind === 'group') r = mEdit.id ? await SL.put(base + 'groups/' + mEdit.id, { title: name }) : await SL.post(base + mPlanId + '/groups', { title: name });
        else r = mEdit.id ? await SL.put(base + 'columns/' + mEdit.id, { title: name }) : await SL.post(base + mPlanId + '/columns', { title: name });
        if (!r || !r.ok || !r.data) { SL.warn('tnWarn', SL.failText(r, 'That could not be saved.')); return; }
        SL.close('testNameOverlay');
        usePlan(r.data);
    }

    async function deleteNamed() {
        var question, url;
        if (mEdit.kind === 'group') {
            var g = groupById(mEdit.id);
            if (!g) return;
            question = 'Delete the group "' + g.title + '" from "' + mPlan.title + '"?\n\nIts ' + g.caseCount + (g.caseCount === 1 ? ' test case' : ' test cases') +
                ' and ' + g.checkedCount + (g.checkedCount === 1 ? ' tick go' : ' ticks go') + ' with it. This cannot be undone.';
            url = '/api/solutions/test-plans/groups/' + g.id;
        } else {
            var c = columnById(mEdit.id);
            if (!c) return;
            question = 'Delete the column "' + c.title + '" from "' + mPlan.title + '"?\n\nIts ' + c.checkedCount + (c.checkedCount === 1 ? ' tick goes' : ' ticks go') +
                ' with it. The test cases stay. This cannot be undone.';
            url = '/api/solutions/test-plans/columns/' + c.id;
        }
        if (!window.confirm(question)) return;
        var r = await SL.del(url);
        if (!r || !r.ok || !r.data) { SL.warn('tnWarn', SL.failText(r, 'That could not be deleted.')); return; }
        SL.close('testNameOverlay');
        usePlan(r.data);
    }

    async function clearColumn() {
        var c = columnById(mEdit.id);
        if (!c) return;
        var n = ticksInColumn(c.id);
        if (!n) { SL.warn('tnWarn', 'Nothing in "' + c.title + '" is ticked.'); return; }
        if (!window.confirm('Untick the ' + n + (n === 1 ? ' tick' : ' ticks') + ' in the column "' + c.title + '" of "' + mPlan.title + '"?\n\nThe other columns are left alone' + (c.skippedCount ? ', and its ' + c.skippedCount +
            (c.skippedCount === 1 ? ' skipped check stays skipped' : ' skipped checks stay skipped') : '') + '.')) return;
        var r = await SL.post('/api/solutions/test-plans/' + mPlanId + '/clear', { columnId: c.id });
        if (!r || !r.ok || !r.data) { SL.warn('tnWarn', SL.failText(r, 'The column could not be unticked.')); return; }
        SL.close('testNameOverlay');
        usePlan(r.data);
    }

    // ---- a test case ----------------------------------------------------------------------------

    function openCase(caseId, groupId) {
        var found = caseId ? caseById(caseId) : null;
        if (caseId && !found) return;
        var g = found ? found.group : groupById(groupId);
        mEdit = { kind: 'case', id: caseId || 0, groupId: g ? g.id : 0 };
        SL.warn('tcWarn', '');
        $('tcHeading').textContent = caseId ? 'Edit test case' : 'New test case';
        $('tcContext').textContent = g ? 'Group: ' + g.title : '';
        $('tcText').value = found ? found.item.text : '';
        $('tcNote').value = found ? (found.item.note || '') : '';
        $('tcGroupFld').hidden = !caseId || mPlan.groups.length < 2;
        SL.fillSelect($('tcGroup'), mPlan.groups.map(function (x) { return { value: String(x.id), label: x.title }; }), g ? g.id : '');
        $('tcDelete').hidden = !caseId;
        SL.open('testCaseOverlay');
        setTimeout(function () { $('tcText').focus(); }, 30);
    }

    async function saveCase() {
        var text = $('tcText').value.trim();
        if (!text) { SL.warn('tcWarn', 'A test case needs some text: what to try, and what should happen.'); return; }
        var r;
        if (mEdit.id) r = await SL.put('/api/solutions/test-plans/cases/' + mEdit.id, { text: text, note: $('tcNote').value, groupId: +$('tcGroup').value || mEdit.groupId });
        else r = await SL.post('/api/solutions/test-plans/groups/' + mEdit.groupId + '/cases', { text: text, note: $('tcNote').value });
        if (!r || !r.ok || !r.data) { SL.warn('tcWarn', SL.failText(r, 'The test case could not be saved.')); return; }
        SL.close('testCaseOverlay');
        usePlan(r.data);
    }

    async function deleteCase() {
        var found = caseById(mEdit.id);
        if (!found) return;
        var ticks = (found.item.checks || []).filter(function (k) { return k.checked; }).length;
        var question = 'Delete the test case "' + shortText(found.item.text) + '" from the group "' + found.group.title + '"?\n\n' +
            (ticks ? 'Its ' + ticks + (ticks === 1 ? ' tick goes' : ' ticks go') + ' with it. ' : '') +
            (found.item.resultNote ? 'Its note ("' + SL.plain(found.item.resultNote, 60) + '") goes with it. ' : '') + 'This cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/test-plans/cases/' + found.item.id);
        if (!r || !r.ok || !r.data) { SL.warn('tcWarn', SL.failText(r, 'The test case could not be deleted.')); return; }
        SL.close('testCaseOverlay');
        usePlan(r.data);
    }

    // ---- print: a clean table, through the Leonardo Print Menu --------------------------------

    var PRINT_CSS =
        '.tp h2{font-size:14px;margin:16px 0 4px}' +
        '.tp table{width:100%;border-collapse:collapse;font-size:11px}' +
        '.tp th,.tp td{border:1px solid #D1D5DB;padding:4px 6px;vertical-align:top}' +
        '.tp th{background:#F3F4F6;text-align:left}' +
        '.tp td.c,.tp th.c{text-align:center;width:70px}' +
        '.tp .ok{color:#15803D;font-weight:700}' +
        '.tp tr.done td.t{color:#15803D}' +
        '.tp .note{color:#6B7280;font-size:10px}' +
        '.tp .bad{color:#B91C1C;font-weight:700}' +
        '.tp tr.failing td.t{color:#111827;background:#FEF2F2}' +
        '.tp .result{color:#B91C1C;font-size:10px;margin-top:2px}' +
        '.tp .sum{font-size:12px;margin:4px 0 10px}' +
        '.tp .skip{color:#6B7280;font-size:10px}' +
        '.tp tr.skipped td.t{color:#6B7280}';

    // The QA handout (2026-10-15): no table -- each group a heading, each test a line with an empty round
    // box on the left to tick by hand. A border, not a :before glyph, so the PDF renderer keeps it.
    var QA_PRINT_CSS =
        '.qa h2{font-size:14px;margin:16px 0 6px}' +
        '.qa .qi{margin:0 0 9px;font-size:12px;page-break-inside:avoid}' +
        '.qa .o{float:left;width:13px;height:13px;border:1.5px solid #374151;border-radius:50%;margin-top:1px}' +
        '.qa .tx{margin-left:24px}' +
        '.qa .tx p{margin:0}' +
        '.qa .note{color:#6B7280;font-size:10px;margin-top:2px}' +
        '.qa .sum{font-size:12px;margin:4px 0 10px;color:#374151}';

    /** The QA tab prints a plain checklist: a round box and the test, group by group. */
    function printHandout(p, groups) {
        var html = '<div class="qa"><h1 style="font-size:17px;margin:0 0 4px">' + esc(p.title) + '</h1>' +
            '<div class="sum">' + esc(Page.solution.name) + '</div>' +
            (p.description ? '<div class="sl-md">' + SL.md(p.description) + '</div>' : '') +
            groups.map(function (g) {
                return '<h2>' + esc(g.title) + '</h2>' + g.cases.map(function (c) {
                    return '<div class="qi"><span class="o"></span><div class="tx">' + SL.md(c.text) +
                        (c.note ? '<div class="note">' + SL.md(c.note) + '</div>' : '') + '</div></div>';
                }).join('');
            }).join('') + '</div>';
        $('printArea').innerHTML = html;
        window.Picasso.printThisPage({ selector: '#printArea', title: Page.solution.name + ' - ' + p.title, extraCss: QA_PRINT_CSS });
    }

    function print() {
        var p = mPlan;
        if (!p) { SL.toast('Open a checklist first.'); return; }
        var cols = p.columns;
        // Only the cases marked Print. The counts are of what is printed, so the page reads whole on its own.
        var groups = p.groups.map(function (g) {
            return { title: g.title, cases: g.cases.filter(function (c) { return c.includeInPrint; }) };
        }).filter(function (g) { return g.cases.length; });
        var shownCases = 0, shownTicks = 0, shownSkips = 0, shownFailing = 0;
        var colTicks = cols.map(function () { return 0; }), colSkips = cols.map(function () { return 0; });
        groups.forEach(function (g) {
            g.cases.forEach(function (c) {
                shownCases++;
                if (c.failing) shownFailing++;
                cols.forEach(function (col, i) {
                    if (tickOf(c, col.id)) { shownTicks++; colTicks[i]++; }
                    else if (skipOf(c, col.id)) { shownSkips++; colSkips[i]++; }
                });
            });
            g.ticks = g.cases.reduce(function (n, c) { return n + cols.filter(function (col) { return !!tickOf(c, col.id); }).length; }, 0);
            g.skips = g.cases.reduce(function (n, c) {
                return n + cols.filter(function (col) { return !tickOf(c, col.id) && !!skipOf(c, col.id); }).length;
            }, 0);
        });
        if (!shownCases) { SL.toast(p.caseCount ? 'Every test is set not to print. Tick Print on the ones you want.' : 'This checklist has no tests to print yet.'); return; }
        if (IS_QA) { printHandout(p, groups); return; }
        var html = '<div class="tp"><h1 style="font-size:17px;margin:0 0 4px">' + esc(p.title) + '</h1>' +
            '<div class="sum">' + esc(Page.solution.name) + ' | ' + shownTicks + ' of ' + toRun(shownCases * cols.length, shownSkips) +
            ' checks ticked' + esc(skippedText(shownSkips)) + ' | ' +
            cols.map(function (c, i) { return esc(c.title) + ' ' + colTicks[i] + ' of ' + toRun(shownCases, colSkips[i]); }).join(' | ') +
            (shownFailing ? ' | <span class="bad">' + shownFailing + ' failing</span>' : '') + '</div>' +
            (p.description ? '<div class="sl-md">' + SL.md(p.description) + '</div>' : '') +
            groups.map(function (g) {
                return '<h2>' + esc(g.title) + ' (' + g.ticks + ' of ' + toRun(g.cases.length * cols.length, g.skips) + esc(skippedText(g.skips)) + ')</h2>' +
                    '<table><thead><tr><th>Test</th>' + cols.map(function (c) { return '<th class="c">' + esc(c.title) + '</th>'; }).join('') + '</tr></thead><tbody>' +
                    g.cases.map(function (c) {
                        var anyTick = cols.some(function (col) { return !!tickOf(c, col.id); });
                        var settled = cols.length > 0 && cols.every(function (col) { return !!tickOf(c, col.id) || !!skipOf(c, col.id); });
                        var done = anyTick && settled, allSkipped = !anyTick && settled;
                        var mark = c.failing ? '<span class="bad">' + CROSS + ' FAILING</span> ' : (done ? '<span class="ok">' + TICK + '</span> ' : '');
                        var cls = c.failing ? ' class="failing"' : (done ? ' class="done"' : (allSkipped ? ' class="skipped"' : ''));
                        return '<tr' + cls + '><td class="t">' + mark + SL.md(c.text) +
                            (c.note ? '<div class="note">' + SL.md(c.note) + '</div>' : '') +
                            (c.resultNote ? '<div class="result">' + SL.md(c.resultNote) + '</div>' : '') + '</td>' +
                            cols.map(function (col) {
                                return '<td class="c">' + (tickOf(c, col.id) ? '<span class="ok">' + TICK + '</span>'
                                    : (skipOf(c, col.id) ? '<span class="skip">Skipped</span>' : '')) + '</td>';
                            }).join('') + '</tr>';
                    }).join('') + '</tbody></table>';
            }).join('') + '</div>';
        $('printArea').innerHTML = html;
        window.Picasso.printThisPage({ selector: '#printArea', title: Page.solution.name + ' - ' + p.title, extraCss: PRINT_CSS });
    }

    function wireToolbar() {
        // The tools fold away under one head row that keeps the checklist's name and count (Roll Up Tools).
        window.Picasso.rollup.attach({ key: 'leo.sl.' + cfg.tab + '.tools.collapsed', pane: $('toolbar-' + cfg.tab), toggle: B('tools-toggle') });
        B('plan-select').addEventListener('change', function () {
            var id = +B('plan-select').value;
            if (id && id !== mPlanId) openPlan(id);
        });
        B('add-plan').addEventListener('click', function () { openPlanDialog(true); });
        B('edit-plan').addEventListener('click', function () { openPlanDialog(false); });
        B('add-group').addEventListener('click', function () { openName('group', 0); });
        B('add-column').addEventListener('click', function () { openName('column', 0); });
        B('expand').addEventListener('click', function () { foldAll(false); });
        B('collapse').addEventListener('click', function () { foldAll(true); });
        B('clear').addEventListener('click', clearAll);
        B('delete').addEventListener('click', deletePlan);
    }

    var api = {
        kind: cfg.kind,
        wireToolbar: wireToolbar,
        load: load,
        print: print,
        /** A deep link (?testPlan=) names the checklist to open before the tab is first shown. */
        choosePlan: function (id) { mPlanId = id; },
        /** The Search tab names a test to scroll to once its checklist is drawn. */
        flashCase: function (id) { mFlashCase = id; },
        savePlanDialog: savePlanDialog, saveName: saveName, deleteNamed: deleteNamed, clearColumn: clearColumn,
        saveCase: saveCase, deleteCase: deleteCase, saveResult: saveResult,
        viewTypedResult: function () { viewResult(mResultCaseId, $('trNote').value); },
        closeResultView: closeResultView, editFromResultView: editFromResultView
    };
    return api;
    }

    // ---- the shared dialogs answer to the tab that is showing ------------------------------------

    function wireDialogs() {
        function on(id, fn, guarded) {
            $(id).addEventListener('click', function () {
                if (!gActive) return;
                if (guarded) SL.guard($(id), function () { return gActive[fn](); }); else gActive[fn]();
            });
        }
        $('tpCancel').addEventListener('click', function () { SL.close('testPlanOverlay'); });
        on('tpSave', 'savePlanDialog', true);
        $('tnCancel').addEventListener('click', function () { SL.close('testNameOverlay'); });
        on('tnSave', 'saveName', true);
        on('tnDelete', 'deleteNamed', true);
        on('tnClear', 'clearColumn', true);
        $('tcCancel').addEventListener('click', function () { SL.close('testCaseOverlay'); });
        on('tcSave', 'saveCase', true);
        on('tcDelete', 'deleteCase', true);
        $('trCancel').addEventListener('click', function () { SL.close('testResultOverlay'); });
        on('trSave', 'saveResult', true);
        on('trView', 'viewTypedResult', false);
        on('trViewClose', 'closeResultView', false);
        on('trViewEdit', 'editFromResultView', false);
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && gActive && $('testResultViewOverlay').classList.contains('open')) gActive.closeResultView();
        });
    }

    var dev = makeTab({ tab: 'tests', kind: 'dev', testid: 'tests', noun: 'testing checklist', nounPlural: 'testing checklists' });
    var qa  = makeTab({ tab: 'qa',    kind: 'qa',  testid: 'qa',    noun: 'QA checklist',      nounPlural: 'QA checklists' });

    function register(name, tab, alsoDialogs) {
        Page.registerTab(name, {
            init: function () { tab.wireToolbar(); if (alsoDialogs) wireDialogs(); },
            show: tab.load,
            print: tab.print,
            choosePlan: tab.choosePlan,
            flashCase: tab.flashCase
        });
    }
    register('tests', dev, true);
    register('qa', qa, false);
})();
