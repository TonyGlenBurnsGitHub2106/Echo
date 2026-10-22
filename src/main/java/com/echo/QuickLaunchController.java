package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for Quick Launch pins. Behind the standard /api/** auth gate.
 */
@RestController
@RequestMapping("/api/quick-launch")
public class QuickLaunchController {

    private final QuickLaunchService service;

    public QuickLaunchController(QuickLaunchService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SaveRequest req) {
        if (req == null || req.label == null || req.label.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "label is required"));
        }
        if (req.url == null || req.url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        int id = service.save(0, req.label, req.url, req.iconKey);
        return ResponseEntity.ok(Map.of("quickLaunchId", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody SaveRequest req) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        if (req == null || req.label == null || req.label.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "label is required"));
        }
        if (req.url == null || req.url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        service.save(id, req.label, req.url, req.iconKey);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        service.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PutMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody ReorderRequest req) {
        if (req == null || req.orderedIds == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderedIds is required"));
        }
        service.reorder(req.orderedIds);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public static class SaveRequest {
        public String label;
        public String url;
        public String iconKey;
    }

    public static class ReorderRequest {
        public List<Integer> orderedIds;
    }
}
