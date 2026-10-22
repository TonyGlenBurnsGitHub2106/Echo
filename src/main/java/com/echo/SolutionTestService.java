package com.echo;

import com.echo.SolutionTestModels.Case;
import com.echo.SolutionTestModels.Check;
import com.echo.SolutionTestModels.Group;
import com.echo.SolutionTestModels.Level;
import com.echo.SolutionTestModels.Location;
import com.echo.SolutionTestModels.Plan;
import com.echo.SolutionTestModels.PlanSummary;
import com.echo.SolutionTestModels.Skip;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;

/**
 * A solution's testing checklists (2026-09-21). Every call first asks where the
 * thing lives and passes the solution through {@link SolutionAccess}, so a
 * scoped client reaches only the checklists on a solution it was given -- and
 * inside such a solution it has full rights, like everywhere else in the family.
 *
 * Every write answers with the whole checklist, counters included, so the page
 * and an agent always hold the current state after one call.
 */
@Service
public class SolutionTestService {

    private final SolutionTestRepository repo;
    private final SolutionAccess access;

    public SolutionTestService(SolutionTestRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    // ---- reads ------------------------------------------------------------------

    /** {@code kind}: dev | qa narrows the list to one tab's checklists; null = every checklist. */
    public List<PlanSummary> list(SolutionCaller caller, int solutionId, String kind) {
        String k = SolutionTestModels.kindOrNull(kind);
        access.requireSolution(caller, solutionId);
        return repo.plans(solutionId, k).stream().map(PlanSummary::of).toList();
    }

    public Plan plan(SolutionCaller caller, int planId) {
        return assemble(require(caller, "plan", planId, "Testing checklist").planId());
    }

    private Plan assemble(int planId) {
        Map<String, Object> header = repo.plan(planId);
        if (header == null) throw new SolutionNotFoundException("Testing checklist");
        PlanSummary s = PlanSummary.of(header);
        Map<String, Integer> counts = repo.counts(planId);
        List<Level> levels = repo.levels(planId).stream().map(r -> Level.of(r, counts)).toList();

        Map<Integer, List<Check>> checksByCase = new HashMap<>();
        for (Map<String, Object> r : repo.checks(planId))
            checksByCase.computeIfAbsent(intOf(r, "SolutionTestCaseID"), k -> new ArrayList<>()).add(Check.of(r));
        Map<Integer, List<Skip>> skipsByCase = new HashMap<>();
        for (Map<String, Object> r : repo.skips(planId))
            skipsByCase.computeIfAbsent(intOf(r, "SolutionTestCaseID"), k -> new ArrayList<>()).add(Skip.of(r));
        Map<Integer, List<Case>> casesByGroup = new HashMap<>();
        for (Map<String, Object> r : repo.cases(planId)) {
            int caseId = intOf(r, "SolutionTestCaseID");
            Case c = Case.of(r, checksByCase.getOrDefault(caseId, List.of()), skipsByCase.getOrDefault(caseId, List.of()));
            casesByGroup.computeIfAbsent(c.groupId(), k -> new ArrayList<>()).add(c);
        }
        List<Group> groups = new ArrayList<>();
        for (Map<String, Object> r : repo.groups(planId)) {
            int gid = intOf(r, "SolutionTestGroupID");
            Map<Integer, Integer> byLevel = new LinkedHashMap<>(), skippedByLevel = new LinkedHashMap<>();
            for (Level l : levels) {
                byLevel.put(l.id(), counts.getOrDefault("grouplevel:" + gid + "." + l.id(), 0));
                skippedByLevel.put(l.id(), counts.getOrDefault("grouplevelskipped:" + gid + "." + l.id(), 0));
            }
            groups.add(new Group(gid, SolutionRows.strOf(r, "GroupTitle"), intOf(r, "SortOrder"),
                counts.getOrDefault("group:" + gid, 0), counts.getOrDefault("groupchecked:" + gid, 0),
                counts.getOrDefault("groupskipped:" + gid, 0), counts.getOrDefault("groupfailing:" + gid, 0), byLevel, skippedByLevel,
                casesByGroup.getOrDefault(gid, List.of())));
        }
        return new Plan(s.id(), s.solutionId(), s.title(), s.description(), s.sortOrder(), s.kind(),
            counts.getOrDefault("cases", 0), counts.getOrDefault("checked", 0), counts.getOrDefault("skipped", 0),
            counts.getOrDefault("cases", 0) * levels.size(), counts.getOrDefault("failing", 0),
            s.modifiedDate(), s.modifiedBy(), levels, groups, counts);
    }

    /** Where the thing lives, provided it exists and this caller may see its solution. */
    private Location require(SolutionCaller caller, String kind, int id, String what) {
        Location loc = id <= 0 ? null : repo.locate(kind, id);
        if (loc == null) throw new SolutionNotFoundException(what);
        access.requireSolution(caller, loc.solutionId());
        return loc;
    }

    // ---- checklists ---------------------------------------------------------------

    /** {@code kind}: dev (the default -- Manual and Unit tested) or qa (one column, Passed). Fixed once made. */
    public Plan createPlan(SolutionCaller caller, int solutionId, String title, String description, String kind) {
        String k = SolutionTestModels.kindOrNull(kind);
        access.requireSolution(caller, solutionId);
        int id = repo.savePlan(0, solutionId, title, description, null, caller.memberCode(),
            k == null ? SolutionTestModels.KIND_DEV : k);
        if (id <= 0) throw new SolutionNotFoundException("Testing checklist");
        return assemble(id);
    }

    /** A PUT is a merge: a field left out keeps its value. */
    public Plan updatePlan(SolutionCaller caller, int planId, String title, String description, Integer sortOrder) {
        require(caller, "plan", planId, "Testing checklist");
        PlanSummary current = PlanSummary.of(repo.plan(planId));
        repo.savePlan(planId, current.solutionId(), SolutionService.keep(title, current.title(), true),
            SolutionService.keep(description, current.description(), false), sortOrder, caller.memberCode(), current.kind());
        return assemble(planId);
    }

    public void deletePlan(SolutionCaller caller, int planId) {
        require(caller, "plan", planId, "Testing checklist");
        if (!repo.deletePlan(planId)) throw new SolutionNotFoundException("Testing checklist");
    }

    // ---- columns ------------------------------------------------------------------

    public Plan addLevel(SolutionCaller caller, int planId, String title) {
        require(caller, "plan", planId, "Testing checklist");
        repo.saveLevel(0, planId, title, null);
        return assemble(planId);
    }

    public Plan updateLevel(SolutionCaller caller, int levelId, String title, Integer sortOrder) {
        Location loc = require(caller, "level", levelId, "Column");
        String current = repo.levels(loc.planId()).stream().filter(r -> intOf(r, "SolutionTestPlanLevelID") == levelId)
            .map(r -> SolutionRows.strOf(r, "LevelTitle")).findFirst().orElse(null);
        repo.saveLevel(levelId, loc.planId(), SolutionService.keep(title, current, true), sortOrder);
        return assemble(loc.planId());
    }

    public Plan deleteLevel(SolutionCaller caller, int levelId) {
        Location loc = require(caller, "level", levelId, "Column");
        if (!repo.deleteLevel(levelId)) throw new SolutionNotFoundException("Column");
        return assemble(loc.planId());
    }

    // ---- groups -------------------------------------------------------------------

    public Plan addGroup(SolutionCaller caller, int planId, String title) {
        require(caller, "plan", planId, "Testing checklist");
        repo.saveGroup(0, planId, title, null);
        return assemble(planId);
    }

    public Plan updateGroup(SolutionCaller caller, int groupId, String title, Integer sortOrder) {
        Location loc = require(caller, "group", groupId, "Group");
        String current = repo.groups(loc.planId()).stream().filter(r -> intOf(r, "SolutionTestGroupID") == groupId)
            .map(r -> SolutionRows.strOf(r, "GroupTitle")).findFirst().orElse(null);
        repo.saveGroup(groupId, loc.planId(), SolutionService.keep(title, current, true), sortOrder);
        return assemble(loc.planId());
    }

    public Plan deleteGroup(SolutionCaller caller, int groupId) {
        Location loc = require(caller, "group", groupId, "Group");
        if (!repo.deleteGroup(groupId)) throw new SolutionNotFoundException("Group");
        return assemble(loc.planId());
    }

    // ---- cases --------------------------------------------------------------------

    public Plan addCase(SolutionCaller caller, int groupId, String text, String note) {
        Location loc = require(caller, "group", groupId, "Group");
        repo.saveCase(0, groupId, text, note, null, null);
        return assemble(loc.planId());
    }

    /** includeInPrint: null keeps it. */
    public Plan updateCase(SolutionCaller caller, int caseId, Integer groupId, String text, String note, Integer sortOrder,
                           Boolean includeInPrint) {
        Location loc = require(caller, "case", caseId, "Test case");
        Map<String, Object> current = repo.cases(loc.planId()).stream()
            .filter(r -> intOf(r, "SolutionTestCaseID") == caseId).findFirst().orElse(null);
        if (current == null) throw new SolutionNotFoundException("Test case");
        int target = (groupId == null || groupId <= 0) ? intOf(current, "SolutionTestGroupID") : groupId;
        if (target != intOf(current, "SolutionTestGroupID")) {
            Location to = repo.locate("group", target);
            if (to == null || to.planId() != loc.planId())
                throw new SolutionRuleException("A test case can only move to a group in the same checklist.");
        }
        repo.saveCase(caseId, target, SolutionService.keep(text, SolutionRows.strOf(current, "CaseText"), true),
            SolutionService.keep(note, SolutionRows.strOf(current, "CaseNote"), false), sortOrder, includeInPrint);
        return assemble(loc.planId());
    }

    public Plan deleteCase(SolutionCaller caller, int caseId) {
        Location loc = require(caller, "case", caseId, "Test case");
        if (!repo.deleteCase(caseId)) throw new SolutionNotFoundException("Test case");
        return assemble(loc.planId());
    }

    // ---- order (drag to reorder) -------------------------------------------------------

    public Plan reorderCases(SolutionCaller caller, int groupId, List<Integer> caseIds) {
        Location loc = require(caller, "group", groupId, "Group");
        repo.reorderCases(groupId, caseIds == null ? List.of() : caseIds);
        return assemble(loc.planId());
    }

    public Plan reorderGroups(SolutionCaller caller, int planId, List<Integer> groupIds) {
        require(caller, "plan", planId, "Testing checklist");
        repo.reorderGroups(planId, groupIds == null ? List.of() : groupIds);
        return assemble(planId);
    }

    // ---- ticks --------------------------------------------------------------------

    /**
     * One cell: {@code checked} ticks or unticks it, {@code skipped} (Story 3534) skips or unskips it -- a skipped cell is
     * neither a pass nor a fail. Exactly one of the two. Ticking unskips the cell and skipping unticks it.
     */
    public Plan setCheck(SolutionCaller caller, int caseId, int levelId, Boolean checked, Boolean skipped) {
        if ((checked == null) == (skipped == null))
            throw new IllegalArgumentException("Send checked (true or false) or skipped (true or false) -- one of the two.");
        Location loc = require(caller, "case", caseId, "Test case");
        Location col = require(caller, "level", levelId, "Column");
        if (col.planId() != loc.planId()) throw new SolutionRuleException("That column belongs to a different checklist.");
        if (skipped != null) repo.setSkip(caseId, levelId, skipped, caller.memberCode());
        else repo.setCheck(caseId, levelId, checked, caller.memberCode());
        return assemble(loc.planId());
    }

    /**
     * The result of running a case (2026-09-25): mark it failing or not, and / or say what is failing. A merge --
     * a field left out keeps its value, a blank note clears it. At least one of the two must be given.
     */
    public Plan setResult(SolutionCaller caller, int caseId, Boolean failing, String resultNote) {
        if (failing == null && resultNote == null)
            throw new IllegalArgumentException("failing (true or false) or note is required.");
        Location loc = require(caller, "case", caseId, "Test case");
        repo.setResult(caseId, failing, resultNote, caller.memberCode());
        return assemble(loc.planId());
    }

    /** Untick the whole checklist (and take its failing marks off), or one column of it (levelId), to run the tests again.
     *  Skipped cells stay skipped -- a skip is a decision about the test, not a result (Story 3534). */
    public Plan clear(SolutionCaller caller, int planId, Integer levelId) {
        require(caller, "plan", planId, "Testing checklist");
        if (levelId != null && levelId > 0) {
            Location col = require(caller, "level", levelId, "Column");
            if (col.planId() != planId) throw new SolutionRuleException("That column belongs to a different checklist.");
        }
        repo.clearChecks(planId, levelId);
        return assemble(planId);
    }
}
