package com.echo;

import com.echo.SolutionDependencyModels.StoredWait;
import com.echo.SolutionDependencyModels.Wait;
import com.echo.SolutionDependencyModels.WaitCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL Server backed {@link SolutionDependencyRepository} (Story 5695).
 */
@Repository
public class JdbcSolutionDependencyRepository implements SolutionDependencyRepository {

    private final SolutionJdbc db;

    public JdbcSolutionDependencyRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public int save(int itemId, int dependsOnItemId, String memberCode) {
        return db.number("EXEC dbo.stp_SolutionItemDependency_Save ?, ?, ?", "SolutionItemDependencyID", itemId, dependsOnItemId, memberCode);
    }

    @Override
    public StoredWait find(int waitId) {
        return db.one("EXEC dbo.stp_SolutionItemDependency_Get ?", StoredWait::of, waitId);
    }

    @Override
    public boolean delete(int waitId) {
        return db.affected("EXEC dbo.stp_SolutionItemDependency_Delete ?", waitId);
    }

    @Override
    public List<Wait> forItem(int itemId) {
        return db.list("EXEC dbo.stp_SolutionItemDependency_ListForItem ?", Wait::of, itemId);
    }

    @Override
    public List<WaitCount> forSolution(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionItemDependency_ListForSolution ?", WaitCount::of, solutionId);
    }
}
