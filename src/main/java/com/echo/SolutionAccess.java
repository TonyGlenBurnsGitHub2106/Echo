package com.echo;

import com.echo.SolutionModels.Solution;
import org.springframework.stereotype.Component;

/**
 * The second fence (Story 3493). The first -- {@link ApiClientAuthFilter} --
 * keeps an API client inside the Solutions paths. This one keeps it inside the
 * SOLUTIONS IT WAS GIVEN: every service call that touches a solution, or
 * anything belonging to one, comes through here first.
 *
 * A person or the house sees every solution. A client sees only those linked to
 * it, and is told "not found" about the rest -- never "forbidden", which would
 * confirm the thing exists.
 */
@Component
public class SolutionAccess {

    private final SolutionRepository solutions;
    private final ApiClientService   clients;

    public SolutionAccess(SolutionRepository solutions, ApiClientService clients) {
        this.solutions = solutions; this.clients = clients;
    }

    /** The solution, provided it exists and this caller may see it. */
    public Solution requireSolution(SolutionCaller caller, int solutionId) {
        Solution s = solutionId <= 0 ? null : solutions.findSolution(solutionId);
        if (s == null) throw new SolutionNotFoundException("Solution");
        if (caller.isClient() && !clients.maySee(caller.apiClientId(), solutionId)) throw new SolutionNotFoundException("Solution");
        return s;
    }

    /** The owner's actions: create / delete a solution, share it, choose its file storage, manage members. */
    public void requireHuman(SolutionCaller caller, String what) {
        if (caller.isClient()) throw new SolutionForbiddenException(what);
    }
}
