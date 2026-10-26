/* Leonardo Reader -- where you stopped reading, and how far through you are
   (Story 2422, Kindle parity). ASCII-only.

   This is the single biggest difference between reading in the Leonardo Reader
   and reading on a Kindle: a Kindle opens a book where you left it, and until
   now we opened every document at the top. The spike (Story 2403) ranked it
   first for exactly that reason -- it is cheap, and it is felt on every single
   reopen.

   THE PLACE IS SERVER-SIDE, NOT localStorage. Tony reads the same document on
   the phone and at the desk; a place kept on the device would be wrong on the
   other one, which is the failure Kindle's whole sync feature exists to avoid.
   It rides on /api/reader/position, keyed by the SAME docKey the marks use, so
   a document's place and its highlights travel together and moving a Reading
   Nook file between buckets loses neither.

   THE PLACE IS RECORDED THREE WAYS, most precise first, because the same
   document does not always render identically from one visit to the next (a
   different font size reflows it; Fidelity and Reader are different renders
   entirely):

     blockIndex + charOffset  the paragraph, and how far into it -- exact, and
                              used whenever a reflowed render is on screen
     pageNumber               the PDF page -- used to restore Fidelity view
     scrollRatio              how far down (0..1) -- the fallback that always
                              works, whatever is on screen

   Restoring tries them in that order and stops at the first that lands.

   SAVING IS DEBOUNCED AND IDEMPOTENT. Scrolling fires constantly; the place is
   written at most once every SAVE_QUIET_MS of stillness, and again when the
   page is hidden (switching apps on a phone is the most common way a reading
   session ends -- there is no unload event to rely on there).

   NOTHING HERE MAY FAIL A READ. A document that was never read, a save that did
   not go through, a place that no longer matches the render -- every one of
   those just means "start at the top". The module reports and moves on.

   IT IS SHARED BY BOTH READING SURFACES (Story 2425). The standalone Leonardo
   Reader page uses it with its own defaults; the Writing Filing Cabinet's
   reader overlay passes its own strip elements and its own pair of anchor
   callbacks, because a writing document is one <pre> of plain text where the
   precise place is a character offset rather than a paragraph index. Everything
   that is genuinely the same -- the debounce, the save policy, the percentage,
   the minutes left, forget -- is written once, here. Anything host-specific
   arrives through init(); nothing in this file names a page it does not own.
*/
(function () {
  "use strict";

  /** Stillness before the place is written. Long enough that a flick costs one save. */
  var SAVE_QUIET_MS = 1200;

  /** How far the place can move before it is worth writing again, as a ratio. */
  var SAVE_MIN_DELTA = 0.004;

  /** Average adult reading speed, for the "about 12 minutes left" readout. */
  var WORDS_PER_MINUTE = 220;

  /** Characters per word, for turning a character count into a word count. */
  var CHARS_PER_WORD = 5.5;

  /** Below this the document is short enough that a time estimate is noise. */
  var MIN_WORDS_FOR_TIME = 300;

  /**
   * How long is left is HIDDEN by default (Story 2427). Tony asked for it: a
   * countdown sitting on the page turns reading into a race against a number,
   * and the percentage alone is the part worth seeing. Tapping the progress
   * strip shows or hides it.
   *
   * The choice is remembered PER DEVICE, and deliberately shared by both
   * reading surfaces -- exactly like the highlight colour and the type
   * preferences. Whether Tony wants to see a countdown is one preference about
   * how he reads, not one per surface, and it is a display choice rather than
   * data, so it stays out of the database.
   */
  var TIME_KEY = "leoReaderShowTimeLeft";

  var state = {
    docKey: "",
    docTitle: "",
    stage: null,
    prose: null,        // the reflowed prose element, when one is on screen
    viewMode: "reader",
    saved: null,        // the place the server currently holds
    lastRatio: -1,      // the ratio last written, so a still page writes nothing
    totalWords: 0,
    pageCount: 0,
    currentPage: null,  // supplied by reader.js in PDF Fidelity view
    trackable: true,    // false for an image, which has no place to keep
    restoring: false,   // suppresses saves while we scroll the reader back
    enabled: false,
    readAnchor: null,   // how the host reads the precise place (see init)
    gotoAnchor: null,   // how the host goes back to one
    pageSelector: "",   // how the host finds a PDF page by number
    onScrollSave: null, // the listeners stop() has to be able to take back off
    onScrollPaint: null,
    onHidden: null,
    onPageHide: null,
    showTime: false     // is "about 12 minutes left" on show? (Story 2427)
                        // The strip's own tap listener is wired once and left
                        // in place: the strip belongs to the page, not to the
                        // document, so stop() has nothing to take back off.
  };

  var el = {};
  var api = {};
  window.LeoReaderPlace = api;

  // ------------------------------------------------------------------ public

  /**
   * Called with the document's identity, once per document.
   *
   * Everything past docKey is optional and defaults to the standalone Leonardo
   * Reader page, so reader.js passes nothing extra. A host with a different
   * layout supplies its own:
   *
   *   stage        the scroller (default #lrStage)
   *   viewMode     recorded with the place, so a surface can tell its own
   *                places apart from another's (default "reader")
   *   progressEl / fillEl / textEl / placeNoteEl
   *                the progress strip and the Contents note
   *   readAnchor   () -> {blockIndex, charOffset} or null -- the precise place
   *                being read right now
   *   gotoAnchor   (place) -> true when it moved -- how to go back to one
   *   pageSelector how to find a PDF page by number (default the reader's)
   */
  api.init = function (opts) {
    opts = opts || {};
    api.stop();
    state.docKey = opts.docKey || "";
    state.docTitle = opts.docTitle || "";
    state.stage = opts.stage || document.getElementById("lrStage");
    state.viewMode = opts.viewMode || "reader";
    state.readAnchor = typeof opts.readAnchor === "function" ? opts.readAnchor : blockPlace;
    state.gotoAnchor = typeof opts.gotoAnchor === "function" ? opts.gotoAnchor : gotoBlock;
    state.pageSelector = opts.pageSelector || '.lr-page[data-page="{n}"]';
    el.progress = opts.progressEl || document.getElementById("lrProgress");
    el.fill = opts.fillEl || document.getElementById("lrProgressFill");
    el.text = opts.textEl || document.getElementById("lrProgressText");
    el.placeNote = opts.placeNoteEl || document.getElementById("lrPlaceNote");
    state.showTime = rememberedShowTime();
    wireProgressTap();
    state.enabled = !!(state.docKey && state.stage && window.LWAuth);
    if (!state.enabled) return;
    wireScroll();
  };

  /**
   * Let go of the document. The standalone page never needs this -- it is one
   * document per page load -- but an overlay opens and closes many documents in
   * one page, and a listener left behind would go on writing the CLOSED
   * document's place as the page underneath it scrolls.
   */
  api.stop = function () {
    if (state.stage && state.onScrollSave) {
      state.stage.removeEventListener("scroll", state.onScrollSave);
      state.stage.removeEventListener("scroll", state.onScrollPaint);
    }
    if (state.onHidden) {
      document.removeEventListener("visibilitychange", state.onHidden);
      window.removeEventListener("pagehide", state.onPageHide);
    }
    state.onScrollSave = null;
    state.onScrollPaint = null;
    state.onHidden = null;
    state.onPageHide = null;
    state.enabled = false;
    state.saved = null;
    state.lastRatio = -1;
    state.currentPage = null;
    state.prose = null;
    if (el.progress) el.progress.hidden = true;
  };

  /**
   * Fetch the stored place. Called once, before the first render, so restoring
   * never has to wait on a round trip after the text is already on screen.
   */
  api.load = function () {
    if (!state.enabled) return Promise.resolve(null);
    return window.LWAuth
      .apiGet("/api/reader/position?docKey=" + encodeURIComponent(state.docKey))
      .then(function (res) {
        // 204 means this document was never read -- the normal case, not a failure.
        if (res && res.ok && res.data && res.data.docKey) state.saved = res.data;
        renderPlaceNote();
        return state.saved;
      })
      .catch(function () { return null; });
  };

  /** True when there is a place to go back to. */
  api.hasPlace = function () { return !!state.saved; };

  /** The stored place, or null. */
  api.place = function () { return state.saved; };

  /**
   * A render finished. `info.prose` is the reflowed element when there is one;
   * `info.pageCount` is set for a PDF. Recomputes the length of the document so
   * the progress readout means something, then redraws it.
   */
  api.setView = function (info) {
    info = info || {};
    state.prose = info.prose || null;
    state.viewMode = info.viewMode || state.viewMode || "reader";
    state.pageCount = info.pageCount || 0;
    state.totalWords = state.prose ? wordsIn(state.prose.textContent || "") : 0;
    // An image is one thing seen at once. It has no reading position, so the
    // strip stays away rather than reporting 0% of something with no progress.
    state.trackable = state.viewMode !== "image";
    renderProgress();
  };

  /** PDF Fidelity view tells us which page is on screen; nothing else knows. */
  api.setCurrentPage = function (page) {
    state.currentPage = page || null;
  };

  /**
   * Go back to the stored place. Resolves with true when it actually moved, so
   * the caller can say so; false means there was nothing to go back to.
   */
  api.restore = function () {
    if (!state.enabled || !state.saved || !state.stage) return Promise.resolve(false);
    var moved = scrollToPlace(state.saved);
    if (!moved) return Promise.resolve(false);
    // Saves are suppressed across the scroll so landing back at the place does
    // not immediately rewrite it -- and, worse, rewrite it from a half-finished
    // smooth scroll that is still in flight.
    state.restoring = true;
    return new Promise(function (resolve) {
      window.setTimeout(function () {
        state.restoring = false;
        renderProgress();
        resolve(true);
      }, 400);
    });
  };

  /** Write the place now, without waiting for the reader to go still. */
  api.saveNow = function () { save(); };

  /** Forget the place, so the document opens at the top next time. */
  api.forget = function () {
    if (!state.enabled) return Promise.resolve(false);
    return window.LWAuth
      .apiPost("/api/reader/position?docKey=" + encodeURIComponent(state.docKey), null, "DELETE")
      .then(function (res) {
        if (!res || !res.ok) return false;
        state.saved = null;
        state.lastRatio = -1;
        renderPlaceNote();
        return true;
      })
      .catch(function () { return false; });
  };

  /**
   * The paragraph being read right now, as {blockIndex, charOffset}, or null
   * when nothing reflowed is on screen. This is what a bookmark anchors to --
   * a bookmark IS a reading position that was kept on purpose.
   */
  api.currentBlock = function () { return anchorNow(); };

  /** The PDF page on screen, or null. */
  api.currentPage = function () { return state.currentPage; };

  /** "46%" / "46%, about 12 minutes left" -- what the strip is showing. */
  api.progressLabel = function () { return el.text ? el.text.textContent : ""; };

  // ----------------------------------------------------------------- reading

  function wireScroll() {
    state.onScrollSave = debounce(function () {
      renderProgress();
      save();
    }, SAVE_QUIET_MS);
    state.stage.addEventListener("scroll", state.onScrollSave, { passive: true });

    // Painting the strip is cheap; do it on every frame's worth of scrolling so
    // the number moves with the thumb rather than a second behind it.
    state.onScrollPaint = throttle(renderProgress, 120);
    state.stage.addEventListener("scroll", state.onScrollPaint, { passive: true });

    // Switching apps on a phone is how a reading session usually ends, and no
    // unload event fires there -- this is the save that actually counts.
    state.onHidden = function () {
      if (document.visibilityState === "hidden") save();
    };
    state.onPageHide = function () { save(); };
    document.addEventListener("visibilitychange", state.onHidden);
    window.addEventListener("pagehide", state.onPageHide);
  }

  /** How far down the document we are, 0..1. */
  function ratio() {
    var span = state.stage.scrollHeight - state.stage.clientHeight;
    if (span <= 0) return 0;
    return clamp(state.stage.scrollTop / span, 0, 1);
  }

  /**
   * The paragraph on screen and how far into it, when a reflowed render is up.
   * The topmost block whose bottom is still below the fold is the one being
   * read -- the one above it has already been finished.
   */
  function blockPlace() {
    if (!state.prose) return null;
    var blocks = blockList();
    if (!blocks.length) return null;
    var top = state.stage.scrollTop;
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      var bTop = offsetTopWithin(b);
      var bBottom = bTop + b.offsetHeight;
      if (bBottom > top) {
        var into = b.offsetHeight > 0 ? clamp((top - bTop) / b.offsetHeight, 0, 1) : 0;
        var len = (b.textContent || "").length;
        return { blockIndex: i, charOffset: Math.round(into * len) };
      }
    }
    var last = blocks.length - 1;
    return { blockIndex: last, charOffset: (blocks[last].textContent || "").length };
  }

  /** The precise place, read the host's way. Never throws -- a host that cannot
   *  answer just means the place falls back to how far down the page we are. */
  function anchorNow() {
    try { return state.readAnchor ? state.readAnchor() : null; }
    catch (e) { return null; }
  }

  /** The default way back to a place: the paragraph, and how far into it. */
  function gotoBlock(p) {
    if (!state.prose) return false;
    var blocks = blockList();
    var b = blocks[p.blockIndex];
    if (!b) return false;
    var len = (b.textContent || "").length;
    var into = len > 0 ? clamp(p.charOffset / len, 0, 1) : 0;
    scrollStageTo(offsetTopWithin(b) + into * b.offsetHeight);
    return true;
  }

  /** The direct children of the prose container -- the same blocks marks anchor to. */
  function blockList() {
    if (!state.prose) return [];
    var kids = state.prose.children;
    if (!kids || kids.length === 0) return [state.prose];   // plain-text render
    return Array.prototype.slice.call(kids);
  }

  /**
    * An element's top in the STAGE'S scroll coordinates.
    *
    * Deliberately measured from rectangles rather than by summing offsetTop up
    * the tree. Nothing between the prose and the stage is positioned, so an
    * element's offsetParent is the BODY -- outside the scroller entirely --
    * and the sum comes back including the toolbar's height and shifting with
    * the scroll. Rectangles plus scrollTop is exact whatever the layout does.
    */
  function offsetTopWithin(node) {
    return node.getBoundingClientRect().top
         - state.stage.getBoundingClientRect().top
         + state.stage.scrollTop;
  }

  // ------------------------------------------------------------------ saving

  function save() {
    if (!state.enabled || state.restoring || !state.trackable) return;
    var r = ratio();
    // A page that has not moved does not need rewriting -- but the FIRST save of
    // a session always goes through, so opening a document and reading a page
    // records it even if the reader barely scrolled.
    if (state.lastRatio >= 0 && Math.abs(r - state.lastRatio) < SAVE_MIN_DELTA) return;
    state.lastRatio = r;

    var place = anchorNow();
    var body = {
      docKey: state.docKey,
      docTitle: state.docTitle,
      viewMode: state.viewMode,
      blockIndex: place ? place.blockIndex : 0,
      charOffset: place ? place.charOffset : 0,
      pageNumber: state.currentPage || null,
      scrollRatio: r,
      percentRead: Math.round(r * 100)
    };

    window.LWAuth.apiPost("/api/reader/position", body)
      .then(function (res) { if (res && res.ok && res.data) state.saved = res.data; })
      .catch(function () { /* the place is a convenience, never a gate on reading */ });
  }

  // --------------------------------------------------------------- restoring

  /**
   * Try each recorded form of the place, most precise first. Returns false when
   * none of them lands, which means the document opens at the top.
   */
  function scrollToPlace(p) {
    if (!p) return false;

    // 1. The precise place, in whatever terms the host records it -- a
    //    paragraph on the standalone reader, a character offset in the
    //    Writing Filing Cabinet's single block of prose.
    try { if (state.gotoAnchor(p)) return true; } catch (e) { /* fall through */ }

    // 2. The PDF page, when the pages are on screen.
    if (p.pageNumber && state.pageSelector) {
      var page = document.querySelector(state.pageSelector.replace("{n}", String(p.pageNumber)));
      if (page) { scrollStageTo(offsetTopWithin(page)); return true; }
    }

    // 3. How far down. Always available, never exact.
    var span = state.stage.scrollHeight - state.stage.clientHeight;
    if (span > 0 && p.scrollRatio > 0) {
      scrollStageTo(clamp(p.scrollRatio, 0, 1) * span);
      return true;
    }
    return false;
  }

  function scrollStageTo(y) {
    if (!state.stage) return;
    state.stage.scrollTop = Math.max(0, Math.round(y));
  }

  // ---------------------------------------------------------------- readouts

  function renderProgress() {
    if (!el.progress) return;
    if (!state.trackable) { el.progress.hidden = true; return; }
    var pct = Math.round(ratio() * 100);
    el.progress.hidden = false;
    if (el.fill) el.fill.style.width = pct + "%";
    if (el.text) el.text.textContent = progressText(pct);
  }

  /**
   * "46%" on its own, plus a time estimate when the document is long enough
   * for one to mean anything, plus the page when a PDF is showing pages.
   */
  function progressText(pct) {
    var parts = [pct + "%"];
    var left = minutesLeft(pct);
    // "finished" is not a countdown, so it is always worth saying.
    if (left > 0) { if (state.showTime) parts.push("about " + left + (left === 1 ? " minute left" : " minutes left")); }
    else if (pct >= 99) parts.push("finished");
    if (state.pageCount && state.currentPage) {
      parts.push("page " + state.currentPage + " of " + state.pageCount);
    }
    return parts.join(" | ");
  }

  /** Zero when the document is too short, or has no extractable text at all. */
  function minutesLeft(pct) {
    if (state.totalWords < MIN_WORDS_FOR_TIME) return 0;
    var remaining = state.totalWords * (1 - pct / 100);
    var mins = Math.round(remaining / WORDS_PER_MINUTE);
    return mins > 0 ? mins : 0;
  }

  // -------------------------------------------------- how long is left (2427)

  /** The remembered choice, defaulting to hidden. */
  function rememberedShowTime() {
    try { return localStorage.getItem(TIME_KEY) === "1"; } catch (e) { return false; }
  }

  /**
   * The progress strip IS the control -- the unobtrusive way in Tony asked for.
   * There is no extra button competing for the toolbar, and the thing you tap is
   * the thing that changes. A small CSS caret (drawn with borders, never a
   * glyph) is the only visible affordance; each host stylesheet says what it
   * looks like, exactly as both say what the search tint looks like.
   */
  function wireProgressTap() {
    if (!el.progress || el.progress.getAttribute("data-time-wired") === "1") {
      applyShowTime();
      return;
    }
    el.progress.setAttribute("data-time-wired", "1");
    el.progress.setAttribute("role", "button");
    el.progress.setAttribute("tabindex", "0");

    if (!el.progress.querySelector(".lr-progress-more")) {
      var caret = document.createElement("span");
      caret.className = "lr-progress-more";
      caret.setAttribute("aria-hidden", "true");
      el.progress.appendChild(caret);
    }

    el.progress.addEventListener("click", toggleShowTime);
    el.progress.addEventListener("keydown", function (ev) {
      if (ev.key !== "Enter" && ev.key !== " ") return;
      ev.preventDefault();
      toggleShowTime();
    });
    applyShowTime();
  }

  function toggleShowTime() {
    state.showTime = !state.showTime;
    try { localStorage.setItem(TIME_KEY, state.showTime ? "1" : "0"); } catch (e) { /* private mode */ }
    applyShowTime();
    renderProgress();
  }

  /** Keep the strip's own state (caret, label) in step with the choice. */
  function applyShowTime() {
    if (!el.progress) return;
    el.progress.classList.toggle("is-time-shown", !!state.showTime);
    el.progress.setAttribute("aria-expanded", state.showTime ? "true" : "false");
    var label = state.showTime
      ? "Hide how long is left"
      : "Show how long is left";
    el.progress.setAttribute("aria-label", label);
    el.progress.setAttribute("title", label);
  }

  /** What the Contents sheet says about where you are. */
  function renderPlaceNote() {
    if (!el.placeNote) return;
    el.placeNote.textContent = state.saved
      ? ("You stopped at " + state.saved.percentRead + "% of this document.")
      : "This document opens at the beginning.";
  }

  function wordsIn(text) {
    return Math.round((text || "").replace(/\s+/g, " ").trim().length / CHARS_PER_WORD);
  }

  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  function debounce(fn, ms) {
    var h;
    return function () { clearTimeout(h); h = setTimeout(fn, ms); };
  }

  /** Leading-edge throttle -- the strip should move with the thumb, not after it. */
  function throttle(fn, ms) {
    var last = 0;
    var h = null;
    return function () {
      var now = Date.now();
      if (now - last >= ms) { last = now; fn(); return; }
      clearTimeout(h);
      h = setTimeout(function () { last = now; fn(); }, ms);
    };
  }
})();
