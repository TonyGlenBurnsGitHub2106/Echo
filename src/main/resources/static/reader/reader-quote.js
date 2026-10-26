/* Leonardo Reader -- Send to Quotes (Story 2429). ASCII-only.
 *
 * ONE sheet, shared by BOTH reading surfaces, exactly as reader-place.js and
 * reader-find.js already are:
 *
 *   /reader/reader.html        the standalone Leonardo Reader (reader-marks.js)
 *   /writing/writing.html      the Writing Filing Cabinet reader (writing-marks.js)
 *
 * Both are called "the reader" in conversation, and the request was for both --
 * a passage worth keeping is worth keeping wherever it was read. Putting the
 * sheet here rather than in either marking layer is what stops the two drifting
 * apart the way the colour palette once did (Story 2395).
 *
 * WHAT IT DOES. Given a stretch of selected text it opens a sheet that files
 * that text on the Quotes shelf of the Leonardo Library, carrying the author
 * and the work the reader already knows, and letting the reader pick the Topic
 * (the one group a quote lives in) and any number of Categories (the
 * cross-cutting tags). Both pick-lists are find-or-create: typing a name that
 * is not in the list makes it.
 *
 * NO NEW SERVER SIDE. The Quotes Manager API already does all of this:
 *
 *   GET  /api/quotes/authors      the author lookup list
 *   GET  /api/quotes/topics       the topic lookup list
 *   GET  /api/quotes/categories   the category lookup list
 *   POST /api/quotes              save; names are resolved to ids server-side
 *
 * DUPLICATES. The save endpoint does not refuse a duplicate -- it answers
 * { ok:true, duplicate:true, existing:[...] } and saves nothing. The sheet says
 * so and turns Save into "Save anyway", which repeats the call with force set.
 * A reader who highlights the same line twice should be told, not silently
 * given a second copy.
 *
 * The controls are the standard ones: PicassoPicker for the single-value
 * pick-lists, PicassoTags for the many-valued one. Never a native select.
 */
(function () {
    'use strict';
    if (window.LeoQuoteSheet) return;

    var SHEET_ID = 'lqSheet';
    var mBuilt = false;
    var mEl = {};                 // the sheet elements, cached after building
    var mAuthors = null;          // lookup lists, fetched once per page load
    var mTopics = null;
    var mCategories = null;
    var mCats = null;             // the PicassoTags handle
    var mBusy = false;
    var mForce = false;           // the pending save is a deliberate duplicate
    var mOnSaved = null;

    function auth() { return window.LWAuth; }

    /* The host page owns the toast; there is no sense in this module growing a
     * second one that would sit in a different corner on each surface. */
    function toast(msg) {
        if (window.LeoReaderMarks && window.LeoReaderMarks.toast) { window.LeoReaderMarks.toast(msg); return; }
        if (window.Picasso && window.Picasso.toast) { window.Picasso.toast(msg); return; }
        say(msg, false);
    }

    // ------------------------------------------------------------- the sheet

    function build() {
        if (mBuilt) return;
        mBuilt = true;

        var scrim = document.createElement('div');
        scrim.className = 'lq-scrim';
        scrim.id = 'lqScrim';
        scrim.hidden = true;
        scrim.setAttribute('data-testid', 'quote-sheet-scrim');

        var sheet = document.createElement('section');
        sheet.className = 'lq-sheet';
        sheet.id = SHEET_ID;
        sheet.hidden = true;
        sheet.setAttribute('aria-label', 'Send to Quotes');
        sheet.setAttribute('data-testid', 'quote-sheet');
        sheet.innerHTML =
            '<header class="lq-head">' +
              '<h2 class="lq-title">Send to Quotes</h2>' +
              '<button type="button" class="lq-x" id="lqClose" data-testid="quote-sheet-close">Close</button>' +
            '</header>' +
            '<div class="lq-body">' +
              '<blockquote class="lq-quote" id="lqText" data-testid="quote-sheet-text"></blockquote>' +

              '<label class="lq-label" for="lqAuthor">Author</label>' +
              '<div class="picker-wrap">' +
                '<input type="text" class="picker-input lq-field" id="lqAuthor" autocomplete="off"' +
                      ' placeholder="Who said or wrote it" data-testid="quote-sheet-author">' +
                '<div class="picker-dropdown" id="lqAuthorDrop"></div>' +
              '</div>' +

              '<label class="lq-label" for="lqSource">Work</label>' +
              '<input type="text" class="lq-field" id="lqSource" autocomplete="off"' +
                    ' placeholder="The book, essay or document it came from" data-testid="quote-sheet-source">' +

              '<label class="lq-label" for="lqTopic">Topic</label>' +
              '<div class="picker-wrap">' +
                '<input type="text" class="picker-input lq-field" id="lqTopic" autocomplete="off"' +
                      ' placeholder="The one group it belongs in" data-testid="quote-sheet-topic">' +
                '<div class="picker-dropdown" id="lqTopicDrop"></div>' +
              '</div>' +

              '<label class="lq-label" for="lqCatInput">Categories</label>' +
              '<div class="pt-wrap">' +
                '<div class="pt-box">' +
                  '<span class="pt-chips" id="lqCatChips" data-testid="quote-sheet-category-chips"></span>' +
                  '<input type="text" class="pt-input" id="lqCatInput" autocomplete="off"' +
                        ' data-testid="quote-sheet-categories">' +
                '</div>' +
                '<div class="pt-dropdown" id="lqCatDrop"></div>' +
              '</div>' +

              '<label class="lq-label" for="lqNotes">Your comment</label>' +
              '<textarea class="lq-field" id="lqNotes" rows="3" data-testid="quote-sheet-notes"></textarea>' +

              '<label class="lq-check">' +
                '<input type="checkbox" id="lqFavorite" data-testid="quote-sheet-favorite">' +
                '<span>Mark as a favorite</span>' +
              '</label>' +

              '<p class="lq-msg" id="lqMsg" data-testid="quote-sheet-msg"></p>' +
            '</div>' +
            '<footer class="lq-foot">' +
              '<button type="button" class="lq-btn" id="lqCancel" data-testid="quote-sheet-cancel">Cancel</button>' +
              '<button type="button" class="lq-btn lq-btn-primary" id="lqSave" data-testid="quote-sheet-save">Save</button>' +
            '</footer>';

        document.body.appendChild(scrim);
        document.body.appendChild(sheet);

        mEl.scrim    = scrim;
        mEl.sheet    = sheet;
        mEl.text     = document.getElementById('lqText');
        mEl.author   = document.getElementById('lqAuthor');
        mEl.source   = document.getElementById('lqSource');
        mEl.topic    = document.getElementById('lqTopic');
        mEl.catInput = document.getElementById('lqCatInput');
        mEl.catChips = document.getElementById('lqCatChips');
        mEl.catDrop  = document.getElementById('lqCatDrop');
        mEl.notes    = document.getElementById('lqNotes');
        mEl.favorite = document.getElementById('lqFavorite');
        mEl.msg      = document.getElementById('lqMsg');
        mEl.save     = document.getElementById('lqSave');

        // A tap on one of the sheet buttons must not collapse the reader's
        // selection before the click lands. The marking layers latch it, but
        // the sheet is opened over a live selection and this keeps it intact.
        sheet.addEventListener('pointerdown', function (ev) {
            if (ev.target.closest && ev.target.closest('button')) ev.preventDefault();
        });

        document.getElementById('lqClose').addEventListener('click', close);
        document.getElementById('lqCancel').addEventListener('click', close);
        mEl.scrim.addEventListener('click', close);
        mEl.save.addEventListener('click', save);

        // Changing anything makes this a different quote from the one that came
        // back as a duplicate, so the "Save anyway" state is dropped.
        [mEl.author, mEl.source, mEl.topic, mEl.notes].forEach(function (f) {
            if (f) f.addEventListener('input', clearDuplicateState);
        });

        attachPickers();
    }

    function attachPickers() {
        if (window.PicassoPicker) {
            window.PicassoPicker.attach({
                input: mEl.author,
                dropdown: document.getElementById('lqAuthorDrop'),
                getItems: function () { return mAuthors || []; },
                allowNew: true,
                newLabel: 'Add'
            });
            window.PicassoPicker.attach({
                input: mEl.topic,
                dropdown: document.getElementById('lqTopicDrop'),
                getItems: function () { return mTopics || []; },
                allowNew: true,
                newLabel: 'Add'
            });
        }
        if (window.PicassoTags) {
            mCats = window.PicassoTags.attach({
                input: mEl.catInput,
                chips: mEl.catChips,
                dropdown: mEl.catDrop,
                getOptions: function () { return mCategories || []; },
                placeholder: 'Pick a category or type a new one'
            });
        }
    }

    /* The three pick-lists come down once per page load. They are small, they
     * change only when a quote is filed, and the sheet opens over a page that is
     * being read -- a round trip on every open would show as a lag. */
    function loadLookups() {
        if (mAuthors && mTopics && mCategories) return Promise.resolve();
        if (!auth() || !auth().apiGet) {
            mAuthors = mAuthors || []; mTopics = mTopics || []; mCategories = mCategories || [];
            return Promise.resolve();
        }
        return Promise.all([
            auth().apiGet('/api/quotes/authors'),
            auth().apiGet('/api/quotes/topics'),
            auth().apiGet('/api/quotes/categories')
        ]).then(function (rs) {
            mAuthors    = listOf(rs[0]);
            mTopics     = listOf(rs[1]);
            mCategories = listOf(rs[2]);
        }).catch(function () {
            mAuthors = mAuthors || []; mTopics = mTopics || []; mCategories = mCategories || [];
        });
    }

    function listOf(res) {
        return (res && res.ok && Array.isArray(res.data)) ? res.data : [];
    }

    function say(msg, isOk) {
        if (!mEl.msg) return;
        mEl.msg.textContent = msg || '';
        mEl.msg.classList.toggle('is-ok', !!isOk);
    }

    function clearDuplicateState() {
        if (!mForce) return;
        mForce = false;
        if (mEl.save) mEl.save.textContent = 'Save';
        say('');
    }

    // ------------------------------------------------------------------ open

    /**
     * Show the sheet over whatever is on screen.
     *
     * opts.text    the passage to file -- the only required part
     * opts.author  who wrote it, when the caller knows (prefills the picker)
     * opts.work    the document it came from (prefills Work)
     * opts.onSaved called with the saved quote once it is filed
     */
    function open(opts) {
        opts = opts || {};
        var text = String(opts.text == null ? '' : opts.text).trim();
        if (!text) { toast('Select some text first.'); return; }

        build();
        mOnSaved = typeof opts.onSaved === 'function' ? opts.onSaved : null;
        mForce = false;
        mBusy = false;

        if (mEl.text) mEl.text.textContent = text;
        mEl.sheet.setAttribute('data-quote-text', text);
        if (mEl.author) mEl.author.value = opts.author || '';
        if (mEl.source) mEl.source.value = opts.work || '';
        if (mEl.topic) mEl.topic.value = '';
        if (mEl.notes) mEl.notes.value = '';
        if (mEl.favorite) mEl.favorite.checked = false;
        if (mCats) mCats.setValues([]);
        if (mEl.save) { mEl.save.textContent = 'Save'; mEl.save.disabled = false; }
        say('');

        mEl.scrim.hidden = false;
        mEl.sheet.hidden = false;
        loadLookups();
    }

    function close() {
        if (!mBuilt) return;
        mEl.scrim.hidden = true;
        mEl.sheet.hidden = true;
        mOnSaved = null;
    }

    function isOpen() { return mBuilt && !mEl.sheet.hidden; }

    // ------------------------------------------------------------------ save

    function save() {
        if (mBusy) return;
        var text = mEl.sheet.getAttribute('data-quote-text') || '';
        if (!text) { say('There is nothing to save.', false); return; }
        if (!auth() || !auth().apiPost) { say('Quotes are not available here.', false); return; }

        mBusy = true;
        mEl.save.disabled = true;

        var topicName = trimOrNull(mEl.topic && mEl.topic.value);
        var body = {
            quoteText: text,
            author: trimOrNull(mEl.author && mEl.author.value),
            topic: topicName,
            source: trimOrNull(mEl.source && mEl.source.value),
            notes: trimOrNull(mEl.notes && mEl.notes.value),
            categories: mCats ? mCats.getValues() : [],
            favorite: !!(mEl.favorite && mEl.favorite.checked),
            force: mForce
        };

        auth().apiPost('/api/quotes', body).then(function (res) {
            mBusy = false;
            mEl.save.disabled = false;
            if (!res || !res.ok) { say(errorTextOf(res, 'That quote could not be saved.'), false); return; }

            // Already on the shelf. Nothing was written; offer to write it anyway.
            if (res.data && res.data.duplicate) {
                mForce = true;
                mEl.save.textContent = 'Save anyway';
                say(duplicateMessage(res.data.existing), false);
                return;
            }

            // A find-or-create may have grown the lists; refetch on next open.
            mAuthors = null; mTopics = null; mCategories = null;

            var saved = res.data;
            var where = topicName ? (' under ' + topicName) : '';
            close();
            toast('Saved to Quotes' + where);
            if (mOnSaved) { try { mOnSaved(saved); } catch (e) { /* the caller's business */ } }
        }).catch(function () {
            mBusy = false;
            mEl.save.disabled = false;
            say('That quote could not be saved.', false);
        });
    }

    function duplicateMessage(existing) {
        var n = Array.isArray(existing) ? existing.length : 0;
        if (n > 1) return 'This is already on the Quotes shelf ' + n + ' times. Save anyway to file it again.';
        return 'This is already on the Quotes shelf. Save anyway to file it again.';
    }

    function errorTextOf(res, fallback) {
        var d = res && res.data;
        if (d && d.message) return d.message;
        if (d && d.error) return d.error;
        return fallback;
    }

    function trimOrNull(v) {
        var s = String(v == null ? '' : v).trim();
        return s ? s : null;
    }

    window.LeoQuoteSheet = { open: open, close: close, isOpen: isOpen };
}());
