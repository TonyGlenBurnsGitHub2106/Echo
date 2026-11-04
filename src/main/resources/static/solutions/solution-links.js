(function () {
    'use strict';

    // Links (2026-11-02). Tony: "Let anything in a solution link to anything else, and gather it by
    // topic ... Do not lose real-estate." One Linked section, drawn at the FOOT of a record's own View
    // (an item, a note, a term, a file, a topic) or in the small Links sheet a log entry opens -- never
    // a new row on a list. A link has no direction, so the section is also the record's backlinks.
    //
    //   SolutionLinks.mount(host, kind, id, title)   draw the section for one record into host
    //   SolutionLinks.openSheet(kind, id, title)     the Links sheet (used by a log entry)
    //   SolutionLinks.openRecord(kind, id)           take the reader to a linked record
    //   SolutionLinks.KIND_NAMES                     'item' -> 'Item' ...
    //
    // Link opens one picker: type a few words, tap what to link. Typing a name no topic has offers
    // New topic "<name>", which makes the topic and links to it in one tap. The section folds, and the fold
    // is remembered in the database (PicassoGroupState), once for each kind of record.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    // Story 5695 (Echo): questions and decisions are linkable records too.
    var KIND_NAMES = { item: 'Item', note: 'Note', log: 'Log', file: 'File', term: 'Term', topic: 'Topic', message: 'Message',
                       question: 'Question', decision: 'Decision' };
    var PAGE_SIZE = 50, TYPING_PAUSE_MS = 250;

    var mFolds = null;
    var mPick = null;          // { kind, id, title, host } while the picker is open; Story 5695: { pick: true, targetKind, onPick } in pick mode
    var mTags = null;          // Story 5695 (Echo): this solution's tags [{id, name}], read once a page, again after a new one
    var mStarterTags = ['Important', 'Risk', 'Idea', 'Parking lot'];
    var mAsk = 0, mTimer = null;
    var mMounted = [];         // every section on screen, so a change redraws each of them

    function folds() {
        if (!mFolds) mFolds = window.PicassoGroupState.create({ key: 'leo.sl.links', defaultOpen: true });
        return mFolds;
    }

    // ---- the section ------------------------------------------------------------------------

    /** Draws the Linked section for one record into host and reads its links. */
    async function mount(host, kind, id, title) {
        if (!host) return;
        mMounted = mMounted.filter(function (m) { return m.host.isConnected && m.host !== host; });
        var state = { host: host, kind: kind, id: id, title: title || '', links: [], total: 0, loading: true };
        mMounted.push(state);
        draw(state);
        await Promise.all([load(state, false), loadTags(false)]);
        draw(state);
    }

    async function load(state, more) {
        var offset = more ? state.links.length : 0;
        var r = await SL.get('/api/solutions/' + Page.id + '/links?kind=' + encodeURIComponent(state.kind) + '&recordId=' + state.id +
            '&offset=' + offset + '&pageSize=' + PAGE_SIZE);
        state.loading = false;
        if (!r || !r.ok || !r.data) { state.error = SL.failText(r, 'The links could not be read.'); draw(state); return; }
        state.error = '';
        state.links = (more ? state.links : []).concat(r.data.links || []);
        state.total = r.data.total || 0;
        draw(state);
    }

    function draw(state) {
        var host = state.host;
        if (!host.isConnected) return;
        var open = folds().isOpen(state.kind);
        var count = state.loading ? '' : '<span class="sl-chip" data-testid="links-count">' + state.total + '</span>';
        var html = '<section class="sl-links' + (open ? '' : ' folded') + '" data-testid="links-section">' +
            '<div class="sl-links-head">' +
                '<button type="button" class="sl-links-toggle" data-links-fold aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="links-toggle">' +
                    '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span><span class="sl-links-title">Linked</span>' + count + '</button>' +
                '<button type="button" class="sl-btn small" data-links-add data-testid="links-add-btn">Link</button>' +
            '</div>';
        if (open) {
            if (state.error) html += '<div class="sl-ov-note sl-ov-error" data-testid="links-error">' + esc(state.error) + '</div>';
            // Story 5695 (Echo): tags are chips at the top, with the solution's other tags offered for one tap.
            var tags = state.links.filter(isTag), rest = state.links.filter(function (l) { return !isTag(l); });
            if (state.kind !== 'topic') html += tagRow(state, tags);
            html += '<div class="sl-links-body">' + rest.map(row).join('') + '</div>';
            if (state.links.length < state.total)
                html += '<button type="button" class="sl-ov-more" data-links-more data-testid="links-more">' + (state.total - state.links.length) + ' more</button>';
        }
        host.innerHTML = html + '</section>';
        wire(state);
    }

    // ---- tags (Story 5695, Echo) ---------------------------------------------------------------------
    // A tag is a topic of kind "tag". On a record it is a chip (x takes it off); the row of dashed chips
    // offers the solution's other tags and the four starters, made the first time one is tapped.

    function isTag(l) { return l.kind === 'topic' && l.topicKind === 'tag'; }

    function tagRow(state, tags) {
        var on = {};
        tags.forEach(function (t) { on[String(t.title || '').toLowerCase()] = true; });
        var offers = (mTags || []).filter(function (t) { return !on[t.name.toLowerCase()]; }).map(function (t) { return { id: t.id, name: t.name }; });
        mStarterTags.forEach(function (n) {
            if (!on[n.toLowerCase()] && !(mTags || []).some(function (t) { return t.name.toLowerCase() === n.toLowerCase(); })) offers.push({ id: 0, name: n });
        });
        return '<div class="sl-tag-row" data-testid="links-tags">' +
            tags.map(function (t) {
                return '<span class="sl-tag" data-testid="links-tag">' + esc(t.title) +
                    '<button type="button" data-link-remove="' + t.linkId + '" title="Take the tag off" aria-label="Take the tag ' + esc(t.title) + ' off" data-testid="links-tag-remove">&#10005;</button></span>';
            }).join('') +
            offers.slice(0, 8).map(function (o) {
                return '<button type="button" class="sl-tag-offer" data-tag-offer="' + o.id + '" data-tag-name="' + esc(o.name) + '" data-testid="links-tag-offer">+ ' + esc(o.name) + '</button>';
            }).join('') + '</div>';
    }

    async function loadTags(force) {
        if (mTags && !force) return;
        var r = await SL.get('/api/solutions/' + Page.id + '/topics?kind=tag&pageSize=100');
        mTags = r && r.ok && r.data ? (r.data.topics || []).map(function (t) { return { id: t.id, name: t.name }; }) : [];
    }

    async function addTag(state, id, name) {
        if (!id) {
            var t = await SL.post('/api/solutions/' + Page.id + '/topics', { name: name, kind: 'tag' });
            if (!t || !t.ok || !t.data) { window.Picasso.error(SL.failText(t, 'The tag could not be made.')); return; }
            id = t.data.id;
            await loadTags(true);
        }
        var r = await SL.post('/api/solutions/' + Page.id + '/links', { fromKind: state.kind, fromId: state.id, toKind: 'topic', toId: id });
        if (!r || !r.ok) { window.Picasso.error(SL.failText(r, 'The tag could not be put on.')); return; }
        refreshAll();
    }

    function row(l) {
        return '<div class="sl-link-row' + (l.closed ? ' closed' : '') + '" data-testid="link-row">' +
            '<button type="button" class="sl-link-open" data-link-open="' + esc(l.kind) + '" data-link-id="' + l.id + '" data-testid="link-open">' +
                '<span class="sl-kind ' + esc(l.kind) + '">' + esc(KIND_NAMES[l.kind] || l.kind) + '</span>' +
                (l.closed ? '<span class="sl-link-tick" aria-label="Closed">&#10003;</span>' : '') +
                '<span class="sl-link-title">' + esc(SL.plain(l.title || '', 160)) + '</span>' +
                (l.sub ? '<span class="sl-link-sub">' + esc(SL.plain(l.sub, 120)) + '</span>' : '') +
            '</button>' +
            '<button type="button" class="sl-iconbtn" data-link-remove="' + l.linkId + '" title="Unlink" aria-label="Unlink" data-testid="link-remove">&#10005;</button>' +
        '</div>';
    }

    function wire(state) {
        var host = state.host;
        var fold = host.querySelector('[data-links-fold]');
        if (fold) fold.addEventListener('click', function () { folds().toggle(state.kind); draw(state); });
        var add = host.querySelector('[data-links-add]');
        if (add) add.addEventListener('click', function () { openPicker(state); });
        var more = host.querySelector('[data-links-more]');
        if (more) more.addEventListener('click', function () { SL.guard(more, function () { return load(state, true); }); });
        Array.prototype.forEach.call(host.querySelectorAll('[data-link-open]'), function (b) {
            b.addEventListener('click', function () { openRecord(b.getAttribute('data-link-open'), +b.getAttribute('data-link-id')); });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-link-remove]'), function (b) {
            b.addEventListener('click', function () { unlink(state, +b.getAttribute('data-link-remove')); });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-tag-offer]'), function (b) {
            b.addEventListener('click', function () {
                SL.guard(b, function () { return addTag(state, +b.getAttribute('data-tag-offer'), b.getAttribute('data-tag-name')); });
            });
        });
    }

    async function unlink(state, linkId) {
        var l = state.links.filter(function (x) { return x.linkId === linkId; })[0];
        if (!l) return;
        var ok = await window.Picasso.confirmDelete(
            'This takes the link away. Both records stay as they are.\n\nThis cannot be undone.',
            { title: 'Unlink ' + (KIND_NAMES[l.kind] || l.kind) + ': ' + SL.plain(l.title || '', 80) + '?' });
        if (!ok) return;
        var r = await SL.del('/api/solutions/' + Page.id + '/links/' + linkId);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The link could not be taken away.')); return; }
        refreshAll();
    }

    /** Every section on screen reads its links again (a link shows at both of its ends). */
    function refreshAll() {
        mMounted = mMounted.filter(function (m) { return m.host.isConnected; });
        mMounted.forEach(function (m) { load(m, false); });
        if (Page.tabs.topics && Page.tabs.topics.changed) Page.tabs.topics.changed();
    }

    // ---- the picker ----------------------------------------------------------------------------

    function openPicker(state) {
        mPick = state;
        $('linkPickFor').textContent = (KIND_NAMES[state.kind] || state.kind) + ': ' + SL.plain(state.title || '', 120);
        $('linkPickSearch').value = '';
        SL.warn('linkPickWarn', '');
        $('linkPickList').innerHTML = '';
        SL.open('linkOverlay');
        $('linkPickSearch').focus();
        search();
    }

    /**
     * Story 5695 (Echo): the same picker in PICK mode -- it does not link; it hands what was tapped to
     * opts.onPick(kind, id, title). opts: { kind, id, title (the record picking), targetKind (one kind only),
     * heading }. Used to choose the item another one waits on.
     */
    function pick(opts) {
        mPick = { pick: true, kind: opts.kind, id: opts.id, title: opts.title || '', targetKind: opts.targetKind || '', onPick: opts.onPick };
        $('linkPickFor').textContent = opts.heading || ((KIND_NAMES[opts.kind] || opts.kind) + ': ' + SL.plain(opts.title || '', 120));
        $('linkPickSearch').value = '';
        SL.warn('linkPickWarn', '');
        $('linkPickList').innerHTML = '';
        SL.open('linkOverlay');
        $('linkPickSearch').focus();
        search();
    }

    function closePicker() { SL.close('linkOverlay'); mPick = null; }

    function searchSoon() {
        clearTimeout(mTimer);
        mTimer = setTimeout(search, TYPING_PAUSE_MS);
    }

    async function search() {
        if (!mPick) return;
        var q = $('linkPickSearch').value.trim(), ask = ++mAsk;
        var r = await SL.get('/api/solutions/' + Page.id + '/link-targets?q=' + encodeURIComponent(q) +
            '&kind=' + encodeURIComponent(mPick.kind) + '&recordId=' + mPick.id +
            (mPick.targetKind ? '&targetKind=' + encodeURIComponent(mPick.targetKind) : ''));
        if (ask !== mAsk || !mPick) return;
        if (!r || !r.ok || !Array.isArray(r.data)) { SL.warn('linkPickWarn', SL.failText(r, 'Could not search this solution.')); return; }
        var rows = r.data;
        var exactTopic = rows.some(function (t) { return t.kind === 'topic' && String(t.title || '').trim().toLowerCase() === q.toLowerCase(); });
        var html = '';
        if (q && !exactTopic && !mPick.pick) {
            html += '<button type="button" class="sl-pick-row new" data-pick-new data-testid="link-pick-new-topic">' +
                '<span class="sl-kind topic">Topic</span><span class="sl-link-title">New topic &#8220;' + esc(q) + '&#8221;</span></button>';
        }
        var linkedCounts = !mPick.pick;                       // Story 5695: in pick mode "already linked" says nothing
        html += rows.map(function (t) {
            if (!linkedCounts) t = Object.assign({}, t, { alreadyLinked: false });
            return '<button type="button" class="sl-pick-row' + (t.alreadyLinked ? ' linked' : '') + '" data-pick-kind="' + esc(t.kind) + '" data-pick-id="' + t.id + '"' +
                (t.alreadyLinked ? ' disabled' : '') + ' data-testid="link-pick-row">' +
                '<span class="sl-kind ' + esc(t.kind) + '">' + esc(KIND_NAMES[t.kind] || t.kind) + '</span>' +
                '<span class="sl-link-title">' + esc(SL.plain(t.title || '', 160)) + '</span>' +
                (t.alreadyLinked ? '<span class="sl-chip">Linked</span>' : (t.sub ? '<span class="sl-link-sub">' + esc(SL.plain(t.sub, 100)) + '</span>' : '')) +
            '</button>';
        }).join('');
        if (!html) html = '<div class="sl-muted sl-pick-none" data-testid="link-pick-none">Nothing in this solution is called that.</div>';
        $('linkPickList').innerHTML = html;
    }

    async function onPick(e) {
        var b = e.target.closest('[data-pick-kind], [data-pick-new]');
        if (!b || b.disabled || !mPick) return;
        await SL.guard(b, async function () {
            if (mPick.pick) {                                      // Story 5695: pick mode hands it back
                var handOff = mPick.onPick, k = b.getAttribute('data-pick-kind'), i = +b.getAttribute('data-pick-id');
                var label = (b.querySelector('.sl-link-title') || {}).textContent || '';
                closePicker();
                if (handOff) await handOff(k, i, label);
                return;
            }
            var toKind, toId;
            if (b.hasAttribute('data-pick-new')) {
                var name = $('linkPickSearch').value.trim();
                var t = await SL.post('/api/solutions/' + Page.id + '/topics', { name: name });
                if (!t || !t.ok || !t.data) { SL.warn('linkPickWarn', SL.failText(t, 'The topic could not be made.')); return; }
                toKind = 'topic'; toId = t.data.id;
            } else {
                toKind = b.getAttribute('data-pick-kind'); toId = +b.getAttribute('data-pick-id');
            }
            var r = await SL.post('/api/solutions/' + Page.id + '/links', { fromKind: mPick.kind, fromId: mPick.id, toKind: toKind, toId: toId });
            if (!r || !r.ok) { SL.warn('linkPickWarn', SL.failText(r, 'The link could not be made.')); return; }
            closePicker();
            refreshAll();
        });
    }

    // ---- the Links sheet (a log entry has no View of its own) -------------------------------------

    function openSheet(kind, id, title) {
        $('linksSheetTitle').textContent = (KIND_NAMES[kind] || kind) + ': ' + SL.plain(title || '', 160);
        SL.open('linksSheetOverlay');
        mount($('linksSheetBody'), kind, id, title);
    }

    // ---- going to a linked record ------------------------------------------------------------------

    /** Closes whatever sheet is open and opens the record the way the Search tab does. */
    function openRecord(kind, id) {
        Array.prototype.forEach.call(document.querySelectorAll('.sl-overlay.open'), function (o) { o.classList.remove('open'); });
        var t = Page.tabs;
        switch (kind) {
            case 'item':    Page.go('items'); t.items.flash(id); break;
            case 'log':     t.log.flash(id); Page.go('log'); break;
            case 'note':    Page.go('notes'); t.notes.openNote(id); break;
            case 'file':    Page.go('files'); t.files.openFile(id); break;
            case 'term':    Page.go('terms'); t.terms.openTerm(id); break;
            case 'topic':   Page.go('topics'); t.topics.openTopic(id); break;
            case 'message': Page.go('conversations'); t.conversations.openThread(id); break;
            // Story 5695 (Echo): their own sepia View opens over whatever tab is showing.
            case 'question': if (window.SolutionDecisions) window.SolutionDecisions.openQuestion(id); break;
            case 'decision': if (window.SolutionDecisions) window.SolutionDecisions.openDecision(id); break;
            default: break;
        }
    }

    function init() {
        $('linkPickSearch').addEventListener('input', searchSoon);
        $('linkPickSearch').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); clearTimeout(mTimer); search(); } });
        $('linkPickList').addEventListener('click', onPick);
        $('linkPickCancel').addEventListener('click', closePicker);
        $('linksSheetClose').addEventListener('click', function () { SL.close('linksSheetOverlay'); });
        // Registered before any tab's own Escape handler, so it can keep one Escape from also closing the
        // sheet underneath: the picker (or the Links sheet) closes, and nothing else hears the key.
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            if ($('linkOverlay').classList.contains('open')) { closePicker(); e.stopImmediatePropagation(); }
            else if ($('linksSheetOverlay').classList.contains('open')) { SL.close('linksSheetOverlay'); e.stopImmediatePropagation(); }
        });
        folds().sync().then(function (changed) { if (changed) mMounted.forEach(draw); }).catch(function () { /* the device copy stands */ });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
    else init();

    window.SolutionLinks = { mount: mount, openSheet: openSheet, openRecord: openRecord, refresh: refreshAll, KIND_NAMES: KIND_NAMES, pick: pick };
})();
