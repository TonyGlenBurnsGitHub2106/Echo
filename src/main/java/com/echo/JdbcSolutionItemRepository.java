package com.echo;

import com.echo.SolutionDrafts.ItemDraft;
import com.echo.SolutionModels.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/** SQL Server backed {@link SolutionItemRepository} (Story 3493): the stp_SolutionProjectItem* procs. */
@Repository
public class JdbcSolutionItemRepository implements SolutionItemRepository {

    private final SolutionJdbc db;

    public JdbcSolutionItemRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<Item> listForSolution(int solutionId, Integer projectId) {
        return db.list("EXEC dbo.stp_SolutionProjectItem_ListForSolution ?, ?", Item::of, solutionId, projectId);
    }

    @Override
    public Item find(int id) { return db.one("EXEC dbo.stp_SolutionProjectItem_Get ?", Item::of, id); }

    @Override
    public int save(ItemDraft d, String authorCode) {
        return db.number("EXEC dbo.stp_SolutionProjectItem_Save ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionProjectItemID",
            d.id(), d.projectId(), d.parentItemId(), d.title(), d.detail(), d.statusCode(), d.assigneeCode(),
            d.priority(), d.sortOrder(), JdbcSolutionRepository.ts(d.dueDate()), authorCode);
    }

    @Override
    public boolean setStatus(int id, String statusCode, String modifiedBy) {
        return db.affected("EXEC dbo.stp_SolutionProjectItem_SetStatus ?, ?, ?", id, statusCode, modifiedBy);
    }

    @Override
    public boolean move(int id, Integer newProjectId, Integer newParentItemId, int newSortOrder, String modifiedBy) {
        return db.affected("EXEC dbo.stp_SolutionProjectItem_Move ?, ?, ?, ?, ?",
            id, newProjectId == null ? 0 : newProjectId, newParentItemId == null ? 0 : newParentItemId, newSortOrder, modifiedBy);
    }

    @Override
    public int delete(int id) { return db.number("EXEC dbo.stp_SolutionProjectItem_Delete ?", "RowsAffected", id); }
}
