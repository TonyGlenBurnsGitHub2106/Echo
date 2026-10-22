(function () {
    'use strict';

    // The Web tab of a solution (2026-10-01, after Story 5566 made a solution a true entity).
    // A solution now carries web addresses exactly the way a contact or a matter does: this is the
    // shared, organized Web tab of every record page (contacts/entity-web.js through LWED --
    // website types as coloured groups, sort, search, Important, logins), hung off the solution's
    // EntityID. Nothing here knows how the Web tab works; it only finds the EntityID and hands over.
    //
    // The solution API never returns the EntityID (it is the outside agents' door too), so it is
    // read from the house-only entity browse. An agent never sees this tab or its addresses.

    var Page = window.SolutionPage, SL = window.SolutionsCommon, $ = SL.$;
    var mEntityId = 0;

    async function entityId() {
        if (mEntityId) return mEntityId;
        var r = await SL.get('/api/entity-browse?type=222&identityId=' + Page.id);
        var row = r && r.ok && r.data && r.data.rows && r.data.rows[0];
        mEntityId = row ? (+row.entityId || 0) : 0;
        return mEntityId;
    }

    async function load() {
        var pane = $('webPane');
        var id = await entityId();
        if (!id) {
            pane.innerHTML = '<div class="sl-empty">This solution has no entity id, so its web addresses cannot be read.</div>';
            return;
        }
        var ED = window.LWED;
        ED.setEntityName(id, Page.solution ? Page.solution.name : 'Solution');
        // Pin (Story 5594): a record page tells the Pin toggles which record they pin to by drawing its
        // Needs attention tab. This page has no such tab, so the same list is drawn into a box that is
        // never shown -- Pin then pins to the solution (seen on its Needs attention in Entity Manager
        // IV), and an address already pinned shows as Pinned.
        if (ED.renderAttention) ED.renderAttention(document.createElement('div'), id, {});
        var r = await window.LWAuth.apiGet(window.EntityWeb.firstPageUrl(id));
        if (!r) return;
        if (!r.ok) { ED.renderError(pane, 'Failed to load: ' + (r.data && r.data.error ? r.data.error : r.status)); return; }
        ED.renderWebAddresses(pane, r.data, id, load);
        if (mFlashId) flashNow();
    }

    // The Search tab (2026-10-16) names a web address to scroll to: the shared tab draws rows with
    // data-xrefid, so the row is found by that once the first page is on screen.
    var mFlashId = 0;
    function flashNow() {
        var id = mFlashId;
        mFlashId = 0;
        window.setTimeout(function () {
            var row = $('webPane').querySelector('[data-xrefid="' + id + '"]');
            if (!row) return;
            row.classList.add('flash');
            row.scrollIntoView({ block: 'center' });
        }, 60);
    }

    Page.registerTab('web', { show: load, flash: function (xrefId) { mFlashId = xrefId; } });
})();
