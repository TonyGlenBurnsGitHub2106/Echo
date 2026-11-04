package com.echo;

import com.echo.SolutionSavedViewModels.SavedView;

import java.util.List;

/**
 * Storage for saved views (Story 5695 Release 5): stp_SolutionSavedView_ListForMember, _Save, _Delete.
 */
public interface SolutionSavedViewRepository {

    List<SavedView> list(int solutionId, String memberCode, String scope);

    /** Same name (any case) for the same member and tab = replaced. Returns the view's id. */
    int save(int solutionId, String memberCode, String scope, String name, String filter);

    /** Only the member's own view; false when there is none. */
    boolean delete(int viewId, String memberCode);
}
