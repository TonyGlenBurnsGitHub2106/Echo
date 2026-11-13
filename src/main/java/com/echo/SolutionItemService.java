package com.echo;

import com.echo.SolutionDrafts.ItemDraft;
import com.echo.SolutionModels.Item;
import com.echo.SolutionModels.Project;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * A solution's action items (Story 3493). An item is reached through its
 * project, and a project through its solution, so every call resolves that
 * chain and checks the solution before it touches anything. Inside a solution
 * it was given, an API client adds, edits, re-statuses, moves and deletes.
 */
@Service
public class SolutionItemService {

    private final SolutionItemRepository items;
    private final SolutionService        solutions;
    private final SolutionAccess         access;
    private final SolutionCodeCheck      codes;

    public SolutionItemService(SolutionItemRepository items, SolutionService solutions, SolutionAccess access, SolutionCodeCheck codes) {
        this.items = items; this.solutions = solutions; this.access = access; this.codes = codes;
    }

    public List<Item> list(SolutionCaller caller, int solutionId, Integer projectId) {
        access.requireSolution(caller, solutionId);
        return items.listForSolution(solutionId, projectId);
    }

    public Item get(SolutionCaller caller, int itemId) {
        Item i = itemId <= 0 ? null : items.find(itemId);
        if (i == null) throw new SolutionNotFoundException("Item");
        access.requireSolution(caller, i.solutionId());
        return i;
    }

    public Item save(SolutionCaller caller, ItemDraft draft) {
        Integer oldParentId = null;
        String currentAssignee = null;
        if (draft.id() > 0) {
            Item current = get(caller, draft.id());
            oldParentId = current.parentItemId();
            currentAssignee = current.assigneeCode();
            draft = merged(draft, current);
        } else solutions.project(caller, draft.projectId());
        codes.status(draft.statusCode());
        codes.member(caller, draft.assigneeCode(), currentAssignee);
        int id = items.save(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Item");
        Item saved = items.find(id);
        wakeAncestors(caller, saved);
        if (saved != null) rollUp(caller, saved.parentItemId(), oldParentId);
        return saved;
    }

    /**
     * Story 5653 (2026-11-09) -- Tony: "when all sub items are checked or otherwise handled, the main item then is
     * auto checked, and if one is unchecked, then the main item is unchecked. This should ONLY be true when the
     * item has subitems. Otherwise, the item is the thing." So after anything that changes which sub-items an item
     * has or how they stand (a status, a new sub-item, a move, a delete), the parents it touched are re-checked,
     * and everything above them: all sub-items closed (Done or Skipped) closes the parent, one open reopens it.
     * Done in stp_SolutionProjectItem_RollUpParents, which stp_SolutionProjectItem_BulkChange calls too. The
     * item that was changed is not itself re-checked: set by hand, it stays as set until a sub-item changes.
     */
    private void rollUp(SolutionCaller caller, Integer... parentIds) {
        List<Integer> ids = new java.util.ArrayList<>();
        for (Integer p : parentIds) if (p != null && p > 0 && !ids.contains(p)) ids.add(p);
        if (!ids.isEmpty()) items.rollUpParents(ids, caller.memberCode());
    }

    /**
     * Work on a sub-item is work on everything above it. When an item is started or closed,
     * every ancestor item still at "not started" moves to "in progress", and so does its
     * project. Nothing is ever moved backwards, and nothing already past "not started" is
     * touched -- a blocked or done parent stays as it is.
     */
    private void wakeAncestors(SolutionCaller caller, Item item) {
        if (item == null || TODO.equals(item.statusCode()) || item.statusCode() == null) return;
        Integer parentId = item.parentItemId();
        int guard = 0;
        while (parentId != null && parentId > 0 && guard++ < 50) {
            Item parent = items.find(parentId);
            if (parent == null) break;
            if (TODO.equals(parent.statusCode())) items.setStatus(parent.id(), IN_PROGRESS, caller.memberCode());
            parentId = parent.parentItemId();
        }
        // Status only, and it never throws: the item is already saved (Story 5766).
        solutions.wakeProject(caller, item.projectId(), TODO, IN_PROGRESS);
    }

    private static final String TODO = "todo";
    private static final String IN_PROGRESS = "inprogress";

    /** A null field on an update keeps its current value; an edit never changes project. */
    private static ItemDraft merged(ItemDraft d, Item c) {
        return new ItemDraft(d.id(), c.projectId(), d.parentItemId() == null ? c.parentItemId() : d.parentItemId(),
            SolutionService.keep(d.title(), c.title(), true), SolutionService.keep(d.detail(), c.detail(), false),
            SolutionService.keep(d.statusCode(), c.statusCode(), true), SolutionService.keep(d.assigneeCode(), c.assigneeCode(), false),
            d.priority() > 0 ? d.priority() : c.priority(), d.sortOrder(),
            SolutionService.date(d.dueDate(), d.clearDueDate(), c.dueDate()), false);
    }

    public Item setStatus(SolutionCaller caller, int itemId, String statusCode) {
        get(caller, itemId);
        if (statusCode == null || statusCode.trim().isEmpty()) throw new IllegalArgumentException("statusCode is required.");
        codes.status(statusCode);
        if (!items.setStatus(itemId, statusCode, caller.memberCode())) throw new SolutionNotFoundException("Item");
        Item saved = items.find(itemId);
        wakeAncestors(caller, saved);
        if (saved != null) rollUp(caller, saved.parentItemId());
        return saved;
    }

    /**
     * Story 5653 (2026-11-06): set one of the item's check boxes. Reviewed and QA are pass / fail, Automated is
     * done / wontdo; null or blank empties the box. The status is not touched, and nothing above or below it moves.
     */
    public Item setCheck(SolutionCaller caller, int itemId, String checkCode, String resultCode) {
        get(caller, itemId);
        String check = checkCode == null ? "" : checkCode.trim().toLowerCase(java.util.Locale.ROOT);
        String result = resultCode == null || resultCode.trim().isEmpty() ? null : resultCode.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> allowed = CHECK_RESULTS.get(check);
        if (allowed == null) throw new IllegalArgumentException("check must be reviewed, qa or automated.");
        if (result != null && !allowed.contains(result))
            throw new IllegalArgumentException("The " + check + " check takes " + String.join(" or ", allowed) + ", or nothing for not done.");
        if (!items.setCheck(itemId, check, result, caller.memberCode())) throw new SolutionNotFoundException("Item");
        return items.find(itemId);
    }

    private static final Map<String, List<String>> CHECK_RESULTS = Map.of(
        "reviewed", List.of("pass", "fail"), "qa", List.of("pass", "fail"), "automated", List.of("done", "wontdo"));

    public Item move(SolutionCaller caller, int itemId, Integer newProjectId, Integer newParentItemId, int newSortOrder) {
        Item current = get(caller, itemId);
        if (newProjectId != null && newProjectId > 0) {
            Project target = solutions.project(caller, newProjectId);
            if (target.solutionId() != current.solutionId())
                throw new SolutionRuleException("An item can only move between projects of the same solution.");
        }
        if (!items.move(itemId, newProjectId, newParentItemId, newSortOrder, caller.memberCode())) throw new SolutionNotFoundException("Item");
        Item moved = items.find(itemId);
        // Story 5653: the parent it left and the parent it joined both re-check their sub-items
        if (moved != null) rollUp(caller, current.parentItemId(), moved.parentItemId());
        return moved;
    }

    /** @return how many items were removed, the item's whole subtree included */
    public int delete(SolutionCaller caller, int itemId) {
        Item current = get(caller, itemId);
        int removed = items.delete(itemId);
        // Story 5653: the parent re-checks what is left under it (none left: it is the thing again, left as it is)
        if (removed > 0) rollUp(caller, current.parentItemId());
        return removed;
    }
}
