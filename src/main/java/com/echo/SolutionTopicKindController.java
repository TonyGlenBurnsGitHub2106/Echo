package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Topic kinds (Story 5695, Echo first): the kinds of part a solution is made of, the order of its parts, and the
 * premise switch. Under /api/solutions, so an agent a solution is shared with may use it too.
 *
 *   GET    /api/solutions/{id}/topic-types              the kinds of part, in order, with how many parts each has
 *   POST   /api/solutions/{id}/topic-types              {name, figureLabel} -- add (or find by name)
 *   PUT    /api/solutions/topic-types/{typeId}          {name, figureLabel} -- rename / what its number counts
 *   DELETE /api/solutions/topic-types/{typeId}          its parts stay, with no kind of part
 *   PUT    /api/solutions/{id}/topics/order             {ids:[...]} -- the parts in their new order (Arrange)
 *   GET    /api/solutions/topics/{topicId}/open-items   the open items linked to a topic (what rests on an assumption)
 *   POST   /api/solutions/topics/{topicId}/proved-false {reason, itemIds:[...]} -- the premise switch
 *   GET    /api/solutions/topic-kinds                   the four kinds and the starter tags
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionTopicKindController extends SolutionControllerSupport {

    private final SolutionTopicKindService service;

    public SolutionTopicKindController(SolutionTopicKindService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record TypeRequest(String name, String figureLabel) {}

    public record OrderRequest(List<Integer> ids) {}

    public record ProvedFalseRequest(String reason, List<Integer> itemIds) {}

    @GetMapping("/topic-kinds")
    public ResponseEntity<?> kinds() {
        return ResponseEntity.ok(Map.of("kinds", List.of("subject", "tag", "part", "assumption"),
            "starterTags", SolutionTopicKindModels.STARTER_TAGS));
    }

    @GetMapping("/{id:\\d+}/topic-types")
    public ResponseEntity<?> types(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.types(caller(req), id));
    }

    @PostMapping("/{id:\\d+}/topic-types")
    public ResponseEntity<?> addType(HttpServletRequest req, @PathVariable int id, @RequestBody TypeRequest body) {
        String name = required(body == null ? null : body.name(), "A name for the kind of part");
        return ResponseEntity.ok(service.addType(caller(req), id, name, body.figureLabel()));
    }

    @PutMapping("/topic-types/{typeId:\\d+}")
    public ResponseEntity<?> updateType(HttpServletRequest req, @PathVariable int typeId, @RequestBody TypeRequest body) {
        if (body == null) throw new IllegalArgumentException("A name or a label is required.");
        return ResponseEntity.ok(service.updateType(caller(req), typeId, body.name(), body.figureLabel()));
    }

    @DeleteMapping("/topic-types/{typeId:\\d+}")
    public ResponseEntity<?> deleteType(HttpServletRequest req, @PathVariable int typeId) {
        service.deleteType(caller(req), typeId);
        return ResponseEntity.ok(ok("deletedId", typeId));
    }

    @PutMapping("/{id:\\d+}/topics/order")
    public ResponseEntity<?> reorder(HttpServletRequest req, @PathVariable int id, @RequestBody OrderRequest body) {
        service.reorder(caller(req), id, body == null ? null : body.ids());
        return ResponseEntity.ok(ok("solutionId", id));
    }

    @GetMapping("/topics/{topicId:\\d+}/open-items")
    public ResponseEntity<?> openItems(HttpServletRequest req, @PathVariable int topicId) {
        return ResponseEntity.ok(service.openItems(caller(req), topicId));
    }

    @PostMapping("/topics/{topicId:\\d+}/proved-false")
    public ResponseEntity<?> provedFalse(HttpServletRequest req, @PathVariable int topicId, @RequestBody ProvedFalseRequest body) {
        if (body == null) throw new IllegalArgumentException("Say why it no longer holds.");
        return ResponseEntity.ok(service.proveFalse(caller(req), topicId, body.reason(), body.itemIds()));
    }
}
