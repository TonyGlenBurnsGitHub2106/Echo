package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/entities/{entityId}/logins")
public class EntityLoginController {

    private final EntityLoginService service;

    public EntityLoginController(EntityLoginService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable int entityId) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.listForEntity(entityId));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable int entityId,
                                     @RequestBody EntityLoginService.LoginSaveRequest req) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        if (req == null || req.loginName == null || req.loginName.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "loginName is required"));
        if (req.userId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        req.loginSetupId = 0;
        return ResponseEntity.ok(service.save(entityId, 0, req));
    }

    @PutMapping("/{xrefId}")
    public ResponseEntity<?> update(@PathVariable int entityId, @PathVariable int xrefId,
                                     @RequestBody EntityLoginService.LoginSaveRequest req) {
        if (entityId <= 0 || xrefId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        if (req == null || req.loginName == null || req.loginName.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "loginName is required"));
        if (req.userId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        return ResponseEntity.ok(service.save(entityId, xrefId, req));
    }

    /** The logins on this record's web addresses, each login once with the web
     *  addresses that use it -- the Logins tab's From web addresses section. */
    @GetMapping("/from-web-addresses")
    public ResponseEntity<?> fromWebAddresses(@PathVariable int entityId) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.listViaWebAddresses(entityId));
    }

    /** Logins not yet attached to this entity, for the link-an-existing picker. */
    @GetMapping("/available")
    public ResponseEntity<?> available(@PathVariable int entityId) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.listAvailable(entityId));
    }

    /** Attach an existing login to this entity (xref only; the shared LoginSetup
     *  record is never modified). Mirrors the web-address link endpoint. */
    @PostMapping("/link/{loginSetupId}")
    public ResponseEntity<?> link(@PathVariable int entityId, @PathVariable int loginSetupId) {
        if (entityId <= 0 || loginSetupId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid ids"));
        service.linkExisting(entityId, loginSetupId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{xrefId}")
    public ResponseEntity<?> delete(@PathVariable int entityId, @PathVariable int xrefId) {
        if (xrefId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid xrefId"));
        service.delete(xrefId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
