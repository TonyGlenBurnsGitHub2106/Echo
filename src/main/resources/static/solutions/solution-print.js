(function () {
    'use strict';

    // Printed reports for one solution (Story 3493). There is no Report button anywhere: the report
    // IS what the standard Leonardo Print Menu prints (Display / printer / download / Notifications /
    // Reading Nook / email ...), so every destination that menu offers works for it.
    //
    //   Overview  -> the whole solution: a cover page, then every project on its own page
    //   Items     -> the project picked in the Project filter (every project when none is)
    //   a project card's Print -> that one project
    //   Log / Notes / Files / Conversations -> what the tab is showing
    //
    // The PDF is rendered server side from XHTML with plain CSS -- tables and page breaks, no flex.
    // The report is built in a detached holder and handed to Picasso.printThisPage by selector.
    // Printing is a PAGE feature: an outside agent has no page, and the print endpoints sit outside
    // the only path it can reach.
    //
    // Story 3503: the builder no longer reads the Solution page's globals. It works from a context
    // {id, solution, projects} -- the Solution page hands over its own, and a record's dashboard
    // builds one with SolutionReport.load(id) -- so the same report is drawn in both places.
    // window.SolutionReport = { CSS, load, buildHtml }.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc;

    var CSS =
        'h1{margin-bottom:4px;} .sub{color:#555;font-size:10pt;margin:0 0 14px;}' +
        'h2{page-break-before:always;color:#0A66C2;border-bottom:1px solid #0A66C2;padding-bottom:4px;font-size:16pt;}' +
        'h2.first{page-break-before:auto;} h3{margin-top:18px;border-bottom:1px solid #DDD;padding-bottom:2px;}' +
        'table{font-size:9.5pt;} th{font-size:9pt;} td.n,th.n{text-align:right;white-space:nowrap;} td.nw{white-space:nowrap;}' +
        '.box{border:1px solid #CCC;background:#F7F8FA;padding:8px 12px;margin:8px 0;}' +
        '.muted{color:#666;font-size:9.5pt;} .done{color:#15803D;}' +
        '.mono{font-family:Consolas,monospace;font-size:9pt;white-space:pre-wrap;}' +
        '.pre{white-space:pre-wrap;} .pill{font-weight:bold;} .md p{margin:0 0 6px;} .md ul,.md ol{margin:0 0 6px;padding-left:18px;}' +
        '.md pre{font-family:Consolas,monospace;font-size:9pt;white-space:pre-wrap;background:#F3F4F6;padding:6px;}' +
        '.detail{font-size:9pt;color:#333;margin-top:3px;} .md code{font-family:Consolas,monospace;font-size:8.5pt;}';

    function pct(closed, total) { return total > 0 ? Math.round(closed * 100 / total) + '%' : '-'; }

    /** Every page of the journal, oldest first -- a report shows the order things were done. */
    async function allLog(ctx) {
        var out = [], offset = 0, total = 1;
        while (offset < total && offset < 5000) {
            var r = await SL.get('/api/solutions/' + ctx.id + '/log?ascending=true&pageSize=500&offset=' + offset);
            if (!r || !r.ok || !r.data) break;
            var page = r.data.entries || [];
            if (!page.length) break;
            out = out.concat(page);
            total = r.data.total || 0;
            offset += page.length;
        }
        return out;
    }

    /**
     * Every item's WHOLE detail, keyed by item id. The item list carries only a 300-character
     * preview, and a printed plan is read when the system it describes may be gone -- the Machine
     * Recovery items keep their commands in the detail, so a cut-off preview prints half a step.
     * Ten requests at a time so a large solution does not flood the server.
     */
    async function fullDetails(items) {
        var out = {}, wanted = items.filter(function (i) { return i.detailPreview; });
        for (var n = 0; n < wanted.length; n += 10) {
            var batch = wanted.slice(n, n + 10);
            var got = await Promise.all(batch.map(function (i) { return SL.get('/api/solutions/items/' + i.id); }));
            got.forEach(function (r, k) { if (r && r.ok && r.data) out[batch[k].id] = r.data.detail || ''; });
        }
        return out;
    }

    async function gather(ctx) {
        var results = await Promise.all([
            SL.get('/api/solutions/' + ctx.id + '/items'), allLog(ctx),
            SL.get('/api/solutions/' + ctx.id + '/notes'), SL.get('/api/solutions/' + ctx.id + '/files')
        ]);
        var heads = (results[2] && results[2].ok && Array.isArray(results[2].data)) ? results[2].data : [];
        var notes = await Promise.all(heads.map(function (d) { return SL.get('/api/solutions/notes/' + d.id); }));
        // The project list carries a 300-character preview; a report prints the whole description.
        var full = await Promise.all(ctx.projects.map(function (p) { return SL.get('/api/solutions/projects/' + p.id); }));
        var descriptions = {};
        full.forEach(function (r) { if (r && r.ok) descriptions[r.data.id] = r.data.description; });
        var items = (results[0] && results[0].ok && Array.isArray(results[0].data)) ? results[0].data : [];
        return {
            descriptions: descriptions,
            items: items,
            details: await fullDetails(items),
            log: results[1],
            notes: notes.filter(function (r) { return r && r.ok; }).map(function (r) { return r.data; }),
            files: (results[3] && results[3].ok && Array.isArray(results[3].data)) ? results[3].data : []
        };
    }

    // ---- sections ---------------------------------------------------------------------

    /** An item's text for the report: the whole detail when it was fetched, else the list preview. */
    function itemDetailHtml(i, details, pad) {
        var whole = details && Object.prototype.hasOwnProperty.call(details, i.id) ? details[i.id] : null;
        if (whole) return '<div class="md detail">' + SL.md(whole) + '</div>';
        return i.detailPreview ? '<div class="muted">' + pad + esc(SL.plain(i.detailPreview, 300, true)) + '</div>' : '';
    }

    function itemsTable(items, details) {
        if (!items.length) return '<p class="muted">No items yet.</p>';
        var byParent = {};
        items.forEach(function (i) { (byParent[i.parentItemId || 0] = byParent[i.parentItemId || 0] || []).push(i); });
        var rows = [];
        (function walk(parent, prefix, depth) {
            (byParent[parent] || []).forEach(function (i, n) {
                var number = prefix + (n + 1);
                var pad = new Array(depth + 1).join('&#160;&#160;&#160;&#160;');
                rows.push('<tr><td class="nw">' + number + '</td><td>' + pad + '<span' + (i.closed ? ' class="done"' : '') + '>' + (i.closed ? '&#10003; ' : '') + esc(i.title) + '</span>' +
                    itemDetailHtml(i, details, pad) + '</td>' +
                    '<td class="nw pill">' + esc(i.statusName) + '</td><td class="nw">' + esc(i.assigneeName || '') + '</td>' +
                    '<td class="n">' + (i.priority || '') + '</td><td class="nw">' + SL.date(i.dueDate) + '</td><td class="nw">' + SL.date(i.completedDate) + '</td></tr>');
                walk(i.id, number + '.', depth + 1);
            });
        })(0, '', 0);
        return '<table><thead><tr><th>#</th><th>Item</th><th>Status</th><th>Assigned</th><th class="n">Pri</th><th>Due</th><th>Closed</th></tr></thead><tbody>' +
            rows.join('') + '</tbody></table>';
    }

    function logTable(entries) {
        if (!entries.length) return '<p class="muted">Nothing logged.</p>';
        return '<table><thead><tr><th class="n">#</th><th>When</th><th>Who</th><th>Kind</th><th>What was done</th></tr></thead><tbody>' +
            entries.map(function (e) {
                return '<tr><td class="n">' + e.sequenceNumber + '</td><td class="nw">' + SL.dateTime(e.logDate) + '</td><td class="nw">' + esc(e.authorName || '') +
                    '</td><td class="nw">' + esc(e.typeName) + '</td><td>' + (e.itemTitle ? '<div class="muted">' + esc(e.itemTitle) + '</div>' : '') +
                    (e.typeCode === 'command' ? '<div class="mono">' + esc(e.text) + (e.truncated ? ' ...' : '') + '</div>' : '<div class="md">' + SL.md(e.text, e.truncated) + '</div>') + '</td></tr>';
            }).join('') + '</tbody></table>';
    }

    function noteBlock(d) {
        var holder = document.createElement('div');
        SL.renderMarkdown(holder, d.body || '');                 // agent-written Markdown is sanitized before it is printed too
        var steps = (d.steps || []).length
            ? '<table><thead><tr><th class="n">Step</th><th>What to do</th><th>Command</th><th>What you should see</th><th>Notes</th></tr></thead><tbody>' +
              d.steps.map(function (s) {
                  return '<tr><td class="n">' + s.stepNumber + '</td><td>' + esc(s.title) + '</td><td class="mono">' + esc(s.command || '') + '</td><td class="md">' +
                      SL.md(s.expectedResult || '') + '</td><td class="md">' + SL.md(s.notes || '') + '</td></tr>';
              }).join('') + '</tbody></table>'
            : '';
        return '<h3>' + esc(d.typeName) + ': ' + esc(d.title) + '</h3><p class="muted">Version ' + d.currentVersionNumber +
            (d.authorName ? ', ' + esc(d.authorName) : '') + ', ' + SL.dateTime(d.versionDate) + '</p>' +
            (d.body ? '<div class="box">' + holder.innerHTML + '</div>' : '') + steps;
    }

    function projectSection(p, data, first) {
        var items = data.items.filter(function (i) { return i.projectId === p.id; });
        var log = data.log.filter(function (e) { return e.projectId === p.id; });
        var notes = data.notes.filter(function (d) { return d.projectId === p.id; });
        return '<h2' + (first ? ' class="first"' : '') + '>' + esc(p.name) + '</h2>' +
            '<p class="sub"><span class="pill">' + esc(p.statusName) + '</span>' + (p.ownerName ? ' | Owner: ' + esc(p.ownerName) : '') +
                ' | ' + p.closedItemCount + ' of ' + p.itemCount + ' items closed (' + pct(p.closedItemCount, p.itemCount) + ')' +
                (p.blockedItemCount ? ' | ' + p.blockedItemCount + ' blocked' : '') + '</p>' +
            (data.descriptions[p.id] ? '<div class="box md">' + SL.md(data.descriptions[p.id]) + '</div>' : '') +
            '<h3>Items</h3>' + itemsTable(items, data.details) +
            (log.length ? '<h3>What was done, in order</h3>' + logTable(log) : '') +
            notes.map(noteBlock).join('');
    }

    function cover(ctx, projects, whole) {
        var s = ctx.solution;
        var html = '<h1>' + esc(s.name) + '</h1><p class="sub">' + esc(s.statusName) + (s.ownerName ? ' | Owner: ' + esc(s.ownerName) : '') +
            (s.targetDate ? ' | Target ' + SL.date(s.targetDate) : '') + ' | Printed ' + SL.dateTime(new Date().toISOString()) + '</p>';
        if (!whole) return html;
        if (s.statusSummary) html += '<div class="box md">' + SL.md('**Where things stand.** ' + s.statusSummary) + '</div>';
        if (s.description) html += '<div class="md">' + SL.md(s.description) + '</div>';
        html += '<h3>Projects</h3><table><thead><tr><th>Project</th><th>Status</th><th>Owner</th><th class="n">Items</th><th class="n">Closed</th>' +
            '<th class="n">Blocked</th><th class="n">Done</th></tr></thead><tbody>' + projects.map(function (p) {
                return '<tr><td>' + esc(p.name) + '</td><td class="pill">' + esc(p.statusName) + '</td><td>' + esc(p.ownerName || '') + '</td><td class="n">' + p.itemCount +
                    '</td><td class="n">' + p.closedItemCount + '</td><td class="n">' + p.blockedItemCount + '</td><td class="n">' + pct(p.closedItemCount, p.itemCount) + '</td></tr>';
            }).join('') + '<tr><th>Total</th><th></th><th></th><th class="n">' + s.itemCount + '</th><th class="n">' + s.closedItemCount +
            '</th><th class="n">' + s.blockedItemCount + '</th><th class="n">' + pct(s.closedItemCount, s.itemCount) + '</th></tr></tbody></table>';
        return html;
    }

    function solutionLevel(data) {
        var log = data.log.filter(function (e) { return !e.projectId; });
        var notes = data.notes.filter(function (d) { return !d.projectId; });
        if (!log.length && !notes.length && !data.files.length) return '';
        var files = data.files.length
            ? '<h3>Files</h3><table><thead><tr><th>Title</th><th>File</th><th>Added by</th><th>When</th></tr></thead><tbody>' + data.files.map(function (f) {
                  return '<tr><td>' + esc(f.title) + '</td><td>' + esc(f.originalFileName) + '</td><td>' + esc(f.uploadedByName || '') + '</td><td class="nw">' + SL.dateTime(f.createdDate) + '</td></tr>';
              }).join('') + '</tbody></table>'
            : '';
        return '<h2>The solution as a whole</h2>' + (log.length ? '<h3>What was done, in order</h3>' + logTable(log) : '') + notes.map(noteBlock).join('') + files;
    }

    // ---- the entry points ---------------------------------------------------------------

    /** The context a report is built from: {id, solution, projects}, or null when the solution cannot be read. */
    async function load(id) {
        var results = await Promise.all([SL.get('/api/solutions/' + id), SL.get('/api/solutions/' + id + '/projects')]);
        if (!results[0] || !results[0].ok || !results[0].data) return null;
        return {
            id: id,
            solution: results[0].data,
            projects: (results[1] && results[1].ok && Array.isArray(results[1].data)) ? results[1].data : []
        };
    }

    /** The report's HTML. projectId: one project's report, or 0 / null for the whole solution. '' when there is no project. */
    async function buildHtml(ctx, projectId) {
        var projects = ctx.projects.filter(function (p) { return !projectId || p.id === projectId; });
        if (!projects.length) return '';
        var data = await gather(ctx);
        var whole = !projectId;
        return cover(ctx, projects, whole) +
            projects.map(function (p, n) { return projectSection(p, data, !whole && n === 0); }).join('') +
            (whole ? solutionLevel(data) : '');
    }

    window.SolutionReport = { CSS: CSS, load: load, buildHtml: buildHtml };

    // The Solution page's Print: the report of the solution on screen, through the Print Menu.
    var Page = window.SolutionPage;
    if (!Page) return;

    /** @param projectId one project's report, or 0 / null for the whole solution */
    async function printReport(projectId) {
        SL.status('Building the report');
        var html = await buildHtml({ id: Page.id, solution: Page.solution, projects: Page.projects }, projectId);
        if (!html) { SL.toast('There is nothing to print yet.'); return; }
        var projects = Page.projects.filter(function (p) { return !projectId || p.id === projectId; });
        $('printArea').innerHTML = html;
        var name = projectId ? Page.solution.name + ' - ' + projects[0].name : Page.solution.name;
        window.Picasso.printThisPage({ selector: '#printArea', title: name, extraCss: CSS });
        SL.status('Report ready');
    }

    Page.printReport = printReport;
})();
