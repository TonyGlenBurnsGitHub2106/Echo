package com.echo;

import com.echo.SolutionDecisionModels.Decision;
import com.echo.SolutionDecisionModels.DecisionDraft;
import com.echo.SolutionDecisionModels.DecisionPage;
import com.echo.SolutionDecisionModels.LinkRef;
import com.echo.SolutionDecisionModels.OpenLink;
import com.echo.SolutionDecisionModels.Question;
import com.echo.SolutionDecisionModels.QuestionDraft;
import com.echo.SolutionDecisionModels.QuestionPage;
import com.echo.SolutionDecisionModels.Saved;
import com.echo.SolutionDecisionModels.Settled;
import com.echo.SolutionDrafts.LogDraft;
import com.echo.SolutionModels.LogEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A solution's questions and decisions (Story 5695, Echo first). From the deep review of 11/02: "Questions
 * and decisions live apart ... The charset question was settled in log entry 505 on October 23. The items
 * 'Decide 837/835 charset' and 'Review charset findings' are still to do." So a decision is a record that
 * can CLOSE things: the open questions and items linked to it.
 *
 * Anyone who can see the solution may ask, answer, decide, supersede and settle -- a solution shared with
 * an API client gives it full rights inside, as everywhere in the family. Visibility comes first, so a
 * hidden question or decision is "not found" to every verb. The record rules live in the procs.
 */
@Service
public class SolutionDecisionService {

    private final SolutionDecisionRepository repo;
    private final SolutionAccess             access;
    private final SolutionCodeCheck          codes;
    private final SolutionItemService        items;
    private final SolutionLogService         log;
    private final SolutionLinkService        links;

    public SolutionDecisionService(SolutionDecisionRepository repo, SolutionAccess access, SolutionCodeCheck codes,
                                   SolutionItemService items, SolutionLogService log, SolutionLinkService links) {
        this.repo = repo; this.access = access; this.codes = codes; this.items = items; this.log = log; this.links = links;
    }

    // ---- questions ----------------------------------------------------------------------

    public QuestionPage questions(SolutionCaller caller, int solutionId, String status, String search, int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        String s = questionStatus(status, true), q = blankToNull(search);
        int size = SolutionDecisionModels.page(pageSize, 500), from = Math.max(0, offset);
        Map<String, Integer> counts = repo.questionCounts(solutionId, s, q);
        return new QuestionPage(repo.questions(solutionId, s, q, from, size), counts, from, size, counts.getOrDefault("total", 0));
    }

    public Question question(SolutionCaller caller, int questionId) {
        Question q = questionId <= 0 ? null : repo.findQuestion(questionId);
        if (q == null) throw new SolutionNotFoundException("Question");
        access.requireSolution(caller, q.solutionId());
        return q;
    }

    /** Adds or merge-edits a question; a new one is linked to every record in linkTo. */
    public Question saveQuestion(SolutionCaller caller, QuestionDraft draft, List<LinkRef> linkTo) {
        int solutionId;
        if (draft.id() > 0) solutionId = question(caller, draft.id()).solutionId();
        else { access.requireSolution(caller, draft.solutionId()); solutionId = draft.solutionId(); }
        if (draft.ownerCode() != null) codes.member(caller, draft.ownerCode());
        if (draft.id() <= 0) { checkLinks(linkTo); requireTargets(solutionId, linkTo); }
        int id = repo.saveQuestion(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Question");
        if (draft.id() <= 0) linkAll(caller, solutionId, "question", id, linkTo);
        return repo.findQuestion(id);
    }

    /** Open, answer (by hand) or drop. Answering through a decision is {@link #settle}. */
    public Question setQuestionStatus(SolutionCaller caller, int questionId, String status) {
        question(caller, questionId);
        String s = questionStatus(status, false);
        if (!repo.setQuestionStatus(questionId, s, null, caller.memberCode())) throw new SolutionNotFoundException("Question");
        return repo.findQuestion(questionId);
    }

    public void deleteQuestion(SolutionCaller caller, int questionId) {
        question(caller, questionId);
        if (!repo.deleteQuestion(questionId)) throw new SolutionNotFoundException("Question");
    }

    /** A journal entry becomes an open question, in place; the two are linked. */
    public Question questionFromLog(SolutionCaller caller, int logId) {
        log.get(caller, logId);
        int id = repo.questionFromLog(logId, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Log entry");
        return repo.findQuestion(id);
    }

    // ---- decisions ----------------------------------------------------------------------

    public DecisionPage decisions(SolutionCaller caller, int solutionId, String status, String search, int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        String s = decisionStatus(status, true), q = blankToNull(search);
        int size = SolutionDecisionModels.page(pageSize, 500), from = Math.max(0, offset);
        Map<String, Integer> counts = repo.decisionCounts(solutionId, s, q);
        return new DecisionPage(repo.decisions(solutionId, s, q, from, size), counts, from, size, counts.getOrDefault("total", 0));
    }

    public Decision decision(SolutionCaller caller, int decisionId) {
        Decision d = decisionId <= 0 ? null : repo.findDecision(decisionId);
        if (d == null) throw new SolutionNotFoundException("Decision");
        access.requireSolution(caller, d.solutionId());
        return d;
    }

    /**
     * Records a decision (with its journal entry) or merge-edits one. A status may be proposed or accepted;
     * superseded comes only from {@link #supersede}. A new decision is linked to every record in linkTo --
     * the questions it answers and the items it settles -- so its View can offer to close them.
     */
    public Decision saveDecision(SolutionCaller caller, DecisionDraft draft, List<LinkRef> linkTo) {
        int solutionId;
        if (draft.id() > 0) solutionId = decision(caller, draft.id()).solutionId();
        else { access.requireSolution(caller, draft.solutionId()); solutionId = draft.solutionId(); }
        if (draft.statusCode() != null && !draft.statusCode().isBlank()) {
            String s = decisionStatus(draft.statusCode(), false);
            if ("superseded".equals(s))
                throw new IllegalArgumentException("A decision is superseded by recording a newer one (supersede), never by setting its status.");
        }
        if (draft.supersedesId() != null && draft.supersedesId() > 0 && draft.id() > 0)
            throw new IllegalArgumentException("Only a new decision can supersede another.");
        if (draft.supersedesId() != null && draft.supersedesId() > 0) decision(caller, draft.supersedesId());
        if (draft.id() <= 0) { checkLinks(linkTo); requireTargets(solutionId, linkTo); }
        Saved saved = repo.saveDecision(draft, caller.memberCode());
        if (saved.id() <= 0) throw new SolutionNotFoundException("Decision");
        if (draft.id() <= 0) linkAll(caller, solutionId, "decision", saved.id(), linkTo);
        return repo.findDecision(saved.id());
    }

    /** A changed mind: a NEW accepted decision that replaces this one, which becomes superseded. */
    public Decision supersede(SolutionCaller caller, int decisionId, DecisionDraft replacement, List<LinkRef> linkTo) {
        Decision old = decision(caller, decisionId);
        if (old.superseded()) throw new SolutionRuleException("That decision was already replaced by a newer one. Nothing was added.");
        DecisionDraft d = new DecisionDraft(0, old.solutionId(), replacement.title(), replacement.context(), replacement.options(),
            replacement.outcome(), "accepted", replacement.decidedDate(), decisionId);
        return saveDecision(caller, d, linkTo);
    }

    public void deleteDecision(SolutionCaller caller, int decisionId) {
        decision(caller, decisionId);
        if (!repo.deleteDecision(decisionId)) throw new SolutionNotFoundException("Decision");
    }

    /** A journal entry (one of the decisions already in a log) becomes a decision, in place; the two are linked. */
    public Decision decisionFromLog(SolutionCaller caller, int logId) {
        log.get(caller, logId);
        int id = repo.decisionFromLog(logId, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Log entry");
        return repo.findDecision(id);
    }

    /** What this decision could close: the linked questions still open and the linked items not closed. */
    public List<OpenLink> openLinks(SolutionCaller caller, int decisionId) {
        decision(caller, decisionId);
        return repo.openLinks(decisionId);
    }

    /**
     * Closes what the decision settles: each named question is answered BY it, each named item is marked
     * done (through the item service, so the wake-the-parents rule holds). Only records that are linked to
     * the decision and still open can be closed -- anything else named is refused before anything changes.
     * One journal entry says what was closed. A proposed decision closes nothing.
     */
    public Settled settle(SolutionCaller caller, int decisionId, List<Integer> questionIds, List<Integer> itemIds) {
        Decision d = decision(caller, decisionId);
        if (d.superseded()) throw new SolutionRuleException("A superseded decision closes nothing. The decision that replaced it does. Nothing was changed.");
        if (!"accepted".equals(d.statusCode())) throw new SolutionRuleException("Accept the decision first. A proposal closes nothing. Nothing was changed.");
        List<OpenLink> open = repo.openLinks(decisionId);
        List<OpenLink> wantQ = pick(open, "question", questionIds), wantI = pick(open, "item", itemIds);
        if (wantQ.isEmpty() && wantI.isEmpty()) throw new IllegalArgumentException("Name at least one open question or item linked to this decision.");

        List<OpenLink> closed = new ArrayList<>();
        for (OpenLink q : wantQ) {
            if (repo.setQuestionStatus(q.id(), "answered", decisionId, caller.memberCode())) closed.add(q);
        }
        for (OpenLink i : wantI) {
            items.setStatus(caller, i.id(), "done");
            closed.add(i);
        }
        // A Result entry, not a Decision: it records what the decision closed, and must not read as a decision itself.
        LogEntry entry = log.save(caller, new LogDraft(0, d.solutionId(), null, null, "result", settledText(d, closed), null));
        List<OpenLink> still = repo.openLinks(decisionId);
        return new Settled(repo.findDecision(decisionId), closed, still, entry == null ? null : entry.id(),
            entry == null ? null : entry.sequenceNumber());
    }

    // ---- the small rules ----------------------------------------------------------------

    /** The named records of one kind, each of which must be in the open list; anything else is a 400. */
    private static List<OpenLink> pick(List<OpenLink> open, String kind, List<Integer> ids) {
        List<OpenLink> out = new ArrayList<>();
        if (ids == null) return out;
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids) {
            if (id == null || !seen.add(id)) continue;
            OpenLink hit = open.stream().filter(o -> kind.equals(o.kind()) && o.id() == id).findFirst().orElse(null);
            if (hit == null)
                throw new IllegalArgumentException("The " + kind + " " + id + " is not an open " + kind + " linked to this decision. Nothing was changed.");
            out.add(hit);
        }
        return out;
    }

    static String settledText(Decision d, List<OpenLink> closed) {
        StringBuilder b = new StringBuilder("**Closed by the decision:** ").append(d.title());
        for (OpenLink c : closed) b.append("\n- ").append("question".equals(c.kind()) ? "Question answered: " : "Item done: ").append(c.title());
        return b.toString();
    }

    /**
     * The links a new record asks for are checked BEFORE it is saved, so a bad one is refused with nothing
     * written -- never a record saved and then a 400 that invites a retry (and a duplicate).
     */
    static void checkLinks(List<LinkRef> linkTo) {
        if (linkTo == null) return;
        for (LinkRef l : linkTo) {
            if (l == null || l.kind() == null || l.id() == null || l.id() <= 0)
                throw new IllegalArgumentException("Each link needs a kind and an id.");
            SolutionLinkService.kind(l.kind());
        }
    }

    /** Each link's other end must be a record of this solution, checked before the save (Story 5695, audit 11/04). */
    private void requireTargets(int solutionId, List<LinkRef> linkTo) {
        if (linkTo == null) return;
        for (LinkRef l : linkTo) links.requireLinkable(solutionId, l.kind(), l.id());
    }

    private void linkAll(SolutionCaller caller, int solutionId, String kind, int id, List<LinkRef> linkTo) {
        if (linkTo == null) return;
        for (LinkRef l : linkTo) {
            if (l == null || l.kind() == null || l.id() == null) continue;
            links.link(caller, solutionId, kind, id, l.kind(), l.id());
        }
    }

    static String questionStatus(String status, boolean blankIsAll) {
        String s = status == null ? "" : status.trim().toLowerCase();
        if (s.isEmpty() && blankIsAll) return null;
        if (!SolutionDecisionModels.QUESTION_STATUSES.contains(s))
            throw new IllegalArgumentException("A question's status is one of open, answered, dropped.");
        return s;
    }

    /** blankIsAll: a LIST filter -- blank is every decision, and "current" is every one not superseded. */
    static String decisionStatus(String status, boolean blankIsAll) {
        String s = status == null ? "" : status.trim().toLowerCase();
        if (s.isEmpty() && blankIsAll) return null;
        if (blankIsAll && "current".equals(s)) return s;
        if (!SolutionDecisionModels.DECISION_STATUSES.contains(s))
            throw new IllegalArgumentException("A decision's status is one of proposed, accepted, superseded.");
        return s;
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
