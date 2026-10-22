(function () {
    'use strict';

    // The Notes tab of one solution (Story 3493) -- "how do you do it": runbooks with ordered
    // steps, strategies, references. The list shows headers; opening one fetches its body and
    // steps. Saving never loses wording -- the server keeps every changed body as a version.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var mNotes = [];
    var mNote = null;            // the open note, or null while the list is showing
    var mEditNoteId = 0, mEditStepId = 0;
    var mFullBodies = {};       // note id -> full body, fetched the first time More is tapped

    // Rows are the default list view, as on the Overview; only an explicit choice of Cards ('0') turns them off.
    var mRows = true;
    try { mRows = localStorage.getItem('sl.notes.rows') !== '0'; } catch (e) { /* private mode */ }

    // The list only carries a 300-character preview; a preview that long was cut short.
    var PREVIEW_CHARS = 300;
    // An open note's body taller than this is clipped behind a More link.
    var BODY_CLAMP_PX = 320;

    /** Fold keys, all in the solution's fold store: 'note:' folds a list card (default open);
        'noteless:' clips a note's text (default clipped) -- shared by the list and the open note. */
    function cardFolded(id) { return Page.fold().isCollapsed('note:' + id, false); }
    function textClipped(id) { return Page.fold().isCollapsed('noteless:' + id, true); }

    // ---- the list ----------------------------------------------------------------------

    async function loadList() {
        mNote = null;
        paintToolbar();
        var r = await SL.get('/api/solutions/' + Page.id + '/notes');
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $('pane-notes').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the notes.')) + '</div>';
            return;
        }
        mNotes = r.data;
        mFullBodies = {};
        await drawList();
    }

    async function drawList() {
        paintToolbar();
        if (!mNotes.length) {
            $('pane-notes').innerHTML = '<div class="sl-empty">No notes yet. A runbook says how to do it again; a strategy says how you mean to go about it.</div>';
            SL.status('0 notes');
            return;
        }
        await fetchFullBodies(mNotes.filter(function (d) { return isCut(d) && !cardFolded(d.id) && !textClipped(d.id); }));
        var html = '';
        Page.lookups.noteTypes.forEach(function (t) {
            var group = mNotes.filter(function (d) { return d.typeCode === t.code; });
            if (!group.length) return;
            var shut = kindFolded(t.id);
            html += '<div class="sl-kind" data-kind-group="' + t.id + '" data-testid="notes-kind">' +
                '<div class="sl-section-title sl-kind-head" data-kind-fold="' + t.id + '" data-testid="notes-kind-head">' +
                    '<span class="sl-grip small" data-kind-grip="' + t.id + '" title="Drag to move this kind up or down" aria-label="Drag to move ' +
                        esc(t.name) + '" data-testid="notes-kind-grip">&#8942;&#8942;</span>' +
                    '<span class="chev' + (shut ? ' folded' : '') + '">&#9660;</span>' +
                    '<span>' + esc(t.name) + (group.length > 1 ? 's' : '') + '</span>' +
                    '<span class="sl-kind-count" data-testid="notes-kind-count">' + group.length + '</span>' +
                '</div>' +
                (shut ? '' : '<div class="sl-cards' + (mRows ? ' rows' : '') + '" data-testid="notes-cards">' + group.map(card).join('') + '</div>') +
            '</div>';
        });
        $('pane-notes').innerHTML = html;
        each('[data-kind-fold]', function (h) {
            h.addEventListener('click', function () { setKindFolded(+h.getAttribute('data-kind-fold'), !kindFolded(+h.getAttribute('data-kind-fold'))); drawList(); });
        });
        each('[data-kind-grip]', function (g) {
            Page.wireProjectGrip(g, '[data-kind-group]', 'data-kind-group', drawList, saveKindOrder);
        });
        // A full body may have been written by an outside agent: never innerHTML it. See SL.safeMarkdown.
        each('[data-note-full]', function (el) {
            SL.renderMarkdown(el, mFullBodies[el.getAttribute('data-note-full')] || '');
            el.addEventListener('click', function (e) { e.stopPropagation(); });   // a link in the text must not open the card
        });
        each('[data-note-fold]', function (t) {
            t.addEventListener('click', function (e) {
                e.stopPropagation();
                Page.fold().toggle('note:' + t.getAttribute('data-note-fold'), false);
                drawList();
            });
        });
        each('[data-note-more]', function (b) {
            b.addEventListener('click', function (e) {
                e.stopPropagation();
                Page.fold().toggle('noteless:' + b.getAttribute('data-note-more'), true);
                drawList();
            });
        });
        each('[data-note-grip]', function (g) {
            Page.wireProjectGrip(g, '[data-note-card]', 'data-note-card', drawList, saveNoteOrder);
        });
        each('[data-note-read]', function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); openView(+b.getAttribute('data-note-read')); });
        });
        each('[data-note-edit]', function (b) {
            b.addEventListener('click', async function (e) { e.stopPropagation(); await openNote(+b.getAttribute('data-note-edit')); if (mNote) openEditor(mNote); });
        });
        each('[data-note-delete]', function (b) {
            b.addEventListener('click', async function (e) { e.stopPropagation(); await openNote(+b.getAttribute('data-note-delete')); if (mNote) deleteNote(); });
        });
        each('[data-note-open]', function (c) {
            c.addEventListener('click', function (e) { e.stopPropagation(); openNote(+c.getAttribute('data-note-open')); });
        });
        SL.status(mNotes.length + (mNotes.length === 1 ? ' note' : ' notes'));
    }

    /** A drop reorders one kind's notes: each that moved gets its new place (1, 2, 3...) by one
        PUT of {sortOrder} alone -- a merge, so nothing else changes and no new version is made. */
    async function saveNoteOrder(ids) {
        var calls = [];
        ids.forEach(function (id, index) {
            var d = mNotes.filter(function (x) { return x.id === id; })[0];
            if (d && d.sortOrder !== index + 1) calls.push(SL.put('/api/solutions/notes/' + id, { sortOrder: index + 1 }));
        });
        var results = await Promise.all(calls);
        var failed = results.filter(function (x) { return !x || !x.ok; })[0];
        if (results.some(function (x) { return !x || !x.ok; })) SL.toast(window.LWAuth.errorText(failed, 'The new order could not be saved.'));
        await loadList();
    }

    /** True when the list's preview is not the whole body. */
    function isCut(d) { return !!d.bodyPreview && d.bodyPreview.length >= PREVIEW_CHARS; }

    /** Fetch, in parallel, the full body of every note in the list that does not have it yet. */
    async function fetchFullBodies(notes) {
        var missing = notes.filter(function (d) { return !(d.id in mFullBodies); });
        await Promise.all(missing.map(async function (d) {
            var r = await SL.get('/api/solutions/notes/' + d.id);
            mFullBodies[d.id] = (r && r.ok && r.data) ? (r.data.body || '') : d.bodyPreview;
        }));
    }

    /** A note card. Its title folds it; folded, it is one line. A cut-short preview carries More / Less. */
    function card(d) {
        var folded = cardFolded(d.id);
        var head = '<div class="sl-cardhead">' +
            '<span class="sl-grip" data-note-grip="' + d.id + '" title="Drag to move this note up or down" aria-label="Drag to move ' +
                esc(d.title) + '" data-testid="notes-card-grip">&#8942;&#8942;</span>' +
            '<div class="sl-cardtitle" data-note-fold="' + d.id + '" data-testid="notes-card-title">' +
                '<span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span> <h3>' + esc(d.title) + '</h3></div>' +
            '<span class="sl-chip">v' + d.currentVersionNumber + '</span>' +
            (d.stepCount ? '<span class="sl-chip">' + d.stepCount + (d.stepCount === 1 ? ' step' : ' steps') + '</span>' : '') + '</div>';
        if (folded) return '<div class="sl-card link folded" data-note-card="' + d.id + '" data-note-open="' + d.id + '" data-testid="notes-card">' + head + '</div>';

        var cut = isCut(d), full = cut && !textClipped(d.id);
        var text = '';
        if (full) text = '<div class="sl-note-body sl-note-inline" data-note-full="' + d.id + '" data-testid="notes-card-fulltext"></div>';
        else if (d.bodyPreview) text = '<div class="summary sl-md" data-testid="notes-card-preview">' + SL.md(d.bodyPreview, cut) + '</div>';
        var more = cut ? '<div><button type="button" class="sl-link" data-note-more="' + d.id + '" data-testid="notes-card-more">' + (full ? 'Less' : 'More') + '</button></div>' : '';

        return '<div class="sl-card link" data-note-card="' + d.id + '" data-note-open="' + d.id + '" data-testid="notes-card">' + head +
            (d.projectName ? '<div class="row" style="margin-top:6px"><span class="sl-chip">' + esc(d.projectName) + '</span></div>' : '') +
            text + more +
            '<div class="sl-muted">' + (d.authorName ? esc(d.authorName) + ', ' : '') + SL.dateTime(d.versionDate) + '</div>' +
            '<div class="actions"><button type="button" class="sl-btn small primary" data-note-open="' + d.id + '" data-testid="notes-card-open">Open</button>' +
            '<button type="button" class="sl-btn small" data-note-read="' + d.id + '" data-testid="notes-card-view">View</button>' +
            '<button type="button" class="sl-btn small" data-note-edit="' + d.id + '" data-testid="notes-card-edit">Edit</button>' +
            '<button type="button" class="sl-btn small danger" data-note-delete="' + d.id + '" data-testid="notes-card-delete">Delete</button></div></div>';
    }

    /** Expand all opens every card and shows every note in full; Collapse all folds them back to one line. */
    async function foldAllList(collapsed) {
        // Tony's rule (2026-09-24): remembered, and reaches notes added later.
        Page.fold().setAllForPrefix('note:', collapsed);
        Page.fold().setAllForPrefix('noteless:', collapsed);
        await drawList();
    }

    /** The kinds shown in the list (a solution shows only the kinds it uses). */
    function shownKinds() {
        return Page.lookups.noteTypes.filter(function (t) { return mNotes.some(function (d) { return d.typeCode === t.code; }); });
    }

    /** The kinds toggle: folds or opens every kind and leaves the notes inside them as they are.
        Expand all / Collapse all are the other level -- the notes -- and leave the kinds alone. */
    function foldAllKinds() {
        var anyOpen = shownKinds().some(function (t) { return !kindFolded(t.id); });
        shownKinds().forEach(function (t) { setKindFolded(t.id, anyOpen); });
        drawList();
    }

    function anyNoteOpen() { return mNotes.some(function (d) { return !cardFolded(d.id); }); }

    function paintNotesFold() {
        var b = $('btnNotesFold');
        b.hidden = !!mNote || !mNotes.length;
        b.textContent = anyNoteOpen() ? 'Fold notes' : 'Open notes';
        b.title = anyNoteOpen() ? 'Fold every note to one line; the kinds stay as they are'
                                : 'Open every note with its full text; the kinds stay as they are';
    }

    function paintKindFold() {
        var b = $('btnKindFold'), kinds = shownKinds();
        b.hidden = !!mNote || kinds.length < 2;
        var anyOpen = kinds.some(function (t) { return !kindFolded(t.id); });
        b.textContent = anyOpen ? 'Fold kinds' : 'Open kinds';
        b.title = anyOpen ? 'Fold every kind; the notes inside keep their own folds' : 'Open every kind; the notes inside keep their own folds';
    }

    // ---- the kinds: folded per solution, remembered in the DATABASE so every device agrees ----

    function kindKey(kindId) { return 'leo.sl.notes.kind.' + Page.id + '.' + kindId; }

    function kindFolded(kindId) {
        return !!(window.PicassoViewState && window.PicassoViewState.get(kindKey(kindId)) === '1');
    }

    function setKindFolded(kindId, folded) {
        if (window.PicassoViewState) window.PicassoViewState.set(kindKey(kindId), folded ? '1' : '0');
    }

    /** Pull the folds another device saved; redraw only if something changed. */
    async function syncKindFolds() {
        if (!window.PicassoViewState) return;
        var changed = await window.PicassoViewState.sync('leo.sl.notes.kind.' + Page.id + '.');
        if (changed && !mNote) drawList();
    }

    /** A drop reorders the kinds. They are shared by every solution, so the server keeps the
        places of kinds this solution does not use and answers the whole list in its new order. */
    async function saveKindOrder(ids) {
        var r = await SL.put('/api/solutions/note-types/order', { ids: ids });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The new order of the kinds could not be saved.')); }
        else if (Array.isArray(r.data)) Page.lookups.noteTypes = r.data;
        drawList();
    }

    function flipView() {
        mRows = !mRows;
        try { localStorage.setItem('sl.notes.rows', mRows ? '1' : '0'); } catch (e) { /* private mode */ }
        drawList();
    }

    // ---- one note ---------------------------------------------------------------------

    async function openNote(id) {
        var r = await SL.get('/api/solutions/notes/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That note could not be opened.')); loadList(); return; }
        mNote = r.data;
        drawNote();
    }

    function drawNote() {
        paintToolbar();
        var d = mNote;
        var steps = (d.steps || []).map(step).join('');
        $('pane-notes').innerHTML =
            '<div class="sl-hero" data-testid="notes-open-header"><h2 style="margin:0 0 8px;font-size:18px">' + esc(d.title) + '</h2>' +
                '<div style="display:flex;flex-wrap:wrap;gap:6px;align-items:center">' +
                    '<span class="sl-chip">' + esc(d.typeName) + '</span>' +
                    (d.projectName ? '<span class="sl-chip">' + esc(d.projectName) + '</span>' : '') +
                    '<span class="sl-chip">Version ' + d.currentVersionNumber + '</span>' +
                    '<span class="sl-muted">' + (d.authorName ? esc(d.authorName) + ', ' : '') + SL.dateTime(d.versionDate) +
                        (d.changeNote ? ' - ' + esc(SL.plain(d.changeNote, 200)) : '') + '</span></div></div>' +
            '<div class="sl-section-title sl-foldable" data-body-fold="1" data-testid="notes-body-toggle"><span class="chev' + (bodyFolded() ? ' folded' : '') + '">&#9660;</span> The note</div>' +
            '<div class="sl-note-body" id="noteBody"' + (bodyFolded() ? ' hidden' : '') + ' data-testid="notes-body"><span class="sl-muted">Nothing written yet.</span></div>' +
            '<div id="noteMoreBar" hidden><button type="button" class="sl-link" id="noteMoreBtn" data-testid="notes-body-more">More</button></div>' +
            (steps || d.typeCode === 'runbook'
                ? '<div class="sl-section-title">Steps</div>' + (steps || '<div class="sl-empty" style="padding:16px">No steps yet. Add one, or turn a log entry into one from the Log tab.</div>')
                : '');
        // The body may have been written by an outside agent: never innerHTML it. See SL.safeMarkdown.
        if (d.body) SL.renderMarkdown($('noteBody'), d.body);
        clipBody();
        bindSteps();
        SL.status(d.title + ' | version ' + d.currentVersionNumber + ' | ' + (d.steps || []).length + ' steps');
    }

    /** A step's title folds it. Steps start open -- a runbook is for reading -- and remember being folded. */
    function step(s, index, all) {
        var isFolded = Page.fold().isCollapsed('step:' + s.id, false, stepScope());
        var title = '<div class="title" data-step-fold="' + s.id + '" data-testid="notes-step-title"><span class="chev' + (isFolded ? ' folded' : '') +
            '">&#9660;</span> ' + esc(s.title) + '</div>';
        if (isFolded) return '<div class="sl-step folded" data-testid="notes-step"><div class="num">' + s.stepNumber + '</div><div class="main">' + title + '</div></div>';
        return '<div class="sl-step" data-testid="notes-step">' +
            '<div class="num">' + s.stepNumber + '</div>' +
            '<div class="main">' + title +
                (s.command ? '<div class="lbl">Command</div><div class="val sl-mono">' + esc(s.command) + '</div>' : '') +
                (s.expectedResult ? '<div class="lbl">What you should see</div><div class="val sl-md">' + SL.md(s.expectedResult) + '</div>' : '') +
                (s.notes ? '<div class="lbl">Notes</div><div class="val sl-md">' + SL.md(s.notes) + '</div>' : '') +
                (s.sourceSequenceNumber ? '<div class="sl-muted" style="margin-top:6px">From log entry #' + s.sourceSequenceNumber + '</div>' : '') +
                '<div class="tools">' +
                    '<button type="button" class="sl-iconbtn sl-arrange-only" title="Move up" data-step-move="' + s.id + '" data-to="' + (s.stepNumber - 1) + '"' + (index === 0 ? ' disabled' : '') + ' data-testid="notes-step-up">&#9650;</button>' +
                    '<button type="button" class="sl-iconbtn sl-arrange-only" title="Move down" data-step-move="' + s.id + '" data-to="' + (s.stepNumber + 1) + '"' + (index === all.length - 1 ? ' disabled' : '') + ' data-testid="notes-step-down">&#9660;</button>' +
                    '<button type="button" class="sl-btn small" data-step-edit="' + s.id + '" data-testid="notes-step-edit">Edit</button>' +
                    '<button type="button" class="sl-btn small danger" data-step-delete="' + s.id + '" data-testid="notes-step-delete">Delete</button>' +
                '</div></div></div>';
    }

    function each(selector, fn) { Array.prototype.forEach.call(document.querySelectorAll(selector), fn); }

    function bodyFolded() { return Page.fold().isCollapsed('notebody:' + mNote.id, false); }

    /** A body taller than BODY_CLAMP_PX is clipped behind More until the reader asks for all of it.
        Measured after rendering, so a short note never shows a pointless link. */
    function clipBody() {
        var body = $('noteBody'), bar = $('noteMoreBar');
        if (!mNote.body || body.hidden || body.scrollHeight <= BODY_CLAMP_PX + 40) return;
        var clipped = textClipped(mNote.id);
        body.classList.toggle('clamped', clipped);
        $('noteMoreBtn').textContent = clipped ? 'More' : 'Less';
        bar.hidden = false;
    }

    // One note's steps share a remembered default (its scope), so this note's Collapse all also folds
    // the steps added to it later -- Tony's rule (2026-09-24) -- without touching other notes' steps.
    function stepScope() { return 'stepof:' + mNote.id + ':'; }

    function foldAll(collapsed) {
        Page.fold().setMany(['notebody:' + mNote.id, 'noteless:' + mNote.id], collapsed);
        Page.fold().setAllForPrefix(stepScope(), collapsed, (mNote.steps || []).map(function (s) { return 'step:' + s.id; }));
        drawNote();
    }

    function bindSteps() {
        each('[data-step-fold]', function (t) {
            t.addEventListener('click', function () { Page.fold().toggle('step:' + t.getAttribute('data-step-fold'), false, stepScope()); drawNote(); });
        });
        each('[data-body-fold]', function (t) {
            t.addEventListener('click', function () { Page.fold().toggle('notebody:' + mNote.id, false); drawNote(); });
        });
        each('#noteMoreBtn', function (b) {
            b.addEventListener('click', function () { Page.fold().toggle('noteless:' + mNote.id, true); drawNote(); });
        });
        each('[data-step-move]',   function (b) { b.addEventListener('click', function () { moveStep(+b.getAttribute('data-step-move'), +b.getAttribute('data-to')); }); });
        each('[data-step-edit]',   function (b) { b.addEventListener('click', function () { openStep(+b.getAttribute('data-step-edit')); }); });
        each('[data-step-delete]', function (b) { b.addEventListener('click', function () { deleteStep(+b.getAttribute('data-step-delete')); }); });
    }

    function paintToolbar() {
        var open = !!mNote;
        $('btnNoteBack').hidden = !open;
        $('btnAddNote').hidden = false;          // always: a new note can be started from inside another
        $('btnNoteRead').hidden = !open;
        $('btnNoteEdit').hidden = !open;
        $('btnNoteVersions').hidden = !open;
        $('btnNoteDelete').hidden = !open;
        $('btnNoteView').hidden = open;           // Rows / Cards is a list choice
        $('btnNoteView').textContent = mRows ? 'Cards' : 'Rows';
        // An open note folds its body and steps with Expand all / Collapse all. The list has two
        // levels, so it has two toggles that say which: Fold kinds / Open kinds and Fold notes /
        // Open notes -- one button each, so the row fits a phone.
        $('btnNoteExpand').hidden = !open;
        $('btnNoteCollapse').hidden = !open;
        paintKindFold();
        paintNotesFold();
        $('btnNoteAddStep').hidden = !(open && mNote.typeCode === 'runbook');
    }

    // ---- view: the note as a page to read ---------------------------------------------------
    //
    // Read-only, on sepia, like The Thinker's View: the title, who wrote which version, the body
    // rendered as Markdown and a runbook's steps. The body may have been written by an outside
    // agent, so it goes through SL.renderMarkdown and every other field through esc().

    var mViewNote = null;

    async function openView(id) {
        var d = (mNote && mNote.id === id) ? mNote : null;
        if (!d) {
            var r = await SL.get('/api/solutions/notes/' + id);
            if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'That note could not be opened.')); return; }
            d = r.data;
        }
        mViewNote = d;
        $('noteViewTitle').textContent = d.title;
        $('noteViewMeta').innerHTML = esc(d.typeName) + (d.projectName ? ' | ' + esc(d.projectName) : '') +
            ' | Version ' + d.currentVersionNumber + ' | ' + (d.authorName ? esc(d.authorName) + ', ' : '') + SL.dateTime(d.versionDate);
        if (d.body) SL.renderMarkdown($('noteViewBody'), d.body);
        else $('noteViewBody').innerHTML = '<p class="sl-sepia-empty">Nothing written yet.</p>';
        var steps = d.steps || [];
        $('noteViewSteps').innerHTML = steps.length ? '<h3 class="sl-sepia-h">Steps</h3>' + steps.map(function (s) {
            return '<div class="sl-sepia-step" data-testid="note-view-step"><div class="n">' + s.stepNumber + '</div><div class="t">' +
                '<div class="st">' + esc(s.title) + '</div>' +
                (s.command ? '<div class="lbl">Command</div><pre>' + esc(s.command) + '</pre>' : '') +
                (s.expectedResult ? '<div class="lbl">What you should see</div><div class="v sl-md">' + SL.md(s.expectedResult) + '</div>' : '') +
                (s.notes ? '<div class="lbl">Notes</div><div class="v sl-md">' + SL.md(s.notes) + '</div>' : '') +
                '</div></div>';
        }).join('') : '';
        SL.open('noteViewOverlay');
        $('noteViewScroll').scrollTop = 0;
    }

    function closeView() { SL.close('noteViewOverlay'); mViewNote = null; }

    async function editFromView() {
        var id = mViewNote ? mViewNote.id : 0;
        closeView();
        if (!id) return;
        if (!mNote || mNote.id !== id) await openNote(id);
        if (mNote) openEditor(mNote);
    }

    // ---- add / edit a note ---------------------------------------------------------------

    function openEditor(note) {
        mEditNoteId = note ? note.id : 0;
        SL.warn('noteWarn', '');
        $('noteTitleBar').textContent = note ? 'Edit note' : 'New note';
        $('dTitle').value = note ? note.title : '';
        $('dBody').value = note ? (note.body || '') : '';
        $('dNote').value = '';
        $('dType').value = note ? Page.kindName(Page.lookups.noteTypes, note.typeCode)
                                : (Page.lookups.noteTypes.length ? Page.lookups.noteTypes[0].name : '');
        SL.fillSelect($('dProject'), Page.projectOptions('The whole solution'), note && note.projectId ? String(note.projectId) : '');
        SL.open('noteOverlay');
        $('dTitle').focus();
    }

    async function saveNote() {
        var title = $('dTitle').value.trim();
        if (!title) { SL.warn('noteWarn', 'A note needs a title.'); return; }
        // typeName: a kind that is not on the list yet is made when the note is saved.
        var body = { title: title, typeName: $('dType').value.trim(), body: $('dBody').value, changeNote: $('dNote').value,
                     projectId: $('dProject').value ? +$('dProject').value : 0 };
        var r = mEditNoteId ? await SL.put('/api/solutions/notes/' + mEditNoteId, body)
                           : await SL.post('/api/solutions/' + Page.id + '/notes', body);
        if (!r || !r.ok) { SL.warn('noteWarn', SL.failText(r, 'The note could not be saved.')); return; }
        SL.close('noteOverlay');
        mNote = r.data;
        await Page.loadLookups();
        drawNote();
        Page.reload();
    }

    async function deleteNote() {
        if (!mNote) return;
        var n = (mNote.steps || []).length;
        var question = 'Delete the ' + mNote.typeName.toLowerCase() + ' "' + mNote.title + '"?\n\nThis also deletes its ' + n +
            (n === 1 ? ' step' : ' steps') + ' and all ' + mNote.currentVersionNumber + ' saved versions.\n\nThis cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/notes/' + mNote.id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The note could not be deleted.')); return; }
        Page.reload();
        loadList();
    }

    // ---- versions ------------------------------------------------------------------------------

    async function openVersions() {
        var r = await SL.get('/api/solutions/notes/' + mNote.id + '/versions');
        var list = (r && r.ok && Array.isArray(r.data)) ? r.data : [];
        $('versionBody').hidden = true;
        $('versionsList').innerHTML = list.map(function (v) {
            return '<li><span class="sl-chip">v' + v.versionNumber + '</span><div class="grow">' +
                esc(v.authorName || 'unknown') + ', ' + SL.dateTime(v.createdDate) + (v.current ? ' <span class="sl-chip agent">current</span>' : '') +
                (v.changeNote ? '<div class="sl-muted">' + esc(SL.plain(v.changeNote, 200)) + '</div>' : '') + '</div>' +
                '<button type="button" class="sl-btn small" data-version="' + v.versionNumber + '" data-testid="versions-read-btn">Read</button></li>';
        }).join('');
        each('[data-version]', function (b) { b.addEventListener('click', function () { readVersion(+b.getAttribute('data-version')); }); });
        SL.open('versionsOverlay');
    }

    async function readVersion(n) {
        var r = await SL.get('/api/solutions/notes/' + mNote.id + '/versions/' + n);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That version could not be opened.')); return; }
        SL.renderMarkdown($('versionBody'), '**Version ' + n + '**\n\n' + (r.data.body || '*Empty.*'));
        $('versionBody').hidden = false;
    }

    // ---- steps -----------------------------------------------------------------------------------

    function openStep(id) {
        mEditStepId = id || 0;
        var s = id ? (mNote.steps || []).filter(function (x) { return x.id === id; })[0] : null;
        SL.warn('stepWarn', '');
        $('stepTitleBar').textContent = s ? 'Edit step ' + s.stepNumber : 'New step';
        $('stTitle').value = s ? s.title : '';
        $('stCommand').value = s ? (s.command || '') : '';
        $('stExpected').value = s ? (s.expectedResult || '') : '';
        $('stNotes').value = s ? (s.notes || '') : '';
        SL.open('stepOverlay');
        $('stTitle').focus();
    }

    async function saveStep() {
        var title = $('stTitle').value.trim();
        if (!title) { SL.warn('stepWarn', 'A step needs a title.'); return; }
        var body = { title: title, command: $('stCommand').value, expectedResult: $('stExpected').value, notes: $('stNotes').value };
        var base = '/api/solutions/notes/' + mNote.id + '/steps';
        var r = mEditStepId ? await SL.put(base + '/' + mEditStepId, body) : await SL.post(base, body);
        if (!r || !r.ok) { SL.warn('stepWarn', SL.failText(r, 'The step could not be saved.')); return; }
        SL.close('stepOverlay');
        mNote = r.data;
        drawNote();
    }

    async function moveStep(id, to) {
        var r = await SL.post('/api/solutions/notes/' + mNote.id + '/steps/' + id + '/move', { stepNumber: to });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The step could not be moved.')); return; }
        mNote = r.data;
        drawNote();
    }

    async function deleteStep(id) {
        var s = (mNote.steps || []).filter(function (x) { return x.id === id; })[0];
        if (!s) return;
        if (!window.confirm('Delete step ' + s.stepNumber + ', "' + s.title + '", from "' + mNote.title + '"?\n\nThe steps after it are renumbered. This cannot be undone.')) return;
        var r = await SL.del('/api/solutions/notes/' + mNote.id + '/steps/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The step could not be deleted.')); return; }
        mNote = r.data;
        drawNote();
    }

    // ---- the tab -----------------------------------------------------------------------------------

    Page.registerTab('notes', {
        init: function () {
            $('btnAddNote').addEventListener('click', function () { openEditor(null); });
            $('btnNoteBack').addEventListener('click', loadList);
            $('btnNoteEdit').addEventListener('click', function () { openEditor(mNote); });
            $('btnNoteRead').addEventListener('click', function () { openView(mNote.id); });
            $('noteViewEdit').addEventListener('click', editFromView);
            $('noteViewClose').addEventListener('click', closeView);
            // Closes on a tap outside the page -- only when the press also BEGAN outside it, so
            // selecting text and letting go past the edge does not throw the reader out.
            document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && $('noteViewOverlay').classList.contains('open')) closeView(); });
            $('btnNoteVersions').addEventListener('click', openVersions);
            $('btnNoteDelete').addEventListener('click', deleteNote);
            $('btnNoteAddStep').addEventListener('click', function () { openStep(0); });
            $('btnNoteExpand').addEventListener('click', function () { if (mNote) foldAll(false); else foldAllList(false); });
            $('btnNoteCollapse').addEventListener('click', function () { if (mNote) foldAll(true); else foldAllList(true); });
            $('btnNoteView').addEventListener('click', flipView);
            $('btnKindFold').addEventListener('click', foldAllKinds);
            $('btnNotesFold').addEventListener('click', function () { foldAllList(anyNoteOpen()); });
            $('noteCancel').addEventListener('click', function () { SL.close('noteOverlay'); });
            $('noteSave').addEventListener('click', function () { SL.guard($('noteSave'), saveNote); });
            if (window.PicassoPicker) {
                window.PicassoPicker.attach({ input: $('dType'), dropdown: $('dTypeDD'), allowNew: true, newLabel: 'Add',
                    getItems: function () { return Page.kindNames(Page.lookups.noteTypes); } });
            }
            $('versionsClose').addEventListener('click', function () { SL.close('versionsOverlay'); });
            $('stepCancel').addEventListener('click', function () { SL.close('stepOverlay'); });
            $('stepSave').addEventListener('click', function () { SL.guard($('stepSave'), saveStep); });
        },
        show: function () { if (mNote) drawNote(); else loadList(); syncKindFolds(); },
        openNote: openNote
    });
})();
