/* entity-phones.js -- the organized Phones tab on every record page (2026-10-15).
 *
 * The Phones tab works like the Web tab (entity-web.js, Story 3525), the Files tab and the
 * Notes tab: every phone has a PHONE TYPE (Home, Work, Cell, Business, Fax ... -- one shared,
 * colored, ordered list managed like file categories, see files/file-kit.js), can be flagged
 * Important, and the tab groups by type or not at all, sorts by name, number, type, date
 * added or Tony's own order, and searches (a typed number matches on its digits) -- all of it
 * in SQL (stp_EntityPhone_ListFiled / _CountsFiled), so page two continues page one.
 *
 * Every phone has a type, so there is no Staging band, and deleting a type that is in use
 * moves its phones to another type first.
 *
 * Arrange (the up-down arrows) drags the type groups into order AND the phones inside a
 * group; the phone order is per record (stp_EntityPhone_Reorder) and shows under the My order
 * sort, which Arrange switches to.
 *
 * Remembered in the DATABASE (PicassoViewState, never localStorage): Group by, the sort and
 * its direction, the rolled-up tools, which groups are folded and which cards are folded.
 *
 * Wiring: entity-detail.js's loader asks EntityPhones.firstPageUrl() for the Phones job and
 * hands the JSON to ED.renderPhones, which calls EntityPhones.render(). Adding, editing,
 * linking, removing and Copy stay in entity-detail.js.
 */
(function () {
    'use strict';
    if (window.EntityPhones) return;

    var PAGE_SIZE = 100;
    var VS_PREFIX = 'leo.phones.';
    var VS_GROUP  = 'leo.phones.group';
    var VS_SORT   = 'leo.phones.sort';
    var VS_DESC   = 'leo.phones.desc';
    var VS_TOOLS  = 'leo.phones.tools';

    var GROUPS = [
        { key: 'type', label: 'Type' },
        { key: 'none', label: 'All' }
    ];
    var SORTS = [
        { key: 'name',   label: 'Name' },
        { key: 'number', label: 'Number' },
        { key: 'type',   label: 'Type' },
        { key: 'added',  label: 'Date added' },
        { key: 'order',  label: 'My order' }
    ];

    var _search = {};   // entityId -> search text (per visit, never persisted)
    var _kit = null;
    var _vsReady = null;

    // ---- small helpers ---------------------------------------------------------

    function F() { return window.FileKit; }
    function E() { return window.LWED; }
    function esc(s) { return F().esc(s); }
    function caret(open) { return open ? '&#9662;' : '&#9656;'; }
    function toast(t) { if (window.Picasso && Picasso.toast) Picasso.toast(t); }
    function plural(n, one, many) { return n + ' ' + (n === 1 ? one : (many || one + 's')); }

    /** The phone-type kit: the file-category chips, picker and manager, pointed at phone types. */
    function K() {
        if (_kit) return _kit;
        _kit = F().kit({
            api: '/api/phone-types',
            noun: 'type', nounPlural: 'phone types',
            title: 'Phone types', pickTitle: 'Change type',
            examples: 'Cell, Office, After hours', maxLen: 50,
            usesLabel: 'Phones of this type', usesNoun: 'phones',
            moveOnDelete: true,
            deleteMessage: function (c, target) {
                var n = c.useCount || 0;
                return 'Delete the phone type "' + c.name + '"?\n\n'
                    + (n > 0 && target
                        ? n + ' phone' + (n === 1 ? '' : 's') + ' of this type, on every record, become'
                          + (n === 1 ? 's' : '') + ' "' + target.name + '". No phone is removed.\n\n'
                        : 'No phones use it.\n\n')
                    + 'This cannot be undone.';
            }
        });
        return _kit;
    }

    // ---- remembered state (database, through PicassoViewState) ---------------------

    /** Loads picasso-view-state.js once and pulls this tab's keys from the server. */
    function viewStateReady() {
        if (_vsReady) return _vsReady;
        _vsReady = new Promise(function (resolve) {
            function sync() {
                Promise.resolve(window.PicassoViewState.sync(VS_PREFIX))
                    .catch(function () { return false; })
                    .then(function () { resolve(true); });
            }
            if (window.PicassoViewState) { sync(); return; }
            var s = document.createElement('script');
            s.src = '/picasso-view-state.js?v=4';
            s.onload = sync;
            s.onerror = function () { resolve(false); };
            document.head.appendChild(s);
        });
        return _vsReady;
    }

    function vsGet(k, dflt) {
        var v = window.PicassoViewState ? window.PicassoViewState.get(k) : null;
        return v == null ? dflt : v;
    }
    function vsSet(k, v) { if (window.PicassoViewState) window.PicassoViewState.set(k, v); }

    function prefs() {
        var g = vsGet(VS_GROUP, 'type');
        var s = vsGet(VS_SORT, 'name');
        return {
            group: GROUPS.some(function (x) { return x.key === g; }) ? g : 'type',
            sort:  SORTS.some(function (x) { return x.key === s; }) ? s : 'name',
            desc:  vsGet(VS_DESC, '0') === '1'
        };
    }

    function toolsCollapsed() { return window.Picasso.rollup.isCollapsed(VS_TOOLS); }

    function setToolsCollapsed(st, collapsed) {
        window.Picasso.rollup.set(VS_TOOLS, st.pane, st.pane.querySelector('[data-ep="tools"]'), collapsed);
        drawSummary(st);
    }

    /** Which groups and which cards are folded, remembered per record. Cards keep a state
     *  apart from the groups, so Fold All never makes new cards arrive folded. */
    function bandStateFor(entityId) {
        return window.PicassoGroupState.create({ key: VS_PREFIX + 'bands.' + entityId, defaultOpen: true });
    }
    function cardStateFor(entityId) {
        return window.PicassoGroupState.create({ key: VS_PREFIX + 'cards.' + entityId, defaultOpen: true });
    }

    function listUrl(entityId, offset) {
        var p = prefs();
        var q = _search[entityId] || '';
        return '/api/entities/' + encodeURIComponent(entityId) + '/phones/filed'
             + '?group=' + encodeURIComponent(p.group)
             + '&sort=' + encodeURIComponent(p.sort)
             + '&desc=' + (p.desc ? 'true' : 'false')
             + (q ? '&q=' + encodeURIComponent(q) : '')
             + '&offset=' + (offset || 0)
             + '&size=' + PAGE_SIZE;
    }

    function telOf(p) {
        var d = String(p.phoneNumberInt || '').replace(/\D/g, '');
        if (!d) return '';
        return d.length === 10 ? 'tel:+1' + d : 'tel:' + d;
    }

    // ---- shell -------------------------------------------------------------------

    function shellHtml() {
        var p = prefs();
        var toolsOpen = !toolsCollapsed();
        return ''
            // The head row: one tap rolls every tool below it (Add Phone down to Arrange) up
            // out of the way, remembered. The summary rides in it so the count stays in view.
            + '<button type="button" class="ef-tools-toggle" data-ep="tools" aria-expanded="' + toolsOpen + '"'
            +   ' title="Show or hide the tools" data-testid="entity-phone-tools-toggle">'
            +   '<span class="ef-caret">' + window.Picasso.rollup.caret(!toolsOpen) + '</span>'
            +   '<span class="ef-summary" data-testid="entity-phone-summary"></span>'
            + '</button>'
            + '<div class="ef-tools" data-testid="entity-phone-tools">'
            + '<div class="ed-pane-actions ef-actions">'
            +   '<button type="button" class="ed-btn primary" data-ep="add" data-testid="entity-phone-add-btn">+ Add Phone</button>'
            +   '<button type="button" class="ed-btn" data-ep="link" data-testid="entity-phone-link-btn">+ Link Phone</button>'
            +   '<button type="button" class="ed-btn" data-ep="select" data-testid="entity-phone-select-btn">Select</button>'
            +   '<button type="button" class="ed-btn" data-ep="types" data-testid="entity-phone-types-btn">Types</button>'
            + '</div>'
            + '<input type="search" class="ef-search" placeholder="Search names, numbers, types"'
            +   ' autocomplete="off" data-testid="entity-phone-search">'
            + '<div class="ef-controls">'
            +   '<div class="ef-seg" role="group" aria-label="Group by" data-testid="entity-phone-group">'
            +     GROUPS.map(function (g) {
                        return '<button type="button" data-group="' + g.key + '"'
                             + (g.key === p.group ? ' class="on" aria-pressed="true"' : ' aria-pressed="false"')
                             + ' data-testid="entity-phone-group-' + g.key + '">' + esc(g.label) + '</button>';
                    }).join('')
            +   '</div>'
            +   '<div class="ef-sort">'
            +     '<select class="ef-sort-sel" aria-label="Sort by" data-testid="entity-phone-sort">'
            +       SORTS.map(function (s) {
                        return '<option value="' + s.key + '"' + (s.key === p.sort ? ' selected' : '') + '>' + esc(s.label) + '</option>';
                    }).join('')
            +     '</select>'
            +     '<button type="button" class="ed-btn ef-dir" data-ep="dir" data-testid="entity-phone-sort-dir"'
            +       ' aria-label="' + (p.desc ? 'Z to A' : 'A to Z') + '">'
            +       (p.desc ? '&#8595;' : '&#8593;') + '</button>'
            +     '<button type="button" class="ed-btn ef-foldall" data-ep="fold-all" hidden'
            +       ' data-testid="entity-phone-fold-all"></button>'
            +     '<button type="button" class="ed-btn ef-arrange" data-ep="arrange" hidden aria-pressed="false"'
            +       ' aria-label="Arrange the groups and the phones in them" title="Arrange"'
            +       ' data-testid="entity-phone-arrange">&#8645;</button>'
            +   '</div>'
            + '</div>'
            + '</div>'
            + '<div class="ef-list" data-testid="entity-phone-list"></div>'
            + '<div class="ef-more" hidden><button type="button" class="ed-btn" data-ep="more" data-testid="entity-phone-more-btn">Load more</button></div>'
            + '<div class="ef-selbar" hidden data-testid="entity-phone-selbar">'
            +   '<span class="ef-selcount"></span>'
            +   '<button type="button" class="ed-btn primary" data-ep="sel-type" data-testid="entity-phone-sel-type-btn">Change type</button>'
            +   '<button type="button" class="ed-btn" data-ep="sel-cancel" data-testid="entity-phone-sel-cancel-btn">Cancel</button>'
            + '</div>';
    }

    function ensureShell(pane, entityId) {
        if (pane._ep && pane._ep.entityId === entityId && pane.querySelector('.ep-pane-marker')) return pane._ep;
        pane.innerHTML = '<span class="ep-pane-marker" hidden></span>' + shellHtml();
        pane.classList.add('ef-pane', 'ep-pane');
        pane.classList.toggle('ef-tools-collapsed', toolsCollapsed());
        var st = {
            entityId: entityId, pane: pane, refresh: null,
            rows: [], groups: [], total: 0, important: 0, hasMore: false,
            selecting: false, selected: new Set(), arranging: false,
            bandState: bandStateFor(entityId), cardState: cardStateFor(entityId)
        };
        pane._ep = st;
        pane.querySelector('.ef-search').value = _search[entityId] || '';
        wireShell(st);
        return st;
    }

    // ---- render ---------------------------------------------------------------------

    /** Called by ED.renderPhones with the first page ({rows, groups, total, hasMore}). */
    function render(pane, data, entityId, refresh) {
        entityId = parseInt(entityId, 10) || 0;
        return viewStateReady().then(function () {
            // The first page may have been asked for before the remembered Group by / sort
            // arrived from the server; if they differ, read it again the remembered way.
            var p = prefs();
            if (data && !pane._epRetried && (data.groupBy !== p.group || data.sort !== p.sort || !!data.desc !== p.desc)) {
                pane._epRetried = true;
                if (refresh) { refresh(); return; }
            }
            var st = ensureShell(pane, entityId);
            st.refresh = refresh;
            st.rows    = (data && data.rows) || [];
            st.groups  = (data && data.groups) || [];
            st.total   = (data && data.total) || 0;
            st.important = (data && data.important) || 0;
            st.hasMore = !!(data && data.hasMore);
            var present = new Set(st.rows.map(function (r) { return r.entityPhoneXrefId; }));
            st.selected.forEach(function (id) { if (!present.has(id)) st.selected.delete(id); });
            drawList(st);
        });
    }

    function groupMeta(st, key) {
        for (var i = 0; i < st.groups.length; i++) if (st.groups[i].key === key) return st.groups[i];
        return null;
    }

    function drawList(st) {
        var pane = st.pane;
        var p = prefs();
        var q = _search[st.entityId] || '';

        drawSummary(st);

        var host = pane.querySelector('.ef-list');
        if (!st.rows.length) {
            host.innerHTML = '<div class="ed-empty">' + (q ? 'No phones match that search.' : 'No phone numbers on file.') + '</div>';
        } else {
            var html = '';
            var curKey = null;
            st.rows.forEach(function (ph, idx) {
                if (ph.groupKey !== curKey) {
                    if (curKey !== null) html += '</div></section>';
                    curKey = ph.groupKey;
                    html += bandHead(st, ph, p.group);
                }
                html += cardHtml(st, ph, idx);
            });
            if (curKey !== null) html += '</div></section>';
            host.innerHTML = html;
        }

        pane.querySelector('.ef-more').hidden = !st.hasMore;
        syncFoldAll(st);
        pane.classList.toggle('ef-selecting', st.selecting);
        pane.querySelector('[data-ep="select"]').classList.toggle('on', st.selecting);
        drawSelbar(st);
        if (E() && E().attentionTabDrew) E().attentionTabDrew('phone');
    }

    /** Summary line: counts come from SQL. While the tools are rolled up the search box is
     *  out of sight, so a live search is named here. */
    function drawSummary(st) {
        var q = _search[st.entityId] || '';
        var loaded = st.rows.length;
        var summary = st.total === 0
            ? (q ? 'No phones match' : 'No phones yet')
            : plural(st.total, 'phone') + (loaded < st.total ? ' (' + loaded + ' shown)' : '')
              + (st.important ? ' | ' + st.important + ' important' : '');
        if (q && toolsCollapsed()) summary += ' | searching "' + q + '"';
        st.pane.querySelector('.ef-summary').textContent = summary;
    }

    function bandHead(st, ph, group) {
        var meta = groupMeta(st, ph.groupKey) || { count: 0 };
        var loadedInGroup = st.rows.filter(function (r) { return r.groupKey === ph.groupKey; }).length;
        var countTxt = meta.count > loadedInGroup ? (loadedInGroup + ' of ' + meta.count) : String(meta.count || loadedInGroup);
        var collapsed = !st.bandState.isOpen(ph.groupKey);
        var color = group === 'type' ? F().colorOf(ph.groupColor) : '';
        var movable = st.arranging && group === 'type' && ph.phoneTypeId;
        return '<section class="ef-band' + (collapsed ? ' collapsed' : '') + '"'
             +   ' data-group="' + esc(ph.groupKey) + '"'
             +   (group === 'type' && ph.phoneTypeId ? ' data-type="' + esc(ph.phoneTypeId) + '"' : '')
             +   ' data-testid="entity-phone-band">'
             +   '<div class="ef-band-head">'
             +     (movable
                      ? '<span class="ef-grip" data-ep-grip title="Drag to move this group up or down"'
                        + ' aria-label="Drag to move ' + esc(ph.groupLabel) + '" data-testid="entity-phone-band-grip">&#8942;&#8942;</span>'
                      : '')
             +     (st.selecting
                      ? '<input type="checkbox" class="ef-band-check" data-ep="band-check" aria-label="Select every phone in '
                        + esc(ph.groupLabel) + '" data-testid="entity-phone-band-check">'
                      : '')
             +     '<button type="button" class="ef-band-toggle" data-ep="toggle" aria-expanded="' + (!collapsed) + '"'
             +       ' data-testid="entity-phone-band-toggle">'
             +       '<span class="ef-caret">' + caret(!collapsed) + '</span>'
             +       (color ? '<span class="ef-band-dot" style="background:' + esc(color) + '"></span>' : '')
             +       '<span class="ef-band-label">' + esc(ph.groupLabel) + '</span>'
             +       '<span class="ef-band-count">' + esc(countTxt) + '</span>'
             +     '</button>'
             +   '</div>'
             +   '<div class="ef-band-body">';
    }

    function cardHtml(st, ph, idx) {
        var name = ph.phoneNumberName || '(no description)';
        var display = ph.phoneNumberDisplay || String(ph.phoneNumberInt || '');
        var tel = telOf(ph);
        var ext = ph.extension && Number(ph.extension) > 0 ? ' ext. ' + esc(ph.extension) : '';
        var sel = st.selected.has(ph.entityPhoneXrefId);
        var open = st.cardState.isOpen(String(ph.entityPhoneXrefId));
        var type = { id: ph.phoneTypeId, name: ph.phoneType || 'Phone', color: ph.typeColor };
        var dates = E().formatDateRange(ph.effectiveDate, ph.endDate);
        return '<div class="ed-card ef-card ep-card' + (open ? '' : ' collapsed') + (sel ? ' ef-sel' : '')
             +     (ph.important ? ' ef-important' : '') + '"'
             +   ' data-xrefid="' + esc(ph.entityPhoneXrefId) + '" data-idx="' + idx + '" data-testid="entity-phone-row">'
             +   '<div class="ef-top">'
             +     (st.arranging && prefs().sort === 'order'
                      ? '<span class="ef-grip" data-ep-card-grip title="Drag to move this phone up or down"'
                        + ' aria-label="Drag to move ' + esc(name) + '" data-testid="entity-phone-card-grip">&#8942;&#8942;</span>'
                      : '')
             +     (st.selecting
                      ? '<input type="checkbox" class="ef-check" data-ep="check"' + (sel ? ' checked' : '')
                        + ' aria-label="Select ' + esc(name) + '" data-testid="entity-phone-check">'
                      : '')
             +     '<button type="button" class="ep-fold" data-ep="card" aria-expanded="' + open + '"'
             +       ' aria-label="' + (open ? 'Fold ' : 'Unfold ') + esc(name) + '" data-testid="entity-phone-card-toggle">'
             +       '<span class="ef-caret">' + caret(open) + '</span></button>'
             +     '<span class="ef-name">' + esc(name) + '</span>'
             +     (display
                      ? (tel ? '<a class="ep-number" href="' + esc(tel) + '" data-testid="entity-phone-call-link">' + esc(display) + ext + '</a>'
                             : '<span class="ep-number">' + esc(display) + ext + '</span>')
                      : '')
             +     '<button type="button" class="ef-flag' + (ph.important ? ' on' : '') + '" data-ep="flag"'
             +       ' aria-pressed="' + (ph.important ? 'true' : 'false') + '"'
             +       ' aria-label="' + (ph.important ? 'Important -- tap to clear' : 'Mark important') + '"'
             +       ' title="Important" data-testid="entity-phone-flag-btn">&#9873;</button>'
             +   '</div>'
             +   '<div class="ef-meta">'
             +     F().categoryChip(type, { act: 'retype', testid: 'entity-phone-type-chip' })
             +     (dates ? '<span class="ep-dates">' + esc(dates) + '</span>' : '')
             +   '</div>'
             +   (ph.notesPreview ? '<div class="notes ep-notes">' + esc(ph.notesPreview) + '</div>' : '')
             +   (st.selecting ? '' :
                  '<div class="ed-card-actions">'
             +     E().pinButton('phone', ph.entityPhoneXrefId, display || '(no number)',
                                 [ph.phoneType, ph.phoneNumberName].filter(Boolean).join(' | '))
             +     (display ? '<button type="button" class="ed-btn" data-ep="copy" data-testid="entity-phone-copy-btn">Copy</button>' : '')
             +     '<button type="button" class="ed-btn" data-ep="edit" data-testid="entity-phone-edit-btn">Edit</button>'
             +     '<button type="button" class="ed-btn danger" data-ep="del" data-testid="entity-phone-del-btn">Delete</button>'
             +   '</div>')
             + '</div>';
    }

    function drawSelbar(st) {
        var bar = st.pane.querySelector('.ef-selbar');
        bar.hidden = !st.selecting;
        if (!st.selecting) return;
        var n = st.selected.size;
        bar.querySelector('.ef-selcount').textContent = n
            ? plural(n, 'phone') + ' selected' : 'Tap phones to select them';
        bar.querySelector('[data-ep="sel-type"]').disabled = n === 0;
    }

    // ---- actions ------------------------------------------------------------------------

    function rowOf(st, el) {
        var card = el.closest('.ep-card');
        if (!card) return null;
        return st.rows[parseInt(card.getAttribute('data-idx'), 10)] || null;
    }

    function reload(st) { if (st.refresh) st.refresh(); }

    function usedTypeIds(st) {
        return st.groups.filter(function (g) { return g.typeId; })
            .sort(function (a, b) { return b.count - a.count; })
            .map(function (g) { return g.typeId; });
    }

    /** Give the listed phones the type pickCategory resolved to. */
    function setType(st, xrefIds, choice) {
        if (!choice || !xrefIds.length || choice.staging) return Promise.resolve(false);
        var body = { xrefIds: xrefIds };
        if (choice.categoryId) body.typeId = choice.categoryId; else body.typeName = choice.categoryName;
        return Promise.resolve(window.LWAuth.apiPost('/api/entities/' + st.entityId + '/phones/type', body)).then(function (r) {
            if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Changing the phone type failed')); return false; }
            K().invalidate();
            toast(plural(r.data.changed, 'phone') + ' changed');
            reload(st);
            return true;
        });
    }

    function retype(st, ph) {
        K().pickCategory({
            title: 'Type of "' + (ph.phoneNumberName || ph.phoneNumberDisplay || 'this phone') + '"',
            currentId: ph.phoneTypeId || 0,
            preferIds: usedTypeIds(st)
        }).then(function (choice) {
            if (choice && choice.categoryId === ph.phoneTypeId) return;
            setType(st, [ph.entityPhoneXrefId], choice);
        });
    }

    function toggleFlag(st, ph, btn) {
        var next = !ph.important;
        btn.classList.toggle('on', next);
        Promise.resolve(window.LWAuth.apiPost('/api/entities/' + st.entityId + '/phones/' + ph.entityPhoneXrefId + '/important',
            { important: next }, 'PUT')).then(function (r) {
            if (!r || !r.ok) { btn.classList.toggle('on', !next); alert(window.LWAuth.errorText(r, 'Changing Important failed')); return; }
            toast(next ? 'Marked important' : 'No longer marked important');
            reload(st);
        });
    }

    function setBandCollapsed(band, collapsed) {
        band.classList.toggle('collapsed', collapsed);
        var t = band.querySelector('[data-ep="toggle"]');
        if (!t) return;
        t.setAttribute('aria-expanded', String(!collapsed));
        t.querySelector('.ef-caret').innerHTML = caret(!collapsed);
    }

    function toggleCard(st, card) {
        var open = st.cardState.toggle(card.getAttribute('data-xrefid'));
        card.classList.toggle('collapsed', !open);
        var b = card.querySelector('[data-ep="card"]');
        if (!b) return;
        b.setAttribute('aria-expanded', String(open));
        b.querySelector('.ef-caret').innerHTML = caret(open);
    }

    /** The Arrange button: when grouping by type with two or more type groups, or when any
     *  group holds two or more phones. */
    function syncArrange(st) {
        var btn = st.pane.querySelector('[data-ep="arrange"]');
        var usable = !st.selecting
                  && ((prefs().group === 'type' && st.pane.querySelectorAll('.ef-band[data-type]').length >= 2)
                      || Array.prototype.some.call(st.pane.querySelectorAll('.ef-band-body'), function (b) {
                             return b.querySelectorAll('.ep-card').length >= 2;
                         }));
        if (!usable && st.arranging) st.arranging = false;
        btn.hidden = !usable;
        btn.classList.toggle('on', !!st.arranging);
        btn.setAttribute('aria-pressed', st.arranging ? 'true' : 'false');
        st.pane.classList.toggle('ef-arranging', !!st.arranging);
        st.pane.querySelectorAll('[data-ep-grip]').forEach(function (g) {
            wireDrag(st, g, '.ef-band', '.ef-band[data-type]', function (el) { return parseInt(el.getAttribute('data-type'), 10); },
                     function (ids) { saveGroupOrder(st, ids); });
        });
        st.pane.querySelectorAll('[data-ep-card-grip]').forEach(function (g) {
            wireDrag(st, g, '.ep-card', '.ep-card', function (el) { return parseInt(el.getAttribute('data-xrefid'), 10); },
                     function (ids) { saveCardOrder(st, ids); });
        });
    }

    // Drag to reorder (Arrange on) -- the same pointer-event pattern as the Web, Files and
    // Notes tabs: listeners on the document, not pointer capture, so touch works. A group
    // moves among the groups; a card never leaves its own group.
    function wireDrag(st, grip, itemSel, siblingSel, idOf, save) {
        if (grip._epWired) return;
        grip._epWired = true;
        grip.addEventListener('click', function (e) { e.stopPropagation(); });
        grip.addEventListener('pointerdown', function (down) {
            var item = grip.closest(itemSel);
            var host = item ? item.parentNode : null;
            if (!item || !host || st.savingOrder) return;
            down.preventDefault();
            down.stopPropagation();
            item.classList.add('dragging');
            function order() {
                return Array.prototype.map.call(host.querySelectorAll(':scope > ' + siblingSel), idOf);
            }
            var before = order().join(',');
            var scroller = st.pane.closest('.ed-body') || document.scrollingElement;
            function onMove(ev) {
                if (scroller && scroller.getBoundingClientRect) {
                    var box = scroller.getBoundingClientRect();
                    if (ev.clientY < box.top + 48) scroller.scrollTop -= 14;
                    if (ev.clientY > box.bottom - 48) scroller.scrollTop += 14;
                }
                var under = document.elementFromPoint(ev.clientX, ev.clientY);
                var over = under && under.closest ? under.closest(siblingSel) : null;
                if (!over || over === item || over.parentNode !== host) return;
                var r = over.getBoundingClientRect();
                var ref = ev.clientY > r.top + r.height / 2 ? over.nextSibling : over;
                if (ref === item || item.nextSibling === ref) return;
                host.insertBefore(item, ref);
            }
            function onUp() {
                document.removeEventListener('pointermove', onMove);
                document.removeEventListener('pointerup', onUp);
                document.removeEventListener('pointercancel', onUp);
                item.classList.remove('dragging');
                var ids = order();
                if (ids.join(',') !== before) save(ids);
            }
            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', onUp);
            document.addEventListener('pointercancel', onUp);
        });
    }

    /** Save the new order of one group's phones on this record. */
    function saveCardOrder(st, xrefIds) {
        st.savingOrder = true;
        Promise.resolve(window.LWAuth.apiPost('/api/entities/' + st.entityId + '/phones/reorder', { xrefIds: xrefIds }))
            .then(function (r) {
                st.savingOrder = false;
                if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Saving the phone order failed')); reload(st); return; }
                toast('Order saved');
                reload(st);
            });
    }

    /** Merge this record's new group order into the shared type order and save it. */
    function saveGroupOrder(st, visibleIds) {
        st.savingOrder = true;
        K().loadCategories(true).then(function (types) {
            var shown = {};
            visibleIds.forEach(function (id) { shown[id] = true; });
            var next = 0;
            var all = types.map(function (t) { return shown[t.id] ? visibleIds[next++] : t.id; });
            return Promise.resolve(window.LWAuth.apiPost('/api/phone-types/reorder', { ids: all }));
        }).then(function (r) {
            st.savingOrder = false;
            if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Saving the group order failed')); reload(st); return; }
            K().invalidate();
            toast('Group order saved');
            reload(st);
        });
    }

    function syncFoldAll(st) {
        syncArrange(st);
        var btn = st.pane.querySelector('[data-ep="fold-all"]');
        var bands = st.pane.querySelectorAll('.ef-band');
        btn.hidden = bands.length < 2;
        var anyOpen = Array.prototype.some.call(bands, function (x) { return !x.classList.contains('collapsed'); });
        btn.innerHTML = anyOpen ? '&#9656;&#9656;' : '&#9662;&#9662;';
        btn.setAttribute('aria-label', anyOpen ? 'Collapse all groups' : 'Expand all groups');
        btn.title = anyOpen ? 'Collapse all' : 'Expand all';
    }

    function syncControls(st) {
        var p = prefs();
        st.pane.querySelectorAll('.ef-seg [data-group]').forEach(function (b) {
            var on = b.getAttribute('data-group') === p.group;
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
        var sel = st.pane.querySelector('.ef-sort-sel');
        if (sel.value !== p.sort) {
            sel.value = p.sort;
            if (window.PicassoSelect && window.PicassoSelect.refresh) window.PicassoSelect.refresh(sel);
        }
        var dir = st.pane.querySelector('[data-ep="dir"]');
        dir.innerHTML = p.desc ? '&#8595;' : '&#8593;';
        dir.setAttribute('aria-label', p.desc ? 'Z to A' : 'A to Z');
    }

    function loadMore(st) {
        var btn = st.pane.querySelector('[data-ep="more"]');
        btn.disabled = true;
        Promise.resolve(window.LWAuth.apiGet(listUrl(st.entityId, st.rows.length))).then(function (r) {
            btn.disabled = false;
            if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Loading more phones failed')); return; }
            st.rows = st.rows.concat(r.data.rows || []);
            st.hasMore = !!r.data.hasMore;
            drawList(st);
        });
    }

    function setSelecting(st, on) {
        st.selecting = on;
        if (!on) st.selected.clear();
        drawList(st);
    }

    function wireShell(st) {
        var pane = st.pane;
        var searchTimer = null;

        pane.querySelector('.ef-search').addEventListener('input', function (e) {
            var v = e.target.value.trim();
            clearTimeout(searchTimer);
            searchTimer = setTimeout(function () {
                if ((_search[st.entityId] || '') === v) return;
                _search[st.entityId] = v;
                reload(st);
            }, 280);
        });

        pane.querySelector('.ef-sort-sel').addEventListener('change', function (e) {
            vsSet(VS_SORT, e.target.value);
            if (e.target.value !== 'order') st.arranging = false;
            reload(st);
        });

        pane.addEventListener('change', function (e) {
            var t = e.target;
            if (t.matches('[data-ep="check"]')) {
                var ph = rowOf(st, t);
                if (!ph) return;
                if (t.checked) st.selected.add(ph.entityPhoneXrefId); else st.selected.delete(ph.entityPhoneXrefId);
                t.closest('.ep-card').classList.toggle('ef-sel', t.checked);
                drawSelbar(st);
            } else if (t.matches('[data-ep="band-check"]')) {
                var key = t.closest('.ef-band').getAttribute('data-group');
                st.rows.forEach(function (r) {
                    if (r.groupKey !== key) return;
                    if (t.checked) st.selected.add(r.entityPhoneXrefId); else st.selected.delete(r.entityPhoneXrefId);
                });
                drawList(st);
            }
        });

        pane.addEventListener('click', function (e) {
            var t = e.target;

            // Select mode: a tap anywhere on a card toggles it.
            if (st.selecting && !t.closest('button, input, a')) {
                var card = t.closest('.ep-card');
                if (card) {
                    var sr = rowOf(st, card);
                    if (sr) {
                        if (st.selected.has(sr.entityPhoneXrefId)) st.selected.delete(sr.entityPhoneXrefId);
                        else st.selected.add(sr.entityPhoneXrefId);
                        drawList(st);
                    }
                    return;
                }
            }

            // A tap on a card's name folds or unfolds it, the same as its caret.
            if (!st.selecting && !st.arranging && t.closest('.ep-card .ef-name')) {
                toggleCard(st, t.closest('.ep-card'));
                return;
            }

            var b = t.closest('[data-ep], [data-act="retype"]');
            if (!b || !pane.contains(b)) return;
            var act = b.getAttribute('data-ep') || b.getAttribute('data-act');
            var ph = rowOf(st, b);
            var done = function () { reload(st); };

            switch (act) {
                case 'add':    E().editPhone(st.entityId, null, done); break;
                case 'link':   E().linkPhone(st.entityId, done); break;
                case 'tools':  setToolsCollapsed(st, !toolsCollapsed()); break;
                case 'select': setSelecting(st, !st.selecting); break;
                case 'types':  K().manageCategories({ onChange: done }); break;
                case 'dir':
                    vsSet(VS_DESC, prefs().desc ? '0' : '1');
                    syncControls(st);
                    reload(st);
                    break;
                case 'more':    loadMore(st); break;
                case 'arrange':
                    st.arranging = !st.arranging;
                    // Arranging the phones means seeing My order, first to last.
                    if (st.arranging && (prefs().sort !== 'order' || prefs().desc)) {
                        vsSet(VS_SORT, 'order');
                        vsSet(VS_DESC, '0');
                        syncControls(st);
                        reload(st);
                        break;
                    }
                    drawList(st);
                    break;
                case 'fold-all': {
                    var bands = pane.querySelectorAll('.ef-band');
                    var foldAll = Array.prototype.some.call(bands, function (x) { return !x.classList.contains('collapsed'); });
                    if (foldAll) st.bandState.collapseAll(); else st.bandState.expandAll();
                    Array.prototype.forEach.call(bands, function (x) { setBandCollapsed(x, foldAll); });
                    syncFoldAll(st);
                    break;
                }
                case 'toggle': {
                    var band = b.closest('.ef-band');
                    var nowCollapsed = !st.bandState.toggle(band.getAttribute('data-group'));
                    setBandCollapsed(band, nowCollapsed);
                    syncFoldAll(st);
                    break;
                }
                case 'card':       toggleCard(st, b.closest('.ep-card')); break;
                case 'sel-type':
                    K().pickCategory({
                        title: 'Type for ' + plural(st.selected.size, 'selected phone'),
                        preferIds: usedTypeIds(st)
                    }).then(function (choice) {
                        return setType(st, Array.from(st.selected), choice);
                    }).then(function (ok) { if (ok) setSelecting(st, false); });
                    break;
                case 'sel-cancel': setSelecting(st, false); break;
                case 'retype':     if (ph) retype(st, ph); break;
                case 'flag':       if (ph) toggleFlag(st, ph, b); break;
                case 'copy':       if (ph) E().copyPhone(ph); break;
                case 'edit':       if (ph) E().editPhone(st.entityId, ph, done); break;
                case 'del':        if (ph) E().deletePhone(st.entityId, ph, done); break;
            }
        });

        pane.querySelectorAll('.ef-seg [data-group]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                vsSet(VS_GROUP, btn.getAttribute('data-group'));
                syncControls(st);
                reload(st);
            });
        });
    }

    // Pull the remembered Group by / sort as early as possible, so the first page the
    // loader asks for is usually already the remembered one.
    viewStateReady();

    window.EntityPhones = {
        firstPageUrl: function (entityId) { return listUrl(entityId, 0); },
        render: render,
        typeKit: K
    };
})();
