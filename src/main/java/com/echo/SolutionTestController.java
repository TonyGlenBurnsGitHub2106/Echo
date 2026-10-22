package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * A solution's testing checklists (2026-09-21): a grid of test CASES in GROUPS
 * down the side and COLUMNS (levels of testing -- Manual, Unit tested,
 * whatever else is added) across the top. Each cell ticks and unticks.
 *
 *   GET    /api/solutions/{id}/test-plans[?kind=dev|qa]          the checklists, with counters (no kind = both)
 *   POST   /api/solutions/{id}/test-plans                        {title, description, kind} -- dev (default) starts with
 *                                                                Manual + Unit tested; qa (the QA tab) with Passed
 *   GET    /api/solutions/test-plans/{planId}                    the whole grid
 *   PUT    /api/solutions/test-plans/{planId}                    {title, description, sortOrder} -- a merge
 *   DELETE /api/solutions/test-plans/{planId}
 *   POST   /api/solutions/test-plans/{planId}/columns            {title}
 *   PUT    /api/solutions/test-plans/columns/{columnId}          {title, sortOrder}
 *   DELETE /api/solutions/test-plans/columns/{columnId}          takes that column's ticks; never the last column
 *   POST   /api/solutions/test-plans/{planId}/groups             {title}
 *   PUT    /api/solutions/test-plans/groups/{groupId}            {title, sortOrder}
 *   DELETE /api/solutions/test-plans/groups/{groupId}            takes its cases
 *   POST   /api/solutions/test-plans/groups/{groupId}/cases      {text, note}
 *   PUT    /api/solutions/test-plans/cases/{caseId}              {text, note, groupId, sortOrder, includeInPrint} -- a merge
 *   DELETE /api/solutions/test-plans/cases/{caseId}
 *   PUT    /api/solutions/test-plans/cases/{caseId}/checks/{columnId}   {checked} or {skipped}
 *   PUT    /api/solutions/test-plans/cases/{caseId}/result      {failing, note} -- a merge; note = what is failing
 *   POST   /api/solutions/test-plans/{planId}/clear              {columnId} -- untick everything (and clear failing), or one column
 *   PUT    /api/solutions/test-plans/groups/{groupId}/case-order {ids} -- the group's cases in their new order
 *   PUT    /api/solutions/test-plans/{planId}/group-order        {ids} -- the checklist's groups in their new order
 *
 * Every write answers with the whole checklist.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionTestController extends SolutionControllerSupport {

    private final SolutionTestService service;

    public SolutionTestController(SolutionTestService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    /** kind (dev | qa) is read on a POST only; a checklist never changes kind. */
    public record PlanRequest(String title, String description, Integer sortOrder, String kind) {}
    public record TitleRequest(String title, Integer sortOrder) {}
    public record CaseRequest(String text, String note, Integer groupId, Integer sortOrder, Boolean includeInPrint) {}
    public record CheckRequest(Boolean checked, Boolean skipped) {}
    public record ResultRequest(Boolean failing, String note) {}
    public record ClearRequest(Integer columnId) {}
    public record OrderRequest(java.util.List<Integer> ids) {}

    // ---- checklists ---------------------------------------------------------------

    @GetMapping("/{id:\\d+}/test-plans")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id, @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(service.list(caller(req), id, kind));
    }

    @PostMapping("/{id:\\d+}/test-plans")
    public ResponseEntity<?> create(HttpServletRequest req, @PathVariable int id, @RequestBody PlanRequest body) {
        if (body == null) throw new IllegalArgumentException("A testing checklist is required.");
        return ResponseEntity.ok(service.createPlan(caller(req), id, required(body.title(), "A title"), body.description(), body.kind()));
    }

    @GetMapping("/test-plans/{planId:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int planId) {
        return ResponseEntity.ok(service.plan(caller(req), planId));
    }

    @PutMapping("/test-plans/{planId:\\d+}")
    public ResponseEntity<?> update(HttpServletRequest req, @PathVariable int planId, @RequestBody PlanRequest body) {
        if (body == null) throw new IllegalArgumentException("A testing checklist is required.");
        return ResponseEntity.ok(service.updatePlan(caller(req), planId, body.title(), body.description(), body.sortOrder()));
    }

    @DeleteMapping("/test-plans/{planId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int planId) {
        service.deletePlan(caller(req), planId);
        return ResponseEntity.ok(ok("deletedId", planId));
    }

    // ---- columns ------------------------------------------------------------------

    @PostMapping("/test-plans/{planId:\\d+}/columns")
    public ResponseEntity<?> addColumn(HttpServletRequest req, @PathVariable int planId, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.addLevel(caller(req), planId, required(body == null ? null : body.title(), "A column name")));
    }

    @PutMapping("/test-plans/columns/{columnId:\\d+}")
    public ResponseEntity<?> updateColumn(HttpServletRequest req, @PathVariable int columnId, @RequestBody TitleRequest body) {
        if (body == null) throw new IllegalArgumentException("A column is required.");
        return ResponseEntity.ok(service.updateLevel(caller(req), columnId, body.title(), body.sortOrder()));
    }

    @DeleteMapping("/test-plans/columns/{columnId:\\d+}")
    public ResponseEntity<?> deleteColumn(HttpServletRequest req, @PathVariable int columnId) {
        return ResponseEntity.ok(service.deleteLevel(caller(req), columnId));
    }

    // ---- groups -------------------------------------------------------------------

    @PostMapping("/test-plans/{planId:\\d+}/groups")
    public ResponseEntity<?> addGroup(HttpServletRequest req, @PathVariable int planId, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.addGroup(caller(req), planId, required(body == null ? null : body.title(), "A group name")));
    }

    @PutMapping("/test-plans/groups/{groupId:\\d+}")
    public ResponseEntity<?> updateGroup(HttpServletRequest req, @PathVariable int groupId, @RequestBody TitleRequest body) {
        if (body == null) throw new IllegalArgumentException("A group is required.");
        return ResponseEntity.ok(service.updateGroup(caller(req), groupId, body.title(), body.sortOrder()));
    }

    @DeleteMapping("/test-plans/groups/{groupId:\\d+}")
    public ResponseEntity<?> deleteGroup(HttpServletRequest req, @PathVariable int groupId) {
        return ResponseEntity.ok(service.deleteGroup(caller(req), groupId));
    }

    // ---- cases --------------------------------------------------------------------

    @PostMapping("/test-plans/groups/{groupId:\\d+}/cases")
    public ResponseEntity<?> addCase(HttpServletRequest req, @PathVariable int groupId, @RequestBody CaseRequest body) {
        if (body == null) throw new IllegalArgumentException("A test case is required.");
        return ResponseEntity.ok(service.addCase(caller(req), groupId, required(body.text(), "Some text"), body.note()));
    }

    @PutMapping("/test-plans/cases/{caseId:\\d+}")
    public ResponseEntity<?> updateCase(HttpServletRequest req, @PathVariable int caseId, @RequestBody CaseRequest body) {
        if (body == null) throw new IllegalArgumentException("A test case is required.");
        return ResponseEntity.ok(service.updateCase(caller(req), caseId, body.groupId(), body.text(), body.note(), body.sortOrder(),
            body.includeInPrint()));
    }

    @DeleteMapping("/test-plans/cases/{caseId:\\d+}")
    public ResponseEntity<?> deleteCase(HttpServletRequest req, @PathVariable int caseId) {
        return ResponseEntity.ok(service.deleteCase(caller(req), caseId));
    }

    // ---- order (drag to reorder) -------------------------------------------------------

    @PutMapping("/test-plans/groups/{groupId:\\d+}/case-order")
    public ResponseEntity<?> caseOrder(HttpServletRequest req, @PathVariable int groupId, @RequestBody OrderRequest body) {
        if (body == null || body.ids() == null) throw new IllegalArgumentException("ids (the new order) is required.");
        return ResponseEntity.ok(service.reorderCases(caller(req), groupId, body.ids()));
    }

    @PutMapping("/test-plans/{planId:\\d+}/group-order")
    public ResponseEntity<?> groupOrder(HttpServletRequest req, @PathVariable int planId, @RequestBody OrderRequest body) {
        if (body == null || body.ids() == null) throw new IllegalArgumentException("ids (the new order) is required.");
        return ResponseEntity.ok(service.reorderGroups(caller(req), planId, body.ids()));
    }

    // ---- ticks --------------------------------------------------------------------

    @PutMapping("/test-plans/cases/{caseId:\\d+}/checks/{columnId:\\d+}")
    public ResponseEntity<?> check(HttpServletRequest req, @PathVariable int caseId, @PathVariable int columnId,
                                   @RequestBody CheckRequest body) {
        if (body == null) throw new IllegalArgumentException("checked (true or false) or skipped (true or false) is required.");
        return ResponseEntity.ok(service.setCheck(caller(req), caseId, columnId, body.checked(), body.skipped()));
    }

    @PutMapping("/test-plans/cases/{caseId:\\d+}/result")
    public ResponseEntity<?> result(HttpServletRequest req, @PathVariable int caseId, @RequestBody ResultRequest body) {
        if (body == null) throw new IllegalArgumentException("failing (true or false) or note is required.");
        return ResponseEntity.ok(service.setResult(caller(req), caseId, body.failing(), body.note()));
    }

    @PostMapping("/test-plans/{planId:\\d+}/clear")
    public ResponseEntity<?> clear(HttpServletRequest req, @PathVariable int planId, @RequestBody(required = false) ClearRequest body) {
        return ResponseEntity.ok(service.clear(caller(req), planId, body == null ? null : body.columnId()));
    }
}
