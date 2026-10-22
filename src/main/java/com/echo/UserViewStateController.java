package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * /api/view-state -- remembered view state shared by every device the user signs
 * in from. picasso-view-state.js is the caller.
 *
 *   GET /api/view-state?prefix=leo.cl.               -> {values: {key: value, ...}}
 *   PUT /api/view-state {values: {key: value, ...}}  -> {saved: n}
 */
@RestController
@RequestMapping("/api/view-state")
public class UserViewStateController {

    private final UserViewStateService service;

    public UserViewStateController(UserViewStateService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(name = "prefix", required = false) String prefix) {
        try {
            return ResponseEntity.ok(Map.of("values", service.list(prefix)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Object values = body == null ? null : body.get("values");
            if (!(values instanceof Map<?, ?>)) throw new IllegalArgumentException("values is required");
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) values;
            return ResponseEntity.ok(Map.of("saved", service.save(typed)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
