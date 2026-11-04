package com.echo;

import com.echo.SolutionLinkModels.Topic;
import com.echo.SolutionLinkModels.TopicDraft;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Solution links, topics and "Since you were last here" (2026-11-02).
 *
 *   GET    /api/solutions/{id}/links?kind=&recordId=[&offset=&pageSize=]  everything linked to one record
 *   POST   /api/solutions/{id}/links            {fromKind, fromId, toKind, toId}  join two records
 *   DELETE /api/solutions/{id}/links/{linkId}   take one link away (neither record is touched)
 *   GET    /api/solutions/{id}/link-targets?q=&kind=&recordId=   what the Link picker offers
 *
 *   GET    /api/solutions/{id}/topics[?search=&offset=&pageSize=]    one page of topics, by name
 *   POST   /api/solutions/{id}/topics           {name, summary}
 *   GET    /api/solutions/topics/{topicId}      one topic, whole (its records: GET .../links?kind=topic&recordId=)
 *   GET    /api/solutions/topics/{topicId}/locate
 *   PUT    /api/solutions/topics/{topicId}      {name, summary} -- a merge: a missing field is kept
 *   DELETE /api/solutions/topics/{topicId}      the topic and its links; the records stay
 *
 *   POST   /api/solutions/{id}/visit            "I have it open": counts the visit, answers what changed
 *   GET    /api/solutions/{id}/since[?offset=&pageSize=]   what others changed since my last visit
 *   POST   /api/solutions/{id}/caught-up        "since" starts now
 *
 * Kinds a link joins: item, note, log, file, term, topic.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionLinkController extends SolutionControllerSupport {

    private final SolutionLinkService service;

    public SolutionLinkController(SolutionLinkService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record LinkRequest(String fromKind, Integer fromId, String toKind, Integer toId) {}

    public record TopicRequest(String name, String summary) {}

    // ---- links ----

    @GetMapping("/{id:\\d+}/links")
    public ResponseEntity<?> links(HttpServletRequest req, @PathVariable int id,
                                   @RequestParam(name = "kind") String kind,
                                   @RequestParam(name = "recordId") int recordId,
                                   @RequestParam(name = "offset",   required = false) Integer offset,
                                   @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.links(caller(req), id, kind, recordId, orZero(offset), orZero(pageSize)));
    }

    @PostMapping("/{id:\\d+}/links")
    public ResponseEntity<?> link(HttpServletRequest req, @PathVariable int id, @RequestBody LinkRequest body) {
        if (body == null || body.fromId() == null || body.toId() == null)
            throw new IllegalArgumentException("fromKind, fromId, toKind and toId are required.");
        return ResponseEntity.ok(service.link(caller(req), id, required(body.fromKind(), "fromKind"), body.fromId(),
            required(body.toKind(), "toKind"), body.toId()));
    }

    @DeleteMapping("/{id:\\d+}/links/{linkId:\\d+}")
    public ResponseEntity<?> unlink(HttpServletRequest req, @PathVariable int id, @PathVariable int linkId) {
        service.unlink(caller(req), id, linkId);
        return ResponseEntity.ok(ok("deletedId", linkId));
    }

    @GetMapping("/{id:\\d+}/link-targets")
    public ResponseEntity<?> targets(HttpServletRequest req, @PathVariable int id,
                                     @RequestParam(name = "q",        required = false) String q,
                                     @RequestParam(name = "kind",     required = false) String kind,
                                     @RequestParam(name = "recordId", required = false) Integer recordId,
                                     @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.targets(caller(req), id, q, kind, recordId, orZero(pageSize)));
    }

    // ---- topics ----

    @GetMapping("/{id:\\d+}/topics")
    public ResponseEntity<?> topics(HttpServletRequest req, @PathVariable int id,
                                    @RequestParam(name = "search",   required = false) String search,
                                    @RequestParam(name = "offset",   required = false) Integer offset,
                                    @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.topics(caller(req), id, search, orZero(offset), orZero(pageSize)));
    }

    @PostMapping("/{id:\\d+}/topics")
    public ResponseEntity<?> addTopic(HttpServletRequest req, @PathVariable int id, @RequestBody TopicRequest body) {
        String name = required(body == null ? null : body.name(), "A topic name");
        return ResponseEntity.ok(service.saveTopic(caller(req), new TopicDraft(0, id, name, body.summary())));
    }

    @GetMapping("/topics/{topicId:\\d+}")
    public ResponseEntity<?> topic(HttpServletRequest req, @PathVariable int topicId) {
        return ResponseEntity.ok(service.topic(caller(req), topicId));
    }

    @GetMapping("/topics/{topicId:\\d+}/locate")
    public ResponseEntity<?> locateTopic(HttpServletRequest req, @PathVariable int topicId) {
        Topic t = service.topic(caller(req), topicId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topicId", t.id());
        body.put("solutionId", t.solutionId());
        return ResponseEntity.ok(body);
    }

    @PutMapping("/topics/{topicId:\\d+}")
    public ResponseEntity<?> updateTopic(HttpServletRequest req, @PathVariable int topicId, @RequestBody TopicRequest body) {
        if (body == null) throw new IllegalArgumentException("A topic is required.");
        return ResponseEntity.ok(service.saveTopic(caller(req), new TopicDraft(topicId, 0, body.name(), body.summary())));
    }

    @DeleteMapping("/topics/{topicId:\\d+}")
    public ResponseEntity<?> deleteTopic(HttpServletRequest req, @PathVariable int topicId) {
        service.deleteTopic(caller(req), topicId);
        return ResponseEntity.ok(ok("deletedId", topicId));
    }

    // ---- since you were last here ----

    @PostMapping("/{id:\\d+}/visit")
    public ResponseEntity<?> visit(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.visit(caller(req), id));
    }

    @GetMapping("/{id:\\d+}/since")
    public ResponseEntity<?> since(HttpServletRequest req, @PathVariable int id,
                                   @RequestParam(name = "offset",   required = false) Integer offset,
                                   @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.since(caller(req), id, orZero(offset), orZero(pageSize)));
    }

    @PostMapping("/{id:\\d+}/caught-up")
    public ResponseEntity<?> caughtUp(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.caughtUp(caller(req), id));
    }
}
