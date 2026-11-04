package com.echo;

import com.echo.SolutionOrientationModels.SummaryAge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * SQL Server backed {@link SolutionOrientationRepository} (Story 5695).
 */
@Repository
public class JdbcSolutionOrientationRepository implements SolutionOrientationRepository {

    private final SolutionJdbc db;

    public JdbcSolutionOrientationRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public SummaryAge summaryAge(int solutionId) {
        return SummaryAge.of(db.row("EXEC dbo.stp_Solution_StatusSummaryAge ?", solutionId));
    }
}
