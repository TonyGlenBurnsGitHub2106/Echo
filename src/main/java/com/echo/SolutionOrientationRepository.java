package com.echo;

import com.echo.SolutionOrientationModels.SummaryAge;

/**
 * Storage for "where am I" in a solution (Story 5695): stp_Solution_StatusSummaryAge. Read-only.
 */
public interface SolutionOrientationRepository {

    SummaryAge summaryAge(int solutionId);
}
