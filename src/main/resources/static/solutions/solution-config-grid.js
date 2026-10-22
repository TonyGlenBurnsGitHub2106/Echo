(function () {
    'use strict';

    // The Configuration II tab of one solution (Story 5611): the generic successor to Configuration.
    //
    //   COLUMNS   the user's own, any number, each with an optional color; one may "apply to all" -- its
    //             value fills any other column left blank (shown faint, marked "all").
    //   AXES      the user's ways of describing a column -- Environment (Local / QA / Prod), Server
    //             (Tomcat 9 / Tomcat 11) -- each value with an optional color.
    //   COMPARE   every column at once, or within an axis: "Within Environment" compares Tomcat 9 with
    //             Tomcat 11 inside each environment, and each cell is tinted by ITS set:
    //               green  the set agrees     orange  partly     red  every column of the set differs
    //   COLOR BY  each column's own color, or an axis (every QA column one color, every Prod another).
    //   STATE     a cell is observed (default), planned or unverified; the last two show a badge.
    //
    // The match rule lives on the server (SolutionConfigGridMatcher); this page only paints what it is told.
    // A value is shown exactly as written; descriptions and notes go through SL.md, never raw Markdown.
    // Compare and Color by are remembered per solution in the DATABASE (PicassoViewState); Show and
    // Columns are this browser session only (sessionStorage); group folds are PicassoGroupState.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var PALETTE = ['#2563EB', '#16A34A', '#DC2626', '#EA580C', '#9333EA', '#0891B2', '#CA8A04', '#DB2777', '#0F766E', '#4B5563'];
    var STATE_LABEL = { all: 'Same', some: 'Partly the same', none: 'All different', incomplete: 'Not filled in',
                        empty: 'Not filled in', single: 'Nothing to compare', secret: 'Secret' };

    var mGrid = null, mEdit = null, mFlashId = 0, mSynced = false, mGroups = null, mColor = null;
    var mAlsoTags = null, mTagPickers = [];

    // ---- remembered choices ------------------------------------------------------------------

    function viewKey(name) { return 'leo.sl.grid.view.' + Page.id + '.' + name; }
    function viewGet(name) { return window.PicassoViewState ? window.PicassoViewState.get(viewKey(name)) : null; }
    function viewSet(name, value) { if (window.PicassoViewState) window.PicassoViewState.set(viewKey(name), value); }

    function groups() {
        if (!mGroups) mGroups = window.PicassoGroupState.create({ key: 'leo.sl.grid.groups.' + Page.id, defaultOpen: true });
        return mGroups;
    }

    function sessionGet(name, fallback) { try { return sessionStorage.getItem('sl.grid.' + name + '.' + Page.id) || fallback; } catch (e) { return fallback; } }
    function sessionSet(name, value) { try { sessionStorage.setItem('sl.grid.' + name + '.' + Page.id, value); } catch (e) { /* private mode */ } }

    /** "all" or an axis id that still exists. */
    function compareKey() {
        var k = viewGet('compare') || 'all';
        return k === 'all' || axisById(+k) ? k : 'all';
    }
    function colorBy() {
        var k = viewGet('colorBy') || 'column';
        return k === 'column' || axisById(+k) ? k : 'column';
    }

    // ---- load -----------------------------------------------------------------------------

    async function load() {
        if (!mSynced) {
            mSynced = true;
            try {
                await Promise.all([groups().sync(), window.PicassoViewState ? window.PicassoViewState.sync('leo.sl.grid.view.' + Page.id + '.') : null]);
            } catch (e) { /* the device copy is used until the server answers */ }
        }
        var r = await SL.get('/api/solutions/' + Page.id + '/config-grid');
        if (!r || !r.ok || !r.data) {
            $('pane-grid').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load Configuration II.')) + '</div>';
            return;
        }
        use(r.data);
    }

    function use(grid) {
        mGrid = grid;
        fillToolbar();
        if (mFlashId) prepareFlash();
        draw();
        if (mFlashId) flashNow();
    }

    function fillToolbar() {
        var axes = mGrid.axes;
        SL.fillSelect($('fGridCompare'), [{ value: 'all', label: 'Every column at once' }].concat(axes.map(function (a) {
            return { value: String(a.id), label: 'Within each ' + a.title };
        })), compareKey());
        SL.fillSelect($('fGridColorBy'), [{ value: 'column', label: 'Each column\'s own color' }].concat(axes.map(function (a) {
            return { value: String(a.id), label: 'By ' + a.title };
        })), colorBy());
        var colOpts = [{ value: 'all', label: 'All columns' }];
        axes.forEach(function (a) { a.values.forEach(function (v) { colOpts.push({ value: 'v' + v.id, label: a.title + ': ' + v.title }); }); });
        var cf = sessionGet('columns', 'all');
        if (!colOpts.some(function (o) { return o.value === cf; })) cf = 'all';
        SL.fillSelect($('fGridColumns'), colOpts, cf);
        $('fGridShow').value = sessionGet('show', 'all');
        $('fGridSearch').value = searchText();
        $('btnGridImport').hidden = !mGrid.canImport;
    }

    // A deep link (?gridProperty=) names a row: show every row and column, unfold its group, then flash it.
    function prepareFlash() {
        var found = propertyById(mFlashId);
        if (!found) { mFlashId = 0; return; }
        sessionSet('show', 'all');
        sessionSet('columns', 'all');
        sessionSet('q', '');
        $('fGridSearch').value = '';
        $('fGridShow').value = 'all';
        $('fGridColumns').value = 'all';
        groups().open(String(found.group.id));
    }

    function flashNow() {
        var b = $('pane-grid').querySelector('[data-gprop-edit="' + mFlashId + '"]');
        mFlashId = 0;
        if (!b) return;
        var row = b.closest('tr');
        row.scrollIntoView({ block: 'center' });
        row.classList.add('flash');
    }

    // ---- lookups inside the tab ---------------------------------------------------------------

    function axisById(id) { return mGrid ? mGrid.axes.filter(function (a) { return a.id === id; })[0] || null : null; }
    function columnById(id) { return mGrid.columns.filter(function (c) { return c.id === id; })[0] || null; }
    function groupById(id) { return mGrid.groups.filter(function (g) { return g.id === id; })[0] || null; }
    function defaultColumn() { return mGrid.columns.filter(function (c) { return c.isDefault; })[0] || null; }
    function propertyById(id) {
        for (var i = 0; i < mGrid.groups.length; i++) {
            var hit = mGrid.groups[i].properties.filter(function (p) { return p.id === id; })[0];
            if (hit) return { item: hit, group: mGrid.groups[i] };
        }
        return null;
    }
    function cellOf(p, columnId) { return (p.values || []).filter(function (v) { return v.columnId === columnId; })[0] || null; }
    function tagOf(column, axisId) { return (column.tags || []).filter(function (t) { return t.axisId === axisId; })[0] || null; }
    function axisValueById(id) {
        for (var i = 0; i < mGrid.axes.length; i++) {
            var hit = mGrid.axes[i].values.filter(function (v) { return v.id === id; })[0];
            if (hit) return hit;
        }
        return null;
    }
    function safeColor(c) { return /^#[0-9A-Fa-f]{6}$/.test(c || '') ? c : ''; }

    /** The color a column is drawn in: its own, or its value's on the chosen axis. */
    function colorOf(column) {
        var by = colorBy();
        if (by === 'column') return safeColor(column.color);
        var t = tagOf(column, +by), v = t ? axisValueById(t.valueId) : null;
        return v ? safeColor(v.color) : '';
    }

    /** The columns on screen, in order: the default first, then the rest, filtered by the Columns choice. */
    function visibleColumns() {
        var cf = $('fGridColumns').value || 'all', want = cf.charAt(0) === 'v' ? +cf.substring(1) : 0;
        var def = defaultColumn(), out = def ? [def] : [];
        mGrid.columns.forEach(function (c) {
            if (c.isDefault) return;
            if (want && !(c.tags || []).some(function (t) { return t.valueId === want; })) return;
            out.push(c);
        });
        return out;
    }

    function matchOf(p) { return (p.matches && p.matches[compareKey()]) || { state: 'empty', sets: [] }; }

    /** The state of the set a column belongs to, for this property, under the chosen comparison. */
    function setStateOf(p, columnId) {
        var sets = matchOf(p).sets || [];
        for (var i = 0; i < sets.length; i++) if (sets[i].columnIds.indexOf(columnId) >= 0) return sets[i].state;
        return '';
    }

    function shown(p) {
        var m = $('fGridShow').value || 'all', s = matchOf(p).state;
        if (m === 'diff') return s === 'some' || s === 'none';
        if (m === 'gaps') return s === 'incomplete' || s === 'empty';
        if (m === 'planned' || m === 'unverified') return (p.values || []).some(function (v) { return v.state === m; });
        return true;
    }

    // ---- the search box at the top of the tab (2026-10-12), this browser session only ----------------
    // Narrows the rows on screen, like Show: a property matches on its name, description, type, any
    // value, where a value is set, a value's state, and any note -- or because its group's name matches.

    function searchText() { return sessionGet('q', ''); }
    function searching() { return !!searchText().trim(); }

    function found(p, g) {
        return SL.searchMatches(searchText(), [g.title, p.key, p.description, p.typeCode,
            (p.values || []).map(function (v) { return [v.value, v.source, v.alsoSetIn, v.note, v.state]; })]);
    }

    /** The fold row's line: what the folded-away search and filters are doing. */
    function toolsSummary() {
        var bits = [], q = searchText().trim();
        if (q) bits.push('Searching for "' + q + '"');
        ['fGridShow', 'fGridColumns'].forEach(function (id) {
            var sel = $(id);
            if (sel.value && sel.value !== 'all' && sel.selectedIndex >= 0) bits.push(sel.options[sel.selectedIndex].text);
        });
        $('gridToolsSummary').textContent = bits.length ? bits.join(' | ') : 'Search and tools';
    }

    function matchCount() {
        var n = 0;
        mGrid.groups.forEach(function (g) { g.properties.forEach(function (p) { if (shown(p) && found(p, g)) n++; }); });
        return n;
    }

    // ---- draw -----------------------------------------------------------------------------

    function count(key) { return (mGrid.counts && mGrid.counts[key]) || 0; }
    function stateCount(state) { return count('state:' + compareKey() + '.' + state); }

    function summary() {
        var total = count('properties'), cols = mGrid.columns.length;
        var chip = function (state, label, cls) {
            var n = stateCount(state) + (state === 'incomplete' ? stateCount('empty') : 0);
            return n ? '<span class="sl-cf-chip ' + cls + '" data-testid="grid-count-' + state + '">' + n + ' ' + label + '</span>' : '';
        };
        var cmp = compareKey() === 'all' ? 'every column at once' : 'within each ' + esc(axisById(+compareKey()).title);
        var legend = '';
        if (colorBy() !== 'column') {
            var ax = axisById(+colorBy());
            legend = '<div class="sl-gd-legend" data-testid="grid-legend">' + ax.values.map(function (v) {
                return '<span class="sl-gd-key"><span class="sw" style="background:' + (safeColor(v.color) || '#E5E7EB') + '"></span>' + esc(v.title) + '</span>';
            }).join('') + '</div>';
        }
        return '<div class="sl-hero" data-testid="grid-summary">' +
            '<div class="sl-herohead" style="cursor:default;margin-bottom:6px"><strong class="sl-heroname">Configuration II</strong>' +
                '<span class="sl-muted" data-testid="grid-total">' + total + (total === 1 ? ' property' : ' properties') + ' | ' +
                cols + (cols === 1 ? ' column' : ' columns') + ' | compared ' + cmp + '</span></div>' +
            '<div class="sl-cf-chips">' + chip('all', 'same', 'all') + chip('some', 'partly the same', 'some') +
                chip('none', 'all different', 'none') + chip('incomplete', 'not filled in', 'gap') + chip('secret', 'secret', 'gap') + '</div>' +
            legend + '</div>';
    }

    function headCell(c) {
        var color = colorOf(c);
        var style = color ? ' style="border-top-color:' + color + ';background:' + color + '1A"' : '';
        var tags = (c.tags || []).map(function (t) { return esc(t.value); }).join(' | ');
        return '<th scope="col" class="sl-gd-colhead' + (c.isDefault ? ' def' : '') + '"' + style + '>' +
            '<button type="button" class="sl-gd-colbtn" data-gcol-edit="' + c.id + '" title="Change this column" data-testid="grid-column-head">' +
            esc(c.title) + '</button>' +
            (c.isDefault ? '<div class="sub">applies to all</div>' : (tags ? '<div class="sub">' + tags + '</div>' : '')) + '</th>';
    }

    function valueCell(p, c, def) {
        var v = cellOf(p, c.id), inner;
        var inherited = !c.isDefault && def && (!v || !v.value) ? cellOf(p, def.id) : null;
        if (p.secret) inner = '<span class="sl-cf-secret">Secret</span>';
        else if (v && v.value) inner = '<span class="val">' + esc(v.value) + '</span>';
        else if (inherited && inherited.value) inner = '<span class="val inh" title="From ' + esc(def.title) + '">' + esc(inherited.value) + '</span> <span class="sl-gd-inh">all</span>';
        else inner = '<span class="sl-cf-blank">--</span>';
        var badge = v && v.state && v.state !== 'observed' ? ' <span class="sl-gd-state ' + esc(v.state) + '" data-testid="grid-cell-state-badge">' + esc(v.state) + '</span>' : '';
        var mark = v && v.note ? ' <span class="sl-cf-note" title="' + esc(SL.plain(v.note, 200, true)) + '">note</span>' : '';
        var src = v && v.source ? '<span class="src">from ' + esc(v.source) + '</span>' : '';
        var also = v && v.alsoSetIn && v.alsoSetIn.length ? '<span class="src">also in ' + v.alsoSetIn.map(esc).join(', ') + '</span>' : '';
        var color = colorOf(c), m = c.isDefault ? '' : setStateOf(p, c.id);
        var style = color ? ' style="box-shadow:inset 3px 0 0 ' + color + '"' : '';
        return '<td class="sl-gd-cell m-' + esc(m || 'none-set') + '"' + style + '><button type="button" class="sl-cf-cellbtn" data-gcell-prop="' + p.id +
            '" data-gcell-col="' + c.id + '" title="' + esc((STATE_LABEL[m] || '') + ' -- change ' + p.key + ' in ' + c.title) + '" data-testid="grid-cell">' +
            inner + badge + mark + src + also + '</button></td>';
    }

    function propertyRow(p, cols, def) {
        var s = matchOf(p).state;
        return '<tr class="sl-gd-row ' + esc(s) + '" data-testid="grid-row" data-state="' + esc(s) + '">' +
            '<th scope="row" class="sl-cf-key"><button type="button" class="sl-cf-keybtn" data-gprop-edit="' + p.id + '" title="' +
                esc(STATE_LABEL[s] || '') + ' -- tap to edit this property" data-testid="grid-key">' + esc(p.key) + '</button>' +
                (p.typeCode && p.typeCode !== 'text' ? ' <span class="sl-cf-type">' + esc(p.typeCode) + '</span>' : '') +
                (p.description ? '<div class="desc sl-md">' + SL.md(p.description) + '</div>' : '') + '</th>' +
            cols.map(function (c) { return valueCell(p, c, def); }).join('') + '</tr>';
    }

    function group(g, cols, def) {
        var props = g.properties.filter(function (p) { return shown(p) && found(p, g); });
        var filtering = ($('fGridShow').value || 'all') !== 'all' || searching();
        if (filtering && !props.length) return '';
        // While searching, a group with a match is drawn open, without changing its remembered fold.
        var open = searching() || groups().isOpen(String(g.id));
        var head = '<div class="sl-cardhead sl-tp-grouphead">' +
            '<div class="sl-cardtitle" data-gfold="' + g.id + '" data-testid="grid-group-title">' +
                '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span> <h3>' + esc(g.title) + '</h3></div>' +
            '<span class="sl-muted sl-count" data-testid="grid-group-count">' + g.propertyCount + (g.propertyCount === 1 ? ' property' : ' properties') + '</span>' +
            '<button type="button" class="sl-btn small" data-gadd-prop="' + g.id + '" data-testid="grid-add-property">+ Property</button>' +
            '<button type="button" class="sl-iconbtn" data-ggroup-edit="' + g.id + '" title="Rename or delete this group" aria-label="Edit group ' + esc(g.title) +
                '" data-testid="grid-group-edit">&#9998;</button></div>';
        if (!open) return '<div class="sl-card folded sl-tp-group" data-testid="grid-group">' + head + '</div>';
        var body;
        if (!cols.length) body = '<div class="sl-muted" style="padding:6px">No columns yet -- add one with + Column.</div>';
        else if (!props.length) body = '<div class="sl-muted" style="padding:6px">No properties in this group yet.</div>';
        else body = '<div class="sl-cf-scroll"><table class="sl-gd-table" style="--gd-cols:' + cols.length + '" data-testid="grid-table"><thead><tr>' +
            '<th scope="col" class="sl-cf-key">Property</th>' + cols.map(headCell).join('') + '</tr></thead><tbody>' +
            props.map(function (p) { return propertyRow(p, cols, def); }).join('') + '</tbody></table></div>';
        return '<div class="sl-card sl-tp-group" data-testid="grid-group">' + head + body + '</div>';
    }

    function draw() {
        if (!mGrid) return;
        var cols = visibleColumns(), def = defaultColumn();
        var body;
        if (!mGrid.groups.length && !mGrid.columns.length)
            body = '<div class="sl-empty">Nothing here yet. Add the columns (+ Column) and a group of properties (+ Group)' +
                (mGrid.canImport ? ', or copy the Configuration tab in with Copy from Configuration.' : '.') + '</div>';
        else if (!mGrid.groups.length) body = '<div class="sl-empty">No property groups yet.</div>';
        else body = mGrid.groups.map(function (g) { return group(g, cols, def); }).join('') || '<div class="sl-empty">Nothing matches that filter.</div>';
        $('pane-grid').innerHTML = summary() + body;

        each('[data-gfold]', function (h) {
            h.addEventListener('click', function () { groups().toggle(h.getAttribute('data-gfold')); draw(); });
        });
        each('[data-gadd-prop]', function (b) { b.addEventListener('click', function () { openProperty(0, +b.getAttribute('data-gadd-prop')); }); });
        each('[data-ggroup-edit]', function (b) { b.addEventListener('click', function () { openGroup(+b.getAttribute('data-ggroup-edit')); }); });
        each('[data-gcol-edit]', function (b) { b.addEventListener('click', function () { openColumn(+b.getAttribute('data-gcol-edit')); }); });
        each('[data-gprop-edit]', function (b) { b.addEventListener('click', function () { openProperty(+b.getAttribute('data-gprop-edit'), 0); }); });
        each('[data-gcell-prop]', function (b) {
            b.addEventListener('click', function () { openCell(+b.getAttribute('data-gcell-prop'), +b.getAttribute('data-gcell-col')); });
        });
        toolsSummary();
        SL.status((searching() ? matchCount() + ' of ' : '') + count('properties') + ' properties | ' + stateCount('all') + ' same | ' + stateCount('some') + ' partly the same | ' +
            stateCount('none') + ' all different | ' + (stateCount('incomplete') + stateCount('empty')) + ' not filled in');
    }

    function each(selector, fn) { Array.prototype.forEach.call($('pane-grid').querySelectorAll(selector), fn); }

    // ---- color swatches ----------------------------------------------------------------------

    /** Draw a swatch row into a container; onPick(hex or '') is called on a tap. */
    function swatches(container, current, onPick, testPrefix) {
        current = (current || '').toUpperCase();
        container.innerHTML = '<button type="button" class="sl-gd-sw none' + (current ? '' : ' on') + '" data-sw="" title="No color" aria-label="No color" data-testid="' +
            testPrefix + '-none">&times;</button>' + PALETTE.map(function (c) {
                return '<button type="button" class="sl-gd-sw' + (c === current ? ' on' : '') + '" data-sw="' + c + '" style="background:' + c +
                    '" title="' + c + '" aria-label="Color ' + c + '" data-testid="' + testPrefix + '"></button>';
            }).join('');
        Array.prototype.forEach.call(container.querySelectorAll('[data-sw]'), function (b) {
            b.addEventListener('click', function () {
                Array.prototype.forEach.call(container.querySelectorAll('[data-sw]'), function (x) { x.classList.toggle('on', x === b); });
                onPick(b.getAttribute('data-sw'));
            });
        });
    }

    // ---- a group ----------------------------------------------------------------------------

    function openGroup(id) {
        var g = id ? groupById(id) : null;
        mEdit = { kind: 'group', id: id || 0 };
        SL.warn('ggWarn', '');
        $('ggHeading').textContent = id ? 'Edit property group' : 'New property group';
        $('ggName').value = g ? g.title : '';
        $('ggDelete').hidden = !id;
        SL.open('gridGroupOverlay');
        setTimeout(function () { $('ggName').focus(); }, 30);
    }

    async function saveGroup() {
        var name = $('ggName').value.trim();
        if (!name) { SL.warn('ggWarn', 'A group needs a name.'); return; }
        var r = mEdit.id ? await SL.put('/api/solutions/config-grid/groups/' + mEdit.id, { title: name })
                         : await SL.post('/api/solutions/' + Page.id + '/config-grid/groups', { title: name });
        if (!r || !r.ok || !r.data) { SL.warn('ggWarn', SL.failText(r, 'The group could not be saved.')); return; }
        SL.close('gridGroupOverlay');
        use(r.data);
    }

    async function deleteGroup() {
        var g = groupById(mEdit.id);
        if (!g) return;
        if (!(await window.Picasso.confirmDelete('Its ' + g.propertyCount + (g.propertyCount === 1 ? ' property' : ' properties') +
            ' and every value of them go with it.\n\nThis cannot be undone.', { title: 'Delete Group: ' + g.title + '?' }))) return;
        var r = await SL.del('/api/solutions/config-grid/groups/' + g.id);
        if (!r || !r.ok || !r.data) { SL.warn('ggWarn', SL.failText(r, 'The group could not be deleted.')); return; }
        SL.close('gridGroupOverlay');
        use(r.data);
    }

    // ---- a column ---------------------------------------------------------------------------

    function openColumn(id) {
        var c = id ? columnById(id) : null;
        mEdit = { kind: 'column', id: id || 0 };
        mColor = c ? (c.color || '') : '';
        SL.warn('gcWarn', '');
        $('gcHeading').textContent = id ? 'Edit column' : 'New column';
        $('gcTitle').value = c ? c.title : '';
        $('gcDefault').checked = !!(c && c.isDefault);
        swatches($('gcColors'), mColor, function (hex) { mColor = hex; }, 'grid-column-color');
        // One find-or-create picker per axis: type a new value to add it.
        mTagPickers = [];
        $('gcTags').innerHTML = mGrid.axes.length ? mGrid.axes.map(function (a, i) {
            var t = c ? tagOf(c, a.id) : null;
            return '<div class="fld sl-gd-tagrow"><label for="gcTag' + i + '">' + esc(a.title) + '</label>' +
                '<div class="picker-wrap"><input type="text" id="gcTag' + i + '" class="picker-input" data-axis="' + esc(a.title) + '" value="' + esc(t ? t.value : '') +
                '" placeholder="None" autocomplete="off" data-testid="grid-column-tag"><div class="picker-dropdown" id="gcTagDD' + i + '"></div></div></div>';
        }).join('') : '';
        mGrid.axes.forEach(function (a, i) {
            if (window.PicassoPicker)
                mTagPickers.push(window.PicassoPicker.attach({ input: $('gcTag' + i), dropdown: $('gcTagDD' + i),
                    getItems: function () { return a.values.map(function (v) { return v.title; }); },
                    allowNew: true, newLabel: 'Add', clearLabel: '(none)' }));
        });
        $('gcNewAxis').value = '';
        $('gcNewAxisValue').value = '';
        $('gcDelete').hidden = !id;
        SL.open('gridColumnOverlay');
        setTimeout(function () { $('gcTitle').focus(); }, 30);
    }

    async function saveColumn() {
        var title = $('gcTitle').value.trim();
        if (!title) { SL.warn('gcWarn', 'A column needs a name, for example QA - Tomcat 11.'); return; }
        var tags = {};
        Array.prototype.forEach.call($('gcTags').querySelectorAll('[data-axis]'), function (inp) { tags[inp.getAttribute('data-axis')] = inp.value.trim(); });
        var newAxis = $('gcNewAxis').value.trim(), newValue = $('gcNewAxisValue').value.trim();
        if (newAxis && !newValue) { SL.warn('gcWarn', 'Give the new axis "' + newAxis + '" a value for this column.'); return; }
        if (newValue && !newAxis) { SL.warn('gcWarn', 'Name the axis the value "' + newValue + '" belongs to.'); return; }
        if (newAxis) tags[newAxis] = newValue;
        var body = { title: title, color: mColor || '', isDefault: $('gcDefault').checked, tags: tags };
        var r = mEdit.id ? await SL.put('/api/solutions/config-grid/columns/' + mEdit.id, body)
                         : await SL.post('/api/solutions/' + Page.id + '/config-grid/columns', body);
        if (!r || !r.ok || !r.data) { SL.warn('gcWarn', SL.failText(r, 'The column could not be saved.')); return; }
        SL.close('gridColumnOverlay');
        use(r.data);
    }

    async function deleteColumn() {
        var c = columnById(mEdit.id);
        if (!c) return;
        var n = 0;
        mGrid.groups.forEach(function (g) { g.properties.forEach(function (p) { if (cellOf(p, c.id)) n++; }); });
        if (!(await window.Picasso.confirmDelete('Its ' + n + (n === 1 ? ' value goes' : ' values go') + ' with it. The properties stay.\n\nThis cannot be undone.',
            { title: 'Delete Column: ' + c.title + '?' }))) return;
        var r = await SL.del('/api/solutions/config-grid/columns/' + c.id);
        if (!r || !r.ok || !r.data) { SL.warn('gcWarn', SL.failText(r, 'The column could not be deleted.')); return; }
        SL.close('gridColumnOverlay');
        use(r.data);
    }

    // ---- the axes sheet ----------------------------------------------------------------------

    function openAxes() {
        SL.warn('gaWarn', '');
        drawAxes();
        SL.open('gridAxesOverlay');
    }

    function drawAxes() {
        var html = mGrid.axes.map(function (a) {
            return '<section class="sl-gd-axis" data-testid="grid-axis">' +
                '<div class="sl-gd-axishead"><input type="text" value="' + esc(a.title) + '" data-ax-title="' + a.id + '" aria-label="Axis name" autocomplete="off" data-testid="grid-axis-title">' +
                    '<button type="button" class="sl-btn small" data-ax-save="' + a.id + '" data-testid="grid-axis-save">Save</button>' +
                    '<button type="button" class="sl-btn small danger" data-ax-del="' + a.id + '" data-testid="grid-axis-delete">Delete</button></div>' +
                a.values.map(function (v) {
                    return '<div class="sl-gd-valrow" data-testid="grid-axis-value">' +
                        '<input type="text" value="' + esc(v.title) + '" data-av-title="' + v.id + '" aria-label="Value name" autocomplete="off" data-testid="grid-axis-value-title">' +
                        '<div class="sl-gd-swatches small" data-av-colors="' + v.id + '"></div>' +
                        '<button type="button" class="sl-btn small" data-av-save="' + v.id + '" data-testid="grid-axis-value-save">Save</button>' +
                        '<button type="button" class="sl-btn small danger" data-av-del="' + v.id + '" data-testid="grid-axis-value-delete">Delete</button></div>';
                }).join('') +
                '<div class="sl-gd-valrow add"><input type="text" placeholder="Add a value" data-av-new="' + a.id + '" autocomplete="off" data-testid="grid-axis-value-new">' +
                    '<button type="button" class="sl-btn small" data-av-add="' + a.id + '" data-testid="grid-axis-value-add">Save</button></div>' +
                '</section>';
        }).join('') +
            '<div class="sl-gd-axishead add"><input type="text" id="gaNewAxis" placeholder="A new axis, for example Server" autocomplete="off" data-testid="grid-axis-new">' +
            '<button type="button" class="sl-btn small primary" id="gaAddAxis" data-testid="grid-axis-add">Save</button></div>';
        $('gaBody').innerHTML = html;
        var picked = {};
        mGrid.axes.forEach(function (a) {
            a.values.forEach(function (v) {
                picked[v.id] = v.color || '';
                swatches($('gaBody').querySelector('[data-av-colors="' + v.id + '"]'), v.color, function (hex) {
                    picked[v.id] = hex;
                    SL.guard($('gaBody').querySelector('[data-av-save="' + v.id + '"]'), function () { return saveValue(v.id, picked); });
                }, 'grid-axis-value-color');
            });
        });
        wireAxes(picked);
    }

    function wireAxes(picked) {
        var body = $('gaBody');
        var on = function (sel, fn) {
            Array.prototype.forEach.call(body.querySelectorAll(sel), function (b) { b.addEventListener('click', function () { SL.guard(b, function () { return fn(b); }); }); });
        };
        on('[data-ax-save]', function (b) {
            var id = +b.getAttribute('data-ax-save');
            return axesCall(SL.put('/api/solutions/config-grid/axes/' + id, { title: body.querySelector('[data-ax-title="' + id + '"]').value }));
        });
        on('[data-ax-del]', async function (b) {
            var a = axisById(+b.getAttribute('data-ax-del'));
            if (!a) return;
            var used = mGrid.columns.filter(function (c) { return tagOf(c, a.id); }).length;
            if (!(await window.Picasso.confirmDelete('Its ' + a.values.length + (a.values.length === 1 ? ' value' : ' values') + ' go with it, and ' + used +
                (used === 1 ? ' column loses' : ' columns lose') + ' its tag. The columns and their values stay.\n\nThis cannot be undone.',
                { title: 'Delete Axis: ' + a.title + '?' }))) return;
            return axesCall(SL.del('/api/solutions/config-grid/axes/' + a.id));
        });
        on('[data-av-save]', function (b) { return saveValue(+b.getAttribute('data-av-save'), picked); });
        on('[data-av-del]', async function (b) {
            var v = axisValueById(+b.getAttribute('data-av-del'));
            if (!v) return;
            var used = mGrid.columns.filter(function (c) { return (c.tags || []).some(function (t) { return t.valueId === v.id; }); }).length;
            if (!(await window.Picasso.confirmDelete(used + (used === 1 ? ' column loses' : ' columns lose') + ' this tag. The columns stay.\n\nThis cannot be undone.',
                { title: 'Delete Axis Value: ' + v.title + '?' }))) return;
            return axesCall(SL.del('/api/solutions/config-grid/axis-values/' + v.id));
        });
        on('[data-av-add]', function (b) {
            var id = +b.getAttribute('data-av-add'), inp = body.querySelector('[data-av-new="' + id + '"]');
            if (!inp.value.trim()) { SL.warn('gaWarn', 'Type the new value first.'); return null; }
            return axesCall(SL.post('/api/solutions/config-grid/axes/' + id + '/values', { title: inp.value }));
        });
        on('#gaAddAxis', function () {
            if (!$('gaNewAxis').value.trim()) { SL.warn('gaWarn', 'Type the new axis name first.'); return null; }
            return axesCall(SL.post('/api/solutions/' + Page.id + '/config-grid/axes', { title: $('gaNewAxis').value }));
        });
    }

    function saveValue(id, picked) {
        return axesCall(SL.put('/api/solutions/config-grid/axis-values/' + id,
            { title: $('gaBody').querySelector('[data-av-title="' + id + '"]').value, color: picked[id] || '' }));
    }

    async function axesCall(promise) {
        var r = await promise;
        if (!r || !r.ok || !r.data) { SL.warn('gaWarn', SL.failText(r, 'That could not be saved.')); return; }
        SL.warn('gaWarn', '');
        use(r.data);
        drawAxes();
    }

    // ---- a property -------------------------------------------------------------------------------

    function openProperty(propertyId, groupId) {
        var found = propertyId ? propertyById(propertyId) : null;
        if (propertyId && !found) return;
        var g = found ? found.group : groupById(groupId);
        mEdit = { kind: 'property', id: propertyId || 0, groupId: g ? g.id : 0 };
        SL.warn('gpWarn', '');
        $('gpHeading').textContent = propertyId ? 'Edit property' : 'New property';
        $('gpKey').value = found ? found.item.key : '';
        $('gpDescription').value = found ? (found.item.description || '') : '';
        SL.fillSelect($('gpType'), mGrid.types.map(function (t) { return { value: t.code, label: t.name }; }), found ? found.item.typeCode : 'text');
        $('gpGroupFld').hidden = !propertyId || mGrid.groups.length < 2;
        SL.fillSelect($('gpGroup'), mGrid.groups.map(function (x) { return { value: String(x.id), label: x.title }; }), g ? g.id : '');
        $('gpDelete').hidden = !propertyId;
        SL.open('gridPropertyOverlay');
        setTimeout(function () { $('gpKey').focus(); }, 30);
    }

    async function saveProperty() {
        var key = $('gpKey').value.trim();
        if (!key) { SL.warn('gpWarn', 'A property needs a name, for example logback.smtpHost.'); return; }
        var type = $('gpType').value;
        var was = mEdit.id ? propertyById(mEdit.id) : null;
        if (was && !was.item.secret && type === 'secret' &&
            !(await window.Picasso.okCancel('Its values are wiped from every column and are never stored again. Where it is set is kept.',
                { title: 'Make ' + was.item.key + ' a secret?' }))) return;
        var body = { key: key, description: $('gpDescription').value, typeCode: type };
        var r;
        if (mEdit.id) { body.groupId = +$('gpGroup').value || mEdit.groupId; r = await SL.put('/api/solutions/config-grid/properties/' + mEdit.id, body); }
        else r = await SL.post('/api/solutions/config-grid/groups/' + mEdit.groupId + '/properties', body);
        if (!r || !r.ok || !r.data) { SL.warn('gpWarn', SL.failText(r, 'The property could not be saved.')); return; }
        SL.close('gridPropertyOverlay');
        use(r.data);
    }

    async function deleteProperty() {
        var found = propertyById(mEdit.id);
        if (!found) return;
        var n = (found.item.values || []).length;
        if (!(await window.Picasso.confirmDelete('From the group "' + found.group.title + '". ' +
            (n ? 'Its ' + n + (n === 1 ? ' value goes' : ' values go') + ' with it.' : '') + '\n\nThis cannot be undone.',
            { title: 'Delete Property: ' + found.item.key + '?' }))) return;
        var r = await SL.del('/api/solutions/config-grid/properties/' + found.item.id);
        if (!r || !r.ok || !r.data) { SL.warn('gpWarn', SL.failText(r, 'The property could not be deleted.')); return; }
        SL.close('gridPropertyOverlay');
        use(r.data);
    }

    // ---- one cell: a property in a column ---------------------------------------------------------

    function openCell(propertyId, columnId) {
        var found = propertyById(propertyId), c = columnById(columnId);
        if (!found || !c) return;
        var p = found.item, v = cellOf(p, columnId), def = defaultColumn();
        mEdit = { kind: 'cell', id: propertyId, columnId: columnId };
        SL.warn('gxWarn', '');
        $('gxHeading').textContent = p.key;
        $('gxContext').textContent = c.title + (p.description ? ' | ' + SL.plain(p.description, 120) : '');
        $('gxValueFld').hidden = !!p.secret;
        $('gxSecretNote').hidden = !p.secret;
        $('gxValue').value = v && v.value ? v.value : '';
        var inherited = !c.isDefault && def ? cellOf(p, def.id) : null;
        $('gxInherited').hidden = !(inherited && inherited.value && !p.secret);
        $('gxInherited').textContent = inherited && inherited.value ? 'Left blank, it takes "' + inherited.value + '" from ' + def.title + '.' : '';
        SL.fillSelect($('gxState'), mGrid.states.map(function (s) { return { value: s.code, label: s.name + ' -- ' + s.description }; }),
            v && v.state ? v.state : 'observed');
        $('gxSource').value = v && v.source ? v.source : '';
        $('gxNote').value = v && v.note ? v.note : '';
        if (mAlsoTags) mAlsoTags.setValues(v && v.alsoSetIn ? v.alsoSetIn : []);
        $('gxClear').hidden = !v;
        var others = mGrid.columns.filter(function (x) { return x.id !== columnId; }).map(function (x) {
            var o = cellOf(p, x.id);
            return x.title + ': ' + (p.secret ? '(secret)' : (o && o.value ? o.value : '--'));
        });
        $('gxOthers').textContent = others.length ? 'Elsewhere -- ' + others.join(' | ') : '';
        SL.open('gridCellOverlay');
        setTimeout(function () { (p.secret ? $('gxSource') : $('gxValue')).focus(); }, 30);
    }

    async function saveCell(clear) {
        if (clear) {
            var found = propertyById(mEdit.id), c = columnById(mEdit.columnId);
            if (!(await window.Picasso.confirmDelete('Its value, where it is read from, the other places, the state and the note are all removed.\n\nThis cannot be undone.',
                { title: 'Clear ' + found.item.key + ' in ' + c.title + '?', yes: 'Clear' }))) return;
        }
        var body = clear ? { value: '', source: '', note: '', alsoSetIn: [] }
                         : { value: $('gxValue').value, source: $('gxSource').value, note: $('gxNote').value, state: $('gxState').value,
                             alsoSetIn: mAlsoTags ? mAlsoTags.getValues() : [] };
        var r = await SL.put('/api/solutions/config-grid/properties/' + mEdit.id + '/values/' + mEdit.columnId, body);
        if (!r || !r.ok || !r.data) { SL.warn('gxWarn', SL.failText(r, 'That could not be saved.')); return; }
        SL.close('gridCellOverlay');
        use(r.data);
    }

    // ---- copy the Configuration tab in --------------------------------------------------------------

    async function importConfig() {
        if (!(await window.Picasso.okCancel('Each environment of the Configuration tab becomes a column here, and its groups, properties and values ' +
            'come across as they are. The Configuration tab itself is not changed.', { title: 'Copy the Configuration tab into Configuration II?' }))) return;
        var r = await SL.post('/api/solutions/' + Page.id + '/config-grid/import', {});
        if (!r || !r.ok || !r.data) { window.Picasso.error(SL.failText(r, 'The Configuration tab could not be copied.')); return; }
        use(r.data);
    }

    function foldAll(collapsed) {
        if (!mGrid) return;
        if (collapsed) groups().collapseAll(); else groups().expandAll();
        draw();
    }

    // ---- print: the same table, through the Leonardo Print Menu -----------------------------------

    var PRINT_CSS =
        '@page{size:letter landscape;margin:12mm}' +
        'body{margin:0;font-size:10pt}' +
        '.cg h1{font-size:15pt;margin:0 0 4px;padding-bottom:4px}' +
        '.cg h2{font-size:12pt;margin:14px 0 4px}' +
        '.cg .sum{font-size:9.5pt;margin:4px 0 8px}' +
        '.cg table{width:100%;table-layout:fixed;border-collapse:collapse;font-size:8.5pt;margin:4px 0 8px;-fs-table-paginate:paginate}' +
        '.cg th,.cg td{border:1px solid #D1D5DB;padding:3px 5px;vertical-align:top;text-align:left;' +
        '  word-wrap:break-word;overflow-wrap:break-word;word-break:break-all}' +
        '.cg thead th{background:#F3F4F6;font-size:8.5pt;border-top-width:3px}' +
        '.cg .v{font-family:Consolas,"Courier New",monospace;font-size:8pt}' +
        '.cg .inh{color:#9CA3AF;font-style:italic}' +
        '.cg .src{color:#6B7280;font-size:7.5pt;margin-top:2px}' +
        '.cg .st{font-size:7pt;font-weight:bold;color:#7C3AED}' +
        '.cg td.m-all .v{color:#15803D}.cg td.m-some .v{color:#C2410C}.cg td.m-none .v{color:#B91C1C}' +
        '.cg thead{display:table-header-group}' +
        '.cg tr{page-break-inside:avoid}';

    function printCell(p, c, def) {
        var v = cellOf(p, c.id), inh = !c.isDefault && def && (!v || !v.value) ? cellOf(p, def.id) : null;
        var value = p.secret ? '<div class="v">(secret)</div>'
            : v && v.value ? '<div class="v">' + esc(v.value) + '</div>'
            : inh && inh.value ? '<div class="v inh">' + esc(inh.value) + ' (all)</div>' : '';
        var st = v && v.state && v.state !== 'observed' ? '<div class="st">' + esc(v.state) + '</div>' : '';
        var src = v && v.source ? '<div class="src">from ' + esc(v.source) + '</div>' : '';
        var also = v && v.alsoSetIn && v.alsoSetIn.length ? '<div class="src">also in ' + v.alsoSetIn.map(esc).join(', ') + '</div>' : '';
        return '<td class="m-' + esc(c.isDefault ? '' : setStateOf(p, c.id)) + '">' + value + st + src + also + '</td>';
    }

    function print() {
        if (!mGrid) return;
        var cols = visibleColumns(), def = defaultColumn();
        var keyPct = 18, colPct = Math.floor((100 - keyPct) / Math.max(cols.length, 1));
        var colgroup = '<colgroup><col style="width:' + keyPct + '%"/>' + cols.map(function () { return '<col style="width:' + colPct + '%"/>'; }).join('') + '</colgroup>';
        var cmp = compareKey() === 'all' ? 'every column at once' : 'within each ' + axisById(+compareKey()).title;
        var head = '<thead><tr><th>Property</th>' + cols.map(function (c) {
            var color = colorOf(c);
            var tags = (c.tags || []).map(function (t) { return esc(t.value); }).join(' | ');
            return '<th' + (color ? ' style="border-top-color:' + color + '"' : '') + '>' + esc(c.title) +
                (c.isDefault ? '<div class="src">applies to all</div>' : tags ? '<div class="src">' + tags + '</div>' : '') + '</th>';
        }).join('') + '</tr></thead>';
        var html = '<div class="cg"><h1>' + esc(Page.solution.name) + ' - Configuration II</h1>' +
            '<div class="sum">' + count('properties') + ' properties | compared ' + esc(cmp) + ' | ' + stateCount('all') + ' same (green) | ' +
            stateCount('some') + ' partly the same (orange) | ' + stateCount('none') + ' all different (red) | ' +
            (stateCount('incomplete') + stateCount('empty')) + ' not filled in</div>' +
            mGrid.groups.map(function (g) {
                var props = g.properties.filter(shown);
                if (!props.length) return '';
                return '<h2>' + esc(g.title) + '</h2><table>' + colgroup + head + '<tbody>' + props.map(function (p) {
                    return '<tr><th>' + esc(p.key) + '</th>' + cols.map(function (c) { return printCell(p, c, def); }).join('') + '</tr>';
                }).join('') + '</tbody></table>';
            }).join('') + '</div>';
        $('printArea').innerHTML = html;
        window.Picasso.printThisPage({ selector: '#printArea', title: Page.solution.name + ' - Configuration II', extraCss: PRINT_CSS });
    }

    Page.registerTab('grid', {
        init: function () {
            $('fGridShow').addEventListener('change', function () { sessionSet('show', $('fGridShow').value); draw(); });
            // The search and every tool fold away under one head row, remembered for this user (Roll Up Tools).
            window.Picasso.rollup.attach({ key: 'leo.sl.grid.tools.collapsed', pane: $('toolbar-grid'), toggle: $('gridToolsToggle') });
            $('fGridSearch').addEventListener('input', function () { sessionSet('q', $('fGridSearch').value); draw(); });
            $('fGridSearch').addEventListener('keydown', function (e) {
                if (e.key !== 'Escape' || !$('fGridSearch').value) return;
                e.stopPropagation();
                $('fGridSearch').value = '';
                sessionSet('q', '');
                draw();
            });
            $('fGridColumns').addEventListener('change', function () { sessionSet('columns', $('fGridColumns').value); draw(); });
            $('fGridCompare').addEventListener('change', function () { viewSet('compare', $('fGridCompare').value); draw(); });
            $('fGridColorBy').addEventListener('change', function () { viewSet('colorBy', $('fGridColorBy').value); draw(); });
            $('btnGridAddGroup').addEventListener('click', function () { openGroup(0); });
            $('btnGridAddColumn').addEventListener('click', function () { openColumn(0); });
            $('btnGridAxes').addEventListener('click', openAxes);
            $('btnGridImport').addEventListener('click', function () { SL.guard($('btnGridImport'), importConfig); });
            $('btnGridExpand').addEventListener('click', function () { foldAll(false); });
            $('btnGridCollapse').addEventListener('click', function () { foldAll(true); });
            $('ggCancel').addEventListener('click', function () { SL.close('gridGroupOverlay'); });
            $('ggSave').addEventListener('click', function () { SL.guard($('ggSave'), saveGroup); });
            $('ggDelete').addEventListener('click', function () { SL.guard($('ggDelete'), deleteGroup); });
            $('gcCancel').addEventListener('click', function () { SL.close('gridColumnOverlay'); });
            $('gcSave').addEventListener('click', function () { SL.guard($('gcSave'), saveColumn); });
            $('gcDelete').addEventListener('click', function () { SL.guard($('gcDelete'), deleteColumn); });
            $('gaClose').addEventListener('click', function () { SL.close('gridAxesOverlay'); });
            $('gpCancel').addEventListener('click', function () { SL.close('gridPropertyOverlay'); });
            $('gpSave').addEventListener('click', function () { SL.guard($('gpSave'), saveProperty); });
            $('gpDelete').addEventListener('click', function () { SL.guard($('gpDelete'), deleteProperty); });
            $('gxCancel').addEventListener('click', function () { SL.close('gridCellOverlay'); });
            $('gxSave').addEventListener('click', function () { SL.guard($('gxSave'), function () { return saveCell(false); }); });
            $('gxClear').addEventListener('click', function () { SL.guard($('gxClear'), function () { return saveCell(true); }); });
            var sources = function () { return mGrid ? mGrid.sources : []; };
            if (window.PicassoPicker)
                window.PicassoPicker.attach({ input: $('gxSource'), dropdown: $('gxSourceDD'), getItems: sources,
                    allowNew: true, newLabel: 'Add', clearLabel: '(nowhere yet)' });
            if (window.PicassoTags)
                mAlsoTags = window.PicassoTags.attach({ input: $('gxAlsoInput'), chips: $('gxAlsoChips'), dropdown: $('gxAlsoDD'), getOptions: sources });
        },
        show: load,
        print: print,
        /** A deep link (?gridProperty=) names the row to flash; call before the tab is first shown. */
        flash: function (propertyId) { mFlashId = propertyId; }
    });
})();
