package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * SQL Server backed {@link MailTriageRepository} (Story 3561). Every call EXECs a proc the
 * Leonardo desktop tree owns (SQL Procs Crud / Logic, stp_MailTriage*).
 *
 * Story 5654: echoed into Echo (the Email Client's whitelist and blacklist); Echo keeps the same
 * procs in db\procs. The Gmail filter, waiting-list, run and account calls are left out.
 */
@Repository
public class JdbcMailTriageRepository implements MailTriageRepository {

    private final JdbcTemplate jdbc;

    public JdbcMailTriageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Map<String, Object>> listRules(String disposition, String search, int offset, int pageSize) {
        return jdbc.queryForList(
            "EXEC dbo.stp_MailTriageRule_List @prmDisposition=?, @prmSearch=?, @prmOffset=?, @prmPageSize=?",
            disposition, search, offset, pageSize);
    }

    @Override
    public Map<String, Object> counts(String search) {
        return jdbc.queryForMap("EXEC dbo.stp_MailTriage_Counts @prmSearch=?", search);
    }

    @Override
    public List<Map<String, Object>> find(String search, int maxRows) {
        return jdbc.queryForList("EXEC dbo.stp_MailTriage_Find @prmSearch=?, @prmMaxRows=?", search, maxRows);
    }

    @Override
    public Map<String, Object> getRule(int id) {
        return first(jdbc.queryForList("EXEC dbo.stp_MailTriageRule_Get @prmMailTriageRuleID=?", id));
    }

    @Override
    public Map<String, Object> getRuleByKey(String senderKey) {
        return first(jdbc.queryForList(
            "EXEC dbo.stp_MailTriageRule_Get @prmMailTriageRuleID=0, @prmSenderKey=?", senderKey));
    }

    @Override
    public Map<String, Object> saveRule(String senderKey, String keyKind, String disposition,
                                        String gmailFilterId, String filterMode, String note) {
        try {
            return jdbc.queryForMap(
                "EXEC dbo.stp_MailTriageRule_Save @prmSenderKey=?, @prmKeyKind=?, @prmDisposition=?, " +
                    "@prmGmailFilterID=?, @prmFilterMode=?, @prmNote=?",
                senderKey, keyKind, disposition, gmailFilterId, filterMode, note);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> setProtected(int id, boolean isProtected) {
        try {
            return jdbc.queryForMap(
                "EXEC dbo.stp_MailTriageRule_SetProtected @prmMailTriageRuleID=?, @prmIsProtected=?",
                id, isProtected);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public boolean deleteRule(int id) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_MailTriageRule_Delete @prmMailTriageRuleID=?",
                rs -> rs.next() ? rs.getInt("Deleted") : 0, id);
            return n != null && n > 0;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** "Nothing was saved." -> SaveRefusedException (409); any other proc rule -> 400. */
    private static RuntimeException asRule(DataAccessException ex) {
        RuntimeException refused = SaveRefusedException.translate(ex);
        if (refused != ex) return refused;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == 50000) {
                return new IllegalArgumentException(sql.getMessage());
            }
        }
        return ex;
    }
}
