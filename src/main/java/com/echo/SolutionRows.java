package com.echo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Reads one value out of a result row for the Solutions family (Story 3493). A
 * row is a column-name map whether it came from a stored procedure
 * ({@code JdbcTemplate.queryForList}) or from the demo store, so the SAME
 * mapper turns either into a model -- which is what keeps the two repository
 * implementations from drifting apart.
 */
final class SolutionRows {

    private SolutionRows() {}

    static int intOf(Map<String, Object> row, String column) {
        Object v = row.get(column);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString().trim()); } catch (NumberFormatException ex) { return 0; }
    }

    /** Null when the column is NULL or zero -- an optional foreign key. */
    static Integer idOrNull(Map<String, Object> row, String column) {
        int v = intOf(row, column);
        return v > 0 ? v : null;
    }

    static boolean boolOf(Map<String, Object> row, String column) {
        Object v = row.get(column);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n)  return n.intValue() != 0;
        return v != null && ("true".equalsIgnoreCase(v.toString().trim()) || "1".equals(v.toString().trim()));
    }

    static String strOf(Map<String, Object> row, String column) {
        Object v = row.get(column);
        return v == null ? null : v.toString();
    }

    static LocalDateTime dateOf(Map<String, Object> row, String column) {
        Object v = row.get(column);
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime();
        try { return LocalDateTime.parse(v.toString().trim().replace(' ', 'T')); } catch (RuntimeException ex) { return null; }
    }
}
