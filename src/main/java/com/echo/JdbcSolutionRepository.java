package com.echo;

import com.echo.SolutionDrafts.ProjectDraft;
import com.echo.SolutionDrafts.SolutionDraft;
import com.echo.SolutionModels.CodeName;
import com.echo.SolutionModels.Lookups;
import com.echo.SolutionModels.Member;
import com.echo.SolutionModels.Project;
import com.echo.SolutionModels.Solution;
import com.echo.SolutionModels.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/** SQL Server backed {@link SolutionRepository} (Story 3493): the stp_Solution* / stp_SolutionProject* procs. */
@Repository
public class JdbcSolutionRepository implements SolutionRepository {

    private final SolutionJdbc db;

    public JdbcSolutionRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public Lookups lookups() {
        List<Status> statuses = db.list("EXEC dbo.stp_SolutionStatus_List", Status::of);
        List<Member> members  = db.list("EXEC dbo.stp_SolutionMember_List ?", Member::of, false);
        List<CodeName> logTypes = db.list("EXEC dbo.stp_SolutionLogType_List",
            r -> new CodeName(intOf(r, "SolutionLogTypeID"), strOf(r, "LogTypeCode"), strOf(r, "LogTypeName"), intOf(r, "SortOrder")));
        List<CodeName> noteTypes = db.list("EXEC dbo.stp_SolutionNoteType_List",
            r -> new CodeName(intOf(r, "SolutionNoteTypeID"), strOf(r, "NoteTypeCode"), strOf(r, "NoteTypeName"), intOf(r, "SortOrder")));
        return new Lookups(statuses, members, logTypes, noteTypes);
    }

    @Override
    public List<Solution> listSolutions(boolean includeArchived, Integer apiClientId) {
        return db.list("EXEC dbo.stp_Solution_List ?, ?", Solution::of, includeArchived, apiClientId);
    }

    @Override
    public Solution findSolution(int id) { return db.one("EXEC dbo.stp_Solution_Get ?", Solution::of, id); }

    @Override
    public int saveSolution(SolutionDraft d, String modifiedBy) {
        return db.number("EXEC dbo.stp_Solution_Save ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionID",
            d.id(), d.name(), d.description(), d.statusCode(), d.ownerCode(), d.statusSummary(),
            ts(d.targetDate()), d.archived(), d.sortOrder(), modifiedBy, d.alias());
    }

    @Override
    public boolean deleteSolution(int id) { return db.affected("EXEC dbo.stp_Solution_Delete ?", id); }

    @Override
    public boolean setSaveFilesToRepo(int id, boolean saveToRepo, String modifiedBy) {
        return db.affected("EXEC dbo.stp_Solution_SetSaveFilesToRepo ?, ?, ?", id, saveToRepo, modifiedBy);
    }

    @Override
    public List<Project> listProjects(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionProject_ListForSolution ?", Project::of, solutionId);
    }

    @Override
    public Project findProject(int id) { return db.one("EXEC dbo.stp_SolutionProject_Get ?", Project::of, id); }

    @Override
    public int saveProject(ProjectDraft d, String modifiedBy) {
        return db.number("EXEC dbo.stp_SolutionProject_Save ?, ?, ?, ?, ?, ?, ?, ?", "SolutionProjectID",
            d.id(), d.solutionId(), d.name(), d.description(), d.statusCode(), d.ownerCode(), d.sortOrder(), modifiedBy);
    }

    @Override
    public boolean deleteProject(int id) { return db.affected("EXEC dbo.stp_SolutionProject_Delete ?", id); }

    @Override
    public int saveMember(int id, String code, String name, boolean agent, boolean active, int sortOrder) {
        return db.number("EXEC dbo.stp_SolutionMember_Save ?, ?, ?, ?, ?, ?", "SolutionMemberID",
            id, code, name, agent, active, sortOrder);
    }

    @Override
    public int findOrCreateNoteType(String name) {
        return db.number("EXEC dbo.stp_SolutionNoteType_FindOrCreate ?", "SolutionNoteTypeID", name);
    }

    @Override
    public void reorderNoteTypes(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) if (id != null) { if (sb.length() > 0) sb.append(','); sb.append(id); }
        db.row("EXEC dbo.stp_SolutionNoteType_Reorder ?", sb.toString());
    }

    @Override
    public int findOrCreateLogType(String name) {
        return db.number("EXEC dbo.stp_SolutionLogType_FindOrCreate ?", "SolutionLogTypeID", name);
    }

    static Timestamp ts(LocalDateTime v) { return v == null ? null : Timestamp.valueOf(v); }
}
