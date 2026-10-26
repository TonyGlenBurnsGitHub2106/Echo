/* Leonardo Reader -- LeonardoWeb experimental viewer (Story 2286, impl of
   spike 2278). Renders the document named in the URL as responsive, zoomable,
   reflowable HTML -- 100% client-side. ASCII-only.

   URL:  reader.html?src=<same-origin API url>&type=<ext>&title=<t>

   Unlike the desktop loopback build, this page lives INSIDE LeonardoWeb: it
   authenticates itself through the standard cookie refresh (LWAuth.ensureAuth)
   and pulls the file's bytes WITH the JWT via LWAuth.apiGetForBlob, then hands
   the already-fetched bytes to the renderer. Nothing else on the server side
   is touched -- it reuses the existing file-serving endpoint.

   Modes:
     PDF  -> Fidelity (pdf.js canvas render, zoom, page nav)
             Reader   (pdf.js text extraction reflowed to page width, font stepper)
     md   -> reflowed prose (minimal markdown), font stepper
     txt  -> reflowed prose (line breaks preserved), font stepper
     img  -> fit-to-width image with zoom

   Marking (Story 2307) lives in reader-marks.js: whenever a reflowed render is
   on screen this file hands it the prose element, and the marking layer takes
   over selection, highlights, excerpts, definitions, and online research.

   Kindle parity (Story 2422) added three more neighbours, and this file's job
   for all of them is the same -- render the document, then hand the render over:

     reader-place.js  where you stopped reading, and the progress strip
     reader-find.js   searching inside the document
     reader-marks.js  highlights, bookmarks, notes, the notebook

   What stayed here is what needs the renderer: how the page is SET (text size,
   typeface, line spacing, margins), the table of CONTENTS (a PDF carries an
   outline; markdown carries headings), and keeping the screen awake. Every one
   of those is a display preference, so they live on the device in localStorage
   and never go near the database.
*/
(function () {
  "use strict";

  var qs = new URLSearchParams(window.location.search);
  // "src" is the canonical param; "doc" is accepted for parity with the
  // desktop build's URL shape.
  var SRC = qs.get("src") || qs.get("doc") || "";
  var RAW_TYPE = (qs.get("type") || "").toLowerCase().replace(/^\./, "");
  var TITLE = qs.get("title") || friendlyName(SRC);
  // Who wrote it and what work it is. Only a caller that knows can say, so both
  // are optional; the work falls back to the document's title, which is what a
  // quotation taken out of it should be credited to (Story 2429).
  var AUTHOR = qs.get("author") || "";
  var WORK = qs.get("work") || TITLE;

  // A document opened to be READ starts reflowed, because that is the only view
  // where text can be selected, highlighted, and saved as an excerpt. Coming in
  // from a Research project always means reading; ?view= can force either way.
  // A PDF opened cold from the Reading Nook still starts in Fidelity.
  var WANT_READER_VIEW = (qs.get("view") || "").toLowerCase() === "reader" ||
                         ((qs.get("view") || "") === "" && !!qs.get("project"));

  var elStatus = document.getElementById("lrStatus");
  var elContent = document.getElementById("lrContent");
  var elTitle = document.getElementById("lrTitle");
  var elZoomReadout = document.getElementById("lrZoomReadout");
  var elPageReadout = document.getElementById("lrPageReadout");

  document.title = TITLE ? (TITLE + " -- Echo Reader") : "Echo Reader";
  if (elTitle) elTitle.textContent = TITLE;

  var IMG_EXT = ["png", "jpg", "jpeg", "gif", "webp", "svg", "bmp"];
  var kind = classify(RAW_TYPE, SRC);

  // Stable identity of the document being read, used to store and restore
  // highlights (Story 2307). See docKeyOf for how it is derived.
  var PROJECT_ID = parseInt(qs.get("project") || "0", 10) || 0;
  var DOC_KEY = docKeyOf(SRC);
  // ?mark=<ReaderHighlightID> -- opened from the Highlights shelf (Story 5613) to
  // show one highlight. The marking layer scrolls to it once the marks are
  // painted, so the saved reading place is NOT restored on that first render.
  var FOCUS_MARK_ID = parseInt(qs.get("mark") || "0", 10) || 0;

  // How the reader likes the page set. A display preference per device, exactly
  // like the highlight colour -- it is not data, so it does not go to the server.
  var TYPE_KEYS = {
    font:    "leoReaderFontSize",
    family:  "leoReaderFamily",
    leading: "leoReaderLeading",
    measure: "leoReaderMeasure",
    awake:   "leoReaderKeepAwake",
    theme:   "leoReaderTheme"
  };

  var THEMES = ["light", "sepia", "dark"];

  var FAMILIES = {
    serif: 'Georgia, "Iowan Old Style", "Times New Roman", serif',
    sans:  '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
    mono:  '"SF Mono", Consolas, "Liberation Mono", Menlo, monospace'
  };
  if (window.LeoReaderMarks) {
    window.LeoReaderMarks.init({
      docKey: DOC_KEY,
      docTitle: TITLE,
      docAuthor: AUTHOR,
      docWork: WORK,
      // Set when the reader was launched from a Research project's Files tab,
      // so Save Excerpt defaults to that project instead of the whole list.
      projectId: PROJECT_ID,
      focusMarkId: FOCUS_MARK_ID,
      // The dock offers a way out of Fidelity view, where marking is impossible.
      onWantReaderView: function () {
        var btn = document.querySelector('[data-mode="reader"]');
        if (btn) setPdfMode("reader", btn);
      }
    });
  }

  // Shared state
  var state = {
    zoom: 1.0,          // fidelity / image zoom multiplier
    readerFont: 18,     // px, reader/md/txt
    family: "serif",    // reflowed typeface
    leading: "1.62",    // reflowed line spacing
    measure: "44",      // reflowed column width, in rem
    keepAwake: true,    // hold the screen on while a document is open
    theme: "light",     // page colour -- light / sepia / dark
    pdfDoc: null,
    pdfMode: WANT_READER_VIEW ? "reader" : "fidelity",
    renderToken: 0,
    pdfData: null,      // Uint8Array of the fetched PDF bytes (re-used on re-render)
    textData: null,     // decoded text for md/txt
    imageUrl: null,     // object URL for image blobs
    toc: null,          // table of contents, built the first time it is asked for
    placeRestored: false, // the saved place is gone back to ONCE, on first render
    wantRestore: false  // ...except after a type change, which re-lays the text out
  };

  loadTypePreferences();
  applyTypePreferences();

  if (window.LeoReaderPlace) {
    window.LeoReaderPlace.init({ docKey: DOC_KEY, docTitle: TITLE, stage: document.getElementById("lrStage") });
  }
  if (window.LeoReaderFind) {
    window.LeoReaderFind.init({ stage: document.getElementById("lrStage") });
  }

  wireControls();
  wireTypeSheet();
  wireContentsSheet();
  wireWakeLock();
  showGroupsFor(kind);

  if (!SRC) {
    fail("No document was supplied.");
    return;
  }

  // Authenticate this page (cookie refresh), then fetch the file's bytes WITH
  // the JWT and hand them to the matching renderer. A single fetch serves
  // every type -- pdf.js reads the bytes, text is decoded, images get an
  // object URL. Nothing is fetched cross-context, so no blob-handoff is needed.
  boot();

  function boot() {
    if (!window.LWAuth || !window.LWAuth.ensureAuth) {
      fail("This reader could not start (auth unavailable).");
      return;
    }
    setStatus("Loading...");
    window.LWAuth.ensureAuth().then(function (tok) {
      // ensureAuth redirects to "/" itself when there is no session; a null
      // return means that redirect is already happening.
      if (tok === null) return;
      // The saved place is fetched alongside the bytes rather than after the
      // render, so going back to it happens the moment the text lands instead
      // of visibly jumping a beat later.
      if (window.LeoReaderPlace) window.LeoReaderPlace.load();
      return window.LWAuth.apiGetForBlob(SRC);
    }).then(function (res) {
      if (!res) return;                 // redirected / no session
      if (!res.ok || !res.blob) {
        fail("Could not open this document (HTTP " + (res.status || "?") + ").");
        return;
      }
      dispatch(res.blob, res.contentType);
    }).catch(function (err) {
      fail("Could not load this document.\n" + msgOf(err));
    });
  }

  function dispatch(blob, contentType) {
    // If no explicit type was passed, fall back to the response content type.
    if (kind === "unknown" && contentType) {
      kind = classifyMime(contentType);
      showGroupsFor(kind);
    }
    if (kind === "pdf") {
      blob.arrayBuffer().then(function (buf) {
        state.pdfData = new Uint8Array(buf);
        startPdf();
      }).catch(function (err) { fail("Could not read this PDF.\n" + msgOf(err)); });
    } else if (kind === "markdown" || kind === "text") {
      blob.text().then(function (txt) {
        state.textData = txt;
        startText(kind === "markdown");
      }).catch(function (err) { fail("Could not read this document.\n" + msgOf(err)); });
    } else if (kind === "html") {
      blob.text().then(function (txt) {
        state.textData = txt;
        startHtml();
      }).catch(function (err) { fail("Could not read this document.\n" + msgOf(err)); });
    } else if (kind === "image") {
      state.imageUrl = URL.createObjectURL(blob);
      startImage();
    } else {
      fail("This file type is not supported in the Echo Reader yet." +
           (RAW_TYPE ? (" (." + RAW_TYPE + ")") : "") +
           "\n\nOpen it with its normal application instead.");
    }
  }

  // ---------------------------------------------------------------- helpers

  function msgOf(err) { return (err && err.message) ? err.message : String(err); }

  function classify(type, url) {
    var t = type || extOf(url);
    if (t === "pdf") return "pdf";
    if (t === "md" || t === "markdown" || t === "mkd" || t === "mdown") return "markdown";
    if (t === "html" || t === "htm" || t === "xhtml") return "html";
    if (t === "txt" || t === "text" || t === "log" || t === "csv") return "text";
    if (IMG_EXT.indexOf(t) >= 0) return "image";
    return "unknown";
  }

  function classifyMime(mime) {
    mime = (mime || "").toLowerCase();
    if (mime.indexOf("application/pdf") >= 0) return "pdf";
    if (mime.indexOf("image/") === 0) return "image";
    if (mime.indexOf("markdown") >= 0) return "markdown";
    if (mime.indexOf("text/html") === 0 || mime.indexOf("xhtml") >= 0) return "html";
    if (mime.indexOf("text/") === 0) return "text";
    return "unknown";
  }

  function extOf(u) {
    // The source is an API URL like /api/reading-nook/file?name=Foo.pdf&...
    // so read the extension off the "name" query param when present, then
    // fall back to the path.
    try {
      var q = u.split("?")[1] || "";
      var params = new URLSearchParams(q);
      var nm = params.get("name");
      if (nm) {
        var mm = /\.([a-z0-9]+)$/i.exec(nm);
        if (mm) return mm[1].toLowerCase();
      }
    } catch (e) { /* fall through */ }
    var m = /\.([a-z0-9]+)(?:[?#]|$)/i.exec(u || "");
    return m ? m[1].toLowerCase() : "";
  }

  // The identity a document's highlights are filed under. It has to be STABLE
  // (the same document must key the same way every time) and DISTINCT (two
  // different documents must never share a key).
  //
  // A Leonardo file is keyed by its catalog id, because its url is
  // /api/leonardo-files/<id>/content -- reading a name off the path would give
  // every research file the key "file:content" and pile every document's marks
  // into one bucket, which is exactly the bug this replaced.
  //
  // A Reading Nook file carries ?name=Foo.pdf, and keying by the name (not the
  // whole url) means moving it between nook buckets keeps its marks.
  //
  // The LAST PATH SEGMENT IS NEVER USED as the key (Story 2459). On an API url
  // it is a verb, not a name -- /api/thinker/entries/12/text ends in "text", so
  // every thought in The Thinker keyed as "file:text" and they all shared one
  // saved place: open thought B and it went back to where thought A stopped.
  // When there is no ?name= and no catalog id, the whole path is the key, which
  // is always distinct per document.
  function docKeyOf(u) {
    var explicit = (qs.get("key") || "").trim();
    if (explicit) return explicit;

    var lf = /\/api\/leonardo-files\/(\d+)\b/i.exec(u || "");
    if (lf) return "lfile:" + lf[1];

    var nm = nameParamOf(u);
    if (nm) return "file:" + nm;
    return "url:" + ((u || "").split("?")[0] || "unknown");
  }

  // The ?name= a Reading Nook url carries, and nothing else. Deliberately does
  // NOT fall back to the last path segment -- see docKeyOf.
  function nameParamOf(u) {
    try {
      var q = (u || "").split("?")[1] || "";
      return new URLSearchParams(q).get("name") || "";
    } catch (e) { return ""; }
  }

  // A title to fall back on when the caller passed none. The last path segment
  // of an API url is often a verb ("content", "raw") rather than a name, and
  // showing that as the document's title is worse than showing nothing.
  function friendlyName(u) {
    var nm = decodeName(u);
    if (!nm || nm === "content" || nm === "raw") return "";
    return nm;
  }

  function decodeName(u) {
    try {
      var q = (u || "").split("?")[1] || "";
      var nm = new URLSearchParams(q).get("name");
      if (nm) return nm;
      var p = (u || "").split(/[?#]/)[0];
      var last = p.split("/").pop();
      return decodeURIComponent(last || "");
    } catch (e) { return ""; }
  }

  function setStatus(msg) {
    if (!elStatus) return;
    elStatus.textContent = msg || "";
    elStatus.classList.remove("is-error");
    elStatus.style.display = msg ? "" : "none";
  }

  function fail(msg) {
    if (!elStatus) return;
    elStatus.textContent = msg || "Something went wrong.";
    elStatus.classList.add("is-error");
    elStatus.style.display = "";
  }

  function clearContent() { elContent.innerHTML = ""; }

  /**
   * One render finished. Everything that reads the rendered document rather
   * than producing it is told here, in one place, so a new render can never
   * leave one of them pointing at the DOM of the last one.
   *
   * `prose` is the reflowed element, or null when what is on screen has no
   * selectable text (PDF Fidelity pages, an image).
   *
   * Going back to the saved place happens ONCE per document, on the first
   * render. Re-doing it on every render would fight the reader: switching to
   * Fidelity, or stepping the text size, would yank the page back to where the
   * session started instead of leaving them where they are.
   */
  function afterRender(prose) {
    if (window.LeoReaderFind) window.LeoReaderFind.setProse(prose);

    var place = window.LeoReaderPlace;
    if (!place) return;
    place.setView({ prose: prose, viewMode: viewModeName(), pageCount: pageCount() });

    var first = !state.placeRestored;
    if (first && FOCUS_MARK_ID) { state.placeRestored = true; return; }
    if (!first && !state.wantRestore) return;
    state.placeRestored = true;
    var announce = first;
    state.wantRestore = false;
    place.restore().then(function (moved) {
      if (moved && announce) toast("Back where you left off -- " + place.progressLabel());
    });
  }

  /** The marking layer owns the toast; there is no reason for a second one. */
  function toast(msg) {
    if (window.LeoReaderMarks && window.LeoReaderMarks.toast) window.LeoReaderMarks.toast(msg);
  }

  // ---------------------------------------------------------------- controls

  // The settings row folds. A reader opens on the document, not on its own
  // toolbar, so the row starts CLOSED the first time and afterwards comes back
  // the way it was left on this device -- the same bargain every Library shelf
  // makes with its Options panel.
  function wireSettingsWedge() {
    var btn = document.getElementById("lrSettingsBtn");
    var row = document.getElementById("lrControls");
    if (!btn || !row) return;

    var open = pref("lr.controls.open") === "1";

    function paint() {
      row.classList.toggle("open", open);
      btn.classList.toggle("open", open);
      btn.setAttribute("aria-expanded", open ? "true" : "false");
    }

    btn.addEventListener("click", function () {
      open = !open;
      setPref("lr.controls.open", open ? "1" : "0");
      paint();
    });

    paint();
  }

  // The whole header rolls up, so the page is nothing but the words. It starts
  // rolled up; Collapse (or a tap on the name or the title) rolls it, and the
  // Expand tab on the top edge brings it back. Remembered for the user in the
  // database, so the desk and the phone agree. The key records "expanded", so
  // a user who never chose anything gets the rolled-up default.
  var HEADER_EXPANDED_KEY = "leo.reader.headerExpanded";

  function wireHeaderRollup() {
    var bar = document.getElementById("lrBar");
    var rollBtn = document.getElementById("lrRollupBtn");
    var collapseBtn = document.getElementById("lrCollapseBtn");
    var unrollBtn = document.getElementById("lrUnrollBtn");
    var vs = window.PicassoViewState;
    if (!bar || !rollBtn || !unrollBtn) return;

    function rolled() { return !vs || vs.get(HEADER_EXPANDED_KEY) !== "1"; }

    function paint() {
      var r = rolled();
      bar.classList.toggle("rolled", r);
      unrollBtn.hidden = !r;
      rollBtn.setAttribute("aria-expanded", r ? "false" : "true");
    }

    function setRolled(r) {
      if (vs) vs.set(HEADER_EXPANDED_KEY, r ? "0" : "1");
      paint();
    }

    rollBtn.addEventListener("click", function () { setRolled(true); });
    if (collapseBtn) collapseBtn.addEventListener("click", function () { setRolled(true); });
    unrollBtn.addEventListener("click", function () { setRolled(false); });

    paint();
    if (vs) vs.sync(HEADER_EXPANDED_KEY).then(function (changed) { if (changed) paint(); });
  }

  function wireControls() {
    wireHeaderRollup();
    wireSettingsWedge();
    document.getElementById("lrControls").addEventListener("click", function (ev) {
      var t = ev.target;
      if (!t || t.nodeName !== "BUTTON") return;

      var theme = t.getAttribute("data-theme");
      if (theme) { setTheme(theme, t); return; }

      var mode = t.getAttribute("data-mode");
      if (mode) { setPdfMode(mode, t); return; }

      runAct(t.getAttribute("data-act"));
    });

    // The Type sheet carries its own A- / A+ and the find bar its own buttons;
    // both go through the same actions, so the behaviour cannot drift.
    document.addEventListener("click", function (ev) {
      var t = ev.target;
      if (!t || t.nodeName !== "BUTTON") return;
      if (!t.closest("#lrTypeSheet, #lrFindBar")) return;
      runAct(t.getAttribute("data-act"));
    });

    var reReflow = debounce(function () {
      if (kind === "pdf" && state.pdfMode === "fidelity") renderPdfFidelity();
    }, 200);
    window.addEventListener("resize", reReflow);
  }

  function runAct(act) {
    if (!act) return;
    switch (act) {
      case "zoom-in":  changeZoom(+0.2); break;
      case "zoom-out": changeZoom(-0.2); break;
      case "page-prev": gotoPageDelta(-1); break;
      case "page-next": gotoPageDelta(+1); break;
      case "font-up":   changeFont(+2); break;
      case "font-down": changeFont(-2); break;
      case "marks-list": if (window.LeoReaderMarks) window.LeoReaderMarks.openList(); break;
      case "bookmark":   if (window.LeoReaderMarks) window.LeoReaderMarks.addBookmark(); break;
      case "type":       openSheet("lrTypeSheet"); break;
      case "contents":   openContents(); break;
      case "find":       if (window.LeoReaderFind) window.LeoReaderFind.toggle(); break;
      case "find-next":  if (window.LeoReaderFind) window.LeoReaderFind.next(); break;
      case "find-prev":  if (window.LeoReaderFind) window.LeoReaderFind.prev(); break;
      case "find-close": if (window.LeoReaderFind) window.LeoReaderFind.close(); break;
      case "listen":     listen(); break;
    }
  }

  // ---------------------------------------------------------------- listen

  // Read the document aloud with the same Listen player the Reading Nook uses
  // (reading-nook/nook-reader.js), so the voice is the one chosen for the Leonardo
  // Reader on Admin -> Voice Settings and the place is kept per document, across
  // devices (Story 5648). Text and Markdown are read from their own words, so a
  // transcript keeps a voice per speaker; HTML is read from what the reader shows;
  // a PDF from its text layer, in either view.
  /** A Listen problem waits for OK (Picasso Dialog) -- never a toast that fades. */
  function listenMessage(msg) {
    if (window.Picasso && window.Picasso.message) window.Picasso.message(msg); else window.alert(msg);
  }

  function listen() {
    if (!window.NookReader || !window.NookReader.playText) { listenMessage("Listen is not available here."); return; }
    var key = DOC_KEY + ":listen";
    if (kind === "markdown" || kind === "text") {
      if (!state.textData || !state.textData.trim()) { listenMessage("Nothing readable to read aloud."); return; }
      window.NookReader.playText(state.textData, TITLE || "Listening", key, false);
      return;
    }
    if (kind === "html") {
      var prose = currentProse();
      var words = prose ? (prose.innerText || prose.textContent || "") : "";
      if (!words.trim()) { listenMessage("Nothing readable to read aloud."); return; }
      window.NookReader.playText(words, TITLE || "Listening", key, true);
      return;
    }
    if (kind === "pdf") {
      pdfPlainText().then(function (words) {
        if (!words.trim()) { listenMessage("This PDF has no text to read aloud (it may be a scan)."); return; }
        window.NookReader.playText(words, TITLE || "Listening", key, true);
      }).catch(function (err) { listenMessage("Could not read this PDF aloud. " + msgOf(err)); });
    }
  }

  // Every page's words, paragraph by paragraph -- the same grouping the Reader view uses.
  function pdfPlainText() {
    var doc = state.pdfDoc;
    if (!doc) return Promise.resolve("");
    var out = [];
    var chain = Promise.resolve();
    for (var i = 1; i <= doc.numPages; i++) {
      (function (pageNum) {
        chain = chain.then(function () {
          return doc.getPage(pageNum).then(function (page) {
            return page.getTextContent().then(function (tc) {
              var paras = itemsToParagraphs(tc.items);
              for (var p = 0; p < paras.length; p++) if (paras[p]) out.push(paras[p]);
            });
          });
        });
      })(i);
    }
    return chain.then(function () { return out.join("\n\n"); });
  }

  /** Sheets are opened and closed by the marking layer, which owns the scrim. */
  function openSheet(id) {
    if (window.LeoReaderMarks && window.LeoReaderMarks.openSheet) window.LeoReaderMarks.openSheet(id);
  }

  function showGroupsFor(k) {
    var show = {
      mode:  k === "pdf",
      zoom:  k === "pdf" || k === "image",
      page:  k === "pdf",
      font:  k === "markdown" || k === "text" || k === "html",
      find:  k === "pdf" || k === "markdown" || k === "text" || k === "html",
      listen: k === "pdf" || k === "markdown" || k === "text" || k === "html",
      marks: k === "pdf" || k === "markdown" || k === "text" || k === "html"
    };
    document.querySelectorAll("[data-group]").forEach(function (g) {
      var name = g.getAttribute("data-group");
      if (name === "theme") return;             // theme always visible
      g.hidden = !show[name];
    });
    // In PDF the page/zoom groups belong to Fidelity, font to Reader.
    syncPdfGroupVisibility();
  }

  function syncPdfGroupVisibility() {
    if (kind !== "pdf") return;
    var fidelity = state.pdfMode === "fidelity";
    setGroupHidden("zoom", !fidelity);
    setGroupHidden("page", !fidelity);
    setGroupHidden("font", fidelity);
    // Marking needs selectable text, which only the reflowed Reader view has.
    setGroupHidden("marks", fidelity);
    // Contents survives Fidelity (a PDF outline knows its page numbers), but
    // searching does not -- the pages there are pictures with no text in them.
    setActHidden("find", fidelity);
    if (fidelity && window.LeoReaderFind && window.LeoReaderFind.isOpen()) {
      window.LeoReaderFind.close();
    }
  }

  function setActHidden(act, hidden) {
    var b = document.querySelector('.lr-controls [data-act="' + act + '"]');
    if (b) b.hidden = hidden;
  }

  function setGroupHidden(name, hidden) {
    var g = document.querySelector('[data-group="' + name + '"]');
    if (g) g.hidden = hidden;
  }

  function setTheme(theme, btn) {
    state.theme = THEMES.indexOf(theme) >= 0 ? theme : "light";
    saveTypePreferences();
    document.body.className = "theme-" + state.theme + " ql-no-pin";
    activate(btn);
  }

  /** Light up the segmented button for a mode we entered without a tap. */
  function markActiveMode(mode) {
    var btn = document.querySelector('[data-mode="' + mode + '"]');
    if (btn) activate(btn);
  }

  function activate(btn) {
    var sibs = btn.parentNode.querySelectorAll(".lr-seg-btn");
    sibs.forEach(function (s) { s.classList.remove("is-active"); });
    btn.classList.add("is-active");
  }

  function changeZoom(delta) {
    state.zoom = clamp(state.zoom + delta, 0.4, 4.0);
    if (elZoomReadout) elZoomReadout.textContent = Math.round(state.zoom * 100) + "%";
    if (kind === "pdf") renderPdfFidelity();
    else if (kind === "image") applyImageZoom();
  }

  /**
   * Text size is the one type control that changes the LAYOUT rather than just
   * the look, so it saves the place first and goes back to it after -- otherwise
   * stepping the size up loses the reader's spot in a long document.
   */
  function changeFont(delta) {
    var before = clamp(state.readerFont + delta, 12, 40);
    if (before === state.readerFont) return;
    state.readerFont = before;
    saveTypePreferences();
    applyTypePreferences();
    reflowKeepingPlace();
  }

  /**
   * Re-set the page and land back where the reader was. The place is written
   * before the change and restored after the browser has re-laid the text out,
   * which is why this waits a frame rather than restoring inline.
   */
  function reflowKeepingPlace() {
    var place = window.LeoReaderPlace;
    if (place) place.saveNow();
    if (kind === "pdf" && state.pdfMode === "reader") {
      // A PDF's Reader view is rebuilt from scratch, so landing back at the
      // place has to happen through afterRender -- which otherwise restores
      // only on the first render of a document.
      state.wantRestore = true;
      renderPdfReader();
      return;
    }
    if (!place) return;
    window.requestAnimationFrame(function () {
      place.setView({ prose: currentProse(), viewMode: viewModeName(), pageCount: pageCount() });
      place.restore();
    });
  }

  function currentProse() { return elContent.querySelector(".lr-prose"); }

  function viewModeName() { return kind === "pdf" ? state.pdfMode : kind; }

  function pageCount() { return state.pdfDoc ? state.pdfDoc.numPages : 0; }

  // ------------------------------------------------------- how the page is set

  /** Read the reader's type preferences off this device, defaults where unset. */
  function loadTypePreferences() {
    state.readerFont = clamp(intPref(TYPE_KEYS.font, 18), 12, 40);
    state.family = FAMILIES[pref(TYPE_KEYS.family)] ? pref(TYPE_KEYS.family) : "serif";
    state.leading = pref(TYPE_KEYS.leading) || "1.62";
    state.measure = pref(TYPE_KEYS.measure) || "44";
    state.keepAwake = pref(TYPE_KEYS.awake) !== "0";
    var savedTheme = pref(TYPE_KEYS.theme);
    state.theme = THEMES.indexOf(savedTheme) >= 0 ? savedTheme : "light";
  }

  function saveTypePreferences() {
    setPref(TYPE_KEYS.font, String(state.readerFont));
    setPref(TYPE_KEYS.family, state.family);
    setPref(TYPE_KEYS.leading, state.leading);
    setPref(TYPE_KEYS.measure, state.measure);
    setPref(TYPE_KEYS.awake, state.keepAwake ? "1" : "0");
    setPref(TYPE_KEYS.theme, state.theme);
  }

  /**
   * Push the preferences onto the document root rather than onto each rendered
   * prose element. Custom properties inherit, so a render that happens later
   * picks them up with no code of its own -- which is what keeps the type
   * controls from having to know when a document was re-rendered.
   */
  function applyTypePreferences() {
    var root = document.documentElement.style;
    root.setProperty("--reader-font", state.readerFont + "px");
    root.setProperty("--reader-family", FAMILIES[state.family] || FAMILIES.serif);
    root.setProperty("--reader-leading", state.leading);
    root.setProperty("--reader-measure", state.measure + "rem");
    applyTheme();
    syncTypeSheet();
  }

  /**
   * The page colour is a display preference like every other one here, so it is
   * restored from the device on the way in -- leaving the reader and coming back
   * must not throw you off Sepia.
   */
  function applyTheme() {
    document.body.className = "theme-" + state.theme + " ql-no-pin";
    var btn = document.querySelector('.lr-controls [data-theme="' + state.theme + '"]');
    if (btn) activate(btn);
  }

  function wireTypeSheet() {
    wireSegment("lrTypeFamily", "data-family", function (v) {
      state.family = FAMILIES[v] ? v : "serif";
      saveTypePreferences();
      applyTypePreferences();
    });
    wireSegment("lrTypeLeading", "data-leading", function (v) {
      state.leading = v;
      saveTypePreferences();
      applyTypePreferences();
      reflowKeepingPlace();
    });
    wireSegment("lrTypeMeasure", "data-measure", function (v) {
      state.measure = v;
      saveTypePreferences();
      applyTypePreferences();
      reflowKeepingPlace();
    });

    var awake = document.getElementById("lrKeepAwake");
    if (awake) awake.addEventListener("change", function () {
      state.keepAwake = awake.checked;
      saveTypePreferences();
      applyWakeLock();
    });
  }

  function wireSegment(hostId, attr, onPick) {
    var host = document.getElementById(hostId);
    if (!host) return;
    host.addEventListener("click", function (ev) {
      var b = ev.target.closest ? ev.target.closest("[" + attr + "]") : null;
      if (!b) return;
      onPick(b.getAttribute(attr));
    });
  }

  /** Light up the option in force on each strip, and show the size in numbers. */
  function syncTypeSheet() {
    markSegment("lrTypeFamily", "data-family", state.family);
    markSegment("lrTypeLeading", "data-leading", state.leading);
    markSegment("lrTypeMeasure", "data-measure", state.measure);
    var readout = document.getElementById("lrFontReadout");
    if (readout) readout.textContent = state.readerFont + " px";
    var awake = document.getElementById("lrKeepAwake");
    if (awake) awake.checked = state.keepAwake;
  }

  function markSegment(hostId, attr, value) {
    var host = document.getElementById(hostId);
    if (!host) return;
    host.querySelectorAll("[" + attr + "]").forEach(function (b) {
      b.classList.toggle("is-active", b.getAttribute(attr) === value);
    });
  }

  function pref(key) {
    try { return window.localStorage.getItem(key); } catch (e) { return null; }
  }

  function intPref(key, dflt) {
    var n = parseInt(pref(key), 10);
    return isNaN(n) ? dflt : n;
  }

  function setPref(key, value) {
    try { window.localStorage.setItem(key, value); } catch (e) { /* private mode */ }
  }

  // ------------------------------------------------- keeping the screen awake

  var _wakeLock = null;

  function wireWakeLock() {
    document.addEventListener("visibilitychange", function () {
      // A wake lock is dropped by the browser whenever the tab is hidden, and
      // is NOT given back on its own -- it has to be asked for again.
      if (document.visibilityState === "visible") applyWakeLock();
    });
    applyWakeLock();
  }

  function applyWakeLock() {
    var note = document.getElementById("lrKeepAwakeNote");
    if (!navigator.wakeLock || !navigator.wakeLock.request) {
      if (note) note.textContent = "This browser cannot hold the screen on; use the phone's own screen timeout.";
      return;
    }
    if (note) note.textContent = "";
    if (!state.keepAwake) {
      if (_wakeLock) { try { _wakeLock.release(); } catch (e) { /* already gone */ } }
      _wakeLock = null;
      return;
    }
    if (_wakeLock || document.visibilityState !== "visible") return;
    navigator.wakeLock.request("screen").then(function (lock) {
      _wakeLock = lock;
      lock.addEventListener("release", function () { _wakeLock = null; });
    }).catch(function () {
      // Refused (low battery, policy). Reading still works; say nothing.
      _wakeLock = null;
    });
  }

  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  function debounce(fn, ms) {
    var h;
    return function () { clearTimeout(h); h = setTimeout(fn, ms); };
  }

  // ----------------------------------------------------------- contents

  /**
   * A table of contents, from whatever the document already carries: a PDF's
   * own outline (the bookmarks its author wrote), or a markdown file's
   * headings. Nothing is invented -- a plain text file has no structure to show
   * and says so.
   *
   * Built once and cached, because resolving a PDF outline's destinations to
   * page numbers is a round trip per entry through pdf.js.
   */
  function buildToc() {
    if (state.toc) return Promise.resolve(state.toc);
    if (kind === "pdf") return buildPdfToc();
    return Promise.resolve(buildHeadingToc());
  }

  function buildPdfToc() {
    if (!state.pdfDoc || !state.pdfDoc.getOutline) return Promise.resolve([]);
    return state.pdfDoc.getOutline().then(function (outline) {
      var flat = [];
      flatten(outline || [], 0, flat);
      // Resolve each entry's destination to a page number, in order. An entry
      // whose destination cannot be resolved is kept and simply does not jump.
      var chain = Promise.resolve();
      flat.forEach(function (entry) {
        chain = chain.then(function () {
          return pageOfDest(entry.dest).then(function (page) { entry.page = page; });
        });
      });
      return chain.then(function () {
        state.toc = flat.map(function (e) {
          return { label: e.title, level: e.level, page: e.page, block: null };
        });
        return state.toc;
      });
    }).catch(function () { state.toc = []; return state.toc; });
  }

  function flatten(items, level, out) {
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      out.push({ title: (it.title || "").trim() || "Untitled", dest: it.dest, level: level, page: null });
      if (it.items && it.items.length) flatten(it.items, level + 1, out);
    }
  }

  /** A pdf.js destination is either a named string or an array whose head is a page ref. */
  function pageOfDest(dest) {
    if (!dest || !state.pdfDoc) return Promise.resolve(null);
    var resolved = typeof dest === "string"
      ? state.pdfDoc.getDestination(dest)
      : Promise.resolve(dest);
    return resolved.then(function (arr) {
      if (!arr || !arr.length) return null;
      return state.pdfDoc.getPageIndex(arr[0]).then(function (i) { return i + 1; });
    }).catch(function () { return null; });
  }

  /** Markdown headings, in the order they appear. Plain text has none. */
  function buildHeadingToc() {
    var prose = currentProse();
    state.toc = [];
    if (!prose || (kind !== "markdown" && kind !== "html")) return state.toc;
    var blocks = Array.prototype.slice.call(prose.children);
    blocks.forEach(function (b, i) {
      var m = /^H([1-6])$/.exec(b.nodeName);
      if (!m) return;
      state.toc.push({
        label: (b.textContent || "").trim(),
        level: parseInt(m[1], 10) - 1,
        page: null,
        block: i
      });
    });
    return state.toc;
  }

  function openContents() {
    openSheet("lrContentsSheet");
    var body = document.getElementById("lrContentsBody");
    if (body) body.innerHTML = '<p class="lr-def-empty">Looking for contents...</p>';
    buildToc().then(renderContents);
  }

  function renderContents(toc) {
    var body = document.getElementById("lrContentsBody");
    if (!body) return;
    if (!toc || !toc.length) {
      body.innerHTML = '<p class="lr-def-empty">This document has no contents to jump to.</p>';
      return;
    }
    var html = "";
    toc.forEach(function (e, i) {
      var where = e.page ? ("Page " + e.page) : "";
      html += '<button type="button" class="lr-toc-row lr-toc-l' + Math.min(e.level, 3) +
              '" data-toc="' + i + '" data-testid="reader-toc-row">' +
              '<span class="lr-toc-label">' + escapeHtml(e.label) + "</span>" +
              (where ? '<span class="lr-toc-where">' + where + "</span>" : "") +
              "</button>";
    });
    body.innerHTML = html;
  }

  function wireContentsSheet() {
    var body = document.getElementById("lrContentsBody");
    if (body) body.addEventListener("click", function (ev) {
      var row = ev.target.closest ? ev.target.closest("[data-toc]") : null;
      if (!row) return;
      jumpToToc(parseInt(row.getAttribute("data-toc"), 10));
    });

    var over = document.getElementById("lrStartOver");
    if (over) over.addEventListener("click", startOver);
  }

  function jumpToToc(index) {
    var entry = (state.toc || [])[index];
    if (!entry) return;
    if (window.LeoReaderMarks && window.LeoReaderMarks.closeSheet) {
      window.LeoReaderMarks.closeSheet("lrContentsSheet");
    }
    var target = null;
    if (entry.block !== null && entry.block !== undefined) {
      var prose = currentProse();
      if (prose) target = prose.children[entry.block];
    } else if (entry.page) {
      // Fidelity shows page canvases; Reader view stamps each paragraph with
      // the page it came from, so the same entry lands in either view.
      target = elContent.querySelector('.lr-page[data-page="' + entry.page + '"]') ||
               elContent.querySelector('.lr-prose [data-page="' + entry.page + '"]');
    }
    if (!target) { toast("That part of the document is not on this view."); return; }
    if (target.scrollIntoView) target.scrollIntoView({ block: "start" });
  }

  /**
   * Forget the saved place and go back to the top. The reader asked to start
   * over, so the old place is not worth keeping -- and leaving it would send
   * them straight back on the next open.
   */
  function startOver() {
    var place = window.LeoReaderPlace;
    var stage = document.getElementById("lrStage");
    if (stage) stage.scrollTop = 0;
    if (window.LeoReaderMarks && window.LeoReaderMarks.closeSheet) {
      window.LeoReaderMarks.closeSheet("lrContentsSheet");
    }
    if (!place) return;
    place.forget().then(function (ok) {
      toast(ok ? "Starting from the beginning" : "That could not be reset.");
    });
  }

  // ---------------------------------------------------------------- PDF

  function startPdf() {
    setStatus("Loading PDF...");
    if (!window.pdfjsLib) { fail("PDF engine failed to load."); return; }
    pdfjsLib.GlobalWorkerOptions.workerSrc = "pdf.worker.min.js?v=1";
    // Clone the bytes for pdf.js -- it transfers/detaches the buffer it is
    // given, and we keep state.pdfData around for re-renders (zoom / mode).
    pdfjsLib.getDocument({ data: state.pdfData.slice(0) }).promise.then(function (doc) {
      state.pdfDoc = doc;
      if (elZoomReadout) elZoomReadout.textContent = Math.round(state.zoom * 100) + "%";
      markActiveMode(state.pdfMode);
      if (state.pdfMode === "reader") renderPdfReader();
      else renderPdfFidelity();
    }).catch(function (err) {
      fail("Could not open this PDF.\n" + msgOf(err));
    });
  }

  function setPdfMode(mode, btn) {
    if (mode === state.pdfMode) return;
    state.pdfMode = mode;
    activate(btn);
    syncPdfGroupVisibility();
    if (mode === "fidelity") renderPdfFidelity();
    else renderPdfReader();
  }

  function contentWidth() {
    var stage = document.getElementById("lrStage");
    var w = stage ? stage.clientWidth : window.innerWidth;
    return Math.max(240, w - 28);
  }

  function renderPdfFidelity() {
    if (!state.pdfDoc) return;
    // A reflowed view of this PDF is one tap away, so the dock offers it rather
    // than leaving the reader to discover the Reader button on their own.
    if (window.LeoReaderMarks) window.LeoReaderMarks.detach(true);
    var token = ++state.renderToken;
    setStatus("Rendering...");
    clearContent();
    var wrap = document.createElement("div");
    wrap.className = "lr-pages";
    elContent.appendChild(wrap);

    var doc = state.pdfDoc;
    var maxW = contentWidth();
    var chain = Promise.resolve();

    for (var i = 1; i <= doc.numPages; i++) {
      (function (pageNum) {
        chain = chain.then(function () {
          if (token !== state.renderToken) return null;
          return doc.getPage(pageNum).then(function (page) {
            if (token !== state.renderToken) return;
            var unscaled = page.getViewport({ scale: 1 });
            var baseScale = maxW / unscaled.width;
            var scale = baseScale * state.zoom;
            var dpr = window.devicePixelRatio || 1;
            var viewport = page.getViewport({ scale: scale });

            var canvas = document.createElement("canvas");
            canvas.className = "lr-page";
            canvas.width = Math.floor(viewport.width * dpr);
            canvas.height = Math.floor(viewport.height * dpr);
            canvas.style.width = Math.floor(viewport.width) + "px";
            canvas.style.height = Math.floor(viewport.height) + "px";
            canvas.setAttribute("data-page", String(pageNum));
            var ctx = canvas.getContext("2d");
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            wrap.appendChild(canvas);
            if (pageNum === 1) setStatus("");
            updatePageReadout();
            return page.render({ canvasContext: ctx, viewport: viewport }).promise;
          });
        });
      })(i);
    }

    chain.then(function () {
      if (token !== state.renderToken) return;
      setStatus("");
      updatePageReadout();
      afterRender(null);
    }).catch(function (err) {
      if (token === state.renderToken) fail("Rendering failed.\n" + msgOf(err));
    });

    trackVisiblePage();
  }

  function updatePageReadout() {
    if (!state.pdfDoc) return;
    var page = currentVisiblePage();
    if (elPageReadout) elPageReadout.textContent = "Page " + page + " / " + state.pdfDoc.numPages;
    // The page strip is the only thing that knows which page is on screen, so
    // it is what feeds the saved place and the progress readout.
    if (window.LeoReaderPlace) window.LeoReaderPlace.setCurrentPage(page);
  }

  function currentVisiblePage() {
    var stage = document.getElementById("lrStage");
    if (!stage) return 1;
    var pages = elContent.querySelectorAll(".lr-page");
    var mid = stage.scrollTop + stage.clientHeight / 2;
    var best = 1, bestDist = Infinity;
    for (var i = 0; i < pages.length; i++) {
      var top = pages[i].offsetTop;
      var center = top + pages[i].offsetHeight / 2;
      var d = Math.abs(center - mid);
      if (d < bestDist) { bestDist = d; best = parseInt(pages[i].getAttribute("data-page"), 10) || (i + 1); }
    }
    return best;
  }

  var _pageTrackWired = false;
  function trackVisiblePage() {
    if (_pageTrackWired) return;
    _pageTrackWired = true;
    var stage = document.getElementById("lrStage");
    if (!stage) return;
    stage.addEventListener("scroll", debounce(function () {
      if (kind === "pdf" && state.pdfMode === "fidelity") updatePageReadout();
    }, 120));
  }

  function gotoPageDelta(delta) {
    if (!state.pdfDoc) return;
    var target = clamp(currentVisiblePage() + delta, 1, state.pdfDoc.numPages);
    var el = elContent.querySelector('.lr-page[data-page="' + target + '"]');
    if (el && el.scrollIntoView) el.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function renderPdfReader() {
    if (!state.pdfDoc) return;
    var token = ++state.renderToken;
    setStatus("Extracting text...");
    clearContent();

    var prose = document.createElement("div");
    prose.className = "lr-prose";
    elContent.appendChild(prose);

    var doc = state.pdfDoc;
    var chain = Promise.resolve();
    var anyText = false;

    for (var i = 1; i <= doc.numPages; i++) {
      (function (pageNum) {
        chain = chain.then(function () {
          if (token !== state.renderToken) return null;
          return doc.getPage(pageNum).then(function (page) {
            return page.getTextContent().then(function (tc) {
              if (token !== state.renderToken) return;
              var paras = itemsToParagraphs(tc.items);
              for (var p = 0; p < paras.length; p++) {
                if (paras[p]) {
                  anyText = true;
                  var el = document.createElement("p");
                  el.textContent = paras[p];
                  // Remember which page the paragraph came from so a highlight
                  // saved here can say "Page 4" when it is filed as an excerpt.
                  el.setAttribute("data-page", String(pageNum));
                  prose.appendChild(el);
                }
              }
              if (pageNum === 1) setStatus("");
            });
          });
        });
      })(i);
    }

    chain.then(function () {
      if (token !== state.renderToken) return;
      setStatus("");
      if (!anyText) {
        // A scan: there is no text in this PDF at all, in either view, so the
        // dock has nothing to offer and says nothing rather than nudging.
        if (window.LeoReaderMarks) window.LeoReaderMarks.detach(false);
        afterRender(null);
        fail("This PDF has no extractable text (it may be a scan). Use Fidelity view instead.");
        return;
      }
      if (window.LeoReaderMarks) window.LeoReaderMarks.attach(prose, "reader");
      afterRender(prose);
    }).catch(function (err) {
      if (token === state.renderToken) fail("Text extraction failed.\n" + msgOf(err));
    });
  }

  // Group pdf.js text items into paragraphs using line Y positions.
  function itemsToParagraphs(items) {
    var lines = [];
    var cur = null;
    var lastY = null;
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      if (typeof it.str !== "string") continue;
      var y = it.transform ? it.transform[5] : 0;
      if (lastY === null || Math.abs(y - lastY) > 3) {
        cur = { y: y, text: "" };
        lines.push(cur);
        lastY = y;
      }
      cur.text += it.str;
      if (it.hasEOL) { lastY = null; }
    }

    // Compute median line height to detect paragraph gaps.
    var ys = lines.map(function (l) { return l.y; });
    var gaps = [];
    for (var g = 1; g < ys.length; g++) { gaps.push(Math.abs(ys[g - 1] - ys[g])); }
    var typical = median(gaps) || 12;

    var paras = [];
    var buf = "";
    for (var j = 0; j < lines.length; j++) {
      var txt = lines[j].text.replace(/\s+/g, " ").trim();
      var bigGap = (j > 0) && (Math.abs(lines[j - 1].y - lines[j].y) > typical * 1.6);
      if (bigGap && buf) { paras.push(buf.trim()); buf = ""; }
      if (!txt) { if (buf) { paras.push(buf.trim()); buf = ""; } continue; }
      // De-hyphenate line-wrapped words.
      if (/[A-Za-z]-$/.test(buf)) buf = buf.replace(/-$/, "") + txt;
      else buf = buf ? (buf + " " + txt) : txt;
    }
    if (buf) paras.push(buf.trim());
    return paras;
  }

  function median(arr) {
    if (!arr.length) return 0;
    var s = arr.slice().sort(function (a, b) { return a - b; });
    var m = Math.floor(s.length / 2);
    return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
  }

  // ---------------------------------------------------------------- text / md

  function startText(isMarkdown) {
    setStatus("");
    clearContent();
    var prose = document.createElement("div");
    prose.className = "lr-prose" + (isMarkdown ? "" : " lr-plain");
    if (isMarkdown) prose.innerHTML = miniMarkdown(state.textData || "");
    else prose.textContent = state.textData || "";
    elContent.appendChild(prose);
    if (window.LeoReaderMarks) {
      window.LeoReaderMarks.attach(prose, isMarkdown ? "markdown" : "text");
    }
    afterRender(prose);
  }

  // ---------------------------------------------------------------- html

  // An HTML document (a debate transcript, a saved article -- Story 5646) is
  // read as prose: its own scripts, styles and frames are dropped, and its
  // layout wrappers are flattened so the text takes the reader's typography and
  // every block (paragraph, heading, list, table) is a direct child of the
  // prose, the shape the marks and the contents sheet expect. The same file
  // always flattens the same way, so saved highlights land where they were.
  //
  // A page that builds itself with script (every Think Tank transcript and
  // scorecard draws its text from a JSON block) is first run in a hidden
  // sandboxed frame -- scripts allowed, but an opaque origin, so it cannot
  // reach Leonardo, its storage or the session -- and the body it ends up with
  // is what gets read.
  function startHtml() {
    var src = state.textData || "";
    if (!/<script\b/i.test(src)) { showHtml(src); return; }
    setStatus("Loading...");
    var probe = "<script>(function(){function send(){try{parent.postMessage({lrHtml:document.body?document.body.innerHTML:''},'*');}catch(e){}}" +
                "if(document.readyState==='complete')setTimeout(send,300);else window.addEventListener('load',function(){setTimeout(send,300);});})();<\/script>";
    var frame = document.createElement("iframe");
    frame.setAttribute("sandbox", "allow-scripts");
    frame.setAttribute("aria-hidden", "true");
    frame.style.cssText = "position:absolute;left:-10000px;top:0;width:800px;height:600px;border:0;visibility:hidden";
    var done = false;
    function finish(html) {
      if (done) return;
      done = true;
      window.removeEventListener("message", onMsg);
      if (frame.parentNode) frame.parentNode.removeChild(frame);
      showHtml(html);
    }
    function onMsg(e) {
      if (e.source !== frame.contentWindow || !e.data || typeof e.data.lrHtml !== "string") return;
      finish(e.data.lrHtml);
    }
    window.addEventListener("message", onMsg);
    // A page whose script fails or never settles is still read as written.
    setTimeout(function () { finish(src); }, 8000);
    frame.srcdoc = /<\/body>/i.test(src) ? src.replace(/<\/body>/i, probe + "</body>") : src + probe;
    document.body.appendChild(frame);
  }

  function showHtml(html) {
    setStatus("");
    clearContent();
    var prose = document.createElement("div");
    prose.className = "lr-prose";
    var doc = new DOMParser().parseFromString(html || "", "text/html");
    var body = doc.body || doc.documentElement;
    cleanHtml(body);
    while (body.firstChild) prose.appendChild(document.adoptNode(body.firstChild));
    elContent.appendChild(prose);
    if (window.LeoReaderMarks) window.LeoReaderMarks.attach(prose, "html");
    afterRender(prose);
  }

  var HTML_DROP = { SCRIPT: 1, STYLE: 1, LINK: 1, META: 1, TITLE: 1, IFRAME: 1, FRAME: 1,
                    OBJECT: 1, EMBED: 1, FORM: 1, INPUT: 1, BUTTON: 1, SELECT: 1,
                    TEXTAREA: 1, NOSCRIPT: 1, TEMPLATE: 1, SVG: 1, CANVAS: 1, VIDEO: 1, AUDIO: 1,
                    LABEL: 1, NAV: 1 };
  // A page's own controls (the transcript's podcast bar and voice panel, its
  // jump-to-round strip) are not part of what is being read.
  var HTML_CHROME_CLASS = /(^|\s)(podbar|voice-panel|unsupported|toc|toolbar|player)(\s|$)/i;
  var HTML_WRAPPERS = { DIV: 1, SECTION: 1, ARTICLE: 1, MAIN: 1, HEADER: 1, FOOTER: 1,
                        ASIDE: 1, CENTER: 1, DETAILS: 1, FIGURE: 1 };
  var HTML_BLOCKS = /^(P|H[1-6]|UL|OL|LI|TABLE|BLOCKQUOTE|PRE|HR|DIV|SECTION|ARTICLE|MAIN|HEADER|FOOTER|ASIDE|CENTER|DETAILS|FIGURE|DL)$/;

  function cleanHtml(root) {
    Array.prototype.slice.call(root.querySelectorAll("*")).forEach(function (el) {
      if (HTML_DROP[el.nodeName.toUpperCase()] || HTML_CHROME_CLASS.test(el.getAttribute("class") || "") ||
          el.getAttribute("role") === "toolbar") {
        if (el.parentNode) el.parentNode.removeChild(el);
        return;
      }
      Array.prototype.slice.call(el.attributes).forEach(function (a) {
        var n = a.name.toLowerCase();
        var keep = (n === "href" && !/^\s*(javascript|data|vbscript):/i.test(a.value)) ||
                   n === "src" || n === "alt" || n === "colspan" || n === "rowspan";
        if (!keep) el.removeAttribute(a.name);
      });
      if (el.nodeName === "A") { el.setAttribute("target", "_blank"); el.setAttribute("rel", "noopener"); }
      if (el.nodeName === "IMG" && /^\s*(javascript|vbscript):/i.test(el.getAttribute("src") || "")) el.removeAttribute("src");
    });
    flattenWrappers(root);
  }

  // A wrapper holding blocks is unwrapped; a wrapper holding only inline text
  // becomes a paragraph, so two lines in two divs never run together.
  function flattenWrappers(root) {
    var again = true;
    while (again) {
      again = false;
      Array.prototype.slice.call(root.children).forEach(function (el) {
        if (!HTML_WRAPPERS[el.nodeName.toUpperCase()]) return;
        var hasBlock = Array.prototype.some.call(el.children, function (c) {
          return HTML_BLOCKS.test(c.nodeName.toUpperCase());
        });
        if (hasBlock) {
          // Loose inline runs between the blocks get a paragraph of their own.
          var run = null;
          Array.prototype.slice.call(el.childNodes).forEach(function (n) {
            var isBlock = n.nodeType === 1 && HTML_BLOCKS.test(n.nodeName.toUpperCase());
            if (isBlock) { run = null; el.parentNode.insertBefore(n, el); return; }
            if (n.nodeType === 3 && !n.nodeValue.trim()) { el.removeChild(n); return; }
            if (!run) { run = el.ownerDocument.createElement("p"); el.parentNode.insertBefore(run, el); }
            run.appendChild(n);
          });
          el.parentNode.removeChild(el);
          again = true;
        } else if ((el.textContent || "").trim() || el.querySelector("img")) {
          var p = el.ownerDocument.createElement("p");
          while (el.firstChild) p.appendChild(el.firstChild);
          el.parentNode.replaceChild(p, el);
        } else {
          el.parentNode.removeChild(el);
        }
      });
    }
    // Loose text sitting straight in the body becomes paragraphs too.
    var run = null;
    Array.prototype.slice.call(root.childNodes).forEach(function (n) {
      var isBlock = n.nodeType === 1 && (HTML_BLOCKS.test(n.nodeName.toUpperCase()) || n.nodeName === "P");
      if (isBlock) { run = null; return; }
      if (n.nodeType === 3 && !n.nodeValue.trim()) { root.removeChild(n); return; }
      if (n.nodeType !== 1 && n.nodeType !== 3) { root.removeChild(n); return; }
      if (!run) { run = root.ownerDocument.createElement("p"); root.insertBefore(run, n); }
      run.appendChild(n);
    });
  }

  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function inlineMd(s) {
    // Order matters: escape first (done by caller), then apply inline rules.
    s = s.replace(/`([^`]+)`/g, function (_, c) { return "<code>" + c + "</code>"; });
    s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    s = s.replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
    s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, function (_, txt, url) {
      var safe = url.replace(/"/g, "%22");
      return '<a href="' + safe + '" target="_blank" rel="noopener">' + txt + "</a>";
    });
    return s;
  }

  // Compact, dependency-free markdown -> HTML. Handles headings, lists,
  // code fences, blockquotes, hr, and paragraphs. Good enough for a reader.
  function miniMarkdown(src) {
    var lines = src.replace(/\r\n?/g, "\n").split("\n");
    var out = [];
    var i = 0;
    while (i < lines.length) {
      var line = lines[i];

      // Fenced code block
      if (/^```/.test(line)) {
        var code = [];
        i++;
        while (i < lines.length && !/^```/.test(lines[i])) { code.push(lines[i]); i++; }
        i++; // skip closing fence
        out.push("<pre><code>" + escapeHtml(code.join("\n")) + "</code></pre>");
        continue;
      }

      // Horizontal rule
      if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(line)) { out.push("<hr>"); i++; continue; }

      // Heading
      var h = /^(#{1,6})\s+(.*)$/.exec(line);
      if (h) {
        var lvl = h[1].length;
        out.push("<h" + lvl + ">" + inlineMd(escapeHtml(h[2].trim())) + "</h" + lvl + ">");
        i++;
        continue;
      }

      // Blockquote
      if (/^\s*>\s?/.test(line)) {
        var quote = [];
        while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
          quote.push(lines[i].replace(/^\s*>\s?/, ""));
          i++;
        }
        out.push("<blockquote>" + inlineMd(escapeHtml(quote.join(" "))) + "</blockquote>");
        continue;
      }

      // Unordered / ordered list
      if (/^\s*([-*+]|\d+\.)\s+/.test(line)) {
        var ordered = /^\s*\d+\.\s+/.test(line);
        var items = [];
        while (i < lines.length && /^\s*([-*+]|\d+\.)\s+/.test(lines[i])) {
          items.push(lines[i].replace(/^\s*([-*+]|\d+\.)\s+/, ""));
          i++;
        }
        var tag = ordered ? "ol" : "ul";
        out.push("<" + tag + ">" + items.map(function (t) {
          return "<li>" + inlineMd(escapeHtml(t)) + "</li>";
        }).join("") + "</" + tag + ">");
        continue;
      }

      // Blank line
      if (/^\s*$/.test(line)) { i++; continue; }

      // Paragraph (gather until blank)
      var para = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) &&
             !/^(#{1,6}\s|```|\s*>\s?|\s*([-*+]|\d+\.)\s+)/.test(lines[i])) {
        para.push(lines[i]);
        i++;
      }
      out.push("<p>" + inlineMd(escapeHtml(para.join(" "))) + "</p>");
    }
    return out.join("\n");
  }

  // ---------------------------------------------------------------- image

  var _img = null;
  function startImage() {
    setStatus("");
    clearContent();
    // An image has no text at all -- there is nothing to offer, so the dock hides.
    if (window.LeoReaderMarks) window.LeoReaderMarks.detach(false);
    var wrap = document.createElement("div");
    wrap.className = "lr-image-wrap";
    _img = document.createElement("img");
    _img.alt = TITLE || "image";
    _img.onerror = function () { fail("Could not load this image."); };
    _img.src = state.imageUrl;
    wrap.appendChild(_img);
    elContent.appendChild(wrap);
    if (elZoomReadout) elZoomReadout.textContent = "100%";
    afterRender(null);
  }

  function applyImageZoom() {
    if (!_img) return;
    _img.style.width = Math.round(state.zoom * 100) + "%";
    _img.style.maxWidth = state.zoom <= 1 ? "100%" : "none";
  }
})();
