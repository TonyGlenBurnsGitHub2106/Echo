/* Leonardo Reader -- the marking layer (Story 2307). ASCII-only.

   THERE ARE TWO MARKING LAYERS. This one serves the STANDALONE reader page
   (/reader/reader.html, reached from the Reading Nook via Open in Reader). The
   Writing Filing Cabinet has its OWN, entirely separate one at
   static/writing/writing-marks.js, driving the reader overlay inside that page.
   They share only the API (/api/reader/highlights) and the colour vocabulary.

   Before changing "the reader", work out WHICH reader the request means -- both
   are called that in conversation. Story 2395 shipped its colour palette into
   this file when the ask was about the Writing Filing Cabinet's reader; it
   passed every test here while the surface Tony was actually using never
   changed at all. A green test suite proves nothing if it is aimed at the wrong
   surface.

   Everything the reader can do WITH a stretch of text lives here, so reader.js
   stays about rendering documents:

     - select text  -> the docked action bar comes alive
     - Highlight    -> the mark is saved and comes back next time
     - Save Excerpt -> the text is filed on a Research project's Excerpts tab
     - Define       -> the word is looked up
     - Research     -> the term is opened in a new tab

   Kindle parity (Story 2422) added three things to the same surface, because
   they are all the same record seen from different angles:

     - Bookmark  -> a mark with no passage, kept at the spot being read. Stored
                    as a ReaderHighlight whose ColorCode is the reserved code
                    "bookmark" -- the shape the Writing Filing Cabinet reader
                    already used and the server already accepts.
     - Notes     -> any number of notes on any mark, bookmark or highlight
                    (/api/reader/notes). The server side shipped with Story 2411
                    and had no button here until now.
     - Notebook  -> every bookmark, highlight and note in the document, out to
                    the Reading Nook or the Leonardo print menu.

   TWO DESIGN DECISIONS worth knowing, both learned the hard way (the first
   version of this file shipped and never once worked in a real browser):

   1. THE ACTION BAR IS DOCKED, NOT FLOATING. The original toolbar floated
      immediately above the selection -- which is exactly where iOS and Android
      draw their own Copy / Look Up / Share callout, painted by the OS on top of
      the page. On a phone it was unreachable. A bar pinned to the bottom edge
      cannot collide with it, and being permanently on screen it also tells the
      reader the actions exist before anything is selected.

   2. THE SELECTION IS LATCHED. Tapping a button outside the selection makes the
      browser collapse it, so an action that reads the live selection at click
      time gets nothing. The anchor is captured on selectionchange and HELD until
      it is used, replaced by a new selection, or explicitly cleared.

   The marking layer only works in the reflowed views (PDF Reader view, markdown,
   plain text) -- the Fidelity page images have no selectable text. In Fidelity
   the bar says so and offers the one tap that fixes it.

   Anchoring: a mark is stored as (blockIndex, startOffset, endOffset) against
   the reflowed paragraphs, plus the text itself. The reflow is deterministic for
   a given document, so the anchor survives a font change; the stored text is the
   fallback used to re-find a mark if a render ever shifts, and is what lets a
   selection spanning several paragraphs be painted across all of them.
*/
(function () {
  "use strict";

  var MAX_POPUP_WORDS_FOR_TITLE = 6;
  var QUOTE_PREVIEW_CHARS = 220;

  /**
   * How long the selection must sit still before the live panel comes back
   * (Story 5589). Dragging a handle fires selectionchange continuously, so a
   * quiet gap this long is the nearest thing a phone offers to "mouse up" --
   * native handle drags deliver no touchend to the page.
   */
  var SELECTION_SETTLE_MS = 650;

  /**
   * A selectionchange that lands this soon after a finger or key came up still
   * belongs to that release (2026-10-06). Without the grace a double-click,
   * whose word selection is reported a moment after the button comes up,
   * would look like a selection nobody let go of.
   */
  var RELEASE_GRACE_MS = 300;

  /**
   * The highlight palette (Story 2395). The order is the order the colour strip
   * shows, and the codes are what gets stored in the highlight's ColorCode and
   * validated by ReaderHighlightService.COLORS -- change one, change all three
   * (here, the service, and the hl-* rules in reader.css).
   */
  var COLORS = [
    { code: "yellow", label: "Yellow" },
    { code: "blue",   label: "Blue" },
    { code: "pink",   label: "Pink" },
    { code: "orange", label: "Orange" },
    { code: "green",  label: "Green" },
    { code: "purple", label: "Purple" }
  ];
  var DEFAULT_COLOR = "yellow";
  var LAST_COLOR_KEY = "leoReaderHighlightColor";

  /**
   * A bookmark is a mark with no passage. It rides in the SAME column as a
   * colour because that is what the server already stores and validates
   * (ReaderHighlightService.RESERVED_CODES) -- it is deliberately NOT in
   * COLORS, so it can never turn up on a colour strip.
   */
  var BOOKMARK = "bookmark";

  /** How much of the page a bookmark keeps, so its row in the list means something. */
  var BOOKMARK_SNIPPET_CHARS = 90;

  var state = {
    docKey: "",
    focusMarkId: 0,     // ?mark= -- the highlight to scroll to once, after the first paint
    docTitle: "",
    docAuthor: "",      // who wrote the document, when the caller knows
    docWork: "",        // the work the document IS, for the Quotes shelf
    viewMode: "reader",
    prose: null,
    marks: [],          // saved highlights for this document
    loaded: false,      // marks fetched at least once
    pending: null,      // the LATCHED selection the action bar is acting on
    projects: null,     // research project pick-list, fetched on first use
    preferProjectId: 0, // project to preselect (set when the reader was opened
                        // from a Research project's Files tab)
    onWantReaderView: null, // reader.js hands us the way to leave Fidelity
    canReflow: false,   // this document HAS a reflowed view to offer
    color: DEFAULT_COLOR, // colour the last highlight was made in, per device
    openMarkId: 0,      // the saved mark the Highlight sheet is acting on
    notes: [],          // every note on every mark in this document
    editingNoteId: 0,   // the note the editor is rewriting, 0 for a new one
    selecting: false,   // a selection is being dragged right now -- panel stays down
    pointerDown: false, // a finger or mouse button is down on the text
    keyDown: false,     // a key is held (Shift + arrows selecting by keyboard)
    sawRelease: false,  // a finger, mouse button or key came UP since this
                        // selection started -- the live panel needs it
    releasedAt: 0,      // when that last happened (ms), for RELEASE_GRACE_MS
    lastGood: null,     // last selection whose both ends were inside the text
    busy: false
  };

  var el = {};

  // ------------------------------------------------------------------ public

  var api = {};
  window.LeoReaderMarks = api;

  /** Called once by reader.js with the document's identity. */
  api.init = function (opts) {
    opts = opts || {};
    state.docKey = opts.docKey || "";
    state.docTitle = opts.docTitle || "";
    state.docAuthor = opts.docAuthor || "";
    state.docWork = opts.docWork || state.docTitle;
    state.preferProjectId = parseInt(opts.projectId || 0, 10) || 0;
    state.focusMarkId = parseInt(opts.focusMarkId || 0, 10) || 0;
    state.onWantReaderView = typeof opts.onWantReaderView === "function" ? opts.onWantReaderView : null;
    state.color = rememberedColor();
    cacheElements();
    wireOnce();
    if (state.preferProjectId) showDestination();
  };

  /** A reflowed render is on screen -- start marking against it. */
  api.attach = function (proseEl, viewMode) {
    state.prose = proseEl || null;
    state.viewMode = viewMode || "reader";
    state.canReflow = true;
    clearPending();
    if (!state.prose || !state.docKey) { renderDock(); return; }
    wireProseTaps(state.prose);
    renderDock();
    // Notes come down AFTER the marks are painted -- there is nothing to hang
    // them on before that -- so the underline that says "this one has a note"
    // is applied in a second pass rather than being missed until the next edit.
    loadMarks().then(applyAllMarks).then(loadNotes).then(applyNotedClasses).then(focusRequestedMark);
  };

  /**
   * The Highlights shelf (Story 5613) opens a document at one highlight with
   * ?mark=<id>. Scroll there once, after the marks are painted; a later render
   * (a text-size step, a mode switch) leaves the reader where they are.
   */
  function focusRequestedMark() {
    var id = state.focusMarkId;
    if (!id || !state.prose) return;
    state.focusMarkId = 0;
    if (!markById(id)) return;
    window.setTimeout(function () { jumpToMark(id); }, 120);
  }

  /**
   * Tapping a painted mark opens it for recolouring. Bound to the prose element
   * itself (a fresh one on every render) and flagged so a second attach against
   * the same element cannot double-bind.
   */
  function wireProseTaps(prose) {
    if (!prose || prose.getAttribute("data-marks-wired") === "1") return;
    prose.setAttribute("data-marks-wired", "1");

    // Touching the text again starts a NEW selection, so the old anchor must go
    // (2026-09-06). The latch exists so a tap on a dock button cannot lose the
    // selection -- but a tap in the TEXT is the reader saying "not that, this".
    // Held past that point, the latch is why re-dragging a selection still
    // highlighted the first stretch picked rather than the current one. Any
    // real selection re-latches on the next selectionchange a moment later.
    prose.addEventListener("pointerdown", function () {
      state.pointerDown = true;
      if (state.pending && !anySheetOpen()) clearPending();
    });

    prose.addEventListener("click", function (ev) {
      var mk = ev.target.closest ? ev.target.closest("mark.lr-mark") : null;
      if (!mk) return;
      var id = parseInt(mk.getAttribute("data-mark-id"), 10);
      if (!id) return;
      // Tapping ANYWHERE in a highlight selects the whole passage (Story 2429).
      // Before this, taking a copy of something already highlighted meant
      // dragging out the exact words again by hand -- and a drag that misses by
      // one character copies the wrong thing. Selecting it here also latches it
      // for the dock, so Send to Quotes acts on the highlight straight away.
      selectMarkText(id);
      openMarkSheet(id);
    });
  }

  /**
   * Put the browser's selection around every piece of one saved mark. A mark
   * can be painted as several <mark> fragments (a passage that runs across
   * paragraphs), so the range runs from the first fragment to the last.
   * Returns the selected text, or "" when the mark is not on screen.
   */
  function selectMarkText(id) {
    if (!state.prose) return "";
    var parts = state.prose.querySelectorAll('mark[data-mark-id="' + id + '"]');
    if (!parts.length) return "";
    try {
      var range = document.createRange();
      range.setStartBefore(parts[0]);
      range.setEndAfter(parts[parts.length - 1]);
      var sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      return range.toString();
    } catch (e) {
      return "";
    }
  }

  /**
   * Copy the open mark's passage. The clipboard API is the reliable way on a
   * phone; where it is not available the text is left selected instead, which
   * is what the reader would otherwise have had to do by hand.
   */
  function copyOpenMark() {
    var found = markById(state.openMarkId);
    var text = found ? (found.selectedText || "") : "";
    if (!text) { toast("There is nothing to copy."); return; }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () {
        toast("Copied");
      }).catch(function () {
        selectMarkText(state.openMarkId);
        toast("The passage is selected and ready to copy.");
      });
      return;
    }
    selectMarkText(state.openMarkId);
    toast("The passage is selected and ready to copy.");
  }

  /** File the open mark's passage on the Quotes shelf. */
  function quoteOpenMark() {
    var found = markById(state.openMarkId);
    var text = found ? (found.selectedText || "") : "";
    if (!text) { toast("There is nothing to send."); return; }
    openQuoteSheet(text);
  }

  /** Hand a passage to the shared Send to Quotes sheet, author and work filled in. */
  function openQuoteSheet(text) {
    if (!window.LeoQuoteSheet) { toast("Quotes are not available here."); return; }
    window.LeoQuoteSheet.open({
      text: text,
      author: state.docAuthor,
      work: state.docWork || state.docTitle
    });
  }

  /**
   * No reflowed render on screen. `canReflow` says whether one is available a
   * tap away (Fidelity view of a text PDF) or not at all (an image, a scan).
   */
  api.detach = function (canReflow) {
    state.prose = null;
    state.canReflow = !!canReflow;
    clearPending();
    renderDock();
  };

  /** True when a reflowed render is available to mark. */
  api.isAttached = function () { return !!state.prose; };

  /** Toolbar button: show everything marked in this document. */
  api.openList = function () {
    loadMarks().then(loadNotes).then(function () {
      renderMarkList();
      openSheet("lrMarksSheet");
    });
  };

  /**
   * Toolbar button: keep the spot being read. A bookmark carries no selection,
   * so it anchors to the paragraph on screen rather than to anything the reader
   * picked out -- which is exactly what the reading position already tracks.
   */
  api.addBookmark = function () {
    if (state.busy) return;
    if (!state.prose) { toast("Switch to Reader view to bookmark a spot."); return; }
    var at = window.LeoReaderPlace ? window.LeoReaderPlace.currentBlock() : null;
    if (!at) { toast("There is nothing on screen to bookmark."); return; }

    var blocks = blockList();
    var block = blocks[at.blockIndex];
    var text = block ? (block.textContent || "") : "";
    var snippet = normalize(text.substr(at.charOffset, BOOKMARK_SNIPPET_CHARS));
    if (!snippet) snippet = normalize(text.slice(0, BOOKMARK_SNIPPET_CHARS));
    if (!snippet) snippet = state.docTitle || "Bookmark";

    state.busy = true;
    window.LWAuth.apiPost("/api/reader/highlights", {
      docKey: state.docKey,
      docTitle: state.docTitle,
      viewMode: state.viewMode,
      pageNumber: block ? pageOf(block) : null,
      blockIndex: at.blockIndex,
      startOffset: at.charOffset,
      endOffset: at.charOffset,
      selectedText: snippet,
      colorCode: BOOKMARK
    }).then(function (res) {
      state.busy = false;
      if (!res || !res.ok) { toast(errorTextOf(res, "That bookmark could not be saved.")); return; }
      state.marks.push(res.data);
      renderMarkList();
      var n = state.marks.filter(isBookmark).length;
      toast("Bookmark " + n + " saved -- open the Notebook to come back to it.");
    }).catch(function () { state.busy = false; toast("That bookmark could not be saved."); });
  };

  /** Opening and closing sheets is owned here, because the scrim is. */
  api.openSheet = function (id) { openSheet(id); };
  api.closeSheet = function (id) { closeSheet(id); };

  /** The one toast on the page; reader.js borrows it rather than growing its own. */
  api.toast = function (msg) { toast(msg); };

  // ------------------------------------------------------------------ wiring

  function cacheElements() {
    el.dock       = document.getElementById("lrDock");
    el.dockIdle   = document.getElementById("lrDockIdle");
    el.dockHint   = document.getElementById("lrDockHint");
    el.dockReady  = document.getElementById("lrDockReady");
    el.dockNudge  = document.getElementById("lrDockNudge");
    el.dockLive   = document.getElementById("lrDockLive");
    el.dockQuote  = document.getElementById("lrDockQuote");
    el.dockDest   = document.getElementById("lrDockDest");
    el.toReader   = document.getElementById("lrDockToReader");
    el.scrim      = document.getElementById("lrScrim");
    el.toast      = document.getElementById("lrToast");
    el.exText     = document.getElementById("lrExcerptText");
    el.exProject  = document.getElementById("lrExProject");
    el.exSource   = document.getElementById("lrExSource");
    el.exTheme    = document.getElementById("lrExTheme");
    el.exStance   = document.getElementById("lrExStance");
    el.exComment  = document.getElementById("lrExCommentary");
    el.exKeyPoint = document.getElementById("lrExKeyPoint");
    el.exMsg      = document.getElementById("lrExcerptMsg");
    el.exSave     = document.getElementById("lrExcerptSave");
    el.defTitle   = document.getElementById("lrDefineTitle");
    el.defBody    = document.getElementById("lrDefineBody");
    el.resTitle   = document.getElementById("lrResearchTitle");
    el.marksBody  = document.getElementById("lrMarksBody");
    el.dockColors = document.getElementById("lrDockColors");
    el.markText   = document.getElementById("lrMarkText");
    el.markColors = document.getElementById("lrMarkColors");
    el.markColorBlock = document.getElementById("lrMarkColorBlock");
    el.markRemove = document.getElementById("lrMarkRemove");
    el.markCopy   = document.getElementById("lrMarkCopy");
    el.markQuote  = document.getElementById("lrMarkQuote");
    el.markNotes  = document.getElementById("lrMarkNotes");
    el.noteEditor = document.getElementById("lrNoteEditor");
    el.noteInput  = document.getElementById("lrNoteInput");
    el.noteAdd    = document.getElementById("lrNoteAdd");
    el.noteSave   = document.getElementById("lrNoteSave");
    el.noteCancel = document.getElementById("lrNoteCancel");
    el.nbSave     = document.getElementById("lrNotebookSave");
    el.nbPrint    = document.getElementById("lrNotebookPrint");
    el.printable  = document.getElementById("lrNotebookPrintable");
  }

  var _wired = false;
  function wireOnce() {
    if (_wired) return;
    _wired = true;

    // Two listeners on purpose (Story 5589). The immediate one drops the live
    // panel the instant a selection starts moving, so it is never sitting over
    // the text a handle is being dragged across; the settled one latches and
    // brings the panel back once the reader lets go.
    document.addEventListener("selectionchange", onSelectionMoving);
    document.addEventListener("selectionchange", debounce(onSelectionChange, SELECTION_SETTLE_MS));
    // The live panel needs TWO things (Tony, 2026-10-06): text is selected AND
    // a finger, mouse button or key came UP. A release is pointerup, touchend
    // or keyup. A CANCEL is not a release: Android cancels the touch the moment
    // a long-press starts selecting, with the finger still down -- treating that
    // as "let go" is what put the panel up on the press itself.
    ["pointerup", "touchend"].forEach(function (type) {
      document.addEventListener(type, function () { onRelease(); });
    });
    ["pointercancel", "touchcancel"].forEach(function (type) {
      document.addEventListener(type, function () { state.pointerDown = false; });
    });
    document.addEventListener("keydown", function (ev) {
      if (!isTypingTarget(ev.target)) state.keyDown = true;
    });
    document.addEventListener("keyup", function (ev) {
      if (isTypingTarget(ev.target)) return;
      state.keyDown = false;
      onRelease();
    });

    if (el.dock) {
      // pointerdown covers mouse and touch alike. Preventing the default stops
      // the browser collapsing the selection before the click lands -- belt and
      // braces alongside the latch, which is what actually guarantees it.
      el.dock.addEventListener("pointerdown", function (ev) {
        if (ev.target.closest && ev.target.closest("button")) ev.preventDefault();
      });
      el.dock.addEventListener("click", function (ev) {
        var btn = ev.target.closest ? ev.target.closest("[data-sel-act]") : null;
        if (btn) { runSelectionAction(btn.getAttribute("data-sel-act")); }
      });
    }

    // The phone never reports letting go of a native selection handle, so a
    // selection that settles without a release waits for this tap -- the tap
    // IS the release.
    if (el.dockReady) el.dockReady.addEventListener("click", function () {
      state.sawRelease = true;
      state.releasedAt = Date.now();
      onSelectionChange();
      renderDock();
    });

    if (el.toReader) el.toReader.addEventListener("click", function () {
      if (state.onWantReaderView) state.onWantReaderView();
    });

    document.querySelectorAll("[data-sheet-close]").forEach(function (b) {
      b.addEventListener("click", function () { closeSheet(b.getAttribute("data-sheet-close")); });
    });
    if (el.scrim) el.scrim.addEventListener("click", closeAllSheets);

    if (el.exProject) el.exProject.addEventListener("change", function () {
      loadProjectDetail(parseInt(el.exProject.value, 10) || 0);
    });
    if (el.exSave) el.exSave.addEventListener("click", saveExcerpt);

    document.querySelectorAll("[data-research-site]").forEach(function (b) {
      b.addEventListener("click", function () {
        openResearchSite(b.getAttribute("data-research-site"));
      });
    });

    renderSwatches(el.dockColors, "reader-sel-swatch", function (code) {
      saveHighlight(code);
    });
    renderSwatches(el.markColors, "reader-mark-swatch", function (code) {
      recolorMark(state.openMarkId, code);
    });

    if (el.markCopy)  el.markCopy.addEventListener("click", copyOpenMark);
    if (el.markQuote) el.markQuote.addEventListener("click", quoteOpenMark);

    if (el.markRemove) el.markRemove.addEventListener("click", function () {
      var id = state.openMarkId;
      closeSheet("lrMarkSheet");
      removeMark(id);
    });

    if (el.noteAdd)    el.noteAdd.addEventListener("click", function () { openNoteEditor(0); });
    if (el.noteCancel) el.noteCancel.addEventListener("click", closeNoteEditor);
    if (el.noteSave)   el.noteSave.addEventListener("click", saveNote);

    if (el.markNotes) el.markNotes.addEventListener("click", function (ev) {
      var edit = ev.target.closest ? ev.target.closest("[data-note-edit]") : null;
      if (edit) { openNoteEditor(parseInt(edit.getAttribute("data-note-edit"), 10)); return; }
      var drop = ev.target.closest ? ev.target.closest("[data-note-drop]") : null;
      if (drop) removeNote(parseInt(drop.getAttribute("data-note-drop"), 10));
    });

    if (el.nbSave)  el.nbSave.addEventListener("click", sendNotebookToNook);
    if (el.nbPrint) el.nbPrint.addEventListener("click", printNotebook);

    if (el.marksBody) el.marksBody.addEventListener("click", function (ev) {
      var jump = ev.target.closest ? ev.target.closest("[data-mark-jump]") : null;
      if (jump) { jumpToMark(parseInt(jump.getAttribute("data-mark-jump"), 10)); return; }
      var drop = ev.target.closest ? ev.target.closest("[data-mark-drop]") : null;
      if (drop) { removeMark(parseInt(drop.getAttribute("data-mark-drop"), 10)); return; }
      var tint = ev.target.closest ? ev.target.closest("[data-mark-color]") : null;
      if (tint) openMarkSheet(parseInt(tint.getAttribute("data-mark-color"), 10));
    });
  }

  /**
   * Fill one colour strip. The palette lives in JS rather than the markup so
   * the dock and the Highlight sheet can never drift apart, and each swatch
   * carries the colour name for a reader who cannot tell them apart by sight.
   */
  function renderSwatches(host, testIdPrefix, onPick) {
    if (!host) return;
    host.innerHTML = "";
    COLORS.forEach(function (c) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "lr-swatch hl-" + c.code;
      b.title = c.label;
      b.setAttribute("aria-label", c.label);
      b.setAttribute("data-color", c.code);
      b.setAttribute("data-testid", testIdPrefix + "-" + c.code);
      b.addEventListener("click", function () { onPick(c.code); });
      host.appendChild(b);
    });
    markCurrentSwatch(host, state.color);
  }

  /** Ring the swatch that is currently in force on one strip. */
  function markCurrentSwatch(host, code) {
    if (!host) return;
    host.querySelectorAll(".lr-swatch").forEach(function (b) {
      b.classList.toggle("is-on", b.getAttribute("data-color") === code);
    });
  }

  /** The colour this device highlighted in last time, defaulted and validated. */
  function rememberedColor() {
    var saved = null;
    try { saved = window.localStorage.getItem(LAST_COLOR_KEY); } catch (e) { saved = null; }
    return isKnownColor(saved) ? saved : DEFAULT_COLOR;
  }

  function rememberColor(code) {
    state.color = isKnownColor(code) ? code : DEFAULT_COLOR;
    try { window.localStorage.setItem(LAST_COLOR_KEY, state.color); } catch (e) { /* private mode */ }
    markCurrentSwatch(el.dockColors, state.color);
  }

  function isKnownColor(code) {
    for (var i = 0; i < COLORS.length; i++) if (COLORS[i].code === code) return true;
    return false;
  }

  /** A mark with no passage -- a kept spot rather than a marked one. */
  function isBookmark(m) { return !!m && String(m.colorCode || "").toLowerCase() === BOOKMARK; }

  /** What this mark is called to the reader. */
  function markNoun(m) { return isBookmark(m) ? "Bookmark" : "Highlight"; }

  /** The class that paints a mark; an unknown stored colour falls back. */
  function colorClassOf(m) {
    var code = m && m.colorCode ? String(m.colorCode).toLowerCase() : "";
    return "hl-" + (isKnownColor(code) ? code : DEFAULT_COLOR);
  }

  // -------------------------------------------------------------- selection

  /**
   * Latch a new selection. A COLLAPSED selection is deliberately ignored rather
   * than treated as "nothing selected": the browser collapses the selection the
   * moment the reader taps one of our buttons, and dropping the anchor there is
   * precisely what made the first version unusable.
   */
  function onSelectionChange() {
    if (!state.prose) return;
    if (anySheetOpen()) return;
    if (state.pointerDown || state.keyDown) return;   // the release will call again
    if (state.selecting) { state.selecting = false; renderDock(); }

    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) return;

    var text = sel.toString().replace(/\s+/g, " ").trim();
    if (!text) return;

    var range = sel.getRangeAt(0);
    if (!state.prose.contains(range.startContainer)) return;

    var anchor = anchorFor(range);
    if (!anchor) return;

    anchor.text = text;
    state.pending = anchor;
    renderDock();
  }

  /**
   * Runs on EVERY selectionchange, undebounced (Story 5589). Two jobs:
   *
   * 1. Take the live panel down while the selection moves. The panel overlays
   *    the top of the text but sits BEFORE the text in the page, so a handle
   *    dragged up under it put the selection's end above the whole document --
   *    everything from the start point to the top of the file got selected.
   * 2. If an end of the selection still lands outside the text, put it back
   *    where it last was. The selection can never leave the prose.
   */
  function onSelectionMoving() {
    if (!state.prose || anySheetOpen()) return;
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || sel.rangeCount === 0) return;
    var p = state.prose;
    var anchorIn = p.contains(sel.anchorNode);
    var focusIn  = p.contains(sel.focusNode);
    if (!anchorIn && !focusIn) return;      // not a selection in the text

    if (anchorIn && focusIn) {
      state.lastGood = { an: sel.anchorNode, ao: sel.anchorOffset,
                         fn: sel.focusNode,  fo: sel.focusOffset };
    } else if (state.lastGood && p.contains(state.lastGood.an) && p.contains(state.lastGood.fn)) {
      var g = state.lastGood;
      try { sel.setBaseAndExtent(g.an, g.ao, g.fn, g.fo); } catch (e) { /* keep what the browser has */ }
    }

    if (!state.selecting) {
      state.selecting = true;
      // A new selection has not been let go of yet -- unless it is the one a
      // release just finished (a double-click reports its word a beat late).
      var justReleased = !state.pointerDown && !state.keyDown &&
                         Date.now() - state.releasedAt < RELEASE_GRACE_MS;
      if (!justReleased) state.sawRelease = false;
      renderDock();
    }
  }

  /**
   * A finger, mouse button or key came up. That is the second of the two
   * things the live panel waits for; the selection itself is the first.
   */
  function onRelease() {
    state.pointerDown = false;
    state.sawRelease = true;
    state.releasedAt = Date.now();
    if (state.selecting || state.pending) setTimeout(function () {
      onSelectionChange();
      renderDock();
    }, 0);
  }

  /** Keys typed into a note, a search box or a sheet are not selecting text. */
  function isTypingTarget(t) {
    if (!t || !t.tagName) return false;
    var tag = t.tagName.toUpperCase();
    return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || !!t.isContentEditable;
  }

  /** Turn a live DOM range into the stored (block, start, end) anchor. */
  function anchorFor(range) {
    var blocks = blockList();
    var block = blockOf(range.startContainer);
    if (!block) return null;
    var index = blocks.indexOf(block);
    if (index < 0) return null;

    var start = offsetWithin(block, range.startContainer, range.startOffset);
    var end;
    if (block.contains(range.endContainer)) {
      end = offsetWithin(block, range.endContainer, range.endOffset);
    } else {
      // Selection ran past this paragraph. Anchor what belongs to this one; the
      // rest is painted from the stored text when the mark is applied.
      end = block.textContent.length;
    }
    if (end < start) { var swap = start; start = end; end = swap; }

    return {
      blockIndex: index,
      startOffset: start,
      endOffset: end,
      pageNumber: pageOf(block)
    };
  }

  /** The direct children of the prose container are the anchorable blocks. */
  function blockList() {
    if (!state.prose) return [];
    var kids = state.prose.children;
    if (!kids || kids.length === 0) return [state.prose];   // plain-text render
    return Array.prototype.slice.call(kids);
  }

  /** Climb from a node to the block that owns it. */
  function blockOf(node) {
    if (!state.prose) return null;
    var kids = state.prose.children;
    if (!kids || kids.length === 0) return state.prose;
    var cur = node;
    while (cur && cur !== state.prose) {
      if (cur.parentNode === state.prose) return cur.nodeType === 1 ? cur : null;
      cur = cur.parentNode;
    }
    return null;
  }

  /** Character offset of (node, offset) measured across the block's text. */
  function offsetWithin(block, node, offset) {
    if (node === block) {
      // Offset counts child nodes, not characters -- sum the ones before it.
      var total = 0;
      for (var i = 0; i < offset && i < block.childNodes.length; i++) {
        total += textLengthOf(block.childNodes[i]);
      }
      return total;
    }
    var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT, null);
    var seen = 0;
    var t;
    while ((t = walker.nextNode())) {
      if (t === node) return seen + offset;
      seen += t.nodeValue.length;
    }
    return seen;
  }

  function textLengthOf(node) {
    return node.nodeType === 3 ? node.nodeValue.length : (node.textContent || "").length;
  }

  /** PDF Reader view stamps each paragraph with the page it came from. */
  function pageOf(block) {
    var raw = block && block.getAttribute ? block.getAttribute("data-page") : null;
    var n = parseInt(raw, 10);
    return isNaN(n) || n <= 0 ? null : n;
  }

  // --------------------------------------------------------------- the dock

  /**
   * The dock has exactly three faces and this is the only place that picks one:
   *   live  -- something is latched, show it and the actions
   *   nudge -- Fidelity view of a document that HAS a reflowed view
   *   idle  -- reflowed view on screen, nothing selected yet
   * With nothing to offer at all (an image, a scan) the dock hides entirely.
   *
   * The idle row STAYS ON SCREEN under the live face. It is what keeps the
   * dock's height in the page flow constant, so going live never moves the
   * text the reader is dragging across (see .lr-dock-live in reader.css).
   */
  function renderDock() {
    if (!el.dock) return;
    var settled = !!(state.prose && state.pending && !state.selecting);
    // Selected AND let go of: only then does the panel come over the text.
    var live  = settled && state.sawRelease;
    // Selected, but the phone never said the finger came up: offer one tap in
    // the idle row instead of putting the panel over the text unasked.
    var ready = settled && !state.sawRelease;
    var nudge = !state.prose && state.canReflow;
    // While a selection is being dragged the idle row stands in for the live
    // face, so the dock keeps its height and the text does not jump.
    var idle  = !!state.prose && (!state.pending || state.selecting);

    el.dock.hidden = !(live || ready || nudge || idle);
    if (el.dockLive)  el.dockLive.hidden  = !live;
    if (el.dockNudge) el.dockNudge.hidden = !nudge;
    if (el.dockIdle)  el.dockIdle.hidden  = !(idle || live || ready);
    if (el.dockReady) el.dockReady.hidden = !ready;
    if (el.dockHint)  el.dockHint.hidden  = ready;

    if (el.dockHint) {
      el.dockHint.textContent = live
        ? "Drag the handles to change what is selected"
        : state.selecting
          ? "Let go to highlight or save what is selected"
          : "Select text to highlight or save an excerpt";
    }

    if (live && el.dockQuote) el.dockQuote.textContent = clip(state.pending.text, QUOTE_PREVIEW_CHARS);
  }

  function clearPending() {
    state.pending = null;
    state.lastGood = null;
    renderDock();
  }

  /** Name the project excerpts will land on, so the destination is never a guess. */
  function showDestination() {
    if (!el.dockDest || !state.preferProjectId) return;
    window.LWAuth.apiGet("/api/research/" + state.preferProjectId).then(function (res) {
      var title = (res && res.ok && res.data) ? res.data.title : "";
      if (!title) return;
      el.dockDest.textContent = "Excerpts go to " + title;
      el.dockDest.hidden = false;
    }).catch(function () { /* the destination label is a nicety, not a gate */ });
  }

  function runSelectionAction(act) {
    if (act === "clear") { clearSelection(); return; }
    if (!state.pending) { toast("Select some text first."); return; }
    if (act === "highlight") { saveHighlight(state.color); return; }
    if (act === "excerpt")   { openExcerptSheet(); return; }
    if (act === "quote")     { openQuoteSheet(state.pending.text); return; }
    if (act === "define")    { openDefineSheet(); return; }
    if (act === "research")  { openResearchSheet(); }
  }

  // ------------------------------------------------------------- marks data

  function loadMarks() {
    if (state.loaded) return Promise.resolve(state.marks);
    if (!window.LWAuth || !window.LWAuth.apiGet || !state.docKey) {
      state.loaded = true;
      return Promise.resolve(state.marks);
    }
    return window.LWAuth.apiGet("/api/reader/highlights?docKey=" + encodeURIComponent(state.docKey))
      .then(function (res) {
        if (res && res.ok && Array.isArray(res.data)) state.marks = res.data;
        state.loaded = true;
        return state.marks;
      })
      .catch(function () { state.loaded = true; return state.marks; });
  }

  function highlightPayload(colorCode) {
    var p = state.pending;
    return {
      docKey: state.docKey,
      docTitle: state.docTitle,
      viewMode: state.viewMode,
      pageNumber: p.pageNumber,
      blockIndex: p.blockIndex,
      startOffset: p.startOffset,
      endOffset: p.endOffset,
      selectedText: p.text,
      colorCode: isKnownColor(colorCode) ? colorCode : state.color
    };
  }

  /** Mark the latched selection in one colour, and remember that colour. */
  function saveHighlight(colorCode) {
    if (state.busy) return;
    if (!state.pending) { toast("Select some text first."); return; }
    state.busy = true;
    rememberColor(colorCode);
    window.LWAuth.apiPost("/api/reader/highlights", highlightPayload(state.color))
      .then(function (res) {
        state.busy = false;
        if (!res || !res.ok) { toast(errorTextOf(res, "That highlight could not be saved.")); return; }
        state.marks.push(res.data);
        applyMark(res.data);
        clearSelection();
      })
      .catch(function () { state.busy = false; toast("That highlight could not be saved."); });
  }

  /**
   * Change a saved mark's colour. The painted <mark> elements are re-tinted in
   * place rather than re-applied, so a passage spanning three paragraphs
   * changes colour in all three without re-running the anchoring.
   */
  function recolorMark(id, colorCode) {
    if (!id || state.busy) return;
    state.busy = true;
    rememberColor(colorCode);
    window.LWAuth.apiPost("/api/reader/highlights/" + id + "/color", { colorCode: state.color })
      .then(function (res) {
        state.busy = false;
        if (!res || !res.ok) { toast(errorTextOf(res, "That color could not be saved.")); return; }
        for (var i = 0; i < state.marks.length; i++) {
          if (state.marks[i].id === id) state.marks[i] = res.data;
        }
        repaintMark(res.data);
        markCurrentSwatch(el.markColors, state.color);
        renderMarkList();
      })
      .catch(function () { state.busy = false; toast("That color could not be saved."); });
  }

  /** Swap the hl-* class on every painted piece of one mark. */
  function repaintMark(m) {
    if (!state.prose || !m) return;
    var wanted = colorClassOf(m);
    state.prose.querySelectorAll('mark[data-mark-id="' + m.id + '"]').forEach(function (mk) {
      COLORS.forEach(function (c) { mk.classList.remove("hl-" + c.code); });
      mk.classList.add(wanted);
    });
  }

  /** Tapping a mark in the text (or a row in the list) opens it for editing. */
  function openMarkSheet(id) {
    var found = markById(id);
    if (!found) return;
    state.openMarkId = id;
    if (el.markText) el.markText.textContent = clip(found.selectedText, QUOTE_PREVIEW_CHARS);

    // A bookmark has no colour to change, so the strip is not shown for one --
    // but everything about notes is identical for both kinds of mark.
    var book = isBookmark(found);
    if (el.markColorBlock) el.markColorBlock.hidden = book;
    if (!book) markCurrentSwatch(el.markColors, colorClassOf(found).replace("hl-", ""));

    // A bookmark holds no passage, so there is nothing to copy and nothing to
    // send to the Quotes shelf -- only a highlight offers those.
    if (el.markCopy)  el.markCopy.hidden = book;
    if (el.markQuote) el.markQuote.hidden = book;

    var title = document.querySelector("#lrMarkSheet .lr-sheet-title");
    if (title) title.textContent = markNoun(found);

    closeNoteEditor();
    renderNotes();
    closeSheet("lrMarksSheet");
    openSheet("lrMarkSheet");
  }

  // ------------------------------------------------ notes on a mark (2411)

  /**
   * Every note in the document comes down in one call alongside the marks, so
   * opening a mark never costs a round trip and the notebook can be built
   * without one either.
   */
  function loadNotes() {
    if (!window.LWAuth || !window.LWAuth.apiGet || !state.docKey) return Promise.resolve(state.notes);
    return window.LWAuth.apiGet("/api/reader/notes?docKey=" + encodeURIComponent(state.docKey))
      .then(function (res) {
        if (res && res.ok && Array.isArray(res.data)) state.notes = res.data;
        return state.notes;
      })
      .catch(function () { return state.notes; });
  }

  /** Every note written against one mark, oldest first -- the order they were written. */
  function notesFor(markId) {
    return state.notes.filter(function (n) { return n.markId === markId; });
  }

  function renderNotes() {
    if (!el.markNotes) return;
    var mine = notesFor(state.openMarkId);
    if (!mine.length) {
      el.markNotes.innerHTML = '<p class="lr-def-empty" data-testid="reader-notes-empty">' +
        "Nothing written here yet.</p>";
      return;
    }
    var html = "";
    mine.forEach(function (n) {
      html += '<div class="lr-note-row" data-testid="reader-note-row">' +
              '<button type="button" class="lr-note-body" data-note-edit="' + n.id +
              '" title="Edit this note" data-testid="reader-note-body">' +
              esc(n.noteText || "") + "</button>" +
              '<button type="button" class="lr-mark-drop" data-note-drop="' + n.id +
              '" data-testid="reader-note-remove">Remove</button></div>';
    });
    el.markNotes.innerHTML = html;
  }

  /** Open the editor blank (a new note) or loaded with one that already exists. */
  function openNoteEditor(noteId) {
    if (!el.noteEditor) return;
    var existing = null;
    for (var i = 0; i < state.notes.length; i++) if (state.notes[i].id === noteId) existing = state.notes[i];
    state.editingNoteId = existing ? existing.id : 0;
    if (el.noteInput) el.noteInput.value = existing ? (existing.noteText || "") : "";
    el.noteEditor.hidden = false;
    if (el.noteInput) el.noteInput.focus();
  }

  function closeNoteEditor() {
    state.editingNoteId = 0;
    if (el.noteInput) el.noteInput.value = "";
    if (el.noteEditor) el.noteEditor.hidden = true;
  }

  /**
   * Save what is in the editor -- a new note, or a rewrite of one. Emptying a
   * note is deliberately NOT a delete: Remove is its own button, so a mis-tap
   * on the keyboard cannot silently wipe what was written.
   */
  function saveNote() {
    if (state.busy || !el.noteInput) return;
    var text = el.noteInput.value.trim();
    if (!text) { toast("Write something first."); return; }
    var editingId = state.editingNoteId;
    var markId = state.openMarkId;
    if (!markId) return;

    state.busy = true;
    var call = editingId
      ? window.LWAuth.apiPost("/api/reader/notes/" + editingId, { noteText: text })
      : window.LWAuth.apiPost("/api/reader/notes", { markId: markId, noteText: text });

    call.then(function (res) {
      state.busy = false;
      if (!res || !res.ok) { toast(errorTextOf(res, "That note could not be saved.")); return; }
      if (editingId) {
        for (var i = 0; i < state.notes.length; i++) {
          if (state.notes[i].id === editingId) state.notes[i] = res.data;
        }
      } else {
        state.notes.push(res.data);
      }
      closeNoteEditor();
      renderNotes();
      repaintNoted(markId);
      toast(editingId ? "Note saved" : "Note added");
    }).catch(function () { state.busy = false; toast("That note could not be saved."); });
  }

  function removeNote(noteId) {
    if (!noteId) return;
    window.LWAuth.apiPost("/api/reader/notes/" + noteId, null, "DELETE").then(function (res) {
      if (!res || !res.ok) { toast(window.LWAuth.errorText(res, "That note could not be removed.")); return; }
      state.notes = state.notes.filter(function (n) { return n.id !== noteId; });
      renderNotes();
      repaintNoted(state.openMarkId);
      toast("Note removed");
    }).catch(function () { toast("That note could not be removed."); });
  }

  /** Underline every painted mark that carries a note. Run once, after loading. */
  function applyNotedClasses() {
    if (!state.prose) return;
    state.marks.forEach(function (m) { repaintNoted(m.id); });
  }

  /** A highlight that carries a note is underlined, so it reads differently. */
  function repaintNoted(markId) {
    if (!state.prose || !markId) return;
    var noted = notesFor(markId).length > 0;
    state.prose.querySelectorAll('mark[data-mark-id="' + markId + '"]').forEach(function (mk) {
      mk.classList.toggle("is-noted", noted);
    });
  }

  function removeMark(id) {
    if (!id) return;
    window.LWAuth.apiPost("/api/reader/highlights/" + id, null, "DELETE")
      .then(function (res) {
        if (!res || !res.ok) { toast(window.LWAuth.errorText(res, "That highlight could not be removed.")); return; }
        var was = markById(id);
        state.marks = state.marks.filter(function (m) { return m.id !== id; });
        // The server clears the mark's notes with it; drop ours to match rather
        // than leaving notes pointing at a mark that is no longer here.
        state.notes = state.notes.filter(function (n) { return n.markId !== id; });
        unwrapMark(id);
        renderMarkList();
        toast(markNoun(was) + " removed");
      })
      .catch(function () { toast("That highlight could not be removed."); });
  }

  function errorTextOf(res, fallback) {
    return (res && res.data && res.data.message) ? res.data.message : fallback;
  }

  // ------------------------------------------------------------ marks in DOM

  function applyAllMarks() {
    if (!state.prose) return;
    state.marks.forEach(applyMark);
  }

  /**
   * Paint one saved mark. The anchor block is painted from the stored offsets,
   * and any text the selection carried past that block is chased through the
   * blocks that follow -- so a passage spanning three paragraphs is highlighted
   * across all three rather than trailing off after the first.
   */
  function applyMark(m) {
    if (!m || !state.prose) return;
    // A bookmark marks a SPOT, not a passage. Painting one would put a
    // zero-width tint in the text that the reader can neither see nor tap.
    if (isBookmark(m)) return;
    var blocks = blockList();
    var block = blocks[m.blockIndex];
    if (!block) return;

    var full = block.textContent || "";
    var whole = normalize(m.selectedText || "");
    var start = m.startOffset;
    var end = m.endOffset;

    // Trust the stored text over the stored offsets: if the render shifted, the
    // text is still findable and the mark still lands in the right place.
    //
    // The probe is capped at THIS BLOCK'S share of the passage, never the whole
    // stored text -- a selection that runs into the next paragraph would
    // otherwise never match here, and the mark would silently paint nothing.
    var span = Math.max(0, end - start);
    var probe = (m.selectedText || "").slice(0, Math.min(120, span));
    if (probe && full.substr(start, probe.length) !== probe) {
      var found = full.indexOf(probe);
      if (found < 0) return;
      start = found;
      end = Math.min(found + span, full.length);
    }
    if (end <= start) return;

    wrapRange(block, start, end, m);

    // Anything left over belongs to the paragraphs after this one.
    var painted = normalize(full.substring(start, end));
    var remainder = whole.length > painted.length ? whole.substring(painted.length).trim() : "";
    for (var i = m.blockIndex + 1; remainder && i < blocks.length; i++) {
      var nextText = normalize(blocks[i].textContent || "");
      if (!nextText) continue;
      var take = Math.min(nextText.length, remainder.length);
      // Only continue while the paragraph really is the next stretch of the
      // stored passage -- a mismatch means the render moved and we stop.
      if (nextText.substring(0, take) !== remainder.substring(0, take)) break;
      wrapRange(blocks[i], 0, take, m);
      remainder = remainder.substring(take).trim();
    }
  }

  /** Collapse runs of whitespace the way the stored selection text was collapsed. */
  function normalize(s) { return (s || "").replace(/\s+/g, " ").trim(); }

  /** Wrap [start, end) of a block's text in a <mark>, splitting text nodes. */
  function wrapRange(block, start, end, m) {
    var nodes = [];
    var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT, null);
    var seen = 0;
    var t;
    while ((t = walker.nextNode())) {
      var nodeStart = seen;
      var nodeEnd = seen + t.nodeValue.length;
      seen = nodeEnd;
      if (nodeEnd <= start || nodeStart >= end) continue;
      if (t.parentNode && t.parentNode.classList &&
          t.parentNode.classList.contains("lr-mark")) continue;   // already marked
      nodes.push({ node: t, from: Math.max(0, start - nodeStart), to: Math.min(t.nodeValue.length, end - nodeStart) });
    }

    nodes.forEach(function (slice) {
      var node = slice.node;
      if (slice.to <= slice.from) return;
      var target = node;
      if (slice.from > 0) target = target.splitText(slice.from);
      if (slice.to - slice.from < target.nodeValue.length) target.splitText(slice.to - slice.from);
      var mark = document.createElement("mark");
      mark.className = "lr-mark " + colorClassOf(m) +
                       (m.researchNoteId ? " is-excerpt" : "") +
                       (notesFor(m.id).length ? " is-noted" : "");
      mark.setAttribute("data-mark-id", String(m.id));
      target.parentNode.insertBefore(mark, target);
      mark.appendChild(target);
    });
  }

  function unwrapMark(id) {
    if (!state.prose) return;
    state.prose.querySelectorAll('mark[data-mark-id="' + id + '"]').forEach(function (mk) {
      var parent = mk.parentNode;
      while (mk.firstChild) parent.insertBefore(mk.firstChild, mk);
      parent.removeChild(mk);
      parent.normalize();
    });
  }

  function jumpToMark(id) {
    var m = markById(id);
    closeAllSheets();
    if (!state.prose) { toast("Switch to Reader view to go back to this."); return; }

    // A bookmark paints nothing, so it is found by the paragraph it was kept
    // at rather than by a painted element.
    if (isBookmark(m)) {
      var block = blockList()[m.blockIndex];
      if (!block) { toast("That bookmark is not on this view."); return; }
      if (block.scrollIntoView) block.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }

    var mk = state.prose.querySelector('mark[data-mark-id="' + id + '"]');
    if (!mk) { toast("That highlight is not on this view."); return; }
    if (mk.scrollIntoView) mk.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function markById(id) {
    for (var i = 0; i < state.marks.length; i++) if (state.marks[i].id === id) return state.marks[i];
    return null;
  }

  // ------------------------------------------------------------ excerpt flow

  function openExcerptSheet() {
    if (!state.pending) return;
    if (el.exText) el.exText.textContent = state.pending.text;
    if (el.exMsg) { el.exMsg.textContent = ""; el.exMsg.classList.remove("is-ok"); }
    if (el.exComment) el.exComment.value = "";
    if (el.exKeyPoint) el.exKeyPoint.checked = false;
    if (el.exStance) el.exStance.value = "";
    refreshSelect(el.exStance);
    openSheet("lrExcerptSheet");
    loadProjects();
  }

  function loadProjects() {
    if (state.projects) { fillProjects(); return; }
    window.LWAuth.apiGet("/api/research").then(function (res) {
      state.projects = (res && res.ok && Array.isArray(res.data)) ? res.data : [];
      fillProjects();
    }).catch(function () { state.projects = []; fillProjects(); });
  }

  function fillProjects() {
    if (!el.exProject) return;
    var list = state.projects || [];
    setOptions(el.exProject, list.map(function (p) {
      return { value: String(p.id), label: p.title || ("Project " + p.id) };
    }), list.length ? null : "No research projects yet");
    // Opened from a project's Files tab -- default the pick-list to that project.
    if (state.preferProjectId) {
      var wanted = String(state.preferProjectId);
      for (var i = 0; i < list.length; i++) {
        if (String(list[i].id) === wanted) {
          el.exProject.value = wanted;
          refreshSelect(el.exProject);
          break;
        }
      }
    }
    if (list.length) loadProjectDetail(parseInt(el.exProject.value, 10) || 0);
    else { setOptions(el.exSource, [], "No source"); setOptions(el.exTheme, [], "No theme"); }
  }

  function loadProjectDetail(projectId) {
    if (!projectId) return;
    window.LWAuth.apiGet("/api/research/" + projectId + "/workspace").then(function (res) {
      var data = (res && res.ok && res.data) ? res.data : {};
      var sources = Array.isArray(data.sources) ? data.sources : [];
      var themes = Array.isArray(data.themes) ? data.themes : [];
      setOptions(el.exSource, sources.map(function (s) {
        return { value: String(s.id), label: s.title || ("Source " + s.id) };
      }), "No source");
      setOptions(el.exTheme, themes.map(function (t) {
        return { value: String(t.id), label: t.themeName || ("Theme " + t.id) };
      }), "No theme");
    }).catch(function () {
      setOptions(el.exSource, [], "No source");
      setOptions(el.exTheme, [], "No theme");
    });
  }

  function saveExcerpt() {
    if (state.busy || !state.pending) return;
    var projectId = parseInt(el.exProject ? el.exProject.value : "", 10) || 0;
    if (!projectId) { showExcerptMsg("Pick a research project first.", false); return; }

    state.busy = true;
    if (el.exSave) el.exSave.disabled = true;
    var body = {
      highlight: highlightPayload(state.color),
      projectId: projectId,
      sourceId: intOrNull(el.exSource ? el.exSource.value : ""),
      themeId: intOrNull(el.exTheme ? el.exTheme.value : ""),
      stance: el.exStance ? (el.exStance.value || null) : null,
      commentary: el.exComment ? (el.exComment.value || null) : null,
      keyPoint: el.exKeyPoint ? el.exKeyPoint.checked : false
    };

    window.LWAuth.apiPost("/api/reader/excerpt", body).then(function (res) {
      state.busy = false;
      if (el.exSave) el.exSave.disabled = false;
      if (!res || !res.ok) {
        showExcerptMsg(errorTextOf(res, "That excerpt could not be saved."), false);
        return;
      }
      if (res.data.highlight) {
        state.marks.push(res.data.highlight);
        applyMark(res.data.highlight);
      }
      clearSelection();
      closeSheet("lrExcerptSheet");
      toast("Excerpt saved to " + projectLabel(projectId));
    }).catch(function () {
      state.busy = false;
      if (el.exSave) el.exSave.disabled = false;
      showExcerptMsg("That excerpt could not be saved.", false);
    });
  }

  function projectLabel(id) {
    var list = state.projects || [];
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i].title || "the project";
    return "the project";
  }

  function showExcerptMsg(msg, isOk) {
    if (!el.exMsg) return;
    el.exMsg.textContent = msg;
    el.exMsg.classList.toggle("is-ok", !!isOk);
  }

  // ------------------------------------------------------------- define flow

  function termOf() {
    var raw = (state.pending && state.pending.text) || "";
    return raw.replace(/^[^A-Za-z0-9]+|[^A-Za-z0-9]+$/g, "").trim();
  }

  function openDefineSheet() {
    var term = termOf();
    if (!term) return;
    if (el.defTitle) el.defTitle.textContent = shortTitle(term);
    if (el.defBody) el.defBody.innerHTML = '<p class="lr-def-empty">Looking it up...</p>';
    openSheet("lrDefineSheet");

    window.LWAuth.apiGet("/api/dictionary/lookup?word=" + encodeURIComponent(term))
      .then(function (res) {
        if (!res) return;
        if (res.status === 404) { renderNoDefinition(term); return; }
        if (!res.ok || !res.data) { renderDefineError(); return; }
        renderDefinition(res.data);
      })
      .catch(renderDefineError);
  }

  function renderDefinition(def) {
    if (!el.defBody) return;
    var html = '<p class="lr-def-word">' + esc(def.word || "") + "</p>";
    if (def.phonetic) html += '<p class="lr-def-phon">' + esc(def.phonetic) + "</p>";
    var meanings = Array.isArray(def.meanings) ? def.meanings : [];
    if (!meanings.length) {
      html += '<p class="lr-def-empty">No definitions came back for this word.</p>';
    }
    meanings.forEach(function (m) {
      html += '<p class="lr-def-pos">' + esc(m.partOfSpeech || "") + "</p><ul class=\"lr-def-list\">";
      (m.definitions || []).forEach(function (d) { html += "<li>" + esc(d) + "</li>"; });
      html += "</ul>";
    });
    el.defBody.innerHTML = html;
  }

  function renderNoDefinition(term) {
    if (!el.defBody) return;
    el.defBody.innerHTML = '<p class="lr-def-empty">No definition found for ' + esc(shortTitle(term)) +
      ". Try Research to look it up online.</p>";
  }

  function renderDefineError() {
    if (!el.defBody) return;
    el.defBody.innerHTML = '<p class="lr-def-empty">The dictionary could not be reached right now.</p>';
  }

  // ----------------------------------------------------------- research flow

  function openResearchSheet() {
    var term = termOf();
    if (!term) return;
    if (el.resTitle) el.resTitle.textContent = shortTitle(term);
    openSheet("lrResearchSheet");
  }

  function openResearchSite(site) {
    var term = termOf();
    if (!term) return;
    var q = encodeURIComponent(term);
    var url;
    if (site === "wikipedia")    url = "https://en.wikipedia.org/w/index.php?search=" + q;
    else if (site === "scholar") url = "https://scholar.google.com/scholar?q=" + q;
    else                         url = "https://www.google.com/search?q=" + q;
    window.open(url, "_blank", "noopener");
    closeSheet("lrResearchSheet");
  }

  // -------------------------------------------------------------- mark list

  /**
   * The notebook: every bookmark and highlight in the document, each with what
   * was written on it. Bookmarks and highlights share the list rather than
   * getting a tab each -- they are the same record, and a reader looking for
   * "the bit about the tide" does not remember which kind they made.
   */
  function renderMarkList() {
    if (!el.marksBody) return;
    if (!state.marks.length) {
      el.marksBody.innerHTML = '<p class="lr-def-empty">' +
        "Nothing is bookmarked or highlighted in this document yet.</p>";
      return;
    }
    var html = "";
    state.marks.forEach(function (m) {
      var book = isBookmark(m);
      var mine = notesFor(m.id);
      var where = whereLabel(m);
      if (m.researchNoteId) where += " | saved as an excerpt";
      if (mine.length) where += " | " + mine.length + (mine.length === 1 ? " note" : " notes");

      // The dot recolours a highlight; on a bookmark, which has no colour, the
      // same spot opens the mark so notes are still one tap away.
      html += '<div class="lr-mark-row">' +
              '<button type="button" class="lr-mark-dot ' +
              (book ? "is-bookmark" : colorClassOf(m)) +
              '" data-mark-color="' + m.id + '" title="' + (book ? "Open this bookmark" : "Change colour") +
              '" aria-label="' + (book ? "Open this bookmark" : "Change colour") + '"' +
              ' data-testid="' + (book ? "reader-bookmark-open" : "reader-mark-recolor") + '"></button>' +
              '<button type="button" class="lr-mark-text" data-mark-jump="' + m.id +
              '" data-testid="reader-mark-jump">' + esc(clip(m.selectedText, 220)) +
              '<span class="lr-mark-where">' + esc(where) + "</span></button>" +
              '<button type="button" class="lr-mark-drop" data-mark-drop="' + m.id +
              '" data-testid="reader-mark-drop">Remove</button></div>';

      mine.forEach(function (n) {
        html += '<div class="lr-mark-note" data-testid="reader-list-note">' +
                esc(n.noteText || "") + "</div>";
      });
    });
    el.marksBody.innerHTML = html;
  }

  /** "Page 4" when the reader knew a page, otherwise which paragraph it is in. */
  function whereLabel(m) {
    return m.pageNumber ? ("Page " + m.pageNumber) : ("Paragraph " + ((m.blockIndex || 0) + 1));
  }

  // ----------------------------------------------------- the notebook, out

  /**
   * The whole notebook as markdown: every bookmark, highlight and note in the
   * order they appear in the document. Markdown rather than HTML because both
   * destinations read it -- the Reading Nook renders it, and the Leonardo print
   * menu is handed the rendered version.
   */
  function notebookMarkdown() {
    var lines = [];
    lines.push("# " + (state.docTitle || "Notebook"));
    lines.push("");
    lines.push("Bookmarks, highlights and notes from the Echo Reader.");
    lines.push("");
    if (!state.marks.length) {
      lines.push("Nothing was marked in this document.");
      return lines.join("\n");
    }
    state.marks.forEach(function (m) {
      lines.push("## " + markNoun(m) + " -- " + whereLabel(m));
      lines.push("");
      lines.push("> " + (m.selectedText || "").replace(/\s+/g, " ").trim());
      lines.push("");
      notesFor(m.id).forEach(function (n) {
        lines.push("- " + (n.noteText || "").replace(/\s+/g, " ").trim());
      });
      if (notesFor(m.id).length) lines.push("");
    });
    return lines.join("\n");
  }

  /** The same notebook as markup, for the print menu. */
  function notebookHtml() {
    var html = "<h1>" + esc(state.docTitle || "Notebook") + "</h1>";
    html += "<p>Bookmarks, highlights and notes from the Echo Reader.</p>";
    if (!state.marks.length) return html + "<p>Nothing was marked in this document.</p>";
    state.marks.forEach(function (m) {
      html += "<h2>" + esc(markNoun(m) + " -- " + whereLabel(m)) + "</h2>";
      html += "<blockquote>" + esc(normalize(m.selectedText)) + "</blockquote>";
      var mine = notesFor(m.id);
      if (!mine.length) return;
      html += "<ul>";
      mine.forEach(function (n) { html += "<li>" + esc(normalize(n.noteText)) + "</li>"; });
      html += "</ul>";
    });
    return html;
  }

  /**
   * Drop the notebook into the Reading Nook's inbox as a markdown file. It goes
   * through the same upload endpoint the nook's own Add uses, so a name that is
   * already taken gets a "(2)" rather than overwriting last week's notebook.
   */
  function sendNotebookToNook() {
    if (state.busy) return;
    if (!state.marks.length) { toast("There is nothing in this notebook yet."); return; }
    if (!window.LWAuth || !window.LWAuth.apiPostForm) { toast("That is not available here."); return; }

    state.busy = true;
    if (el.nbSave) el.nbSave.disabled = true;
    var name = safeFileName(state.docTitle || "Document") + " notebook.md";
    var form = new FormData();
    form.append("file", new Blob([notebookMarkdown()], { type: "text/markdown" }), name);
    form.append("view", "inbox");

    window.LWAuth.apiPostForm("/api/reading-nook/upload", form).then(function (res) {
      state.busy = false;
      if (el.nbSave) el.nbSave.disabled = false;
      if (!res || !res.ok) { toast(window.LWAuth.errorText(res, "That notebook could not be saved.")); return; }
      var saved = (res.data && res.data.name) ? res.data.name : name;
      toast("Saved to the Reading Nook inbox as " + saved);
    }).catch(function () {
      state.busy = false;
      if (el.nbSave) el.nbSave.disabled = false;
      toast("That notebook could not be saved.");
    });
  }

  /**
   * Print the notebook through the Leonardo print menu -- never window.print().
   * The markup is staged into a hidden element first because printThisPage
   * prints a SELECTOR, and the list on screen is made of buttons that would
   * print as controls rather than as a document.
   */
  function printNotebook() {
    if (!state.marks.length) { toast("There is nothing in this notebook yet."); return; }
    if (!window.Picasso || !window.Picasso.printThisPage) { toast("Printing is not available here."); return; }
    if (!el.printable) { toast("Printing is not available here."); return; }
    el.printable.innerHTML = notebookHtml();
    window.Picasso.printThisPage({
      selector: "#lrNotebookPrintable",
      title: (state.docTitle || "Document") + " notebook"
    });
  }

  /** A file name the Reading Nook will accept, with the extension left to us. */
  function safeFileName(raw) {
    var name = String(raw).replace(/\.[A-Za-z0-9]{1,5}$/, "");   // drop ".pdf" etc
    name = name.replace(/[^A-Za-z0-9 _-]+/g, " ").replace(/\s+/g, " ").trim();
    return name || "Document";
  }

  // ------------------------------------------------------------------ sheets

  function openSheet(id) {
    var sheet = document.getElementById(id);
    if (!sheet) return;
    if (el.scrim) el.scrim.hidden = false;
    sheet.hidden = false;
  }

  function closeSheet(id) {
    var sheet = document.getElementById(id);
    if (sheet) sheet.hidden = true;
    if (!anySheetOpen() && el.scrim) el.scrim.hidden = true;
  }

  function closeAllSheets() {
    document.querySelectorAll(".lr-sheet").forEach(function (s) { s.hidden = true; });
    if (el.scrim) el.scrim.hidden = true;
  }

  function anySheetOpen() {
    var sheets = document.querySelectorAll(".lr-sheet");
    for (var i = 0; i < sheets.length; i++) if (!sheets[i].hidden) return true;
    return false;
  }

  // ------------------------------------------------------------------ shared

  /** Replace a pick-list's options, keeping the Picasso dropdown in step. */
  function setOptions(sel, options, emptyLabel) {
    if (!sel) return;
    sel.innerHTML = "";
    if (emptyLabel) {
      var blank = document.createElement("option");
      blank.value = "";
      blank.textContent = emptyLabel;
      sel.appendChild(blank);
    }
    options.forEach(function (o) {
      var opt = document.createElement("option");
      opt.value = o.value;
      opt.textContent = o.label;
      sel.appendChild(opt);
    });
    refreshSelect(sel);
  }

  function refreshSelect(sel) {
    if (sel && window.PicassoSelect && window.PicassoSelect.refresh) window.PicassoSelect.refresh(sel);
  }

  function intOrNull(v) {
    var n = parseInt(v, 10);
    return isNaN(n) || n <= 0 ? null : n;
  }

  function clearSelection() {
    var sel = window.getSelection();
    if (sel && sel.removeAllRanges) sel.removeAllRanges();
    clearPending();
  }

  function shortTitle(term) {
    var words = term.split(/\s+/);
    if (words.length <= MAX_POPUP_WORDS_FOR_TITLE) return term;
    return words.slice(0, MAX_POPUP_WORDS_FOR_TITLE).join(" ") + "...";
  }

  function clip(s, max) {
    s = s || "";
    return s.length <= max ? s : s.slice(0, max) + "...";
  }

  function esc(s) {
    return String(s === null || s === undefined ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function toast(msg) {
      // A message waits for OK -- never a timer (Story 5606).
      if (window.Picasso && Picasso.toast) {
          Picasso.toast(msg);
          return;
      }
      window.alert(String(msg));
  }

  function debounce(fn, ms) {
    var h;
    return function () { clearTimeout(h); h = setTimeout(fn, ms); };
  }
})();
