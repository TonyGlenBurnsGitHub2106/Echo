/* Picasso Dialog -- the standard message and question modals (Story 5606).

   Tony's rule (2026-10-08): a message is never dismissed by a timer. The user closes it,
   which is the only way we know it was read. So every message on a LeonardoWeb page is a
   dialog that waits for OK, and every question is a dialog that waits for an answer.
   Picasso.toast routes here too, so old toast calls already behave.

   Every call returns a Promise of the answer. Dialogs queue: a second call waits until the
   first is answered, and an identical message that is already showing or waiting is not
   shown twice. Closed ONLY by its own buttons or Escape (never a backdrop tap, a click
   outside, or blur -- the modal rule).

   Ready-made:
     PicassoDialog.message(text, opts)        OK                          -> 'ok'
     PicassoDialog.info / success / warning / error(text, opts)          -> 'ok'
     PicassoDialog.yesNo(text, opts)          Yes / No                    -> true | false
     PicassoDialog.okCancel(text, opts)       OK / Cancel                 -> true | false
     PicassoDialog.saveCancel(text, opts)     Save / Cancel               -> 'save' | 'cancel'
         opts.discard: true adds Don't Save (or a string: that label)      -> 'discard'
     PicassoDialog.confirmDelete(text, opts)  Delete / Cancel (red)       -> true | false
     PicassoDialog.toast(text)                OK, kind guessed from text  -> 'ok'
                                              (a bare "it saved" note shows nothing -- below)

   The one thing NOT shown: an old toast whose only news is that a save, insert, update or
   delete worked ("Saved", "Updated -- GL# 12", "Transaction deleted"). Those used to fade by
   themselves; as dialogs they cost an OK on every save. Tony (2026-10-09): "I don't want to
   have to dismiss a modal every time it happens." Errors, warnings and anything else still
   wait for OK.

   Fully custom:
     PicassoDialog.show({ title, message | html, kind, buttons, escape })
       kind:    'info' | 'success' | 'warning' | 'error' | 'question' | 'danger'
       buttons: [{ label, value, style: 'primary' | 'danger' | 'plain', isDefault }]
                left to right; the default button gets focus, so Enter answers it.
       escape:  the value Escape returns (default: the last plain button, else the last
                button).
       message: plain text, newlines kept. html: trusted markup -- escape what you insert.

   opts on the ready-made calls: { title, kind, html } plus yes / no / ok / cancel / save
   to relabel a button. Labels carry no punctuation (button-label rule). Source is ASCII only. */
(function () {
    'use strict';

    if (window.PicassoDialog) return;

    var CSS_HREF = '/picasso-dialog.css?v=1';

    var ICONS = {
        info:     'i',
        success:  String.fromCharCode(0x2713),   // check mark
        warning:  '!',
        error:    String.fromCharCode(0x2715),   // multiplication x
        question: '?',
        danger:   '!'
    };

    var TITLES = {
        info:     'Note',
        success:  'Done',
        warning:  'Heads up',
        error:    'That did not work',
        question: 'Please confirm',
        danger:   'Please confirm'
    };

    var mQueue = [];
    var mCurrent = null;

    function ensureCss() {
        if (document.querySelector('link[data-pdlg-css]')) return;
        var have = document.querySelectorAll('link[rel="stylesheet"]');
        for (var i = 0; i < have.length; i++) {
            if ((have[i].getAttribute('href') || '').indexOf('picasso-dialog.css') !== -1) return;
        }
        var l = document.createElement('link');
        l.rel = 'stylesheet';
        l.href = CSS_HREF;
        l.setAttribute('data-pdlg-css', '1');
        document.head.appendChild(l);
    }

    function keyOf(o) { return o.kind + '|' + o.title + '|' + o.message + '|' + (o.html || ''); }

    /** Guesses the kind of a bare message, for Picasso.toast callers that never said. */
    function guessKind(text) {
        var t = String(text == null ? '' : text);
        if (/(fail|error|could not|couldn't|cannot|can't|refused|nothing was saved|nothing was added|not found|invalid|denied|unable|did not|didn't|problem|timed out|no longer exists)/i.test(t)) return 'error';
        if (/(required|pick |choose |enter |select |missing|warning|careful|already)/i.test(t)) return 'warning';
        if (/(saved|added|deleted|removed|copied|sent|updated|created|imported|done|moved|linked|restored|archived|resolved|posted|cleared|uploaded)/i.test(t)) return 'success';
        return 'info';
    }

    /** True for a short success note whose only news is that a record was saved, added,
        updated, removed or moved: "Saved", "Task deleted", "Moved to Trash", "Updated -- GL# 12".
        A note that goes on to tell the user something ("Debate added. You can link files now")
        is not routine and still waits for OK. */
    function isRoutineSave(text) {
        var t = String(text == null ? '' : text).trim();
        if (!t || t.length > 80) return false;
        if (/(\.\s+\S| -- )/.test(t)) return false;
        return /^(?:[^\s]+\s+){0,3}?(saved|updated|added|created|inserted|deleted|removed|renamed|moved)\b/i.test(t);
    }
    function show(opts) {
        opts = opts || {};
        var o = {
            kind:    ICONS.hasOwnProperty(opts.kind) ? opts.kind : 'info',
            title:   opts.title,
            message: opts.message == null ? '' : String(opts.message),
            html:    opts.html || null,
            buttons: (opts.buttons && opts.buttons.length) ? opts.buttons : [{ label: 'OK', value: 'ok', style: 'primary', isDefault: true }],
            escape:  opts.escape
        };
        if (o.title == null) o.title = TITLES[o.kind];

        var key = keyOf(o);
        if (mCurrent && mCurrent.key === key) return mCurrent.promise;
        for (var i = 0; i < mQueue.length; i++) if (mQueue[i].key === key) return mQueue[i].promise;

        var job = { key: key, opts: o };
        job.promise = new Promise(function (resolve) { job.resolve = resolve; });
        mQueue.push(job);
        pump();
        return job.promise;
    }

    function pump() {
        if (mCurrent || !mQueue.length) return;
        if (!document.body) { document.addEventListener('DOMContentLoaded', pump, { once: true }); return; }
        mCurrent = mQueue.shift();
        render(mCurrent);
    }

    function render(job) {
        ensureCss();
        var o = job.opts;
        var prevFocus = document.activeElement;

        var back = document.createElement('div');
        back.className = 'pdlg-backdrop';
        back.setAttribute('data-testid', 'picasso-dialog');

        var card = document.createElement('div');
        card.className = 'pdlg-card k-' + o.kind;
        card.setAttribute('role', (o.kind === 'error' || o.kind === 'warning' || o.kind === 'danger') ? 'alertdialog' : 'dialog');
        card.setAttribute('aria-modal', 'true');
        card.setAttribute('aria-labelledby', 'pdlgTitle');
        card.setAttribute('aria-describedby', 'pdlgBody');

        var band = document.createElement('div');
        band.className = 'pdlg-band';
        card.appendChild(band);

        var head = document.createElement('div');
        head.className = 'pdlg-head';
        var icon = document.createElement('div');
        icon.className = 'pdlg-icon';
        icon.setAttribute('aria-hidden', 'true');
        icon.textContent = ICONS[o.kind];
        var title = document.createElement('h2');
        title.className = 'pdlg-title';
        title.id = 'pdlgTitle';
        title.setAttribute('data-testid', 'picasso-dialog-title');
        title.textContent = o.title;
        head.appendChild(icon);
        head.appendChild(title);
        card.appendChild(head);

        var body = document.createElement('div');
        body.className = 'pdlg-body';
        body.id = 'pdlgBody';
        body.setAttribute('data-testid', 'picasso-dialog-message');
        if (o.html) body.innerHTML = o.html; else body.textContent = o.message;
        if (!o.html && !o.message) body.style.display = 'none';
        card.appendChild(body);

        var bar = document.createElement('div');
        bar.className = 'pdlg-buttons';
        var defaultBtn = null;
        o.buttons.forEach(function (b) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'pdlg-btn' + (b.style === 'primary' ? ' primary' : b.style === 'danger' ? ' danger' : '');
            btn.textContent = b.label;
            btn.setAttribute('data-testid', 'picasso-dialog-btn-' + String(b.value).toLowerCase().replace(/[^a-z0-9]+/g, '-'));
            btn.addEventListener('click', function () { close(b.value); });
            bar.appendChild(btn);
            if (b.isDefault) defaultBtn = btn;
        });
        if (!defaultBtn) defaultBtn = bar.lastChild;
        card.appendChild(bar);
        back.appendChild(card);

        var escapeValue = o.escape;
        if (escapeValue === undefined) {
            var plain = o.buttons.filter(function (b) { return b.style !== 'primary' && b.style !== 'danger'; });
            escapeValue = (plain.length ? plain[plain.length - 1] : o.buttons[o.buttons.length - 1]).value;
        }

        function onKey(e) {
            if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close(escapeValue); return; }
            if (e.key === 'Tab') {
                var btns = bar.querySelectorAll('button');
                var first = btns[0], last = btns[btns.length - 1];
                if (!card.contains(document.activeElement)) { e.preventDefault(); first.focus(); }
                else if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
                else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
            }
        }

        var closed = false;
        function close(value) {
            if (closed) return;
            closed = true;
            document.removeEventListener('keydown', onKey, true);
            if (back.parentNode) back.parentNode.removeChild(back);
            try { if (prevFocus && prevFocus.focus && document.contains(prevFocus)) prevFocus.focus(); } catch (_) { /* focus is a nicety */ }
            mCurrent = null;
            job.resolve(value);
            pump();
        }

        document.addEventListener('keydown', onKey, true);
        document.body.appendChild(back);
        setTimeout(function () { try { defaultBtn.focus(); } catch (_) { /* nothing to focus */ } }, 0);
    }

    function relabel(opts, key, fallback) { return (opts && opts[key]) || fallback; }

    function message(text, opts, kind) {
        opts = opts || {};
        return show({
            kind:    opts.kind || kind || 'info',
            title:   opts.title,
            message: text,
            html:    opts.html,
            buttons: [{ label: relabel(opts, 'ok', 'OK'), value: 'ok', style: 'primary', isDefault: true }],
            escape:  'ok'
        });
    }

    function twoWay(text, opts, yesLabel, noLabel, kind, yesStyle, defaultIsNo) {
        opts = opts || {};
        return show({
            kind:    opts.kind || kind,
            title:   opts.title,
            message: text,
            html:    opts.html,
            buttons: [
                { label: noLabel,  value: false, style: 'plain', isDefault: !!defaultIsNo },
                { label: yesLabel, value: true,  style: yesStyle || 'primary', isDefault: !defaultIsNo }
            ],
            escape:  false
        });
    }

    window.PicassoDialog = {
        show:    show,
        message: function (text, opts) { return message(text, opts); },
        info:    function (text, opts) { return message(text, opts, 'info'); },
        success: function (text, opts) { return message(text, opts, 'success'); },
        warning: function (text, opts) { return message(text, opts, 'warning'); },
        error:   function (text, opts) { return message(text, opts, 'error'); },

        yesNo: function (text, opts) {
            return twoWay(text, opts, relabel(opts, 'yes', 'Yes'), relabel(opts, 'no', 'No'), 'question');
        },
        okCancel: function (text, opts) {
            return twoWay(text, opts, relabel(opts, 'ok', 'OK'), relabel(opts, 'cancel', 'Cancel'), 'question');
        },
        /** Delete / Cancel in red; Cancel is the default, so Enter never deletes. */
        confirmDelete: function (text, opts) {
            return twoWay(text, opts, relabel(opts, 'yes', 'Delete'), relabel(opts, 'no', 'Cancel'),
                          'danger', 'danger', true);
        },
        saveCancel: function (text, opts) {
            opts = opts || {};
            var buttons = [{ label: relabel(opts, 'cancel', 'Cancel'), value: 'cancel', style: 'plain' }];
            if (opts.discard) {
                var discardLabel = typeof opts.discard === 'string' ? opts.discard : 'Don\'t Save';
                buttons.push({ label: discardLabel, value: 'discard', style: 'plain' });
            }
            buttons.push({ label: relabel(opts, 'save', 'Save'), value: 'save', style: 'primary', isDefault: true });
            return show({ kind: opts.kind || 'question', title: opts.title || 'Save your changes?',
                          message: text, html: opts.html, buttons: buttons, escape: 'cancel' });
        },

        /** What Picasso.toast now does: the same one-line message, held until OK -- except a
            routine "it saved" note, which needs nothing from the user and is not shown. */
        toast: function (text) {
            var kind = guessKind(text);
            if (kind === 'success' && isRoutineSave(text)) return Promise.resolve('ok');
            return message(text, null, kind);
        },

        guessKind: guessKind
    };
})();
