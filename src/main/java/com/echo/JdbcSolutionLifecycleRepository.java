package com.echo;

import com.echo.SolutionLifecycleModels.OpenThing;
import com.echo.SolutionLifecycleModels.ShapeResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL Server backed {@link SolutionLifecycleRepository} (Story 5695 Release 6).
 */
@Repository
public class JdbcSolutionLifecycleRepository implements SolutionLifecycleRepository {

    private final SolutionJdbc db;

    public JdbcSolutionLifecycleRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public ShapeResult applyShape(int solutionId, String shapeCode, Integer fromSolutionId, String memberCode) {
        return ShapeResult.of(db.row("EXEC dbo.stp_Solution_ApplyShape ?, ?, ?, ?", solutionId, shapeCode, fromSolutionId, memberCode));
    }

    @Override
    public List<OpenThing> closeOut(int solutionId) {
        return db.list("EXEC dbo.stp_Solution_CloseOutList ?", OpenThing::of, solutionId);
    }
}
