package com.echo;

import com.echo.SolutionDrafts.ItemDraft;
import com.echo.SolutionModels.Item;

import java.util.List;

/**
 * Storage for a solution's action items (Story 3493): flat rows that carry
 * their parent id, so the caller draws the tree. Jdbc = stp_SolutionProjectItem*.
 */
public interface SolutionItemRepository {

    /** Every item of a solution, or of one of its projects, ordered for display. */
    List<Item> listForSolution(int solutionId, Integer projectId);

    /** One item WITH its full detail. */
    Item find(int id);

    int save(ItemDraft draft, String authorCode);

    boolean setStatus(int id, String statusCode, String modifiedBy);

    boolean move(int id, Integer newProjectId, Integer newParentItemId, int newSortOrder, String modifiedBy);

    /** Removes the item and its whole subtree; returns how many items went. */
    int delete(int id);
}
