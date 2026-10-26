/* entity-detail-echo.js -- Story 5653 (Echo).
 *
 * entity-detail.js is LeonardoWeb's file, copied unchanged so the two can be compared and changes
 * carried across. Two things in it belong to Leonardo's Entity Manager, which Echo does not have:
 * Needs attention (a record's pinned rows and open matters) and the Pin toggle that feeds it. This
 * file, loaded right after entity-detail.js, switches both off:
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
})();
