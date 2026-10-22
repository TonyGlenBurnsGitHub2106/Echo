package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * File categories (Story 3520) -- the shared lookup the Files tab files documents under
 * (Tax, Collections, Receipts, ...). Every record uses the same list.
 *
 *   GET    /api/file-categories              every category, in order, with its use count
 *   POST   /api/file-categories              find-or-create by name  { name }
 *   PUT    /api/file-categories/{id}         rename / recolor / move { name, color, sortOrder }
 *   DELETE /api/file-categories/{id}         delete; its documents go back to staging
 *   POST   /api/file-categories/reorder      { ids: [..] } first shown first
 */
@RestController
@RequestMapping("/api/file-categories")
public class FileCategoryController {

    private final FileFilingService service;

    public FileCategoryController(FileFilingService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.categories());
    }

    @PostMapping
    public ResponseEntity<?> findOrCreate(@RequestBody(required = false) CategoryRequest req) {
        if (req == null || req.name == null || req.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        try {
            Map<String, Object> cat = service.findOrCreateCategory(req.name);
            if (req.color != null && !req.color.isBlank() && Boolean.TRUE.equals(cat.get("created"))) {
                int id = ((Number) cat.get("id")).intValue();
                Map<String, Object> saved = service.saveCategory(id, (String) cat.get("name"), req.color, null);
                saved.put("created", true);
                return ResponseEntity.ok(saved);
            }
            return ResponseEntity.ok(cat);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody(required = false) CategoryRequest req) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "missing body"));
        try {
            return ResponseEntity.ok(service.saveCategory(id, req.name, req.color, req.sortOrder));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        return ResponseEntity.ok(Map.of("deleted", true, "filesReturned", service.deleteCategory(id)));
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody(required = false) ReorderRequest req) {
        if (req == null || req.ids == null || req.ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids is required"));
        }
        return ResponseEntity.ok(service.reorderCategories(req.ids));
    }

    public static class CategoryRequest {
        public String  name;
        public String  color;
        public Integer sortOrder;
    }

    public static class ReorderRequest {
        public List<Integer> ids;
    }
}
