(function () {
    'use strict';

    // The Conversations tab of one solution (Story 3493): the threads between an outside agent
    // and the house. It is a relay with a person in the middle, and the page says so: a message
    // from the agent waits on YOU. Nothing on this page asks the AI anything -- to have Felix
    // answer, tell him (or Stewart) that there is a message, and his reply lands here.

    var SL = window.SolutionsCommon, $ = SL.$, esc = SL.esc, Page = window.SolutionPage;

    var mThreads = [];
    var mThread = null;        // the open thread's summary, or null while the list is showing
    var mMessages = [];

    // ---- the list -------------------------------------------------------------------

    async function loadList() {
        mThread = null;
        paintToolbar();
        var r = await SL.get('/api/solutions/' + Page.id + '/conversations');
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $('pane-conversations').innerHTML = '<div class="sl-empty">' + esc(SL.failText(r, 'Could not load the conversations.')) + '</div>';
            return;
        }
        mThreads = r.data;
        if (!mThreads.length) {
            $('pane-conversations').innerHTML = '<div class="sl-empty">No conversations yet.</div>';
            SL.status('0 conversations');
            return;
        }
        $('pane-conversations').innerHTML = '<div class="sl-cards">' + mThreads.map(card).join('') + '</div>';
        Array.prototype.forEach.call(document.querySelectorAll('[data-thread]'), function (c) {
            c.addEventListener('click', function () { openThread(+c.getAttribute('data-thread')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-conv-rename]'), function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); renameThread(+b.getAttribute('data-conv-rename')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-conv-remove]'), function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); unlinkThread(+b.getAttribute('data-conv-remove')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-conv-resolve]'), function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); SL.guard(b, function () { return resolveThread(+b.getAttribute('data-conv-resolve')); }); });
        });
        var waiting = mThreads.filter(function (t) { return t.status !== 'closed' && t.awaitingTonyCount > 0; }).length;
        SL.status(mThreads.length + (mThreads.length === 1 ? ' conversation' : ' conversations') + (waiting ? ' | ' + waiting + ' waiting on you' : ''));
    }

    function card(t) {
        var turn = '';
        var resolved = t.status === 'closed';
        if (resolved) turn = '<span class="sl-chip resolved" data-testid="conv-card-resolved">&#10003; Resolved</span>';
        else if (t.awaitingTonyCount > 0) turn = '<span class="sl-chip wait">Waiting on you</span>';
        else if (t.awaitingFrom) turn = '<span class="sl-chip">Waiting on ' + esc(memberName(t.awaitingFrom)) + '</span>';
        return '<div class="sl-card link" data-thread="' + t.conversationId + '" data-testid="conv-card">' +
            '<h3>' + esc(t.title) + '</h3>' +
            '<div class="row">' + turn + '<span class="sl-chip">' + t.messageCount + (t.messageCount === 1 ? ' message' : ' messages') + '</span></div>' +
            '<div class="sl-muted">' + (t.lastSender ? 'Last from ' + esc(memberName(t.lastSender)) + ', ' : '') + SL.dateTime(t.modifiedDate || t.createdDate) + '</div>' +
            '<div class="actions"><button type="button" class="sl-btn small" data-conv-resolve="' + t.conversationId + '" data-testid="conv-card-resolve">' + (resolved ? 'Reopen' : 'Resolve') + '</button>' +
            '<button type="button" class="sl-btn small" data-conv-rename="' + t.conversationId + '" data-testid="conv-card-rename">Edit</button>' +
            '<button type="button" class="sl-btn small danger" data-conv-remove="' + t.conversationId + '" data-testid="conv-card-remove">Remove</button></div></div>';
    }

    function memberName(code) {
        var m = Page.lookups.members.filter(function (x) { return x.code === code; })[0];
        return m ? m.name : code;
    }

    // ---- one thread -------------------------------------------------------------------

    async function openThread(conversationId) {
        if (!mThreads.length) {
            var l = await SL.get('/api/solutions/' + Page.id + '/conversations');
            mThreads = (l && l.ok && Array.isArray(l.data)) ? l.data : [];
        }
        var t = mThreads.filter(function (x) { return x.conversationId === conversationId; })[0];
        if (!t) { SL.toast('That conversation is not part of this solution.'); loadList(); return; }
        var wasWaiting = t.awaitingTonyCount > 0;
        // Opening it as a person marks it read -- the server clears the "waiting on Tony" flag.
        var r = await SL.get('/api/solutions/conversations/' + conversationId + '/messages');
        if (!r || !r.ok || !Array.isArray(r.data)) { SL.toast(SL.failText(r, 'That conversation could not be opened.')); return; }
        mThread = t;
        mMessages = r.data;
        drawThread(wasWaiting);
        Page.reload();
    }

    function drawThread(wasWaiting) {
        paintToolbar();
        var note = wasWaiting
            ? '<div class="sl-relay-note" data-testid="conv-relay-note">This was waiting on you. Nothing here wakes Felix on its own: to have him answer, ' +
              'tell Felix or Stewart there is a message on "' + esc(mThread.title) + '" in ' + esc(Page.solution.name) + ', or reply yourself below.</div>'
            : '';
        $('pane-conversations').innerHTML = '<div class="sl-thread">' +
            '<div class="sl-section-title" style="margin-top:0">' + esc(mThread.title) + '</div>' + note +
            '<div id="convMessages" data-testid="conv-messages">' + mMessages.map(message).join('') + '</div>' +
            '<div class="sl-compose" style="margin-top:12px" data-testid="conv-compose">' +
                '<textarea id="convReply" data-testid="conv-reply-text" placeholder="Reply"></textarea>' +
                '<div class="foot"><button type="button" class="sl-btn primary" id="convSend" data-testid="conv-reply-save">Save</button></div>' +
            '</div></div>';
        $('convSend').addEventListener('click', function () { SL.guard($('convSend'), reply); });
        Array.prototype.forEach.call(document.querySelectorAll('[data-msg-edit]'), function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); editMessage(+b.getAttribute('data-msg-edit')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-msg-delete]'), function (b) {
            b.addEventListener('click', function (e) { e.stopPropagation(); deleteMessage(+b.getAttribute('data-msg-delete')); });
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-msg-fold]'), function (h) {
            h.addEventListener('click', function (e) {
                if (e.target.closest('.sl-msgtools')) return;
                var id = +h.getAttribute('data-msg-fold');
                var m = mMessages.filter(function (x) { return x.id === id; })[0];
                if (!m) return;
                Page.fold().toggle('msg:' + id, SL.isLong(m.body, 600, 10));
                drawThread(false);
            });
        });
        var box = $('slBody');
        if (box) box.scrollTop = box.scrollHeight;
        SL.status(mThread.title + ' | ' + mMessages.length + ' messages');
    }

    function msgTools(m) {
        return '<span class="sl-msgtools"><button type="button" class="sl-iconbtn" title="Edit this message" data-msg-edit="' + m.id + '" data-testid="conv-message-edit">&#9998;</button>' +
            '<button type="button" class="sl-iconbtn" title="Delete this message" data-msg-delete="' + m.id + '" data-testid="conv-message-delete">&#10005;</button></span>';
    }

    /** The owner correcting the record: an inline editor on one message. */
    function editMessage(id) {
        var m = mMessages.filter(function (x) { return x.id === id; })[0];
        var host = document.querySelector('[data-msg-id="' + id + '"] .body');
        if (!m || !host) return;
        host.innerHTML = '<textarea class="sl-inline" data-testid="conv-message-editor" style="width:100%;min-height:90px;box-sizing:border-box"></textarea>' +
            '<div class="foot" style="margin-top:6px"><button type="button" class="sl-btn small" data-msg-cancel="1" data-testid="conv-message-cancel">Cancel</button> ' +
            '<button type="button" class="sl-btn small primary" data-msg-save="1" data-testid="conv-message-save">Save</button></div>';
        var ta = host.querySelector('textarea'); ta.value = m.body; ta.focus();
        host.querySelector('[data-msg-cancel]').addEventListener('click', function () { drawThread(false); });
        host.querySelector('[data-msg-save]').addEventListener('click', async function () {
            var text = ta.value.trim();
            if (!text) { SL.toast('A message needs some text.'); return; }
            var r = await SL.put('/api/solutions/conversations/' + mThread.conversationId + '/messages/' + id, { body: text });
            if (!r || !r.ok) { SL.toast(SL.failText(r, 'The message could not be changed.')); return; }
            m.body = r.data.body;
            drawThread(false);
        });
    }

    async function deleteMessage(id) {
        var m = mMessages.filter(function (x) { return x.id === id; })[0];
        if (!m) return;
        if (!window.confirm('Delete this message from ' + memberName(m.sender) + '?\n\n"' + SL.plain(m.body, 120) + '"\n\nThis cannot be undone.')) return;
        var r = await SL.del('/api/solutions/conversations/' + mThread.conversationId + '/messages/' + id);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The message could not be deleted.')); return; }
        mMessages = mMessages.filter(function (x) { return x.id !== id; });
        drawThread(false);
        Page.reload();
    }

    async function renameThread(conversationId) {
        var t = mThreads.filter(function (x) { return x.conversationId === conversationId; })[0] || mThread;
        var title = window.prompt('Conversation title:', t ? t.title : '');
        if (title === null) return;
        if (!title.trim()) { SL.toast('A conversation needs a title.'); return; }
        var r = await SL.put('/api/solutions/conversations/' + conversationId, { title: title.trim() });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The conversation could not be renamed.')); return; }
        if (mThread && mThread.conversationId === conversationId) { mThread.title = r.data.title; drawThread(false); }
        else loadList();
    }

    /** Resolve: the thread is finished and nobody is waited on. Reopen undoes it; a new message also reopens it. */
    async function resolveThread(conversationId) {
        var t = mThreads.filter(function (x) { return x.conversationId === conversationId; })[0];
        if (!t) return;
        var resolving = t.status !== 'closed';
        var question = resolving
            ? 'Resolve the conversation "' + t.title + '" (' + t.messageCount + ' messages)?\n\nIt will stop showing as waiting on anyone. A new message from either side opens it again.'
            : 'Reopen the conversation "' + t.title + '" (' + t.messageCount + ' messages)?';
        if (!window.confirm(question)) return;
        var r = await SL.put('/api/solutions/conversations/' + conversationId + '/resolved', { resolved: resolving });
        if (!r || !r.ok) { SL.toast(SL.failText(r, resolving ? 'The conversation could not be resolved.' : 'The conversation could not be reopened.')); return; }
        Page.reload();
        loadList();
    }

    async function unlinkThread(conversationId) {
        var t = mThreads.filter(function (x) { return x.conversationId === conversationId; })[0];
        if (!t) return;
        var question = 'Remove the conversation "' + t.title + '" (' + t.messageCount + ' messages) from ' + Page.solution.name +
            '?\n\nThe conversation itself is kept, but it will no longer show here and an outside agent will no longer be able to read it.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/' + Page.id + '/conversations/' + conversationId);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That did not work.')); return; }
        Page.reload();
        loadList();
    }

    /** A long message starts folded to its first line; its header flips it. */
    function message(m) {
        var fromAgent = Page.lookups.members.some(function (x) { return x.code === m.sender && x.agent && x.code !== 'felix' && x.code !== 'stewart'; });
        var isFolded = Page.fold().isCollapsed('msg:' + m.id, SL.isLong(m.body, 600, 10));
        if (isFolded) {
            return '<div class="sl-msg' + (fromAgent ? '' : ' mine') + '" data-msg-id="' + m.id + '" data-testid="conv-message">' +
                '<div class="who sl-foldable" data-msg-fold="' + m.id + '" data-testid="conv-message-head"><span class="chev folded">&#9660;</span><span>' + esc(memberName(m.sender)) +
                '</span><span class="sl-muted">' + SL.dateTime(m.createdDate) + '</span>' + msgTools(m) + '</div><div class="body">' + esc(SL.plain(m.body, 160)) + ' &hellip;</div></div>';
        }
        return '<div class="sl-msg' + (fromAgent ? '' : ' mine') + '" data-msg-id="' + m.id + '" data-testid="conv-message">' +
            '<div class="who sl-foldable" data-msg-fold="' + m.id + '" data-testid="conv-message-head"><span class="chev">&#9660;</span><span>' + esc(memberName(m.sender)) + '</span><span class="sl-muted">' + SL.dateTime(m.createdDate) + '</span>' +
                (m.awaitingFrom ? '<span class="sl-chip wait">waiting on ' + esc(memberName(m.awaitingFrom)) + '</span>' : '') + msgTools(m) + '</div>' +
            '<div class="body sl-md" data-testid="conv-message-body">' + SL.md(m.body) + '</div></div>';
    }

    async function reply() {
        var text = $('convReply').value.trim();
        if (!text) { SL.toast('A message needs some text.'); return; }
        var r = await SL.post('/api/solutions/conversations/' + mThread.conversationId + '/messages', { body: text });
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'The message could not be saved.')); return; }
        mMessages.push(r.data);
        drawThread(false);
        Page.reload();
    }

    // ---- new thread, unlink -------------------------------------------------------------

    function openNew() {
        SL.warn('convWarn', '');
        $('cTitle').value = '';
        $('cMessage').value = '';
        SL.open('convOverlay');
        $('cTitle').focus();
    }

    async function saveNew() {
        var title = $('cTitle').value.trim();
        if (!title) { SL.warn('convWarn', 'A conversation needs a title.'); return; }
        var r = await SL.post('/api/solutions/' + Page.id + '/conversations', { title: title, message: $('cMessage').value });
        if (!r || !r.ok) { SL.warn('convWarn', SL.failText(r, 'The conversation could not be saved.')); return; }
        SL.close('convOverlay');
        mThreads = [];
        openThread(r.data.conversationId);
    }

    async function unlink() {
        if (!mThread) return;
        var question = 'Remove the conversation "' + mThread.title + '" (' + mMessages.length + ' messages) from ' + Page.solution.name +
            '?\n\nThe conversation itself is kept, but it will no longer show here and an outside agent will no longer be able to read it.';
        if (!window.confirm(question)) return;
        var r = await SL.del('/api/solutions/' + Page.id + '/conversations/' + mThread.conversationId);
        if (!r || !r.ok) { SL.toast(SL.failText(r, 'That did not work.')); return; }
        Page.reload();
        loadList();
    }

    function paintToolbar() {
        var open = !!mThread;
        $('btnConvBack').hidden = !open;
        $('btnConvRename').hidden = !open;
        $('btnAddConv').hidden = false;         // always: a new thread can be started from inside another
        $('btnConvUnlink').hidden = !open;
    }

    // ---- the tab ---------------------------------------------------------------------------

    Page.registerTab('conversations', {
        init: function () {
            $('btnAddConv').addEventListener('click', openNew);
            $('btnConvBack').addEventListener('click', loadList);
            $('btnConvRename').addEventListener('click', function () { if (mThread) renameThread(mThread.conversationId); });
            $('btnConvUnlink').addEventListener('click', unlink);
            $('convCancel').addEventListener('click', function () { SL.close('convOverlay'); });
            $('convSave').addEventListener('click', function () { SL.guard($('convSave'), saveNew); });
        },
        show: function () { if (mThread) drawThread(false); else loadList(); },
        openThread: openThread
    });
})();
