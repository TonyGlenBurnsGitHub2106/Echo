/* gmail-client.js -- Gmail Client (Story 5569): a Leonardo mail client over Tony's real Gmail.
 *
 * Story 5654: echoed into Echo as its Email Client, over the classic Outlook app on the work
 * computer. The same page and the same API paths; what differs is marked Story 5654: the words say
 * Outlook and Echo, there is no demo, no Reading Nook, no Listen, no View in Gmail, no Save to
 * Leonardo Files and no links to records (Echo has none of those), and the whitelist / blacklist is
 * Echo's own (gmail-client-echo.js adds its list and the Outlook theme). Nothing here ever makes
 * an Outlook rule.
 *
 * Opening the page checks mail; Check mail checks again. Only whitelisted senders' mail shows
 * (Mail Triage decides who is whitelisted), filed into the inbox chosen for each sender, with
 * All, Follow up and Unclassified always there. Delete hides a message in Leonardo only -- Gmail
 * keeps it. Reply, Reply all, Forward and New message send through Gmail.
 *
 * The quick search searches ALL mail (it moves to the All tab); Custom search opens a dialog
 * with more fields. All / Unread / Read narrows any tab. Select (or a long press on the phone)
 * picks many messages for one action; Mark all marks every message the tab shows, not just the
 * loaded page. Follow up flags a message for a day; the Follow up tab lists them, earliest first.
 *
 * On the phone a card swipes: right toggles read / unread, left uncovers Follow up and Delete.
 * Messages are grouped under day headings (Follow up: Overdue / Today / Coming up), and the
 * reader steps to the newer or older message without closing.
 *
 * How far back the list goes is a choice too: Any time, Today, This week, This month, This year,
 * or a number of days typed in. It narrows the list, the tab counts and Mark all alike (it is sent
 * as the "on or after" day; a custom search's own on-or-after wins when it is later).
 *
 * View on any row opens a sepia quick view of the message (Esc closes it). The preview view (the
 * small split-pane button) shows the list and the selected message together -- the list on the left
 * or on top (the small button beside it switches; the PC and the phone each remember their own,
 * left and top by default); the up and down arrows walk the list. The bar between them drags to make either side
 * wider (remembered; double-click it to go back to the usual width). Inboxes has Arrange (Chess Shuffle): drag
 * the inboxes by their grips to put the tabs in order.
 *
 * Remembered for Tony in the database (UserViewState), PER DEVICE (2026-10-05): the tab, the range
 * and its days, the preview view and its layout, and whether the page header and the message
 * details are folded -- the phone and the PC each keep their own. Per USER, the same on
 * every device: the All / Unread choice, the compose theme and the signature. Undecided sits right
 * after All.
 *
 * Check mail clears whatever note the page was showing (a delete's Undo, an earlier count), since
 * it may no longer apply; the check then says what it found.
 *
 * A check that takes longer than a few seconds carries on in the server's background; while it
 * runs the status is re-read every few seconds, and only then. Nothing here runs by itself.
 *
 * URL: ?focus=<messageId> opens that message (Leonardo Find); ?sent=<sentId> opens a sent message.
 *
 * SENT (Story 5596): the Sent tab lists the mail Tony sent -- from here (kept as it goes) and from
 * Gmail itself (read by Check mail from Gmail's Sent folder). Each one shows Replied (mail read in
 * from the same Gmail thread afterwards) or No reply yet, and All sent / No reply yet replaces
 * All / Unread on that tab. Open one to see its replies (each opens), Write again (to the same people,
 * same conversation), Forward, Follow up or Delete (Leonardo only). Its rows carry kind 'sent' and
 * their own ids, so every per-message call goes to /sent/<id> (msgUrl).
 *
 * STORY 5608 (2026-10-08), the enhancements:
 *   - Attach files to anything sent, from the device (uploaded the moment they are picked, held on
 *     the server until Send) or from the Reading Nook; a forward can carry the original's own files.
 *   - The search box understands from: to: subject: has:attachment is:unread flag: after: before:
 *     newer_than: older_than: and "phrases"; every word must match. The server reads it
 *     (GmailClientSearch) and says back how, on the filter line. The Tips button lists them.
 *   - Conversations (the speech-bubble toggle, remembered per device): one row per back-and-forth,
 *     and the reader shows the whole conversation -- every message a fold, only the one opened
 *     (and the newest) unfolded, quoted text tucked away. Off, a message that belongs to one says so
 *     and shows it on a tap.
 *   - Inboxes file by domain, and one sender can be filed on their own (sender over domain).
 *   - The message is a Leonardo entity: Linked to on the reader links it to any record (contact,
 *     vendor, matter, call log...), and those records list it on their Gmail tab. A SENT message
 *     links the same way (2026-10-12), through /sent/<id>/links.
 *   - Flags: grouping labels typed onto a message (made when new), put on or taken off a
 *     selection, the Flagged tab grouped by flag, and the Flags dialog (More -> Flags).
 *   - Colors by sender, domain or flag as a thin card edge; More -> Colors sees, changes a ticked
 *     set of, removes or clears them all. Themes for the whole page (the header's Theme).
 *   - Confirmations that need nothing ("2 new messages", "Sent to Greg") fade after a few seconds
 *     -- Tony's exception to the wait-for-OK rule; anything that went wrong, or offers Undo, stays.
 *   Themes, colors and the Flags / Colors dialogs live in gmail-client-look.js.
 *
 * DEMO (2026-10-02): ?demo=1 is the Gmail Client Demo -- the same page and the same server logic,
 * pointed at /api/gmail-client-demo, which runs the real GmailClientService over made-up mail and
 * inboxes held in memory (GmailClientDemo). It never reads or sends real mail and remembers its
 * own view choices under leo.gmailclientdemo.* so it never disturbs the real page's.
 */
(function () {
    'use strict';

    var A = window.LWAuth;
    var PAGE_SIZE = 50;
    var MAX_PAGE = 200;
    var LOAD_ALL_CAP = 2000;
    var OLDER_DAYS = 30;
    var DEMO = false;   // Story 5654: Echo has no demo page (its demo is a mail source, echo.mail.source=demo)
    var API = DEMO ? '/api/gmail-client-demo' : '/api/gmail-client';
    var ECHO = true;   // Story 5654: the Echo build of this page (Leonardo-only features stay off)
    // Two scopes. VS_PREFIX is per USER (every device shares it): the compose theme and the signature.
    // DEV_PREFIX is per USER PLUS DEVICE: everything about how the page is laid out, so the phone and
    // the PC each keep their own tab, range, folds and preview pane (Tony, 2026-10-05).
    var VS_PREFIX = DEMO ? 'leo.gmailclientdemo.' : 'leo.gmailclient.';
    var DEV_NAME = DEMO ? 'gmailclientdemo.' : 'gmailclient.';
    var DEV_PREFIX = window.PicassoViewState && window.PicassoViewState.deviceKey
        ? window.PicassoViewState.deviceKey(DEV_NAME) : VS_PREFIX;
    var VS_TAB = DEV_PREFIX + 'tab';               // all | followup | unclassified | inbox:<id>
    var VS_READ = VS_PREFIX + 'read';              // all | unread -- per USER, the same on every device
    var VS_FOLD = DEV_PREFIX + 'headerFolded';     // '1' when the page header is folded
    var VS_HEAD = DEV_PREFIX + 'detailsFolded';    // '1' when a message's details are folded
    var VS_ACTIONS = DEV_PREFIX + 'actionsFolded'; // '1' when the reading buttons are folded (phone only)
    var VS_RANGE = DEV_PREFIX + 'range';           // any | today | week | month | year | days
    var VS_DAYS = DEV_PREFIX + 'days';             // the typed number of days, when range = days
    var VS_VIEW = DEV_PREFIX + 'view';             // list | preview
    var VS_THREADS = DEV_PREFIX + 'threads';       // '1' when the list shows conversations (Story 5608)
    var VS_FLAGS = DEV_PREFIX + 'flagFilter';      // the flag filter: flag ids, comma separated (Story 5608 QA)
    var VS_SEEDED = DEV_PREFIX + 'seeded';         // '1' once this device has taken its first copy
    // The preview view is the Bay Window (picasso-split.js, Leonardo Term "Preview Split"). It remembers,
    // per KIND of screen, under the DEVICE prefix: <dev>layout.<desk|phone> (left | top),
    // <dev>split.<desk|phone>.<left|top> (the list's share, %), <dev>listMin.<desk|phone> and
    // <dev>pvMin.<desk|phone> ('1' = that side minimized to a strip).
    // Before 2026-10-05 all of these were per user under VS_PREFIX; a device seeing the page for the
    // first time starts from those values (seedDevice), then goes its own way.
    var DEVICE_NAMES = ['tab', 'headerFolded', 'detailsFolded', 'actionsFolded', 'range', 'days', 'view',
        'layout.desk', 'layout.phone', 'split.desk.left', 'split.desk.top', 'split.phone.left', 'split.phone.top',
        'listMin.desk', 'listMin.phone', 'pvMin.desk', 'pvMin.phone'];
    var SPLIT_MIN = 20, SPLIT_MAX = 75, SPLIT_STEP = 2;
    var RANGES = ['any', 'today', 'week', 'month', 'year', 'days'];
    var MAX_DAYS = 3650;
    var DESK_MIN_WIDTH = 900;                         // narrower than this counts as the phone
    var PREVIEW_DELAY_MS = 250;                       // arrowing fast fetches only where it stops

    var S = {
        status: null, tabs: [], tab: 'all', read: 'all', rows: [], more: false,
        q: '', custom: null,            // custom search fields, or null
        open: null,                     // the message being read
        compose: null,                  // { mode: new|reply|replyAll|forward, id }
        followUp: null,                 // { ids: [...] } while the follow-up dialog is open
        selecting: false, sel: {},      // select mode and the picked ids
        range: 'any', days: 0,          // how far back the list goes
        view: 'list', pv: 0, pvSeq: 0,  // the preview view, the previewed id, a stale-fetch guard
        pvMsg: null, qMsg: null,        // the message the preview pane / quick view is showing (for Listen)
        arranging: false,               // the Inboxes dialog's Arrange
        reply: 'all',                   // the Sent tab: all | waiting (no reply yet)
        inboxes: [],
        threads: false, totalThreads: 0,// Story 5608: the list as conversations
        flagFilter: [],                 // the flag filter: show mail with ANY of these flag ids
        searchEcho: null,               // how the server read the search box: { understood, notes }
        convShow: false, conv: null,    // the reader's conversation: shown?, and what it holds
        fileSender: false,              // the reader files one sender on their own, not the domain
        look: null                      // gmail-client-look.js: themes, colors, flags
    };

    function el(id) { return document.getElementById(id); }
    function esc(s) { return window.FileKit.esc(s == null ? '' : String(s)); }
    function errText(r, d) { return window.FileKit.errText(r, d); }
    var flashTimer = 0;

    /**
     * A confirmation that needs nothing from Tony -- "2 new messages", "Sent to Greg" -- shows on the
     * status line for a few seconds and fades (Story 5608: "this is the exception, because it is not
     * actionable"). Longer text stays longer, and a pointer resting on it holds it. Anything that
     * went wrong, or offers Undo, goes through note() and stays until something replaces it.
     */
    function flash(text) {
        note(text);
        var n = el('note');
        n.classList.add('flash');
        var wait = Math.min(10000, 3000 + String(text || '').length * 40);
        flashTimer = window.setTimeout(function fade() {
            if (!n.classList.contains('flash')) return;
            if (n.matches(':hover')) { flashTimer = window.setTimeout(fade, 1500); return; }
            n.classList.add('fading');
            flashTimer = window.setTimeout(function () { if (n.classList.contains('flash')) note(''); }, 700);
        }, wait);
    }
    function plural(n, one, many) { return n + ' ' + (n === 1 ? one : (many || one + 's')); }
    function vs() { return window.PicassoViewState || null; }
    function remember(key, value) { var v = vs(); if (v) v.set(key, value); }
    function recalled(key) { var v = vs(); return v ? v.get(key) : null; }

    // ---- Sent mail (Story 5596) ----
    // The Sent tab lists the mail Tony sent. Its rows carry kind 'sent' and their own ids (a separate
    // numbering from inbox mail), so every per-message call goes to /sent/<id> instead of /messages/<id>.

    function isSent() { return S.tab === 'sent'; }
    /** The Drafts tab (Story 5620): drafts, not mail -- its own rows, its own ids, its own card. */
    function isDrafts() { return S.tab === 'drafts'; }
    function rowIsSent(m) { return !!(m && m.kind === 'sent'); }
    function msgUrl(id, sent) { return API + (sent ? '/sent/' : '/messages/') + id; }

    /** "Pat Smith <pat@x.com>, lee@y.com" -> "Pat Smith, lee@y.com" (commas inside quotes kept). */
    function toNames(to) {
        return String(to || '').split(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/).map(function (a) { return displayName(a); })
            .filter(function (a) { return a; }).join(', ');
    }

    /** yyyy-MM-ddTHH:mm:ss -> MM/dd/yyyy h:mm am */
    function fmtWhen(iso) {
        var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso || '');
        if (!m) return iso || '';
        var h = +m[4], ap = h < 12 ? 'am' : 'pm';
        h = h % 12 || 12;
        return m[2] + '/' + m[3] + '/' + m[1] + ' ' + h + ':' + m[5] + ' ' + ap;
    }

    /** Today -> the time; this year -> MM/dd; older -> MM/dd/yyyy. */
    function shortWhen(iso) {
        var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso || '');
        if (!m) return '';
        var now = new Date();
        var today = now.getFullYear() === +m[1] && now.getMonth() + 1 === +m[2] && now.getDate() === +m[3];
        if (today) { var h = +m[4]; return (h % 12 || 12) + ':' + m[5] + ' ' + (h < 12 ? 'am' : 'pm'); }
        return m[2] + '/' + m[3] + (now.getFullYear() === +m[1] ? '' : '/' + m[1]);
    }

    function pad2(n) { return (n < 10 ? '0' : '') + n; }

    /** A local day as yyyy-mm-dd, days from today. */
    function isoDay(daysFromToday) {
        var d = new Date();
        d.setDate(d.getDate() + (daysFromToday || 0));
        return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
    }

    /** yyyy-mm-dd -> MM/dd/yyyy */
    function fmtDay(iso) {
        var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
        return m ? m[2] + '/' + m[3] + '/' + m[1] : (iso || '');
    }

    /** overdue | today | later, for a follow-up day. */
    function dueState(iso) {
        var today = isoDay(0);
        return iso < today ? 'od' : iso === today ? 'today' : 'later';
    }

    /** "Pat Smith <pat@x.com>" -> "Pat Smith"; a bare address stays an address. */
    function displayName(from) {
        var s = String(from || '').trim();
        var m = /^"?([^"<]+?)"?\s*<[^>]+>$/.exec(s);
        return m ? m[1].trim() : s;
    }

    function note(text, isErr, undoIds, undoSent) {
        var n = el('note');
        window.clearTimeout(flashTimer);
        if (!text) { n.hidden = true; n.innerHTML = ''; n.className = 'gc-note'; return; }
        n.innerHTML = '<span>' + esc(text) + '</span>'
            + (undoIds ? '<button type="button" class="gc-link" data-undo="' + esc(undoIds.join(',')) + '"'
                + (undoSent ? ' data-undo-sent="1"' : '') + ' data-testid="gmail-client-undo">Undo</button>' : '');
        n.className = 'gc-note' + (isErr ? ' err' : '');
        n.hidden = false;
    }

    // ---- status and checking --------------------------------------------------------------

    function loadStatus() {
        return Promise.resolve(A.apiGet(API + '/status')).then(function (r) {
            if (!r || !r.ok) { el('status').textContent = window.LWAuth.errorText(r, 'Could not read the Email Client status.'); return; }
            var wasChecking = !!(S.status && S.status.checking);
            S.status = r.data;
            drawStatus();
            drawReach();
            if (r.data.checking) followCheck();
            else if (wasChecking) { checkFinished(r.data.lastCheck); refreshList(); }
        });
    }

    function drawStatus() {
        var s = S.status, html;
        if (!s.connected) {
            // Story 5654: the mail is in Outlook, which must be running on this computer.
            html = esc(s.outlookError || 'Outlook is not running on this computer.')
                + ' Open Outlook, then tap Check mail. The mail already in Echo is below.';
        } else {
            html = 'Outlook: <b>' + esc(s.account) + '</b>';
            if (s.checking && s.checking.bodiesToSave) {
                // Story 5576: after the headers, each message is saved whole in Leonardo.
                html += ' | saving messages in Echo: ' + s.checking.bodiesSaved + ' of ' + s.checking.bodiesToSave;
            } else if (s.checking) {
                html += ' | checking now: ' + plural(s.checking.messagesListed, 'message') + ' looked at, '
                    + s.checking.messagesAdded + ' new';
            } else if (s.lastCheck) {
                html += ' | checked ' + esc(fmtWhen(s.lastCheck.startedDate));
                if (s.lastCheck.error) html += ' <span class="gc-err">(' + esc(s.lastCheck.error) + ')</span>';
                if (s.notYetSaved) html += ' | ' + plural(s.notYetSaved, 'message') + ' not yet saved in Echo -- Check mail saves them';
            }
        }
        el('status').innerHTML = html;
        el('btnCheck').hidden = false;   // Story 5654: Check mail is the way to try again once Outlook is open
        el('btnCompose').hidden = !s.connected;
        el('btnCheck').disabled = !!s.checking;
        el('btnCheck').textContent = s.checking ? 'Checking' : 'Check mail';
        el('btnOlder').disabled = !!s.checking;
    }

    var followTimer = 0;
    function followCheck() {
        if (followTimer) return;
        followTimer = window.setTimeout(function () { followTimer = 0; loadStatus(); }, 3000);
    }

    function checkFinished(c) {
        if (!c) return;
        note('');   // whatever the page was saying before the check may no longer apply
        if (c.error) note('Check mail stopped: ' + c.error, true);
        else if (c.undecidedAdded) {
            // Mail from a sender not decided on in Mail Triage lands only in Undecided -- say so, or it
            // looks like the check found mail that is nowhere (2026-10-04).
            flash(plural(c.messagesAdded, 'new message') + ' -- ' + c.undecidedAdded + ' from '
                + (c.undecidedAdded === 1 ? 'a sender' : 'senders') + ' you have not decided on yet. '
                + 'They are in the Undecided tab: whitelist a sender there and its mail moves into your inboxes.');
        }
        // A background check reports on the status line -- not a dialog that interrupts -- and,
        // since it needs nothing done, fades after a few seconds (Tony's exception, Story 5608).
        else if (c.messagesAdded) flash(plural(c.messagesAdded, 'new message'));
        else if (c.sentAdded) flash(plural(c.sentAdded, 'sent message') + ' added to Sent');
        else if (c.draftsAdded) flash(plural(c.draftsAdded, 'draft') + ' from Outlook added to Drafts');
        // Story 5620: reading Gmail's Drafts never stops a check, but a failure there is said, and stays.
        if (!c.error && c.draftError) note(c.draftError, true);
    }

    /** Check mail (url /check), or read older mail (url /check-older). */
    function runCheck(url, body, quiet) {
        // Story 5654: Outlook may have been opened since the page said it was not running -- ask again.
        if (!S.status || !S.status.connected) {
            return loadStatus().then(function () {
                if (S.status && S.status.connected) return runCheck(url, body, quiet);
                note((S.status && S.status.outlookError) || 'Outlook is not running on this computer. Open Outlook, then try again.', true);
            });
        }
        // A check changes what is in the list, so an earlier message (a delete with its Undo, a count of
        // new mail, a "still checking") may no longer be true: clear it now; the check says what it found.
        note('');
        el('btnCheck').disabled = true;
        el('btnCheck').textContent = 'Checking';
        el('btnOlder').disabled = true;
        return Promise.resolve(A.apiPost(url, body || {})).then(function (r) {
            if (!r || !r.ok) {
                el('btnCheck').disabled = false;
                el('btnCheck').textContent = 'Check mail';
                el('btnOlder').disabled = false;
                note(errText(r, 'Check mail did not finish.'), true);
                loadStatus();
                return;
            }
            if (r.data.checking) {
                if (!quiet) note('Still checking. The first check reads a month of mail from Outlook, so it can take a few '
                    + 'minutes; new mail appears when it is done.');
                return loadStatus();
            }
            checkFinished(r.data);
            return loadStatus().then(refreshList);
        });
    }

    function checkMail(quiet) { return runCheck(API + '/check', {}, quiet); }
    function readOlder() { return runCheck(API + '/check-older', { days: OLDER_DAYS }, false); }

    /** The foot of the list: how far back mail has been read, and a way to read further. */
    function drawReach() {
        var s = S.status;
        var show = !!(s && s.connected && !S.more);
        el('reach').hidden = !show;
        if (!show) return;
        el('reachText').textContent = s.reachDate
            ? 'Mail read back to ' + fmtDay(s.reachDate) + '.'
            : 'No mail has been read yet.';
        el('btnOlder').textContent = 'Read ' + OLDER_DAYS + ' more days';
    }

    // ---- tabs and the list ----------------------------------------------------------------

    // ---- how far back (remembered) ----------------------------------------------------------

    /** The first day the range shows (yyyy-mm-dd), or '' for any time. Weeks start on Sunday. */
    function rangeAfter() {
        var d = new Date();
        switch (S.range) {
            case 'today': return isoDay(0);
            case 'week':  return isoDay(-d.getDay());
            case 'month': return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-01';
            case 'year':  return d.getFullYear() + '-01-01';
            case 'days':  return S.days > 0 ? isoDay(-S.days) : '';
            default:      return '';
        }
    }

    var drawingRange = false;   // PicassoSelect.refresh can fire change; never take that as a choice

    function drawRange() {
        var sel = el('rangeSel');
        drawingRange = true;
        try {
            sel.value = S.range;
            if (window.PicassoSelect && PicassoSelect.refresh) PicassoSelect.refresh(sel);
        } finally { drawingRange = false; }
        el('rangeDays').value = S.range === 'days' && S.days > 0 ? String(S.days) : '';
        el('rangeWrap').classList.toggle('on', S.range !== 'any');
    }

    function setRange(range, days) {
        S.range = RANGES.indexOf(range) >= 0 ? range : 'any';
        S.days = S.range === 'days' ? days : 0;
        if (S.range === 'days' && !(S.days > 0)) S.range = 'any';
        remember(VS_RANGE, S.range);
        remember(VS_DAYS, S.range === 'days' ? String(S.days) : '');
        drawRange();
        el('body').scrollTop = 0;
        return loadList();
    }

    /** The typed days: 1 to 3,650; blank goes back to Any time. */
    function takeDays() {
        var raw = el('rangeDays').value.trim();
        if (!raw) { if (S.range === 'days') setRange('any'); return; }
        var n = parseInt(raw, 10);
        if (!(n >= 1 && n <= MAX_DAYS) || String(n) !== raw) {
            note('Type a number of days from 1 to ' + MAX_DAYS.toLocaleString() + '.', true);
            drawRange();
            return;
        }
        note('');
        if (S.range === 'days' && S.days === n) return;
        setRange('days', n);
    }

    /** The filters as query parameters (the read choice and the range included). */
    function filterParams() {
        var p = {};
        // Drafts are searched by their words only (who it is to, the subject, the start of the text).
        if (isDrafts()) { if (S.q) p.q = S.q; return p; }
        function add(k, v) { if (v !== undefined && v !== null && String(v).trim() !== '') p[k] = String(v).trim(); }
        add('q', S.q);
        var c = S.custom, after = rangeAfter();
        if (c) {
            add('from', c.from); add('to', c.to); add('subject', c.subject); add('words', c.words);
            if (c.after && c.after > after) after = c.after;
            add('before', c.before);
            if (c.unread) p.unread = 'true';
        }
        add('after', after);
        if (S.flagFilter.length) p.flags = S.flagFilter.join(',');
        if (isSent()) {
            // Sent mail is all Tony's: no From and no read / unread, but it can be waiting for a reply.
            // It carries no flags either.
            delete p.from;
            delete p.unread;
            delete p.flags;
            if (S.reply !== 'all') p.reply = S.reply;
        } else if (S.read !== 'all') p.read = S.read;
        return p;
    }

    function scopeParams() {
        if (S.tab === 'unclassified' || S.tab === 'followup' || S.tab === 'undecided' || S.tab === 'flagged') return { scope: S.tab };
        var m = /^inbox:(\d+)$/.exec(S.tab);
        return m ? { scope: 'inbox', inbox: m[1] } : { scope: 'all' };
    }

    function toQuery(o) {
        return Object.keys(o).map(function (k) { return k + '=' + encodeURIComponent(o[k]); }).join('&');
    }

    /** The list shows conversations (Story 5608) -- never on the Sent tab, which lists what went. */
    function threadsOn() { return S.threads && !isSent() && !isDrafts(); }

    /** One page: append = the next page, size = how many (at most 200). */
    function fetchPage(offset, size) {
        if (isSent() || isDrafts()) {
            return Promise.resolve(A.apiGet(API + (isSent() ? '/sent?' : '/drafts?')
                + toQuery(Object.assign({}, filterParams(), { offset: offset, size: size }))));
        }
        var q = Object.assign({}, scopeParams(), filterParams(), { offset: offset, size: size });
        return Promise.resolve(A.apiGet(API + (threadsOn() ? '/threads?' : '/messages?') + toQuery(q)));
    }

    function loadList(append) {
        return fetchPage(append ? S.rows.length : 0, PAGE_SIZE).then(function (r) { return takePage(r, append); });
    }

    /** Reload what is shown (up to 200 rows) after a change, so a long list keeps its place. */
    function refreshList() {
        var size = Math.max(PAGE_SIZE, Math.min(S.rows.length, MAX_PAGE));
        return fetchPage(0, size).then(function (r) { return takePage(r, false); });
    }

    function takePage(r, append) {
        if (!r || !r.ok) {
            // A remembered inbox that was deleted: fall back to All.
            if (r && r.status === 400 && S.tab !== 'all') { setTab('all'); return false; }
            el('list').innerHTML = '<div class="gc-empty">' + esc(errText(r, 'Could not load the mail.')) + '</div>';
            return false;
        }
        S.rows = append ? S.rows.concat(r.data.rows) : r.data.rows;
        S.more = !!r.data.hasMore;
        S.tabs = r.data.tabs || [];
        S.totalThreads = r.data.totalThreads || 0;
        S.searchEcho = r.data.search || null;
        drawFilterLine();
        if (!tabExists(S.tab)) { setTab('all'); return false; }
        pruneSelection();
        drawTabs();
        drawList();
        return true;
    }

    /** Load more, page after page, until the tab is all loaded (or 2,000 rows). */
    function loadAll() {
        var b = el('btnMoreAll');
        b.disabled = true;
        el('btnMore').disabled = true;
        function step() {
            if (!S.more || S.rows.length >= LOAD_ALL_CAP) return Promise.resolve();
            return fetchPage(S.rows.length, MAX_PAGE).then(function (r) {
                return takePage(r, true) ? step() : null;
            });
        }
        return step().then(function () {
            b.disabled = false;
            el('btnMore').disabled = false;
            if (S.more) note('Showing the first ' + S.rows.length + '. Narrow it with a search to see the rest.');
        });
    }

    function tabKey(t) { return t.scope === 'inbox' ? 'inbox:' + t.inboxId : t.scope; }
    function tabExists(key) { return S.tabs.some(function (t) { return tabKey(t) === key; }); }
    function currentTab() { return S.tabs.filter(function (t) { return tabKey(t) === S.tab; })[0]; }

    function drawTabs() {
        el('tabs').innerHTML = S.tabs.map(function (t) {
            var key = tabKey(t);
            var badge = t.overdue
                ? '<span class="u od" title="overdue">' + t.overdue + '</span>'
                : t.unread ? '<span class="u" title="unread">' + t.unread + '</span>'
                : t.waiting ? '<span class="u wait" title="no reply yet">' + t.waiting + '</span>' : '';
            // Undecided reads red while it holds anything still waiting on a decision.
            var cls = (key === S.tab ? 'on' : '') + (t.scope === 'undecided' && t.count > 0 ? ' gc-pending' : '');
            return '<button type="button" role="tab" data-tab="' + esc(key) + '" class="' + cls.trim() + '"'
                + ' aria-selected="' + (key === S.tab) + '" data-testid="gmail-client-tab">'
                + esc(t.name) + ' <span class="n">(' + t.count + ')</span>' + badge + '</button>';
        }).join('');
        var on = el('tabs').querySelector('button.on');
        if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }

    /** The heading a row falls under: by day received, or (Follow up) by when it is due. */
    function groupOf(m) {
        if (S.tab === 'flagged') return m.firstFlagName || 'Flagged';   // grouped by flag (Story 5608)
        if (S.tab === 'followup') {
            var st = m.followUpDate ? dueState(m.followUpDate) : 'later';
            return st === 'od' ? 'Overdue' : st === 'today' ? 'Due today' : 'Coming up';
        }
        var day = (m.receivedDate || '').substring(0, 10);
        if (day === isoDay(0)) return 'Today';
        if (day === isoDay(-1)) return 'Yesterday';
        if (day >= isoDay(-6)) return 'Earlier this week';
        if (day >= isoDay(-30)) return 'Earlier this month';
        return 'Older';
    }

    function followUpPill(m) {
        if (!m.followUpDate) return '';
        var st = dueState(m.followUpDate);
        return '<span class="gc-pill fu' + (st === 'later' ? '' : ' ' + st) + '" data-testid="gmail-client-row-followup-pill">'
            + (st === 'od' ? 'Overdue ' : 'Follow up ') + esc(fmtDay(m.followUpDate)) + '</span>';
    }

    /** Whitelist / Blacklist for a sender not decided on yet (the Undecided tab and the reader). */
    function decideBar(senderKey, tid) {
        if (!senderKey) return '';
        return '<div class="gc-decide" data-testid="' + tid + '-decide">'
            + '<span>' + esc(senderKey) + ' is not decided on</span>'
            + '<button type="button" class="gc-btn" data-decide="white" data-sender="' + esc(senderKey) + '"'
            + ' data-testid="' + tid + '-whitelist">Whitelist</button>'
            + '<button type="button" class="gc-btn danger" data-decide="black" data-sender="' + esc(senderKey) + '"'
            + ' data-testid="' + tid + '-blacklist">Blacklist</button>'
            + '</div>';
    }

    /** Mail Triage's decision, made here. Whitelisted mail moves into the inboxes; blacklisted mail goes. */
    function decideSender(sender, side, btn) {
        var count = S.rows.filter(function (r) { return r.senderKey === sender; }).length;
        var msg = side === 'white'
            ? 'Whitelist ' + sender + '?\n\nIts mail' + (count ? ' (' + plural(count, 'message') + ' here)' : '')
              + ' moves out of Undecided into your inboxes, and its new mail shows from now on.'
            : 'Blacklist ' + sender + '?\n\nIts mail' + (count ? ' (' + plural(count, 'message') + ' here)' : '')
              + ' leaves the Email Client, and its new mail is never read in. Nothing changes in Outlook -- the list'
              + ' is kept in Echo only. Undo it under Lists.';   // Story 5654: no Outlook rule, ever
        if (!window.confirm(msg)) return Promise.resolve();
        if (btn) btn.disabled = true;
        return Promise.resolve(A.apiPost(API + '/senders/decide', { sender: sender, disposition: side })).then(function (r) {
            if (btn) btn.disabled = false;
            if (!r || !r.ok) { note(errText(r, 'Could not ' + (side === 'white' ? 'whitelist ' : 'blacklist ') + sender), true); return; }
            flash(sender + (side === 'white' ? ' is whitelisted' : ' is blacklisted'));
            if (r.data && r.data.warning) note(r.data.warning, true);
            if (S.open && S.open.senderKey === sender) { S.open.senderDecided = true; drawDecide(); }
            return refreshList();
        });
    }

    /** A sent message's reply state: green Replied (with the count), or grey No reply yet. */
    function replyPill(m) {
        return m.replyCount
            ? '<span class="gc-pill rp" title="Last reply ' + esc(fmtWhen(m.lastReplyDate)) + '" data-testid="gmail-client-row-replied">Replied'
              + (m.replyCount > 1 ? ' (' + m.replyCount + ')' : '') + '</span>'
            : '<span class="gc-pill wait" data-testid="gmail-client-row-waiting">No reply yet</span>';
    }

    /**
     * Story 5597: the address of this message in Gmail itself. The Gmail id the API gave us is
     * the same id Gmail's web page uses, and authuser picks the connected account even when the
     * browser is signed in to several. Empty when the row has no Gmail id.
     */
    function gmailUrl(m) {
        if (ECHO) return '';   // Story 5654: Outlook mail has no web address to open
        if (!m || !m.gmailId) return '';
        var acct = S.status && S.status.account;
        return 'https://mail.google.com/mail/' + (acct ? '?authuser=' + encodeURIComponent(acct) : 'u/0/')
            + '#all/' + encodeURIComponent(m.gmailId);
    }

    /** The row's View in Gmail link, beside View. Opens Gmail in a new tab (the Gmail app on a phone). */
    function gmailLinkHtml(m) {
        var url = gmailUrl(m);
        if (!url) return '';
        return '<a class="gc-qv gc-gv" href="' + esc(url) + '" target="_blank" rel="noopener" data-gmail="1"'
            + ' title="View in Gmail" aria-label="View in Gmail" data-testid="gmail-client-row-gmail">Gmail</a>';
    }

    /** Point a View in Gmail link at a message, or hide it when there is nowhere to go. */
    function setGmailLink(a, m) {
        var url = gmailUrl(m);
        a.hidden = !url;
        if (url) a.href = url; else a.removeAttribute('href');
    }

    /**
     * A Drafts tab row (Story 5620): who it is to (or what it answers), when it was last changed, the
     * subject and the start of the text. Tap it to carry on writing; Discard asks first.
     */
    function draftCardHtml(m) {
        var what = { reply: 'Reply', replyAll: 'Reply all', forward: 'Forward', writeAgain: 'Write again', forwardSent: 'Forward' }[m.mode];
        var who = m.to ? 'To ' + toNames(m.to) : what ? what : 'To (no one yet)';
        return '<div class="gc-card gc-draft" data-id="' + m.id + '" data-draft="' + m.id + '" role="button" tabindex="0" data-testid="gmail-client-draft-row">'
            + '<div class="gc-row1"><span class="gc-from"><span class="gc-draft-tag">Draft</span> ' + esc(who) + '</span>'
            + '<span class="gc-when">' + esc(shortWhen(m.modifiedDate)) + '</span>'
            + '<button type="button" class="gc-qv" data-discard="' + m.id + '" title="Discard this draft"'
            + ' data-testid="gmail-client-draft-discard">Discard</button></div>'
            + '<div class="gc-subject">' + esc(m.subject || '(no subject)')
            + (what && m.to ? '<span class="gc-pill">' + esc(what) + '</span>' : '')
            + (m.fromGmail ? '<span class="gc-pill" title="Started in Outlook itself" data-testid="gmail-client-draft-from-gmail">From Outlook</span>' : '')
            + (m.attachmentCount ? '<span class="gc-clip" title="' + plural(m.attachmentCount, 'attachment') + '" aria-label="Has attachments">&#128206;</span>' : '')
            + '</div>'
            + (m.snippet ? '<div class="gc-snippet">' + esc(m.snippet) + '</div>' : '')
            + '</div>';
    }

    function cardHtml(m) {
        if (isDrafts()) return draftCardHtml(m);
        var picked = !!S.sel[m.id];
        var sent = rowIsSent(m);
        var who = sent ? 'To ' + (toNames(m.to) || '(no one)') : (displayName(m.from) || m.senderKey);
        // Conversations (Story 5608): "Greg, me" when Tony wrote back, and how many messages there are.
        if (threadsOn() && m.sentCount) who += ', me';
        var tcount = threadsOn() && m.threadCount > 1
            ? '<span class="gc-tcount" title="' + m.threadCount + ' messages in this conversation"'
              + ' data-testid="gmail-client-row-thread-count">' + m.threadCount + '</span>' : '';
        // A color fills the whole row (Story 5608 QA); a flag's color stays on its chip.
        var tint = S.look && !sent ? S.look.tintFor(m) : null;
        // No inbox name on the row (Tony, 2026-10-09: distracting); the open message shows its inbox.
        var main = '<div class="gc-row1"><span class="gc-from">' + esc(who) + '</span>' + tcount
            + '<span class="gc-when">' + esc(shortWhen(m.receivedDate)) + '</span>'
            + (S.selecting ? '' : '<button type="button" class="gc-qv" data-quick="' + m.id + '" aria-label="Quick view"'
                + ' title="Quick view" data-testid="gmail-client-row-quickview">View</button>'
                + gmailLinkHtml(m))
            + '</div>'
            + '<div class="gc-subject">' + esc(m.subject || '(no subject)')
            + (sent ? replyPill(m) : '')
            + (S.tab !== 'followup' ? followUpPill(m) : '')
            + (m.hasAttachments ? '<span class="gc-clip" title="Has attachments" aria-label="Has attachments"'
                + ' data-testid="gmail-client-row-clip">&#128206;</span>' : '')
            + (m.linkCount ? '<span class="gc-clip" title="Linked to ' + plural(m.linkCount, 'record') + '" aria-label="Linked to a record"'
                + ' data-testid="gmail-client-row-linked">&#128279;</span>' : '')
            + (S.look && m.flagIds && m.flagIds.length ? S.look.flagChipsHtml(m.flagIds) : '')
            + '</div>'
            + (S.tab === 'followup' && m.followUpDate
                ? '<div class="gc-fu-note">' + followUpPill(m) + (m.followUpNote ? ' ' + esc(m.followUpNote) : '') + '</div>' : '')
            + (m.snippet ? '<div class="gc-snippet">' + esc(m.snippet) + '</div>' : '')
            + (S.tab === 'undecided' && !S.selecting ? decideBar(m.senderKey, 'gmail-client-row') : '');
        var card = '<div class="gc-card' + (m.isRead ? '' : ' unread') + (S.selecting ? ' sel-row' : '') + (picked ? ' picked' : '')
            + (previewLive() && m.id === S.pv ? ' pv-on' : '') + (tint ? ' tinted tint-' + tint.style + ' ink-' + tint.ink : '') + '"'
            + (tint ? ' style="--tint:' + tint.hex + ';--tint-bg:' + esc(tint.bg) + ';--tint-edge:' + tint.edge + '"' : '')
            + ' data-id="' + m.id + '" role="button" tabindex="0" data-testid="gmail-client-row">'
            + (S.selecting
                ? '<input type="checkbox" class="gc-check" aria-label="Select" data-testid="gmail-client-row-check"' + (picked ? ' checked' : '') + '>'
                  + '<div class="gc-card-main">' + main + '</div>'
                : main)
            + '</div>';
        return '<div class="gc-swipe" data-id="' + m.id + '">'
            + '<div class="gc-acts-l">' + (sent ? '' : m.isRead ? 'Unread' : 'Read') + '</div>'
            + '<div class="gc-acts">'
            + '<button type="button" class="fu" data-swipe="followup" data-testid="gmail-client-row-followup">Follow up</button>'
            + '<button type="button" class="del" data-swipe="delete" data-testid="gmail-client-row-delete">Delete</button>'
            + '</div>' + card + '</div>';
    }

    function drawList() {
        var t = currentTab(), total = threadsOn() ? S.totalThreads : (t ? t.count : S.rows.length);
        drawTabTools();
        if (!S.rows.length) {
            var filtering = isDrafts() ? !!S.q
                : S.q || S.custom || (!isSent() && (S.read !== 'all' || S.flagFilter.length)) || S.range !== 'any';
            el('list').innerHTML = '<div class="gc-empty" data-testid="gmail-client-empty">'
                + (filtering ? (isDrafts() ? 'No draft matches.' : 'No mail matches.')
                    : isDrafts() ? 'No drafts. A message you close without sending waits here -- and so does one you start in Outlook, once Check mail has read it.'
                    : isSent() && S.reply === 'waiting' ? 'Every message you sent has had a reply.'
                    : isSent() ? 'Nothing sent yet. Mail you send -- from here, or from Outlook once Check mail has read it -- shows here.'
                    : S.tab === 'all' ? 'No mail yet. Only senders you whitelisted show here -- new senders wait on the Undecided tab.'
                    : S.tab === 'followup' ? 'Nothing to follow up. Flag a message with Follow up and it waits here until its day.'
                    : 'No mail in ' + esc(t ? t.name : 'this inbox') + '.') + '</div>';
        } else {
            var html = S.rows.length < total
                ? '<div class="gc-total">' + S.rows.length + ' of ' + total + (threadsOn() ? ' conversations' : '') + ' shown</div>' : '';
            var last = null;
            S.rows.forEach(function (m) {
                var g = groupOf(m);
                if (g !== last) {
                    html += '<div class="gc-day' + (g === 'Overdue' ? ' od' : '') + '" data-testid="gmail-client-day">'
                        + (S.tab === 'flagged' ? flagHeading(g) : esc(g)) + '</div>';
                    last = g;
                }
                html += cardHtml(m);
            });
            el('list').innerHTML = html;
        }
        el('more').hidden = !S.more;
        drawReach();
        drawSelbar();
        if (previewLive()) syncPreview();
    }

    function setTab(key) {
        S.tab = key;
        remember(VS_TAB, key);
        el('body').scrollTop = 0;
        return loadList();
    }

    function setRead(state) {
        S.read = state;
        remember(VS_READ, state);
        drawReadSeg();
        el('body').scrollTop = 0;
        return loadList();
    }

    /**
     * The Sent tab has no read / unread: All / Unread, Mark all and the Read / Unread selection buttons
     * give way to All sent / No reply yet (Story 5596).
     */
    function drawTabTools() {
        var sent = isSent(), drafts = isDrafts();
        el('readSeg').hidden = sent || drafts;
        el('replySeg').hidden = !sent;
        el('btnMarkAll').closest('.gc-menu-wrap').hidden = sent || drafts;
        // Drafts (Story 5620): no how-far-back, no Select -- a draft is opened, sent or discarded one at a time.
        el('rangeWrap').hidden = drafts;
        el('btnSelect').hidden = drafts;
        el('btnView').hidden = drafts;
        if (S.view === 'preview' && (drafts || el('preview').hidden)) showPreviewPane(!drafts);
        Array.prototype.forEach.call(el('selbar').querySelectorAll('[data-bulk="read"], [data-bulk="unread"], [data-bulk="flag"]'), function (b) {
            b.hidden = sent;
        });
        el('btnThreads').hidden = sent || drafts;   // Sent lists what went; conversations are for the inbox tabs
        drawFlagFilter();                 // and sent mail carries no flags
        Array.prototype.forEach.call(el('replySeg').querySelectorAll('[data-reply]'), function (b) {
            var on = b.getAttribute('data-reply') === S.reply;
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', String(on));
        });
        el('search').placeholder = drafts ? 'Search drafts' : sent ? 'Search sent mail, or to:greg has:attachment' : 'Search all mail, or from:greg has:attachment';
    }

    function setReply(state) {
        S.reply = state === 'waiting' ? 'waiting' : 'all';
        el('body').scrollTop = 0;
        return loadList();
    }

    function drawReadSeg() {
        Array.prototype.forEach.call(el('readSeg').querySelectorAll('[data-read]'), function (b) {
            var on = b.getAttribute('data-read') === S.read;
            b.classList.toggle('on', on);
            b.setAttribute('aria-pressed', String(on));
        });
    }

    function drawFilterLine() {
        var c = S.custom, parts = [];
        if (c) {
            if (c.from) parts.push('from "' + c.from + '"');
            if (c.to) parts.push('to "' + c.to + '"');
            if (c.subject) parts.push('subject "' + c.subject + '"');
            if (c.words) parts.push('words "' + c.words + '"');
            if (c.after) parts.push('on or after ' + c.after);
            if (c.before) parts.push('on or before ' + c.before);
            if (c.unread) parts.push('unread only');
        }
        // Story 5608: the server says back how it read the search box (from:, has:attachment ...).
        var echo = S.q && S.searchEcho ? S.searchEcho : null;
        if (S.q) parts.unshift(echo && echo.understood && echo.understood.length ? echo.understood.join(', ') : '"' + S.q + '"');
        el('filterLine').hidden = !parts.length;
        var text = parts.length ? 'Searching ' + (isSent() ? 'sent' : 'all') + ' mail: ' + parts.join(', ') : '';
        if (echo && echo.notes && echo.notes.length) text += '. ' + echo.notes.join(' ');
        el('filterText').textContent = text;
    }

    /** The search box's operators, on the ? button and More -> Search tips. */
    function showSearchTips() {
        window.Picasso.message('Every word you type must appear in the sender, the subject or the preview. Put a phrase in quotes.\n\n'
            + 'from:greg   or   FROM: Greg   -- who it is from (a name, address or domain)\n'
            + 'to:greg   -- sent to or copied to\n'
            + 'subject:invoice\n'
            + 'has:attachment   (-has:attachment for none)\n'
            + 'is:unread   or   is:read\n'
            + 'flag:taxes   -- a flag, or the start of its name\n'
            + 'after:2026-10-01   before:10/31/2026\n'
            + 'newer_than:3d   older_than:2w   (d, w, m, y)\n\n'
            + 'Put them together: has:attachment AND from:greg. AND is optional -- every part must match. OR is not understood.',
            { title: 'Search tips' });
    }

    // ---- folding (remembered) -------------------------------------------------------------

    function applyHeaderFold(folded) {
        document.querySelector('.gc-page').classList.toggle('gc-folded', folded);
        var b = el('btnFold');
        b.innerHTML = folded ? '&#9660;' : '&#9650;';
        b.setAttribute('aria-expanded', String(!folded));
        b.setAttribute('aria-label', folded ? 'Unfold the header' : 'Fold the header');
        b.title = b.getAttribute('aria-label');
    }

    function applyDetailsFold(folded) {
        el('rHead').classList.toggle('folded', folded);
        var b = el('btnHeadFold');
        b.innerHTML = folded ? '&#9660;' : '&#9650;';
        b.setAttribute('aria-expanded', String(!folded));
        b.setAttribute('aria-label', folded ? 'Show the message details' : 'Fold the message details');
        b.title = b.getAttribute('aria-label');
    }

    /**
     * The reading buttons (Reply ... Delete) fold behind an Actions toggle on the phone. The class
     * is set on every screen, but only the phone CSS acts on it, so the PC never changes.
     */
    function applyActionsFold(folded) {
        el('reader').classList.toggle('gc-actions-folded', folded);
        var b = el('btnActionsFold');
        b.querySelector('.gc-actions-arrow').innerHTML = folded ? '&#9660;' : '&#9650;';
        b.setAttribute('aria-expanded', String(!folded));
        b.setAttribute('aria-label', folded ? 'Show the message buttons' : 'Fold the message buttons');
        b.title = b.getAttribute('aria-label');
    }

    // ---- selecting many -------------------------------------------------------------------

    function selectedIds() { return Object.keys(S.sel).map(Number); }

    function pruneSelection() {
        var shown = {};
        S.rows.forEach(function (m) { shown[m.id] = true; });
        Object.keys(S.sel).forEach(function (id) { if (!shown[id]) delete S.sel[id]; });
    }

    function startSelecting(firstId) {
        S.selecting = true;
        S.sel = {};
        if (firstId) S.sel[firstId] = true;
        closeSwipes();
        drawList();
    }

    function stopSelecting() {
        S.selecting = false;
        S.sel = {};
        drawList();
    }

    function toggleSelect(id) {
        if (S.sel[id]) delete S.sel[id]; else S.sel[id] = true;
        var card = el('list').querySelector('.gc-card[data-id="' + id + '"]');
        if (card) {
            card.classList.toggle('picked', !!S.sel[id]);
            var box = card.querySelector('.gc-check');
            if (box) box.checked = !!S.sel[id];
        }
        drawSelbar();
    }

    function drawSelbar() {
        el('tools').hidden = S.selecting;
        el('selbar').hidden = !S.selecting;
        if (!S.selecting) return;
        var n = selectedIds().length;
        el('selCount').textContent = n + ' selected';
        el('selAll').checked = n > 0 && n === S.rows.length;
        el('selAll').indeterminate = n > 0 && n < S.rows.length;
        Array.prototype.forEach.call(el('selbar').querySelectorAll('[data-bulk]'), function (b) {
            if (b.getAttribute('data-bulk') !== 'cancel') b.disabled = n === 0;
        });
    }

    function rowsFor(ids) {
        var want = {};
        ids.forEach(function (id) { want[id] = true; });
        return S.rows.filter(function (m) { return want[m.id]; });
    }

    /** "Lunch" from A Friend, "Your account" from Midland and 3 more */
    function describe(rows) {
        var named = rows.slice(0, 2).map(function (m) {
            return '"' + (m.subject || '(no subject)') + '"' + (rowIsSent(m) ? ' to ' + toNames(m.to) : ' from ' + displayName(m.from));
        });
        return named.join(', ') + (rows.length > 2 ? ' and ' + (rows.length - 2) + ' more' : '');
    }

    /**
     * read | unread | delete | restore for these ids, then the list catches up. sent (default: the
     * Sent tab is showing) sends it to the sent mail's own endpoint, which takes delete and restore.
     */
    function bulk(ids, action, button, sent) {
        if (sent === undefined) sent = isSent();
        if (button) button.disabled = true;
        return Promise.resolve(A.apiPost(API + (sent ? '/sent/bulk' : '/messages/bulk'), { ids: ids, action: action })).then(function (r) {
            if (button) button.disabled = false;
            if (!r || !r.ok) { note(errText(r, 'That did not work.'), true); return false; }
            return true;
        });
    }

    /**
     * In the conversation view a row IS its conversation (Story 5608): Read, Unread, Delete, Follow
     * up and Flag on it act on every message of it. Resolves to the ids to act on, or null when the
     * server could not say (a note says why).
     */
    function expandIds(ids) {
        if (!threadsOn() || !ids.length) return Promise.resolve(ids);
        return Promise.resolve(A.apiPost(API + '/threads/messages', { ids: ids })).then(function (r) {
            if (!r || !r.ok) { note(errText(r, 'Could not read those conversations.'), true); return null; }
            return r.data.ids && r.data.ids.length ? r.data.ids : ids;
        });
    }

    /** shownIds: the rows picked, when ids holds their whole conversations (the conversation view). */
    function deleteMany(ids, button, shownIds) {
        var rows = rowsFor(shownIds || ids);
        if (!rows.length) return Promise.resolve(false);
        var convs = shownIds && ids.length > shownIds.length;
        var what = convs ? plural(rows.length, 'conversation') + ' (' + plural(ids.length, 'message') + '): ' + describe(rows)
            : rows.length === 1 ? describe(rows) + ' (' + fmtWhen(rows[0].receivedDate) + ')' : plural(rows.length, 'message') + ': ' + describe(rows);
        var sent = isSent();
        return Promise.resolve(window.Picasso.confirmDelete('Delete ' + what + '?\n\nThey leave the Email Client only. '
            + 'Outlook keeps them, and Undo brings them back.', { title: 'Delete' })).then(function (yes) {
            if (!yes) return false;
            return bulk(ids, 'delete', button, sent).then(function (ok) {
                if (!ok) return false;
                note(convs ? 'Deleted ' + plural(rows.length, 'conversation') + '.'
                    : rows.length === 1 ? 'Deleted "' + (rows[0].subject || '(no subject)') + '".' : 'Deleted ' + plural(rows.length, 'message') + '.', false, ids, sent);
                return true;
            });
        });
    }

    function bulkAction(e) {
        var b = e.target.closest('[data-bulk]');
        if (!b) return;
        var act = b.getAttribute('data-bulk'), picked = selectedIds();
        if (act === 'cancel') { stopSelecting(); return; }
        if (!picked.length) return;
        expandIds(picked).then(function (ids) {
            if (!ids) return;
            if (act === 'followup') { openFollowUp(ids, isSent()); return; }
            if (act === 'flag') { openFlagSel(ids); return; }
            var run = act === 'delete' ? deleteMany(ids, b, picked) : bulk(ids, act, b);
            run.then(function (ok) {
                if (!ok) return;
                if (act !== 'delete') flash(plural(ids.length, 'message') + ' marked ' + act);
                S.selecting = false;
                S.sel = {};
                refreshList();
            });
        });
    }

    // ---- Flag the selected messages (Story 5608) ----------------------------------------------

    var flagSelIds = [];

    function openFlagSel(ids) {
        flagSelIds = ids.slice();
        el('fsTitle').textContent = 'Flag ' + plural(ids.length, 'message');
        el('fsName').value = '';
        el('fsErr').hidden = true;
        el('fsAdd').disabled = false;
        el('fsRemove').disabled = false;
        drawFlagSelChips();
        el('flagSel').hidden = false;
        el('fsName').focus();
    }

    function drawFlagSelChips() {
        var flags = S.look ? S.look.flags() : [];
        var cur = el('fsName').value.trim().toLowerCase();
        el('fsFlags').innerHTML = flags.map(function (f) {
            return '<button type="button" class="gc-flag pick' + (f.name.toLowerCase() === cur ? ' on' : '') + '" style="--fc:' + esc(f.color || '#708090') + '"'
                + ' data-fs-name="' + esc(f.name) + '" data-testid="gmail-client-flagsel-flag">' + esc(f.name) + '</button>';
        }).join('');
    }

    function runFlagSel(action) {
        var name = el('fsName').value.trim();
        if (!name) { el('fsErr').textContent = 'Tap a flag, or type a new one.'; el('fsErr').hidden = false; return; }
        var b = el(action === 'add' ? 'fsAdd' : 'fsRemove');
        if (b.disabled) return;
        b.disabled = true;
        Promise.resolve(A.apiPost(API + '/flags/bulk', { ids: flagSelIds, flag: name, action: action })).then(function (r) {
            b.disabled = false;
            if (!r || !r.ok) { el('fsErr').textContent = errText(r, 'The flag was not changed.'); el('fsErr').hidden = false; return; }
            el('flagSel').hidden = true;
            if (S.look) S.look.setFlags(r.data.flags);
            flash(action === 'add' ? name + ' put on ' + plural(r.data.updated, 'message')
                : name + ' taken off ' + plural(r.data.updated, 'message'));
            S.selecting = false;
            S.sel = {};
            refreshList();
        });
    }

    function undo(ids, sent) {
        bulk(ids, 'restore', null, !!sent).then(function (ok) {
            if (!ok) return;
            note('');
            refreshList();
        });
    }

    // ---- mark all -------------------------------------------------------------------------

    function toggleMarkMenu(open) {
        var menu = el('markAllMenu');
        var show = open === undefined ? menu.hidden : open;
        menu.hidden = !show;
        el('btnMarkAll').setAttribute('aria-expanded', String(show));
    }

    function markAll(read) {
        toggleMarkMenu(false);
        var t = currentTab();
        var n = t ? (read ? t.unread : t.count - t.unread) : 0;
        var where = t ? (t.scope === 'all' ? 'all mail' : t.name) : 'this tab';
        if (!n) { flash('Nothing to mark ' + (read ? 'read' : 'unread') + ' in ' + where + '.'); return; }
        var filtered = S.q || S.custom || S.read !== 'all' || S.range !== 'any' || S.flagFilter.length;
        if (!window.confirm('Mark ' + plural(n, 'message') + ' in ' + where + (filtered ? ' that match what is shown' : '')
            + ' as ' + (read ? 'read' : 'unread') + '?\n\nThis is every one the tab shows, not only the ones loaded. Outlook is not changed.')) return;
        var body = Object.assign({}, scopeParams(), filterParams(), { isRead: read });
        if (body.inbox) body.inbox = parseInt(body.inbox, 10);
        if (body.unread) body.unread = true;
        var b = el('btnMarkAll');
        b.disabled = true;
        Promise.resolve(A.apiPost(API + '/messages/mark-all', body)).then(function (r) {
            b.disabled = false;
            if (!r || !r.ok) { note(errText(r, 'Could not mark them.'), true); return; }
            flash(plural(r.data.updated, 'message') + ' marked ' + (read ? 'read' : 'unread'));
            refreshList();
        });
    }

    // ---- follow up ------------------------------------------------------------------------

    /** sent: the ids are sent messages (the Sent tab, or a sent message open in the reader). */
    function openFollowUp(ids, sent) {
        var rows = sent === isSent() ? rowsFor(ids) : [];
        if (!rows.length && S.open && ids.length === 1 && ids[0] === S.open.id) rows = [S.open];
        var flagged = rows.filter(function (m) { return m.followUpDate; });
        S.followUp = { ids: ids, sent: !!sent };
        el('fTitle').textContent = ids.length === 1 ? 'Follow up' : 'Follow up on ' + plural(ids.length, 'message');
        var one = rows.length === 1 ? rows[0] : null;
        el('fDate').value = one && one.followUpDate ? one.followUpDate : isoDay(1);
        el('fNote').value = one && one.followUpNote ? one.followUpNote : '';
        el('fDone').hidden = !flagged.length;
        el('fErr').hidden = true;
        el('fSave').disabled = false;
        el('fDone').disabled = false;
        drawQuick();
        el('followup').hidden = false;
    }

    function drawQuick() {
        var v = el('fDate').value;
        Array.prototype.forEach.call(el('fQuick').querySelectorAll('[data-days]'), function (b) {
            b.classList.toggle('on', isoDay(parseInt(b.getAttribute('data-days'), 10)) === v);
        });
    }

    function closeFollowUp() { el('followup').hidden = true; S.followUp = null; }

    function followUpError(r, d) {
        el('fErr').textContent = errText(r, d);
        el('fErr').hidden = false;
    }

    function afterFollowUp(message) {
        var ids = S.followUp ? S.followUp.ids : [];
        var sent = S.followUp ? S.followUp.sent : false;
        closeFollowUp();
        flash(message);
        if (S.selecting) { S.selecting = false; S.sel = {}; }
        var reading = S.open && ids.length === 1 && ids[0] === S.open.id && rowIsSent(S.open) === sent;
        return refreshList().then(function () {
            if (reading) {
                var row = sent === isSent() ? rowsFor([S.open.id])[0] : null;
                S.open.followUpDate = row ? row.followUpDate : null;
                S.open.followUpNote = row ? row.followUpNote : null;
                if (!row) reloadOpenFollowUp();
                drawFollowUpChip();
            }
        });
    }

    /** The open message may not be in the loaded list (a Find link); ask the server. */
    function reloadOpenFollowUp() {
        var id = S.open && S.open.id;
        if (!id) return;
        Promise.resolve(A.apiGet(msgUrl(id, rowIsSent(S.open)))).then(function (r) {
            if (!r || !r.ok || !S.open || S.open.id !== id) return;
            S.open.followUpDate = r.data.followUpDate;
            S.open.followUpNote = r.data.followUpNote;
            drawFollowUpChip();
        });
    }

    function saveFollowUp() {
        var f = S.followUp, b = el('fSave');
        if (!f || b.disabled) return;
        b.disabled = true;   // a Save pressed twice must not save twice
        Promise.resolve(A.apiPost(API + (f.sent ? '/sent/followups' : '/followups'), { ids: f.ids, dueDate: el('fDate').value, note: el('fNote').value }))
            .then(function (r) {
                if (!r || !r.ok) { b.disabled = false; followUpError(r, 'The follow-up was not saved.'); return; }
                afterFollowUp('Follow up on ' + fmtDay(el('fDate').value));
            });
    }

    function doneFollowUp() {
        var f = S.followUp, b = el('fDone');
        if (!f || b.disabled) return;
        b.disabled = true;
        Promise.resolve(A.apiPost(API + (f.sent ? '/sent/followups/done' : '/followups/done'), { ids: f.ids })).then(function (r) {
            if (!r || !r.ok) { b.disabled = false; followUpError(r, 'Could not mark it done.'); return; }
            afterFollowUp(r.data.updated === 1 ? 'Follow-up done' : plural(r.data.updated, 'follow-up') + ' done');
        });
    }

    function drawFollowUpChip() {
        var m = S.open, chip = el('rFollowUp');
        if (!m || !m.followUpDate) { chip.hidden = true; chip.innerHTML = ''; return; }
        var st = dueState(m.followUpDate);
        chip.className = 'gc-fu-chip' + (st === 'later' ? '' : ' ' + st);
        chip.innerHTML = '<span>' + (st === 'od' ? 'Overdue: follow up ' : 'Follow up ') + esc(fmtDay(m.followUpDate)) + '</span>'
            + (m.followUpNote ? '<span>' + esc(m.followUpNote) + '</span>' : '');
        chip.hidden = false;
    }

    // ---- reading one message --------------------------------------------------------------

    /** Opens one message. sent: a sent message (default: whatever the Sent tab is showing). */
    function openMessage(id, sent) {
        if (sent === undefined) sent = isSent();
        return Promise.resolve(A.apiGet(msgUrl(id, sent))).then(function (r) {
            if (!r || !r.ok) { note(errText(r, 'Could not open that message.'), true); return; }
            var wasOpen = !el('reader').hidden;
            S.open = r.data;
            S.fileSender = !!r.data.isSenderLevel;
            // A fresh open follows the list: conversations shown when the list shows them.
            if (!wasOpen) S.convShow = threadsOn();
            drawReader();
            el('reader').hidden = false;
            el('rBody').scrollTop = 0;
            if (!sent) markSeen(id);
            if (sent === isSent() && previewLive() && rowsFor([id]).length && S.pv !== id) previewSelect(id);
        });
    }

    function drawReader() {
        var m = S.open, sent = rowIsSent(m);
        el('rSubject').textContent = m.subject || '(no subject)';
        el('rMeta').innerHTML = (sent ? '<div><b>To</b> ' + esc(m.to || '') + '</div>' : '<div><b>From</b> ' + esc(m.from) + '</div>')
            + '<div class="gc-meta-more">'
            + (sent ? (m.from ? '<div><b>From</b> ' + esc(m.from) + '</div>' : '') : (m.to ? '<div><b>To</b> ' + esc(m.to) + '</div>' : ''))
            + (m.cc ? '<div><b>Cc</b> ' + esc(m.cc) + '</div>' : '')
            + (m.bcc ? '<div><b>Bcc</b> ' + esc(m.bcc) + '</div>' : '')
            + '<div><b>' + (sent ? 'Sent' : 'Date') + '</b> ' + esc(fmtWhen(m.receivedDate)) + '</div></div>';
        // A sent message: Write again instead of Reply / Reply all, no read state, no sender to file.
        el('btnReply').hidden = sent;
        el('btnReplyAll').hidden = sent;
        el('btnWriteAgain').hidden = !sent;
        el('btnUnread').hidden = sent;
        setGmailLink(el('btnInGmail'), m);
        el('rInbox').closest('.gc-inbox-pick').hidden = sent;
        drawDecide();
        drawReplies();
        if (!sent) drawFiling(m);
        drawFollowUpChip();
        drawSteps();
        drawFlagsRow(m);
        drawLinks(m);

        fillBody(el('rBody'), m, '', 'gmail-client-reader');
        drawConversation();
    }

    // ---- filing one sender apart from the domain (Story 5608) ---------------------------------

    /** True when the message is filed by its domain, so one sender there can be filed on their own. */
    function domainKeyed(m) {
        return !!(m && m.senderKey && m.senderKey.indexOf('@') < 0 && m.senderAddress);
    }

    /**
     * "Inbox for example.com [Legal]  File greg@example.com on its own" -- or, once that sender is
     * on their own, "Inbox for greg@example.com (just this sender) [Personal]  Go back to example.com".
     */
    function drawFiling(m) {
        var level = el('rLevel');
        if (!domainKeyed(m)) {
            el('rSender').textContent = m.senderKey || '';
            el('rSenderNote').textContent = '';
            level.hidden = true;
            fillInboxSelect(el('rInbox'), m.inboxId, 'Unclassified');
            return;
        }
        level.hidden = false;
        if (S.fileSender) {
            el('rSender').textContent = m.senderAddress;
            el('rSenderNote').textContent = ' (just this sender)';
            level.textContent = m.isSenderLevel
                ? 'Go back to ' + m.senderKey + ' (' + (m.domainInboxName || 'Unclassified') + ')'
                : 'File all of ' + m.senderKey + ' instead';
        } else {
            el('rSender').textContent = m.senderKey;
            el('rSenderNote').textContent = m.isSenderLevel ? '' : '';
            level.textContent = 'File ' + m.senderAddress + ' on its own';
        }
        fillInboxSelect(el('rInbox'), S.fileSender || !m.isSenderLevel ? m.inboxId : m.domainInboxId, 'Unclassified');
    }

    /** The level link: start filing one sender on their own, or drop their override. */
    function levelClicked() {
        var m = S.open;
        if (!m || !domainKeyed(m)) return;
        if (S.fileSender && m.isSenderLevel) {
            var b = el('rLevel');
            b.disabled = true;
            Promise.resolve(A.apiPost(API + '/senders/sender-inbox', { address: m.senderAddress, followDomain: true })).then(function (r) {
                b.disabled = false;
                if (!r || !r.ok) { note(errText(r, 'Could not put ' + m.senderAddress + ' back with ' + m.senderKey + '.'), true); return; }
                flash(m.senderAddress + ' is filed with ' + m.senderKey + ' again');
                S.fileSender = false;
                reopenOpen();
            });
            return;
        }
        S.fileSender = !S.fileSender;
        drawFiling(m);
    }

    /** Reads the open message again (its inbox, flags or links changed) and redraws the reader. */
    function reopenOpen() {
        var m = S.open;
        if (!m) return Promise.resolve();
        return Promise.resolve(A.apiGet(msgUrl(m.id, rowIsSent(m)) + (rowIsSent(m) ? '' : '?peek=true'))).then(function (r) {
            if (!r || !r.ok || !S.open || S.open.id !== m.id) return;
            S.open = r.data;
            drawReader();
            return refreshTabs();
        });
    }

    /** Color from the reader: this sender, or everyone at the domain (when there is one). */
    function colorFromReader() {
        var m = S.open;
        if (!m || !S.look) return;
        var b = el('rColor');
        var address = m.senderAddress || m.senderKey;
        var sample = { from: displayName(m.from) || address, subject: m.subject || '(no subject)' };
        if (!domainKeyed(m)) { S.look.colorQuick(b, 'sender', address, sample); return; }
        Promise.resolve(window.PicassoDialog ? window.PicassoDialog.show({
            title: 'Color', kind: 'question',
            message: 'Color just ' + address + ', or everyone who writes from ' + m.senderKey + '?',
            buttons: [{ label: 'Cancel', value: '', style: 'plain' },
                      { label: 'Everyone at ' + m.senderKey, value: 'domain', style: 'plain' },
                      { label: 'Just this sender', value: 'sender', style: 'primary', isDefault: true }]
        }) : Promise.resolve('sender')).then(function (kind) {
            if (!kind) return;
            S.look.colorQuick(b, kind, kind === 'domain' ? m.senderKey : address, sample);
        });
    }

    /** The reader's Color button wears the open message's color, so a new one shows at once. */
    function drawColorButton() {
        var b = el('rColor'), m = S.open;
        if (!b) return;
        var t = m && S.look && !rowIsSent(m) ? S.look.tintFor(m) : null;
        b.innerHTML = (t ? '<span class="gc-colordot' + (t.style !== 'plain' ? ' sw-' + t.style : '') + '" style="--sw:' + t.hex
            + ';background:' + esc(t.bg) + '" data-testid="gmail-client-reader-color-dot"></span>' : '') + '<span class="gc-color-icon" aria-hidden="true">&#127912;</span>';
        b.title = t ? 'Color: ' + S.look.nameOf(t.hex) + '. Tap to change it' : 'Give this sender a color';
        b.setAttribute('aria-label', b.title);
    }

    // ---- flags on the open message (Story 5608) ----------------------------------------------

    var flagTags = null, drawingFlags = false, flagTimer = 0;

    function ensureFlagTags() {
        if (flagTags || !window.PicassoTags) return flagTags;
        flagTags = window.PicassoTags.attach({
            input: el('rFlagInput'), chips: el('rFlagChips'), dropdown: el('rFlagDrop'),
            getOptions: function () { return S.look ? S.look.flags().map(function (f) { return { id: f.id, name: f.name }; }) : []; },
            placeholder: 'Add a flag', noun: 'flag', testid: 'gmail-client-reader-flag',
            addLabel: function (t) { return 'New flag ' + t; },
            onChange: function () {
                if (drawingFlags) return;
                window.clearTimeout(flagTimer);
                flagTimer = window.setTimeout(saveFlags, 350);
            }
        });
        return flagTags;
    }

    function drawFlagsRow(m) {
        drawColorButton();
        var row = el('rFlagsRow');
        row.hidden = rowIsSent(m) || !window.PicassoTags;
        if (row.hidden) return;
        var t = ensureFlagTags();
        drawingFlags = true;
        try {
            t.setValues((m.flagIds || []).map(function (id) { var f = S.look && S.look.flagById(id); return f ? f.name : null; })
                .filter(function (n) { return n; }));
        } finally { drawingFlags = false; }
    }

    /** Exactly the chips' flags on the open message; a new name makes the flag. */
    function saveFlags() {
        var m = S.open;
        if (!m || rowIsSent(m) || !flagTags) return;
        var names = flagTags.getValues();
        Promise.resolve(A.apiPost(API + '/messages/' + m.id + '/flags', { names: names })).then(function (r) {
            if (!r || !r.ok) { note(errText(r, 'The flags were not saved.'), true); return; }
            if (S.look) S.look.setFlags(r.data.flags);
            var ids = (r.data.messageFlags || []).map(function (f) { return f.id; });
            if (S.open && S.open.id === m.id) S.open.flagIds = ids;
            var row = rowsFor([m.id])[0];
            if (row && !rowIsSent(row)) row.flagIds = ids;
            if (r.data.tabs) { S.tabs = r.data.tabs; drawTabs(); }
            drawList();
        });
    }

    /** A Flagged-tab heading: the flag's own chip. */
    function flagHeading(name) {
        if (!S.look) return esc(name);
        var f = S.look.flags().filter(function (x) { return x.name === name; })[0];
        return f ? S.look.flagChipsHtml([f.id], 'gmail-client-flag-heading') : esc(name);
    }

    // ---- Linked to: the records an email is linked to (Story 5608) ----------------------------

    // Record pages open by the record's OWN key (IdentityID); anything else opens on Entity Manager IV.
    var RECORD_PAGE_BY_TYPE_ID = {
        5: '/contacts/contact.html?id=', 1: '/vendors/vendor.html?id=',
        30: '/userentities/userentity.html?id=', 217: '/matters/matter.html?id=',
        219: '/call-logs/call-log.html?id=', 222: '/solutions/solution.html?id=',
        225: '/vehicles/vehicle.html?id=', 226: '/taxes/tax-year.html?id=',
        230: '/health-ii/person.html?id=', 231: '/health/health-visit.html?id=', 232: '/health-ii/issue.html?id=', 233: '/insurance/insurance-plan.html?id=', 234: '/insurance/insurance-eob.html?id='
    };

    function recordUrl(l) {
        var base = RECORD_PAGE_BY_TYPE_ID[l.entityTypeId];
        return base && l.identityId ? base + encodeURIComponent(l.identityId)
            : '/entity-manager-iv/entity-manager-iv.html?id=' + encodeURIComponent(l.entityId);
    }

    function drawLinks(m) {
        var links = m.links || [];
        el('rLinks').innerHTML = links.map(function (l) {
            return '<span class="gc-linkchip"><a href="' + esc(recordUrl(l)) + '" data-testid="gmail-client-reader-link">'
                + esc(l.entityTypeName) + ': ' + esc(l.displayName) + '</a>'
                + '<button type="button" class="gc-linkx" data-unlink="' + l.entityId + '" aria-label="Unlink ' + esc(l.displayName) + '"'
                + ' title="Unlink" data-testid="gmail-client-reader-unlink">&times;</button></span>';
        }).join('');
    }

    function afterLinks(m, links) {
        m.links = links || [];
        // A sent id and a received id can be the same number: only the row of the same kind is this message.
        var row = rowsFor([m.id]).filter(function (x) { return rowIsSent(x) === rowIsSent(m); })[0];
        if (row) row.linkCount = m.links.length;
        drawLinks(m);
        drawList();
    }

    function unlinkRecord(entityId) {
        var m = S.open;
        if (!m) return;
        var l = (m.links || []).filter(function (x) { return x.entityId === entityId; })[0];
        if (!l) return;
        var who = rowIsSent(m) ? 'to ' + toNames(m.to) : displayName(m.from);
        Promise.resolve(window.Picasso.confirmDelete('Unlink "' + (m.subject || '(no subject)') + '" (' + who + ', '
            + fmtWhen(m.receivedDate) + ') from ' + l.entityTypeName + ': ' + l.displayName + '?\n\n'
            + 'The email and the record both stay -- only the link between them goes.', { title: 'Unlink', yes: 'Unlink' }))
            .then(function (ok) {
                if (!ok) return;
                Promise.resolve(A.apiPost(msgUrl(m.id, rowIsSent(m)) + '/links/' + entityId, null, 'DELETE')).then(function (r) {
                    if (!r || !r.ok) { note(errText(r, 'Could not unlink ' + l.displayName + '.'), true); return; }
                    afterLinks(m, r.data.links);
                    flash('Unlinked from ' + l.displayName);
                });
            });
    }

    var linkTimer = 0, linkSeq = 0;

    function openLinkDialog() {
        if (!S.open) return;
        el('lkErr').hidden = true;
        el('lkSearch').value = '';
        el('lkList').innerHTML = '<div class="gc-hint">Type part of a name.</div>';
        el('links').hidden = false;
        el('lkSearch').focus();
    }

    function searchRecords() {
        var q = el('lkSearch').value.trim();
        var seq = ++linkSeq;
        if (!q) { el('lkList').innerHTML = '<div class="gc-hint">Type part of a name.</div>'; return; }
        Promise.resolve(A.apiGet(API + '/record-search?max=30&q=' + encodeURIComponent(q))).then(function (r) {
            if (seq !== linkSeq) return;
            if (!r || !r.ok) { el('lkList').innerHTML = '<div class="gc-hint">' + esc(errText(r, 'Could not search the records.')) + '</div>'; return; }
            var recs = r.data.records || [];
            var linked = {};
            ((S.open && S.open.links) || []).forEach(function (l) { linked[l.entityId] = true; });
            el('lkList').innerHTML = recs.length ? recs.map(function (x) {
                return '<button type="button" class="gc-pick-row" data-link-entity="' + x.entityId + '"' + (linked[x.entityId] ? ' disabled' : '')
                    + ' data-testid="gmail-client-link-result"><b>' + esc(x.displayName) + '</b> <span class="gc-pick-sub">'
                    + esc(x.entityTypeName) + (linked[x.entityId] ? ' -- linked already' : '') + '</span></button>';
            }).join('') : '<div class="gc-hint">No record matches ' + esc(q) + '.</div>';
        });
    }

    function linkRecord(btn) {
        var m = S.open;
        if (!m || btn.disabled) return;
        var entityId = parseInt(btn.getAttribute('data-link-entity'), 10);
        var name = btn.querySelector('b').textContent;
        btn.disabled = true;
        Promise.resolve(A.apiPost(msgUrl(m.id, rowIsSent(m)) + '/links', { entityId: entityId })).then(function (r) {
            if (!r || !r.ok) {
                btn.disabled = false;
                el('lkErr').textContent = errText(r, 'Could not link the email to ' + name + '.');
                el('lkErr').hidden = false;
                return;
            }
            el('links').hidden = true;
            afterLinks(m, r.data.links);
            flash('Linked to ' + name);
        });
    }

    // ---- the conversation in the reader (Story 5608) -----------------------------------------
    //
    // "If I send to Greg, then he to me, then I to him, in a conversation, I should be able to review
    // it as such, with expand collapse for each piece." Every message of the Gmail thread -- received
    // and sent -- is a fold, oldest first. Auto-collapsed: only the message opened and the newest
    // are unfolded. A piece shows its own words; what it quotes is tucked under Show quoted text, and
    // Show formatted puts back the message's own layout. Unfolding an unread piece reads it.

    var convSeq = 0;

    function pieceKey(p) { return (p.kind === 'sent' ? 's' : 'i') + p.id; }
    function openKey(m) { return (rowIsSent(m) ? 's' : 'i') + m.id; }

    /** How many messages the open one's conversation holds (1 = none to show). */
    function convCount(m) {
        if (!m) return 1;
        return rowIsSent(m) ? 1 + (m.replies || []).length : Math.max(1, m.threadCount || 1);
    }

    function ensureConvBox() {
        var box = el('rConv');
        if (box) return box;
        box = document.createElement('div');
        box.id = 'rConv';
        box.setAttribute('data-testid', 'gmail-client-conversation');
        el('rBody').parentNode.insertBefore(box, el('rBody'));
        box.addEventListener('click', convClick);
        return box;
    }

    function drawConversation() {
        var m = S.open, box = ensureConvBox();
        var n = convCount(m);
        if (n < 2) { box.hidden = true; box.innerHTML = ''; el('rBody').hidden = false; return; }
        box.hidden = false;
        if (!S.convShow) {
            box.className = 'gc-conv-bar';
            box.innerHTML = '<span>One of ' + n + ' messages in a conversation.</span>'
                + '<button type="button" class="gc-link" data-conv="show" data-testid="gmail-client-conversation-show">Show the conversation</button>';
            el('rBody').hidden = false;
            return;
        }
        box.className = 'gc-conv';
        el('rBody').hidden = true;
        var key = openKey(m);
        // Stepping to another message of the same conversation keeps what is loaded and unfolded.
        if (S.conv && S.conv.list && S.conv.list.some(function (p) { return pieceKey(p) === key; })) {
            S.conv.msgs[key] = m;
            S.conv.open[key] = true;
            renderConv();
            return;
        }
        S.conv = { list: null, open: {}, msgs: {}, quoted: {}, formatted: {} };
        S.conv.msgs[key] = m;
        S.conv.open[key] = true;
        box.innerHTML = '<div class="gc-empty">Opening the conversation</div>';
        var seq = ++convSeq;
        Promise.resolve(A.apiGet(msgUrl(m.id, rowIsSent(m)) + '/conversation')).then(function (r) {
            if (seq !== convSeq || !S.open || openKey(S.open) !== key) return;
            if (!r || !r.ok) { box.innerHTML = '<div class="gc-empty">' + esc(errText(r, 'Could not open the conversation.')) + '</div>'; return; }
            S.conv.list = r.data.messages || [];
            var newest = S.conv.list[S.conv.list.length - 1];
            if (newest) S.conv.open[pieceKey(newest)] = true;
            renderConv();
            var cur = box.querySelector('.gc-piece.current');
            if (cur && cur.scrollIntoView) cur.scrollIntoView({ block: 'nearest' });
        });
    }

    function initial(name) {
        var t = String(name || '?').replace(/["']/g, '').trim();
        return (t.charAt(0) || '?').toUpperCase();
    }

    /** The address inside a From line, lower case. */
    function addressIn(from) {
        var m = /<([^>]+)>/.exec(String(from || ''));
        return (m ? m[1] : String(from || '')).trim().toLowerCase();
    }

    function renderConv() {
        var box = el('rConv'), list = S.conv.list || [];
        var cur = S.open ? openKey(S.open) : '';
        box.innerHTML = '<div class="gc-conv-head"><b>Conversation</b><span class="gc-conv-n">' + plural(list.length, 'message') + '</span>'
            + '<button type="button" class="gc-link" data-conv="expand" data-testid="gmail-client-conversation-expand">Unfold all</button>'
            + '<button type="button" class="gc-link" data-conv="collapse" data-testid="gmail-client-conversation-collapse">Fold all</button>'
            + '<button type="button" class="gc-link" data-conv="hide" data-testid="gmail-client-conversation-hide">This message only</button></div>'
            + list.map(function (p) {
                var k = pieceKey(p), open = !!S.conv.open[k], sent = p.kind === 'sent';
                var who = sent ? 'Me' : (displayName(p.from) || p.senderKey || '');
                var tint = !sent && S.look ? S.look.tintFor({ senderAddress: addressIn(p.from), senderKey: p.senderKey }) : null;
                return '<div class="gc-piece' + (open ? ' open' : '') + (sent ? ' sent' : '') + (p.isRead ? '' : ' unread')
                    + (k === cur ? ' current' : '') + '" data-piece="' + k + '" data-testid="gmail-client-piece">'
                    + '<button type="button" class="gc-piece-head" data-conv="toggle" aria-expanded="' + open + '" data-testid="gmail-client-piece-toggle">'
                    + '<span class="gc-avatar' + (sent ? ' me' : '') + '"'
                    + (tint ? ' style="background:' + esc(tint.bg) + ';color:' + (tint.ink === 'dark' ? '#111' : '#fff') + '"' : '') + '>'
                    + esc(initial(who)) + '</span>'
                    + '<span class="gc-piece-who">' + esc(who) + (sent && p.to ? ' <span class="gc-piece-to">to ' + esc(toNames(p.to)) + '</span>' : '')
                    + '</span><span class="gc-piece-when">' + (p.hasAttachments ? '&#128206; ' : '') + esc(shortWhen(p.date)) + '</span>'
                    + (open ? '' : '<span class="gc-piece-snip">' + esc(p.snippet || '') + '</span>')
                    + '</button>'
                    + (open ? '<div class="gc-piece-body" data-piece-body="' + k + '"><div class="gc-empty">Opening</div></div>' : '')
                    + '</div>';
            }).join('');
        list.forEach(function (p) { if (S.conv.open[pieceKey(p)]) fillPiece(p); });
    }

    /** One unfolded piece: its body read (and, an inbox message, marked read) the first time. */
    function fillPiece(p) {
        var k = pieceKey(p), sent = p.kind === 'sent';
        var have = S.conv.msgs[k];
        if (have) { drawPiece(p, have); return; }
        Promise.resolve(A.apiGet(msgUrl(p.id, sent))).then(function (r) {
            if (!S.conv || !S.conv.open[k]) return;
            var body = el('rConv').querySelector('[data-piece-body="' + k + '"]');
            if (!r || !r.ok) { if (body) body.innerHTML = '<div class="gc-empty">' + esc(errText(r, 'Could not open that message.')) + '</div>'; return; }
            S.conv.msgs[k] = r.data;
            if (!sent && !p.isRead) { p.isRead = true; markSeen(p.id); }
            drawPiece(p, r.data);
        });
    }

    /** Splits a message's text into its own words and what it quotes ("On ... wrote:", "> ", a forward). */
    function splitQuoted(text) {
        var lines = String(text || '').split(/\r?\n/);
        for (var i = 1; i < lines.length; i++) {
            if (/^On .{4,200}wrote:\s*$/.test(lines[i]) || /^-{5,}\s*Forwarded message/i.test(lines[i])
                || (/^>/.test(lines[i]) && /^\s*$/.test(lines[i - 1]))) {
                return { mine: lines.slice(0, i).join('\n').replace(/\s+$/, ''), quoted: lines.slice(i).join('\n') };
            }
        }
        return { mine: String(text || ''), quoted: '' };
    }

    function drawPiece(p, msg) {
        var k = pieceKey(p), sent = p.kind === 'sent';
        var box = el('rConv').querySelector('[data-piece-body="' + k + '"]');
        if (!box) return;
        var meta = '<div class="gc-piece-meta">'
            + (sent ? '<b>To</b> ' + esc(msg.to || '') : '<b>From</b> ' + esc(msg.from || '') + (msg.to ? ' <b>To</b> ' + esc(msg.to) : ''))
            + (msg.cc ? ' <b>Cc</b> ' + esc(msg.cc) : '') + ' <span>' + esc(fmtWhen(msg.receivedDate)) + '</span></div>';
        var acts = '<div class="gc-piece-acts">'
            + (sent ? '<button type="button" class="gc-btn" data-piece-act="writeAgain" data-testid="gmail-client-piece-write-again">Write again</button>'
                : '<button type="button" class="gc-btn" data-piece-act="reply" data-testid="gmail-client-piece-reply">Reply</button>'
                  + '<button type="button" class="gc-btn" data-piece-act="replyAll" data-testid="gmail-client-piece-reply-all">Reply all</button>')
            + '<button type="button" class="gc-btn" data-piece-act="forward" data-testid="gmail-client-piece-forward">Forward</button>'
            + (msg.bodyHtml ? '<button type="button" class="gc-link" data-piece-act="formatted" data-testid="gmail-client-piece-formatted">'
                + (S.conv.formatted[k] ? 'Show plain text' : 'Show formatted') + '</button>' : '')
            + '<button type="button" class="gc-link" data-piece-act="alone" data-testid="gmail-client-piece-alone">Open on its own</button>'
            + '</div>';
        box.innerHTML = meta + '<div class="gc-piece-text"></div>' + acts;
        var holder = box.querySelector('.gc-piece-text');
        if (S.conv.formatted[k] && msg.bodyHtml) {
            fillBody(holder, msg, S.look ? S.look.paperCss() : '', 'gmail-client-piece');
            return;
        }
        var parts = splitQuoted(msg.bodyText || (msg.bodyHtml ? '' : '(no text)'));
        holder.innerHTML = '<pre class="gc-text" data-testid="gmail-client-piece-text">' + esc(parts.mine || '(no text of its own)') + '</pre>'
            + (parts.quoted ? '<button type="button" class="gc-link gc-quoted-btn" data-piece-act="quoted" data-testid="gmail-client-piece-quoted">'
                + (S.conv.quoted[k] ? 'Hide quoted text' : 'Show quoted text') + '</button>'
                + (S.conv.quoted[k] ? '<pre class="gc-text gc-quoted">' + esc(parts.quoted) + '</pre>' : '') : '');
        if (msg.attachments && msg.attachments.length) {
            // Reuses the reader's attachment buttons (open / save to Leonardo Files).
            var tmp = document.createElement('div');
            fillBody(tmp, { id: msg.id, kind: msg.kind, attachments: msg.attachments, bodyText: '' }, '', 'gmail-client-piece');
            var att = tmp.querySelector('.gc-attach');
            if (att) holder.appendChild(att);
        }
    }

    function convClick(e) {
        var b = e.target.closest('[data-conv], [data-piece-act]');
        if (!b) return;
        var act = b.getAttribute('data-conv') || b.getAttribute('data-piece-act');
        if (act === 'show') { S.convShow = true; drawConversation(); return; }
        if (act === 'hide') { S.convShow = false; drawConversation(); return; }
        if (!S.conv || !S.conv.list) return;
        if (act === 'expand') { S.conv.list.forEach(function (p) { S.conv.open[pieceKey(p)] = true; }); renderConv(); return; }
        if (act === 'collapse') { S.conv.open = {}; renderConv(); return; }
        var pieceEl = b.closest('[data-piece]');
        if (!pieceEl) return;
        var k = pieceEl.getAttribute('data-piece');
        var p = S.conv.list.filter(function (x) { return pieceKey(x) === k; })[0];
        if (!p) return;
        if (act === 'toggle') { S.conv.open[k] = !S.conv.open[k]; renderConv(); return; }
        if (act === 'quoted') { S.conv.quoted[k] = !S.conv.quoted[k]; drawPiece(p, S.conv.msgs[k]); return; }
        if (act === 'formatted') { S.conv.formatted[k] = !S.conv.formatted[k]; drawPiece(p, S.conv.msgs[k]); return; }
        var msg = S.conv.msgs[k];
        if (!msg) return;
        // Reply, Forward ... act on THAT piece: it becomes the open message, the conversation stays.
        S.open = msg;
        S.fileSender = !!msg.isSenderLevel;
        if (act === 'alone') { S.convShow = false; drawReader(); return; }
        drawReader();
        openCompose(act);
    }

    /** A sent message's replies, each one tap from opening; or how long it has waited for one. */
    function drawReplies() {
        var box = el('rReplies');
        if (!box) {
            box = document.createElement('div');
            box.id = 'rReplies';
            box.setAttribute('data-testid', 'gmail-client-reader-replies');
            el('rBody').parentNode.insertBefore(box, el('rBody'));
            box.addEventListener('click', function (e) {
                var b = e.target.closest('[data-reply-id]');
                if (b) openMessage(parseInt(b.getAttribute('data-reply-id'), 10), false);
            });
        }
        var m = S.open;
        if (!rowIsSent(m)) { box.className = ''; box.innerHTML = ''; return; }
        var replies = m.replies || [];
        box.className = 'gc-replies' + (replies.length ? ' has' : '');
        box.innerHTML = replies.length
            ? '<span class="gc-replies-h">' + plural(replies.length, 'reply', 'replies') + '</span>'
              + replies.map(function (rp) {
                  return '<button type="button" class="gc-reply-row' + (rp.isRead ? '' : ' unread') + '" data-reply-id="' + rp.id + '"'
                      + ' data-testid="gmail-client-reader-reply">'
                      + '<b>' + esc(displayName(rp.from)) + '</b> <span class="gc-reply-when">' + esc(fmtWhen(rp.receivedDate)) + '</span>'
                      + (rp.snippet ? '<span class="gc-reply-snip">' + esc(rp.snippet) + '</span>' : '') + '</button>';
              }).join('')
            : '<span class="gc-replies-h">No reply yet</span> <span class="gc-reply-when">sent ' + esc(fmtWhen(m.sentDate))
              + '. A reply shows here once Check mail reads it in.</span>';
    }

    var READER_CSS = 'body{font:14px/1.5 "Segoe UI",system-ui,sans-serif;color:#111;margin:12px 14px;word-wrap:break-word}'
        + 'table{max-width:100%}pre{white-space:pre-wrap}img{max-width:100%;height:auto}';
    // Sepia: the message's own colors give way to warm paper and brown ink; pictures stay.
    var SEPIA_CSS = 'html,body{background:#F4ECD8 !important}'
        + 'body{font:15px/1.6 Georgia,"Times New Roman",serif;color:#5B4636;margin:14px 18px;word-wrap:break-word}'
        + 'body *{background-color:transparent !important;color:#5B4636 !important;border-color:#D9C9A8 !important}'
        + 'a,a *{color:#8B5A2B !important}table{max-width:100%}pre{white-space:pre-wrap}';

    /** A message's body into a box: sanitized HTML in a sandboxed frame, or its text. */
    function fillBody(box, m, extraCss, tid) {
        if (m.bodyHtml) {
            // Sanitized on the server, then shown in a sandboxed frame: no scripts, links open a new tab.
            var doc = '<!doctype html><html><head><meta charset="utf-8"><base target="_blank">'
                + '<style>' + READER_CSS + (S.look ? S.look.paperCss() : '') + (extraCss || '') + '</style></head><body>'
                + m.bodyHtml + '</body></html>';
            box.innerHTML = '<iframe class="gc-frame" sandbox="allow-popups allow-popups-to-escape-sandbox" title="Message"'
                + ' data-testid="' + tid + '-frame" srcdoc="' + doc.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '"></iframe>';
        } else {
            box.innerHTML = '<pre class="gc-text" data-testid="' + tid + '-text">' + esc(m.bodyText || '(no text)') + '</pre>';
        }
        if (m.attachments && m.attachments.length) {
            // 2026-10-04: each attachment opens (read from Gmail on demand) and can be saved to Leonardo Files.
            box.insertAdjacentHTML('beforeend', '<div class="gc-attach" data-testid="' + tid + '-attachments">'
                + '<span class="gc-attach-h">&#128206; ' + plural(m.attachments.length, 'attachment') + '</span>'
                + m.attachments.map(function (name, i) {
                    var kind = rowIsSent(m) ? ' data-att-sent="1"' : '';
                    return '<span class="gc-att">'
                        + '<button type="button" class="gc-att-open" data-att-msg="' + m.id + '" data-att="' + i + '"' + kind
                        + ' title="Open ' + esc(name) + '" data-testid="' + tid + '-attachment">' + esc(name) + '</button>'
                        + (ECHO ? '' : '<button type="button" class="gc-att-save" data-att-msg="' + m.id + '" data-att="' + i + '"' + kind
                            + ' data-att-name="' + esc(name) + '" title="Save to Leonardo Files"'
                            + ' data-testid="' + tid + '-attachment-save">Save to Leonardo Files</button>')
                        + '</span>';
                }).join('') + '</div>');
        }
    }

    /** Open one attachment: fetched through the page's own sign-in, then handed to the browser as a file. */
    /** The attachment's address: under the inbox message or the sent message the button belongs to. */
    function attachmentUrl(btn) {
        return msgUrl(btn.getAttribute('data-att-msg'), btn.getAttribute('data-att-sent') === '1')
            + '/attachments/' + btn.getAttribute('data-att');
    }

    function openAttachment(btn) {
        var url = attachmentUrl(btn);
        var name = btn.textContent;
        btn.disabled = true;
        return Promise.resolve(A.apiGetForBlob(url)).then(function (r) {
            btn.disabled = false;
            if (!r || !r.ok || !r.blob) { note(errText(r, 'Could not open ' + name), true); return; }
            var href = URL.createObjectURL(r.blob);
            var a = document.createElement('a');
            a.href = href;
            a.download = r.filename || name;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.setTimeout(function () { URL.revokeObjectURL(href); }, 60000);
        });
    }

    function saveAttachment(btn) {
        var name = btn.getAttribute('data-att-name');
        if (!window.confirm('Save ' + name + ' to Leonardo Files?\n\nA copy lands in Leonardo Files (Leonardo Library -> Leonardo Files), '
            + 'where you can link it to a record.')) return Promise.resolve();
        btn.disabled = true;
        var url = attachmentUrl(btn) + '/save';
        return Promise.resolve(A.apiPost(url, {})).then(function (r) {
            if (!r || !r.ok) { btn.disabled = false; note(errText(r, 'Could not save ' + name + ' to Leonardo Files'), true); return; }
            btn.textContent = 'Saved to Leonardo Files';
            flash(name + ' is in Leonardo Files');
        });
    }

    // One listener for every attachment button -- the reader, the preview and the quick view.
    document.addEventListener('click', function (e) {
        var o = e.target.closest('.gc-att-open');
        if (o) { e.preventDefault(); openAttachment(o); return; }
        var sv = e.target.closest('.gc-att-save');
        if (sv && !sv.disabled) { e.preventDefault(); saveAttachment(sv); }
    });

    /** Opening a message marks it read: show that on its card and in the tab counts, without a reload. */
    /**
     * A message was just read (opened, previewed or quick-viewed; the server marked it read). Every
     * tab it counts in loses one unread -- and, under the Unread filter, one from its count too,
     * because it no longer matches. It stays in the list until the next refresh, as Gmail does, so
     * the reader's newer / older steps never lose their place.
     */
    function markSeen(id) {
        var row = rowsFor([id])[0];
        if (!row || row.isRead) return;
        row.isRead = true;
        var card = el('list').querySelector('.gc-card[data-id="' + id + '"]');
        if (card) card.classList.remove('unread');
        S.tabs.forEach(function (t) {
            if ((S.tab === 'undecided') !== (t.scope === 'undecided')) return;
            var hit = t.scope === 'undecided' || t.scope === 'all'
                || (t.scope === 'inbox' && row.inboxId === t.inboxId)
                || (t.scope === 'unclassified' && !row.inboxId)
                || (t.scope === 'followup' && row.followUpDate);
            if (!hit) return;
            if (t.unread > 0) t.unread--;
            if (S.read === 'unread' && t.count > 0) t.count--;
        });
        drawTabs();
    }

    // ---- quick view (sepia) ---------------------------------------------------------------

    function quickView(id) {
        var row = rowsFor([id])[0];
        el('qSubject').textContent = row ? (row.subject || '(no subject)') : '';
        el('qMeta').textContent = '';
        setGmailLink(el('qGmail'), row);
        el('qBody').innerHTML = '<div class="gc-sepia-wait">Opening</div>';
        S.qMsg = null;
        el('quick').hidden = false;
        var seq = ++S.pvSeq;
        Promise.resolve(A.apiGet(msgUrl(id, isSent()))).then(function (r) {
            if (seq !== S.pvSeq || el('quick').hidden) return;
            if (!r || !r.ok) { el('qBody').innerHTML = '<div class="gc-sepia-wait">' + esc(errText(r, 'Could not open that message.')) + '</div>'; return; }
            var m = r.data;
            S.qMsg = m;
            el('qSubject').textContent = m.subject || '(no subject)';
            el('qMeta').textContent = (rowIsSent(m) ? 'To ' + toNames(m.to) : (displayName(m.from) || m.senderKey || ''))
                + '  |  ' + fmtWhen(m.receivedDate);
            fillBody(el('qBody'), m, SEPIA_CSS, 'gmail-client-quickview');
            markSeen(id);
        });
    }

    function closeQuick() { el('quick').hidden = true; el('qBody').innerHTML = ''; S.qMsg = null; }

    // ---- Listen: read a message aloud (2026-10-08) --------------------------------------------

    /** Reads one message aloud in the "mail" voice (Admin -> Voice Settings; picasso-mail-listen.js). */
    function listenTo(m) {
        if (!m) { window.Picasso.message('The message is still opening. Try Listen again in a moment.'); return; }
        var sent = rowIsSent(m);
        window.PicassoMailListen.listen({
            subject: m.subject,
            sent: sent, from: displayName(m.from) || m.senderKey, to: toNames(m.to),
            date: m.receivedDate,
            bodyText: m.bodyText, bodyHtml: m.bodyHtml,
            key: (sent ? 'gmailSent:' : 'gmail:') + m.id
        });
    }

    // ---- the preview view (wide screens) -----------------------------------------------------

    /** The Drafts tab is always the plain list: a draft opens in the writing window, so there is nothing to preview. */
    function previewOn() { return S.view === 'preview' && !isDrafts(); }

    /** The preview view with the preview pane showing (not rolled away to its strip). */
    function previewLive() { return previewOn() && !ensureSplit().previewMinimized(); }

    var split = null;   // the Bay Window: list on the left or on top, the drag bar, minimizing either side
    var wasLive = false;   // whether the preview pane was showing at the last Bay Window change

    function ensureSplit() {
        if (split) return split;
        split = window.PicassoSplit.create({
            root: el('split'), list: el('body'), preview: el('preview'),
            key: DEV_PREFIX, testid: 'gmail-client', listLabel: 'Mail list', previewLabel: 'Preview',
            deskMinWidth: DESK_MIN_WIDTH, min: SPLIT_MIN, max: SPLIT_MAX, step: SPLIT_STEP,
            onChange: onSplitChange
        });
        addPreviewSteps();
        return split;
    }

    /**
     * Up / down beside the "Mail list" label (Story 5637): the previous or next message in the
     * preview, one tap each -- the arrow keys' twin for a phone or a mouse.
     */
    function addPreviewSteps() {
        var label = el('body').querySelector('.ps-listbar-text');
        if (!label) return;
        var box = document.createElement('span');
        box.className = 'gc-pvsteps';
        box.innerHTML = '<button type="button" class="ps-min" id="btnPvUp" title="Previous message" aria-label="Previous message"'
            + ' data-testid="gmail-client-preview-up">&#9650;</button>'
            + '<button type="button" class="ps-min" id="btnPvDown" title="Next message" aria-label="Next message"'
            + ' data-testid="gmail-client-preview-down">&#9660;</button>';
        label.parentNode.insertBefore(box, label.nextSibling);
        el('btnPvUp').addEventListener('click', function (e) { e.stopPropagation(); previewStep(-1); });
        el('btnPvDown').addEventListener('click', function (e) { e.stopPropagation(); previewStep(1); });
    }

    /** The up / down arrows: shown while the preview is showing, each off at its end of the list. */
    function drawPreviewSteps() {
        var box = el('body').querySelector('.gc-pvsteps');
        if (!box) return;
        box.hidden = !previewLive();
        var i = -1;
        for (var k = 0; k < S.rows.length; k++) if (S.rows[k].id === S.pv) { i = k; break; }
        el('btnPvUp').disabled = i <= 0;
        el('btnPvDown').disabled = i < 0 || (i >= S.rows.length - 1 && !S.more);
    }

    /** The preview pane rolled away or came back: redraw so the highlight and the preview follow. */
    function onSplitChange() {
        drawLayoutButton();
        drawPreviewSteps();
        var live = previewLive();
        if (live === wasLive) return;
        wasLive = live;
        if (previewOn()) drawList();
    }

    function layout() { return ensureSplit().layout(); }

    /** The layout button beside the preview button: shown in the preview view, naming the other layout. */
    function drawLayoutButton() {
        var on = previewOn(), top = on && layout() === 'top';
        document.querySelector('.gc-page').classList.toggle('gc-top', top);
        var lb = el('btnLayout');
        lb.hidden = !on;
        lb.innerHTML = top ? '&#9703;' : '&#11026;';
        lb.title = top ? 'Put the list on the left' : 'Put the list on top';
        lb.setAttribute('aria-label', lb.title);
    }

    function applyView() {
        var on = previewOn();
        showPreviewPane(on);
        if (!on) { S.pv = 0; return; }
        drawList();
    }

    /** The preview pane and its button, on or off -- without redrawing the list. */
    function showPreviewPane(on) {
        document.querySelector('.gc-page').classList.toggle('gc-pv-on', on);
        el('preview').hidden = !on;
        var b = el('btnView');
        b.classList.toggle('on', on);
        b.setAttribute('aria-pressed', String(on));
        b.title = on ? 'Back to the list view' : 'Preview view';
        b.setAttribute('aria-label', b.title);
        ensureSplit().setOn(on);   // draws the layout button through onChange
    }

    /**
     * Keep a previewed message: the one already picked if it is still listed, else the first -- shown
     * WITHOUT marking it read (Story 5607). The page picked it, not Tony; auto-reading it made the
     * newest mail vanish from the Unread filter, and each refresh then read the next one.
     */
    function syncPreview() {
        if (!S.rows.length) { S.pv = 0; drawPreviewEmpty(); }
        else if (!rowsFor([S.pv]).length) previewSelect(S.rows[0].id, true);
        drawPreviewSteps();
    }

    function drawPreviewEmpty() {
        el('pvHead').innerHTML = '';
        el('pvBody').innerHTML = '<div class="gc-empty">Nothing to preview.</div>';
    }

    /** Preview one message. auto = the page picked it, so it is only peeked at and stays unread. */
    function previewSelect(id, auto) {
        var row = rowsFor([id])[0];
        if (!row) return;
        S.pv = id;
        Array.prototype.forEach.call(el('list').querySelectorAll('.gc-card.pv-on'), function (c) { c.classList.remove('pv-on'); });
        var card = el('list').querySelector('.gc-card[data-id="' + id + '"]');
        if (card) {
            card.classList.add('pv-on');
            if (card.scrollIntoView) card.scrollIntoView({ block: 'nearest' });
        }
        el('pvHead').innerHTML = '<div class="gc-pv-title"><h2 data-testid="gmail-client-preview-subject">' + esc(row.subject || '(no subject)') + '</h2>'
            + '<button type="button" class="gc-btn" data-pv="listen" title="Read this message aloud" data-testid="gmail-client-preview-listen">&#127911; Listen</button>'
            + '<button type="button" class="gc-btn" data-pv="open" data-testid="gmail-client-preview-open">Open</button></div>'
            + '<div class="gc-pv-meta">' + esc(rowIsSent(row) ? 'To ' + (row.to || '') : (row.from || row.senderKey || ''))
            + (rowIsSent(row) ? ' ' + replyPill(row) : '') + '  |  ' + esc(fmtWhen(row.receivedDate)) + '</div>';
        el('pvBody').innerHTML = '<div class="gc-empty">Opening</div>';
        drawPreviewSteps();
        S.pvMsg = null;
        var seq = ++S.pvSeq;
        // Wait until the arrows stop: every open reads the body from Gmail.
        window.setTimeout(function () {
            if (seq !== S.pvSeq || S.pv !== id) return;
            var peek = auto && !rowIsSent(row);
            Promise.resolve(A.apiGet(msgUrl(id, rowIsSent(row)) + (peek ? '?peek=true' : ''))).then(function (r) {
                if (seq !== S.pvSeq || S.pv !== id) return;
                if (!r || !r.ok) { el('pvBody').innerHTML = '<div class="gc-empty">' + esc(errText(r, 'Could not open that message.')) + '</div>'; return; }
                S.pvMsg = r.data;
                fillBody(el('pvBody'), r.data, '', 'gmail-client-preview');
                if (!peek) markSeen(id);
            });
        }, PREVIEW_DELAY_MS);
    }

    /** Up / down in the preview view: the previous or next message, loading more at the end. */
    function previewStep(delta) {
        var i = -1;
        for (var k = 0; k < S.rows.length; k++) if (S.rows[k].id === S.pv) { i = k; break; }
        var j = i + delta;
        if (j < 0) return;
        if (j < S.rows.length) { previewSelect(S.rows[j].id); return; }
        if (S.more) loadList(true).then(function () { if (j < S.rows.length) previewSelect(S.rows[j].id); });
    }

    /** True while something sits over the list or the keys belong to a field. */
    function keysBusy(e) {
        var t = e.target;
        if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return true;
        if (t && t.id === 'splitter') return true;
        if (t && t.closest && t.closest('.ps-panel, .ps-sheet, .picasso-select-panel')) return true;
        return !el('reader').hidden || !el('quick').hidden || !el('compose').hidden || !el('followup').hidden
            || !el('custom').hidden || !el('inboxes').hidden || !el('links').hidden || !el('flagSel').hidden
            || !el('nookPick').hidden || !!(S.look && S.look.isOpen());
    }

    /** Where the open message sits in the list; -1 when it is not there (a reply opened from a sent message). */
    function openIndex() {
        if (!S.open) return -1;
        for (var i = 0; i < S.rows.length; i++) {
            if (S.rows[i].id === S.open.id && rowIsSent(S.rows[i]) === rowIsSent(S.open)) return i;
        }
        return -1;
    }

    /** Newer / Older step through the list the reader was opened from. */
    function drawSteps() {
        var i = openIndex();
        el('btnNewer').disabled = i <= 0;
        el('btnOlderMsg').disabled = i < 0 || (i >= S.rows.length - 1 && !S.more);
    }

    function step(delta) {
        var i = openIndex();
        if (i < 0) return Promise.resolve();
        var j = i + delta;
        if (j < 0) return Promise.resolve();
        if (j < S.rows.length) return openMessage(S.rows[j].id);
        if (!S.more) return Promise.resolve();
        return loadList(true).then(function () { if (j < S.rows.length) return openMessage(S.rows[j].id); });
    }

    /** The reader's Whitelist / Blacklist bar, only while the open message's sender is undecided. */
    function drawDecide() {
        var box = el('rDecide');
        if (!box) {
            box = document.createElement('div');
            box.id = 'rDecide';
            el('rBody').parentNode.insertBefore(box, el('rBody'));
            box.addEventListener('click', function (e) {
                var b = e.target.closest('[data-decide]');
                if (b) decideSender(b.getAttribute('data-sender'), b.getAttribute('data-decide'), b);
            });
        }
        var m = S.open;
        box.innerHTML = m && m.senderDecided === false ? decideBar(m.senderKey, 'gmail-client-reader') : '';
    }

    function closeReader() {
        el('reader').hidden = true;
        el('rBody').innerHTML = '';
        S.open = null;
    }

    function refreshTabs() {
        // Counts move after a read, a delete or a re-file; reloading what is shown keeps them true.
        return refreshList();
    }

    function fillInboxSelect(sel, selectedId, noneLabel) {
        sel.innerHTML = '<option value="">' + esc(noneLabel) + '</option>' + S.inboxes.map(function (i) {
            return '<option value="' + i.id + '">' + esc(i.name) + '</option>';
        }).join('');
        sel.value = selectedId ? String(selectedId) : '';
        if (window.PicassoSelect && PicassoSelect.refresh) PicassoSelect.refresh(sel);
    }

    function loadInboxes() {
        return Promise.resolve(A.apiGet(API + '/inboxes')).then(function (r) {
            if (r && r.ok) S.inboxes = r.data.inboxes || [];
        });
    }

    /** File the open message's sender in an inbox (by name; a new name makes the inbox). */
    function fileSender(inboxName) {
        var m = S.open;
        if (!m || !m.senderKey) return;
        if (S.fileSender && domainKeyed(m)) {
            // Story 5608: just this sender, apart from the domain.
            return Promise.resolve(A.apiPost(API + '/senders/sender-inbox', { address: m.senderAddress, inbox: inboxName || '' })).then(function (r) {
                if (!r || !r.ok) { note(errText(r, 'Could not file ' + m.senderAddress + '.'), true); drawFiling(m); return; }
                flash('Mail from ' + m.senderAddress + ' now goes to ' + (r.data.inboxName || 'Unclassified') + ' -- the rest of ' + m.senderKey + ' stays put');
                return loadInboxes().then(reopenOpen);
            });
        }
        return Promise.resolve(A.apiPost(API + '/senders/inbox', { sender: m.senderKey, inbox: inboxName || '' })).then(function (r) {
            if (!r || !r.ok) { note(errText(r, 'Could not move the sender.'), true); fillInboxSelect(el('rInbox'), m.inboxId, 'Unclassified'); return; }
            m.inboxId = r.data.inboxId;
            m.inboxName = r.data.inboxName;
            flash('Mail from ' + r.data.senderKey + ' now goes to ' + (r.data.inboxName || 'Unclassified'));
            return loadInboxes().then(function () { fillInboxSelect(el('rInbox'), m.inboxId, 'Unclassified'); refreshTabs(); });
        });
    }

    /** Delete from the reader, then carry on to the next message instead of closing. */
    function deleteOpen() {
        var m = S.open;
        if (!m) return;
        var sent = rowIsSent(m);
        if (!window.confirm('Delete "' + (m.subject || '(no subject)') + '" ' + (sent ? 'to ' + toNames(m.to) : 'from ' + displayName(m.from))
            + ' (' + fmtWhen(m.receivedDate) + ')?\n\nIt leaves the Email Client only. Outlook keeps it, and Undo brings it back.')) return;
        var i = openIndex();
        var next = i >= 0 ? (S.rows[i + 1] || S.rows[i - 1]) : null;
        bulk([m.id], 'delete', el('btnDelete'), sent).then(function (ok) {
            if (!ok) return;
            note('Deleted "' + (m.subject || '(no subject)') + '".', false, [m.id], sent);
            refreshList().then(function () {
                if (next && rowsFor([next.id]).length) openMessage(next.id); else closeReader();
            });
        });
    }

    function markUnread() {
        var m = S.open;
        if (!m) return;
        bulk([m.id], 'unread', el('btnUnread')).then(function (ok) {
            if (!ok) return;
            closeReader();
            refreshTabs();
        });
    }

    // ---- swiping a card (phone) -----------------------------------------------------------

    var SWIPE_READ = 80, SWIPE_OPEN = 60, LONG_PRESS_MS = 550;
    var sw = null, ignoreClickUntil = 0;

    function closeSwipes(except) {
        Array.prototype.forEach.call(el('list').querySelectorAll('.gc-swipe.open'), function (w) {
            if (w !== except) w.classList.remove('open');
        });
    }

    function onTouchStart(e) {
        var wrap = e.target.closest('.gc-swipe');
        if (!wrap || e.touches.length !== 1 || e.target.closest('.gc-acts') || e.target.closest('.gc-qv')) { sw = null; return; }
        var t = e.touches[0];
        sw = { wrap: wrap, card: wrap.querySelector('.gc-card'), x0: t.clientX, y0: t.clientY, dx: 0, moving: false,
               id: parseInt(wrap.getAttribute('data-id'), 10) };
        sw.timer = window.setTimeout(function () {
            // A long press starts selecting, with this message picked.
            if (!sw || sw.moving) return;
            ignoreClickUntil = Date.now() + 700;
            if (S.selecting) toggleSelect(sw.id); else startSelecting(sw.id);
            sw = null;
        }, LONG_PRESS_MS);
    }

    function onTouchMove(e) {
        if (!sw) return;
        var t = e.touches[0], dx = t.clientX - sw.x0, dy = t.clientY - sw.y0;
        if (!sw.moving) {
            if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) { window.clearTimeout(sw.timer); sw = null; return; }   // scrolling
            if (Math.abs(dx) < 12) return;
            if (S.selecting) { window.clearTimeout(sw.timer); sw = null; return; }
            sw.moving = true;
            window.clearTimeout(sw.timer);
            closeSwipes(sw.wrap);
            sw.wrap.classList.add('dragging');
        }
        e.preventDefault();
        sw.dx = Math.max(-176, Math.min(120, dx + (sw.wrap.classList.contains('open') ? -176 : 0)));
        sw.card.style.transform = 'translateX(' + sw.dx + 'px)';
    }

    function onTouchEnd() {
        if (!sw) return;
        window.clearTimeout(sw.timer);
        var s = sw;
        sw = null;
        if (!s.moving) return;
        ignoreClickUntil = Date.now() + 400;
        s.wrap.classList.remove('dragging');
        s.card.style.transform = '';
        if (s.dx >= SWIPE_READ) {
            s.wrap.classList.remove('open');
            var row = rowsFor([s.id])[0];
            // Sent mail has no read state, so a right swipe does nothing there. A conversation row
            // reads (or un-reads) the whole conversation.
            if (row && !rowIsSent(row)) {
                expandIds([s.id]).then(function (ids) {
                    if (ids) bulk(ids, row.isRead ? 'unread' : 'read').then(function (ok) { if (ok) refreshList(); });
                });
            }
        } else {
            s.wrap.classList.toggle('open', s.dx <= -SWIPE_OPEN);
        }
    }

    function swipeAction(b) {
        var wrap = b.closest('.gc-swipe'), id = parseInt(wrap.getAttribute('data-id'), 10);
        wrap.classList.remove('open');
        var follow = b.getAttribute('data-swipe') === 'followup';
        expandIds([id]).then(function (ids) {
            if (!ids) return;
            if (follow) openFollowUp(ids, isSent());
            else deleteMany(ids, null, [id]).then(function (ok) { if (ok) refreshList(); });
        });
    }

    // ---- compose / reply / forward --------------------------------------------------------

    // ---- New message / reply / forward (Story 5575) ----------------------------------------
    //
    // A themed dialog. To, Cc and Bcc are PicassoTags chip fields that ask the server who to
    // suggest (Leonardo's email addresses, named after their contact, and people who wrote) and
    // also take any address typed or pasted. The theme, the signature and whether it is added are
    // remembered for Tony (UserViewState); what is being written is kept as a draft in Leonardo as
    // it is typed, and in Gmail's Drafts when the window closes (Story 5620), until it is sent or
    // discarded -- so closing the dialog, reloading the page or changing device never loses it.

    var THEMES = [
        { key: 'classic',  name: 'Classic',  swatch: '#0A66C2' },
        { key: 'paper',    name: 'Paper',    swatch: '#8B5E34' },
        { key: 'midnight', name: 'Midnight', swatch: '#475569' },
        { key: 'ocean',    name: 'Ocean',    swatch: '#0F766E' },
        { key: 'rose',     name: 'Rose',     swatch: '#BE185D' },
        { key: 'forest',   name: 'Forest',   swatch: '#166534' },
        // Story 5608: the old colors the page themes have, for writing too.
        { key: 'vellum',   name: 'Vellum',   swatch: '#2F3E64' },
        { key: 'library',  name: 'Library',  swatch: '#2F5D3A' },
        { key: 'oxblood',  name: 'Oxblood',  swatch: '#7B1E28' }
    ];
    var VS_THEME = VS_PREFIX + 'composeTheme';
    var VS_SIG = VS_PREFIX + 'signature';
    var VS_SIG_ON = VS_PREFIX + 'signatureOn';
    var SS_DRAFT = VS_PREFIX + 'draft';           // sessionStorage: where a draft lived before Story 5620 (taken over once)
    var EMAIL = /[^\s@<>",;]+@[^\s@<>",;]+\.[A-Za-z]{2,}/;
    var tags = {};                                // to / cc / bcc -> PicassoTags
    var draftTimer = 0;

    function addressOk(v) { return EMAIL.test(v || ''); }

    function recipientSearch(q) {
        return Promise.resolve(A.apiGet(API + '/recipients?top=10' + (q ? '&q=' + encodeURIComponent(q) : ''))).then(function (r) {
            if (!r || !r.ok) return [];
            return (r.data.recipients || []).map(function (x) {
                return {
                    name: x.value,
                    label: x.name || x.address,
                    sub: (x.name ? x.address + '  |  ' : '') + (x.source === 'leonardo' ? 'In Leonardo' : 'You have written to each other')
                };
            });
        });
    }

    function ensureTags() {
        if (tags.to || !window.PicassoTags) return;
        ['To', 'Cc', 'Bcc'].forEach(function (k) {
            tags[k.toLowerCase()] = window.PicassoTags.attach({
                input: el('c' + k), chips: el('c' + k + 'Chips'), dropdown: el('c' + k + 'Drop'),
                search: recipientSearch, validate: addressOk, split: /[,;]+/,
                noun: 'address', testid: 'gmail-client-compose-' + k.toLowerCase(),
                addLabel: function (t) { return addressOk(t) ? 'Use ' + t : t + ' -- not an email address yet'; },
                placeholder: k === 'To' ? 'Name or email address' : '',
                chipLabel: function (v) { var m = /^\s*"?([^"<]+?)"?\s*<([^>]+)>\s*$/.exec(v); return m ? m[1] : v; },
                onChange: function () { el('cErr').hidden = true; saveDraftSoon(); }
            });
        });
    }

    function drawThemes() {
        var cur = recalled(VS_THEME) || 'classic';
        if (!THEMES.some(function (t) { return t.key === cur; })) cur = 'classic';
        el('cDialog').setAttribute('data-theme', cur);
        el('cThemes').innerHTML = THEMES.map(function (t) {
            return '<button type="button" class="gc-theme' + (t.key === cur ? ' on' : '') + '" data-theme="' + t.key + '"'
                + ' style="--sw:' + t.swatch + '" aria-pressed="' + (t.key === cur) + '" aria-label="' + t.name + ' theme"'
                + ' title="' + t.name + '" data-testid="gmail-client-compose-theme-' + t.key + '"></button>';
        }).join('');
    }

    function drawSignature() {
        el('cSigOn').checked = recalled(VS_SIG_ON) !== '0' && !!(recalled(VS_SIG) || '').trim();
        el('cSigOn').disabled = !(recalled(VS_SIG) || '').trim();
        el('cSig').value = recalled(VS_SIG) || '';
    }

    function drawCount() {
        var words = (el('cBody').value.trim().match(/\S+/g) || []).length;
        el('cCount').textContent = words ? plural(words, 'word') : '';
    }

    // ---- the draft (Story 5620): kept in Leonardo as you type, and in Gmail's Drafts on close ----
    //
    // Every change is saved to Leonardo a moment after you stop typing -- one save at a time, in
    // order, so the first save's id is the one every later save updates (and the page's own key
    // means a save sent twice is still one draft). Closing the window puts the draft in Gmail's
    // Drafts folder too. Sending or discarding closes it in both places.

    var DRAFT_DELAY_MS = 1500;

    /** The draft's kind as the server names it: a forward of a sent message is forwardSent. */
    function draftMode(c) { return c.mode === 'forward' && c.sent ? 'forwardSent' : c.mode; }

    /** A key for a draft not saved yet, so a save sent twice is still one draft. */
    function newClientKey() { return 'd' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10); }

    /** "A <a@x>, \"Burns, Tony\" <t@y>" -> each address, commas inside quotes kept. */
    function splitAddresses(s) {
        return String(s || '').split(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/).map(function (x) { return x.trim(); }).filter(Boolean);
    }

    /** What the window holds, as a draft save carries it; empty = nothing written yet. */
    function draftPayload(c) {
        var files = (c.files || []).filter(function (f) { return f.kind === 'nook' || f.token; })
            .map(function (f) { return { kind: f.kind, token: f.token || null, nook: f.nook || null, name: f.name, size: f.size || 0 }; });
        var to = tags.to ? tags.to.getValues() : [], cc = tags.cc ? tags.cc.getValues() : [], bcc = tags.bcc ? tags.bcc.getValues() : [];
        var free = c.mode === 'new' || c.mode === 'gmail';
        var p = {
            mode: draftMode(c), sourceId: free ? null : c.id, to: to.join(', '), cc: cc.join(', '), bcc: bcc.join(', '),
            subject: el('cSubject').value, body: el('cBody').value,
            includeOriginal: !el('cOrigRow').hidden && el('cIncludeOrig').checked, attachments: files
        };
        p.empty = !p.body.trim() && !to.length && !cc.length && !bcc.length && !files.length && (!free || !p.subject.trim());
        return p;
    }

    /** The draft line at the foot of the window (only while that draft's window is the one open). */
    function drawDraftState(c, text, isErr) {
        if (S.compose !== c) return;
        el('cDraft').textContent = text;
        el('cDraft').classList.toggle('err', !!isErr);
    }

    function saveDraftSoon() {
        var c = S.compose;
        if (!c) return;
        c.changed = true;
        clearTimeout(draftTimer);
        draftTimer = setTimeout(function () { saveDraft(c, false); }, DRAFT_DELAY_MS);
    }

    /**
     * Saves what is written as the draft. toGmail (closing the window) also puts it in Gmail's
     * Drafts. Saves queue behind each other on c.chain. A Promise of the save's answer, or null.
     */
    function saveDraft(c, toGmail) {
        if (!c || c.done || c.sending) return Promise.resolve(null);
        clearTimeout(draftTimer);
        var p = draftPayload(c);
        c.chain = Promise.resolve(c.chain).then(function () {
            if (c.done) return null;
            if (p.empty && !c.draftId) { drawDraftState(c, ''); return null; }
            drawDraftState(c, 'Saving draft');
            return Promise.resolve(A.apiPost(API + '/drafts', {
                id: c.draftId || 0, clientKey: c.clientKey, mode: p.mode, sourceId: p.sourceId, to: p.to, cc: p.cc, bcc: p.bcc,
                subject: p.subject, body: p.body, includeOriginal: p.includeOriginal, attachments: p.attachments,
                toGmail: !!toGmail && !p.empty
            })).then(function (r) {
                if (!r || !r.ok) {
                    var why = errText(r, 'The draft was not saved.');
                    drawDraftState(c, 'Draft not saved: ' + why, true);
                    if (S.compose !== c) note(why, true);   // the window is closed: say it on the page
                    return null;
                }
                c.draftId = r.data.id;
                if (toGmail && !r.data.gmailError) c.gmailUpToDate = true;
                drawDraftState(c, r.data.gmailError ? 'Draft saved in Echo, not yet in Outlook' : 'Draft saved');
                if (r.data.gmailError) note(r.data.gmailError, true);
                return r.data;
            });
        }).catch(function () { return null; });
        return c.chain;
    }

    /**
     * The window is closing: what was written is kept (in Leonardo and Gmail's Drafts). A draft
     * emptied out is thrown away; one not touched since Gmail last had it costs no call at all.
     */
    function finishDraft(c) {
        if (!c || c.done || c.sending) return;
        var p = draftPayload(c);
        if (p.empty) {
            clearTimeout(draftTimer);
            c.done = true;
            Promise.resolve(c.chain).then(function () {
                if (!c.draftId) return null;
                return Promise.resolve(A.apiPost(API + '/drafts/' + c.draftId + '/discard', {})).then(refreshList);
            });
            return;
        }
        if (!c.changed && c.draftId && c.gmailUpToDate) return;
        saveDraft(c, true).then(function (d) { if (d) refreshList(); });   // the Drafts tab's count, and its list when showing
    }

    /** Before Story 5620 a draft lived in this browser tab only; one still there is taken over once. */
    function takeLegacyDraft(mode, m) {
        try {
            var key = mode + ':' + (rowIsSent(m) ? 's' : '') + (m ? m.id : 0);
            var d = JSON.parse(sessionStorage.getItem(SS_DRAFT) || 'null');
            if (!d || d.key !== key) return null;
            sessionStorage.removeItem(SS_DRAFT);
            return { to: (d.to || []).join(', '), cc: (d.cc || []).join(', '), bcc: (d.bcc || []).join(', '), subject: d.subject,
                     body: d.body, attachments: JSON.stringify(d.files || []), includeOriginal: true, legacy: true };
        } catch (e) { return null; }
    }

    // ---- opening, closing, sending ----

    /**
     * New message, Reply, Reply all, Forward, Write again. A reply or forward of a message Tony
     * already started answering opens that draft (Story 5620).
     */
    function openCompose(mode) {
        var m = mode === 'new' ? null : S.open;
        var fromReader = mode !== 'new' && !el('reader').hidden;
        if (!m) { fillCompose(mode, null, null, fromReader); return Promise.resolve(); }
        var srv = mode === 'forward' && rowIsSent(m) ? 'forwardSent' : mode;
        return Promise.resolve(A.apiGet(API + '/drafts/find?mode=' + encodeURIComponent(srv) + '&source=' + m.id)).then(function (r) {
            fillCompose(mode, m, r && r.ok && r.data ? r.data.draft : null, fromReader);
        });
    }

    /** A draft from the Drafts tab: the message it answers is read first, so the window shows what it is. */
    function openDraft(id) {
        return Promise.resolve(A.apiGet(API + '/drafts/' + id)).then(function (r) {
            if (!r || !r.ok) {
                note(errText(r, 'Could not open the draft.'), true);
                if (r && r.status === 404) refreshList();   // sent or discarded elsewhere
                return;
            }
            var d = r.data, mode = d.mode === 'forwardSent' ? 'forward' : d.mode;
            if (mode === 'new' || mode === 'gmail') { fillCompose(mode, null, d, false); return; }
            var sent = d.mode === 'writeAgain' || d.mode === 'forwardSent';
            return Promise.resolve(A.apiGet(msgUrl(d.sourceId, sent) + (sent ? '' : '?peek=true'))).then(function (s) {
                if (!s || !s.ok) {
                    note(errText(s, 'The message this draft answers could not be opened') + ' -- discard the draft, or copy its words first.', true);
                    return;
                }
                fillCompose(mode, s.data, d, false);
            });
        });
    }

    /** Discard on a Drafts tab row. It asks first, naming the draft. */
    function discardDraftRow(id, btn) {
        var m = rowsFor([id])[0];
        var name = m ? '"' + (m.subject || '(no subject)') + '"' + (m.to ? ' to ' + toNames(m.to) : '') : 'this draft';
        Promise.resolve(window.Picasso.confirmDelete('Discard draft: ' + name + '?\n\nIt is thrown away here and in Outlook\'s Drafts.'
            + ' Nothing has been sent. This cannot be undone.', { title: 'Discard draft', yes: 'Discard' })).then(function (ok) {
            if (!ok) return;
            btn.disabled = true;
            return Promise.resolve(A.apiPost(API + '/drafts/' + id + '/discard', {})).then(function (r) {
                btn.disabled = false;
                if (!r || !r.ok) { note(errText(r, 'The draft was not discarded.'), true); return; }
                if (r.data && r.data.gmailError) note(r.data.gmailError, true);
                return refreshList();
            });
        });
    }

    /** Fills and shows the writing window: mode, the message it answers (m), and a draft to carry on (d). */
    function fillCompose(mode, m, d, fromReader) {
        ensureTags();
        var sent = rowIsSent(m);
        if (!d && mode !== 'gmail') d = takeLegacyDraft(mode, m);
        // fromReader: Send closes the open message too, the way mail clients do (Story 5608 QA).
        S.compose = { mode: mode, id: m ? m.id : 0, sent: sent, fromReader: fromReader,
                      draftId: d && !d.legacy ? d.id : 0, clientKey: newClientKey(),
                      gmailUpToDate: !!(d && d.gmailUpToDate), changed: !!(d && d.legacy) };
        var titles = { 'new': 'New message', reply: 'Reply', replyAll: 'Reply all', writeAgain: 'Write again', forward: 'Forward',
                       gmail: 'Draft' };
        // Write again (Story 5596): a sent message, to the same people in the same conversation -- a reply in all but name.
        var isReply = mode === 'reply' || mode === 'replyAll' || mode === 'writeAgain';
        var free = mode === 'new' || mode === 'gmail';
        el('cTitle').textContent = titles[mode];
        drawThemes();
        drawSignature();
        el('cSigEditor').hidden = true;

        // Replies go back to who wrote (and, for Reply all, everyone copied); the server addresses them.
        el('cToFixedRow').hidden = !isReply;
        el('cToRow').hidden = isReply;
        el('cToFixed').textContent = !isReply ? ''
            : mode === 'writeAgain' ? toNames(m.to) + (m.cc ? ', Cc ' + toNames(m.cc) : '')
            : (m.from || '') + (mode === 'replyAll' ? ' and everyone copied' : '');
        el('cShowCc').hidden = isReply;
        el('cReply').hidden = !(isReply || mode === 'forward');
        el('cReplyText').innerHTML = !(m && (isReply || mode === 'forward')) ? ''
            : mode === 'writeAgain' ? 'Writing again about <b>' + esc(m.subject || '(no subject)') + '</b>'
            : sent ? 'Forwarding your message to <b>' + esc(toNames(m.to)) + '</b> -- ' + esc(m.subject || '(no subject)')
            : (isReply ? 'Replying to ' : 'Forwarding ') + '<b>' + esc(displayName(m.from)) + '</b> -- ' + esc(m.subject || '(no subject)');
        el('cOrig').hidden = true;
        el('cOrig').textContent = m ? (m.bodyText || '') : '';
        el('cShowOrig').hidden = !(m && m.bodyText);
        el('cShowOrig').textContent = 'Show the original';

        var to = d ? splitAddresses(d.to) : [], cc = d ? splitAddresses(d.cc) : [], bcc = d ? splitAddresses(d.bcc) : [];
        if (tags.to) {
            tags.to.setValues(to);
            tags.cc.setValues(cc);
            tags.bcc.setValues(bcc);
        }
        var showCc = !isReply && (cc.length > 0 || bcc.length > 0);
        el('cCcRow').hidden = !showCc;
        el('cBccRow').hidden = !showCc;
        if (showCc) el('cShowCc').hidden = true;
        el('cSubject').value = d && d.subject != null ? d.subject : (free ? '' : (isReply ? 'Re: ' : 'Fwd: ') + (m.subject || ''));
        el('cSubject').disabled = !free;
        el('cBody').value = d ? (d.body || '') : '';
        drawDraftState(S.compose, d ? 'Draft restored' : '');
        el('cHint').textContent = mode === 'gmail'
            ? 'Started in Outlook. It goes exactly as written' + (d && d.subject && /^re:/i.test(d.subject) ? ', in the same conversation.' : '.')
            : mode === 'writeAgain'
            ? 'It goes to the same people, in the same conversation, with your earlier message quoted below.'
            : isReply ? 'The original message is quoted below what you write.'
            : mode === 'forward' ? 'The original message goes below what you write.' : '';
        el('cErr').hidden = true;
        el('cSend').disabled = false;
        el('cSend').textContent = 'Send';
        // Story 5608: attachments -- from the draft, and (a forward) the original's own.
        var kept = [];
        try { kept = d && d.attachments ? JSON.parse(d.attachments) : []; } catch (e) { kept = []; }
        S.compose.files = (Array.isArray(kept) ? kept : []).filter(function (f) { return f && (f.kind === 'nook' || f.token); })
            .map(function (f) { return { key: ++fileSeq, kind: f.kind, token: f.token, nook: f.nook, name: f.name, size: f.size }; });
        var origs = (mode === 'forward' && m && m.attachments) ? m.attachments : [];
        el('cOrigRow').hidden = !origs.length;
        el('cIncludeOrig').checked = origs.length > 0 && (d && !d.legacy ? !!d.includeOriginal : true);
        el('cOrigText').textContent = origs.length ? 'Include the original\'s ' + plural(origs.length, 'attachment') + ': ' + origs.join(', ') : '';
        el('cAttachMenu').hidden = true;
        el('cAttachMenu').querySelector('[data-attach="nook"]').hidden = DEMO || ECHO;   // Story 5654: no Reading Nook
        drawFiles();
        drawCount();
        el('compose').hidden = false;
        // A draft already addressed carries on in its text; focusing To would open its suggestions over the subject.
        if (isReply || to.length) el('cBody').focus(); else if (tags.to) tags.to.focus(); else el('cTo').focus();
    }

    /** Close (and Save) keep what was written as a draft; Discard (with a confirm) throws it away. */
    function closeCompose() {
        if (S.compose) finishDraft(S.compose);
        if (tags.to) { tags.to.close(); tags.cc.close(); tags.bcc.close(); }
        el('compose').hidden = true;
        el('cDialog').classList.remove('gc-cfull');
        el('cFull').setAttribute('aria-pressed', 'false');
        S.compose = null;
    }

    function discardCompose() {
        var c = S.compose;
        var files = (c && c.files) || [];
        var hasText = el('cBody').value.trim() || files.length
            || (tags.to && (tags.to.getValues().length || tags.cc.getValues().length || tags.bcc.getValues().length));
        var go = hasText || (c && c.draftId)
            ? window.Picasso.confirmDelete('Discard this message' + (el('cSubject').value.trim() ? ': ' + el('cSubject').value.trim() : '') + '?\n\n'
                + 'What you wrote' + (files.length ? ' and ' + plural(files.length, 'attachment') : '')
                + ' is thrown away, here and in Outlook\'s Drafts. Nothing has been sent. This cannot be undone.',
                { title: 'Discard', yes: 'Discard' })
            : Promise.resolve(true);
        Promise.resolve(go).then(function (ok) {
            if (!ok || !c) return;
            clearTimeout(draftTimer);
            c.done = true;   // no save after this; one already on its way finishes first, then is discarded
            files.forEach(function (f) { if (f.token) A.apiPost(API + '/attachments/' + encodeURIComponent(f.token), null, 'DELETE'); });
            Promise.resolve(c.chain).then(function () {
                if (!c.draftId) return null;
                return Promise.resolve(A.apiPost(API + '/drafts/' + c.draftId + '/discard', {})).then(function (r) {
                    if (r && !r.ok && r.status !== 404) note(errText(r, 'The draft was not discarded.'), true);
                    else if (r && r.data && r.data.gmailError) note(r.data.gmailError, true);
                    return refreshList();
                });
            });
            S.compose = null;
            closeCompose();
        });
    }

    // ---- attachments (Story 5608) --------------------------------------------------------------

    var MAX_ATTACH = 25 * 1024 * 1024;   // Gmail's limit for one message
    var fileSeq = 0;

    function sizeText(n) {
        if (!(n >= 0)) return '';
        if (n < 1024) return n + ' B';
        if (n < 1024 * 1024) return Math.round(n / 1024) + ' KB';
        return (n / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function drawFiles() {
        var files = (S.compose && S.compose.files) || [];
        el('cFiles').innerHTML = files.map(function (f, i) {
            var state = f.uploading ? ' -- uploading' : f.error ? ' -- ' + f.error : '';
            return '<span class="gc-cfile' + (f.error ? ' bad' : '') + (f.uploading ? ' busy' : '') + '" title="' + esc(f.error || f.name) + '"'
                + ' data-testid="gmail-client-compose-file">' + (f.kind === 'nook' ? '&#128214; ' : '&#128206; ')
                + esc(f.name) + (f.size ? ' <span class="gc-cfile-size">' + sizeText(f.size) + '</span>' : '') + esc(state)
                + '<button type="button" class="gc-linkx" data-file-x="' + i + '" aria-label="Take ' + esc(f.name) + ' off"'
                + ' title="Take it off" data-testid="gmail-client-compose-file-remove">&times;</button></span>';
        }).join('');
    }

    /** Files picked on the device: each uploads at once and is held until Send. */
    function addDeviceFiles(list) {
        if (!S.compose) return;
        Array.prototype.forEach.call(list || [], function (file) {
            var f = { key: ++fileSeq, kind: 'upload', name: file.name, size: file.size, uploading: true };
            S.compose.files.push(f);
            if (file.size > MAX_ATTACH) {
                f.uploading = false;
                f.error = 'over 25 MB, more than one message carries';
                return;
            }
            var fd = new FormData();
            fd.append('file', file, file.name);
            Promise.resolve(A.apiPostForm(API + '/attachments', fd)).then(function (r) {
                f.uploading = false;
                if (!r || !r.ok) f.error = errText(r, 'not attached');
                else { f.token = r.data.token; f.size = r.data.size; f.name = r.data.name || f.name; }
                drawFiles();
                saveDraftSoon();
            });
        });
        drawFiles();
    }

    function removeFile(i) {
        var files = S.compose && S.compose.files;
        if (!files || !files[i]) return;
        var f = files.splice(i, 1)[0];
        if (f.token) A.apiPost(API + '/attachments/' + encodeURIComponent(f.token), null, 'DELETE');
        drawFiles();
        saveDraftSoon();
    }

    // ---- attach from the Reading Nook ----

    var nookSeq = 0, nookTimer = 0;

    function openNookPicker() {
        el('npErr').hidden = true;
        el('npSearch').value = '';
        el('nookPick').hidden = false;
        loadNookFiles();
        el('npSearch').focus();
    }

    function loadNookFiles() {
        var q = el('npSearch').value.trim();
        var seq = ++nookSeq;
        el('npList').innerHTML = '<div class="gc-hint">Looking</div>';
        Promise.resolve(A.apiGet('/api/reading-nook/search?q=' + encodeURIComponent(q))).then(function (r) {
            if (seq !== nookSeq) return;
            if (!r || !r.ok) { el('npList').innerHTML = '<div class="gc-hint">' + esc(errText(r, 'Could not look in the Reading Nook.')) + '</div>'; return; }
            var rows = (Array.isArray(r.data) ? r.data : []).filter(function (x) { return x.view !== 'trash'; });
            var shown = rows.slice(0, 60);
            el('npList').innerHTML = shown.length ? shown.map(function (x, i) {
                return '<button type="button" class="gc-pick-row" data-nook-i="' + i + '" data-testid="gmail-client-nook-file">'
                    + '<b>' + esc(x.name) + '</b> <span class="gc-pick-sub">' + esc(x.view) + '  |  ' + sizeText(x.size) + '</span></button>';
            }).join('') + (rows.length > shown.length ? '<div class="gc-hint">' + (rows.length - shown.length) + ' more -- type to narrow.</div>' : '')
                : '<div class="gc-hint">No file in the Reading Nook matches.</div>';
            el('npList').nookRows = shown;
        });
    }

    function pickNookFile(btn) {
        var x = (el('npList').nookRows || [])[parseInt(btn.getAttribute('data-nook-i'), 10)];
        if (!x || !S.compose) return;
        if (x.size > MAX_ATTACH) { el('npErr').textContent = x.name + ' is over 25 MB, more than one message carries.'; el('npErr').hidden = false; return; }
        S.compose.files.push({ key: ++fileSeq, kind: 'nook', nook: x.view, name: x.name, size: x.size });
        el('nookPick').hidden = true;
        drawFiles();
        saveDraftSoon();
    }

    function sendCompose() {
        var c = S.compose, b = el('cSend');
        if (!c || b.disabled) return;
        var to = tags.to ? tags.to.commit() : [], cc = tags.cc ? tags.cc.commit() : [], bcc = tags.bcc ? tags.bcc.commit() : [];
        var bad = to.concat(cc, bcc).filter(function (v) { return !addressOk(v); });
        function fail(msg) { el('cErr').textContent = msg; el('cErr').hidden = false; }
        if (c.mode === 'new' || c.mode === 'forward' || c.mode === 'gmail') {
            if (!to.length) { fail('Who is it to? Add at least one address.'); return; }
            if (bad.length) { fail('Fix the address in red: ' + bad.join(', ')); return; }
        }
        var files = c.files || [];
        if (files.some(function (f) { return f.uploading; })) { fail('Wait a moment -- an attachment is still uploading.'); return; }
        if (files.some(function (f) { return f.error; })) { fail('Take off the attachment marked in red, then send.'); return; }
        var total = files.reduce(function (n, f) { return n + (f.size || 0); }, 0);
        if (total > MAX_ATTACH) { fail('Send at most 25 MB of attachments in one message; these come to ' + sizeText(total) + '.'); return; }
        var attachments = files.map(function (f) {
            return f.kind === 'nook' ? { nook: f.nook, name: f.name } : { upload: f.token, name: f.name };
        });
        var includeOriginal = !el('cOrigRow').hidden && el('cIncludeOrig').checked;
        var body = el('cBody').value;
        var sig = (recalled(VS_SIG) || '').trim();
        if (el('cSigOn').checked && sig && body.indexOf(sig) < 0) body = body.replace(/\s+$/, '') + '\n\n-- \n' + sig;
        var url, payload;
        if (c.mode === 'reply' || c.mode === 'replyAll') {
            url = API + '/messages/' + c.id + '/reply';
            payload = { body: body, replyAll: c.mode === 'replyAll', attachments: attachments };
        } else if (c.mode === 'writeAgain') {
            url = msgUrl(c.id, true) + '/write-again';
            payload = { body: body, attachments: attachments };
        } else if (c.mode === 'forward') {
            url = msgUrl(c.id, c.sent) + '/forward';
            payload = { to: to.join(', '), cc: cc.join(', '), bcc: bcc.join(', '), body: body, attachments: attachments,
                        includeOriginal: includeOriginal };
        } else {
            // New message -- and (Story 5620) a draft started in Gmail, sent through its own address below.
            url = API + '/send';
            payload = { to: to.join(', '), cc: cc.join(', '), bcc: bcc.join(', '), subject: el('cSubject').value, body: body,
                        attachments: attachments };
        }
        el('cErr').hidden = true;
        b.disabled = true;   // a Send pressed twice must not send twice
        b.textContent = 'Sending';
        // Story 5620: no draft save while it sends; one already on its way finishes first, so the send
        // knows the draft it finishes (closed, and taken out of Gmail's Drafts, once it goes).
        clearTimeout(draftTimer);
        c.sending = true;
        Promise.resolve(c.chain).then(function () {
            if (c.draftId) payload.draftId = c.draftId;
            // A draft started in Gmail goes as written, in the conversation Gmail kept.
            if (c.mode === 'gmail') url = API + '/drafts/' + c.draftId + '/send';
            return A.apiPost(url, payload);
        }).then(function (r) {
            b.textContent = 'Send';
            if (!r || !r.ok) {
                b.disabled = false;
                c.sending = false;
                fail(errText(r, 'The message was not sent.'));
                return;
            }
            c.done = true;
            S.compose = null;
            closeCompose();
            if (c.fromReader && !el('reader').hidden) closeReader();
            flash('Sent to ' + r.data.to + (r.data.attachments ? ' with ' + plural(r.data.attachments, 'attachment') : ''));
            refreshList();   // it is in the Sent tab already (Story 5596): its count, and the list when showing
        });
    }

    function wireCompose() {
        el('cCancel').addEventListener('click', closeCompose);
        el('cX').addEventListener('click', closeCompose);
        el('cSend').addEventListener('click', sendCompose);
        el('cDiscard').addEventListener('click', discardCompose);
        el('cThemes').addEventListener('click', function (e) {
            var t = e.target.closest('[data-theme]');
            if (!t) return;
            remember(VS_THEME, t.getAttribute('data-theme'));
            drawThemes();
        });
        el('cFull').addEventListener('click', function () {
            var on = !el('cDialog').classList.contains('gc-cfull');
            el('cDialog').classList.toggle('gc-cfull', on);
            el('cFull').setAttribute('aria-pressed', String(on));
        });
        el('cShowCc').addEventListener('click', function () {
            el('cCcRow').hidden = false;
            el('cBccRow').hidden = false;
            el('cShowCc').hidden = true;
            if (tags.cc) tags.cc.focus();
        });
        el('cShowOrig').addEventListener('click', function () {
            var open = el('cOrig').hidden;
            el('cOrig').hidden = !open;
            el('cShowOrig').textContent = open ? 'Hide the original' : 'Show the original';
        });
        el('cSigOn').addEventListener('change', function () { remember(VS_SIG_ON, el('cSigOn').checked ? '1' : '0'); });
        el('cSigEdit').addEventListener('click', function () {
            el('cSigEditor').hidden = !el('cSigEditor').hidden;
            if (!el('cSigEditor').hidden) el('cSig').focus();
        });
        el('cSigSave').addEventListener('click', function () {
            remember(VS_SIG, el('cSig').value.trim());
            if (el('cSig').value.trim()) remember(VS_SIG_ON, '1');
            drawSignature();
            el('cSigEditor').hidden = true;
            flash(el('cSig').value.trim() ? 'Signature saved' : 'Signature cleared');
        });
        ['cSubject', 'cBody'].forEach(function (id) {
            el(id).addEventListener('input', function () { saveDraftSoon(); if (id === 'cBody') drawCount(); });
        });
        // Control or Command and Enter sends, for the keyboard.
        el('cDialog').addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); sendCompose(); }
        });
    }

    // ---- custom search --------------------------------------------------------------------

    function openCustom() {
        var c = S.custom || {};
        el('sFrom').value = c.from || '';
        el('sTo').value = c.to || '';
        el('sSubject').value = c.subject || '';
        el('sWords').value = c.words || '';
        el('sAfter').value = c.after || '';
        el('sBefore').value = c.before || '';
        el('sUnread').checked = !!c.unread;
        fillInboxSelect(el('sInbox'), c.inboxKey && /^inbox:(\d+)$/.test(c.inboxKey) ? c.inboxKey.split(':')[1] : '', 'Any inbox');
        if (c.inboxKey === 'unclassified') { addUnclassifiedOption(el('sInbox')); el('sInbox').value = 'unclassified'; }
        else addUnclassifiedOption(el('sInbox'));
        el('custom').hidden = false;
        el('sFrom').focus();
    }

    function addUnclassifiedOption(sel) {
        if (!sel.querySelector('option[value="unclassified"]')) {
            var o = document.createElement('option');
            o.value = 'unclassified';
            o.textContent = 'Unclassified';
            sel.appendChild(o);
        }
        if (window.PicassoSelect && PicassoSelect.refresh) PicassoSelect.refresh(sel);
    }

    function runCustom() {
        var inboxVal = el('sInbox').value;
        var c = {
            from: el('sFrom').value.trim(), to: el('sTo').value.trim(), subject: el('sSubject').value.trim(),
            words: el('sWords').value.trim(), after: el('sAfter').value, before: el('sBefore').value,
            unread: el('sUnread').checked,
            inboxKey: inboxVal === 'unclassified' ? 'unclassified' : inboxVal ? 'inbox:' + inboxVal : ''
        };
        var any = c.from || c.to || c.subject || c.words || c.after || c.before || c.unread;
        S.custom = any ? c : null;
        el('custom').hidden = true;
        drawFilterLine();
        setTab(c.inboxKey || 'all');
    }

    function clearFilters() {
        S.q = '';
        S.custom = null;
        el('search').value = '';
        drawFilterLine();
        loadList();
    }

    // ---- inboxes dialog -------------------------------------------------------------------

    function openInboxes() {
        el('iErr').hidden = true;
        el('newInbox').value = '';
        S.arranging = false;
        loadInboxes().then(function () { drawInboxList(); el('inboxes').hidden = false; });
    }

    function drawInboxList() {
        var b = el('btnArrange');
        if (S.inboxes.length < 2) S.arranging = false;
        b.hidden = S.inboxes.length < 2;
        b.classList.toggle('on', S.arranging);
        b.setAttribute('aria-pressed', String(S.arranging));
        el('inboxes').classList.toggle('gc-arranging', S.arranging);
        el('inboxList').innerHTML = S.inboxes.length ? S.inboxes.map(function (i) {
            return '<div class="gc-inbox-row" data-id="' + i.id + '" data-testid="gmail-client-inbox-row">'
                + '<span class="gc-grip" data-grip aria-hidden="true" title="Drag to move" data-testid="gmail-client-inbox-grip">&#8942;&#8942;</span>'
                + '<span class="gc-inbox-label">' + esc(i.name) + '</span>'
                + '<input type="text" value="' + esc(i.name) + '" aria-label="Inbox name" data-testid="gmail-client-inbox-name">'
                + '<button type="button" class="gc-count" data-act="senders" title="See who is in this inbox"'
                + ' data-testid="gmail-client-inbox-senders">' + plural(i.senderCount, 'sender') + '</button>'
                + '<button type="button" class="gc-btn" data-act="rename" data-testid="gmail-client-inbox-rename">Save</button>'
                + '<button type="button" class="gc-btn danger" data-act="delete" data-testid="gmail-client-inbox-delete">Delete</button>'
                + '</div>';
        }).join('') : '<div class="gc-hint">No inboxes yet.</div>';
    }

    function inboxError(r, d) {
        el('iErr').textContent = errText(r, d);
        el('iErr').hidden = false;
    }

    function afterInboxChange() {
        el('iErr').hidden = true;
        return loadInboxes().then(function () { drawInboxList(); refreshTabs(); });
    }

    function addInbox() {
        var b = el('btnAddInbox'), name = el('newInbox').value.trim();
        if (b.disabled) return;
        b.disabled = true;
        Promise.resolve(A.apiPost(API + '/inboxes', { name: name })).then(function (r) {
            b.disabled = false;
            if (!r || !r.ok) { inboxError(r, 'Could not add the inbox.'); return; }
            el('newInbox').value = '';
            afterInboxChange();
        });
    }

    // Arrange (Chess Shuffle): drag an inbox by its grip. Pointer events with the move / up
    // listeners on the DOCUMENT, never pointer capture -- the Notes tab and Solutions pattern.
    function onGripDown(down) {
        var grip = down.target.closest('[data-grip]');
        if (!grip || !S.arranging) return;
        var row = grip.closest('.gc-inbox-row'), host = el('inboxList');
        down.preventDefault();
        row.classList.add('dragging');
        function order() {
            return Array.prototype.map.call(host.querySelectorAll('.gc-inbox-row'), function (r) {
                return parseInt(r.getAttribute('data-id'), 10);
            });
        }
        var before = order().join(',');
        function onMove(ev) {
            var under = document.elementFromPoint(ev.clientX, ev.clientY);
            var over = under && under.closest ? under.closest('.gc-inbox-row') : null;
            if (!over || over === row || over.parentNode !== host) return;
            var r = over.getBoundingClientRect();
            var ref = ev.clientY > r.top + r.height / 2 ? over.nextSibling : over;
            if (ref === row || row.nextSibling === ref) return;
            host.insertBefore(row, ref);
        }
        function onUp() {
            document.removeEventListener('pointermove', onMove);
            document.removeEventListener('pointerup', onUp);
            document.removeEventListener('pointercancel', onUp);
            row.classList.remove('dragging');
            var ids = order();
            if (ids.join(',') !== before) saveInboxOrder(ids);
        }
        document.addEventListener('pointermove', onMove);
        document.addEventListener('pointerup', onUp);
        document.addEventListener('pointercancel', onUp);
    }

    function saveInboxOrder(ids) {
        Promise.resolve(A.apiPost(API + '/inboxes/reorder', { ids: ids })).then(function (r) {
            if (!r || !r.ok) { inboxError(r, 'Could not save the new order.'); return afterInboxChange(); }
            el('iErr').hidden = true;
            S.inboxes = r.data.inboxes || S.inboxes;
            if (r.data.tabs) { S.tabs = r.data.tabs; drawTabs(); }
            drawInboxList();
            flash('Inbox order saved');
        });
    }

    function inboxAction(e) {
        var b = e.target.closest('[data-act]');
        if (!b) return;
        var row = b.closest('.gc-inbox-row'), id = parseInt(row.getAttribute('data-id'), 10);
        var inbox = S.inboxes.filter(function (i) { return i.id === id; })[0];
        if (!inbox) return;
        if (b.getAttribute('data-act') === 'senders') { openInboxSenders(inbox); return; }
        if (b.getAttribute('data-act') === 'rename') {
            b.disabled = true;
            Promise.resolve(A.apiPost(API + '/inboxes/' + id, { name: row.querySelector('input').value }, 'PUT')).then(function (r) {
                b.disabled = false;
                if (!r || !r.ok) { inboxError(r, 'Could not rename the inbox.'); return; }
                afterInboxChange();
            });
            return;
        }
        if (!window.confirm('Delete the inbox "' + inbox.name + '"?\n\n'
            + (inbox.senderCount ? 'Its ' + plural(inbox.senderCount, 'sender') + ' go back to Unclassified. ' : '')
            + 'No mail is deleted.\n\nThis cannot be undone.')) return;
        b.disabled = true;
        Promise.resolve(A.apiPost(API + '/inboxes/' + id, null, 'DELETE')).then(function (r) {
            b.disabled = false;
            if (!r || !r.ok) { inboxError(r, 'Could not delete the inbox.'); return; }
            if (S.tab === 'inbox:' + id) S.tab = 'all';
            afterInboxChange();
        });
    }

    // ---- who is in an inbox (tap its sender count in Inboxes) ------------------------------

    var sendersInbox = null;   // the inbox whose senders are showing

    function openInboxSenders(inbox) {
        sendersInbox = inbox;
        el('sdTitle').textContent = 'Senders in ' + inbox.name;
        el('sdErr').hidden = true;
        el('sdList').innerHTML = '<div class="gc-hint">Loading</div>';
        el('senders').hidden = false;
        return loadInboxSenders();
    }

    function loadInboxSenders() {
        var inbox = sendersInbox;
        if (!inbox) return Promise.resolve();
        return Promise.resolve(A.apiGet(API + '/inboxes/' + inbox.id + '/senders')).then(function (r) {
            if (!r || !r.ok) { el('sdList').innerHTML = '<div class="gc-hint">' + esc(errText(r, 'Could not load the senders.')) + '</div>'; return; }
            var list = r.data.senders || [];
            el('sdHint').textContent = list.length
                ? plural(list.length, 'sender') + ' -- every message from them shows in ' + inbox.name + '.'
                : '';
            el('sdList').innerHTML = list.length ? list.map(function (s) {
                var bits = [plural(s.messages, 'message')];
                if (s.unread) bits.push(s.unread + ' unread');
                if (s.lastReceived) bits.push('last ' + shortWhen(s.lastReceived));
                return '<div class="gc-sender-row" data-key="' + esc(s.senderKey) + '" data-testid="gmail-client-sender-row">'
                    + '<div class="gc-sender-main"><div class="gc-sender-name">' + esc(s.name || s.senderKey) + '</div>'
                    + (s.name ? '<div class="gc-sender-key">' + esc(s.senderKey) + '</div>' : '')
                    + '<div class="gc-sender-meta">' + esc(bits.join('  |  ')) + '</div></div>'
                    + '<button type="button" class="gc-btn" data-sd="show" data-testid="gmail-client-sender-show">Show mail</button>'
                    + '<button type="button" class="gc-btn danger" data-sd="remove" data-testid="gmail-client-sender-remove">Remove</button>'
                    + '</div>';
            }).join('') : '<div class="gc-hint">No senders in ' + esc(inbox.name)
                + ' yet. Open any message and choose this inbox under "Inbox for".</div>';
        });
    }

    function senderAction(e) {
        var b = e.target.closest('[data-sd]');
        if (!b || !sendersInbox) return;
        var row = b.closest('.gc-sender-row'), key = row.getAttribute('data-key');
        var inbox = sendersInbox;
        if (b.getAttribute('data-sd') === 'show') {
            // Close the dialogs and show that sender's mail in its inbox.
            el('senders').hidden = true;
            el('inboxes').hidden = true;
            S.q = key;
            S.custom = null;
            el('search').value = key;
            drawFilterLine();
            setTab('all');   // the quick search searches all mail
            return;
        }
        var name = row.querySelector('.gc-sender-name').textContent;
        if (!window.confirm('Take ' + name + (name !== key ? ' (' + key + ')' : '') + ' out of ' + inbox.name + '?\n\n'
            + 'Its mail goes back to Unclassified. No message is deleted, and you can file it again from any of its messages.')) return;
        b.disabled = true;
        Promise.resolve(A.apiPost(API + '/senders/inbox', { sender: key, inbox: '' })).then(function (r) {
            b.disabled = false;
            if (!r || !r.ok) { el('sdErr').textContent = errText(r, 'Could not take the sender out.'); el('sdErr').hidden = false; return; }
            flash(name + ' is back in Unclassified');
            loadInboxSenders();
            afterInboxChange();
        });
    }

    // ---- wiring ---------------------------------------------------------------------------

    /** The Conversations toggle's look: pressed when the list shows conversations. */
    function applyThreadsButton() {
        var b = el('btnThreads');
        b.classList.toggle('on', S.threads);
        b.setAttribute('aria-pressed', String(S.threads));
        b.title = S.threads ? 'Back to one row per message' : 'Conversations: one row per back-and-forth';
        b.setAttribute('aria-label', b.title);
    }

    function toggleMoreMenu(open) {
        var menu = el('moreMenu');
        var show = open === undefined ? menu.hidden : open;
        menu.hidden = !show;
        el('btnMoreMenu').setAttribute('aria-expanded', String(show));
    }

    /** The theme changed: the header's dot, and the open message re-drawn on the new paper. */
    function themeChanged(key) {
        var t = window.GmailClientLook.THEMES.filter(function (x) { return x.key === key; })[0];
        var dot = el('btnTheme').querySelector('.gc-theme-dot');
        if (dot && t) dot.style.cssText = 'background:' + t.head + ';box-shadow:inset -6px 0 0 ' + t.swatch;
        if (S.open && !el('reader').hidden && !el('rBody').hidden) fillBody(el('rBody'), S.open, '', 'gmail-client-reader');
        if (previewOn() && S.pvMsg && previewLive()) fillBody(el('pvBody'), S.pvMsg, '', 'gmail-client-preview');
        if (window.EchoMail) window.EchoMail.themeChanged(key);   // Story 5654: the Outlook theme's own layout
    }

    // ---- the flag filter (Story 5608 QA): one or more flags, any of them ------------------------
    // "I should be able to filter for flags on the main page ... for one or more." The Flags button
    // in the tools row opens a row of flag chips under it; the list then shows only mail carrying
    // ANY of the chosen flags, on every inbox tab, and the counts follow. Remembered per device.

    var flagFilterTags = null, drawingFlagFilter = false;

    function ensureFlagFilter() {
        if (flagFilterTags || !window.PicassoTags) return flagFilterTags;
        flagFilterTags = window.PicassoTags.attach({
            input: el('ffInput'), chips: el('ffChips'), dropdown: el('ffDrop'),
            getOptions: function () { return S.look ? S.look.flags().map(function (f) { return { id: f.id, name: f.name }; }) : []; },
            placeholder: 'Pick a flag', noun: 'flag', testid: 'gmail-client-flag-filter', pickOnly: true,
            onChange: function (names) {
                if (drawingFlagFilter || !S.look) return;
                var ids = [];
                names.forEach(function (n) {
                    var f = S.look.flags().filter(function (x) { return x.name.toLowerCase() === String(n).toLowerCase(); })[0];
                    if (f && ids.indexOf(f.id) < 0) ids.push(f.id);
                });
                setFlagFilter(ids, true);
            }
        });
        return flagFilterTags;
    }

    /** The Flags button says how many are chosen; the chips row shows while open or filtering. */
    function drawFlagFilter() {
        var b = el('btnFlagFilter'), row = el('flagFilterRow');
        if (!b || !row) return;
        var sent = isSent() || isDrafts(), n = S.flagFilter.length;   // sent mail and drafts carry no flags
        b.hidden = sent;
        b.textContent = n ? 'Flags (' + n + ')' : 'Flags';
        b.classList.toggle('on', n > 0);
        b.setAttribute('aria-expanded', String(!row.hidden));
        if (sent) { row.hidden = true; return; }
        if (n) row.hidden = false;
        var t = ensureFlagFilter();
        if (t && S.look) {
            drawingFlagFilter = true;
            t.setValues(S.flagFilter.map(function (id) { var f = S.look.flagById(id); return f ? f.name : null; }).filter(Boolean));
            drawingFlagFilter = false;
        }
    }

    /** Narrow the list to these flag ids (any of them); [] shows all mail again. */
    function setFlagFilter(ids, fromChips) {
        S.flagFilter = (ids || []).filter(function (id) { return S.look && S.look.flagById(id); });
        remember(VS_FLAGS, S.flagFilter.join(','));
        if (!fromChips) drawFlagFilter(); else el('btnFlagFilter').textContent = S.flagFilter.length ? 'Flags (' + S.flagFilter.length + ')' : 'Flags';
        el('btnFlagFilter').classList.toggle('on', S.flagFilter.length > 0);
        el('body').scrollTop = 0;
        return loadList();
    }

    /** A flag's count in the Flags dialog: the main list, narrowed to that flag. */
    function showFlagFilter(ids) {
        closeReader();
        el('flagFilterRow').hidden = false;
        S.tab = 'all';   // all mail with the flag, whatever inbox it is in
        remember(VS_TAB, S.tab);
        setFlagFilter(ids);
    }

    function toggleFlagFilterRow() {
        var row = el('flagFilterRow');
        if (row.hidden) {
            if (S.look && !S.look.flags().length) {
                window.Picasso.message('There are no flags yet. Open a message and type one under Flags, or select messages and choose Flag.', { title: 'Flags' });
                return;
            }
            row.hidden = false;
            drawFlagFilter();
            var t = ensureFlagFilter();
            if (t) t.focus();
        } else if (!S.flagFilter.length) {
            row.hidden = true;
            drawFlagFilter();
        } else {
            var t2 = ensureFlagFilter();
            if (t2) t2.focus();
        }
    }

    /** A flag or a color changed (gmail-client-look.js): the cards, the tabs and the open message follow. */
    function lookChanged(tabs) {
        if (tabs) { S.tabs = tabs; drawTabs(); }
        // A deleted flag leaves the filter (and the list reloads without it).
        var kept = S.flagFilter.filter(function (id) { return S.look && S.look.flagById(id); });
        if (kept.length !== S.flagFilter.length) setFlagFilter(kept); else drawFlagFilter();
        drawList();
        if (S.open && !el('reader').hidden) drawFlagsRow(S.open);
        if (S.conv && S.conv.list && !el('reader').hidden && S.convShow) renderConv();
    }

    /** Search for something from elsewhere on the page (a flag's Show) -- all mail, the box filled in. */
    function searchFor(q) {
        S.q = q;
        S.custom = null;
        el('search').value = q;
        drawFilterLine();
        closeReader();
        setTab('all');
    }

    function wireStory5608() {
        el('btnTheme').addEventListener('click', function (e) { e.stopPropagation(); if (S.look) S.look.openThemeMenu(el('btnTheme')); });
        el('btnSearchTips').addEventListener('click', showSearchTips);
        el('btnThreads').addEventListener('click', function () {
            S.threads = !S.threads;
            S.convShow = S.threads;
            remember(VS_THREADS, S.threads ? '1' : '');
            applyThreadsButton();
            el('body').scrollTop = 0;
            loadList();
        });
        el('btnFlagFilter').addEventListener('click', toggleFlagFilterRow);
        el('btnFlagFilterClear').addEventListener('click', function () {
            el('flagFilterRow').hidden = true;
            setFlagFilter([]);
        });
        el('btnMoreMenu').addEventListener('click', function (e) { e.stopPropagation(); toggleMoreMenu(); });
        el('moreMenu').addEventListener('click', function (e) {
            var b = e.target.closest('[data-more]');
            if (!b) return;
            toggleMoreMenu(false);
            var a = b.getAttribute('data-more');
            if (a === 'flags' && S.look) S.look.openFlags();
            else if (a === 'colors' && S.look) S.look.openColors();
            else if (a === 'tips') showSearchTips();
        });
        document.addEventListener('click', function (e) {
            if (!el('moreMenu').hidden && !e.target.closest('.gc-more-wrap')) toggleMoreMenu(false);
            if (!el('cAttachMenu').hidden && !e.target.closest('#cAttachRow')) el('cAttachMenu').hidden = true;
        });

        // The reader: one sender on their own, a color, flags, links.
        el('rLevel').addEventListener('click', function (e) { e.preventDefault(); levelClicked(); });
        el('rColor').addEventListener('click', function (e) { e.preventDefault(); colorFromReader(); });
        el('rLinkAdd').addEventListener('click', openLinkDialog);
        el('rLinks').addEventListener('click', function (e) {
            var x = e.target.closest('[data-unlink]');
            if (x) { e.preventDefault(); unlinkRecord(parseInt(x.getAttribute('data-unlink'), 10)); }
        });
        el('lkSearch').addEventListener('input', function () { clearTimeout(linkTimer); linkTimer = setTimeout(searchRecords, 250); });
        el('lkList').addEventListener('click', function (e) { var b = e.target.closest('[data-link-entity]'); if (b) linkRecord(b); });
        el('lkCancel').addEventListener('click', function () { el('links').hidden = true; });

        // Flag the selection.
        el('fsFlags').addEventListener('click', function (e) {
            var b = e.target.closest('[data-fs-name]');
            if (!b) return;
            el('fsName').value = b.getAttribute('data-fs-name');
            drawFlagSelChips();
        });
        el('fsName').addEventListener('input', drawFlagSelChips);
        el('fsCancel').addEventListener('click', function () { el('flagSel').hidden = true; });
        el('fsAdd').addEventListener('click', function () { runFlagSel('add'); });
        el('fsRemove').addEventListener('click', function () { runFlagSel('remove'); });

        // Attachments: the Attach menu, the device's file chooser, dropping files, the Reading Nook.
        el('cAttach').addEventListener('click', function (e) {
            e.stopPropagation();
            if (DEMO) { el('cFileInput').click(); return; }   // the demo never reads the real Reading Nook
            var menu = el('cAttachMenu');
            menu.hidden = !menu.hidden;
            el('cAttach').setAttribute('aria-expanded', String(!menu.hidden));
        });
        el('cAttachMenu').addEventListener('click', function (e) {
            var b = e.target.closest('[data-attach]');
            if (!b) return;
            el('cAttachMenu').hidden = true;
            if (b.getAttribute('data-attach') === 'device') el('cFileInput').click(); else openNookPicker();
        });
        el('cFileInput').addEventListener('change', function () { addDeviceFiles(el('cFileInput').files); el('cFileInput').value = ''; });
        el('cFiles').addEventListener('click', function (e) {
            var x = e.target.closest('[data-file-x]');
            if (x) removeFile(parseInt(x.getAttribute('data-file-x'), 10));
        });
        el('cDialog').addEventListener('dragover', function (e) {
            if (e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types || [], 'Files') >= 0) {
                e.preventDefault();
                el('cDialog').classList.add('gc-drop');
            }
        });
        el('cDialog').addEventListener('dragleave', function (e) {
            if (!el('cDialog').contains(e.relatedTarget)) el('cDialog').classList.remove('gc-drop');
        });
        el('cDialog').addEventListener('drop', function (e) {
            el('cDialog').classList.remove('gc-drop');
            if (!e.dataTransfer || !e.dataTransfer.files || !e.dataTransfer.files.length) return;
            e.preventDefault();
            addDeviceFiles(e.dataTransfer.files);
        });
        el('npSearch').addEventListener('input', function () { clearTimeout(nookTimer); nookTimer = setTimeout(loadNookFiles, 250); });
        el('npList').addEventListener('click', function (e) { var b = e.target.closest('[data-nook-i]'); if (b) pickNookFile(b); });
        el('npClose').addEventListener('click', function () { el('nookPick').hidden = true; });
    }

    function wire() {
        wireStory5608();
        el('btnCheck').addEventListener('click', function () { checkMail(false); });
        el('btnCompose').addEventListener('click', function () { S.open = null; openCompose('new'); });
        el('btnOlder').addEventListener('click', readOlder);
        el('tabs').addEventListener('click', function (e) {
            var b = e.target.closest('[data-tab]');
            if (b) setTab(b.getAttribute('data-tab'));
        });

        el('list').addEventListener('click', function (e) {
            if (Date.now() < ignoreClickUntil) { e.preventDefault(); return; }
            // Drafts (Story 5620): Discard on the row, or tap the row to carry on writing.
            var dx = e.target.closest('[data-discard]');
            if (dx) { e.stopPropagation(); discardDraftRow(parseInt(dx.getAttribute('data-discard'), 10), dx); return; }
            var dr = e.target.closest('[data-draft]');
            if (dr) { openDraft(parseInt(dr.getAttribute('data-draft'), 10)); return; }
            // View in Gmail: let the link go; the card must not open behind it.
            if (e.target.closest('[data-gmail]')) { e.stopPropagation(); return; }
            var qv = e.target.closest('[data-quick]');
            if (qv) { e.stopPropagation(); quickView(parseInt(qv.getAttribute('data-quick'), 10)); return; }
            var dec = e.target.closest('[data-decide]');
            if (dec) { e.stopPropagation(); decideSender(dec.getAttribute('data-sender'), dec.getAttribute('data-decide'), dec); return; }
            var act = e.target.closest('[data-swipe]');
            if (act) { swipeAction(act); return; }
            var wrap = e.target.closest('.gc-swipe');
            if (wrap && wrap.classList.contains('open')) { wrap.classList.remove('open'); return; }
            closeSwipes();
            var c = e.target.closest('.gc-card');
            if (!c) return;
            var id = parseInt(c.getAttribute('data-id'), 10);
            if (S.selecting) { toggleSelect(id); return; }   // the box follows S.sel, ticked or not
            if (previewLive()) { previewSelect(id); return; }  // the preview view: a click previews
            openMessage(id);
        });
        el('list').addEventListener('dblclick', function (e) {
            if (e.target.closest('[data-draft]')) return;
            var c = e.target.closest('.gc-card');
            if (c && previewLive() && !S.selecting && !e.target.closest('[data-quick], [data-gmail]')) openMessage(parseInt(c.getAttribute('data-id'), 10));
        });
        el('list').addEventListener('keydown', function (e) {
            if (e.target.closest('[data-quick], [data-gmail], [data-discard]')) return;      // View / Gmail / Discard handle their own Enter
            var c = e.target.closest('.gc-card');
            if (!c || (e.key !== 'Enter' && e.key !== ' ')) return;
            e.preventDefault();
            if (c.hasAttribute('data-draft')) { openDraft(parseInt(c.getAttribute('data-draft'), 10)); return; }
            var id = parseInt(c.getAttribute('data-id'), 10);
            if (S.selecting) toggleSelect(id); else openMessage(id);
        });
        el('list').addEventListener('touchstart', onTouchStart, { passive: true });
        el('list').addEventListener('touchmove', onTouchMove, { passive: false });
        el('list').addEventListener('touchend', onTouchEnd);
        el('list').addEventListener('touchcancel', onTouchEnd);

        el('btnMore').addEventListener('click', function () { loadList(true); });
        el('btnMoreAll').addEventListener('click', loadAll);
        el('note').addEventListener('click', function (e) {
            var u = e.target.closest('[data-undo]');
            if (u) undo(u.getAttribute('data-undo').split(',').map(Number), u.getAttribute('data-undo-sent') === '1');
        });

        el('readSeg').addEventListener('click', function (e) {
            var b = e.target.closest('[data-read]');
            if (b && b.getAttribute('data-read') !== S.read) setRead(b.getAttribute('data-read'));
        });
        el('replySeg').addEventListener('click', function (e) {
            var b = e.target.closest('[data-reply]');
            if (b && b.getAttribute('data-reply') !== S.reply) setReply(b.getAttribute('data-reply'));
        });
        el('btnSelect').addEventListener('click', function () { startSelecting(0); });
        el('rangeSel').addEventListener('change', function () {
            if (drawingRange) return;
            var v = el('rangeSel').value;
            if (v === S.range) return;
            if (v === 'days') { el('rangeDays').focus(); return; }   // Days back: the number field takes it from here
            if (v !== S.range) setRange(v);
        });
        var dt = 0;
        el('rangeDays').addEventListener('input', function () { clearTimeout(dt); dt = setTimeout(takeDays, 700); });
        el('rangeDays').addEventListener('change', function () { clearTimeout(dt); takeDays(); });
        el('rangeDays').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); clearTimeout(dt); takeDays(); }
        });
        el('btnView').addEventListener('click', function () {
            S.view = S.view === 'preview' ? 'list' : 'preview';
            remember(VS_VIEW, S.view);
            applyView();
        });
        // The drag bar, minimizing the list and crossing between PC and phone widths are the
        // Bay Window's own (picasso-split.js).
        el('btnLayout').addEventListener('click', function () { ensureSplit().toggleLayout(); });
        el('preview').addEventListener('click', function (e) {
            if (e.target.closest('[data-pv="open"]') && S.pv) openMessage(S.pv);
            if (e.target.closest('[data-pv="listen"]') && S.pv) listenTo(S.pvMsg);
        });
        el('qClose').addEventListener('click', closeQuick);
        el('qListen').addEventListener('click', function () { listenTo(S.qMsg); });
        el('btnListen').addEventListener('click', function () { listenTo(S.open); });
        el('selbar').addEventListener('click', bulkAction);
        el('selAll').addEventListener('change', function () {
            S.sel = {};
            if (el('selAll').checked) S.rows.forEach(function (m) { S.sel[m.id] = true; });
            drawList();
        });
        el('btnMarkAll').addEventListener('click', function (e) { e.stopPropagation(); toggleMarkMenu(); });
        el('markAllMenu').addEventListener('click', function (e) {
            var b = e.target.closest('[data-mark]');
            if (b) markAll(b.getAttribute('data-mark') === 'read');
        });
        document.addEventListener('click', function (e) {
            if (!el('markAllMenu').hidden && !e.target.closest('.gc-menu-wrap')) toggleMarkMenu(false);
        });

        el('btnFold').addEventListener('click', function () {
            var folded = !document.querySelector('.gc-page').classList.contains('gc-folded');
            applyHeaderFold(folded);
            remember(VS_FOLD, folded ? '1' : '');
        });
        el('btnActionsFold').addEventListener('click', function () {
            var folded = !el('reader').classList.contains('gc-actions-folded');
            applyActionsFold(folded);
            remember(VS_ACTIONS, folded ? '1' : '');
        });
        el('btnHeadFold').addEventListener('click', function () {
            var folded = !el('rHead').classList.contains('folded');
            applyDetailsFold(folded);
            remember(VS_HEAD, folded ? '1' : '');
        });

        var t = 0;
        el('search').addEventListener('input', function () {
            clearTimeout(t);
            t = setTimeout(function () {
                S.q = el('search').value.trim();
                drawFilterLine();
                // The quick search searches all mail -- or, on the Sent tab, all sent mail; on Drafts, the drafts.
                if (S.q && S.tab !== 'all' && !isSent() && !isDrafts()) setTab('all'); else loadList();
            }, 300);
        });
        el('btnCustom').addEventListener('click', function () { loadInboxes().then(openCustom); });
        el('btnClearFilter').addEventListener('click', clearFilters);
        el('sGo').addEventListener('click', runCustom);
        el('sCancel').addEventListener('click', function () { el('custom').hidden = true; });
        el('sReset').addEventListener('click', function () {
            ['sFrom', 'sTo', 'sSubject', 'sWords', 'sAfter', 'sBefore'].forEach(function (k) { el(k).value = ''; });
            el('sUnread').checked = false;
            el('sInbox').value = '';
            if (window.PicassoSelect && PicassoSelect.refresh) PicassoSelect.refresh(el('sInbox'));
        });

        el('btnClose').addEventListener('click', closeReader);
        el('btnNewer').addEventListener('click', function () { step(-1); });
        el('btnOlderMsg').addEventListener('click', function () { step(1); });
        el('btnReply').addEventListener('click', function () { openCompose('reply'); });
        el('btnReplyAll').addEventListener('click', function () { openCompose('replyAll'); });
        el('btnWriteAgain').addEventListener('click', function () { openCompose('writeAgain'); });
        el('btnForward').addEventListener('click', function () { openCompose('forward'); });
        el('btnFollowUp').addEventListener('click', function () { if (S.open) openFollowUp([S.open.id], rowIsSent(S.open)); });
        el('btnDelete').addEventListener('click', deleteOpen);
        el('btnUnread').addEventListener('click', markUnread);
        el('rInbox').addEventListener('change', function () {
            var v = el('rInbox').value;
            var inbox = S.inboxes.filter(function (i) { return String(i.id) === v; })[0];
            if (S.open && String(S.open.inboxId || '') !== v) fileSender(inbox ? inbox.name : '');
        });
        el('rInbox').addEventListener('ps-add', function (e) {
            var name = (e.detail && e.detail.text || '').trim();
            if (name) fileSender(name);
        });

        el('fQuick').addEventListener('click', function (e) {
            var b = e.target.closest('[data-days]');
            if (!b) return;
            el('fDate').value = isoDay(parseInt(b.getAttribute('data-days'), 10));
            drawQuick();
        });
        el('fDate').addEventListener('change', drawQuick);
        el('fCancel').addEventListener('click', closeFollowUp);
        el('fSave').addEventListener('click', saveFollowUp);
        el('fDone').addEventListener('click', doneFollowUp);

        wireCompose();

        el('btnInboxes').addEventListener('click', openInboxes);
        el('btnArrange').addEventListener('click', function () { S.arranging = !S.arranging; drawInboxList(); });
        el('inboxList').addEventListener('pointerdown', onGripDown);
        el('iClose').addEventListener('click', function () { el('inboxes').hidden = true; });
        el('btnAddInbox').addEventListener('click', addInbox);
        el('inboxList').addEventListener('click', inboxAction);
        el('sdList').addEventListener('click', senderAction);
        el('sdClose').addEventListener('click', function () { el('senders').hidden = true; sendersInbox = null; });

        document.addEventListener('keydown', function (e) {
            if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && previewLive() && !isDrafts() && !S.selecting && !keysBusy(e)) {
                e.preventDefault();
                previewStep(e.key === 'ArrowDown' ? 1 : -1);
                return;
            }
            if (e.key !== 'Escape') return;
            if (S.look && S.look.escape()) return;
            if (!el('nookPick').hidden) { el('nookPick').hidden = true; return; }
            if (!el('cAttachMenu').hidden) { el('cAttachMenu').hidden = true; return; }
            if (!el('moreMenu').hidden) { toggleMoreMenu(false); return; }
            if (!el('links').hidden) { el('links').hidden = true; return; }
            if (!el('flagSel').hidden) { el('flagSel').hidden = true; return; }
            if (!el('quick').hidden) closeQuick();
            else if (!el('compose').hidden) closeCompose();
            else if (!el('followup').hidden) closeFollowUp();
            else if (!el('custom').hidden) el('custom').hidden = true;
            else if (!el('senders').hidden) { el('senders').hidden = true; sendersInbox = null; }
            else if (!el('inboxes').hidden) el('inboxes').hidden = true;
            else if (!el('reader').hidden) closeReader();
            else if (!el('markAllMenu').hidden) toggleMarkMenu(false);
            else if (S.selecting) stopSelecting();
        });

        el('btnPrint').addEventListener('click', function (e) {
            e.preventDefault();
            var reading = !el('reader').hidden && S.open;
            window.Picasso.printThisPage({
                selector: reading ? '#reader' : '#list',
                title: reading ? (S.open.subject || 'Message') : 'Email Client -- ' + ((currentTab() || {}).name || 'All'),
                stripSelector: 'button, select, input, label.gc-inbox-pick, .gc-acts, .gc-acts-l, .gc-qv, #rFlagsRow .pt-input, '
                    + '.pt-dropdown, .gc-piece-acts, .gc-conv-head button, script, style'
            });
        });
    }

    function focusParam() {
        var m = /[?&]focus=(\d+)/.exec(window.location.search);
        return m ? parseInt(m[1], 10) : 0;
    }

    /** ?sent=<id> (Leonardo Find, Story 5596): one sent message, opened on the Sent tab. */
    function sentParam() {
        var m = /[?&]sent=(\d+)/.exec(window.location.search);
        return m ? parseInt(m[1], 10) : 0;
    }

    /** ?draft=<id> (Leonardo Find, Story 5620): one draft, opened on the Drafts tab to carry on writing. */
    function draftParam() {
        var m = /[?&]draft=(\d+)/.exec(window.location.search);
        return m ? parseInt(m[1], 10) : 0;
    }

    /** Gmail Client Demo: retitle the page, badge it, and offer Reset; Mail Triage is real, so no link. */
    function demoChrome() {
        if (!DEMO) return;
        document.title = 'Gmail Client Demo -- LeonardoWeb';
        var h1 = document.querySelector('.ct-header h1');
        h1.textContent = 'Gmail Client Demo';
        var link = document.querySelector('[data-testid="gmail-client-to-mail-triage"]');
        if (link) link.hidden = true;
        h1.insertAdjacentHTML('afterend', '<span class="gc-demo-pill" data-testid="gmail-client-demo-pill"'
            + ' title="Made-up mail and inboxes. Nothing here reads or sends real mail.">Demo -- made-up mail</span>'
            + '<button type="button" class="gc-demo-reset" id="btnDemoReset" data-testid="gmail-client-demo-reset">Reset demo</button>');
        el('btnDemoReset').addEventListener('click', function () {
            if (!window.confirm('Reset the Gmail Client Demo?\n\nEvery made-up message, inbox, follow-up and sent '
                + 'message goes back to how the demo starts. Your real mail is never involved.')) return;
            var b = el('btnDemoReset');
            b.disabled = true;
            Promise.resolve(A.apiPost(API + '/reset', {})).then(function (r) {
                b.disabled = false;
                if (!r || !r.ok) { note(errText(r, 'Could not reset the demo.'), true); return; }
                window.location.reload();
            });
        });
    }

    /** A device's first visit starts from the per-user values the page kept before 2026-10-05. Once only. */
    function seedDevice() {
        if (DEV_PREFIX === VS_PREFIX || recalled(VS_SEEDED) === '1') return;
        DEVICE_NAMES.forEach(function (n) {
            var old = recalled(VS_PREFIX + n);
            if (old !== null && old !== '' && recalled(DEV_PREFIX + n) === null) remember(DEV_PREFIX + n, old);
        });
        remember(VS_SEEDED, '1');
    }

    /**
     * Story 5654: what Echo's own script (gmail-client-echo.js -- the Outlook theme and the
     * whitelist / blacklist) may ask of this page.
     */
    window.GmailClientPage = {
        api: API,
        refresh: function () { return Promise.all([loadStatus(), refreshList()]); },
        flash: function (t) { flash(t); },
        note: function (t, bad) { note(t, bad); },
        searchFor: function (q) { searchFor(q); },
        previewOn: function () { return S.view === 'preview'; },
        setPreview: function (on) {
            if ((S.view === 'preview') === !!on) return;
            S.view = on ? 'preview' : 'list';
            remember(VS_VIEW, S.view);
            applyView();
        },
        openTab: function (key) { if (tabExists(key)) { setTab(key); } }
    };

    function boot() {
        A.ensureAuth().then(function () {
            demoChrome();
            wire();
            var v = vs();
            var syncs = v ? Promise.all([v.sync(VS_PREFIX), v.sync(DEV_PREFIX)]).then(seedDevice)
                .catch(function () { return false; }) : Promise.resolve(false);
            Promise.all([syncs, loadInboxes(), loadStatus()]).then(function () {
                // Story 5608: themes, colors and flags (gmail-client-look.js), once the view state is in.
                S.look = window.GmailClientLook.create({
                    api: API, vsPrefix: VS_PREFIX, flash: flash,
                    onChange: lookChanged, onTheme: themeChanged, showMail: searchFor, showFlags: showFlagFilter
                });
                themeChanged(S.look.recalledTheme());
                S.threads = recalled(VS_THREADS) === '1';
                applyThreadsButton();
                return S.look.load();
            }).then(function () {
                S.flagFilter = String(recalled(VS_FLAGS) || '').split(',').map(function (x) { return parseInt(x, 10); })
                    .filter(function (id) { return id > 0 && S.look.flagById(id); });
                drawFlagFilter();
                S.tab = recalled(VS_TAB) || 'all';
                var r = recalled(VS_READ);
                S.read = r === 'unread' || r === 'read' ? r : 'all';
                drawReadSeg();
                var rg = recalled(VS_RANGE), dy = parseInt(recalled(VS_DAYS) || '', 10);
                S.range = RANGES.indexOf(rg) >= 0 ? rg : 'any';
                S.days = S.range === 'days' && dy >= 1 && dy <= MAX_DAYS ? dy : 0;
                if (S.range === 'days' && !S.days) S.range = 'any';
                drawRange();
                S.view = recalled(VS_VIEW) === 'preview' ? 'preview' : 'list';
                applyView();
                applyHeaderFold(recalled(VS_FOLD) === '1');
                applyDetailsFold(recalled(VS_HEAD) === '1');
                applyActionsFold(recalled(VS_ACTIONS) === '1');
                var focus = focusParam(), sentFocus = sentParam(), draftFocus = draftParam();
                if (draftFocus) S.tab = 'drafts';
                else if (sentFocus) S.tab = 'sent';
                else if (focus && (S.tab === 'sent' || S.tab === 'drafts')) S.tab = 'all';   // ?focus= is inbox mail
                return loadList().then(function () {
                    if (draftFocus) openDraft(draftFocus);
                    else if (sentFocus) openMessage(sentFocus, true);
                    else if (focus) openMessage(focus, false);
                    checkMail(true);   // opening the page checks mail
                });
            });
        });
    }

    boot();
})();
