package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves a WebAddressID -> the WebAddress's own tblEntity row -> the LoginSetup
 * rows linked to that entity. Powers the "Copy Password" action on each Web Address
 * card on the contact / vendor / UDE detail page, mirroring the desktop's
 * cLoginPasswordCopy helper. Endpoint only exposes active rows (ThroughDate empty
 * or in the future).
 */
@RestController
@RequestMapping("/api/web-addresses/{webAddressId}/logins")
public class WebAddressLoginController {

    private final JdbcTemplate jdbc;
    private final EntityLoginService entityLoginService;

    public WebAddressLoginController(JdbcTemplate jdbc, EntityLoginService entityLoginService) {
        this.jdbc = jdbc;
        this.entityLoginService = entityLoginService;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable int webAddressId) {
        if (webAddressId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid webAddressId"));
        }
        Integer entityId = lookupWebAddressEntityId(webAddressId);
        if (entityId == null || entityId <= 0) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<Map<String, Object>> all = entityLoginService.listForEntity(entityId);
        // Active only: through date is null OR >= today. The list-for-entity method does
        // not filter, so do it here to keep the desktop / web behaviours consistent.
        java.time.LocalDate today = java.time.LocalDate.now();
        all.removeIf(row -> {
            Object t = row.get("throughDate");
            if (t == null) return false;
            String s = t.toString();
            if (s.isEmpty()) return false;
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(s.substring(0, Math.min(10, s.length())));
                return d.isBefore(today);
            } catch (Exception e) {
                return false;
            }
        });
        return ResponseEntity.ok(all);
    }

    private Integer lookupWebAddressEntityId(int webAddressId) {
        try {
            return jdbc.queryForObject(
                "SELECT EntityID FROM dbo.tblWebAddress WHERE WebAddressID = ?",
                Integer.class, webAddressId);
        } catch (Exception e) {
            return null;
        }
    }
}
