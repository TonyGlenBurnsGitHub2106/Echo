package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The few JdbcTemplate moves every Solutions-family repository makes (Story
 * 3493), written once: EXEC a proc and map its rows, read the id a save proc
 * returns, read RowsAffected, and turn a proc's RAISERROR -- a rule written for
 * the reader -- into {@link SolutionRuleException}.
 *
 * Every save / delete proc in the family ends in a SELECT, so all of them go
 * through a query, never {@code jdbc.update}.
 */
final class SolutionJdbc {

    private static final int RAISERROR_USER_DEFINED = 50000;

    private final JdbcTemplate jdbc;

    SolutionJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    <T> List<T> list(String sql, Function<Map<String, Object>, T> mapper, Object... args) {
        try {
            return jdbc.queryForList(sql, args).stream().map(mapper).toList();
        } catch (DataAccessException ex) { throw unwrap(ex); }
    }

    <T> T one(String sql, Function<Map<String, Object>, T> mapper, Object... args) {
        List<T> rows = list(sql, mapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The first row of a proc that returns one, or null. */
    Map<String, Object> row(String sql, Object... args) {
        return one(sql, r -> r, args);
    }

    /** The int a save proc reports in {@code column}; 0 when it reports nothing. */
    int number(String sql, String column, Object... args) {
        Map<String, Object> r = row(sql, args);
        return r == null ? 0 : SolutionRows.intOf(r, column);
    }

    boolean affected(String sql, Object... args) {
        return number(sql, "RowsAffected", args) > 0;
    }

    /** For the rare proc that returns nothing at all. */
    void run(String sql, Object... args) {
        try { jdbc.update(sql, args); } catch (DataAccessException ex) { throw unwrap(ex); }
    }

    private static RuntimeException unwrap(DataAccessException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == RAISERROR_USER_DEFINED)
                return new SolutionRuleException(sql.getMessage());
        }
        return ex;
    }
}
