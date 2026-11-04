package com.echo;

import com.echo.SolutionOrientationModels.Activity;
import com.echo.SolutionOrientationModels.LeftOff;
import com.echo.SolutionOrientationModels.LensItem;
import com.echo.SolutionOrientationModels.SummaryAge;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Storage for "where am I" in a solution (Story 5695): stp_Solution_StatusSummaryAge. Read-only.
 */
public interface SolutionOrientationRepository {

    SummaryAge summaryAge(int solutionId);

    /** Story 5695 Release 4: bucket null = the first page of each bucket. */
    List<LensItem> lens(int solutionId, String memberCode, String bucket, int offset, int pageSize, int soonDays, int idleDays);

    LeftOff leftOff(int solutionId, String memberCode);

    void saveLeftOff(int solutionId, String memberCode, String note);

    /** Every row carries TotalCount; read it from the first. */
    List<Activity> feed(int solutionId, LocalDateTime since, String who, String kind, Integer topicId, int offset, int pageSize);

    int feedTotal(int solutionId, LocalDateTime since, String who, String kind, Integer topicId);
}
