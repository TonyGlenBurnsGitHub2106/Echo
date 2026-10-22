package com.echo;

import com.echo.ApiClientModels.SolutionLink;
import com.echo.SolutionModels.ConversationSummary;
import com.echo.SolutionModels.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The conversation between an outside agent and the house (Story 3493).
 *
 * It is a relay with a human in the middle, by design. When an API client posts,
 * the message is marked as awaiting TONY -- never 'claude'. Stewart's poller
 * wakes the AI on any row awaiting 'claude' no matter who wrote it, and a
 * machine's post is not a human's request, so marking it that way would be a
 * service triggering Claude. Tony sees the flag, tells Felix or Stewart to
 * answer, and the reply is marked as awaiting the client, which reads it on its
 * next poll.
 *
 * A conversation is reachable here only if it is linked to a solution the caller
 * may see; every other thread in Leonardo does not exist as far as this service
 * is concerned.
 */
@Service
public class SolutionConversationService {

    static final String HUMAN_PARTY = "tony";
    static final String AI_PARTY    = "claude";

    /** Conversation.Status values, shared with the Conversations page. */
    static final String STATUS_RESOLVED = "closed";
    static final String STATUS_OPEN     = "active";

    /**
     * Story 5571: a member code that starts with this belongs to a TEST -- the Playwright
     * client zz-pwtest, its house actor zz-pwtest-house, the zz-audit and zz-probe scripts.
     * A test's message never waits on a person: it is stored awaiting nobody, so a test run
     * can never put a your-turn thread in front of Tony (or, through a reply, Stewart).
     */
    static final String TEST_CODE_PREFIX = "zz-";

    static boolean isTestParty(String memberCode) {
        return memberCode != null && memberCode.trim().toLowerCase().startsWith(TEST_CODE_PREFIX);
    }

    /** Who a new message waits on, after the test guard: a test never waits on tony or claude. */
    static String guardAwaiting(String senderCode, String awaiting) {
        if (awaiting == null || !isTestParty(senderCode)) return awaiting;
        String a = awaiting.trim().toLowerCase();
        return (HUMAN_PARTY.equals(a) || AI_PARTY.equals(a)) ? null : awaiting;
    }

    private final SolutionConversationRepository repo;
    private final ApiClientService               clients;
    private final SolutionAccess                 access;

    public SolutionConversationService(SolutionConversationRepository repo, ApiClientService clients, SolutionAccess access) {
        this.repo = repo; this.clients = clients; this.access = access;
    }

    public List<ConversationSummary> list(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.listForSolution(solutionId);
    }

    public ConversationSummary create(SolutionCaller caller, int solutionId, String title, String firstMessage) {
        access.requireSolution(caller, solutionId);
        int conversationId = repo.create(solutionId, title);
        if (firstMessage != null && !firstMessage.isBlank()) post(caller, conversationId, firstMessage);
        return repo.listForSolution(solutionId).stream()
            .filter(c -> c.conversationId() == conversationId).findFirst()
            .orElseThrow(() -> new SolutionNotFoundException("Conversation"));
    }

    /** Messages newer than {@code sinceMessageId}. Reading marks the thread as read for the reader's side. */
    public List<Message> messages(SolutionCaller caller, int conversationId, int sinceMessageId) {
        requireConversation(caller, conversationId);
        List<Message> out = repo.messagesSince(conversationId, Math.max(0, sinceMessageId));
        if (caller.isClient()) repo.clearAwaitingFor(conversationId, caller.memberCode());
        else if (caller.kind() == SolutionCaller.Kind.USER) repo.clearAwaitingFor(conversationId, HUMAN_PARTY);
        return out;
    }

    public Message post(SolutionCaller caller, int conversationId, String body) {
        int solutionId = requireConversation(caller, conversationId);
        if (body == null || body.isBlank()) throw new IllegalArgumentException("A message needs some text.");
        String awaiting;
        if (caller.isClient()) {
            awaiting = HUMAN_PARTY;
        } else {
            awaiting = firstClientOf(solutionId);
            repo.clearAwaitingFor(conversationId, HUMAN_PARTY);   // the house has answered
        }
        awaiting = guardAwaiting(caller.memberCode(), awaiting);
        // A new message on a resolved thread opens it again, so nothing waits unseen.
        if (isResolved(solutionId, conversationId)) repo.setStatus(conversationId, STATUS_OPEN, caller.memberCode());
        int id =repo.addMessage(conversationId, caller.memberCode(), body.trim(), awaiting);
        return repo.messagesSince(conversationId, id - 1).stream().filter(m -> m.id() == id).findFirst()
            .orElseThrow(() -> new SolutionNotFoundException("Message"));
    }

    // Undoing and correcting are the owner's: a thread is the record of what was asked and
    // answered, and an outside agent may only add to it. A person may rename a thread, and
    // change or remove any message in it -- their own or the agent's.

    public ConversationSummary rename(SolutionCaller caller, int conversationId, String title) {
        int solutionId = requireConversation(caller, conversationId);
        access.requireHuman(caller, "Only a person may rename a conversation.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A conversation needs a title.");
        if (!repo.rename(conversationId, title.trim())) throw new SolutionNotFoundException("Conversation");
        return repo.listForSolution(solutionId).stream().filter(c -> c.conversationId() == conversationId).findFirst()
            .orElseThrow(() -> new SolutionNotFoundException("Conversation"));
    }

    /**
     * Owner only: the thread is finished (resolved = true) -- nobody is waited on any more and
     * the list shows it as Resolved -- or it is opened again (false). Messages are untouched.
     */
    public ConversationSummary resolve(SolutionCaller caller, int conversationId, boolean resolved) {
        int solutionId = requireConversation(caller, conversationId);
        access.requireHuman(caller, "Only a person may resolve or reopen a conversation.");
        if (!repo.setStatus(conversationId, resolved ? STATUS_RESOLVED : STATUS_OPEN, caller.memberCode()))
            throw new SolutionNotFoundException("Conversation");
        return summaryOf(solutionId, conversationId);
    }

    private boolean isResolved(int solutionId, int conversationId) {
        return repo.listForSolution(solutionId).stream()
            .anyMatch(c -> c.conversationId() == conversationId && STATUS_RESOLVED.equals(c.status()));
    }

    private ConversationSummary summaryOf(int solutionId, int conversationId) {
        return repo.listForSolution(solutionId).stream().filter(c -> c.conversationId() == conversationId).findFirst()
            .orElseThrow(() -> new SolutionNotFoundException("Conversation"));
    }

    public Message editMessage(SolutionCaller caller, int conversationId, int messageId, String body) {
        requireConversation(caller, conversationId);
        access.requireHuman(caller, "Only a person may change a message.");
        if (body == null || body.isBlank()) throw new IllegalArgumentException("A message needs some text.");
        if (!repo.editMessage(conversationId, messageId, body.trim())) throw new SolutionNotFoundException("Message");
        return repo.messagesSince(conversationId, messageId - 1).stream().filter(m -> m.id() == messageId).findFirst()
            .orElseThrow(() -> new SolutionNotFoundException("Message"));
    }

    public void deleteMessage(SolutionCaller caller, int conversationId, int messageId) {
        requireConversation(caller, conversationId);
        access.requireHuman(caller, "Only a person may remove a message.");
        if (!repo.deleteMessage(conversationId, messageId)) throw new SolutionNotFoundException("Message");
    }

    public void unlink(SolutionCaller caller, int solutionId, int conversationId) {
        access.requireSolution(caller, solutionId);
        if (!repo.unlink(solutionId, conversationId)) throw new SolutionNotFoundException("Conversation");
    }

    /** The id of a solution this conversation belongs to AND the caller may see; otherwise "not found". */
    private int requireConversation(SolutionCaller caller, int conversationId) {
        if (conversationId > 0) {
            for (int solutionId : repo.solutionIdsOf(conversationId)) {
                try {
                    access.requireSolution(caller, solutionId);
                    return solutionId;
                } catch (SolutionNotFoundException hidden) { /* try the next link */ }
            }
        }
        throw new SolutionNotFoundException("Conversation");
    }

    private String firstClientOf(int solutionId) {
        return clients.clientsForSolution(solutionId).stream().map(SolutionLink::clientCode)
            .filter(c -> c != null && !c.isBlank()).findFirst().orElse(null);
    }
}
