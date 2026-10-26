package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Website types (Story 3525) -- Business, Email, Blog ... -- as coloured, ordered,
 * find-or-create categories exactly like file categories (Story 3520) and note types
 * (Story 3524).
 *
 *   GET    /api/web-site-types                 every type, in order, with colour and use count
 *   POST   /api/web-site-types                 find-or-create by name  { name, color? }
 *   PUT    /api/web-site-types/{id}            rename / recolour       { name, color }
 *   DELETE /api/web-site-types/{id}?moveTo=    delete; its addresses move to moveTo (required while any use it)
 *   POST   /api/web-site-types/reorder         { ids: [..] } first shown first
 */
@RestController
@RequestMapping("/api/web-site-types")
public class WebSiteTypeController {

    private final WebTypeService service;

    public WebSiteTypeController(WebTypeService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.types());
    }

    @PostMapping
    public ResponseEntity<?> findOrCreate(@RequestBody(required = false) TypeRequest req) {
        if (req == null || req.name == null || req.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        try {
            Map<String, Object> t = service.findOrCreateType(req.name);
            if (req.color != null && !req.color.isBlank() && Boolean.TRUE.equals(t.get("created"))) {
                Map<String, Object> saved = service.saveType(((Number) t.get("id")).intValue(), (String) t.get("name"), req.color);
                saved.put("created", true);
                return ResponseEntity.ok(saved);
            }
            return ResponseEntity.ok(t);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody(required = false) TypeRequest req) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "missing body"));
        try {
            return ResponseEntity.ok(service.saveType(id, req.name, req.color));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id, @RequestParam(required = false) Integer moveTo) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        try {
            return ResponseEntity.ok(Map.of("deleted", true, "addressesMoved", service.deleteType(id, moveTo)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody(required = false) ReorderRequest req) {
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids is required"));
        }
        return ResponseEntity.ok(service.reorderTypes(req.ids));
    }

    public static class TypeRequest {
        public String name;
        public String color;
    }

    public static class ReorderRequest {
        public List<Integer> ids;
    }
}
