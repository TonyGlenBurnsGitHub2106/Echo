package com.echo;

import com.echo.SolutionLinkModels.LinkPage;
import com.echo.SolutionLinkModels.LinkTarget;
import com.echo.SolutionLinkModels.SincePage;
import com.echo.SolutionLinkModels.StoredLink;
import com.echo.SolutionLinkModels.Topic;
import com.echo.SolutionLinkModels.TopicDraft;
import com.echo.SolutionLinkModels.TopicPage;
import com.echo.SolutionLinkModels.Visit;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Solution links, topics and "Since you were last here" (2026-11-02). Tony:
 * "Let anything in a solution link to anything else, and gather it by topic ...
 * give each solution a 'since you were last here' card when you open it."
 *
 * Anyone who can see the solution may link, unlink and keep its topics -- a
 * solution shared with an API client gives it full rights inside, as everywhere
 * in the family. Visibility comes first, so a hidden solution, link or topic is
 * "not found" to every verb. The rules (both ends in this solution, no repeat
 * link, no repeat topic name) live in the procs.
 */
@Service
public class SolutionLinkService {

    /** How far back "since" reaches for a member who never had the solution open before. */
    static final int FIRST_VISIT_DAYS = 7;

    private final SolutionLinkRepository links;
    private final SolutionAccess         access;

    public SolutionLinkService(SolutionLinkRepository links, SolutionAccess access) {
        this.links = links; this.access = access;
    }

    // ---- links ----------------------------------------------------------------------

    public LinkPage links(SolutionCaller caller, int solutionId, String kind, int id, int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        return links.links(solutionId, kind(kind), id, Math.max(0, offset), SolutionLinkModels.page(pageSize, 500));
    }

    /** Joins two records; answers with the FROM record's links, the new one among them. */
    public LinkPage link(SolutionCaller caller, int solutionId, String fromKind, int fromId, String toKind, int toId) {
        access.requireSolution(caller, solutionId);
        String from = kind(fromKind), to = kind(toKind);
        links.link(solutionId, from, fromId, to, toId, caller.memberCode());
        return links.links(solutionId, from, fromId, 0, 50);
    }

    /**
     * Story 5695 (Echo): refuses (409, nothing written) a link a new record asks for when its other end is not a
     * record of this solution. Called BEFORE that record is saved.
     */
    public void requireLinkable(int solutionId, String kind, int id) {
        String k = kind(kind);
        if (!links.linkable(solutionId, k, id))
            throw new SolutionRuleException("The " + k + " " + id + " to link to is not in this solution. Nothing was saved.");
    }

    public void unlink(SolutionCaller caller, int solutionId, int linkId) {
        access.requireSolution(caller, solutionId);
        StoredLink l = linkId <= 0 ? null : links.findLink(linkId);
        if (l == null || l.solutionId() != solutionId) throw new SolutionNotFoundException("Link");
        if (!links.unlink(linkId)) throw new SolutionNotFoundException("Link");
    }

    public List<LinkTarget> targets(SolutionCaller caller, int solutionId, String search, String kind, Integer id, int pageSize) {
        return targets(caller, solutionId, search, kind, id, pageSize, null);
    }

    /** Story 5695 (Echo): targetKind (null or blank = any) narrows the picker to one kind -- items to wait on. */
    public List<LinkTarget> targets(SolutionCaller caller, int solutionId, String search, String kind, Integer id, int pageSize, String targetKind) {
        access.requireSolution(caller, solutionId);
        String k = (kind == null || kind.isBlank()) ? null : kind(kind);
        String t = (targetKind == null || targetKind.isBlank()) ? null : kind(targetKind);
        String q = (search == null || search.isBlank()) ? null : search.trim();
        return links.targets(solutionId, q, k, id, SolutionLinkModels.page(pageSize <= 0 ? 30 : pageSize, 100), t);
    }

    // ---- topics ---------------------------------------------------------------------

    public TopicPage topics(SolutionCaller caller, int solutionId, String search, int offset, int pageSize) {
        return topics(caller, solutionId, search, null, offset, pageSize);
    }

    /** Story 5695 (Echo): one kind of topic only (subject, tag, part, assumption); null or blank = every kind. */
    public TopicPage topics(SolutionCaller caller, int solutionId, String search, String kind, int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        String q = (search == null || search.isBlank()) ? null : search.trim();
        String k = topicKind(kind, true);
        int size = SolutionLinkModels.page(pageSize, 500), from = Math.max(0, offset);
        Map<String, Integer> counts = links.topicCounts(solutionId, q, k);
        return new TopicPage(links.topics(solutionId, q, k, from, size), counts, from, size, counts.getOrDefault("total", 0));
    }

    /** Story 5695 (Echo): one of the four kinds of topic, or a 400. blankIsNull: blank means "no change" / "every kind". */
    static String topicKind(String kind, boolean blankIsNull) {
        String k = kind == null ? "" : kind.trim().toLowerCase();
        if (k.isEmpty() && blankIsNull) return null;
        if (!java.util.Set.of("subject", "tag", "part", "assumption").contains(k))
            throw new IllegalArgumentException("A topic's kind is one of subject, tag, part, assumption.");
        return k;
    }

    public Topic topic(SolutionCaller caller, int topicId) {
        Topic t = topicId <= 0 ? null : links.findTopic(topicId);
        if (t == null) throw new SolutionNotFoundException("Topic");
        access.requireSolution(caller, t.solutionId());
        return t;
    }

    public Topic saveTopic(SolutionCaller caller, TopicDraft draft) {
        if (draft.id() > 0) topic(caller, draft.id());
        else access.requireSolution(caller, draft.solutionId());
        topicKind(draft.kindCode(), true);           // Story 5695 (Echo): a bad kind is a 400 before anything is written
        int id = links.saveTopic(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Topic");
        return links.findTopic(id);
    }

    public void deleteTopic(SolutionCaller caller, int topicId) {
        topic(caller, topicId);
        if (!links.deleteTopic(topicId)) throw new SolutionNotFoundException("Topic");
    }

    // ---- since you were last here ---------------------------------------------------

    /** The member opened the solution: the visit is counted, then what changed since the last one. */
    public SincePage visit(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        links.touch(solutionId, caller.memberCode());
        return since(caller, solutionId, 0, 50);
    }

    /** What others changed since the member was last here, newest first, a page at a time. */
    public SincePage since(SolutionCaller caller, int solutionId, int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        Visit v = links.visit(solutionId, caller.memberCode());
        boolean first = v == null || v.previousSeen() == null;
        LocalDateTime since = first ? LocalDateTime.now().minusDays(FIRST_VISIT_DAYS).withNano(0) : v.previousSeen();
        int size = SolutionLinkModels.page(pageSize, 500), from = Math.max(0, offset);
        Map<String, Integer> counts = links.changeCounts(solutionId, since, caller.memberCode());
        return new SincePage(since, first, links.changes(solutionId, since, caller.memberCode(), from, size), counts,
            from, size, counts.getOrDefault("total", 0));
    }

    /** The member has read it all: "since" starts now. */
    public SincePage caughtUp(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        links.caughtUp(solutionId, caller.memberCode());
        return since(caller, solutionId, 0, 50);
    }

    /** One of the eight kinds a link joins, or a 400. */
    static String kind(String kind) {
        String k = kind == null ? "" : kind.trim().toLowerCase();
        if (!SolutionLinkModels.KINDS.contains(k))
            throw new IllegalArgumentException("kind must be one of item, note, log, file, term, topic, question, decision.");
        return k;
    }
}
