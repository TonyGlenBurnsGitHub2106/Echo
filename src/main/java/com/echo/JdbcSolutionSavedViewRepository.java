package com.echo;

import com.echo.SolutionSavedViewModels.SavedView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL Server backed {@link SolutionSavedViewRepository} (Story 5695 Release 5).
 */
@Repository
public class JdbcSolutionSavedViewRepository implements SolutionSavedViewRepository {

    private final SolutionJdbc db;

    public JdbcSolutionSavedViewRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<SavedView> list(int solutionId, String memberCode, String scope) {
        return db.list("EXEC dbo.stp_SolutionSavedView_ListForMember ?, ?, ?", SavedView::of, solutionId, memberCode, scope);
    }

    @Override
    public int save(int solutionId, String memberCode, String scope, String name, String filter) {
        return db.number("EXEC dbo.stp_SolutionSavedView_Save ?, ?, ?, ?, ?", "SolutionSavedViewID",
            solutionId, memberCode, scope, name, filter);
    }

    @Override
    public boolean delete(int viewId, String memberCode) {
        return db.affected("EXEC dbo.stp_SolutionSavedView_Delete ?, ?", viewId, memberCode);
    }
}
