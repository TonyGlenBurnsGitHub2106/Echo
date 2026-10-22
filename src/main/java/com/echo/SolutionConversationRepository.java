package com.echo;

import com.echo.SolutionModels.ConversationSummary;
import com.echo.SolutionModels.Message;

import java.util.List;

/**
 * Storage for the conversation threads linked to a solution (Story 3493). The
 * threads live in the ordinary Conversation tables; this family only links to
 * them, reads them, and appends to them.
 *
 * There is deliberately NO way through this interface to mark a message as
 * awaiting 'claude'. Nothing in the Solutions family may wake the AI: an outside
 * agent posting a question is a machine, not a human typing, and the house rule
 * is that only a human does that.
 */
public interface SolutionConversationRepository {

    List<ConversationSummary> listForSolution(int solutionId);

    /** The solutions a conversation is linked to; empty when it is not part of this family. */
    List<Integer> solutionIdsOf(int conversationId);

    /** Open a new thread linked to the solution; returns the conversation id. */
    int create(int solutionId, String title);

    boolean unlink(int solutionId, int conversationId);

    boolean rename(int conversationId, String title);

    boolean editMessage(int conversationId, int messageId, String body);

    boolean deleteMessage(int conversationId, int messageId);

    /** Messages newer than {@code sinceMessageId}, oldest first. */
    List<Message> messagesSince(int conversationId, int sinceMessageId);

    /** @param awaitingFrom who should read this next, or null. Never 'claude'. */
    int addMessage(int conversationId, String sender, String body, String awaitingFrom);

    /** The named party has read the thread. */
    void clearAwaitingFor(int conversationId, String party);

    /**
     * Resolve (status 'closed', every waiting flag cleared) or reopen ('active') a thread.
     * Shares stp_Conversation_SetStatus with the Conversations page.
     */
    boolean setStatus(int conversationId, String status, String by);
}
