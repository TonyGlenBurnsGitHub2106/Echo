package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class JdbcEntityWebAddressRepository implements EntityWebAddressRepository {

    private final JdbcTemplate jdbc;
    // Story 5653 (Echo): no EntityXrefReciprocalLinker -- the "owning record" link back from a web address
    // feeds Leonardo's Entity Manager, which Echo does not have. The two linkBack calls below are left out.
    public JdbcEntityWebAddressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> listForEntity(int entityId) {
        return jdbc.queryForList("EXEC dbo.stp_LW_WebAddress_List @prmEntityID=?", entityId);
    }

    @Override
    public Map<String, Object> save(int xrefId, int webAddressId, int parentEntityId,
                                     String name, String url, int webAddressTypeId, int webSiteTypeId,
                                     String notes) {
        Map<String, Object> saved = jdbc.queryForMap(
            "EXEC dbo.stp_LW_WebAddress_Save @prmEntityWebAddressXRefID=?, @prmWebAddressID=?, " +
                "@prmParentEntityID=?, @prmWebAddressName=?, @prmWebAddress=?, @prmWebAddressTypeID=?, " +
                "@prmWebSiteTypeID=?, @prmNotes=?",
            xrefId, webAddressId, parentEntityId, name, url, webAddressTypeId, webSiteTypeId, notes);
        return saved;
    }

    @Override
    public void delete(int xrefId) {
        jdbc.update("EXEC dbo.stp_LW_EntityWebAddress_Delete @prmEntityWebAddressXRefID=?", xrefId);
    }

    @Override
    public boolean existsByUrl(String url, int excludeWebAddressId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.tblWebAddress WHERE WebAddress = ? AND WebAddressID <> ?",
            Integer.class, url, excludeWebAddressId);
        return count != null && count > 0;
    }

    @Override
    public List<Map<String, Object>> listAvailable(int entityId) {
        return jdbc.queryForList("EXEC dbo.stp_LW_WebAddress_Available @prmEntityID=?", entityId);
    }

    @Override
    public void linkExisting(int parentEntityId, int webAddressId) {
        jdbc.update("EXEC dbo.stp_LW_EntityWebAddress_Link @prmParentEntityID=?, @prmWebAddressID=?",
            parentEntityId, webAddressId);
    }
}
