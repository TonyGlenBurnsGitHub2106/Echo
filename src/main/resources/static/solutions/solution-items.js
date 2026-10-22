(function () {
    'use strict';

    // The Items tab of one solution (Story 3493): every action item as a tree, one collapsible
    // band per project. The server sends flat rows carrying parentItemId, already in order; this
    // file only nests them.
    //
    // Everything folds, and the page remembers what you chose (Page.fold, kept on the device):
    //   - a project band folds by clicking its title;
    //   - an item folds by clicking ITS title. Folded, it is one line -- status, title, and how
    //     many sub-items it has; open, it shows its detail, its tools and its sub-items.
    //   - bands and items start FOLDED, so a big solution opens as a short outline;
    //   - Expand all / Collapse all act on every band and item at once.
    // While a filter is on, everything is shown open: a match must never hide inside a fold.
    // Reordering is two arrows rather than a drag, because arrows work on a phone.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var mItems = [];
    var mEditId = 0, mEditProjectId = 0, mEditParentId = null;
    var mStatusItemId = 0;
    var mFlashId = 0;
    var mFilter = { project: '', status: '', assignee: '', hideClosed: false };
    function filterKey() { return 'sl.items.filter.' + Page.id; }
    function loadFilter() { try { var s = sessionStorage.getItem(filterKey()); if (s) mFilter = Object.assign(mFilter, JSON.parse(s)); } catch (e) { /* private mode */ } }
    function saveFilter() { try { sessionStorage.setItem(filterKey(), JSON.stringify(mFilter)); } catch (e) { /* private mode */ } }

    // ---- load -------------------------------------------------------------------

    async function load() {
        var r = await SL.get('/api/solutions/' + Page.id + '/items');
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $('pane-items').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the items.')) + '</div>';
            return;
        }
        mItems = r.data;
        draw();
    }

    // ---- fold state -----------------------------------------------------------------

    function filtering() { return !!(mFilter.status || mFilter.assignee || mFilter.hideClosed); }
    function bandFolded(projectId) { return !filtering() && Page.fold().isCollapsed('band:' + projectId, true); }
    function itemFolded(itemId)    { return !filtering() && Page.fold().isCollapsed('item:' + itemId, true); }

    function setAll(collapsed) {
        // Tony's rule (2026-09-24): remembered, and reaches bands and items added later.
        Page.fold().setAllForPrefix('band:', collapsed);
        Page.fold().setAllForPrefix('item:', collapsed);
        draw();
    }

    // ---- draw -------------------------------------------------------------------

    function passes(i) {
        if (mFilter.status && i.statusCode !== mFilter.status) return false;
        if (mFilter.assignee && i.assigneeCode !== mFilter.assignee) return false;
        if (mFilter.hideClosed && i.closed) return false;
        return true;
    }

    /** An item is shown when it passes the filter or any descendant does, so a match never loses its path. */
    function visibleIds(items) {
        var byParent = {};
        items.forEach(function (i) { (byParent[i.parentItemId || 0] = byParent[i.parentItemId || 0] || []).push(i); });
        var keep = {};
        function walk(i) {
            var any = passes(i);
            (byParent[i.id] || []).forEach(function (c) { if (walk(c)) any = true; });
            if (any) keep[i.id] = true;
            return any;
        }
        (byParent[0] || []).forEach(walk);
        return { keep: keep, byParent: byParent };
    }

    function countBelow(byParent, id) {
        var n = 0;
        (byParent[id] || []).forEach(function (c) { n += 1 + countBelow(byParent, c.id); });
        return n;
    }

    /** How many of the items under this one, at any depth, are still open. */
    function openBelow(byParent, id) {
        var n = 0;
        (byParent[id] || []).forEach(function (c) { n += (c.closed ? 0 : 1) + openBelow(byParent, c.id); });
        return n;
    }

    function draw() {
        var projects = Page.projects.filter(function (p) { return !mFilter.project || String(p.id) === mFilter.project; });
        if (!Page.projects.length) {
            $('pane-items').innerHTML = '<div class="sl-empty">Add a project on the Overview tab first. Items live inside a project.</div>';
            return;
        }
        var shown = 0;
        var html = projects.map(function (p) {
            var mine = mItems.filter(function (i) { return i.projectId === p.id; });
            var v = visibleIds(mine);
            var rows = [];
            (function emit(parent, depth) {
                (v.byParent[parent] || []).forEach(function (i, index, siblings) {
                    if (!v.keep[i.id]) return;
                    var folded = itemFolded(i.id);
                    rows.push(itemRow(i, depth, index, siblings.length, folded, (v.byParent[i.id] || []).length, countBelow(v.byParent, i.id), openBelow(v.byParent, i.id)));
                    shown++;
                    if (!folded) emit(i.id, depth + 1);
                });
            })(0, 0);
            var open = mine.filter(function (i) { return !i.closed; }).length;
            return '<div class="sl-band' + (bandFolded(p.id) ? ' collapsed' : '') + '" data-band="' + p.id + '" data-testid="items-band">' +
                '<div class="sl-band-head" data-band-toggle="' + p.id + '" data-testid="items-band-toggle">' +
                    '<span class="sl-grip" data-band-grip="' + p.id + '" title="Drag to move this project up or down" aria-label="Drag to move ' + esc(p.name) + '" data-testid="items-band-grip">&#8942;&#8942;</span>' +
                    '<span class="chev">&#9660;</span><span class="name">' + esc(p.name) + '</span>' +
                    '<span class="sl-muted">' + open + ' open of ' + mine.length + '</span>' + Page.copyButton(p) +
                    (mine.length ? '<button type="button" class="sl-iconbtn" data-tick-all-in="' + p.id + '" title="Check or uncheck every item in this project" aria-label="Check or uncheck every item in ' +
                        esc(p.name) + '" data-testid="items-band-tick-all">&#10003;&#10003;</button>' : '') +
                    '<button type="button" class="sl-btn small" data-add-in="' + p.id + '" data-testid="items-band-add">+ Item</button></div>' +
                '<div class="sl-band-body">' + (rows.length ? rows.join('') : '<div class="sl-empty" style="padding:14px">Nothing here' +
                    (mine.length ? ' matches the filter.' : ' yet.') + '</div>') + '</div></div>';
        }).join('');
        $('pane-items').innerHTML = html;
        bind();
        SL.status(mItems.length + (mItems.length === 1 ? ' item' : ' items') + (filtering() ? ' | filtered, everything shown open' : ''));
        if (mFlashId) flashNow();
    }

    /** Skipped is the status coded 'cancelled' (named Skipped): closed for now, not done. */
    function isSkipped(i) { return i.statusCode === 'cancelled'; }
    /** The tick's state: 'on' (done), 'skip' (skipped) or '' (not done). Any other closed status counts as done. */
    function tickState(i) { return isSkipped(i) ? 'skip' : (i.closed ? 'on' : ''); }
    var TICK_TITLE = { '': 'Not done. Tap to mark it done', on: 'Done. Tap to skip it', skip: 'Skipped. Tap to mark it not done' };

    function itemRow(i, depth, index, siblingCount, folded, childCount, belowCount, openCount) {
        var s = Page.statusOf(i.statusCode);
        // The chip says what is INSIDE a folded item: how many, and how many of those are still open.
        var subs = childCount ? '<span class="sl-chip' + (openCount ? ' wait' : '') + '" data-testid="items-sub-count">' + childCount + (childCount === 1 ? ' sub-item' : ' sub-items') +
            (belowCount > childCount ? ', ' + belowCount + ' in all' : '') + (openCount ? ', ' + openCount + ' open' : ', all closed') + '</span>' : '';
        var grip = depth === 0 ? '<span class="sl-grip small" data-item-grip="' + i.id + '" title="Drag to move this item up or down" aria-label="Drag to move ' + esc(i.title) + '" data-testid="items-grip">&#8942;&#8942;</span>' : '';
        // One tap moves the item on a step -- no picker, no edit form. Three states, like the
        // Tests tab's cells: not done -> done (a tick) -> skipped (a dash: status Skipped -- not now, maybe
        // later) -> not done again (Tony, 2026-10-09).
        var t = tickState(i);
        var tick = '<button type="button" class="sl-tick' + (t ? ' ' + t : '') + '" data-tick="' + i.id + '" role="checkbox" aria-checked="' +
            (t === 'on' ? 'true' : (t === 'skip' ? 'mixed' : 'false')) + '" title="' + TICK_TITLE[t] + '" aria-label="' + TICK_TITLE[t] + ': ' + esc(i.title) +
            '" data-testid="items-tick">' + (t === 'on' ? '&#10003;' : (t === 'skip' ? '&#8211;' : '')) + '</button>';
        var head = grip + tick + '<button type="button" class="sl-pill" style="background:' + esc(s.colorHex || '#6B7280') + '" data-status-of="' + i.id +
                '" data-testid="items-status-btn">' + esc(i.statusName) + '</button>' +
            '<div class="main" data-fold="' + i.id + '" data-testid="items-title">' +
                '<div class="title"><span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span> ' + esc(i.title) + ' ' + subs + '</div>';
        // Every row carries the same tool strip, folded or not (Tony, 2026-10-12: a folded row had none, so
        // most rows -- they start folded -- could not be edited, viewed or copied without unfolding first).
        var tools = '<div class="tools">' +
                '<button type="button" class="sl-iconbtn sl-arrange-only" title="Move up" data-move="' + i.id + '" data-to="' + (index) + '"' + (index === 0 ? ' disabled' : '') + ' data-testid="items-up-btn">&#9650;</button>' +
                '<button type="button" class="sl-iconbtn sl-arrange-only" title="Move down" data-move="' + i.id + '" data-to="' + (index + 2) + '"' + (index >= siblingCount - 1 ? ' disabled' : '') + ' data-testid="items-down-btn">&#9660;</button>' +
                '<button type="button" class="sl-iconbtn" title="Add a sub-item" data-sub-of="' + i.id + '" data-testid="items-sub-btn">+</button>' +
                (childCount ? '<button type="button" class="sl-iconbtn" title="Check or uncheck every sub-item" aria-label="Check or uncheck every sub-item of ' + esc(i.title) +
                    '" data-tick-subs="' + i.id + '" data-testid="items-sub-tick-all">&#10003;&#10003;</button>' : '') +
                '<button type="button" class="sl-iconbtn" title="View" aria-label="View ' + esc(i.title) + '" data-view="' + i.id + '" data-testid="items-view-btn">&#128065;</button>' +
                '<button type="button" class="sl-iconbtn" title="Edit" data-edit="' + i.id + '" data-testid="items-edit-btn">&#9998;</button>' +
                '<button type="button" class="sl-iconbtn" title="Copy the title" aria-label="Copy the title ' + esc(i.title) + '" data-copy-item="' + i.id + '" data-testid="items-copy-btn">&#10697;</button>' +
                '<button type="button" class="sl-iconbtn" title="Delete" data-delete="' + i.id + '" data-testid="items-delete-btn">&#10005;</button>' +
            '</div>';
        if (folded) {
            return '<div class="sl-item folded' + (i.closed ? ' closed' : '') + (isSkipped(i) ? ' skipped' : '') + '" id="item-' + i.id + '" data-item-row="' + i.id + '" data-depth="' + depth + '" data-index="' + index + '" style="padding-left:' + (6 + depth * 18) +
                'px" data-testid="items-row">' + head + '</div>' + tools + '</div>';
        }
        var meta = [Page.personChip(i.assigneeCode, i.assigneeName)];
        if (i.priority && i.priority !== 3) meta.push('<span class="sl-chip' + (i.priority < 3 ? ' warn' : '') + '">P' + i.priority + '</span>');
        if (i.dueDate) meta.push('<span class="sl-muted">Due ' + SL.date(i.dueDate) + '</span>');
        if (i.completedDate) meta.push('<span class="sl-muted">Closed ' + SL.date(i.completedDate) + '</span>');
        if (i.logCount) meta.push('<span class="sl-muted">' + i.logCount + ' log</span>');
        return '<div class="sl-item' + (i.closed ? ' closed' : '') + (isSkipped(i) ? ' skipped' : '') + '" id="item-' + i.id + '" data-item-row="' + i.id + '" data-depth="' + depth + '" data-index="' + index + '" style="padding-left:' + (6 + depth * 18) + 'px" data-testid="items-row">' +
            head +
                '<div class="meta">' + meta.join('') + '</div>' +
                (i.detailPreview ? '<div class="preview sl-md" data-testid="items-detail">' + SL.md(i.detailPreview, i.detailPreview.length >= 300) + '</div>' : '') +
            '</div>' +
            tools + '</div>';
    }

    function each(selector, fn) { Array.prototype.forEach.call(document.querySelectorAll(selector), fn); }

    function bind() {
        // The project groups reorder the same way the Overview rows do; the order is the project's own.
        each('[data-band-grip]', function (g) { Page.wireProjectGrip(g, '[data-band]', 'data-band', draw); });
        each('[data-band-toggle]', function (h) {
            h.addEventListener('click', function (e) {
                if (e.target.closest('button') || e.target.closest('[data-band-grip]')) return;
                Page.fold().toggle('band:' + h.getAttribute('data-band-toggle'), true);
                draw();
            });
        });
        each('[data-fold]', function (t) {
            t.addEventListener('click', function () {
                Page.fold().toggle('item:' + t.getAttribute('data-fold'), true);
                draw();
            });
        });
        each('[data-add-in]',    function (b) { b.addEventListener('click', function () { openItem(0, +b.getAttribute('data-add-in'), null); }); });
        each('[data-sub-of]',    function (b) { b.addEventListener('click', function () {
            var parent = byId(+b.getAttribute('data-sub-of'));
            if (parent) openItem(0, parent.projectId, parent.id);
        }); });
        each('[data-edit]',      function (b) { b.addEventListener('click', function () { openItem(+b.getAttribute('data-edit'), 0, null); }); });
        each('[data-view]',      function (b) { b.addEventListener('click', function () { openItemView(+b.getAttribute('data-view')); }); });
        each('[data-copy-item]', function (b) { b.addEventListener('click', function () { SL.guard(b, function () { return copyTitle(+b.getAttribute('data-copy-item')); }); }); });
        each('[data-item-grip]', wireItemGrip);
        each('[data-tick]', function (b) { b.addEventListener('click', function () { SL.guard(b, function () { return tickItem(+b.getAttribute('data-tick')); }); }); });
        each('[data-tick-all-in]', function (b) { b.addEventListener('click', function (e) {
            e.stopPropagation();                                                   // the band head folds on a click
            SL.guard(b, function () { return tickMany('project', +b.getAttribute('data-tick-all-in')); });
        }); });
        each('[data-tick-subs]', function (b) { b.addEventListener('click', function () { SL.guard(b, function () { return tickMany('item', +b.getAttribute('data-tick-subs')); }); }); });
        each('[data-status-of]', function (b) { b.addEventListener('click', function () { openStatus(+b.getAttribute('data-status-of')); }); });
        each('[data-delete]',    function (b) { b.addEventListener('click', function () { removeItem(+b.getAttribute('data-delete')); }); });
        each('[data-move]',      function (b) { b.addEventListener('click', function () { moveItem(+b.getAttribute('data-move'), +b.getAttribute('data-to')); }); });
    }

    function byId(id) { return mItems.filter(function (i) { return i.id === id; })[0]; }

    /** Open the band and every ancestor of an item, so the item itself is on screen. */
    function reveal(itemId) {
        var i = byId(itemId);
        if (!i) return;
        Page.fold().set('band:' + i.projectId, false);
        for (var p = byId(i.parentItemId); p; p = byId(p.parentItemId)) Page.fold().set('item:' + p.id, false);
    }

    // ---- view / copy ----------------------------------------------------------------
    //
    // View is the read-only sepia page the Notes tab uses: the whole item, not the preview the
    // tree carries. Its Copy puts the same thing on the clipboard as plain text; the Copy button on a
    // row in the tree copies the title alone (Tony, 2026-10-09). View and its Copy fetch the item,
    // because only GET /items/{id} carries the full detail. The detail may have been written by
    // an outside agent, so it is drawn through SL.renderMarkdown and every other field esc()aped.

    var mViewItemId = 0;

    async function fetchItem(id, failText) {
        var r = await SL.get('/api/solutions/items/' + id);
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, failText)); return null; }
        return r.data;
    }

    /** The one-line summary under the title: project, status, who, priority, dates. Plain text. */
    function itemMetaBits(i) {
        var bits = [];
        if (i.projectName) bits.push(i.projectName);
        if (i.statusName) bits.push(i.statusName);
        bits.push(i.assigneeName || 'Nobody yet');
        if (i.priority && i.priority !== 3) bits.push('Priority ' + i.priority);
        if (i.dueDate) bits.push('Due ' + SL.date(i.dueDate));
        if (i.completedDate) bits.push('Closed ' + SL.date(i.completedDate));
        return bits;
    }

    async function openItemView(id) {
        var i = await fetchItem(id, 'That item could not be opened.');
        if (!i) return;
        mViewItemId = i.id;
        $('itemViewTitle').textContent = i.title;
        $('itemViewMeta').innerHTML = itemMetaBits(i).map(esc).join(' | ');
        if (i.detail) SL.renderMarkdown($('itemViewBody'), i.detail);
        else $('itemViewBody').innerHTML = '<p class="sl-sepia-empty">Nothing written here.</p>';
        // Sub-items come from the tree already on screen. A closed one is a green tick, never a
        // strike-through.
        var kids = mItems.filter(function (c) { return c.parentItemId === i.id; });
        $('itemViewSubs').innerHTML = kids.length ? '<h3 class="sl-sepia-h">Sub-items</h3>' + kids.map(function (c, n) {
            return '<div class="sl-sepia-step" data-testid="item-view-sub">' +
                '<div class="n"' + (isSkipped(c) ? ' style="background:#9CA3AF"' : (c.closed ? ' style="background:#15803D"' : '')) + '>' +
                    (isSkipped(c) ? '&#8211;' : (c.closed ? '&#10003;' : (n + 1))) + '</div>' +
                '<div class="t"><div class="st">' + esc(c.title) + '</div>' +
                '<div class="v sl-md">' + esc(c.statusName) + (c.assigneeName ? ' | ' + esc(c.assigneeName) : '') + '</div></div></div>';
        }).join('') : '';
        SL.open('itemViewOverlay');
        $('itemViewScroll').scrollTop = 0;
    }

    function closeItemView() { SL.close('itemViewOverlay'); mViewItemId = 0; }

    function editFromItemView() {
        var id = mViewItemId;
        closeItemView();
        if (id) openItem(id, 0, null);
    }

    /** A row's Copy button: the item's title and nothing else, silently. */
    async function copyTitle(id) {
        var i = byId(id);
        if (!i) return;
        // No message when it works -- Tony knows it copied (2026-10-09); only a failure says so.
        if (!(await SL.copyText(i.title))) SL.toast('Could not copy -- the browser refused.');
    }

    /** The item as text: its title, its summary line, then the detail as it was written. */
    async function copyItem(id) {
        if (!id) return;
        var i = await fetchItem(id, 'That item could not be read.');
        if (!i) return;
        var text = i.title + '\n' + itemMetaBits(i).join(' | ') + (i.detail ? '\n\n' + i.detail : '');
        SL.toast(await SL.copyText(text) ? 'Copied: ' + SL.plain(i.title, 60) : 'Could not copy -- the browser refused.');
    }

    // ---- add / edit ---------------------------------------------------------------

    async function openItem(id, projectId, parentId) {
        mEditId = id; mEditProjectId = projectId; mEditParentId = parentId;
        SL.warn('itemWarn', '');
        var i = { title: '', detail: '', statusCode: Page.lookups.statuses.length ? Page.lookups.statuses[0].code : '',
                  assigneeCode: '', priority: 3, dueDate: null, projectId: projectId };
        if (id) {
            var r = await SL.get('/api/solutions/items/' + id);          // the tree carries only a preview of the detail
            if (!r || !r.ok) { SL.toast(SL.failText(r, 'That item could not be opened.')); return; }
            i = r.data;
        }
        var parent = parentId ? byId(parentId) : null;
        $('itemTitleBar').textContent = id ? 'Edit item' : (parent ? 'New sub-item' : 'New item');
        $('itemContext').textContent = parent ? 'Under: ' + parent.title : '';
        $('iTitle').value = i.title || '';
        $('iDetail').value = i.detail || '';
        $('iDue').value = SL.date(i.dueDate);
        SL.fillSelect($('iStatus'), Page.statusOptions(), i.statusCode);
        SL.fillSelect($('iAssignee'), Page.memberOptions('Nobody yet'), i.assigneeCode || '');
        SL.setSelect($('iPriority'), i.priority || 3);
        // A top-level item can change project here; a sub-item follows its parent.
        $('iProjectFld').hidden = !!(parent || (id && i.parentItemId));
        SL.fillSelect($('iProject'), Page.projectOptions(), String(i.projectId || projectId));
        SL.open('itemOverlay');
        $('iTitle').focus();
    }

    async function saveItem() {
        var title = $('iTitle').value.trim();
        if (!title) { SL.warn('itemWarn', 'An item needs a title.'); return; }
        var due = SL.toIsoDate($('iDue').value);
        if (due === null) { SL.warn('itemWarn', 'The due date should look like 09/17/2026.'); return; }
        var body = { title: title, detail: $('iDetail').value, statusCode: $('iStatus').value,
                     assigneeCode: $('iAssignee').value, priority: +$('iPriority').value };
        if (due) body.dueDate = due;
        var r;
        if (mEditId) {
            r = await SL.put('/api/solutions/items/' + mEditId, body);
            var chosen = +$('iProject').value;
            if (r && r.ok && !$('iProjectFld').hidden && chosen && chosen !== r.data.projectId)
                r = await SL.post('/api/solutions/items/' + mEditId + '/move', { projectId: chosen });
        } else {
            var projectId = mEditParentId ? mEditProjectId : (+$('iProject').value || mEditProjectId);
            if (mEditParentId) body.parentItemId = mEditParentId;
            r = await SL.post('/api/solutions/projects/' + projectId + '/items', body);
        }
        if (!r || !r.ok) { SL.warn('itemWarn', SL.failText(r, 'The item could not be saved.')); return; }
        SL.close('itemOverlay');
        if (mEditParentId) Page.fold().set('item:' + mEditParentId, false);      // a new sub-item must be visible
        mFlashId = r.data.id;
        await refresh();
    }

    // ---- status, move, delete --------------------------------------------------------

    function openStatus(id) {
        var i = byId(id);
        if (!i) return;
        mStatusItemId = id;
        $('statusTitle').textContent = i.title;
        $('statusChoices').innerHTML = Page.lookups.statuses.map(function (s) {
            return '<li><button type="button" class="sl-pill" style="background:' + esc(s.colorHex || '#6B7280') + ';font-size:13px;padding:6px 14px" data-pick="' +
                esc(s.code) + '" data-testid="status-choice">' + esc(s.name) + '</button>' +
                (s.code === i.statusCode ? '<span class="sl-muted">now</span>' : '') + '</li>';
        }).join('');
        each('[data-pick]', function (b) { b.addEventListener('click', function () { setStatus(b.getAttribute('data-pick')); }); });
        SL.open('statusOverlay');
    }

    async function setStatus(code) {
        SL.close('statusOverlay');
        var r = await SL.post('/api/solutions/items/' + mStatusItemId + '/status', { statusCode: code });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The status could not be changed.')); return; }
        mFlashId = mStatusItemId;
        await refresh();
    }

    // The tick: not done -> Done -> Skipped -> In progress. Sub-items are left as they
    // are -- closing a parent does not close what is under it.
    async function tickItem(id) {
        var i = byId(id);
        if (!i) return;
        var t = tickState(i);
        var code = t === '' ? 'done' : (t === 'on' ? 'cancelled' : 'inprogress');
        var r = await SL.post('/api/solutions/items/' + id + '/status', { statusCode: code });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The item could not be ' + (t === '' ? 'marked done.' : (t === 'on' ? 'skipped.' : 'reopened.')))); return; }
        // No message: the row turning green (or back) and its flash say it; a dialog on every tick
        // was one tap too many (Tony, 2026-10-08).
        mFlashId = id;
        await refresh();
    }

    // Check all / Uncheck all: every item in a project, or every sub-item (any depth) under one
    // item -- the item itself is left alone. One dialog both asks which and names what it will
    // touch. It does what the tick does, one item at a time through the same status call, so the
    // server's wake-the-parents rule and the agent door are unchanged. Check all closes the open
    // ones (Done); Uncheck all reopens the closed ones (In progress). Items already in the asked
    // state are not sent, so pressing it twice changes nothing the second time.
    async function tickMany(scope, id) {
        var targets, what;
        if (scope === 'project') {
            var p = Page.projects.filter(function (x) { return x.id === id; })[0];
            targets = mItems.filter(function (i) { return i.projectId === id; });
            what = 'The project "' + (p ? p.name : '') + '" holds ';
        } else {
            var parent = byId(id);
            if (!parent) return;
            targets = descendants(id);
            what = 'Under "' + parent.title + '" (the item itself is left as it is): ';
        }
        var open = targets.filter(function (i) { return !i.closed; });
        var closed = targets.filter(function (i) { return i.closed; });
        if (!targets.length) return;
        var buttons = [];
        if (open.length)   buttons.push({ label: 'Check all', value: 'check', style: 'primary', isDefault: true });
        if (closed.length) buttons.push({ label: 'Uncheck all', value: 'uncheck', style: open.length ? 'plain' : 'primary', isDefault: !open.length });
        buttons.push({ label: 'Cancel', value: 'cancel', style: 'plain' });
        var message = what + targets.length + (scope === 'item' ? (targets.length === 1 ? ' sub-item' : ' sub-items') : (targets.length === 1 ? ' item' : ' items')) +
            ' at every level: ' + open.length + ' open and ' + closed.length + ' closed.' +
            (open.length ? '\n\nCheck all closes the ' + open.length + ' open ' + (open.length === 1 ? 'one' : 'ones') + ' (Done).' : '') +
            (closed.length ? '\n\nUncheck all reopens the ' + closed.length + ' closed ' + (closed.length === 1 ? 'one' : 'ones') + ' (In progress).' : '');
        var answer;
        try {
            var dlg = await window.Picasso.dialog();
            answer = await dlg.show({ kind: 'question', title: 'Check or uncheck all', message: message, buttons: buttons, escape: 'cancel' });
        } catch (e) {
            SL.toast('The question could not be shown, so nothing was changed: ' + (e && e.message ? e.message : 'the dialog did not load.'));
            return;
        }
        if (answer !== 'check' && answer !== 'uncheck') return;
        var send = answer === 'check' ? open : closed;
        var code = answer === 'check' ? 'done' : 'inprogress';
        var verb = answer === 'check' ? 'Closing' : 'Reopening';
        for (var n = 0; n < send.length; n++) {
            SL.status(verb + ' ' + (n + 1) + ' of ' + send.length);
            var r = await SL.post('/api/solutions/items/' + send[n].id + '/status', { statusCode: code });
            if (!r || !r.ok) {
                await refresh();
                SL.toast(SL.failText(r, '"' + send[n].title + '" could not be ' + (answer === 'check' ? 'closed.' : 'reopened.')) +
                    '\n\n' + n + ' of ' + send.length + ' were changed before it stopped.');
                return;
            }
        }
        // Like the single tick, success says nothing: the rows turning green (or back) say it.
        if (scope === 'item') Page.fold().set('item:' + id, false);                // show what changed
        else Page.fold().set('band:' + id, false);
        await refresh();
    }

    /** Every item under this one, at any depth, in tree order. */
    function descendants(id) {
        var out = [];
        mItems.forEach(function (c) { if (c.parentItemId === id) { out.push(c); out = out.concat(descendants(c.id)); } });
        return out;
    }

    // Drag a TOP-LEVEL item up or down among its siblings in the same project. Sub-items keep
    // the arrows. The rows are a flat list (a parent's sub-items follow it), so the DOM is not
    // shuffled while dragging; a line shows where it will land, and the drop calls the same
    // move the arrows use. Listeners go on the document, never pointer capture.
    function wireItemGrip(grip) {
        grip.addEventListener('click', function (e) { e.stopPropagation(); });
        grip.addEventListener('pointerdown', function (down) {
            var id = +grip.getAttribute('data-item-grip');
            var me = byId(id);
            var row = grip.closest('[data-item-row]');
            var band = row ? row.closest('[data-band]') : null;
            if (!me || !band) return;
            down.preventDefault(); down.stopPropagation();
            row.classList.add('dragging');
            var target = null, after = false;
            function tops() { return Array.prototype.filter.call(band.querySelectorAll('[data-item-row][data-depth="0"]'), function (r) { return r !== row; }); }
            function clearMark() { Array.prototype.forEach.call(band.querySelectorAll('.sl-drop-before, .sl-drop-after'), function (r) { r.classList.remove('sl-drop-before', 'sl-drop-after'); }); }
            function onMove(ev) {
                var scroller = $('slBody'), box = scroller.getBoundingClientRect();
                if (ev.clientY < box.top + 48) scroller.scrollTop -= 14;
                if (ev.clientY > box.bottom - 48) scroller.scrollTop += 14;
                var under = document.elementFromPoint(ev.clientX, ev.clientY);
                var over = under && under.closest ? under.closest('[data-item-row]') : null;
                if (over && over.closest('[data-band]') !== band) over = null;
                if (over && over.getAttribute('data-depth') !== '0') {
                    // over a sub-item: the drop point is its top-level ancestor
                    var anc = byId(+over.getAttribute('data-item-row'));
                    while (anc && anc.parentItemId) anc = byId(anc.parentItemId);
                    over = anc ? band.querySelector('[data-item-row="' + anc.id + '"]') : null;
                }
                clearMark();
                if (!over || over === row) { target = null; return; }
                var r = over.getBoundingClientRect();
                after = ev.clientY > r.top + r.height / 2;
                target = over;
                over.classList.add(after ? 'sl-drop-after' : 'sl-drop-before');
            }
            function onUp() {
                document.removeEventListener('pointermove', onMove);
                document.removeEventListener('pointerup', onUp);
                document.removeEventListener('pointercancel', onUp);
                row.classList.remove('dragging');
                clearMark();
                if (!target) return;
                // Final 1-based position among the top-level siblings, counted without the dragged row.
                var others = tops();
                var pos = others.indexOf(target) + (after ? 2 : 1);
                if (pos !== +row.getAttribute('data-index') + 1) moveItem(id, pos);
            }
            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', onUp);
            document.addEventListener('pointercancel', onUp);
        });
    }

    async function moveItem(id, toSortOrder) {
        var i = byId(id);
        if (!i) return;
        var r = await SL.post('/api/solutions/items/' + id + '/move', { parentItemId: i.parentItemId || 0, sortOrder: toSortOrder });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The item could not be moved.')); return; }
        mFlashId = id;
        await load();
    }

    async function removeItem(id) {
        var i = byId(id);
        if (!i) return;
        var below = countDescendants(id);
        var question = 'Delete the item "' + i.title + '" (' + i.statusName + ')?' +
            (below ? '\n\nThis also deletes the ' + below + (below === 1 ? ' sub-item' : ' sub-items') + ' under it.' : '') +
            '\n\nLog entries about it are kept.\n\nThis cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/items/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The item could not be deleted.')); return; }
        await refresh();
    }

    function countDescendants(id) {
        var n = 0;
        mItems.forEach(function (c) { if (c.parentItemId === id) n += 1 + countDescendants(c.id); });
        return n;
    }

    /** Items changed: redraw the tree and let the header's counts catch up. */
    async function refresh() {
        await Promise.all([load(), Page.reload()]);
    }

    function flashNow() {
        var id = mFlashId;
        mFlashId = 0;
        // Open the path to it -- its project band and every folded parent -- then draw. The row is in the DOM
        // even inside a folded band (display:none), so this cannot be skipped when the row is found (2026-10-16).
        reveal(id); draw();
        var row = $('item-' + id);
        if (!row) return;
        row.classList.add('flash');
        row.scrollIntoView({ block: 'center' });
    }

    // ---- filters ----------------------------------------------------------------------

    function fillFilters() {
        SL.fillSelect($('fItemProject'), Page.projectOptions('All projects'), mFilter.project);
        SL.fillSelect($('fItemStatus'), [{ value: '', label: 'Any status' }].concat(Page.statusOptions()), mFilter.status);
        SL.fillSelect($('fItemAssignee'), Page.memberOptions('Anyone'), mFilter.assignee);
        $('fItemHideClosed').checked = mFilter.hideClosed;
    }

    function readFilters() {
        mFilter = { project: $('fItemProject').value, status: $('fItemStatus').value,
                    assignee: $('fItemAssignee').value, hideClosed: $('fItemHideClosed').checked };
        saveFilter();
        draw();
    }

    // ---- the tab ------------------------------------------------------------------------

    Page.registerTab('items', {
        init: function () {
            loadFilter();
            ['fItemProject', 'fItemStatus', 'fItemAssignee', 'fItemHideClosed'].forEach(function (id) {
                $(id).addEventListener('change', readFilters);
            });
            $('btnAddItem').addEventListener('click', function () {
                var first = mFilter.project ? +mFilter.project : (Page.projects.length ? Page.projects[0].id : 0);
                if (!first) { SL.toast('Add a project on the Overview tab first.'); return; }
                Page.fold().set('band:' + first, false);
                openItem(0, first, null);
            });
            $('btnItemsExpand').addEventListener('click', function () { setAll(false); });
            $('btnItemsCollapse').addEventListener('click', function () { setAll(true); });
            $('itemCancel').addEventListener('click', function () { SL.close('itemOverlay'); });
            $('itemSave').addEventListener('click', function () { SL.guard($('itemSave'), saveItem); });
            $('statusCancel').addEventListener('click', function () { SL.close('statusOverlay'); });
            $('itemViewClose').addEventListener('click', closeItemView);
            $('itemViewEdit').addEventListener('click', editFromItemView);
            $('itemViewCopy').addEventListener('click', function () {
                SL.guard($('itemViewCopy'), function () { return copyItem(mViewItemId); });
            });
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' && $('itemViewOverlay').classList.contains('open')) closeItemView();
            });
        },
        show: function () { fillFilters(); load(); },
        /** From the Overview: open Items narrowed to one project, with that band open. */
        openProject: function (projectId) {
            mFilter.project = String(projectId);
            Page.fold().set('band:' + projectId, false);
            Page.go('items');
        },
        /** From a deep link: scroll to one item and flash it. */
        flash: function (itemId) { mFlashId = itemId; }
    });
})();
