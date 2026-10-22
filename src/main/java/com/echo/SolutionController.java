package com.echo;

import com.echo.SolutionDrafts.ProjectDraft;
import com.echo.SolutionDrafts.SolutionDraft;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Solutions and their projects (Story 3493). Base {@code /api/solutions}.
 *
 *   GET    /api/solutions/whoami                     who the server thinks is calling
 *   GET    /api/solutions/lookups                    statuses, members, log types, note types
 *   GET    /api/solutions                            the solutions the caller may see
 *   POST   /api/solutions                            create (a person only)
 *   GET    /api/solutions/{id}
 *   PUT    /api/solutions/{id}
 *   DELETE /api/solutions/{id}                       (a person only)
 *   GET    /api/solutions/{id}/projects
 *   POST   /api/solutions/{id}/projects
 *   GET    /api/solutions/projects/{projectId}
 *   PUT    /api/solutions/projects/{projectId}
 *   DELETE /api/solutions/projects/{projectId}       (a person only)
 *   POST   /api/solutions/members                    add or edit a member (a person only)
 *
 * Statuses and people travel as CODES. The whole contract, written for an agent
 * to read, is {@code GET /api/solutions/spec}.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionController extends SolutionControllerSupport {

    private final SolutionService service;

    public SolutionController(SolutionService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/whoami")
    public ResponseEntity<?> whoAmI(HttpServletRequest req) {
        SolutionCaller c = caller(req);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", c.kind().name().toLowerCase());
        body.put("memberCode", c.memberCode());
        body.put("scoped", c.isClient());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/lookups")
    public ResponseEntity<?> lookups() { return ResponseEntity.ok(service.lookups()); }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest req,
                                  @RequestParam(name = "includeArchived", required = false) Boolean includeArchived) {
        return ResponseEntity.ok(service.list(caller(req), orFalse(includeArchived)));
    }

    /** A solution by the short handle its owner gave it. Hidden from a caller who may not see it. */
    @GetMapping("/by-alias/{alias:[A-Za-z0-9-]+}")
    public ResponseEntity<?> byAlias(HttpServletRequest req, @PathVariable String alias) {
        return ResponseEntity.ok(service.byAlias(caller(req), alias));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.get(caller(req), id));
    }

    public record SolutionRequest(String name, String description, String statusCode, String ownerCode,
                                  String statusSummary, String targetDate, Boolean archived, Integer sortOrder, String alias) {}

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest req, @RequestBody SolutionRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(0, body)));
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> update(HttpServletRequest req, @PathVariable int id, @RequestBody SolutionRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(id, body)));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int id) {
        service.delete(caller(req), id);
        return ResponseEntity.ok(ok("deletedId", id));
    }

    // ---- projects ---------------------------------------------------------

    @GetMapping("/{id:\\d+}/projects")
    public ResponseEntity<?> projects(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.projects(caller(req), id));
    }

    public record ProjectRequest(String name, String description, String statusCode, String ownerCode, Integer sortOrder) {}

    @PostMapping("/{id:\\d+}/projects")
    public ResponseEntity<?> createProject(HttpServletRequest req, @PathVariable int id, @RequestBody ProjectRequest body) {
        return ResponseEntity.ok(service.saveProject(caller(req), toDraft(0, id, body)));
    }

    @GetMapping("/projects/{projectId:\\d+}")
    public ResponseEntity<?> project(HttpServletRequest req, @PathVariable int projectId) {
        return ResponseEntity.ok(service.project(caller(req), projectId));
    }

    @PutMapping("/projects/{projectId:\\d+}")
    public ResponseEntity<?> updateProject(HttpServletRequest req, @PathVariable int projectId, @RequestBody ProjectRequest body) {
        return ResponseEntity.ok(service.saveProject(caller(req), toDraft(projectId, 0, body)));
    }

    @DeleteMapping("/projects/{projectId:\\d+}")
    public ResponseEntity<?> deleteProject(HttpServletRequest req, @PathVariable int projectId) {
        service.deleteProject(caller(req), projectId);
        return ResponseEntity.ok(ok("deletedId", projectId));
    }

    // ---- members ----------------------------------------------------------

    public record MemberRequest(Integer id, String code, String name, Boolean agent, Boolean active, Integer sortOrder) {}

    @PostMapping("/members")
    public ResponseEntity<?> saveMember(HttpServletRequest req, @RequestBody MemberRequest body) {
        if (body == null) throw new IllegalArgumentException("A member is required.");
        return ResponseEntity.ok(service.saveMember(caller(req), orZero(body.id()), required(body.code(), "A code"),
            required(body.name(), "A name"), orFalse(body.agent()), body.active() == null || body.active(), orZero(body.sortOrder())));
    }

    // ---- mapping ----------------------------------------------------------

    private static SolutionDraft toDraft(int id, SolutionRequest b) {
        if (b == null) throw new IllegalArgumentException("A solution is required.");
        return new SolutionDraft(id, id > 0 ? b.name() : required(b.name(), "A name"), b.description(), b.statusCode(), b.ownerCode(),
            b.statusSummary(), date(b.targetDate(), "The target date"), b.archived(), orZero(b.sortOrder()), b.alias());
    }

    private static ProjectDraft toDraft(int id, int solutionId, ProjectRequest b) {
        if (b == null) throw new IllegalArgumentException("A project is required.");
        return new ProjectDraft(id, solutionId, id > 0 ? b.name() : required(b.name(), "A name"), b.description(), b.statusCode(),
            b.ownerCode(), orZero(b.sortOrder()));
    }
}
