(function () {
    'use strict';

    // The Terms tab of one solution (2026-10-09) -- its glossary. Shaped like the Leonardo Dictionary:
    // a term, its definition, a type, categories, a source, notes and a favorite star; grouped by type
    // or category, searched, starred, picked at random and copied. Its own tables (SolutionTerm*), and a
    // type or category belongs to this solution alone.
    //
    // Searching, filtering, grouping, ordering and counting all happen on the server, one page at a time:
    // a filter change goes back to the server rather than filtering the rows already here. Group by and
    // Reverse are remembered for the user (database view state), the folds per solution, and the filters
    // for this browser session only.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PAGE_SIZE = 50;
    var VIEW_KEYS = 'leo.sl.terms.view.';
    var TYPING_PAUSE_MS = 1000;
    var CHECK = String.fromCharCode(0x2713);

    var mTerms = [], mCounts = {}, mOffset = 0, mMore = false, mLoading = false, mAsk = 0;
    var mLookups = { types: [], categories: [] };
    var mEditId = 0, mViewTerm = null, mSurprise = false, mTags = null, mSearchTimer = null;
    var mGroups = null, mSynced = false;

    // ---- remembered state ------------------------------------------------------------------

    /** Folds, per solution, in the database; a group nobody has touched starts open. */
    function groups() {
        if (!mGroups) mGroups = window.PicassoGroupState.create({ key: 'leo.sl.terms.groups.' + Page.id, defaultOpen: true });
        return mGroups;
    }

    function viewGet(name) { return window.PicassoViewState ? window.PicassoViewState.get(VIEW_KEYS + name) : null; }
    function viewSet(name, value) { if (window.PicassoViewState) window.PicassoViewState.set(VIEW_KEYS + name, value); }

    function filterKey() { return 'sl.terms.filter.' + Page.id; }

    function saveFilters() {
        try {
            sessionStorage.setItem(filterKey(), JSON.stringify({ q: $('fTermSearch').value, type: $('fTermType').value,
                category: $('fTermCategory').value, fav: $('fTermFav').checked }));
        } catch (e) { /* private mode */ }
    }

    function restoreFilters() {
        var f = null;
        try { f = JSON.parse(sessionStorage.getItem(filterKey()) || 'null'); } catch (e) { f = null; }
        $('fTermSearch').value = (f && f.q) || '';
        $('fTermFav').checked = !!(f && f.fav);
        return f || {};
    }

    function groupBy() { return $('fTermGroup').value || 'type'; }
    function reversed() { return $('btnTermsReverse').getAttribute('aria-pressed') === 'true'; }

    function setReversed(on) {
        $('btnTermsReverse').setAttribute('aria-pressed', on ? 'true' : 'false');
        $('btnTermsReverse').classList.toggle('on', !!on);
    }

    // ---- load --------------------------------------------------------------------------------

    function filterQuery() {
        var q = '';
        if ($('fTermSearch').value.trim()) q += '&search=' + encodeURIComponent($('fTermSearch').value.trim());
        if ($('fTermType').value) q += '&typeId=' + encodeURIComponent($('fTermType').value);
        if ($('fTermCategory').value) q += '&categoryId=' + encodeURIComponent($('fTermCategory').value);
        if ($('fTermFav').checked) q += '&favorites=true';
        return q;
    }

    function pageUrl(offset, pageSize, group) {
        return '/api/solutions/' + Page.id + '/terms?offset=' + offset + '&pageSize=' + pageSize +
            '&groupBy=' + encodeURIComponent(group || groupBy()) + (reversed() ? '&reverse=true' : '') + filterQuery();
    }

    async function loadFirst() {
        mTerms = []; mOffset = 0; mMore = false;
        saveFilters();
        await loadPage();
    }

    async function loadPage() {
        var ask = ++mAsk;              // an answer to an older question is dropped
        mLoading = true;
        var r = await SL.get(pageUrl(mOffset, PAGE_SIZE));
        if (ask !== mAsk) return;
        mLoading = false;
        if (!r || !r.ok || !r.data) {
            $('termList').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the terms.')) + '</div>';
            return;
        }
        var page = Array.isArray(r.data.terms) ? r.data.terms : [];
        mTerms = mTerms.concat(page);
        mCounts = r.data.counts || {};
        mOffset += page.length;
        mMore = page.length === PAGE_SIZE;
        draw();
    }

    async function loadLookups() {
        var r = await SL.get('/api/solutions/' + Page.id + '/terms/lookups');
        if (r && r.ok && r.data) mLookups = { types: r.data.types || [], categories: r.data.categories || [] };
        fillFilters();
    }

    function fillFilters(restored) {
        var keepType = restored ? restored.type : $('fTermType').value, keepCat = restored ? restored.category : $('fTermCategory').value;
        SL.fillSelect($('fTermType'), [{ value: '', label: 'Every type' }].concat(mLookups.types.map(function (t) {
            return { value: String(t.id), label: t.name };
        })), keepType || '');
        SL.fillSelect($('fTermCategory'), [{ value: '', label: 'Every category' }].concat(mLookups.categories.map(function (c) {
            return { value: String(c.id), label: c.name };
        })), keepCat || '');
    }

    // ---- draw --------------------------------------------------------------------------------

    function drawShell() {
        $('pane-terms').innerHTML =
            '<div id="termList" data-testid="terms-list"><div class="sl-empty">Loading&hellip;</div></div>' +
            '<div class="sl-more" id="termMore" hidden><button type="button" class="sl-btn" id="btnTermMore" data-testid="terms-more-btn">Load more</button></div>';
        $('btnTermMore').addEventListener('click', function () { if (!mLoading) loadPage(); });
        $('termList').addEventListener('click', onListClick);
    }

    function filtered() {
        return !!($('fTermSearch').value.trim() || $('fTermType').value || $('fTermCategory').value || $('fTermFav').checked);
    }

    function distinctLoaded() {
        var seen = {};
        mTerms.forEach(function (t) { seen[t.id] = 1; });
        return Object.keys(seen).length;
    }

    function draw() {
        var list = $('termList');
        if (!list) return;
        var total = mCounts.total || 0, all = mCounts.all || 0;
        if (!mTerms.length) {
            list.innerHTML = '<div class="sl-empty">' + (all ? 'No terms match the filter.' : 'No terms yet. Add the words, acronyms and rules this solution uses.') + '</div>';
        } else if (groupBy() === 'none') {
            list.innerHTML = mTerms.map(card).join('');
        } else {
            list.innerHTML = bands();
        }
        $('termMore').hidden = !mMore;
        drawFoldButton();
        var loaded = distinctLoaded();
        SL.status((loaded < total ? loaded + ' of ' : '') + total + (total === 1 ? ' term' : ' terms') +
            (filtered() ? ' match (' + all + ' in all)' : '') + ' | ' + (mCounts.favorites || 0) + ' favorites | ' +
            (mCounts.types || 0) + ' types | ' + (mCounts.categories || 0) + ' categories');
    }

    /** The rows arrive in group order, so a group is a run of rows that share a key. */
    function bands() {
        var html = '', i = 0;
        while (i < mTerms.length) {
            var key = mTerms[i].groupKey, label = mTerms[i].groupLabel, rows = [];
            while (i < mTerms.length && mTerms[i].groupKey === key) rows.push(mTerms[i++]);
            var count = mCounts['group:' + key] || rows.length;
            var open = groups().isOpen(key);
            html += '<div class="sl-band' + (open ? '' : ' collapsed') + '" data-term-group="' + esc(key) + '" data-testid="terms-group">' +
                '<div class="sl-band-head" data-term-fold="' + esc(key) + '" data-testid="terms-group-head">' +
                    '<span class="chev">&#9660;</span><span class="name">' + esc(label) + '</span>' +
                    '<span class="sl-chip" data-testid="terms-group-count">' + (rows.length < count ? rows.length + ' of ' + count : count) + '</span>' +
                '</div>' +
                '<div class="sl-band-body">' + rows.map(card).join('') + '</div></div>';
        }
        return html;
    }

    function card(t) {
        var meta = '';
        if (t.type && groupBy() !== 'type') meta += '<span class="sl-chip" data-testid="term-card-type">' + esc(t.type) + '</span>';
        (t.categories || []).forEach(function (c) { meta += '<span class="sl-term-cat" data-testid="term-card-category">' + esc(c) + '</span>'; });
        if (t.source) meta += '<span class="sl-muted" data-testid="term-card-source">' + esc(SL.plain(t.source, 120)) + '</span>';
        if (t.hasNotes) meta += '<span class="sl-muted">has notes</span>';
        return '<div class="sl-term' + (t.favorite ? ' fav' : '') + '" data-term="' + t.id + '" data-testid="term-card">' +
            '<div class="sl-term-head">' +
                '<span class="sl-term-word" data-testid="term-card-word">' + esc(t.term) + '</span>' +
                '<span class="tools">' +
                    '<button type="button" class="sl-iconbtn star' + (t.favorite ? ' on' : '') + '" data-act="star" title="' + (t.favorite ? 'Unfavorite' : 'Favorite') +
                        '" aria-label="Favorite" data-testid="term-star-btn">' + (t.favorite ? '&#9733;' : '&#9734;') + '</button>' +
                    '<button type="button" class="sl-iconbtn" data-act="view" title="View" aria-label="View" data-testid="term-view-btn">&#128065;</button>' +
                    '<button type="button" class="sl-iconbtn" data-act="copy" title="Copy" aria-label="Copy" data-testid="term-copy-btn">&#10697;</button>' +
                    '<button type="button" class="sl-iconbtn" data-act="edit" title="Edit" aria-label="Edit" data-testid="term-edit-btn">&#9998;</button>' +
                    '<button type="button" class="sl-iconbtn" data-act="delete" title="Delete" aria-label="Delete" data-testid="term-delete-btn">&#10005;</button>' +
                '</span>' +
            '</div>' +
            '<div class="sl-term-def sl-md" data-testid="term-card-definition">' + SL.md(t.definition, t.truncated) + '</div>' +
            (meta ? '<div class="sl-term-meta">' + meta + '</div>' : '') +
        '</div>';
    }

    function termById(id) { return mTerms.filter(function (t) { return t.id === id; })[0] || null; }

    function onListClick(e) {
        var fold = e.target.closest('[data-term-fold]');
        if (fold) {
            var key = fold.getAttribute('data-term-fold');
            var open = groups().toggle(key);
            fold.parentNode.classList.toggle('collapsed', !open);
            drawFoldButton();
            return;
        }
        var btn = e.target.closest('[data-act]');
        if (!btn) return;
        var id = +btn.closest('[data-term]').getAttribute('data-term');
        var act = btn.getAttribute('data-act');
        if (act === 'star') SL.guard(btn, function () { return toggleStar(id); });
        else if (act === 'view') openTerm(id);
        else if (act === 'copy') copyOne(id, btn);
        else if (act === 'edit') openEdit(id);
        else if (act === 'delete') remove(id);
    }

    // ---- Expand all / Collapse all: one button, whichever applies ---------------------------------

    function bandKeys() {
        return Array.prototype.map.call(document.querySelectorAll('#termList [data-term-group]'), function (b) { return b.getAttribute('data-term-group'); });
    }

    function drawFoldButton() {
        var keys = bandKeys(), b = $('btnTermsFold');
        b.hidden = !keys.length;
        b.textContent = keys.some(function (k) { return groups().isOpen(k); }) ? 'Collapse all' : 'Expand all';
    }

    function foldAll() {
        if ($('btnTermsFold').textContent === 'Collapse all') groups().collapseAll(); else groups().expandAll();
        draw();
    }

    // ---- star, copy, delete ----------------------------------------------------------------------

    async function toggleStar(id) {
        var t = termById(id) || (mViewTerm && mViewTerm.id === id ? mViewTerm : null);
        if (!t) return;
        var r = await SL.put('/api/solutions/terms/' + id + '/favorite', { favorite: !t.favorite });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The star could not be changed.')); return; }
        var now = !!r.data.favorite;
        mCounts.favorites = (mCounts.favorites || 0) + (now === !!t.favorite ? 0 : (now ? 1 : -1));
        mTerms.forEach(function (x) { if (x.id === id) x.favorite = now; });
        if (mViewTerm && mViewTerm.id === id) { mViewTerm.favorite = now; drawViewStar(); }
        if ($('fTermFav').checked && !now) { await loadFirst(); return; }
        draw();
    }

    function termText(t) {
        var tail = [];
        if (t.type) tail.push(t.type);
        (t.categories || []).forEach(function (c) { tail.push(c); });
        return t.term + '\n' + (t.definition || '') + (tail.length ? '\n[' + tail.join(', ') + ']' : '') + (t.source ? '\nSource: ' + t.source : '');
    }

    /** The whole term (the list holds a preview), copied as plain text. Success is shown on the button itself. */
    async function copyOne(id, btn) {
        var r = await SL.get('/api/solutions/terms/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That term could not be copied.')); return; }
        try { await SL.copyText(termText(r.data)); } catch (e) { SL.toast('The term could not be put on the clipboard: ' + (e && e.message ? e.message : 'the browser refused.')); return; }
        if (btn) {
            var glyph = btn.innerHTML;
            btn.textContent = CHECK;
            setTimeout(function () { btn.innerHTML = glyph; }, 1200);
        }
    }

    async function remove(id) {
        var t = termById(id);
        if (!t) return;
        var quote = SL.plain(t.definition, 120);
        var detail = (t.type ? 'Type: ' + t.type + '\n' : '') + (t.categories && t.categories.length ? 'Categories: ' + t.categories.join(', ') + '\n' : '') +
            '"' + quote + '"\n\nThis cannot be undone.';
        if (!(await window.Picasso.confirmDelete(detail, { title: 'Delete Term: ' + t.term + '?' }))) return;
        var r = await SL.del('/api/solutions/terms/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The term could not be deleted.')); return; }
        await Promise.all([loadLookups(), loadFirst()]);
    }

    // ---- copy all and print: every term the filter shows, fetched a big page at a time ------------

    async function fetchAll(group) {
        var out = [], offset = 0;
        for (;;) {
            var r = await SL.get(pageUrl(offset, 500, group));
            if (!r || !r.ok || !r.data) throw new Error(SL.failText(r, 'The terms could not be loaded.'));
            var page = r.data.terms || [];
            out = out.concat(page);
            offset += page.length;
            if (page.length < 500) return out;
        }
    }

    async function copyAll() {
        var rows;
        try { rows = await fetchAll('none'); } catch (e) { SL.toast(e.message); return; }
        if (!rows.length) { SL.toast('There are no terms to copy.'); return; }
        // A list carries a 600-character preview; a long definition is fetched whole.
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].truncated) {
                var full = await SL.get('/api/solutions/terms/' + rows[i].id);
                if (full && full.ok) rows[i] = full.data;
            }
        }
        try { await SL.copyText(rows.map(termText).join('\n\n')); }
        catch (e) { SL.toast('The terms could not be put on the clipboard: ' + (e && e.message ? e.message : 'the browser refused.')); return; }
        SL.status('Copied ' + rows.length + (rows.length === 1 ? ' term' : ' terms') + ' to the clipboard');
    }

    var PRINT_CSS =
        '.tm h2{font-size:14px;margin:16px 0 6px;border-bottom:1px solid #D1D5DB;padding-bottom:2px}' +
        '.tm .t{margin:0 0 10px;page-break-inside:avoid}' +
        '.tm .w{font-weight:700;font-size:13px}' +
        '.tm .d{font-size:12px;margin:2px 0}' +
        '.tm .m{font-size:10.5px;color:#6B7280}' +
        '.tm .sum{font-size:12px;margin:2px 0 8px;color:#374151}';

    async function print() {
        var rows;
        try { rows = await fetchAll(groupBy()); } catch (e) { SL.toast(e.message); return; }
        if (!rows.length) { SL.toast('There are no terms to print.'); return; }
        var html = '<div class="tm"><h1 style="font-size:17px;margin:0 0 4px">' + esc(Page.solution.name) + ' - Terms</h1>' +
            '<div class="sum">' + (mCounts.total || rows.length) + ' terms' + (filtered() ? ' (filtered)' : '') + '</div>';
        var last = null;
        rows.forEach(function (t) {
            if (groupBy() !== 'none' && t.groupKey !== last) { html += '<h2>' + esc(t.groupLabel) + '</h2>'; last = t.groupKey; }
            var meta = [];
            if (t.type && groupBy() !== 'type') meta.push(esc(t.type));
            if (t.categories && t.categories.length) meta.push(esc(t.categories.join(', ')));
            if (t.source) meta.push('Source: ' + esc(t.source));
            html += '<div class="t"><div class="w">' + (t.favorite ? '&#9733; ' : '') + esc(t.term) + '</div>' +
                '<div class="d sl-md">' + SL.md(t.definition, t.truncated) + '</div>' +
                (meta.length ? '<div class="m">' + meta.join(' | ') + '</div>' : '') + '</div>';
        });
        $('printArea').innerHTML = html + '</div>';
        window.Picasso.printThisPage({ selector: '#printArea', title: Page.solution.name + ' - Terms', extraCss: PRINT_CSS });
    }

    // ---- view: the read-only sepia page ------------------------------------------------------------

    async function openTerm(id) {
        var r = await SL.get('/api/solutions/terms/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That term could not be opened.')); return; }
        mSurprise = false;
        showView(r.data);
    }

    function showView(t) {
        mViewTerm = t;
        $('termViewTitle').textContent = t.term;
        var meta = [];
        if (t.type) meta.push(esc(t.type));
        if (t.categories && t.categories.length) meta.push(esc(t.categories.join(', ')));
        if (t.source) meta.push('Source: ' + esc(t.source));
        meta.push('Added by ' + esc(t.authorName || t.authorCode || 'unknown') + ' ' + esc(SL.date(t.createdDate)) +
            (t.modifiedDate ? ', changed ' + esc(SL.date(t.modifiedDate)) : ''));
        $('termViewMeta').innerHTML = meta.join(' | ');
        SL.renderMarkdown($('termViewBody'), t.definition || '');
        var notes = $('termViewNotes');
        notes.innerHTML = '';
        if (t.notes) {
            notes.innerHTML = '<h3 class="sl-term-notes-h">Notes</h3><div class="sl-sepia-body" id="termViewNotesBody"></div>';
            SL.renderMarkdown($('termViewNotesBody'), t.notes);
        }
        $('termViewAnother').hidden = !mSurprise;
        drawViewStar();
        SL.open('termViewOverlay');
    }

    function drawViewStar() {
        if (!mViewTerm) return;
        $('termViewStar').innerHTML = mViewTerm.favorite ? '&#9733; Unfavorite' : '&#9734; Favorite';
    }

    /** Surprise me: one term at random from what the filter shows. */
    async function surprise() {
        var total = mCounts.total || 0;
        if (!total) { SL.toast('There are no terms to pick from.'); return; }
        var pick = Math.floor(Math.random() * total);
        var r = await SL.get(pageUrl(pick, 1, 'none'));
        var t = r && r.ok && r.data && r.data.terms && r.data.terms[0];
        if (!t) { SL.toast(SL.failText(r, 'No term could be picked.')); return; }
        var full = await SL.get('/api/solutions/terms/' + t.id);
        if (!full || !full.ok) { SL.toast(SL.failText(full, 'That term could not be opened.')); return; }
        mSurprise = true;
        showView(full.data);
    }

    // ---- add / edit ------------------------------------------------------------------------------

    function lookupNames(list) { return list.map(function (x) { return x.name; }); }

    function prepareEditor(t) {
        SL.warn('termWarn', '');
        $('termTitleBar').textContent = t ? 'Edit term' : 'New term';
        $('tmTerm').value = t ? t.term : '';
        $('tmDefinition').value = t ? (t.definition || '') : '';
        $('tmType').value = t ? (t.type || '') : '';
        $('tmSource').value = t ? (t.source || '') : '';
        $('tmNotes').value = t ? (t.notes || '') : '';
        if (mTags) { mTags.refresh(); mTags.setValues(t ? (t.categories || []).slice() : []); }
        SL.open('termOverlay');
        if (window.Picasso && window.Picasso.autosizeTextarea) { window.Picasso.autosizeTextarea($('tmDefinition')); window.Picasso.autosizeTextarea($('tmNotes')); }
        if (!t) $('tmTerm').focus();
    }

    function openAdd() { mEditId = 0; prepareEditor(null); }

    async function openEdit(id) {
        var r = await SL.get('/api/solutions/terms/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That term could not be opened.')); return; }
        mEditId = id;
        prepareEditor(r.data);
    }

    async function save() {
        if (mTags && mTags.commit) mTags.commit();
        var term = $('tmTerm').value.trim(), definition = $('tmDefinition').value.trim();
        if (!term) { SL.warn('termWarn', 'A term needs the word or phrase it defines.'); $('tmTerm').focus(); return; }
        if (!definition) { SL.warn('termWarn', 'A term needs a definition.'); $('tmDefinition').focus(); return; }
        var body = { term: term, definition: definition, type: $('tmType').value.trim(), source: $('tmSource').value.trim(),
                     notes: $('tmNotes').value, categories: mTags ? mTags.getValues() : [] };
        var r = mEditId > 0 ? await SL.put('/api/solutions/terms/' + mEditId, body)
                            : await SL.post('/api/solutions/' + Page.id + '/terms', body);
        if (!r || !r.ok) { SL.warn('termWarn', SL.failText(r, 'The term could not be saved.')); return; }
        SL.close('termOverlay');
        if (mViewTerm && mViewTerm.id === r.data.id && $('termViewOverlay').classList.contains('open')) showView(r.data);
        await Promise.all([loadLookups(), loadFirst()]);
    }

    // ---- types and categories --------------------------------------------------------------------

    var mRenaming = null;   // { kind, id } while one row is being renamed

    function openLookups() {
        mRenaming = null;
        SL.warn('termLookupsWarn', '');
        drawLookups();
        SL.open('termLookupsOverlay');
    }

    function drawLookups() {
        function section(kind, title, list) {
            var rows = list.length ? list.map(function (x) {
                if (mRenaming && mRenaming.kind === kind && mRenaming.id === x.id) {
                    return '<div class="sl-lookup-row" data-testid="term-lookup-row">' +
                        '<input type="text" id="tlRename" value="' + esc(x.name) + '" maxlength="200" autocomplete="off" data-testid="term-lookup-rename-input">' +
                        '<button type="button" class="sl-btn small" data-lk="cancel" data-testid="term-lookup-rename-cancel">Cancel</button>' +
                        '<button type="button" class="sl-btn small primary" data-lk="save" data-kind="' + kind + '" data-id="' + x.id + '" data-testid="term-lookup-rename-save">Save</button></div>';
                }
                return '<div class="sl-lookup-row" data-testid="term-lookup-row"><span class="name" data-testid="term-lookup-name">' + esc(x.name) + '</span>' +
                    '<span class="sl-muted">' + x.usageCount + (x.usageCount === 1 ? ' term' : ' terms') + '</span>' +
                    '<button type="button" class="sl-iconbtn" data-lk="rename" data-kind="' + kind + '" data-id="' + x.id + '" title="Rename" aria-label="Rename" data-testid="term-lookup-rename-btn">&#9998;</button>' +
                    '<button type="button" class="sl-iconbtn" data-lk="delete" data-kind="' + kind + '" data-id="' + x.id + '" title="Delete" aria-label="Delete" data-testid="term-lookup-delete-btn">&#10005;</button></div>';
            }).join('') : '<div class="sl-muted" style="padding:4px 0 8px">None yet -- they are made as you type them on a term.</div>';
            return '<h3 class="sl-lookup-h">' + title + '</h3>' + rows;
        }
        $('termLookupsBody').innerHTML = section('types', 'Types', mLookups.types) + section('categories', 'Categories', mLookups.categories);
        var input = $('tlRename');
        if (input) { input.focus(); input.select(); }
    }

    function lookupOf(kind, id) {
        return (kind === 'types' ? mLookups.types : mLookups.categories).filter(function (x) { return x.id === id; })[0] || null;
    }

    async function onLookupClick(e) {
        var b = e.target.closest('[data-lk]');
        if (!b) return;
        var act = b.getAttribute('data-lk'), kind = b.getAttribute('data-kind'), id = +b.getAttribute('data-id');
        if (act === 'cancel') { mRenaming = null; drawLookups(); return; }
        if (act === 'rename') { mRenaming = { kind: kind, id: id }; SL.warn('termLookupsWarn', ''); drawLookups(); return; }
        if (act === 'save') { await SL.guard(b, function () { return renameLookup(kind, id); }); return; }
        if (act === 'delete') await deleteLookup(kind, id);
    }

    async function renameLookup(kind, id) {
        var name = $('tlRename').value.trim();
        if (!name) { SL.warn('termLookupsWarn', 'A name is required.'); return; }
        var r = await SL.put('/api/solutions/' + Page.id + '/terms/' + kind + '/' + id, { name: name });
        if (!r || !r.ok) { SL.warn('termLookupsWarn', SL.failText(r, 'That could not be renamed.')); return; }
        afterLookupChange(r.data);
    }

    async function deleteLookup(kind, id) {
        var x = lookupOf(kind, id);
        if (!x) return;
        var isType = kind === 'types', n = x.usageCount;
        var what = isType
            ? (n ? n + (n === 1 ? ' term is' : ' terms are') + ' left with no type.' : 'No term uses it.')
            : (n ? 'It is taken off ' + n + (n === 1 ? ' term.' : ' terms.') : 'No term carries it.');
        var ok = await window.Picasso.confirmDelete(what + ' No term is removed.\n\nThis cannot be undone.',
            { title: 'Delete ' + (isType ? 'Type' : 'Category') + ': ' + x.name + '?' });
        if (!ok) return;
        var r = await SL.del('/api/solutions/' + Page.id + '/terms/' + kind + '/' + id);
        if (!r || !r.ok) { SL.warn('termLookupsWarn', SL.failText(r, 'That could not be removed.')); return; }
        afterLookupChange(r.data);
    }

    function afterLookupChange(data) {
        mRenaming = null;
        if (data) mLookups = { types: data.types || [], categories: data.categories || [] };
        SL.warn('termLookupsWarn', '');
        drawLookups();
        fillFilters();
        loadFirst();
    }

    // ---- the tab -------------------------------------------------------------------------------------

    function searchSoon() {
        clearTimeout(mSearchTimer);
        mSearchTimer = setTimeout(loadFirst, TYPING_PAUSE_MS);   // nothing is sent while still typing
    }

    Page.registerTab('terms', {
        init: function () {
            $('btnAddTerm').addEventListener('click', openAdd);
            $('fTermSearch').addEventListener('input', searchSoon);
            $('fTermSearch').addEventListener('keydown', function (e) {
                if (e.key === 'Enter') { e.preventDefault(); clearTimeout(mSearchTimer); loadFirst(); }
            });
            $('fTermGroup').addEventListener('change', function () { viewSet('groupBy', groupBy()); loadFirst(); });
            $('fTermType').addEventListener('change', loadFirst);
            $('fTermCategory').addEventListener('change', loadFirst);
            $('fTermFav').addEventListener('change', loadFirst);
            $('btnTermsFold').addEventListener('click', foldAll);
            $('btnTermsReverse').addEventListener('click', function () { setReversed(!reversed()); viewSet('reverse', reversed() ? '1' : '0'); loadFirst(); });
            $('btnTermsRandom').addEventListener('click', function () { SL.guard($('btnTermsRandom'), surprise); });
            $('btnTermsCopy').addEventListener('click', function () { SL.guard($('btnTermsCopy'), copyAll); });
            $('btnTermsLookups').addEventListener('click', openLookups);

            $('termCancel').addEventListener('click', function () { SL.close('termOverlay'); });
            $('termSave').addEventListener('click', function () { SL.guard($('termSave'), save); });
            if (window.PicassoPicker) {
                window.PicassoPicker.attach({ input: $('tmType'), dropdown: $('tmTypeDD'), allowNew: true, newLabel: 'Add type', clearLabel: '(no type)',
                    getItems: function () { return lookupNames(mLookups.types); } });
            }
            if (window.PicassoTags) {
                mTags = window.PicassoTags.attach({ input: $('tmCatInput'), chips: $('tmCatChips'), dropdown: $('tmCatDD'), noun: 'category',
                    getOptions: function () { return lookupNames(mLookups.categories); } });
            }

            $('termViewClose').addEventListener('click', function () { SL.close('termViewOverlay'); mViewTerm = null; });
            $('termViewEdit').addEventListener('click', function () { if (mViewTerm) { var id = mViewTerm.id; SL.close('termViewOverlay'); openEdit(id); } });
            $('termViewStar').addEventListener('click', function () { if (mViewTerm) SL.guard($('termViewStar'), function () { return toggleStar(mViewTerm.id); }); });
            $('termViewAnother').addEventListener('click', function () { SL.guard($('termViewAnother'), surprise); });

            $('termLookupsClose').addEventListener('click', function () { SL.close('termLookupsOverlay'); });
            $('termLookupsBody').addEventListener('click', onLookupClick);
            $('termLookupsBody').addEventListener('keydown', function (e) {
                if (e.target.id !== 'tlRename') return;
                if (e.key === 'Enter') { e.preventDefault(); var s = $('termLookupsBody').querySelector('[data-lk="save"]'); if (s) s.click(); }
            });
        },
        show: async function () {
            drawShell();
            var restored = restoreFilters();
            if (!mSynced) {
                mSynced = true;
                try {
                    await Promise.all([groups().sync(), window.PicassoViewState ? window.PicassoViewState.sync(VIEW_KEYS) : null]);
                } catch (e) { /* the device copy is used until the server answers */ }
                var g = viewGet('groupBy');
                $('fTermGroup').value = (g === 'category' || g === 'none') ? g : 'type';
                setReversed(viewGet('reverse') === '1');
            }
            await loadLookups();
            fillFilters(restored);
            await loadFirst();
        },
        print: print,
        /** A deep link (?term=) or anything else that wants one term shown. */
        openTerm: openTerm
    });
})();
