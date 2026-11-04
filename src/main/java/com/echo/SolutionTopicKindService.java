package com.echo;

import com.echo.SolutionDrafts.LogDraft;
import com.echo.SolutionLinkModels.Topic;
import com.echo.SolutionLinkModels.TopicDraft;
import com.echo.SolutionModels.LogEntry;
import com.echo.SolutionTopicKindModels.OpenItem;
import com.echo.SolutionTopicKindModels.ProvedFalse;
import com.echo.SolutionTopicKindModels.TopicType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Topic kinds (Story 5695, Echo first): the kinds of part a solution is made of, the order of its parts, and
 * the PREMISE SWITCH. From the deep review of 11/02: "When the 837 path died, 'Cancelled 16 Oct 2026: ...' was
 * pasted into 20 items across 5 projects." An assumption is a topic; the items that rest on it are linked to it;
 * proving it false cancels the chosen ones in one act, with one reason, and the journal says so.
 *
 * Anyone who can see the solution may do all of it -- an API client a solution is shared with included.
 */
@Service
public class SolutionTopicKindService {

    private final SolutionTopicKindRepository repo;
    private final SolutionLinkService         links;
    private final SolutionItemService         items;
    private final SolutionLogService          log;
    private final SolutionAccess              access;

    public SolutionTopicKindService(SolutionTopicKindRepository repo, SolutionLinkService links, SolutionItemService items,
                                    SolutionLogService log, SolutionAccess access) {
        this.repo = repo; this.links = links; this.items = items; this.log = log; this.access = access;
    }

    // ---- kinds of part ----------------------------------------------------------------

    public List<TopicType> types(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.types(solutionId);
    }

    public TopicType type(SolutionCaller caller, int typeId) {
        TopicType t = typeId <= 0 ? null : repo.findType(typeId);
        if (t == null) throw new SolutionNotFoundException("Kind of part");
        access.requireSolution(caller, t.solutionId());
        return t;
    }

    /** Adds (or finds by name) a kind of part in the solution. */
    public TopicType addType(SolutionCaller caller, int solutionId, String name, String figureLabel) {
        access.requireSolution(caller, solutionId);
        int id = repo.saveType(0, solutionId, name, figureLabel);
        if (id <= 0) throw new SolutionNotFoundException("Kind of part");
        return repo.findType(id);
    }

    /** Renames a kind of part and / or changes what its number counts (null keeps, "" clears the label). */
    public TopicType updateType(SolutionCaller caller, int typeId, String name, String figureLabel) {
        type(caller, typeId);
        if (repo.saveType(typeId, 0, name, figureLabel) <= 0) throw new SolutionNotFoundException("Kind of part");
        return repo.findType(typeId);
    }

    /** Removes a kind of part; its parts stay, with no kind. */
    public void deleteType(SolutionCaller caller, int typeId) {
        type(caller, typeId);
        if (!repo.deleteType(typeId)) throw new SolutionNotFoundException("Kind of part");
    }

    // ---- the order of the parts ---------------------------------------------------------

    public void reorder(SolutionCaller caller, int solutionId, List<Integer> topicIds) {
        access.requireSolution(caller, solutionId);
        if (topicIds == null || topicIds.isEmpty()) throw new IllegalArgumentException("Name the topics in their new order.");
        for (Integer id : topicIds) if (id == null || id <= 0) throw new IllegalArgumentException("The order is a list of topic ids.");
        repo.reorder(solutionId, topicIds);
    }

    // ---- the premise switch -------------------------------------------------------------

    /** The open items linked to a topic -- for an assumption, the work that rests on it. */
    public List<OpenItem> openItems(SolutionCaller caller, int topicId) {
        links.topic(caller, topicId);
        return repo.openItems(topicId);
    }

    /**
     * The assumption is proved false: it is marked so, each named item is cancelled (status Skipped, through the
     * item service), and one journal entry -- linked to the assumption -- records the reason and what went. Only
     * items linked to it and still open can be named; anything else is refused before anything changes.
     */
    public ProvedFalse proveFalse(SolutionCaller caller, int topicId, String reason, List<Integer> itemIds) {
        Topic t = links.topic(caller, topicId);
        if (!"assumption".equals(t.kindCode())) throw new SolutionRuleException("Only an assumption can be proved false. Nothing was changed.");
        if (Boolean.FALSE.equals(t.holds())) throw new SolutionRuleException("That assumption was already proved false. Nothing was changed.");
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw new IllegalArgumentException("Say why it no longer holds. The journal keeps the reason.");
        List<OpenItem> open = repo.openItems(topicId);
        List<OpenItem> chosen = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (itemIds != null) for (Integer id : itemIds) {
            if (id == null || !seen.add(id)) continue;
            OpenItem hit = open.stream().filter(o -> o.id() == id).findFirst().orElse(null);
            if (hit == null) throw new IllegalArgumentException("The item " + id + " is not an open item resting on this assumption. Nothing was changed.");
            chosen.add(hit);
        }

        links.saveTopic(caller, new TopicDraft(topicId, 0, null, null, null, null, null, null, false, false));
        for (OpenItem i : chosen) items.setStatus(caller, i.id(), "cancelled");
        LogEntry entry = log.save(caller, new LogDraft(0, t.solutionId(), null, null, "result", provedFalseText(t, why, chosen), null));
        if (entry != null) links.link(caller, t.solutionId(), "log", entry.id(), "topic", topicId);
        return new ProvedFalse(links.topic(caller, topicId), chosen, repo.openItems(topicId),
            entry == null ? null : entry.id(), entry == null ? null : entry.sequenceNumber());
    }

    static String provedFalseText(Topic t, String reason, List<OpenItem> cancelled) {
        StringBuilder b = new StringBuilder("**Assumption proved false:** ").append(t.name()).append("\n\n").append(reason);
        if (!cancelled.isEmpty()) {
            b.append("\n\nCancelled because of it:");
            for (OpenItem i : cancelled) b.append("\n- ").append(i.title());
        }
        return b.toString();
    }
}
