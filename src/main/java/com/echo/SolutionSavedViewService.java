package com.echo;

import com.echo.SolutionSavedViewModels.SavedView;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A member's saved views of a solution's tab (Story 5695, Echo first, Release 5). A member sees, saves and deletes
 * only their own; the solution must be one they can see.
 */
@Service
public class SolutionSavedViewService {

    private final SolutionSavedViewRepository repo;
    private final SolutionAccess              access;

    public SolutionSavedViewService(SolutionSavedViewRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    public List<SavedView> list(SolutionCaller caller, int solutionId, String scope) {
        access.requireSolution(caller, solutionId);
        return repo.list(solutionId, caller.memberCode(), scopeOf(scope));
    }

    /** Saves (or, same name, replaces) a view; answers with the member's views of that tab. */
    public List<SavedView> save(SolutionCaller caller, int solutionId, String scope, String name, String filter) {
        access.requireSolution(caller, solutionId);
        String s = scopeOf(scope);
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("A view needs a name.");
        if (n.length() > 100) throw new IllegalArgumentException("A view's name can be at most 100 characters.");
        String f = filter == null ? "" : filter.trim();
        if (!f.startsWith("{") || !f.endsWith("}")) throw new IllegalArgumentException("filter must be a JSON object, as a string.");
        if (f.length() > 2000) throw new IllegalArgumentException("A view's filters can be at most 2,000 characters.");
        repo.save(solutionId, caller.memberCode(), s, n, f);
        return repo.list(solutionId, caller.memberCode(), s);
    }

    public void delete(SolutionCaller caller, int viewId) {
        if (viewId <= 0 || !repo.delete(viewId, caller.memberCode())) throw new SolutionNotFoundException("View");
    }

    private static String scopeOf(String scope) {
        String s = scope == null || scope.isBlank() ? "items" : scope.trim().toLowerCase();
        if (!SolutionSavedViewModels.SCOPES.contains(s)) throw new IllegalArgumentException("Views can be saved for the items tab (scope=items).");
        return s;
    }
}
