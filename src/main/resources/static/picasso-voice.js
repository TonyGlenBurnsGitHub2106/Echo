/* picasso-voice.js -- In-app voice dictation for LeonardoWeb.

   Adds a mic toggle in the page chrome and a small mic icon next to every
   <input type="text"|"search"> and <textarea>. Tap a field's mic to start
   continuous dictation via the Web Speech API; tap again to stop.

   Field mics are HIDDEN by default on every page load. Tony has to tap the
   footer toggle to reveal them -- no per-page memory between visits.

   Five placement prototypes -- switch with a URL param (?vt=footer | floating
   | corner | nav | header). The placement choice persists in localStorage
   (voiceTogglePlacement); the per-page on/off does NOT. Default: 'footer'. */
(function () {
    'use strict';

    var Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) {
        console.info('PicassoVoice: SpeechRecognition not available; voice dictation disabled.');
        return;
    }

    // ---- Toggle placement (prototype switcher) -------------------------------

    var PLACEMENT_KEY = 'voiceTogglePlacement';
    var VALID_PLACEMENTS = ['footer', 'floating', 'corner', 'nav', 'header'];
    var DEFAULT_PLACEMENT = 'footer';

    function getPlacement() {
        try {
            var sp = new URLSearchParams(location.search);
            var fromUrl = sp.get('vt');
            if (fromUrl && VALID_PLACEMENTS.indexOf(fromUrl) >= 0) {
                try { localStorage.setItem(PLACEMENT_KEY, fromUrl); } catch (e) {}
                return fromUrl;
            }
        } catch (e) {}
        try {
            var stored = localStorage.getItem(PLACEMENT_KEY);
            if (stored && VALID_PLACEMENTS.indexOf(stored) >= 0) return stored;
        } catch (e) {}
        return DEFAULT_PLACEMENT;
    }

    // ---- State ---------------------------------------------------------------

    // After this many consecutive engine rounds with no speech captured, give
    // up and stop the mic. Each round is roughly a few seconds of silence per
    // the browser engine, so 3 ~ ~10-20 seconds of total quiet before stop.
    var MAX_CONSECUTIVE_SILENT = 3;

    var state     = 'off';        // 'on' | 'off'
    var toggleBtn = null;
    var attached  = new WeakSet();
    var current   = null;         // active recording session

    // ---- Eligibility ---------------------------------------------------------

    function isEligible(el) {
        if (!el || !el.tagName) return false;
        if (el.disabled || el.readOnly) return false;
        if (el.dataset && el.dataset.voiceSkip === 'true') return false;
        var tag = el.tagName.toLowerCase();
        if (tag === 'textarea') return true;
        if (tag !== 'input') return false;
        var type = (el.getAttribute('type') || 'text').toLowerCase();
        return type === 'text' || type === 'search';
    }

    function eligibleInputsIn(root) {
        return (root || document).querySelectorAll(
            'textarea, input[type="text"], input[type="search"], input:not([type])'
        );
    }

    function hasAnyEligible() {
        var inputs = eligibleInputsIn(document);
        for (var i = 0; i < inputs.length; i++) {
            if (isEligible(inputs[i])) return true;
        }
        return false;
    }

    // ---- Per-field mic icon attach -------------------------------------------

    function attachMic(el) {
        if (!el || attached.has(el) || !isEligible(el)) return;
        var parent = el.parentNode;
        if (!parent) return;

        var wrap = document.createElement('span');
        wrap.className = 'voice-mic-wrap';
        if (el.tagName.toLowerCase() === 'textarea' ||
            getComputedStyle(el).display === 'block') {
            wrap.classList.add('voice-mic-wrap-block');
        }
        parent.insertBefore(wrap, el);
        wrap.appendChild(el);

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'voice-mic-btn';
        btn.setAttribute('aria-label', 'Dictate to this field');
        btn.title = 'Dictate (tap to start, tap again to stop)';
        btn.innerHTML = '&#127908;';
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            toggleRecording(el, btn);
        });
        wrap.appendChild(btn);
        attached.add(el);
    }

    function attachAllNow() {
        var inputs = eligibleInputsIn(document);
        for (var i = 0; i < inputs.length; i++) attachMic(inputs[i]);
    }

    function setMicsVisible(visible) {
        document.body.classList.toggle('voice-mics-hidden', !visible);
    }

    // ---- Recording -----------------------------------------------------------

    function toggleRecording(el, btn) {
        if (current && current.el === el) { stopRecording(); return; }
        if (current) stopRecording();
        startRecording(el, btn);
    }

    // Web Speech transcripts arrive lowercase. Dictation rule (matches
    // Word / Google Docs and what Tony asked for explicitly):
    //
    //   1. ALWAYS capitalize the first letter of every utterance. Engine
    //      runs in single-utterance mode with auto-restart on silence, so
    //      every round-start is a natural sentence boundary -- even when
    //      the prior round didn't end with explicit "period". Without
    //      this, "hi tony" + pause + "how are you" produced
    //      "Hi tony how are you" instead of "Hi tony How are you".
    //   2. Also capitalize anything that follows mid-utterance sentence
    //      punctuation (. ! ? optionally + closing brackets + whitespace).
    //      The engine sometimes hands us "Hi. how are you" in a single
    //      round -- the first letter is already fine, but the post-period
    //      "h" still needs to be uppercased.
    //
    // `prev` is no longer consulted -- kept in the signature so callers
    // don't have to change. Mid-utterance corrections via replace() are
    // idempotent: capitalizing an already-capitalized letter is a no-op.
    function capitalizeForContext(prev, text) {
        if (!text) return text;
        text = text.replace(/^(\s*)(\S)/, function (_, ws, ch) {
            return ws + ch.toUpperCase();
        });
        text = text.replace(/([.!?]["')\]]*\s+)([a-z])/g, function (_, sep, ch) {
            return sep + ch.toUpperCase();
        });
        return text;
    }

    // Join the new transcript piece onto the prior buffer without leaving a
    // stray space in front of punctuation. Auto-restart sends each phrase as
    // a separate round, so a pause before saying "period" / "comma" would
    // otherwise produce "Hello ." across the round boundary. Also collapses
    // "word <space> punct" that the engine sometimes returns within a single
    // round. The user can still force a space by saying the word "space" --
    // the engine returns "space" as literal text, not whitespace.
    function joinSpeech(prev, addition) {
        addition = addition.replace(/\s+([.,!?;:])/g, '$1');
        if (/^[.,!?;:]/.test(addition)) {
            prev = prev.replace(/\s+$/, '');
        } else if (/\s$/.test(prev)) {
            addition = addition.replace(/^\s+/, '');
        }
        return prev + addition;
    }

    function startRecording(el, btn) {
        var rec;
        try { rec = new Recognition(); }
        catch (e) { showToast('Voice unavailable: ' + e.message); return; }

        // continuous=false -> single-utterance mode. One engine round = one
        // phrase, which is reliable across Chrome versions (continuous mode
        // produced duplicated transcripts on Android). To give the user time
        // to think between phrases, `onend` auto-restarts the engine until
        // either the user taps stop or several rounds in a row come back
        // empty (MAX_CONSECUTIVE_SILENT below).
        rec.continuous      = false;
        rec.interimResults  = true;
        rec.lang            = navigator.language || 'en-US';
        rec.maxAlternatives = 1;

        var baseValue = el.value || '';
        if (baseValue && !/\s$/.test(baseValue)) baseValue += ' ';

        current = {
            el: el, btn: btn, recognition: rec,
            baseValue:        baseValue,
            userStopped:      false,
            committed:        false,  // ensure the final transcript is committed exactly once per round
            gotSpeech:        false,  // did this round capture any speech?
            consecutiveSilent: 0      // empty rounds in a row -- stop after MAX_CONSECUTIVE_SILENT
        };

        rec.onresult = function (ev) {
            // Single-utterance mode: the LAST result is the most current
            // version of what was spoken. Show it as interim, or commit it
            // once if final.
            var last = ev.results[ev.results.length - 1];
            if (!last) return;
            var t = capitalizeForContext(current.baseValue, last[0].transcript);
            if (last.isFinal) {
                if (!current.committed) {
                    current.committed  = true;
                    current.gotSpeech  = true;
                    current.baseValue  = joinSpeech(current.baseValue, t);
                    if (!/\s$/.test(current.baseValue)) current.baseValue += ' ';
                }
                el.value = current.baseValue;
            } else {
                current.gotSpeech = true;
                el.value = joinSpeech(current.baseValue, t);
            }
            el.dispatchEvent(new Event('input', { bubbles: true }));
            try {
                var len = el.value.length;
                el.setSelectionRange(len, len);
            } catch (e) {}
        };

        rec.onerror = function (ev) {
            if (ev.error === 'not-allowed' || ev.error === 'service-not-allowed') {
                showToast('Microphone permission denied.');
            } else if (ev.error === 'no-speech' || ev.error === 'aborted') {
                /* quiet -- onend cleans up */
            } else {
                showToast('Voice error: ' + ev.error);
            }
        };

        rec.onend = function () {
            // Round ended. If the user is still going, restart the engine so
            // they can keep thinking and speaking without re-tapping the mic.
            // Give up after a few silent rounds so a forgotten-on mic doesn't
            // stay hot indefinitely.
            if (!current || current.userStopped) { cleanup(); return; }
            if (current.gotSpeech) {
                current.consecutiveSilent = 0;
            } else {
                current.consecutiveSilent++;
                if (current.consecutiveSilent >= MAX_CONSECUTIVE_SILENT) { cleanup(); return; }
            }
            current.committed = false;
            current.gotSpeech = false;
            var session = current;
            setTimeout(function () {
                if (!current || current !== session || current.userStopped) return;
                try { rec.start(); }
                catch (e) { cleanup(); }
            }, 50);
        };

        try {
            rec.start();
            btn.classList.add('recording');
            el.focus();
            try {
                var len = el.value.length;
                el.setSelectionRange(len, len);
            } catch (e) {}
        } catch (e) {
            showToast('Could not start dictation: ' + e.message);
            cleanup();
        }
    }

    function stopRecording() {
        if (!current) return;
        current.userStopped = true;
        try { current.recognition.stop(); } catch (e) {}
    }

    function cleanup() {
        if (!current) return;
        current.btn.classList.remove('recording');
        current = null;
    }

    function showToast(msg) {
        if (window.Picasso && typeof Picasso.toast === 'function') Picasso.toast(msg);
        else console.warn('PicassoVoice: ' + msg);
    }

    // ---- Header toggle (placement variants) ----------------------------------

    function makeToggleButton() {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'voice-toggle-btn';
        btn.innerHTML = '&#127908;';
        btn.setAttribute('aria-label', 'Toggle voice dictation icons');
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            setState(state === 'on' ? 'off' : 'on');
        });
        return btn;
    }

    // Each placer returns true on success, false if the page lacks the host.
    function placeInHeader(btn) {
        var host = document.querySelector('.picasso-header, [class*="-header"], header');
        if (!host) return false;
        btn.classList.add('voice-tp-header');
        host.appendChild(btn);
        return true;
    }
    function placeFloatingBR(btn) {
        btn.classList.add('voice-tp-floating');
        document.body.appendChild(btn);
        return true;
    }
    function placeInNav(btn) {
        var nav = document.querySelector('.lw-nav');
        if (!nav) return false;
        btn.classList.add('voice-tp-nav');
        nav.appendChild(btn);
        return true;
    }
    function placeCornerDot(btn) {
        btn.classList.add('voice-tp-corner');
        document.body.appendChild(btn);
        return true;
    }
    function placeInFooter(btn) {
        // Append directly to body, NOT into the actual status bar element.
        // Most pages update their status bar via .textContent / .innerHTML
        // ("Loading..." -> "5 receipts"), which would wipe the toggle out of
        // the DOM. Body-attached + position:fixed guarantees the toggle
        // stays put regardless of what page JS does, and avoids stacking-
        // context problems with modal backdrops.
        btn.classList.add('voice-tp-footer');
        document.body.appendChild(btn);
        return true;
    }

    var PLACERS = {
        footer:   placeInFooter,
        header:   placeInHeader,
        floating: placeFloatingBR,
        nav:      placeInNav,
        corner:   placeCornerDot
    };

    function injectToggle() {
        var btn = makeToggleButton();
        var pick = getPlacement();
        var placer = PLACERS[pick] || PLACERS[DEFAULT_PLACEMENT];
        if (!placer(btn)) {
            // Page is missing the host this placement needs -- fall back to floating.
            placeFloatingBR(btn);
        }
        toggleBtn = btn;
        updateToggleAppearance();
    }

    function updateToggleAppearance() {
        if (!toggleBtn) return;
        var on = state === 'on';
        toggleBtn.title = on ? 'Voice dictation: ON  (tap to hide field mics)'
                             : 'Voice dictation: OFF (tap to show field mics)';
        toggleBtn.classList.toggle('voice-toggle-on', on);
        toggleBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
    }

    function setState(newState) {
        state = newState;
        // No persistence -- every page visit starts OFF, by design.
        updateToggleAppearance();
        setMicsVisible(state === 'on');
        if (state === 'on') attachAllNow();
        else if (current)   stopRecording();
    }

    // ---- DOM observer for dynamically-added inputs ---------------------------

    function startObserver() {
        var obs = new MutationObserver(function (mutations) {
            for (var m = 0; m < mutations.length; m++) {
                var added = mutations[m].addedNodes;
                for (var n = 0; n < added.length; n++) {
                    var node = added[n];
                    if (!node || node.nodeType !== 1) continue;
                    if (isEligible(node)) attachMic(node);
                    if (node.querySelectorAll) {
                        var nested = eligibleInputsIn(node);
                        for (var k = 0; k < nested.length; k++) attachMic(nested[k]);
                    }
                }
            }
        });
        obs.observe(document.body, { childList: true, subtree: true });
    }

    // ---- Modal-open detection (so the toggle reaches above bottom sheets) ----

    function startModalWatcher() {
        function anyModalVisible() {
            var modals = document.querySelectorAll('.modal-backdrop, .ed-modal-bg');
            for (var i = 0; i < modals.length; i++) {
                var m = modals[i];
                // Don't use offsetParent -- it's always null for position:fixed
                // elements (which all our modal backdrops are), so the prior
                // check missed every modal.
                if (m.style.display === 'none') continue;
                var cs = window.getComputedStyle(m);
                if (cs.display !== 'none' && cs.visibility !== 'hidden') return true;
            }
            return false;
        }
        function check() {
            document.body.classList.toggle('has-voice-modal-open', anyModalVisible());
        }
        var obs = new MutationObserver(check);
        obs.observe(document.body, {
            childList: true, subtree: true,
            attributes: true, attributeFilter: ['style', 'class']
        });
        check();
    }

    // ---- Boot ----------------------------------------------------------------

    function boot() {
        state = 'off';   // every visit starts hidden -- Tony must tap the toggle.
        attachAllNow();
        setMicsVisible(false);
        startObserver();
        startModalWatcher();
        // Always inject the toggle. Entity-detail pages (contact.html /
        // vendor.html / list.html) render their forms lazily, so a boot-time
        // "any inputs?" check would hide the toggle forever on pages where
        // a tab/modal will later add fields. The new subtle styling makes
        // an idle toggle on a field-less page unobtrusive.
        injectToggle();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }

    window.PicassoVoice = {
        getState:    function () { return state; },
        setState:    setState,
        getPlacement: getPlacement,
        attachAll:   attachAllNow
    };

})();
