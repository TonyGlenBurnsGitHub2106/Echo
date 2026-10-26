(function () {
    'use strict';

    // The Files tab of one solution (Story 3493), organized like the Files tab of every record
    // (Story 3520) since Story 5594 -- the two cannot be told apart. Every file can be filed under a
    // CATEGORY (the same shared list the record Files tabs use, files/file-kit.js; no category =
    // Staging, where every upload lands), flagged Important and given a document date. The tab groups
    // by category, type, year or not at all, sorts and searches -- all of it in SQL, through the
    // house-only /api/solution-files (a category name is house information, so the outside agents'
    // door, /api/solutions, never carries it). Select files tick by tick or a whole group and file
    // them together; drop files from the desktop straight onto the tab; Arrange drags the category
    // groups; the preview view (the Bay Window, picasso-split.js) shows the list and the picked file
    // side by side.
    //
    // What the Solutions tab always had is kept as it was:
    //  * Where the bytes are saved is the solution's own switch -- Leonardo Files (committed with the
    //    repo) or the local solutions nook (git-ignored). A solution shared with an outside agent is
    //    always local: the switch is locked off and says why.
    //  * A file is never opened inline from the server: the download is always an attachment. View
    //    and the preview read those bytes in the page and draw them so nothing can run -- an HTML file
    //    in a sandboxed frame with NO permissions (no scripts, an opaque origin), Markdown through
    //    SL.renderMarkdown, text as text, a picture as an image, a PDF as page pictures (pdf.js).
    //  * Open folder asks LeonardoWeb to open Explorer at the file (the Clipboard Bridge).
    //  * Rows (the default) or Cards.
    //
    // Remembered for Tony in the DATABASE (PicassoViewState), never localStorage: the grouping, sort,
    // direction, rolled-up tools, Rows / Cards, the preview view, and which groups are folded (per
    // solution) -- keys leo.sl.files.*.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var VS = 'leo.sl.files.';
    var VS_GROUP = VS + 'group', VS_SORT = VS + 'sort', VS_DESC = VS + 'desc', VS_TOOLS = VS + 'tools',
        VS_ROWS = VS + 'rows', VS_VIEW = VS + 'view';
    var PAGE_SIZE = 100;
    var QUICK_CHIPS = 5;
    var PREVIEW_DELAY_MS = 200;
    var PREVIEW_MAX_BYTES = 40 * 1024 * 1024;
    var TEXT_CAP = 2 * 1024 * 1024;              // characters of a text file drawn on screen

    var GROUPS = [
        { key: 'category', label: 'Category' },
        { key: 'type',     label: 'Type' },
        { key: 'year',     label: 'Year' },
        { key: 'none',     label: 'All' }
    ];
    var SORTS = [
        { key: 'added',   label: 'Date added' },
        { key: 'docdate', label: 'Document date' },
        { key: 'name',    label: 'Name' },
        { key: 'size',    label: 'Size' },
        { key: 'type',    label: 'Type' }
    ];

    var HTML_EXT  = { html: 1, htm: 1, xhtml: 1 };
    var MD_EXT    = { md: 1, markdown: 1 };
    var TEXT_EXT  = { txt: 1, log: 1, json: 1, xml: 1, csv: 1, tsv: 1, sql: 1, yml: 1, yaml: 1, properties: 1, ini: 1, cfg: 1,
                      conf: 1, java: 1, js: 1, ts: 1, css: 1, ps1: 1, bat: 1, cmd: 1, sh: 1, py: 1, cs: 1, groovy: 1, gradle: 1,
                      kt: 1, edi: 1, x12: 1, '835': 1, '837': 1 };
    var IMAGE_EXT = { png: 1, jpg: 1, jpeg: 1, gif: 1, webp: 1, bmp: 1, svg: 1 };
    // The Leonardo Reader is offered only where its own renderer cannot be steered by the file's
    // content: PDFs, pictures and plain text. Markdown and web pages -- which an outside agent can
    // upload to a shared solution -- are read with View, drawn the safe way.
    var READER_EXT = { pdf: 1, png: 1, jpg: 1, jpeg: 1, gif: 1, webp: 1, bmp: 1, txt: 1, log: 1, csv: 1 };

    // ---- state ---------------------------------------------------------------------------------

    var st = {
        pane: null, rows: [], groups: [], total: 0, staged: 0, hasMore: false,
        selecting: false, selected: new Set(), flash: new Set(), arranging: false,
        search: '', cats: [], split: null, pv: 0, pvSeq: 0, pvBlobUrl: null, pvPdf: null, pvWidth: 0,
        folds: null, foldsFor: 0
    };
    var mEditId = 0, mViewId = 0, mViewUrl = null, mViewPdf = null, mViewSeq = 0, mLoading = null;
    var _pdfReady = null, _splitReady = null;

    function K() { return window.FileKit; }
    function vs() { return window.PicassoViewState; }
    function vget(key, dflt) { var v = vs() && vs().get(key); return v == null || v === '' ? dflt : v; }
    function vset(key, value) { if (vs()) vs().set(key, String(value)); }

    function prefs() {
        var g = vget(VS_GROUP, 'category'), s = vget(VS_SORT, 'added');
        return {
            group: GROUPS.some(function (x) { return x.key === g; }) ? g : 'category',
            sort:  SORTS.some(function (x) { return x.key === s; }) ? s : 'added',
            desc:  vget(VS_DESC, '1') !== '0'
        };
    }
    function toolsCollapsed() { return vget(VS_TOOLS, '0') === '1'; }
    function rowsView() {
        var v = vget(VS_ROWS, null);
        if (v == null) {
            // Moved off localStorage (2026-10-05): honour the old device choice once.
            try { v = localStorage.getItem('sl.files.rows') === '0' ? '0' : '1'; } catch (e) { v = '1'; }
        }
        return v !== '0';
    }

    // Folded groups, per solution, in the database: the keys the user has folded.
    function foldKey() { return VS + 'folds.' + Page.id; }
    function folds() {
        if (st.folds && st.foldsFor === Page.id) return st.folds;
        var list = [];
        try { list = JSON.parse(vget(foldKey(), '[]')) || []; } catch (e) { list = []; }
        st.folds = new Set(list);
        st.foldsFor = Page.id;
        return st.folds;
    }
    function saveFolds() { vset(foldKey(), JSON.stringify(Array.from(folds()))); }
    function isFolded(key) { return folds().has(key); }

    function plural(n, one, many) { return n + ' ' + (n === 1 ? one : (many || one + 's')); }

    function size(bytes) {
        if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
        if (bytes >= 1024) return Math.round(bytes / 1024) + ' KB';
        return (bytes || 0) + ' bytes';
    }

    function ext(f) {
        var m = /\.([A-Za-z0-9]+)$/.exec(f.originalFileName || '');
        return m ? m[1].toLowerCase() : String(f.ext || '').toLowerCase();
    }

    function kindOf(f) {
        var e = ext(f);
        if (e === 'pdf') return 'pdf';
        if (HTML_EXT[e]) return 'html';
        if (MD_EXT[e]) return 'md';
        if (TEXT_EXT[e]) return 'text';
        if (IMAGE_EXT[e]) return 'image';
        var t = String(f.contentType || '').toLowerCase();
        if (t.indexOf('application/pdf') === 0) return 'pdf';
        if (t.indexOf('text/html') === 0) return 'html';
        if (t.indexOf('text/') === 0) return 'text';
        if (t.indexOf('image/') === 0) return 'image';
        return '';
    }
    function canPrint(f) { var k = kindOf(f); return k === 'pdf' || k === 'image' || k === 'text' || k === 'md'; }
    function readerOk(f) { return !!READER_EXT[ext(f)]; }

    function byId(id) { for (var i = 0; i < st.rows.length; i++) if (st.rows[i].id === id) return st.rows[i]; return null; }

    function listUrl(offset) {
        var p = prefs();
        return '/api/solution-files/' + Page.id + '/filed'
             + '?group=' + encodeURIComponent(p.group)
             + '&sort=' + encodeURIComponent(p.sort)
             + '&desc=' + (p.desc ? 'true' : 'false')
             + (st.search ? '&q=' + encodeURIComponent(st.search) : '')
             + '&offset=' + (offset || 0)
             + '&size=' + PAGE_SIZE;
    }

    // ---- shell ---------------------------------------------------------------------------------

    function shellHtml() {
        var p = prefs();
        var toolsOpen = !toolsCollapsed();
        return ''
            + '<button type="button" class="ef-tools-toggle" data-ef="tools" aria-expanded="' + toolsOpen + '"'
            +   ' title="Show or hide the tools" data-testid="entity-file-tools-toggle">'
            +   '<span class="ef-caret">' + (toolsOpen ? '&#9662;' : '&#9656;') + '</span>'
            +   '<span class="ef-summary" data-testid="entity-file-summary"></span>'
            + '</button>'
            + '<div class="ef-tools" data-testid="entity-file-tools">'
            + '<div class="ed-pane-actions ef-actions">'
            +   '<button type="button" class="ed-btn primary" data-ef="add" data-testid="entity-file-add-btn">+ Add Files</button>'
            +   '<button type="button" class="ed-btn" data-ef="select" data-testid="entity-file-select-btn">Select</button>'
            +   '<button type="button" class="ed-btn" data-ef="categories" data-testid="entity-file-categories-btn">Categories</button>'
            + '</div>'
            + '<input type="search" class="ef-search" placeholder="Search names, categories, items"'
            +   ' autocomplete="off" data-testid="entity-file-search">'
            + '<div class="ef-controls">'
            +   '<div class="ef-seg" role="group" aria-label="Group by" data-testid="entity-file-group">'
            +     GROUPS.map(function (g) {
                        return '<button type="button" data-group="' + g.key + '"'
                             + (g.key === p.group ? ' class="on" aria-pressed="true"' : ' aria-pressed="false"')
                             + ' data-testid="entity-file-group-' + g.key + '">' + esc(g.label) + '</button>';
                    }).join('')
            +   '</div>'
            +   '<div class="ef-sort">'
            +     '<select class="ef-sort-sel" aria-label="Sort by" data-testid="entity-file-sort">'
            +       SORTS.map(function (s) {
                        return '<option value="' + s.key + '"' + (s.key === p.sort ? ' selected' : '') + '>' + esc(s.label) + '</option>';
                    }).join('')
            +     '</select>'
            +     '<button type="button" class="ed-btn ef-dir" data-ef="dir" data-testid="entity-file-sort-dir"'
            +       ' aria-label="' + (p.desc ? 'Newest or largest first' : 'Oldest or smallest first') + '">'
            +       (p.desc ? '&#8595;' : '&#8593;') + '</button>'
            +     '<button type="button" class="ed-btn ef-viewbtn" data-ef="layout" hidden'
            +       ' aria-label="Put the list on top" title="Put the list on top"'
            +       ' data-testid="entity-file-layout-toggle">&#11026;</button>'
            +     '<button type="button" class="ed-btn ef-viewbtn" data-ef="preview" aria-pressed="false"'
            +       ' aria-label="Preview view" title="Preview view" data-testid="entity-file-view-toggle">&#9703;</button>'
            +     '<button type="button" class="ed-btn ef-foldall" data-ef="expand-all" hidden'
            +       ' data-testid="entity-file-expand-all">Expand all</button>'
            +     '<button type="button" class="ed-btn ef-foldall" data-ef="collapse-all" hidden'
            +       ' data-testid="entity-file-collapse-all">Collapse all</button>'
            +     '<button type="button" class="ed-btn ef-arrange" data-ef="arrange" hidden aria-pressed="false"'
            +       ' aria-label="Arrange the category groups" title="Arrange groups"'
            +       ' data-testid="entity-file-arrange">&#8645;</button>'
            +   '</div>'
            + '</div>'
            + '</div>'
            + '<div class="ef-banner" hidden data-testid="entity-file-staging-banner"></div>'
            + '<div class="ef-split" data-testid="entity-file-split">'
            +   '<div class="ef-listwrap" data-testid="entity-file-listwrap">'
            +     '<div class="ef-list" data-testid="entity-file-list"></div>'
            +     '<div class="ef-more" hidden><button type="button" class="ed-btn" data-ef="more" data-testid="entity-file-more-btn">Load more</button></div>'
            +   '</div>'
            +   '<div class="ef-pvpane" data-testid="entity-file-preview">'
            +     '<div class="ef-pv-head" data-testid="entity-file-preview-head"></div>'
            +     '<div class="ef-pv-body" data-testid="entity-file-preview-body"></div>'
            +   '</div>'
            + '</div>'
            + '<div class="ef-selbar" hidden data-testid="entity-file-selbar">'
            +   '<span class="ef-selcount"></span>'
            +   '<button type="button" class="ed-btn primary" data-ef="sel-file" data-testid="entity-file-sel-file-btn">File to</button>'
            +   '<button type="button" class="ed-btn" data-ef="sel-staging" data-testid="entity-file-sel-staging-btn">Back to staging</button>'
            +   '<button type="button" class="ed-btn" data-ef="sel-cancel" data-testid="entity-file-sel-cancel-btn">Cancel</button>'
            + '</div>';
    }

    function ensureShell() {
        var pane = $('pane-files');
        if (st.pane === pane && pane.querySelector('.ef-list')) return;
        pane.innerHTML = shellHtml();
        pane.classList.add('ef-pane', 'ed-pane', 'active');
        pane.classList.toggle('ef-tools-collapsed', toolsCollapsed());
        st.pane = pane;
        st.split = null;
        pane.querySelector('.ef-search').value = st.search;
        wireShell();
        attachSplit();
    }

    // ---- the preview view (the Bay Window) ------------------------------------------------------

    function splitAssets() {
        if (_splitReady) return _splitReady;
        _splitReady = new Promise(function (resolve) {
            if (!document.querySelector('link[href^="/picasso-split.css"]')) {
                var l = document.createElement('link');
                l.rel = 'stylesheet';
                l.href = '/picasso-split.css?v=3';
                document.head.appendChild(l);
            }
            function ready() { window.PicassoSplit.ensureViewState(VS).then(function () { resolve(true); }); }
            if (window.PicassoSplit) { ready(); return; }
            var sc = document.createElement('script');
            sc.src = '/picasso-split.js?v=7';
            sc.onload = ready;
            sc.onerror = function () { resolve(false); };
            document.head.appendChild(sc);
        });
        return _splitReady;
    }

    /** pdf.js -- the Reader's own copy -- loaded the first time a PDF is drawn. It draws pictures of
     *  the pages; nothing in a PDF runs. */
    function pdfAssets() {
        if (_pdfReady) return _pdfReady;
        _pdfReady = new Promise(function (resolve) {
            function ready() {
                if (!window.pdfjsLib) { resolve(false); return; }
                window.pdfjsLib.GlobalWorkerOptions.workerSrc = '/reader/pdf.worker.min.js?v=1';
                window.pdfjsLib.GlobalWorkerOptions.isEvalSupported = false;
                resolve(true);
            }
            if (window.pdfjsLib) { ready(); return; }
            var sc = document.createElement('script');
            sc.src = '/reader/pdf.min.js?v=1';
            sc.onload = ready;
            sc.onerror = function () { _pdfReady = null; resolve(false); };
            document.head.appendChild(sc);
        });
        return _pdfReady;
    }

    function attachSplit() {
        splitAssets().then(function (ok) {
            if (!ok || st.split || !st.pane.querySelector('.ef-split')) return;
            st.split = window.PicassoSplit.create({
                root: st.pane.querySelector('.ef-split'),
                list: st.pane.querySelector('.ef-listwrap'),
                preview: st.pane.querySelector('.ef-pvpane'),
                key: VS, testid: 'entity-file', listLabel: 'Files',
                onChange: drawViewButtons
            });
            st.split.setOn(vget(VS_VIEW, 'list') === 'preview');
            if (st.split.isOn()) syncPreview();
            drawViewButtons();
            watchPreviewWidth();
        });
    }

    function previewing() { return !!(st.split && st.split.isOn()); }

    function drawViewButtons() {
        if (!st.pane) return;
        var on = previewing(), top = on && st.split.layout() === 'top';
        var pb = st.pane.querySelector('[data-ef="preview"]');
        pb.classList.toggle('on', on);
        pb.setAttribute('aria-pressed', String(on));
        pb.title = on ? 'Back to the list view' : 'Preview view';
        pb.setAttribute('aria-label', pb.title);
        var lb = st.pane.querySelector('[data-ef="layout"]');
        lb.hidden = !on;
        lb.innerHTML = top ? '&#9703;' : '&#11026;';
        lb.title = top ? 'Put the list on the left' : 'Put the list on top';
        lb.setAttribute('aria-label', lb.title);
    }

    function setPreview(on) {
        // A tap that arrives before the Bay Window has loaded waits for it, rather than being lost.
        if (!st.split) { vset(VS_VIEW, on ? 'preview' : 'list'); splitAssets().then(function () { if (st.split) setPreview(on); }); return; }
        vset(VS_VIEW, on ? 'preview' : 'list');
        st.split.setOn(on);
        if (on) syncPreview(); else { st.pv = 0; clearPreview(); }
        markPreviewCard();
    }

    function syncPreview() {
        if (!previewing()) return;
        if (!st.rows.length) {
            st.pv = 0;
            clearPreview();
            st.pane.querySelector('.ef-pv-head').innerHTML = '';
            st.pane.querySelector('.ef-pv-body').innerHTML = '<div class="ed-empty">Nothing to preview.</div>';
            return;
        }
        if (st.pv && byId(st.pv)) markPreviewCard(); else previewSelect(st.rows[0]);
    }

    function markPreviewCard() {
        st.pane.querySelectorAll('.ef-card.ef-pv-on').forEach(function (c) { c.classList.remove('ef-pv-on'); });
        if (!previewing() || !st.pv) return null;
        var card = st.pane.querySelector('.ef-card[data-fileid="' + st.pv + '"]');
        if (card) card.classList.add('ef-pv-on');
        return card;
    }

    function clearPreview() {
        st.pvSeq++;
        if (st.pvBlobUrl) { try { URL.revokeObjectURL(st.pvBlobUrl); } catch (e) {} st.pvBlobUrl = null; }
        if (st.pvPdf) { try { st.pvPdf.destroy(); } catch (e) {} st.pvPdf = null; }
    }

    function catOf(f) { return f.categoryId ? { id: f.categoryId, name: f.categoryName, color: f.categoryColor } : null; }

    function previewSelect(f) {
        if (!f) return;
        clearPreview();
        st.pv = f.id;
        var card = markPreviewCard();
        if (card && card.scrollIntoView) card.scrollIntoView({ block: 'nearest' });

        var cat = catOf(f);
        var meta = [];
        if (f.documentDate) meta.push('<span class="ef-docdate">Dated ' + esc(K().fmtDate(f.documentDate)) + '</span>');
        if (f.sizeBytes) meta.push('<span>' + esc(size(f.sizeBytes)) + '</span>');
        st.pane.querySelector('.ef-pv-head').innerHTML =
              '<div class="ef-pv-title">'
            +   '<span class="fk-type ' + K().typeClass(f.typeFamily || 'Other') + '">' + esc(f.ext || 'FILE') + '</span>'
            +   '<h3 data-testid="entity-file-preview-title">' + esc(f.title || f.originalFileName) + '</h3>'
            +   (readerOk(f) ? '<button type="button" class="ed-btn" data-ef="pv-reader" data-testid="entity-file-preview-reader">Reader</button>' : '')
            +   '<button type="button" class="ed-btn primary" data-ef="pv-view" data-testid="entity-file-preview-open">View</button>'
            + '</div>'
            + '<div class="ef-meta ef-pv-meta">'
            +   (cat ? K().categoryChip(cat, {}) : '<span class="fk-chip fk-staging"><span class="fk-dot"></span>Staging</span>')
            +   meta.join('<span class="ef-sep">|</span>')
            + '</div>';

        var body = st.pane.querySelector('.ef-pv-body');
        var kind = kindOf(f);
        if (!kind) { body.innerHTML = noPreviewHtml('There is no preview for ' + (ext(f) ? 'a .' + ext(f) + ' file' : 'this file') + '. Download it to open it.'); return; }
        if (f.sizeBytes && f.sizeBytes > PREVIEW_MAX_BYTES) { body.innerHTML = noPreviewHtml('This file is too big to preview here.'); return; }
        body.innerHTML = '<div class="ed-empty">Opening</div>';

        var seq = st.pvSeq, id = f.id;
        function current() { return seq === st.pvSeq && st.pv === id; }
        window.setTimeout(function () {
            if (!current()) return;
            fetchBlob(id).then(function (blob) {
                if (!current()) return;
                if (!blob) { body.innerHTML = noPreviewHtml('That file could not be opened.'); return; }
                return draw(body, kind, blob, f, current, function (url) { st.pvBlobUrl = url; },
                    function (doc) { st.pvPdf = doc; }, 'entity-file-preview');
            });
        }, PREVIEW_DELAY_MS);
    }

    function noPreviewHtml(msg) {
        return '<div class="ef-pv-none" data-testid="entity-file-preview-none"><div>' + esc(msg) + '</div></div>';
    }

    /**
     * Draw the bytes of a file into an element, by kind, so that nothing in it can run. Used by the
     * preview pane and the View sheet alike. keepUrl / keepPdf hand back what the caller must let go
     * of later; testid prefixes the drawn element's test id.
     */
    function draw(body, kind, blob, f, current, keepUrl, keepPdf, testid) {
        if (kind === 'image') {
            var url = URL.createObjectURL(blob);
            keepUrl(url);
            body.innerHTML = '';
            var wrap = document.createElement('div');
            wrap.className = 'ef-pv-img';
            var img = document.createElement('img');
            img.alt = f.title || '';
            img.src = url;
            img.setAttribute('data-testid', testid + '-image');
            wrap.appendChild(img);
            body.appendChild(wrap);
            return Promise.resolve();
        }
        if (kind === 'pdf') {
            return Promise.all([pdfAssets(), blob.arrayBuffer()]).then(function (res) {
                if (!current()) return;
                if (!res[0]) { body.innerHTML = noPreviewHtml('The PDF viewer could not load. Download the file to read it.'); return; }
                return window.pdfjsLib.getDocument({ data: res[1], isEvalSupported: false }).promise.then(function (doc) {
                    if (!current()) { try { doc.destroy(); } catch (e) {} return; }
                    keepPdf(doc);
                    return renderPdf(doc, body, current, testid);
                });
            }).catch(function () {
                if (current()) body.innerHTML = noPreviewHtml('Could not show this PDF. Download it to read it.');
            });
        }
        return blob.text().then(function (text) {
            if (!current()) return;
            var cut = text.length > TEXT_CAP;
            if (cut) text = text.slice(0, TEXT_CAP);
            body.innerHTML = '';
            if (kind === 'html') {
                // No allow-scripts and no allow-same-origin: the page is drawn, nothing in it runs, and it
                // cannot reach this tab, its storage or its session.
                var frame = document.createElement('iframe');
                frame.className = 'ef-pv-frame';
                frame.setAttribute('sandbox', '');
                frame.setAttribute('referrerpolicy', 'no-referrer');
                frame.setAttribute('title', f.title || 'File');
                frame.setAttribute('data-testid', testid + '-frame');
                frame.srcdoc = text;
                body.appendChild(frame);
            } else if (kind === 'md') {
                var md = document.createElement('div');
                md.className = 'sl-md sl-fileview-md';
                md.setAttribute('data-testid', testid + '-text');
                SL.renderMarkdown(md, text);
                body.appendChild(md);
            } else {
                var pre = document.createElement('pre');
                pre.className = 'ef-pv-text sl-fileview-pre';
                pre.setAttribute('data-testid', testid + '-text');
                pre.textContent = text;
                body.appendChild(pre);
            }
            if (cut) {
                var note = document.createElement('div');
                note.className = 'ef-pv-none';
                note.textContent = 'Only the first part is shown. Download the file to read all of it.';
                body.appendChild(note);
            }
        });
    }

    /** Every page of a PDF drawn to the element's width, one under the next. */
    function renderPdf(doc, body, current, testid) {
        var top = body.scrollTop;
        var wrap = document.createElement('div');
        wrap.className = 'ef-pv-pages';
        wrap.setAttribute('data-testid', testid + '-pdf');
        body.innerHTML = '';
        body.appendChild(wrap);
        var width = Math.max(200, body.clientWidth - 24);
        if (body === (st.pane && st.pane.querySelector('.ef-pv-body'))) st.pvWidth = body.clientWidth;
        var dpr = window.devicePixelRatio || 1;
        var chain = Promise.resolve();
        for (var i = 1; i <= doc.numPages; i++) {
            (function (n) {
                chain = chain.then(function () {
                    if (!current()) return null;
                    return doc.getPage(n).then(function (page) {
                        if (!current()) return null;
                        var vp = page.getViewport({ scale: width / page.getViewport({ scale: 1 }).width });
                        var canvas = document.createElement('canvas');
                        canvas.width = Math.floor(vp.width * dpr);
                        canvas.height = Math.floor(vp.height * dpr);
                        canvas.style.width = Math.floor(vp.width) + 'px';
                        canvas.setAttribute('aria-label', 'Page ' + n);
                        var ctx = canvas.getContext('2d');
                        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                        wrap.appendChild(canvas);
                        if (n === 1 && top) body.scrollTop = top;
                        return page.render({ canvasContext: ctx, viewport: vp }).promise;
                    });
                });
            })(i);
        }
        return chain.catch(function () {
            if (current() && !wrap.children.length) body.innerHTML = noPreviewHtml('Could not show this PDF.');
        });
    }

    function watchPreviewWidth() {
        if (!window.ResizeObserver) return;
        var body = st.pane.querySelector('.ef-pv-body');
        var timer = null;
        new ResizeObserver(function () {
            clearTimeout(timer);
            timer = setTimeout(function () {
                if (!st.pvPdf || !previewing() || !body.clientWidth) return;
                if (Math.abs(body.clientWidth - (st.pvWidth || 0)) < 40) return;
                var seq = st.pvSeq, id = st.pv;
                renderPdf(st.pvPdf, body, function () { return seq === st.pvSeq && st.pv === id; }, 'entity-file-preview');
            }, 300);
        }).observe(body);
    }

    function previewStep(delta) {
        var i = -1;
        for (var k = 0; k < st.rows.length; k++) if (st.rows[k].id === st.pv) { i = k; break; }
        var j = i + delta;
        if (j < 0) return;
        if (j < st.rows.length) { previewSelect(st.rows[j]); return; }
        if (st.hasMore) loadMore(function () { if (j < st.rows.length) previewSelect(st.rows[j]); });
    }

    // ---- load and draw ------------------------------------------------------------------------

    var loadSeq = 0;
    async function load() {
        var mine = ++loadSeq;   // taps can start loads faster than they finish; only the newest draws
        paintStorage();
        paintView();
        if (vs() && vs().sync) { try { await vs().sync(VS); } catch (e) { /* drawn from the cache */ } }
        if (mine !== loadSeq) return;
        st.folds = null;
        ensureShell();
        var r = await SL.get(listUrl(0));
        if (mine !== loadSeq) return;
        if (!r || !r.ok || !r.data) {
            st.pane.querySelector('.ef-list').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the files.')) + '</div>';
            return;
        }
        st.rows    = r.data.rows || [];
        st.groups  = r.data.groups || [];
        st.total   = r.data.total || 0;
        st.staged  = r.data.staged || 0;
        st.hasMore = !!r.data.hasMore;
        var present = new Set(st.rows.map(function (f) { return f.id; }));
        st.selected.forEach(function (id) { if (!present.has(id)) st.selected.delete(id); });
        try { st.cats = await K().loadCategories(); } catch (e) { st.cats = []; }
        if (mine !== loadSeq) return;
        drawList();
    }

    function reload() { return load(); }

    function loadMore(then) {
        var btn = st.pane.querySelector('[data-ef="more"]');
        btn.disabled = true;
        SL.get(listUrl(st.rows.length)).then(function (r) {
            btn.disabled = false;
            if (!r || !r.ok) { SL.toast(SL.failText(r, 'Could not load more files.')); return; }
            st.rows = st.rows.concat(r.data.rows || []);
            st.hasMore = !!r.data.hasMore;
            drawList();
            if (then) then();
        });
    }

    function groupMeta(key) {
        for (var i = 0; i < st.groups.length; i++) if (st.groups[i].key === key) return st.groups[i];
        return null;
    }

    function usedCategoryIds() {
        return st.groups.filter(function (g) { return g.categoryId; })
            .sort(function (a, b) { return b.count - a.count; })
            .map(function (g) { return g.categoryId; });
    }

    function quickCats() {
        var used = usedCategoryIds();
        var cats = (st.cats || []).slice().sort(function (a, b) {
            var ia = used.indexOf(a.id), ib = used.indexOf(b.id);
            ia = ia < 0 ? 999 : ia; ib = ib < 0 ? 999 : ib;
            return ia - ib || a.sortOrder - b.sortOrder;
        });
        return cats.slice(0, QUICK_CHIPS);
    }

    function drawList() {
        var pane = st.pane, p = prefs();
        drawSummary();

        var banner = pane.querySelector('.ef-banner');
        if (p.group !== 'category' && st.staged > 0) {
            banner.hidden = false;
            banner.innerHTML = '<span>' + esc(plural(st.staged, 'file')) + ' waiting in staging</span>'
                + '<button type="button" class="ed-btn" data-ef="show-staging" data-testid="entity-file-show-staging-btn">Show staging</button>';
        } else {
            banner.hidden = true;
            banner.innerHTML = '';
        }

        var host = pane.querySelector('.ef-list');
        host.classList.toggle('sl-files-cards', !rowsView());
        if (!st.rows.length) {
            host.innerHTML = '<div class="sl-empty">' + (st.search ? 'No files match.' : 'No files attached yet. Tap + Add Files, or drop files here.') + '</div>';
        } else {
            var html = '', curKey = null, quick = quickCats();
            st.rows.forEach(function (f, idx) {
                if (f.groupKey !== curKey) {
                    if (curKey !== null) html += '</div></section>';
                    curKey = f.groupKey;
                    html += bandHead(f, p.group);
                }
                html += cardHtml(f, idx, quick);
            });
            if (curKey !== null) html += '</div></section>';
            host.innerHTML = html;
        }

        pane.querySelector('.ef-more').hidden = !st.hasMore;
        if (previewing()) syncPreview();
        syncFoldAll();
        pane.classList.toggle('ef-selecting', st.selecting);
        pane.querySelector('[data-ef="select"]').classList.toggle('on', st.selecting);
        drawSelbar();
        SL.status(plural(st.total, 'file'));
    }

    function drawSummary() {
        var loaded = st.rows.length;
        var summary = st.total === 0
            ? (st.search ? 'No files match' : 'No files yet')
            : plural(st.total, 'file') + (loaded < st.total ? ' (' + loaded + ' shown)' : '')
              + (st.staged ? ' | ' + st.staged + ' in staging' : '');
        if (st.search && toolsCollapsed()) summary += ' | searching "' + st.search + '"';
        st.pane.querySelector('.ef-summary').textContent = summary;
    }

    function bandHead(f, group) {
        var meta = groupMeta(f.groupKey) || { count: 0 };
        var loadedInGroup = st.rows.filter(function (r) { return r.groupKey === f.groupKey; }).length;
        var countTxt = meta.count > loadedInGroup ? (loadedInGroup + ' of ' + meta.count) : String(meta.count || loadedInGroup);
        var isStaging = f.groupKey === 'staging';
        var collapsed = isFolded(f.groupKey);
        var color = group === 'category' && !isStaging ? K().colorOf(f.groupColor) : '';
        var movable = st.arranging && group === 'category' && !isStaging && f.categoryId;
        return '<section class="ef-band' + (isStaging ? ' ef-band-staging' : '') + (collapsed ? ' collapsed' : '') + '"'
             +   ' data-group="' + esc(f.groupKey) + '"'
             +   (group === 'category' && f.categoryId ? ' data-cat="' + esc(f.categoryId) + '"' : '')
             +   ' data-testid="entity-file-band">'
             +   '<div class="ef-band-head">'
             +     (movable
                      ? '<span class="ef-grip" data-ef-grip title="Drag to move this group up or down"'
                        + ' aria-label="Drag to move ' + esc(f.groupLabel) + '" data-testid="entity-file-band-grip">&#8942;&#8942;</span>'
                      : '')
             +     (st.selecting
                      ? '<input type="checkbox" class="ef-band-check" data-ef="band-check" aria-label="Select every file in '
                        + esc(f.groupLabel) + '" data-testid="entity-file-band-check">'
                      : '')
             +     '<button type="button" class="ef-band-toggle" data-ef="toggle" aria-expanded="' + (!collapsed) + '"'
             +       ' data-testid="entity-file-band-toggle">'
             +       '<span class="ef-caret">' + (collapsed ? '&#9656;' : '&#9662;') + '</span>'
             +       (color ? '<span class="ef-band-dot" style="background:' + esc(color) + '"></span>' : '')
             +       '<span class="ef-band-label">' + esc(f.groupLabel) + '</span>'
             +       '<span class="ef-band-count">' + esc(countTxt) + '</span>'
             +     '</button>'
             +     (isStaging && !st.selecting
                      ? '<button type="button" class="ed-btn ef-band-action" data-ef="file-all" data-testid="entity-file-file-all-btn">File all</button>'
                      : '')
             +   '</div>'
             +   '<div class="ef-band-body">';
    }

    function cardHtml(f, idx, quick) {
        var name = f.title || f.originalFileName || '(unnamed)';
        var fam = f.typeFamily || 'Other';
        var sel = st.selected.has(f.id);
        var cat = catOf(f);
        var viewable = !!kindOf(f);
        var meta = [];
        if (f.documentDate) meta.push('<span class="ef-docdate" title="Document date">Dated ' + esc(K().fmtDate(f.documentDate)) + '</span>');
        if (f.sizeBytes) meta.push('<span>' + esc(size(f.sizeBytes)) + '</span>');
        if (f.createdDate) meta.push('<span title="Date added">Added ' + esc(K().fmtDate(f.createdDate)) + '</span>');
        meta.push('<span class="sl-files-store' + (f.storageCode === 'repo' ? ' repo' : '') + '" data-testid="files-storage-tag">'
            + (f.storageCode === 'repo' ? 'In Leonardo Files' : 'In Echo Files') + '</span>');

        return '<div class="ed-card ef-card' + (f.staged ? ' ef-staged' : '') + (sel ? ' ef-sel' : '')
             +     (st.flash.has(f.id) ? ' ef-flash' : '') + (f.important ? ' ef-important' : '') + '"'
             +   ' data-fileid="' + esc(f.id) + '" data-idx="' + idx + '" data-testid="entity-file-row">'
             +   '<div class="ef-top">'
             +     (st.selecting
                      ? '<input type="checkbox" class="ef-check" data-ef="check"' + (sel ? ' checked' : '')
                        + ' aria-label="Select ' + esc(name) + '" data-testid="entity-file-check">'
                      : '')
             +     '<span class="fk-type ' + K().typeClass(fam) + '" title="' + esc(fam) + '">' + esc(f.ext || 'FILE') + '</span>'
             +     '<span class="ef-name">' + esc(name) + '</span>'
             +     '<button type="button" class="ef-flag' + (f.important ? ' on' : '') + '" data-ef="flag"'
             +       ' aria-pressed="' + (f.important ? 'true' : 'false') + '"'
             +       ' aria-label="' + (f.important ? 'Important -- tap to clear' : 'Mark important') + '"'
             +       ' title="Important" data-testid="entity-file-flag-btn">&#9873;</button>'
             +   '</div>'
             +   '<div class="ef-meta">'
             +     (cat
                      ? K().categoryChip(cat, { act: 'refile', testid: 'entity-file-category-chip' })
                      : '<button type="button" class="fk-chip fk-staging" data-act="refile" data-testid="entity-file-category-chip">'
                        + '<span class="fk-dot"></span>Staging</button>')
             +     meta.join('<span class="ef-sep">|</span>')
             +   '</div>'
             +   (f.originalFileName && f.originalFileName !== name ? '<div class="ef-desc">' + esc(f.originalFileName) + '</div>' : '')
             +   '<div class="ef-desc sl-files-who">' + Page.personChip(f.uploadedByCode, f.uploadedByName)
             +     (f.itemTitle ? ' <span class="sl-muted">For: ' + esc(f.itemTitle) + '</span>' : '') + '</div>'
             +   (f.notesPreview ? '<div class="notes sl-md">' + SL.md(f.notesPreview) + '</div>' : '')
             +   (f.staged && !st.selecting
                    ? '<div class="ef-fileto"><span class="ef-fileto-lbl">File to</span>'
                      + quick.map(function (c) { return K().categoryChip(c, { act: 'quick', testid: 'entity-file-quick-chip' }); }).join('')
                      + '<button type="button" class="fk-chip ef-more-chip" data-act="refile" data-testid="entity-file-quick-more">More</button>'
                      + '</div>'
                    : '')
             +   (st.selecting ? '' :
                  '<div class="ed-card-actions">'
             +     (viewable ? '<button type="button" class="ed-btn" data-ef="view" data-testid="files-view-btn">View</button>' : '')
             +     (readerOk(f) ? '<button type="button" class="ed-btn" data-ef="reader" data-testid="entity-file-reader-btn">Reader</button>' : '')
             +     '<button type="button" class="ed-btn' + (viewable ? '' : ' primary') + '" data-ef="download" data-testid="files-download-btn">Download</button>'
             +     '<button type="button" class="ed-btn" data-ef="folder" data-testid="files-open-folder-btn" title="Open the folder that holds this file, on this computer">Open folder</button>'
             +     (canPrint(f) ? '<button type="button" class="ed-btn" data-ef="print" data-testid="entity-file-print-btn">Print</button>' : '')
             +     '<button type="button" class="ed-btn" data-ef="edit" data-testid="files-edit-btn">Edit</button>'
             +     '<button type="button" class="ed-btn danger" data-ef="del" data-testid="files-delete-btn">Delete</button>'
             +   '</div>')
             + '</div>';
    }

    function drawSelbar() {
        var bar = st.pane.querySelector('.ef-selbar');
        bar.hidden = !st.selecting;
        if (!st.selecting) return;
        var n = st.selected.size;
        bar.querySelector('.ef-selcount').textContent = n ? plural(n, 'file') + ' selected' : 'Tap files to select them';
        bar.querySelectorAll('[data-ef="sel-file"], [data-ef="sel-staging"]').forEach(function (b) { b.disabled = n === 0; });
    }

    function setBandCollapsed(band, collapsed) {
        band.classList.toggle('collapsed', collapsed);
        var t = band.querySelector('[data-ef="toggle"]');
        if (!t) return;
        t.setAttribute('aria-expanded', String(!collapsed));
        t.querySelector('.ef-caret').innerHTML = collapsed ? '&#9656;' : '&#9662;';
    }

    function syncArrange() {
        var btn = st.pane.querySelector('[data-ef="arrange"]');
        var usable = prefs().group === 'category' && !st.selecting
                  && st.pane.querySelectorAll('.ef-band[data-cat]').length >= 2;
        if (!usable && st.arranging) st.arranging = false;
        btn.hidden = !usable;
        btn.classList.toggle('on', st.arranging);
        btn.setAttribute('aria-pressed', st.arranging ? 'true' : 'false');
        st.pane.classList.toggle('ef-arranging', st.arranging);
        st.pane.querySelectorAll('[data-ef-grip]').forEach(wireGrip);
    }

    function syncFoldAll() {
        syncArrange();
        var none = !st.pane.querySelector('.ef-band');
        st.pane.querySelectorAll('.ef-foldall').forEach(function (btn) { btn.hidden = none; });
    }

    function syncControls() {
        var p = prefs();
        st.pane.querySelectorAll('.ef-seg [data-group]').forEach(function (b) {
            var on = b.getAttribute('data-group') === p.group;
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
        var dir = st.pane.querySelector('[data-ef="dir"]');
        dir.innerHTML = p.desc ? '&#8595;' : '&#8593;';
        dir.setAttribute('aria-label', p.desc ? 'Newest or largest first' : 'Oldest or smallest first');
    }

    // ---- drag to reorder the category groups (Arrange on) ---------------------------------------
    // The categories are one shared list, so the order is saved for every record and solution: the
    // groups shown here take their new order, the others keep their places. Staging never moves.

    function wireGrip(grip) {
        if (grip._slWired) return;
        grip._slWired = true;
        grip.addEventListener('click', function (e) { e.stopPropagation(); });
        grip.addEventListener('pointerdown', function (down) {
            var band = grip.closest('.ef-band');
            var host = band ? band.parentNode : null;
            if (!band || !host) return;
            down.preventDefault();
            down.stopPropagation();
            band.classList.add('dragging');
            function order() {
                return Array.prototype.map.call(host.querySelectorAll('.ef-band[data-cat]'), function (b) {
                    return parseInt(b.getAttribute('data-cat'), 10);
                });
            }
            var before = order().join(',');
            var scroller = document.scrollingElement;
            function onMove(ev) {
                if (ev.clientY < 48) scroller.scrollTop -= 14;
                if (ev.clientY > window.innerHeight - 48) scroller.scrollTop += 14;
                var under = document.elementFromPoint(ev.clientX, ev.clientY);
                var over = under && under.closest ? under.closest('.ef-band[data-cat]') : null;
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
                if (ids.join(',') !== before) saveGroupOrder(ids);
            }
            document.addEventListener('pointermove', onMove);
            document.addEventListener('pointerup', onUp);
            document.addEventListener('pointercancel', onUp);
        });
    }

    function saveGroupOrder(visibleIds) {
        K().loadCategories(true).then(function (cats) {
            var shown = {};
            visibleIds.forEach(function (id) { shown[id] = true; });
            var next = 0;
            var all = cats.map(function (c) { return shown[c.id] ? visibleIds[next++] : c.id; });
            return window.LWAuth.apiPost('/api/file-categories/reorder', { ids: all });
        }).then(function (r) {
            if (!r || !r.ok) { SL.toast(SL.failText(r, 'Could not save the new order.')); reload(); return; }
            K().invalidateCategories();
            SL.toast('Group order saved');
            reload();
        });
    }

    // ---- filing ---------------------------------------------------------------------------------

    /** File the given ids under what pickCategory resolved to. */
    function fileTo(ids, choice) {
        if (!choice || !ids.length) return Promise.resolve(false);
        var body = { fileIds: ids };
        if (choice.staging) body.staging = true;
        else if (choice.categoryId) body.categoryId = choice.categoryId;
        else body.categoryName = choice.categoryName;
        return SL.post('/api/solution-files/' + Page.id + '/file', body).then(function (r) {
            if (!r || !r.ok) { SL.toast(SL.failText(r, 'Could not file those files.')); return false; }
            if (body.categoryName) K().invalidateCategories();
            var label = choice.staging ? 'Staging'
                : choice.categoryName
                  || ((st.cats || []).find(function (c) { return c.id === choice.categoryId; }) || {}).name
                  || 'the category';
            SL.toast(plural(r.data.filed, 'file') + ' filed to ' + label);
            reload();
            return true;
        });
    }

    function refile(f) {
        K().pickCategory({
            title: 'File "' + (f.title || 'this file') + '"',
            currentId: f.categoryId || 0,
            allowStaging: !f.staged,
            preferIds: usedCategoryIds()
        }).then(function (choice) { fileTo([f.id], choice); });
    }

    function toggleFlag(f, btn) {
        var next = !f.important;
        btn.classList.toggle('on', next);
        btn.disabled = true;
        SL.put('/api/solution-files/' + Page.id + '/' + f.id + '/important', { important: next }).then(function (r) {
            btn.disabled = false;
            if (!r || !r.ok) { btn.classList.toggle('on', !next); SL.toast(SL.failText(r, 'Could not change it.')); return; }
            SL.toast(next ? 'Marked important' : 'No longer marked important');
            reload();
        });
    }

    function setSelecting(on) {
        st.selecting = on;
        if (!on) st.selected.clear();
        drawList();
    }

    // ---- upload (lands in Staging), download ------------------------------------------------------

    async function upload(fileList) {
        var files = Array.prototype.slice.call(fileList || []);
        if (!files.length) return;
        var done = 0, failed = [], newIds = [];
        for (var i = 0; i < files.length; i++) {
            var form = new FormData();
            form.append('file', files[i]);
            SL.status('Uploading ' + (i + 1) + ' of ' + files.length + ': ' + files[i].name);
            var r = await window.LWAuth.apiPostForm('/api/solutions/' + Page.id + '/files', form);
            if (!r || !r.ok) { failed.push(files[i].name + ' -- ' + SL.failText(r, 'could not be uploaded')); continue; }
            done++;
            if (r.data && r.data.id) newIds.push(r.data.id);
        }
        $('fileInput').value = '';
        await landed(done, newIds);
        if (failed.length) window.alert('These did not upload:\n\n' + failed.join('\n'));
    }

    /** After files arrive: say so, open Staging where they landed, flash them, redraw. */
    async function landed(done, newIds) {
        st.flash = new Set(newIds);
        if (done) {
            SL.toast(plural(done, 'file') + ' added to Staging');
            if (prefs().group !== 'category') vset(VS_GROUP, 'category');
            folds().delete('staging');
            saveFolds();
        }
        await Promise.all([load(), Page.reload()]);
        syncControls();
        setTimeout(function () { st.flash = new Set(); }, 4000);
    }

    // ---- + Add Files: the same source dialog every record's Files tab uses (entity-detail.js) ------
    // Reading Nook and Leonardo Files picks are copied into the solution through the house-only
    // /api/solution-files doors, secured and landing in Staging like an upload. No typed path.

    function openAddFiles() {
        var ED = window.LWED;
        if (!ED || !ED.openFileSourcePicker) { $('fileInput').click(); return; }
        ED.openFileSourcePicker(0, reload, {
            title:      'Add Files',
            uploadHint: 'Pick files from your phone or PC -- they are saved securely with this solution.',
            typedPath:  false,
            upload:     function () { $('fileInput').click(); },
            files:      function (files) { return upload(files); },
            where:      'this solution',
            nook:       function (pick) {
                attachFrom('/from-nook', { view: pick.viewKey, name: pick.name }, pick.name);
            },
            library:    function (leonardoFileId, picked) {
                attachFrom('/from-library', { leonardoFileId: leonardoFileId }, picked && picked.name);
            }
        });
    }

    async function attachFrom(path, body, name) {
        SL.status('Adding ' + (name || 'the file'));
        var r = await SL.post('/api/solution-files/' + Page.id + path, body);
        if (!r || !r.ok) { await Picasso.error(SL.failText(r, 'Could not add ' + (name || 'the file'))); return; }
        await landed(1, r.data && r.data.id ? [r.data.id] : []);
    }

    async function fetchBlob(id) {
        var r = await window.LWAuth.apiGetForBlob('/api/solutions/files/' + id + '/content');
        return (r && r.ok && r.blob) ? r.blob : null;
    }

    async function download(id) {
        var f = byId(id);
        var blob = await fetchBlob(id);
        if (!blob) { SL.toast('That file could not be downloaded.'); return; }
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = f ? f.originalFileName : 'file';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
    }

    /** The Leonardo Reader, for PDFs, pictures and plain text (see READER_EXT). It fetches the
     *  attachment with the session, exactly as it reads a Leonardo File. */
    function openReader(f) {
        var e = ext(f);
        var url = '/reader/reader.html?src=' + encodeURIComponent('/api/solutions/files/' + f.id + '/content')
                + '&type=' + encodeURIComponent(e) + '&title=' + encodeURIComponent(f.title || f.originalFileName || 'File');
        window.open(url, '_blank');
    }

    // ---- view: read the file here, without letting it run --------------------------------------

    function releaseView() {
        mViewSeq++;
        if (mViewUrl) { URL.revokeObjectURL(mViewUrl); mViewUrl = null; }
        if (mViewPdf) { try { mViewPdf.destroy(); } catch (e) {} mViewPdf = null; }
    }

    /** Open a file in the View sheet; resolves once it is drawn (Print waits for that). */
    async function openFile(id) {
        if (mLoading) { try { await mLoading; } catch (e) { /* the list says why */ } }
        for (var guard = 0; !byId(id) && st.hasMore && guard < 20; guard++) {
            var r = await SL.get(listUrl(st.rows.length));
            if (!r || !r.ok || !r.data) break;
            st.rows = st.rows.concat(r.data.rows || []);
            st.hasMore = !!r.data.hasMore;
        }
        if (!byId(id)) { SL.toast('That file is no longer on this solution.'); return; }
        drawList();
        await openView(id);
    }

    async function openView(id) {
        var f = byId(id);
        if (!f) return false;
        var kind = kindOf(f);
        releaseView();
        mViewId = id;
        var seq = mViewSeq;
        function current() { return seq === mViewSeq && mViewId === id; }
        $('fvTitle').textContent = f.title;
        $('fvMeta').textContent = f.originalFileName + ' | ' + size(f.sizeBytes) + ' | ' + SL.dateTime(f.createdDate)
            + (f.categoryName ? ' | ' + f.categoryName : ' | Staging');
        $('fvReader').hidden = !readerOk(f);
        $('fvPrint').hidden = !canPrint(f);
        var body = $('fvBody');
        body.className = 'sl-fileview-body' + (kind === 'image' ? ' image' : kind === 'html' ? ' frame' : '');
        body.innerHTML = '<div class="sl-empty">Opening&hellip;</div>';
        SL.open('fileViewOverlay');

        var blob = await fetchBlob(id);
        if (!current()) return false;
        if (!blob) { body.innerHTML = '<div class="sl-empty">That file could not be opened.</div>'; return false; }
        await draw(body, kind, blob, f, current, function (url) { mViewUrl = url; }, function (doc) { mViewPdf = doc; }, 'files-view');
        return current();
    }

    function closeView() {
        mViewId = 0;
        SL.close('fileViewOverlay');
        $('fvBody').innerHTML = '';
        releaseView();
    }

    /** Print what View drew, through the Leonardo Print menu. A PDF's pages are pictures on a canvas,
     *  which a copy of the page would lose, so they are turned into images first. Web pages are never
     *  printed this way: their HTML would be handed to the print renderer. */
    function printView() {
        var f = byId(mViewId);
        if (!f || !canPrint(f)) return;
        var area = document.createElement('div');
        area.id = 'slFilePrintArea';
        area.style.display = 'none';
        var head = document.createElement('h2');
        head.textContent = f.title || f.originalFileName;
        area.appendChild(head);
        var copy = $('fvBody').cloneNode(true);
        var live = $('fvBody').querySelectorAll('canvas');
        Array.prototype.forEach.call(copy.querySelectorAll('canvas'), function (c, i) {
            var img = document.createElement('img');
            try { img.src = live[i].toDataURL('image/png'); } catch (e) { return; }
            img.style.width = '100%';
            c.parentNode.replaceChild(img, c);
        });
        area.appendChild(copy);
        document.body.appendChild(area);
        window.Picasso.printThisPage({ selector: '#slFilePrintArea', title: f.title || 'File' });
        setTimeout(function () { if (area.parentNode) area.parentNode.removeChild(area); }, 2000);
    }

    async function printFile(id) {
        if (await openView(id)) printView();
    }

    // ---- open the folder on this computer ------------------------------------------------------

    async function openFolder(id) {
        var r = await SL.post('/api/solutions/files/' + id + '/open-folder', {});
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The folder could not be opened.')); return; }
        SL.toast('Opened in Explorer on the computer: ' + r.data.folder);
    }

    // ---- edit (title, notes AND filing), delete, storage ---------------------------------------

    var _catPicker = null;

    async function openEdit(id) {
        var f = byId(id);
        if (!f) return;
        // The list carries a notes preview only; read the whole file first so saving never cuts the notes.
        var r = await SL.get('/api/solutions/files/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'Could not load that file.')); return; }
        var full = r.data;
        mEditId = id;
        SL.warn('fileWarn', '');
        $('fTitle').value = full.title || '';
        $('fNotes').value = full.notes || '';
        $('fCategory').value = f.categoryName || '';
        $('fDocDate').value = f.documentDate || '';
        $('fImportant').checked = !!f.important;
        try { st.cats = await K().loadCategories(); } catch (e) { /* the box still takes a typed name */ }
        if (!_catPicker && window.PicassoPicker) {
            _catPicker = window.PicassoPicker.attach({
                input: $('fCategory'),
                dropdown: $('fCategoryList'),
                getItems: function () {
                    return (st.cats || []).map(function (c) { return { id: c.id, name: c.name }; })
                        .sort(function (a, b) { return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }); });
                },
                allowNew: true,
                newLabel: 'New category'
            });
        }
        SL.open('fileOverlay');
    }

    async function saveEdit() {
        var title = $('fTitle').value.trim();
        if (!title) { SL.warn('fileWarn', 'A file needs a title.'); return; }
        var r = await SL.put('/api/solutions/files/' + mEditId, { title: title, notes: $('fNotes').value });
        if (!r || !r.ok) { SL.warn('fileWarn', SL.failText(r, 'The file could not be saved.')); return; }
        var catName = $('fCategory').value.trim();
        var filing = { documentDate: $('fDocDate').value || '', important: $('fImportant').checked };
        if (catName) filing.categoryName = catName; else filing.staging = true;
        var fr = await SL.put('/api/solution-files/' + Page.id + '/' + mEditId + '/filing', filing);
        if (!fr || !fr.ok) { SL.warn('fileWarn', SL.failText(fr, 'The title was saved, but the filing could not be.')); return; }
        if (catName) K().invalidateCategories();
        SL.close('fileOverlay');
        await load();
    }

    async function remove(id) {
        var f = byId(id);
        if (!f) return;
        var where = 'The file is moved to the Echo Files trash folder, where you can still find it.';
        if (!window.confirm('Remove the file "' + f.title + '" (' + f.originalFileName + ', ' + size(f.sizeBytes) + ', '
                + (f.categoryName || 'Staging') + ') from ' + Page.solution.name + '?\n\n' + where)) return;
        var r = await SL.del('/api/solutions/files/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The file could not be removed.')); return; }
        await Promise.all([load(), Page.reload()]);
    }

    // Echo (Story 5653): every solution file is kept in Echo's own files folder, never in git.
    function paintStorage() {
        $('fileStorageChip').className = 'sl-chip';
        $('fileStorageChip').textContent = 'Files are kept in Echo Files on this computer';
    }

    function paintView() { $('btnFileView').textContent = rowsView() ? 'Cards' : 'Rows'; }

    function flipView() {
        vset(VS_ROWS, rowsView() ? '0' : '1');
        paintView();
        if (st.pane) drawList();
    }

    // ---- wiring ---------------------------------------------------------------------------------

    function rowOf(el) {
        var card = el.closest('.ef-card');
        if (!card) return null;
        return st.rows[parseInt(card.getAttribute('data-idx'), 10)] || null;
    }

    function wireShell() {
        var pane = st.pane;
        var searchTimer = null;

        pane.querySelector('.ef-search').addEventListener('input', function (e) {
            var v = e.target.value.trim();
            clearTimeout(searchTimer);
            searchTimer = setTimeout(function () {
                if (st.search === v) return;
                st.search = v;
                reload();
            }, 280);
        });

        pane.querySelector('.ef-sort-sel').addEventListener('change', function (e) {
            vset(VS_SORT, e.target.value);
            reload();
        });

        // Drop files from the desktop straight onto the tab: they upload to Staging.
        pane.addEventListener('dragover', function (e) {
            if (!e.dataTransfer || Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') < 0) return;
            e.preventDefault();
            pane.classList.add('ef-drop');
        });
        pane.addEventListener('dragleave', function (e) {
            if (e.target === pane) pane.classList.remove('ef-drop');
        });
        pane.addEventListener('drop', function (e) {
            if (!e.dataTransfer || !e.dataTransfer.files || !e.dataTransfer.files.length) return;
            e.preventDefault();
            pane.classList.remove('ef-drop');
            upload(e.dataTransfer.files);
        });

        pane.addEventListener('change', function (e) {
            var t = e.target;
            if (t.matches('[data-ef="check"]')) {
                var f = rowOf(t);
                if (!f) return;
                if (t.checked) st.selected.add(f.id); else st.selected.delete(f.id);
                t.closest('.ef-card').classList.toggle('ef-sel', t.checked);
                drawSelbar();
            } else if (t.matches('[data-ef="band-check"]')) {
                var key = t.closest('.ef-band').getAttribute('data-group');
                st.rows.forEach(function (r) {
                    if (r.groupKey !== key) return;
                    if (t.checked) st.selected.add(r.id); else st.selected.delete(r.id);
                });
                drawList();
            }
        });

        pane.addEventListener('click', function (e) {
            var t = e.target;

            if (st.selecting && !t.closest('button, input, a')) {
                var card = t.closest('.ef-card');
                if (card) {
                    var fr = rowOf(card);
                    if (fr) {
                        if (st.selected.has(fr.id)) st.selected.delete(fr.id); else st.selected.add(fr.id);
                        drawList();
                    }
                    return;
                }
            }

            if (previewing() && !st.selecting && !t.closest('button, input, a, select, [data-act]')) {
                var pc = t.closest('.ef-card');
                if (pc) { previewSelect(rowOf(pc)); return; }
            }

            var b = t.closest('[data-ef], [data-act="refile"], [data-act="quick"]');
            if (!b || !pane.contains(b)) return;
            var act = b.getAttribute('data-ef') || b.getAttribute('data-act');
            var f = rowOf(b);

            switch (act) {
                case 'tools': {
                    var collapse = !toolsCollapsed();
                    vset(VS_TOOLS, collapse ? '1' : '0');
                    pane.classList.toggle('ef-tools-collapsed', collapse);
                    b.setAttribute('aria-expanded', String(!collapse));
                    b.querySelector('.ef-caret').innerHTML = collapse ? '&#9656;' : '&#9662;';
                    drawSummary();
                    break;
                }
                case 'add':        openAddFiles(); break;
                case 'select':     setSelecting(!st.selecting); break;
                case 'categories': K().manageCategories({ onChange: reload }); break;
                case 'dir':
                    vset(VS_DESC, prefs().desc ? '0' : '1');
                    syncControls();
                    reload();
                    break;
                case 'more':       loadMore(); break;
                case 'view':       if (f) openView(f.id); break;
                case 'preview':    setPreview(!previewing()); break;
                case 'layout':     if (st.split) st.split.toggleLayout(); break;
                case 'pv-reader':  if (byId(st.pv)) openReader(byId(st.pv)); break;
                case 'pv-view':    if (byId(st.pv)) openView(st.pv); break;
                case 'show-staging':
                    vset(VS_GROUP, 'category');
                    folds().delete('staging');
                    saveFolds();
                    syncControls();
                    reload();
                    break;
                case 'arrange':
                    st.arranging = !st.arranging;
                    drawList();
                    break;
                case 'expand-all':
                case 'collapse-all': {
                    var bands = pane.querySelectorAll('.ef-band');
                    var foldAll = act === 'collapse-all';
                    Array.prototype.forEach.call(bands, function (x) {
                        var key = x.getAttribute('data-group');
                        if (foldAll) folds().add(key); else folds().delete(key);
                        setBandCollapsed(x, foldAll);
                    });
                    saveFolds();
                    syncFoldAll();
                    break;
                }
                case 'toggle': {
                    var band = b.closest('.ef-band');
                    var gk = band.getAttribute('data-group');
                    var nowCollapsed = !isFolded(gk);
                    if (nowCollapsed) folds().add(gk); else folds().delete(gk);
                    saveFolds();
                    setBandCollapsed(band, nowCollapsed);
                    syncFoldAll();
                    break;
                }
                case 'file-all': {
                    var ids = st.rows.filter(function (r) { return r.staged; }).map(function (r) { return r.id; });
                    K().pickCategory({
                        title: 'File ' + plural(ids.length, 'staged file'),
                        subtitle: 'Every file in staging goes to the category you pick.',
                        preferIds: usedCategoryIds()
                    }).then(function (choice) { fileTo(ids, choice); });
                    break;
                }
                case 'sel-file':
                    K().pickCategory({
                        title: 'File ' + plural(st.selected.size, 'selected file'),
                        preferIds: usedCategoryIds()
                    }).then(function (choice) {
                        return fileTo(Array.from(st.selected), choice);
                    }).then(function (ok) { if (ok) setSelecting(false); });
                    break;
                case 'sel-staging':
                    fileTo(Array.from(st.selected), { staging: true }).then(function (ok) { if (ok) setSelecting(false); });
                    break;
                case 'sel-cancel': setSelecting(false); break;
                case 'refile':     if (f) refile(f); break;
                case 'quick':      if (f) fileTo([f.id], { categoryId: parseInt(b.getAttribute('data-cat-id'), 10) }); break;
                case 'flag':       if (f) toggleFlag(f, b); break;
                case 'reader':     if (f) openReader(f); break;
                case 'download':   if (f) download(f.id); break;
                case 'folder':     if (f) SL.guard(b, function () { return openFolder(f.id); }); break;
                case 'print':      if (f) printFile(f.id); break;
                case 'edit':       if (f) openEdit(f.id); break;
                case 'del':        if (f) remove(f.id); break;
            }
        });

        // Preview view: a double-click opens the file in View; up and down walk the files.
        pane.addEventListener('dblclick', function (e) {
            if (!previewing() || st.selecting || e.target.closest('button, input, a, select, [data-act]')) return;
            var card = e.target.closest('.ef-card');
            var f = card && rowOf(card);
            if (f && kindOf(f)) openView(f.id);
        });

        pane.querySelectorAll('.ef-seg [data-group]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                vset(VS_GROUP, btn.getAttribute('data-group'));
                syncControls();
                reload();
            });
        });
    }

    Page.registerTab('files', {
        init: function () {
            $('fileInput').addEventListener('change', function () { upload($('fileInput').files); });
            $('btnFileView').addEventListener('click', flipView);
            $('fileCancel').addEventListener('click', function () { SL.close('fileOverlay'); });
            $('fileSave').addEventListener('click', function () { SL.guard($('fileSave'), saveEdit); });
            $('fvClose').addEventListener('click', closeView);
            $('fvDownload').addEventListener('click', function () { if (mViewId) download(mViewId); });
            $('fvFolder').addEventListener('click', function () { if (mViewId) SL.guard($('fvFolder'), function () { return openFolder(mViewId); }); });
            $('fvReader').addEventListener('click', function () { var f = byId(mViewId); if (f) openReader(f); });
            $('fvPrint').addEventListener('click', printView);
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' && $('fileViewOverlay').classList.contains('open')) { closeView(); return; }
                if ((e.key !== 'ArrowDown' && e.key !== 'ArrowUp') || !previewing() || st.selecting) return;
                if (!st.pane || st.pane.offsetParent === null) return;
                var a = document.activeElement;
                if (a && a !== document.body && (a.closest('input, textarea, select, [contenteditable="true"]')
                    || (a.closest('.sl-overlay.open, [role="dialog"]') && !st.pane.contains(a)))) return;
                if (a && a.classList && a.classList.contains('ps-splitter')) return;
                e.preventDefault();
                previewStep(e.key === 'ArrowDown' ? 1 : -1);
            });
        },
        show: function () { st.arranging = false; mLoading = load(); return mLoading; },
        /** A deep link (?file=, Leonardo Find) wants one file shown: wait for the tab's own load,
         *  page on until the row is there, then open the sepia View. */
        openFile: openFile
    });
})();
