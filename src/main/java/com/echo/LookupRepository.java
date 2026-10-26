package com.echo;

import java.util.List;
import java.util.Map;

/**
 * Generic lookup-table repository. Runs a {@code stp_LW_Lookup_*} stored
 * proc and returns the resulting rows. All lookup procs follow the same
 * shape: {@code SELECT Id, Name FROM SomeTable ORDER BY Name}.
 *
 * Two implementations exist; Spring picks one based on
 * {@code leonardo.demo.enabled}:
 *   - {@link JdbcLookupRepository}     -- real SQL Server (default)
 *   - {@link InMemoryLookupRepository} -- JSON-backed demo store
 */
public interface LookupRepository {

    /**
     * Executes the named lookup proc. Each row Map has at minimum
     * {@code Id, Name}; specific procs may include more columns.
     */
    List<Map<String, Object>> run(String procName);

    /**
     * Find-or-create one row of a simple type list by name (Story 3550).
     * {@code lookup} is the URL slug (vendor-types, address-types, ...).
     * Returns {@code Id, Name, WasCreated}.
     */
    Map<String, Object> findOrCreate(String lookup, String name);
}
