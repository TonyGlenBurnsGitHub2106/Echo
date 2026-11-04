package com.echo;

import com.echo.SolutionChangeModels.BulkResult;
import com.echo.SolutionChangeModels.Promoted;
import com.echo.SolutionChangeModels.Provenance;

import java.util.List;

/**
 * Storage for bulk changes and promotions (Story 5695 Release 5): stp_SolutionProjectItem_BulkChange,
 * stp_Solution_Promote, stp_SolutionPromotion_ForRecord. Each write is one proc, one transaction.
 */
public interface SolutionChangeRepository {

    /** itemIds is comma separated. */
    BulkResult bulkChange(int solutionId, String itemIds, String field, String value, String reason, String memberCode);

    Promoted promote(int solutionId, String sourceKind, int sourceId, String targetKind, Integer projectId, String title,
                     String memberCode);

    List<Provenance> provenance(int solutionId, String kind, int recordId);
}
