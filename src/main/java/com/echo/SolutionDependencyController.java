package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * What items wait on (Story 5695, Echo first). Under /api/solutions, so an agent a solution is shared with may
 * use it too.
 *
 *   GET    /api/solutions/items/{itemId}/waits          what the item waits on ('waitsOn') and what waits on it ('blocks')
 *   POST   /api/solutions/items/{itemId}/waits          {dependsOnItemId} -- the item now waits on that one
 *   DELETE /api/solutions/waits/{waitId}                the wait is taken away; both items stay
 *   GET    /api/solutions/{id}/waits                    every waiting item of the solution, with how many waits are open
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionDependencyController extends SolutionControllerSupport {

    private final SolutionDependencyService service;

    public SolutionDependencyController(SolutionDependencyService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record WaitRequest(Integer dependsOnItemId) {}

    @GetMapping("/items/{itemId:\\d+}/waits")
    public ResponseEntity<?> forItem(HttpServletRequest req, @PathVariable int itemId) {
        return ResponseEntity.ok(service.forItem(caller(req), itemId));
    }

    @PostMapping("/items/{itemId:\\d+}/waits")
    public ResponseEntity<?> add(HttpServletRequest req, @PathVariable int itemId, @RequestBody WaitRequest body) {
        if (body == null || body.dependsOnItemId() == null || body.dependsOnItemId() <= 0)
            throw new IllegalArgumentException("dependsOnItemId is required.");
        return ResponseEntity.ok(service.add(caller(req), itemId, body.dependsOnItemId()));
    }

    @DeleteMapping("/waits/{waitId:\\d+}")
    public ResponseEntity<?> remove(HttpServletRequest req, @PathVariable int waitId) {
        service.remove(caller(req), waitId);
        return ResponseEntity.ok(ok("deletedId", waitId));
    }

    @GetMapping("/{id:\\d+}/waits")
    public ResponseEntity<?> forSolution(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.forSolution(caller(req), id));
    }
}
