/* entity-web.js -- the organized Web tab on every record page (Story 3525).
 *
 * The Web tab (web addresses: websites, email addresses) works exactly like the Files tab
 * (entity-files.js, Story 3520) and the Notes tab (entity-notes.js, Story 3524): every web
 * address has a WEBSITE TYPE (Business, Email, Blog, Shopping ... -- one shared, coloured,
 * ordered list managed like file categories, see files/file-kit.js), can be flagged
 * Important, and the tab groups by type, by kind (website / email address) or not at all,
 * sorts by name, address, type or date added, and searches -- all of it in SQL
 * (stp_EntityWebAddress_ListFiled / _CountsFiled), so page two continues page one.
 *
 * Every address has a type ("Business" is the default), so there is no Staging band, and
 * deleting a type that is in use moves its addresses to another type first.
 *
 * Arrange (the up-down arrows) drags the type groups into order AND the web addresses inside
 * a group; the address order is per record (stp_EntityWebAddress_Reorder) and shows under the
 * My order sort, which Arrange switches to.
 *
 * Wiring: entity-detail.js's loader asks EntityWeb.firstPageUrl() for the Web job and hands
 * the JSON to ED.renderWebAddresses, which calls EntityWeb.render(). Adding, editing,
 * linking, removing, the logins panel and Copy Password stay in entity-detail.js.
 */
(function () {
    'use strict';
    if (window.EntityWeb) return;

    var PAGE_SIZE = 100;
    var LS_GROUP = 'lw.web.group';
    var LS_SORT  = 'lw.web.sort';
    var LS_DESC  = 'lw.web.desc';
    var LS_TOOLS = 'lw.web.tools.collapsed';
    var KIND_EMAIL = 2;   // tblWebAddressType: 1 Website, 2 Email Address, 3 FTP Address

    var GROUPS = [
        { key: 'type', label: 'Type' },
        { key: 'kind', label: 'Kind' },
        { key: 'none', label: 'All' }
    ];
    var SORTS = [
        { key: 'name',    label: 'Name' },
        { key: 'address', label: 'Address' },
        { key: 'type',    label: 'Type' },
        { key: 'added',   label: 'Date added' },
        { key: 'order',   label: 'My order' }
    ];

    var _search = {};   // entityId -> search text (per visit, never persisted)
    var _kit = null;

    // ---- small helpers ---------------------------------------------------------

    function F() { return window.FileKit; }
    function esc(s) { return F().esc(s); }

    /** The website-type kit: the file-category chips, picker and manager, pointed at website types. */
    function K() {
        if (_kit) return _kit;
        _kit = F().kit({
            api: '/api/web-site-types',
            noun: 'type', nounPlural: 'website types',
            title: 'Website types', pickTitle: 'Change type',
            examples: 'Banking, Utilities, Shopping', maxLen: 50,
            usesLabel: 'Web addresses of this type', usesNoun: 'web addresses',
            moveOnDelete: true,
            deleteMessage: function (c, target) {
                var n = c.useCount || 0;
                return 'Delete the website type "' + c.name + '"?\n\n'
                    + (n > 0 && target
                        ? n + ' web address' + (n === 1 ? '' : 'es') + ' of this type, on every record, become'
                          + (n === 1 ? 's' : '') + ' "' + target.name + '". No web address is removed.\n\n'
                        : 'No web addresses use it.\n\n')
                    + 'This cannot be undone.';
            }
        });
        return _kit;
    }

    function lsGet(k, dflt) { try { var v = localStorage.getItem(k); return v == null ? dflt : v; } catch (e) { return dflt; } }
    function lsSet(k, v)    { try { localStorage.setItem(k, v); } catch (e) {} }

    function prefs() {
        var g = lsGet(LS_GROUP, 'type');
        var s = lsGet(LS_SORT, 'name');
        return {
            group: GROUPS.some(function (x) { return x.key === g; }) ? g : 'type',
            sort:  SORTS.some(function (x) { return x.key === s; }) ? s : 'name',
            desc:  lsGet(LS_DESC, '0') === '1'
        };
    }

    /** Whether the tools are rolled up -- one setting for every record, remembered on this
     *  device. The same Roller Blind as the Files tab (shared Picasso.rollup). */
    function toolsCollapsed() { return window.Picasso.rollup.isCollapsed(LS_TOOLS); }

    function setToolsCollapsed(st, collapsed) {
        window.Picasso.rollup.set(LS_TOOLS, st.pane, st.pane.querySelector('[data-ew="tools"]'), collapsed);
        drawSummary(st);
    }

    /** Which groups are open, remembered per record across reloads and sessions. */
    function groupStateFor(entityId) {
        return window.PicassoGroupState.create({ key: 'lw.web.groups.' + entityId, defaultOpen: true });
    }

    function listUrl(entityId, offset) {
        var p = prefs();
        var q = _search[entityId] || '';
        return '/api/entities/' + encodeURIComponent(entityId) + '/web-addresses/filed'
             + '?group=' + encodeURIComponent(p.group)
             + '&sort=' + encodeURIComponent(p.sort)
             + '&desc=' + (p.desc ? 'true' : 'false')
             + (q ? '&q=' + encodeURIComponent(q) : '')
             + '&offset=' + (offset || 0)
             + '&size=' + PAGE_SIZE;
    }

    function toast(t) { if (window.Picasso && Picasso.toast) Picasso.toast(t); }
    function plural(n, one, many) { return n + ' ' + (n === 1 ? one : (many || one + 's')); }

    function hrefOf(w) {
        var url = w.webAddress || '';
        if (!url) return '';
        if (w.webAddressTypeId === KIND_EMAIL || (/^[^\s@\/]+@[^\s@\/]+$/.test(url))) {
            return /^mailto:/i.test(url) ? url : 'mailto:' + url;
        }
        return /^[a-z][a-z0-9+.-]*:\/\//i.test(url) ? url : 'https://' + url;
    }

    // ---- shell -------------------------------------------------------------------

    function shellHtml() {
        var p = prefs();
        var toolsOpen = !toolsCollapsed();
        return ''
            // The head row: one tap rolls every tool below it (Add Web Address down to
            // Arrange) up out of the way, remembered on this device. The summary rides in
            // it so the count stays in view while the tools are rolled up.
            + '<button type="button" class="ef-tools-toggle" data-ew="tools" aria-expanded="' + toolsOpen + '"'
            +   ' title="Show or hide the tools" data-testid="entity-web-tools-toggle">'
            +   '<span class="ef-caret">' + window.Picasso.rollup.caret(!toolsOpen) + '</span>'
            +   '<span class="ef-summary" data-testid="entity-web-summary"></span>'
            + '</button>'
            + '<div class="ef-tools" data-testid="entity-web-tools">'
            + '<div class="ed-pane-actions ef-actions">'
            +   '<button type="button" class="ed-btn primary" data-ew="add" data-testid="entity-web-add-btn">+ Add Web Address</button>'
            +   '<button type="button" class="ed-btn" data-ew="link" data-testid="entity-web-link-btn">+ Link Web Address</button>'
            +   '<button type="button" class="ed-btn" data-ew="select" data-testid="entity-web-select-btn">Select</button>'
            +   '<button type="button" class="ed-btn" data-ew="types" data-testid="entity-web-types-btn">Types</button>'
            + '</div>'
            + '<input type="search" class="ef-search" placeholder="Search names, addresses, types"'
            +   ' autocomplete="off" data-testid="entity-web-search">'
            + '<div class="ef-controls">'
            +   '<div class="ef-seg" role="group" aria-label="Group by" data-testid="entity-web-group">'
            +     GROUPS.map(function (g) {
                        return '<button type="button" data-group="' + g.key + '"'
                             + (g.key === p.group ? ' class="on" aria-pressed="true"' : ' aria-pressed="false"')
                             + ' data-testid="entity-web-group-' + g.key + '">' + esc(g.label) + '</button>';
                    }).join('')
            +   '</div>'
            +   '<div class="ef-sort">'
            +     '<select class="ef-sort-sel" aria-label="Sort by" data-testid="entity-web-sort">'
            +       SORTS.map(function (s) {
                        return '<option value="' + s.key + '"' + (s.key === p.sort ? ' selected' : '') + '>' + esc(s.label) + '</option>';
                    }).join('')
            +     '</select>'
            +     '<button type="button" class="ed-btn ef-dir" data-ew="dir" data-testid="entity-web-sort-dir"'
            +       ' aria-label="' + (p.desc ? 'Z to A' : 'A to Z') + '">'
            +       (p.desc ? '&#8595;' : '&#8593;') + '</button>'
            +     '<button type="button" class="ed-btn ef-foldall" data-ew="fold-all" hidden'
            +       ' data-testid="entity-web-fold-all"></button>'
            +     '<button type="button" class="ed-btn ef-arrange" data-ew="arrange" hidden aria-pressed="false"'
            +       ' aria-label="Arrange the groups and the web addresses in them" title="Arrange"'
            +       ' data-testid="entity-web-arrange">&#8645;</button>'
            +   '</div>'
            + '</div>'
            + '</div>'
            + '<div class="ef-list" data-testid="entity-web-list"></div>'
            + '<div class="ef-more" hidden><button type="button" class="ed-btn" data-ew="more" data-testid="entity-web-more-btn">Load more</button></div>'
            + '<div class="ef-selbar" hidden data-testid="entity-web-selbar">'
            +   '<span class="ef-selcount"></span>'
            +   '<button type="button" class="ed-btn primary" data-ew="sel-type" data-testid="entity-web-sel-type-btn">Change type</button>'
            +   '<button type="button" class="ed-btn" data-ew="sel-cancel" data-testid="entity-web-sel-cancel-btn">Cancel</button>'
            + '</div>';
    }

    function ensureShell(pane, entityId) {
        if (pane._ew && pane._ew.entityId === entityId && pane.querySelector('.ef-list')) return pane._ew;
        pane.innerHTML = shellHtml();
        pane.classList.add('ef-pane', 'ew-pane');
        pane.classList.toggle('ef-tools-collapsed', toolsCollapsed());
        var st = {
            entityId: entityId, pane: pane, refresh: null,
            rows: [], groups: [], total: 0, hasMore: false,
            selecting: false, selected: new Set(),
            bandState: groupStateFor(entityId)
        };
        pane._ew = st;
        pane.querySelector('.ef-search').value = _search[entityId] || '';
        wireShell(st);
        return st;
    }

    // ---- render ---------------------------------------------------------------------

    /** Called by ED.renderWebAddresses with the first page ({rows, groups, total, hasMore}). */
    function render(pane, data, entityId, refresh) {
        entityId = parseInt(entityId, 10) || 0;
        var st = ensureShell(pane, entityId);
        st.refresh = refresh;
        st.rows    = (data && data.rows) || [];
        st.groups  = (data && data.groups) || [];
        st.total   = (data && data.total) || 0;
        st.important = (data && data.important) || 0;
        st.hasMore = !!(data && data.hasMore);
        var present = new Set(st.rows.map(function (r) { return r.entityWebAddressXRefId; }));
        st.selected.forEach(function (id) { if (!present.has(id)) st.selected.delete(id); });
        drawList(st);
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
            host.innerHTML = '<div class="ed-empty">' + (q ? 'No web addresses match that search.' : 'No web addresses on file.') + '</div>';
        } else {
            var html = '';
            var curKey = null;
            st.rows.forEach(function (w, idx) {
                if (w.groupKey !== curKey) {
                    if (curKey !== null) html += '</div></section>';
                    curKey = w.groupKey;
                    html += bandHead(st, w, p.group);
                }
                html += cardHtml(st, w, idx);
            });
            if (curKey !== null) html += '</div></section>';
            host.innerHTML = html;
        }

        pane.querySelector('.ef-more').hidden = !st.hasMore;
        syncFoldAll(st);
        pane.classList.toggle('ef-selecting', st.selecting);
        pane.querySelector('[data-ew="select"]').classList.toggle('on', st.selecting);
        drawSelbar(st);
        if (window.LWED && window.LWED.attentionTabDrew) window.LWED.attentionTabDrew('web');
    }

    /** Summary line: counts come from SQL. While the tools are rolled up the search box is
     *  out of sight, so a live search is named here. */
    function drawSummary(st) {
        var q = _search[st.entityId] || '';
        var loaded = st.rows.length;
        var summary = st.total === 0
            ? (q ? 'No web addresses match' : 'No web addresses yet')
            : plural(st.total, 'web address', 'web addresses') + (loaded < st.total ? ' (' + loaded + ' shown)' : '')
              + (st.important ? ' | ' + st.important + ' important' : '');
        if (q && toolsCollapsed()) summary += ' | searching "' + q + '"';
        st.pane.querySelector('.ef-summary').textContent = summary;
    }

    function bandHead(st, w, group) {
        var meta = groupMeta(st, w.groupKey) || { count: 0 };
        var loadedInGroup = st.rows.filter(function (r) { return r.groupKey === w.groupKey; }).length;
        var countTxt = meta.count > loadedInGroup ? (loadedInGroup + ' of ' + meta.count) : String(meta.count || loadedInGroup);
        var collapsed = !st.bandState.isOpen(w.groupKey);
        var color = group === 'type' ? F().colorOf(w.groupColor) : '';
        var movable = st.arranging && group === 'type' && w.webSiteTypeId;
        return '<section class="ef-band' + (collapsed ? ' collapsed' : '') + '"'
             +   ' data-group="' + esc(w.groupKey) + '"'
             +   (group === 'type' && w.webSiteTypeId ? ' data-type="' + esc(w.webSiteTypeId) + '"' : '')
             +   ' data-testid="entity-web-band">'
             +   '<div class="ef-band-head">'
             +     (movable
                      ? '<span class="ef-grip" data-ew-grip title="Drag to move this group up or down"'
                        + ' aria-label="Drag to move ' + esc(w.groupLabel) + '" data-testid="entity-web-band-grip">&#8942;&#8942;</span>'
                      : '')
             +     (st.selecting
                      ? '<input type="checkbox" class="ef-band-check" data-ew="band-check" aria-label="Select every web address in '
                        + esc(w.groupLabel) + '" data-testid="entity-web-band-check">'
                      : '')
             +     '<button type="button" class="ef-band-toggle" data-ew="toggle" aria-expanded="' + (!collapsed) + '"'
             +       ' data-testid="entity-web-band-toggle">'
             +       '<span class="ef-caret">' + (collapsed ? '&#9656;' : '&#9662;') + '</span>'
             +       (color ? '<span class="ef-band-dot" style="background:' + esc(color) + '"></span>' : '')
             +       '<span class="ef-band-label">' + esc(w.groupLabel) + '</span>'
             +       '<span class="ef-band-count">' + esc(countTxt) + '</span>'
             +     '</button>'
             +   '</div>'
             +   '<div class="ef-band-body">';
    }

    function cardHtml(st, w, idx) {
        var E = window.LWED;
        var url = w.webAddress || '';
        var name = w.webAddressName || url || '(no address)';
        var href = hrefOf(w);
        var sel = st.selected.has(w.entityWebAddressXRefId);
        var type = { id: w.webSiteTypeId, name: w.webSiteType || 'Business', color: w.typeColor };
        return '<div class="ed-card ef-card ew-card' + (sel ? ' ef-sel' : '') + (w.important ? ' ef-important' : '') + '"'
             +   ' data-xrefid="' + esc(w.entityWebAddressXRefId) + '" data-idx="' + idx + '" data-testid="entity-web-row">'
             +   '<div class="ef-top">'
             +     (st.arranging && prefs().sort === 'order'
                      ? '<span class="ef-grip" data-ew-card-grip title="Drag to move this web address up or down"'
                        + ' aria-label="Drag to move ' + esc(name) + '" data-testid="entity-web-card-grip">&#8942;&#8942;</span>'
                      : '')
             +     (st.selecting
                      ? '<input type="checkbox" class="ef-check" data-ew="check"' + (sel ? ' checked' : '')
                        + ' aria-label="Select ' + esc(name) + '" data-testid="entity-web-check">'
                      : '')
             +     '<span class="ef-name">' + esc(name) + '</span>'
             +     '<button type="button" class="ef-flag' + (w.important ? ' on' : '') + '" data-ew="flag"'
             +       ' aria-pressed="' + (w.important ? 'true' : 'false') + '"'
             +       ' aria-label="' + (w.important ? 'Important -- tap to clear' : 'Mark important') + '"'
             +       ' title="Important" data-testid="entity-web-flag-btn">&#9873;</button>'
             +   '</div>'
             +   '<div class="ef-meta">'
             +     F().categoryChip(type, { act: 'retype', testid: 'entity-web-type-chip' })
             +     (w.webAddressType ? '<span class="ew-kind">' + esc(w.webAddressType) + '</span>' : '')
             +   '</div>'
             +   (url
                    ? '<div class="ew-url"><a href="' + esc(href) + '" target="_blank" rel="noopener"'
                      + ' data-testid="entity-web-open-link">' + esc(url) + '</a></div>'
                    : '')
             +   (w.notesPreview ? '<div class="notes ew-notes">' + esc(w.notesPreview) + '</div>' : '')
             +   (st.selecting ? '' :
                  '<div class="ed-card-actions">'
             +     (E && E.pinButton ? E.pinButton('web', w.entityWebAddressXRefId, name, url, '') : '')
             +     '<button type="button" class="ed-btn" data-ew="logins" data-testid="entity-web-manage-logins-btn">Manage Logins</button>'
             +     '<button type="button" class="ed-btn" data-ew="copy-pass" data-testid="entity-web-copy-pass-btn">Copy Password</button>'
             +     '<button type="button" class="ed-btn" data-ew="edit" data-testid="entity-web-edit-btn">Edit</button>'
             +     '<button type="button" class="ed-btn danger" data-ew="del" data-testid="entity-web-del-btn">Delete</button>'
             +   '</div>')
             + '</div>';
    }

    function drawSelbar(st) {
        var bar = st.pane.querySelector('.ef-selbar');
        bar.hidden = !st.selecting;
        if (!st.selecting) return;
        var n = st.selected.size;
        bar.querySelector('.ef-selcount').textContent = n
            ? plural(n, 'web address', 'web addresses') + ' selected' : 'Tap web addresses to select them';
        bar.querySelector('[data-ew="sel-type"]').disabled = n === 0;
    }

    // ---- actions ------------------------------------------------------------------------

    function rowOf(st, el) {
        var card = el.closest('.ew-card');
        if (!card) return null;
        return st.rows[parseInt(card.getAttribute('data-idx'), 10)] || null;
    }

    function reload(st) { if (st.refresh) st.refresh(); }

    function usedTypeIds(st) {
        return st.groups.filter(function (g) { return g.typeId; })
            .sort(function (a, b) { return b.count - a.count; })
            .map(function (g) { return g.typeId; });
    }

    /** Give the listed web addresses the type pickCategory resolved to. */
    function setType(st, xrefIds, choice) {
        if (!choice || !xrefIds.length || choice.staging) return Promise.resolve(false);
        var body = { xrefIds: xrefIds };
        if (choice.categoryId) body.typeId = choice.categoryId; else body.typeName = choice.categoryName;
        return Promise.resolve(window.LWAuth.apiPost('/api/entities/' + st.entityId + '/web-addresses/type', body)).then(function (r) {
            if (!r || !r.ok) { alert(F().errText(r, 'Could not change the type.')); return false; }
            K().invalidate();
            toast(plural(r.data.changed, 'web address', 'web addresses') + ' changed');
            reload(st);
            return true;
        });
    }

    function retype(st, w) {
        K().pickCategory({
            title: 'Type of "' + (w.webAddressName || w.webAddress || 'this web address') + '"',
            currentId: w.webSiteTypeId || 0,
            preferIds: usedTypeIds(st)
        }).then(function (choice) {
            if (choice && choice.categoryId === w.webSiteTypeId) return;
            setType(st, [w.entityWebAddressXRefId], choice);
        });
    }

    function toggleFlag(st, w, btn) {
        var next = !w.important;
        btn.classList.toggle('on', next);
        Promise.resolve(window.LWAuth.apiPost('/api/entities/' + st.entityId + '/web-addresses/' + w.entityWebAddressXRefId + '/important',
            { important: next }, 'PUT')).then(function (r) {
            if (!r || !r.ok) { btn.classList.toggle('on', !next); alert(F().errText(r, 'Could not change it.')); return; }
            toast(next ? 'Marked important' : 'No longer marked important');
            reload(st);
        });
    }

    function setBandCollapsed(band, collapsed) {
        band.classList.toggle('collapsed', collapsed);
        var t = band.querySelector('[data-ew="toggle"]');
        if (!t) return;
        t.setAttribute('aria-expanded', String(!collapsed));
        t.querySelector('.ef-caret').innerHTML = collapsed ? '&#9656;' : '&#9662;';
    }

    /** The Arrange button: only when grouping by type with two or more type groups. */
    function syncArrange(st) {
        var btn = st.pane.querySelector('[data-ew="arrange"]');
        var usable = !st.selecting
                  && ((prefs().group === 'type' && st.pane.querySelectorAll('.ef-band[data-type]').length >= 2)
                      || Array.prototype.some.call(st.pane.querySelectorAll('.ef-band-body'), function (b) {
                             return b.querySelectorAll('.ew-card').length >= 2;
                         }));
        if (!usable && st.arranging) st.arranging = false;
        btn.hidden = !usable;
        btn.classList.toggle('on', !!st.arranging);
        btn.setAttribute('aria-pressed', st.arranging ? 'true' : 'false');
        st.pane.classList.toggle('ef-arranging', !!st.arranging);
        st.pane.querySelectorAll('[data-ew-grip]').forEach(function (g) { wireGrip(st, g); });
        st.pane.querySelectorAll('[data-ew-card-grip]').forEach(function (g) { wireCardGrip(st, g); });
    }

    // Drag to reorder the type groups (Arrange on) -- the same pointer-event pattern as the
    // Files and Notes tabs and Solutions: listeners on the document, not pointer capture.
    function wireGrip(st, grip) {
        if (grip._ewWired) return;
        grip._ewWired = true;
        grip.addEventListener('click', function (e) { e.stopPropagation(); });
        grip.addEventListener('pointerdown', function (down) {
            var band = grip.closest('.ef-band');
            var host = band ? band.parentNode : null;
            if (!band || !host) return;
            down.preventDefault();
            down.stopPropagation();
            band.classList.add('dragging');
            function order() {
                return Array.prototype.map.call(host.querySelectorAll('.ef-band[data-type]'), function (b) {
                    return parseInt(b.getAttribute('data-type'), 10);
                });
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
                var over = under && under.closest ? under.closest('.ef-band[data-type]') : null;
                if (!over || over === band || over.parentNode !== host) return;
                var r = over.getBoundingClientRect();
                var ref = ev.clientY > r.top + r.height / 2 ? over.nextSibling : over;
                if (ref === band || band.nextSibling === ref) return;
                host.insertBefore(band, ref);
            }
            function onUp() {
                document.removeEventListener('pointermove', onMove);
                document.removeEventListener('pointerup', onUp);
                document.removeEventListener('pointercancel', onUp);
                band.classList.remove('dragging');
                var ids = order();
                if (ids.join(',') !== before) saveGroupOrder(st, ids);
            }
            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', onUp);
            document.addEventListener('pointercancel', onUp);
        });
    }

    // Drag a web address up or down inside its own group (Arrange on, My order sort). Same
    // pointer pattern as the group grips; the card never leaves its group.
    function wireCardGrip(st, grip) {
        if (grip._ewWired) return;
        grip._ewWired = true;
        grip.addEventListener('click', function (e) { e.stopPropagation(); });
        grip.addEventListener('pointerdown', function (down) {
            var card = grip.closest('.ew-card');
            var host = card ? card.parentNode : null;
            if (!card || !host || st.savingOrder) return;
            down.preventDefault();
            down.stopPropagation();
            card.classList.add('dragging');
            function order() {
                return Array.prototype.map.call(host.querySelectorAll('.ew-card'), function (c) {
                    return parseInt(c.getAttribute('data-xrefid'), 10);
                });
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
                var over = under && under.closest ? under.closest('.ew-card') : null;
                if (!over || over === card || over.parentNode !== host) return;
                var r = over.getBoundingClientRect();
                var ref = ev.clientY > r.top + r.height / 2 ? over.nextSibling : over;
                if (ref === card || card.nextSibling === ref) return;
                host.insertBefore(card, ref);
            }
            function onUp() {
                document.removeEventListener('pointermove', onMove);
                document.removeEventListener('pointerup', onUp);
                document.removeEventListener('pointercancel', onUp);
                card.classList.remove('dragging');
                var ids = order();
                if (ids.join(',') !== before) saveCardOrder(st, ids);
            }
            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', onUp);
            document.addEventListener('pointercancel', onUp);
        });
    }

    /** Save the new order of one group's web addresses on this record. */
    function saveCardOrder(st, xrefIds) {
        st.savingOrder = true;
        Promise.resolve(window.LWAuth.apiPost('/api/entities/' + st.entityId + '/web-addresses/reorder', { xrefIds: xrefIds }))
            .then(function (r) {
                st.savingOrder = false;
                if (!r || !r.ok) { alert(F().errText(r, 'Could not save the new order.')); reload(st); return; }
                toast('Order saved');
                reload(st);
            });
    }

    /** Merge this record's new group order into the shared type order and save it. */
    function saveGroupOrder(st, visibleIds) {
        K().loadCategories(true).then(function (types) {
            var shown = {};
            visibleIds.forEach(function (id) { shown[id] = true; });
            var next = 0;
            var all = types.map(function (t) { return shown[t.id] ? visibleIds[next++] : t.id; });
            return Promise.resolve(window.LWAuth.apiPost('/api/web-site-types/reorder', { ids: all }));
        }).then(function (r) {
            if (!r || !r.ok) { alert(F().errText(r, 'Could not save the new order.')); reload(st); return; }
            K().invalidate();
            toast('Group order saved');
            reload(st);
        });
    }

    function syncFoldAll(st) {
        syncArrange(st);
        var btn = st.pane.querySelector('[data-ew="fold-all"]');
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
        var dir = st.pane.querySelector('[data-ew="dir"]');
        dir.innerHTML = p.desc ? '&#8595;' : '&#8593;';
        dir.setAttribute('aria-label', p.desc ? 'Z to A' : 'A to Z');
    }

    function loadMore(st) {
        var btn = st.pane.querySelector('[data-ew="more"]');
        btn.disabled = true;
        Promise.resolve(window.LWAuth.apiGet(listUrl(st.entityId, st.rows.length))).then(function (r) {
            btn.disabled = false;
            if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load more web addresses.')); return; }
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
            lsSet(LS_SORT, e.target.value);
            if (e.target.value !== 'order') st.arranging = false;
            reload(st);
        });

        pane.addEventListener('change', function (e) {
            var t = e.target;
            if (t.matches('[data-ew="check"]')) {
                var w = rowOf(st, t);
                if (!w) return;
                if (t.checked) st.selected.add(w.entityWebAddressXRefId); else st.selected.delete(w.entityWebAddressXRefId);
                t.closest('.ew-card').classList.toggle('ef-sel', t.checked);
                drawSelbar(st);
            } else if (t.matches('[data-ew="band-check"]')) {
                var key = t.closest('.ef-band').getAttribute('data-group');
                st.rows.forEach(function (r) {
                    if (r.groupKey !== key) return;
                    if (t.checked) st.selected.add(r.entityWebAddressXRefId); else st.selected.delete(r.entityWebAddressXRefId);
                });
                drawList(st);
            }
        });

        pane.addEventListener('click', function (e) {
            var t = e.target;
            var E = window.LWED;

            // Select mode: a tap anywhere on a card toggles it.
            if (st.selecting && !t.closest('button, input, a')) {
                var card = t.closest('.ew-card');
                if (card) {
                    var sr = rowOf(st, card);
                    if (sr) {
                        if (st.selected.has(sr.entityWebAddressXRefId)) st.selected.delete(sr.entityWebAddressXRefId);
                        else st.selected.add(sr.entityWebAddressXRefId);
                        drawList(st);
                    }
                    return;
                }
            }

            var b = t.closest('[data-ew], [data-act="retype"]');
            if (!b || !pane.contains(b)) return;
            var act = b.getAttribute('data-ew') || b.getAttribute('data-act');
            var w = rowOf(st, b);
            var done = function () { reload(st); };

            switch (act) {
                case 'add':    E.editWebAddress(st.entityId, null, done); break;
                case 'link':   E.linkWebAddress(st.entityId, done); break;
                case 'tools':  setToolsCollapsed(st, !toolsCollapsed()); break;
                case 'select': setSelecting(st, !st.selecting); break;
                case 'types':  K().manageCategories({ onChange: done }); break;
                case 'dir':
                    lsSet(LS_DESC, prefs().desc ? '0' : '1');
                    syncControls(st);
                    reload(st);
                    break;
                case 'more':    loadMore(st); break;
                case 'arrange':
                    st.arranging = !st.arranging;
                    // Arranging the addresses means seeing My order, first to last.
                    if (st.arranging && (prefs().sort !== 'order' || prefs().desc)) {
                        lsSet(LS_SORT, 'order');
                        lsSet(LS_DESC, '0');
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
                case 'sel-type':
                    K().pickCategory({
                        title: 'Type for ' + plural(st.selected.size, 'selected web address', 'selected web addresses'),
                        preferIds: usedTypeIds(st)
                    }).then(function (choice) {
                        return setType(st, Array.from(st.selected), choice);
                    }).then(function (ok) { if (ok) setSelecting(st, false); });
                    break;
                case 'sel-cancel': setSelecting(st, false); break;
                case 'retype':     if (w) retype(st, w); break;
                case 'flag':       if (w) toggleFlag(st, w, b); break;
                case 'logins':     if (w) E.openLoginPanel(w); break;
                case 'copy-pass':  if (w) E.copyWebAddressPassword(w); break;
                case 'edit':       if (w) E.editWebAddress(st.entityId, w, done); break;
                case 'del':        if (w) E.deleteWebAddress(st.entityId, w, done); break;
            }
        });

        pane.querySelectorAll('.ef-seg [data-group]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                lsSet(LS_GROUP, btn.getAttribute('data-group'));
                syncControls(st);
                reload(st);
            });
        });
    }

    window.EntityWeb = {
        firstPageUrl: function (entityId) { return listUrl(entityId, 0); },
        render: render,
        typeKit: K
    };
})();
