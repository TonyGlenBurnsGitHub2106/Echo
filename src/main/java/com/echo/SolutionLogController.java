package com.echo;

import com.echo.SolutionDrafts.LogDraft;
import com.echo.SolutionDrafts.LogFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * A solution's journal (Story 3493) -- "how did we do it".
 *
 *   GET    /api/solutions/{id}/log     one page; filters projectId, itemId, author, type, since, search;
 *                                      ascending=true reads it in the order things were done
 *   POST   /api/solutions/{id}/log     append an entry -- it takes the next sequence number
 *   GET    /api/solutions/log/{logId}  one entry WITH its full text
 *   PUT    /api/solutions/log/{logId}  correct an entry (its author, or a person)
 *   DELETE /api/solutions/log/{logId}  (a person only)
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionLogController extends SolutionControllerSupport {

    private final SolutionLogService service;

    public SolutionLogController(SolutionLogService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/log")
    public ResponseEntity<?> page(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "projectId", required = false) Integer projectId,
                                  @RequestParam(name = "itemId",    required = false) Integer itemId,
                                  @RequestParam(name = "author",    required = false) String author,
                                  @RequestParam(name = "type",      required = false) String type,
                                  @RequestParam(name = "since",     required = false) Integer since,
                                  @RequestParam(name = "search",    required = false) String search,
                                  @RequestParam(name = "ascending", required = false) Boolean ascending,
                                  @RequestParam(name = "offset",    required = false) Integer offset,
                                  @RequestParam(name = "pageSize",  required = false) Integer pageSize) {
        LogFilter filter = new LogFilter(id, projectId, itemId, author, type, since, search, orFalse(ascending),
            orZero(offset), orZero(pageSize));
        return ResponseEntity.ok(service.page(caller(req), filter));
    }

    @GetMapping("/log/{logId:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int logId) {
        return ResponseEntity.ok(service.get(caller(req), logId));
    }

    public record LogRequest(Integer projectId, Integer itemId, String typeCode, String typeName, String text, String logDate) {}

    @PostMapping("/{id:\\d+}/log")
    public ResponseEntity<?> append(HttpServletRequest req, @PathVariable int id, @RequestBody LogRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(0, id, body), body.typeName()));
    }

    @PutMapping("/log/{logId:\\d+}")
    public ResponseEntity<?> correct(HttpServletRequest req, @PathVariable int logId, @RequestBody LogRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(logId, 0, body), body.typeName()));
    }

    @DeleteMapping("/log/{logId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int logId) {
        service.delete(caller(req), logId);
        return ResponseEntity.ok(ok("deletedId", logId));
    }

    private static LogDraft toDraft(int id, int solutionId, LogRequest b) {
        if (b == null) throw new IllegalArgumentException("A log entry is required.");
        return new LogDraft(id, solutionId, b.projectId(), b.itemId(), b.typeCode(), id > 0 ? b.text() : required(b.text(), "Some text"),
            date(b.logDate(), "The log date"));
    }
}
