(function () {
    'use strict';

    // The Configuration tab of one solution (Story 3516): every property that means something, in
    // groups, with its value in each environment (Local, QA, Production by default -- + Environment
    // adds more) and where that value is read from. Across a row the values are compared:
    //
    //   green  every environment has the same value        orange  some match, not all
    //   red    every environment differs                   plain   an environment has no value yet
    //
    // The rule lives on the server (vw_SolutionConfigPropertyState); this page only paints what it is
    // told. A SECRET property never has a value here -- only where it is set.
    //
    // A value is shown exactly as written (it is a setting, not prose); descriptions and notes are
    // drawn formatted through SL.md, never as raw Markdown.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var mConfig = null, mEdit = null, mSeeding = false, mFlashId = 0;
    var mAlsoTags = null, mSourcePicker = null;

    var STATE_LABEL = { all: 'Same everywhere', some: 'Partly the same', none: 'All different', incomplete: 'Not filled in',
                        empty: 'Not filled in', secret: 'Secret' };

    // ---- which rows show, remembered for this browser session ------------------------------

    function showKey() { return 'sl.config.show.' + Page.id; }
    function showMode() { try { return sessionStorage.getItem(showKey()) || 'all'; } catch (e) { return 'all'; } }
    function setShowMode(v) { try { sessionStorage.setItem(showKey(), v); } catch (e) { /* private mode */ } }
    function shown(p) {
        var m = showMode();
        if (m === 'diff') return p.matchState === 'some' || p.matchState === 'none';
        if (m === 'gaps') return p.matchState === 'incomplete' || p.matchState === 'empty';
        return true;
    }

    // ---- the search box at the top of the tab (2026-10-12), remembered for this browser session --
    // The tab already holds the whole configuration, so the search narrows the rows on screen, the
    // same way Show does: a property matches on its name, description, type, any value, where a
    // value is set, and any note -- or because its group's name matches.

    function searchKey() { return 'sl.config.q.' + Page.id; }
    function searchText() { try { return sessionStorage.getItem(searchKey()) || ''; } catch (e) { return ''; } }
    function setSearchText(v) { try { sessionStorage.setItem(searchKey(), v); } catch (e) { /* private mode */ } }
    function searching() { return !!searchText().trim(); }

    function found(p, g) {
        return SL.searchMatches(searchText(), [g.title, p.key, p.description, p.typeCode,
            (p.values || []).map(function (v) { return [v.value, v.source, v.alsoSetIn, v.note]; })]);
    }

    // ---- load -----------------------------------------------------------------------------

    async function load() {
        var r = await SL.get('/api/solutions/' + Page.id + '/config');
        if (!r || !r.ok || !r.data) {
            $('pane-config').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the configuration.')) + '</div>';
            return;
        }
        // A solution starts with Local, QA and Production the first time its configuration is opened.
        if (!r.data.environments.length && !mSeeding) {
            mSeeding = true;
            var s = await SL.post('/api/solutions/' + Page.id + '/config/environments/defaults', {});
            mSeeding = false;
            if (s && s.ok && s.data) r = s;
        }
        use(r.data);
    }

    function use(config) {
        mConfig = config;
        $('fConfigShow').value = showMode();
        $('fConfigSearch').value = searchText();
        if (mFlashId) prepareFlash();
        draw();
        if (mFlashId) flashNow();
    }

    // A deep link (?configProperty=) names a row: show every row, unfold its group, then flash it once drawn.
    function prepareFlash() {
        var found = propertyById(mFlashId);
        if (!found) { mFlashId = 0; return; }
        setShowMode('all');
        $('fConfigShow').value = 'all';
        setSearchText('');
        $('fConfigSearch').value = '';
        Page.fold().set('cgroup:' + found.group.id, false);
    }

    function flashNow() {
        var b = $('pane-config').querySelector('[data-prop-edit="' + mFlashId + '"]');
        mFlashId = 0;
        if (!b) return;
        var row = b.closest('tr');
        row.scrollIntoView({ block: 'center' });
        row.classList.add('flash');
    }

    // ---- draw -----------------------------------------------------------------------------

    function count(key) { return (mConfig.counts && mConfig.counts[key]) || 0; }

    function summary() {
        var c = mConfig, total = count('properties');
        var chip = function (state, label, cls) {
            var n = count('state:' + state) + (state === 'incomplete' ? count('state:empty') : 0);
            return n ? '<span class="sl-cf-chip ' + cls + '" data-testid="config-count-' + state + '">' + n + ' ' + label + '</span>' : '';
        };
        return '<div class="sl-hero" data-testid="config-summary">' +
            '<div class="sl-herohead" style="cursor:default;margin-bottom:6px"><strong class="sl-heroname">Configuration</strong>' +
                '<span class="sl-muted" data-testid="config-total">' + total + (total === 1 ? ' property' : ' properties') + ' | ' +
                c.environments.length + (c.environments.length === 1 ? ' environment' : ' environments') + ': ' +
                c.environments.map(function (e) { return esc(e.title); }).join(', ') + '</span></div>' +
            '<div class="sl-cf-chips">' + chip('all', 'same everywhere', 'all') + chip('some', 'partly the same', 'some') +
                chip('none', 'all different', 'none') + chip('incomplete', 'not filled in', 'gap') + chip('secret', 'secret', 'gap') + '</div>' +
            '</div>';
    }

    function cellOf(p, envId) { return (p.values || []).filter(function (v) { return v.environmentId === envId; })[0] || null; }

    function valueCell(p, e) {
        var v = cellOf(p, e.id);
        var inner;
        if (p.secret) inner = '<span class="sl-cf-secret">Secret -- not stored</span>';
        else if (v && v.value) inner = '<span class="val">' + esc(v.value) + '</span>';
        else inner = '<span class="sl-cf-blank">--</span>';
        var src = v && v.source ? '<span class="in-src">from ' + esc(v.source) + '</span>' : '';
        var mark = v && v.note ? ' <span class="sl-cf-note" title="' + esc(SL.plain(v.note, 200, true)) + '">note</span>' : '';
        return '<td class="sl-cf-cell"><button type="button" class="sl-cf-cellbtn" data-cell-prop="' + p.id + '" data-cell-env="' + e.id +
            '" title="Change ' + esc(p.key) + ' in ' + esc(e.title) + '" data-testid="config-cell">' + inner + mark + src + '</button></td>';
    }

    function sourceCell(p, e) {
        var v = cellOf(p, e.id);
        if (!v || (!v.source && !(v.alsoSetIn || []).length)) return '<td class="col-src sl-cf-blank">--</td>';
        return '<td class="col-src" data-testid="config-source">' + (v.source ? esc(v.source) : '<span class="sl-cf-blank">--</span>') +
            ((v.alsoSetIn || []).length ? '<div class="also">also in ' + v.alsoSetIn.map(esc).join(', ') + '</div>' : '') + '</td>';
    }

    function propertyRow(p) {
        var envs = mConfig.environments;
        return '<tr class="sl-cf-row ' + esc(p.matchState) + '" data-testid="config-row" data-state="' + esc(p.matchState) + '">' +
            '<th scope="row" class="sl-cf-key"><button type="button" class="sl-cf-keybtn" data-prop-edit="' + p.id + '" title="' +
                esc(STATE_LABEL[p.matchState] || '') + ' -- tap to edit this property" data-testid="config-key">' + esc(p.key) + '</button>' +
                (p.typeCode && p.typeCode !== 'text' ? ' <span class="sl-cf-type">' + esc(p.typeCode) + '</span>' : '') +
                (p.description ? '<div class="desc sl-md">' + SL.md(p.description) + '</div>' : '') + '</th>' +
            envs.map(function (e) { return valueCell(p, e); }).join('') +
            envs.map(function (e) { return sourceCell(p, e); }).join('') + '</tr>';
    }

    function group(g) {
        var props = g.properties.filter(function (p) { return shown(p) && found(p, g); });
        if ((showMode() !== 'all' || searching()) && !props.length) return '';
        // While searching, a group with a match is drawn open, without changing its remembered fold.
        var folded = !searching() && Page.fold().isCollapsed('cgroup:' + g.id, false);
        var envs = mConfig.environments;
        var head = '<div class="sl-cardhead sl-tp-grouphead">' +
            '<div class="sl-cardtitle" data-cfold="' + g.id + '" data-testid="config-group-title">' +
                '<span class="chev' + (folded ? ' folded' : '') + '">&#9660;</span> <h3>' + esc(g.title) + '</h3></div>' +
            '<span class="sl-muted sl-count" data-testid="config-group-count">' + g.propertyCount + (g.propertyCount === 1 ? ' property' : ' properties') + '</span>' +
            '<button type="button" class="sl-btn small" data-add-prop="' + g.id + '" data-testid="config-add-property">+ Property</button>' +
            '<button type="button" class="sl-iconbtn" data-group-edit="' + g.id + '" title="Rename or delete this group" aria-label="Edit group ' + esc(g.title) +
                '" data-testid="config-group-edit">&#9998;</button></div>';
        if (folded) return '<div class="sl-card folded sl-tp-group" data-testid="config-group">' + head + '</div>';
        var table = props.length
            ? '<div class="sl-cf-scroll"><table class="sl-cf-table" style="--cf-envs:' + envs.length + '" data-testid="config-table"><thead><tr><th scope="col" class="sl-cf-key">Property</th>' +
                envs.map(function (e) {
                    return '<th scope="col"><button type="button" class="sl-tp-colhead" data-env-edit="' + e.id + '" title="Rename or delete this environment" data-testid="config-env-head">' +
                        esc(e.title) + '</button></th>';
                }).join('') +
                envs.map(function (e) { return '<th scope="col" class="col-src">' + esc(e.title) + ' source</th>'; }).join('') +
                '</tr></thead><tbody>' + props.map(propertyRow).join('') + '</tbody></table></div>'
            : '<div class="sl-muted" style="padding:6px">No properties in this group yet.</div>';
        return '<div class="sl-card sl-tp-group" data-testid="config-group">' + head + table + '</div>';
    }

    function draw() {
        if (!mConfig) return;
        var groups = mConfig.groups.map(group).join('');
        $('pane-config').innerHTML = summary() + (mConfig.groups.length
            ? (groups || '<div class="sl-empty">Nothing matches that filter.</div>')
            : '<div class="sl-empty">No property groups yet.</div>');

        each('[data-cfold]', function (h) {
            h.addEventListener('click', function () { Page.fold().toggle('cgroup:' + h.getAttribute('data-cfold'), false); draw(); });
        });
        each('[data-add-prop]', function (b) { b.addEventListener('click', function () { openProperty(0, +b.getAttribute('data-add-prop')); }); });
        each('[data-group-edit]', function (b) { b.addEventListener('click', function () { openName('group', +b.getAttribute('data-group-edit')); }); });
        each('[data-env-edit]', function (b) { b.addEventListener('click', function () { openName('environment', +b.getAttribute('data-env-edit')); }); });
        each('[data-prop-edit]', function (b) { b.addEventListener('click', function () { openProperty(+b.getAttribute('data-prop-edit'), 0); }); });
        each('[data-cell-prop]', function (b) {
            b.addEventListener('click', function () { openCell(+b.getAttribute('data-cell-prop'), +b.getAttribute('data-cell-env')); });
        });
        toolsSummary();
        SL.status((searching() ? matchCount() + ' of ' : '') + count('properties') + ' properties | ' + count('state:all') + ' same everywhere | ' +
            count('state:some') + ' partly the same | ' + count('state:none') + ' all different | ' +
            (count('state:incomplete') + count('state:empty')) + ' not filled in');
    }

    /** The fold row's line: what the folded-away search and Show are doing, so a narrowed list is never a mystery. */
    function toolsSummary() {
        var bits = [], q = searchText().trim(), sel = $('fConfigShow');
        if (q) bits.push('Searching for "' + q + '"');
        if (showMode() !== 'all' && sel.selectedIndex >= 0) bits.push(sel.options[sel.selectedIndex].text);
        $('configToolsSummary').textContent = bits.length ? bits.join(' | ') : 'Search and tools';
    }

    /** How many properties the search and Show let through. */
    function matchCount() {
        var n = 0;
        mConfig.groups.forEach(function (g) { g.properties.forEach(function (p) { if (shown(p) && found(p, g)) n++; }); });
        return n;
    }

    function each(selector, fn) { Array.prototype.forEach.call($('pane-config').querySelectorAll(selector), fn); }

    // ---- lookups inside the tab ---------------------------------------------------------------

    function groupById(id) { return mConfig.groups.filter(function (g) { return g.id === id; })[0] || null; }
    function envById(id) { return mConfig.environments.filter(function (e) { return e.id === id; })[0] || null; }
    function propertyById(id) {
        for (var i = 0; i < mConfig.groups.length; i++) {
            var hit = mConfig.groups[i].properties.filter(function (p) { return p.id === id; })[0];
            if (hit) return { item: hit, group: mConfig.groups[i] };
        }
        return null;
    }
    function valuesInEnv(envId) {
        var n = 0;
        mConfig.groups.forEach(function (g) { g.properties.forEach(function (p) { if (cellOf(p, envId)) n++; }); });
        return n;
    }

    // ---- a group or an environment: a name, and delete -----------------------------------------

    function openName(kind, id) {
        var isNew = !id;
        var thing = kind === 'group' ? (isNew ? null : groupById(id)) : (isNew ? null : envById(id));
        mEdit = { kind: kind, id: id || 0 };
        SL.warn('cnWarn', '');
        $('cnHeading').textContent = (isNew ? 'New ' : 'Edit ') + (kind === 'group' ? 'property group' : 'environment');
        $('cnLabel').textContent = kind === 'group' ? 'Group name' : 'Environment name';
        $('cnName').placeholder = kind === 'group' ? 'For example: Message broker' : 'For example: Staging';
        $('cnName').value = thing ? thing.title : '';
        $('cnDelete').hidden = isNew;
        $('cnDelete').textContent = kind === 'group' ? 'Delete group' : 'Delete environment';
        SL.open('configNameOverlay');
        setTimeout(function () { $('cnName').focus(); }, 30);
    }

    async function saveName() {
        var name = $('cnName').value.trim();
        if (!name) { SL.warn('cnWarn', mEdit.kind === 'group' ? 'A group needs a name.' : 'An environment needs a name.'); return; }
        var r;
        if (mEdit.kind === 'group') r = mEdit.id ? await SL.put('/api/solutions/config/groups/' + mEdit.id, { title: name })
                                                 : await SL.post('/api/solutions/' + Page.id + '/config/groups', { title: name });
        else r = mEdit.id ? await SL.put('/api/solutions/config/environments/' + mEdit.id, { title: name })
                          : await SL.post('/api/solutions/' + Page.id + '/config/environments', { title: name });
        if (!r || !r.ok || !r.data) { SL.warn('cnWarn', SL.failText(r, 'That could not be saved.')); return; }
        SL.close('configNameOverlay');
        use(r.data);
    }

    async function deleteNamed() {
        var question, url;
        if (mEdit.kind === 'group') {
            var g = groupById(mEdit.id);
            if (!g) return;
            question = 'Delete the property group "' + g.title + '"?\n\nIts ' + g.propertyCount + (g.propertyCount === 1 ? ' property' : ' properties') +
                ' and every value of them go with it. This cannot be undone.';
            url = '/api/solutions/config/groups/' + g.id;
        } else {
            var e = envById(mEdit.id);
            if (!e) return;
            var n = valuesInEnv(e.id);
            question = 'Delete the environment "' + e.title + '"?\n\nIts ' + n + (n === 1 ? ' value goes' : ' values go') +
                ' with it. The properties stay. This cannot be undone.';
            url = '/api/solutions/config/environments/' + e.id;
        }
        if (!window.confirm(question)) return;
        var r = await SL.del(url);
        if (!r || !r.ok || !r.data) { SL.warn('cnWarn', SL.failText(r, 'That could not be deleted.')); return; }
        SL.close('configNameOverlay');
        use(r.data);
    }

    // ---- a property -------------------------------------------------------------------------------

    function openProperty(propertyId, groupId) {
        var found = propertyId ? propertyById(propertyId) : null;
        if (propertyId && !found) return;
        var g = found ? found.group : groupById(groupId);
        mEdit = { kind: 'property', id: propertyId || 0, groupId: g ? g.id : 0 };
        SL.warn('cpWarn', '');
        $('cpHeading').textContent = propertyId ? 'Edit property' : 'New property';
        $('cpKey').value = found ? found.item.key : '';
        $('cpDescription').value = found ? (found.item.description || '') : '';
        SL.fillSelect($('cpType'), mConfig.types.map(function (t) { return { value: t.code, label: t.name }; }), found ? found.item.typeCode : 'text');
        $('cpGroupFld').hidden = !propertyId || mConfig.groups.length < 2;
        SL.fillSelect($('cpGroup'), mConfig.groups.map(function (x) { return { value: String(x.id), label: x.title }; }), g ? g.id : '');
        $('cpDelete').hidden = !propertyId;
        SL.open('configPropertyOverlay');
        setTimeout(function () { $('cpKey').focus(); }, 30);
    }

    async function saveProperty() {
        var key = $('cpKey').value.trim();
        if (!key) { SL.warn('cpWarn', 'A property needs a name, for example logback.smtpHost.'); return; }
        var type = $('cpType').value;
        var was = mEdit.id ? propertyById(mEdit.id) : null;
        if (was && !was.item.secret && type === 'secret' &&
            !window.confirm('Make "' + was.item.key + '" a secret?\n\nIts values are wiped from every environment and are never stored again. Where it is set is kept.')) return;
        var body = { key: key, description: $('cpDescription').value, typeCode: type };
        var r;
        if (mEdit.id) { body.groupId = +$('cpGroup').value || mEdit.groupId; r = await SL.put('/api/solutions/config/properties/' + mEdit.id, body); }
        else r = await SL.post('/api/solutions/config/groups/' + mEdit.groupId + '/properties', body);
        if (!r || !r.ok || !r.data) { SL.warn('cpWarn', SL.failText(r, 'The property could not be saved.')); return; }
        SL.close('configPropertyOverlay');
        use(r.data);
    }

    async function deleteProperty() {
        var found = propertyById(mEdit.id);
        if (!found) return;
        var n = (found.item.values || []).length;
        if (!window.confirm('Delete the property "' + found.item.key + '" from the group "' + found.group.title + '"?\n\n' +
            (n ? 'Its ' + n + (n === 1 ? ' value goes' : ' values go') + ' with it. ' : '') + 'This cannot be undone.')) return;
        var r = await SL.del('/api/solutions/config/properties/' + found.item.id);
        if (!r || !r.ok || !r.data) { SL.warn('cpWarn', SL.failText(r, 'The property could not be deleted.')); return; }
        SL.close('configPropertyOverlay');
        use(r.data);
    }

    // ---- one cell: a property in an environment ---------------------------------------------------

    function openCell(propertyId, envId) {
        var found = propertyById(propertyId), e = envById(envId);
        if (!found || !e) return;
        var p = found.item, v = cellOf(p, envId);
        mEdit = { kind: 'cell', id: propertyId, envId: envId };
        SL.warn('ccWarn', '');
        $('ccHeading').textContent = p.key;
        $('ccContext').textContent = e.title + (p.description ? ' | ' + SL.plain(p.description, 120) : '');
        $('ccValueFld').hidden = !!p.secret;
        $('ccSecretNote').hidden = !p.secret;
        $('ccValue').value = v && v.value ? v.value : '';
        $('ccSource').value = v && v.source ? v.source : '';
        $('ccNote').value = v && v.note ? v.note : '';
        if (mAlsoTags) mAlsoTags.setValues(v && v.alsoSetIn ? v.alsoSetIn : []);
        $('ccClear').hidden = !v;
        var others = mConfig.environments.filter(function (x) { return x.id !== envId; }).map(function (x) {
            var o = cellOf(p, x.id);
            return x.title + ': ' + (p.secret ? '(secret)' : (o && o.value ? o.value : '--'));
        });
        $('ccOthers').textContent = others.length ? 'Elsewhere -- ' + others.join(' | ') : '';
        SL.open('configCellOverlay');
        setTimeout(function () { (p.secret ? $('ccSource') : $('ccValue')).focus(); }, 30);
    }

    async function saveCell(clear) {
        var body = clear ? { value: '', source: '', note: '', alsoSetIn: [] }
                         : { value: $('ccValue').value, source: $('ccSource').value, note: $('ccNote').value,
                             alsoSetIn: mAlsoTags ? mAlsoTags.getValues() : [] };
        if (clear) {
            var found = propertyById(mEdit.id), e = envById(mEdit.envId);
            if (!window.confirm('Clear "' + found.item.key + '" in ' + e.title + '?\n\nIts value, where it is read from, the other places and the note are all removed.')) return;
        }
        var r = await SL.put('/api/solutions/config/properties/' + mEdit.id + '/values/' + mEdit.envId, body);
        if (!r || !r.ok || !r.data) { SL.warn('ccWarn', SL.failText(r, 'That could not be saved.')); return; }
        SL.close('configCellOverlay');
        use(r.data);
    }

    function foldAll(collapsed) {
        if (!mConfig) return;
        // Tony's rule (2026-09-24): remembered, and reaches groups added later.
        Page.fold().setAllForPrefix('cgroup:', collapsed);
        draw();
    }

    // ---- By source: the settings grouped by WHERE they are set, one environment at a time ------------
    //
    // Read-only, from the data already on the page. One environment: a section per place, largest first,
    // listing the settings read from there (value beside each) and, quieter, the ones also set there but
    // overridden; settings with no place yet come last. All: a table of places x environments with counts,
    // which is what shows "production reads thirty things from the service, QA three". Copy puts the
    // view on the clipboard as plain text.

    var NOWHERE = '(not recorded yet)';

    function srcKey() { return 'sl.config.bysource.' + Page.id; }
    function chosenEnv() { try { return sessionStorage.getItem(srcKey()) || ''; } catch (e) { return ''; } }
    function chooseEnv(v) { try { sessionStorage.setItem(srcKey(), String(v)); } catch (e) { /* private mode */ } }

    function allProperties() {
        var out = [];
        mConfig.groups.forEach(function (g) { g.properties.forEach(function (p) { out.push(p); }); });
        return out;
    }

    /** For one environment: [{source, read: [{key, value, secret}], also: [key]}], largest first, the unrecorded last. */
    function bySource(envId) {
        var map = {}, order = [];
        function bucket(name) { if (!map[name]) { map[name] = { source: name, read: [], also: [] }; order.push(name); } return map[name]; }
        allProperties().forEach(function (p) {
            var v = cellOf(p, envId);
            bucket(v && v.source ? v.source : NOWHERE).read.push({ key: p.key, value: v && v.value ? v.value : '', secret: p.secret });
            (v && v.alsoSetIn ? v.alsoSetIn : []).forEach(function (a) { bucket(a).also.push(p.key); });
        });
        return order.map(function (n) { return map[n]; }).sort(function (a, b) {
            if (a.source === NOWHERE) return 1;
            if (b.source === NOWHERE) return -1;
            return (b.read.length - a.read.length) || a.source.localeCompare(b.source);
        });
    }

    /** Places x environments: [{source, counts: [n per environment], total}], biggest first. */
    function sourceMatrix() {
        var envs = mConfig.environments, map = {}, order = [];
        envs.forEach(function (e, i) {
            allProperties().forEach(function (p) {
                var v = cellOf(p, e.id), name = v && v.source ? v.source : NOWHERE;
                if (!map[name]) { map[name] = { source: name, counts: envs.map(function () { return 0; }), total: 0 }; order.push(name); }
                map[name].counts[i]++;
                map[name].total++;
            });
        });
        return order.map(function (n) { return map[n]; }).sort(function (a, b) {
            if (a.source === NOWHERE) return 1;
            if (b.source === NOWHERE) return -1;
            return (b.total - a.total) || a.source.localeCompare(b.source);
        });
    }

    function openBySource() {
        if (!mConfig) return;
        var want = chosenEnv();
        if (want !== 'all' && !envById(+want)) want = mConfig.environments.length ? String(mConfig.environments[mConfig.environments.length - 1].id) : 'all';
        drawBySource(want);
        SL.open('configSourceOverlay');
    }

    function drawBySource(which) {
        chooseEnv(which);
        var envs = mConfig.environments;
        $('csEnvs').innerHTML = envs.map(function (e) {
            return '<button type="button" class="sl-cf-envchip' + (String(e.id) === which ? ' on' : '') + '" data-cs-env="' + e.id + '" data-testid="config-source-env">' + esc(e.title) + '</button>';
        }).join('') + '<button type="button" class="sl-cf-envchip' + (which === 'all' ? ' on' : '') + '" data-cs-env="all" data-testid="config-source-env-all">All, side by side</button>';
        Array.prototype.forEach.call($('csEnvs').querySelectorAll('[data-cs-env]'), function (b) {
            b.addEventListener('click', function () { drawBySource(b.getAttribute('data-cs-env')); });
        });
        if (which === 'all') {
            var rows = sourceMatrix();
            $('csBody').innerHTML = '<table class="sl-cf-srctable" data-testid="config-source-matrix"><thead><tr><th>Where it is set</th>' +
                envs.map(function (e) { return '<th class="n">' + esc(e.title) + '</th>'; }).join('') + '</tr></thead><tbody>' +
                rows.map(function (r) {
                    return '<tr' + (r.source === NOWHERE ? ' class="nowhere"' : '') + '><td>' + esc(r.source) + '</td>' +
                        r.counts.map(function (n) { return '<td class="n' + (n ? '' : ' zero') + '">' + n + '</td>'; }).join('') + '</tr>';
                }).join('') + '</tbody></table>';
            return;
        }
        var sections = bySource(+which);
        $('csBody').innerHTML = sections.map(function (s) {
            return '<section class="sl-cf-srcsec' + (s.source === NOWHERE ? ' nowhere' : '') + '" data-testid="config-source-section">' +
                '<h3>' + esc(s.source) + ' <span class="sl-muted">' + s.read.length + (s.read.length === 1 ? ' setting' : ' settings') + '</span></h3>' +
                (s.read.length ? '<ul>' + s.read.map(function (r) {
                    return '<li><span class="k">' + esc(r.key) + '</span>' +
                        (r.secret ? ' <span class="sl-cf-secret">secret</span>' : (r.value ? ' <span class="v">' + esc(r.value) + '</span>' : '')) + '</li>';
                }).join('') + '</ul>' : '') +
                (s.also.length ? '<div class="also">Also set here, but read from elsewhere: ' + s.also.map(esc).join(', ') + '</div>' : '') +
                '</section>';
        }).join('');
    }

    /** The current view as plain text, for the clipboard. */
    function bySourceText() {
        var which = chosenEnv(), envs = mConfig.environments, lines = [];
        var head = Page.solution.name + ' - where settings are set';
        if (which === 'all') {
            lines.push(head + ' (all environments)', '');
            lines.push(['Where it is set'].concat(envs.map(function (e) { return e.title; })).join('\t'));
            sourceMatrix().forEach(function (r) { lines.push([r.source].concat(r.counts).join('\t')); });
            return lines.join('\n');
        }
        var e = envById(+which);
        lines.push(head + ' - ' + (e ? e.title : ''), '');
        bySource(+which).forEach(function (s) {
            lines.push(s.source + ' (' + s.read.length + ')');
            s.read.forEach(function (r) { lines.push('  ' + r.key + (r.secret ? '  [secret]' : (r.value ? '  = ' + r.value : ''))); });
            if (s.also.length) lines.push('  also set here, read from elsewhere: ' + s.also.join(', '));
            lines.push('');
        });
        return lines.join('\n');
    }

    // ---- print: the same table, through the Leonardo Print Menu -----------------------------------

    // The printed table is built for paper, not copied from the screen: landscape, and ONE column per
    // environment (not a value column and a source column each), with the source and the other places in
    // smaller grey type under the value. Column widths are fixed and every cell may break anywhere, so a long
    // path or URL wraps inside its cell instead of pushing the table off the page -- which is what the first
    // version did (seven columns on portrait; the sources ran off the right edge).
    var PRINT_CSS =
        '@page{size:letter landscape;margin:12mm}' +
        'body{margin:0;font-size:10pt}' +
        '.cf h1{font-size:15pt;margin:0 0 4px;padding-bottom:4px}' +
        '.cf h2{font-size:12pt;margin:14px 0 4px}' +
        '.cf .sum{font-size:9.5pt;margin:4px 0 8px}' +
        '.cf table{width:100%;table-layout:fixed;border-collapse:collapse;font-size:8.5pt;margin:4px 0 8px;-fs-table-paginate:paginate}' +
        '.cf th,.cf td{border:1px solid #D1D5DB;padding:3px 5px;vertical-align:top;text-align:left;' +
        '  word-wrap:break-word;overflow-wrap:break-word;word-break:break-all}' +
        '.cf thead th{background:#F3F4F6;font-size:8.5pt}' +
        '.cf .v{font-family:Consolas,"Courier New",monospace;font-size:8pt}' +
        '.cf .src{color:#6B7280;font-size:7.5pt;margin-top:2px}' +
        '.cf tr.all th,.cf tr.all .v{color:#15803D}' +
        '.cf tr.some th,.cf tr.some .v{color:#C2410C}' +
        '.cf tr.none th,.cf tr.none .v{color:#B91C1C}' +
        '.cf thead{display:table-header-group}' +
        '.cf tr{page-break-inside:avoid}';

    function printCell(p, e) {
        var v = cellOf(p, e.id);
        var value = p.secret ? '<div class="v">(secret)</div>' : (v && v.value ? '<div class="v">' + esc(v.value) + '</div>' : '');
        var src = v && v.source ? '<div class="src">from ' + esc(v.source) + '</div>' : '';
        var also = v && v.alsoSetIn && v.alsoSetIn.length ? '<div class="src">also in ' + v.alsoSetIn.map(esc).join(', ') + '</div>' : '';
        return '<td>' + value + src + also + '</td>';
    }

    function print() {
        var c = mConfig;
        if (!c) return;
        var envs = c.environments;
        // The property column takes a fifth of the width; the environments share the rest equally.
        var keyPct = 20, envPct = Math.floor((100 - keyPct) / Math.max(envs.length, 1));
        var cols = '<colgroup><col style="width:' + keyPct + '%"/>' +
            envs.map(function () { return '<col style="width:' + envPct + '%"/>'; }).join('') + '</colgroup>';
        var html = '<div class="cf"><h1>' + esc(Page.solution.name) + ' - Configuration</h1>' +
            '<div class="sum">' + count('properties') + ' properties | ' + count('state:all') + ' same everywhere (green) | ' + count('state:some') +
            ' partly the same (orange) | ' + count('state:none') + ' all different (red) | ' + (count('state:incomplete') + count('state:empty')) + ' not filled in</div>' +
            c.groups.map(function (g) {
                if (!g.properties.length) return '';
                return '<h2>' + esc(g.title) + '</h2><table>' + cols + '<thead><tr><th>Property</th>' +
                    envs.map(function (e) { return '<th>' + esc(e.title) + '</th>'; }).join('') + '</tr></thead><tbody>' +
                    g.properties.map(function (p) {
                        return '<tr class="' + esc(p.matchState) + '"><th>' + esc(p.key) + '</th>' +
                            envs.map(function (e) { return printCell(p, e); }).join('') + '</tr>';
                    }).join('') + '</tbody></table>';
            }).join('') + '</div>';
        $('printArea').innerHTML = html;
        window.Picasso.printThisPage({ selector: '#printArea', title: Page.solution.name + ' - Configuration', extraCss: PRINT_CSS });
    }

    Page.registerTab('config', {
        init: function () {
            $('fConfigShow').addEventListener('change', function () { setShowMode($('fConfigShow').value); draw(); });
            // The search and every tool fold away under one head row, remembered for this user (Roll Up Tools).
            window.Picasso.rollup.attach({ key: 'leo.sl.config.tools.collapsed', pane: $('toolbar-config'), toggle: $('configToolsToggle') });
            $('fConfigSearch').addEventListener('input', function () { setSearchText($('fConfigSearch').value); draw(); });
            $('fConfigSearch').addEventListener('keydown', function (e) {
                if (e.key !== 'Escape' || !$('fConfigSearch').value) return;
                e.stopPropagation();
                $('fConfigSearch').value = '';
                setSearchText('');
                draw();
            });
            $('btnAddConfigGroup').addEventListener('click', function () { openName('group', 0); });
            $('btnAddConfigEnv').addEventListener('click', function () { openName('environment', 0); });
            $('btnConfigBySource').addEventListener('click', openBySource);
            $('csClose').addEventListener('click', function () { SL.close('configSourceOverlay'); });
            $('csCopy').addEventListener('click', async function () {
                SL.toast(await SL.copyText(bySourceText()) ? 'Copied' : 'Could not copy -- the browser refused.');
            });
            $('btnConfigExpand').addEventListener('click', function () { foldAll(false); });
            $('btnConfigCollapse').addEventListener('click', function () { foldAll(true); });
            $('cnCancel').addEventListener('click', function () { SL.close('configNameOverlay'); });
            $('cnSave').addEventListener('click', function () { SL.guard($('cnSave'), saveName); });
            $('cnDelete').addEventListener('click', function () { SL.guard($('cnDelete'), deleteNamed); });
            $('cpCancel').addEventListener('click', function () { SL.close('configPropertyOverlay'); });
            $('cpSave').addEventListener('click', function () { SL.guard($('cpSave'), saveProperty); });
            $('cpDelete').addEventListener('click', function () { SL.guard($('cpDelete'), deleteProperty); });
            $('ccCancel').addEventListener('click', function () { SL.close('configCellOverlay'); });
            $('ccSave').addEventListener('click', function () { SL.guard($('ccSave'), function () { return saveCell(false); }); });
            $('ccClear').addEventListener('click', function () { SL.guard($('ccClear'), function () { return saveCell(true); }); });
            var sources = function () { return mConfig ? mConfig.sources : []; };
            if (window.PicassoPicker) {
                mSourcePicker = window.PicassoPicker.attach({ input: $('ccSource'), dropdown: $('ccSourceDD'), getItems: sources,
                    allowNew: true, newLabel: 'Add', clearLabel: '(nowhere yet)' });
            }
            if (window.PicassoTags) {
                mAlsoTags = window.PicassoTags.attach({ input: $('ccAlsoInput'), chips: $('ccAlsoChips'), dropdown: $('ccAlsoDD'), getOptions: sources });
            }
        },
        show: load,
        print: print,
        /** A deep link (?configProperty=) names the row to flash; call before the tab is first shown. */
        flash: function (propertyId) { mFlashId = propertyId; }
    });
})();
