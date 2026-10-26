package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/lookups")
public class LookupController {

    private final LookupService service;

    public LookupController(LookupService service) {
        this.service = service;
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> get(@PathVariable String name) {
        String proc = service.resolveProc(name);
        if (proc == null) return ResponseEntity.status(404).body(Map.of("error", "unknown lookup: " + name));
        return ResponseEntity.ok(service.get(proc));
    }

    /** Body for {@link #add}: the typed type name. */
    public record AddRequest(String name) {}

    /**
     * Add-in-place (Story 3550): find-or-create a type by name in a simple type
     * list -- the green "+ Add" row in a record dialog's Type pick-list.
     * 200 {id, name, created}; 400 {error} for a blank / too-long name or a
     * list that does not take in-place adds.
     */
    @PostMapping("/{name}")
    public ResponseEntity<?> add(@PathVariable String name, @RequestBody(required = false) AddRequest req) {
        if (service.resolveProc(name) == null) return ResponseEntity.status(404).body(Map.of("error", "unknown lookup: " + name));
        try {
            return ResponseEntity.ok(service.findOrCreate(name, req == null ? null : req.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
