(function () {
    'use strict';

    // Solutions (Story 3493) -- the helpers the list page, the solution page and the
    // API Clients page share. One job each; no page logic in here.

    var SL = {};

    SL.$ = function (id) { return document.getElementById(id); };

    SL.esc = function (s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };

    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    /** ISO timestamp -> 09/17/2026 (always two-digit month and day). */
    SL.date = function (iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        return pad(d.getMonth() + 1) + '/' + pad(d.getDate()) + '/' + d.getFullYear();
    };

    /** ISO timestamp -> 09/17/2026 2:05 PM */
    SL.dateTime = function (iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        var h = d.getHours(), ampm = h >= 12 ? 'PM' : 'AM';
        h = h % 12; if (h === 0) h = 12;
        return SL.date(iso) + ' ' + h + ':' + pad(d.getMinutes()) + ' ' + ampm;
    };

    /** What the user typed (09/17/2026, 9/17/26 or 2026-09-17) -> 2026-09-17, '' for blank, null when unreadable. */
    SL.toIsoDate = function (text) {
        var t = String(text == null ? '' : text).trim();
        if (!t) return '';
        var m = t.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
        if (m) return m[1] + '-' + pad(+m[2]) + '-' + pad(+m[3]);
        m = t.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/);
        if (!m) return null;
        var y = m[3].length === 2 ? '20' + m[3] : m[3];
        if (+m[1] < 1 || +m[1] > 12 || +m[2] < 1 || +m[2] > 31) return null;
        return y + '-' + pad(+m[1]) + '-' + pad(+m[2]);
    };

    SL.status = function (text) { var el = SL.$('statusBar'); if (el) el.textContent = text; };

    SL.toast = function (text) {
        if (window.Picasso && window.Picasso.toast) window.Picasso.toast(text);
        else SL.status(text);
    };

    /** The message a failed call should show: the server's own sentence when it sent one. */
    SL.failText = function (r, fallback) {
        // Story 5570: the whole story -- what, why, HTTP status, address -- via LWAuth.errorText.
        // The Solutions API answers {error: <code>, message: <the sentence>}; errorText reads error first,
        // so a refusal read "rule" instead of what was wrong. Hand it the sentence (2026-10-09).
        if (r && r.data && typeof r.data === 'object' && r.data.message && /^[a-z_]+$/.test(String(r.data.error || ''))) {
            var copy = {};
            for (var k in r) if (Object.prototype.hasOwnProperty.call(r, k)) copy[k] = r[k];
            copy.data = { error: r.data.message, errorId: r.data.errorId };
            r = copy;
        }
        if (window.LWAuth && window.LWAuth.errorText) return window.LWAuth.errorText(r, fallback);
        if (r && r.data) {
            if (r.data.message) return r.data.message;
            if (r.data.error && r.data.nextAction) return r.data.error + ' ' + r.data.nextAction;
            if (typeof r.data.error === 'string' && r.data.error.indexOf(' ') > 0) return r.data.error;
        }
        return fallback || 'That did not work.';
    };

    SL.get  = function (url)       { return window.LWAuth.apiGet(url); };
    SL.post = function (url, body) { return window.LWAuth.apiPost(url, body == null ? {} : body, 'POST'); };
    SL.put  = function (url, body) { return window.LWAuth.apiPost(url, body == null ? {} : body, 'PUT'); };
    SL.del  = function (url)       { return window.LWAuth.apiPost(url, null, 'DELETE'); };

    // ---- modals -----------------------------------------------------------

    SL.open  = function (id) { var o = SL.$(id); if (o) o.classList.add('open'); };
    SL.close = function (id) { var o = SL.$(id); if (o) o.classList.remove('open'); };
    SL.warn  = function (id, text) {
        var w = SL.$(id);
        if (!w) return;
        w.textContent = text || '';
        w.classList.toggle('show', !!text);
    };

    /** Disable a Save button while its call is in flight, so a slow phone cannot save twice. */
    SL.guard = async function (button, work) {
        if (button.disabled) return;
        button.disabled = true;
        try { await work(); } finally { button.disabled = false; }
    };

    // ---- selects (auto-enhanced by picasso-select.js) ----------------------
    //
    // Never call PicassoSelect.refresh() from here. The enhancer already repaints itself when a
    // select's options change (MutationObserver) or its value is set from script (it intercepts
    // the setter), and refresh() also DISPATCHES a change event -- so a change handler that
    // refills its own select recurses until the stack is gone. Found by the Story 3493 probe.

    /** options: [{value, label}] ; first is an optional {value:'', label:'...'} blank. */
    SL.fillSelect = function (sel, options, value) {
        if (!sel) return;
        sel.innerHTML = options.map(function (o) {
            return '<option value="' + SL.esc(o.value) + '">' + SL.esc(o.label) + '</option>';
        }).join('');
        // A blank first option ("All projects") is what the enhanced dropdown should show when nothing is picked.
        if (options.length && options[0].value === '') sel.setAttribute('data-ps-placeholder', options[0].label);
        sel.value = value == null ? '' : String(value);
    };

    SL.setSelect = function (sel, value) {
        if (!sel) return;
        sel.value = value == null ? '' : String(value);
    };

    // ---- expand / collapse that remembers what the user chose ---------------------
    //
    // One store per solution, kept on the device. Every collapsible thing on the page -- a
    // project band, an item, a project card, a log entry, a runbook step -- has a key in it,
    // shaped '<prefix>:<id>' ('proj:12', 'item:40', 'log:7').
    //
    // Tony's rule (2026-09-24): every expand / collapse is remembered across a reload and a new
    // session, AND Expand all / Collapse all also reach things that appear LATER (a new project,
    // item, note, step, log entry...). So, like /picasso-groups.js, the store keeps a remembered
    // DEFAULT per prefix plus per-key EXCEPTIONS:
    //   - an all-button calls setAllForPrefix(prefix, collapsed): it remembers the prefix default
    //     and clears that prefix's per-key choices, so every key -- present or future -- follows it;
    //   - one toggle writes one per-key value, an exception to the default.
    // A read falls back: the key's own value -> the remembered default for the caller's scope
    // (optional, e.g. 'stepof:<noteId>:' for one note's steps) -> the remembered default for the
    // key's prefix -> the caller's hard default (e.g. "a long log entry starts folded"). Until an
    // all-button is pressed nothing is remembered per prefix, so a first visit is unchanged.

    SL.collapseStore = function (storeKey) {
        var defaultsKey = storeKey + '.defaults';
        var state = {}, defaults = {};
        try { state = JSON.parse(localStorage.getItem(storeKey) || '{}') || {}; } catch (e) { state = {}; }
        try { defaults = JSON.parse(localStorage.getItem(defaultsKey) || '{}') || {}; } catch (e) { defaults = {}; }
        function has(map, k) { return Object.prototype.hasOwnProperty.call(map, k); }
        function save() {
            try {
                localStorage.setItem(storeKey, JSON.stringify(state));
                localStorage.setItem(defaultsKey, JSON.stringify(defaults));
            } catch (e) { /* private mode */ }
        }
        function prefixOf(key) { var s = String(key), i = s.indexOf(':'); return i < 0 ? '' : s.slice(0, i + 1); }
        function fallback(key, defaultCollapsed, scope) {
            if (scope && has(defaults, scope)) return !!defaults[scope];
            var p = prefixOf(key);
            if (p && has(defaults, p)) return !!defaults[p];
            return !!defaultCollapsed;
        }
        return {
            isCollapsed: function (key, defaultCollapsed, scope) {
                return has(state, key) ? !!state[key] : fallback(key, defaultCollapsed, scope);
            },
            set: function (key, collapsed) { state[key] = !!collapsed; save(); },
            toggle: function (key, defaultCollapsed, scope) {
                var next = !this.isCollapsed(key, defaultCollapsed, scope);
                this.set(key, next);
                return next;
            },
            setMany: function (keys, collapsed) { keys.forEach(function (k) { state[k] = !!collapsed; }); save(); },
            /** An all-button: remember `collapsed` as the default for every key under `prefix` (or a
                scope name), and forget the per-key choices under it plus any extra `keys` given. */
            setAllForPrefix: function (prefix, collapsed, keys) {
                defaults[prefix] = !!collapsed;
                Object.keys(state).forEach(function (k) { if (k.indexOf(prefix) === 0) delete state[k]; });
                (keys || []).forEach(function (k) { delete state[k]; });
                save();
            }
        };
    };

    /** Text is "long" when it is worth folding away: many characters or many lines. */
    SL.isLong = function (text, chars, lines) {
        var t = String(text == null ? '' : text);
        return t.length > (chars || 280) || t.split('\n').length > (lines || 4);
    };

    SL.firstLine = function (text, max) {
        var line = String(text == null ? '' : text).split('\n')[0].trim();
        return line.length > (max || 140) ? line.slice(0, max || 140) : line;
    };

    // ---- small renderers ---------------------------------------------------

    SL.pill = function (name, colorHex) {
        return '<span class="sl-pill" style="background:' + SL.esc(colorHex || '#6B7280') + '">' + SL.esc(name || '') + '</span>';
    };

    SL.progress = function (closed, total) {
        var pct = total > 0 ? Math.round(closed * 100 / total) : 0;
        return '<div class="sl-progress"><span style="width:' + pct + '%"></span></div>' +
               '<div class="sl-progress-label">' + closed + ' of ' + total + ' items closed' + (total > 0 ? ' (' + pct + '%)' : '') + '</div>';
    };

    // ---- markdown written by an OUTSIDE agent, shown to a signed-in person -------------
    //
    // A note body can be written through the API by a scoped client. Rendered raw, it would
    // run in the reader's browser WITH the reader's session -- an outside agent reaching all of
    // Leonardo through Tony's own tab. So nothing the markdown engine emits is trusted: it is
    // parsed into an inert <template> (no script runs, no image loads, no handler fires there),
    // and only what is on the allowlists below is copied out. Everything else becomes plain text
    // or is dropped. Default deny, like the path fence on the server.

    var SAFE_TAGS = { P: 1, BR: 1, HR: 1, H1: 1, H2: 1, H3: 1, H4: 1, H5: 1, H6: 1, UL: 1, OL: 1, LI: 1, STRONG: 1, EM: 1, B: 1, I: 1,
                      DEL: 1, CODE: 1, PRE: 1, BLOCKQUOTE: 1, TABLE: 1, THEAD: 1, TBODY: 1, TR: 1, TH: 1, TD: 1, A: 1 };
    var DROPPED_WHOLE = { SCRIPT: 1, STYLE: 1, IFRAME: 1, OBJECT: 1, EMBED: 1, LINK: 1, META: 1, BASE: 1, FORM: 1, INPUT: 1, BUTTON: 1,
                          TEXTAREA: 1, SELECT: 1, SVG: 1, MATH: 1, TEMPLATE: 1, NOSCRIPT: 1, IMG: 1, VIDEO: 1, AUDIO: 1, SOURCE: 1 };

    function safeHref(href) {
        var h = String(href == null ? '' : href).trim();
        // Only plain web and mail links. No javascript:, data:, vbscript:, blob:, protocol-relative or odd schemes.
        return /^(https?:\/\/|mailto:)/i.test(h) ? h : '';
    }

    // The parser's line breaks BETWEEN blocks are layout, not text; dropped so a container that
    // keeps white space (pre-wrap) does not show them as blank lines.
    var BLOCK_PARENTS = { UL: 1, OL: 1, TABLE: 1, THEAD: 1, TBODY: 1, TR: 1, BLOCKQUOTE: 1 };

    function copySafe(node, into) {
        var isBlockParent = node.nodeType === 11 || (node.nodeType === 1 && BLOCK_PARENTS[node.tagName.toUpperCase()]);
        Array.prototype.forEach.call(node.childNodes, function (child) {
            if (child.nodeType === 3 && isBlockParent && /^\s*$/.test(child.nodeValue)) return;
            if (child.nodeType === 3) { into.appendChild(document.createTextNode(child.nodeValue)); return; }
            if (child.nodeType !== 1) return;                                    // comments, processing instructions: gone
            var tag = child.tagName.toUpperCase();
            if (DROPPED_WHOLE[tag]) return;
            if (!SAFE_TAGS[tag]) { copySafe(child, into); return; }              // unknown element: keep its text, lose the element
            var el = document.createElement(tag.toLowerCase());                  // a NEW element: no attribute survives by default
            if (tag === 'A') {
                var href = safeHref(child.getAttribute('href'));
                if (href) { el.setAttribute('href', href); el.setAttribute('target', '_blank'); el.setAttribute('rel', 'noopener noreferrer nofollow'); }
            }
            copySafe(child, el);
            into.appendChild(el);
        });
    }

    /** Markdown -> HTML from the parser (still untrusted). A single line break stays a line break, so
        text typed without any Markdown looks exactly as typed. With no parser on the page the text is
        shown escaped, line for line -- it must never simply vanish. */
    function parse(markdown) {
        var text = String(markdown == null ? '' : markdown);
        if (!window.marked) return '<p>' + SL.esc(text).replace(/\n/g, '<br>') + '</p>';
        return window.marked.parse(text, { breaks: true, gfm: true });
    }

    // The sanitizer now lives in /picasso-markdown.js (Story 3510), shared with Leonardo Codex. A page
    // that loads it gets that copy; a page that does not keeps the code below, which behaves the same.
    function shared() {
        var p = window.PicassoMarkdown;
        return (p && typeof p.safeMarkdown === 'function' && typeof p.md === 'function' && typeof p.plain === 'function') ? p : null;
    }

    /** Markdown -> a DOM fragment that is safe to put on a signed-in page. */
    SL.safeMarkdown = function (markdown) {
        var p = shared();
        if (p) return p.safeMarkdown(markdown);
        var holder = document.createElement('template');
        holder.innerHTML = parse(markdown);
        var out = document.createDocumentFragment();
        copySafe(holder.content, out);
        return out;
    };

    /** Replace an element's content with safely rendered markdown. */
    SL.renderMarkdown = function (element, markdown) {
        if (!element) return;
        while (element.firstChild) element.removeChild(element.firstChild);
        element.appendChild(SL.safeMarkdown(markdown));
    };

    // Tony's rule (2026-09-18): "I don't ever want to see markdown." Every text a person or an agent
    // wrote is shown FORMATTED (SL.md, in an element with class sl-md) or, where there is room for one
    // line only, as PLAIN words with the marks taken out (SL.plain). Never esc(text) of a text field.
    // A command is the one exception: it is literal, and is shown as typed in monospace.

    var ELLIPSIS = String.fromCharCode(0x2026);

    /** Markdown -> safe HTML, as a string for a template. cut: the text was cut short -- end it with an ellipsis. */
    SL.md = function (markdown, cut) {
        var p = shared();
        if (p) return p.md(markdown, cut);
        var box = document.createElement('div');
        box.appendChild(SL.safeMarkdown(markdown));
        if (cut) {
            var last = box;
            while (last.lastElementChild && !/^(PRE|CODE|BR|HR|TABLE)$/.test(last.lastElementChild.tagName)) last = last.lastElementChild;
            last.appendChild(document.createTextNode(ELLIPSIS));
        }
        // The browser writes a non-breaking space as &nbsp;, which the PDF renderer refuses (strict
        // XHTML, no DTD); the numeric form means the same and prints.
        return box.innerHTML.replace(/&nbsp;/g, '&#160;');
    };

    /** Markdown -> plain words, for a one-line place. The first line with anything on it, or with
        allLines every line run together; cut to max characters. Escape the result like any text. */
    SL.plain = function (markdown, max, allLines) {
        var p = shared();
        if (p) return p.plain(markdown, max, allLines);
        var box = document.createElement('div');
        box.appendChild(SL.safeMarkdown(markdown));
        Array.prototype.forEach.call(box.querySelectorAll('p,li,h1,h2,h3,h4,h5,h6,tr,pre,blockquote,br,hr'), function (el) {
            el.parentNode.insertBefore(document.createTextNode('\n'), el.nextSibling);
        });
        var lines = box.textContent.split('\n').map(function (l) { return l.replace(/\s+/g, ' ').trim(); }).filter(Boolean);
        var text = allLines ? lines.join(' ') : (lines[0] || '');
        max = max || 140;
        return text.length > max ? text.slice(0, max) + ELLIPSIS : text;
    };

    // A link inside rendered text opens in a new tab and does nothing else -- it must not also
    // open, fold or select the card or row the text sits in.
    document.addEventListener('click', function (e) {
        var a = e.target && e.target.closest ? e.target.closest('.sl-md a[href]') : null;
        if (a) e.stopPropagation();
    }, true);

    /** Put text on the clipboard of the device in hand. Resolves true when it worked. The old
        execCommand route is kept for a page served without a secure context. */
    SL.copyText = async function (text) {
        var s = String(text == null ? '' : text);
        try {
            if (navigator.clipboard && window.isSecureContext) { await navigator.clipboard.writeText(s); return true; }
        } catch (e) { /* fall through to the old way */ }
        var box = document.createElement('textarea');
        box.value = s;
        box.setAttribute('readonly', '');
        box.style.position = 'fixed'; box.style.top = '-1000px'; box.style.opacity = '0';
        document.body.appendChild(box);
        box.select();
        var ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
        document.body.removeChild(box);
        return ok;
    };

    SL.param = function (name) {
        var m = new RegExp('[?&]' + name + '=([^&#]*)').exec(window.location.search);
        return m ? decodeURIComponent(m[1]) : '';
    };

    /**
     * Does a row match what was typed in a tab's search box? Every word typed must appear
     * somewhere in the row's texts (any order, any case). An empty search matches everything.
     * texts may hold strings, numbers, arrays of them, or null.
     */
    SL.searchMatches = function (needle, texts) {
        var words = String(needle || '').toLowerCase().split(/\s+/).filter(Boolean);
        if (!words.length) return true;
        var hay = [];
        (function add(x) {
            if (x == null) return;
            if (Array.isArray(x)) { x.forEach(add); return; }
            hay.push(String(x).toLowerCase());
        })(texts);
        var all = hay.join('\n');
        return words.every(function (w) { return all.indexOf(w) >= 0; });
    };

    window.SolutionsCommon = SL;
})();
