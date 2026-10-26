package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * JDBC lookup repository -- runs the named {@code stp_LW_Lookup_*} proc.
 * Loaded when {@code leonardo.demo.enabled=false} (default).
 */
@Repository
public class JdbcLookupRepository implements LookupRepository {

    private final JdbcTemplate jdbc;

    public JdbcLookupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> run(String procName) {
        return jdbc.queryForList("EXEC dbo." + procName);
    }

    @Override
    public Map<String, Object> findOrCreate(String lookup, String name) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_TypeLookup_FindOrCreate @prmLookup = ?, @prmName = ?",
                lookup, name);
        } catch (DataAccessException e) {
            // A RAISERROR from the proc (error 50000) is a message for the user.
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof SQLException && ((SQLException) t).getErrorCode() == 50000) {
                    throw new IllegalArgumentException(t.getMessage());
                }
            }
            throw e;
        }
    }
}
