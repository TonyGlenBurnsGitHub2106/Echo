package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL Server backed {@link WebTypeRepository} (Story 3525). Every call EXECs a proc the
 * Leonardo desktop tree owns. Deleting a type goes through the generic stp_Lookup_Delete
 * ('web-site-type' in dbo.LookupRegistry), which moves the addresses and refuses while any
 * would be left without a type.
 */
@Repository
public class JdbcWebTypeRepository implements WebTypeRepository {

    private final JdbcTemplate jdbc;

    public JdbcWebTypeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Map<String, Object>> listTypes() {
        return jdbc.queryForList("EXEC dbo.stp_WebSiteType_List");
    }

    @Override
    public Map<String, Object> findOrCreateType(String name) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_WebSiteType_FindOrCreate @prmWebSiteType=?", name);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> saveType(int id, String name, String colorHex) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_WebSiteType_Save @prmWebSiteTypeID=?, @prmWebSiteType=?, @prmColorHex=?",
                id, name, colorHex);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> reorderTypes(List<Integer> ids) {
        return jdbc.queryForList("EXEC dbo.stp_WebSiteType_Reorder @prmWebSiteTypeIDs=?", csv(ids));
    }

    @Override
    public int deleteType(int id, Integer moveToId) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_Lookup_Delete @prmLookupKey=?, @prmItemKey=?, @prmMoveToKey=?",
                rs -> rs.next() ? rs.getInt("Affected") : 0,
                "web-site-type", String.valueOf(id), moveToId == null ? null : String.valueOf(moveToId));
            return n == null ? 0 : n;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> listFiled(int entityId, String groupBy, String sortKey, boolean sortDesc,
                                               String search, int offset, int pageSize) {
        return jdbc.queryForList(
            "EXEC dbo.stp_EntityWebAddress_ListFiled @prmEntityID=?, @prmGroupBy=?, @prmSortKey=?, @prmSortDesc=?, " +
                "@prmSearch=?, @prmOffset=?, @prmPageSize=?",
            entityId, groupBy, sortKey, sortDesc, search, offset, pageSize);
    }

    @Override
    public List<Map<String, Object>> countsFiled(int entityId, String groupBy, String search) {
        return jdbc.queryForList("EXEC dbo.stp_EntityWebAddress_CountsFiled @prmEntityID=?, @prmGroupBy=?, @prmSearch=?",
            entityId, groupBy, search);
    }

    @Override
    public int setType(int entityId, List<Integer> xrefIds, int typeId) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_EntityWebAddress_SetType @prmEntityID=?, @prmXRefIDs=?, @prmWebSiteTypeID=?",
                rs -> rs.next() ? rs.getInt("Changed") : 0, entityId, csv(xrefIds), typeId);
            return n == null ? 0 : n;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public boolean setImportant(int entityId, int xrefId, boolean important) {
        Integer n = jdbc.query("EXEC dbo.stp_EntityWebAddress_SetImportant @prmEntityID=?, @prmXRefID=?, @prmIsImportant=?",
            rs -> rs.next() ? rs.getInt("Changed") : 0, entityId, xrefId, important);
        return n != null && n > 0;
    }

    @Override
    public int reorderOnEntity(int entityId, List<Integer> xrefIds) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_EntityWebAddress_Reorder @prmEntityID=?, @prmXRefIDs=?",
                rs -> rs.next() ? rs.getInt("Changed") : 0, entityId, csv(xrefIds));
            return n == null ? 0 : n;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    private static RuntimeException asRule(DataAccessException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == 50000) {
                return new IllegalArgumentException(sql.getMessage());
            }
        }
        return ex;
    }

    private static String csv(List<Integer> ids) {
        return ids == null ? "" : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
