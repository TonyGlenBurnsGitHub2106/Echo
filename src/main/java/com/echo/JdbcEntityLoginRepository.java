package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class JdbcEntityLoginRepository implements EntityLoginRepository {

    private final JdbcTemplate jdbc;

    public JdbcEntityLoginRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> listForEntity(int entityId) {
        return jdbc.queryForList("EXEC dbo.stp_LW_EntityLogin_List @prmEntityID=?", entityId);
    }

    @Override
    public Map<String, Object> save(int xrefId, int loginSetupId, int parentEntityId,
                                     String loginName, String loginUserName, String pass,
                                     int userId, String notes, String effectiveDate, String throughDate) {
        return jdbc.queryForMap(
            "EXEC dbo.stp_LW_LoginSetup_Save @prmEntityLoginSetupXRefID=?, @prmLoginSetupID=?, " +
                "@prmParentEntityID=?, @prmLoginName=?, @prmLoginUserName=?, @prmPass=?, @prmUserID=?, " +
                "@prmNotes=?, @prmEffectiveDate=?, @prmThroughDate=?",
            xrefId, loginSetupId, parentEntityId, loginName, loginUserName, pass, userId, notes,
            effectiveDate, throughDate);
    }

    @Override
    public void delete(int xrefId) {
        jdbc.update("EXEC dbo.stp_LW_EntityLogin_Delete @prmEntityLoginSetupXRefID=?", xrefId);
    }

    @Override
    public List<Map<String, Object>> listAvailable(int entityId) {
        return jdbc.queryForList("EXEC dbo.stp_LoginSetup_ListAvailable @prmEntityID=?", entityId);
    }

    @Override
    public void linkExisting(int parentEntityId, int loginSetupId) {
        jdbc.update("EXEC dbo.stp_EntityLoginSetup_Link @prmParentEntityID=?, @prmLoginSetupID=?",
            parentEntityId, loginSetupId);
    }

    @Override
    public List<Map<String, Object>> listViaWebAddresses(int entityId) {
        return jdbc.queryForList("EXEC dbo.stp_EntityLogin_ListViaWebAddresses @prmEntityID=?", entityId);
    }

    @Override
    public List<Map<String, Object>> listSecrets() {
        return jdbc.queryForList("EXEC dbo.stp_LoginSecret_List");
    }

    @Override
    public void saveSecret(int loginSetupId, byte[] cipher, String placeholder) {
        try {
            jdbc.update("EXEC dbo.stp_LoginSecret_Save @prmLoginSetupID=?, @prmPassCipher=?, @prmPlaceholder=?",
                loginSetupId, cipher, placeholder);
        } catch (DataAccessException ex) {
            throw SaveRefusedException.translate(ex);
        }
    }

    @Override
    public void deleteSecret(int loginSetupId) {
        jdbc.update("EXEC dbo.stp_LoginSecret_Delete @prmLoginSetupID=?", loginSetupId);
    }
}
