package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sharing a solution and choosing where its files are saved (Story 3493). Both
 * are the OWNER'S: every call here answers 403 to an API client (and 404 first
 * if the solution is not one it was given, so nothing is confirmed to exist).
 *
 *   GET    /api/solutions/{id}/shares                 every API client, marked shared or not
 *   POST   /api/solutions/{id}/shares                 {apiClientId} -- share; forces files off the repo
 *   DELETE /api/solutions/{id}/shares/{apiClientId}   stop sharing
 *   GET    /api/solutions/{id}/shares/{apiClientId}/brief   the hand-off text for that client (no key in it)
 *   PUT    /api/solutions/{id}/file-storage           {saveToRepo} -- refused (409) while shared
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionShareController extends SolutionControllerSupport {

    private final SolutionShareService shares;
    private final SolutionService      solutions;

    public SolutionShareController(SolutionShareService shares, SolutionService solutions, SolutionCallerResolver callers) {
        super(callers);
        this.shares = shares; this.solutions = solutions;
    }

    @GetMapping("/{id:\\d+}/shares")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(shares.shares(caller(req), id));
    }

    public record ShareRequest(Integer apiClientId) {}

    @PostMapping("/{id:\\d+}/shares")
    public ResponseEntity<?> share(HttpServletRequest req, @PathVariable int id, @RequestBody ShareRequest body) {
        if (body == null || orZero(body.apiClientId()) <= 0) throw new IllegalArgumentException("An API client id is required.");
        return ResponseEntity.ok(shares.setShared(caller(req), id, body.apiClientId(), true));
    }

    @DeleteMapping("/{id:\\d+}/shares/{apiClientId:\\d+}")
    public ResponseEntity<?> unshare(HttpServletRequest req, @PathVariable int id, @PathVariable int apiClientId) {
        return ResponseEntity.ok(shares.setShared(caller(req), id, apiClientId, false));
    }

    @GetMapping("/{id:\\d+}/shares/{apiClientId:\\d+}/brief")
    public ResponseEntity<?> brief(HttpServletRequest req, @PathVariable int id, @PathVariable int apiClientId) {
        return ResponseEntity.ok(shares.brief(caller(req), id, apiClientId));
    }

    public record StorageRequest(Boolean saveToRepo) {}

    @PutMapping("/{id:\\d+}/file-storage")
    public ResponseEntity<?> fileStorage(HttpServletRequest req, @PathVariable int id, @RequestBody StorageRequest body) {
        if (body == null || body.saveToRepo() == null) throw new IllegalArgumentException("saveToRepo is required.");
        return ResponseEntity.ok(solutions.setSaveFilesToRepo(caller(req), id, body.saveToRepo()));
    }
}
