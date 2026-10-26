package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/entities/{entityId}/web-addresses")
public class EntityWebAddressController {

    private final EntityWebAddressService service;
    private final WebTypeService types;

    public EntityWebAddressController(EntityWebAddressService service, WebTypeService types) {
        this.service = service;
        this.types = types;
    }

    /**
     * Story 3525: the website type may arrive as a typed NAME (the dialog's type box finds or
     * creates it) instead of an id. Returns an error message, or null when the request now
     * carries a type id.
     */
    private String resolveSiteType(EntityWebAddressService.WebAddressSaveRequest req) {
        if (req == null || req.webSiteTypeId > 0) return null;
        try {
            int id = types.resolveType(null, req.webSiteTypeName);
            if (id > 0) req.webSiteTypeId = id;
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable int entityId) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.listForEntity(entityId));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable int entityId,
                                     @RequestBody EntityWebAddressService.WebAddressSaveRequest req) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        if (req == null || req.webAddress == null || req.webAddress.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "webAddress is required"));
        if (req.webAddressTypeId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "webAddressTypeId is required"));
        String typeProblem = resolveSiteType(req);
        if (typeProblem != null) return ResponseEntity.badRequest().body(Map.of("error", typeProblem));
        if (req.webSiteTypeId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "webSiteTypeId is required"));
        req.webAddressId = 0;
        return ResponseEntity.ok(service.save(entityId, 0, req));
    }

    @PutMapping("/{xrefId}")
    public ResponseEntity<?> update(@PathVariable int entityId, @PathVariable int xrefId,
                                     @RequestBody EntityWebAddressService.WebAddressSaveRequest req) {
        if (entityId <= 0 || xrefId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        if (req == null || req.webAddress == null || req.webAddress.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "webAddress is required"));
        String typeProblem = resolveSiteType(req);
        if (typeProblem != null) return ResponseEntity.badRequest().body(Map.of("error", typeProblem));
        if (req.webAddressTypeId <= 0 || req.webSiteTypeId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "type ids are required"));
        return ResponseEntity.ok(service.save(entityId, xrefId, req));
    }

    @DeleteMapping("/{xrefId}")
    public ResponseEntity<?> delete(@PathVariable int entityId, @PathVariable int xrefId) {
        if (xrefId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid xrefId"));
        service.delete(xrefId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Web addresses not yet attached to this entity, for the add-existing picker. */
    @GetMapping("/available")
    public ResponseEntity<?> available(@PathVariable int entityId) {
        if (entityId <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid entityId"));
        return ResponseEntity.ok(service.listAvailable(entityId));
    }

    /** Attach an existing web address to this entity (xref only; the shared
     *  web-address record is never modified). Mirrors the phone link endpoint. */
    @PostMapping("/link/{webAddressId}")
    public ResponseEntity<?> link(@PathVariable int entityId, @PathVariable int webAddressId) {
        if (entityId <= 0 || webAddressId <= 0)
            return ResponseEntity.badRequest().body(Map.of("error", "invalid ids"));
        service.linkExisting(entityId, webAddressId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
