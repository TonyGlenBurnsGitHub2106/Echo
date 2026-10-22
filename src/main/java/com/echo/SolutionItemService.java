package com.echo;

import com.echo.SolutionDrafts.ItemDraft;
import com.echo.SolutionModels.Item;
import com.echo.SolutionModels.Project;
import org.springframework.stereotype.Service;

import java.util.List;

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
        if (draft.id() > 0) draft = merged(draft, get(caller, draft.id()));
        else solutions.project(caller, draft.projectId());
        codes.status(draft.statusCode());
        codes.member(caller, draft.assigneeCode());
        int id = items.save(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Item");
        Item saved = items.find(id);
        wakeAncestors(caller, saved);
        return saved;
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
        Project project = solutions.project(caller, item.projectId());
        if (project != null && TODO.equals(project.statusCode()))
            solutions.saveProject(caller, new SolutionDrafts.ProjectDraft(project.id(), project.solutionId(), null, null, IN_PROGRESS, null, 0));
    }

    private static final String TODO = "todo";
    private static final String IN_PROGRESS = "inprogress";

    /** A null field on an update keeps its current value; an edit never changes project. */
    private static ItemDraft merged(ItemDraft d, Item c) {
        return new ItemDraft(d.id(), c.projectId(), d.parentItemId() == null ? c.parentItemId() : d.parentItemId(),
            SolutionService.keep(d.title(), c.title(), true), SolutionService.keep(d.detail(), c.detail(), false),
            SolutionService.keep(d.statusCode(), c.statusCode(), true), SolutionService.keep(d.assigneeCode(), c.assigneeCode(), false),
            d.priority() > 0 ? d.priority() : c.priority(), d.sortOrder(), d.dueDate() == null ? c.dueDate() : d.dueDate());
    }

    public Item setStatus(SolutionCaller caller, int itemId, String statusCode) {
        get(caller, itemId);
        if (statusCode == null || statusCode.trim().isEmpty()) throw new IllegalArgumentException("statusCode is required.");
        codes.status(statusCode);
        if (!items.setStatus(itemId, statusCode, caller.memberCode())) throw new SolutionNotFoundException("Item");
        Item saved = items.find(itemId);
        wakeAncestors(caller, saved);
        return saved;
    }

    public Item move(SolutionCaller caller, int itemId, Integer newProjectId, Integer newParentItemId, int newSortOrder) {
        Item current = get(caller, itemId);
        if (newProjectId != null && newProjectId > 0) {
            Project target = solutions.project(caller, newProjectId);
            if (target.solutionId() != current.solutionId())
                throw new SolutionRuleException("An item can only move between projects of the same solution.");
        }
        if (!items.move(itemId, newProjectId, newParentItemId, newSortOrder, caller.memberCode())) throw new SolutionNotFoundException("Item");
        return items.find(itemId);
    }

    /** @return how many items were removed, the item's whole subtree included */
    public int delete(SolutionCaller caller, int itemId) {
        get(caller, itemId);
        return items.delete(itemId);
    }
}
