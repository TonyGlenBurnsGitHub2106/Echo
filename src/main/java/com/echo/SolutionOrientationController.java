package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Where am I" in a solution (Story 5695, Echo first).
 *
 *   GET /api/solutions/{id}/summary-age   when "Where things stand" was last written: {writtenDate, ageDays, stale}
 *   GET /api/solutions/{id}/lens[?bucket=now|next|waiting&offset=&pageSize=]   the open items, Now / Next / Waiting
 *   GET /api/solutions/{id}/left-off      the caller's note for next time: {note, writtenDate}
 *   PUT /api/solutions/{id}/left-off      {note} -- write it; blank takes it away
 *   GET /api/solutions/{id}/activity[?days=&who=&kind=&topicId=&offset=&pageSize=]   everyone's changes, newest first
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

    public record LeftOffRequest(String note) {}

    @GetMapping("/{id:\\d+}/lens")
    public ResponseEntity<?> lens(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "bucket",   required = false) String bucket,
                                  @RequestParam(name = "offset",   required = false) Integer offset,
                                  @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.lens(caller(req), id, bucket, orZero(offset), orZero(pageSize)));
    }

    @GetMapping("/{id:\\d+}/left-off")
    public ResponseEntity<?> leftOff(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.leftOff(caller(req), id));
    }

    @PutMapping("/{id:\\d+}/left-off")
    public ResponseEntity<?> saveLeftOff(HttpServletRequest req, @PathVariable int id, @RequestBody LeftOffRequest body) {
        return ResponseEntity.ok(service.saveLeftOff(caller(req), id, body == null ? null : body.note()));
    }

    @GetMapping("/{id:\\d+}/activity")
    public ResponseEntity<?> activity(HttpServletRequest req, @PathVariable int id,
                                      @RequestParam(name = "days",     required = false) Integer days,
                                      @RequestParam(name = "who",      required = false) String who,
                                      @RequestParam(name = "kind",     required = false) String kind,
                                      @RequestParam(name = "topicId",  required = false) Integer topicId,
                                      @RequestParam(name = "offset",   required = false) Integer offset,
                                      @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.feed(caller(req), id, days, who, kind, topicId, orZero(offset), orZero(pageSize)));
    }
}
