package com.echo;

import com.echo.SolutionTestModels.Location;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionTestRepository} (2026-09-21): the
 * stp_SolutionTest* procs. Every write proc ends in a SELECT, so all of them go
 * through {@link SolutionJdbc}'s query path and a RAISERROR comes back as a
 * {@link SolutionRuleException} (409).
 */
@Repository
public class JdbcSolutionTestRepository implements SolutionTestRepository {

    private final SolutionJdbc db;

    public JdbcSolutionTestRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public Location locate(String kind, int id) {
        Map<String, Object> r = db.row("EXEC dbo.stp_SolutionTest_Locate ?, ?", kind, id);
        return r == null ? null : new Location(intOf(r, "SolutionID"), intOf(r, "SolutionTestPlanID"));
    }

    @Override
    public List<Map<String, Object>> plans(int solutionId, String kind) {
        return db.list("EXEC dbo.stp_SolutionTestPlan_ListForSolution ?, ?", r -> r, solutionId, kind);
    }

    @Override
    public Map<String, Object> plan(int planId) { return db.row("EXEC dbo.stp_SolutionTestPlan_Get ?", planId); }

    @Override
    public List<Map<String, Object>> levels(int planId) {
        return db.list("EXEC dbo.stp_SolutionTestPlanLevel_ListForPlan ?", r -> r, planId);
    }

    @Override
    public List<Map<String, Object>> groups(int planId) {
        return db.list("EXEC dbo.stp_SolutionTestGroup_ListForPlan ?", r -> r, planId);
    }

    @Override
    public List<Map<String, Object>> cases(int planId) {
        return db.list("EXEC dbo.stp_SolutionTestCase_ListForPlan ?", r -> r, planId);
    }

    @Override
    public List<Map<String, Object>> checks(int planId) {
        return db.list("EXEC dbo.stp_SolutionTestCheck_ListForPlan ?", r -> r, planId);
    }

    @Override
    public List<Map<String, Object>> skips(int planId) {
        return db.list("EXEC dbo.stp_SolutionTestSkip_ListForPlan ?", r -> r, planId);
    }

    @Override
    public Map<String, Integer> counts(int planId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        db.list("EXEC dbo.stp_SolutionTestPlan_Counts ?", r -> r, planId).forEach(r -> {
            String bucket = strOf(r, "Bucket");
            String key = strOf(r, "BucketKey");
            out.put((key == null || key.isEmpty()) ? bucket : bucket + ":" + key, intOf(r, "Cnt"));
        });
        return out;
    }

    @Override
    public int savePlan(int id, int solutionId, String title, String description, Integer sortOrder, String modifiedBy, String kind) {
        return db.number("EXEC dbo.stp_SolutionTestPlan_Save ?, ?, ?, ?, ?, ?, ?", "SolutionTestPlanID",
            id, solutionId, title, description, sortOrder, modifiedBy, kind);
    }

    @Override
    public boolean deletePlan(int id) { return db.affected("EXEC dbo.stp_SolutionTestPlan_Delete ?", id); }

    @Override
    public int saveLevel(int id, int planId, String title, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionTestPlanLevel_Save ?, ?, ?, ?", "SolutionTestPlanLevelID", id, planId, title, sortOrder);
    }

    @Override
    public boolean deleteLevel(int id) { return db.affected("EXEC dbo.stp_SolutionTestPlanLevel_Delete ?", id); }

    @Override
    public int saveGroup(int id, int planId, String title, Integer sortOrder) {
        return db.number("EXEC dbo.stp_SolutionTestGroup_Save ?, ?, ?, ?", "SolutionTestGroupID", id, planId, title, sortOrder);
    }

    @Override
    public boolean deleteGroup(int id) { return db.affected("EXEC dbo.stp_SolutionTestGroup_Delete ?", id); }

    @Override
    public int saveCase(int id, int groupId, String text, String note, Integer sortOrder, Boolean includeInPrint) {
        return db.number("EXEC dbo.stp_SolutionTestCase_Save ?, ?, ?, ?, ?, ?", "SolutionTestCaseID", id, groupId, text, note, sortOrder, includeInPrint);
    }

    @Override
    public boolean deleteCase(int id) { return db.affected("EXEC dbo.stp_SolutionTestCase_Delete ?", id); }

    @Override
    public Map<String, Object> setCheck(int caseId, int levelId, boolean checked, String memberCode) {
        return db.row("EXEC dbo.stp_SolutionTestCheck_Set ?, ?, ?, ?", caseId, levelId, checked, memberCode);
    }

    @Override
    public Map<String, Object> setSkip(int caseId, int levelId, boolean skipped, String memberCode) {
        return db.row("EXEC dbo.stp_SolutionTestSkip_Set ?, ?, ?, ?", caseId, levelId, skipped, memberCode);
    }

    @Override
    public Map<String, Object> setResult(int caseId, Boolean failing, String resultNote, String memberCode) {
        return db.row("EXEC dbo.stp_SolutionTestResult_Set ?, ?, ?, ?", caseId, failing, resultNote, memberCode);
    }

    @Override
    public void reorderCases(int groupId, List<Integer> caseIds) {
        db.row("EXEC dbo.stp_SolutionTestCase_Reorder ?, ?", groupId, joined(caseIds));
    }

    @Override
    public void reorderGroups(int planId, List<Integer> groupIds) {
        db.row("EXEC dbo.stp_SolutionTestGroup_Reorder ?, ?", planId, joined(groupIds));
    }

    private static String joined(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) if (id != null) { if (sb.length() > 0) sb.append(','); sb.append(id); }
        return sb.toString();
    }

    @Override
    public int clearChecks(int planId, Integer levelId) {
        return db.number("EXEC dbo.stp_SolutionTestCheck_Clear ?, ?", "RowsAffected", planId, levelId);
    }
}
