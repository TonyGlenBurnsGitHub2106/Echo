package com.echo;

import com.echo.SolutionDrafts.ItemDraft;
import com.echo.SolutionModels.Item;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A solution's action items (Story 3493).
 *
 *   GET    /api/solutions/{id}/items[?projectId=]        every item, flat, each carrying parentItemId
 *   POST   /api/solutions/projects/{projectId}/items     add an item (parentItemId makes it a sub-item)
 *   GET    /api/solutions/items/{itemId}                 one item WITH its full detail
 *   PUT    /api/solutions/items/{itemId}
 *   POST   /api/solutions/items/{itemId}/status          {statusCode}
 *   POST   /api/solutions/items/{itemId}/move            {projectId, parentItemId, sortOrder}
 *   GET    /api/solutions/items/{itemId}/locate          which solution and project it lives in
 *   DELETE /api/solutions/items/{itemId}                 (a person only; takes the whole subtree)
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionItemController extends SolutionControllerSupport {

    private final SolutionItemService service;

    public SolutionItemController(SolutionItemService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/items")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "projectId", required = false) Integer projectId) {
        return ResponseEntity.ok(service.list(caller(req), id, projectId));
    }

    @GetMapping("/items/{itemId:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int itemId) {
        return ResponseEntity.ok(service.get(caller(req), itemId));
    }

    @GetMapping("/items/{itemId:\\d+}/locate")
    public ResponseEntity<?> locate(HttpServletRequest req, @PathVariable int itemId) {
        Item i = service.get(caller(req), itemId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", i.id());
        body.put("projectId", i.projectId());
        body.put("solutionId", i.solutionId());
        return ResponseEntity.ok(body);
    }

    public record ItemRequest(Integer parentItemId, String title, String detail, String statusCode,
                              String assigneeCode, Integer priority, Integer sortOrder, String dueDate) {}

    @PostMapping("/projects/{projectId:\\d+}/items")
    public ResponseEntity<?> create(HttpServletRequest req, @PathVariable int projectId, @RequestBody ItemRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(0, projectId, body)));
    }

    @PutMapping("/items/{itemId:\\d+}")
    public ResponseEntity<?> update(HttpServletRequest req, @PathVariable int itemId, @RequestBody ItemRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(itemId, 0, body)));
    }

    public record StatusRequest(String statusCode) {}

    @PostMapping("/items/{itemId:\\d+}/status")
    public ResponseEntity<?> setStatus(HttpServletRequest req, @PathVariable int itemId, @RequestBody StatusRequest body) {
        String code = required(body == null ? null : body.statusCode(), "A status code");
        return ResponseEntity.ok(service.setStatus(caller(req), itemId, code));
    }

    public record MoveRequest(Integer projectId, Integer parentItemId, Integer sortOrder) {}

    @PostMapping("/items/{itemId:\\d+}/move")
    public ResponseEntity<?> move(HttpServletRequest req, @PathVariable int itemId, @RequestBody MoveRequest body) {
        if (body == null) throw new IllegalArgumentException("A destination is required.");
        return ResponseEntity.ok(service.move(caller(req), itemId, body.projectId(), body.parentItemId(), orZero(body.sortOrder())));
    }

    @DeleteMapping("/items/{itemId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int itemId) {
        int removed = service.delete(caller(req), itemId);
        Map<String, Object> body = ok("deletedId", itemId);
        body.put("itemsRemoved", removed);
        return ResponseEntity.ok(body);
    }

    private static ItemDraft toDraft(int id, int projectId, ItemRequest b) {
        if (b == null) throw new IllegalArgumentException("An item is required.");
        return new ItemDraft(id, projectId, b.parentItemId(), id > 0 ? b.title() : required(b.title(), "A title"), b.detail(), b.statusCode(),
            b.assigneeCode(), orZero(b.priority()), orZero(b.sortOrder()), date(b.dueDate(), "The due date"));
    }
}
