(function () {
    'use strict';

    // The Search tab of one solution (2026-10-16): every other tab searched at once, the way
    // Find Transaction works -- words that may appear (OR), words that must all appear (AND),
    // words that must not (NOT), a date range, which tabs to look in, titles only, open only.
    //
    // The server (house-only /api/solution-search) does the finding and answers one band per
    // tab that had anything, newest first, the first page of each with how many there are in
    // all. The bands arrive FOLDED, showing only the count; open one to see the rows, tap a row
    // to go to it on its own tab. Folds are remembered in the database (PicassoGroupState);
    // what was typed is kept for the browser session, so coming back to the tab shows the
    // same search again. Nothing here changes any data.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PAGE_SIZE = 25;
    var TABS = [
        { key: 'overview', label: 'Projects' },     { key: 'items', label: 'Items' },
        { key: 'log', label: 'Log' },               { key: 'notes', label: 'Notes' },
        { key: 'terms', label: 'Terms' },           { key: 'tests', label: 'Testing' },
        { key: 'qa', label: 'QA' },                 { key: 'config', label: 'Configuration' },
        { key: 'grid', label: 'Configuration II' }, { key: 'files', label: 'Files' },
        { key: 'conversations', label: 'Conversations' }, { key: 'web', label: 'Web' }
    ];
    var KIND_LABEL = { project: 'Project', item: 'Item', entry: 'Log entry', note: 'Note', step: 'Runbook step', term: 'Term',
                       checklist: 'Checklist', test: 'Test', property: 'Property', file: 'File', conversation: 'Conversation',
                       message: 'Message', web: 'Web address' };

    var mResult = null;        // the last answer: {sources:[{key,label,total,hits}], total}
    var mQuery = null;         // the filter that produced it
    var mBands = null;         // PicassoGroupState: which result bands are open (database)
    var mForm = null;          // PicassoGroupState: whether the search fields are folded (database)
    var mDrawn = false;        // the form is in the pane
    var mBusy = false;

    // ---- the form ----------------------------------------------------------------------------

    function sessionKey() { return 'sl.search.' + Page.id; }

    function textBoxes(prefix, n, hint) {
        var out = '';
        for (var i = 1; i <= n; i++)
            out += '<input type="text" id="' + prefix + i + '" maxlength="200" placeholder="' + hint + '" autocomplete="off" data-testid="search-' + prefix + '-' + i + '">';
        return out;
    }

    function formHtml() {
        var folded = !mForm.isOpen('fields');
        return '<div class="sl-card sl-search-form' + (folded ? ' folded' : '') + '" id="searchForm" data-testid="search-form">' +
            '<div class="sl-cardhead sl-foldable" id="searchFormHead" role="button" tabindex="0" aria-expanded="' + (folded ? 'false' : 'true') + '" data-testid="search-form-toggle">' +
                '<span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span><h3>Search fields</h3>' +
                '<span class="sl-muted sl-search-summary" id="searchSummary" data-testid="search-form-summary"></span></div>' +
            '<div class="sl-search-body" id="searchFormBody">' +
                '<div class="sl-search-group"><div class="sl-search-group-title">Any of these words (OR)</div>' +
                    '<div class="sl-search-boxes">' + textBoxes('sOr', 4, 'contains') + '</div></div>' +
                '<div class="sl-search-group"><div class="sl-search-group-title">All of these words (AND)</div>' +
                    '<div class="sl-search-boxes">' + textBoxes('sAnd', 4, 'contains') + '</div></div>' +
                '<div class="sl-search-group"><div class="sl-search-group-title">None of these words (NOT)</div>' +
                    '<div class="sl-search-boxes sl-search-boxes-2">' + textBoxes('sNot', 2, 'does not contain') + '</div></div>' +
                '<div class="sl-search-row">' +
                    '<div class="fld"><label for="sFrom">From date</label><input type="date" id="sFrom" data-testid="search-from"></div>' +
                    '<div class="fld"><label for="sThrough">Through date</label><input type="date" id="sThrough" data-testid="search-through"></div>' +
                    '<label class="check"><input type="checkbox" id="sTitlesOnly" data-testid="search-titles-only"> Titles only</label>' +
                    '<label class="check"><input type="checkbox" id="sOpenOnly" data-testid="search-open-only"> Open only</label>' +
                '</div>' +
                '<div class="sl-search-group"><div class="sl-search-group-title">Which tabs' +
                    ' <button type="button" class="sl-btn small" id="sTabsAll" data-testid="search-tabs-all">All</button>' +
                    ' <button type="button" class="sl-btn small" id="sTabsNone" data-testid="search-tabs-none">None</button></div>' +
                    '<div class="sl-search-tabs">' + TABS.map(function (t) {
                        return '<label class="check"><input type="checkbox" data-source="' + t.key + '" checked data-testid="search-tab-' + t.key + '"> ' + esc(t.label) + '</label>';
                    }).join('') + '</div></div>' +
                '<div class="sl-search-actions">' +
                    '<button type="button" class="sl-btn" id="sClear" data-testid="search-clear">Clear</button>' +
                    '<button type="button" class="sl-btn primary" id="sGo" data-testid="search-go">Search</button>' +
                '</div>' +
            '</div></div>' +
            '<div id="searchResults" data-testid="search-results"></div>';
    }

    function drawForm() {
        $('pane-search').innerHTML = formHtml();
        mDrawn = true;
        function flip() { mForm.toggle('fields'); paintFormFold(); }
        $('searchFormHead').addEventListener('click', flip);
        $('searchFormHead').addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flip(); } });
        $('sGo').addEventListener('click', function () { SL.guard($('sGo'), runSearch); });
        $('sClear').addEventListener('click', clearForm);
        $('sTabsAll').addEventListener('click', function () { setTabs(true); });
        $('sTabsNone').addEventListener('click', function () { setTabs(false); });
        Array.prototype.forEach.call($('searchFormBody').querySelectorAll('input[type="text"]'), function (box) {
            box.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); SL.guard($('sGo'), runSearch); } });
        });
        restoreForm();
    }

    function paintFormFold() {
        var folded = !mForm.isOpen('fields');
        $('searchForm').classList.toggle('folded', folded);
        $('searchFormHead').setAttribute('aria-expanded', folded ? 'false' : 'true');
        $('searchFormHead').querySelector('.chev').classList.toggle('folded', folded);
        $('searchSummary').textContent = folded && mQuery ? describe(mQuery) : '';
    }

    function setTabs(on) {
        Array.prototype.forEach.call($('searchFormBody').querySelectorAll('[data-source]'), function (box) { box.checked = on; });
    }

    function values(prefix, n) {
        var out = [];
        for (var i = 1; i <= n; i++) { var v = $(prefix + i).value.trim(); if (v) out.push(v); }
        return out;
    }

    /** What the form says now. */
    function readForm() {
        var sources = Array.prototype.filter.call($('searchFormBody').querySelectorAll('[data-source]'), function (b) { return b.checked; })
            .map(function (b) { return b.getAttribute('data-source'); });
        return { or: values('sOr', 4), and: values('sAnd', 4), not: values('sNot', 2),
                 from: $('sFrom').value, through: $('sThrough').value,
                 titlesOnly: $('sTitlesOnly').checked, openOnly: $('sOpenOnly').checked, sources: sources };
    }

    function writeForm(q) {
        function fill(prefix, n, list) { for (var i = 1; i <= n; i++) $(prefix + i).value = (list && list[i - 1]) || ''; }
        fill('sOr', 4, q.or); fill('sAnd', 4, q.and); fill('sNot', 2, q.not);
        $('sFrom').value = q.from || '';
        $('sThrough').value = q.through || '';
        $('sTitlesOnly').checked = !!q.titlesOnly;
        $('sOpenOnly').checked = !!q.openOnly;
        var wanted = q.sources && q.sources.length ? q.sources : TABS.map(function (t) { return t.key; });
        Array.prototype.forEach.call($('searchFormBody').querySelectorAll('[data-source]'), function (box) {
            box.checked = wanted.indexOf(box.getAttribute('data-source')) >= 0;
        });
    }

    function clearForm() {
        writeForm({});
        mResult = null; mQuery = null;
        try { sessionStorage.removeItem(sessionKey()); } catch (e) { /* private mode */ }
        $('searchResults').innerHTML = '';
        $('searchSummary').textContent = '';
        if (!mForm.isOpen('fields')) { mForm.open('fields'); paintFormFold(); }
        paintTools();
        SL.status('Search every tab of this solution');
        $('sOr1').focus();
    }

    /** The last search of this browser session comes back with the tab; a ?q= link starts one. */
    function restoreForm() {
        var q = null;
        try { q = JSON.parse(sessionStorage.getItem(sessionKey()) || 'null'); } catch (e) { q = null; }
        if (!q && SL.param('q')) q = { or: [SL.param('q')] };
        if (q) { writeForm(q); runSearch(); }
        else { if (!mForm.isOpen('fields')) { mForm.open('fields'); } paintFormFold(); paintTools(); }
    }

    /** One line that says what was searched for, for the folded head and the status line. */
    function describe(q) {
        var parts = [];
        if (q.or.length) parts.push('any of: ' + q.or.join(', '));
        if (q.and.length) parts.push('all of: ' + q.and.join(', '));
        if (q.not.length) parts.push('not: ' + q.not.join(', '));
        if (q.from || q.through) parts.push((q.from ? SL.date(q.from + 'T00:00:00') : 'start') + ' to ' + (q.through ? SL.date(q.through + 'T00:00:00') : 'now'));
        if (q.titlesOnly) parts.push('titles only');
        if (q.openOnly) parts.push('open only');
        if (q.sources.length && q.sources.length < TABS.length) parts.push(q.sources.length + ' of ' + TABS.length + ' tabs');
        return parts.join(' | ');
    }

    // ---- the search --------------------------------------------------------------------------

    function query(q, source, offset) {
        var p = [];
        q.or.forEach(function (t) { p.push('or=' + encodeURIComponent(t)); });
        q.and.forEach(function (t) { p.push('and=' + encodeURIComponent(t)); });
        q.not.forEach(function (t) { p.push('not=' + encodeURIComponent(t)); });
        if (q.sources.length && q.sources.length < TABS.length) p.push('sources=' + encodeURIComponent(q.sources.join(',')));
        if (q.from) p.push('from=' + encodeURIComponent(q.from));
        if (q.through) p.push('through=' + encodeURIComponent(q.through));
        if (q.titlesOnly) p.push('titlesOnly=true');
        if (q.openOnly) p.push('openOnly=true');
        if (source) { p.push('source=' + encodeURIComponent(source)); p.push('offset=' + offset); }
        p.push('pageSize=' + PAGE_SIZE);
        return '/api/solution-search/' + Page.id + '?' + p.join('&');
    }

    async function runSearch() {
        var q = readForm();
        if (!q.or.length && !q.and.length && !q.not.length && !q.from && !q.through) {
            SL.toast('Type at least one word, or pick a date, then tap Search.');
            return;
        }
        if (!q.sources.length) { SL.toast('Tick at least one tab to search.'); return; }
        mBusy = true;
        SL.status('Searching' + String.fromCharCode(0x2026));
        var r = await SL.get(query(q, null, 0));
        mBusy = false;
        if (!r || !r.ok || !r.data) {
            $('searchResults').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'The search could not be run.')) + '</div>';
            return;
        }
        mQuery = q;
        mResult = r.data;
        try { sessionStorage.setItem(sessionKey(), JSON.stringify(q)); } catch (e) { /* private mode */ }
        // The results own the screen now: the fields fold to one line that says what was searched.
        if (mForm.isOpen('fields')) mForm.toggle('fields');
        paintFormFold();
        drawResults();
    }

    async function loadMore(key) {
        var band = bandOf(key);
        if (!band || mBusy) return;
        mBusy = true;
        var r = await SL.get(query(mQuery, key, band.hits.length));
        mBusy = false;
        if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'The next page could not be loaded.')); return; }
        var more = (r.data.sources || []).filter(function (s) { return s.key === key; })[0];
        if (more) { band.hits = band.hits.concat(more.hits || []); band.total = more.total; }
        drawResults();
    }

    function bandOf(key) { return mResult ? mResult.sources.filter(function (s) { return s.key === key; })[0] : null; }

    // ---- the results: one folded band per tab ------------------------------------------------

    function drawResults() {
        var host = $('searchResults');
        if (!mResult) { host.innerHTML = ''; paintTools(); return; }
        if (!mResult.sources.length) {
            host.innerHTML = '<div class="sl-empty" data-testid="search-none">Nothing matches. Try fewer words, or more tabs.</div>';
            SL.status('Nothing found | ' + describe(mQuery));
            paintTools();
            return;
        }
        host.innerHTML = mResult.sources.map(bandHtml).join('');
        Array.prototype.forEach.call(host.querySelectorAll('[data-search-band]'), function (h) {
            h.addEventListener('click', function () { mBands.toggle(h.getAttribute('data-search-band')); drawResults(); });
            h.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); mBands.toggle(h.getAttribute('data-search-band')); drawResults(); } });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-hit]'), function (row) {
            row.addEventListener('click', function () { drill(row.getAttribute('data-hit-source'), +row.getAttribute('data-hit')); });
            row.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); drill(row.getAttribute('data-hit-source'), +row.getAttribute('data-hit')); } });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-more]'), function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); loadMore(b.getAttribute('data-more')); });
        });
        SL.status(mResult.total + ' found in ' + mResult.sources.length + (mResult.sources.length === 1 ? ' tab' : ' tabs') + ' | ' + describe(mQuery));
        paintTools();
    }

    function bandHtml(s) {
        var open = mBands.isOpen(s.key);
        return '<div class="sl-band' + (open ? '' : ' collapsed') + '" data-testid="search-band" data-band-key="' + esc(s.key) + '">' +
            '<div class="sl-band-head" data-search-band="' + esc(s.key) + '" role="button" tabindex="0" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="search-band-toggle">' +
                '<span class="chev">&#9660;</span><span class="name">' + esc(s.label) + '</span>' +
                '<span class="sl-chip" data-testid="search-band-count">' + s.total + (s.total === 1 ? ' found' : ' found') + '</span></div>' +
            '<div class="sl-band-body">' + s.hits.map(function (h) { return hitHtml(s, h); }).join('') +
                (s.hits.length < s.total
                    ? '<div class="sl-more"><button type="button" class="sl-btn" data-more="' + esc(s.key) + '" data-testid="search-more-btn">Load more (' + s.hits.length + ' of ' + s.total + ')</button></div>'
                    : '') +
            '</div></div>';
    }

    function hitHtml(s, h) {
        var kind = KIND_LABEL[h.kind] || h.kind;
        var title = h.title || '';
        // A log entry's title is "#12 Kind"; a message's is who sent it. Everything else is the thing's own name.
        return '<div class="sl-item sl-hit' + (h.closed ? ' closed' : '') + '" data-hit="' + h.id + '" data-hit-source="' + esc(s.key) + '" role="button" tabindex="0" data-testid="search-hit">' +
            '<div class="main">' +
                '<div class="title">' + highlight(title) + '</div>' +
                (h.parentTitle ? '<div class="sl-muted sl-hit-where">' + highlight(h.parentTitle) + '</div>' : '') +
                // The snippet is a cut of text somebody wrote in Markdown: shown as plain words, marks taken out
                // (Tony, 2026-09-18: never show Markdown), then the search words lit up.
                (h.snippet ? '<div class="preview sl-hit-snippet">' + highlight(SL.plain(h.snippet, 400, true)) + '</div>' : '') +
                '<div class="meta"><span class="sl-chip">' + esc(kind) + '</span>' +
                    (h.statusName ? SL.pill(h.statusName, h.colorHex) : '') +
                    (h.extra ? '<span class="sl-chip' + (h.extra === 'failing' ? ' warn' : '') + '">' + esc(h.extra) + '</span>' : '') +
                    (h.date ? '<span class="sl-muted">' + SL.dateTime(h.date) + '</span>' : '') +
                '</div>' +
            '</div></div>';
    }

    /** Escaped text with every positive term wrapped in <mark>, whatever its case. */
    function highlight(text) {
        var terms = mQuery ? mQuery.or.concat(mQuery.and) : [];
        var s = String(text == null ? '' : text);
        if (!terms.length) return esc(s);
        var re = new RegExp('(' + terms.map(function (t) { return t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }).join('|') + ')', 'ig');
        return s.split(re).map(function (piece, i) { return i % 2 ? '<mark>' + esc(piece) + '</mark>' : esc(piece); }).join('');
    }

    // ---- Fold all (the double caret) and the tool row ----------------------------------------

    function anyOpen() {
        return !!mResult && mResult.sources.some(function (s) { return mBands.isOpen(s.key); });
    }

    function foldAll() {
        if (anyOpen()) mBands.collapseAll(); else mBands.expandAll();
        drawResults();
    }

    function paintTools() {
        var b = $('btnSearchFoldAll');
        if (!b) return;
        var has = !!mResult && mResult.sources.length > 1;
        b.hidden = !has;
        b.innerHTML = anyOpen() ? '&#9656;&#9656;' : '&#9662;&#9662;';
        b.title = anyOpen() ? 'Fold every tab' : 'Open every tab';
        $('searchToolsSummary').textContent = mResult && mQuery
            ? mResult.total + ' found' + (mResult.sources.length ? ' in ' + mResult.sources.length + (mResult.sources.length === 1 ? ' tab' : ' tabs') : '')
            : 'Search every tab of this solution';
    }

    // ---- drilling: a row goes to its own tab -------------------------------------------------

    function drill(source, id) {
        var band = bandOf(source);
        var h = band ? band.hits.filter(function (x) { return x.id === id; })[0] : null;
        if (!h) return;
        var t = Page.tabs;
        switch (source) {
            case 'overview':      t.items.openProject(h.id); break;
            case 'items':         Page.go('items'); t.items.flash(h.id); break;
            case 'log':           t.log.flash(h.id); Page.go('log'); break;
            case 'notes':         Page.go('notes'); t.notes.openNote(h.parentId || h.id); break;
            case 'terms':         Page.go('terms'); t.terms.openTerm(h.id); break;
            case 'tests':
            case 'qa':            t[source].choosePlan(h.parentId || h.id); if (h.kind === 'test') t[source].flashCase(h.id); Page.go(source); break;
            case 'config':        t.config.flash(h.id); Page.go('config'); break;
            case 'grid':          t.grid.flash(h.id); Page.go('grid'); break;
            case 'files':         Page.go('files'); t.files.openFile(h.id); break;
            case 'conversations': Page.go('conversations'); t.conversations.openThread(h.parentId || h.id); break;
            case 'web':           t.web.flash(h.id); Page.go('web'); break;
        }
    }

    // ---- the tab ---------------------------------------------------------------------------------

    Page.registerTab('search', {
        init: function () {
            mBands = window.PicassoGroupState.create({ key: 'leo.sl.search.bands.' + Page.id, defaultOpen: false });
            mForm  = window.PicassoGroupState.create({ key: 'leo.sl.search.form.' + Page.id, defaultOpen: true });
            window.Picasso.rollup.attach({ key: 'leo.sl.search.tools.collapsed', pane: $('toolbar-search'), toggle: $('searchToolsToggle') });
            $('btnSearchGo').addEventListener('click', function () { if (mDrawn) SL.guard($('btnSearchGo'), runSearch); });
            $('btnSearchClear').addEventListener('click', function () { if (mDrawn) clearForm(); });
            $('btnSearchFoldAll').addEventListener('click', foldAll);
        },
        show: async function () {
            if (!mDrawn) {
                // Both devices' folds first, so the first draw already shows what was chosen elsewhere.
                try { await Promise.all([mBands.sync(), mForm.sync()]); } catch (e) { /* the device copy stands */ }
                drawForm();
            } else if (mResult) {
                drawResults();
            } else paintTools();
        }
    });
})();
