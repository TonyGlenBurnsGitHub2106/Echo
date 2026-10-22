(function () {
    'use strict';

    // One solution (Story 3493): the page core. Owns the solution, its projects, the lookups
    // and the tab switch, and draws the Overview tab. Each other tab is its own script that
    // registers itself here -- solution-items.js, solution-log.js, solution-notes.js,
    // solution-conv.js, solution-files.js, solution-tests.js -- so this file never learns how a tab works.
    //
    // Deep links this page reads:
    //   ?id=<SolutionID>[&tab=items|log|notes|terms|tests|qa|config|files|conversations|web|search]
    //   ?id=<SolutionID>&tab=search&q=<word>   opens the Search tab and runs that word (2026-10-16)
    //   ?item=<ItemID>        resolves its own solution, opens Items, flashes the row
    //   ?note=<NoteID>        resolves its own solution, opens that note (?doc= is still read: older links)
    //   ?term=<TermID>        resolves its own solution, opens the Terms tab and shows that term
    //   ?testPlan=<PlanID>    resolves its own solution, opens the Testing tab (or QA, for a QA checklist) on it
    //   ?configProperty=<ID>  resolves its own solution, opens Configuration and flashes that property (Story 3516)
    //   ?gridProperty=<ID>    the same for Configuration II (Story 5611)
    //   ?id=..&conv=<ConversationID>  opens that thread

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc;

    var Page = {
        id: 0, solution: null, projects: [],
        lookups: { statuses: [], members: [], logTypes: [], noteTypes: [] },
        tabs: {}, active: 'overview'
    };
    window.SolutionPage = Page;

    Page.registerTab = function (name, tab) { Page.tabs[name] = tab; };

    // What is folded and what is open, remembered on this device, one store per solution.
    var mFold = null;
    Page.fold = function () {
        if (!mFold) mFold = SL.collapseStore('sl.fold.' + Page.id);
        return mFold;
    };

    // Projects: one full-width row each (the default), or a grid of cards. A preference, not per
    // solution. Only an explicit choice of Cards ('0') turns rows off.
    var mRows = true;
    try { mRows = localStorage.getItem('sl.projects.rows') !== '0'; } catch (e) { /* private mode */ }

    // ---- option lists every tab needs -----------------------------------------

    Page.statusOptions = function () { return Page.lookups.statuses.map(function (s) { return { value: s.code, label: s.name }; }); };
    Page.memberOptions = function (blankLabel) {
        return [{ value: '', label: blankLabel }].concat(Page.lookups.members
            .filter(function (m) { return m.active; }).map(function (m) { return { value: m.code, label: m.name }; }));
    };
    Page.projectOptions = function (blankLabel) {
        var head = blankLabel == null ? [] : [{ value: '', label: blankLabel }];
        return head.concat(Page.projects.map(function (p) { return { value: String(p.id), label: p.name }; }));
    };
    Page.statusOf = function (code) {
        return Page.lookups.statuses.filter(function (s) { return s.code === code; })[0] || { name: code, colorHex: '#6B7280' };
    };
    Page.isAgent = function (code) {
        return Page.lookups.members.some(function (m) { return m.code === code && m.agent; });
    };
    Page.personChip = function (code, name) {
        if (!code) return '';
        return '<span class="sl-chip' + (Page.isAgent(code) ? ' agent' : '') + '">' + esc(name || code) + '</span>';
    };

    // ---- load -----------------------------------------------------------------

    /** Statuses, people and the two kinds. Fetched again after a save that may have added a kind. */
    Page.loadLookups = async function () {
        var l = await SL.get('/api/solutions/lookups');
        if (l && l.ok && l.data) Page.lookups = l.data;
    };

    /** The names of a list of kinds, for a Kind picker. */
    Page.kindNames = function (kinds) { return (kinds || []).map(function (t) { return t.name; }); };

    /** The name of the kind with this code ('' when there is none). */
    Page.kindName = function (kinds, code) {
        var k = (kinds || []).filter(function (t) { return t.code === code; })[0];
        return k ? k.name : '';
    };

    Page.reload = async function () {
        var results = await Promise.all([
            SL.get('/api/solutions/' + Page.id),
            SL.get('/api/solutions/' + Page.id + '/projects')
        ]);
        if (!results[0] || !results[0].ok) return false;
        Page.solution = results[0].data;
        Page.projects = (results[1] && results[1].ok && Array.isArray(results[1].data)) ? results[1].data : [];
        paintHeader();
        return true;
    };

    function paintHeader() {
        var s = Page.solution;
        if ($('pageTitle')) $('pageTitle').textContent = s.name;
        document.title = s.name + ' - Solutions - Echo';
        var open = s.itemCount - s.closedItemCount;
        $('badgeItems').textContent = open > 0 ? String(open) : '';
        $('badgeItems').style.background = '#4B5563';
        $('badgeConv').textContent = s.awaitingTonyCount > 0 ? String(s.awaitingTonyCount) : '';
        $('btnArchive').textContent = s.archived ? 'Restore' : 'Archive';
    }

    // ---- tabs -----------------------------------------------------------------

    // ---- Arrange ---------------------------------------------------------------------------
    // Every grip and every up / down arrow on the page is hidden until Arrange (the up-and-down
    // arrows button on the tab's tool row) is tapped -- the way a matter's Notes tab works. Tap it
    // again to finish. Not remembered: it is a mode you are in, and changing tab ends it.
    // CSS does the hiding (body.sl-arrangeable:not(.sl-arranging)), so no tab redraws.

    Page.arranging = false;

    Page.setArranging = function (on) {
        Page.arranging = !!on;
        document.body.classList.toggle('sl-arranging', Page.arranging);
        Array.prototype.forEach.call(document.querySelectorAll('[data-arrange]'), function (b) {
            b.classList.toggle('on', Page.arranging);
            b.setAttribute('aria-pressed', Page.arranging ? 'true' : 'false');
            b.title = Page.arranging ? 'Done arranging' : 'Arrange -- drag to reorder';
        });
    };

    Page.go = function (name) {
        if (name === 'documents') name = 'notes';   // the tab was called Documents until 2026-09-22
        if (name !== Page.active) Page.setArranging(false);
        if (name !== 'overview' && !Page.tabs[name]) name = 'overview';
        Page.active = name;
        Array.prototype.forEach.call(document.querySelectorAll('.sl-tab'), function (t) {
            t.classList.toggle('active', t.getAttribute('data-tab') === name);
        });
        ['overview', 'items', 'log', 'notes', 'terms', 'tests', 'qa', 'config', 'grid', 'files', 'conversations', 'search'].forEach(function (n) {
            $('pane-' + n).hidden = n !== name;
            // The Web tab (the shared record Web tab) carries its own tool row, so it has no toolbar here.
            if ($('toolbar-' + n)) $('toolbar-' + n).hidden = n !== name;
        });
        try { sessionStorage.setItem('sl.tab.' + Page.id, name); } catch (e) { /* private mode */ }
        if (name === 'overview') drawOverview();
        else Page.tabs[name].show();
    };

    // ---- overview -------------------------------------------------------------

    function drawOverview() {
        var s = Page.solution;
        // The summary card folds to one line (status and percent closed) so the projects get the
        // room; the choice is remembered with the other folds for this solution.
        var heroFolded = Page.fold().isCollapsed('hero', false);
        var pct = s.itemCount > 0 ? Math.round(s.closedItemCount * 100 / s.itemCount) : 0;
        var heroHead = '<div class="sl-herohead sl-foldable" id="heroFold" data-testid="solution-hero-toggle" role="button" tabindex="0" aria-expanded="' + (heroFolded ? 'false' : 'true') + '">' +
            '<span class="chev' + (heroFolded ? ' folded' : '') + '">&#9660;</span><strong class="sl-heroname" id="pageTitle" data-testid="page-title">' + esc(s.name) + '</strong>' +
            (s.alias ? '<span class="sl-chip" title="The short name for this solution" data-testid="solution-alias-chip">' + esc(s.alias) + '</span>' : '') +
            (heroFolded ? '<span class="sl-muted">' + s.closedItemCount + ' of ' + s.itemCount + ' items closed' + (s.itemCount > 0 ? ' (' + pct + '%)' : '') + '</span>' : '') +
            (heroFolded ? SL.pill(s.statusName, s.colorHex) + (s.blockedItemCount ? '<span class="sl-chip warn">' + s.blockedItemCount + ' blocked</span>' : '') +
                (s.awaitingTonyCount ? '<span class="sl-chip wait">' + s.awaitingTonyCount + ' waiting on you</span>' : '') : '') + '</div>';
        var hero = heroFolded
            ? '<div class="sl-hero folded" data-testid="solution-hero">' + heroHead + '</div>'
            : '<div class="sl-hero" data-testid="solution-hero">' + heroHead +
            '<div class="row" style="display:flex;flex-wrap:wrap;gap:6px;align-items:center">' + SL.pill(s.statusName, s.colorHex) +
                Page.personChip(s.ownerCode, s.ownerName) +
                (s.targetDate ? '<span class="sl-muted">Target ' + SL.date(s.targetDate) + '</span>' : '') +
                (s.archived ? '<span class="sl-chip">Archived</span>' : '') +
                (s.blockedItemCount ? '<span class="sl-chip warn">' + s.blockedItemCount + ' blocked</span>' : '') +
                (s.awaitingTonyCount ? '<span class="sl-chip wait">' + s.awaitingTonyCount + ' waiting on you</span>' : '') + '</div>' +
            (s.statusSummary ? '<div class="desc sl-md" data-testid="solution-summary-text">' + SL.md('**Where things stand.** ' + s.statusSummary) + '</div>' : '') +
            (s.description ? '<div class="desc sl-md">' + SL.md(s.description) + '</div>' : '') +
            SL.progress(s.closedItemCount, s.itemCount) +
            '</div>';

        var shown = openOnly() ? Page.projects.filter(function (p) { return !p.closed; }) : Page.projects;
        $('fProjOpen').checked = openOnly();
        var projects = shown.length
            ? '<div class="sl-cards' + (mRows ? ' rows' : '') + '" id="projectCards" data-testid="project-cards">' + shown.map(projectCard).join('') + '</div>'
            : '<div class="sl-empty">' + (Page.projects.length ? 'Every project is closed. Untick Open only to see them.' : 'No projects yet. A project is one workstream of the solution.') + '</div>';

        $('pane-overview').innerHTML = hero +
            '<div class="sl-section-title">Projects</div>' + projects +
            '<div class="sl-section-title">Who can reach this from outside</div><div id="clientLine" class="sl-muted" data-testid="solution-clients">Checking&hellip;</div>';

        function flipHero() { Page.fold().toggle('hero', false); drawOverview(); }
        $('heroFold').addEventListener('click', flipHero);
        $('heroFold').addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flipHero(); } });
        Array.prototype.forEach.call(document.querySelectorAll('[data-project-open]'), function (b) {
            b.addEventListener('click', function () { Page.tabs.items.openProject(+b.getAttribute('data-project-open')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-project-print]'), function (b) {
            b.addEventListener('click', function () { Page.printReport(+b.getAttribute('data-project-print')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-project-edit]'), function (b) {
            b.addEventListener('click', function () { openProject(+b.getAttribute('data-project-edit')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-project-delete]'), function (b) {
            b.addEventListener('click', function () { deleteProject(+b.getAttribute('data-project-delete')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-proj-fold]'), function (h) {
            h.addEventListener('click', function () {
                Page.fold().toggle('proj:' + h.getAttribute('data-proj-fold'), true);
                drawOverview();
            });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-proj-grip]'), function (g) {
            Page.wireProjectGrip(g, '[data-project-card]', 'data-project-card', drawOverview);
        });
        $('btnProjView').textContent = mRows ? 'Cards' : 'Rows';
        drawClients();
        SL.status(Page.projects.length + (Page.projects.length === 1 ? ' project' : ' projects') + ' | ' +
            s.itemCount + ' items | ' + s.logCount + ' log entries | ' + s.noteCount + ' notes');
    }

    /** A project card. Its title folds it; folded, it is one line. The grip at its left reorders it. */
    function projectCard(p) {
        var folded = Page.fold().isCollapsed('proj:' + p.id, true);
        // Badge far right, count right-aligned just left of it, both a fixed width -- so down a list of
        // projects the counts and the badges line up in columns (Tony, 2026-09-18).
        var head = '<div class="sl-cardhead sl-projhead">' +
            '<span class="sl-grip" data-proj-grip="' + p.id + '" title="Drag to move this project up or down" aria-label="Drag to move ' + esc(p.name) + '" data-testid="project-grip">&#8942;&#8942;</span>' +
            '<div class="sl-cardtitle" data-proj-fold="' + p.id + '" data-testid="project-title">' +
                '<span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span> <h3>' + esc(p.name) + '</h3></div>' +
            (p.blockedItemCount ? '<span class="sl-chip warn">' + p.blockedItemCount + ' blocked</span>' : '') +
            Page.copyButton(p) +
            '<span class="sl-muted sl-count" data-testid="project-count">' + p.closedItemCount + ' of ' + p.itemCount + '</span>' +
            SL.pill(p.statusName, p.colorHex) + '</div>';
        if (folded) return '<div class="sl-card folded" data-project-card="' + p.id + '" data-testid="project-card">' + head + '</div>';
        return '<div class="sl-card" data-project-card="' + p.id + '" data-testid="project-card">' + head +
            '<div class="row" style="margin-top:8px">' + Page.personChip(p.ownerCode, p.ownerName) + '</div>' +
            (p.descriptionPreview ? '<div class="summary sl-md">' + SL.md(p.descriptionPreview, p.descriptionPreview.length >= 300) + '</div>' : '') +
            SL.progress(p.closedItemCount, p.itemCount) +
            '<div class="actions">' +
                '<button type="button" class="sl-btn small primary" data-project-open="' + p.id + '" data-testid="project-open-items">Items</button>' +
                '<button type="button" class="sl-btn small" data-project-print="' + p.id + '" data-testid="project-print-btn">Print</button>' +
                '<button type="button" class="sl-btn small" data-project-edit="' + p.id + '" data-testid="project-edit-btn">Edit</button>' +
                '<button type="button" class="sl-btn small danger" data-project-delete="' + p.id + '" data-testid="project-delete-btn">Delete</button>' +
            '</div></div>';
    }

    // ---- copy a project's name ----------------------------------------------------------
    //
    // A small Copy button beside the name, on the Overview rows / cards and on the Items tab's project
    // groups. One document-level listener serves both, so a redraw never loses it.

    Page.copyButton = function (p) {
        return '<button type="button" class="sl-iconbtn sl-copyname" data-copy-project="' + p.id + '" title="Copy the project name" aria-label="Copy the name ' +
            esc(p.name) + '" data-testid="project-copy-name">Copy</button>';
    };

    document.addEventListener('click', async function (e) {
        var b = e.target && e.target.closest ? e.target.closest('[data-copy-project]') : null;
        if (!b) return;
        e.preventDefault();
        e.stopPropagation();
        var p = Page.projects.filter(function (x) { return x.id === +b.getAttribute('data-copy-project'); })[0];
        if (!p) return;
        if (!(await SL.copyText(p.name))) SL.toast('Could not copy -- the browser refused.');   // silent when it works
    }, true);

    // ---- reorder projects by their grip -------------------------------------------------
    //
    // Pointer events, so one handler serves a mouse and a finger; the grip sets touch-action:none
    // so a finger on it moves the card instead of scrolling the page. The card is reordered in
    // the DOM as it travels, and the new order is saved when it is let go.

    // Shared by the Overview (project rows / cards), the Items tab (project groups) and the
    // Notes tab: the thing being moved is whatever matches cardSelector, its id is in
    // idAttr, and redraw repaints the caller's tab once the new order is saved. onDrop, when
    // given, saves the new order itself (it gets the ids in their new order); without it the
    // ids are projects. A card only moves among its own siblings, so notes stay in their kind.
    Page.wireProjectGrip = function (grip, cardSelector, idAttr, redraw, onDrop) {
        grip.addEventListener('click', function (e) { e.stopPropagation(); });
        grip.addEventListener('pointerdown', function (down) {
            var card = grip.closest(cardSelector);
            var host = card ? card.parentNode : null;
            if (!card || !host) return;
            down.preventDefault();
            down.stopPropagation();
            // Listen on the DOCUMENT, never through pointer capture: moving the card in the DOM
            // re-inserts the very element holding the capture, which silently releases it and
            // the rest of the gesture is lost.
            card.classList.add('dragging');
            function order() {
                return Array.prototype.map.call(host.querySelectorAll(cardSelector), function (c) { return +c.getAttribute(idAttr); });
            }
            var before = order().join(',');

            function onMove(ev) {
                var scroller = $('slBody'), box = scroller.getBoundingClientRect();
                if (ev.clientY < box.top + 48) scroller.scrollTop -= 14;
                if (ev.clientY > box.bottom - 48) scroller.scrollTop += 14;
                var under = document.elementFromPoint(ev.clientX, ev.clientY);
                var over = under && under.closest ? under.closest(cardSelector) : null;
                if (!over || over === card || over.parentNode !== host) return;
                // Same visual row (a multi-column grid): left or right half decides. Otherwise: top or bottom half.
                var r = over.getBoundingClientRect(), mine = card.getBoundingClientRect();
                var sameRow = Math.abs(r.top - mine.top) < 10;
                var after = sameRow ? ev.clientX > r.left + r.width / 2 : ev.clientY > r.top + r.height / 2;
                var ref = after ? over.nextSibling : over;
                if (ref === card || card.nextSibling === ref) return;        // already there: do not touch the DOM
                host.insertBefore(card, ref);
            }
            function onUp() {
                document.removeEventListener('pointermove', onMove);
                document.removeEventListener('pointerup', onUp);
                document.removeEventListener('pointercancel', onUp);
                card.classList.remove('dragging');
                var ids = order();
                if (ids.join(',') === before) return;
                if (onDrop) onDrop(ids); else saveOrder(ids, redraw);
            }
            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', onUp);
            document.addEventListener('pointercancel', onUp);
        });
    };

    async function saveOrder(ids, redraw) {
        var calls = [];
        ids.forEach(function (id, index) {
            var p = Page.projects.filter(function (x) { return x.id === id; })[0];
            if (p && p.sortOrder !== index + 1) calls.push(SL.put('/api/solutions/projects/' + id, { sortOrder: index + 1 }));
        });
        var results = await Promise.all(calls);
        var failed = results.filter(function (x) { return !x || !x.ok; })[0];
        if (results.some(function (x) { return !x || !x.ok; })) SL.toast(window.LWAuth.errorText(failed, 'The new order could not be saved.'));
        await Page.reload();
        if (redraw) redraw();
    }

    // "Open only" on the projects: remembered for this solution for the browser session.
    function openOnly() { try { return sessionStorage.getItem('sl.projects.open.' + Page.id) === '1'; } catch (e) { return false; } }
    function setOpenOnly(on) { try { sessionStorage.setItem('sl.projects.open.' + Page.id, on ? '1' : '0'); } catch (e) { /* private mode */ } }

    function foldAllProjects(collapsed) {
        // Tony's rule (2026-09-24): remembered, and reaches projects added later.
        Page.fold().setAllForPrefix('proj:', collapsed);
        drawOverview();
    }

    /** Who this solution is shared with. Changed with the Share button. */
    async function drawClients() {
        var r = await SL.get('/api/solutions/' + Page.id + '/shares');
        var line = $('clientLine');
        if (!line) return;
        if (!r || !r.ok || !Array.isArray(r.data)) { line.textContent = window.LWAuth.errorText(r, 'Could not check.'); return; }
        var shared = r.data.filter(function (c) { return c.shared; });
        mSharedIds = shared.map(function (c) { return c.apiClientId; });
        $('btnBrief').hidden = !shared.length;
        if (!shared.length) { line.textContent = 'Nobody. This solution is visible only inside Echo.'; return; }
        // One line per client it is shared with, each with the Brief to hand that client.
        line.innerHTML = shared.map(function (c) {
            var state = !c.enabled ? ' <span class="sl-chip warn">switched off</span>' : (!c.hasKey ? ' <span class="sl-chip wait">no key yet</span>' : '');
            return '<div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:6px" data-testid="solution-shared-row">' +
                '<span>Shared with <strong>' + esc(c.clientName) + '</strong>' + state + '</span>' +
                '<button type="button" class="sl-btn small" data-share-brief="' + c.apiClientId + '" data-testid="solution-brief-btn">Brief</button></div>';
        }).join('') + '<div>Its files stay off the repo.</div>';
        wireBriefButtons(line);
    }

    // ---- the brief for a client this solution is shared with -------------------------------
    //
    // The server sends the text with a SLOT where the key goes; it has no key to send. The key
    // the person pastes is swapped in here, in the browser, only for the copy -- it is never
    // posted, never stored, never drawn on screen, and the field is wiped when the box closes.

    var mBrief = null;      // {text, keySlot, keyHint, clientName}
    var mSharedIds = [];    // who this solution is shared with; drives the Brief button in the band

    /** Brief, from the band: straight to it when there is one client, the Share box when several. */
    function briefFromBand() {
        if (mSharedIds.length === 1) openBrief(mSharedIds[0]);
        else openShare();
    }

    // The header row is only the anchor for the tool bar strip. Once Print has been lifted out
    // of it into the strip it is empty, so it folds away. If the strip never arrives, the row
    // stays, because it is then the only place Print lives.
    function slimHeader() {
        var header = document.querySelector('.ct-header'), tries = 0;
        var t = window.setInterval(function () {
            tries++;
            if (header && !header.querySelector('button, a')) { header.classList.add('is-empty'); window.clearInterval(t); }
            else if (tries > 40) window.clearInterval(t);
        }, 150);
    }

    function wireBriefButtons(scope) {
        Array.prototype.forEach.call(scope.querySelectorAll('[data-share-brief]'), function (b) {
            b.addEventListener('click', function () { openBrief(+b.getAttribute('data-share-brief')); });
        });
    }

    async function openBrief(apiClientId) {
        var r = await SL.get('/api/solutions/' + Page.id + '/shares/' + apiClientId + '/brief');
        if (!r || !r.ok || !r.data || !r.data.text) { SL.toast(SL.failText(r, 'The brief could not be loaded.')); return; }
        mBrief = r.data;
        $('sbTitle').textContent = 'Brief for ' + mBrief.clientName + ' -- ' + Page.solution.name;
        $('sbKeyLabel').textContent = mBrief.hasKey ? 'The key (the one ending ' + (mBrief.keyHint || '?') + ')'
                                                    : 'The key -- none issued yet: Admin, API Clients, Issue key';
        $('sbKey').value = '';
        $('sbText').textContent = mBrief.text;
        SL.warn('sbWarn', '');
        SL.open('shareBriefOverlay');
    }

    function closeBrief() { $('sbKey').value = ''; mBrief = null; SL.close('shareBriefOverlay'); }

    async function copyBrief(withKey) {
        if (!mBrief) return;
        var text = mBrief.text;
        if (withKey) {
            var key = $('sbKey').value.trim();
            if (!key) { SL.warn('sbWarn', 'Paste the key first, or use Copy without key.'); $('sbKey').focus(); return; }
            if (key.indexOf('echo_') !== 0) { SL.warn('sbWarn', 'That does not look like an Echo client key. They start with echo_.'); return; }
            if (mBrief.keyHint && key.slice(-mBrief.keyHint.length) !== mBrief.keyHint) {
                SL.warn('sbWarn', 'That is not the current key: the current one ends ' + mBrief.keyHint + '. An old key no longer works.');
                return;
            }
            text = text.split(mBrief.keySlot).join(key);
        }
        SL.warn('sbWarn', '');
        try { await navigator.clipboard.writeText(text); SL.toast(withKey ? 'Brief copied, key included' : 'Brief copied, no key in it'); }
        catch (e) { SL.warn('sbWarn', 'The browser would not copy. Select the text and copy it by hand' + (withKey ? ', then put the key in yourself.' : '.')); }
    }

    // ---- share ---------------------------------------------------------------------

    async function openShare() {
        SL.warn('shareWarn', '');
        var r = await SL.get('/api/solutions/' + Page.id + '/shares');
        var rows = (r && r.ok && Array.isArray(r.data)) ? r.data : [];
        $('shareList').innerHTML = rows.length ? rows.map(function (c) {
            var state = !c.enabled ? ' <span class="sl-chip warn">switched off</span>' : (!c.hasKey ? ' <span class="sl-chip wait">no key yet</span>' : '');
            return '<li><label class="grow" style="display:flex;gap:10px;align-items:center;cursor:pointer">' +
                '<input type="checkbox" data-share="' + c.apiClientId + '"' + (c.shared ? ' checked' : '') + ' data-testid="share-check">' +
                '<span>' + esc(c.clientName) + ' <span class="sl-muted">' + esc(c.clientCode) + '</span>' + state + '</span></label>' +
                (c.shared ? '<button type="button" class="sl-btn small" data-share-brief="' + c.apiClientId + '" data-testid="share-brief-btn">Brief</button>' : '') + '</li>';
        }).join('') : '<li><span class="sl-muted">There is nobody to share with yet. Create an API client first.</span></li>';
        Array.prototype.forEach.call(document.querySelectorAll('[data-share]'), function (box) {
            box.addEventListener('change', function () { setShare(+box.getAttribute('data-share'), box.checked, box); });
        });
        wireBriefButtons($('shareList'));
        SL.open('shareOverlay');
    }

    async function setShare(apiClientId, shared, box) {
        var r = shared ? await SL.post('/api/solutions/' + Page.id + '/shares', { apiClientId: apiClientId })
                       : await SL.del('/api/solutions/' + Page.id + '/shares/' + apiClientId);
        if (!r || !r.ok) { box.checked = !shared; SL.warn('shareWarn', SL.failText(r, 'That did not work.')); return; }
        SL.warn('shareWarn', r.data.filesMovedOffRepo ? 'Shared. This solution was saving files to the repo; that is now switched off.' : '');
        SL.toast(shared ? 'Shared' : 'No longer shared');
        await Page.reload();
        var moved = r.data.filesMovedOffRepo;
        await openShare();      // redraw the list: a Brief button belongs only beside a client it is shared with
        if (moved) SL.warn('shareWarn', 'Shared. This solution was saving files to the repo; that is now switched off.');
    }

    // ---- edit the solution ------------------------------------------------------

    function openSolution() {
        var s = Page.solution;
        SL.warn('solutionWarn', '');
        $('sName').value = s.name || '';
        $('sAlias').value = s.alias || '';
        $('sSummary').value = s.statusSummary || '';
        $('sDescription').value = s.description || '';
        $('sTarget').value = SL.date(s.targetDate);
        SL.fillSelect($('sStatus'), Page.statusOptions(), s.statusCode);
        SL.fillSelect($('sOwner'), Page.memberOptions('Nobody yet'), s.ownerCode || '');
        SL.open('solutionOverlay');
    }

    async function saveSolution() {
        var name = $('sName').value.trim();
        if (!name) { SL.warn('solutionWarn', 'A solution needs a name.'); return; }
        var target = SL.toIsoDate($('sTarget').value);
        if (target === null) { SL.warn('solutionWarn', 'The target date should look like 09/17/2026.'); return; }
        var alias = $('sAlias').value.trim().toLowerCase();
        if (alias && (!/^[a-z0-9-]+$/.test(alias) || !/[a-z]/.test(alias))) { SL.warn('solutionWarn', 'An alias is letters, digits and hyphens, with at least one letter -- for example: claims'); return; }
        var body = { alias: alias, name: name, statusSummary: $('sSummary').value, description: $('sDescription').value,
                     statusCode: $('sStatus').value, ownerCode: $('sOwner').value };
        if (target) body.targetDate = target;
        var r = await SL.put('/api/solutions/' + Page.id, body);
        if (!r || !r.ok) { SL.warn('solutionWarn', SL.failText(r, 'The solution could not be saved.')); return; }
        SL.close('solutionOverlay');
        await Page.reload();
        Page.go('overview');
    }

    async function toggleArchive() {
        var s = Page.solution;
        var question = s.archived
            ? 'Restore the solution "' + s.name + '" to the list?'
            : 'Archive the solution "' + s.name + '"? It leaves the list but nothing is deleted, and you can restore it.';
        if (!window.confirm(question)) return;
        var r = await SL.put('/api/solutions/' + Page.id, { archived: !s.archived });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That did not work.')); return; }
        await Page.reload();
        Page.go('overview');
    }

    async function deleteSolution() {
        var s = Page.solution;
        var question = 'Delete the solution "' + s.name + '"?\n\nThis also deletes its ' + s.projectCount + ' projects, ' + s.itemCount +
            ' items, ' + s.logCount + ' log entries and ' + s.noteCount + ' notes. Its conversations are kept.\n\nThis cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/' + Page.id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The solution could not be deleted.')); return; }
        window.location.href = '/solutions/solutions.html';
    }

    // ---- projects ----------------------------------------------------------------

    var mProjectId = 0;

    async function openProject(id) {
        mProjectId = id || 0;
        SL.warn('projectWarn', '');
        var p = { name: '', description: '', statusCode: Page.lookups.statuses.length ? Page.lookups.statuses[0].code : '', ownerCode: '' };
        if (mProjectId) {
            var r = await SL.get('/api/solutions/projects/' + mProjectId);     // the list carries only a preview
            if (!r || !r.ok) { SL.toast(SL.failText(r, 'That project could not be opened.')); return; }
            p = r.data;
        }
        $('projectTitle').textContent = mProjectId ? 'Edit project' : 'New project';
        $('pName').value = p.name || '';
        $('pDescription').value = p.description || '';
        SL.fillSelect($('pStatus'), Page.statusOptions(), p.statusCode);
        SL.fillSelect($('pOwner'), Page.memberOptions('Nobody yet'), p.ownerCode || '');
        SL.open('projectOverlay');
        $('pName').focus();
    }

    async function saveProject() {
        var name = $('pName').value.trim();
        if (!name) { SL.warn('projectWarn', 'A project needs a name.'); return; }
        var body = { name: name, description: $('pDescription').value, statusCode: $('pStatus').value, ownerCode: $('pOwner').value };
        var r = mProjectId ? await SL.put('/api/solutions/projects/' + mProjectId, body)
                           : await SL.post('/api/solutions/' + Page.id + '/projects', body);
        if (!r || !r.ok) { SL.warn('projectWarn', SL.failText(r, 'The project could not be saved.')); return; }
        SL.close('projectOverlay');
        await Page.reload();
        Page.go('overview');
    }

    async function deleteProject(id) {
        var p = Page.projects.filter(function (x) { return x.id === id; })[0];
        if (!p) return;
        var question = 'Delete the project "' + p.name + '"?\n\nThis also deletes its ' + p.itemCount +
            (p.itemCount === 1 ? ' item' : ' items') + '. Log entries and notes are kept.\n\nThis cannot be undone.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/projects/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The project could not be deleted.')); return; }
        await Page.reload();
        Page.go('overview');
    }

    // ---- wire + boot --------------------------------------------------------------

    function wire() {
        Array.prototype.forEach.call(document.querySelectorAll('.sl-tab'), function (t) {
            t.addEventListener('click', function () { Page.go(t.getAttribute('data-tab')); });
        });
        document.body.classList.add('sl-arrangeable');
        Array.prototype.forEach.call(document.querySelectorAll('[data-arrange]'), function (b) {
            b.addEventListener('click', function () { Page.setArranging(!Page.arranging); });
        });
        $('btnEditSolution').addEventListener('click', openSolution);
        $('btnShare').addEventListener('click', openShare);
        $('btnProjExpand').addEventListener('click', function () { foldAllProjects(false); });
        $('btnProjCollapse').addEventListener('click', function () { foldAllProjects(true); });
        $('fProjOpen').addEventListener('change', function () { setOpenOnly($('fProjOpen').checked); drawOverview(); });
        $('btnProjView').addEventListener('click', function () {
            mRows = !mRows;
            try { localStorage.setItem('sl.projects.rows', mRows ? '1' : '0'); } catch (e) { /* private mode */ }
            drawOverview();
        });
        $('btnBrief').addEventListener('click', briefFromBand);
        slimHeader();
        $('sbCopyKey').addEventListener('click', function () { copyBrief(true); });
        $('sbCopyPlain').addEventListener('click', function () { copyBrief(false); });
        $('sbClose').addEventListener('click', closeBrief);
        $('shareClose').addEventListener('click', function () { SL.close('shareOverlay'); if (Page.active === 'overview') Page.go('overview'); });
        $('solutionCancel').addEventListener('click', function () { SL.close('solutionOverlay'); });
        $('solutionSave').addEventListener('click', function () { SL.guard($('solutionSave'), saveSolution); });
        $('btnArchive').addEventListener('click', toggleArchive);
        $('btnDeleteSolution').addEventListener('click', deleteSolution);
        $('btnAddProject').addEventListener('click', function () { openProject(0); });
        $('projectCancel').addEventListener('click', function () { SL.close('projectOverlay'); });
        $('projectSave').addEventListener('click', function () { SL.guard($('projectSave'), saveProject); });
        $('btnPrint').addEventListener('click', function (e) {
            e.preventDefault();
            // Overview and Items print the formatted REPORT (whole solution, or the project the Items
            // filter names); the other tabs print what they are showing. All through the Leonardo Print Menu.
            if (Page.active === 'overview') { Page.printReport(0); return; }
            if (Page.active === 'items') { Page.printReport(+$('fItemProject').value || 0); return; }
            // The Testing tab prints its checklist as a clean table (ticks are buttons, which the page print strips).
            if (Page.tabs[Page.active] && Page.tabs[Page.active].print) { Page.tabs[Page.active].print(); return; }
            var names = { overview: 'Overview', items: 'Items', log: 'Log', notes: 'Notes', terms: 'Terms', tests: 'Testing', qa: 'QA', config: 'Configuration', grid: 'Configuration II', files: 'Files', conversations: 'Conversations', web: 'Web', search: 'Search' };
            window.Picasso.printThisPage({
                selector: '#pane-' + Page.active,
                title: (Page.solution ? Page.solution.name : 'Solution') + ' - ' + names[Page.active],
                stripSelector: 'button, .tools, .actions, .foot, .sl-compose, script, style'
            });
        });
    }

    /** A link that names only an item or a note: ask the server where it lives. */
    async function resolveDeepLink() {
        var id = +SL.param('id') || 0, item = +SL.param('item') || 0, note = +SL.param('note') || +SL.param('doc') || 0, testPlan = +SL.param('testPlan') || 0,
            configProperty = +SL.param('configProperty') || 0, term = +SL.param('term') || 0, gridProperty = +SL.param('gridProperty') || 0,
            file = +SL.param('file') || 0;
        if (!id && item) {
            var ri = await SL.get('/api/solutions/items/' + item + '/locate');
            if (ri && ri.ok) id = ri.data.solutionId;
        }
        if (!id && note) {
            var rd = await SL.get('/api/solutions/notes/' + note + '/locate');
            if (rd && rd.ok) id = rd.data.solutionId;
        }
        var testPlanTab = 'tests';
        if (testPlan) {
            // Always asked, even with ?id=: a QA checklist opens on the QA tab (2026-10-15).
            var rt = await SL.get('/api/solutions/test-plans/' + testPlan);
            if (rt && rt.ok) { if (!id) id = rt.data.solutionId; if (rt.data.kind === 'qa') testPlanTab = 'qa'; }
        }
        if (!id && configProperty) {
            var rc = await SL.get('/api/solutions/config/properties/' + configProperty + '/locate');
            if (rc && rc.ok) id = rc.data.solutionId;
        }
        if (!id && gridProperty) {
            var rg = await SL.get('/api/solutions/config-grid/properties/' + gridProperty + '/locate');
            if (rg && rg.ok) id = rg.data.solutionId;
        }
        if (!id && term) {
            var rm = await SL.get('/api/solutions/terms/' + term + '/locate');
            if (rm && rm.ok) id = rm.data.solutionId;
        }
        if (!id && file) {
            var rf = await SL.get('/api/solutions/files/' + file);
            if (rf && rf.ok) id = rf.data.solutionId;
        }
        return { id: id, item: item, note: note, term: term, file: file, testPlan: testPlan, testPlanTab: testPlanTab, configProperty: configProperty, gridProperty: gridProperty, conv: +SL.param('conv') || 0, tab: SL.param('tab') };
    }

    (async function () {
        await window.LWAuth.ensureAuth();
        wire();
        var link = await resolveDeepLink();
        Page.id = link.id;
        await Page.loadLookups();
        if (!Page.id || !(await Page.reload())) {
            $('pane-overview').innerHTML = '<div class="sl-empty">That solution no longer exists. ' +
                '<a href="/solutions/solutions.html" data-testid="solution-missing-link">All solutions</a></div>';
            $('toolbar-overview').hidden = true;
            return;
        }
        Object.keys(Page.tabs).forEach(function (n) { if (Page.tabs[n].init) Page.tabs[n].init(); });

        var remembered = '';
        try { remembered = sessionStorage.getItem('sl.tab.' + Page.id) || ''; } catch (e) { /* private mode */ }
        if (link.item)      { Page.go('items'); Page.tabs.items.flash(link.item); }
        else if (link.note) { Page.go('notes'); Page.tabs.notes.openNote(link.note); }
        else if (link.term) { Page.go('terms'); Page.tabs.terms.openTerm(link.term); }
        else if (link.file) { Page.go('files'); Page.tabs.files.openFile(link.file); }
        else if (link.testPlan) { Page.tabs[link.testPlanTab].choosePlan(link.testPlan); Page.go(link.testPlanTab); }
        else if (link.configProperty) { Page.tabs.config.flash(link.configProperty); Page.go('config'); }
        else if (link.gridProperty) { Page.tabs.grid.flash(link.gridProperty); Page.go('grid'); }
        else if (link.conv) { Page.go('conversations'); Page.tabs.conversations.openThread(link.conv); }
        else Page.go(link.tab || remembered || 'overview');
    })();
})();
