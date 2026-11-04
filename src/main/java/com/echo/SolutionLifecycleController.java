package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The start and the end of a solution (Story 5695, Echo first, Release 6).
 *
 *   GET  /api/solutions/shapes             the shapes a new solution can start from
 *   POST /api/solutions/{id}/shape         {shape, fromSolutionId} -- a new, empty solution takes a shape
 *   GET  /api/solutions/{id}/close-out     everything still open, by group, with counts
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionLifecycleController extends SolutionControllerSupport {

    private final SolutionLifecycleService service;

    public SolutionLifecycleController(SolutionLifecycleService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record ShapeRequest(String shape, Integer fromSolutionId) {}

    @GetMapping("/shapes")
    public ResponseEntity<?> shapes(HttpServletRequest req) {
        caller(req);
        return ResponseEntity.ok(service.shapes());
    }

    @PostMapping("/{id:\\d+}/shape")
    public ResponseEntity<?> applyShape(HttpServletRequest req, @PathVariable int id, @RequestBody ShapeRequest body) {
        if (body == null) throw new IllegalArgumentException("Say which shape.");
        return ResponseEntity.ok(service.applyShape(caller(req), id, body.shape(), body.fromSolutionId()));
    }

    @GetMapping("/{id:\\d+}/close-out")
    public ResponseEntity<?> closeOut(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.closeOut(caller(req), id));
    }
}
