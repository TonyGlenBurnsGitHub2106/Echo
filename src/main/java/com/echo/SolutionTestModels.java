package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The read models of a solution's TESTING CHECKLISTS (2026-09-21). A checklist
 * (a "test plan") is a grid: its rows are CASES gathered into GROUPS, and its
 * columns are LEVELS of testing -- Manual and Unit tested to begin
 * with, and whatever the user adds after. A CHECK is one cell: a case in a
 * column, ticked, skipped (Story 3534 -- neither a pass nor a fail) or neither. Column names are the ones the stp_SolutionTest* procs
 * return; the demo store uses the same names.
 */
public final class SolutionTestModels {

    private SolutionTestModels() {}

    /** Where a plan, column, group or case lives -- asked before every read or write. */
    public record Location(int solutionId, int planId) {}

    /** Who a checklist is for (2026-10-15): the developer's Testing tab, or the QA tab -- what a tester does. */
    public static final String KIND_DEV = "dev";
    public static final String KIND_QA  = "qa";

    /** dev | qa from what a caller sent; null when it is blank (= the default). Anything else is refused. */
    public static String kindOrNull(String kind) {
        if (kind == null || kind.isBlank()) return null;
        String k = kind.trim().toLowerCase();
        if (!k.equals(KIND_DEV) && !k.equals(KIND_QA))
            throw new IllegalArgumentException("A checklist kind is dev or qa, not \"" + kind.trim() + "\".");
        return k;
    }

    /** One checklist on a solution's list, with its counters. {@code kind}: dev (Testing tab) or qa (QA tab). */
    public record PlanSummary(int id, int solutionId, String title, String description, int sortOrder, String kind,
                              int groupCount, int caseCount, int levelCount, int checkedCount, int skippedCount, int cellCount,
                              LocalDateTime createdDate, LocalDateTime modifiedDate, String modifiedBy) {
        static PlanSummary of(Map<String, Object> r) {
            int cases = intOf(r, "CaseCount"), levels = intOf(r, "LevelCount");
            String kind = strOf(r, "PlanKindCode");
            return new PlanSummary(intOf(r, "SolutionTestPlanID"), intOf(r, "SolutionID"), strOf(r, "PlanTitle"),
                strOf(r, "PlanDescription"), intOf(r, "SortOrder"), kind == null ? KIND_DEV : kind.trim(), intOf(r, "GroupCount"), cases, levels,
                intOf(r, "CheckedCount"), intOf(r, "SkippedCount"), cases * levels, dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"),
                strOf(r, "ModifiedBy"));
        }
    }

    /** A column (a level of testing). {@code id} is the column id every check refers to; {@code levelId} is the shared title. */
    public record Level(int id, int levelId, String title, int sortOrder, int checkedCount, int skippedCount) {
        static Level of(Map<String, Object> r, Map<String, Integer> counts) {
            int id = intOf(r, "SolutionTestPlanLevelID");
            return new Level(id, intOf(r, "SolutionTestLevelID"), strOf(r, "LevelTitle"), intOf(r, "SortOrder"),
                counts.getOrDefault("level:" + id, 0), counts.getOrDefault("levelskipped:" + id, 0));
        }
    }

    /** A ticked cell: who ticked it and when. An unticked cell is simply absent from a case's list. */
    public record Check(int caseId, int columnId, boolean checked, LocalDateTime checkedDate,
                        String checkedByCode, String checkedByName) {
        static Check of(Map<String, Object> r) {
            return new Check(intOf(r, "SolutionTestCaseID"), intOf(r, "SolutionTestPlanLevelID"), boolOf(r, "IsChecked"),
                dateOf(r, "CheckedDate"), strOf(r, "CheckedByCode"), strOf(r, "CheckedByName"));
        }
    }

    /** A skipped cell (Story 3534): who skipped it and when. A cell that is not skipped is simply absent. */
    public record Skip(int caseId, int columnId, LocalDateTime skippedDate, String skippedByCode, String skippedByName) {
        static Skip of(Map<String, Object> r) {
            return new Skip(intOf(r, "SolutionTestCaseID"), intOf(r, "SolutionTestPlanLevelID"), dateOf(r, "SkippedDate"),
                strOf(r, "SkippedByCode"), strOf(r, "SkippedByName"));
        }
    }

    /**
     * {@code includeInPrint}: whether the case appears on the printed checklist (on unless someone turned it off).
     * {@code note} is the SET-UP note (how to run it); {@code failing} and {@code resultNote} are what happened
     * when it was run (2026-09-25) -- marked failing, and what is failing. Both live in SolutionTestResult.
     */
    public record Case(int id, int groupId, String text, String note, int sortOrder, boolean includeInPrint,
                       boolean failing, LocalDateTime failingDate, String resultNote, LocalDateTime resultDate,
                       String resultByName, List<Check> checks, List<Skip> skips) {
        static Case of(Map<String, Object> r, List<Check> checks, List<Skip> skips) {
            return new Case(intOf(r, "SolutionTestCaseID"), intOf(r, "SolutionTestGroupID"), strOf(r, "CaseText"),
                strOf(r, "CaseNote"), intOf(r, "SortOrder"), r.get("IncludeInPrint") == null || boolOf(r, "IncludeInPrint"),
                r.get("IsFailing") != null && boolOf(r, "IsFailing"), dateOf(r, "FailingDate"), strOf(r, "ResultNote"),
                dateOf(r, "ResultModifiedDate"), strOf(r, "ResultByName"), checks, skips);
        }
    }

    /** A group, its cases, and its counters: {@code checkedByColumn} and {@code skippedByColumn} are keyed by the column id. */
    public record Group(int id, String title, int sortOrder, int caseCount, int checkedCount, int skippedCount, int failingCount,
                        Map<Integer, Integer> checkedByColumn, Map<Integer, Integer> skippedByColumn, List<Case> cases) {}

    /** A whole checklist, as the page and an agent read it. {@code counts} is the raw counter set. */
    public record Plan(int id, int solutionId, String title, String description, int sortOrder, String kind,
                       int caseCount, int checkedCount, int skippedCount, int cellCount, int failingCount, LocalDateTime modifiedDate, String modifiedBy,
                       List<Level> columns, List<Group> groups, Map<String, Integer> counts) {}
}
