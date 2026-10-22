package com.echo;

import com.echo.SolutionTestModels.Location;

import java.util.List;
import java.util.Map;

/**
 * Storage for a solution's testing checklists (2026-09-21). Reads hand back
 * result rows (column-name maps) so ONE set of mappers in
 * {@link SolutionTestModels} serves both implementations. Jdbc = the
 * stp_SolutionTest* procs; demo = {@link InMemorySolutionTestRepository}.
 * A checklist is shown whole, so none of these reads is paged.
 */
public interface SolutionTestRepository {

    /** kind = plan | level | group | case. Null when it does not exist. */
    Location locate(String kind, int id);

    /** kind: dev | qa, or null for every checklist on the solution. */
    List<Map<String, Object>> plans(int solutionId, String kind);

    Map<String, Object> plan(int planId);

    List<Map<String, Object>> levels(int planId);

    List<Map<String, Object>> groups(int planId);

    List<Map<String, Object>> cases(int planId);

    /** Only the ticked cells. */
    List<Map<String, Object>> checks(int planId);

    /** Only the skipped cells (Story 3534). */
    List<Map<String, Object>> skips(int planId);

    /** "cases", "checked", "failing", "level:c", "group:g", "groupchecked:g", "groupfailing:g", "grouplevel:g.c",
     *  "skipped", "levelskipped:c", "groupskipped:g", "grouplevelskipped:g.c". */
    Map<String, Integer> counts(int planId);

    /** Add (id = 0) or edit; returns the id. A new dev checklist starts with Manual and Unit tested, a new qa one with
     *  Passed. kind is read only on an add -- an edit never changes it. */
    int savePlan(int id, int solutionId, String title, String description, Integer sortOrder, String modifiedBy, String kind);

    boolean deletePlan(int id);

    int saveLevel(int id, int planId, String title, Integer sortOrder);

    boolean deleteLevel(int id);

    int saveGroup(int id, int planId, String title, Integer sortOrder);

    boolean deleteGroup(int id);

    /** includeInPrint: null keeps it on an edit, and means on for a new case. */
    int saveCase(int id, int groupId, String text, String note, Integer sortOrder, Boolean includeInPrint);

    boolean deleteCase(int id);

    /** Tick or untick one cell; ticking also unskips it. Returns the cell row. */
    Map<String, Object> setCheck(int caseId, int levelId, boolean checked, String memberCode);

    /** Skip or unskip one cell (Story 3534); skipping also unticks it. Returns the cell row. */
    Map<String, Object> setSkip(int caseId, int levelId, boolean skipped, String memberCode);

    /**
     * Mark a case failing or not, and / or set its result note (what is failing). A merge: null keeps what is
     * there, a blank note clears it. Returns the result row.
     */
    Map<String, Object> setResult(int caseId, Boolean failing, String resultNote, String memberCode);

    /** Put a group's cases in this order (drag to reorder); ids outside the group are ignored. */
    void reorderCases(int groupId, java.util.List<Integer> caseIds);

    /** Put a checklist's groups in this order; ids outside the checklist are ignored. */
    void reorderGroups(int planId, java.util.List<Integer> groupIds);

    /** Untick every cell of a checklist, or of one column; returns how many were unticked. Clearing the whole
     *  checklist also takes every failing mark off (result notes stay). */
    int clearChecks(int planId, Integer levelId);
}
