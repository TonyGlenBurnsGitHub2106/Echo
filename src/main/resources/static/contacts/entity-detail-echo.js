/* entity-detail-echo.js -- Story 5653 (Echo).
 *
 * entity-detail.js is LeonardoWeb's file, copied unchanged so the two can be compared and changes
 * carried across. Two things in it belong to Leonardo's Entity Manager, which Echo does not have:
 * Needs attention (a record's pinned rows and open matters) and the Pin toggle that feeds it. This
 * file, loaded right after entity-detail.js, switches both off (and takes the Reading Nook and
 * library buttons out of the Add Files box -- Echo has neither):
 *
 *   - LWED.renderAttention / loadAttention draw nothing and ask the server nothing;
 *   - LWED.pinButton returns no button, so the Web tab rows show no Pin.
 *
 * Everything else (the Web tab, logins, the record dialogs) is Leonardo's code as is.
 */
(function () {
    'use strict';
    var ED = window.LWED;
    if (!ED) return;

    var EMPTY = { matters: [], callLogs: [], pins: [] };

    ED.loadAttention = function () { return Promise.resolve(EMPTY); };
    ED.renderAttention = function (hostEl, entityId, opts) {
        if (hostEl) hostEl.innerHTML = '';
        if (opts && typeof opts.onCount === 'function') opts.onCount(0);
        return Promise.resolve(0);
    };
    ED.renderAttentionTab = function () { };
    ED.attentionTabDrew = function () { };
    ED.pinButton = function () { return ''; };

    // Story 5653: Echo has no Reading Nook and no Leonardo Files library, so the Add Files box
    // offers neither -- the two buttons would only fail. Upload, Scan, Paste text and New text
    // note stay, and Upload takes several files at once.
    var openFileSourcePicker = ED.openFileSourcePicker;
    if (openFileSourcePicker) {
        ED.openFileSourcePicker = function () {
            var out = openFileSourcePicker.apply(this, arguments);
            var form = document.getElementById('lwedForm');
            if (form) {
                form.querySelectorAll('[data-testid="ed-file-src-nook"], [data-testid="ed-file-src-library"]')
                    .forEach(function (b) { b.parentNode.removeChild(b); });
            }
            return out;
        };
    }
})();
