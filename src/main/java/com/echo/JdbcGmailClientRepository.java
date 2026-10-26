package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link GmailClientRepository} over the stp_GmailClient* procs (Story 5569). Story 5654: echoed into
 * Echo; the record-link calls are left out and stp_GmailClientSent_Rekey is Echo's own.
 */
@Repository
public class JdbcGmailClientRepository implements GmailClientRepository {

    private static final String FILTER_ARGS =
        "@prmSearch=?, @prmFrom=?, @prmTo=?, @prmSubject=?, @prmWords=?, @prmAfter=?, @prmBefore=?, @prmReadState=?, "
        + "@prmHasAttachment=?, @prmFlag=?, @prmFlagIDs=?";

    /** The filter's values, in FILTER_ARGS order. */
    private static Object[] filterValues(Filter f) {
        return new Object[] { f.search(), f.from(), f.to(), f.subject(), f.words(), f.after(), f.before(), f.readState(),
            f.hasAttachment(), f.flag(), f.flagIds() };
    }

    private static Object[] join(Object[] head, Object[] middle, Object... tail) {
        Object[] out = new Object[head.length + middle.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(middle, 0, out, head.length, middle.length);
        System.arraycopy(tail, 0, out, head.length + middle.length, tail.length);
        return out;
    }

    private final JdbcTemplate jdbc;

    public JdbcGmailClientRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Map<String, Object>> listMessages(String scope, Integer inboxId, Filter f, int offset, int pageSize) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientMessage_ListPage @prmScope=?, @prmInboxID=?, "
                    + FILTER_ARGS + ", @prmOffset=?, @prmPageSize=?",
                join(new Object[] { scope, inboxId }, filterValues(f), offset, pageSize));
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> counts(Filter f) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientMessage_Counts " + FILTER_ARGS, filterValues(f));
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    // ---- conversations (Story 5608) ----

    @Override
    public List<Map<String, Object>> listThreads(String scope, Integer inboxId, Filter f, int offset, int pageSize) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientThread_ListPage @prmScope=?, @prmInboxID=?, "
                    + FILTER_ARGS + ", @prmOffset=?, @prmPageSize=?",
                join(new Object[] { scope, inboxId }, filterValues(f), offset, pageSize));
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Integer> threadMessageIds(List<Integer> ids) {
        try {
            return jdbc.query("EXEC dbo.stp_GmailClientThread_MessageIDs @prmIDs=?",
                (rs, n) -> rs.getInt("GmailClientMessageID"), csv(ids));
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> thread(String kind, int id) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientThread_Get @prmKind=?, @prmID=?", kind, id);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> getMessage(int id) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientMessage_Get @prmGmailClientMessageID=?", id));
    }

    @Override
    public boolean recordMessage(String gmailId, String threadId, String senderKey, String from, String to, String cc,
                                 String subject, String snippet, String messageIdHeader, LocalDateTime received) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_GmailClientMessage_Record @prmGmailID=?, @prmThreadID=?, "
                    + "@prmSenderKey=?, @prmFromHeader=?, @prmToHeader=?, @prmCcHeader=?, @prmSubject=?, "
                    + "@prmSnippet=?, @prmMessageIDHeader=?, @prmReceivedDate=?",
                rs -> rs.next() ? rs.getInt("Recorded") : 0,
                gmailId, threadId, senderKey, from, to, cc, subject, snippet, messageIdHeader, Timestamp.valueOf(received));
            return n != null && n > 0;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Set<String> knownSince(LocalDateTime since) {
        Set<String> ids = new HashSet<>();
        jdbc.query("EXEC dbo.stp_GmailClientMessage_KnownSince @prmSince=?",
            rs -> { ids.add(rs.getString("GmailID")); }, Timestamp.valueOf(since));
        return ids;
    }

    @Override
    public boolean setRead(int id, boolean read) {
        return updated("EXEC dbo.stp_GmailClientMessage_SetRead @prmGmailClientMessageID=?, @prmIsRead=?", id, read);
    }

    @Override
    public boolean setHidden(int id, boolean hidden) {
        return updated("EXEC dbo.stp_GmailClientMessage_SetHidden @prmGmailClientMessageID=?, @prmIsHidden=?", id, hidden);
    }

    @Override
    public int setMany(List<Integer> ids, String action) {
        return count("EXEC dbo.stp_GmailClientMessage_SetMany @prmIDs=?, @prmAction=?", csv(ids), action);
    }

    @Override
    public int markAll(String scope, Integer inboxId, Filter f, boolean read) {
        return count("EXEC dbo.stp_GmailClientMessage_MarkAll @prmScope=?, @prmInboxID=?, " + FILTER_ARGS + ", @prmIsRead=?",
            join(new Object[] { scope, inboxId }, filterValues(f), read));
    }

    @Override
    public int setFollowUp(List<Integer> ids, String dueDate, String note) {
        return count("EXEC dbo.stp_GmailClientFollowUp_Set @prmIDs=?, @prmDueDate=?, @prmNote=?", csv(ids), dueDate, note);
    }

    @Override
    public int followUpDone(List<Integer> ids) {
        return count("EXEC dbo.stp_GmailClientFollowUp_Done @prmIDs=?", csv(ids));
    }

    @Override
    public Map<String, Object> getBody(int id) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientMessageBody_Get @prmGmailClientMessageID=?", id));
    }

    @Override
    public void saveBody(int id, String html, String text, String attachmentNames, boolean gone) {
        try {
            jdbc.query("EXEC dbo.stp_GmailClientMessageBody_Save @prmGmailClientMessageID=?, @prmBodyHtml=?, @prmBodyText=?, "
                    + "@prmAttachmentNames=?, @prmIsGoneFromGmail=?", rs -> null, id, html, text, attachmentNames, gone);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> listBodiesSavedBefore(LocalDateTime before, int top) {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientBody_ListSavedBefore @prmSavedBefore=?, @prmTop=?",
            Timestamp.valueOf(before), top);
    }

    @Override
    public List<Map<String, Object>> listMissingBodies(int top) {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientMessageBody_ListMissing @prmTop=?", top);
    }

    @Override
    public Map<String, Object> bodyCounts() {
        return jdbc.queryForMap("EXEC dbo.stp_GmailClientMessageBody_Counts");
    }

    @Override
    public List<Map<String, Object>> searchRecipients(String search, int top) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientRecipient_Search @prmSearch=?, @prmTop=?", search, top);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> setSenderInbox(String senderKey, String inboxName) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientSenderInbox_Set @prmSenderKey=?, @prmInboxName=?",
                senderKey, inboxName);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> setSenderInboxForSender(String address, String inboxName, boolean followDomain) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientSenderInbox_SetForSender @prmSenderAddress=?, @prmInboxName=?, "
                + "@prmFollowDomain=?", address, inboxName, followDomain);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    // ---- flags (Story 5608) ----

    @Override
    public List<Map<String, Object>> listFlags() { return jdbc.queryForList("EXEC dbo.stp_GmailClientFlag_List"); }

    @Override
    public Map<String, Object> saveFlag(int id, String name) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientFlag_Save @prmGmailClientFlagID=?, @prmFlagName=?", id, name);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> deleteFlag(int id) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientFlag_Delete @prmGmailClientFlagID=?", id);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> setMessageFlags(int messageId, List<String> names) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientMessageFlag_Set @prmGmailClientMessageID=?, @prmFlagNames=?",
                messageId, names == null ? null : String.join("\n", names));
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> bulkFlag(List<Integer> ids, String flagName, String action) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientMessageFlag_Bulk @prmIDs=?, @prmFlagName=?, @prmAction=?",
                csv(ids), flagName, action);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    // ---- colours (Story 5608) ----

    @Override
    public List<Map<String, Object>> listColors() { return jdbc.queryForList("EXEC dbo.stp_GmailClientColor_List"); }

    @Override
    public Map<String, Object> saveColor(int id, String kind, String matchText, Integer flagId, String colorHex) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientColor_Save @prmGmailClientColorID=?, @prmColorKind=?, "
                + "@prmMatchText=?, @prmGmailClientFlagID=?, @prmColorHex=?", id, kind, matchText, flagId, colorHex);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public int setColors(List<Integer> ids, String colorHex) {
        return count("EXEC dbo.stp_GmailClientColor_SetMany @prmIDs=?, @prmColorHex=?", csv(ids), colorHex);
    }

    @Override
    public int deleteColors(List<Integer> ids, boolean all) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_GmailClientColor_Delete @prmIDs=?, @prmAll=?",
                rs -> rs.next() ? rs.getInt("Deleted") : 0, ids == null ? null : csv(ids), all);
            return n == null ? 0 : n;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    // ---- emails linked to records (Story 5608): Story 5654 -- left out; Echo has no records ----

    @Override
    public List<Map<String, Object>> listInboxes() {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientInbox_List");
    }

    @Override
    public List<Map<String, Object>> listInboxSenders(int inboxId) {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientSenderInbox_ListForInbox @prmGmailClientInboxID=?", inboxId);
    }

    @Override
    public Map<String, Object> saveInbox(int id, String name) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientInbox_Save @prmGmailClientInboxID=?, @prmInboxName=?", id, name);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> deleteInbox(int id) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientInbox_Delete @prmGmailClientInboxID=?", id);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> reorderInboxes(List<Integer> ids) {
        StringBuilder csv = new StringBuilder();
        for (Integer id : ids) {
            if (id == null) continue;
            if (csv.length() > 0) csv.append(',');
            csv.append(id.intValue());
        }
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientInbox_Reorder @prmGmailClientInboxIDs=?", csv.toString());
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public int startCheck(LocalDateTime windowStart) {
        try {
            Integer id = jdbc.query("EXEC dbo.stp_GmailClientCheck_Start @prmWindowStartDate=?",
                rs -> rs.next() ? rs.getInt("GmailClientCheckID") : 0, Timestamp.valueOf(windowStart));
            return id == null ? 0 : id;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> finishCheck(int checkId, int listed, int added, boolean complete, String error) {
        return jdbc.queryForMap("EXEC dbo.stp_GmailClientCheck_Finish @prmGmailClientCheckID=?, @prmMessagesListed=?, "
            + "@prmMessagesAdded=?, @prmIsComplete=?, @prmErrorText=?", checkId, listed, added, complete, error);
    }

    @Override
    public Map<String, Object> lastCheck(boolean completeOnly) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientCheck_GetLast @prmCompleteOnly=?", completeOnly));
    }

    @Override
    public LocalDateTime reach() {
        Timestamp t = jdbc.query("EXEC dbo.stp_GmailClientCheck_GetReach", rs -> rs.next() ? rs.getTimestamp("ReachDate") : null);
        return t == null ? null : t.toLocalDateTime();
    }

    // ---- Sent mail (Story 5596) ---------------------------------------------------------

    private static final String SENT_FILTER_ARGS =
        "@prmSearch=?, @prmTo=?, @prmSubject=?, @prmWords=?, @prmAfter=?, @prmBefore=?, @prmReplyState=?, @prmHasAttachment=?";

    @Override
    public List<Map<String, Object>> listSent(SentFilter f, int offset, int pageSize) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientSent_ListPage " + SENT_FILTER_ARGS + ", @prmOffset=?, @prmPageSize=?",
                f.search(), f.to(), f.subject(), f.words(), f.after(), f.before(), f.replyState(), f.hasAttachment(), offset, pageSize);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> sentCounts(SentFilter f) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientSent_Counts " + SENT_FILTER_ARGS,
                f.search(), f.to(), f.subject(), f.words(), f.after(), f.before(), f.replyState(), f.hasAttachment());
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> getSent(int id) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientSent_Get @prmGmailClientSentMessageID=?", id));
    }

    @Override
    public List<Map<String, Object>> sentReplies(int id) {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientSent_Replies @prmGmailClientSentMessageID=?", id);
    }

    @Override
    public Map<String, Object> recordSent(String gmailId, String threadId, String from, String to, String cc, String bcc,
                                          String subject, String snippet, String messageIdHeader, LocalDateTime sent,
                                          boolean fromGmailClient) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientSent_Record @prmGmailID=?, @prmThreadID=?, @prmFromHeader=?, "
                    + "@prmToHeader=?, @prmCcHeader=?, @prmBccHeader=?, @prmSubject=?, @prmSnippet=?, @prmMessageIDHeader=?, "
                    + "@prmSentDate=?, @prmIsFromGmailClient=?",
                gmailId, threadId, from, to, cc, bcc, subject, snippet, messageIdHeader, Timestamp.valueOf(sent), fromGmailClient);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Set<String> sentKnownSince(LocalDateTime since) {
        Set<String> ids = new HashSet<>();
        jdbc.query("EXEC dbo.stp_GmailClientSent_KnownSince @prmSince=?",
            rs -> { ids.add(rs.getString("GmailID")); }, Timestamp.valueOf(since));
        return ids;
    }

    /** Story 5654: a sent message kept under its send key takes the id Outlook gave it in Sent Items. */
    @Override
    public Integer rekeySent(String oldGmailId, String newGmailId, String threadId, String messageIdHeader) {
        try {
            return jdbc.query("EXEC dbo.stp_GmailClientSent_Rekey @prmOldGmailID=?, @prmNewGmailID=?, @prmThreadID=?, @prmMessageIDHeader=?",
                rs -> rs.next() && rs.getInt("Rekeyed") > 0 ? Integer.valueOf(rs.getInt("GmailClientSentMessageID")) : null,
                oldGmailId, newGmailId, threadId, messageIdHeader);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public int setManySent(List<Integer> ids, String action) {
        return count("EXEC dbo.stp_GmailClientSent_SetMany @prmIDs=?, @prmAction=?", csv(ids), action);
    }

    @Override
    public int setSentFollowUp(List<Integer> ids, String dueDate, String note) {
        return count("EXEC dbo.stp_GmailClientSent_FollowUpSet @prmIDs=?, @prmDueDate=?, @prmNote=?", csv(ids), dueDate, note);
    }

    @Override
    public int sentFollowUpDone(List<Integer> ids) {
        return count("EXEC dbo.stp_GmailClientSent_FollowUpDone @prmIDs=?", csv(ids));
    }

    @Override
    public Map<String, Object> getSentBody(int id) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientSentBody_Get @prmGmailClientSentMessageID=?", id));
    }

    @Override
    public void saveSentBody(int id, String html, String text, String attachmentNames, boolean gone) {
        try {
            jdbc.query("EXEC dbo.stp_GmailClientSentBody_Save @prmGmailClientSentMessageID=?, @prmBodyHtml=?, @prmBodyText=?, "
                    + "@prmAttachmentNames=?, @prmIsGoneFromGmail=?", rs -> null, id, html, text, attachmentNames, gone);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public List<Map<String, Object>> listMissingSentBodies(int top) {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientSentBody_ListMissing @prmTop=?", top);
    }

    // ---- Drafts (Story 5620) ------------------------------------------------------------

    @Override
    public List<Map<String, Object>> listDrafts(String search, int offset, int pageSize) {
        try {
            return jdbc.queryForList("EXEC dbo.stp_GmailClientDraft_ListPage @prmSearch=?, @prmOffset=?, @prmPageSize=?",
                search, offset, pageSize);
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public int draftCount(String search) {
        try {
            Integer n = jdbc.query("EXEC dbo.stp_GmailClientDraft_Counts @prmSearch=?", rs -> rs.next() ? rs.getInt("Cnt") : 0, search);
            return n == null ? 0 : n;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public Map<String, Object> getDraft(int id) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientDraft_Get @prmGmailClientDraftID=?", id));
    }

    @Override
    public Map<String, Object> findDraft(String mode, int sourceId) {
        return first(jdbc.queryForList("EXEC dbo.stp_GmailClientDraft_Find @prmMode=?, @prmSourceID=?", mode, sourceId));
    }

    @Override
    public Map<String, Object> saveDraft(int id, DraftInput in) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientDraft_Save @prmGmailClientDraftID=?, @prmClientKey=?, @prmMode=?, "
                    + "@prmSourceID=?, @prmToHeader=?, @prmCcHeader=?, @prmBccHeader=?, @prmSubject=?, @prmBodyText=?, "
                    + "@prmIncludeOriginal=?, @prmAttachmentsJson=?, @prmAttachmentCount=?",
                id, in.clientKey(), in.mode(), in.sourceId(), in.to(), in.cc(), in.bcc(), in.subject(), in.body(),
                in.includeOriginal(), in.attachmentsJson(), in.attachmentCount());
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    @Override
    public boolean setDraftGmail(int id, String gmailDraftId, String gmailMessageId, String threadId, String inReplyTo,
                                 String references, LocalDateTime pushedAt) {
        return updated("EXEC dbo.stp_GmailClientDraft_SetGmail @prmGmailClientDraftID=?, @prmGmailDraftID=?, @prmGmailMessageID=?, "
                + "@prmThreadID=?, @prmInReplyTo=?, @prmReferences=?, @prmPushedAt=?",
            id, gmailDraftId, gmailMessageId, threadId, inReplyTo, references, Timestamp.valueOf(pushedAt));
    }

    @Override
    public boolean closeDraft(int id, String reason) {
        return updated("EXEC dbo.stp_GmailClientDraft_Close @prmGmailClientDraftID=?, @prmReason=?", id, reason);
    }

    @Override
    public List<Map<String, Object>> draftsInGmail() {
        return jdbc.queryForList("EXEC dbo.stp_GmailClientDraft_GmailList");
    }

    @Override
    public Map<String, Object> draftFromGmail(OutlookClient.DraftContent d, String text) {   // Story 5654: Outlook's Drafts
        try {
            return jdbc.queryForMap("EXEC dbo.stp_GmailClientDraft_FromGmail @prmGmailDraftID=?, @prmGmailMessageID=?, @prmThreadID=?, "
                    + "@prmInReplyTo=?, @prmReferences=?, @prmToHeader=?, @prmCcHeader=?, @prmBccHeader=?, @prmSubject=?, @prmBodyText=?, "
                    + "@prmDraftDate=?",
                d.draftId(), d.version(), d.threadId(), null, null, d.to(), d.cc(), d.bcc(), d.subject(), text,
                d.updated() == null ? null : Timestamp.valueOf(d.updated()));
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    private boolean updated(String sql, Object... args) { return count(sql, args) > 0; }

    /** A proc that answers one row with an Updated count. */
    private int count(String sql, Object... args) {
        try {
            Integer n = jdbc.query(sql, rs -> rs.next() ? rs.getInt("Updated") : 0, args);
            return n == null ? 0 : n;
        } catch (DataAccessException ex) { throw asRule(ex); }
    }

    private static String csv(List<Integer> ids) {
        StringBuilder b = new StringBuilder();
        for (Integer id : ids) {
            if (id == null) continue;
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return b.toString();
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

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
