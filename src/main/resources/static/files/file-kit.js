/* FileKit -- shared pieces of organized Files (Story 3520).
 *
 * Used by the Files tab on every record page (contacts/entity-files.js) and by the
 * Leonardo Files page (files/files.js):
 *
 *   FileKit.loadCategories(force)   -> Promise<[{id,name,color,sortOrder,useCount}]>, cached
 *   FileKit.pickCategory(opts)      -> Promise<{categoryId}|{categoryName}|{staging:true}|null>
 *                                      the "File to" sheet: one tap on a category chip, or type
 *                                      a new name (find-or-create) and Save
 *   FileKit.manageCategories(opts)  -> the Categories sheet: rename, colour, reorder, delete
 *   FileKit.categoryChip(cat, opts) -> chip markup
 *   FileKit.typeClass(family)       -> css class for a file-type badge
 *   FileKit.fmtBytes / fmtDate / esc
 *
 * The categories are a shared lookup (dbo.FileCategory): every record files under the same
 * list, and typing a name nobody has used yet is how a category is born.
 */
(function () {
    'use strict';
    if (window.FileKit) return;

    function esc(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function fmtBytes(n) {
        if (n == null || n === '') return '';
        n = Number(n) || 0;
        if (n < 1024) return n + ' B';
        if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
        if (n < 1024 * 1024 * 1024) return (n / (1024 * 1024)).toFixed(1) + ' MB';
        return (n / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
    }

    /** mm/dd/yyyy from an ISO date or date-time string; '' when absent. */
    function fmtDate(iso) {
        if (!iso) return '';
        var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso));
        return m ? (m[2] + '/' + m[3] + '/' + m[1]) : '';
    }

    function typeClass(family) {
        switch (family) {
            case 'PDF':             return 'fk-type-pdf';
            case 'Images':          return 'fk-type-img';
            case 'Documents':       return 'fk-type-doc';
            case 'Spreadsheets':    return 'fk-type-sheet';
            case 'Web pages':       return 'fk-type-web';
            case 'Audio and video': return 'fk-type-media';
            default:                return 'fk-type-other';
        }
    }

    var NEUTRAL = '#64748B';
    function colorOf(c) { return (c && /^#[0-9A-Fa-f]{6}$/.test(c)) ? c : NEUTRAL; }

    /** A category chip. opts.act = data-act value (makes it a button), opts.testid, opts.extraClass. */
    function categoryChip(cat, opts) {
        opts = opts || {};
        var color = colorOf(cat && cat.color);
        var name  = cat && cat.name ? cat.name : 'Staging';
        var tag   = opts.act ? 'button' : 'span';
        return '<' + tag + (opts.act ? ' type="button" data-act="' + esc(opts.act) + '"' : '')
             + (cat && cat.id ? ' data-cat-id="' + esc(cat.id) + '"' : '')
             + ' class="fk-chip ' + (opts.extraClass || '') + '"'
             + ' style="--fk-c:' + esc(color) + '"'
             + (opts.testid ? ' data-testid="' + esc(opts.testid) + '"' : '') + '>'
             + '<span class="fk-dot"></span>' + esc(name)
             + (opts.count != null ? ' <span class="fk-chip-n">' + esc(opts.count) + '</span>' : '')
             + '</' + tag + '>';
    }

    function api() { return window.LWAuth; }

    /** The whole story of a failed call -- what, why, HTTP status, address (Story 5570). */
    function errText(r, fallback) {
        if (window.LWAuth && window.LWAuth.errorText) return window.LWAuth.errorText(r, fallback);
        return (r && r.data && r.data.error) ? r.data.error : fallback;
    }

    // ===== Sheet shell ======================================================
    // One overlay per open sheet. Bottom sheet on a phone, centred card on a desktop.

    function openSheet(title, testid) {
        var bg = document.createElement('div');
        bg.className = 'fk-sheet-bg';
        bg.innerHTML = '<div class="fk-sheet" role="dialog" aria-modal="true"'
                     +     (testid ? ' data-testid="' + esc(testid) + '"' : '') + '>'
                     +   '<div class="fk-sheet-head"><h2>' + esc(title) + '</h2></div>'
                     +   '<div class="fk-sheet-body"></div>'
                     +   '<div class="fk-sheet-extra"></div>'
                     +   '<div class="fk-sheet-err"></div>'
                     +   '<div class="fk-sheet-foot"></div>'
                     + '</div>';
        document.body.appendChild(bg);
        requestAnimationFrame(function () { bg.classList.add('open'); });
        var sheet = {
            bg:   bg,
            body: bg.querySelector('.fk-sheet-body'),
            extra: bg.querySelector('.fk-sheet-extra'),
            foot: bg.querySelector('.fk-sheet-foot'),
            err:  bg.querySelector('.fk-sheet-err'),
            onDismiss: null,
            close: function () {
                bg.classList.remove('open');
                setTimeout(function () { if (bg.parentNode) bg.parentNode.removeChild(bg); }, 180);
            }
        };
        return sheet;
    }

    // ===== One list of categories (a kit) =======================================
    // FileKit started as the file-category kit (Story 3520); the same chips, File to sheet
    // and manager now serve any list shaped like it -- note types (Story 3524) -- through
    // FileKit.kit({ api, noun, ... }). The default kit below is still file categories.

    function createKit(cfg) {
    var _cats = null;
    var _catsPromise = null;

    function loadCategories(force) {
        if (_cats && !force) return Promise.resolve(_cats);
        if (_catsPromise && !force) return _catsPromise;
        _catsPromise = Promise.resolve(api().apiGet(cfg.api)).then(function (r) {
            _catsPromise = null;
            if (!r || !r.ok || !Array.isArray(r.data)) throw new Error('Could not load the ' + cfg.nounPlural + '.');
            _cats = r.data;
            return _cats;
        }, function (e) { _catsPromise = null; throw e; });
        return _catsPromise;
    }

    // ===== File to ============================================================

    /**
     * The "File to" sheet. Resolves to what the caller should send:
     *   { categoryId: n }       an existing one was picked
     *   { categoryName: 'X' }   a new name was typed (the server finds or creates it)
     *   { staging: true }       Back to staging
     *   null                    cancelled
     * opts: { title, subtitle, currentId, excludeId, allowStaging, noNew,
     *         preferIds (no longer used: the list is alphabetical) }
     *
     * No chips here (Story 5592): one typeahead, its list open under the box. Typing
     * filters the list; one tap picks and closes. The chips stay on the page itself.
     */
    function pickCategory(opts) {
        opts = opts || {};
        return loadCategories().then(function (cats) {
            return new Promise(function (resolve) {
                var done = false;
                function finish(v) { if (done) return; done = true; sheet.close(); resolve(v); }

                var sheet = openSheet(opts.title || cfg.pickTitle, 'file-to-sheet');
                sheet.onDismiss = function () { if (!done) { done = true; resolve(null); } };

                // Alphabetical, so a name is found by reading down the list (Tony, 2026-09-24).
                // The group order (sortOrder) still drives the tab's bands and the manager.
                var ordered = cats.filter(function (c) { return c.id !== opts.excludeId; }).sort(function (a, b) {
                    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
                });
                var current = cats.find(function (c) { return opts.currentId && c.id === opts.currentId; });

                sheet.body.innerHTML =
                      (opts.subtitle ? '<div class="fk-sub">' + esc(opts.subtitle) + '</div>' : '')
                    + (current ? '<div class="fk-sub" data-testid="file-to-current">Now: <strong>' + esc(current.name) + '</strong></div>' : '')
                    + '<div class="fk-new-row">'
                    +   '<div class="picker-wrap fk-new-wrap fk-pick-wrap">'
                    +     '<input id="fkNewCat" class="picker-input" type="text" autocomplete="off" maxlength="' + cfg.maxLen + '"'
                    +       ' placeholder="' + esc(opts.noNew ? 'Type to find a ' + cfg.noun : 'Type to find or add a ' + cfg.noun) + '"'
                    +       ' aria-label="' + esc(cfg.noun) + '" data-testid="file-to-new-input">'
                    +     '<div class="picker-dropdown fk-pick-list" data-testid="file-to-list"></div>'
                    +   '</div>'
                    +   (opts.noNew ? '' : '<button type="button" class="fk-btn primary" data-act="save-new" data-testid="file-to-save-btn">Save</button>')
                    + '</div>';

                sheet.foot.innerHTML =
                      (opts.allowStaging
                        ? '<button type="button" class="fk-btn" data-act="staging" data-testid="file-to-staging-btn">'
                          + esc(cfg.stagingLabel || 'Back to staging') + '</button>'
                        : '')
                    + '<button type="button" class="fk-btn" data-act="cancel" data-testid="file-to-cancel-btn">Cancel</button>';

                var input = sheet.body.querySelector('#fkNewCat');
                var list  = sheet.body.querySelector('.fk-pick-list');

                function finishName(name) {
                    var hit = cats.find(function (c) { return c.name.toLowerCase() === name.toLowerCase(); });
                    if (hit) finish({ categoryId: hit.id });
                    else if (opts.noNew) { sheet.err.textContent = 'Pick a ' + cfg.noun + ' from the list.'; input.focus(); }
                    else { _cats = null; finish({ categoryName: name }); }
                }

                var picker = window.PicassoPicker && window.PicassoPicker.attach({
                    input:    input,
                    dropdown: list,
                    getItems: function () { return ordered.map(function (c) { return { id: c.id, name: c.name }; }); },
                    maxShown: 1000,
                    allowNew: !opts.noNew,
                    newLabel: 'New ' + cfg.noun,
                    onSelect: function (item) {
                        var id = parseInt(item.id, 10);
                        if (!item.created && id > 0) finish({ categoryId: id });
                        else if (item.name) finishName(item.name);
                    }
                });
                if (!picker) { sheet.err.textContent = 'The ' + cfg.noun + ' list could not start on this page.'; return; }
                picker.open();
                // Focus straight into the box so typing filters at once (a phone may hold the
                // keyboard back until the box is tapped; the list is tappable either way).
                setTimeout(function () { try { input.focus(); } catch (e) {} }, 60);

                function saveTyped() {
                    var name = (input.value || '').trim();
                    if (!name) { sheet.err.textContent = 'Type a ' + cfg.noun + ' name, or pick one from the list.'; input.focus(); return; }
                    finishName(name);
                }

                input.addEventListener('keydown', function (e) {
                    // The picker handles Enter on a highlighted row itself.
                    if (e.key === 'Enter' && !list.querySelector('.picker-option.active')) { e.preventDefault(); saveTyped(); }
                });
                sheet.bg.addEventListener('click', function (e) {
                    var b = e.target.closest('[data-act]');
                    if (!b || !sheet.bg.contains(b)) return;
                    var act = b.getAttribute('data-act');
                    if (act === 'save-new') saveTyped();
                    if (act === 'staging')  finish({ staging: true });
                    if (act === 'cancel')   finish(null);
                });
            });
        });
    }

    // ===== Categories manager ===================================================

    /**
     * The Categories sheet: every category with its colour, name, how many documents use
     * it, move up / down, and delete. Each change saves as it is made; opts.onChange() runs
     * after any change so the caller can redraw.
     */
    function manageCategories(opts) {
        opts = opts || {};
        var sheet = openSheet(cfg.title, 'file-categories-sheet');
        var changed = false;
        sheet.foot.innerHTML = '<button type="button" class="fk-btn primary" data-act="close" data-testid="file-categories-close-btn">Close</button>';
        sheet.onDismiss = function () { if (changed && opts.onChange) opts.onChange(); };

        function flagChange() { changed = true; _cats = null; }

        function draw() {
            loadCategories(true).then(function (cats) {
                sheet.body.innerHTML =
                      '<div class="fk-cat-list">'
                    +   cats.map(function (c, i) {
                            return '<div class="fk-cat-row" data-id="' + c.id + '" data-testid="file-category-row">'
                                 +   '<button type="button" class="fk-swatch" data-act="palette" style="background:' + esc(colorOf(c.color)) + '"'
                                 +     ' aria-label="Colour of ' + esc(c.name) + '" data-testid="file-category-color"></button>'
                                 +   '<input type="hidden" class="fk-color" value="' + esc(colorOf(c.color)) + '">'
                                 +   '<input type="text" class="fk-cat-name" maxlength="' + cfg.maxLen + '" value="' + esc(c.name) + '"'
                                 +     ' aria-label="Name" data-testid="file-category-name">'
                                 +   '<span class="fk-cat-uses" title="' + esc(cfg.usesLabel) + '">' + (c.useCount || 0) + '</span>'
                                 +   '<button type="button" class="fk-icon-btn" data-act="up" aria-label="Move up"'
                                 +     (i === 0 ? ' disabled' : '') + ' data-testid="file-category-up-btn">&#9650;</button>'
                                 +   '<button type="button" class="fk-icon-btn" data-act="down" aria-label="Move down"'
                                 +     (i === cats.length - 1 ? ' disabled' : '') + ' data-testid="file-category-down-btn">&#9660;</button>'
                                 +   '<button type="button" class="fk-icon-btn danger" data-act="del" aria-label="Delete"'
                                 +     ' data-testid="file-category-del-btn">&#10005;</button>'
                                 + '</div>';
                        }).join('')
                    + '</div>'
                    + '<div class="fk-new-row">'
                    +   '<input type="text" id="fkAddCat" class="fk-cat-name" maxlength="' + cfg.maxLen + '" placeholder="New ' + esc(cfg.noun) + '"'
                    +     ' data-testid="file-category-new-input">'
                    +   '<button type="button" class="fk-btn primary" data-act="add" data-testid="file-category-add-btn">Save</button>'
                    + '</div>';
                wire(cats);
            }).catch(function (e) { sheet.err.textContent = e.message; });
        }

        function wire(cats) {
            sheet.body.querySelectorAll('.fk-cat-row').forEach(function (row) {
                var id = parseInt(row.getAttribute('data-id'), 10);
                var cat = cats.find(function (c) { return c.id === id; });
                var nameEl = row.querySelector('.fk-cat-name');
                var colorEl = row.querySelector('.fk-color');
                function save() {
                    var name = nameEl.value.trim();
                    if (!name) { nameEl.value = cat.name; return; }
                    if (name === cat.name && colorEl.value.toUpperCase() === colorOf(cat.color).toUpperCase()) return;
                    Promise.resolve(api().apiPost(cfg.api + '/' + id,
                        { name: name, color: colorEl.value }, 'PUT')).then(function (r) {
                        if (!r || !r.ok) { sheet.err.textContent = errText(r, 'Could not save it.'); nameEl.value = cat.name; return; }
                        sheet.err.textContent = '';
                        cat.name = r.data.name; cat.color = r.data.color;
                        flagChange();
                        if (window.Picasso && Picasso.toast) Picasso.toast('Saved');
                    });
                }
                row._save = save;
                nameEl.addEventListener('change', save);
                nameEl.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); nameEl.blur(); } });
            });
            var addEl = sheet.body.querySelector('#fkAddCat');
            addEl.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); addNew(); } });
        }

        // A tap-a-colour palette rather than <input type="color">, which some phone
        // browsers render as a bare text box.
        var PALETTE = ['#B45309', '#B91C1C', '#BE185D', '#9D174D', '#6D28D9', '#4338CA', '#1D4ED8', '#0E7490',
                       '#0F766E', '#15803D', '#4D7C0F', '#A16207', '#475569', '#78350F'];
        function togglePalette(row) {
            var open = row.querySelector('.fk-palette');
            sheet.body.querySelectorAll('.fk-palette').forEach(function (p) { p.parentNode.removeChild(p); });
            if (open) return;
            var cur = row.querySelector('.fk-color').value.toUpperCase();
            var div = document.createElement('div');
            div.className = 'fk-palette';
            div.innerHTML = PALETTE.map(function (hex) {
                return '<button type="button" class="fk-swatch' + (hex === cur ? ' on' : '') + '" data-act="swatch" data-hex="' + hex + '"'
                     + ' style="background:' + hex + '" aria-label="Colour ' + hex + '" data-testid="file-category-swatch"></button>';
            }).join('');
            row.appendChild(div);
        }

        function addNew() {
            var el = sheet.body.querySelector('#fkAddCat');
            var name = (el.value || '').trim();
            if (!name) { el.focus(); return; }
            Promise.resolve(api().apiPost(cfg.api, { name: name })).then(function (r) {
                if (!r || !r.ok) { sheet.err.textContent = errText(r, 'Could not add it.'); return; }
                sheet.err.textContent = r.data.created ? '' : ('"' + r.data.name + '" is already a ' + cfg.noun + '.');
                flagChange();
                draw();
            });
        }

        function move(id, delta) {
            loadCategories().then(function (cats) {
                var ids = cats.map(function (c) { return c.id; });
                var i = ids.indexOf(id), j = i + delta;
                if (i < 0 || j < 0 || j >= ids.length) return;
                ids.splice(j, 0, ids.splice(i, 1)[0]);
                return Promise.resolve(api().apiPost(cfg.api + '/reorder', { ids: ids })).then(function (r) {
                    if (!r || !r.ok) { sheet.err.textContent = errText(r, 'Could not reorder.'); return; }
                    flagChange();
                    draw();
                });
            });
        }

        function del(id) {
            loadCategories().then(function (cats) {
                var c = cats.find(function (x) { return x.id === id; });
                if (!c) return;
                function send(moveTo) {
                    var url = cfg.api + '/' + id + (moveTo ? '?moveTo=' + encodeURIComponent(moveTo.id) : '');
                    Promise.resolve(api().apiPost(url, null, 'DELETE')).then(function (r) {
                        if (!r || !r.ok) { sheet.err.textContent = errText(r, 'Could not delete it.'); return; }
                        flagChange();
                        draw();
                    });
                }
                // A list whose things MUST have one (note types): pick where they move first.
                if (cfg.moveOnDelete && (c.useCount || 0) > 0) {
                    pickCategory({ title: 'Move its ' + c.useCount + ' ' + cfg.usesNoun + ' to', excludeId: id, noNew: true })
                        .then(function (choice) {
                            if (!choice || !choice.categoryId) return;
                            var target = cats.find(function (x) { return x.id === choice.categoryId; }) || { id: choice.categoryId, name: '' };
                            if (!window.confirm(cfg.deleteMessage(c, target))) return;
                            send(target);
                        });
                    return;
                }
                if (!window.confirm(cfg.deleteMessage(c, null))) return;
                send(null);
            });
        }

        sheet.bg.addEventListener('click', function (e) {
            var b = e.target.closest('[data-act]');
            if (!b || !sheet.bg.contains(b)) return;
            var act = b.getAttribute('data-act');
            var row = b.closest('.fk-cat-row');
            var id = row ? parseInt(row.getAttribute('data-id'), 10) : 0;
            if (act === 'palette') { togglePalette(row); return; }
            if (act === 'swatch') {
                var r2 = b.closest('.fk-cat-row');
                var hex = b.getAttribute('data-hex');
                r2.querySelector('.fk-color').value = hex;
                r2.querySelector('.fk-swatch').style.background = hex;
                togglePalette(r2);
                if (r2._save) r2._save();
                return;
            }
            if (act === 'close') { sheet.close(); if (changed && opts.onChange) opts.onChange(); }
            if (act === 'add')   addNew();
            if (act === 'up')    move(id, -1);
            if (act === 'down')  move(id, 1);
            if (act === 'del')   del(id);
        });

        draw();
    }

    return {
        loadCategories: loadCategories,
        invalidate: function () { _cats = null; },
        pickCategory: pickCategory,
        manageCategories: manageCategories,
        cfg: cfg
    };
    } // createKit

    var FILE_KIT = createKit({
        api: '/api/file-categories',
        noun: 'category', nounPlural: 'file categories',
        title: 'File categories', pickTitle: 'File to',
        examples: 'Medical, Insurance, 2026 Taxes', maxLen: 100,
        usesLabel: 'Documents filed here',
        moveOnDelete: false,
        deleteMessage: function (c) {
            var n = c.useCount || 0;
            return 'Delete the file category "' + c.name + '"?\n\n'
                + (n > 0
                    ? n + ' document' + (n === 1 ? '' : 's') + ' filed under it go' + (n === 1 ? 'es' : '')
                      + ' back to Staging on ' + (n === 1 ? 'its record' : 'their records') + '. No file is removed.\n\n'
                    : 'No documents are filed under it.\n\n')
                + 'This cannot be undone.';
        }
    });

    window.FileKit = {
        esc: esc,
        fmtBytes: fmtBytes,
        fmtDate: fmtDate,
        typeClass: typeClass,
        colorOf: colorOf,
        categoryChip: categoryChip,
        loadCategories: FILE_KIT.loadCategories,
        invalidateCategories: FILE_KIT.invalidate,
        pickCategory: FILE_KIT.pickCategory,
        manageCategories: FILE_KIT.manageCategories,
        kit: createKit,
        openSheet: openSheet,
        errText: errText
    };
})();
