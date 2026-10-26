package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The organized Web tab on a record (Story 3525), grouped and sorted like the Files and
 * Notes tabs. Adding, editing, linking and removing a web address stay on
 * {@link EntityWebAddressController}; this only ORGANIZES them.
 *
 *   GET  /api/entities/{entityId}/web-addresses/filed     ?group=type|kind|none&sort=name|address|type|added&desc=&q=&offset=&size=
 *   GET  /api/entities/{entityId}/web-addresses/counts    ?group=&q=
 *   POST /api/entities/{entityId}/web-addresses/type      { xrefIds, typeId | typeName }
 *   PUT  /api/entities/{entityId}/web-addresses/{xrefId}/important   { important }
 *   POST /api/entities/{entityId}/web-addresses/reorder   { xrefIds }  -- Tony's order inside a group
 */
@RestController
@RequestMapping("/api/entities/{entityId}/web-addresses")
public class EntityWebFilingController {

    private final WebTypeService service;

    public EntityWebFilingController(WebTypeService service) { this.service = service; }

    @GetMapping("/filed")
    public ResponseEntity<?> filed(@PathVariable int entityId,
                                   @RequestParam(required = false) String group,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false, defaultValue = "false") boolean desc,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false, defaultValue = "0") int offset,
                                   @RequestParam(required = false, defaultValue = "100") int size) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.listForEntity(entityId, group, sort, desc, q, offset, size));
    }

    @GetMapping("/counts")
    public ResponseEntity<?> counts(@PathVariable int entityId,
                                    @RequestParam(required = false) String group,
                                    @RequestParam(required = false) String q) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.counts(entityId, group, q == null || q.isBlank() ? null : q.trim()));
    }

    @PostMapping("/type")
    public ResponseEntity<?> setType(@PathVariable int entityId, @RequestBody(required = false) TypeRequest req) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        if (req == null || req.xrefIds == null || req.xrefIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "xrefIds is required"));
        }
        try {
            int typeId = service.resolveType(req.typeId, req.typeName);
            if (typeId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "typeId or typeName is required"));
            return ResponseEntity.ok(Map.of("changed", service.setType(entityId, req.xrefIds, typeId), "typeId", typeId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{xrefId}/important")
    public ResponseEntity<?> important(@PathVariable int entityId, @PathVariable int xrefId,
                                       @RequestBody(required = false) ImportantRequest req) {
        if (entityId <= 0 || xrefId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        boolean on = req != null && Boolean.TRUE.equals(req.important);
        if (!service.setImportant(entityId, xrefId, on)) {
            return ResponseEntity.status(404).body(Map.of("error", "that web address is not on this record"));
        }
        return ResponseEntity.ok(Map.of("xrefId", xrefId, "important", on));
    }

    @PostMapping("/reorder")
    public ResponseEntity<?> reorder(@PathVariable int entityId, @RequestBody(required = false) ReorderRequest req) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        try {
            return ResponseEntity.ok(Map.of("changed", service.reorderOnEntity(entityId, req == null ? null : req.xrefIds)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    public static class ReorderRequest {
        public List<Integer> xrefIds;
    }

    public static class TypeRequest {
        public List<Integer> xrefIds;
        public Integer typeId;
        public String  typeName;
    }

    public static class ImportantRequest {
        public Boolean important;
    }
}
