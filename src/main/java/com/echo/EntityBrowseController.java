package com.echo;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Story 5653 (Echo): the one question the solution page's Web tab asks of LeonardoWeb's Entity Browse
 * -- "which EntityID is solution N?" ({@code GET /api/entity-browse?type=222&identityId=N}) -- answered
 * in the same shape, {@code {rows: [{entityId}]}}, so solution-web.js stays Leonardo's file unchanged.
 * Echo has no Entity Browse otherwise; any other type answers no rows.
 *
 * House-only: the EntityID never goes out under /api/solutions, and an API client's key is fenced to
 * /api/solutions/, so an outside agent cannot reach this.
 */
@RestController
@RequestMapping("/api/entity-browse")
public class EntityBrowseController {

    static final int SOLUTION_ENTITY_TYPE = 222;

    private final JdbcTemplate jdbc;

    public EntityBrowseController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<?> browse(@RequestParam("type") int type, @RequestParam("identityId") int identityId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (type == SOLUTION_ENTITY_TYPE && identityId > 0) {
            for (Map<String, Object> r : jdbc.queryForList("EXEC dbo.stp_Solution_GetEntity @prmSolutionID = ?", identityId)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("entityId", r.get("EntityID"));
                m.put("identityId", r.get("SolutionID"));
                rows.add(m);
            }
        }
        return ResponseEntity.ok(Map.of("rows", rows));
    }
}
