package com.echo;

import com.echo.SolutionOrientationModels.SummaryAge;
import org.springframework.stereotype.Service;

/**
 * "Where am I" in a solution (Story 5695, Echo first): how old "Where things stand" is, so a summary that
 * has stopped being true says so. Anyone who can see the solution may read it.
 */
@Service
public class SolutionOrientationService {

    private final SolutionOrientationRepository repo;
    private final SolutionAccess                access;

    public SolutionOrientationService(SolutionOrientationRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    public SummaryAge summaryAge(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.summaryAge(solutionId);
    }
}
