/* Shared rendering + edit infrastructure for Contact and Vendor detail pages.
   Exports a single namespace, window.LWED, with these surface areas:
     - escapeHtml / formatDateRange / field         -- small render helpers
     - wireTabs / renderEmpty / renderError         -- pane lifecycle
     - getLookup(name)                              -- cached /api/lookups/{name}
     - openModal(config) / closeModal()             -- generic edit dialog
     - loadEntitySubLists(entityId, panes)          -- fetches every child list
                                                       the caller supplied a pane
                                                       for, all in parallel
     - renderAddresses/Phones/WebAddresses/Logins   -- pane painters with edit affordances

   Single responsibility per function -- controllers/services on the Java side
   stay clean; this file is only the view layer for the four sub-list panes
   and the shared modal. Per-page edit logic for the Info pane lives in
   contact.js / vendor.js. */
(function () {
    'use strict';

    const ED = window.LWED = {};

    // ===== Small helpers ====================================================

    ED.escapeHtml = function (s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };

    ED.formatDateRange = function (effective, end) {
        if (!effective && !end) return '';
        return (effective || '\u221E') + ' \u2192 ' + (end || '\u221E');
    };

    // Age in whole years from a birth date, for read-only display. Accepts the
    // 'yyyy-MM-dd' the API returns -- parsed field-by-field on purpose, because
    // ===== Story 3481: defaults for a NEW child record =====================
    //
    // Adding a phone, web address, address or login to a record is almost
    // always describing THAT record -- the description Tony types is the
    // entity's own name, and the effective date is today. Typing both again
    // on every child was the whole complaint. Defaults apply on ADD only; an
    // existing row always shows what is stored, never a default.

    // Today as yyyy-MM-dd in LOCAL time. Never toISOString() -- that is UTC
    // and lands on tomorrow's date all evening west of Greenwich.
    ED.todayIso = function () {
        const t = new Date();
        return t.getFullYear() + '-'
             + String(t.getMonth() + 1).padStart(2, '0') + '-'
             + String(t.getDate()).padStart(2, '0');
    };

    // The display name of the record whose detail page is open. Host pages
    // (contact / vendor / userentity / matter / call-log) set this as soon as
    // they know it; it is cleared with the entity id so a stale name from the
    // previously viewed record can never leak into a new one's child form.
    let _entityName   = '';
    let _entityNameId = 0;

    ED.setEntityName = function (entityId, name) {
        _entityNameId = parseInt(entityId, 10) || 0;
        _entityName   = (name == null ? '' : String(name)).trim();
    };

    /** The current record's name, or '' when it is unknown or belongs to a
     *  different record than the one being asked about. */
    ED.entityName = function (entityId) {
        const id = parseInt(entityId, 10) || 0;
        if (!id || id !== _entityNameId) return '';
        return _entityName;
    };

    // new Date('yyyy-MM-dd') is treated as UTC and lands a day early west of
    // Greenwich -- and falls back to Date.parse for anything else. Returns ''
    // when the date is missing, unparseable, or in the future, so callers can
    // render the blank marker without special-casing.
    ED.ageFromBirthDate = function (birthDate) {
        if (birthDate == null) return '';
        const raw = String(birthDate).trim();
        if (!raw) return '';
        let y, m, d;
        const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(raw);
        if (iso) {
            y = parseInt(iso[1], 10); m = parseInt(iso[2], 10); d = parseInt(iso[3], 10);
        } else {
            const parsed = new Date(raw);
            if (isNaN(parsed.getTime())) return '';
            y = parsed.getFullYear(); m = parsed.getMonth() + 1; d = parsed.getDate();
        }
        const born = new Date(y, m - 1, d);
        if (isNaN(born.getTime()) || born.getFullYear() !== y
            || born.getMonth() !== m - 1 || born.getDate() !== d) return '';
        const now = new Date();
        const nowMonth = now.getMonth() + 1;
        let age = now.getFullYear() - y;
        if (nowMonth < m || (nowMonth === m && now.getDate() < d)) age -= 1;
        if (age < 0) return '';
        return String(age);
    };

    ED.field = function (label, value) {
        return '<div class="ed-field"><div class="lbl">' + ED.escapeHtml(label)
            + '</div><div class="val">' + (value == null || value === '' ? '--' : ED.escapeHtml(value))
            + '</div></div>';
    };

    // Phone-number helpers. The DB stores digits only (tblPhone.PhoneNumberInt
    // is BIGINT) and the display string is derived server-side, so we strip
    // formatting before saving and pretty-print for display. formatPhone is
    // forgiving: a non-standard length is returned unchanged so partial or
    // international numbers are not mangled. (Story 2226)
    ED.stripPhone = function (raw) {
        return String(raw == null ? '' : raw).replace(/\D/g, '');
    };
    ED.formatPhone = function (raw) {
        const d = ED.stripPhone(raw);
        if (d.length === 10) {
            return '(' + d.slice(0, 3) + ') ' + d.slice(3, 6) + '-' + d.slice(6);
        }
        if (d.length === 11 && d.charAt(0) === '1') {
            return '1 (' + d.slice(1, 4) + ') ' + d.slice(4, 7) + '-' + d.slice(7);
        }
        return raw == null ? '' : String(raw);
    };

    // Tab wiring with optional URL-driven default + URL sync.
    //   opts.defaultTab  string  -- falls back here when ?tab= isn't in the URL
    //   opts.syncUrl     bool    -- when true, switching tabs pushes ?tab=X
    //                              into the URL via history.replaceState, so
    //                              Quick Launch's Pin captures the active tab
    function activateTab(name, tabs, panes) {
        let matched = false;
        tabs.forEach(function (t) {
            const isMatch = t.dataset.tab === name;
            t.classList.toggle('active', isMatch);
            if (isMatch) matched = true;
        });
        panes.forEach(function (p) {
            p.classList.toggle('active', p.dataset.pane === name);
        });
        return matched;
    }

    let _tabSyncUrl = false;

    /** Switch to a tab by name -- the same as tapping it. Tabs and panes are
     *  looked up now, not remembered, because the Needs attention tab (Story
     *  3504) is injected after the page wired its tabs. */
    ED.showTab = function (name) {
        if (!name) return;
        activateTab(name, document.querySelectorAll('.ed-tab'), document.querySelectorAll('.ed-pane'));
        dashboardTabShown(name);
        timelineTabShown(name);
        if (_tabSyncUrl && history.replaceState) {
            const p = new URLSearchParams(location.search);
            p.set('tab', name);
            history.replaceState(null, '', location.pathname + '?' + p.toString());
        }
    };

    /** Go to a tab from somewhere other than the tab bar -- the Overview's
     *  section titles are a menu of the record's tabs. False when this page has
     *  no such tab showing, so the caller can do something else instead. */
    ED.openTab = function (name) {
        const tab = name ? document.querySelector('.ed-tab[data-tab="' + name + '"]') : null;
        if (!tab || tab.hidden || tab.disabled) return false;
        ED.showTab(name);
        if (tab.scrollIntoView) tab.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        return true;
    };

    ED.wireTabs = function (opts) {
        opts = opts || {};
        _tabSyncUrl = !!opts.syncUrl;
        const tabs  = document.querySelectorAll('.ed-tab');
        const panes = document.querySelectorAll('.ed-pane');

        // Pick initial tab. Priority: ?tab= in URL > opts.defaultTab > HTML
        const params  = new URLSearchParams(location.search);
        const urlTab  = (params.get('tab') || '').trim().toLowerCase();
        if (urlTab && activateTab(urlTab, tabs, panes)) {
            // Activated from URL -- done.
        } else if (opts.defaultTab) {
            activateTab(opts.defaultTab, tabs, panes);
        }
        // else: HTML's pre-set "active" class wins, untouched.

        // Delegated on the bar rather than bound per tab, so a tab added later
        // (Needs attention) switches like the rest. A disabled tab never fires.
        document.querySelectorAll('.ed-tabs').forEach(function (bar) {
            bar.addEventListener('click', function (e) {
                const tab = e.target.closest ? e.target.closest('.ed-tab') : null;
                if (!tab || !bar.contains(tab) || tab.disabled) return;
                ED.showTab(tab.dataset.tab);
            });
        });
    };

    ED.renderEmpty = function (paneEl, message) {
        paneEl.innerHTML = '<div class="ed-empty">' + ED.escapeHtml(message) + '</div>';
    };
    ED.renderError = function (paneEl, message) {
        paneEl.innerHTML = '<div class="ed-error">' + ED.escapeHtml(message) + '</div>';
    };

    // ===== Lookup cache =====================================================
    // Many of the modal forms need lookup lists (address types, phone types,
    // etc.). Cache them once per page load so the dialog opens fast.

    const _lookupCache = {};

    ED.getLookup = async function (name) {
        if (_lookupCache[name]) return _lookupCache[name];
        const r = await window.LWAuth.apiGet('/api/lookups/' + name);
        if (!r || !r.ok) return [];
        _lookupCache[name] = r.data || [];
        return _lookupCache[name];
    };

    /** Add-in-place (Story 3550): find-or-create one type by name in a simple
     *  type list (vendor-types, address-types, ...) and keep the page's cached
     *  copy of that list current, so the next dialog offers it too.
     *  Resolves {id, name}; rejects with a readable Error. */
    ED.addLookupValue = async function (slug, name) {
        const r = await window.LWAuth.apiPost('/api/lookups/' + encodeURIComponent(slug), { name: name });
        if (!r || !r.ok || !r.data) throw new Error(window.LWAuth.errorText(r, 'Could not add that.'));
        const item = { id: r.data.id, name: r.data.name };
        const cache = _lookupCache[slug];
        if (cache && !cache.some(function (o) { return String(o.id) === String(item.id); })) cache.push(item);
        return item;
    };

    /** Add-in-place for a hand-built record form's type <select> (Story 3550):
     *  gives it the green "+ <label>" row and, when tapped, makes the typed
     *  type (or asks for a name) and selects it. errEl receives any refusal.
     *  Call after the select is in the DOM. */
    ED.wireSelectAdd = function (selEl, slug, label, errEl) {
        if (!selEl) return;
        selEl.setAttribute('data-ps-add', label);
        selEl.addEventListener('ps-add', async function (e) {
            if (errEl) errEl.textContent = '';
            try {
                const typed = (e.detail && e.detail.text) || '';
                const create = function (n) { return ED.addLookupValue(slug, n); };
                const item = window.PicassoQuickAdd
                    ? await window.PicassoQuickAdd.simple({ typed: typed, title: label, create: create })
                    : (typed ? await create(typed) : null);
                if (item && window.PicassoSelect) window.PicassoSelect.addOption(selEl, item.id, item.name);
            } catch (err) {
                if (errEl) errEl.textContent = (err && err.message) || 'Could not add that.';
            }
        });
    };

    // A field's addNew is either a lookup slug ('vendor-types') -- the plain
    // "type a name, it exists" case -- or { label, create(typed) -> {id,name} }
    // for anything that needs more (create may open a PicassoQuickAdd form and
    // resolve null when the user backs out).
    function addSpecOf(f) {
        if (!f.addNew) return null;
        const noun = String(f.addNoun || f.label || 'one').toLowerCase();
        if (typeof f.addNew === 'string') {
            const slug = f.addNew;
            return { label: f.addLabel || ('Add ' + noun), nameOnly: true,
                     create: function (typed) { return ED.addLookupValue(slug, typed); } };
        }
        return { label: f.addNew.label || f.addLabel || ('Add ' + noun), nameOnly: false, create: f.addNew.create };
    }
    function pushOption(options, item) {
        if (options && !options.some(function (o) { return String(o.id) === String(item.id); })) options.push(item);
    }

    // ===== Modal infrastructure ============================================
    // One modal element per page, lazily injected. Configured per call so
    // every entity type can reuse the same dialog with different fields.

    let _modalEl = null;
    let _modalDirty = false;
    function ensureModal() {
        if (_modalEl) return _modalEl;
        const bg = document.createElement('div');
        bg.className = 'ed-modal-bg';
        bg.innerHTML =
            '<div class="ed-modal" role="dialog" aria-modal="true">'
          +     '<h2 id="lwedTitle" data-testid="ed-modal-title"></h2>'
          +     '<div class="lwed-toolbar" id="lwedToolbar" style="display:none"></div>'
          +     '<form id="lwedForm" autocomplete="off"></form>'
          +     '<div class="err" id="lwedErr"></div>'
          +     '<div class="actions">'
          +         '<button type="button" class="ed-btn primary" id="lwedSave" data-testid="ed-modal-save-btn">Save</button>'
          +         '<button type="button" class="ed-btn"         id="lwedCancel" data-testid="ed-modal-cancel-btn">Cancel</button>'
          +     '</div>'
          + '</div>';
        document.body.appendChild(bg);
        // Backdrop tap and Cancel both route through requestClose so a stray tap
        // outside the sheet never silently discards a half-typed form. In a real
        // browser this used to nuke every edit. (Story 2226)
        //
        // The tap has to START on the backdrop, not merely end there. A typeahead
        // option is chosen on mousedown, which closes the dropdown; the click that
        // follows the same press then lands on whatever is under the pointer --
        // and a dropdown hanging past the sheet's edge leaves the backdrop there.
        // The modal dismissed itself the moment a note was picked (Story 3476).
        document.getElementById('lwedCancel').addEventListener('click', requestClose);
        // Any user edit marks the form dirty. Delegated + attached once: the
        // form element is reused across openModal calls, so we must not re-bind
        // per open. Programmatic value sets (initial render, phone formatting)
        // do not dispatch 'input', so they never falsely mark dirty.
        const formEl = bg.querySelector('#lwedForm');
        formEl.addEventListener('input',  function () { _modalDirty = true; });
        formEl.addEventListener('change', function () { _modalDirty = true; });
        // Escape closes it like Cancel (house rule: a modal closes by its own
        // buttons or Escape). Capture phase, so an open dropdown inside the
        // form is seen before its own Escape shuts it: that Escape only closes
        // the dropdown, and the next one closes the dialog. A message or a
        // quick-add sheet opened on top of it takes its own Escape.
        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape' || !bg.classList.contains('open')) return;
            if (bg.querySelector('.picker-dropdown.open, .is-open')) return;
            if (document.querySelector('.pdlg-backdrop, .pqa-bg')) return;
            e.stopPropagation();
            requestClose();
        }, true);
        _modalEl = bg;
        return bg;
    }

    function closeModal() {
        if (_modalEl) _modalEl.classList.remove('open');
        _modalDirty = false;
    }
    ED.closeModal = closeModal;

    // Close attempt that respects unsaved edits. Backdrop tap and Cancel call
    // this; a successful Save calls closeModal() directly (nothing to lose).
    function requestClose() {
        if (_modalDirty && !confirm('Discard your unsaved changes?')) return;
        closeModal();
    }

    /** Open a generic edit dialog. config = { title, fields, onSave }
     *  fields is an array of { key, label, type, options, required, value }
     *  type in 'text' | 'textarea' | 'select' | 'date' | 'number' | 'tel' | 'password'
     *  options (for select) is an array of { id, name } (from getLookup).
     *  onSave receives an object { key: stringValue } and must return a promise.
     *  If the promise resolves, modal closes; rejects (with a message) shows an error.
     *  focusKey (optional) names the field that takes focus on open instead of the first one;
     *  it gets the caret at the end, never select-all (Tony, 2026-09-30: Edit on a note opened
     *  the Type picker's list and left no easy way into the note). */
    ED.openModal = async function (config) {
        const bg = ensureModal();
        document.getElementById('lwedTitle').textContent = config.title || '';
        const form = document.getElementById('lwedForm');
        form.innerHTML = config.fields.map(function (f) {
            const id = 'lwed_' + f.key;
            const val = f.value == null ? '' : f.value;
            if (f.type === 'select') {
                const list = f.sortable ? sortOptions(f.options, getLinkSort()) : (f.options || []);
                const opts = list.map(function (o) {
                    const sel = String(o.id) === String(val) ? ' selected' : '';
                    return '<option value="' + ED.escapeHtml(o.id) + '"' + sel + '>'
                         + ED.escapeHtml(o.name) + '</option>';
                }).join('');
                const addSel = addSpecOf(f);
                return '<div class="field"><label for="' + id + '">' + ED.escapeHtml(f.label) + '</label>'
                     + (f.sortable ? sortToggleHtml(f.key) : '')
                     + '<select id="' + id + '" name="' + f.key + '" data-testid="ed-select-' + ED.escapeHtml(f.key) + '"'
                     + (addSel ? ' data-ps-add="' + ED.escapeHtml(addSel.label) + '"' : '') + '>'
                     + (f.required ? '' : '<option value="">-- none --</option>')
                     + opts + '</select></div>';
            }
            if (f.type === 'textarea') {
                return '<div class="field"><label for="' + id + '">' + ED.escapeHtml(f.label) + '</label>'
                     // data-autosize="full": grow to show ALL the text, on open
                     // and while typing alike -- the modal scrolls, not the box,
                     // and the Save row is sticky (Story 3490).
                     + '<textarea id="' + id + '" name="' + f.key + '" data-autosize="full">'
                     + ED.escapeHtml(val) + '</textarea></div>';
            }
            if (f.type === 'typeahead') {
                // Picker for any field with too many options to fit a dropdown
                // comfortably (>12 -- see leonardo-programming skill). Two inputs:
                // visible text (the typed name) + hidden id (the selected ID).
                // Initial visible text = the name of the currently-selected ID, if any.
                const sel = (f.options || []).find(function (o) { return String(o.id) === String(val); });
                const initText = sel ? sel.name : '';
                return '<div class="field"><label for="' + id + '">' + ED.escapeHtml(f.label) + '</label>'
                     + (f.sortable ? sortToggleHtml(f.key) : '')
                     + '<div class="picker-wrap" data-picker-for="' + f.key + '">'
                     +     '<input id="' + id + '" class="picker-input" type="text" autocomplete="off" '
                     +         'data-testid="ed-picker-' + ED.escapeHtml(f.key) + '" '
                     +         'placeholder="Type to search\u2026" value="' + ED.escapeHtml(initText) + '">'
                     +     '<input type="hidden" name="' + f.key + '" value="' + ED.escapeHtml(val) + '">'
                     +     '<div class="picker-dropdown"></div>'
                     + '</div></div>';
            }
            if (f.type === 'picker') {
                // Single-value find-or-create pick-list -- the standard
                // PicassoPicker. Unlike 'typeahead' (which resolves to an id and
                // rejects anything not on the list) the value here is the NAME,
                // so typing a brand-new one is how a new option gets created.
                return '<div class="field"><label for="' + id + '">' + ED.escapeHtml(f.label) + '</label>'
                     + '<div class="picker-wrap" data-freepicker-for="' + f.key + '">'
                     +     '<input id="' + id + '" name="' + f.key + '" class="picker-input" type="text" '
                     +         'autocomplete="off" value="' + ED.escapeHtml(val) + '">'
                     +     '<div class="picker-dropdown"></div>'
                     + '</div></div>';
            }
            if (f.type === 'tags') {
                // Multi-value pick-list -- the PicassoTags chip picker, never a
                // comma-separated text box and never <select multiple>. Typing a
                // value the list doesn't have keeps it as a new tag; the save
                // proc find-or-creates it.
                return '<div class="field"><label for="' + id + '">' + ED.escapeHtml(f.label) + '</label>'
                     + '<div class="pt-wrap" data-tags-for="' + f.key + '">'
                     +     '<div class="pt-box">'
                     +         '<span class="pt-chips"></span>'
                     +         '<input id="' + id + '" class="pt-input" type="text" autocomplete="off">'
                     +     '</div>'
                     +     '<div class="pt-dropdown"></div>'
                     + '</div></div>';
            }
            if (f.type === 'checkbox') {
                const checked = val ? ' checked' : '';
                return '<div class="checkbox-field">'
                     + '<input id="' + id + '" type="checkbox" name="' + f.key + '"' + checked + '>'
                     + '<label for="' + id + '">' + ED.escapeHtml(f.label) + '</label></div>';
            }
            const t = f.type || 'text';
            return '<div class="field"><label for="' + id + '">' + ED.escapeHtml(f.label) + '</label>'
                 + '<input id="' + id + '" type="' + t + '" name="' + f.key + '" value="'
                 + ED.escapeHtml(val) + '"' + (f.maxlength ? ' maxlength="' + f.maxlength + '"' : '') + '></div>';
        }).join('');

        // Wire any typeahead pickers in the form.
        for (const f of config.fields) {
            if (f.type !== 'typeahead') continue;
            const wrap     = form.querySelector('[data-picker-for="' + f.key + '"]');
            if (!wrap) continue;
            const textEl   = wrap.querySelector('.picker-input');
            const hiddenEl = wrap.querySelector('input[type="hidden"]');
            const dropEl   = wrap.querySelector('.picker-dropdown');
            const sortEl   = f.sortable ? form.querySelector('[data-sort-for="' + f.key + '"]') : null;
            if (!f.options && !f.search) f.options = [];
            wirePicker(textEl, hiddenEl, dropEl, f.options || [], f.search, sortEl, addSpecOf(f), f.onPick);
        }

        // Add-in-place on a short fixed list (Story 3550): the Picasso dropdown
        // shows a green "+ Add" row and fires ps-add; create the value and drop
        // it straight into this select, still inside the open dialog.
        for (const f of config.fields) {
            if (f.type !== 'select') continue;
            const add = addSpecOf(f);
            if (!add) continue;
            const selEl = form.querySelector('select[name="' + f.key + '"]');
            if (!selEl) continue;
            const errBox = document.getElementById('lwedErr');
            selEl.addEventListener('ps-add', async function (e) {
                errBox.textContent = '';
                const typed = (e.detail && e.detail.text) || '';
                try {
                    // A name-only list takes the typed name at once (or asks for
                    // one); a richer create opens its own form with it prefilled.
                    const item = (add.nameOnly && window.PicassoQuickAdd)
                        ? await window.PicassoQuickAdd.simple({ typed: typed, title: add.label,
                                                                label: f.label, create: add.create })
                        : ((typed || !add.nameOnly) ? await add.create(typed) : null);
                    if (!item) return;
                    pushOption(f.options, item);
                    if (window.PicassoSelect && window.PicassoSelect.addOption) {
                        window.PicassoSelect.addOption(selEl, item.id, item.name);
                    }
                } catch (err) {
                    errBox.textContent = (err && err.message) || 'Could not add that.';
                }
            });
        }

        // A sortable select (the PicassoSelect-enhanced List / Task List /
        // Checklist pickers) rebuilds its options in the new order and keeps
        // whatever was already picked.
        for (const f of config.fields) {
            if (f.type !== 'select' || !f.sortable) continue;
            const sortEl = form.querySelector('[data-sort-for="' + f.key + '"]');
            const selEl  = form.querySelector('select[name="' + f.key + '"]');
            if (!sortEl || !selEl) continue;
            sortEl.addEventListener('click', function (e) {
                const b = e.target.closest('.lwed-sort-btn');
                if (!b) return;
                const mode = b.dataset.sort;
                setLinkSort(mode);
                markSortButtons(sortEl, mode);
                const keep = selEl.value;
                const blank = f.required ? '' : '<option value="">-- none --</option>';
                selEl.innerHTML = blank + sortOptions(f.options, mode).map(function (o) {
                    return '<option value="' + ED.escapeHtml(o.id) + '">' + ED.escapeHtml(o.name) + '</option>';
                }).join('');
                selEl.value = keep;
                if (window.PicassoSelect && window.PicassoSelect.refresh) window.PicassoSelect.refresh(selEl);
            });
        }

        // Wire any find-or-create single pickers. Value stays in the named
        // input, so the generic save path below reads it with no special case.
        for (const f of config.fields) {
            if (f.type !== 'picker') continue;
            const wrap = form.querySelector('[data-freepicker-for="' + f.key + '"]');
            if (!wrap || !window.PicassoPicker) continue;
            window.PicassoPicker.attach({
                input:      wrap.querySelector('.picker-input'),
                dropdown:   wrap.querySelector('.picker-dropdown'),
                getItems:   function () { return f.options || []; },
                allowNew:   f.allowNew !== false,
                clearLabel: f.required ? null : '(none)'
            });
        }

        // Wire any multi-select chip pickers. The instances are kept by field
        // key because the chips are the value -- there is no input to read.
        const tagPickers = {};
        for (const f of config.fields) {
            if (f.type !== 'tags') continue;
            const wrap = form.querySelector('[data-tags-for="' + f.key + '"]');
            if (!wrap || !window.PicassoTags) continue;
            tagPickers[f.key] = window.PicassoTags.attach({
                input:       wrap.querySelector('.pt-input'),
                chips:       wrap.querySelector('.pt-chips'),
                dropdown:    wrap.querySelector('.pt-dropdown'),
                getOptions:  function () { return f.options || []; },
                values:      f.value || [],
                placeholder: f.placeholder || 'Pick one or type a new one'
            });
        }

        // Optional toolbar above the form. config.toolbar is an array of
        // { label, title, testid, onClick(formApi) }. formApi lets a toolbar
        // action read/write named fields -- used by the address clipboard paste
        // buttons to fill street/city/state/zip at once. (Story 2226)
        const formApi = {
            setValue: function (key, val) {
                const el = form.querySelector('[name="' + key + '"]');
                if (!el) return;
                el.value = (val == null ? '' : val);
                // Dispatch input so dirty-tracking + textarea autosize react.
                el.dispatchEvent(new Event('input', { bubbles: true }));
            },
            getValue: function (key) {
                const el = form.querySelector('[name="' + key + '"]');
                return el ? el.value : '';
            }
        };
        const toolbarEl = document.getElementById('lwedToolbar');
        toolbarEl.innerHTML = '';
        const toolbar = config.toolbar || [];
        toolbar.forEach(function (btn) {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'ed-btn lwed-toolbar-btn';
            b.textContent = btn.label || '';
            if (btn.title)  b.title = btn.title;
            if (btn.testid) b.setAttribute('data-testid', btn.testid);
            b.addEventListener('click', function () { btn.onClick(formApi); });
            toolbarEl.appendChild(b);
        });
        toolbarEl.style.display = toolbar.length ? 'flex' : 'none';

        const errEl = document.getElementById('lwedErr');
        errEl.textContent = '';
        const saveBtn = document.getElementById('lwedSave');
        // The modal element is reused across opens, and the custom source
        // pickers (Add Files, Reading Nook) hide Save because they are
        // tap-a-row dialogs. Cancelling one of those left Save hidden for
        // every later modal on the page. Restore it on every generic open.
        saveBtn.style.display = '';
        saveBtn.onclick = async function () {
            errEl.textContent = '';
            const data = {};
            for (const f of config.fields) {
                // Chip pickers carry their value in the chips, not in an input.
                if (f.type === 'tags') {
                    const picker = tagPickers[f.key];
                    data[f.key] = picker ? picker.getValues() : (f.value || []);
                    continue;
                }
                // For checkbox, read .checked. For typeahead, value comes from
                // the hidden input (querySelector by name finds the hidden
                // one because the visible text input has no name attribute).
                const el = form.querySelector('[name="' + f.key + '"]');
                let v = (f.type === 'checkbox') ? !!el.checked : el.value;
                // Forgiving typeahead: a user who types a name in full and taps
                // Save WITHOUT tapping a dropdown option leaves the hidden id
                // blank (input clears it on every keystroke). Very common on a
                // phone. If the visible text matches exactly one option name
                // (case-insensitive), resolve it to that option's id so the
                // save succeeds instead of erroring "Pick X first". (Story 2219)
                if (f.type === 'typeahead' && (v === '' || v == null)) {
                    const wrap  = form.querySelector('[data-picker-for="' + f.key + '"]');
                    const pick  = wrap ? wrap.querySelector('.picker-input') : null;
                    const typed = pick ? (pick.value || '').trim() : '';
                    if (typed) {
                        // A server-searched field matches against the last list
                        // the user saw, which is the only list there is.
                        v = resolveTyped(f.options || (pick && pick._pickerItems) || [], typed);
                        // Still nothing, and the field is mandatory: say so here
                        // rather than letting the caller's onSave throw a vague
                        // "Pick X first". Silence is what made the Link Note
                        // failure look like the button did nothing at all.
                        if (!v && f.required) {
                            errEl.textContent = 'Nothing in the list matches "' + typed
                                              + '" on its own -- tap the one you want'
                                              + (f.addNew ? ', or tap + Add to create it.' : '.');
                            return;
                        }
                    }
                }
                // Phone fields persist digits only (BIGINT column); strip any
                // parens / hyphens / dots the user typed or that we formatted
                // into the field on blur. (Story 2226)
                if (f.type === 'tel') v = ED.stripPhone(v);
                data[f.key] = v;
            }
            saveBtn.disabled = true;
            try {
                // onSave may return a function -- a continuation to run once
                // this dialog is closed. Multi-step flows (pick a file, then
                // name it) need it: opening the next modal from inside onSave
                // gets it slammed shut by the closeModal() below, because both
                // steps share one modal element.
                const next = await config.onSave(data);
                closeModal();
                if (typeof next === 'function') next();
            } catch (err) {
                errEl.textContent = err && err.message ? err.message : 'Save failed';
            } finally {
                saveBtn.disabled = false;
            }
        };
        bg.classList.add('open');

        // Auto-select-all on focus -- globalized for every modal so re-entering
        // a field clears whatever was typed before by just typing over the
        // default. Applies to inputs and textareas (NOT selects; that breaks
        // keyboard navigation). Phone numerics + dates included.
        // Tony's #8 from the entity-files plan, generalized.
        const SEL_ALL_FOCUS = 'input[type="text"], input[type="date"], input[type="number"], '
                            + 'input[type="tel"], input[type="password"], input[type="email"], '
                            + 'input[type="search"], input:not([type]), textarea';
        form.querySelectorAll(SEL_ALL_FOCUS).forEach(function (el) {
            el.addEventListener('focus', function () {
                // setTimeout so the browser's default cursor-placement runs
                // first, then we select. Bypasses iOS Safari's quirk where
                // immediate .select() inside focus gets cancelled.
                setTimeout(function () { try { el.select(); } catch (e) {} }, 0);
            });
        });

        // Phone fields: show a formatted number on open and reformat when the
        // field loses focus, e.g. (314) 747-5747. Values are set directly (no
        // 'input' dispatch) so formatting never marks the form dirty. Digits
        // are stripped back out at save time. (Story 2226)
        form.querySelectorAll('input[type="tel"]').forEach(function (el) {
            el.value = ED.formatPhone(el.value);
            el.addEventListener('blur', function () { el.value = ED.formatPhone(el.value); });
        });

        const focusEl = config.focusKey ? form.querySelector('[name="' + config.focusKey + '"]') : null;
        if (focusEl) {
            focusEl.focus();
            // Runs after the select-all-on-focus timeout above, so the caret lands at the end
            // and one keystroke cannot replace the whole note.
            setTimeout(function () {
                try {
                    const n = focusEl.value.length;
                    focusEl.setSelectionRange(n, n);
                    focusEl.scrollTop = focusEl.scrollHeight;
                } catch (e) {}
            }, 0);
        } else {
            const first = form.querySelector('input,select,textarea');
            if (first) first.focus();
        }
        // Size any prefilled textareas to fit their content. The delegated
        // input listener in picasso-utils.js handles keystroke growth; this
        // covers the initial render.
        if (window.Picasso && window.Picasso.autosizeTextarea) {
            form.querySelectorAll('textarea').forEach(window.Picasso.autosizeTextarea);
        }

        // Form starts clean: everything above was programmatic. Any subsequent
        // keystroke flips _modalDirty via the delegated listener in ensureModal.
        _modalDirty = false;
    };

    /** Resolve what the user TYPED into a typeahead back to an option id, for
     *  the very common case where the id never got set because the option was
     *  never successfully tapped. Widens in three steps -- exact name, then
     *  starts-with, then contains -- and only accepts a step that lands on
     *  exactly ONE option, so an ambiguous fragment stays unresolved instead of
     *  linking the wrong record. Typing "anthem" and pressing Save now works
     *  even when the dropdown tap never registered. Returns '' when unresolved.
     */
    function resolveTyped(options, typed) {
        const lc = String(typed).trim().toLowerCase();
        if (!lc) return '';
        function only(pred) {
            const hits = options.filter(function (o) {
                return pred(String(o.name == null ? '' : o.name).trim().toLowerCase());
            });
            return hits.length === 1 ? String(hits[0].id) : '';
        }
        return only(function (n) { return n === lc; })
            || only(function (n) { return n.indexOf(lc) === 0; })
            || only(function (n) { return n.indexOf(lc) >= 0; });
    }

    // ===== Link-existing sort: Newest first | A to Z ==========================
    // A field marked sortable:true (every "Link an existing X" picker) offers
    // both orders. Newest means most recently ADDED: CreatedDate for the
    // server-searched pickers (entity-browse sort=created), and the record's
    // own id -- an identity, so it only grows -- for a list the page already
    // holds. The choice is remembered per device; Newest is the default,
    // because the record you are linking is usually the one you just added.
    const LINK_SORT_KEY = 'lwed.linkSort';
    const LINK_SORT_NEWEST = 'newest';
    const LINK_SORT_NAME   = 'name';

    function getLinkSort() {
        try { return localStorage.getItem(LINK_SORT_KEY) === LINK_SORT_NAME ? LINK_SORT_NAME : LINK_SORT_NEWEST; }
        catch (e) { return LINK_SORT_NEWEST; }
    }
    function setLinkSort(mode) {
        try { localStorage.setItem(LINK_SORT_KEY, mode); } catch (e) { /* private window */ }
    }
    function sortOptions(options, mode) {
        const copy = (options || []).slice();
        if (mode === LINK_SORT_NEWEST) {
            copy.sort(function (a, b) { return (Number(b.id) || 0) - (Number(a.id) || 0); });
        } else {
            copy.sort(function (a, b) {
                return String(a.name || '').localeCompare(String(b.name || ''), undefined, { sensitivity: 'base' });
            });
        }
        return copy;
    }
    function sortToggleHtml(key) {
        const mode = getLinkSort();
        function btn(m, label) {
            const on = mode === m;
            return '<button type="button" class="lwed-sort-btn' + (on ? ' active' : '') + '" data-sort="' + m
                 + '" aria-pressed="' + on + '" data-testid="lwed-sort-' + m + '-btn">' + label + '</button>';
        }
        return '<div class="lwed-sort" data-sort-for="' + ED.escapeHtml(key) + '">'
             + btn(LINK_SORT_NEWEST, 'Newest first') + btn(LINK_SORT_NAME, 'A to Z') + '</div>';
    }
    function markSortButtons(sortEl, mode) {
        sortEl.querySelectorAll('.lwed-sort-btn').forEach(function (b) {
            const on = b.dataset.sort === mode;
            b.classList.toggle('active', on);
            b.setAttribute('aria-pressed', String(on));
        });
    }

    // ===== Typeahead picker -- shared by every field with type:'typeahead' ====
    // Mirrors the receipts.html quick-entry picker. Use whenever a select
    // would have >12 options (per the leonardo-programming skill).
    //
    // Two sources. `items` is a fixed array the picker filters itself -- right
    // for a lookup of a few dozen rows. `search` is an async function
    // (needle -> [{id, name}]) the picker calls once the user STOPS typing, so
    // the SERVER does the matching and returns only the top rows -- required
    // whenever the pool is a whole table (notes, contacts, vendors, entities,
    // Leonardo Files: Story 3476). With `search`, `items` is ignored and nothing
    // is filtered here: the server already matched on fields the label may not show.
    //
    // sortEl (optional) is the Newest first | A to Z toggle: it reorders a fixed
    // list here, or is handed to `search` as its second argument.

    // How long a keystroke waits for the next one before the server is asked.
    // Tony's rule (2026-09-26): nothing is sent while he is still typing, or the
    // picker reloads on every letter. Opening the picker and re-sorting it are
    // not typing, so those ask almost at once -- the short wait only folds the
    // modal's double focus on open into one request.
    const SEARCH_TYPING_PAUSE_MS = 1000;
    const SEARCH_OPEN_PAUSE_MS   = 150;

    // onPick (optional): called with the chosen {id, name, ...} row -- e.g. the Gmail
    // link dialog fills its Email Name from the picked email's subject.
    function wirePicker(textEl, hiddenEl, dropEl, items, search, sortEl, add, onPick) {
        let sortMode = getLinkSort();
        let adding = false;
        const base = items || [];
        let live = search ? [] : (sortEl ? sortOptions(base, sortMode) : base);
        let searchSeq = 0, searchTimer = null;
        // What resolveTyped should match against at Save time: the last list
        // the user actually saw.
        textEl._pickerItems = live;

        function filter(needle) {
            if (search) return live.slice(0, 50);
            if (!needle || !needle.trim()) return live.slice(0, 40);
            const lc = needle.toLowerCase();
            // Digits the user typed, stripped of formatting. Lets a raw
            // "3145551234" match a "(314) 555-1234" label (and the reverse),
            // so phone linking no longer forces the user to type the parens.
            const nd = needle.replace(/\D/g, '');
            return live.filter(function (a) {
                const name = String(a.name || '');
                if (name.toLowerCase().indexOf(lc) >= 0) return true;
                if (nd && name.replace(/\D/g, '').indexOf(nd) >= 0) return true;
                return false;
            }).slice(0, 40);
        }

        // Ask the server once the typing pauses (every keystroke restarts the
        // wait); drop any answer that arrives after a newer question was asked.
        function scheduleSearch(needle, delayMs) {
            clearTimeout(searchTimer);
            ++searchSeq;   // anything still in flight is now stale
            searchTimer = setTimeout(async function () {
                const seq = ++searchSeq;
                let got = [];
                try { got = await search(needle, sortEl ? sortMode : undefined) || []; } catch (e) { got = []; }
                if (seq !== searchSeq) return;
                live = got;
                textEl._pickerItems = live;
                paintDropdown(needle);
            }, delayMs);
        }

        function highlight(str, needle) {
            if (!needle || !needle.trim()) return ED.escapeHtml(str);
            const idx = String(str).toLowerCase().indexOf(needle.toLowerCase());
            if (idx < 0) return ED.escapeHtml(str);
            return ED.escapeHtml(str.substring(0, idx))
                 + '<mark>' + ED.escapeHtml(str.substring(idx, idx + needle.length)) + '</mark>'
                 + ED.escapeHtml(str.substring(idx + needle.length));
        }

        // What a keystroke does: paint from the local list, or let the server
        // answer once the typing pauses. While it waits, the last answer stays on
        // screen (no flicker per letter); "Searching" shows only when there is
        // nothing to show yet. `immediate` = not typing (open, re-sort): ask now.
        function renderDropdown(needle, immediate) {
            if (search) {
                if (!dropEl.classList.contains('open') || !live.length) {
                    dropEl.innerHTML = '<div class="picker-option no-match">Searching...</div>';
                    dropEl.classList.add('open');
                }
                positionDropdownFixed();
                scheduleSearch(needle, immediate ? SEARCH_OPEN_PAUSE_MS : SEARCH_TYPING_PAUSE_MS);
                return;
            }
            paintDropdown(needle);
        }

        // Add-in-place (Story 3550): a green "+ Add "typed"" row whenever the
        // typed text is not already an option -- the same row PicassoPicker
        // shows everywhere else in Leonardo.
        function addRowHtml(needle) {
            if (!add) return '';
            const t = String(needle || '').trim();
            if (!t) return '';
            const lc = t.toLowerCase();
            const exact = (search ? live : base).some(function (a) {
                return String(a.name || '').trim().toLowerCase() === lc;
            });
            if (exact) return '';
            return '<div class="picker-option picker-add" data-add="1" data-name="' + ED.escapeHtml(t) + '"'
                 + ' data-testid="ed-picker-add">+ ' + ED.escapeHtml(add.label) + ' "' + ED.escapeHtml(t) + '"</div>';
        }

        function paintDropdown(needle) {
            const matches = filter(needle);
            const addHtml = addRowHtml(needle);
            if (!matches.length) {
                dropEl.innerHTML = addHtml
                    || (add ? '<div class="picker-option no-match">Type a new name to add it</div>'
                            : '<div class="picker-option no-match">No matches</div>');
            } else {
                dropEl.innerHTML = matches.map(function (a) {
                    return '<div class="picker-option" data-id="' + ED.escapeHtml(a.id)
                         + '" data-name="' + ED.escapeHtml(a.name) + '">'
                         + highlight(a.name, needle) + '</div>';
                }).join('') + addHtml;
            }
            // Position the dropdown as a viewport-fixed element so it escapes
            // the .ed-modal's overflow:auto clip (the modal would otherwise
            // limit how tall the dropdown can grow before getting cut off).
            // Recomputed every render so the position tracks input changes.
            positionDropdownFixed();
            dropEl.classList.add('open');
        }
        function positionDropdownFixed() {
            var r = textEl.getBoundingClientRect();
            var vh = window.innerHeight || document.documentElement.clientHeight || 800;
            // Available space below the input; we cap the dropdown to ~60% of
            // viewport or whatever fits below the input, whichever is smaller.
            var spaceBelow = Math.max(160, vh - r.bottom - 16);
            var maxH       = Math.min(spaceBelow, Math.round(vh * 0.6));
            dropEl.style.position  = 'fixed';
            dropEl.style.top       = (r.bottom) + 'px';
            dropEl.style.left      = (r.left)   + 'px';
            dropEl.style.width     = (r.width)  + 'px';
            dropEl.style.maxHeight = maxH + 'px';
            dropEl.style.right     = 'auto';
            // z-index above any modal (modal bg is ~1000, modal sheet's children
            // come from the same stacking context; 5000 keeps us on top).
            dropEl.style.zIndex    = '5000';
        }
        // Reposition on scroll/resize while the dropdown is open so it tracks
        // the input. Throttled implicitly because the user is unlikely to
        // scroll faster than 60fps and these calls are O(1).
        function repositionIfOpen() {
            if (dropEl.classList.contains('open')) positionDropdownFixed();
        }
        window.addEventListener('resize', repositionIfOpen);
        // Listen on capture so scrolling any ancestor (modal body etc.) is caught.
        window.addEventListener('scroll', repositionIfOpen, true);

        function selectItem(id, name) {
            hiddenEl.value = id;
            textEl.value   = name;
            dropEl.classList.remove('open');
            if (typeof onPick === 'function') {
                const row = live.find(function (a) { return String(a.id) === String(id); });
                onPick(row || { id: id, name: name });
            }
        }

        function close() { dropEl.classList.remove('open'); }

        // Create the typed value, add it to this picker's list (and the caller's
        // array, which is usually the page's cached lookup), and select it. The
        // row can be hit by mousedown, touchend AND click for one tap, so a
        // latch keeps it to a single create.
        async function addTyped(name) {
            if (!add || adding) return;
            adding = true;
            dropEl.innerHTML = '<div class="picker-option no-match">Adding "' + ED.escapeHtml(name) + '"...</div>';
            try {
                const item = await add.create(name);
                if (!item) { close(); return; }
                pushOption(base, item);
                if (!search && live !== base) pushOption(live, item);
                textEl._pickerItems = live;
                selectItem(item.id, item.name);
                // A pick marks the dialog dirty the same way a keystroke does.
                hiddenEl.dispatchEvent(new Event('change', { bubbles: true }));
            } catch (err) {
                dropEl.innerHTML = '<div class="picker-option no-match">'
                                 + ED.escapeHtml((err && err.message) || 'Could not add that.') + '</div>';
                dropEl.classList.add('open');
            } finally {
                adding = false;
            }
        }
        // One place that answers "the user chose this row".
        function chooseRow(opt) {
            if (!opt) return false;
            if (opt.dataset.add) { addTyped(opt.dataset.name); return true; }
            if (opt.dataset.id) { selectItem(opt.dataset.id, opt.dataset.name); return true; }
            return false;
        }

        if (sortEl) sortEl.addEventListener('click', function (e) {
            const b = e.target.closest('.lwed-sort-btn');
            if (!b || b.dataset.sort === sortMode) return;
            sortMode = b.dataset.sort;
            setLinkSort(sortMode);
            markSortButtons(sortEl, sortMode);
            if (!search) { live = sortOptions(base, sortMode); textEl._pickerItems = live; }
            // Show the reordered list straight away.
            textEl.focus();
            renderDropdown(textEl.value, true);
        });

        textEl.addEventListener('input', function () { hiddenEl.value = ''; renderDropdown(this.value); });
        textEl.addEventListener('focus', function () { this.select(); renderDropdown(this.value, true); });
        textEl.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { close(); return; }
            const opts = dropEl.querySelectorAll('.picker-option:not(.no-match)');
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                if (!opts.length) return;
                const cur = dropEl.querySelector('.picker-option.active');
                let idx = cur ? Array.from(opts).indexOf(cur) : -1;
                if (cur) cur.classList.remove('active');
                idx = e.key === 'ArrowDown' ? Math.min(idx + 1, opts.length - 1) : Math.max(idx - 1, 0);
                opts[idx].classList.add('active');
                opts[idx].scrollIntoView({ block: 'nearest' });
                e.preventDefault();
            }
            if (e.key === 'Enter' || e.key === 'Tab') {
                const active = dropEl.querySelector('.picker-option.active');
                if (active && (active.dataset.id || active.dataset.add)) {
                    chooseRow(active);
                    if (e.key === 'Enter') e.preventDefault();
                } else {
                    close();
                }
            }
        });
        dropEl.addEventListener('mousedown', function (e) {
            if (chooseRow(e.target.closest('.picker-option'))) e.preventDefault();
        });
        // Touch devices need their own selection path. A tap inside the dropdown
        // does NOT reliably produce a mousedown: if the finger drifts a couple of
        // pixels, or the list was scrolled a moment earlier, the browser calls the
        // gesture a scroll and suppresses the synthesized mouse events entirely.
        // The option then never gets selected, the hidden id stays empty, and Save
        // silently links nothing -- reported on the phone from Events tab ->
        // Link Note. Select on touchend, with a small movement threshold so a real
        // scroll still scrolls, and preventDefault so the browser does not replay a
        // synthetic click onto whatever now sits under the finger (the Save button
        // is directly below the list).
        let touchX = 0, touchY = 0, touchScrolled = false;
        dropEl.addEventListener('touchstart', function (e) {
            const t = e.changedTouches[0];
            touchX = t.clientX; touchY = t.clientY; touchScrolled = false;
        }, { passive: true });
        dropEl.addEventListener('touchmove', function (e) {
            const t = e.changedTouches[0];
            if (Math.abs(t.clientX - touchX) > 10 || Math.abs(t.clientY - touchY) > 10) touchScrolled = true;
        }, { passive: true });
        dropEl.addEventListener('touchend', function (e) {
            if (touchScrolled) return;
            if (chooseRow(e.target.closest('.picker-option'))) e.preventDefault();
        }, { passive: false });
        // Third selection path: a plain click. mousedown and touchend between
        // them still miss on some devices (a pen, an assistive pointer, or a
        // touch the browser reclassifies after the fact), and when both miss
        // the hidden id stays empty and Save links nothing -- the Link Note
        // failure Tony hit twice. click is the one event that always arrives,
        // so it is the backstop; re-selecting the same option is a no-op.
        dropEl.addEventListener('click', function (e) {
            const opt = e.target.closest('.picker-option');
            // The add row was already acted on by mousedown / touchend; only an
            // existing option is safe to re-select here.
            if (opt && opt.dataset.id) selectItem(opt.dataset.id, opt.dataset.name);
            else if (opt && opt.dataset.add && !adding && !hiddenEl.value) chooseRow(opt);
        });
        function closeIfOutside(e) {
            if (!textEl.contains(e.target) && !dropEl.contains(e.target)) close();
        }
        document.addEventListener('mousedown', closeIfOutside);
        // Same reason as above: on a phone there is no mousedown to close on, so
        // the dropdown used to hang open over the rest of the form.
        document.addEventListener('touchstart', closeIfOutside, { passive: true });
    }

    // ===== Sub-list renderers (with per-card edit/delete + +Add) ============
    // Each takes (paneEl, rows, parentEntityId, refresh) and writes the HTML.
    // refresh is a callback that re-fetches+rerenders the pane after a save.

    function actionsBarHtml(addLabel, kind, extras) {
        // Tolerate the older 2-arg call (addLabel, extras) -- if the second
        // arg looks like markup, shift it into the extras slot.
        if (typeof kind === 'string' && /^</.test(kind)) { extras = kind; kind = null; }
        const tid = kind ? ' data-testid="entity-' + kind + '-add-btn"' : '';
        return '<div class="ed-pane-actions">'
             +     '<button type="button" class="ed-btn primary" data-act="add"' + tid + '>+ ' + ED.escapeHtml(addLabel) + '</button>'
             +     (extras || '')
             + '</div>';
    }

    // Module-level filter flags. Re-read at fetch time so the UI checkbox state
    // survives across pane refresh cycles (the rebuild blows away the DOM).
    let _addrShowInactive = false;

    function cardActionsHtml(kind, leadingHtml) {
        const ek  = kind ? 'entity-' + kind + '-' : '';
        const ed  = ek ? ' data-testid="' + ek + 'edit-btn"' : '';
        const dl  = ek ? ' data-testid="' + ek + 'del-btn"'  : '';
        return '<div class="ed-card-actions">'
             +     (leadingHtml || '')
             +     '<button type="button" class="ed-btn"        data-act="edit"' + ed + '>Edit</button>'
             +     '<button type="button" class="ed-btn danger" data-act="del"'  + dl + '>Delete</button>'
             + '</div>';
    }

    /** Returns the HTML attribute string `data-testid="entity-<kind>-row"`
     *  with a leading space; empty string when kind is falsy. Used to stamp
     *  child-list rows so Playwrite topics can target a specific sub-entity
     *  row by `[data-testid="entity-addr-row"]:first-of-type` etc. */
    function rowTid(kind) {
        return kind ? ' data-testid="entity-' + kind + '-row"' : '';
    }

    function wirePaneActions(paneEl, rows, handlers) {
        const addBtn = paneEl.querySelector('[data-act="add"]');
        if (addBtn) addBtn.addEventListener('click', function () { handlers.add(); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const editBtn = card.querySelector('[data-act="edit"]');
            const delBtn  = card.querySelector('[data-act="del"]');
            if (editBtn) editBtn.addEventListener('click', function () { handlers.edit(rows[idx]); });
            if (delBtn)  delBtn.addEventListener('click',  function () { handlers.del(rows[idx]); });
        });
    }

    // Panes that live in their own file (entity-communications.js) render the
    // same card furniture, so the builders are shared rather than copied.
    ED.actionsBarHtml  = actionsBarHtml;
    ED.rowTid          = rowTid;
    ED.wirePaneActions = wirePaneActions;

    // Map button on an address card. The universal Google Maps search URL is an
    // Android App Link, so on the phone it opens the Google Maps app -- the same
    // hand-off a tapped address in a text message makes. On a desktop it opens
    // maps in a new tab. Nothing to map -> no button.
    function mapsLinkHtml(lines) {
        const query = (lines || []).join(', ').trim();
        if (!query) return '';
        return '<a class="ed-btn" target="_blank" rel="noopener" data-testid="entity-addr-map-btn" '
             + 'href="https://www.google.com/maps/search/?api=1&amp;query=' + encodeURIComponent(query) + '">Map</a>';
    }

    // Copy button on an address card: puts the whole address on the clipboard
    // as one line ("123 Main St, Apt 4, Austin, TX 78701"). Nothing -> no button.
    function copyAddressBtnHtml(lines) {
        if (!lines || !lines.length) return '';
        return '<button type="button" class="ed-btn" data-act="copyAddr" data-testid="entity-addr-copy-btn">Copy</button>';
    }

    /** One-line form of an address: street, second street, city, state zip. */
    ED.addressOneLine = function (a) {
        const stateZip = [a.state, a.zipCode].filter(Boolean).join(' ');
        return [a.streetAddress, a.secondStreetAddress, a.city, stateZip]
            .map(function (p) { return (p || '').trim(); })
            .filter(Boolean).join(', ');
    };

    ED.copyAddress = async function (a) {
        const text = ED.addressOneLine(a || {});
        if (!text) return;
        try {
            await ED.writeClipboard(text);
            if (window.Picasso && Picasso.toast) Picasso.toast('Address copied');
        } catch (e) {
            alert('Could not copy the address: ' + ((e && e.message) || e || 'the browser refused clipboard access') + '.');
        }
    };

    /** A phone as (972) 690-8115 (plus " ext. 12") from the stored ten digits;
     *  anything that is not ten digits stays as entered. The phone card's Copy
     *  and the record printout share it. */
    ED.phoneOneLine = function (p) {
        const digits = String((p && p.phoneNumberInt) || '').replace(/\D/g, '');
        const num = digits.length === 10
            ? '(' + digits.slice(0, 3) + ') ' + digits.slice(3, 6) + '-' + digits.slice(6)
            : ((p && p.phoneNumberDisplay) || digits);
        return num + (p && p.extension && p.extension > 0 ? ' ext. ' + p.extension : '');
    };

    ED.copyPhone = async function (p) {
        const text = ED.phoneOneLine(p || {});
        if (!text) return;
        try {
            await ED.writeClipboard(text);
            if (window.Picasso && Picasso.toast) Picasso.toast('Phone number copied');
        } catch (e) {
            alert('Could not copy the phone number: ' + ((e && e.message) || e || 'the browser refused clipboard access') + '.');
        }
    };

    ED.renderAddresses = function (paneEl, rows, entityId, refresh) {
        const filterHtml = '<label style="margin-left:auto;display:flex;align-items:center;gap:6px;font-size:12px;color:#555;">'
                         +     '<input type="checkbox" id="addrShowInactive"' + (_addrShowInactive ? ' checked' : '') + '> Show inactive'
                         + '</label>';
        const cardsHtml = (rows && rows.length) ? rows.map(function (a) {
            const lines = [];
            if (a.streetAddress)       lines.push(a.streetAddress);
            if (a.secondStreetAddress) lines.push(a.secondStreetAddress);
            const city = [a.city, a.state].filter(Boolean).join(', ');
            const cityZip = [city, a.zipCode].filter(Boolean).join(' ');
            if (cityZip) lines.push(cityZip);
            return '<div class="ed-card"' + rowTid('addr') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(a.addressType || 'Address') + '</span>'
                +         '<span class="name">' + ED.escapeHtml(a.streetAddress || '(no street)') + '</span>'
                +         '<span class="dates">' + ED.escapeHtml(ED.formatDateRange(a.effectiveDate, a.endDate)) + '</span>'
                +     '</div>'
                +     '<div class="body">' + lines.map(function (l) { return '<div class="row">' + ED.escapeHtml(l) + '</div>'; }).join('') + '</div>'
                +     (a.notes ? '<div class="notes">' + ED.escapeHtml(a.notes) + '</div>' : '')
                +     cardActionsHtml('addr', ED.pinButton('address', a.entityAddressXrefId, a.streetAddress || '(no street)',
                                          [a.addressType, ED.addressOneLine(a)].filter(Boolean).join(' | '))
                                      + mapsLinkHtml(lines) + copyAddressBtnHtml(lines))
                + '</div>';
        }).join('') : '<div class="ed-empty">No addresses on file.</div>';
        const linkExtra = '<button type="button" class="ed-btn" data-act="linkExisting" '
                        + 'data-testid="entity-addr-link-btn">+ Link Address</button>';
        paneEl.innerHTML = actionsBarHtml('Add Address', 'addr', linkExtra + filterHtml) + cardsHtml;
        const chk = paneEl.querySelector('#addrShowInactive');
        if (chk) chk.addEventListener('change', function () {
            _addrShowInactive = chk.checked;
            refresh();
        });
        wirePaneActions(paneEl, rows || [], {
            add:  function ()   { ED.editAddress(entityId, null, refresh); },
            edit: function (a)  { ED.editAddress(entityId, a, refresh); },
            del:  function (a)  { ED.deleteAddress(entityId, a, refresh); }
        });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const cpBtn = card.querySelector('[data-act="copyAddr"]');
            if (cpBtn) cpBtn.addEventListener('click', function () { ED.copyAddress((rows || [])[idx]); });
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkAddress(entityId, refresh); });
    };

    /** Attach an existing address to this entity. Mirrors linkPhone: pick from
     *  a typeahead of addresses not yet linked here, then POST an xref-only
     *  link (the shared address record is never modified). */
    ED.linkAddress = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/addresses/available');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load addresses.')); return; }
        const items = (r.data || []).map(function (a) {
            const where = [a.city, a.state].filter(Boolean).join(', ');
            const label = (a.streetAddress || '(no street)')
                        + (where ? ' -- ' + where : '')
                        + (a.zipCode ? ' ' + a.zipCode : '')
                        + (a.addressType ? ' (' + a.addressType + ')' : '');
            return { id: a.addressId, name: label.trim() };
        });
        if (!items.length) { alert('No other addresses are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing address',
            fields: [
                { key: 'addressId', sortable: true, label: 'Address', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.addressId, 10) || 0;
                if (!id) throw new Error('Pick an address first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/addresses/link/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // The Phones tab is ORGANIZED like Web, Files and Notes -- phone types as colored,
    // ordered categories, grouping, sorting, search, Important, Arrange -- by
    // contacts/entity-phones.js. The loader hands it the first page of /phones/filed
    // ({rows, groups, ...}); the plain card list below is the fallback for a page that
    // has not loaded it.
    ED.renderPhones = function (paneEl, data, entityId, refresh) {
        if (window.EntityPhones && window.FileKit && data && !Array.isArray(data)) {
            window.EntityPhones.render(paneEl, data, entityId, refresh);
            return;
        }
        renderPhonesPlain(paneEl, Array.isArray(data) ? data : ((data && data.rows) || []), entityId, refresh);
    };

    function renderPhonesPlain(paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (p) {
            const tel = p.phoneNumberInt ? ('tel:+1' + p.phoneNumberInt) : '';
            const display = p.phoneNumberDisplay || '';
            return '<div class="ed-card"' + rowTid('phone') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(p.phoneType || 'Phone') + '</span>'
                +         '<span class="name">' + ED.escapeHtml(p.phoneNumberName || '(no description)') + '</span>'
                +         '<span class="dates">' + ED.escapeHtml(ED.formatDateRange(p.effectiveDate, p.endDate)) + '</span>'
                +     '</div>'
                +     '<div class="body">'
                +         '<div class="row">' + (tel ? '<a href="' + tel + '">' + ED.escapeHtml(display) + '</a>' : ED.escapeHtml(display))
                +         (p.extension && p.extension > 0 ? ' ext. ' + ED.escapeHtml(p.extension) : '')
                +         '</div>'
                +     '</div>'
                +     (p.notes ? '<div class="notes">' + ED.escapeHtml(p.notes) + '</div>' : '')
                +     cardActionsHtml('phone', ED.pinButton('phone', p.entityPhoneXrefId, display || '(no number)',
                                           [p.phoneType, p.phoneNumberName].filter(Boolean).join(' | '))
                                      + (p.phoneNumberInt || display
                                            ? '<button type="button" class="ed-btn" data-act="copyPhone" data-testid="entity-phone-copy-btn">Copy</button>'
                                            : ''))
                + '</div>';
        }).join('') : '<div class="ed-empty">No phone numbers on file.</div>';
        const linkExtra = '<button type="button" class="ed-btn" data-act="linkExisting" '
                        + 'data-testid="entity-phone-link-btn">+ Link Phone</button>';
        paneEl.innerHTML = actionsBarHtml('Add Phone', 'phone', linkExtra) + cardsHtml;
        wirePaneActions(paneEl, rows || [], {
            add:  function ()  { ED.editPhone(entityId, null, refresh); },
            edit: function (p) { ED.editPhone(entityId, p, refresh); },
            del:  function (p) { ED.deletePhone(entityId, p, refresh); }
        });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const cpBtn = card.querySelector('[data-act="copyPhone"]');
            if (cpBtn) cpBtn.addEventListener('click', function () { ED.copyPhone((rows || [])[idx]); });
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkPhone(entityId, refresh); });
    }

    /** Attach an existing phone number to this entity. Mirrors linkVendor /
     *  linkContact: pick from a typeahead of phones not yet linked here, then
     *  POST an xref-only link (the shared phone record is never modified). */
    ED.linkPhone = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/phones/available');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load phones.')); return; }
        const items = (r.data || []).map(function (p) {
            const label = (p.phoneNumberDisplay || '')
                        + (p.phoneNumberName ? ' -- ' + p.phoneNumberName : '')
                        + (p.phoneType ? ' (' + p.phoneType + ')' : '');
            return { id: p.phoneId, name: label.trim() };
        });
        if (!items.length) { alert('No other phones are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing phone',
            fields: [
                { key: 'phoneId', sortable: true, label: 'Phone', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.phoneId, 10) || 0;
                if (!id) throw new Error('Pick a phone first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/phones/link/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // Story 3525: the Web tab is ORGANIZED like Files and Notes -- website types as
    // coloured, ordered categories, grouping, sorting, search, Important -- by
    // contacts/entity-web.js. The loader hands it the first page of /web-addresses/filed
    // ({rows, groups, ...}); the plain card list below is the fallback for a page that
    // has not loaded it.
    ED.renderWebAddresses = function (paneEl, data, entityId, refresh) {
        if (window.EntityWeb && window.FileKit && data && !Array.isArray(data)) {
            window.EntityWeb.render(paneEl, data, entityId, refresh);
            return;
        }
        renderWebAddressesPlain(paneEl, Array.isArray(data) ? data : ((data && data.rows) || []), entityId, refresh);
    };

    function renderWebAddressesPlain(paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (w) {
            const url = w.webAddress || '';
            const href = url.match(/^https?:\/\//i) ? url : ('https://' + url);
            const badge = w.webSiteType || w.webAddressType || 'Web';
            const actions = '<div class="ed-card-actions">'
                          +     ED.pinButton('web', w.entityWebAddressXRefId, w.webAddressName || url || '(no address)', url, '')
                          +     '<button type="button" class="ed-btn" data-act="manageLogins" data-testid="entity-web-manage-logins-btn">Manage Logins</button>'
                          +     '<button type="button" class="ed-btn" data-act="copyPass" data-testid="entity-web-copy-pass-btn">Copy Password</button>'
                          +     '<button type="button" class="ed-btn" data-act="edit" data-testid="entity-web-edit-btn">Edit</button>'
                          +     '<button type="button" class="ed-btn danger" data-act="del" data-testid="entity-web-del-btn">Delete</button>'
                          + '</div>';
            return '<div class="ed-card"' + rowTid('web') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(badge) + '</span>'
                +         '<span class="name">' + ED.escapeHtml(w.webAddressName || url) + '</span>'
                +     '</div>'
                +     '<div class="body">'
                +         (url ? '<div class="row"><a href="' + ED.escapeHtml(href) + '" target="_blank" rel="noopener">' + ED.escapeHtml(url) + '</a></div>' : '')
                +     '</div>'
                +     (w.notes ? '<div class="notes">' + ED.escapeHtml(w.notes) + '</div>' : '')
                +     actions
                + '</div>';
        }).join('') : '<div class="ed-empty">No web addresses on file.</div>';
        const linkExtra = '<button type="button" class="ed-btn" data-act="linkExisting" '
                        + 'data-testid="entity-web-link-btn">+ Link Web Address</button>';
        paneEl.innerHTML = actionsBarHtml('Add Web Address', 'web', linkExtra) + cardsHtml;
        wirePaneActions(paneEl, rows || [], {
            add:  function ()  { ED.editWebAddress(entityId, null, refresh); },
            edit: function (w) { ED.editWebAddress(entityId, w, refresh); },
            del:  function (w) { ED.deleteWebAddress(entityId, w, refresh); }
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkWebAddress(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            var w = (rows || [])[idx];
            var cpBtn = card.querySelector('[data-act="copyPass"]');
            if (cpBtn) cpBtn.addEventListener('click', function () {
                ED.copyWebAddressPassword(w);
            });
            var mlBtn = card.querySelector('[data-act="manageLogins"]');
            if (mlBtn) mlBtn.addEventListener('click', function () {
                ED.openLoginPanel(w);
            });
        });
    }

    /** Attach an existing web address to this entity. Mirrors linkPhone /
     *  linkAddress: pick from a typeahead of web addresses not yet linked here,
     *  then POST an xref-only link (the shared record is never modified). */
    ED.linkWebAddress = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/web-addresses/available');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load web addresses.')); return; }
        const items = (r.data || []).map(function (w) {
            const label = (w.webAddress || '(no URL)')
                        + (w.webAddressName ? ' -- ' + w.webAddressName : '')
                        + (w.webAddressType ? ' (' + w.webAddressType + ')' : '');
            return { id: w.webAddressId, name: label.trim() };
        });
        if (!items.length) { alert('No other web addresses are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing web address',
            fields: [
                { key: 'webAddressId', sortable: true, label: 'Web Address', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.webAddressId, 10) || 0;
                if (!id) throw new Error('Pick a web address first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/web-addresses/link/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    ED.copyWebAddressPassword = async function (w) {
        try {
            if (!w || !w.webAddressId) {
                if (window.Picasso && Picasso.toast) Picasso.toast('No web address selected.');
                return;
            }
            const r = await window.LWAuth.apiGet('/api/web-addresses/' + w.webAddressId + '/logins');
            const list = (r && r.data) ? r.data : [];
            if (!list.length) {
                if (window.Picasso && Picasso.toast) Picasso.toast('No login configured for this web address.');
                return;
            }
            list.sort(function (a, b) {
                return (a.loginName || '').localeCompare(b.loginName || '');
            });
            const first = list[0];
            const pass  = first.password || '';
            if (!pass) {
                if (window.Picasso && Picasso.toast) {
                    Picasso.toast(first.loginName
                        ? 'Login "' + first.loginName + '" has no password set.'
                        : 'Login has no password set.');
                }
                return;
            }
            await ED.writeClipboard(pass);
            const msg = (list.length > 1)
                ? 'Password copied for "' + (first.loginName || '') + '" (one of ' + list.length + ' logins).'
                : 'Password copied' + (first.loginName ? ' for ' + first.loginName : '') + '.';
            if (window.Picasso && Picasso.toast) Picasso.toast(msg);
        } catch (e) {
            if (window.Picasso && Picasso.toast) Picasso.toast('Copy password failed.');
        }
    };

    // ===== Login panel for a web address =====================================
    // Mirrors the desktop "Setup Login" button: opens a panel showing logins
    // linked to this web address's own entity, with add / edit / delete.

    var _loginPanelEl = null;

    function ensureLoginPanel() {
        if (_loginPanelEl) return _loginPanelEl;
        var bg = document.createElement('div');
        bg.className = 'ed-login-panel-bg';
        bg.innerHTML =
            '<div class="ed-login-panel" role="dialog" aria-modal="true">'
          +     '<div class="ed-login-panel-head">'
          +         '<h2 id="lwedLoginPanelTitle"></h2>'
          +         '<button type="button" class="ed-btn" id="lwedLoginPanelClose" data-testid="login-panel-close-btn">Close</button>'
          +     '</div>'
          +     '<div id="lwedLoginPanelBody"></div>'
          + '</div>';
        document.body.appendChild(bg);
        document.getElementById('lwedLoginPanelClose').addEventListener('click', closeLoginPanel);
        _loginPanelEl = bg;
        return bg;
    }

    // Called once when the panel closes, so a caller showing the same logins
    // elsewhere (the Logins tab's From web addresses band) can re-read them.
    var _loginPanelOnClose = null;

    function closeLoginPanel() {
        if (_loginPanelEl) _loginPanelEl.classList.remove('open');
        var fn = _loginPanelOnClose;
        _loginPanelOnClose = null;
        if (fn) fn();
    }

    ED.openLoginPanel = async function (w, onClose) {
        if (!w || !w.webAddressEntityId) {
            if (window.Picasso && Picasso.toast) Picasso.toast('Save the web address first.');
            return;
        }
        var waEntityId = w.webAddressEntityId;
        var title = w.webAddressName || w.webAddress || 'Web Address';
        var bg = ensureLoginPanel();
        _loginPanelOnClose = typeof onClose === 'function' ? onClose : null;
        document.getElementById('lwedLoginPanelTitle').textContent = 'Logins \u2014 ' + title;
        bg.classList.add('open');
        await refreshLoginPanel(waEntityId);
    };

    async function refreshLoginPanel(waEntityId) {
        var body = document.getElementById('lwedLoginPanelBody');
        body.innerHTML = '<div class="ed-loading">Loading logins...</div>';
        try {
            var r = await window.LWAuth.apiGet('/api/entities/' + waEntityId + '/logins');
            var rows = (r && r.ok && r.data) ? r.data : [];
            renderLoginPanelCards(body, rows, waEntityId);
        } catch (e) {
            body.innerHTML = '<div class="ed-error">Failed to load logins.</div>';
        }
    }

    function renderLoginPanelCards(body, rows, waEntityId) {
        var addBar = '<div class="ed-pane-actions">'
                   +     '<button type="button" class="ed-btn primary" data-act="addLogin" data-testid="login-panel-add-btn">+ Add Login</button>'
                   +     '<button type="button" class="ed-btn" data-act="linkLogin" data-testid="login-panel-link-btn">+ Link Existing Login</button>'
                   + '</div>';
        var cardsHtml;
        if (rows && rows.length) {
            cardsHtml = rows.map(function (l, i) {
                return '<div class="ed-card" data-testid="login-panel-row">'
                    +     '<div class="head">'
                    +         '<span class="badge">' + ED.escapeHtml(l.owningUser || 'Login') + '</span>'
                    +         '<span class="name">' + ED.escapeHtml(l.loginName || '(unnamed)') + '</span>'
                    +         '<span class="dates">' + ED.escapeHtml(ED.formatDateRange(l.effectiveDate, l.throughDate)) + '</span>'
                    +     '</div>'
                    +     '<div class="body">'
                    +         (l.loginUserName ? '<div class="row">User: <strong>' + ED.escapeHtml(l.loginUserName) + '</strong></div>' : '')
                    +         (l.password      ? '<div class="row">Pass: <span class="pw masked" data-pw-id="lp' + i + '" title="Tap to reveal">'
                                                + ED.escapeHtml(l.password) + '</span></div>' : '')
                    +     '</div>'
                    +     (l.notes ? '<div class="notes">' + ED.escapeHtml(l.notes) + '</div>' : '')
                    +     '<div class="ed-card-actions">'
                    +         copyLoginButtonHtml(l, 'login-panel-copy-btn')
                    +         '<button type="button" class="ed-btn" data-act="editLogin" data-testid="login-panel-edit-btn">Edit</button>'
                    +         '<button type="button" class="ed-btn danger" data-act="delLogin" data-testid="login-panel-del-btn">Delete</button>'
                    +     '</div>'
                    + '</div>';
            }).join('');
        } else {
            cardsHtml = '<div class="ed-empty">No logins linked to this web address.</div>';
        }
        body.innerHTML = addBar + cardsHtml;

        body.querySelectorAll('.pw').forEach(function (el) {
            el.addEventListener('click', function () { el.classList.toggle('masked'); });
        });

        wireCopyLoginButtons(body, rows || []);

        var addBtn = body.querySelector('[data-act="addLogin"]');
        if (addBtn) addBtn.addEventListener('click', function () {
            ED.editLogin(waEntityId, null, function () { refreshLoginPanel(waEntityId); });
        });

        var linkBtn = body.querySelector('[data-act="linkLogin"]');
        if (linkBtn) linkBtn.addEventListener('click', function () {
            ED.linkLogin(waEntityId, function () { refreshLoginPanel(waEntityId); });
        });

        body.querySelectorAll('.ed-card').forEach(function (card, idx) {
            var editBtn = card.querySelector('[data-act="editLogin"]');
            var delBtn  = card.querySelector('[data-act="delLogin"]');
            if (editBtn) editBtn.addEventListener('click', function () {
                ED.editLogin(waEntityId, rows[idx], function () { refreshLoginPanel(waEntityId); });
            });
            if (delBtn) delBtn.addEventListener('click', function () {
                ED.deleteLogin(waEntityId, rows[idx], function () { refreshLoginPanel(waEntityId); });
            });
        });
    }

    // Copy button on a login card: puts that login's password on the
    // clipboard. No password -> no button, so the button always does something.
    function copyLoginButtonHtml(l, testId) {
        if (!l || !l.password) return '';
        return '<button type="button" class="ed-btn" data-act="copyLoginPass" '
             + 'data-testid="' + testId + '">Copy</button>';
    }

    function wireCopyLoginButtons(containerEl, rows) {
        containerEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const btn = card.querySelector('[data-act="copyLoginPass"]');
            if (!btn) return;
            btn.addEventListener('click', async function () {
                const l = rows[idx] || {};
                try {
                    await ED.writeClipboard(l.password || '');
                    if (window.Picasso && Picasso.toast) {
                        Picasso.toast('Password copied' + (l.loginName ? ' for ' + l.loginName : '') + '.');
                    }
                } catch (e) {
                    if (window.Picasso && Picasso.toast) Picasso.toast('Copy password failed.');
                }
            });
        });
    }

    ED.writeClipboard = async function (text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(text);
            return;
        }
        const ta = document.createElement('textarea');
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); } finally { document.body.removeChild(ta); }
    };

    ED.loginCopyButtonHtml  = copyLoginButtonHtml;
    ED.wireLoginCopyButtons = wireCopyLoginButtons;

    ED.renderLogins = function (paneEl, rows, entityId, refresh) {
        // The banded tab (on this record + from web addresses, Story 5629) lives in
        // entity-logins.js; a page without it keeps the plain list below.
        if (window.EntityLogins && window.PicassoGroupState) {
            window.EntityLogins.render(paneEl, rows, entityId, refresh);
            return;
        }
        const cardsHtml = (rows && rows.length) ? rows.map(function (l, i) {
            return '<div class="ed-card"' + rowTid('login') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(l.owningUser || 'Login') + '</span>'
                +         '<span class="name">' + ED.escapeHtml(l.loginName || '(unnamed)') + '</span>'
                +         '<span class="dates">' + ED.escapeHtml(ED.formatDateRange(l.effectiveDate, l.throughDate)) + '</span>'
                +     '</div>'
                +     '<div class="body">'
                +         (l.loginUserName ? '<div class="row">User: <strong>' + ED.escapeHtml(l.loginUserName) + '</strong></div>' : '')
                +         (l.password      ? '<div class="row">Pass: <span class="pw masked" data-pw-id="' + i + '" title="Tap to reveal">'
                                            + ED.escapeHtml(l.password) + '</span></div>' : '')
                +     '</div>'
                +     (l.notes ? '<div class="notes">' + ED.escapeHtml(l.notes) + '</div>' : '')
                +     cardActionsHtml('login', ED.pinButton('login', l.entityLoginSetupXRefId, l.loginName || '(unnamed)',
                                           l.loginUserName ? 'User: ' + l.loginUserName : '')
                                         + copyLoginButtonHtml(l, 'entity-login-copy-btn'))
                + '</div>';
        }).join('') : '<div class="ed-empty">No logins set up.</div>';
        const linkExtra = '<button type="button" class="ed-btn" data-act="linkExisting" '
                        + 'data-testid="entity-login-link-btn">+ Link Existing Login</button>';
        paneEl.innerHTML = actionsBarHtml('Add Login', 'login', linkExtra) + cardsHtml;
        paneEl.querySelectorAll('.pw').forEach(function (el) {
            el.addEventListener('click', function () { el.classList.toggle('masked'); });
        });
        wireCopyLoginButtons(paneEl, rows || []);
        wirePaneActions(paneEl, rows || [], {
            add:  function ()  { ED.editLogin(entityId, null, refresh); },
            edit: function (l) { ED.editLogin(entityId, l, refresh); },
            del:  function (l) { ED.deleteLogin(entityId, l, refresh); }
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkLogin(entityId, refresh); });
    };

    /** Attach an existing login to this entity. Mirrors linkWebAddress /
     *  linkPhone: pick from a typeahead of logins not yet linked here, then POST
     *  an xref-only link. The shared LoginSetup record is never modified, so the
     *  other owners of that login see no change -- this is what keeps one
     *  password from being retyped once per web address. */
    ED.linkLogin = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/logins/available');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load logins.')); return; }
        const items = (r.data || []).map(function (l) {
            const label = (l.loginName || '(unnamed)')
                        + (l.loginUserName ? ' -- ' + l.loginUserName : '')
                        + (l.owningUser ? ' (' + l.owningUser + ')' : '');
            return { id: l.loginSetupId, name: label.trim() };
        });
        if (!items.length) { alert('No other logins are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing login',
            fields: [
                { key: 'loginSetupId', sortable: true, label: 'Login', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.loginSetupId, 10) || 0;
                if (!id) throw new Error('Pick a login first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/logins/link/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // ===== Sub-list edit/save handlers ======================================

    // Clipboard address paste -- mirrors Leonardo desktop's fAddress paste
    // buttons (Project Address/fAddress.cs). Vertical = one field per line;
    // Horizontal = a single line of whitespace-separated tokens. In both modes
    // commas act as separators, and the trailing tokens map to zip / state /
    // city, with everything before them folded into the street. (Story 2226)
    function parseClipboardAddress(text, mode) {
        // One line with commas ("7290 Virginia Parkway, McKinney, TX 75071"):
        // the commas say where the street and city end, so a two-word city
        // ("Fort Worth") or street stays whole. The last part holds state + zip.
        const oneLine = String(text || '').replace(/[\r\n]+/g, ' ').trim();
        if (mode === 'horizontal' && oneLine.indexOf(',') >= 0) {
            const parts = oneLine.split(',').map(function (t) { return t.trim(); })
                                 .filter(function (t) { return t.length > 0; });
            const last  = (parts.length ? parts[parts.length - 1] : '').split(/\s+/);
            const zip   = last.length > 1 ? last[last.length - 1] : '';
            const state = last.length > 1 ? last.slice(0, -1).join(' ') : (last[0] || '');
            return {
                zip:    zip,
                state:  state,
                city:   parts.length >= 2 ? parts[parts.length - 2] : '',
                street: parts.length >= 3 ? parts.slice(0, parts.length - 2).join(', ') : ''
            };
        }
        const cleaned = String(text || '').replace(/,/g, ' ');
        let tokens = (mode === 'horizontal')
            ? cleaned.split(/\s+/)
            : cleaned.split(/[\r\n]+/);
        tokens = tokens.map(function (t) { return t.trim(); })
                       .filter(function (t) { return t.length > 0; });
        const n = tokens.length;
        return {
            zip:    n >= 1 ? tokens[n - 1] : '',
            state:  n >= 2 ? tokens[n - 2] : '',
            city:   n >= 3 ? tokens[n - 3] : '',
            street: n >= 4 ? tokens.slice(0, n - 3).join(' ') : ''
        };
    }

    function fillAddress(formApi, text, mode) {
        const a = parseClipboardAddress(text, mode);
        formApi.setValue('streetAddress', a.street);
        formApi.setValue('city',         a.city);
        formApi.setValue('state',        a.state);
        formApi.setValue('zipCode',      a.zip);
    }

    // Phones (and some browsers) will not let a page READ the clipboard -- the
    // read is refused or needs a permission the user never saw. That is not an
    // empty clipboard, so instead of blaming it, open a box at the top of the
    // form: paste into it (long-press -> Paste) and the fields fill in.
    function showPasteBox(formApi, mode) {
        const form = document.getElementById('lwedForm');
        if (!form) return;
        let box = form.querySelector('[data-testid="address-paste-box"]');
        if (!box) {
            const wrap = document.createElement('div');
            wrap.className = 'field';
            wrap.innerHTML = '<label for="lwed_pasteBox">Paste the address here</label>'
                           + '<textarea id="lwed_pasteBox" data-testid="address-paste-box" rows="3"></textarea>';
            form.insertBefore(wrap, form.firstChild);
            box = wrap.querySelector('textarea');
        }
        box.dataset.mode = mode;
        if (!box._wired) {
            box._wired = true;
            box.addEventListener('input', function () {
                if (box.value.trim()) fillAddress(formApi, box.value, box.dataset.mode);
            });
        }
        box.focus();
    }

    ED.pasteAddress = async function (formApi, mode) {
        let text = '';
        try {
            if (navigator.clipboard && navigator.clipboard.readText) {
                text = await navigator.clipboard.readText();
            }
        } catch (e) { text = ''; }
        if (!text || !text.trim()) { showPasteBox(formApi, mode); return; }
        fillAddress(formApi, text, mode);
    };

    ED.editAddress = async function (entityId, row, refresh) {
        const types = await ED.getLookup('address-types');
        ED.openModal({
            title: row ? 'Edit Address' : 'Add Address',
            toolbar: [
                { label: 'Paste Vertical',   testid: 'address-paste-vertical',
                  title: 'Paste an address copied as separate lines (street, city, state, zip)',
                  onClick: function (api) { ED.pasteAddress(api, 'vertical'); } },
                { label: 'Paste Horizontal', testid: 'address-paste-horizontal',
                  title: 'Paste an address copied as a single line',
                  onClick: function (api) { ED.pasteAddress(api, 'horizontal'); } }
            ],
            fields: [
                { key: 'addressTypeId',       label: 'Type',                type: 'select', options: types, required: true,
                  addNew: 'address-types', addNoun: 'address type',
                  value: row ? row.addressTypeId : '' },
                { key: 'streetAddress',       label: 'Street',              type: 'text',     value: row ? row.streetAddress : '',       maxlength: 50 },
                { key: 'secondStreetAddress', label: 'Street 2',            type: 'text',     value: row ? row.secondStreetAddress : '', maxlength: 50 },
                { key: 'city',                label: 'City',                type: 'text',     value: row ? row.city : '',                maxlength: 20 },
                { key: 'state',               label: 'State (2 char)',      type: 'text',     value: row ? row.state : '',               maxlength: 2 },
                { key: 'zipCode',             label: 'Zip',                 type: 'text',     value: row ? row.zipCode : '',             maxlength: 12 },
                { key: 'effectiveDate',       label: 'Effective Date',      type: 'date',     value: row ? (row.effectiveDate || '') : ED.todayIso() },
                { key: 'endDate',             label: 'End Date',            type: 'date',     value: row ? (row.endDate       || '') : '' },
                { key: 'notes',               label: 'Notes',               type: 'textarea', value: row ? row.notes : '' }
            ],
            onSave: async function (data) {
                const body = {
                    addressId:           row ? row.addressId : 0,
                    addressTypeId:       parseInt(data.addressTypeId, 10) || 0,
                    streetAddress:       data.streetAddress,
                    secondStreetAddress: data.secondStreetAddress,
                    city:                data.city,
                    state:               data.state,
                    zipCode:             data.zipCode,
                    notes:               data.notes,
                    effectiveDate:       data.effectiveDate,
                    endDate:             data.endDate
                };
                if (!body.addressTypeId) throw new Error('Address Type is required');
                const url = row
                    ? '/api/entities/' + entityId + '/addresses/' + row.entityAddressXrefId
                    : '/api/entities/' + entityId + '/addresses';
                const r = await window.LWAuth.apiPost(url, body, row ? 'PUT' : 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Save failed'));
                refresh();
            }
        });
    };

    ED.deleteAddress = async function (entityId, row, refresh) {
        if (!confirm('Delete this address?')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/addresses/' + row.entityAddressXrefId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Delete failed')); return; }
        refresh();
    };

    ED.editPhone = async function (entityId, row, refresh) {
        const types = await ED.getLookup('phone-types');
        ED.openModal({
            title: row ? 'Edit Phone' : 'Add Phone',
            fields: [
                { key: 'phoneTypeId',     label: 'Type',          type: 'select', options: types, required: true,
                  addNew: 'phone-types', addNoun: 'phone type',
                  value: row ? row.phoneTypeId : '' },
                { key: 'phoneNumberName', label: 'Description',   type: 'text',   value: row ? row.phoneNumberName : ED.entityName(entityId), maxlength: 500 },
                { key: 'phoneNumberInt',  label: 'Phone Number',  type: 'tel',    value: row ? (row.phoneNumberDisplay || row.phoneNumberInt || '') : '' },
                { key: 'extension',       label: 'Extension',     type: 'number', value: row && row.extension ? row.extension : '' },
                { key: 'effectiveDate',   label: 'Effective Date',type: 'date',   value: row ? (row.effectiveDate || '') : ED.todayIso() },
                { key: 'endDate',         label: 'End Date',      type: 'date',   value: row ? (row.endDate       || '') : '' },
                { key: 'notes',           label: 'Notes',         type: 'textarea', value: row ? row.notes : '' }
            ],
            onSave: async function (data) {
                const body = {
                    phoneId:         row ? row.phoneId : 0,
                    phoneTypeId:     parseInt(data.phoneTypeId, 10) || 0,
                    phoneNumberName: data.phoneNumberName,
                    phoneNumberInt:  data.phoneNumberInt,
                    extension:       parseInt(data.extension || '0', 10) || 0,
                    notes:           data.notes,
                    effectiveDate:   data.effectiveDate,
                    endDate:         data.endDate
                };
                if (!body.phoneTypeId) throw new Error('Phone Type is required');
                const url = row
                    ? '/api/entities/' + entityId + '/phones/' + row.entityPhoneXrefId
                    : '/api/entities/' + entityId + '/phones';
                const r = await window.LWAuth.apiPost(url, body, row ? 'PUT' : 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Save failed'));
                // A type added in place here must show in the Phones tab's type chips.
                if (window.EntityPhones) window.EntityPhones.typeKit().invalidate();
                refresh();
            }
        });
    };

    ED.deletePhone = async function (entityId, row, refresh) {
        const what = [row.phoneNumberName, row.phoneNumberDisplay].filter(Boolean).join(' -- ') || 'this phone';
        if (!confirm('Delete Phone: ' + what + '?\n\nIt comes off this record, and the number itself is deleted when no other record uses it. This cannot be undone.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/phones/' + row.entityPhoneXrefId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Delete failed')); return; }
        refresh();
    };

    // What a NEW web address starts as (Story 3495). Nearly everything Tony
    // files is a company's website, so the two required dropdowns open on
    // Website / Business instead of blank. Ids from tblWebAddressType and
    // tblWebSiteType; an id the lookup no longer offers falls back to blank.
    const WEB_ADDRESS_TYPE_WEBSITE = 1;
    const WEB_SITE_TYPE_BUSINESS   = 8;

    function defaultIfOffered(options, id) {
        return (options || []).some(function (o) { return o.id === id; }) ? id : '';
    }

    ED.editWebAddress = async function (entityId, row, refresh) {
        // Story 3525: the website type is a find-or-create picker over the website types
        // (alphabetical) -- type a new one and it is made on save. Read fresh, so a type
        // added on the Web tab a moment ago is offered.
        const [waTypes, tr] = await Promise.all([
            ED.getLookup('web-address-types'),
            window.LWAuth.apiGet('/api/web-site-types')
        ]);
        const wsTypes = (tr && tr.ok && Array.isArray(tr.data)) ? tr.data.map(function (t) { return { id: t.id, name: t.name }; })
            .sort(function (a, b) { return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }); }) : [];
        const businessType = wsTypes.find(function (t) { return t.id === WEB_SITE_TYPE_BUSINESS; });
        ED.openModal({
            title: row ? 'Edit Web Address' : 'Add Web Address',
            fields: [
                { key: 'webAddressName',   label: 'Description',       type: 'text', maxlength: 500,
                  value: row ? row.webAddressName : ED.entityName(entityId) },
                { key: 'webAddress',       label: 'URL',               type: 'text', maxlength: 900,
                  value: row ? row.webAddress : '' },
                { key: 'webAddressTypeId', label: 'Address Type',      type: 'select', options: waTypes, required: true,
                  addNew: 'web-address-types', addNoun: 'web address type',
                  value: row ? row.webAddressTypeId : defaultIfOffered(waTypes, WEB_ADDRESS_TYPE_WEBSITE) },
                { key: 'webSiteTypeName',  label: 'Site Type',         type: 'picker', options: wsTypes, required: true,
                  value: row ? (row.webSiteType || '') : (businessType ? businessType.name : '') },
                { key: 'notes',            label: 'Notes',             type: 'textarea', value: row ? row.notes : '' }
            ],
            onSave: async function (data) {
                const typed = (data.webSiteTypeName || '').trim();
                const known = wsTypes.find(function (t) { return t.name.toLowerCase() === typed.toLowerCase(); });
                const body = {
                    webAddressId:     row ? row.webAddressId : 0,
                    webAddressName:   data.webAddressName,
                    webAddress:       data.webAddress,
                    webAddressTypeId: parseInt(data.webAddressTypeId, 10) || 0,
                    webSiteTypeId:    known ? known.id : 0,
                    webSiteTypeName:  known ? null : typed,
                    notes:            data.notes
                };
                if (!body.webAddress)        throw new Error('URL is required');
                if (!body.webAddressTypeId)  throw new Error('Address Type is required');
                if (!typed)                  throw new Error('Site Type is required');
                const url = row
                    ? '/api/entities/' + entityId + '/web-addresses/' + row.entityWebAddressXRefId
                    : '/api/entities/' + entityId + '/web-addresses';
                const r = await window.LWAuth.apiPost(url, body, row ? 'PUT' : 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Save failed'));
                if (!known && window.EntityWeb) window.EntityWeb.typeKit().invalidate();
                refresh();
            }
        });
    };

    ED.deleteWebAddress = async function (entityId, row, refresh) {
        const label = row.webAddressName && row.webAddressName !== row.webAddress
            ? row.webAddressName + ' (' + (row.webAddress || '') + ')' : (row.webAddress || 'this web address');
        if (!confirm('Delete the web address ' + label + ' from this record?\n\n'
                   + 'If no other record uses it, the address itself is deleted too. '
                   + 'This cannot be undone.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/web-addresses/' + row.entityWebAddressXRefId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Delete failed')); return; }
        refresh();
    };

    ED.editLogin = async function (entityId, row, refresh) {
        const users = await ED.getLookup('users');
        ED.openModal({
            title: row ? 'Edit Login' : 'Add Login',
            fields: [
                { key: 'loginName',     label: 'Description',    type: 'text', maxlength: 50, required: true,
                  value: row ? row.loginName : ED.entityName(entityId).substring(0, 50) },
                { key: 'loginUserName', label: 'Login Username', type: 'text', maxlength: 50,
                  value: row ? row.loginUserName : '' },
                { key: 'password',      label: 'Password',       type: 'text', maxlength: 50,
                  value: row ? row.password : '' },
                { key: 'userId',        label: 'Owning User',    type: 'select', options: users, required: true,
                  value: row ? row.userId : '' },
                { key: 'effectiveDate', label: 'Effective Date', type: 'date',
                  value: row ? (row.effectiveDate || '') : ED.todayIso() },
                { key: 'throughDate',   label: 'Through Date',   type: 'date',
                  value: row ? (row.throughDate   || '') : '' },
                { key: 'notes',         label: 'Notes',          type: 'textarea',
                  value: row ? row.notes : '' }
            ],
            onSave: async function (data) {
                const body = {
                    loginSetupId:  row ? row.loginSetupId : 0,
                    loginName:     data.loginName,
                    loginUserName: data.loginUserName,
                    password:      data.password,
                    userId:        parseInt(data.userId, 10) || 0,
                    notes:         data.notes,
                    effectiveDate: data.effectiveDate,
                    throughDate:   data.throughDate
                };
                if (!body.loginName) throw new Error('Description is required');
                if (!body.userId)    throw new Error('Owning User is required');
                const url = row
                    ? '/api/entities/' + entityId + '/logins/' + row.entityLoginSetupXRefId
                    : '/api/entities/' + entityId + '/logins';
                const r = await window.LWAuth.apiPost(url, body, row ? 'PUT' : 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Save failed'));
                refresh();
            }
        });
    };

    ED.deleteLogin = async function (entityId, row, refresh) {
        // A login can be shared by several records, so this unlinks it here; the
        // server deletes the login itself only when nothing else still uses it.
        const loginLabel = (row.loginName || '(unnamed)') + (row.loginUserName ? ' (' + row.loginUserName + ')' : '');
        if (!confirm('Remove Login: ' + loginLabel + ' from this record?\n\n'
                   + 'If nothing else uses it, the login itself is deleted. This cannot be undone.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/logins/' + row.entityLoginSetupXRefId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Delete failed')); return; }
        refresh();
    };

    // ===== Vendors xref tab (Vendors linked to a contact) ==================

    ED.renderVendors = function (paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (v) {
            return '<div class="ed-card" data-testid="entity-vendor-row">'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(v.vendorType || 'Vendor') + '</span>'
                +         '<a href="/vendors/vendor.html?id=' + encodeURIComponent(v.vendorId) + '" class="name">'
                +             ED.escapeHtml(v.vendorName || '(unnamed)') + '</a>'
                +     '</div>'
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('vendor', v.vendorId, v.vendorName || '(unnamed)', v.vendorType || '',
                                      '/vendors/vendor.html?id=' + encodeURIComponent(v.vendorId))
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-vendor-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No vendors linked to this entity.</div>';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-vendor-link-btn">+ Link Vendor</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-vendor-new-btn">+ New Vendor</button>'
                     + '</div>';
        paneEl.innerHTML = actions + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkVendor(entityId, refresh); });
        const newVendorBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newVendorBtn) newVendorBtn.addEventListener('click', function () { ED.createVendor(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkVendor(entityId, rows[idx], refresh); });
        });
    };

    // ----- Server-searched link pickers (Story 3476) --------------------------
    // The "+ Link Vendor / Contact / Entity" pickers used to download the whole
    // vendor, contact or user-defined-entity table to fill a typeahead. Each
    // keystroke now asks /api/entity-browse for the 50 best matches of ONE type,
    // the same paged read Entity Manager IV lists with. Entity type ids mirror
    // cEntityTypeEnumerator.ENUM_ENTITY_TYPE in the desktop.
    const ENTITY_TYPE_VENDOR       = 1;
    const ENTITY_TYPE_CONTACT      = 5;
    const ENTITY_TYPE_USER_DEFINED = 30;
    const ENTITY_TYPE_PAGE_LINK    = 220;   // Story 3485
    const ENTITY_TYPE_DASHBOARD    = 221;   // Story 3489 -- Record Dashboard (not 199, the GL-account one)

    function browseSearch(entityTypeId, labelOf) {
        // sort (from the Newest first | A to Z toggle) is 'newest' or 'name';
        // entity-browse calls newest 'created' (CreatedDate descending).
        return async function (needle, sort) {
            const r = await window.LWAuth.apiGet('/api/entity-browse?type=' + entityTypeId
                                                 + '&pageSize=50&q=' + encodeURIComponent(needle || '')
                                                 + (sort ? '&sort=' + (sort === LINK_SORT_NEWEST ? 'created' : 'name') : ''));
            if (!r || !r.ok || !r.data) return [];
            return (r.data.rows || []).map(function (row) {
                return { id: row.identityId, name: labelOf(row) };
            });
        };
    }

    ED.linkVendor = async function (entityId, refresh) {
        ED.openModal({
            title: 'Link an existing vendor',
            fields: [
                { key: 'vendorId', sortable: true, label: 'Vendor', type: 'typeahead', required: true, value: '',
                  search: browseSearch(ENTITY_TYPE_VENDOR, function (row) {
                      return row.displayName + (row.active ? '' : ' (inactive)');
                  }) }
            ],
            onSave: async function (data) {
                const id = parseInt(data.vendorId, 10) || 0;
                if (!id) throw new Error('Pick a vendor first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/vendors/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /**
     * Story 2414: create a vendor and link it to this record in one step, so a
     * vendor that does not exist yet no longer forces a trip to the Vendors page
     * and back. Same shape as ED.createUserEntity: create through the normal
     * /api/vendors endpoint, then attach the new id through the same xref call
     * that "+ Link Vendor" uses.
     */
    ED.createVendor = async function (entityId, refresh) {
        const types = await ED.getLookup('vendor-types') || [];
        ED.openModal({
            title: 'New vendor for this record',
            fields: [
                { key: 'vendorName',   label: 'Vendor name', type: 'text',      maxlength: 100, required: true, value: '' },
                { key: 'vendorTypeId', label: 'Type',        type: 'typeahead', options: types, required: true, value: '',
                  addNew: 'vendor-types', addNoun: 'vendor type' },
                { key: 'active',       label: 'Active',      type: 'checkbox',  value: true },
                { key: 'notes',        label: 'Notes',       type: 'textarea',  value: '' }
            ],
            onSave: async function (data) {
                const body = {
                    vendorName:   (data.vendorName || '').trim(),
                    vendorTypeId: parseInt(data.vendorTypeId, 10) || 0,
                    active:       !!data.active,
                    notes:        data.notes || ''
                };
                if (!body.vendorName)   throw new Error('Vendor name is required');
                if (!body.vendorTypeId) throw new Error('Type is required');
                const created = await window.LWAuth.apiPost('/api/vendors', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = parseInt(created.data && created.data.vendorId, 10) || 0;
                if (!newId) throw new Error('The vendor was saved but no id came back, so it was not linked.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/vendors/' + newId, null, 'POST');
                if (!link || !link.ok)
                    throw new Error(window.LWAuth.errorText(link, 'The vendor was created but the link failed.'));
                refresh();
            }
        });
    };

    ED.unlinkVendor = async function (entityId, row, refresh) {
        if (!confirm('Unlink vendor "' + (row.vendorName || '') + '" from this entity?\n\n'
                   + 'The vendor itself stays in Leonardo; only the link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost(
            '/api/entities/' + entityId + '/vendors/' + row.vendorId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Contacts xref tab (Contacts linked to a vendor) =================

    ED.renderContacts = function (paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (c) {
            return '<div class="ed-card" data-testid="entity-contact-row">'
                +     '<div class="head">'
                +         '<span class="badge">Contact</span>'
                +         '<a href="/contacts/contact.html?id=' + encodeURIComponent(c.contactId) + '" class="name">'
                +             ED.escapeHtml(c.fullName || '(unnamed)') + '</a>'
                +     '</div>'
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('contact', c.contactId, c.fullName || '(unnamed)', 'Contact',
                                      '/contacts/contact.html?id=' + encodeURIComponent(c.contactId))
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-contact-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No contacts linked to this entity.</div>';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-contact-link-btn">+ Link Contact</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-contact-new-btn">+ New Contact</button>'
                     + '</div>';
        paneEl.innerHTML = actions + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkContact(entityId, refresh); });
        const newContactBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newContactBtn) newContactBtn.addEventListener('click', function () { ED.createContact(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkContact(entityId, rows[idx], refresh); });
        });
    };

    ED.linkContact = async function (entityId, refresh) {
        ED.openModal({
            title: 'Link an existing contact',
            fields: [
                { key: 'contactId', sortable: true, label: 'Contact', type: 'typeahead', required: true, value: '',
                  search: browseSearch(ENTITY_TYPE_CONTACT, function (row) { return row.displayName; }) }
            ],
            onSave: async function (data) {
                const id = parseInt(data.contactId, 10) || 0;
                if (!id) throw new Error('Pick a contact first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/contacts/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /**
     * Story 2414: create a contact and link it to this record in one step. This is
     * the case Tony named -- previously a brand-new contact had to be made on the
     * Contacts page first and only then linked back here.
     */
    ED.createContact = function (entityId, refresh) {
        ED.openModal({
            title: 'New contact for this record',
            fields: [
                { key: 'firstName',  label: 'First name',  type: 'text', maxlength: 50, value: '' },
                { key: 'middleName', label: 'Middle name', type: 'text', maxlength: 50, value: '' },
                { key: 'lastName',   label: 'Last name',   type: 'text', maxlength: 50, required: true, value: '' },
                { key: 'notes',      label: 'Notes',       type: 'textarea', value: '' }
            ],
            onSave: async function (data) {
                const body = {
                    firstName:  (data.firstName || '').trim(),
                    middleName: (data.middleName || '').trim(),
                    lastName:   (data.lastName || '').trim(),
                    notes:     data.notes || ''
                };
                if (!body.lastName) throw new Error('Last name is required');
                const created = await window.LWAuth.apiPost('/api/contacts', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = parseInt(created.data && created.data.contactId, 10) || 0;
                if (!newId) throw new Error('The contact was saved but no id came back, so it was not linked.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/contacts/' + newId, null, 'POST');
                if (!link || !link.ok)
                    throw new Error(window.LWAuth.errorText(link, 'The contact was created but the link failed.'));
                refresh();
            }
        });
    };

    ED.unlinkContact = async function (entityId, row, refresh) {
        if (!confirm('Unlink contact "' + (row.fullName || '') + '" from this entity?\n\n'
                   + 'The contact itself stays in Leonardo; only the link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost(
            '/api/entities/' + entityId + '/contacts/' + row.contactId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== User-Defined entities xref tab ==================================
    // Mirrors the Vendors / Contacts xref tabs, plus a "+ New Entity" action:
    // it creates the user-defined entity through the normal /api/userentities
    // endpoint and then links it here in one step, so the user never has to
    // leave the record to attach something that does not exist yet.

    ED.renderUserEntities = function (paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (u) {
            return '<div class="ed-card" data-testid="entity-userentity-row">'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(u.userDefinedEntityType || 'Entity') + '</span>'
                +         '<a href="/userentities/userentity.html?id=' + encodeURIComponent(u.userDefinedEntityId) + '" class="name">'
                +             ED.escapeHtml(u.userDefinedEntityName || '(unnamed)') + '</a>'
                +     '</div>'
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('userentity', u.userDefinedEntityId, u.userDefinedEntityName || '(unnamed)',
                                      u.userDefinedEntityType || '',
                                      '/userentities/userentity.html?id=' + encodeURIComponent(u.userDefinedEntityId))
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-userentity-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No user-defined entities linked to this entity.</div>';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-userentity-link-btn">+ Link Entity</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-userentity-new-btn">+ New Entity</button>'
                     + '</div>';
        paneEl.innerHTML = actions + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkUserEntity(entityId, refresh); });
        const newBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newBtn) newBtn.addEventListener('click', function () { ED.createUserEntity(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkUserEntity(entityId, rows[idx], refresh); });
        });
    };

    ED.linkUserEntity = async function (entityId, refresh) {
        ED.openModal({
            title: 'Link an existing user-defined entity',
            fields: [
                { key: 'userDefinedEntityId', sortable: true, label: 'Entity', type: 'typeahead', required: true, value: '',
                  search: browseSearch(ENTITY_TYPE_USER_DEFINED, function (row) {
                      return row.displayName + ((row.kindLabel || row.typeLabel) ? ' (' + (row.kindLabel || row.typeLabel) + ')' : '');
                  }) }
            ],
            onSave: async function (data) {
                const id = parseInt(data.userDefinedEntityId, 10) || 0;
                if (!id) throw new Error('Pick an entity first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/userentities/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    ED.createUserEntity = async function (entityId, refresh) {
        const types = await ED.getLookup('user-defined-entity-types') || [];
        ED.openModal({
            title: 'New user-defined entity',
            fields: [
                { key: 'userDefinedEntityName',   label: 'Name',  type: 'text',      maxlength: 500, required: true, value: '' },
                { key: 'userDefinedEntityTypeId', label: 'Type',  type: 'typeahead', options: types, required: true, value: '',
                  addNew: 'user-defined-entity-types', addNoun: 'type' },
                { key: 'notes',                   label: 'Notes', type: 'textarea',  value: '' }
            ],
            onSave: async function (data) {
                const body = {
                    userDefinedEntityName:   (data.userDefinedEntityName || '').trim(),
                    userDefinedEntityTypeId: parseInt(data.userDefinedEntityTypeId, 10) || 0,
                    notes:                   data.notes || ''
                };
                if (!body.userDefinedEntityName)   throw new Error('Name is required');
                if (!body.userDefinedEntityTypeId) throw new Error('Type is required');
                const created = await window.LWAuth.apiPost('/api/userentities', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.userDefinedEntityId;
                if (!newId) throw new Error('The entity saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/userentities/' + newId, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    ED.unlinkUserEntity = async function (entityId, row, refresh) {
        if (!confirm('Unlink "' + (row.userDefinedEntityName || '') + '" from this entity?\n\n'
                   + 'The entity itself stays in Leonardo; only the link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost(
            '/api/entities/' + entityId + '/userentities/' + row.userDefinedEntityId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Page Links xref tab (Story 3485) ================================
    // A Page Link is a pinned LeonardoWeb page (entity type 220 -- the Links
    // view of Entity Manager IV). Attaching one here ties a whole page -- Debt
    // Resolution, a checklist, a report -- to THIS record, the way its files and
    // checklists are, instead of a pasted address on the Web tab (which should
    // not know Leonardo's own addresses). "+ Link Page" picks a link that
    // already exists; "+ New Page" creates one and attaches it in one step.

    ED.renderPageLinks = function (paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (p) {
            const url = p.url || '';
            return '<div class="ed-card" data-testid="entity-pagelink-row">'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(p.iconKey || 'Page') + '</span>'
                +         '<a href="' + ED.escapeHtml(url || '#') + '" class="name" data-testid="entity-pagelink-open">'
                +             ED.escapeHtml(p.title || '(untitled)') + '</a>'
                +     '</div>'
                +     '<div class="body">'
                +         (url ? '<div class="row"><a href="' + ED.escapeHtml(url) + '">' + ED.escapeHtml(url) + '</a></div>' : '')
                +     '</div>'
                +     (p.notes ? '<div class="notes">' + ED.escapeHtml(p.notes) + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('pagelink', p.pageLinkId, p.title || url || '(untitled)', url, url)
                +         '<a class="ed-btn" href="/entity-manager-iv/entity-manager-iv.html?type=links&id=' + encodeURIComponent(p.pageLinkId) + '" data-testid="entity-pagelink-record">Details</a>'
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-pagelink-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No pages linked to this record.</div>';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-pagelink-link-btn">+ Link Page</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-pagelink-new-btn">+ New Page</button>'
                     + '</div>';
        paneEl.innerHTML = actions + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkPageLink(entityId, refresh); });
        const newBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newBtn) newBtn.addEventListener('click', function () { ED.createPageLink(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkPageLink(entityId, rows[idx], refresh); });
        });
    };

    // Pick a page link that already exists (the Links view of Entity Manager IV,
    // searched on the server by title or address) and attach it.
    ED.linkPageLink = async function (entityId, refresh) {
        ED.openModal({
            title: 'Link an existing page',
            fields: [
                { key: 'pageLinkId', sortable: true, label: 'Page', type: 'typeahead', required: true, value: '',
                  search: browseSearch(ENTITY_TYPE_PAGE_LINK, function (row) {
                      return row.displayName + ((row.kindLabel || row.typeLabel) ? ' (' + (row.kindLabel || row.typeLabel) + ')' : '');
                  }) }
            ],
            onSave: async function (data) {
                const id = parseInt(data.pageLinkId, 10) || 0;
                if (!id) throw new Error('Pick a page first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/page-links/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // Create the page link through the normal /api/page-links endpoint, then
    // attach it -- so a page nobody has pinned yet is still one step away. A
    // full address pasted from the browser is trimmed to its path + query.
    ED.createPageLink = async function (entityId, refresh) {
        ED.openModal({
            title: 'New page link',
            fields: [
                { key: 'title', label: 'Title',        type: 'text',     maxlength: 200, required: true, value: '' },
                { key: 'url',   label: 'Page address', type: 'text',     maxlength: 800, required: true, value: '' },
                { key: 'notes', label: 'Notes',        type: 'textarea', value: '' }
            ],
            onSave: async function (data) {
                const body = {
                    title: (data.title || '').trim(),
                    url:   ED.pageAddressOf(data.url),
                    notes: data.notes || ''
                };
                if (!body.title) throw new Error('A link needs a title.');
                if (!body.url)   throw new Error('A link needs a page address.');
                const created = await window.LWAuth.apiPost('/api/page-links', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.pageLinkId;
                if (!newId) throw new Error('The page link saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/page-links/' + newId, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // "https://leonardo-web.com/matters/matter.html?id=5" -> "/matters/matter.html?id=5".
    // A relative address is kept as typed. Page links hold the address WITHIN
    // LeonardoWeb so a pin means the same page from any host.
    ED.pageAddressOf = function (raw) {
        const s = (raw || '').trim();
        if (!s) return '';
        const m = s.match(/^https?:\/\/[^\/]+(\/.*)?$/i);
        if (m) return m[1] || '/';
        return s.charAt(0) === '/' ? s : '/' + s;
    };

    ED.unlinkPageLink = async function (entityId, row, refresh) {
        if (!confirm('Unlink the page "' + (row.title || row.url || '') + '" from this record?\n\n'
                   + 'The page link itself stays on the Links view of Entity Manager IV; only its attachment to this record is removed.')) return;
        const r = await window.LWAuth.apiPost(
            '/api/entities/' + entityId + '/page-links/' + row.pageLinkId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Solutions xref tab (Story 3503) =================================
    // A Solution is a technical project (the Solutions program, Story 3493):
    // projects, items, a log, runbooks. Attaching one here ties that project to
    // THIS record -- the matter it serves, the vendor it is about -- and the
    // record's dashboard then carries the solution's printed report. The link
    // is house-only: it lives under /api/entities, which an outside agent on a
    // shared solution can never reach, so it never learns which record it is on.

    ED.solutionUrl = function (solutionId) { return '/solutions/solution.html?id=' + encodeURIComponent(solutionId); };

    // "2026-12-01" -> "12/01/2026" (always two-digit month and day).
    function solutionDate(iso) {
        const m = String(iso || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
        return m ? m[2] + '/' + m[3] + '/' + m[1] : '';
    }

    ED.renderSolutions = function (paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (s) {
            const url = ED.solutionUrl(s.solutionId);
            const total = s.itemCount || 0, closed = s.closedItemCount || 0;
            const progress = total > 0
                ? closed + ' of ' + total + ' items closed (' + Math.round(closed * 100 / total) + '%)'
                : 'No items yet';
            const due = solutionDate(s.targetDate);
            return '<div class="ed-card" data-testid="entity-solution-row">'
                +     '<div class="head">'
                +         '<span class="badge" style="background:' + ED.escapeHtml(s.colorHex || '#6B7280') + ';color:#fff">'
                +             ED.escapeHtml(s.statusName || 'Solution') + '</span>'
                +         '<a href="' + url + '" class="name" data-testid="entity-solution-open">'
                +             ED.escapeHtml(s.name || '(unnamed)') + '</a>'
                +         (s.archived ? ' <span class="ed-chip">Archived</span>' : '')
                +     '</div>'
                +     '<div class="body">'
                +         '<div class="row">' + ED.escapeHtml(progress) + (due ? ' | Target ' + due : '') + '</div>'
                +         (s.alias ? '<div class="row">Alias: ' + ED.escapeHtml(s.alias) + '</div>' : '')
                +     '</div>'
                +     (s.statusSummary ? '<div class="notes">' + ED.escapeHtml(s.statusSummary) + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('solution', s.solutionId, s.name || '(unnamed)', s.statusName || 'Solution', url)
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-solution-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No solutions linked to this record.</div>';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-solution-link-btn">+ Link Solution</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-solution-new-btn">+ New Solution</button>'
                     + '</div>';
        paneEl.innerHTML = actions + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkSolution(entityId, rows || [], refresh); });
        const newBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newBtn) newBtn.addEventListener('click', function () { ED.createSolution(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkSolution(entityId, rows[idx], refresh); });
        });
    };

    // Pick any solution (archived ones included) that is not already on this
    // record. The whole list is small -- it is Tony's projects, not a table of
    // records -- so it comes in one read and the picker filters as he types.
    ED.linkSolution = async function (entityId, linkedRows, refresh) {
        const r = await window.LWAuth.apiGet('/api/solutions?includeArchived=true');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load solutions.')); return; }
        const linked = new Set(linkedRows.map(function (s) { return s.solutionId; }));
        const items = (Array.isArray(r.data) ? r.data : [])
            .filter(function (s) { return !linked.has(s.id); })
            .map(function (s) {
                return { id: s.id, name: s.name + (s.archived ? ' (archived)' : ' (' + (s.statusName || '') + ')') };
            });
        if (!items.length) { alert('Every solution is already linked to this record. Use "+ New Solution" instead.'); return; }
        ED.openModal({
            title: 'Link an existing solution',
            fields: [
                { key: 'solutionId', sortable: true, label: 'Solution', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.solutionId, 10) || 0;
                if (!id) throw new Error('Pick a solution first.');
                const link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/solutions/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // Create the solution through the normal /api/solutions endpoint (status,
    // owner and the rest take their defaults; the Solutions page edits them),
    // then attach it, so a new project for this record is one step away.
    ED.createSolution = async function (entityId, refresh) {
        ED.openModal({
            title: 'New solution',
            fields: [
                { key: 'name',        label: 'Name',        type: 'text',     maxlength: 200, required: true, value: '' },
                { key: 'description', label: 'Description', type: 'textarea', value: '' }
            ],
            onSave: async function (data) {
                const body = { name: (data.name || '').trim(), description: data.description || '' };
                if (!body.name) throw new Error('A solution needs a name.');
                const created = await window.LWAuth.apiPost('/api/solutions', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.id;
                if (!newId) throw new Error('The solution saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/solutions/' + newId, null, 'POST');
                if (!link || !link.ok) throw new Error('The solution was saved but could not be linked. Use "+ Link Solution" to attach it.');
                refresh();
            }
        });
    };

    ED.unlinkSolution = async function (entityId, row, refresh) {
        if (!confirm('Unlink the solution "' + (row.name || '') + '" (' + (row.statusName || 'Solution') + ') from this record?\n\n'
                   + 'The solution itself, with its projects, items and log, stays on the Solutions page; only its link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/solutions/' + row.solutionId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Documentation xref tab (Story 3510 -- Leonardo Codex) ============
    // A Codex page is a page of Tony's own documentation (entity type "Codex
    // Page"). Attaching one here ties the write-up to THIS record -- the
    // vendor's runbook, the matter's brief -- through EntityCodexPageXRef.
    // "+ Link Existing" searches the Codex on the server; "+ New" opens the
    // Codex editor with this record already linked. House-only, like every
    // /api/entities route: an outside agent has no door into the Codex.

    ED.codexPageUrl = function (codexPageId) { return '/codex/space.html?page=' + encodeURIComponent(codexPageId); };
    ED.codexSuiteUrl = function (codexSpaceId) { return '/codex/suite.html?id=' + encodeURIComponent(codexSpaceId); };

    // Documentation Suites (2026-09-21): a Codex space is an entity too, so the
    // tab reads GET /documentation -- {suites, pages} -- and draws the suites
    // attached to this record above its single pages. A bare array (the older
    // /codex-pages read) still draws as pages only.
    function suiteCardHtml(s) {
        const url = ED.codexSuiteUrl(s.id);
        const chips = '<span class="ed-chip">' + (+s.pageCount || 0) + (+s.pageCount === 1 ? ' page' : ' pages') + '</span>'
            + (+s.openActionCount > 0 ? ' <span class="ed-chip">' + (+s.openActionCount) + ' open ' + (+s.openActionCount === 1 ? 'action' : 'actions') + '</span>' : '')
            + (s.readOnlyMount ? ' <span class="ed-chip">Read only</span>' : '')
            + (s.archived ? ' <span class="ed-chip">Archived</span>' : '');
        return '<div class="ed-card" data-testid="entity-codex-suite-row" style="border-left:4px solid ' + ED.escapeHtml(s.colorHex || '#6B7280') + '">'
            +     '<div class="head">'
            +         '<span class="badge" style="background:' + ED.escapeHtml(s.colorHex || '#6B7280') + ';color:#fff">Suite</span>'
            +         '<a href="' + url + '" class="name" data-testid="entity-codex-suite-open">' + ED.escapeHtml(s.name || '(unnamed)') + '</a>'
            +         ' ' + chips
            +     '</div>'
            +     (s.description ? '<div class="body"><div class="row">' + ED.escapeHtml(s.description) + '</div></div>' : '')
            +     (s.statusSummary ? '<div class="notes">' + ED.escapeHtml(s.statusSummary) + '</div>' : '')
            +     '<div class="ed-card-actions">'
            +         '<a class="ed-btn" href="/codex/space.html?space=' + encodeURIComponent(s.alias || '') + '" data-testid="entity-codex-suite-pages">Pages</a>'
            +         ED.pinButton('codexsuite', s.id, s.name || '(unnamed)', 'Documentation Suite', url)
            +         '<button type="button" class="ed-btn danger" data-act="unlinkSuite" data-testid="entity-codex-suite-unlink-btn">Unlink</button>'
            +     '</div>'
            + '</div>';
    }

    ED.renderCodexPages = function (paneEl, data, entityId, refresh) {
        const rows   = Array.isArray(data) ? data : ((data && data.pages) || []);
        const suites = Array.isArray(data) ? [] : ((data && data.suites) || []);
        const suitesHtml = suites.map(suiteCardHtml).join('');
        const cardsHtml = (rows && rows.length) ? rows.map(function (p) {
            const url = ED.codexPageUrl(p.id);
            const modified = solutionDate(p.modifiedDate);
            const where = (p.spaceName || p.spaceAlias || '') + (p.path ? ' | ' + p.path : '');
            return '<div class="ed-card" data-testid="entity-codex-row">'
                +     '<div class="head">'
                +         '<span class="badge" style="background:' + ED.escapeHtml(p.statusColor || '#6B7280') + ';color:#fff">'
                +             ED.escapeHtml(p.statusName || p.statusCode || 'Page') + '</span>'
                +         '<a href="' + url + '" class="name" data-testid="entity-codex-open">'
                +             ED.escapeHtml(p.title || '(untitled)') + '</a>'
                +         ' <span class="ed-chip">' + ED.escapeHtml(p.typeName || p.typeCode || 'Page') + '</span>'
                +         (p.archived ? ' <span class="ed-chip">Archived</span>' : '')
                +     '</div>'
                +     '<div class="body">'
                +         (where ? '<div class="row">' + ED.escapeHtml(where) + '</div>' : '')
                +         (modified ? '<div class="row">Modified ' + modified + '</div>' : '')
                +     '</div>'
                +     (p.excerpt ? '<div class="notes">' + ED.escapeHtml(p.excerpt) + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('codexpage', p.id, p.title || '(untitled)', p.spaceName || 'Leonardo Codex', url)
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-codex-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '';
        const empty = (!suites.length && !(rows && rows.length)) ? '<div class="ed-empty">No documentation linked to this record.</div>' : '';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkSuite" data-testid="entity-codex-suite-link-btn">+ Link Suite</button>'
                     +     '<button type="button" class="ed-btn" data-act="createSuite" data-testid="entity-codex-suite-new-btn">+ New Suite about this</button>'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-codex-link-btn">+ Link Page</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-codex-new-btn">+ New page about this</button>'
                     + '</div>';
        paneEl.innerHTML = actions + empty + suitesHtml + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkCodexPage(entityId, refresh); });
        const newBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newBtn) newBtn.addEventListener('click', function () { ED.createCodexPage(entityId); });
        const linkSuiteBtn = paneEl.querySelector('[data-act="linkSuite"]');
        if (linkSuiteBtn) linkSuiteBtn.addEventListener('click', function () { ED.linkCodexSuite(entityId, suites, refresh); });
        const newSuiteBtn = paneEl.querySelector('[data-act="createSuite"]');
        if (newSuiteBtn) newSuiteBtn.addEventListener('click', function () { ED.createCodexSuite(entityId, refresh); });
        paneEl.querySelectorAll('[data-testid="entity-codex-suite-row"]').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlinkSuite"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkCodexSuite(entityId, suites[idx], refresh); });
        });
        paneEl.querySelectorAll('[data-testid="entity-codex-row"]').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkCodexPage(entityId, rows[idx], refresh); });
        });
        // The suite rows carry their own pin kind; the loader only announces 'codexpage' for this tab.
        ED.attentionTabDrew('codexsuite');
    };

    // Every suite, archived ones included, less the ones already here. Suites are
    // a short list -- a handful of bodies of documentation -- so one read does it.
    ED.linkCodexSuite = async function (entityId, linkedSuites, refresh) {
        const r = await window.LWAuth.apiGet('/api/codex/spaces?includeArchived=true');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load the Documentation Suites.')); return; }
        const linked = new Set((linkedSuites || []).map(function (s) { return s.id; }));
        const items = (Array.isArray(r.data) ? r.data : [])
            .filter(function (s) { return !linked.has(s.id) && s.entityId !== entityId; })
            .map(function (s) { return { id: s.id, name: (s.name || s.alias) + (s.archived ? ' (archived)' : '') }; });
        if (!items.length) { alert('Every Documentation Suite is already linked to this record. Use "+ New Suite about this" instead.'); return; }
        ED.openModal({
            title: 'Link a Documentation Suite',
            fields: [
                { key: 'codexSpaceId', sortable: true, label: 'Suite', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.codexSpaceId, 10) || 0;
                if (!id) throw new Error('Pick a suite first.');
                const link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/codex-suites', { codexSpaceId: id }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // Make the suite through the normal /api/codex/spaces endpoint (the short
    // name for links is made from the name; colour, icon and the rest are edited
    // on the suite itself), then attach it here. Two calls, like "+ New Solution".
    ED.createCodexSuite = async function (entityId, refresh) {
        ED.openModal({
            title: 'New Documentation Suite about this record',
            fields: [
                { key: 'name',        label: 'Name',        type: 'text',     maxlength: 150, required: true, value: '' },
                { key: 'description', label: 'Description', type: 'textarea', value: '' }
            ],
            onSave: async function (data) {
                const name = (data.name || '').trim();
                if (!name) throw new Error('A suite needs a name.');
                const created = await window.LWAuth.apiPost('/api/codex/spaces', { name: name, description: data.description || '' }, 'POST');
                if (!created || !created.ok) throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.id;
                if (!newId) throw new Error('The suite saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/codex-suites', { codexSpaceId: newId }, 'POST');
                if (!link || !link.ok) throw new Error('The suite was saved but could not be linked. Use "+ Link Suite" to attach it.');
                refresh();
            }
        });
    };

    ED.unlinkCodexSuite = async function (entityId, row, refresh) {
        const n = +row.pageCount || 0;
        if (!confirm('Unlink the Documentation Suite "' + (row.name || '') + '" (' + n + (n === 1 ? ' page' : ' pages') + ') from this record?\n\n'
                   + 'The suite and its pages stay in the Leonardo Codex; only its link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/codex-suites/' + row.id, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // The Codex can hold thousands of pages, so the picker asks the server on
    // every keystroke (top 30 by title) instead of preloading a list.
    function codexPageSearch() {
        return async function (needle) {
            const r = await window.LWAuth.apiGet('/api/codex/pages/picker?q=' + encodeURIComponent(needle || ''));
            if (!r || !r.ok || !Array.isArray(r.data)) return [];
            return r.data.map(function (row) {
                return { id: row.id, name: (row.title || '(untitled)')
                                         + (row.spaceAlias ? ' (' + row.spaceAlias + (row.path ? ': ' + row.path : '') + ')' : '') };
            });
        };
    }

    ED.linkCodexPage = async function (entityId, refresh) {
        ED.openModal({
            title: 'Link an existing Codex page',
            fields: [
                { key: 'codexPageId', label: 'Page', type: 'typeahead', required: true, value: '',
                  search: codexPageSearch() }
            ],
            onSave: async function (data) {
                const id = parseInt(data.codexPageId, 10) || 0;
                if (!id) throw new Error('Pick a page first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/codex-pages', { codexPageId: id }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // Ask which space and what title, then hand over to the Codex editor with
    // entity=<EntityID>, so the page is linked to this record when it is first
    // saved. Nothing is written here -- leaving the editor leaves nothing behind.
    ED.createCodexPage = async function (entityId) {
        const r = await window.LWAuth.apiGet('/api/codex/spaces');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load the Codex spaces.')); return; }
        const spaces = (r.data || []).filter(function (s) { return !s.readOnlyMount && !s.archived; })
            .map(function (s) { return { id: s.id, name: s.name || s.alias }; });
        if (!spaces.length) { alert('There is no Codex space to write in yet. Add one on the Leonardo Codex home page first.'); return; }
        ED.openModal({
            title: 'New Codex page about this record',
            fields: [
                { key: 'spaceId', label: 'Space', type: 'select', options: spaces, required: true, value: spaces[0].id },
                { key: 'title',   label: 'Title', type: 'text', maxlength: 200, required: true, value: '' }
            ],
            onSave: async function (data) {
                const spaceId = parseInt(data.spaceId, 10) || 0;
                const title = (data.title || '').trim();
                if (!spaceId) throw new Error('Pick a space first.');
                if (!title) throw new Error('A page needs a title.');
                window.location.href = '/codex/edit.html?space=' + spaceId
                    + '&parent=&title=' + encodeURIComponent(title)
                    + '&type=page&entity=' + encodeURIComponent(entityId);
            }
        });
    };

    ED.unlinkCodexPage = async function (entityId, row, refresh) {
        if (!confirm('Unlink the Codex page "' + (row.title || '') + '" from this record?\n\n'
                   + 'The page itself stays in the Leonardo Codex; only its link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/codex-pages/' + row.id, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Dashboards (Story 3489) ==========================================
    //
    // A record DASHBOARD is the single-page, expand / collapse view of one
    // record: every tab this page has, stacked as collapsible sections in a
    // saved order (/dashboards/dashboard.html). Every record has its own
    // dashboard -- the first card on this tab, created the first time it is
    // opened -- and OTHER records' dashboards can be attached here the way
    // page links are, which is how the eleven account dashboards hang off the
    // matter that houses them.

    ED.dashboardUrl          = function (recordDashboardId) { return '/dashboards/dashboard.html?id=' + recordDashboardId; };
    ED.dashboardUrlForEntity = function (entityId)          { return '/dashboards/dashboard.html?entityId=' + entityId; };

    ED.renderDashboards = function (paneEl, rows, entityId, refresh) {
        const ownHtml = '<div class="ed-card" data-testid="entity-dashboard-own">'
                      +     '<div class="head">'
                      +         '<span class="badge">This record</span>'
                      +         '<a href="' + ED.dashboardUrlForEntity(entityId) + '" class="name" data-testid="entity-dashboard-own-open">'
                      +             'Dashboard of this record</a>'
                      +     '</div>'
                      + '</div>';
        const cardsHtml = (rows && rows.length) ? rows.map(function (d) {
            return '<div class="ed-card" data-testid="entity-dashboard-row">'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(d.targetEntityType || 'Dashboard') + '</span>'
                +         '<a href="' + ED.dashboardUrl(d.recordDashboardId) + '" class="name" data-testid="entity-dashboard-open">'
                +             ED.escapeHtml(d.title || '(untitled)') + '</a>'
                +     '</div>'
                +     (d.notes ? '<div class="notes">' + ED.escapeHtml(d.notes) + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('dashboard', d.recordDashboardId, d.title || '(untitled)', d.targetEntityType || 'Dashboard',
                                      ED.dashboardUrl(d.recordDashboardId))
                +         '<a class="ed-btn" href="/entity-manager-iv/entity-manager-iv.html?type=dashboards&id=' + encodeURIComponent(d.recordDashboardId) + '" data-testid="entity-dashboard-record">Details</a>'
                +         '<button type="button" class="ed-btn danger" data-act="unlink" data-testid="entity-dashboard-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '';
        const actions = '<div class="ed-pane-actions">'
                     +     '<button type="button" class="ed-btn primary" data-act="linkExisting" data-testid="entity-dashboard-link-btn">+ Link Dashboard</button>'
                     +     '<button type="button" class="ed-btn" data-act="createNew" data-testid="entity-dashboard-new-btn">+ New Dashboard</button>'
                     + '</div>';
        paneEl.innerHTML = actions + ownHtml + cardsHtml;
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkDashboard(entityId, refresh); });
        const newBtn = paneEl.querySelector('[data-act="createNew"]');
        if (newBtn) newBtn.addEventListener('click', function () { ED.createDashboard(entityId, refresh); });
        paneEl.querySelectorAll('[data-testid="entity-dashboard-row"]').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkDashboard(entityId, rows[idx], refresh); });
        });
    };

    // Pick a dashboard that already exists (the Dashboards view of Entity
    // Manager IV, searched on the server by title) and attach it.
    ED.linkDashboard = async function (entityId, refresh) {
        ED.openModal({
            title: 'Link an existing dashboard',
            fields: [
                { key: 'recordDashboardId', sortable: true, label: 'Dashboard', type: 'typeahead', required: true, value: '',
                  search: browseSearch(ENTITY_TYPE_DASHBOARD, function (row) {
                      return row.displayName + ((row.kindLabel || row.typeLabel) ? ' (' + (row.kindLabel || row.typeLabel) + ')' : '');
                  }) }
            ],
            onSave: async function (data) {
                const id = parseInt(data.recordDashboardId, 10) || 0;
                if (!id) throw new Error('Pick a dashboard first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/dashboards/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // Any record on Entity Manager IV, searched on the server, keyed on its
    // EntityID -- which is what a dashboard is made of.
    function browseSearchAnyRecord() {
        return async function (needle) {
            const r = await window.LWAuth.apiGet('/api/entity-browse?type=0&includeHealth=true&pageSize=50&q=' + encodeURIComponent(needle || ''));
            if (!r || !r.ok || !r.data) return [];
            return (r.data.rows || []).map(function (row) {
                return { id: row.entityId, name: row.displayName + ((row.kindLabel || row.typeLabel) ? ' (' + (row.kindLabel || row.typeLabel) + ')' : '') };
            });
        };
    }

    // Make (or find) the dashboard of some OTHER record and attach it here --
    // "a matter for Chase, and its dashboard on the Credit Card matter" in one step.
    ED.createDashboard = async function (entityId, refresh) {
        ED.openModal({
            title: 'New dashboard',
            fields: [
                { key: 'targetEntityId', label: 'Dashboard of', type: 'typeahead', required: true, value: '',
                  search: browseSearchAnyRecord() },
                { key: 'title', label: 'Title', type: 'text', maxlength: 200, value: '' },
                { key: 'notes', label: 'Notes', type: 'textarea', value: '' }
            ],
            onSave: async function (data) {
                const target = parseInt(data.targetEntityId, 10) || 0;
                if (!target) throw new Error('Pick the record the dashboard is of.');
                const body = { targetEntityId: target, title: (data.title || '').trim() || null, notes: data.notes || '' };
                const created = await window.LWAuth.apiPost('/api/dashboards', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.recordDashboardId;
                if (!newId) throw new Error('The dashboard saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/dashboards/' + newId, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    ED.unlinkDashboard = async function (entityId, row, refresh) {
        if (!confirm('Unlink the dashboard "' + (row.title || '') + '" from this record?\n\n'
                   + 'The dashboard itself stays -- it is still the dashboard of its own record; only its attachment to this record is removed.')) return;
        const r = await window.LWAuth.apiPost(
            '/api/entities/' + entityId + '/dashboards/' + row.recordDashboardId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Notes (the old Events tab) =========================================
    //
    // Story 3524: an "event" is really a note, so the tab is Notes, and it is
    // ORGANIZED like the Files tab -- note types as coloured, ordered categories,
    // grouping, sorting, search, Important -- by contacts/entity-notes.js. The
    // loader hands it the first page of /notes/filed ({rows, groups, ...}). The
    // plain card list below is the fallback for a page that has not loaded it.

    ED.renderEvents = function (paneEl, data, entityId, refresh) {
        if (window.EntityNotes && window.FileKit && data && !Array.isArray(data)) {
            window.EntityNotes.render(paneEl, data, entityId, refresh);
            return;
        }
        renderEventsPlain(paneEl, Array.isArray(data) ? data : ((data && data.rows) || []), entityId, refresh);
    };

    function renderEventsPlain(paneEl, rows, entityId, refresh) {
        const cardsHtml = (rows && rows.length) ? rows.map(function (e) {
            const dateRange = e.eventThroughDate && e.eventThroughDate !== e.eventDate
                ? (e.eventDate + ' to ' + e.eventThroughDate)
                : (e.eventDate || '');
            // View is the primary action -- Tony uses it most for reading notes.
            // Custom action row (not cardActionsHtml) so View can be wired
            // separately, mirroring the Files pane's Open button pattern.
            return '<div class="ed-card"' + rowTid('event') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(e.eventType || 'Note') + '</span>'
                +         '<span class="name">' + ED.escapeHtml(e.eventTitle || '(no title)') + '</span>'
                +         '<span class="dates">' + ED.escapeHtml(dateRange) + '</span>'
                +     '</div>'
                +     (e.eventDescriptionPreview
                        ? '<div class="notes">' + ED.escapeHtml(e.eventDescriptionPreview) + '</div>'
                        : '')
                +     '<div class="ed-card-actions">'
                +         '<button type="button" class="ed-btn primary" data-act="view" data-testid="entity-event-view-btn">View</button>'
                +         '<button type="button" class="ed-btn" data-act="reader" data-testid="entity-event-reader-btn">Reader</button>'
                +         (ED.canListen() ? '<button type="button" class="ed-btn" data-act="listen" data-testid="entity-event-listen-btn">Listen</button>' : '')
                +         ED.pinButton('event', e.eventId, e.eventTitle || '(no title)',
                                      [e.eventType, dateRange].filter(Boolean).join(' | '))
                +         '<button type="button" class="ed-btn"        data-act="edit" data-testid="entity-event-edit-btn">Edit</button>'
                +         '<button type="button" class="ed-btn danger" data-act="del"  data-testid="entity-event-del-btn">Delete</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No notes on file.</div>';
        const linkExtra = '<button type="button" class="ed-btn" data-act="linkExisting" '
                        + 'data-testid="entity-event-link-btn">+ Link Note</button>'
                        + '<button type="button" class="ed-btn" data-act="permaLinks" '
                        + 'data-testid="entity-note-perma-btn">Perma Links</button>';
        paneEl.innerHTML = actionsBarHtml('Add Note', 'event', linkExtra) + cardsHtml;
        wirePaneActions(paneEl, rows || [], {
            add:  function ()  { ED.editEvent(entityId, null, refresh); },
            edit: function (e) { ED.editEvent(entityId, e,    refresh); },
            del:  function (e) { ED.deleteEvent(entityId, e,  refresh); }
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkEvent(entityId, refresh); });
        const permaBtn = paneEl.querySelector('[data-act="permaLinks"]');
        if (permaBtn) permaBtn.addEventListener('click', function () { ED.managePermaLinks(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (cardEl, idx) {
            const viewBtn = cardEl.querySelector('[data-act="view"]');
            if (!viewBtn) return;
            viewBtn.addEventListener('click', function () {
                ED.viewEvent(entityId, (rows || [])[idx]);
            });
            cardEl.querySelector('[data-act="reader"]').addEventListener('click', function () {
                ED.openNoteInReader(entityId, (rows || [])[idx]);
            });
            const listenBtn = cardEl.querySelector('[data-act="listen"]');
            if (listenBtn) listenBtn.addEventListener('click', function () {
                ED.listenToNote(entityId, (rows || [])[idx]);
            });
        });
    }

    /** Attach an existing event/note to this entity. Mirrors linkAddress: pick
     *  from a typeahead of events not yet linked here (most-recent-first), then
     *  POST an xref-only link (the shared event record is never modified). */
    ED.linkEvent = async function (entityId, refresh) {
        // Server-searched: each keystroke asks for the 50 newest unlinked notes
        // matching the text (title, type, or yyyy-mm-dd date). The whole table
        // never comes down -- it was 900 notes and 280 KB per tap (Story 3476).
        async function searchNotes(needle) {
            const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/events/available?limit=50&q='
                                                 + encodeURIComponent(needle || ''));
            if (!r || !r.ok) return [];
            return (r.data || []).map(function (e) {
                const label = (e.eventDate ? e.eventDate + ' -- ' : '')
                            + (e.eventTitle || '(no title)')
                            + (e.eventType ? ' (' + e.eventType + ')' : '');
                return { id: e.eventId, name: label.trim() };
            });
        }
        ED.openModal({
            title: 'Link an existing note',
            fields: [
                { key: 'eventId', label: 'Note', type: 'typeahead', required: true, value: '', search: searchNotes }
            ],
            onSave: async function (data) {
                const id = parseInt(data.eventId, 10) || 0;
                if (!id) throw new Error('Pick a note first.');
                const link = await window.LWAuth.apiPost(
                    '/api/entities/' + entityId + '/events/link/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    // ----- Note perma links (Story 3562; turned around 2026-10-02) --------------------
    //
    // A perma link is one-way: notes entered on one record are also filed on another. The
    // Perma Links window works from the record that COLLECTS the notes -- on Samantha, adding
    // Lakeview means a note entered on Lakeview is also filed on Samantha (Tony's reading,
    // 2026-10-02; the first build went the other way). Add Note on Lakeview then offers
    // Samantha as a ticked "Also file on" box; untick it to keep that one note off her.

    // The records a NEW note on this one is also filed on (Add Note's checkboxes).
    ED.loadPermaLinks = async function (entityId) {
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/note-perma-links');
        return (r && r.ok && r.data && Array.isArray(r.data.permaLinks)) ? r.data.permaLinks : [];
    };

    // Perma Links come in two kinds, each its own setting (Story 5631): notes, and files.
    // The window, the pull-existing offer and the wording are shared; only the words and the
    // address differ. kind is 'notes' (the default) or 'files'.
    const PERMA_KINDS = {
        notes: { path: 'note-perma-links', one: 'note', many: 'notes', Many: 'Notes', countKey: 'noteCount' },
        files: { path: 'file-perma-links', one: 'file', many: 'files', Many: 'Files', countKey: 'fileCount' }
    };
    function permaKind(kind) { return PERMA_KINDS[kind] || PERMA_KINDS.notes; }

    // The records whose new notes (or files) are also filed on this one (the Perma Links window).
    ED.loadPermaLinkSources = async function (entityId, kind) {
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/' + permaKind(kind).path + '/sources');
        return (r && r.ok && r.data && Array.isArray(r.data.sources)) ? r.data.sources : [];
    };

    function permaLinkLabel(p) {
        return (p.displayName || ('Record #' + p.linkedEntityId))
             + (p.entityTypeName ? ' (' + p.entityTypeName + ')' : '');
    }

    function permaSourceLabel(p) {
        return (p.displayName || ('Record #' + p.sourceEntityId))
             + (p.entityTypeName ? ' (' + p.entityTypeName + ')' : '');
    }

    // Perma Links file NOTES between whole records. A health visit or issue (types 231 / 232) is
    // part of a health record, so offering them here only buried the record itself under every
    // appointment it holds (Tony, Story 5628 #9224): the record is offered, its parts are not.
    const PERMA_SKIP_TYPES = [231, 232];
    function permaSearch() {
        return async function (needle) {
            const r = await window.LWAuth.apiGet('/api/entity-browse?type=0&includeHealth=true&pageSize=50&q=' + encodeURIComponent(needle || ''));
            if (!r || !r.ok || !r.data) return [];
            return (r.data.rows || [])
                .filter(function (row) { return PERMA_SKIP_TYPES.indexOf(Number(row.entityTypeId)) < 0; })
                .map(function (row) {
                    return { id: row.entityId, name: row.displayName + ((row.kindLabel || row.typeLabel) ? ' (' + (row.kindLabel || row.typeLabel) + ')' : '') };
                });
        };
    }

    ED.managePermaLinks = async function (entityId, refresh, kind) {
        const k = permaKind(kind);
        const sources = await ED.loadPermaLinkSources(entityId, kind);
        const search = permaSearch();
        const base = '/api/entities/' + entityId + '/' + k.path + '/sources/';
        ED.openModal({
            title: 'Perma Links -- ' + k.many + ' on these records are also filed here',
            fields: sources.map(function (p) {
                return { key: 'keep_' + p.sourceEntityId, label: permaSourceLabel(p), type: 'checkbox', value: true };
            }).concat([
                { key: 'addEntityId', label: sources.length ? 'Add another record' : 'Add a record',
                  type: 'typeahead', value: '',
                  search: async function (needle) {
                      const rows = await search(needle);
                      return rows.filter(function (o) { return String(o.id) !== String(entityId); });
                  } }
            ]),
            onSave: async function (data) {
                const dropped = sources.filter(function (p) { return !data['keep_' + p.sourceEntityId]; });
                const addId = parseInt(data.addEntityId, 10) || 0;
                if (dropped.length && !confirm('Stop filing new ' + k.many + ' from these records here:\n\n'
                        + dropped.map(function (p) { return '  ' + permaSourceLabel(p); }).join('\n')
                        + '\n\n' + k.Many + ' already filed here stay where they are. This cannot be undone.')) {
                    throw new Error('Nothing was changed.');
                }
                for (const p of dropped) {
                    const d = await window.LWAuth.apiPost(base + p.sourceEntityId, null, 'DELETE');
                    if (!d || !d.ok) throw new Error('Could not remove ' + permaSourceLabel(p) + '. Close and try again.');
                }
                if (addId > 0) {
                    const a = await window.LWAuth.apiPost(base + addId, null, 'POST');
                    if (!a || !a.ok) throw new Error((a && a.data && (a.data.message || a.data.error))
                                                     || 'That record could not be added. Nothing was saved.');
                    const added = ((a.data && a.data.sources) || []).find(function (p) {
                        return String(p.sourceEntityId) === String(addId);
                    });
                    await offerPullExisting(base + addId + '/pull-existing',
                                            added ? permaSourceLabel(added) : 'that record', k);
                }
                if (refresh) refresh();
            }
        });
    };

    // A file attached to a record is filed on that record's file perma links by the server
    // (Story 5631); the save answers with filedOn: [{linkedEntityId, displayName}]. This gathers
    // the names from one or more answers and says where else the files went -- a confirmation
    // that needs nothing from the reader, so it may fade (the Picasso toast rule's exception).
    ED.filedOnNames = function (answers, into) {
        const names = into || [];
        (answers || []).forEach(function (a) {
            const list = a && a.data && Array.isArray(a.data.filedOn) ? a.data.filedOn : [];
            list.forEach(function (f) {
                const n = (f && f.displayName) || '';
                if (n && names.indexOf(n) < 0) names.push(n);
            });
        });
        return names;
    };
    ED.sayFiledOn = function (names) {
        if (!names || !names.length || !(window.Picasso && Picasso.toast)) return;
        Picasso.toast('Also filed on ' + names.join(', ') + ' (Perma Links).');
    };

    // "Pull existing notes" (2026-10-02). A perma link only files NEW notes; right after one is
    // added, offer to file the notes ALREADY on the added record here too. The server skips any
    // note already here, so saying yes twice never files one twice. The link itself is already
    // saved, so a failure here says so rather than reporting the save failed. Files (Story 5631)
    // make the same offer; k says which kind.
    async function offerPullExisting(url, label, k) {
        k = k || permaKind('notes');
        const preview = await window.LWAuth.apiGet(url);
        if (!preview || !preview.ok || !preview.data) return;
        const toFile = preview.data.toFileCount || 0;
        if (toFile <= 0) return;
        const already = preview.data.alreadyCount || 0;
        const plural = function (n) { return n + ' ' + (n === 1 ? k.one : k.many); };
        const ask = 'Pull existing ' + k.many + '?\n\n'
            + label + ' has ' + plural(preview.data[k.countKey] || 0)
            + (already ? ', and ' + already + (already === 1 ? ' is' : ' are') + ' already on this record' : '')
            + '.\n\nFile ' + (already ? 'the other ' : 'all ') + plural(toFile) + ' here too?\n\n'
            + 'OK files them now. Cancel leaves them where they are; new ' + k.many + ' on ' + label
            + ' are still filed here.';
        if (!confirm(ask)) return;
        const pulled = await window.LWAuth.apiPost(url, null, 'POST');
        if (!pulled || !pulled.ok) {
            throw new Error('The perma link was saved, but the ' + k.many + ' on ' + label + ' could not be filed here: '
                + (window.LWAuth.errorText(pulled, 'no reason given'))
                + ' Open Perma Links again and add it once more to retry.');
        }
        if (window.Picasso && Picasso.toast) {
            Picasso.toast(plural(pulled.data.filedCount || 0) + ' from ' + label + ' filed here.');
        }
    }

    // Lazy-injected read-only viewer. Separate from the edit modal so its
    // typography/layout don't fight the form CSS. Reused across viewEvent calls.
    let _viewerEl = null;
    function ensureViewer() {
        if (_viewerEl) return _viewerEl;
        const bg = document.createElement('div');
        bg.className = 'ed-viewer-bg';
        bg.innerHTML =
            '<div class="ed-viewer" role="dialog" aria-modal="true">'
          +     '<div class="ed-viewer-head">'
          +         '<button type="button" class="ed-viewer-print" title="Print" aria-label="Print"'
          +             ' data-testid="entity-viewer-print">' + VIEWER_PRINT_SVG + '</button>'
          +         '<span class="ed-viewer-badge"></span>'
          +         '<h2 class="ed-viewer-title"></h2>'
          +         '<div class="ed-viewer-dates"></div>'
          +     '</div>'
          +     '<div class="ed-viewer-body"></div>'
          +     '<div class="ed-viewer-actions">'
          +         '<button type="button" class="ed-btn" id="lwedViewerReader" hidden title="Read it in the Leonardo Reader -- bookmark, highlight, Send to Quotes"'
          +             ' data-testid="entity-viewer-reader">Reader</button>'
          +         '<button type="button" class="ed-btn" id="lwedViewerListen" hidden title="Read it aloud"'
          +             ' data-testid="entity-viewer-listen">Listen</button>'
          +         '<button type="button" class="ed-btn" id="lwedViewerCopy" title="Copy the note to the clipboard"'
          +             ' data-testid="entity-viewer-copy">Copy</button>'
          +         '<button type="button" class="ed-btn" id="lwedViewerClose">Close</button>'
          +     '</div>'
          + '</div>';
        document.body.appendChild(bg);
        function close() { bg.classList.remove('open'); }
        bg.querySelector('#lwedViewerClose').addEventListener('click', close);
        bg.querySelector('.ed-viewer-print').addEventListener('click', function () { printViewer(bg); });
        bg.querySelector('#lwedViewerCopy').addEventListener('click', function () { copyViewer(bg); });
        // Story 5648: Reader leaves the viewer for the Leonardo Reader; Listen keeps it open
        // so the words stay on screen while they are read.
        bg.querySelector('#lwedViewerReader').addEventListener('click', function () {
            const go = bg._onReader;
            close();
            if (go) go();
        });
        bg.querySelector('#lwedViewerListen').addEventListener('click', function () {
            if (bg._onListen) bg._onListen();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && bg.classList.contains('open')) close();
        });
        _viewerEl = bg;
        return bg;
    }

    // The same printer glyph the icon band's Print uses, so it reads as Print at a glance.
    const VIEWER_PRINT_SVG =
        '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">'
      + '<path d="M6 3h12v4H6V3zm13 5H5a3 3 0 0 0-3 3v6h4v3h12v-3h4v-6a3 3 0 0 0-3-3z'
      + 'm-3 11H8v-4h8v4zm3-6.5a1 1 0 1 1 0-2 1 1 0 0 1 0 2z"/></svg>';

    // Story 5618: print what the viewer shows -- type, title, dates, the note --
    // through the Leonardo Print Menu. Built in an offscreen node so the sheet's
    // buttons never reach the page; line breaks become <br/> for the PDF renderer.
    function printViewer(bg) {
        if (!window.Picasso || !window.Picasso.printThisPage) return;
        const title = bg.querySelector('.ed-viewer-title').textContent || 'Note';
        const badge = bg.querySelector('.ed-viewer-badge').textContent || '';
        const dates = bg.querySelector('.ed-viewer-dates').textContent || '';
        const bodyEl = bg.querySelector('.ed-viewer-body');
        const body  = bodyEl.classList.contains('empty') ? '' : (bodyEl.textContent || '');
        let area = document.getElementById('lwedViewerPrintArea');
        if (!area) {
            area = document.createElement('div');
            area.id = 'lwedViewerPrintArea';
            area.style.display = 'none';
            document.body.appendChild(area);
        }
        area.innerHTML =
            '<h1>' + ED.escapeHtml(title) + '</h1>'
          + (badge || dates
                ? '<div class="pv-meta">' + ED.escapeHtml([badge, dates].filter(Boolean).join('  |  ')) + '</div>'
                : '')
          + '<div class="pv-body">'
          +     (body ? ED.escapeHtml(body).replace(/\r?\n/g, '<br/>') : '<em>Nothing is written in this note.</em>')
          + '</div>';
        window.Picasso.printThisPage({
            selector: '#lwedViewerPrintArea',
            title:    title,
            extraCss: '.pv-meta{color:#7A6B3C;font-size:10pt;margin:0 0 14px;}'
                    + '.pv-body{font:11.5pt/1.6 Georgia,serif;}'
        });
    }

    // Copy what the viewer shows -- title, type and dates, then the note -- as plain
    // text. The button itself says "Copied" for a moment; no dialog to dismiss.
    function copyViewer(bg) {
        const title = bg.querySelector('.ed-viewer-title').textContent || '';
        const badge = bg.querySelector('.ed-viewer-badge').textContent || '';
        const dates = bg.querySelector('.ed-viewer-dates').textContent || '';
        const bodyEl = bg.querySelector('.ed-viewer-body');
        const body  = bodyEl.classList.contains('empty') ? '' : (bodyEl.textContent || '');
        const meta  = [badge, dates].filter(Boolean).join('  |  ');
        const text  = [title, meta].filter(Boolean).join('\n') + (body ? '\n\n' + body : '');
        const btn   = bg.querySelector('#lwedViewerCopy');
        function done(label) {
            btn.textContent = label;
            setTimeout(function () { btn.textContent = 'Copy'; }, 1500);
        }
        const p = (window.Picasso && window.Picasso.copyText)
            ? window.Picasso.copyText(text)
            : (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject());
        p.then(function () { done('Copied'); }, function () { done('Copy failed'); });
    }

    /** Open the read-only viewer on any long-text record.
     *  config = { badge, title, dates, body, empty, onReader, listenKey }
     *  onReader (optional) shows a Reader button that opens the text in the Leonardo
     *  Reader. listenKey (optional) shows Listen, which reads the body aloud and keeps
     *  its place under that key (Story 5648). */
    ED.showViewer = function (config) {
        const bg = ensureViewer();
        const hasBody = !!(config.body && config.body.trim());
        bg._onReader = (hasBody && config.onReader) || null;
        bg._onListen = (hasBody && config.listenKey && ED.canListen())
            ? function () { ED.listenToText(config.body, config.title || 'Note', config.listenKey); }
            : null;
        bg.querySelector('#lwedViewerReader').hidden = !bg._onReader;
        bg.querySelector('#lwedViewerListen').hidden = !bg._onListen;
        bg.querySelector('.ed-viewer-badge').textContent = config.badge || '';
        bg.querySelector('.ed-viewer-title').textContent = config.title || '';
        bg.querySelector('.ed-viewer-dates').textContent = config.dates || '';
        const bodyEl = bg.querySelector('.ed-viewer-body');
        if (config.body && config.body.trim()) {
            bodyEl.textContent = config.body;   // pre-wrap CSS preserves line breaks
            bodyEl.classList.remove('empty');
        } else {
            bodyEl.textContent = config.empty || 'Nothing was written down here.';
            bodyEl.classList.add('empty');
        }
        bg.classList.add('open');
    };

    // ----- Record View: the edit dialog as a report, on sepia ------------------
    //
    // View shows exactly what Edit would -- the same fields, in the same order,
    // with the same labels -- as a read-only page to read (the Leonardo
    // Reader's sepia, like The Thinker's and Solutions' View). It is fed the
    // SAME field list ED.openModal takes, so the two can never drift apart:
    // a field added to an editor appears in its View for free.

    let _recordViewEl = null;
    function ensureRecordView() {
        if (_recordViewEl) return _recordViewEl;
        const bg = document.createElement('div');
        bg.className = 'ed-rview-bg';
        bg.innerHTML =
            '<div class="ed-rview" role="dialog" aria-modal="true" data-testid="entity-record-view">'
          +     '<div class="ed-rview-scroll">'
          +         '<div class="ed-rview-badge"></div>'
          +         '<h2 class="ed-rview-title" data-testid="entity-record-view-title"></h2>'
          +         '<dl class="ed-rview-fields"></dl>'
          +     '</div>'
          +     '<div class="ed-rview-actions">'
          +         '<button type="button" class="ed-btn" data-act="print" data-testid="entity-record-view-print">Print</button>'
          +         '<button type="button" class="ed-btn" data-act="open" data-testid="entity-record-view-open">Open</button>'
          +         '<button type="button" class="ed-btn" data-act="edit" data-testid="entity-record-view-edit">Edit</button>'
          +         '<button type="button" class="ed-btn primary" data-act="close" data-testid="entity-record-view-close">Close</button>'
          +     '</div>'
          + '</div>';
        document.body.appendChild(bg);
        bg.querySelector('[data-act="close"]').addEventListener('click', closeRecordView);
        bg.querySelector('[data-act="edit"]').addEventListener('click', function () {
            const onEdit = bg._onEdit;
            closeRecordView();
            if (typeof onEdit === 'function') onEdit();
        });
        bg.querySelector('[data-act="open"]').addEventListener('click', function () {
            const onOpen = bg._onOpen;
            closeRecordView();
            if (typeof onOpen === 'function') onOpen();
            else if (typeof onOpen === 'string') window.location.href = onOpen;
        });
        // Every View prints through the Leonardo Print Menu (Story 5651): the sheet as it reads.
        bg.querySelector('[data-act="print"]').addEventListener('click', function () {
            if (!window.Picasso || !window.Picasso.printThisPage) return;
            const title = (bg.querySelector('.ed-rview-title').textContent || 'Record').trim();
            window.Picasso.printThisPage({ selector: '.ed-rview-scroll', title: title,
                filename: title.replace(/[^A-Za-z0-9 _-]+/g, '').trim().replace(/\s+/g, '-') + '.pdf' });
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && bg.classList.contains('open')) closeRecordView();
        });
        _recordViewEl = bg;
        return bg;
    }
    function closeRecordView() { if (_recordViewEl) _recordViewEl.classList.remove('open'); }

    /** One edit-dialog field as the text a reader sees ('' = nothing on file). */
    function recordViewValue(f) {
        const v = f.value;
        if (f.type === 'checkbox') return v ? 'Yes' : 'No';
        if (v == null || v === '') return '';
        if (f.type === 'select' || f.type === 'typeahead') {
            const o = (f.options || []).find(function (x) { return String(x.id) === String(v); });
            return o ? String(o.name) : (f.options && f.options.length ? '' : String(v));
        }
        if (f.type === 'tags') return Array.isArray(v) ? v.join(', ') : String(v);
        if (f.type === 'date') return usDate(v) || String(v);
        if (f.type === 'tel') return ED.formatPhone ? ED.formatPhone(v) : String(v);
        if (f.type === 'password') return String.fromCharCode(0x2022).repeat(8);
        return String(v);
    }

    /** config = { badge, title, fields (ED.openModal's field list), onEdit, onOpen }.
     *  onEdit, when given, puts an Edit button beside Close; onOpen (a function, or the record
     *  page's address) puts an Open button there. Print -- the Leonardo Print Menu -- is always
     *  there (Story 5651: a View link opens the sepia sheet with Open, Print, Edit and Close).
     *  A field of type 'html' carries trusted markup in f.html (escape what goes into it). */
    ED.showRecordView = function (config) {
        const bg = ensureRecordView();
        bg.querySelector('.ed-rview-badge').textContent = config.badge || '';
        bg.querySelector('.ed-rview-title').textContent = config.title || '';
        bg.querySelector('.ed-rview-fields').innerHTML = (config.fields || [])
            .filter(function (f) { return f && f.label && f.type !== 'hidden'; })
            .map(function (f) {
                // A section heading inside the sheet (the insurance card's "Benefits, 2026", Story 5641).
                if (f.type === 'heading') return '<h3 class="ed-rview-head" data-testid="entity-record-view-heading">' + ED.escapeHtml(f.label) + '</h3>';
                // Trusted markup the caller built and escaped (a table of service lines, Story 5651).
                if (f.type === 'html') return '<div class="ed-rview-row" data-testid="entity-record-view-field"><dt>' + ED.escapeHtml(f.label)
                                            + '</dt><dd class="ed-rview-val long">' + (f.html || '&mdash;') + '</dd></div>';
                const text = recordViewValue(f);
                const cls  = 'ed-rview-val' + (f.type === 'textarea' ? ' long' : '') + (text ? '' : ' none');
                return '<div class="ed-rview-row" data-testid="entity-record-view-field">'
                     +     '<dt>' + ED.escapeHtml(f.label) + '</dt>'
                     +     '<dd class="' + cls + '">' + (text ? ED.escapeHtml(text) : '&mdash;') + '</dd>'
                     + '</div>';
            }).join('');
        bg._onEdit = config.onEdit || null;
        bg._onOpen = config.onOpen || null;
        bg.querySelector('[data-act="edit"]').hidden = !config.onEdit;
        bg.querySelector('[data-act="open"]').hidden = !config.onOpen;
        bg.querySelector('.ed-rview-scroll').scrollTop = 0;
        bg.classList.add('open');
        setTimeout(function () { const c = bg.querySelector('[data-act="close"]'); if (c) c.focus(); }, 30);
    };

    ED.viewEvent = async function (entityId, row) {
        if (!row) return;
        // List payload truncates description at 300 chars; fetch the full row.
        let full = '';
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/events/' + row.entityEventXrefId);
        if (r && r.ok && r.data) full = r.data.eventDescription || '';

        const dateRange = row.eventThroughDate && row.eventThroughDate !== row.eventDate
            ? (row.eventDate + '  \u2192  ' + row.eventThroughDate)
            : (row.eventDate || '');

        ED.showViewer({
            badge: row.eventType  || 'Note',
            title: row.eventTitle || '(no title)',
            dates: dateRange,
            body:  full,
            empty: 'Nothing is written in this note.',
            onReader:  function () { ED.openNoteInReader(entityId, row); },
            listenKey: noteDocKey(entityId, row) + ':listen'
        });
    };

    // ----- A note in the Leonardo Reader, and read aloud (Story 5648) ---------
    //
    // A note opens in the Reader as a plain-text document of its own, so it gets
    // everything the Reader gives any document: bookmarks, highlights in color,
    // notes on a mark, Send to Quotes, Define, the Notebook, the saved place --
    // and Listen. Its marks are filed under 'enote:<entityId>:<xrefId>' (a row
    // in dbo.ReaderSource, Data_ReaderSource.sql), which is how the Highlights
    // shelf finds the note again.

    function noteDocKey(entityId, row) {
        return 'enote:' + entityId + ':' + row.entityEventXrefId;
    }

    ED.openNoteInReader = function (entityId, row) {
        if (!row || !row.entityEventXrefId) return;
        const src = '/api/entities/' + encodeURIComponent(entityId) + '/events/'
                  + encodeURIComponent(row.entityEventXrefId) + '/text';
        window.open('/reader/reader.html?src=' + encodeURIComponent(src)
            + '&type=txt'
            + '&title=' + encodeURIComponent(row.eventTitle || 'Note')
            + '&key=' + encodeURIComponent(noteDocKey(entityId, row)), '_blank');
    };

    /** Read one note aloud, fetching its full text (the list carries only a preview). */
    ED.listenToNote = async function (entityId, row) {
        if (!row || !row.entityEventXrefId) return;
        if (!listenReady()) return;
        const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/events/' + row.entityEventXrefId);
        if (!r || !r.ok) { window.Picasso.error(window.LWAuth.errorText(r, 'Could not load that note to read.')); return; }
        const text = (r.data && r.data.eventDescription) || '';
        if (!text.trim()) { window.Picasso.message('Nothing is written in this note.'); return; }
        ED.listenToText(text, row.eventTitle || 'Note', noteDocKey(entityId, row) + ':listen');
    };

    /** The Listen player (reading-nook/nook-reader.js) is on the page. These files are
     *  shared by every record page; only the ones that load the player (Entity Manager
     *  IV and V) show Listen, so a button is never offered that cannot work. */
    ED.canListen = function () {
        return !!(window.NookReader && window.NookReader.playText);
    };

    function listenReady() {
        if (ED.canListen()) return true;
        window.Picasso.error('Listen is not available on this page.');
        return false;
    }

    /** Read text already in hand aloud, as one narration in sentence-sized passages. */
    ED.listenToText = function (text, title, key) {
        if (!listenReady()) return;
        window.NookReader.playText(text, title, key, true);
    };

    ED.editEvent = async function (entityId, row, refresh, prefill) {
        // Story 3524: the type is a find-or-create picker over the note types --
        // type a new one and it is made on save. Read fresh, so a type added on
        // the Notes tab a moment ago is offered.
        const tr = await window.LWAuth.apiGet('/api/note-types');
        const types = (tr && tr.ok && Array.isArray(tr.data)) ? tr.data.map(function (t) { return { id: t.id, name: t.name }; })
            .sort(function (a, b) { return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }); }) : [];
        function typeName(id) {
            const t = types.find(function (x) { return x.id === parseInt(id, 10); });
            return t ? t.name : '';
        }
        // For edit, fetch the FULL description (list view truncates to 300 chars).
        let fullDescription = '';
        if (row) {
            const r = await window.LWAuth.apiGet('/api/entities/' + entityId + '/events/' + row.entityEventXrefId);
            if (r && r.ok && r.data) fullDescription = r.data.eventDescription || '';
        }
        const today = new Date();
        const todayStr = today.getFullYear() + '-'
            + String(today.getMonth() + 1).padStart(2, '0') + '-'
            + String(today.getDate()).padStart(2, '0');
        const pre = prefill || {};   // voice-command prefill -- only applies on add

        // Story 3496: the mistake this guards against is logging a plain event
        // on somebody who already has a matter open about exactly this. When
        // the record has unresolved matters, a NEW event offers them at the
        // very top of the form. Nothing is preselected -- plenty of events have
        // nothing to do with the matter -- but it cannot be overlooked, and
        // choosing one files the same event under the matter as well.
        let openMatters = [];
        if (!row) {
            const att = await ED.loadAttention(entityId);
            openMatters = att.matters.map(function (m) {
                return { id: m.MatterID, name: (m.Title || '(no title)') + ' (' + (m.StatusName || 'Open') + ')' };
            });
        }
        const matterField = openMatters.length
            ? [{ key: 'matterId', label: 'Open matter on this record -- also log it there', type: 'select',
                 options: openMatters, value: '' }]
            : [];

        // Story 3562: a NEW note is also filed on each perma-linked record, ticked by default.
        const permaLinks = row ? [] : await ED.loadPermaLinks(entityId);
        const permaFields = permaLinks.map(function (p) {
            return { key: 'perma_' + p.linkedEntityId, label: 'Also file on ' + permaLinkLabel(p),
                     type: 'checkbox', value: true };
        });

        ED.openModal({
            title: row ? 'Edit Note' : 'Add Note',
            // Editing an existing note means editing its text -- land there, not in the Type picker.
            focusKey: row ? 'eventDescription' : null,
            fields: matterField.concat([
                // New notes default to the type "Notes" -- what virtually every
                // contact / vendor note is.
                { key: 'eventTypeName',    label: 'Type',              type: 'picker', options: types, required: true,
                  value: row ? (row.eventType || '') : (typeName(pre.eventTypeId) || 'Notes') },
                { key: 'eventTitle',       label: 'Title',             type: 'text', maxlength: 100, required: true,
                  value: row ? row.eventTitle : (pre.eventTitle || '') },
                { key: 'eventDate',        label: 'Date',              type: 'date',
                  value: row ? (row.eventDate || '') : todayStr },
                { key: 'eventThroughDate', label: 'Through date',      type: 'date',
                  value: row ? (row.eventThroughDate || '') : '' },
                { key: 'eventDescription', label: 'Note',              type: 'textarea',
                  value: row ? fullDescription : (pre.eventDescription || '') }
            ]).concat(permaFields),
            onSave: async function (data) {
                const typed = (data.eventTypeName || '').trim();
                const known = types.find(function (t) { return t.name.toLowerCase() === typed.toLowerCase(); });
                const body = {
                    eventId:          row ? row.eventId : 0,
                    eventTypeId:      known ? known.id : 0,
                    eventTypeName:    known ? null : typed,
                    eventTitle:       data.eventTitle,
                    eventDescription: data.eventDescription,
                    eventDate:        data.eventDate,
                    eventThroughDate: data.eventThroughDate
                };
                if (!typed) throw new Error('Type is required');
                if (!body.eventTitle || !body.eventTitle.trim()) throw new Error('Title is required');
                const url = row
                    ? '/api/entities/' + entityId + '/events/' + row.entityEventXrefId
                    : '/api/entities/' + entityId + '/events';
                const r = await window.LWAuth.apiPost(url, body, row ? 'PUT' : 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Save failed'));

                // The event exists now; filing it under the matter is a second,
                // separate write. If that one is refused the event is NOT lost,
                // so say exactly that rather than reporting the save as failed.
                const matterId = parseInt(data.matterId, 10) || 0;
                if (matterId > 0 && r.data && r.data.eventId) {
                    const link = await window.LWAuth.apiPost('/api/matters/' + matterId + '/events',
                                                             { eventId: r.data.eventId });
                    if (!link || !link.ok) {
                        refresh();
                        throw new Error('The note was saved on this record, but could not be added to the matter: '
                            + (window.LWAuth.errorText(link, 'no reason given'))
                            + ' Open the matter and attach it from there.');
                    }
                }

                // Perma links: one server call files the note on every ticked record. The
                // link is idempotent, so a retry never doubles it.
                const fileOn = permaLinks.filter(function (p) { return data['perma_' + p.linkedEntityId]; })
                                         .map(function (p) { return p.linkedEntityId; });
                if (fileOn.length && r.data && r.data.eventId) {
                    const filed = await window.LWAuth.apiPost('/api/entities/' + entityId
                        + '/note-perma-links/file/' + r.data.eventId, { linkedEntityIds: fileOn });
                    if (!filed || !filed.ok) {
                        refresh();
                        throw new Error('The note was saved on this record, but could not be filed on its perma links: '
                            + (window.LWAuth.errorText(filed, 'no reason given'))
                            + ' Use + Link Note on those records to attach it.');
                    }
                }
                if (!known && window.EntityNotes) window.EntityNotes.typeKit().invalidate();
                refresh();
            }
        });
    };

    ED.deleteEvent = async function (entityId, row, refresh) {
        if (!confirm('Delete the note "' + (row.eventTitle || '(no title)') + '"'
                   + (row.eventDate ? ' (' + row.eventDate + ')' : '') + ' from this record?\n\n'
                   + 'The note itself stays in Leonardo (it may be linked to other records); only its link to this record '
                   + 'is removed. This cannot be undone.')) return;
        const r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/events/' + row.entityEventXrefId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Delete failed')); return; }
        refresh();
    };

    // ===== Files ============================================================
    //
    // Many-to-many association between any entity and a LeonardoFile (catalog
    // row + on-disk pointer). Pointer-only -- moving / deleting the underlying
    // nook file is what the conflict modal in reading-nook.js handles.
    //
    // Four "+ Add Files" sources, all converging on editFileMetadata():
    //
    //   1. From Reading Nook        -- pick a file in inbox/archive/leonardo,
    //                                  create a new LeonardoFile + xref
    //   2. From existing Library     -- typeahead /api/leonardo-files, attach
    //                                  the existing LeonardoFileID to a new xref
    //   3. Upload from this device   -- multipart upload -> LeonardoFiles\ folder,
    //                                  then create xref
    //   4. From a typed local path   -- Tony types/pastes an absolute path,
    //                                  create LeonardoFile pointing at it

    var FILE_SRC_NOOK_INBOX    = 'NookInbox';
    var FILE_SRC_NOOK_ARCHIVE  = 'NookArchive';
    var FILE_SRC_NOOK_LEONARDO = 'NookLeonardo';
    var FILE_SRC_LIBRARY       = 'LeonardoFiles';
    var FILE_SRC_TYPED_PATH    = 'ExternalPath';

    function fileTypeBadge(name) {
        if (!name) return 'FILE';
        var dot = name.lastIndexOf('.');
        if (dot < 0) return 'FILE';
        return name.substring(dot + 1).toUpperCase();
    }

    function fmtBytes(n) {
        if (n == null) return '';
        n = Number(n) || 0;
        if (n < 1024)              return n + ' B';
        if (n < 1024 * 1024)       return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / (1024 * 1024)).toFixed(1) + ' MB';
        return (n / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
    }

    // Story 3520: the Files tab is ORGANIZED -- categories, staging, grouping,
    // sorting, Important, import -- by contacts/entity-files.js. The loader hands
    // it the first page of /files/filed ({rows, groups, ...}). The plain card list
    // below stays as the fallback for a page that does not load entity-files.js.
    ED.renderFiles = function (paneEl, data, entityId, refresh) {
        if (window.EntityFiles && window.FileKit && data && !Array.isArray(data)) {
            window.EntityFiles.render(paneEl, data, entityId, refresh);
            return;
        }
        renderFilesPlain(paneEl, Array.isArray(data) ? data : (data && data.rows) || [], entityId, refresh);
    };

    function renderFilesPlain(paneEl, rows, entityId, refresh) {
        var cardsHtml = (rows && rows.length) ? rows.map(function (f) {
            var name        = f.name        || '(unnamed)';
            var description = f.description || '';
            var notes       = f.notes       || '';
            var badge       = fileTypeBadge(f.filePath || f.name);
            var sizeLabel   = f.sizeBytes != null ? fmtBytes(f.sizeBytes) : '';
            return '<div class="ed-card" data-xrefid="' + ED.escapeHtml(f.xrefId) + '"' + rowTid('file') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(badge) + '</span>'
                +         '<span class="name">' + ED.escapeHtml(name) + '</span>'
                +         '<span class="dates">' + ED.escapeHtml(badge) + (sizeLabel ? ' &middot; ' + ED.escapeHtml(sizeLabel) : '') + '</span>'
                +     '</div>'
                +     (description ? '<div class="notes"><b>' + ED.escapeHtml(description) + '</b></div>' : '')
                +     (notes       ? '<div class="notes">'  + ED.escapeHtml(notes) + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         (ED.canReadFile(f) ? '<button type="button" class="ed-btn" data-act="reader" data-testid="entity-file-reader-btn">Reader</button>' : '')
                +         (ED.canListenToFile(f) ? '<button type="button" class="ed-btn" data-act="listen" data-testid="entity-file-listen-btn">Listen</button>' : '')
                +         '<button type="button" class="ed-btn"        data-act="open" data-testid="entity-file-open-btn">Open</button>'
                +         ED.pinButton('file', f.xrefId, name, [badge, description].filter(Boolean).join(' | '))
                +         (f.leonardoFileId ? '<button type="button" class="ed-btn" data-act="print" data-testid="entity-file-print-btn">Print</button>' : '')
                +         '<button type="button" class="ed-btn"        data-act="edit" data-testid="entity-file-edit-btn">Edit</button>'
                +         '<button type="button" class="ed-btn danger" data-act="del"  data-testid="entity-file-del-btn">Remove</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No files attached. Click <b>+ Add Files</b> to associate one.</div>';
        paneEl.innerHTML = actionsBarHtml('Add Files', 'file') + cardsHtml;
        wirePaneActions(paneEl, rows || [], {
            add:  function ()  { ED.openFileSourcePicker(entityId, refresh); },
            // Pass leonardoFileId on edit so the save body satisfies the
            // server's "either leonardoFileId or sourcePath is required" rule
            // -- without it, Edit on any existing row 400s.
            edit: function (f) { ED.editFileMetadata(entityId, {
                xrefId: f.xrefId, leonardoFileId: f.leonardoFileId, row: f
            }, refresh); },
            del:  function (f) { ED.deleteFile(entityId, f, refresh); }
        });
        // The Open buttons are a separate action not covered by wirePaneActions
        // (which only knows about edit/del/add). Wire them directly off the
        // rendered card list.
        paneEl.querySelectorAll('.ed-card').forEach(function (cardEl) {
            var openBtn = cardEl.querySelector('[data-act="open"]');
            if (!openBtn) return;
            var xref = cardEl.getAttribute('data-xrefid');
            var row = (rows || []).find(function (r) { return String(r.xrefId) === String(xref); });
            if (!row) return;
            openBtn.addEventListener('click', function () { ED.openLeonardoFile(row, { native: true }); });
            var readerBtn = cardEl.querySelector('[data-act="reader"]');
            if (readerBtn) readerBtn.addEventListener('click', function () { ED.openInReader(row); });
            var listenBtn = cardEl.querySelector('[data-act="listen"]');
            if (listenBtn) listenBtn.addEventListener('click', function () { ED.listenToFile(row); });
            var printBtn = cardEl.querySelector('[data-act="print"]');
            if (printBtn) printBtn.addEventListener('click', function () { ED.printLeonardoFile(row); });
        });
    }

    /**
     * Print an attached file through the standard Leonardo Print Menu (display /
     * print / save / email) instead of opening it. The server turns the file into
     * a PDF -- a PDF as-is, markdown and HTML rendered -- and the picker does the
     * rest. (Tony, 2026-09-09, Entity Manager IV Files tab.)
     */
    ED.printLeonardoFile = function (row) {
        if (!row || !row.leonardoFileId) return;
        var name = row.name || lastPathSegment(fileNameForRow(row)) || 'Document';
        window.Picasso.printPagePdf({
            kind:     'leonardo-file',
            id:       row.leonardoFileId,
            title:    name,
            filename: name.replace(/\.[A-Za-z0-9]{1,5}$/, '') + '.pdf'
        });
    };

    // Which attached files the Leonardo Reader can render. Same list the
    // Reading Nook uses (isReaderSupportedName in reading-nook.js), so a
    // document opens the same way whether it is reached from the nook, from a
    // Research project, or from an entity's Files tab.
    // HTML joined the list with Story 5648: the Reader reads a saved page or a debate
    // transcript in its own type, with highlights and quoting, like the Reading Nook does.
    var READER_EXT_RE     = /[.](pdf|png|jpe?g|gif|webp|svg|bmp|txt|md|markdown|log|csv|html?)$/i;
    // Text-shaped documents must come back as bytes, not as server-rendered
    // HTML -- the Reader reflows them itself and marking needs the raw text.
    var READER_RAW_EXT_RE = /[.](txt|md|markdown|log|html?)$/i;

    // The extension to judge by: the file on disk knows, while the catalog
    // Name may have been renamed without one.
    // An encrypted twin (.leosec) opens as its original, so it is judged by the original's
    // name (Story 3533).
    function fileNameForRow(row) {
        return String((row && (row.filePath || row.name)) || '').replace(/[.]leosec$/i, '');
    }

    // Last path segment, tolerating either separator. Written with
    // charCode escapes so the file stays 7-bit ASCII and needs no regex
    // escaping of the Windows separator.
    function lastPathSegment(path) {
        var s = String(path || '');
        var i = Math.max(s.lastIndexOf('/'), s.lastIndexOf(String.fromCharCode(92)));
        return i >= 0 ? s.substring(i + 1) : s;
    }

    function fileExtOf(row) {
        var m = /[.]([A-Za-z0-9]+)$/.exec(fileNameForRow(row));
        return m ? m[1].toLowerCase() : '';
    }

    function isReaderSupportedRow(row) {
        return READER_EXT_RE.test(fileNameForRow(row));
    }

    // Story 5648: which attached files Listen can read aloud. Text-shaped files are
    // read from /raw (a Think Tank transcript keeps a voice per speaker); a PDF or a
    // Word file has its words pulled out on the server first (/text), the same
    // extraction the Reading Nook's Listen uses.
    var LISTEN_RAW_EXT_RE     = /[.](txt|md|markdown|log|csv|html?)$/i;
    var LISTEN_EXTRACT_EXT_RE = /[.](pdf|docx?)$/i;

    /** A catalog file the Leonardo Reader can open (the Files tab's Reader button). */
    ED.canReadFile = function (row) {
        return !!(row && row.leonardoFileId && isReaderSupportedRow(row));
    };

    /** A catalog file Listen can read aloud (the Files tab's Listen button). */
    ED.canListenToFile = function (row) {
        if (!row || !row.leonardoFileId || !ED.canListen()) return false;
        const name = fileNameForRow(row);
        return LISTEN_RAW_EXT_RE.test(name) || LISTEN_EXTRACT_EXT_RE.test(name);
    };

    /** Read one attached file aloud. Its place is kept per file, across devices. */
    ED.listenToFile = function (row) {
        if (!ED.canListenToFile(row) || !listenReady()) return;
        const id    = row.leonardoFileId;
        const title = row.name || lastPathSegment(fileNameForRow(row)) || 'Document';
        const key   = 'lfile:' + id + ':listen';
        if (!LISTEN_EXTRACT_EXT_RE.test(fileNameForRow(row))) {
            window.NookReader.playUrl('/api/leonardo-files/' + encodeURIComponent(id) + '/raw', title, key);
            return;
        }
        if (window.Picasso.busy) window.Picasso.busy('Preparing audio');
        Promise.resolve(window.LWAuth.apiGet('/api/leonardo-files/' + encodeURIComponent(id) + '/text')).then(function (r) {
            if (window.Picasso.idle) window.Picasso.idle();
            const d = (r && r.data) || {};
            if (!r || !r.ok) { window.Picasso.error(window.LWAuth.errorText(r, 'Could not load that file to read.')); return; }
            if (!d.text) { window.Picasso.message(d.message || 'Nothing readable to read aloud.'); return; }
            window.NookReader.playText(d.text, title, key, true);
        }, function () {
            if (window.Picasso.idle) window.Picasso.idle();
            window.Picasso.error('Could not load that file to read.');
        });
    };

    /**
     * Open a LeonardoFile. By default anything the Leonardo Reader can render
     * -- PDF, image, txt / md / log / csv -- goes to the Reader (Story 2286),
     * which is the same reading surface the Leonardo Library uses from the
     * Reading Nook and from a Research project: reflowed text, text size and
     * typeface, search, bookmarks, highlights, excerpts, and the saved reading
     * position. The Reader authenticates itself and pulls the bytes with the
     * JWT, so all we hand it is the API URL of the file.
     *
     * opts.native = true skips the Reader and always hands the browser the
     * file in its natural state -- a PDF in the browser's own viewer, an image
     * as an image, anything else as a download the device opens with its
     * associated app. Story 3473: the Files tab shows a separate Reader button
     * for renderable files, and its Open button always takes this path, so
     * Tony picks per file which way it opens.
     *
     * The native path fetches the bytes here and hands the browser a blob in
     * a new tab. Files in the nook and markdown come back rendered to HTML by
     * /api/leonardo-files/{id}/content; files elsewhere on disk stream as-is.
     */
    ED.openLeonardoFile = async function (row, opts) {
        if (!row) return;
        var native = !!(opts && opts.native);

        if (!native && row.leonardoFileId && isReaderSupportedRow(row)) {
            ED.openInReader(row);
            return;
        }

        var url;
        if (row.leonardoFileId) {
            url = '/api/leonardo-files/' + encodeURIComponent(row.leonardoFileId) + '/content';
        } else if (row.filePath) {
            url = '/api/leonardo-files/by-path?path=' + encodeURIComponent(row.filePath);
        } else {
            alert('This row has no openable file.');
            return;
        }
        // Pre-open the placeholder window inside the user gesture (iOS Safari
        // pop-up blocker rule -- mirrors the Reading Nook pattern).
        var w = null;
        try { w = window.open('', '_blank'); } catch (e) {}
        if (w) {
            try { w.document.write('<html><body style="font:14pt system-ui;color:#666;padding:24px">Loading...</body></html>'); } catch (e) {}
        }
        try {
            var r = await window.LWAuth.apiGetForBlob(url);
            if (!r || !r.ok) {
                if (w) try { w.close(); } catch (e) {}
                alert(window.LWAuth.errorText(r, 'Could not open file.'));
                return;
            }
            var blobUrl = URL.createObjectURL(r.blob);
            if (w) { try { w.location.href = blobUrl; } catch (e) { window.open(blobUrl, '_blank'); } }
            else   { window.open(blobUrl, '_blank'); }
            setTimeout(function () { URL.revokeObjectURL(blobUrl); }, 300000);
        } catch (ex) {
            if (w) try { w.close(); } catch (e) {}
            alert('Could not open file: ' + ex.message);
        }
    };

    /**
     * Hand one attached file to the Leonardo Reader in a new tab. Text-shaped
     * documents go to /raw (unrendered bytes, which the Reader reflows itself);
     * PDFs and images go to /content, which streams them with the right
     * content type.
     */
    ED.openInReader = function (row) {
        var ext = fileExtOf(row);
        var raw = READER_RAW_EXT_RE.test('.' + ext);
        var fileUrl = '/api/leonardo-files/' + encodeURIComponent(row.leonardoFileId) +
                      (raw ? '/raw' : '/content');
        var title = row.name || lastPathSegment(fileNameForRow(row)) || 'Document';
        var readerUrl = '/reader/reader.html?src=' + encodeURIComponent(fileUrl) +
                        '&type=' + encodeURIComponent(ext) +
                        '&title=' + encodeURIComponent(title);
        window.open(readerUrl, '_blank');
    };

    /**
     * Step 1 of the + Add Files flow -- pick a source. Custom modal (NOT
     * openModal because there are no fields, just 4 large buttons).
     *
     * sink (optional) lets another Files tab -- a solution's (2026-10-15) --
     * reuse this same dialog and its Reading Nook / library pickers while
     * doing its own attaching: { title, uploadHint, upload(), nook(picks),
     * library(items), typedPath: false } -- picks are nook rows (viewKey,
     * name, ...) and items are { leonardoFileId, file }; both are arrays,
     * since 2026-10-29 the pickers take one or more. With no sink the
     * picks are linked to the record as always.
     */
    ED.openFileSourcePicker = function (entityId, refresh, sink) {
        var bg = ensureModal();
        document.getElementById('lwedTitle').textContent = (sink && sink.title) || 'Add a file association';
        document.getElementById('lwedErr').textContent = '';
        var form = document.getElementById('lwedForm');
        form.innerHTML = ''
            + '<div style="display:flex;flex-direction:column;gap:8px;margin:4px 0 8px 0">'
            +     '<button type="button" class="ed-btn primary" data-src="' + FILE_SRC_NOOK_INBOX + '" data-testid="ed-file-src-nook" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">\uD83D\uDCE5  From the Reading Nook</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">Pick one or more files from any nook -- Inbox, Archive, Articles, Videos, Rex Inbox, your own nooks and the rest.</div>'
            +     '</button>'
            +     '<button type="button" class="ed-btn primary" data-src="' + FILE_SRC_LIBRARY + '" data-testid="ed-file-src-library" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">\uD83D\uDCDA  From the Leonardo Files library</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">Pick one or more files already in the catalog (a file can be on many records).</div>'
            +     '</button>'
            +     '<button type="button" class="ed-btn primary" data-src="upload" data-testid="ed-file-src-upload" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">\u2B06  Upload from this device</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">' + ED.escapeHtml((sink && sink.uploadHint)
                          || 'Pick a file from your phone or PC -- it gets stored under LeonardoFiles\\ and pointed at.') + '</div>'
            +     '</button>'
            +     (sink && !sink.files ? '' :
                  '<button type="button" class="ed-btn primary" data-src="scan" data-testid="ed-file-src-scan" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">&#128247;  Scan with the camera</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">Snap a page or a document -- the same scanner the Reading Nook uses.</div>'
            +     '</button>'
            +     '<button type="button" class="ed-btn primary" data-src="paste" data-testid="ed-file-src-paste" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">&#128203;  Paste text</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">Save whatever text is on the clipboard as a text file.</div>'
            +     '</button>'
            +     '<button type="button" class="ed-btn primary" data-src="note" data-testid="ed-file-src-note" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">&#9998;  New text note</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">Type a note and add it as a text file.</div>'
            +     '</button>')
            +     (sink && !sink.typedPath ? '' :
                  '<button type="button" class="ed-btn primary" data-src="' + FILE_SRC_TYPED_PATH + '" data-testid="ed-file-src-path" style="padding:14px;text-align:left">'
            +         '<div style="font-weight:600">\u2328  From a typed path</div>'
            +         '<div style="font-size:9pt;opacity:0.8;margin-top:2px">Paste any local path (e.g. <code>C:\\Tony\\\u2026\\DMV.pdf</code>). LeonardoFile points at it -- file stays where it lives.</div>'
            +     '</button>')
            + '</div>';
        // Hide the default Save button; Cancel-only.
        var saveBtn = document.getElementById('lwedSave');
        saveBtn.style.display = 'none';
        bg.classList.add('open');

        form.querySelectorAll('[data-src]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var src = btn.getAttribute('data-src');
                closeModal();
                saveBtn.style.display = '';
                if (src === 'scan' || src === 'paste' || src === 'note') { ED.openCaptureFlow(src, entityId, refresh, sink); return; }
                if (src === 'upload' && sink)          { sink.upload();                                 return; }
                if (src === 'upload')                  { ED.openUploadFileFlow(entityId, refresh);      return; }
                if (src === FILE_SRC_LIBRARY)          { ED.openLibraryPicker(entityId, refresh, sink); return; }
                if (src === FILE_SRC_NOOK_INBOX)       { ED.openNookPicker(entityId, refresh, sink);    return; }
                if (src === FILE_SRC_TYPED_PATH)       { ED.openTypedPathFlow(entityId, refresh);       return; }
            });
        });
    };

    /**
     * Scan / Paste text / New text note on the Add a file menu. The tools are
     * the shared ones the Reading Nook uses (picasso-capture.js, loaded on
     * first use); each hands back a File, which is uploaded into Leonardo
     * Files and linked to the record under its own name -- or, when another
     * Files tab passed a sink with files(fileArray), handed to that sink.
     */
    var _capture = { loading: null };
    function loadCaptureScript() {
        if (window.PicassoCapture) return Promise.resolve(window.PicassoCapture);
        if (_capture.loading) return _capture.loading;
        _capture.loading = new Promise(function (resolve, reject) {
            const s = document.createElement('script');
            s.src = '/picasso-capture.js?v=1';
            s.onload  = function () { window.PicassoCapture ? resolve(window.PicassoCapture) : reject(new Error('picasso-capture.js loaded but did not start')); };
            s.onerror = function () { _capture.loading = null; reject(new Error('picasso-capture.js could not be loaded')); };
            document.head.appendChild(s);
        });
        return _capture.loading;
    }

    /** The shared pick-one-or-more list (picasso-multi-pick.js), loaded on
     *  first use like the capture tools, so no page needs another script tag. */
    var _multiPick = { loading: null };
    function loadMultiPick() {
        if (window.PicassoMultiPick) return Promise.resolve(window.PicassoMultiPick);
        if (_multiPick.loading) return _multiPick.loading;
        _multiPick.loading = new Promise(function (resolve, reject) {
            const s = document.createElement('script');
            s.src = '/picasso-multi-pick.js?v=1';
            s.onload  = function () { window.PicassoMultiPick ? resolve(window.PicassoMultiPick) : reject(new Error('picasso-multi-pick.js loaded but did not start')); };
            s.onerror = function () { _multiPick.loading = null; reject(new Error('picasso-multi-pick.js could not be loaded')); };
            document.head.appendChild(s);
        });
        return _multiPick.loading;
    }
    async function openMultiPick(opts) {
        let M;
        try { M = await loadMultiPick(); }
        catch (e) {
            if (window.Picasso) await Picasso.error('The file picker did not load: ' + e.message);
            return null;
        }
        return M.open(opts);
    }

    /** Link a file already in Leonardo Files to the record under its own name,
     *  with no details dialog -- how a batch of picks is added. Throws an Error
     *  that names the file; answers the link response. */
    async function linkCatalogFile(entityId, leonardoFileId, name) {
        const link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/files',
            { leonardoFileId: leonardoFileId, sourcePath: null, sourceType: null,
              name: name, description: name, notes: '' }, 'POST');
        if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, name + ' could not be linked to this record'));
        return link;
    }

    /** Add a batch of picks one after another under one busy overlay: each
     *  addOne(pick) resolves with its link response or throws. Then refresh,
     *  and list anything that failed (that message waits for OK). */
    async function addPicksInTurn(picks, addOne, refresh) {
        const answers = [], failed = [];
        function label(i) { return 'Adding ' + (i + 1) + ' of ' + picks.length + ': ' + picks[i].name; }
        if (window.Picasso) Picasso.busy(label(0));
        try {
            for (let i = 0; i < picks.length; i++) {
                if (window.Picasso && i > 0) { Picasso.busy(label(i)); Picasso.idle(); }
                try { answers.push(await addOne(picks[i])); }
                catch (e) { failed.push(picks[i].name + ' -- ' + (e && e.message ? e.message : 'no reason given')); }
            }
        } finally { if (window.Picasso) Picasso.idle(); }
        refresh();
        ED.sayFiledOn(ED.filedOnNames(answers));
        if (failed.length && window.Picasso) {
            await Picasso.error((answers.length ? answers.length + ' added. ' : '')
                + 'These were not added:\n\n' + failed.join('\n'));
        }
    }

    /** Upload one File into Leonardo Files and link it to the record. Throws
     *  an Error that says what went wrong; answers the link response. */
    ED.uploadAndLinkOne = async function (entityId, file) {
        const fd = new FormData();
        fd.append('file', file, file.name);
        const r = await window.LWAuth.apiPostForm('/api/leonardo-files/upload', fd);
        const id = r && r.ok && r.data ? r.data.leonardoFileId : 0;
        if (!id) throw new Error(window.LWAuth.errorText(r, 'Uploading ' + file.name + ' failed'));
        const name = (r.data.name || file.name);
        const link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/files',
            { leonardoFileId: id, sourcePath: null, sourceType: null,
              name: name, description: name, notes: '' }, 'POST');
        if (!link || !link.ok) {
            throw new Error(window.LWAuth.errorText(link, name + ' was uploaded to Leonardo Files but could not be linked to this record'));
        }
        return link;
    };

    ED.openCaptureFlow = async function (kind, entityId, refresh, sink) {
        let C;
        try { C = await loadCaptureScript(); }
        catch (e) {
            if (window.Picasso) await Picasso.error('The scan and note tools did not load: ' + e.message);
            return;
        }
        const answers = [];
        const save = async function (file) {
            if (sink) { await sink.files([file]); return; }
            answers.push(await ED.uploadAndLinkOne(entityId, file));
        };
        const finish = function () {
            if (sink) return;
            refresh();
            ED.sayFiledOn(ED.filedOnNames(answers.splice(0)));
        };
        const opts = { save: save, where: (sink && sink.where) || 'this record' };
        if (kind === 'scan') {
            opts.title = 'Scan';
            opts.onClose = function (count) { if (count > 0) finish(); };
            C.scan(opts);
        } else if (kind === 'paste') {
            opts.onSaved = finish;
            C.pasteText(opts);
        } else {
            opts.onSaved = finish;
            C.newNote(opts);
        }
    };

    /**
     * Step 2 (Nook source) -- pick a file from inbox / archive / leonardo.
     * Secure is explicitly NOT offered (web has no access to its contents,
     * and the catalog already disallows references to secure paths from the
     * web side per ReadingNookService.isWebVisible).
     */
    /** Short local date for a nook file's last-modified time (mm/dd/yyyy). */
    function fmtNookDate(millis) {
        if (!millis) return '';
        var dt = new Date(millis);
        return String(dt.getMonth() + 1).padStart(2, '0') + '/'
             + String(dt.getDate()).padStart(2, '0') + '/'
             + dt.getFullYear();
    }

    var NOOK_PICKER_ROW_CAP = 200;

    // Built-in nooks keep their historical sourceType names; any other nook
    // (Articles, Videos, a custom nook) gets 'Nook' + its key.
    var NOOK_SOURCE_TYPES = {
        inbox: FILE_SRC_NOOK_INBOX, archive: FILE_SRC_NOOK_ARCHIVE, leonardo: FILE_SRC_NOOK_LEONARDO,
        notifications: 'NookNotifications', felix: 'NookFelix', trash: 'NookTrash'
    };

    /** Every active, web-visible nook, in the Reading Nook's own order. Null
     *  (after telling the user why) when the list could not be read. */
    async function loadPickerNooks() {
        var r = await window.LWAuth.apiGet('/api/reading-nook/shelves');
        if (!r || !r.ok || !Array.isArray(r.data)) {
            if (window.Picasso) await Picasso.error(window.LWAuth.errorText(r, 'Could not read the list of nooks'));
            return null;
        }
        return r.data
            .filter(function (s) { return s && s.key && s.isWebVisible && s.isActive !== false; })
            .map(function (s) {
                var key = String(s.key).toLowerCase();
                return {
                    key:        key,
                    label:      s.label || key,
                    sourceType: NOOK_SOURCE_TYPES[key] || ('Nook' + key.charAt(0).toUpperCase() + key.slice(1))
                };
            });
    }

    ED.openNookPicker = async function (entityId, refresh, sink) {
        // Fetch every nook list in parallel, merge with a leading view label.
        // Every web-visible nook is a valid source (Secure is never web-visible
        // and cannot be listed). The from-nook endpoint copies the pick into
        // Leonardo Files, so it gets "checked in" regardless of which nook it
        // came from. sourceType is display-only here; staging uses viewKey.
        // The nook list comes from the server (2026-10-12): a hardcoded list of
        // six left Articles, Videos, Rex Inbox and every custom nook unfindable.
        var views = await loadPickerNooks();
        if (!views) return;
        // Genuinely in parallel -- sequential round trips are phone latencies
        // stacked end to end before the picker can open.
        var lists = await Promise.all(views.map(function (v) {
            return window.LWAuth.apiGet('/api/reading-nook?view=' + v.key)
                .then(function (r) { return r; })
                .catch(function () { return null; });
        }));
        var results = [];
        views.forEach(function (v, i) {
            var r = lists[i];
            if (!r || !r.ok || !Array.isArray(r.data)) return;
            r.data.forEach(function (f) {
                results.push({
                    viewKey:    v.key,
                    viewLabel:  v.label,
                    sourceType: v.sourceType,
                    name:       f.name,
                    size:       f.size,
                    modified:   f.modifiedMillis
                });
            });
        });
        if (!results.length) {
            if (window.Picasso) await Picasso.message('The Reading Nook has no files in any nook.');
            return;
        }
        // Newest first. The file Tony wants to attach is almost always the one
        // he just dropped in a nook, and with six buckets merged together a
        // bucket-order list buries it (Story 2436).
        results.sort(function (a, b) { return (b.modified || 0) - (a.modified || 0); });
        // Pick one or more (2026-10-29). Articles alone holds over a thousand
        // files -- the list draws the newest NOOK_PICKER_ROW_CAP and the filter
        // narrows to the rest.
        var picks = await openMultiPick({
            title:       'Pick files from the Reading Nook',
            hint:        'Tap each file you want, then Save. Ticks stay put while you filter.',
            placeholder: 'Filter by name',
            rowCap:      NOOK_PICKER_ROW_CAP,
            testid:      'ed-nook-pick',
            rows: results.map(function (f) {
                f.key = f.viewKey + '/' + f.name;
                f.sub = f.viewLabel + '  |  ' + fmtBytes(f.size) + (f.modified ? '  |  ' + fmtNookDate(f.modified) : '');
                return f;
            })
        });
        if (!picks || !picks.length) return;
        // Another Files tab (a solution's) does its own attaching.
        if (sink) { sink.nook(picks); return; }
        if (picks.length === 1) { addOneNookPick(entityId, picks[0], refresh); return; }
        var videos = [];
        await addPicksInTurn(picks, async function (pick) {
            var d = await stageNookPick(pick);
            if (d.video) videos.push(pick.name);
            return linkCatalogFile(entityId, d.leonardoFileId, d.name || pick.name);
        }, refresh);
        if (videos.length && window.Picasso) {
            await Picasso.message(videos.join(', ') + (videos.length === 1 ? ' is a video, so it was' : ' are videos, so they were')
                + ' linked in place in the Reading Nook and NOT copied into Leonardo Files. '
                + 'That keeps videos out of the repository.', { title: 'Videos Linked In Place' });
        }
    };

    /** Stage 1 -- promote a nook file into the LeonardoFile catalog. For Inbox /
     *  Archive sources the server copies it into LeonardoFiles\ first; the
     *  Leonardo source is registered in place. Answers the response data
     *  (leonardoFileId, name, copied, video) or throws an Error naming the file. */
    async function stageNookPick(pick) {
        var rFn = await window.LWAuth.apiPost('/api/leonardo-files/from-nook',
            { view: pick.viewKey, name: pick.name }, 'POST');
        if (!rFn || !rFn.ok || !rFn.data) {
            throw new Error(window.LWAuth.errorText(rFn, 'Could not add ' + pick.name + ' to Leonardo Files'));
        }
        return rFn.data;
    }

    /** One nook pick: stage it, then the details dialog, as it always was. */
    async function addOneNookPick(entityId, pick, refresh) {
        var d;
        if (window.Picasso) Picasso.busy('Adding to library\u2026');
        try { d = await stageNookPick(pick); }
        catch (e) { if (window.Picasso) Picasso.idle(); if (window.Picasso) await Picasso.error(e.message); return; }
        if (window.Picasso) Picasso.idle();
        // A video is linked in place, never copied into Leonardo
        // Files (videos stay out of the repository) -- say so.
        if (d.video && window.Picasso) {
            await Picasso.message('"' + pick.name + '" is a video, so it is linked in place '
                + 'in the ' + pick.viewLabel + ' nook and was NOT copied into Leonardo Files. '
                + 'That keeps the video out of the repository.',
                { title: 'Video Linked In Place' });
        }
        ED.editFileMetadata(entityId, {
            leonardoFileId: d.leonardoFileId,
            renameOnDisk:   !!d.copied,
            defaults: { name: d.name || pick.name }
        }, refresh);
    }

    /**
     * Step 2 (Library source) -- pick one or more from the Leonardo Files
     * library. The SERVER searches (stp_LW_LeonardoFile_List: name or
     * description, newest first) once the typing pauses. It used to load the
     * newest 200 files and filter them here, so every older file was
     * unfindable -- the Progressive ID cards, 290 files back, were the report
     * (2026-09-26).
     */
    var LIBRARY_PICKER_LIMIT = 50;
    ED.openLibraryPicker = async function (entityId, refresh, sink) {
        async function searchFiles(needle) {
            var r = await window.LWAuth.apiGet('/api/leonardo-files?limit=' + LIBRARY_PICKER_LIMIT
                                               + '&q=' + encodeURIComponent(needle || ''));
            if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Could not search Leonardo Files'));
            return (Array.isArray(r.data) ? r.data : []).map(function (f) {
                return { key: String(f.leonardoFileId), id: f.leonardoFileId, file: f, name: f.name,
                         sub: (f.description && f.description !== f.name) ? f.description : '' };
            });
        }
        var picks = await openMultiPick({
            title:       'Pick from the Leonardo Files library',
            hint:        'Type to search. Tap each file you want, then Save. Ticks stay put while you search.',
            placeholder: 'Search names and descriptions',
            rowCap:      LIBRARY_PICKER_LIMIT,
            testid:      'ed-library-pick',
            search:      searchFiles
        });
        if (!picks || !picks.length) return;
        if (sink) { sink.library(picks.map(function (p) { return { leonardoFileId: p.id, file: p.file }; })); return; }
        if (picks.length === 1) {
            // One pick goes on to the details dialog with the catalog row's
            // name, description and notes filled in, as it always did.
            var f = picks[0].file;
            ED.editFileMetadata(entityId, {
                leonardoFileId: picks[0].id,
                defaults: { name: f.name, description: f.description, notes: f.notes }
            }, refresh);
            return;
        }
        await addPicksInTurn(picks, function (p) {
            return linkCatalogFile(entityId, p.id, p.file.name || p.name);
        }, refresh);
    };

    /**
     * Step 2 (Upload source) -- pick a file with the native picker, upload
     * it to /api/leonardo-files/upload, then move on to metadata.
     */
    ED.openUploadFileFlow = function (entityId, refresh) {
        var input = document.createElement('input');
        input.type = 'file';
        input.multiple = true;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('change', async function () {
            if (!input.files || !input.files.length) { document.body.removeChild(input); return; }
            // Several picked: upload and link them all, each under its own file name.
            if (input.files.length > 1) {
                var many = Array.prototype.slice.call(input.files);
                document.body.removeChild(input);
                await ED.uploadAndLinkFiles(entityId, many, refresh);
                return;
            }
            var f = input.files[0];
            var fd = new FormData();
            fd.append('file', f, f.name);
            if (window.Picasso) Picasso.busy('Uploading ' + f.name + '\u2026');
            var r;
            try { r = await window.LWAuth.apiPostForm('/api/leonardo-files/upload', fd); }
            finally { if (window.Picasso) Picasso.idle(); }
            document.body.removeChild(input);
            if (!r || !r.ok) { alert('Upload failed: ' + (r && r.data && r.data.error ? r.data.error : 'unknown')); return; }
            var leonardoFileId = r.data && r.data.leonardoFileId;
            if (!leonardoFileId) { alert('Upload returned no LeonardoFileID.'); return; }
            ED.editFileMetadata(entityId, {
                leonardoFileId: leonardoFileId,
                renameOnDisk:   true,
                defaults: { name: f.name }
            }, refresh);
        });
        input.click();
    };

    /**
     * Several files from the Upload source at once: each is uploaded into
     * Leonardo Files and linked to the record with no metadata dialog -- its
     * name and description are the file's own name. Failures are listed at the end.
     */
    ED.uploadAndLinkFiles = async function (entityId, files, refresh) {
        var done = 0, failed = [], filedOn = [];
        // Picasso.busy() counts its calls and idle() releases one, so the
        // overlay is raised ONCE for the whole batch. Each later file only
        // relabels it -- a busy()+idle() pair leaves the count at one.
        function label(i) { return 'Uploading ' + (i + 1) + ' of ' + files.length + ': ' + files[i].name + '...'; }
        if (window.Picasso) Picasso.busy(label(0));
        try {
            for (var i = 0; i < files.length; i++) {
                var f = files[i];
                if (window.Picasso && i > 0) { Picasso.busy(label(i)); Picasso.idle(); }
                try {
                    var link = await ED.uploadAndLinkOne(entityId, f);
                    done++;
                    ED.filedOnNames([link], filedOn);
                } catch (e) {
                    failed.push(f.name + ' -- ' + (e && e.message ? e.message : 'no reason given'));
                }
            }
        } finally {
            if (window.Picasso) Picasso.idle();
        }
        refresh();
        if (window.Picasso && Picasso.toast) {
            Picasso.toast(done + (done === 1 ? ' file' : ' files') + ' added'
                + (filedOn.length ? '; also filed on ' + filedOn.join(', ') + ' (Perma Links)' : ''));
        }
        if (failed.length) alert('These did not upload:\n\n' + failed.join('\n'));
    };

    /**
     * Step 2 (Typed-path source) -- paste an absolute path. LeonardoFile gets
     * a new pointer at that path; the file itself stays where it lives.
     */
    ED.openTypedPathFlow = function (entityId, refresh) {
        ED.openModal({
            title: 'Attach a file by typed path',
            fields: [
                { key: 'sourcePath', label: 'Absolute path on this PC',
                  type: 'text', maxlength: 4000, required: true, value: '' }
            ],
            onSave: async function (data) {
                var p = (data.sourcePath || '').trim();
                if (!p) throw new Error('Path is required.');
                // Auto-derive Name default from the trailing path segment.
                var name = p;
                var slash = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
                if (slash >= 0) name = p.substring(slash + 1);
                return function () {
                    ED.editFileMetadata(entityId, {
                        leonardoFileId: 0,
                        sourcePath:     p,
                        sourceType:     FILE_SRC_TYPED_PATH,
                        defaults: { name: name }
                    }, refresh);
                };
            }
        });
    };

    /**
     * Step 3 -- Name / Description / Notes. Called from every source path.
     * For edit (opts.xrefId present + opts.row populated) the form is
     * pre-filled with the existing xref's metadata.
     */
    ED.editFileMetadata = function (entityId, opts, refresh) {
        opts = opts || {};
        var isEdit = !!opts.xrefId;
        var defaults = opts.defaults || (opts.row || {});
        ED.openModal({
            title: isEdit ? 'Edit file association' : 'Save file association',
            fields: [
                { key: 'name',        label: 'Name',         type: 'text',     maxlength: 200, required: true,
                  value: defaults.name || '' },
                { key: 'description', label: 'Description',  type: 'text',     maxlength: 500,
                  value: defaults.description || defaults.name || '' },
                { key: 'notes',       label: 'Notes',        type: 'textarea',
                  value: defaults.notes || '' }
            ],
            onSave: async function (data) {
                if (!data.name || !data.name.trim()) throw new Error('Name is required.');
                // Renaming here renames the actual file too, for the sources
                // that put a fresh copy in Leonardo Files (upload, Reading
                // Nook). Without this the copy on disk keeps whatever name the
                // phone gave the photo while the catalog shows the new one.
                if (opts.renameOnDisk && opts.leonardoFileId
                        && data.name.trim() !== (defaults.name || '')) {
                    var rn = await window.LWAuth.apiPost(
                        '/api/leonardo-files/' + encodeURIComponent(opts.leonardoFileId) + '/rename',
                        { name: data.name.trim() }, 'POST');
                    // A pointer we do not own comes back renamed:false with a
                    // reason and that is fine -- only a hard failure stops the
                    // save, so the disk name and the catalog name never quietly
                    // drift apart again.
                    if (!rn || !rn.ok) {
                        throw new Error('Could not rename the file: '
                            + (window.LWAuth.errorText(rn, 'unknown')));
                    }
                }
                var body = {
                    leonardoFileId: opts.leonardoFileId ? parseInt(opts.leonardoFileId, 10) : 0,
                    sourcePath:     opts.sourcePath || null,
                    sourceType:     opts.sourceType || null,
                    name:           data.name.trim(),
                    description:    data.description || '',
                    notes:          data.notes || ''
                };
                var url = isEdit
                    ? '/api/entities/' + entityId + '/files/' + opts.xrefId
                    : '/api/entities/' + entityId + '/files';
                var r = await window.LWAuth.apiPost(url, body, isEdit ? 'PUT' : 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Save failed'));
                refresh();
                if (!isEdit) ED.sayFiledOn(ED.filedOnNames([r]));
            }
        });
    };

    /**
     * Cancel-default confirm before removing the entity<->file xref. The
     * underlying LeonardoFile catalog row stays -- it might be associated
     * with other entities, and even if it isn't, keeping it means the
     * library picker can re-attach it later. Erasing the catalog row is
     * a separate (library-side) operation.
     */
    ED.deleteFile = async function (entityId, row, refresh) {
        var name = row && row.name ? row.name : 'this file';
        if (!confirmCancelDefault(
                'Remove this file association?',
                '"' + name + '" stays in the Leonardo Files library -- only the link to this entity is removed.')) {
            return;
        }
        var r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/files/' + row.xrefId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Remove failed')); return; }
        refresh();
    };

    /**
     * Small synchronous-style confirm with Cancel as the default. Built on
     * the existing modal element. Unlike window.confirm(), button focus is
     * on Cancel -- pressing Enter cancels.
     *
     * Returns a Promise<boolean>; we expose a sync-looking caller via async/
     * await at call sites. Keeps the rule "every Delete button defaults to
     * Cancel" honest without forcing every caller into a custom modal.
     */
    function confirmCancelDefault(title, subtitle) {
        // Use the native confirm() -- it's the lightest path and on every
        // mobile browser the default-button IS Cancel (the OS-supplied
        // button order puts Cancel first). On desktop browsers OK is the
        // default; we accept that asymmetry rather than block on a custom
        // modal here. The skill rule is documented; if Tony hits a desktop
        // misfire, we'll upgrade to a custom modal.
        return window.confirm(title + '\n\n' + (subtitle || ''));
    }

    // ===== Printing a record -> the Leonardo Print Menu =====================
    //
    // Story 2464 round 4. Tony: "We should never have report buttons. We
    // should always use the standard Leonardo Print Menu." So there is no
    // Report button and no report modal anywhere. Every record page carries
    // the STANDARD header Print control (quick-launch.js relocates it into
    // the icon band), and printing a record builds its one-page story -- the
    // record's fields, everything connected to it, what has happened oldest
    // first -- into an offscreen node and hands it to Picasso.printThisPage,
    // which opens the full Leonardo Print Menu (display / this device / home
    // printer / download / notifications / nook / email / email to contact /
    // link to contact).

    function reportHost() {
        let node = document.getElementById('lwedReport');
        if (node) return node;
        const host = document.createElement('div');
        host.id = 'lwedReportHost';
        host.setAttribute('aria-hidden', 'true');
        host.style.position = 'absolute';
        host.style.left = '-99999px';
        host.style.top = '0';
        host.style.width = '700px';
        host.innerHTML = '<div class="ed-report" id="lwedReport"></div>';
        document.body.appendChild(host);
        return document.getElementById('lwedReport');
    }

    // Story 3477: the call logs linked to a record, each with its parties and
    // their calls, for the printed report.
    //
    // The list endpoint returns headline counts only (11 parties, 2 calls) --
    // the calls themselves live behind the per-log detail endpoint, so each
    // log costs one more GET. They go out together rather than in a loop: a
    // record has a handful of logs, not a page of them.
    //
    // activeOnly=false on purpose. A resolved party is still part of the story
    // of how the matter was worked, and a printout that silently drops the
    // settled creditors is worse than no printout at all.
    //
    // extraCallLogs is for the call log's OWN record page: /api/call-logs
    // lists the logs LINKED to an entity, and a log is not linked to itself,
    // so without this the one record the page is actually about would be the
    // only one missing from the printout.
    async function loadCallLogsForReport(entityId, listData, extraCallLogs) {
        const list = (listData && listData.callLogs) ? listData.callLogs : (listData || []);
        const details = await Promise.all(list.map(function (c) {
            return window.LWAuth.apiGet('/api/call-logs/' + c.CallLogID + '?activeOnly=false');
        }));
        const out  = [];
        const seen = {};
        (extraCallLogs || []).forEach(function (d) {
            if (!d || !d.callLog) return;
            seen[d.callLog.CallLogID] = true;
            out.push(d);
        });
        for (let i = 0; i < details.length; i++) {
            const r = details[i];
            if (!r || !r.ok || !r.data || !r.data.callLog) continue;
            if (seen[r.data.callLog.CallLogID]) continue;
            seen[r.data.callLog.CallLogID] = true;
            out.push(r.data);
        }
        return out;
    }

    /** The Calls section of the printed report. Empty string when the record
     *  has no call logs -- an empty heading is dead weight on paper too. */
    function callLogsReportHtml(logs) {
        if (!logs || !logs.length) return '';
        let h = '<h2>Calls</h2>';
        logs.forEach(function (d) {
            const log     = d.callLog || {};
            const closed  = log.IsClosed === true || log.IsClosed === 1;
            h += '<div class="ed-rp-group">' + ED.escapeHtml(log.Title || '(no title)')
               + ' <span class="ed-rp-dim">' + (closed ? 'Closed' : 'Open') + '</span></div>';
            if (log.AboutText) {
                h += '<div class="ed-rp-desc">' + ED.escapeHtml(log.AboutText) + '</div>';
            }
            const parties = d.parties || [];
            if (!parties.length) {
                h += '<div class="ed-rp-row ed-rp-dim">No parties on this log.</div>';
                return;
            }
            parties.forEach(function (p) {
                h += '<div class="ed-rp-party">' + ED.escapeHtml(p.PartyName || '(no name)')
                   + (p.Phone ? ' <span class="ed-rp-dim">' + ED.escapeHtml(p.Phone) + '</span>' : '')
                   + ' <span class="ed-rp-dim">' + ED.escapeHtml(p.StatusName || 'Open') + '</span>'
                   + '</div>';
                const entries = (p.entries || []).slice().sort(function (a, b) {
                    return String(a.EntryDate || '').localeCompare(String(b.EntryDate || ''));
                });
                if (!entries.length) {
                    h += '<div class="ed-rp-row ed-rp-dim">No calls yet.</div>';
                    return;
                }
                entries.forEach(function (e) {
                    h += '<div class="ed-rp-row">'
                       +     '<span class="ed-rp-when">'
                       +         ED.escapeHtml(String(e.EntryDate || '').substring(0, 10)) + '</span> '
                       +     '<span class="ed-rp-dim">' + ED.escapeHtml(e.StatusName || '') + '</span>'
                       // pre-wrap in the report CSS: a call note is typed with
                       // real paragraphs and reads as one run-on blob without it.
                       +     '<div class="ed-rp-desc">' + ED.escapeHtml(e.Note || '') + '</div>'
                       + '</div>';
                });
            });
        });
        return h;
    }

    /**
     * Fills the offscreen report node for an entity and returns it.
     *   entityId -- the record's tblEntity id
     *   title    -- what to head the report with (the record's display name)
     *   fields   -- [[label, value], ...] from the record's own info view
     */
    async function buildEntityReport(entityId, title, fields, extraCallLogs, contactCard) {
        const body = reportHost();

        const [linksRes, eventsRes, callLogsRes] = await Promise.all([
            window.LWAuth.apiGet('/api/entities/' + entityId + '/links'),
            window.LWAuth.apiGet('/api/entities/' + entityId + '/events'),
            window.LWAuth.apiGet(ED.callLogsUrl(entityId))
        ]);
        const links  = (linksRes  && linksRes.ok)  ? (linksRes.data  || []) : [];
        const events = (eventsRes && eventsRes.ok) ? (eventsRes.data || []) : [];
        // Story 3477: the record's call logs print in FULL -- every party and
        // every call under it. The "Connected to" list below names them, which
        // is all a matter's printout used to carry: four log titles and not one
        // word of what was actually said on the phone. The calls ARE the
        // record on a matter like a debt resolution, so they belong in the
        // printout, not just a link to go read them on screen.
        const callLogs = await loadCallLogsForReport(entityId,
            (callLogsRes && callLogsRes.ok) ? callLogsRes.data : null, extraCallLogs);

        // h1/h2 on purpose: printThisPage's standard print stylesheet gives
        // them the polished Leonardo report look (blue underlined title,
        // spaced section heads); the .ed-rp-* details ride along as extraCss.
        let h = '<h1>' + ED.escapeHtml(title || 'Record') + '</h1>';

        h += (fields || []).filter(function (f) { return f[1] != null && String(f[1]).trim() !== ''; })
            .map(function (f) {
                return '<div class="ed-rp-field"><span class="ed-rp-lbl">' + ED.escapeHtml(f[0])
                     + '</span> ' + ED.escapeHtml(String(f[1])) + '</div>';
            }).join('');

        h += callLogsReportHtml(callLogs);

        // A contact card is the record, how to reach it, and its Timeline: no
        // linked records, no files, and no raw notes list -- the Timeline
        // already carries the notes (Tony, 2026-10-15).
        if (contactCard) {
            h += await contactCardReportHtml(entityId);
            const T = await loadTimelineScript();
            h += '<h2>Timeline</h2>' + await T.buildNotesHtml(entityId);
            body.innerHTML = h;
            return body;
        }
        h += connectedToReportHtml(links);

        h += '<h2>What has happened, oldest first</h2>';
        if (!events.length) {
            h += '<div class="ed-rp-row ed-rp-dim">No notes recorded.</div>';
        } else {
            events.slice().sort(function (a, b) {
                return String(a.eventDate || '').localeCompare(String(b.eventDate || ''));
            }).forEach(function (e) {
                h += '<div class="ed-rp-row"><span class="ed-rp-when">' + ED.escapeHtml(e.eventDate || '')
                   + '</span> ' + ED.escapeHtml(e.eventTitle || '(no title)')
                   + (e.eventType ? ' <span class="ed-rp-dim">' + ED.escapeHtml(e.eventType) + '</span>' : '')
                   + (e.eventDescriptionPreview
                        ? '<div class="ed-rp-desc">' + ED.escapeHtml(e.eventDescriptionPreview) + '</div>' : '')
                   + '</div>';
            });
        }

        body.innerHTML = h;
        return body;
    }

    /** The record's own contact details, printed in full: every active address
     *  on its lines (no address type), every phone as (972) 690-8115, every web address with its
     *  URL. Used in place of "Connected to" when the page asks for a contact
     *  card -- a vendor printout is its address and phone, not the records it
     *  happens to be linked to (Tony, 2026-10-15). */
    async function contactCardReportHtml(entityId) {
        const base = '/api/entities/' + entityId;
        const [addrRes, phoneRes, webRes] = await Promise.all([
            window.LWAuth.apiGet(base + '/addresses?activeOnly=true'),
            window.LWAuth.apiGet(base + '/phones'),
            window.LWAuth.apiGet(base + '/web-addresses')
        ]);
        const addrs  = (addrRes  && addrRes.ok)  ? (addrRes.data  || []) : [];
        const phones = (phoneRes && phoneRes.ok) ? (phoneRes.data || []) : [];
        const webs   = (webRes   && webRes.ok)   ? (webRes.data   || []) : [];

        let h = '';
        if (addrs.length) {
            h += '<h2>' + (addrs.length > 1 ? 'Addresses' : 'Address') + '</h2>';
            addrs.forEach(function (a) {
                const city    = [a.city, a.state].filter(Boolean).join(', ');
                const lines   = [a.streetAddress, a.secondStreetAddress,
                                 [city, a.zipCode].filter(Boolean).join(' ')].filter(Boolean);
                h += '<div class="ed-rp-row">' + lines.map(ED.escapeHtml).join('<br>') + '</div>';
            });
        }
        if (phones.length) {
            h += '<h2>' + (phones.length > 1 ? 'Phones' : 'Phone') + '</h2>';
            phones.forEach(function (p) {
                h += '<div class="ed-rp-row">'
                   + '<span class="ed-rp-when">' + ED.escapeHtml(p.phoneType || 'Phone') + '</span> '
                   + ED.escapeHtml(ED.phoneOneLine(p)) + '</div>';
            });
        }
        if (webs.length) {
            h += '<h2>' + (webs.length > 1 ? 'Web Addresses' : 'Web Address') + '</h2>';
            webs.forEach(function (w) {
                // name - url, as Tony asked; either alone when the other is blank.
                const line = [w.webAddressName, w.webAddress].filter(Boolean).join(' - ');
                h += '<div class="ed-rp-row">' + ED.escapeHtml(line) + '</div>';
            });
        }
        return h;
    }

    /** Every record linked to this one, grouped by type. */
    function connectedToReportHtml(links) {
        let h = '<h2>Connected to</h2>';
        if (!links.length) {
            h += '<div class="ed-rp-row ed-rp-dim">Nothing is linked to this record.</div>';
        } else {
            let lastType = null;
            links.forEach(function (row) {
                const t = row.entityTypeName || 'Other';
                if (t !== lastType) { h += '<div class="ed-rp-group">' + ED.escapeHtml(t) + '</div>'; lastType = t; }
                h += '<div class="ed-rp-row">' + ED.escapeHtml(row.displayName || '(no name)') + '</div>';
            });
        }
        return h;
    }

    // The report's own styles, shipped INTO the print document. printThisPage
    // clones bare HTML away from the page, so page stylesheets do not travel:
    // without this the story printed as an unstyled mess (Story 2464 comment
    // 4516). Rides on top of the standard print stylesheet's h1/h2 treatment.
    const REPORT_PRINT_CSS =
        '.ed-rp-field{padding:2px 0;}' +
        '.ed-rp-lbl{display:inline-block;min-width:110px;font-weight:600;color:#555;}' +
        '.ed-rp-group{font-weight:700;margin:12px 0 3px;color:#333;}' +
        '.ed-rp-row{padding:3px 0 3px 14px;border-bottom:1px dotted #E2E2E8;}' +
        '.ed-rp-when{display:inline-block;min-width:92px;color:#666;font-size:9.5pt;}' +
        '.ed-rp-dim{color:#8A8A93;font-size:9.5pt;font-style:italic;}' +
        '.ed-rp-desc{margin:3px 0 6px 106px;color:#444;font-size:10pt;white-space:pre-wrap;}' +
        // Story 3477: a party heading inside the Calls section, and multi-line
        // field values (the call-log page's own party/calls fields) keeping the
        // line breaks the user typed.
        '.ed-rp-party{margin:8px 0 2px 14px;font-weight:600;color:#333;}' +
        '.ed-rp-field{white-space:pre-wrap;}';

    /**
     * Wires the page's standard header Print control (already relocated into
     * the icon band, handlers preserved) to print the record through the
     * Leonardo Print Menu. provider() returns { entityId, title, fields } for
     * whatever record the page currently shows; contactCard: true prints the
     * record's own addresses / phones / web addresses in full instead of the
     * "Connected to" list of linked records.
     */
    ED.wireEntityPrint = function (provider) {
        const btn = document.getElementById('btnPrint');
        if (!btn || btn.getAttribute('data-lwed-print-wired')) return;
        btn.setAttribute('data-lwed-print-wired', '1');
        btn.addEventListener('click', async function () {
            const p = provider();
            if (!p || !p.entityId) return;
            // Story 4562: on the Timeline tab, Print prints the timeline --
            // only the notes ticked to print.
            if (ED.isTimelineTabActive()) {
                const T = await loadTimelineScript();
                const body = reportHost();
                body.innerHTML = await T.buildReportHtml(p.entityId, p.title);
                window.Picasso.printThisPage({
                    selector: '.ed-report',
                    title:    (p.title || 'Record') + ' - Timeline',
                    extraCss: REPORT_PRINT_CSS + T.REPORT_PRINT_CSS
                });
                return;
            }
            await buildEntityReport(p.entityId, p.title, p.fields, p.callLogs, p.contactCard);
            const timelineCss = p.contactCard ? (await loadTimelineScript()).REPORT_PRINT_CSS : '';
            window.Picasso.printThisPage({
                selector: '.ed-report',
                title:    p.title || 'Record',
                extraCss: REPORT_PRINT_CSS + timelineCss
            });
        });
    };

    // ===== Needs attention (Story 3496; its own tab + pins, Story 3504) ======
    //
    // A matter and a call log are not history, they are unfinished business.
    // Every unresolved matter and every open call log the record is part of,
    // each one tap from its own page -- plus every row Tony PINNED on the
    // record's tabs (an address, a phone, a vendor, a closed matter ...).
    //
    // Story 3496 drew this as a band above the tabs. On a record with a few open
    // things the band ate most of the phone screen (Story 3504), so it is now
    // the FIRST TAB -- "Needs attention (N)", amber -- and it exists only while
    // there is something in it. A record with nothing open and nothing pinned
    // shows no such tab at all: silence is the all-clear. The record's own tab
    // stays the one that opens by default; the amber tab beside it is the
    // signal.

    /** mm/dd/yyyy from an ISO date or date-time; '' when there is none. */
    function usDate(iso) {
        const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ''));
        return m ? (m[2] + '/' + m[3] + '/' + m[1]) : '';
    }

    ED.attentionUrls = function (entityId) {
        return {
            matters:  '/api/matters?entityId='   + entityId + '&includeClosed=false&sort=updated&pageSize=25',
            callLogs: '/api/call-logs?entityId=' + entityId + '&includeClosed=false&pageSize=25',
            pins:     '/api/entities/' + entityId + '/pins'
        };
    };

    /** The unresolved matters, open call logs and pins for a record, as plain
     *  arrays. A failed read is an empty list: this must never block a record. */
    ED.loadAttention = async function (entityId) {
        const urls = ED.attentionUrls(entityId);
        const [m, c, p] = await Promise.all([
            window.LWAuth.apiGet(urls.matters).catch(function () { return null; }),
            window.LWAuth.apiGet(urls.callLogs).catch(function () { return null; }),
            window.LWAuth.apiGet(urls.pins).catch(function () { return null; })
        ]);
        return {
            matters:  (m && m.ok && m.data && m.data.matters)  ? m.data.matters  : [],
            callLogs: (c && c.ok && c.data && c.data.callLogs) ? c.data.callLogs : [],
            pins:     (p && p.ok && p.data && p.data.pins)     ? p.data.pins     : []
        };
    };

    // ---- Pins ---------------------------------------------------------------
    //
    // Every tab row carries a Pin toggle beside its Unlink / Delete. The pin is
    // stored per record (EntityPin: record, kind = the tab, the row's own id)
    // with a snapshot of the row's label, so the Needs attention tab can draw
    // it even where the tabs are not loaded (Entity Manager IV). Where they ARE
    // loaded, the live row wins -- a renamed vendor shows its new name -- and a
    // pin whose row has gone from its (loaded) tab is not shown.

    const PIN_KIND_LABEL = {
        address: 'Address', phone: 'Phone', web: 'Web', login: 'Login', event: 'Event',
        communication: 'Note', vendor: 'Vendor', contact: 'Contact', userentity: 'Entity',
        pagelink: 'Link', dashboard: 'Dashboard', file: 'File', matter: 'Matter',
        calllog: 'Call log', list: 'List', task: 'Task list', checklist: 'Checklist',
        solution: 'Solution', codexpage: 'Codex page', codexsuite: 'Documentation Suite',
        insuranceplan: 'Insurance plan', eob: 'EOB'
    };

    // The state of the record whose Needs attention is on screen. One record
    // per page -- a record page, or the one selected in Entity Manager IV.
    const _attn = { entityId: 0, host: null, opts: {}, data: null };
    const _pinned = new Set();        // 'kind:id' pinned on _attn.entityId
    const _loadedKinds = new Set();   // kinds whose tab has rendered on this page

    function pinKey(kind, id) { return kind + ':' + id; }

    /** The Pin toggle for one tab row. label / detail / url describe the row as
     *  the Needs attention tab should show it; url is where tapping it goes
     *  (omit it and the tap opens the row's own tab). */
    ED.pinButton = function (kind, id, label, detail, url) {
        id = parseInt(id, 10) || 0;
        if (!id || !PIN_KIND_LABEL[kind]) return '';
        const on = _pinned.has(pinKey(kind, id));
        return pinToggleHtml(kind, id, on, label, detail, url) + ED.linkedButton(kind, id);
    };

    /** The "Linked" button for one tab row -- what that row's own record is
     *  linked to (picasso-linked.js; 2026-10-11). Rides beside Pin on every row
     *  that has one; a right-click on the row opens the same window. Empty for a
     *  kind that is not a linkable record (a checklist). */
    ED.linkedButton = function (kind, id) {
        return window.PicassoLinked ? window.PicassoLinked.button(kind, id) : '';
    };

    function pinToggleHtml(kind, id, on, label, detail, url) {
        return '<button type="button" class="ed-btn ed-pin-btn' + (on ? ' on' : '') + '" data-act="pin"'
             +     ' data-pin-kind="' + kind + '" data-pin-id="' + id + '"'
             +     ' data-pin-label="'  + ED.escapeHtml(label  || '') + '"'
             +     ' data-pin-detail="' + ED.escapeHtml(detail || '') + '"'
             +     ' data-pin-url="'    + ED.escapeHtml(url    || '') + '"'
             +     ' aria-pressed="' + (on ? 'true' : 'false') + '" data-testid="entity-pin-btn">'
             +     (on ? '&#9733; Pinned' : '&#9734; Pin')
             + '</button>';
    };

    /** Repaint every Pin toggle for one row after it changes. */
    function syncPinButtons(kind, id) {
        const on = _pinned.has(pinKey(kind, id));
        document.querySelectorAll('[data-act="pin"][data-pin-kind="' + kind + '"][data-pin-id="' + id + '"]')
            .forEach(function (b) {
                b.classList.toggle('on', on);
                b.setAttribute('aria-pressed', on ? 'true' : 'false');
                b.innerHTML = on ? '&#9733; Pinned' : '&#9734; Pin';
            });
    }

    /** The live tab row for a pin, read off its Pin toggle, or null. */
    function liveRowOf(kind, id) {
        const btn = document.querySelector('.ed-pane:not([data-pane="attention"]) [data-act="pin"]'
                                          + '[data-pin-kind="' + kind + '"][data-pin-id="' + id + '"]');
        if (!btn) return null;
        const pane = btn.closest('.ed-pane');
        return {
            label:  btn.getAttribute('data-pin-label')  || '',
            detail: btn.getAttribute('data-pin-detail') || '',
            url:    btn.getAttribute('data-pin-url')    || '',
            tab:    pane ? pane.getAttribute('data-pane') : ''
        };
    }

    async function togglePin(btn) {
        const entityId = parseInt(btn.getAttribute('data-entity-id'), 10) || _attn.entityId;
        const kind = btn.getAttribute('data-pin-kind');
        const id   = parseInt(btn.getAttribute('data-pin-id'), 10) || 0;
        if (!entityId || !kind || !id) return;
        const key = pinKey(kind, id);
        const base = '/api/entities/' + entityId + '/pins';
        btn.disabled = true;
        try {
            if (_pinned.has(key)) {
                const r = await window.LWAuth.apiPost(base + '/' + kind + '/' + id, null, 'DELETE');
                // 404 = already gone; the end state is the same.
                if (!r || (!r.ok && r.status !== 404)) { alert(window.LWAuth.errorText(r, 'Could not unpin it.')); return; }
                _pinned.delete(key);
                if (_attn.data) {
                    _attn.data.pins = _attn.data.pins.filter(function (p) { return pinKey(p.kind, p.pinnedId) !== key; });
                }
            } else {
                const r = await window.LWAuth.apiPost(base, {
                    kind: kind, pinnedId: id,
                    label:  btn.getAttribute('data-pin-label') || PIN_KIND_LABEL[kind],
                    detail: btn.getAttribute('data-pin-detail') || '',
                    url:    btn.getAttribute('data-pin-url') || ''
                }, 'POST');
                if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not pin it.')); return; }
                _pinned.add(key);
                if (_attn.data) {
                    _attn.data.pins = [r.data].concat(_attn.data.pins.filter(function (p) {
                        return pinKey(p.kind, p.pinnedId) !== key;
                    }));
                }
            }
        } finally {
            btn.disabled = false;
        }
        syncPinButtons(kind, id);
        paintAttention();
    }

    // One delegated handler for every Pin toggle, Unpin, and "go to its tab"
    // row on the page. Capture phase, so a pane's own click wiring (and an
    // enclosing link) never sees these taps.
    document.addEventListener('click', function (e) {
        const t = e.target && e.target.closest ? e.target : null;
        if (!t) return;
        const pin = t.closest('[data-act="pin"], [data-act="unpin"]');
        if (pin) {
            e.preventDefault();
            e.stopPropagation();
            if (!pin.disabled) togglePin(pin);
            return;
        }
        const go = t.closest('[data-goto-tab]');
        if (go) {
            e.preventDefault();
            ED.showTab(go.getAttribute('data-goto-tab'));
        }
    }, true);

    // ---- Drawing it -----------------------------------------------------------

    function matterRowHtml(m) {
        const overdue = m.IsOverdue === 1 || m.IsOverdue === true || m.IsOverdue === '1';
        const bits = [];
        if (m.NextActionText) {
            bits.push('Next: ' + ED.escapeHtml(m.NextActionText)
                    + (m.NextActionDate ? ' (' + (overdue ? 'was due ' : 'by ') + usDate(m.NextActionDate) + ')' : ''));
        }
        bits.push('Opened ' + usDate(m.OpenedDate || m.CreatedDate));
        if (m.LastTouchedDate) bits.push('last touched ' + usDate(m.LastTouchedDate));
        return '<a class="ed-attn-row' + (overdue ? ' overdue' : '') + '" data-testid="entity-attention-matter"'
             +     ' href="/matters/matter.html?id=' + m.MatterID + '">'
             +   '<span class="ed-attn-kind">Matter</span>'
             +   '<span class="ed-attn-main">'
             +     '<span class="ed-attn-title">' + ED.escapeHtml(m.Title || '(no title)') + '</span>'
             +     '<span class="ed-attn-sub">' + ED.escapeHtml(m.StatusName || 'Open') + ' | ' + bits.join(' | ') + '</span>'
             +   '</span>'
             + '</a>';
    }

    function callLogRowHtml(c) {
        const bits = [];
        if (c.LastEntryDate) bits.push('last call ' + usDate(c.LastEntryDate)
                                     + (c.LastParty ? ' with ' + ED.escapeHtml(c.LastParty) : ''));
        else bits.push('no calls logged yet');
        const waiting = parseInt(c.ActivePartyCount, 10) || 0;
        if (waiting) bits.push(waiting + ' still open');
        return '<a class="ed-attn-row" data-testid="entity-attention-calllog"'
             +     ' href="/call-logs/call-log.html?id=' + c.CallLogID + '">'
             +   '<span class="ed-attn-kind calls">Call log</span>'
             +   '<span class="ed-attn-main">'
             +     '<span class="ed-attn-title">' + ED.escapeHtml(c.Title || '(no title)') + '</span>'
             +     '<span class="ed-attn-sub">' + bits.join(' | ') + '</span>'
             +   '</span>'
             + '</a>';
    }

    function pinRowHtml(p, entityId, fallbackUrl) {
        const live = liveRowOf(p.kind, p.pinnedId);
        const label  = live ? live.label  : p.label;
        const detail = live ? live.detail : p.detail;
        const url    = (live ? live.url : p.url) || '';
        // Where the tap goes: the row's own page; else its tab on this record;
        // else (Entity Manager IV, no tabs) the record's full page.
        const target = url ? ' href="' + ED.escapeHtml(url) + '"'
                     : (live && live.tab) ? ' href="#" data-goto-tab="' + ED.escapeHtml(live.tab) + '"'
                     : ' href="' + ED.escapeHtml(fallbackUrl || '#') + '"';
        return '<div class="ed-attn-row pinned" data-testid="entity-attention-pin">'
             +   '<span class="ed-attn-kind pin">&#9733; ' + ED.escapeHtml(PIN_KIND_LABEL[p.kind] || p.kind) + '</span>'
             +   '<a class="ed-attn-main"' + target + '>'
             +     '<span class="ed-attn-title">' + ED.escapeHtml(label || '(no title)') + '</span>'
             +     (detail ? '<span class="ed-attn-sub">' + ED.escapeHtml(detail) + '</span>' : '')
             +   '</a>'
             +   '<button type="button" class="ed-btn ed-attn-unpin" data-act="unpin"'
             +       ' data-entity-id="' + entityId + '" data-pin-kind="' + ED.escapeHtml(p.kind) + '"'
             +       ' data-pin-id="' + p.pinnedId + '" data-testid="entity-attention-unpin-btn">Unpin</button>'
             + '</div>';
    }

    /** The pins worth showing: not an open matter / call log already listed
     *  above, and not a row that has left its (loaded) tab. */
    function visiblePins(data) {
        const openM = new Set(data.matters.map(function (m) { return String(m.MatterID); }));
        const openC = new Set(data.callLogs.map(function (c) { return String(c.CallLogID); }));
        return data.pins.filter(function (p) {
            if (p.kind === 'matter'  && openM.has(String(p.pinnedId))) return false;
            if (p.kind === 'calllog' && openC.has(String(p.pinnedId))) return false;
            if (_loadedKinds.has(p.kind) && !liveRowOf(p.kind, p.pinnedId)) return false;
            return true;
        });
    }

    /** Redraw the current record's Needs attention from what is already loaded. */
    function paintAttention() {
        const host = _attn.host, data = _attn.data, opts = _attn.opts || {};
        if (!host || !data) return;
        const pins  = visiblePins(data);
        const total = data.matters.length + data.callLogs.length + pins.length;
        host.innerHTML = total === 0 ? '' :
              '<div class="ed-attn" data-testid="entity-attention">'
            +   data.matters.map(matterRowHtml).join('')
            +   data.callLogs.map(callLogRowHtml).join('')
            +   pins.map(function (p) { return pinRowHtml(p, _attn.entityId, opts.fallbackUrl); }).join('')
            + '</div>';
        if (typeof opts.onCount === 'function') opts.onCount(total);
    }

    /** Load and draw a record's Needs attention into hostEl. opts.onCount(n) is
     *  told the count after every draw (0 = nothing: hide the tab);
     *  opts.fallbackUrl is where a pinned row with no page of its own goes
     *  when its tab is not on this screen. Resolves to the count. */
    ED.renderAttention = async function (hostEl, entityId, opts) {
        if (!hostEl) return 0;
        entityId = parseInt(entityId, 10) || 0;
        _attn.host = hostEl;
        _attn.opts = opts || {};
        if (_attn.entityId !== entityId) { _attn.data = null; _pinned.clear(); }
        _attn.entityId = entityId;
        if (entityId <= 0) {
            hostEl.innerHTML = '';
            if (typeof _attn.opts.onCount === 'function') _attn.opts.onCount(0);
            return 0;
        }
        const data = await ED.loadAttention(entityId);
        if (_attn.entityId !== entityId) return 0;   // another record was opened meanwhile
        _attn.data = data;
        _pinned.clear();
        data.pins.forEach(function (p) { _pinned.add(pinKey(p.kind, p.pinnedId)); });
        // Tabs may have drawn before the pins arrived: bring their toggles up to date.
        data.pins.forEach(function (p) { syncPinButtons(p.kind, p.pinnedId); });
        paintAttention();
        return data.matters.length + data.callLogs.length + visiblePins(data).length;
    };

    // ---- The tab, on a full record page ---------------------------------------

    /** The Needs attention tab + pane, injected as the FIRST tab so no page has
     *  to carry markup for it. Hidden until it has something in it. Null on a
     *  page without a tab bar. */
    ED.attentionTab = function () {
        let tab = document.querySelector('.ed-tab[data-tab="attention"]');
        if (tab) return { tab: tab, pane: document.querySelector('.ed-pane[data-pane="attention"]') };
        const bar = document.querySelector('.ed-tabs');
        const firstPane = document.querySelector('.ed-pane');
        if (!bar || !firstPane || !firstPane.parentNode) return null;
        tab = document.createElement('button');
        tab.type = 'button';
        tab.className = 'ed-tab ed-tab-attn';
        tab.setAttribute('data-tab', 'attention');
        tab.setAttribute('data-testid', 'entity-tab-attention');
        tab.hidden = true;
        bar.insertBefore(tab, bar.firstChild);
        const pane = document.createElement('div');
        pane.className = 'ed-pane';
        pane.setAttribute('data-pane', 'attention');
        pane.setAttribute('data-testid', 'entity-pane-attention');
        firstPane.parentNode.insertBefore(pane, firstPane);
        return { tab: tab, pane: pane };
    };

    /** Draw the record page's Needs attention tab. Shown with its count while
     *  anything needs attention; hidden -- and never left selected -- when not. */
    ED.renderAttentionTab = function (entityId) {
        const t = ED.attentionTab();
        if (!t) return;
        ED.renderAttention(t.pane, entityId, {
            onCount: function (n) {
                t.tab.textContent = 'Needs attention (' + n + ')';
                const wasHidden = t.tab.hidden;
                t.tab.hidden = n === 0;
                if (n === 0 && t.tab.classList.contains('active')) {
                    const first = document.querySelector('.ed-tab:not([data-tab="attention"]):not([hidden])');
                    if (first) ED.showTab(first.getAttribute('data-tab'));
                }
                // A pinned address (?tab=attention) opens straight onto it.
                if (n > 0 && wasHidden
                    && (new URLSearchParams(location.search).get('tab') || '').toLowerCase() === 'attention') {
                    ED.showTab('attention');
                }
            }
        });
    };

    /** Called by the sub-list loader each time a tab has drawn: the pins of
     *  that kind now read their live rows, and pins whose row is gone drop out. */
    ED.attentionTabDrew = function (kind) {
        if (kind) _loadedKinds.add(kind);
        paintAttention();
    };

    // ===== Overview (the record's own dashboard, as a tab; was "This Entity's Dashboard") =====
    //
    // The Dashboards tab lists dashboards, and tapping one LEAVES the record
    // for /dashboards/dashboard.html -- the record's tabs and menu are gone.
    // This tab shows the record's OWN dashboard right here, under the record's
    // tab bar, so nothing is lost. It sits just right of Needs attention when
    // that shows, otherwise first.
    //
    // The dashboard page is framed in its embed mode (?embed=1: no header, no
    // icon band, no Back-stack entry, links open in this window). A frame
    // rather than a second copy of the sections in this document, because the
    // dashboard paints the same panes this page's tabs do -- two copies of
    // every pane in one document would collide on ids, pins and counts.
    //
    // Built on first open, never before: asking for a record's own dashboard
    // creates it, and opening a record must not create one.

    const DASH_TAB = 'thisdashboard';
    const _dash = { entityId: 0, framedFor: 0, dirty: false };

    /** The tab + pane, injected just after the Needs attention tab (itself
     *  first, and hidden when empty), so no page carries markup for it. Null
     *  on a page without a tab bar -- the dashboard page itself, Entity
     *  Manager IV. */
    ED.entityDashboardTab = function () {
        let tab = document.querySelector('.ed-tab[data-tab="' + DASH_TAB + '"]');
        if (tab) return { tab: tab, pane: document.querySelector('.ed-pane[data-pane="' + DASH_TAB + '"]') };
        const bar = document.querySelector('.ed-tabs');
        const firstPane = document.querySelector('.ed-pane');
        if (!bar || !firstPane || !firstPane.parentNode) return null;
        const attnTab  = bar.querySelector('.ed-tab[data-tab="attention"]');
        const attnPane = document.querySelector('.ed-pane[data-pane="attention"]');
        tab = document.createElement('button');
        tab.type = 'button';
        tab.className = 'ed-tab';
        tab.setAttribute('data-tab', DASH_TAB);
        tab.setAttribute('data-testid', 'entity-tab-thisdashboard');
        tab.textContent = 'Overview';
        bar.insertBefore(tab, attnTab ? attnTab.nextSibling : bar.firstChild);
        const pane = document.createElement('div');
        pane.className = 'ed-pane ed-pane-frame';
        pane.setAttribute('data-pane', DASH_TAB);
        pane.setAttribute('data-testid', 'entity-pane-thisdashboard');
        const before = attnPane ? attnPane.nextSibling : firstPane;
        firstPane.parentNode.insertBefore(pane, before);
        return { tab: tab, pane: pane };
    };

    /** Called by the sub-list loader: make sure the tab is there for this
     *  record, and honour ?tab=thisdashboard (a pinned address). */
    ED.renderEntityDashboardTab = function (entityId) {
        const t = ED.entityDashboardTab();
        if (!t) return;
        entityId = parseInt(entityId, 10) || 0;
        _dash.entityId = entityId;
        t.tab.hidden = entityId <= 0;       // an unsaved record has no dashboard yet
        if (entityId <= 0) return;
        if (t.tab.classList.contains('active')) {
            if (_dash.framedFor !== entityId) ensureDashboardFrame();
        } else if ((new URLSearchParams(location.search).get('tab') || '').toLowerCase() === DASH_TAB) {
            ED.showTab(DASH_TAB);
        }
    };

    function ensureDashboardFrame() {
        const t = ED.entityDashboardTab();
        if (!t || _dash.entityId <= 0) return;
        if (_dash.framedFor === _dash.entityId) {
            // Coming back to it: the other tabs may have changed the record
            // meanwhile, so the dashboard re-reads its sections (not the page).
            const existing = t.pane.querySelector('iframe');
            try {
                const w = existing && existing.contentWindow;
                if (w && w.LWDashboard && typeof w.LWDashboard.reload === 'function') w.LWDashboard.reload();
            } catch (e) { /* still loading -- it will read fresh anyway */ }
            return;
        }
        _dash.framedFor = _dash.entityId;
        const frame = document.createElement('iframe');
        frame.className = 'ed-dash-frame';
        frame.title = 'Overview';
        frame.setAttribute('data-testid', 'entity-thisdashboard-frame');
        frame.src = '/dashboards/dashboard.html?embed=1&entityId=' + _dash.entityId;
        t.pane.innerHTML = '';
        t.pane.appendChild(frame);
    }

    /** Called by ED.showTab on every switch. Opening the tab builds the frame;
     *  leaving it after something was changed inside re-reads the record's
     *  tabs, so an address added on the dashboard is on the Addresses tab. */
    function dashboardTabShown(name) {
        if (name === DASH_TAB) { ensureDashboardFrame(); return; }
        if (_dash.dirty && _subLists.entityId === _dash.entityId && _subLists.panes) {
            _dash.dirty = false;
            ED.loadEntitySubLists(_subLists.entityId, _subLists.panes);
        }
    }

    // The framed dashboard says so when it writes (dashboard.js, embed mode).
    window.addEventListener('message', function (e) {
        if (e.origin !== location.origin || !e.data || e.data.type !== 'lw-dashboard-changed') return;
        _dash.dirty = true;
    });

    // ===== Timeline (Story 4562) =============================================
    //
    // Every note filed on the record or on anything in its universe -- the
    // records linked to it, and the records linked to those, out to a set
    // depth -- from a set date forward, oldest first. The LAST tab on every
    // record page, injected here so no page carries markup for it, and a band
    // on the record's Overview (the dashboard's "timeline" section).
    //
    // The tab's code lives in entity-timeline.js, loaded the first time a
    // timeline is shown: most record opens never visit it. Every visit
    // refreshes (new notes and links appear elsewhere all the time), which is
    // why this re-renders on EVERY switch to the tab, not just the first.

    const TIMELINE_TAB = 'timeline';
    const _timeline = { entityId: 0, loading: null };

    /** The tab + pane, at the end of the tab bar -- but ahead of Solutions,
     *  which is the very last tab (Tony, 2026-10-08) unless the record's tabs
     *  were arranged in Entity Manager V (applyArrangedTabOrder). Null on a page
     *  with no tab bar (the dashboard page, Entity Manager IV). */
    ED.timelineTab = function () {
        let tab = document.querySelector('.ed-tab[data-tab="' + TIMELINE_TAB + '"]');
        if (tab) return { tab: tab, pane: document.querySelector('.ed-pane[data-pane="' + TIMELINE_TAB + '"]') };
        const bar = document.querySelector('.ed-tabs');
        const panes = document.querySelectorAll('.ed-pane');
        const lastPane = panes.length ? panes[panes.length - 1] : null;
        if (!bar || !lastPane || !lastPane.parentNode) return null;
        tab = document.createElement('button');
        tab.type = 'button';
        tab.className = 'ed-tab';
        tab.setAttribute('data-tab', TIMELINE_TAB);
        tab.setAttribute('data-testid', 'entity-tab-timeline');
        tab.textContent = 'Timeline';
        const solTab  = bar.querySelector('.ed-tab[data-tab="solutions"]');
        const solPane = document.querySelector('.ed-pane[data-pane="solutions"]');
        bar.insertBefore(tab, solTab);
        const pane = document.createElement('section');
        pane.className = 'ed-pane';
        pane.setAttribute('data-pane', TIMELINE_TAB);
        pane.setAttribute('data-testid', 'entity-pane-timeline');
        if (solPane && solPane.parentNode) solPane.parentNode.insertBefore(pane, solPane);
        else lastPane.parentNode.insertBefore(pane, lastPane.nextSibling);
        return { tab: tab, pane: pane };
    };

    /** Loads entity-timeline.js (+ its css) once; resolves to window.EntityTimeline. */
    function loadTimelineScript() {
        if (window.EntityTimeline) return Promise.resolve(window.EntityTimeline);
        if (_timeline.loading) return _timeline.loading;
        _timeline.loading = new Promise(function (resolve, reject) {
            if (!document.querySelector('link[data-lw-timeline-css]')) {
                const css = document.createElement('link');
                css.rel = 'stylesheet';
                css.href = '/contacts/entity-timeline.css?v=6';
                css.setAttribute('data-lw-timeline-css', '1');
                document.head.appendChild(css);
            }
            const s = document.createElement('script');
            s.src = '/contacts/entity-timeline.js?v=18';
            s.onload  = function () { window.EntityTimeline ? resolve(window.EntityTimeline) : reject(new Error('timeline script did not load')); };
            s.onerror = function () { _timeline.loading = null; reject(new Error('timeline script did not load')); };
            document.head.appendChild(s);
        });
        return _timeline.loading;
    }

    /** Draws (and refreshes) a timeline into any pane -- the tab's, or a band
     *  on the Overview. */
    ED.renderTimelinePane = function (pane, entityId) {
        entityId = parseInt(entityId, 10) || 0;
        if (!pane || entityId <= 0) return;
        loadTimelineScript().then(function (T) { T.render(pane, entityId); })
            .catch(function (err) { ED.renderError(pane, 'Failed to load the timeline: ' + (err && err.message ? err.message : err)); });
    };

    /** Called by the sub-list loader: make sure the tab is there for this
     *  record, and honour ?tab=timeline (a pinned address). */
    ED.renderTimelineTab = function (entityId) {
        const t = ED.timelineTab();
        if (!t) return;
        entityId = parseInt(entityId, 10) || 0;
        _timeline.entityId = entityId;
        t.tab.hidden = entityId <= 0;       // an unsaved record has no notes to collate
        if (entityId <= 0) return;
        if (t.tab.classList.contains('active')) {
            ED.renderTimelinePane(t.pane, entityId);
        } else if ((new URLSearchParams(location.search).get('tab') || '').toLowerCase() === TIMELINE_TAB) {
            ED.showTab(TIMELINE_TAB);
        }
    };

    /** Called by ED.showTab on every switch: entering the tab refreshes it. */
    function timelineTabShown(name) {
        if (name !== TIMELINE_TAB || _timeline.entityId <= 0) return;
        const t = ED.timelineTab();
        if (t) ED.renderTimelinePane(t.pane, _timeline.entityId);
    }

    /** True while the record page is showing its Timeline tab -- Print then
     *  prints the timeline instead of the record's one-page story. */
    ED.isTimelineTabActive = function () {
        const tab = document.querySelector('.ed-tab[data-tab="' + TIMELINE_TAB + '"]');
        return !!(tab && !tab.hidden && tab.classList.contains('active'));
    };

    // ===== Matters ==========================================================
    //
    // The Matters tab (Story 2464 rework): every record shows the matters it is
    // part of, rightmost tab. Matter is a true entity (tblEntityType 217) whose
    // cross-ref table is MatterParticipant, so linking here IS the participant
    // link the Matters page shows -- one table, both directions.
    //
    // Default sort is created-date descending, with an unobtrusive switch to
    // recently-updated (Tony's #4 on the rework comment).

    let _mattersSort = 'created';

    ED.renderMatters = function (paneEl, rows, entityId, refresh) {
        rows = (rows && rows.matters) ? rows.matters : (rows || []);
        const cardsHtml = rows.length ? rows.map(function (m) {
            const closed = m.IsTerminal === true || m.IsTerminal === 1;
            const bits = [];
            if (m.PartySummary) bits.push(ED.escapeHtml(m.PartySummary));
            if (m.NextActionText && !closed) bits.push('Next: ' + ED.escapeHtml(m.NextActionText));
            return '<div class="ed-card"' + rowTid('matter') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(m.StatusName || 'Matter') + '</span>'
                +         '<a href="/matters/matter.html?id=' + m.MatterID + '" class="name" data-testid="entity-matter-open-link">'
                +             ED.escapeHtml(m.Title || '(no title)') + '</a>'
                +         '<span class="dates">' + ED.escapeHtml((m.CreatedDate || '').substring(0, 10)) + '</span>'
                +     '</div>'
                +     (bits.length ? '<div class="notes">' + bits.join(' &middot; ') + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('matter', m.MatterID, m.Title || '(no title)', m.StatusName || 'Matter',
                                      '/matters/matter.html?id=' + m.MatterID)
                +         '<button type="button" class="ed-btn" data-act="unlink" '
                +             'data-testid="entity-matter-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">This record is not part of any matter.</div>';

        const sortExtra = '<button type="button" class="ed-btn" data-act="linkExisting" '
                        +     'data-testid="entity-matter-link-btn">+ Link Existing Matter</button>'
                        + '<button type="button" class="ed-btn" data-act="sort" '
                        +     'data-testid="entity-matter-sort-btn" style="margin-left:auto">'
                        +     (_mattersSort === 'created' ? 'Newest first' : 'Recently updated') + '</button>';
        paneEl.innerHTML = actionsBarHtml('Add Matter', 'matter', sortExtra) + cardsHtml;
        wirePaneActions(paneEl, rows, {
            add:  function ()  { ED.createMatter(entityId, refresh); },
            edit: function ()  {},
            del:  function ()  {}
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkMatter(entityId, refresh); });
        const sortBtn = paneEl.querySelector('[data-act="sort"]');
        if (sortBtn) sortBtn.addEventListener('click', function () {
            _mattersSort = _mattersSort === 'created' ? 'updated' : 'created';
            refresh();
        });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkMatter(entityId, rows[idx], refresh); });
        });
    };

    ED.mattersUrl = function (entityId) {
        return '/api/matters?entityId=' + entityId + '&includeClosed=true&sort=' + _mattersSort + '&pageSize=50';
    };

    /** Link this record into a matter that already exists. */
    ED.linkMatter = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/matters/available?entityId=' + entityId);
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load matters.')); return; }
        const items = (r.data || []).map(function (m) {
            return { id: m.MatterID, name: (m.Title || '(no title)') + (m.StatusName ? ' (' + m.StatusName + ')' : '') };
        });
        if (!items.length) { alert('No other matters are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing matter',
            fields: [
                { key: 'matterId', sortable: true, label: 'Matter', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.matterId, 10) || 0;
                if (!id) throw new Error('Pick a matter first.');
                const link = await window.LWAuth.apiPost(
                    '/api/matters/' + id + '/participants', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Open a brand-new matter with this record already in it. */
    ED.createMatter = async function (entityId, refresh) {
        ED.openModal({
            title: 'Open a matter',
            fields: [
                { key: 'title',          label: 'What is it',       type: 'text', maxlength: 200, required: true, value: '' },
                { key: 'aboutText',      label: 'What it is about', type: 'text', maxlength: 300, value: '' },
                { key: 'nextActionDate', label: 'Next action by (optional)', type: 'date', value: '' },
                { key: 'nextActionText', label: 'Next action',      type: 'text', maxlength: 200, value: '' }
            ],
            onSave: async function (data) {
                const body = {
                    title:          (data.title || '').trim(),
                    aboutText:      data.aboutText || '',
                    nextActionDate: data.nextActionDate || '',
                    nextActionText: data.nextActionText || ''
                };
                if (!body.title) throw new Error('A matter needs a title.');
                const created = await window.LWAuth.apiPost('/api/matters', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.matter && created.data.matter.MatterID;
                if (!newId) throw new Error('The matter saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost(
                    '/api/matters/' + newId + '/participants', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Take this record off a matter. The matter itself is untouched. */
    ED.unlinkMatter = async function (entityId, row, refresh) {
        if (!confirm('Take this record off "' + (row.Title || '') + '"?\n\n'
                   + 'The matter itself stays in Leonardo; only this record\'s part in it is removed.')) return;
        // The list payload has no participant id; the matter detail does.
        const d = await window.LWAuth.apiGet('/api/matters/' + row.MatterID);
        if (!d || !d.ok || !d.data) { alert(window.LWAuth.errorText(d, 'Could not load the matter.')); return; }
        const p = (d.data.participants || []).find(function (x) {
            return parseInt(x.EntityID, 10) === parseInt(entityId, 10);
        });
        if (!p) { refresh(); return; }
        const r = await window.LWAuth.apiPost(
            '/api/matters/' + row.MatterID + '/participants/' + p.MatterParticipantID, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Call Logs ========================================================
    //
    // The Call Logs tab (Story 3467): every record shows the call logs it is
    // linked to, as the last tab. Call Log is a true entity (tblEntityType 219)
    // whose cross-ref table is CallLogEntityXRef, so a link made here is the same
    // link the Call Logs page shows -- one table, both directions. A party
    // inside a log that IS this record links the log here automatically.

    // ---- Call Logs: each log folds open to show its calls ----------------------
    // Which logs are open is remembered per record in the DATABASE
    // (/api/view-state, key leo.ed.calllogs.open.<entityId>, value = the open
    // CallLogIDs comma-separated) -- never localStorage (Tony, 2026-09-29). The
    // map below is only this page's in-memory copy, so a re-render after an
    // edit does not re-ask the server.
    const CL_FOLD_KEY = 'leo.ed.calllogs.open.';
    const clOpenMemo = {};          // entityId -> { callLogId: true }
    const clCallsMemo = {};         // callLogId -> rendered calls HTML

    async function clLoadOpen(entityId) {
        if (clOpenMemo[entityId]) return clOpenMemo[entityId];
        const key = CL_FOLD_KEY + entityId;
        const open = {};
        try {
            const r = await window.LWAuth.apiGet('/api/view-state?prefix=' + encodeURIComponent(key));
            const v = (r && r.ok && r.data && r.data.values) ? r.data.values[key] : '';
            String(v || '').split(',').forEach(function (s) { if (s.trim()) open[s.trim()] = true; });
        } catch (e) { /* nothing remembered -- everything starts folded */ }
        clOpenMemo[entityId] = open;
        return open;
    }

    function clSaveOpen(entityId) {
        // A Playwright-driven browser never saves as Tony (picasso-view-state.js, 2026-10-22).
        if (navigator.webdriver && !window.__LEO_VIEWSTATE_LIVE) return;
        const values = {};
        values[CL_FOLD_KEY + entityId] = Object.keys(clOpenMemo[entityId] || {}).join(',');
        window.LWAuth.apiPost('/api/view-state', { values: values }, 'PUT')
            .catch(function () { /* the fold still works; it just is not remembered */ });
    }

    // One call log's calls, newest first: date, who, status, then the note.
    async function clCallsHtml(callLogId) {
        if (clCallsMemo[callLogId]) return clCallsMemo[callLogId];
        const r = await window.LWAuth.apiGet('/api/call-logs/' + callLogId + '?activeOnly=false');
        if (!r || !r.ok || !r.data) return '<div class="ed-cl-empty">Could not load the calls.</div>';
        const calls = [];
        (r.data.parties || []).forEach(function (p) {
            (p.entries || []).forEach(function (e) { calls.push({ e: e, party: p.PartyName || '' }); });
        });
        calls.sort(function (a, b) { return String(b.e.EntryDate || '').localeCompare(String(a.e.EntryDate || '')); });
        const html = calls.length ? calls.map(function (x) {
            return '<div class="ed-cl-call" data-testid="entity-calllog-call">'
                 +     '<span class="ed-cl-date">' + ED.escapeHtml(String(x.e.EntryDate || '').substring(0, 10)) + '</span>'
                 +     '<span class="ed-cl-party">' + ED.escapeHtml(x.party) + '</span>'
                 +     (x.e.StatusName ? '<span class="ed-cl-status">' + ED.escapeHtml(x.e.StatusName) + '</span>' : '')
                 +     (x.e.Note ? '<div class="ed-cl-note">' + ED.escapeHtml(x.e.Note) + '</div>' : '')
                 + '</div>';
        }).join('') : '<div class="ed-cl-empty">No calls logged yet.</div>';
        clCallsMemo[callLogId] = html;
        return html;
    }

    async function clSetOpen(card, open) {
        const btn = card.querySelector('[data-act="cl-toggle"]');
        const box = card.querySelector('.ed-cl-calls');
        if (!btn || !box) return;
        btn.innerHTML = open ? '&#9662;' : '&#9656;';
        btn.setAttribute('aria-expanded', String(open));
        btn.title = open ? 'Hide calls' : 'Show calls';
        btn.setAttribute('aria-label', btn.title);
        box.hidden = !open;
        if (open && !box.getAttribute('data-loaded')) {
            box.innerHTML = '<div class="ed-cl-empty">Loading calls...</div>';
            box.innerHTML = await clCallsHtml(card.getAttribute('data-cl-id'));
            box.setAttribute('data-loaded', '1');
        }
    }

    // The Fold All button: two open carets = a tap opens them all, two closed
    // carets = a tap folds them all (the house "Fold All" pattern).
    function clSyncFoldAll(paneEl) {
        const btn = paneEl.querySelector('[data-act="cl-foldall"]');
        if (!btn) return;
        const toggles = paneEl.querySelectorAll('[data-act="cl-toggle"]');
        btn.hidden = toggles.length === 0;
        const anyOpen = Array.prototype.some.call(toggles, function (t) { return t.getAttribute('aria-expanded') === 'true'; });
        btn.innerHTML = anyOpen ? '&#9656;&#9656;' : '&#9662;&#9662;';
        btn.title = anyOpen ? 'Collapse all' : 'Expand all';
        btn.setAttribute('aria-label', anyOpen ? 'Collapse all call logs' : 'Expand all call logs');
    }

    function wireCallLogFolds(paneEl, entityId) {
        clSyncFoldAll(paneEl);
        paneEl.querySelectorAll('[data-act="cl-toggle"]').forEach(function (btn) {
            btn.addEventListener('click', async function (ev) {
                ev.preventDefault();
                ev.stopPropagation();
                const card = btn.closest('.ed-card');
                const id = card.getAttribute('data-cl-id');
                const open = await clLoadOpen(entityId);
                const nowOpen = btn.getAttribute('aria-expanded') !== 'true';
                if (nowOpen) open[id] = true; else delete open[id];
                clSaveOpen(entityId);
                clSyncFoldAll(paneEl);
                await clSetOpen(card, nowOpen);
                clSyncFoldAll(paneEl);
            });
        });
        const foldAll = paneEl.querySelector('[data-act="cl-foldall"]');
        if (foldAll) foldAll.addEventListener('click', async function () {
            const open = await clLoadOpen(entityId);
            const toggles = paneEl.querySelectorAll('[data-act="cl-toggle"]');
            const anyOpen = Array.prototype.some.call(toggles, function (t) { return t.getAttribute('aria-expanded') === 'true'; });
            const cards = Array.prototype.map.call(toggles, function (t) { return t.closest('.ed-card'); });
            cards.forEach(function (card) {
                const id = card.getAttribute('data-cl-id');
                if (anyOpen) delete open[id]; else open[id] = true;
            });
            clSaveOpen(entityId);
            await Promise.all(cards.map(function (card) { return clSetOpen(card, !anyOpen); }));
            clSyncFoldAll(paneEl);
        });
        // Re-open whatever was open last time, from the database.
        clLoadOpen(entityId).then(function (open) {
            if (!paneEl.isConnected) return;
            const cards = paneEl.querySelectorAll('.ed-card[data-cl-id]');
            return Promise.all(Array.prototype.map.call(cards, function (card) {
                return open[card.getAttribute('data-cl-id')] ? clSetOpen(card, true) : null;
            }));
        }).then(function () { clSyncFoldAll(paneEl); });
    }

    ED.renderCallLogs = function (paneEl, rows, entityId, refresh) {
        rows = (rows && rows.callLogs) ? rows.callLogs : (rows || []);
        // A re-render follows a change (a call logged, a log linked), so read calls fresh.
        Object.keys(clCallsMemo).forEach(function (k) { delete clCallsMemo[k]; });
        const cardsHtml = rows.length ? rows.map(function (c) {
            const closed = c.IsClosed === true || c.IsClosed === 1;
            const bits = [];
            if (c.PartyCount) bits.push(c.PartyCount + (c.PartyCount === 1 ? ' party' : ' parties'));
            if (c.EntryCount) bits.push(c.EntryCount + (c.EntryCount === 1 ? ' call' : ' calls'));
            if (c.LastEntryDate) {
                bits.push('Last ' + ED.escapeHtml(String(c.LastEntryDate).substring(0, 10))
                        + (c.LastParty ? ' with ' + ED.escapeHtml(c.LastParty) : '')
                        + (c.LastStatus ? ' (' + ED.escapeHtml(c.LastStatus) + ')' : ''));
            }
            const hasCalls = (c.EntryCount || 0) > 0;
            return '<div class="ed-card" data-cl-id="' + c.CallLogID + '"' + rowTid('calllog') + '>'
                +     '<div class="head">'
                +         (hasCalls
                              ? '<button type="button" class="ed-cl-toggle" data-act="cl-toggle" aria-expanded="false"'
                              +     ' aria-label="Show calls" title="Show calls" data-testid="entity-calllog-toggle">&#9656;</button>'
                              : '')
                +         '<span class="badge">' + (closed ? 'Closed' : 'Open') + '</span>'
                +         '<a href="/call-logs/call-log.html?id=' + c.CallLogID + '" class="name" data-testid="entity-calllog-open-link">'
                +             ED.escapeHtml(c.Title || '(no title)') + '</a>'
                +         '<span class="dates">' + ED.escapeHtml((c.CreatedDate || '').substring(0, 10)) + '</span>'
                +     '</div>'
                +     (c.AboutText ? '<div class="notes">' + ED.escapeHtml(c.AboutText) + '</div>' : '')
                +     (bits.length ? '<div class="notes">' + bits.join(' &middot; ') + '</div>' : '')
                +     (c.LastNote ? '<div class="notes">' + ED.escapeHtml(c.LastNote) + '</div>' : '')
                +     (hasCalls ? '<div class="ed-cl-calls" hidden data-testid="entity-calllog-calls"></div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('calllog', c.CallLogID, c.Title || '(no title)', closed ? 'Closed' : 'Open',
                                      '/call-logs/call-log.html?id=' + c.CallLogID)
                +         '<button type="button" class="ed-btn" data-act="unlink" '
                +             'data-testid="entity-calllog-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No call log is linked to this record.</div>';

        const extras = '<button type="button" class="ed-btn" data-act="linkExisting" '
                     +     'data-testid="entity-calllog-link-btn">+ Link Existing Call Log</button>'
                     + '<button type="button" class="ed-btn ed-cl-foldall" data-act="cl-foldall" hidden '
                     +     'data-testid="entity-calllog-fold-all"></button>';
        paneEl.innerHTML = actionsBarHtml('Start Call Log', 'calllog', extras) + cardsHtml;
        wireCallLogFolds(paneEl, entityId);
        wirePaneActions(paneEl, rows, {
            add:  function ()  { ED.createCallLog(entityId, refresh); },
            edit: function ()  {},
            del:  function ()  {}
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkCallLog(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkCallLog(entityId, rows[idx], refresh); });
        });
    };

    ED.callLogsUrl = function (entityId) {
        return '/api/call-logs?entityId=' + entityId + '&includeClosed=true&pageSize=50';
    };

    /** Link this record to a call log that already exists. */
    ED.linkCallLog = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/call-logs/available?entityId=' + entityId);
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load call logs.')); return; }
        const items = (r.data || []).map(function (c) {
            return { id: c.CallLogID, name: (c.Title || '(no title)') + (c.IsClosed ? ' (closed)' : '') };
        });
        if (!items.length) { alert('No other call logs are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing call log',
            fields: [
                { key: 'callLogId', sortable: true, label: 'Call log', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.callLogId, 10) || 0;
                if (!id) throw new Error('Pick a call log first.');
                const link = await window.LWAuth.apiPost('/api/call-logs/' + id + '/links', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Start a brand-new call log already linked to this record. */
    ED.createCallLog = async function (entityId, refresh) {
        ED.openModal({
            title: 'Start a call log',
            fields: [
                { key: 'title',     label: 'What is it',       type: 'text', maxlength: 200, required: true, value: '' },
                { key: 'aboutText', label: 'What it is about', type: 'text', maxlength: 500, value: '' }
            ],
            onSave: async function (data) {
                const body = { title: (data.title || '').trim(), aboutText: data.aboutText || '' };
                if (!body.title) throw new Error('A call log needs a title.');
                const created = await window.LWAuth.apiPost('/api/call-logs', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.callLog && created.data.callLog.CallLogID;
                if (!newId) throw new Error('The call log saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost('/api/call-logs/' + newId + '/links', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                await ED.defaultCallLogParty(newId, entityId, link.data);
                refresh();
            }
        });
    };

    /**
     * A log started from a contact or a vendor is, nine times in ten, a log of
     * calls TO that contact or vendor -- so they become its first party, with
     * their phone. A matter or a user-defined entity cannot be called, so a log
     * started there gets no default party.
     */
    ED.defaultCallLogParty = async function (callLogId, entityId, detail) {
        const CALLABLE_TYPES = [5, 1];   // contact, vendor (tblEntityType ids)
        const links = (detail && detail.links) || [];
        const me = links.find(function (l) { return parseInt(l.EntityID, 10) === parseInt(entityId, 10); });
        if (!me || CALLABLE_TYPES.indexOf(parseInt(me.EntityTypeID, 10)) < 0) return;
        let phone = '';
        const ph = await window.LWAuth.apiGet('/api/entities/' + entityId + '/phones');
        if (ph && ph.ok && ph.data && ph.data.length) phone = ph.data[0].phoneNumberDisplay || '';
        await window.LWAuth.apiPost('/api/call-logs/' + callLogId + '/parties',
            { partyName: me.DisplayName || '(no name)', entityId: entityId, phone: phone }, 'POST');
    };

    /** Unlink this record from a call log. The log itself is untouched. */
    ED.unlinkCallLog = async function (entityId, row, refresh) {
        if (!confirm('Unlink this record from "' + (row.Title || '') + '"?\n\n'
                   + 'The call log stays in Leonardo; only the link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost('/api/call-logs/' + row.CallLogID + '/links/' + entityId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Vehicles =========================================================
    //
    // The Vehicles tab (2026-09-26): every record shows the vehicles it is
    // linked to -- the car a contact drives, the shop that services it, the
    // matter about the accident. Vehicle is a true entity (tblEntityType 225)
    // whose cross-ref table is VehicleEntityXRef, and the link is mirrored both
    // ways, so a link made here also shows on the vehicle's own record.

    ED.vehiclesUrl = function (entityId) {
        return '/api/vehicles?entityId=' + entityId + '&includeInactive=true&pageSize=50';
    };

    ED.renderVehicles = function (paneEl, rows, entityId, refresh) {
        rows = (rows && rows.vehicles) ? rows.vehicles : (rows || []);
        const cardsHtml = rows.length ? rows.map(function (v) {
            const active = v.IsActive === true || v.IsActive === 1;
            const what = [v.ModelYear, v.MakeName, v.Model].filter(function (x) { return x; }).join(' ');
            const bits = [];
            if (v.LicensePlate) bits.push('Plate ' + ED.escapeHtml(v.LicensePlate) + (v.PlateState ? ' (' + ED.escapeHtml(v.PlateState) + ')' : ''));
            if (v.CurrentOdometer != null) bits.push(Number(v.CurrentOdometer).toLocaleString('en-US') + ' mi');
            if (v.LastEntryDate) bits.push('Last ' + ED.escapeHtml(usDate(v.LastEntryDate)) + ': ' + ED.escapeHtml(v.LastEntrySummary || ''));
            return '<div class="ed-card"' + rowTid('vehicle') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + (active ? 'Active' : 'Sold / gone') + '</span>'
                +         '<a href="/vehicles/vehicle.html?id=' + v.VehicleID + '" class="name" data-testid="entity-vehicle-open-link">'
                +             ED.escapeHtml(v.Nickname || '(no name)') + '</a>'
                +     '</div>'
                +     (what && what !== v.Nickname ? '<div class="notes">' + ED.escapeHtml(what) + '</div>' : '')
                +     (bits.length ? '<div class="notes">' + bits.join(' | ') + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.linkedButton('vehicle', v.VehicleID)
                +         '<button type="button" class="ed-btn" data-act="unlink" '
                +             'data-testid="entity-vehicle-unlink-btn">Unlink</button>'
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No vehicle is linked to this record.</div>';

        const extras = '<button type="button" class="ed-btn" data-act="linkExisting" '
                     +     'data-testid="entity-vehicle-link-btn">+ Link Existing Vehicle</button>';
        paneEl.innerHTML = actionsBarHtml('New Vehicle', 'vehicle', extras) + cardsHtml;
        wirePaneActions(paneEl, rows, {
            add:  function ()  { ED.createVehicle(entityId, refresh); },
            edit: function ()  {},
            del:  function ()  {}
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkVehicle(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkVehicle(entityId, rows[idx], refresh); });
        });
    };

    /** Link this record to a vehicle that already exists. */
    ED.linkVehicle = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/vehicles/available?entityId=' + entityId);
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load vehicles.')); return; }
        const items = (r.data || []).map(function (v) {
            const active = v.IsActive === true || v.IsActive === 1;
            return { id: v.VehicleID, name: (v.Nickname || '(no name)') + (active ? '' : ' (sold)') };
        });
        if (!items.length) { alert('No other vehicles are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing vehicle',
            fields: [
                { key: 'vehicleId', sortable: true, label: 'Vehicle', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.vehicleId, 10) || 0;
                if (!id) throw new Error('Pick a vehicle first.');
                const link = await window.LWAuth.apiPost('/api/vehicles/' + id + '/links', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Add a brand-new vehicle already linked to this record. */
    ED.createVehicle = async function (entityId, refresh) {
        ED.openModal({
            title: 'New vehicle',
            fields: [
                { key: 'nickname',  label: 'Nickname',   type: 'text',   maxlength: 100, value: '' },
                { key: 'modelYear', label: 'Year',       type: 'number', value: '' },
                { key: 'makeName',  label: 'Make',       type: 'text',   maxlength: 100, value: '' },
                { key: 'model',     label: 'Model',      type: 'text',   maxlength: 100, value: '' }
            ],
            onSave: async function (data) {
                const body = {
                    nickname:  (data.nickname || '').trim(),
                    modelYear: parseInt(data.modelYear, 10) || null,
                    makeName:  (data.makeName || '').trim(),
                    model:     (data.model || '').trim()
                };
                if (!body.nickname && !body.modelYear && !body.makeName && !body.model)
                    throw new Error('Give the vehicle a nickname, or its year, make or model.');
                const created = await window.LWAuth.apiPost('/api/vehicles', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                const newId = created.data && created.data.vehicle && created.data.vehicle.VehicleID;
                if (!newId) throw new Error('The vehicle saved but returned no id, so it was not linked.');
                const link = await window.LWAuth.apiPost('/api/vehicles/' + newId + '/links', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Unlink this record from a vehicle. The vehicle itself is untouched. */
    ED.unlinkVehicle = async function (entityId, row, refresh) {
        if (!confirm('Unlink this record from the vehicle "' + (row.Nickname || '') + '"?\n\n'
                   + 'The vehicle and its log stay in Leonardo; only the link to this record is removed.')) return;
        const r = await window.LWAuth.apiPost('/api/vehicles/' + row.VehicleID + '/links/' + entityId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Insurance (Story 5640) ===========================================
    //
    // The health insurance plans a record is part of, and how: the vendor that
    // ISSUES the plan (Insurer), the employer that offers it (Sponsor), a person
    // it covers (Member, with their member id), or a plain link. "+ New Plan"
    // starts a plan from this record -- a vendor becomes its insurer, a person
    // its first member. Only a plain link can be unlinked here; insurer, sponsor
    // and members are changed on the plan itself.

    ED.insuranceUrl = function (entityId) {
        return '/api/insurance/plans?entityId=' + entityId + '&includeInactive=true&pageSize=100';
    };

    ED.renderInsurance = function (paneEl, data, entityId, refresh) {
        const rows = (data && data.plans) ? data.plans : (data || []);
        const cardsHtml = rows.length ? rows.map(function (p) {
            const active = p.IsActive === true || p.IsActive === 1;
            const bits = [];
            if (p.Relation === 'Member' && p.MemberNumber) bits.push('Member ID ' + ED.escapeHtml(p.MemberNumber));
            if (p.GroupNumber) bits.push('Group ' + ED.escapeHtml(p.GroupNumber));
            if (p.MemberNames) bits.push(ED.escapeHtml(p.MemberNames));
            if (p.EobCount) bits.push(p.EobCount + (p.EobCount === 1 ? ' EOB' : ' EOBs'));
            const label = p.PlanName + ' (' + p.InsurerName + ')';
            return '<div class="ed-card"' + rowTid('insuranceplan') + '>'
                +     '<div class="head">'
                +         '<span class="badge">' + ED.escapeHtml(p.Relation || 'Plan') + '</span>'
                +         '<a href="/insurance/insurance-plan.html?id=' + p.InsurancePlanID + '" class="name" data-testid="entity-insuranceplan-open-link">'
                +             ED.escapeHtml(p.PlanName) + '</a>'
                +     '</div>'
                +     '<div class="notes">' + ED.escapeHtml(p.InsurerName) + (p.TypeName ? ' | ' + ED.escapeHtml(p.TypeName) : '')
                +         (active ? '' : ' | no longer active') + '</div>'
                +     (bits.length ? '<div class="notes">' + bits.join(' | ') + '</div>' : '')
                +     '<div class="ed-card-actions">'
                +         ED.pinButton('insuranceplan', p.InsurancePlanID, label, p.Relation || '',
                                       '/insurance/insurance-plan.html?id=' + p.InsurancePlanID)
                +         (p.Relation === 'Linked'
                             ? '<button type="button" class="ed-btn" data-act="unlink" data-testid="entity-insuranceplan-unlink-btn">Unlink</button>' : '')
                +     '</div>'
                + '</div>';
        }).join('') : '<div class="ed-empty">No insurance plan is tied to this record.</div>';

        const extras = '<button type="button" class="ed-btn" data-act="linkExisting" '
                     +     'data-testid="entity-insuranceplan-link-btn">+ Link Existing Plan</button>';
        paneEl.innerHTML = actionsBarHtml('New Plan', 'insuranceplan', extras) + cardsHtml;
        // On a page that loads the insurance views (contact, vendor), the plans' members and every EOB
        // tied to this record follow the plan cards, grouped by date, person, issue or provider.
        if (rows.length && window.InsuranceCommon) {
            const more = document.createElement('div');
            more.className = 'ed-insurance-more';
            paneEl.appendChild(more);
            window.InsuranceCommon.drawEntityInsurance(more, entityId, rows);
        }
        wirePaneActions(paneEl, rows, {
            add:  function () { location.href = '/insurance/insurance-plan.html?id=0&fromEntityId=' + entityId; },
            edit: function () {},
            del:  function () {}
        });
        const linkBtn = paneEl.querySelector('[data-act="linkExisting"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkInsurancePlan(entityId, refresh); });
        paneEl.querySelectorAll('.ed-card').forEach(function (card, idx) {
            const ub = card.querySelector('[data-act="unlink"]');
            if (ub) ub.addEventListener('click', function () { ED.unlinkInsurancePlan(entityId, rows[idx], refresh); });
        });
    };

    /** Link this record to a plan that already exists (a plain link -- not insurer, sponsor or member). */
    ED.linkInsurancePlan = async function (entityId, refresh) {
        const r = await window.LWAuth.apiGet('/api/insurance/plans/available?entityId=' + entityId);
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load the insurance plans')); return; }
        const items = (r.data || []).map(function (p) {
            return { id: p.InsurancePlanID, name: p.PlanName + ' (' + p.InsurerName + ')' };
        });
        if (!items.length) { alert('No other insurance plans are available to link.'); return; }
        ED.openModal({
            title: 'Link an existing insurance plan',
            fields: [
                { key: 'planId', sortable: true, label: 'Plan', type: 'typeahead', options: items, required: true, value: '' }
            ],
            onSave: async function (data) {
                const id = parseInt(data.planId, 10) || 0;
                if (!id) throw new Error('Pick a plan first.');
                const link = await window.LWAuth.apiPost('/api/insurance/plans/' + id + '/links', { entityId: entityId }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Unlink this record from a plan. The plan itself is untouched. */
    ED.unlinkInsurancePlan = async function (entityId, row, refresh) {
        const ok = window.Picasso && window.Picasso.okCancel
            ? await window.Picasso.okCancel('The plan stays in Leonardo; only the link to this record is removed.',
                                            { title: 'Unlink Insurance Plan: ' + row.PlanName + ' (' + row.InsurerName + ')?' })
            : confirm('Unlink Insurance Plan: ' + row.PlanName + '?');
        if (!ok) return;
        const r = await window.LWAuth.apiPost('/api/insurance/plans/' + row.InsurancePlanID + '/links/' + entityId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Loader ===========================================================
    // Fetches every sub-list the caller supplied a pane for and renders into
    // that panes object. { addr, phone, web, login } are always fetched; the
    // rest (events, communications, vendors, contacts, userentities, pagelinks, emails,
    // files, lists, tasks, checklists, matters, calllogs) are fetched only when
    // the caller passed a pane for them. Each pane gets its own refresh
    // callback so post-save the right pane re-fetches itself.

    // Voice-command entry point. The /voice page navigates here with
    // ?prefilleventtext=... (and optional ?prefilleventtitle=...). After the
    // entity has loaded and sub-lists are rendered, the calling page invokes
    // this once to open the Add-Event modal pre-filled and then clear the
    // query params so a refresh doesn't re-open the modal.
    ED.maybeOpenPrefilledEvent = function (entityId, panes) {
        const params = new URLSearchParams(location.search);
        const text   = params.get('prefilleventtext');
        if (!text) return;
        const title  = params.get('prefilleventtitle') || '';
        params.delete('prefilleventtext');
        params.delete('prefilleventtitle');
        const cleanedQuery = params.toString();
        history.replaceState(null, '',
            location.pathname + (cleanedQuery ? '?' + cleanedQuery : ''));
        const refresh = function () { ED.loadEntitySubLists(entityId, panes); };
        ED.editEvent(entityId, null, refresh, {
            eventTitle:       title,
            eventDescription: text
        });
    };

    // The last record + panes the sub-list loader drew, so leaving This
    // Entity's Dashboard after a change can re-read the tabs.
    const _subLists = { entityId: 0, panes: null };

    // ===== Arranged tab order (Story 5645) ===================================
    //
    // Entity Manager V lets Tony arrange a record's sections (All -> the
    // arrows). The record pages' tabs follow the same arrangement, so both
    // places read alike. Kept in UserViewState by Entity V:
    //   leo.emv.order.e<EntityID>  one record's arrangement (CSV of keys)
    //   leo.emv.order.last         the EntityID arranged most recently; a
    //                              record never arranged follows that one's
    // The keys are the tabs' data-tab names (addr, phone, ..., calendar, log,
    // timeline). The record's own tab, Needs attention and Overview stay where
    // they are; a tab the arrangement does not name keeps its place after the
    // named ones. With nothing arranged the page's own order stands.

    const ARRANGE_FIXED = { info: 1, attention: 1, thisdashboard: 1 };
    const ARRANGE_PREFIX = 'leo.emv.order.';

    function arrangedCsv(entityId, read) {
        const own = read(ARRANGE_PREFIX + 'e' + entityId);
        if (own) return own;
        const last = parseInt(read(ARRANGE_PREFIX + 'last'), 10) || 0;
        return last > 0 ? read(ARRANGE_PREFIX + 'e' + last) : '';
    }

    function placeTabs(csv) {
        const bar = document.querySelector('.ed-tabs');
        if (!bar) return;
        const tabs = Array.prototype.slice.call(bar.querySelectorAll('.ed-tab')).filter(function (t) {
            return t.parentNode === bar && !ARRANGE_FIXED[t.dataset.tab];
        });
        if (!tabs.length) return;
        // The page's own order, remembered once, so clearing an arrangement puts it back.
        if (!bar._edPageOrder) bar._edPageOrder = tabs.map(function (t) { return t.dataset.tab; });
        const base = bar._edPageOrder.slice();
        tabs.forEach(function (t) { if (base.indexOf(t.dataset.tab) < 0) base.push(t.dataset.tab); });
        const saved = String(csv || '').split(',').map(function (k) { return k.trim(); }).filter(Boolean);
        const rank = function (k) {
            const i = saved.indexOf(k);
            return i >= 0 ? i : saved.length + base.indexOf(k);
        };
        const sorted = tabs.slice().sort(function (a, b) { return rank(a.dataset.tab) - rank(b.dataset.tab); });
        if (sorted.every(function (t, i) { return t === tabs[i]; })) return;
        const after = tabs[tabs.length - 1].nextSibling;
        sorted.forEach(function (t) { bar.insertBefore(t, after); });
    }

    /** Put this record's tabs in the order arranged in Entity Manager V: at
     *  once from this device's copy, then again from the database's. */
    ED.applyArrangedTabOrder = async function (entityId) {
        entityId = parseInt(entityId, 10) || 0;
        if (entityId <= 0 || !document.querySelector('.ed-tabs')) return;
        const local = function (k) {
            try { return (window.localStorage && window.localStorage.getItem(k)) || ''; } catch (e) { return ''; }
        };
        placeTabs(arrangedCsv(entityId, local));
        let r;
        try { r = await window.LWAuth.apiGet('/api/view-state?prefix=' + encodeURIComponent(ARRANGE_PREFIX)); }
        catch (e) { return; }
        if (!r || !r.ok || !r.data || !r.data.values) return;
        const server = r.data.values;
        placeTabs(arrangedCsv(entityId, function (k) { return server[k] == null ? '' : String(server[k]); }));
    };

    ED.loadEntitySubLists = async function (entityId, panes) {
        _subLists.entityId = parseInt(entityId, 10) || 0;
        _subLists.panes    = panes;
        const jobs = [
            { urlFn: function () { return '/api/entities/' + entityId + '/addresses?activeOnly=' + (_addrShowInactive ? 'false' : 'true'); },
              pane: panes.addr,  render: ED.renderAddresses },
            { urlFn: function () {
                  return window.EntityPhones && window.FileKit
                      ? window.EntityPhones.firstPageUrl(entityId)
                      : '/api/entities/' + entityId + '/phones';
              },
              pane: panes.phone, render: ED.renderPhones },
            { urlFn: function () {
                  return window.EntityWeb && window.FileKit
                      ? window.EntityWeb.firstPageUrl(entityId)
                      : '/api/entities/' + entityId + '/web-addresses';
              },
              pane: panes.web,   render: ED.renderWebAddresses },
            { urlFn: function () { return '/api/entities/' + entityId + '/logins'; },
              pane: panes.login, render: ED.renderLogins }
        ];
        if (panes.events) {
            jobs.push({
                urlFn: function () {
                    return window.EntityNotes && window.FileKit
                        ? window.EntityNotes.firstPageUrl(entityId)
                        : '/api/entities/' + entityId + '/events';
                },
                pane: panes.events, render: ED.renderEvents
            });
        }
        if (panes.communications) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/communications'; },
                pane: panes.communications, render: ED.renderCommunications
            });
        }
        if (panes.vendors) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/vendors'; },
                pane: panes.vendors, render: ED.renderVendors
            });
        }
        if (panes.contacts) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/contacts'; },
                pane: panes.contacts, render: ED.renderContacts
            });
        }
        if (panes.userentities) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/userentities'; },
                pane: panes.userentities, render: ED.renderUserEntities
            });
        }
        if (panes.pagelinks) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/page-links'; },
                pane: panes.pagelinks, render: ED.renderPageLinks
            });
        }
        if (panes.solutions) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/solutions'; },
                pane: panes.solutions, render: ED.renderSolutions
            });
        }
        if (panes.codexpages) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/documentation'; },
                pane: panes.codexpages, render: ED.renderCodexPages
            });
        }
        if (panes.dashboards) {
            jobs.push({
                urlFn: function () { return '/api/entities/' + entityId + '/dashboards'; },
                pane: panes.dashboards, render: ED.renderDashboards
            });
        }
        // A record dashboard's Emails band (section key "emails") shows the
        // emails LINKED to the record -- the Gmail tab's list -- never every
        // email Leonardo sent to its address (Story 5614: a contact with two
        // linked emails showed 100). The record pages no longer carry a
        // "Sent from Leonardo" tab; Sent Emails is its own page.
        if (panes.emails) {
            jobs.push({
                urlFn: function () { return ED.gmailUrl(entityId); },
                pane: panes.emails, render: ED.renderGmail
            });
        }
        if (panes.gmail) {
            jobs.push({
                urlFn: function () { return ED.gmailUrl(entityId); },
                pane: panes.gmail, render: ED.renderGmail
            });
        }
        if (panes.files) {
            jobs.push({
                urlFn: function () {
                    return window.EntityFiles && window.FileKit
                        ? window.EntityFiles.firstPageUrl(entityId)
                        : '/api/entities/' + entityId + '/files';
                },
                pane: panes.files, render: ED.renderFiles
            });
        }
        if (panes.lists) {
            jobs.push({
                urlFn: function () { return '/api/lists/by-entity/' + entityId; },
                pane: panes.lists, render: ED.renderEntityLists
            });
        }
        if (panes.tasks) {
            jobs.push({
                urlFn: function () { return '/api/tasks/headers/by-entity/' + entityId; },
                pane: panes.tasks, render: ED.renderEntityTaskHeaders
            });
        }
        if (panes.checklists) {
            jobs.push({
                urlFn: function () { return '/api/checklists/by-entity/' + entityId; },
                pane: panes.checklists, render: ED.renderEntityChecklists
            });
        }
        if (panes.matters) {
            jobs.push({
                urlFn: function () { return ED.mattersUrl(entityId); },
                pane: panes.matters, render: ED.renderMatters
            });
        }
        if (panes.calllogs) {
            jobs.push({
                urlFn: function () { return ED.callLogsUrl(entityId); },
                pane: panes.calllogs, render: ED.renderCallLogs
            });
        }
        if (panes.vehicles) {
            jobs.push({
                urlFn: function () { return ED.vehiclesUrl(entityId); },
                pane: panes.vehicles, render: ED.renderVehicles
            });
        }
        if (panes.insurance) {
            jobs.push({
                urlFn: function () { return ED.insuranceUrl(entityId); },
                pane: panes.insurance, render: ED.renderInsurance
            });
        }
        // Story 3475 -- these go out TOGETHER, not one after another.
        //
        // No job reads another job's result: each is an independent GET against
        // its own endpoint, painting its own pane. The original loop awaited
        // them in sequence, which was harmless when this function fetched four
        // sub-lists (the header comment above still says four) and quietly
        // became the slowest thing in Leonardo as tabs were added -- sixteen
        // now. Serially, the cost is one full network round trip PER TAB before
        // the last pane paints: on the phone, through the Cloudflare tunnel,
        // that is sixteen sequential waits of a couple hundred milliseconds
        // each. Server-side nothing looked wrong -- every one of those requests
        // is logged at 1-10 ms -- because the time was never spent in the
        // server. In parallel the whole set costs about one round trip.
        //
        // Each job swallows its own failure so one dead endpoint cannot stop
        // the other fifteen panes from rendering; refreshOne already reports a
        // non-ok response into its own pane, and this catches an outright throw
        // (offline, aborted fetch) the same way.
        // The Needs attention tab, deliberately not awaited: three more reads
        // that no pane depends on (Story 3496; a tab since Story 3504). Re-run
        // on every reload so resolving a matter from its tab clears it.
        ED.renderAttentionTab(entityId);
        // Overview -- injected just after Needs attention (which
        // the line above has just put first), framed only when it is opened.
        ED.renderEntityDashboardTab(entityId);
        // Timeline (Story 4562) -- the last tab, drawn only when it is opened;
        // on the Overview it is a band of its own (panes.timeline).
        ED.renderTimelineTab(entityId);
        if (panes.timeline) ED.renderTimelinePane(panes.timeline, entityId);
        // Tabs in the order arranged in Entity Manager V (not awaited).
        ED.applyArrangedTabOrder(entityId);

        // A page that shows only some panes (the Health Records II issue page shows Notes and
        // Files, Story 5656) leaves the others out: a pane it does not have is not loaded.
        await Promise.all(jobs.filter(function (job) { return !!job.pane; }).map(function (job) {
            return refreshOne(job, entityId).catch(function (err) {
                ED.renderError(job.pane, 'Failed to load: '
                    + (err && err.message ? err.message : String(err)));
            });
        }));
    };

    // ===== Gmail tab (Story 5608) ===========================================
    //
    // The Gmail Client messages linked to this record. A message is a true
    // entity (its own EntityID), linked through EntityGmailClientMessageXRef; the
    // link is made here or from the message itself in the Gmail Client. A row
    // opens the message in the Gmail Client; Unlink removes only the link -- the
    // email stays in the Gmail Client. Link an email picks from the mail not yet
    // linked, mail from or to one of this record's own addresses first.
    //
    // SENT mail too (2026-10-12): the tab asks for it (withSent) and lists it
    // beside received mail, marked Sent and named by who it went to. A sent row
    // carries kind "sent" and a sent id -- its own numbering -- so every call for
    // it goes to /sent/<id> (gmailApi), never /messages/<id>.

    const GMAIL_PICK_TOP = 50;

    ED.gmailUrl = function (entityId) {
        return '/api/gmail-client/entities/' + entityId + '/messages?withSent=true';
    };

    function gmailIsSent(m) { return !!(m && m.kind === 'sent'); }

    /** The API path of one row: /sent/<id> for a sent email, /messages/<id> for a received one. */
    function gmailApi(m) {
        return '/api/gmail-client/' + (gmailIsSent(m) ? 'sent/' : 'messages/') + encodeURIComponent(m.id);
    }

    /** Who a row is about: the sender of a received email, "To <names>" for a sent one. */
    function gmailWho(m) {
        if (!gmailIsSent(m)) return gmailSender(m.from);
        const to = String(m.to || '').split(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/)
            .map(function (a) { return gmailSender(a); }).filter(function (a) { return a; }).join(', ');
        return to ? 'To ' + to : '';
    }

    /** "Pat Smith <pat@x.com>" -> "Pat Smith"; a bare address stays an address. */
    function gmailSender(from) {
        const s = String(from || '').trim();
        const m = /^"?([^"<]+?)"?\s*<[^>]+>$/.exec(s);
        return m ? m[1].trim() : s;
    }

    /** yyyy-MM-ddTHH:mm:ss -> MM/dd/yyyy h:mm am */
    function gmailWhen(iso) {
        const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(String(iso || ''));
        if (!m) return usDate(iso);
        const h = +m[4];
        return m[2] + '/' + m[3] + '/' + m[1] + ' ' + (h % 12 || 12) + ':' + m[5] + ' ' + (h < 12 ? 'am' : 'pm');
    }

    function gmailOpenUrl(m) {
        return '/gmail-client/gmail-client.html?' + (gmailIsSent(m) ? 'sent=' : 'focus=') + encodeURIComponent(m.id);
    }

    /** How an email is named in a question: "Subject" from Pat Smith (10/08/2026 9:15 am), or "Subject" sent to Pat Smith. */
    function gmailLabel(m) {
        const who = gmailIsSent(m) ? gmailWho(m).replace(/^To /, 'sent to ') : (gmailSender(m.from) ? 'from ' + gmailSender(m.from) : '');
        const when = gmailWhen(m.receivedDate);
        return '"' + gmailName(m) + '"' + (who ? ' ' + who : '') + (when ? ' (' + when + ')' : '');
    }

    /** The email's name on this record (2026-10-12): the name given when it was linked, else the subject. */
    function gmailName(m) {
        return (m && (m.emailName || m.subject)) || '(no subject)';
    }

    /** Longest Email Name a link keeps (EmailName / Subject width). */
    const GMAIL_NAME_MAX = 1000;

    ED.renderGmail = function (paneEl, data, entityId, refresh) {
        const rows = (data && data.messages) ? data.messages : (Array.isArray(data) ? data : []);
        let html = '<div class="ed-pane-actions">'
                 +   '<button type="button" class="ed-btn primary" data-act="linkGmail" data-testid="entity-gmail-link-btn">+ Link an email</button>'
                 +   '<a class="ed-btn" href="/gmail-client/gmail-client.html" data-testid="entity-gmail-browse-link">Open Gmail Client</a>'
                 + '</div>';
        if (!rows.length) {
            html += '<div class="ed-empty" data-testid="entity-gmail-empty">No emails linked to this record.</div>';
        } else {
            html += '<ul class="ed-stack">';
            rows.forEach(function (m, i) {
                const sent = gmailIsSent(m);
                const who = gmailWho(m);
                const when = gmailWhen(m.receivedDate);
                html += '<li class="ed-stack-row" data-testid="entity-gmail-row">'
                      +   '<a href="' + gmailOpenUrl(m) + '" class="ed-stack-name" data-testid="entity-gmail-open-link">'
                      +     ED.escapeHtml(gmailName(m)) + '</a>'
                      +   (sent ? '<span class="ed-chip" title="An email you sent" data-testid="entity-gmail-sent">Sent</span>' : '')
                      +   (m.isRead ? '' : '<span class="ed-chip ed-chip-blue" data-testid="entity-gmail-unread">Unread</span>')
                      +   (m.hasAttachments ? '<span class="ed-chip" title="Has attachments" data-testid="entity-gmail-clip">&#128206;</span>' : '')
                      +   '<button type="button" class="ed-btn" data-act="viewGmail" data-idx="' + i + '"'
                      +     ' title="Read it on sepia" data-testid="entity-gmail-view-btn">View</button>'
                      // A sent email is not an entity of its own, so it has no Linked list.
                      +   (sent ? '' : ED.linkedButton('gmail', m.id))
                      +   '<button type="button" class="ed-btn" data-act="renameGmail" data-idx="' + i + '"'
                      +     ' title="Change the name this email goes by on this record" data-testid="entity-gmail-rename-btn">Rename</button>'
                      +   '<button type="button" class="ed-btn danger" data-act="unlinkGmail" data-idx="' + i + '"'
                      +     ' data-testid="entity-gmail-unlink-btn">Unlink</button>'
                      +   '<div class="ed-stack-meta">' + ED.escapeHtml(who) + (who && when ? ' | ' : '') + ED.escapeHtml(when)
                      +     (m.emailName && m.subject && m.emailName !== m.subject
                              ? ' | Subject: ' + ED.escapeHtml(m.subject) : '') + '</div>'
                      + '</li>';
            });
            html += '</ul>';
        }
        paneEl.innerHTML = html;
        const linkBtn = paneEl.querySelector('[data-act="linkGmail"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkGmail(entityId, refresh); });
        paneEl.querySelectorAll('[data-act="viewGmail"]').forEach(function (b) {
            b.addEventListener('click', function () {
                const row = rows[parseInt(b.getAttribute('data-idx'), 10)];
                // Opening a message marks it read; redraw on Close so its Unread chip goes.
                ED.viewGmail(row, row && !row.isRead ? refresh : null);
            });
        });
        paneEl.querySelectorAll('[data-act="renameGmail"]').forEach(function (b) {
            b.addEventListener('click', function () {
                ED.renameGmail(entityId, rows[parseInt(b.getAttribute('data-idx'), 10)], refresh);
            });
        });
        paneEl.querySelectorAll('[data-act="unlinkGmail"]').forEach(function (b) {
            b.addEventListener('click', function () {
                ED.unlinkGmail(entityId, rows[parseInt(b.getAttribute('data-idx'), 10)], b, refresh);
            });
        });
    };

    // ----- View: one linked email on sepia ---------------------------------------
    //
    // The Gmail Client's quick view, brought to the record: the Record View's sepia
    // shell, the message's own colors giving way to paper and brown ink. The body is
    // sanitized on the server and shown in a sandboxed frame (no scripts; links open
    // a new tab), exactly as the Gmail Client shows it.

    const GMAIL_SEPIA_CSS = 'html,body{background:#F7F0DF !important}'
        + 'body{font:15px/1.6 Georgia,"Times New Roman",serif;color:#3B3323;margin:16px 22px;word-wrap:break-word}'
        + 'body *{background-color:transparent !important;color:#3B3323 !important;border-color:#D8C9A6 !important}'
        + 'a,a *{color:#8B5A2B !important}table{max-width:100%}pre{white-space:pre-wrap}img{max-width:100%;height:auto}';

    let _gmailViewEl = null;
    function ensureGmailView() {
        if (_gmailViewEl) return _gmailViewEl;
        const bg = document.createElement('div');
        bg.className = 'ed-rview-bg ed-gview-bg';
        bg.innerHTML =
            '<div class="ed-rview ed-gview" role="dialog" aria-modal="true" aria-labelledby="edGviewTitle" data-testid="entity-gmail-view">'
          +     '<div class="ed-gview-head">'
          +         '<h2 class="ed-gview-title" id="edGviewTitle" data-testid="entity-gmail-view-title"></h2>'
          +         '<div class="ed-gview-meta" data-testid="entity-gmail-view-meta"></div>'
          +     '</div>'
          +     '<div class="ed-gview-body" data-testid="entity-gmail-view-body"></div>'
          +     '<div class="ed-rview-actions">'
          +         '<a class="ed-btn" data-act="open" data-testid="entity-gmail-view-open">Open in Gmail Client</a>'
          +         '<button type="button" class="ed-btn primary" data-act="close" data-testid="entity-gmail-view-close">Close</button>'
          +     '</div>'
          + '</div>';
        document.body.appendChild(bg);
        bg.querySelector('[data-act="close"]').addEventListener('click', closeGmailView);
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && bg.classList.contains('open')) closeGmailView();
        });
        _gmailViewEl = bg;
        return bg;
    }

    function closeGmailView() {
        const bg = _gmailViewEl;
        if (!bg || !bg.classList.contains('open')) return;
        bg.classList.remove('open');
        bg.querySelector('.ed-gview-body').innerHTML = '';
        bg._seq = (bg._seq || 0) + 1;
        const onClose = bg._onClose;
        bg._onClose = null;
        if (typeof onClose === 'function') onClose();
    }

    /** "From Pat Smith  |  10/08/2026 9:15 am" (or "To Pat Smith  |  ..." when sent) -- the line under the subject. */
    function gmailMeta(m) {
        const who = gmailIsSent(m) ? gmailWho(m) : (gmailSender(m.from) ? 'From ' + gmailSender(m.from) : '');
        const when = gmailWhen(m.receivedDate);
        return who + (who && when ? '  |  ' : '') + (when || '');
    }

    /** Read one linked email on sepia. onClose (optional) runs when the sheet closes. */
    ED.viewGmail = async function (row, onClose) {
        if (!row) return;
        const bg = ensureGmailView();
        const body = bg.querySelector('.ed-gview-body');
        const seq = (bg._seq || 0) + 1;
        bg._seq = seq;
        bg._onClose = onClose || null;
        bg.querySelector('.ed-gview-title').textContent = row.subject || '(no subject)';
        bg.querySelector('.ed-gview-meta').textContent = gmailMeta(row);
        bg.querySelector('[data-act="open"]').setAttribute('href', gmailOpenUrl(row));
        body.innerHTML = '<div class="ed-gview-wait">Opening</div>';
        bg.classList.add('open');
        const r = await window.LWAuth.apiGet(gmailApi(row));
        if (bg._seq !== seq) return;
        if (!r || !r.ok || !r.data) {
            body.innerHTML = '<div class="ed-gview-wait">' + window.LWAuth.errorHtml(r, 'Could not open that email') + '</div>';
            return;
        }
        const m = r.data;
        if (!m.kind) m.kind = row.kind;
        bg.querySelector('.ed-gview-title').textContent = m.subject || row.subject || '(no subject)';
        bg.querySelector('.ed-gview-meta').textContent = gmailMeta(m);
        if (m.bodyHtml) {
            const doc = '<!doctype html><html><head><meta charset="utf-8"><base target="_blank">'
                + '<style>' + GMAIL_SEPIA_CSS + '</style></head><body>' + m.bodyHtml + '</body></html>';
            body.innerHTML = '<iframe class="ed-gview-frame" sandbox="allow-popups allow-popups-to-escape-sandbox"'
                + ' title="Email" data-testid="entity-gmail-view-frame" srcdoc="'
                + doc.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '"></iframe>';
        } else {
            body.innerHTML = '<pre class="ed-gview-text" data-testid="entity-gmail-view-text">'
                + ED.escapeHtml(m.bodyText || '(no text)') + '</pre>';
        }
        if (m.attachments && m.attachments.length) {
            body.insertAdjacentHTML('beforeend', '<div class="ed-gview-attach" data-testid="entity-gmail-view-attachments">'
                + '&#128206; ' + ED.escapeHtml(m.attachments.join(', '))
                + ' <span class="ed-gview-attach-note">(open them in the Gmail Client)</span></div>');
        }
    };

    /**
     * Link an email that is not linked yet -- received or sent: the server does the
     * matching as Tony types. A pick's id is "m<id>" (received) or "s<id>" (sent).
     */
    ED.linkGmail = function (entityId, refresh) {
        const base = '/api/gmail-client/entities/' + entityId + '/available?withSent=true&top=' + GMAIL_PICK_TOP;
        // Email Name follows the picked email's subject until Tony types his own;
        // once he has, a later pick leaves his name alone.
        let autoName = '';
        function fillName(row) {
            const el = document.getElementById('lwed_emailName');
            if (!el || !row) return;
            const cur = el.value;
            if (cur.trim() !== '' && cur !== autoName) return;
            autoName = row.subject || '';
            el.value = autoName;
        }
        ED.openModal({
            title: 'Link an email',
            fields: [
                { key: 'messageId', label: 'Email (search by sender, recipient or subject)', type: 'typeahead', required: true, value: '',
                  search: async function (needle) {
                      const q = (needle || '').trim();
                      const r = await window.LWAuth.apiGet(base + (q ? '&q=' + encodeURIComponent(q) : ''));
                      const errEl = document.getElementById('lwedErr');
                      if (!r || !r.ok) {
                          // The picker swallows a thrown error into "No matches"; say what happened instead.
                          if (errEl) errEl.textContent = window.LWAuth.errorText(r, 'Could not load the emails');
                          return [];
                      }
                      if (errEl) errEl.textContent = '';
                      return ((r.data && r.data.messages) || []).map(function (m) {
                          const sent = gmailIsSent(m);
                          const who = sent ? gmailWho(m) : gmailSender(m.from);
                          return {
                              id: (sent ? 's' : 'm') + m.id,
                              name: (sent ? 'Sent: ' : '') + (m.subject || '(no subject)') + (who ? ' -- ' + who : '')
                                  + ' | ' + gmailWhen(m.receivedDate) + (m.related ? ' | to or from this record' : ''),
                              subject: m.subject || ''
                          };
                      });
                  },
                  onPick: fillName },
                { key: 'emailName', label: 'Email Name (starts as the subject -- change it if you like)', type: 'text',
                  value: '', maxlength: GMAIL_NAME_MAX }
            ],
            onSave: async function (data) {
                const pick = /^([ms])(\d+)$/.exec(String(data.messageId || ''));
                if (!pick) throw new Error('Pick an email first.');
                const row = { id: parseInt(pick[2], 10), kind: pick[1] === 's' ? 'sent' : 'inbox' };
                const link = await window.LWAuth.apiPost(gmailApi(row) + '/links',
                    { entityId: entityId, emailName: (data.emailName || '').trim() }, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Rename one linked email on this record (the email itself is not changed). Blank = back to the subject. */
    ED.renameGmail = function (entityId, row, refresh) {
        if (!row) return;
        ED.openModal({
            title: 'Rename email',
            focusKey: 'emailName',
            fields: [
                { key: 'emailName', label: 'Email Name (leave blank for the subject: ' + (row.subject || '(no subject)') + ')',
                  type: 'text', value: gmailName(row), maxlength: GMAIL_NAME_MAX }
            ],
            onSave: async function (data) {
                const typed = (data.emailName || '').trim();
                const r = await window.LWAuth.apiPost(gmailApi(row) + '/links',
                    { entityId: entityId, emailName: typed || row.subject || '' }, 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Rename failed'));
                refresh();
            }
        });
    };

    /** Unlink one email from this record. The email stays in the Gmail Client. */
    ED.unlinkGmail = async function (entityId, row, btn, refresh) {
        if (!row) return;
        const ok = await window.Picasso.yesNo('Unlink the email ' + gmailLabel(row) + ' from this record?\n\n'
            + 'The email itself is kept in the Gmail Client -- only its link to this record is removed.',
            { title: 'Unlink email', yes: 'Unlink', no: 'Cancel' });
        if (!ok) return;
        if (btn) btn.disabled = true;
        const r = await window.LWAuth.apiPost(gmailApi(row) + '/links/' + entityId, null, 'DELETE');
        if (!r || !r.ok) {
            if (btn) btn.disabled = false;
            window.Picasso.error(window.LWAuth.errorText(r, 'Unlink failed'));
            return;
        }
        refresh();
    };

    // ===== Lists and Task Lists tabs ========================================

    ED.renderEntityLists = function (pane, rows, entityId, refresh) {
        var actions = '<div class="ed-pane-actions">'
                    +   '<button type="button" class="ed-btn primary" data-act="linkList" data-testid="entity-list-link-btn">+ Link List</button>'
                    +   '<button type="button" class="ed-btn" data-act="newList" data-testid="entity-list-new-btn">+ New List</button>'
                    +   '<a class="ed-btn" href="/lists/lists.html">Open Lists page</a>'
                    + '</div>';
        var body;
        if (!rows || !rows.length) {
            body = '<div class="ed-empty">No lists are linked to this entity.</div>';
        } else {
            body = '<ul class="ed-stack">';
            for (var i = 0; i < rows.length; i++) {
                var r = rows[i];
                var typeChip = r.listType ? '<span class="ed-chip">' + ED.escapeHtml(r.listType) + '</span>' : '';
                body += '<li class="ed-stack-row" data-testid="entity-list-row">'
                      +   '<a href="/lists/list.html?id=' + encodeURIComponent(r.listHeaderId) + '" class="ed-stack-name">'
                      +     ED.escapeHtml(r.name || '(unnamed)') + '</a>'
                      +   typeChip
                      +   ED.pinButton('list', r.listHeaderId, r.name || '(unnamed)', r.listType || 'List',
                                     '/lists/list.html?id=' + encodeURIComponent(r.listHeaderId))
                      +   '<button type="button" class="ed-btn danger" data-act="unlinkList" data-idx="' + i + '" data-testid="entity-list-unlink-btn">Unlink</button>'
                      +   (r.notes ? '<div class="ed-stack-meta">' + ED.escapeHtml(r.notes) + '</div>' : '')
                      + '</li>';
            }
            body += '</ul>';
        }
        pane.innerHTML = actions + body;
        var linkBtn = pane.querySelector('[data-act="linkList"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkList(entityId, refresh); });
        var newListBtn = pane.querySelector('[data-act="newList"]');
        if (newListBtn) newListBtn.addEventListener('click', function () { ED.createList(entityId, refresh); });
        pane.querySelectorAll('[data-act="unlinkList"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                ED.unlinkList(entityId, rows[parseInt(btn.getAttribute('data-idx'), 10)], refresh);
            });
        });
    };

    ED.linkList = async function (entityId, refresh) {
        var r = await window.LWAuth.apiGet('/api/lists');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load lists.')); return; }
        var items = (r.data || []).map(function (l) {
            return { id: l.listHeaderId, name: l.name + (l.listType ? ' (' + l.listType + ')' : '') };
        });
        ED.openModal({
            title: 'Link an existing list',
            // type:'select' (PicassoSelect-enhanced) rather than the custom
            // typeahead: PicassoSelect keeps the chosen value separate from its
            // filter box and selects on click, so searching then tapping a
            // result links reliably on mobile. required:false keeps a blank
            // first option so nothing is pre-linked until the user picks.
            fields: [
                { key: 'listHeaderId', sortable: true, label: 'List', type: 'select', options: items, required: false, value: '' }
            ],
            onSave: async function (data) {
                var id = parseInt(data.listHeaderId, 10) || 0;
                if (!id) throw new Error('Pick a list first.');
                var link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/lists/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    /** Story 2414: create a list and link it to this record in one step. */
    ED.createList = async function (entityId, refresh) {
        var types = await ED.getLookup('list-types') || [];
        ED.openModal({
            title: 'New list for this record',
            fields: [
                { key: 'name',       label: 'List name', type: 'text',      maxlength: 100, required: true, value: '' },
                { key: 'listTypeId', label: 'Type',      type: 'typeahead', options: types, required: true, value: '',
                  addNew: 'list-types', addNoun: 'list type' },
                { key: 'notes',      label: 'Notes',     type: 'textarea',  value: '' }
            ],
            onSave: async function (data) {
                var body = {
                    name:       (data.name || '').trim(),
                    listTypeId: parseInt(data.listTypeId, 10) || 0,
                    notes:      data.notes || ''
                };
                if (!body.name)       throw new Error('List name is required');
                if (!body.listTypeId) throw new Error('Type is required');
                var created = await window.LWAuth.apiPost('/api/lists', body, 'POST');
                if (!created || !created.ok)
                    throw new Error(window.LWAuth.errorText(created, 'Save failed'));
                var newId = parseInt(created.data && created.data.listHeaderId, 10) || 0;
                if (!newId) throw new Error('The list was saved but no id came back, so it was not linked.');
                var link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/lists/' + newId, null, 'POST');
                if (!link || !link.ok)
                    throw new Error(window.LWAuth.errorText(link, 'The list was created but the link failed.'));
                refresh();
            }
        });
    };

    ED.unlinkList = async function (entityId, row, refresh) {
        if (!row) return;
        if (!confirm('Unlink list "' + (row.name || '') + '" from this entity?\n\n'
                   + 'The list itself stays in Leonardo; only the link to this record is removed.')) return;
        var r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/lists/' + row.listHeaderId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    ED.renderEntityTaskHeaders = function (pane, rows, entityId, refresh) {
        var actions = '<div class="ed-pane-actions">'
                    +   '<button type="button" class="ed-btn primary" data-act="linkTask" data-testid="entity-task-link-btn">+ Link Task List</button>'
                    +   '<a class="ed-btn" href="/tasks/tasks.html">Open Tasks page</a>'
                    + '</div>';
        var body;
        if (!rows || !rows.length) {
            body = '<div class="ed-empty">No task lists are linked to this entity.</div>';
        } else {
            body = '<ul class="ed-stack">';
            for (var i = 0; i < rows.length; i++) {
                var r = rows[i];
                var openChip = (r.openCount && r.openCount > 0)
                    ? '<span class="ed-chip ed-chip-blue">' + r.openCount + ' open</span>'
                    : '';
                body += '<li class="ed-stack-row" data-testid="entity-task-row">'
                      +   '<a href="/tasks/tasks.html?headerid=' + encodeURIComponent(r.taskHeaderId) + '" class="ed-stack-name">'
                      +     ED.escapeHtml(r.taskHeaderDesc || '(unnamed)') + '</a>'
                      +   openChip
                      +   ED.pinButton('task', r.taskHeaderId, r.taskHeaderDesc || '(unnamed)',
                                     r.openCount ? r.openCount + ' open' : 'Task list',
                                     '/tasks/tasks.html?headerid=' + encodeURIComponent(r.taskHeaderId))
                      +   '<button type="button" class="ed-btn danger" data-act="unlinkTask" data-idx="' + i + '" data-testid="entity-task-unlink-btn">Unlink</button>'
                      +   (r.notes ? '<div class="ed-stack-meta">' + ED.escapeHtml(r.notes) + '</div>' : '')
                      + '</li>';
            }
            body += '</ul>';
        }
        pane.innerHTML = actions + body;
        var linkBtn = pane.querySelector('[data-act="linkTask"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkTaskHeader(entityId, refresh); });
        pane.querySelectorAll('[data-act="unlinkTask"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                ED.unlinkTaskHeader(entityId, rows[parseInt(btn.getAttribute('data-idx'), 10)], refresh);
            });
        });
    };

    ED.linkTaskHeader = async function (entityId, refresh) {
        var r = await window.LWAuth.apiGet('/api/tasks/headers');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load task lists.')); return; }
        var items = (r.data || []).map(function (t) {
            return { id: t.taskHeaderId, name: t.taskHeaderDesc };
        });
        ED.openModal({
            title: 'Link an existing task list',
            // See linkList: PicassoSelect-enhanced select rather than the custom
            // typeahead so search-then-tap links reliably on mobile.
            fields: [
                { key: 'taskHeaderId', sortable: true, label: 'Task List', type: 'select', options: items, required: false, value: '' }
            ],
            onSave: async function (data) {
                var id = parseInt(data.taskHeaderId, 10) || 0;
                if (!id) throw new Error('Pick a task list first.');
                var link = await window.LWAuth.apiPost('/api/entities/' + entityId + '/task-headers/' + id, null, 'POST');
                if (!link || !link.ok) throw new Error(window.LWAuth.errorText(link, 'Link failed'));
                refresh();
            }
        });
    };

    ED.unlinkTaskHeader = async function (entityId, row, refresh) {
        if (!row) return;
        if (!confirm('Unlink task list "' + (row.taskHeaderDesc || '') + '" from this entity?\n\n'
                   + 'The task list itself stays in Leonardo; only the link to this record is removed.')) return;
        var r = await window.LWAuth.apiPost('/api/entities/' + entityId + '/task-headers/' + row.taskHeaderId, null, 'DELETE');
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Unlink failed')); return; }
        refresh();
    };

    // ===== Checklists tab (Story 2264) ======================================
    // A checklist links to a single entity through Checklist.LinkedEntityID
    // (one-to-one), unlike the many-to-many Lists / Task Lists xrefs. So this
    // tab reads the entity's linked checklists, lets you create one already
    // linked, attach an existing unlinked checklist, or unlink one (which frees
    // it, it is never deleted).

    ED.renderEntityChecklists = function (pane, rows, entityId, refresh) {
        var actions = '<div class="ed-pane-actions">'
                    +   '<button type="button" class="ed-btn primary" data-act="newChecklist" data-testid="entity-checklist-new-btn">+ New Checklist</button>'
                    +   '<button type="button" class="ed-btn" data-act="linkChecklist" data-testid="entity-checklist-link-btn">+ Link Existing</button>'
                    +   '<a class="ed-btn" href="/checklists/checklists.html">Open Checklists page</a>'
                    + '</div>';
        var body;
        if (!rows || !rows.length) {
            body = '<div class="ed-empty">No checklists are linked to this entity.</div>';
        } else {
            body = '<ul class="ed-stack">';
            for (var i = 0; i < rows.length; i++) {
                var r = rows[i];
                var total = r.totalItems || 0, checked = r.checkedItems || 0;
                var progressChip = '<span class="ed-chip ed-chip-blue">' + checked + '/' + total + '</span>';
                var resolvedChip = r.isResolved ? '<span class="ed-chip ed-chip-green">Resolved</span>' : '';
                body += '<li class="ed-stack-row" data-testid="entity-checklist-row">'
                      +   '<a href="/checklists/checklist.html?id=' + encodeURIComponent(r.checklistId) + '" class="ed-stack-name">'
                      +     ED.escapeHtml(r.name || '(unnamed)') + '</a>'
                      +   progressChip + resolvedChip
                      +   ED.pinButton('checklist', r.checklistId, r.name || '(unnamed)',
                                     (r.checkedItems || 0) + '/' + (r.totalItems || 0) + (r.isResolved ? ' | Resolved' : ''),
                                     '/checklists/checklist.html?id=' + encodeURIComponent(r.checklistId))
                      +   '<button type="button" class="ed-btn danger" data-act="unlinkChecklist" data-idx="' + i + '" data-testid="entity-checklist-unlink-btn">Unlink</button>'
                      +   (r.description ? '<div class="ed-stack-meta">' + ED.escapeHtml(r.description) + '</div>' : '')
                      + '</li>';
            }
            body += '</ul>';
        }
        pane.innerHTML = actions + body;
        var newBtn = pane.querySelector('[data-act="newChecklist"]');
        if (newBtn) newBtn.addEventListener('click', function () { ED.newChecklist(entityId, refresh); });
        var linkBtn = pane.querySelector('[data-act="linkChecklist"]');
        if (linkBtn) linkBtn.addEventListener('click', function () { ED.linkChecklist(entityId, refresh); });
        pane.querySelectorAll('[data-act="unlinkChecklist"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                ED.unlinkChecklist(entityId, rows[parseInt(btn.getAttribute('data-idx'), 10)], refresh);
            });
        });
    };

    ED.newChecklist = function (entityId, refresh) {
        ED.openModal({
            title: 'New checklist for this entity',
            fields: [
                { key: 'name', label: 'Checklist name', type: 'text', required: true, value: '' }
            ],
            onSave: async function (data) {
                var name = (data.name || '').trim();
                if (!name) throw new Error('Enter a name first.');
                var r = await window.LWAuth.apiPost('/api/checklists',
                    { name: name, linkedEntityId: entityId }, 'POST');
                if (!r || !r.ok) throw new Error(window.LWAuth.errorText(r, 'Create failed'));
                refresh();
            }
        });
    };

    ED.linkChecklist = async function (entityId, refresh) {
        // The server does the filtering: active, non-template, and not already
        // on THIS record. A checklist attached to other records IS offered --
        // attaching it here does not take it off them.
        var r = await window.LWAuth.apiGet('/api/checklists/available-for-entity/' + entityId);
        if (!r || !r.ok) { alert(window.LWAuth.errorText(r, 'Could not load checklists.')); return; }
        var items = (r.data || []).map(function (c) {
            var label = c.name;
            if (c.linkedEntityNames) label += '   (also on: ' + c.linkedEntityNames + ')';
            return { id: c.checklistId, name: label };
        });
        if (!items.length) { alert('No other active checklists are available to attach. Use "+ New Checklist" instead.'); return; }
        ED.openModal({
            title: 'Link an existing checklist',
            // PicassoSelect-enhanced select (see linkList) so search-then-tap
            // links reliably on mobile; blank first option means nothing is
            // pre-linked until the user picks.
            fields: [
                { key: 'checklistId', sortable: true, label: 'Checklist', type: 'select', options: items, required: false, value: '' }
            ],
            onSave: async function (data) {
                var id = parseInt(data.checklistId, 10) || 0;
                if (!id) throw new Error('Pick a checklist first.');
                // Pure add -- no confirm needed, nothing is taken from anyone.
                await ED.linkChecklistToEntity(id, entityId);
                refresh();
            }
        });
    };

    ED.unlinkChecklist = async function (entityId, row, refresh) {
        if (!row) return;
        // Say what unlinking here actually costs: the checklist may well be on
        // other records, and those keep it.
        var others = row.otherLinkCount || 0;
        var stays  = others > 0
            ? 'It stays on ' + others + ' other record' + (others === 1 ? '' : 's') + '.'
            : 'The checklist itself stays in Leonardo; only the link to this record is removed.';
        if (!confirm('Unlink checklist "' + (row.name || '') + '" from this entity?\n\n' + stays)) return;
        await ED.unlinkChecklistFromEntity(row.checklistId, entityId);
        refresh();
    };

    // Attaching and detaching are pairings in their own right now, not edits to
    // the checklist header, so neither call touches the checklist's own fields.
    ED.linkChecklistToEntity = async function (checklistId, entityId) {
        var p = await window.LWAuth.apiPost('/api/checklists/' + checklistId + '/entities/' + entityId, {});
        if (!p || !p.ok) throw new Error(window.LWAuth.errorText(p, 'Link failed'));
    };

    ED.unlinkChecklistFromEntity = async function (checklistId, entityId) {
        var p = await window.LWAuth.apiPost('/api/checklists/' + checklistId + '/entities/' + entityId, {}, 'DELETE');
        if (!p || !p.ok) throw new Error(window.LWAuth.errorText(p, 'Unlink failed'));
    };

    // Which Pin kind each tab's rows carry (Story 3504) -- keyed on the render
    // function, so a job needs no extra field. Read when the pane has drawn.
    function pinKindOfRender(render) {
        const map = [
            [ED.renderAddresses, 'address'], [ED.renderPhones, 'phone'], [ED.renderWebAddresses, 'web'],
            [ED.renderLogins, 'login'], [ED.renderEvents, 'event'], [ED.renderCommunications, 'communication'],
            [ED.renderVendors, 'vendor'], [ED.renderContacts, 'contact'], [ED.renderUserEntities, 'userentity'],
            [ED.renderPageLinks, 'pagelink'], [ED.renderDashboards, 'dashboard'], [ED.renderFiles, 'file'],
            [ED.renderMatters, 'matter'], [ED.renderCallLogs, 'calllog'], [ED.renderEntityLists, 'list'],
            [ED.renderEntityTaskHeaders, 'task'], [ED.renderEntityChecklists, 'checklist'],
            [ED.renderSolutions, 'solution'], [ED.renderCodexPages, 'codexpage'],
            [ED.renderInsurance, 'insuranceplan']
        ];
        for (let i = 0; i < map.length; i++) if (map[i][0] && map[i][0] === render) return map[i][1];
        return null;
    }

    async function refreshOne(job, entityId) {
        const r = await window.LWAuth.apiGet(job.urlFn());
        if (!r) return;
        if (!r.ok) { ED.renderError(job.pane, 'Failed to load: ' + (r.data && r.data.error ? r.data.error : r.status)); return; }
        job.render(job.pane, r.data, entityId, function () { refreshOne(job, entityId); });
        // Pinned rows of this kind now read the live row (or drop out if it is gone).
        ED.attentionTabDrew(pinKindOfRender(job.render));
    }
})();
