/* ------------------------------------------------------------------------
   PicassoGroupState -- remembered open / collapsed state for the collapsible
   group bands used by the Library shelves and the Research shelf.

   The model is a persisted DEFAULT plus a persisted set of EXCEPTIONS:

     - Expand All / Collapse All set the DEFAULT and clear the exceptions.
     - Toggling one band records that band as an exception to the default.

   That is what makes "Collapse All" survive a reload AND a Group by change:
   a band the user has never seen simply follows the remembered default,
   instead of falling back to a hardcoded one. Storing only a set of names
   (the old way) could never remember an all-at-once choice, because a group
   that did not exist yet was not in the set.

   Usage:
       var mGroups = window.PicassoGroupState.create({
           key: 'qm.groups',          // localStorage prefix for this page
           defaultOpen: false,        // state before the user has chosen
           legacyKey: 'qm.expanded',  // optional one-time import
           legacyIsCollapsed: false   // legacy list held collapsed names?
       });
       mGroups.isOpen(name); mGroups.toggle(name); mGroups.open(name);
       mGroups.expandAll(); mGroups.collapseAll();
       mGroups.sync().then(function (changed) { if (changed) render(); });

   Where it is kept: when the page also loads picasso-view-state.js, the state
   is saved to the DATABASE through PicassoViewState, so the desktop browser
   and the phone PWA share it; call sync() once after sign-in to pull the
   other device's choices. Without that script it falls back to this
   device's localStorage only, as before.
   ------------------------------------------------------------------------ */
(function () {
    'use strict';

    function create(opts) {
        opts = opts || {};
        var keyDefault = opts.key + '.default';
        var keyExcept  = opts.key + '.except';
        var isOpenDefault = !!opts.defaultOpen;
        var except = new Set();

        // PicassoViewState when the page loaded it (database, shared across
        // devices), else this device's localStorage.
        function store() {
            if (window.PicassoViewState) return window.PicassoViewState;
            return {
                get: function (k) { return localStorage.getItem(k); },
                set: function (k, v) { localStorage.setItem(k, v); }
            };
        }

        function load() {
            try {
                var d = store().get(keyDefault);
                if (d === '1' || d === '0') {
                    isOpenDefault = (d === '1');
                    except = new Set(JSON.parse(store().get(keyExcept) || '[]'));
                    return;
                }
                // Nothing stored yet -- carry over whatever the page remembered
                // under its old key so the first visit after this change keeps
                // the bands the user had already opened or closed.
                if (opts.legacyKey) {
                    var old = JSON.parse(localStorage.getItem(opts.legacyKey) || '[]') || [];
                    // A legacy collapsed-list matches an open default, and a
                    // legacy expanded-list matches a collapsed default, so in
                    // both cases the old names are exactly the exceptions.
                    if (old.length) except = new Set(old);
                }
            } catch (e) { except = new Set(); }
        }

        function save() {
            try {
                store().set(keyDefault, isOpenDefault ? '1' : '0');
                // Array.from, NOT Array.prototype.slice.call. slice needs an
                // array-LIKE (.length plus indexed keys); a Set has .size and
                // neither, so slice.call(set) silently returns [] every time.
                // That bug persisted an always-empty exception list, so a band
                // the user folded came back open on the next visit while
                // Collapse All (which only writes the default) appeared to work.
                store().set(keyExcept, JSON.stringify(Array.from(except)));
            } catch (e) { /* private mode / quota -- state just stops persisting */ }
        }

        load();

        return {
            /** Pull this key's state from the server; resolves true when it changed. */
            sync: function () {
                if (!window.PicassoViewState) return Promise.resolve(false);
                return window.PicassoViewState.sync(opts.key + '.').then(function (changed) {
                    if (changed) load();
                    return changed;
                });
            },
            isOpen: function (name) {
                return except.has(name) ? !isOpenDefault : isOpenDefault;
            },
            toggle: function (name) {
                if (except.has(name)) except.delete(name); else except.add(name);
                save();
                return this.isOpen(name);
            },
            /** Force one band open (e.g. the group you just added a record to). */
            open: function (name) {
                if (isOpenDefault) except.delete(name); else except.add(name);
                save();
            },
            expandAll: function () {
                isOpenDefault = true; except.clear(); save();
            },
            collapseAll: function () {
                isOpenDefault = false; except.clear(); save();
            }
        };
    }

    window.PicassoGroupState = { create: create };
})();
