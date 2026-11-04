package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member's saved views of a solution's tab (Story 5695, Echo first, Release 5).
 *
 *   GET    /api/solutions/{id}/views[?scope=items]     the caller's views, in order
 *   POST   /api/solutions/{id}/views    {scope, name, filter}   save (same name replaces); answers with the views
 *   DELETE /api/solutions/views/{viewId}               one of the caller's own
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionSavedViewController extends SolutionControllerSupport {

    private final SolutionSavedViewService service;

    public SolutionSavedViewController(SolutionSavedViewService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record ViewRequest(String scope, String name, String filter) {}

    @GetMapping("/{id:\\d+}/views")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "scope", required = false) String scope) {
        return ResponseEntity.ok(service.list(caller(req), id, scope));
    }

    @PostMapping("/{id:\\d+}/views")
    public ResponseEntity<?> save(HttpServletRequest req, @PathVariable int id, @RequestBody ViewRequest body) {
        if (body == null) throw new IllegalArgumentException("Nothing to save.");
        return ResponseEntity.ok(service.save(caller(req), id, body.scope(), body.name(), body.filter()));
    }

    @DeleteMapping("/views/{viewId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int viewId) {
        service.delete(caller(req), viewId);
        return ResponseEntity.ok(ok("deleted", true));
    }
}
