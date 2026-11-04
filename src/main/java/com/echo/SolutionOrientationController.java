package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Where am I" in a solution (Story 5695, Echo first).
 *
 *   GET /api/solutions/{id}/summary-age   when "Where things stand" was last written: {writtenDate, ageDays, stale}
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionOrientationController extends SolutionControllerSupport {

    private final SolutionOrientationService service;

    public SolutionOrientationController(SolutionOrientationService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/summary-age")
    public ResponseEntity<?> summaryAge(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.summaryAge(caller(req), id));
    }
}
