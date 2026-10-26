/* PicassoTts -- the one browser-side client for Leonardo's voices (Stories 5599 / 5600).
 *
 * Every place that reads text aloud has a key (thinker, readingNook, dictionary, ...). Its voice
 * is chosen on Admin -> Voice Settings: the browser's own voice (the default -- what it always
 * used) or a Google Cloud natural voice. This file answers "what voice does this place use",
 * fetches Google audio through /api/tts/speak (the server saves each passage, so a replay is
 * free), and falls back to the browser's voice whenever Google cannot answer -- no key, the
 * month's cap reached, no network.
 *
 *   PicassoTts.surface(key)                   -> Promise {engine: 'browser'|'google', voiceName}
 *   PicassoTts.voices()                       -> Promise [{name, gender}]
 *   PicassoTts.pickVoice(choice)              -> Promise voice name ('random:female' etc. rolled once)
 *   PicassoTts.poolFor(choice)                -> Promise [{name, gender}] a choice draws from
 *   PicassoTts.fetchAudio(key, text, voice)   -> Promise {ok, url} | {ok:false, status, message}
 *   PicassoTts.unlock(audioElement)           -> call inside a tap, before any await
 *   PicassoTts.say(key, text, {rate, lang, onend}) -> {stop()}  one-shot (Dictionary, WordWise)
 *   PicassoTts.forget()                       -> re-read the settings (after Voice Settings saves)
 *   PicassoTts.prime()                        -> inside a tap, before an await that ends in say()
 *
 * The Listen player (reading-nook/nook-reader.js) uses the first four; one-shot speaker
 * buttons use say().
 */
(function () {
    'use strict';

    var settingsPromise = null;
    var voicesPromise = null;
    var urls = {};                 // voice + text -> object URL, this page only
    var urlOrder = [];
    var MAX_URLS = 60;
    var silent = null;
    var shared = null;
    var sayGen = 0;

    function api() { return window.LWAuth || null; }
    function toast(msg) { if (msg && window.Picasso && window.Picasso.toast) window.Picasso.toast(msg); }

    function outputSettings() {
        if (!settingsPromise) {
            var a = api();
            settingsPromise = !a ? Promise.resolve([]) : a.apiGet('/api/voice/settings/output').then(function (r) {
                return (r && r.ok && Array.isArray(r.data)) ? r.data : [];
            }, function () { return []; });
        }
        return settingsPromise;
    }

    function surface(key) {
        return outputSettings().then(function (list) {
            for (var i = 0; i < list.length; i++) {
                if (list[i].key === key) {
                    return { engine: list[i].engine === 'google' ? 'google' : 'browser', voiceName: list[i].voiceName || null };
                }
            }
            return { engine: 'browser', voiceName: null };
        });
    }

    // [{name, gender: 'MALE'|'FEMALE'}], sorted by name.
    function voices() {
        if (!voicesPromise) {
            var a = api();
            voicesPromise = !a ? Promise.resolve([]) : a.apiGet('/api/tts/voices').then(function (r) {
                var list = (r && r.ok && r.data && r.data.voices) || [];
                return list.map(function (v) { return { name: v.name, gender: String(v.gender || '').toUpperCase() }; });
            }, function () { return []; });
        }
        return voicesPromise;
    }

    // ---- random voices (Tony, 2026-10-06) ----
    // A place may be set to 'random:any', 'random:female' or 'random:male'. The actual voice is
    // picked ONCE per reading (or per sample) by pickVoice() and kept for that reading.
    function isRandom(choice) { return /^random:(any|female|male)$/.test(choice || ''); }
    function genderOf(choice) {
        return choice === 'random:female' ? 'FEMALE' : choice === 'random:male' ? 'MALE' : null;
    }

    // The voices a choice draws from: a random choice's gender (or all), else every voice.
    function poolFor(choice) {
        var want = genderOf(choice);
        return voices().then(function (list) {
            return list.filter(function (v) { return !want || v.gender === want; });
        });
    }

    // A concrete voice name for a choice: itself, or one drawn at random from its pool.
    function pickVoice(choice) {
        if (!isRandom(choice)) return Promise.resolve(choice || null);
        return poolFor(choice).then(function (pool) {
            return pool.length ? pool[Math.floor(Math.random() * pool.length)].name : null;
        });
    }

    function remember(k, url) {
        urls[k] = url;
        urlOrder.push(k);
        while (urlOrder.length > MAX_URLS) {
            var old = urlOrder.shift();
            if (urls[old]) { try { URL.revokeObjectURL(urls[old]); } catch (e) { } delete urls[old]; }
        }
    }

    function fetchAudio(key, text, voiceName) {
        var k = (voiceName || '') + '\n' + text;
        if (urls[k]) return Promise.resolve({ ok: true, url: urls[k] });
        var a = api();
        if (!a || !a.apiPostForBlob) return Promise.resolve({ ok: false, message: 'Cannot reach the voice service from this page.' });
        return a.apiPostForBlob('/api/tts/speak', { surface: key, text: text, voice: voiceName || null }).then(function (r) {
            if (!r) return { ok: false, message: 'Sign in again to use the Google voice.' };
            if (!r.ok || !r.blob) {
                var why = r.data && r.data.error ? r.data.error : a.errorText(r, 'The Google voice did not answer');
                return { ok: false, status: r.status, message: why };
            }
            var url = URL.createObjectURL(r.blob);
            remember(k, url);
            return { ok: true, url: url };
        }, function () {
            return { ok: false, message: 'The Google voice could not be reached.' };
        });
    }

    /* A tenth of a second of silence as a WAV, built here so no binary lives in source. Playing
     * it inside a tap is what lets a phone browser start real audio later, after a fetch. */
    function silentUrl() {
        if (silent) return silent;
        var n = 800, buf = new ArrayBuffer(44 + n), v = new DataView(buf), i;
        function str(o, s) { for (var j = 0; j < s.length; j++) v.setUint8(o + j, s.charCodeAt(j)); }
        str(0, 'RIFF'); v.setUint32(4, 36 + n, true); str(8, 'WAVE'); str(12, 'fmt ');
        v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
        v.setUint32(24, 8000, true); v.setUint32(28, 8000, true); v.setUint16(32, 1, true); v.setUint16(34, 8, true);
        str(36, 'data'); v.setUint32(40, n, true);
        for (i = 0; i < n; i++) v.setUint8(44 + i, 128);
        silent = URL.createObjectURL(new Blob([buf], { type: 'audio/wav' }));
        return silent;
    }

    function unlock(audio) {
        if (!audio || audio.__lwUnlocked) return;
        audio.__lwUnlocked = true;
        audio.onended = null;
        audio.onerror = null;
        try {
            audio.src = silentUrl();
            var p = audio.play();
            if (p && p.catch) p.catch(function () { audio.__lwUnlocked = false; });
        } catch (e) { audio.__lwUnlocked = false; }
    }

    function sharedAudio() {
        if (!shared) { shared = new Audio(); shared.preload = 'auto'; }
        return shared;
    }

    function say(key, text, opts) {
        opts = opts || {};
        var my = ++sayGen;
        var stopped = false;
        var audio = sharedAudio();
        var synth = window.speechSynthesis || null;
        unlock(audio);

        function live() { return !stopped && my === sayGen; }
        function done() { if (opts.onend) opts.onend(); }
        function browserVoice() {
            if (!live()) return;
            if (!synth) { done(); return; }
            var u = new SpeechSynthesisUtterance(text);
            u.lang = opts.lang || navigator.language || 'en-US';
            if (opts.rate) u.rate = opts.rate;
            u.onend = done;
            u.onerror = done;
            try { synth.cancel(); synth.speak(u); } catch (e) { done(); }
        }

        surface(key).then(function (sv) {
            if (!live()) return;
            if (sv.engine !== 'google') { browserVoice(); return; }
            pickVoice(sv.voiceName).then(function (voice) {
                if (!live()) return;
                if (opts.onvoice && voice) opts.onvoice(voice);
                return fetchAudio(key, text, voice);
            }).then(function (res) {
                if (!res) return;
                if (!live()) return;
                if (!res.ok) { toast(res.message); browserVoice(); return; }
                audio.onended = function () { if (live()) done(); };
                audio.onerror = function () { if (live()) browserVoice(); };
                audio.src = res.url;
                var p = audio.play();
                if (p && p.catch) p.catch(function () { if (live()) browserVoice(); });
            });
        });

        return {
            stop: function () {
                stopped = true;
                try { audio.pause(); } catch (e) { }
                try { if (synth) synth.cancel(); } catch (e) { }
            }
        };
    }

    /* Voice Settings changes a place's voice on the same page that then plays it: forget the
     * settings read so far so the next surface() asks the server again. */
    function forget() { settingsPromise = null; }

    /* Call inside a tap when audio will start only after an await (a save, a fetch), so a
     * phone still lets say() play it. */
    function prime() { unlock(sharedAudio()); }

    window.PicassoTts = {
        surface: surface,
        voices: voices,
        fetchAudio: fetchAudio,
        unlock: unlock,
        say: say,
        forget: forget,
        prime: prime,
        isRandom: isRandom,
        poolFor: poolFor,
        pickVoice: pickVoice
    };
})();
