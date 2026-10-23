package com.echo

import javax.sql.DataSource

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource

/**
 * Story 5653: the base of every DATABASE integration spec (*ITSpec). Each spec runs the real
 * Jdbc* repositories against a real, throwaway Echo database -- never Echo, Leonardo or Hearth.
 *
 * Safety:
 *  - the JDBC URL comes from the system property echo.it.url, else the environment variable
 *    ECHO_IT_URL (scripts\Test-EchoDatabase.ps1 sets it). There is no default: a SQL login never
 *    lives in this public repository;
 *  - the database named in the URL must start with EchoTest_, checked before any connection is
 *    opened, and the connected DB_NAME() is checked again after it is opened;
 *  - every feature runs on ONE connection with auto-commit off (the driver then runs with
 *    IMPLICIT_TRANSACTIONS ON), and cleanup() rolls it back. A proc's own BEGIN / COMMIT TRAN only
 *    nests inside that outer transaction, so nothing a feature writes is ever committed.
 *
 * Opt-in only: these specs run with mvnw -Pdb-it (see pom.xml and scripts\Test-EchoDatabase.ps1).
 */
abstract class EchoDbITSpec extends EchoSpockBaseSpec {


    static final String PREFIX = 'EchoTest_'

    /** A hash long enough for stp_AppUser_Save (20+ characters). */
    static final String HASH = 'pbkdf2$210000$AAAAAAAAAAAAAAAAAAAAAA==$BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB='

    SingleConnectionDataSource ds
    JdbcTemplate sql

    /** -Decho.it.url, else the ECHO_IT_URL environment variable (scripts\Test-EchoDatabase.ps1 sets it). */
    static String url() {
        String u = System.getProperty('echo.it.url')
        if (u == null || u.trim().isEmpty()) u = System.getenv('ECHO_IT_URL')
        if (u == null || u.trim().isEmpty()) {
            throw new IllegalStateException('No test database: run scripts\\Test-EchoDatabase.ps1, or set echo.it.url / ECHO_IT_URL.')
        }
        return u.trim()
    }

    /** The databaseName (or database) property of a SQL Server JDBC URL; null when it has none. */
    static String databaseNameOf(String jdbcUrl) {
        if (jdbcUrl == null) return null
        for (String part : jdbcUrl.split(';')) {
            int eq = part.indexOf('=')
            if (eq < 0) continue
            String k = part.substring(0, eq).trim().toLowerCase()
            if (k == 'databasename' || k == 'database') return part.substring(eq + 1).trim()
        }
        return null
    }

    static void refuseUnlessTestDatabase(String name) {
        if (name == null || !name.startsWith(PREFIX)) {
            throw new IllegalStateException('Refusing to run: the database in echo.it.url is "' + name +
                '", and database integration specs only ever run against a database whose name starts with ' + PREFIX + '.')
        }
    }

    def setupSpec() {
        refuseUnlessTestDatabase(databaseNameOf(url()))
    }

    def setup() {
        refuseUnlessTestDatabase(databaseNameOf(url()))
        ds = new SingleConnectionDataSource(url(), true)
        ds.setAutoCommit(false)
        sql = new JdbcTemplate(ds)
        // Belt and braces: the server agrees about where we are.
        refuseUnlessTestDatabase(sql.queryForObject('SELECT DB_NAME()', String))
    }

    def cleanup() {
        if (ds != null) {
            try { ds.getConnection().rollback() } finally { ds.destroy() }
        }
    }

    DataSource dataSource() { ds }

    // ---- small SQL helpers (reads only, plus fixture writes through the procs) ----

    Object scalar(String q, Object... args) {
        List<Map<String, Object>> rows = sql.queryForList(q, args)
        return rows.isEmpty() ? null : rows[0].values().iterator().next()
    }

    int count(String q, Object... args) { ((Number) scalar(q, args)).intValue() }

    /** The first column of the first row as an int -- the id a save proc returns. */
    int idOf(String q, Object... args) { ((Number) scalar(q, args)).intValue() }

    List<Map<String, Object>> rows(String q, Object... args) { sql.queryForList(q, args) }

    Map<String, Object> row(String q, Object... args) {
        List<Map<String, Object>> r = sql.queryForList(q, args)
        return r.isEmpty() ? null : r[0]
    }

    /** The audit rows a trigger wrote for one record, oldest first. */
    List<Map<String, Object>> logRows(String table, String keyColumn, Object id) {
        String logId = table + '_LOGID'
        sql.queryForList('SELECT * FROM dbo.' + table + '_LOG WHERE ' + keyColumn + ' = ? ORDER BY ' + logId, id)
    }

    /** A unique suffix so a feature's names never collide with anything already in the database. */
    static String uniq() { Long.toString(System.nanoTime(), 36) }

    // ---- fixtures through the real procs ----

    int newSolution(String name = 'IT solution ' + uniq(), String alias = null) {
        idOf('EXEC dbo.stp_Solution_Save 0, ?, ?, ?, ?, NULL, NULL, 0, 0, ?, ?', name, 'desc', 'todo', 'tony', 'felix', alias)
    }

    int newProject(int solutionId, String name = 'IT project ' + uniq()) {
        idOf('EXEC dbo.stp_SolutionProject_Save 0, ?, ?, ?, ?, ?, 0, ?', solutionId, name, 'pdesc', 'todo', 'tony', 'felix')
    }

    int newItem(int projectId, String title = 'IT item ' + uniq(), Integer parentId = null, String status = 'todo') {
        idOf('EXEC dbo.stp_SolutionProjectItem_Save 0, ?, ?, ?, ?, ?, ?, 0, 0, NULL, ?',
            projectId, parentId, title, 'detail', status, 'tony', 'tony')
    }

    int newApiClient(String code = 'it' + uniq()) {
        idOf('EXEC dbo.stp_ApiClient_Save 0, ?, ?, ?, ?, 1', code, 'IT client', '/api/solutions/', 'n')
    }

    int newLog(int solutionId, String text, String type = 'note') {
        idOf("EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, NULL, ?, 'tony', ?, NULL", solutionId, type, text)
    }

    int newFile(int solutionId, String originalFileName, String title = null, long sizeBytes = 10L) {
        idOf("EXEC dbo.stp_SolutionFile_Save 0, ?, NULL, ?, ?, 'local', NULL, 's', 'x', ?, NULL, 'tony'",
            solutionId, title, originalFileName, sizeBytes)
    }

    // ---- assertions ----

    /** Runs the closure and returns the SolutionRuleException it threw (fails when it threw none). */
    static SolutionRuleException ruleOf(Closure c) {
        try { c.call() } catch (SolutionRuleException ex) { return ex }
        throw new AssertionError('Expected a SolutionRuleException, but none was thrown.')
    }
}
