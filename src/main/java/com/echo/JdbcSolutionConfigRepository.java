package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionConfigRepository} (Story 3516): the stp_SolutionConfig* procs.
 * Every write proc ends in a SELECT, so all go through {@link SolutionJdbc}'s query path, and a
 * RAISERROR comes back as a {@link SolutionRuleException} (409).
 */
@Repository
public class JdbcSolutionConfigRepository implements SolutionConfigRepository {

    private final SolutionJdbc db;

    public JdbcSolutionConfigRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public Integer locate(String kind, int id) {
        Map<String, Object> r = db.row("EXEC dbo.stp_SolutionConfig_Locate ?, ?", kind, id);
        return r == null ? null : intOf(r, "SolutionID");
    }

    @Override
    public List<Map<String, Object>> environments(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigEnvironment_List ?", r -> r, solutionId);
    }

    @Override
    public List<Map<String, Object>> groups(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigGroup_List ?", r -> r, solutionId);
    }

    @Override
    public List<Map<String, Object>> properties(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigProperty_List ?", r -> r, solutionId);
    }

    @Override
    public List<Map<String, Object>> values(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigValue_List ?", r -> r, solutionId);
    }

    @Override
    public List<Map<String, Object>> alsoSet(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigAlsoSet_List ?", r -> r, solutionId);
    }

    @Override
    public Map<String, Integer> counts(int solutionId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        db.list("EXEC dbo.stp_SolutionConfig_Counts ?", r -> r, solutionId).forEach(r -> {
            String bucket = strOf(r, "Bucket");
            String key = strOf(r, "BucketKey");
            out.put((key == null || key.isEmpty()) ? bucket : bucket + ":" + key, intOf(r, "Cnt"));
        });
        return out;
    }

    @Override
    public List<Map<String, Object>> types() { return db.list("EXEC dbo.stp_SolutionConfigType_List", r -> r); }

    @Override
    public List<String> sources(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConfigSource_ListForSolution ?", r -> strOf(r, "SourceTitle"), solutionId);
    }

    @Override
    public int seedDefaultEnvironments(int solutionId) {
        return db.number("EXEC dbo.stp_SolutionConfigEnvironment_SeedDefaults ?", "RowsAffected", solutionId);
    }

    @Override
    public int saveEnvironment(int id, int solutionId, String title, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionConfigEnvironment_Save ?, ?, ?, ?", "SolutionConfigEnvironmentID", id, solutionId, title, sortOrder);
    }

    @Override
    public boolean deleteEnvironment(int id) { return db.affected("EXEC dbo.stp_SolutionConfigEnvironment_Delete ?", id); }

    @Override
    public int saveGroup(int id, int solutionId, String title, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionConfigGroup_Save ?, ?, ?, ?", "SolutionConfigGroupID", id, solutionId, title, sortOrder);
    }

    @Override
    public boolean deleteGroup(int id) { return db.affected("EXEC dbo.stp_SolutionConfigGroup_Delete ?", id); }

    @Override
    public int saveProperty(int id, int groupId, String key, String description, String typeCode, Integer sortOrder, String modifiedBy) {
        return db.number("EXEC dbo.stp_SolutionConfigProperty_Save ?, ?, ?, ?, ?, ?, ?", "SolutionConfigPropertyID",
            id, groupId, key, description, typeCode, sortOrder, modifiedBy);
    }

    @Override
    public boolean deleteProperty(int id) { return db.affected("EXEC dbo.stp_SolutionConfigProperty_Delete ?", id); }

    @Override
    public void saveValue(int propertyId, int environmentId, String value, String source, String note, List<String> alsoSetIn, String memberCode) {
        db.row("EXEC dbo.stp_SolutionConfigValue_Save ?, ?, ?, ?, ?, ?, ?", propertyId, environmentId, value, source, note,
            alsoSetIn == null || alsoSetIn.isEmpty() ? null : String.join("|", alsoSetIn), memberCode);
    }
}
