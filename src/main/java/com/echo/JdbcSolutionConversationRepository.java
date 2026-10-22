package com.echo;

import com.echo.SolutionModels.ConversationSummary;
import com.echo.SolutionModels.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

import static com.echo.SolutionRows.intOf;

/**
 * SQL Server backed {@link SolutionConversationRepository} (Story 3493): the
 * stp_SolutionConversation* procs, plus the shared stp_ConversationMessage_Save
 * every conversation surface writes through.
 *
 * {@link #addMessage} refuses 'claude' as the awaited party. That flag is what
 * Stewart's on-demand poller wakes on, and only a human typing may set it.
 */
@Repository
public class JdbcSolutionConversationRepository implements SolutionConversationRepository {

    private final SolutionJdbc db;

    public JdbcSolutionConversationRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<ConversationSummary> listForSolution(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionConversation_ListForSolution ?", ConversationSummary::of, solutionId);
    }

    @Override
    public List<Integer> solutionIdsOf(int conversationId) {
        return db.list("EXEC dbo.stp_SolutionConversation_Locate ?", r -> intOf(r, "SolutionID"), conversationId);
    }

    @Override
    public int create(int solutionId, String title) {
        return db.number("EXEC dbo.stp_SolutionConversation_Create ?, ?", "ConversationID", solutionId, title);
    }

    @Override
    public boolean unlink(int solutionId, int conversationId) {
        return db.affected("EXEC dbo.stp_SolutionConversation_Remove ?, ?", solutionId, conversationId);
    }

    @Override
    public boolean rename(int conversationId, String title) {
        return db.affected("EXEC dbo.stp_SolutionConversation_Rename ?, ?", conversationId, title);
    }

    @Override
    public boolean editMessage(int conversationId, int messageId, String body) {
        if (!belongs(conversationId, messageId)) return false;
        return db.affected("EXEC dbo.stp_ConversationMessage_EditBody ?, ?", messageId, body);
    }

    @Override
    public boolean deleteMessage(int conversationId, int messageId) {
        if (!belongs(conversationId, messageId)) return false;
        return db.affected("EXEC dbo.stp_ConversationMessage_Delete ?", messageId);
    }

    /** A message id from one thread must never reach another thread's message. */
    private boolean belongs(int conversationId, int messageId) {
        return messagesSince(conversationId, messageId - 1).stream().anyMatch(m -> m.id() == messageId);
    }

    @Override
    public List<Message> messagesSince(int conversationId, int sinceMessageId) {
        return db.list("EXEC dbo.stp_ConversationMessage_ListSince ?, ?", Message::of, conversationId, sinceMessageId);
    }

    @Override
    public int addMessage(int conversationId, String sender, String body, String awaitingFrom) {
        if (awaitingFrom != null && "claude".equalsIgnoreCase(awaitingFrom.trim()))
            throw new IllegalArgumentException("A Solutions message may never await claude.");
        return db.number(
            "EXEC dbo.stp_ConversationMessage_Save @prmConversationMessageID=?, @prmConversationID=?, @prmSender=?, " +
                "@prmBody=?, @prmAwaitingResponseFrom=?, @prmTokensIn=?, @prmTokensOut=?, @prmDurationMs=?, " +
                "@prmModelUsed=?, @prmErrorText=?",
            "ConversationMessageID",
            0, conversationId, sender, body, awaitingFrom == null ? "" : awaitingFrom.trim().toLowerCase(), 0, 0, 0, "", "");
    }

    @Override
    public void clearAwaitingFor(int conversationId, String party) {
        db.affected("EXEC dbo.stp_ConversationMessage_ClearAwaitingFor ?, ?", conversationId, party);
    }

    @Override
    public boolean setStatus(int conversationId, String status, String by) {
        // stp_Conversation_SetStatus returns no row count; the caller has already proved the thread exists.
        db.run("EXEC dbo.stp_Conversation_SetStatus ?, ?, ?", conversationId, status, by);
        return true;
    }
}
