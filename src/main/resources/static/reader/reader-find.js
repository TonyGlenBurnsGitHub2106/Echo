/* Leonardo Reader -- searching inside the document (Story 2422, Kindle parity).
   ASCII-only.

   Kindle lets you find a phrase without leaving the book, and a long PDF is
   nearly unusable without it. This is that, over the reflowed text: type, and
   every match is tinted, with the one you are on tinted harder and scrolled to.

   WHY IT PAINTS ITS OWN SPANS rather than using the browser's find: a PWA in
   standalone mode has no browser find bar at all, and on a phone Chrome's find
   is buried behind the overflow menu that a standalone PWA does not show.

   THE PAINT IS ALWAYS UNDONE BEFORE IT IS REDONE. Matches are wrapped in
   <span class="lr-find-hit">, which splits text nodes. Leaving that in place
   would slowly shred the prose into fragments and -- much worse -- change what
   the marking layer measures when it anchors a highlight. clear() unwraps every
   span and normalizes the parents back, so the DOM the marks see is byte for
   byte the DOM they were anchored against.

   MARKS ARE NEVER TOUCHED. The walker skips nothing, but wrapping a text node
   INSIDE a <mark> leaves the mark itself intact -- the highlight still paints,
   the search tint sits on top of it, and unwrapping puts it back.

   Only reflowed views can be searched. PDF Fidelity pages are pictures; the
   toolbar hides the button there, exactly as it does for highlighting.
*/
(function () {
  "use strict";

  /** Below this a search matches half the document and helps nobody. */
  var MIN_QUERY_LENGTH = 2;

  /** Past this many hits the paint costs more than it is worth. */
  var MAX_HITS = 500;

  var state = {
    prose: null,
    stage: null,
    hits: [],       // the painted spans, in document order
    current: -1,
    query: "",
    open: false
  };

  var el = {};
  var api = {};
  window.LeoReaderFind = api;

  /**
   * Point the search at a document. Everything is optional and defaults to the
   * standalone Leonardo Reader page's own bar, so reader.js passes only the
   * stage; the Writing Filing Cabinet's reader overlay passes its own three
   * elements (Story 2425). The painted span's class is the same on both
   * surfaces, so each stylesheet only has to say what the tint looks like.
   */
  api.init = function (opts) {
    opts = opts || {};
    state.stage = opts.stage || document.getElementById("lrStage");
    el.bar = opts.barEl || document.getElementById("lrFindBar");
    el.input = opts.inputEl || document.getElementById("lrFindInput");
    el.count = opts.countEl || document.getElementById("lrFindCount");
    wireOnce();
  };

  /** A new render is on screen -- search the new text, not the old spans. */
  api.setProse = function (proseEl) {
    state.prose = proseEl || null;
    state.hits = [];
    state.current = -1;
    if (state.open && state.prose && state.query) run(state.query);
    else renderCount();
  };

  api.isOpen = function () { return state.open; };

  api.open = function () {
    if (!el.bar) return;
    state.open = true;
    el.bar.hidden = false;
    if (el.input) { el.input.focus(); el.input.select(); }
    renderCount();
  };

  api.close = function () {
    state.open = false;
    if (el.bar) el.bar.hidden = true;
    clear();
    state.query = "";
    if (el.input) el.input.value = "";
    renderCount();
  };

  api.toggle = function () { if (state.open) api.close(); else api.open(); };

  api.next = function () { step(+1); };
  api.prev = function () { step(-1); };

  // ------------------------------------------------------------------ wiring

  // Wiring is per INPUT, not per module: a page may init the search more than
  // once (an overlay reopens on every document), and re-listening to the same
  // input would run the search twice for every keystroke.
  var _wiredInput = null;
  function wireOnce() {
    if (!el.bar || _wiredInput === el.input) return;
    _wiredInput = el.input;

    if (el.input) {
      el.input.addEventListener("input", debounce(function () {
        run(el.input.value || "");
      }, 220));
      // Enter walks the hits on a keyboard; the Prev / Next buttons are the
      // only way that matters on a phone, which is why they are on the bar.
      el.input.addEventListener("keydown", function (ev) {
        if (ev.key !== "Enter") return;
        ev.preventDefault();
        if (ev.shiftKey) api.prev(); else api.next();
      });
    }
  }

  // ------------------------------------------------------------- the search

  function run(raw) {
    var q = (raw || "").trim();
    state.query = q;
    clear();
    if (!state.prose || q.length < MIN_QUERY_LENGTH) { renderCount(); return; }

    paint(q);
    state.current = state.hits.length ? 0 : -1;
    focusCurrent(false);
    renderCount();
  }

  /**
   * Wrap every match in a span. Text nodes are collected FIRST and wrapped
   * afterwards -- wrapping while the walker is live would have it walk straight
   * into the nodes it just created and match them again forever.
   */
  function paint(q) {
    var needle = q.toLowerCase();
    var texts = [];
    var walker = document.createTreeWalker(state.prose, NodeFilter.SHOW_TEXT, null);
    var t;
    while ((t = walker.nextNode())) {
      if (t.nodeValue && t.nodeValue.toLowerCase().indexOf(needle) >= 0) texts.push(t);
    }

    for (var i = 0; i < texts.length && state.hits.length < MAX_HITS; i++) {
      wrapMatches(texts[i], needle);
    }
  }

  /** Wrap every occurrence of `needle` inside one text node. */
  function wrapMatches(node, needle) {
    var rest = node;
    while (rest && state.hits.length < MAX_HITS) {
      var hay = rest.nodeValue || "";
      var at = hay.toLowerCase().indexOf(needle);
      if (at < 0) return;

      var target = at > 0 ? rest.splitText(at) : rest;
      if (target.nodeValue.length > needle.length) target.splitText(needle.length);

      var span = document.createElement("span");
      span.className = "lr-find-hit";
      target.parentNode.insertBefore(span, target);
      span.appendChild(target);
      state.hits.push(span);

      rest = span.nextSibling && span.nextSibling.nodeType === 3 ? span.nextSibling : null;
    }
  }

  /**
   * Take every painted span back out and stitch the text nodes together again,
   * so the prose is exactly what it was before the search touched it.
   */
  function clear() {
    if (!state.prose) { state.hits = []; state.current = -1; return; }
    var spans = state.prose.querySelectorAll("span.lr-find-hit");
    for (var i = 0; i < spans.length; i++) {
      var sp = spans[i];
      var parent = sp.parentNode;
      if (!parent) continue;
      while (sp.firstChild) parent.insertBefore(sp.firstChild, sp);
      parent.removeChild(sp);
      parent.normalize();
    }
    state.hits = [];
    state.current = -1;
  }

  function step(delta) {
    if (!state.hits.length) return;
    state.current = (state.current + delta + state.hits.length) % state.hits.length;
    focusCurrent(true);
    renderCount();
  }

  function focusCurrent(scroll) {
    for (var i = 0; i < state.hits.length; i++) {
      state.hits[i].classList.toggle("is-current", i === state.current);
    }
    if (!scroll || state.current < 0) return;
    var hit = state.hits[state.current];
    if (hit && hit.scrollIntoView) hit.scrollIntoView({ block: "center" });
  }

  function renderCount() {
    if (!el.count) return;
    if (!state.query || state.query.length < MIN_QUERY_LENGTH) { el.count.textContent = ""; return; }
    if (!state.hits.length) { el.count.textContent = "No matches"; return; }
    var capped = state.hits.length >= MAX_HITS ? "+" : "";
    el.count.textContent = (state.current + 1) + " of " + state.hits.length + capped;
  }

  function debounce(fn, ms) {
    var h;
    return function () { clearTimeout(h); h = setTimeout(fn, ms); };
  }
})();
