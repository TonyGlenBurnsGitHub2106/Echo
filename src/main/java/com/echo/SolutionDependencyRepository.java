package com.echo;

import com.echo.SolutionDependencyModels.StoredWait;
import com.echo.SolutionDependencyModels.Wait;
import com.echo.SolutionDependencyModels.WaitCount;

import java.util.List;

/**
 * Storage for what items wait on (Story 5695): the stp_SolutionItemDependency* procs. A refusal (an item of
 * another solution, itself, a repeat, a circle) is a {@link SolutionRuleException} with the proc's sentence.
 */
public interface SolutionDependencyRepository {

    int save(int itemId, int dependsOnItemId, String memberCode);

    StoredWait find(int waitId);

    boolean delete(int waitId);

    /** What one item waits on, and what waits on it. */
    List<Wait> forItem(int itemId);

    /** Every item of a solution that waits on something, with how many of its waits are still open. */
    List<WaitCount> forSolution(int solutionId);
}
