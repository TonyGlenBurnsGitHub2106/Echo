package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A solution's Configuration tab (Story 3516): every property that means something, in groups, with its
 * value in each environment (Local, QA, Production by default) and where that value is read from.
 *
 *   GET    /api/solutions/{id}/config                                  the whole tab, with counters
 *   POST   /api/solutions/{id}/config/environments/defaults            Local, QA, Production -- only when there are none
 *   POST   /api/solutions/{id}/config/environments                     {title}
 *   PUT    /api/solutions/config/environments/{envId}                  {title, sortOrder}
 *   DELETE /api/solutions/config/environments/{envId}                  takes its values; never the last one
 *   POST   /api/solutions/{id}/config/groups                           {title}
 *   PUT    /api/solutions/config/groups/{groupId}                      {title, sortOrder}
 *   DELETE /api/solutions/config/groups/{groupId}                      takes its properties
 *   POST   /api/solutions/config/groups/{groupId}/properties           {key, description, typeCode}
 *   PUT    /api/solutions/config/properties/{propertyId}               {key, description, typeCode, groupId, sortOrder} -- a merge
 *   DELETE /api/solutions/config/properties/{propertyId}
 *   GET    /api/solutions/config/properties/{propertyId}/locate        {solutionId} -- for a Find link
 *   PUT    /api/solutions/config/properties/{propertyId}/values/{envId}  {value, source, note, alsoSetIn} -- a merge
 *
 * Every write answers with the whole tab.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionConfigController extends SolutionControllerSupport {

    private final SolutionConfigService service;

    public SolutionConfigController(SolutionConfigService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record TitleRequest(String title, Integer sortOrder) {}
    public record PropertyRequest(String key, String description, String typeCode, Integer groupId, Integer sortOrder) {}
    public record ValueRequest(String value, String source, String note, List<String> alsoSetIn) {}

    @GetMapping("/{id:\\d+}/config")
    public ResponseEntity<?> config(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.config(caller(req), id));
    }

    // ---- environments -------------------------------------------------------------

    @PostMapping("/{id:\\d+}/config/environments/defaults")
    public ResponseEntity<?> defaults(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.seedDefaults(caller(req), id));
    }

    @PostMapping("/{id:\\d+}/config/environments")
    public ResponseEntity<?> addEnvironment(HttpServletRequest req, @PathVariable int id, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.addEnvironment(caller(req), id, required(body == null ? null : body.title(), "An environment name")));
    }

    @PutMapping("/config/environments/{envId:\\d+}")
    public ResponseEntity<?> updateEnvironment(HttpServletRequest req, @PathVariable int envId, @RequestBody TitleRequest body) {
        if (body == null) throw new IllegalArgumentException("An environment is required.");
        return ResponseEntity.ok(service.updateEnvironment(caller(req), envId, body.title(), body.sortOrder()));
    }

    @DeleteMapping("/config/environments/{envId:\\d+}")
    public ResponseEntity<?> deleteEnvironment(HttpServletRequest req, @PathVariable int envId) {
        return ResponseEntity.ok(service.deleteEnvironment(caller(req), envId));
    }

    // ---- groups -------------------------------------------------------------------

    @PostMapping("/{id:\\d+}/config/groups")
    public ResponseEntity<?> addGroup(HttpServletRequest req, @PathVariable int id, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.addGroup(caller(req), id, required(body == null ? null : body.title(), "A group name")));
    }

    @PutMapping("/config/groups/{groupId:\\d+}")
    public ResponseEntity<?> updateGroup(HttpServletRequest req, @PathVariable int groupId, @RequestBody TitleRequest body) {
        if (body == null) throw new IllegalArgumentException("A group is required.");
        return ResponseEntity.ok(service.updateGroup(caller(req), groupId, body.title(), body.sortOrder()));
    }

    @DeleteMapping("/config/groups/{groupId:\\d+}")
    public ResponseEntity<?> deleteGroup(HttpServletRequest req, @PathVariable int groupId) {
        return ResponseEntity.ok(service.deleteGroup(caller(req), groupId));
    }

    // ---- properties ---------------------------------------------------------------

    @PostMapping("/config/groups/{groupId:\\d+}/properties")
    public ResponseEntity<?> addProperty(HttpServletRequest req, @PathVariable int groupId, @RequestBody PropertyRequest body) {
        if (body == null) throw new IllegalArgumentException("A property is required.");
        return ResponseEntity.ok(service.addProperty(caller(req), groupId, required(body.key(), "A property name"), body.description(), body.typeCode()));
    }

    @PutMapping("/config/properties/{propertyId:\\d+}")
    public ResponseEntity<?> updateProperty(HttpServletRequest req, @PathVariable int propertyId, @RequestBody PropertyRequest body) {
        if (body == null) throw new IllegalArgumentException("A property is required.");
        return ResponseEntity.ok(service.updateProperty(caller(req), propertyId, body.key(), body.description(), body.typeCode(),
            body.groupId(), body.sortOrder()));
    }

    @DeleteMapping("/config/properties/{propertyId:\\d+}")
    public ResponseEntity<?> deleteProperty(HttpServletRequest req, @PathVariable int propertyId) {
        return ResponseEntity.ok(service.deleteProperty(caller(req), propertyId));
    }

    @GetMapping("/config/properties/{propertyId:\\d+}/locate")
    public ResponseEntity<?> locate(HttpServletRequest req, @PathVariable int propertyId) {
        return ResponseEntity.ok(service.locateProperty(caller(req), propertyId));
    }

    // ---- a cell -------------------------------------------------------------------

    @PutMapping("/config/properties/{propertyId:\\d+}/values/{envId:\\d+}")
    public ResponseEntity<?> setValue(HttpServletRequest req, @PathVariable int propertyId, @PathVariable int envId,
                                      @RequestBody ValueRequest body) {
        if (body == null) throw new IllegalArgumentException("A value is required.");
        return ResponseEntity.ok(service.setValue(caller(req), propertyId, envId, body.value(), body.source(), body.note(), body.alsoSetIn()));
    }
}
