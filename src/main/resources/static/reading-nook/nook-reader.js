/* Reading Nook "Listen" -- read any text document aloud, podcast style.
 *
 * One job: given a nook file, fetch its readable text, turn it into an ordered
 * list of passages, and read them with the device's own speech voices. When the
 * text looks like a transcript ("Name: said this") -- or is a Think Tank
 * transcript HTML -- each speaker gets a distinct voice; otherwise it reads in a
 * single narrator voice.
 *
 * It never starts on its own: NookReader.play() loads the text and REVEALS the
 * transport bar (hidden until then). The user presses Play to begin.
 *
 * Resilience (added for phone use): it KEEPS YOUR PLACE. The current passage is
 * remembered per file, so a phone call / screensaver / lock means a one-tap
 * resume, never a restart. While playing it holds a screen Wake Lock so the
 * screensaver does not interrupt, and it auto-resumes from your spot when you
 * return to the page. A seek bar shows and lets you jump to any passage.
 *
 * Two engines (Stories 5599 / 5600). Each page that uses Listen is a "place"
 * with its own voice setting on Admin -> Voice Settings:
 *   - browser (the default, what it always was): the device's speechSynthesis.
 *     It cannot play while the screen is off or the tab is in the background.
 *   - google: real MP3 audio from Google Cloud natural voices via picasso-tts.js.
 *     It keeps playing with the screen locked, and the lock screen shows
 *     Play / Pause / Skip. If Google cannot answer (no key, the month's cap,
 *     no network) the player says so and carries on in the browser voice.
 * All speaking goes through speakEntry(); the playlist, speaker map and
 * controls are shared by both engines.
 *
 * The saved spot lives in the database (PicassoViewState, key
 * leo.listen.<hash>.pos), so it follows Tony between the phone and the desk.
 */
(function () {
    "use strict";

    var synth = window.speechSynthesis || null;

    var bar = null;
    var playlist = [];
    var voicePool = [];
    var speakerVoice = {};          // speaker -> {voice, pitch, rate} (browser) or {gvoice} (google)
    var masterRate = 1;

    var cur = -1, playing = false, paused = false, chunks = [], chunkAt = 0;

    var mKey = null;                // this document's identity; its saved spot is stored under posKey(mKey)
    var wakeLock = null;            // screen wake-lock sentinel while playing
    var gen = 0;                    // bumped on every (re)start so stale utterance callbacks are ignored

    // ---- engine (Story 5600) ----
    var engine = "browser";         // "browser" | "google" -- from this place's Voice Settings row
    var gVoice = null;              // the place's Google voice (Narrator and single-voice text)
    var gPool = [];                 // other Google voices, for the other speakers in a transcript
    var audio = null;               // the one <audio> element Google playback uses
    var surfaceKey = surfaceFromPage();

    // Which place this page is, for Voice Settings. A page can override with NookReader.setSurface().
    function surfaceFromPage() {
        var p = (window.location && window.location.pathname) || "";
        if (p.indexOf("/thinker/") === 0) return "thinker";
        if (p.indexOf("/writing/") === 0) return "writingFilingCabinet";
        if (p.indexOf("/debates/") === 0) return "debates";
        if (p.indexOf("/personas/") === 0) return "personas";
        if (p.indexOf("/reader/") === 0) return "reader";
        if (p.indexOf("/entity-manager-iv/") === 0 || p.indexOf("/entity-manager-v/") === 0) return "entityManager";
        return "readingNook";
    }

    function esc(s) { return String(s == null ? "" : s).replace(/[&<>]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]; }); }
    function hash(s) { var h = 5381; s = String(s); for (var i = 0; i < s.length; i++) { h = ((h << 5) + h + s.charCodeAt(i)) | 0; } return Math.abs(h); }
    function toast(msg) { if (window.Picasso && Picasso.toast) Picasso.toast(msg); }

    // ---- saved place (the "spot") -------------------------------------------

    // Kept in the database through PicassoViewState (Tony's rule: nothing remembered in
    // localStorage). A spot saved the old way, in localStorage under the bare key, is read
    // once, moved across, and removed.
    function vs() { return window.PicassoViewState || null; }
    function posPrefix(k) { return "leo.listen." + hash(k) + "."; }
    function posKey(k) { return posPrefix(k) + "pos"; }
    function savePos() { if (mKey && cur >= 0 && vs()) vs().set(posKey(mKey), String(cur)); }
    function loadPos() {
        if (!mKey) return NaN;
        var v = vs() ? vs().get(posKey(mKey)) : null;
        if (v === null || v === "") {
            var legacy = null;
            try { legacy = localStorage.getItem(mKey); } catch (e) { }
            if (legacy !== null) {
                if (vs()) vs().set(posKey(mKey), legacy);
                try { localStorage.removeItem(mKey); } catch (e) { }
                v = legacy;
            }
        }
        return parseInt(v, 10);
    }
    function clearPos() { if (mKey && vs()) vs().set(posKey(mKey), ""); }

    // ---- screen wake lock (stop the screensaver mid-listen) -----------------

    function requestWakeLock() {
        if (!navigator.wakeLock || wakeLock) return;
        navigator.wakeLock.request("screen").then(function (wl) {
            wakeLock = wl;
            wakeLock.addEventListener("release", function () { wakeLock = null; });
        }).catch(function () { /* not granted -- listening still works */ });
    }
    function releaseWakeLock() {
        if (wakeLock) { try { wakeLock.release(); } catch (e) { } wakeLock = null; }
    }

    // ---- text -> playlist ---------------------------------------------------

    function cleanForSpeech(t) {
        return String(t)
            .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")     // images
            .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")    // links -> link text
            .replace(/[*_`~]+/g, " ")                   // emphasis / code marks
            .replace(/^\s*#{1,6}\s*/, "")               // heading hashes
            .replace(/^\s*>\s*/, "")                    // blockquote
            .replace(/^\s*[-*+]\s+/, "")                // bullet
            .replace(/^\s*\d+\.\s+/, "")                // numbered list
            .replace(/<[^>]+>/g, " ")                   // stray html tags
            .replace(/\s+/g, " ").trim();
    }

    var SPEAKER_RE = /^\s*(?:#{1,6}\s*)?\**([A-Z][A-Za-z0-9 .,'&\/()-]{0,38}?)\**\s*:\s+(\S.*)$/;

    function buildPlaylist(text) {
        var out = [];
        var lines = String(text || "").replace(/\r\n?/g, "\n").split("\n");
        lines.forEach(function (line) {
            if (!line.trim()) return;
            if (/^```/.test(line.trim())) return;
            var m = line.match(SPEAKER_RE);
            if (m && m[1].trim().split(/\s+/).length <= 4) {
                var spoken = cleanForSpeech(m[2]);
                if (spoken) out.push({ speaker: m[1].trim(), text: spoken });
            } else {
                var n = cleanForSpeech(line);
                if (n) out.push({ speaker: "Narrator", text: n });
            }
        });
        return out;
    }

    // A Think Tank transcript carries its turns in a JSON island (id=tt-data).
    function buildFromTranscript(data) {
        var out = [];
        (data.rounds || []).forEach(function (rd) {
            (rd.turns || []).forEach(function (t) {
                var speaker = t.speaker || (t.role === "synth" ? "Hermes" : (t.role === "conductor" ? "Felix" : "Speaker"));
                var text = t.text;
                if (!text && t.role === "synth") {
                    text = [
                        (t.agreements && t.agreements.length) ? ("Agreements. " + t.agreements.join(". ")) : "",
                        (t.openConflicts && t.openConflicts.length) ? ("Open conflicts. " + t.openConflicts.join(". ")) : "",
                        (t.cruxes && t.cruxes.length) ? ("Cruxes. " + t.cruxes.join(". ")) : ""
                    ].filter(Boolean).join(" ");
                }
                text = cleanForSpeech(text || "");
                if (text) out.push({ speaker: speaker, text: text });
            });
        });
        var r = data.ruling;
        if (r) {
            var by = r.by || data.arbiter || "Felix";
            if (r.spoken) { var s = cleanForSpeech(r.spoken); if (s) out.push({ speaker: by, text: s }); }
            if (r.decision) out.push({ speaker: by, text: "The decision. " + cleanForSpeech(r.decision) });
            if (r.dissent) out.push({ speaker: by, text: "Strongest surviving dissent. " + cleanForSpeech(r.dissent) });
        }
        return out;
    }

    function stripHtml(s) {
        return String(s)
            .replace(/<script[\s\S]*?<\/script>/gi, " ")
            .replace(/<style[\s\S]*?<\/style>/gi, " ")
            .replace(/<[^>]+>/g, " ")
            .replace(/&nbsp;/gi, " ").replace(/&middot;/gi, " ").replace(/&mdash;/gi, " - ")
            .replace(/&amp;/gi, "&").replace(/&lt;/gi, "<").replace(/&gt;/gi, ">")
            .replace(/\s+/g, " ").trim();
    }
    function buildNarrator(text) {
        return chunkText(text).map(function (c) { return { speaker: "Narrator", text: c }; });
    }

    function toPlaylist(raw) {
        var text = String(raw || "").replace(/<!--[\s\S]*?-->/g, " ");
        var island =
            text.match(/<script[^>]*\bid=["']tt-data["'][^>]*\btype=["']application\/json["'][^>]*>([\s\S]*?)<\/script>/i) ||
            text.match(/<script[^>]*\btype=["']application\/json["'][^>]*\bid=["']tt-data["'][^>]*>([\s\S]*?)<\/script>/i);
        if (island) {
            try { var pl = buildFromTranscript(JSON.parse(island[1])); if (pl.length) return pl; } catch (e) { }
        }
        if (/<(html|body|head|div|p|span|script)\b/i.test(text)) return buildNarrator(stripHtml(text));
        return buildPlaylist(text);
    }

    // ---- voices -------------------------------------------------------------

    // Tony is a native American-English speaker and finds heavy accents hard to
    // follow, so the player favors clearly-articulate English voices. These are
    // the locales an American ear parses most easily, best first; English voices
    // in any other locale (en-IN, en-ZA, en-NG, en-PH, en-HK, en-SG, ...) are
    // treated as "heavily accented" and used only if nothing clearer is installed.
    var CLEAR_LANGS = ["en-us", "en-gb", "en-ca", "en-ie", "en-au", "en-nz"];

    // Modern "natural / neural / online" voices articulate far better than the
    // legacy robotic ones, so they win ties within the same locale.
    var GOOD_VOICE_RE = /natural|neural|online|google|premium|enhanced|wavenet|siri/i;

    function langRank(v) {
        var lang = String((v && v.lang) || "").toLowerCase().replace(/_/g, "-");
        var i = CLEAR_LANGS.indexOf(lang);
        return i === -1 ? 99 : i;          // 99 = not a clear-accent locale
    }
    function voiceScore(v) {
        // Locale dominates; voice quality only breaks ties inside a locale.
        return langRank(v) * 2 + (GOOD_VOICE_RE.test((v && v.name) || "") ? 0 : 1);
    }

    function loadVoices() {
        var all = (synth.getVoices && synth.getVoices()) || [];
        var en = all.filter(function (v) { return /^en/i.test(v.lang); });
        var base = en.length ? en : all;
        // Keep only the clearly-articulate accents when any are installed; fall
        // back to the full English set (then to everything) only if none are.
        var clear = base.filter(function (v) { return langRank(v) < 99; });
        var pool = (clear.length ? clear : base).slice();
        pool.sort(function (a, b) { return voiceScore(a) - voiceScore(b); });
        voicePool = pool;
    }
    function uniqueSpeakers() {
        var seen = {}, order = [];
        playlist.forEach(function (e) { if (!seen[e.speaker]) { seen[e.speaker] = 1; order.push(e.speaker); } });
        return order;
    }
    function assignVoices() {
        if (engine === "google") { assignGoogleVoices(); return; }
        if (!voicePool.length) return;
        var def = voicePool.filter(function (v) { return v.default; })[0] || voicePool[0];
        uniqueSpeakers().forEach(function (name) {
            if (name === "Narrator") { speakerVoice[name] = { voice: def, pitch: 1.0, rate: 1.0 }; return; }
            var hv = hash(name);
            speakerVoice[name] = {
                voice: voicePool[hv % voicePool.length],
                pitch: 0.85 + (hv % 7) * 0.05,
                rate: 0.92 + (hv % 4) * 0.045
            };
        });
    }
    // Google: the Narrator (and any single-voice text) gets the place's voice; every other speaker
    // in a transcript gets a voice of their OWN -- no two speakers share one while the pool lasts
    // (Tony, 2026-10-06: "each a unique voice"). A fixed voice deals the others in a steady order
    // so a debate sounds the same each time; a random choice deals them shuffled, once per reading.
    function assignGoogleVoices() {
        var used = {}, k = 0;
        if (gVoice) used[gVoice] = 1;
        uniqueSpeakers().forEach(function (name) {
            if (name === "Narrator" || !gPool.length) { speakerVoice[name] = { gvoice: gVoice }; return; }
            var pick = null;
            for (var i = 0; i < gPool.length && !pick; i++) {
                var cand = gPool[(k + i) % gPool.length];
                if (!used[cand]) pick = cand;
            }
            if (!pick) pick = gPool[k % gPool.length];     // more speakers than voices: reuse in turn
            used[pick] = 1;
            k++;
            speakerVoice[name] = { gvoice: pick };
        });
    }

    function shuffle(a) {
        for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = a[i]; a[i] = a[j]; a[j] = t; }
        return a;
    }
    function refreshVoices() { if (engine === "browser") loadVoices(); assignVoices(); }

    // Reads this place's voice setting (and, for Google, the voice list) before playback starts.
    function loadEngine() {
        if (!window.PicassoTts) { engine = "browser"; return Promise.resolve(); }
        return window.PicassoTts.surface(surfaceKey).then(function (sv) {
            engine = sv.engine;
            if (engine !== "google") return;
            var choice = sv.voiceName;
            var random = window.PicassoTts.isRandom(choice);
            // Roll a random choice ONCE here, at the start of the reading; it holds until Close.
            return Promise.all([window.PicassoTts.pickVoice(choice), window.PicassoTts.poolFor(choice)]).then(function (got) {
                gVoice = got[0];
                var names = got[1].map(function (v) { return v.name; }).filter(function (n) { return n !== gVoice; });
                gPool = random ? shuffle(names) : names.slice(hash(String(gVoice)) % Math.max(1, names.length)).concat(
                    names.slice(0, hash(String(gVoice)) % Math.max(1, names.length)));
            });
        }, function () { engine = "browser"; });
    }

    // Google could not answer: say why once, and carry on in the device's own voice.
    function fallBackToBrowser(why) {
        toast((why || "The Google voice is not available.") + (synth ? " Using the device voice." : ""));
        engine = "browser";
        if (audio) { try { audio.pause(); } catch (e) { } }
        refreshVoices();
        if (!synth) { playing = false; paused = false; releaseWakeLock(); setBtns(); return false; }
        return true;
    }

    function stopSound() {
        if (synth) { try { synth.cancel(); } catch (e) { } }
        if (audio) { try { audio.pause(); } catch (e) { } audio.__lwEntry = -1; }
    }

    function ensureAudio() {
        if (!audio) { audio = new Audio(); audio.preload = "auto"; }
        return audio;
    }

    // The lock screen / notification shade shows the title and Play / Pause / Skip.
    function setMediaSession() {
        if (engine !== "google" || !("mediaSession" in navigator)) return;
        try {
            var title = (bar && bar.querySelector("#nrTitle").textContent) || "Listening";
            navigator.mediaSession.metadata = new window.MediaMetadata({ title: title, artist: "Leonardo" });
            navigator.mediaSession.setActionHandler("play", function () { play(); });
            navigator.mediaSession.setActionHandler("pause", function () { pauseIt(); });
            navigator.mediaSession.setActionHandler("nexttrack", function () { jumpTo((cur < 0 ? 0 : cur) + 1); });
            navigator.mediaSession.setActionHandler("previoustrack", function () { jumpTo((cur < 0 ? 0 : cur) - 1); });
        } catch (e) { /* older browsers: the on-page controls still work */ }
    }

    // ---- transport bar ------------------------------------------------------

    function ensureBar() {
        if (bar) return bar;
        bar = document.createElement("div");
        bar.id = "nook-reader-bar";
        bar.setAttribute("data-testid", "nook-reader-bar");
        bar.innerHTML =
            "<div class='nr-row'>" +
                "<span class='nr-title' id='nrTitle' data-testid='nook-reader-title'></span>" +
                "<button class='nr-close' id='nrClose' data-testid='nook-reader-close'>Close</button>" +
            "</div>" +
            "<div class='nr-row'>" +
                "<input class='nr-seek' id='nrSeek' type='range' min='0' max='0' value='0' step='1' data-testid='nook-reader-seek' aria-label='Position'>" +
            "</div>" +
            "<div class='nr-row'>" +
                "<button class='nr-primary' id='nrToggle' data-testid='nook-reader-toggle'>Play</button>" +
                "<button id='nrBack' data-testid='nook-reader-back'>Back</button>" +
                "<button id='nrSkip' data-testid='nook-reader-skip'>Skip</button>" +
                "<span class='nr-speed'>Speed<input id='nrRate' type='range' min='0.6' max='1.4' step='0.05' value='1' data-testid='nook-reader-speed'></span>" +
            "</div>" +
            "<div class='nr-now' id='nrNow' data-testid='nook-reader-now'></div>";
        document.body.appendChild(bar);

        bar.querySelector("#nrToggle").addEventListener("click", toggle);
        bar.querySelector("#nrBack").addEventListener("click", function () { jumpTo((cur < 0 ? 0 : cur) - 1); });
        bar.querySelector("#nrSkip").addEventListener("click", function () { jumpTo((cur < 0 ? 0 : cur) + 1); });
        bar.querySelector("#nrClose").addEventListener("click", closeBar);
        bar.querySelector("#nrRate").addEventListener("input", function () {
            masterRate = parseFloat(this.value) || 1;
            if (audio) audio.playbackRate = masterRate;
        });
        // Seek bar: preview the target while dragging, commit on release.
        var seek = bar.querySelector("#nrSeek");
        seek.addEventListener("input", function () { previewSeek(parseInt(this.value, 10)); });
        seek.addEventListener("change", function () { jumpTo(parseInt(this.value, 10)); });

        window.addEventListener("beforeunload", function () { savePos(); stopSound(); });
        window.addEventListener("pagehide", freezeForBackground);  // phone may discard the page on lock; save first
        document.addEventListener("visibilitychange", onVisibilityChange);
        return bar;
    }

    function setBtns() {
        var t = bar.querySelector("#nrToggle");
        t.textContent = playing ? "Pause" : ((paused || cur >= 0) ? "Resume" : "Play");
    }

    function syncUi() {
        var seek = bar.querySelector("#nrSeek");
        if (seek) { seek.max = String(Math.max(0, playlist.length - 1)); seek.value = String(cur < 0 ? 0 : cur); }
        var el = bar.querySelector("#nrNow");
        if (cur >= 0 && playlist[cur]) {
            el.innerHTML = "Reading <b>" + esc(playlist[cur].speaker) + "</b> &middot; " + (cur + 1) + " of " + playlist.length;
        } else {
            el.innerHTML = playlist.length + " passage" + (playlist.length === 1 ? "" : "s") + " &middot; press Play to listen";
        }
    }

    // Show where the slider points without seeking yet (drag preview).
    function previewSeek(i) {
        var el = bar.querySelector("#nrNow");
        var who = playlist[i] ? playlist[i].speaker : "";
        el.innerHTML = "Go to <b>" + esc(who) + "</b> &middot; " + (i + 1) + " of " + playlist.length;
    }

    // ---- playback engine ----------------------------------------------------

    function chunkText(t) {
        var parts = String(t == null ? "" : t).replace(/\s+/g, " ").trim().match(/[^.!?]+[.!?]*\s*/g) || [String(t)];
        var out = [], buf = "";
        parts.forEach(function (p) {
            if ((buf + p).length > 160) { if (buf) out.push(buf.trim()); buf = p; }
            else { buf += p; }
        });
        if (buf.trim()) out.push(buf.trim());
        return out.length ? out : [String(t)];
    }

    function speakEntry(i) {
        cur = i; savePos(); syncUi();
        if (engine === "google") { playGoogle(i, ++gen); return; }
        chunks = chunkText(playlist[i].text); chunkAt = 0;
        speakChunk(++gen);
    }

    function googleAudioFor(i) {
        var e = playlist[i];
        var sv = speakerVoice[e.speaker] || {};
        return window.PicassoTts.fetchAudio(surfaceKey, e.text, sv.gvoice || gVoice);
    }

    // Fetch the next two passages while this one plays, so there is no gap between them.
    function prefetch(i) {
        for (var k = i + 1; k <= i + 2 && k < playlist.length; k++) googleAudioFor(k);
    }

    function playGoogle(i, g) {
        googleAudioFor(i).then(function (res) {
            if (!playing || g !== gen) return;
            if (!res.ok) { if (fallBackToBrowser(res.message)) speakEntry(i); return; }
            var a = ensureAudio();
            a.onended = function () { if (!playing || g !== gen) return; onEntryDone(); };
            a.onerror = function () { if (!playing || g !== gen) return; if (fallBackToBrowser("The Google audio would not play.")) speakEntry(i); };
            a.src = res.url;
            a.__lwEntry = i;
            a.playbackRate = masterRate;
            var p = a.play();
            if (p && p.catch) p.catch(function () { if (playing && g === gen && fallBackToBrowser("The Google audio would not play.")) speakEntry(i); });
            prefetch(i);
        });
    }

    function speakChunk(g) {
        if (!playing || g !== gen) return;     // a newer start (or stop) supersedes this chain
        if (chunkAt >= chunks.length) { onEntryDone(); return; }
        var e = playlist[cur];
        var sv = speakerVoice[e.speaker] || {};
        var u = new SpeechSynthesisUtterance(chunks[chunkAt]);
        if (sv.voice) u.voice = sv.voice;
        u.lang = (sv.voice && sv.voice.lang) || navigator.language || "en-US";
        u.pitch = sv.pitch || 1;
        u.rate = (sv.rate || 1) * masterRate;
        u.onend = function () { if (!playing || g !== gen) return; chunkAt++; speakChunk(g); };
        u.onerror = function () { if (!playing || g !== gen) return; chunkAt++; speakChunk(g); };
        synth.speak(u);
    }

    function onEntryDone() {
        if (!playing) return;
        if (cur + 1 < playlist.length) { speakEntry(cur + 1); }
        else { playing = false; paused = false; cur = -1; clearPos(); releaseWakeLock(); setBtns(); syncUi(); }
    }

    function play() {
        if (engine === "google") {
            // Inside the tap: let the phone start audio that will only arrive after a fetch.
            var a = ensureAudio();
            if (window.PicassoTts && !paused) window.PicassoTts.unlock(a);
            requestWakeLock();
            if (paused) {
                paused = false; playing = true; setBtns();
                // Resume the passage in the player if it is this one; a pause that landed while
                // the passage was still downloading left nothing to resume, so fetch it again.
                if (a.__lwEntry === cur && !a.ended) { var p = a.play(); if (p && p.catch) p.catch(function () { }); }
                else speakEntry(cur < 0 ? 0 : cur);
                return;
            }
            if (playing) return;
            playing = true; paused = false; setBtns(); setMediaSession();
            if (cur < 0) cur = 0;
            speakEntry(cur);
            return;
        }
        if (!synth) return;
        requestWakeLock();
        if (paused) { synth.resume(); paused = false; playing = true; setBtns(); return; }
        if (playing) return;
        playing = true; paused = false; setBtns();
        if (cur < 0) cur = 0;
        speakEntry(cur);
    }
    function pauseIt() {
        if (!playing) return;
        if (engine === "google") { if (audio) audio.pause(); }
        else synth.pause();
        paused = true; playing = false; savePos(); releaseWakeLock(); setBtns();
    }
    function toggle() { if (playing) pauseIt(); else play(); }
    function closeBar() {
        savePos();
        playing = false; paused = false; gen++;
        stopSound();
        releaseWakeLock();
        if (bar) bar.classList.remove("open");
    }
    function jumpTo(i) {
        if (!playlist.length) return;
        var live = playing || paused;
        stopSound();
        cur = Math.max(0, Math.min(playlist.length - 1, i));
        savePos(); syncUi();
        if (live) { playing = true; paused = false; setBtns(); speakEntry(cur); }
        else { setBtns(); }
    }

    // When the page is hidden (call, screensaver, app switch) the browser kills
    // speech -- and the cancelled utterance's end-events would otherwise race
    // the playlist to the end and WIPE the saved spot. So FREEZE here: stop the
    // chain, bump the generation so no stale callback can advance, and save the
    // exact passage. The player is left ready to resume from it. We do NOT
    // auto-restart speech on return (mobile browsers only speak after a tap), so
    // the user taps Play and it continues from the saved passage.
    // Google audio is real audio: it keeps playing behind a locked screen, so it is not frozen.
    function freezeForBackground() {
        if (!playing && !paused) return;
        if (engine === "google") { savePos(); return; }
        playing = false; paused = false; gen++;
        savePos();
        try { synth.cancel(); } catch (e) { }
        releaseWakeLock();
        if (bar) setBtns();
    }
    function onVisibilityChange() {
        if (document.hidden) freezeForBackground();
        else if (bar) { setBtns(); syncUi(); }
    }

    // ---- entry point --------------------------------------------------------

    // Core: fetch raw text from any URL, build the playlist, reveal the bar.
    // posKey is the localStorage key under which this document's spot is kept,
    // so two different sources never clobber each other's resume point.
    function begin(url, displayName, posKey) {
        if (!window.LWAuth || !window.LWAuth.apiGetText) { toast("Cannot read this file right now."); return; }
        if (window.Picasso && Picasso.busy) Picasso.busy("Preparing audio");
        window.LWAuth.apiGetText(url).then(function (r) {
            if (window.Picasso && Picasso.idle) Picasso.idle();
            if (!r || !r.ok || !r.data) { toast(window.LWAuth.errorText(r, "Could not load that file to read.")); return; }
            beginWithText(r.data, displayName, posKey);
        }, function () {
            if (window.Picasso && Picasso.idle) Picasso.idle();
            toast("Could not load that file to read.");
        });
    }

    // Build the player from text already in hand (no fetch). Used when the
    // caller already has the readable text on screen -- e.g. the Writing Filing
    // Cabinet reader, which fetches a work's extracted text as JSON and just
    // hands us the string to read aloud.
    // narrate = true reads the text as one continuous narration grouped into
    // sentence-sized passages, ignoring line breaks. Text extracted from a PDF
    // or Word file carries a hard break on every printed line, so reading it
    // line by line would make every line its own passage (Story 3544).
    function beginWithText(raw, displayName, posKey, narrate) {
        var list = narrate ? buildNarrator(raw) : toPlaylist(raw);
        if (!list.length) { toast("Nothing readable to read aloud."); return; }
        // Stop anything already playing before the new document takes over.
        playing = false; paused = false; gen++; stopSound();
        var spotSync = (vs() && posKey) ? vs().sync(posPrefix(posKey)).catch(function () { }) : Promise.resolve();
        Promise.all([loadEngine(), spotSync]).then(function () {
            if (engine === "browser" && !synth) { toast("This browser has no built-in speech voices."); return; }
            playlist = list;
            speakerVoice = {};
            ensureBar();
            mKey = posKey;
            // Restore the saved spot so an interrupted listen resumes, not restarts.
            var sp = loadPos();
            cur = (!isNaN(sp) && sp > 0 && sp < playlist.length) ? sp : -1;
            bar.querySelector("#nrTitle").textContent = displayName || "Listening";
            refreshVoices();
            setBtns(); syncUi();
            if (cur > 0) toast("Resuming where you left off");
            bar.classList.add("open");          // reveal controls -- silent until Play
            if (synth && typeof synth.onvoiceschanged !== "undefined") { synth.onvoiceschanged = refreshVoices; }
        });
    }

    // Documents whose text has to be pulled out on the server -- the same
    // extraction the Library's reader uses. Text-like files keep the raw path
    // so markdown speakers and Think Tank transcripts still get their voices.
    var EXTRACTED_RE = /\.(pdf|docx?)$/i;

    function startExtracted(name, v, displayName) {
        if (!window.LWAuth || !window.LWAuth.apiGet) { toast("Cannot read this file right now."); return; }
        var url = "/api/reading-nook/text?name=" + encodeURIComponent(name) + "&view=" + encodeURIComponent(v);
        if (window.Picasso && Picasso.busy) Picasso.busy("Preparing audio");
        window.LWAuth.apiGet(url).then(function (r) {
            if (window.Picasso && Picasso.idle) Picasso.idle();
            if (!r) return;
            var d = r.data || {};
            if (!r.ok) { toast(window.LWAuth.errorText(r, "Could not load that file to read.")); return; }
            if (!d.text) { toast(d.message || "Nothing readable to read aloud."); return; }
            beginWithText(d.text, displayName, "nookReaderPos:" + v + "/" + name, true);
        }, function () {
            if (window.Picasso && Picasso.idle) Picasso.idle();
            toast("Could not load that file to read.");
        });
    }

    // Read a Reading Nook file (the original entry point).
    function start(name, view, displayName) {
        var v = view || "inbox";
        if (EXTRACTED_RE.test(name || "")) { startExtracted(name, v, displayName || name); return; }
        var url = "/api/reading-nook/file?name=" + encodeURIComponent(name) + "&view=" + encodeURIComponent(v) + "&raw=true";
        begin(url, displayName || name, "nookReaderPos:" + v + "/" + name);
    }

    // Read any other text source by URL (e.g. a Leonardo File linked to a debate).
    function startUrl(url, displayName, posKey) {
        begin(url, displayName, posKey || ("urlReaderPos:" + url));
    }

    // Read text the caller already has loaded in memory (no HTTP fetch).
    // narrate = true reads it as one narration in sentence-sized passages (an email, whose
    // hard-wrapped lines and "Note:" lines must not become passages or speakers).
    function startText(text, displayName, posKey, narrate) {
        beginWithText(text, displayName, posKey || ("textReaderPos:" + (displayName || "")), !!narrate);
    }

    // A page whose address does not say which place it is can name it (see surfaceFromPage).
    function setSurface(key) { if (key) surfaceKey = key; }

    window.NookReader = { play: start, playUrl: startUrl, playText: startText, setSurface: setSurface };
})();
