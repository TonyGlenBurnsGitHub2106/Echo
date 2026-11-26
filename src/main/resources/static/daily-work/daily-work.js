(function () {
    'use strict';

    // Daily Work (Story 6852) -- a quick log of what I did at work.
    // Tony: "I need to be able to fill in the least number of fields possible and it still work.
    // I don't want logging work to become a task unto itself." So: one required box (What did
    // you do?), the date and time are the moment of saving unless changed, the team fills itself
    // from the first contact picked, and everything else waits under More.

    var VS = window.PicassoViewState;
    var PREFIX = 'echo.dw.';
    var KEY_DAYS = PREFIX + 'fold.days';
    var KEY_RANGE = PREFIX + 'range';
    var KEY_FOLLOW = PREFIX + 'followUp';
    var MAX_FOLDED_DAYS = 120;

    var mLookups = { teams: [], contacts: [], kinds: [] };
    var mPicked = [];            // contact ids on the form, in the order picked
    var mEditId = 0;             // 0 = a new entry
    var mWhenTouched = false;    // a new entry's date / time were changed by hand
    var mEntries = [];
    var mSearchTimer = null;
    var mLoadSeq = 0;

    function $(id) { return document.getElementById(id); }
    function esc(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function pad(n) { return n < 10 ? '0' + n : '' + n; }
    function isoDay(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
    function hhmm(d) { return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
    function fail(r, fallback) { return window.LWAuth.errorText(r, fallback); }

    function vsGet(key) { try { return VS ? VS.get(key) : null; } catch (e) { return null; } }
    function vsSet(key, value) { try { if (VS) VS.set(key, value); } catch (e) { /* remembered next time */ } }

    // ---- dates ----------------------------------------------------------------------------

    /** "2026-11-25T09:30:00" -> a local Date (the server sends local wall-clock time). */
    function parseLocal(iso) {
        if (!iso) return null;
        var m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2}))?)?/.exec(String(iso));
        if (!m) return null;
        return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0));
    }
    function usDate(d) { return pad(d.getMonth() + 1) + '/' + pad(d.getDate()) + '/' + d.getFullYear(); }
    function clock(d) {
        var h = d.getHours(), ap = h < 12 ? 'AM' : 'PM';
        h = h % 12 || 12;
        return h + ':' + pad(d.getMinutes()) + ' ' + ap;
    }
    var DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    function dayLabel(d) {
        var today = new Date(); today.setHours(0, 0, 0, 0);
        var that = new Date(d.getFullYear(), d.getMonth(), d.getDate());
        var diff = Math.round((today - that) / 86400000);
        var name = DAYS[d.getDay()] + ' ' + usDate(d);
        if (diff === 0) return 'Today, ' + name;
        if (diff === 1) return 'Yesterday, ' + name;
        return name;
    }

    /** The first day of the chosen range (null = everything). */
    function rangeFrom(code) {
        var d = new Date(); d.setHours(0, 0, 0, 0);
        if (code === 'week') { d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return isoDay(d); }   // Monday
        if (code === '2weeks') { d.setDate(d.getDate() - 13); return isoDay(d); }
        if (code === 'month') { d.setDate(1); return isoDay(d); }
        if (code === '90') { d.setDate(d.getDate() - 89); return isoDay(d); }
        if (code === 'year') { d.setMonth(0, 1); return isoDay(d); }
        return null;
    }
    var RANGE_NAMES = { week: 'This week', '2weeks': 'Last two weeks', month: 'This month', '90': 'Last 90 days', year: 'This year', all: 'Everything' };

    // ---- links ----------------------------------------------------------------------------

    function isUrl(s) { return /^https?:\/\//i.test(String(s || '').trim()); }

    /** A short name for a link: a Confluence page's title from its address, else the host and path. */
    function linkLabel(url) {
        try {
            var u = new URL(url);
            var title = u.searchParams.get('title');
            var parts = u.pathname.split('/').filter(function (p) { return p; });
            var last = title || (parts.length ? parts[parts.length - 1] : '');
            last = decodeURIComponent(String(last).replace(/\+/g, ' '));
            if (!last || /^\d+$/.test(last) || /\.action$/i.test(last)) last = u.host + u.pathname;
            return last.length > 60 ? last.slice(0, 57) + '...' : last;
        } catch (e) {
            return url.length > 60 ? url.slice(0, 57) + '...' : url;
        }
    }
    function linkHtml(url, label) {
        if (!isUrl(url)) return esc(url);
        return '<a href="' + esc(url) + '" target="_blank" rel="noopener noreferrer" data-testid="dw-link">' + esc(label || linkLabel(url)) + '</a>';
    }
    function storyHtml(story) {
        if (!story) return '';
        return isUrl(story) ? linkHtml(story) : esc(story);
    }

    // ---- folds (every header folds and remembers) ------------------------------------------

    function foldKey(name) { return PREFIX + 'fold.' + name; }

    function applyFold(head, folded) {
        var box = head.classList.contains('dw-link-btn') ? head.parentNode : head.parentNode;
        box.classList.toggle('is-folded', folded);
        head.setAttribute('aria-expanded', folded ? 'false' : 'true');
    }

    function wireFolds() {
        Array.prototype.forEach.call(document.querySelectorAll('.dw-fold-head[data-fold]'), function (head) {
            var name = head.getAttribute('data-fold');
            var saved = vsGet(foldKey(name));
            // More starts folded; the form and the list start open.
            applyFold(head, saved == null ? name === 'more' : saved === '1');
            head.addEventListener('click', function (e) {
                if (e.target.closest('a, input, select, textarea')) return;
                var folded = !head.parentNode.classList.contains('is-folded');
                applyFold(head, folded);
                vsSet(foldKey(name), folded ? '1' : '0');
                if (name === 'more') paintMoreSummary();
            });
        });
    }

    function foldedDays() {
        var raw = vsGet(KEY_DAYS) || '';
        return raw ? raw.split(',') : [];
    }
    function setFoldedDays(list) {
        vsSet(KEY_DAYS, list.slice(-MAX_FOLDED_DAYS).join(','));
    }

    // ---- lookups and the dropdowns --------------------------------------------------------

    async function loadLookups() {
        var r = await window.LWAuth.apiGet('/api/daily-work/lookups');
        if (r && r.ok && r.data) mLookups = r.data;
        else window.Picasso.error(fail(r, 'Could not load the teams and contacts.'));
    }

    function fill(sel, options, blankLabel, value) {
        sel.innerHTML = '<option value="">' + esc(blankLabel) + '</option>' + options.map(function (o) {
            return '<option value="' + esc(o.id) + '">' + esc(o.name) + '</option>';
        }).join('');
        sel.value = value == null ? '' : String(value);
    }

    function fillContacts() { fill($('fldContact'), mLookups.contacts, 'Pick a contact', ''); }
    function fillTeams(value) { fill($('fldTeam'), mLookups.teams, 'No team', value); }
    function fillKinds(value) { fill($('fldKind'), mLookups.kinds, 'Any', value); }

    function contactById(id) {
        for (var i = 0; i < mLookups.contacts.length; i++) if (mLookups.contacts[i].id === id) return mLookups.contacts[i];
        return null;
    }

    /** Add a contact to the entry. The team fills from the first contact that has one, unless a team is already chosen. */
    function pickContact(id) {
        if (!id || mPicked.indexOf(id) >= 0) return;
        mPicked.push(id);
        var c = contactById(id);
        if (c && c.teamId && !$('fldTeam').value) $('fldTeam').value = String(c.teamId);
        paintChips();
    }

    function paintChips() {
        $('dwContactChips').innerHTML = mPicked.map(function (id) {
            var c = contactById(id);
            return '<span class="dw-chip" data-testid="dw-contact-chip">' + esc(c ? c.name : ('#' + id)) +
                '<button type="button" data-unpick="' + id + '" title="Take off this entry" aria-label="Remove">&times;</button></span>';
        }).join('');
    }

    async function addNamed(kind, text) {
        var name = String(text || '').trim();
        if (!name) { await window.Picasso.message('Type the name in the box first, then tap Add.'); return null; }
        var body = { name: name };
        if (kind === 'contacts' && $('fldTeam').value) body.teamId = +$('fldTeam').value;
        var r = await window.LWAuth.apiPost('/api/daily-work/' + kind, body, 'POST');
        if (!r || !r.ok || !r.data) { await window.Picasso.error(fail(r, 'That could not be added.')); return null; }
        var list = kind === 'teams' ? mLookups.teams : (kind === 'kinds' ? mLookups.kinds : mLookups.contacts);
        if (!list.some(function (x) { return x.id === r.data.id; })) {
            list.push(r.data);
            list.sort(function (a, b) { return a.name.localeCompare(b.name); });
        }
        return r.data;
    }

    // ---- the form -------------------------------------------------------------------------

    function warn(text) {
        $('dwWarn').textContent = text || '';
        $('dwWarn').classList.toggle('show', !!text);
    }

    function setWhen(d) {
        $('fldDate').value = isoDay(d);
        $('fldTime').value = hhmm(d);
    }

    function paintWhenHint() {
        $('dwWhenHint').textContent = mEditId ? 'When this work was done.'
            : (mWhenTouched ? 'This entry will be saved with the date and time above.'
                            : 'Leave the date and time alone and they are the moment you save.');
    }

    function paintMoreSummary() {
        var bits = [];
        if ($('fldStory').value.trim()) bits.push('story');
        if ($('fldKind').value) bits.push('kind');
        var links = $('fldLinks').value.split(/\r?\n/).filter(function (l) { return l.trim(); }).length;
        if (links) bits.push(links + (links === 1 ? ' link' : ' links'));
        if ($('fldMinutes').value) bits.push($('fldMinutes').value + ' min');
        if ($('fldFollowUp').checked) bits.push('follow-up');
        if ($('fldNotes').value.trim()) bits.push('notes');
        if (mWhenTouched || mEditId) bits.push($('fldDate').value ? usDate(parseLocal($('fldDate').value)) : '');
        $('dwMoreSummary').textContent = bits.filter(Boolean).length ? '(' + bits.filter(Boolean).join(', ') + ')' : '';
    }

    function hasTyping() {
        return !!($('fldWorkDone').value.trim() || mPicked.length || $('fldStory').value.trim() || $('fldLinks').value.trim()
            || $('fldNotes').value.trim() || $('fldMinutes').value || $('fldFollowUp').checked || $('fldKind').value);
    }

    function resetForm() {
        mEditId = 0;
        mPicked = [];
        mWhenTouched = false;
        warn('');
        $('fldWorkDone').value = '';
        $('fldStory').value = '';
        $('fldLinks').value = '';
        $('fldNotes').value = '';
        $('fldMinutes').value = '';
        $('fldFollowUp').checked = false;
        fillContacts();
        fillTeams('');
        fillKinds('');
        setWhen(new Date());
        paintChips();
        $('dwFormTitle').textContent = 'Log work';
        $('dwFormSub').textContent = '';
        $('btnDelete').hidden = true;
        $('btnClear').textContent = 'Clear';
        paintWhenHint();
        paintMoreSummary();
    }

    function openFold(name) {
        var head = document.querySelector('.dw-fold-head[data-fold="' + name + '"]');
        if (head && head.parentNode.classList.contains('is-folded')) {
            applyFold(head, false);
            vsSet(foldKey(name), '0');
        }
    }

    async function startEdit(id) {
        var r = await window.LWAuth.apiGet('/api/daily-work/entries/' + id);
        if (!r || !r.ok || !r.data) { await window.Picasso.error(fail(r, 'Could not open that entry.')); return; }
        var e = r.data;
        resetForm();
        mEditId = e.id;
        $('fldWorkDone').value = e.workDone || '';
        mPicked = (e.contacts || []).map(function (c) { return c.id; });
        // A contact made inactive still shows on an old entry: keep its name for the chip.
        (e.contacts || []).forEach(function (c) { if (!contactById(c.id)) mLookups.contacts.push({ id: c.id, name: c.name, teamId: null, teamName: null }); });
        fillTeams(e.teamId || '');
        fillKinds(e.kindId || '');
        $('fldStory').value = e.storyRef || '';
        $('fldLinks').value = (e.links || []).join('\n');
        $('fldNotes').value = e.notes || '';
        $('fldMinutes').value = e.minutesSpent == null ? '' : String(e.minutesSpent);
        $('fldFollowUp').checked = !!e.followUp;
        var when = parseLocal(e.workDate);
        if (when) setWhen(when);
        paintChips();
        $('dwFormTitle').textContent = 'Edit entry';
        $('dwFormSub').textContent = when ? dayLabel(when) + ' ' + clock(when) : '';
        $('btnDelete').hidden = false;
        $('btnClear').textContent = 'Cancel edit';
        paintWhenHint();
        paintMoreSummary();
        openFold('form');
        $('dwBody').scrollTop = 0;
        $('fldWorkDone').focus();
    }

    function draft() {
        var minutes = $('fldMinutes').value.trim();
        var when = null;
        if (mEditId || mWhenTouched) {
            var d = $('fldDate').value, t = $('fldTime').value || '00:00';
            if (d) when = d + 'T' + t + ':00';
        }
        return {
            workDate: when,
            workDone: $('fldWorkDone').value,
            storyRef: $('fldStory').value,
            teamId: $('fldTeam').value ? +$('fldTeam').value : null,
            kindId: $('fldKind').value ? +$('fldKind').value : null,
            minutesSpent: minutes === '' ? null : Math.round(+minutes),
            followUp: $('fldFollowUp').checked,
            notes: $('fldNotes').value,
            contactIds: mPicked.slice(),
            links: $('fldLinks').value.split(/\r?\n/).map(function (l) { return l.trim(); }).filter(Boolean)
        };
    }

    async function save() {
        warn('');
        var body = draft();
        if (!body.workDone.trim()) { warn('Say what you did.'); $('fldWorkDone').focus(); return; }
        if (body.minutesSpent != null && (isNaN(body.minutesSpent) || body.minutesSpent < 0 || body.minutesSpent > 1440)) {
            warn('Minutes spent should be between 0 and 1440.'); openFold('more'); return;
        }
        var bad = body.links.filter(function (l) { return !isUrl(l); });
        if (bad.length) { warn('A link should start with http:// or https:// -- ' + bad[0]); openFold('more'); return; }
        var r = mEditId
            ? await window.LWAuth.apiPost('/api/daily-work/entries/' + mEditId, body, 'PUT')
            : await window.LWAuth.apiPost('/api/daily-work/entries', body, 'POST');
        if (!r || !r.ok) { warn(fail(r, 'The entry was not saved.')); return; }
        // A routine save shows nothing: the entry appears in the list. The save may have given a
        // contact its team (the proc learns it), so the lookups are read again before the next pick.
        await loadLookups();
        resetForm();
        await loadList(r.data && r.data.id);
        $('fldWorkDone').focus();
    }

    async function clearOrCancel() {
        if (hasTyping()) {
            var ok = await window.Picasso.yesNo(mEditId ? 'Stop editing? The changes you made are not saved.'
                                                         : 'Clear what you typed?');
            if (!ok) return;
        }
        resetForm();
        $('fldWorkDone').focus();
    }

    async function deleteEntry() {
        if (!mEditId) return;
        var what = $('fldWorkDone').value.trim();
        if (what.length > 60) what = what.slice(0, 57) + '...';
        var ok = await window.Picasso.confirmDelete('Delete Daily Work entry: ' + what + '?\n\n' +
            'Its contacts and links come off with it (the people and teams stay). This cannot be undone.');
        if (!ok) return;
        var r = await window.LWAuth.apiPost('/api/daily-work/entries/' + mEditId, null, 'DELETE');
        if (!r || !r.ok) { await window.Picasso.error(fail(r, 'The entry was not deleted.')); return; }
        resetForm();
        await loadList();
    }

    // ---- the list -------------------------------------------------------------------------

    function filterNow() {
        return {
            range: $('fldRange').value || 'week',
            q: $('fldSearch').value.trim(),
            followUp: $('chkFollowUp').checked
        };
    }

    async function loadList(flashId) {
        var f = filterNow(), from = rangeFrom(f.range);
        var qs = [];
        if (from) qs.push('from=' + from);
        if (f.q) qs.push('q=' + encodeURIComponent(f.q));
        if (f.followUp) qs.push('followUp=true');
        var seq = ++mLoadSeq;
        var r = await window.LWAuth.apiGet('/api/daily-work/entries' + (qs.length ? '?' + qs.join('&') : ''));
        if (seq !== mLoadSeq) return;   // a newer search answered first
        if (!r || !r.ok || !Array.isArray(r.data)) {
            $('dwList').innerHTML = '<div class="dw-empty">' + esc(fail(r, 'Could not load the entries.')) + '</div>';
            return;
        }
        mEntries = r.data;
        render(flashId);
    }

    function groupByDay(list) {
        var groups = [], byKey = {};
        list.forEach(function (e) {
            var d = parseLocal(e.workDate) || new Date();
            var key = isoDay(d);
            if (!byKey[key]) { byKey[key] = { key: key, date: d, entries: [], minutes: 0 }; groups.push(byKey[key]); }
            byKey[key].entries.push(e);
            if (e.minutesSpent) byKey[key].minutes += e.minutesSpent;
        });
        return groups;
    }

    function minutesText(m) {
        if (!m) return '';
        var h = Math.floor(m / 60), mm = m % 60;
        return h ? h + 'h' + (mm ? ' ' + mm + 'm' : '') : mm + 'm';
    }

    function entryHtml(e) {
        var d = parseLocal(e.workDate);
        var meta = [];
        (e.contacts || []).forEach(function (c) { meta.push('<span class="dw-chip plain">' + esc(c.name) + '</span>'); });
        if (e.teamName) meta.push('<span class="dw-chip team">' + esc(e.teamName) + '</span>');
        if (e.kindName) meta.push('<span class="dw-chip kind">' + esc(e.kindName) + '</span>');
        if (e.storyRef) meta.push('<span>Story ' + storyHtml(e.storyRef) + '</span>');
        (e.links || []).forEach(function (l) { meta.push(linkHtml(l)); });
        if (e.minutesSpent) meta.push('<span>' + esc(minutesText(e.minutesSpent)) + '</span>');
        return '<div class="dw-entry' + (e.followUp ? ' follow' : '') + '" data-id="' + e.id + '" data-testid="dw-entry">' +
            '<div class="dw-time">' + (d ? esc(clock(d)) : '') + '</div>' +
            '<div class="dw-main">' +
                '<div class="dw-what" data-testid="dw-entry-what">' + esc(e.workDone) + '</div>' +
                (meta.length ? '<div class="dw-meta">' + meta.join('') + '</div>' : '') +
                (e.notesPreview ? '<div class="dw-notes">' + esc(e.notesPreview) + (e.notesCut ? '...' : '') + '</div>' : '') +
            '</div>' +
            '<div class="dw-acts">' +
                '<button type="button" class="dw-flag' + (e.followUp ? ' on' : '') + '" data-flag="' + e.id + '" data-testid="dw-entry-flag"' +
                    ' title="' + (e.followUp ? 'Needs a follow-up. Tap when it is dealt with.' : 'Tap to mark it as needing a follow-up.') + '">' +
                    (e.followUp ? '&#9873; Follow up' : '&#9872; Follow up') + '</button>' +
                '<a data-view="' + e.id + '" data-testid="dw-entry-view">View</a>' +
                '<a data-edit="' + e.id + '" data-testid="dw-entry-edit">Edit</a>' +
            '</div>' +
        '</div>';
    }

    function render(flashId) {
        var f = filterNow();
        var n = mEntries.length;
        $('dwCount').textContent = n ? '(' + n + ')' : '';
        if (!n) {
            $('dwList').innerHTML = '<div class="dw-empty" data-testid="dw-empty">' +
                (f.q || f.followUp ? 'Nothing matches.' : 'Nothing logged ' + (RANGE_NAMES[f.range] || '').toLowerCase() + ' yet.') + '</div>';
            $('statusBar').textContent = 'Echo | Daily Work';
            paintFoldAll();
            return;
        }
        var folded = foldedDays();
        $('dwList').innerHTML = groupByDay(mEntries).map(function (g) {
            var isFolded = folded.indexOf(g.key) >= 0;
            return '<div class="dw-day' + (isFolded ? ' is-folded' : '') + '" data-day="' + g.key + '" data-testid="dw-day">' +
                '<div class="dw-fold-head" data-testid="dw-day-head">' + esc(dayLabel(g.date)) +
                    ' <span class="dw-sub">' + g.entries.length + (g.entries.length === 1 ? ' entry' : ' entries') +
                    (g.minutes ? ', ' + esc(minutesText(g.minutes)) : '') + '</span>' +
                    '<span class="dw-chev" aria-hidden="true">&#9662;</span></div>' +
                '<div class="dw-fold-body">' + g.entries.map(entryHtml).join('') + '</div>' +
            '</div>';
        }).join('');
        var follow = mEntries.filter(function (e) { return e.followUp; }).length;
        $('statusBar').textContent = n + (n === 1 ? ' entry' : ' entries') + (n >= 500 ? ' (the newest 500)' : '') +
            (follow ? ' | ' + follow + ' to follow up' : '');
        paintFoldAll();
        if (flashId) {
            var row = document.querySelector('.dw-entry[data-id="' + flashId + '"]');
            if (row) {
                var day = row.closest('.dw-day');
                if (day && day.classList.contains('is-folded')) toggleDay(day, false);
            }
        }
    }

    function toggleDay(day, folded) {
        var key = day.getAttribute('data-day');
        day.classList.toggle('is-folded', folded);
        var list = foldedDays().filter(function (k) { return k !== key; });
        if (folded) list.push(key);
        setFoldedDays(list);
        paintFoldAll();
    }

    function paintFoldAll() {
        var days = document.querySelectorAll('.dw-day');
        var btn = $('btnFoldAll');
        btn.hidden = days.length < 2;
        var anyOpen = Array.prototype.some.call(days, function (d) { return !d.classList.contains('is-folded'); });
        btn.textContent = anyOpen ? 'Fold all' : 'Open all';
        btn.title = anyOpen ? 'Fold every day' : 'Open every day';
    }

    function foldAll() {
        var days = Array.prototype.slice.call(document.querySelectorAll('.dw-day'));
        var anyOpen = days.some(function (d) { return !d.classList.contains('is-folded'); });
        var list = foldedDays();
        days.forEach(function (d) {
            var key = d.getAttribute('data-day');
            d.classList.toggle('is-folded', anyOpen);
            list = list.filter(function (k) { return k !== key; });
            if (anyOpen) list.push(key);
        });
        setFoldedDays(list);
        paintFoldAll();
    }

    async function toggleFollowUp(id) {
        var e = mEntries.filter(function (x) { return x.id === id; })[0];
        if (!e) return;
        var r = await window.LWAuth.apiPost('/api/daily-work/entries/' + id + '/follow-up', { followUp: !e.followUp }, 'PUT');
        if (!r || !r.ok) { await window.Picasso.error(fail(r, 'The follow-up flag was not changed.')); return; }
        var top = $('dwBody').scrollTop;
        await loadList();
        $('dwBody').scrollTop = top;   // a tap in place never moves the page
    }

    async function viewEntry(id) {
        var r = await window.LWAuth.apiGet('/api/daily-work/entries/' + id);
        if (!r || !r.ok || !r.data) { await window.Picasso.error(fail(r, 'Could not open that entry.')); return; }
        var e = r.data, d = parseLocal(e.workDate);
        var title = String(e.workDone || '').split(/\r?\n/)[0];
        if (title.length > 80) title = title.slice(0, 77) + '...';
        window.LWED.showRecordView({
            badge: 'Daily Work',
            title: title,
            fields: [
                { label: 'When', value: d ? dayLabel(d) + ', ' + clock(d) : '' },
                { label: 'What I did', type: 'textarea', value: e.workDone },
                { label: 'Who for', value: (e.contacts || []).map(function (c) { return c.name; }).join(', ') },
                { label: 'Team', value: e.teamName || '' },
                { label: 'Kind of work', value: e.kindName || '' },
                { label: 'Story', type: 'html', html: storyHtml(e.storyRef) },
                { label: 'Links', type: 'html', html: (e.links || []).map(function (l) { return linkHtml(l, l); }).join('<br>') },
                { label: 'Minutes spent', value: e.minutesSpent == null ? '' : String(e.minutesSpent) },
                { label: 'Needs a follow-up', type: 'checkbox', value: !!e.followUp },
                { label: 'Notes', type: 'textarea', value: e.notes || '' }
            ],
            onEdit: function () { startEdit(e.id); }
        });
    }

    // ---- print: a clean report of what the list shows now ----------------------------------

    function printReport() {
        var f = filterNow();
        var what = [RANGE_NAMES[f.range] || 'Everything'];
        if (f.q) what.push('matching "' + f.q + '"');
        if (f.followUp) what.push('follow-ups only');
        var html = '<h1>Daily Work</h1><p>' + esc(what.join(', ')) + ' | ' + mEntries.length +
            (mEntries.length === 1 ? ' entry' : ' entries') + ' | printed ' + esc(usDate(new Date()) + ' ' + clock(new Date())) + '</p>';
        if (!mEntries.length) html += '<p>Nothing to show.</p>';
        groupByDay(mEntries).forEach(function (g) {
            html += '<h2>' + esc(dayLabel(g.date)) + (g.minutes ? ' (' + esc(minutesText(g.minutes)) + ')' : '') + '</h2>' +
                '<table style="width:100%;border-collapse:collapse;font-size:9.5pt"><thead><tr>' +
                ['Time', 'What I did', 'Who for', 'Team', 'Kind', 'Story / links'].map(function (h) {
                    return '<th style="text-align:left;border-bottom:1px solid #999;padding:3px 4px">' + h + '</th>';
                }).join('') + '</tr></thead><tbody>' +
                g.entries.map(function (e) {
                    var d = parseLocal(e.workDate);
                    var refs = [];
                    if (e.storyRef) refs.push(esc(e.storyRef));
                    (e.links || []).forEach(function (l) { refs.push(esc(linkLabel(l))); });
                    var cell = 'style="vertical-align:top;border-bottom:1px solid #ddd;padding:3px 4px"';
                    return '<tr>' +
                        '<td ' + cell + '>' + (d ? esc(clock(d)) : '') + '</td>' +
                        '<td ' + cell + '>' + esc(e.workDone) + (e.followUp ? ' <b>(follow up)</b>' : '') +
                            (e.notesPreview ? '<br><i>' + esc(e.notesPreview) + (e.notesCut ? '...' : '') + '</i>' : '') + '</td>' +
                        '<td ' + cell + '>' + esc((e.contacts || []).map(function (c) { return c.name; }).join(', ')) + '</td>' +
                        '<td ' + cell + '>' + esc(e.teamName || '') + '</td>' +
                        '<td ' + cell + '>' + esc(e.kindName || '') + '</td>' +
                        '<td ' + cell + '>' + refs.join('<br>') + '</td></tr>';
                }).join('') + '</tbody></table>';
        });
        $('dwReport').innerHTML = html;
        window.Picasso.printThisPage({ selector: '#dwReport', title: 'Daily Work' });
    }

    // ---- wire -----------------------------------------------------------------------------

    function wire() {
        $('btnSave').addEventListener('click', function () {
            var b = $('btnSave');
            if (b.disabled) return;
            b.disabled = true;
            save().finally(function () { b.disabled = false; });
        });
        $('btnClear').addEventListener('click', clearOrCancel);
        $('btnDelete').addEventListener('click', deleteEntry);
        $('btnPrint').addEventListener('click', function (e) { e.preventDefault(); printReport(); });
        $('btnFoldAll').addEventListener('click', foldAll);

        // Ctrl+Enter (or Cmd+Enter) saves from anywhere in the form.
        $('dwFormCard').addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); $('btnSave').click(); }
        });

        $('fldContact').addEventListener('change', function () {
            var v = +$('fldContact').value;
            if (!v) return;
            pickContact(v);
            $('fldContact').value = '';
        });
        $('dwContactChips').addEventListener('click', function (e) {
            var b = e.target.closest('[data-unpick]');
            if (!b) return;
            var id = +b.getAttribute('data-unpick');
            mPicked = mPicked.filter(function (x) { return x !== id; });
            paintChips();
        });
        $('fldContact').addEventListener('ps-add', async function (e) {
            var c = await addNamed('contacts', e.detail && e.detail.text);
            if (!c) return;
            fillContacts();
            pickContact(c.id);
        });
        $('fldTeam').addEventListener('ps-add', async function (e) {
            var t = await addNamed('teams', e.detail && e.detail.text);
            if (!t) return;
            fillTeams('');
            window.PicassoSelect.addOption($('fldTeam'), t.id, t.name);
        });
        $('fldKind').addEventListener('ps-add', async function (e) {
            var k = await addNamed('kinds', e.detail && e.detail.text);
            if (!k) return;
            fillKinds('');
            window.PicassoSelect.addOption($('fldKind'), k.id, k.name);
            paintMoreSummary();
        });

        ['fldDate', 'fldTime'].forEach(function (id) {
            $(id).addEventListener('input', function () { mWhenTouched = true; paintWhenHint(); paintMoreSummary(); });
        });
        ['fldStory', 'fldLinks', 'fldMinutes', 'fldNotes'].forEach(function (id) { $(id).addEventListener('input', paintMoreSummary); });
        $('fldFollowUp').addEventListener('change', paintMoreSummary);
        $('fldKind').addEventListener('change', paintMoreSummary);

        // The list.
        var savedRange = vsGet(KEY_RANGE);
        if (savedRange && RANGE_NAMES[savedRange]) $('fldRange').value = savedRange;
        $('chkFollowUp').checked = vsGet(KEY_FOLLOW) === '1';
        $('fldRange').addEventListener('change', function () { vsSet(KEY_RANGE, $('fldRange').value); loadList(); });
        $('chkFollowUp').addEventListener('change', function () { vsSet(KEY_FOLLOW, $('chkFollowUp').checked ? '1' : '0'); loadList(); });
        $('fldSearch').addEventListener('input', function () {
            clearTimeout(mSearchTimer);
            mSearchTimer = setTimeout(function () { loadList(); }, 300);
        });

        $('dwList').addEventListener('click', function (e) {
            var t = e.target;
            var flag = t.closest('[data-flag]');
            if (flag) { toggleFollowUp(+flag.getAttribute('data-flag')); return; }
            var view = t.closest('[data-view]');
            if (view) { viewEntry(+view.getAttribute('data-view')); return; }
            var edit = t.closest('[data-edit]');
            if (edit) { startEdit(+edit.getAttribute('data-edit')); return; }
            if (t.closest('a')) return;   // a story or Confluence link opens itself
            var head = t.closest('.dw-day > .dw-fold-head');
            if (head) { var day = head.parentNode; toggleDay(day, !day.classList.contains('is-folded')); }
        });
    }

    (async function () {
        await window.LWAuth.ensureAuth();
        if (VS && VS.sync) { try { await VS.sync(PREFIX); } catch (e) { /* the cache will do */ } }
        wireFolds();
        wire();
        await loadLookups();
        resetForm();
        await loadList();
        $('fldWorkDone').focus();
    })();
})();
