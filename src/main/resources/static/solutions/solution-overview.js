(function () {
    'use strict';

    // The Overview's bands (Story 5667, 2026-10-30): "Solutions needs an overview conception Entity IV
    // has for its tabs". A record's Overview in Entity IV shows every section on one page; this does the
    // same for a solution. Under the summary card, every tab is a band -- its title, a count, an Open
    // button and its newest few rows -- and a row opens that record on its own tab. Projects keep their
    // full rows / cards (solution.js draws them); the other bands are read-only previews, so a tab's own
    // tools, dialogs and element ids are never drawn twice on the page.
    //
    // Folds are per solution, remembered in the DATABASE (PicassoGroupState through PicassoViewState),
    // default open. Fold All folds or opens every band. Arrange drags a band by its grip; the order is one
    // order for every solution (leo.sl.ov.order) and a band with nothing in it is hidden but keeps its
    // place. Data is read when the Overview is shown; a fold or a project redraw reuses what was read.

    var Page = window.SolutionPage, SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc;
    var ORDER_KEY = 'leo.sl.ov.order';
    var ROWS = 6;

    var BANDS = [
        { key: 'since',         title: 'Since you were last here' },
        { key: 'projects',      title: 'Projects' },
        { key: 'items',         title: 'Items',            tab: 'items' },
        { key: 'conversations', title: 'Conversations',    tab: 'conversations' },
        { key: 'log',           title: 'Log',              tab: 'log' },
        // Story 5695 (Echo): the Log tab's Questions and Decisions views, each as a band of its own.
        { key: 'questions',     title: 'Open questions',   tab: 'log', view: 'questions' },
        { key: 'decisions',     title: 'Decisions',        tab: 'log', view: 'decisions' },
        { key: 'notes',         title: 'Notes',            tab: 'notes' },
        { key: 'topics',        title: 'Topics',           tab: 'topics' },
        { key: 'files',         title: 'Files',            tab: 'files' },
        { key: 'tests',         title: 'Testing',          tab: 'tests' },
        { key: 'qa',            title: 'QA',               tab: 'qa' },
        { key: 'terms',         title: 'Terms',            tab: 'terms' },
        { key: 'config',        title: 'Configuration',    tab: 'config' },
        { key: 'grid',          title: 'Configuration II', tab: 'grid' },
        { key: 'web',           title: 'Web',              tab: 'web' },
        { key: 'sharing',       title: 'Who can reach this from outside' }
    ];

    var mFolds = null, mFoldsFor = 0, mSynced = false;
    var mData = {}, mDataFor = 0, mLoading = null;

    function folds() {
        if (!mFolds || mFoldsFor !== Page.id) {
            mFolds = window.PicassoGroupState.create({ key: 'leo.sl.ov.' + Page.id, defaultOpen: true });
            mFoldsFor = Page.id;
        }
        return mFolds;
    }

    function order() {
        var saved = String((window.PicassoViewState && window.PicassoViewState.get(ORDER_KEY)) || '').split(',');
        var keys = BANDS.map(function (b) { return b.key; });
        var out = saved.filter(function (k) { return keys.indexOf(k) >= 0; });
        // A band newer than the saved order goes where BANDS puts it: after its nearest older neighbor
        // that is already placed, or first when none is (so 'Since you were last here' leads).
        keys.forEach(function (k, i) {
            if (out.indexOf(k) >= 0) return;
            var at = 0;
            for (var j = i - 1; j >= 0; j--) { var p = out.indexOf(keys[j]); if (p >= 0) { at = p + 1; break; } }
            out.splice(at, 0, k);
        });
        return out.map(function (k) { return BANDS.filter(function (b) { return b.key === k; })[0]; });
    }

    // ---- reading each tab's newest rows -------------------------------------------------------

    function list(r) { return r && r.ok && Array.isArray(r.data) ? r.data : []; }
    function newest(rows, field) {
        return rows.slice().sort(function (a, b) { return String(b[field] || '').localeCompare(String(a[field] || '')); });
    }
    function failed(r, what) { return { error: window.LWAuth.errorText(r, what) }; }

    async function webEntityId() {
        var r = await SL.get('/api/entity-browse?type=222&identityId=' + Page.id);
        var row = r && r.ok && r.data && r.data.rows && r.data.rows[0];
        return row ? (+row.entityId || 0) : 0;
    }

    // ---- Since you were last here (2026-11-02) ---------------------------------------------------
    // The first read of a page load is a VISIT: the server counts it (a new visit only after an hour
    // away, so a reload never empties the band) and answers what OTHERS changed since the last one.
    // Later reads (the Overview shown again) only ask. The band hides when nothing changed, and the
    // same count shows on the Overview tab, so it costs no room on a quiet day.

    var KIND = { item: 'Item', log: 'Log', note: 'Note', file: 'File', message: 'Message', term: 'Term', topic: 'Topic',
                 question: 'Question', decision: 'Decision' };   // Story 5695 (Echo)
    var mVisited = 0, mSince = null, mSinceSeq = 0;   // mSinceSeq: an answer asked before Caught up is dropped

    async function readSince(sid, more) {
        var r, seq = mSinceSeq;
        if (!more && mVisited !== sid) {
            r = await SL.post('/api/solutions/' + sid + '/visit', {});
            if (r && r.ok) mVisited = sid;
        } else {
            r = await SL.get('/api/solutions/' + sid + '/since?offset=' + (more ? mSince.changes.length : 0) + '&pageSize=50');
        }
        if (!r || !r.ok || !r.data) return failed(r, 'Could not read what changed');
        if (seq !== mSinceSeq && mSince) return sinceBand();
        if (more && mSince) r.data.changes = mSince.changes.concat(r.data.changes || []);
        mSince = r.data;
        return sinceBand();
    }

    function sinceBand() {
        var d = mSince, total = d.total || 0, shown = Math.min(d.changes.length, d.showAll ? d.changes.length : ROWS);
        setBadge(total);
        var when = d.firstVisit ? 'in the last week' : 'since ' + SL.dateTime(d.since);
        var rows = d.changes.slice(0, shown).map(function (c) {
            return '<button type="button" class="sl-ov-row sl-since-row" data-ov-go="since-' + esc(c.kind) + '" data-ov-id="' + c.id + '" data-testid="overview-since-row">' +
                '<span class="sl-kind ' + esc(c.kind) + '">' + esc(KIND[c.kind] || c.kind) + '</span>' +
                '<span class="sl-ov-main">' + esc(SL.plain(c.title || '', 140)) + '</span>' +
                '<span class="sl-ov-sub">' + esc([(c.whoName || c.who || 'Someone') + ' ' + c.action, SL.dateTime(c.changedDate)].join(' | ')) + '</span></button>';
        }).join('');
        var tools = '<div class="sl-since-tools">';
        if (shown < d.changes.length) tools += '<button type="button" class="sl-ov-more" data-ov-act="since-all" data-testid="overview-since-all">Show all ' + d.changes.length + '</button>';
        else if (d.changes.length < total) tools += '<button type="button" class="sl-ov-more" data-ov-act="since-more" data-testid="overview-since-more">' + (total - d.changes.length) + ' more</button>';
        tools += '<button type="button" class="sl-btn small" data-ov-act="since-done" data-testid="overview-since-caught-up">Caught up</button></div>';
        return {
            total: total, count: total + (total === 1 ? ' change ' : ' changes ') + when,
            html: rows + tools, rows: []
        };
    }

    function setBadge(n) {
        var b = $('badgeOverview');
        if (!b) return;
        b.textContent = n > 0 ? String(n) : '';
        b.style.background = '#15803D';
    }

    var ACTIONS = {
        'since-all': function () { mSince.showAll = true; mData.since = sinceBand(); return true; },
        'since-more': async function () { mData.since = await readSince(Page.id, true); mSince.showAll = true; mData.since = sinceBand(); return true; },
        'since-done': async function () {
            mSinceSeq++;
            var r = await SL.post('/api/solutions/' + Page.id + '/caught-up', {});
            if (!r || !r.ok || !r.data) { SL.toast(SL.failText(r, 'Could not mark it read.')); return false; }
            mSince = r.data;
            mData.since = sinceBand();
            return true;
        }
    };

    var LOADERS = {
        since: function (sid) { return readSince(sid, false); },
        topics: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/topics?pageSize=' + ROWS);
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the topics');
            var total = r.data.total || 0;
            return {
                total: total, count: total + (total === 1 ? ' topic' : ' topics'), more: total,
                rows: (r.data.topics || []).map(function (t) {
                    return { id: t.id, main: esc(SL.plain(t.name, 140)),
                             sub: esc([t.memberCount + (t.memberCount === 1 ? ' record' : ' records'), t.summary ? SL.plain(t.summary, 120) : ''].filter(Boolean).join(' | ')) };
                })
            };
        },
        items: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/items');
            if (!r || !r.ok) return failed(r, 'Could not read the items');
            var all = list(r), open = all.filter(function (i) { return !i.closed; });
            return {
                total: all.length, count: open.length + ' open of ' + all.length,
                more: open.length,
                rows: newest(open, 'modifiedDate').slice(0, ROWS).map(function (i) {
                    return { id: i.id, main: SL.pill(i.statusName, i.colorHex) + ' ' + esc(SL.plain(i.title, 140)),
                             sub: [i.projectName, i.assigneeName].filter(Boolean).map(esc).join(' | ') };
                }),
                empty: all.length ? '' : 'No items yet.'
            };
        },
        conversations: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/conversations');
            if (!r || !r.ok) return failed(r, 'Could not read the conversations');
            var all = list(r), waiting = all.filter(function (c) { return c.awaitingTonyCount > 0; }).length;
            var sorted = all.slice().sort(function (a, b) {
                return (b.awaitingTonyCount > 0) - (a.awaitingTonyCount > 0) || String(b.modifiedDate || '').localeCompare(String(a.modifiedDate || ''));
            });
            return {
                total: all.length, count: all.length + (all.length === 1 ? ' thread' : ' threads') + (waiting ? ' | ' + waiting + ' waiting on you' : ''),
                more: all.length,
                rows: sorted.slice(0, ROWS).map(function (c) {
                    var state = c.status === 'closed' ? '<span class="sl-chip">Resolved</span> '
                              : (c.awaitingTonyCount > 0 ? '<span class="sl-chip wait">Waiting on you</span> ' : '');
                    return { id: c.conversationId, main: state + esc(SL.plain(c.title || 'Untitled thread', 140)),
                             sub: c.messageCount + (c.messageCount === 1 ? ' message' : ' messages') +
                                  (c.lastSender ? ' | last from ' + esc(c.lastSender) : '') + (c.modifiedDate ? ' | ' + SL.date(c.modifiedDate) : '') };
                })
            };
        },
        log: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/log?pageSize=' + ROWS);
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the log');
            var total = r.data.total || 0;
            return {
                total: total, count: total + (total === 1 ? ' entry' : ' entries'), more: total,
                rows: (r.data.entries || []).map(function (e) {
                    return { id: e.id, main: esc(SL.plain(e.text, 160)),
                             sub: ['#' + e.sequenceNumber, SL.date(e.logDate || e.createdDate), e.authorName, e.typeName].filter(Boolean).map(esc).join(' | ') };
                })
            };
        },
        // Story 5695 (Echo): the open questions, newest first; the current decisions, proposed ones first.
        questions: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/questions?status=open&pageSize=' + ROWS);
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the questions');
            var open = r.data.total || 0, all = (r.data.counts || {}).all || 0;
            return {
                total: open, count: open + ' open' + (all > open ? ' of ' + all : ''), more: open,
                rows: (r.data.questions || []).map(function (q) {
                    return { id: q.id, main: esc(SL.plain(q.text, 160)),
                             sub: [q.askedByName ? 'asked by ' + q.askedByName : 'asked', SL.date(q.createdDate), q.ownerName ? 'for ' + q.ownerName : '']
                                 .filter(Boolean).map(esc).join(' | ') };
                })
            };
        },
        decisions: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/decisions?status=current&pageSize=' + ROWS);
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the decisions');
            var c = r.data.counts || {}, current = r.data.total || 0, proposed = c['status:proposed'] || 0;
            return {
                total: current, count: current + (current === 1 ? ' decision' : ' decisions') + (proposed ? ' | ' + proposed + ' proposed' : ''),
                more: current,
                rows: (r.data.decisions || []).map(function (d) {
                    return { id: d.id, main: (d.statusCode === 'proposed' ? '<span class="sl-chip wait">Proposed</span> ' : '') + esc(SL.plain(d.title, 160)),
                             sub: [d.decidedDate ? 'decided ' + SL.date(d.decidedDate) : '', d.decidedByName, d.logSequenceNumber ? 'log #' + d.logSequenceNumber : '']
                                 .filter(Boolean).map(esc).join(' | ') };
                })
            };
        },
        notes: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/notes');
            if (!r || !r.ok) return failed(r, 'Could not read the notes');
            var all = list(r);
            return {
                total: all.length, count: all.length + (all.length === 1 ? ' note' : ' notes'), more: all.length,
                rows: newest(all, 'modifiedDate').slice(0, ROWS).map(function (n) {
                    return { id: n.id, main: esc(SL.plain(n.title, 140)),
                             sub: [n.typeName, n.modifiedDate ? 'changed ' + SL.date(n.modifiedDate) : ''].filter(Boolean).map(esc).join(' | ') };
                })
            };
        },
        files: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/files');
            if (!r || !r.ok) return failed(r, 'Could not read the files');
            var all = list(r);
            return {
                total: all.length, count: all.length + (all.length === 1 ? ' file' : ' files'), more: all.length,
                rows: newest(all, 'createdDate').slice(0, ROWS).map(function (f) {
                    return { id: f.id, main: esc(SL.plain(f.title || f.originalFileName, 140)),
                             sub: [f.title && f.title !== f.originalFileName ? f.originalFileName : '', SL.date(f.createdDate), size(f.sizeBytes), f.itemTitle]
                                 .filter(Boolean).map(esc).join(' | ') };
                })
            };
        },
        tests: function (sid) { return plans(sid, 'dev', 'checklist'); },
        qa:    function (sid) { return plans(sid, 'qa', 'QA checklist'); },
        terms: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/terms?groupBy=none&pageSize=' + ROWS);
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the terms');
            var total = r.data.total || 0;
            return {
                total: total, count: total + (total === 1 ? ' term' : ' terms'), more: total,
                rows: (r.data.terms || []).map(function (t) {
                    return { id: t.id, main: esc(SL.plain(t.term, 120)), sub: esc(SL.plain(t.definition, 160)) };
                })
            };
        },
        config: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/config');
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the configuration');
            var c = r.data.counts || {}, n = function (k) { return c[k] || 0; };
            return configBand(n('properties'), (r.data.environments || []).length, 'environment', [
                [n('state:all'), 'same everywhere', 'all'], [n('state:some'), 'partly the same', 'some'],
                [n('state:none'), 'all different', 'none'], [n('state:incomplete') + n('state:empty'), 'not filled in', 'gap'], [n('state:secret'), 'secret', 'gap']]);
        },
        grid: async function (sid) {
            var r = await SL.get('/api/solutions/' + sid + '/config-grid');
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read Configuration II');
            var c = r.data.counts || {}, n = function (k) { return c[k] || 0; };
            return configBand(n('properties'), (r.data.columns || []).length, 'column', [
                [n('state:all.all'), 'same everywhere', 'all'], [n('state:all.some'), 'partly the same', 'some'],
                [n('state:all.none'), 'all different', 'none'], [n('state:all.incomplete') + n('state:all.empty'), 'not filled in', 'gap']]);
        },
        web: async function () {
            var eid = await webEntityId();
            if (!eid || !window.EntityWeb) return { total: 0, rows: [] };
            var r = await window.LWAuth.apiGet(window.EntityWeb.firstPageUrl(eid));
            if (!r || !r.ok || !r.data) return failed(r, 'Could not read the web addresses');
            var total = r.data.total || 0;
            return {
                total: total, count: total + (total === 1 ? ' address' : ' addresses'), more: total,
                rows: (r.data.rows || []).slice(0, ROWS).map(function (w) {
                    return { id: 0, main: esc(SL.plain(w.webAddressName || w.webAddress || '(no address)', 140)),
                             sub: [w.webAddressType, w.webAddressName ? w.webAddress : ''].filter(Boolean).map(esc).join(' | ') };
                })
            };
        }
    };

    async function plans(sid, kind, noun) {
        var r = await SL.get('/api/solutions/' + sid + '/test-plans?kind=' + kind);
        if (!r || !r.ok) return failed(r, 'Could not read the ' + noun + 's');
        var all = Array.isArray(r.data) ? r.data : (r.data && r.data.rows) || [];
        return {
            total: all.length, count: all.length + ' ' + noun + (all.length === 1 ? '' : 's'), more: all.length,
            rows: all.slice(0, ROWS).map(function (p) {
                var of = Math.max(0, (p.cellCount || 0) - (p.skippedCount || 0));
                return { id: p.id, main: esc(SL.plain(p.title, 140)),
                         sub: (p.caseCount || 0) + ((p.caseCount || 0) === 1 ? ' test' : ' tests') + ' | ' + (p.checkedCount || 0) + ' of ' + of + ' checked' };
            })
        };
    }

    function configBand(total, places, placeNoun, chips) {
        var html = chips.filter(function (c) { return c[0] > 0; }).map(function (c) {
            return '<span class="sl-cf-chip ' + c[2] + '">' + c[0] + ' ' + c[1] + '</span>';
        }).join('');
        return { total: total, count: total + (total === 1 ? ' property' : ' properties') + ' | ' + places + ' ' + placeNoun + (places === 1 ? '' : 's'),
                 html: html ? '<div class="sl-cf-chips sl-ov-chips">' + html + '</div>' : '', rows: [] };
    }

    function size(bytes) {
        if (!bytes) return '';
        if (bytes < 1024) return bytes + ' bytes';
        if (bytes < 1048576) return Math.round(bytes / 1024) + ' KB';
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    /** Reads every band at once (one round trip each, side by side). A failing band says why. */
    function load() {
        var sid = Page.id;
        mLoading = Promise.all(Object.keys(LOADERS).map(function (k) {
            return Promise.resolve().then(function () { return LOADERS[k](sid); })
                .catch(function (e) { return { error: 'Could not read it: ' + (e && e.message ? e.message : e) }; })
                .then(function (d) { return [k, d]; });
        })).then(function (pairs) {
            if (sid !== Page.id) return false;
            mData = {}; mDataFor = sid;
            pairs.forEach(function (p) { mData[p[0]] = p[1]; });
            return true;
        });
        return mLoading;
    }

    // ---- drawing ------------------------------------------------------------------------------

    function bandHtml(b, fixed) {
        var d = fixed[b.key] ? fixed[b.key] : mData[b.key];
        var loading = !fixed[b.key] && mDataFor !== Page.id;
        var hide = !loading && d && !d.error && b.key !== 'projects' && b.key !== 'sharing' && !(d.total > 0);
        var open = folds().isOpen(b.key);
        var count = loading ? 'Reading&hellip;' : (d && d.count ? esc(d.count) : '');
        var head = '<div class="sl-ov-head">' +
            '<span class="sl-grip" data-ov-grip title="Drag to move this band up or down" aria-label="Drag to move the ' + esc(b.title) + ' band" data-testid="overview-band-grip">&#8942;&#8942;</span>' +
            '<button type="button" class="sl-ov-toggle" data-ov-toggle="' + b.key + '" aria-expanded="' + (open ? 'true' : 'false') + '" data-testid="overview-band-toggle">' +
                '<span class="chev' + (open ? '' : ' folded') + '">&#9660;</span><span class="sl-ov-title">' + esc(b.title) + '</span>' +
                (count ? '<span class="sl-ov-count" data-testid="overview-band-count">' + count + '</span>' : '') + '</button>' +
            (b.tab ? '<button type="button" class="sl-btn small" data-ov-open="' + b.tab + '"' + (b.view ? ' data-ov-view="' + b.view + '"' : '') +
                ' data-testid="overview-band-open">Open</button>' : '') +
            '</div>';
        var body = '';
        if (open) {
            if (fixed[b.key]) body = fixed[b.key].html;
            else if (loading) body = '<div class="sl-muted sl-ov-note">Reading&hellip;</div>';
            else if (d.error) body = '<div class="sl-ov-note sl-ov-error" data-testid="overview-band-error">' + esc(d.error) + '</div>';
            else {
                body = (d.html || '') + (d.rows || []).map(function (r) {
                    return '<button type="button" class="sl-ov-row" data-ov-go="' + b.key + '" data-ov-id="' + r.id + '" data-testid="overview-row">' +
                        '<span class="sl-ov-main">' + r.main + '</span>' + (r.sub ? '<span class="sl-ov-sub">' + r.sub + '</span>' : '') + '</button>';
                }).join('');
                var shown = (d.rows || []).length;
                if (b.tab && d.more > shown) {
                    body += '<button type="button" class="sl-ov-more" data-ov-open="' + b.tab + '"' + (b.view ? ' data-ov-view="' + b.view + '"' : '') +
                        ' data-testid="overview-band-more">' +
                        (shown ? (d.more - shown) + ' more' : 'See them') + ' on the ' + esc(b.title) + ' tab</button>';
                }
            }
        }
        return '<section class="sl-ov-band' + (open ? '' : ' folded') + '" data-ov-band="' + b.key + '"' + (hide ? ' hidden' : '') +
            ' data-testid="overview-band-' + b.key + '">' + head + (open ? '<div class="sl-ov-body">' + body + '</div>' : '') + '</section>';
    }

    /**
     * Draws the bands into host. fixed = {projects: {html, count}, sharing: {html}} -- the parts
     * solution.js owns. refresh = read every band again (the tab was just shown). Returns once the
     * bands are on screen; the data bands fill in when their answers arrive.
     */
    function draw(host, fixed, refresh) {
        var first = !mSynced;
        mSynced = true;
        paint(host, fixed);
        if (first) {
            // Another device's folds and order, once per page load.
            Promise.all([folds().sync(), window.PicassoViewState ? window.PicassoViewState.sync('leo.sl.ov.order') : false])
                .then(function (ch) { if ((ch[0] || ch[1]) && host.isConnected) paint(host, fixed); });
        }
        if (refresh || mDataFor !== Page.id) {
            load().then(function (ok) { if (ok && host.isConnected) paint(host, fixed); });
        }
    }

    /** Every paint draws the bands afresh; solution.js wires its own parts again in fixed.after. */
    function paint(host, fixed) {
        host.innerHTML = order().map(function (b) { return bandHtml(b, fixed); }).join('');
        wire(host, fixed);
        paintFoldAll();
        if (fixed.after) fixed.after();
    }

    function wire(host, fixed) {
        Array.prototype.forEach.call(host.querySelectorAll('[data-ov-toggle]'), function (t) {
            t.addEventListener('click', function () {
                folds().toggle(t.getAttribute('data-ov-toggle'));

                paint(host, fixed);
            });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-ov-open]'), function (b) {
            b.addEventListener('click', function () {
                // Story 5695 (Echo): a band that is one of the Log tab's views opens the tab on that view.
                if (b.getAttribute('data-ov-view') && Page.tabs.log && Page.tabs.log.openView) Page.tabs.log.openView(b.getAttribute('data-ov-view'));
                Page.go(b.getAttribute('data-ov-open'));
            });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-ov-act]'), function (b) {
            b.addEventListener('click', function () {
                SL.guard(b, async function () { if (await ACTIONS[b.getAttribute('data-ov-act')]()) paint(host, fixed); });
            });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-ov-go]'), function (row) {
            row.addEventListener('click', function () { openRow(row.getAttribute('data-ov-go'), +row.getAttribute('data-ov-id')); });
        });
        Array.prototype.forEach.call(host.querySelectorAll('[data-ov-grip]'), function (g) {
            // While a band travels, every band shrinks to its head, so the one moving stays under the finger.
            g.addEventListener('pointerdown', function () {
                host.classList.add('sl-ov-dragging');
                var off = function () { host.classList.remove('sl-ov-dragging'); document.removeEventListener('pointerup', off); document.removeEventListener('pointercancel', off); };
                document.addEventListener('pointerup', off);
                document.addEventListener('pointercancel', off);
            });
            Page.wireProjectGrip(g, '[data-ov-band]', 'data-ov-band', null, function (keys) {
                if (window.PicassoViewState) window.PicassoViewState.set(ORDER_KEY, keys.join(','));

                paint(host, fixed);
            });
        });
    }

    /** The same moves the Search tab makes: open the tab, then the record on it. */
    function openRow(key, id) {
        var t = Page.tabs;
        if (key.indexOf('since-') === 0) { window.SolutionLinks.openRecord(key.substring(6), id); return; }
        switch (key) {
            case 'items':         Page.go('items'); t.items.flash(id); break;
            case 'conversations': Page.go('conversations'); t.conversations.openThread(id); break;
            case 'log':           t.log.flash(id); Page.go('log'); break;
            case 'notes':         Page.go('notes'); t.notes.openNote(id); break;
            case 'files':         Page.go('files'); t.files.openFile(id); break;
            case 'tests':
            case 'qa':            t[key].choosePlan(id); Page.go(key); break;
            case 'terms':         Page.go('terms'); t.terms.openTerm(id); break;
            case 'topics':        Page.go('topics'); t.topics.openTopic(id); break;
            case 'questions':     window.SolutionDecisions.openQuestion(id); break;   // Story 5695 (Echo)
            case 'decisions':     window.SolutionDecisions.openDecision(id); break;
            default:              Page.go(key);
        }
    }

    // ---- Fold All (the double caret on the Overview's tool row) ----------------------------------

    function anyOpen() {
        return BANDS.some(function (b) {
            var el = document.querySelector('[data-ov-band="' + b.key + '"]');
            return el && !el.hidden && folds().isOpen(b.key);
        });
    }

    function paintFoldAll() {
        var btn = $('btnOvFoldAll');
        if (!btn) return;
        var open = anyOpen();
        btn.innerHTML = open ? '&#9656;&#9656;' : '&#9662;&#9662;';
        btn.title = open ? 'Fold every band' : 'Open every band';
        btn.setAttribute('aria-label', btn.title);
    }

    function foldAll(host, fixed) {
        if (anyOpen()) folds().collapseAll(); else folds().expandAll();

        paint(host, fixed);
    }

    window.SolutionOverview = {
        draw: draw, foldAll: foldAll, isOpen: function (k) { return folds().isOpen(k); },
        /** Counts the visit and paints the Overview tab's count, whatever tab the page opened on. */
        visit: function () { return readSince(Page.id, false); }
    };
})();
