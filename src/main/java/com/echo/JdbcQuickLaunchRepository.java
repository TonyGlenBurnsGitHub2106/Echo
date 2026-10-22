package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC implementation backed by {@code stp_QuickLaunch_*} procs.
 * Loaded when {@code leonardo.demo.enabled=false} (default).
 */
@Repository
public class JdbcQuickLaunchRepository implements QuickLaunchRepository {

    private final JdbcTemplate jdbc;

    public JdbcQuickLaunchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<QuickLaunch> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("EXEC dbo.stp_QuickLaunch_List");
        List<QuickLaunch> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(rowToPin(r));
        return out;
    }

    @Override
    public int save(int quickLaunchId, String label, String url, String iconKey) {
        Map<String, Object> row = jdbc.queryForMap(
            "EXEC dbo.stp_QuickLaunch_Save @prmQuickLaunchID=?, @prmLabel=?, @prmURL=?, @prmIconKey=?",
            quickLaunchId, label, url, iconKey);
        Object v = row.get("QuickLaunchID");
        return v == null ? 0 : ((Number) v).intValue();
    }

    @Override
    public void delete(int quickLaunchId) {
        jdbc.update("EXEC dbo.stp_QuickLaunch_Delete @prmQuickLaunchID=?", quickLaunchId);
    }

    @Override
    public void setSortOrder(int quickLaunchId, int sortOrder) {
        jdbc.update("EXEC dbo.stp_QuickLaunch_Reorder @prmQuickLaunchID=?, @prmSortOrder=?",
            quickLaunchId, sortOrder);
    }

    private static QuickLaunch rowToPin(Map<String, Object> r) {
        QuickLaunch q = new QuickLaunch();
        q.quickLaunchId = intVal(r.get("QuickLaunchID"));
        q.label         = strVal(r.get("Label"));
        q.url           = strVal(r.get("URL"));
        q.iconKey       = strVal(r.get("IconKey"));
        q.sortOrder     = intVal(r.get("SortOrder"));
        q.createdUtc    = tsVal (r.get("CreatedUtc"));
        q.modifiedUtc   = tsVal (r.get("ModifiedUtc"));
        return q;
    }

    private static int intVal(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception e) { return 0; }
    }

    private static String strVal(Object o) { return o == null ? "" : o.toString(); }

    private static java.time.LocalDateTime tsVal(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp) return ((Timestamp) o).toLocalDateTime();
        if (o instanceof java.time.LocalDateTime) return (java.time.LocalDateTime) o;
        return null;
    }
}
