package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * A solution's Configuration II tab (Story 5611): user-defined COLUMNS (each with a Color; one may be the
 * "applies to all" default), AXES that describe the columns (Environment, Server) with Colored VALUES,
 * properties in groups, and a cell per property per column with a value, where it is read from, the other
 * places it is set, a note and a STATE (observed / planned / unverified).
 *
 *   GET    /api/solutions/{id}/config-grid                                     the whole tab, with matches and counters
 *   POST   /api/solutions/{id}/config-grid/import                              copy the Configuration tab in (only while this is empty)
 *   POST   /api/solutions/{id}/config-grid/axes                                {title}
 *   PUT    /api/solutions/config-grid/axes/{axisId}                            {title, sortOrder}
 *   DELETE /api/solutions/config-grid/axes/{axisId}                            takes its values and the columns' tags on it
 *   POST   /api/solutions/config-grid/axes/{axisId}/values                     {title, color}
 *   PUT    /api/solutions/config-grid/axis-values/{valueId}                    {title, color, sortOrder}
 *   DELETE /api/solutions/config-grid/axis-values/{valueId}
 *   POST   /api/solutions/{id}/config-grid/columns                             {title, color, isDefault, tags: {axis: value}}
 *   PUT    /api/solutions/config-grid/columns/{columnId}                       {title, color, isDefault, sortOrder, tags} -- a merge
 *   DELETE /api/solutions/config-grid/columns/{columnId}                       takes its cells
 *   POST   /api/solutions/{id}/config-grid/groups                              {title}
 *   PUT    /api/solutions/config-grid/groups/{groupId}                         {title, sortOrder}
 *   DELETE /api/solutions/config-grid/groups/{groupId}                         takes its properties
 *   POST   /api/solutions/config-grid/groups/{groupId}/properties              {key, description, typeCode}
 *   PUT    /api/solutions/config-grid/properties/{propertyId}                  {key, description, typeCode, groupId, sortOrder} -- a merge
 *   DELETE /api/solutions/config-grid/properties/{propertyId}
 *   GET    /api/solutions/config-grid/properties/{propertyId}/locate           {solutionId} -- for a Find link
 *   PUT    /api/solutions/config-grid/properties/{propertyId}/values/{columnId}  {value, source, note, state, alsoSetIn} -- a merge
 *
 * Every write answers with the whole tab.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionConfigGridController extends SolutionControllerSupport {

    private final SolutionConfigGridService service;

    public SolutionConfigGridController(SolutionConfigGridService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record TitleRequest(String title, Integer sortOrder) {}
    public record AxisValueRequest(String title, String color, Integer sortOrder) {}
    public record ColumnRequest(String title, String color, Boolean isDefault, Integer sortOrder, Map<String, String> tags) {}
    public record PropertyRequest(String key, String description, String typeCode, Integer groupId, Integer sortOrder) {}
    public record ValueRequest(String value, String source, String note, String state, List<String> alsoSetIn) {}

    @GetMapping("/{id:\\d+}/config-grid")
    public ResponseEntity<?> grid(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.grid(caller(req), id));
    }

    @PostMapping("/{id:\\d+}/config-grid/import")
    public ResponseEntity<?> importFromConfig(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.importFromConfig(caller(req), id));
    }

    // ---- axes ---------------------------------------------------------------------

    @PostMapping("/{id:\\d+}/config-grid/axes")
    public ResponseEntity<?> addAxis(HttpServletRequest req, @PathVariable int id, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.addAxis(caller(req), id, required(body == null ? null : body.title(), "An axis name")));
    }

    @PutMapping("/config-grid/axes/{axisId:\\d+}")
    public ResponseEntity<?> updateAxis(HttpServletRequest req, @PathVariable int axisId, @RequestBody TitleRequest body) {
        if (body == null) throw new IllegalArgumentException("An axis is required.");
        return ResponseEntity.ok(service.updateAxis(caller(req), axisId, body.title(), body.sortOrder()));
    }

    @DeleteMapping("/config-grid/axes/{axisId:\\d+}")
    public ResponseEntity<?> deleteAxis(HttpServletRequest req, @PathVariable int axisId) {
        return ResponseEntity.ok(service.deleteAxis(caller(req), axisId));
    }

    @PostMapping("/config-grid/axes/{axisId:\\d+}/values")
    public ResponseEntity<?> addAxisValue(HttpServletRequest req, @PathVariable int axisId, @RequestBody AxisValueRequest body) {
        if (body == null) throw new IllegalArgumentException("An axis value is required.");
        return ResponseEntity.ok(service.addAxisValue(caller(req), axisId, required(body.title(), "An axis value"), body.color()));
    }

    @PutMapping("/config-grid/axis-values/{valueId:\\d+}")
    public ResponseEntity<?> updateAxisValue(HttpServletRequest req, @PathVariable int valueId, @RequestBody AxisValueRequest body) {
        if (body == null) throw new IllegalArgumentException("An axis value is required.");
        return ResponseEntity.ok(service.updateAxisValue(caller(req), valueId, body.title(), body.color(), body.sortOrder()));
    }

    @DeleteMapping("/config-grid/axis-values/{valueId:\\d+}")
    public ResponseEntity<?> deleteAxisValue(HttpServletRequest req, @PathVariable int valueId) {
        return ResponseEntity.ok(service.deleteAxisValue(caller(req), valueId));
    }

    // ---- columns ------------------------------------------------------------------

    @PostMapping("/{id:\\d+}/config-grid/columns")
    public ResponseEntity<?> addColumn(HttpServletRequest req, @PathVariable int id, @RequestBody ColumnRequest body) {
        if (body == null) throw new IllegalArgumentException("A column is required.");
        return ResponseEntity.ok(service.addColumn(caller(req), id, required(body.title(), "A column name"), body.color(), body.isDefault(), body.tags()));
    }

    @PutMapping("/config-grid/columns/{columnId:\\d+}")
    public ResponseEntity<?> updateColumn(HttpServletRequest req, @PathVariable int columnId, @RequestBody ColumnRequest body) {
        if (body == null) throw new IllegalArgumentException("A column is required.");
        return ResponseEntity.ok(service.updateColumn(caller(req), columnId, body.title(), body.color(), body.isDefault(), body.sortOrder(), body.tags()));
    }

    @DeleteMapping("/config-grid/columns/{columnId:\\d+}")
    public ResponseEntity<?> deleteColumn(HttpServletRequest req, @PathVariable int columnId) {
        return ResponseEntity.ok(service.deleteColumn(caller(req), columnId));
    }

    // ---- groups -------------------------------------------------------------------

    @PostMapping("/{id:\\d+}/config-grid/groups")
    public ResponseEntity<?> addGroup(HttpServletRequest req, @PathVariable int id, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.addGroup(caller(req), id, required(body == null ? null : body.title(), "A group name")));
    }

    @PutMapping("/config-grid/groups/{groupId:\\d+}")
    public ResponseEntity<?> updateGroup(HttpServletRequest req, @PathVariable int groupId, @RequestBody TitleRequest body) {
        if (body == null) throw new IllegalArgumentException("A group is required.");
        return ResponseEntity.ok(service.updateGroup(caller(req), groupId, body.title(), body.sortOrder()));
    }

    @DeleteMapping("/config-grid/groups/{groupId:\\d+}")
    public ResponseEntity<?> deleteGroup(HttpServletRequest req, @PathVariable int groupId) {
        return ResponseEntity.ok(service.deleteGroup(caller(req), groupId));
    }

    // ---- properties ---------------------------------------------------------------

    @PostMapping("/config-grid/groups/{groupId:\\d+}/properties")
    public ResponseEntity<?> addProperty(HttpServletRequest req, @PathVariable int groupId, @RequestBody PropertyRequest body) {
        if (body == null) throw new IllegalArgumentException("A property is required.");
        return ResponseEntity.ok(service.addProperty(caller(req), groupId, required(body.key(), "A property name"), body.description(), body.typeCode()));
    }

    @PutMapping("/config-grid/properties/{propertyId:\\d+}")
    public ResponseEntity<?> updateProperty(HttpServletRequest req, @PathVariable int propertyId, @RequestBody PropertyRequest body) {
        if (body == null) throw new IllegalArgumentException("A property is required.");
        return ResponseEntity.ok(service.updateProperty(caller(req), propertyId, body.key(), body.description(), body.typeCode(),
            body.groupId(), body.sortOrder()));
    }

    @DeleteMapping("/config-grid/properties/{propertyId:\\d+}")
    public ResponseEntity<?> deleteProperty(HttpServletRequest req, @PathVariable int propertyId) {
        return ResponseEntity.ok(service.deleteProperty(caller(req), propertyId));
    }

    @GetMapping("/config-grid/properties/{propertyId:\\d+}/locate")
    public ResponseEntity<?> locate(HttpServletRequest req, @PathVariable int propertyId) {
        return ResponseEntity.ok(service.locateProperty(caller(req), propertyId));
    }

    // ---- a cell -------------------------------------------------------------------

    @PutMapping("/config-grid/properties/{propertyId:\\d+}/values/{columnId:\\d+}")
    public ResponseEntity<?> setValue(HttpServletRequest req, @PathVariable int propertyId, @PathVariable int columnId,
                                      @RequestBody ValueRequest body) {
        if (body == null) throw new IllegalArgumentException("A value is required.");
        return ResponseEntity.ok(service.setValue(caller(req), propertyId, columnId, body.value(), body.source(), body.note(),
            body.state(), body.alsoSetIn()));
    }
}
