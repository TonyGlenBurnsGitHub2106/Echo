package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Changing many items at once, and making one record into another (Story 5695, Echo first, Release 5). Under
 * /api/solutions, the door an agent a solution is shared with may use.
 *
 *   POST /api/solutions/{id}/items/bulk    {itemIds:[...], field, value, reason} -- one field on every item, one log entry
 *   POST /api/solutions/{id}/promote       {sourceKind, sourceId, targetKind, projectId, title} -- a new record from another
 *   GET  /api/solutions/{id}/provenance?kind=&recordId=   where a record came from and what was made from it
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionChangeController extends SolutionControllerSupport {

    private final SolutionChangeService service;

    public SolutionChangeController(SolutionChangeService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record BulkRequest(List<Integer> itemIds, String field, String value, String reason) {}

    public record PromoteRequest(String sourceKind, Integer sourceId, String targetKind, Integer projectId, String title) {}

    @PostMapping("/{id:\\d+}/items/bulk")
    public ResponseEntity<?> bulk(HttpServletRequest req, @PathVariable int id, @RequestBody BulkRequest body) {
        if (body == null) throw new IllegalArgumentException("Nothing to change.");
        return ResponseEntity.ok(service.bulkChange(caller(req), id, body.itemIds(), body.field(), body.value(), body.reason()));
    }

    @PostMapping("/{id:\\d+}/promote")
    public ResponseEntity<?> promote(HttpServletRequest req, @PathVariable int id, @RequestBody PromoteRequest body) {
        if (body == null) throw new IllegalArgumentException("Say what to make into what.");
        return ResponseEntity.ok(service.promote(caller(req), id, body.sourceKind(), body.sourceId(), body.targetKind(),
            body.projectId(), body.title()));
    }

    @GetMapping("/{id:\\d+}/provenance")
    public ResponseEntity<?> provenance(HttpServletRequest req, @PathVariable int id,
                                        @RequestParam(name = "kind",     required = false) String kind,
                                        @RequestParam(name = "recordId", required = false) Integer recordId) {
        return ResponseEntity.ok(service.provenance(caller(req), id, kind, recordId));
    }
}
