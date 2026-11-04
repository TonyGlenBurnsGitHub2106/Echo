package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.idOrNull;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The shapes of a solution's questions and decisions (Story 5695, Echo first). A question is open until a
 * decision answers it (or it is answered by hand, or dropped). A decision has a title, its context, the
 * options weighed and the outcome, and a status; a changed mind is a NEW decision that supersedes the old
 * one, never an edit. Both are linkable records, kinds "question" and "decision".
 */
final class SolutionDecisionModels {

    private SolutionDecisionModels() {}

    static final Set<String> QUESTION_STATUSES = Set.of("open", "answered", "dropped");
    static final Set<String> DECISION_STATUSES = Set.of("proposed", "accepted", "superseded");

    /** One question. In a list, detail is a 300-character preview (truncated says so). */
    public record Question(int id, int solutionId, String text, String detail, boolean truncated, String statusCode,
                           String askedByCode, String askedByName, String ownerCode, String ownerName,
                           Integer answeredByDecisionId, String answeredByTitle, LocalDateTime answeredDate,
                           Integer logId, Integer logSequenceNumber, int linkCount,
                           String modifiedBy, LocalDateTime createdDate, LocalDateTime modifiedDate) {
        static Question of(Map<String, Object> r) {
            return new Question(intOf(r, "SolutionQuestionID"), intOf(r, "SolutionID"), strOf(r, "QuestionText"),
                strOf(r, "QuestionDetail"), boolOf(r, "Truncated"), strOf(r, "StatusCode"),
                strOf(r, "AskedByCode"), strOf(r, "AskedByName"), strOf(r, "OwnerCode"), strOf(r, "OwnerName"),
                idOrNull(r, "AnsweredBySolutionDecisionID"), strOf(r, "AnsweredByTitle"), dateOf(r, "AnsweredDate"),
                idOrNull(r, "SolutionLogID"), idOrNull(r, "LogSequenceNumber"), intOf(r, "LinkCount"),
                strOf(r, "ModifiedBy"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"));
        }
        boolean open() { return "open".equals(statusCode); }
    }

    /** An add (id 0) or a merge-edit: a null field keeps what the question has; "" clears detail or owner. */
    public record QuestionDraft(int id, int solutionId, String text, String detail, String ownerCode) {}

    public record QuestionPage(List<Question> questions, Map<String, Integer> counts, int offset, int pageSize, int total) {}

    /** One decision. In a list, outcome is a 300-character preview and context / options are left out (truncated). */
    public record Decision(int id, int solutionId, String title, String context, String options, String outcome, boolean truncated,
                           String statusCode, LocalDateTime decidedDate, String decidedByCode, String decidedByName,
                           Integer logId, Integer logSequenceNumber,
                           Integer supersededById, String supersededByTitle, Integer supersedesId, String supersedesTitle,
                           int answeredCount, int linkCount,
                           String modifiedBy, LocalDateTime createdDate, LocalDateTime modifiedDate) {
        static Decision of(Map<String, Object> r) {
            return new Decision(intOf(r, "SolutionDecisionID"), intOf(r, "SolutionID"), strOf(r, "DecisionTitle"),
                strOf(r, "Context"), strOf(r, "OptionsText"), strOf(r, "Outcome"), boolOf(r, "Truncated"),
                strOf(r, "StatusCode"), dateOf(r, "DecidedDate"), strOf(r, "DecidedByCode"), strOf(r, "DecidedByName"),
                idOrNull(r, "SolutionLogID"), idOrNull(r, "LogSequenceNumber"),
                idOrNull(r, "SupersededBySolutionDecisionID"), strOf(r, "SupersededByTitle"),
                idOrNull(r, "SupersedesSolutionDecisionID"), strOf(r, "SupersedesTitle"),
                intOf(r, "AnsweredCount"), intOf(r, "LinkCount"),
                strOf(r, "ModifiedBy"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"));
        }
        boolean superseded() { return "superseded".equals(statusCode); }
    }

    /**
     * An add (id 0) or a merge-edit: a null field keeps what the decision has; "" clears context, options
     * or outcome. supersedesId (an add only) names the decision this one replaces.
     */
    public record DecisionDraft(int id, int solutionId, String title, String context, String options, String outcome,
                                String statusCode, LocalDateTime decidedDate, Integer supersedesId) {}

    public record DecisionPage(List<Decision> decisions, Map<String, Integer> counts, int offset, int pageSize, int total) {}

    /** What a save or a promote made: the record's id and, for a decision, the journal entry that records it. */
    public record Saved(int id, Integer logId) {
        static Saved of(Map<String, Object> r, String idColumn) {
            return r == null ? new Saved(0, null) : new Saved(intOf(r, idColumn), idOrNull(r, "SolutionLogID"));
        }
    }

    /** A record linked to a decision that is still open -- what the decision could close. */
    public record OpenLink(String kind, int id, String title, String sub) {
        static OpenLink of(Map<String, Object> r) {
            return new OpenLink(strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"));
        }
    }

    /** One record to link a new question or decision to, as it is saved. */
    public record LinkRef(String kind, Integer id) {}

    /** What Settle closed, and the journal entry that says so (null when nothing was closed). */
    public record Settled(Decision decision, List<OpenLink> closed, List<OpenLink> stillOpen, Integer logId, Integer logSequenceNumber) {}

    static int page(int pageSize, int max) { return pageSize <= 0 ? 50 : Math.min(pageSize, max); }
}
