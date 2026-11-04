package com.echo;

import com.echo.SolutionChangeModels.BulkResult;
import com.echo.SolutionChangeModels.Promoted;
import com.echo.SolutionChangeModels.Provenance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL Server backed {@link SolutionChangeRepository} (Story 5695 Release 5).
 */
@Repository
public class JdbcSolutionChangeRepository implements SolutionChangeRepository {

    private final SolutionJdbc db;

    public JdbcSolutionChangeRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public BulkResult bulkChange(int solutionId, String itemIds, String field, String value, String reason, String memberCode) {
        return BulkResult.of(db.row("EXEC dbo.stp_SolutionProjectItem_BulkChange ?, ?, ?, ?, ?, ?",
            solutionId, itemIds, field, value, reason, memberCode));
    }

    @Override
    public Promoted promote(int solutionId, String sourceKind, int sourceId, String targetKind, Integer projectId, String title,
                            String memberCode) {
        return db.one("EXEC dbo.stp_Solution_Promote ?, ?, ?, ?, ?, ?, ?", Promoted::of,
            solutionId, sourceKind, sourceId, targetKind, projectId, title, memberCode);
    }

    @Override
    public List<Provenance> provenance(int solutionId, String kind, int recordId) {
        return db.list("EXEC dbo.stp_SolutionPromotion_ForRecord ?, ?, ?", Provenance::of, solutionId, kind, recordId);
    }
}
