package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionConfigGridRepository} (Story 5611): the stp_SolutionConfigGrid* procs.
 * Every write proc ends in a SELECT, so all go through {@link SolutionJdbc}'s query path, and a RAISERROR
 * comes back as a {@link SolutionRuleException} (409).
 */
@Repository
public class JdbcSolutionConfigGridRepository implements SolutionConfigGridRepository {

    private final SolutionJdbc db;

    public JdbcSolutionConfigGridRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public Integer locate(String kind, int id) {
        Map<String, Object> r = db.row("EXEC dbo.stp_SolutionConfigGrid_Locate ?, ?", kind, id);
        return r == null ? null : intOf(r, "SolutionID");
    }

    @Override
    public List<Map<String, Object>> axes(int solutionId) { return db.list("EXEC dbo.stp_SolutionConfigGridAxis_List ?", r -> r, solutionId); }

    @Override
    public List<Map<String, Object>> axisValues(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigGridAxisValue_List ?", r -> r, solutionId);
    }

    @Override
    public List<Map<String, Object>> columns(int solutionId) { return db.list("EXEC dbo.stp_SolutionConfigGridColumn_List ?", r -> r, solutionId); }

    @Override
    public List<Map<String, Object>> tags(int solutionId) { return db.list("EXEC dbo.stp_SolutionConfigGridColumnTag_List ?", r -> r, solutionId); }

    @Override
    public List<Map<String, Object>> groups(int solutionId) { return db.list("EXEC dbo.stp_SolutionConfigGridGroup_List ?", r -> r, solutionId); }

    @Override
    public List<Map<String, Object>> properties(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigGridProperty_List ?", r -> r, solutionId);
    }

    @Override
    public List<Map<String, Object>> values(int solutionId) { return db.list("EXEC dbo.stp_SolutionConfigGridValue_List ?", r -> r, solutionId); }

    @Override
    public List<Map<String, Object>> alsoSet(int solutionId) { return db.list("EXEC dbo.stp_SolutionConfigGridAlsoSet_List ?", r -> r, solutionId); }

    @Override
    public List<Map<String, Object>> types() { return db.list("EXEC dbo.stp_SolutionConfigType_List", r -> r); }

    @Override
    public List<Map<String, Object>> cellStates() { return db.list("EXEC dbo.stp_SolutionConfigGridCellState_List", r -> r); }

    @Override
    public List<String> sources(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigGridSource_ListForSolution ?", r -> strOf(r, "SourceTitle"), solutionId);
    }

    @Override
    public int saveAxis(int id, int solutionId, String title, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionConfigGridAxis_Save ?, ?, ?, ?", "SolutionConfigGridAxisID", id, solutionId, title, sortOrder);
    }

    @Override
    public boolean deleteAxis(int id) { return db.affected("EXEC dbo.stp_SolutionConfigGridAxis_Delete ?", id); }

    @Override
    public int saveAxisValue(int id, int axisId, String title, String color, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionConfigGridAxisValue_Save ?, ?, ?, ?, ?", "SolutionConfigGridAxisValueID",
            id, axisId, title, color, sortOrder);
    }

    @Override
    public boolean deleteAxisValue(int id) { return db.affected("EXEC dbo.stp_SolutionConfigGridAxisValue_Delete ?", id); }

    @Override
    public int saveColumn(int id, int solutionId, String title, String color, boolean isDefault, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionConfigGridColumn_Save ?, ?, ?, ?, ?, ?", "SolutionConfigGridColumnID",
            id, solutionId, title, color, isDefault, sortOrder);
    }

    @Override
    public boolean deleteColumn(int id) { return db.affected("EXEC dbo.stp_SolutionConfigGridColumn_Delete ?", id); }

    @Override
    public void setTag(int columnId, String axisTitle, String valueTitle) {
        db.row("EXEC dbo.stp_SolutionConfigGridColumnTag_Set ?, ?, ?", columnId, axisTitle, valueTitle);
    }

    @Override
    public int saveGroup(int id, int solutionId, String title, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionConfigGridGroup_Save ?, ?, ?, ?", "SolutionConfigGridGroupID", id, solutionId, title, sortOrder);
    }

    @Override
    public boolean deleteGroup(int id) { return db.affected("EXEC dbo.stp_SolutionConfigGridGroup_Delete ?", id); }

    @Override
    public int saveProperty(int id, int groupId, String key, String description, String typeCode, Integer sortOrder, String modifiedBy) {
        return db.number("EXEC dbo.stp_SolutionConfigGridProperty_Save ?, ?, ?, ?, ?, ?, ?", "SolutionConfigGridPropertyID",
            id, groupId, key, description, typeCode, sortOrder, modifiedBy);
    }

    @Override
    public boolean deleteProperty(int id) { return db.affected("EXEC dbo.stp_SolutionConfigGridProperty_Delete ?", id); }

    @Override
    public void saveValue(int propertyId, int columnId, String value, String source, String note, String state, List<String> alsoSetIn, String memberCode) {
        db.row("EXEC dbo.stp_SolutionConfigGridValue_Save ?, ?, ?, ?, ?, ?, ?, ?", propertyId, columnId, value, source, note, state,
            alsoSetIn == null || alsoSetIn.isEmpty() ? null : String.join("|", alsoSetIn), memberCode);
    }

    @Override
    public Map<String, Object> importFromConfig(int solutionId) {
        return db.row("EXEC dbo.stp_SolutionConfigGrid_ImportFromConfig ?", solutionId);
    }
}
