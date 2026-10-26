/* gmail-client-echo.js -- Story 5654: what Echo's Email Client adds to the page it copied from
 * Leonardo's Gmail Client (gmail-client.js stays as close to Leonardo's as it can, so changes carry
 * across). Two things:
 *
 *   1. THE OUTLOOK THEME. Picked from the Theme button like any other theme. Besides Outlook's colors
 *      (gmail-client-echo.css), it gives the page Outlook's shape: the inboxes move out of the tab
 *      strip into a folder pane down the left, with the whitelist and the blacklist under them, and
 *      the list and the message sit to its right. Picking the theme also turns the reading pane (the
 *      preview view) on, as Outlook has it. On a phone the folder pane becomes a strip across the top.
 *      Any other theme puts everything back where it was.
 *
 *   2. LISTS -- the whitelist and the blacklist. Tony's rule for the Story: Echo NEVER creates rules
 *      in Outlook. The lists live in Echo's database only: a whitelisted sender's mail shows in the
 *      inboxes, a blacklisted sender's mail is never read in, and a sender on neither waits on the
 *      Undecided tab. The Lists dialog shows both lists, finds a sender, moves one from list to list,
 *      takes one off (its mail goes back to Undecided), and adds one by hand.
 *
 * Remembered: whether the folder pane is folded, per device, in the database (PicassoViewState).
 */
(function () {
    'use strict';

    var A = window.LWAuth;
    var PAGE_SIZE = 50;
    var lists = { side: 'all', q: '', rows: [], more: false, timer: 0, seq: 0 };
    var olState = { on: false, seen: false, saved: null };

    function el(id) { return document.getElementById(id); }
    function page() { return window.GmailClientPage; }
    function api() { return (page() && page().api) || '/api/gmail-client'; }
    function esc(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function plural(n, word) { return n + ' ' + word + (n === 1 ? '' : 's'); }
    function errText(r, what) { return A && A.errorText ? A.errorText(r, what) : what; }
    function vs() { return window.PicassoViewState || null; }
    var FOLD_KEY = function () { var v = vs(); return v && v.deviceKey ? v.deviceKey('gmailclient.olFoldersFolded') : 'leo.gmailclient.olFoldersFolded'; };

    // ---- 1. the Outlook theme ---------------------------------------------------------------

    /** Called by gmail-client.js whenever the theme is applied (first at load, then on every pick). */
    function themeChanged(key) {
        var picked = olState.seen;   // the first call is the page loading; later ones are Tony's picks
        olState.seen = true;
        if (key === 'outlook') {
            enterOutlook();
            if (picked && page() && !page().previewOn()) page().setPreview(true);
        } else {
            leaveOutlook();
        }
    }

    function enterOutlook() {
        if (olState.on) return;
        var tabsRow = document.querySelector('.gc-tabs-row');
        var parts = ['tools', 'flagFilterRow', 'selbar', 'split'].map(el);
        if (!tabsRow || parts.some(function (p) { return !p; })) return;
        var parent = tabsRow.parentNode, anchor = parts[parts.length - 1].nextSibling;
        olState.saved = { parent: parent, anchor: anchor, nodes: [tabsRow].concat(parts) };

        var main = document.createElement('div');
        main.className = 'ol-main';
        main.id = 'olMain';
        main.setAttribute('data-testid', 'echo-mail-ol-main');
        var pane = document.createElement('aside');
        pane.className = 'ol-folders';
        pane.id = 'olFolders';
        pane.setAttribute('data-testid', 'echo-mail-ol-folders');
        pane.innerHTML = '<div class="ol-pane-head">'
            + '<button type="button" class="ol-pane-fold" id="olFold" aria-expanded="true" aria-controls="olFolderBody"'
            + ' title="Fold the folder pane" data-testid="echo-mail-ol-fold">&#9776;</button>'
            + '<span class="ol-pane-title">Folders</span></div>'
            + '<div class="ol-folder-body" id="olFolderBody"></div>';
        var body = pane.querySelector('#olFolderBody');
        body.appendChild(tabsRow);
        body.insertAdjacentHTML('beforeend', '<div class="ol-group">Senders</div>'
            + '<button type="button" class="ol-folder" data-ol-list="white" data-testid="echo-mail-ol-whitelist">'
            + '<span class="ol-ico ok" aria-hidden="true">&#10003;</span> Whitelist</button>'
            + '<button type="button" class="ol-folder" data-ol-list="black" data-testid="echo-mail-ol-blacklist">'
            + '<span class="ol-ico no" aria-hidden="true">&#8856;</span> Blacklist</button>');
        var right = document.createElement('div');
        right.className = 'ol-right';
        right.setAttribute('data-testid', 'echo-mail-ol-right');
        parts.forEach(function (p) { right.appendChild(p); });
        main.appendChild(pane);
        main.appendChild(right);
        parent.insertBefore(main, anchor);
        pane.addEventListener('click', function (e) {
            var b = e.target.closest('[data-ol-list]');
            if (b) { openLists(b.getAttribute('data-ol-list')); return; }
            if (e.target.closest('#olFold')) setFolded(!pane.classList.contains('folded'), true);
        });
        var v = vs();
        setFolded(!!(v && v.get(FOLD_KEY()) === '1'), false);
        document.body.classList.add('ol-on');
        olState.on = true;
    }

    function leaveOutlook() {
        if (!olState.on || !olState.saved) return;
        var s = olState.saved;
        s.nodes.forEach(function (n) { s.parent.insertBefore(n, s.anchor); });
        var main = el('olMain');
        if (main && main.parentNode) main.parentNode.removeChild(main);
        document.body.classList.remove('ol-on');
        olState.on = false;
        olState.saved = null;
    }

    /** Folds the folder pane to a narrow strip (Outlook's collapsed navigation), remembered per device. */
    function setFolded(folded, remember) {
        var pane = el('olFolders');
        if (!pane) return;
        pane.classList.toggle('folded', folded);
        var b = el('olFold');
        if (b) {
            b.setAttribute('aria-expanded', String(!folded));
            b.title = folded ? 'Show the folder pane' : 'Fold the folder pane';
        }
        var v = vs();
        if (remember && v) v.set(FOLD_KEY(), folded ? '1' : '0');
    }

    // ---- 2. the whitelist and the blacklist ---------------------------------------------------

    function buildLists() {
        if (el('olLists')) return;
        document.body.insertAdjacentHTML('beforeend',
            '<div class="gc-modal" id="olLists" hidden data-testid="echo-mail-lists">'
            + '<div class="gc-dialog ol-lists-dialog" role="dialog" aria-labelledby="olListsTitle">'
            + '<h3 id="olListsTitle">Whitelist and blacklist</h3>'
            + '<div class="gc-hint">Kept in Echo only -- Outlook never gets a rule. A whitelisted sender\'s mail shows in your'
            + ' inboxes; a blacklisted sender\'s mail is never read in; a sender on neither list waits on the Undecided tab.</div>'
            + '<div class="gc-seg ol-list-seg" id="olListSeg" role="group" aria-label="Which list" data-testid="echo-mail-lists-seg">'
            + '<button type="button" data-side="all" data-testid="echo-mail-lists-all">Both</button>'
            + '<button type="button" data-side="white" data-testid="echo-mail-lists-white">Whitelist</button>'
            + '<button type="button" data-side="black" data-testid="echo-mail-lists-black">Blacklist</button></div>'
            + '<input type="search" id="olListSearch" autocomplete="off" placeholder="Find a sender" data-testid="echo-mail-lists-search">'
            + '<div class="ol-list-count" id="olListCount" data-testid="echo-mail-lists-count"></div>'
            + '<div id="olListRows" class="gc-sender-list" data-testid="echo-mail-lists-rows"></div>'
            + '<div class="gc-more"><button type="button" class="gc-btn" id="olListMore" hidden data-testid="echo-mail-lists-more">Load more</button></div>'
            + '<div class="ol-list-add">'
            + '<input type="text" id="olListAdd" autocomplete="off" placeholder="Add a sender: greg@example.com or example.com"'
            + ' data-testid="echo-mail-lists-add">'
            + '<button type="button" class="gc-btn" data-add="white" data-testid="echo-mail-lists-add-white">Whitelist</button>'
            + '<button type="button" class="gc-btn danger" data-add="black" data-testid="echo-mail-lists-add-black">Blacklist</button></div>'
            + '<div class="gc-dialog-err" id="olListErr" hidden data-testid="echo-mail-lists-error"></div>'
            + '<div class="gc-dialog-btns"><button type="button" class="gc-btn" id="olListClose" data-testid="echo-mail-lists-close">Close</button></div>'
            + '</div></div>');
        var dlg = el('olLists');
        el('olListClose').addEventListener('click', closeLists);
        // Escape closes it wherever the focus is -- but a question asked over it (Remove? Blacklist?)
        // answers Escape first. Captured, so the page's own Escape handling never sees it.
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape' || dlg.hidden || document.querySelector('[data-testid="picasso-dialog"]')) return;
            e.preventDefault();
            e.stopPropagation();
            closeLists();
        }, true);
        el('olListSeg').addEventListener('click', function (e) {
            var b = e.target.closest('[data-side]');
            if (!b) return;
            lists.side = b.getAttribute('data-side');
            loadLists(false);
        });
        el('olListSearch').addEventListener('input', function () {
            window.clearTimeout(lists.timer);
            lists.timer = window.setTimeout(function () { lists.q = el('olListSearch').value.trim(); loadLists(false); }, 250);
        });
        el('olListMore').addEventListener('click', function () { loadLists(true); });
        dlg.querySelector('.ol-list-add').addEventListener('click', function (e) {
            var b = e.target.closest('[data-add]');
            if (b) addSender(b.getAttribute('data-add'), b);
        });
        el('olListAdd').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); addSender('white', null); }
        });
        el('olListRows').addEventListener('click', function (e) {
            var b = e.target.closest('[data-rule-act]');
            if (!b) return;
            var row = lists.rows.filter(function (r) { return String(r.id) === b.getAttribute('data-rule-id'); })[0];
            if (row) ruleAction(row, b.getAttribute('data-rule-act'), b);
        });
    }

    function openLists(side) {
        buildLists();
        lists.side = side === 'white' || side === 'black' ? side : 'all';
        lists.q = '';
        el('olListSearch').value = '';
        el('olListAdd').value = '';
        listError('');
        el('olLists').hidden = false;
        loadLists(false);
        el('olListSearch').focus();
    }

    function closeLists() { el('olLists').hidden = true; }

    function listError(t) {
        el('olListErr').textContent = t || '';
        el('olListErr').hidden = !t;
    }

    function loadLists(more) {
        var seq = ++lists.seq;
        var offset = more ? lists.rows.length : 0;
        Array.prototype.forEach.call(el('olListSeg').querySelectorAll('button'), function (b) {
            b.classList.toggle('on', b.getAttribute('data-side') === lists.side);
        });
        var url = api() + '/senders/rules?disposition=' + encodeURIComponent(lists.side)
            + '&q=' + encodeURIComponent(lists.q) + '&offset=' + offset + '&size=' + PAGE_SIZE;
        return Promise.resolve(A.apiGet(url)).then(function (r) {
            if (seq !== lists.seq) return;
            if (!r || !r.ok) { listError(errText(r, 'Could not read the lists.')); return; }
            listError('');
            lists.rows = more ? lists.rows.concat(r.data.rows || []) : (r.data.rows || []);
            lists.more = !!r.data.hasMore;
            drawLists(r.data.counts || {});
        });
    }

    function drawLists(c) {
        var shown = lists.rows.length;
        var total = lists.side === 'white' ? c.matchWhite : lists.side === 'black' ? c.matchBlack : c.matchAll;
        el('olListCount').textContent = (c.white || 0) + ' whitelisted, ' + (c.black || 0) + ' blacklisted'
            + (lists.q ? ' | ' + (total || 0) + ' match "' + lists.q + '"' : '')
            + (lists.more ? ' | ' + shown + ' shown' : '');
        el('olListRows').innerHTML = shown ? lists.rows.map(ruleRowHtml).join('')
            : '<div class="gc-hint" data-testid="echo-mail-lists-empty">'
              + (lists.q ? 'No sender on these lists matches.' : lists.side === 'black' ? 'Nobody is blacklisted.'
                : lists.side === 'white' ? 'Nobody is whitelisted yet. Whitelist a sender from the Undecided tab, or add one below.'
                : 'Both lists are empty. Decide on senders from the Undecided tab, or add one below.') + '</div>';
        el('olListMore').hidden = !lists.more;
    }

    function ruleRowHtml(r) {
        var white = r.disposition === 'white';
        return '<div class="gc-sender-row ol-rule" data-testid="echo-mail-lists-row">'
            + '<span class="ol-side ' + (white ? 'white' : 'black') + '" data-testid="echo-mail-lists-row-side">'
            + (white ? 'Whitelist' : 'Blacklist') + '</span>'
            + '<span class="ol-rule-key"><b data-testid="echo-mail-lists-row-sender">' + esc(r.senderKey) + '</b>'
            + (r.keyKind === 'domain' ? ' <span class="gc-pick-sub">everyone at it</span>' : '')
            + (r.isProtected ? ' <span class="gc-pick-sub">protected</span>' : '') + '</span>'
            + '<span class="ol-rule-btns">'
            + (white ? '<button type="button" class="gc-btn" data-rule-act="show" data-rule-id="' + r.id + '"'
                + ' data-testid="echo-mail-lists-row-show">Show mail</button>' : '')
            + '<button type="button" class="gc-btn" data-rule-act="' + (white ? 'black' : 'white') + '" data-rule-id="' + r.id + '"'
            + ' data-testid="echo-mail-lists-row-move">' + (white ? 'Move to blacklist' : 'Move to whitelist') + '</button>'
            + '<button type="button" class="gc-btn danger" data-rule-act="remove" data-rule-id="' + r.id + '"'
            + ' data-testid="echo-mail-lists-row-remove">Remove</button>'
            + '</span></div>';
    }

    function ruleAction(r, act, btn) {
        if (act === 'show') {
            closeLists();
            if (page()) page().searchFor('from:' + r.senderKey);
            return;
        }
        if (act === 'remove') {
            var msg = 'Remove ' + r.senderKey + ' from the ' + (r.disposition === 'white' ? 'whitelist' : 'blacklist') + '?\n\n'
                + (r.disposition === 'white'
                    ? 'Its mail leaves your inboxes and waits on the Undecided tab until you decide again. No message is deleted.'
                    : 'Its new mail will be read in again and wait on the Undecided tab.')
                + ' Nothing changes in Outlook.';
            return Promise.resolve(window.Picasso.confirmDelete(msg, { title: 'Remove sender', yes: 'Remove' })).then(function (yes) {
                if (!yes) return;
                btn.disabled = true;
                return Promise.resolve(A.apiPost(api() + '/senders/rules/' + r.id, null, 'DELETE')).then(function (x) {
                    btn.disabled = false;
                    if (!x || !x.ok) { listError(errText(x, 'Could not remove ' + r.senderKey + '.')); return; }
                    changed(r.senderKey + ' is on neither list now');
                });
            });
        }
        return decide(r.senderKey, act, btn);
    }

    function addSender(side, btn) {
        var who = el('olListAdd').value.trim();
        if (!who) { listError('Type a sender first: an address such as greg@example.com, or a domain such as example.com.'); return; }
        decide(who, side, btn).then(function (ok) { if (ok) el('olListAdd').value = ''; });
    }

    /** Whitelist or blacklist a sender -- the same decision the Undecided tab makes. Resolves true when saved. */
    function decide(sender, side, btn) {
        var msg = side === 'white'
            ? 'Whitelist ' + sender + '?\n\nIts mail shows in your inboxes from now on.'
            : 'Blacklist ' + sender + '?\n\nIts mail leaves the Email Client and its new mail is never read in.'
              + ' Nothing changes in Outlook -- the list is kept in Echo only.';
        return Promise.resolve(window.Picasso.yesNo(msg, { title: side === 'white' ? 'Whitelist' : 'Blacklist' })).then(function (yes) {
            if (!yes) return false;
            if (btn) btn.disabled = true;
            return Promise.resolve(A.apiPost(api() + '/senders/decide', { sender: sender, disposition: side })).then(function (r) {
                if (btn) btn.disabled = false;
                if (!r || !r.ok) { listError(errText(r, 'Could not ' + (side === 'white' ? 'whitelist ' : 'blacklist ') + sender + '.')); return false; }
                changed(((r.data && r.data.rule && r.data.rule.senderKey) || sender) + (side === 'white' ? ' is whitelisted' : ' is blacklisted'));
                return true;
            });
        });
    }

    function changed(what) {
        listError('');
        loadLists(false);
        if (page()) { page().flash(what); page().refresh(); }
    }

    // ---- wiring -------------------------------------------------------------------------------

    function wire() {
        var head = el('btnLists');
        if (head) head.addEventListener('click', function () { openLists('all'); });
        var more = document.querySelector('[data-more="lists"]');
        if (more) more.addEventListener('click', function () {
            var menu = el('moreMenu');
            if (menu) menu.hidden = true;
            openLists('all');
        });
    }

    window.EchoMail = { themeChanged: themeChanged, openLists: openLists };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', wire);
    else wire();
})();
