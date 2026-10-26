package com.echo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The small questions the shared pages ask (Story 5653):
 *
 *   GET /api/system/config       {sessionInactivityMinutes} -- the idle sign-out in auth.js
 *   GET /api/system/demo/status  {demoEnabled:false} -- Echo has no demo mode; auth.js asks anyway
 *   GET /api/system/health       {ok, database} -- open, so a script can tell Echo is up
 *   GET /api/system/about        version, uptime, database, files folder, release (signed in)
 *   POST /api/system/update      Settings -> Update Echo: pull, newest release, database, restart (signed in)
 *   GET /api/system/update/status how the last update went (signed in)
 *
 * /api/system/ is fenced off from every API client (ApiClientPathFence), so no agent can update Echo.
 */
@RestController
public class SystemController {

    private final JdbcTemplate jdbc;
    private final int idleMinutes;
    private final String filesPath;
    private final EchoUpdater updater;

    public SystemController(DataSource ds, EchoUpdater updater,
                            @Value("${echo.session.inactivity-minutes:0}") int idleMinutes,
                            @Value("${echo.files.path:EchoFiles}") String filesPath) {
        this.jdbc = new JdbcTemplate(ds);
        this.idleMinutes = idleMinutes;
        this.filesPath = filesPath;
        this.updater = updater;
    }

    @GetMapping("/api/system/config")
    public Map<String, Object> config() { return Map.of("sessionInactivityMinutes", Math.max(0, idleMinutes)); }

    @GetMapping("/api/system/demo/status")
    public Map<String, Object> demo() { return Map.of("demoEnabled", false); }

    @GetMapping("/api/system/health")
    public ResponseEntity<?> health() {
        boolean db = databaseAnswers();
        return ResponseEntity.status(db ? 200 : 503).body(Map.of("ok", db, "database", db ? "up" : "down"));
    }

    @GetMapping("/api/system/about")
    public Map<String, Object> about() {
        Map<String, Object> m = new LinkedHashMap<>();
        Package p = SystemController.class.getPackage();
        m.put("app", "Echo");
        m.put("version", p == null || p.getImplementationVersion() == null ? "dev" : p.getImplementationVersion());
        m.put("java", System.getProperty("java.version"));
        Duration up = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        m.put("uptime", up.toDays() + "d " + up.toHoursPart() + "h " + up.toMinutesPart() + "m");
        m.put("database", databaseName());
        m.put("databaseUp", databaseAnswers());
        m.put("filesFolder", java.nio.file.Path.of(filesPath).toAbsolutePath().normalize().toString());
        Map<String, Object> current = updater.currentRelease();
        Object release = current == null ? null : current.get("tag");
        m.put("release", release == null ? "built on this computer" : release);   // Story 5653
        return m;
    }

    // Story 5653: the Update Echo button. 202 = started (the page then waits for Echo to come back).
    @PostMapping("/api/system/update")
    public ResponseEntity<?> update() {
        try { return ResponseEntity.accepted().body(updater.start()); }
        catch (EchoUpdater.UpdateRefusedException ex) { return ResponseEntity.status(409).body(Map.of("error", ex.getMessage())); }
    }

    @GetMapping("/api/system/update/status")
    public Map<String, Object> updateStatus() { return updater.status(); }

    private boolean databaseAnswers() {
        try { return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class)); }
        catch (RuntimeException ex) { return false; }
    }

    private String databaseName() {
        try { return jdbc.queryForObject("SELECT @@SERVERNAME + ' / ' + DB_NAME()", String.class); }
        catch (RuntimeException ex) { return "(not reachable)"; }
    }
}
