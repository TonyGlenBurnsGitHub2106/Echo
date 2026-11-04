package com.echo;

import com.echo.SolutionDependencyModels.StoredWait;
import com.echo.SolutionDependencyModels.Wait;
import com.echo.SolutionDependencyModels.WaitCount;
import com.echo.SolutionModels.Item;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What items wait on (Story 5695, Echo first). An item is reached through its solution, so every call checks
 * the item (and the solution) first; anyone who can see the solution may add and remove waits, an API client
 * a solution is shared with included.
 */
@Service
public class SolutionDependencyService {

    private final SolutionDependencyRepository repo;
    private final SolutionItemService          items;
    private final SolutionAccess               access;

    public SolutionDependencyService(SolutionDependencyRepository repo, SolutionItemService items, SolutionAccess access) {
        this.repo = repo; this.items = items; this.access = access;
    }

    /** What the item waits on and what waits on it. */
    public List<Wait> forItem(SolutionCaller caller, int itemId) {
        items.get(caller, itemId);
        return repo.forItem(itemId);
    }

    /** The item now waits on the other; answers with the item's waits. Both must be visible and of one solution. */
    public List<Wait> add(SolutionCaller caller, int itemId, int dependsOnItemId) {
        Item item = items.get(caller, itemId);
        Item other = items.get(caller, dependsOnItemId);
        if (item.solutionId() != other.solutionId())
            throw new SolutionRuleException("An item can only wait on an item of the same solution. Nothing was saved.");
        if (repo.save(itemId, dependsOnItemId, caller.memberCode()) <= 0) throw new SolutionNotFoundException("Item");
        return repo.forItem(itemId);
    }

    public void remove(SolutionCaller caller, int waitId) {
        StoredWait w = waitId <= 0 ? null : repo.find(waitId);
        if (w == null) throw new SolutionNotFoundException("Wait");
        access.requireSolution(caller, w.solutionId());
        if (!repo.delete(waitId)) throw new SolutionNotFoundException("Wait");
    }

    /** For the Items tab's chips: every waiting item of the solution with how many of its waits are open. */
    public List<WaitCount> forSolution(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.forSolution(solutionId);
    }
}
