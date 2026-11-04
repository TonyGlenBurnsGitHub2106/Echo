package com.echo;

import com.echo.SolutionDecisionModels.Decision;
import com.echo.SolutionDecisionModels.DecisionDraft;
import com.echo.SolutionDecisionModels.OpenLink;
import com.echo.SolutionDecisionModels.Question;
import com.echo.SolutionDecisionModels.QuestionDraft;
import com.echo.SolutionDecisionModels.Saved;

import java.util.List;
import java.util.Map;

/**
 * Storage for a solution's questions and decisions (Story 5695): the stp_SolutionQuestion* and
 * stp_SolutionDecision* procs. Every refusal is a {@link SolutionRuleException} carrying the proc's own
 * sentence. Lists and their counts are bound from the same arguments, so they describe the same set.
 */
public interface SolutionDecisionRepository {

    // ---- questions ----

    List<Question> questions(int solutionId, String statusCode, String search, int offset, int pageSize);

    /** "total" (what matches), "all", and "status:&lt;code&gt;". */
    Map<String, Integer> questionCounts(int solutionId, String statusCode, String search);

    Question findQuestion(int questionId);

    int saveQuestion(QuestionDraft draft, String memberCode);

    /** answeredByDecisionId may be null (answered by hand). */
    boolean setQuestionStatus(int questionId, String statusCode, Integer answeredByDecisionId, String memberCode);

    boolean deleteQuestion(int questionId);

    int questionFromLog(int logId, String memberCode);

    // ---- decisions ----

    List<Decision> decisions(int solutionId, String statusCode, String search, int offset, int pageSize);

    Map<String, Integer> decisionCounts(int solutionId, String statusCode, String search);

    Decision findDecision(int decisionId);

    /** An add also writes the journal entry that records it (and supersedes the named decision). */
    Saved saveDecision(DecisionDraft draft, String memberCode);

    boolean deleteDecision(int decisionId);

    int decisionFromLog(int logId, String memberCode);

    /** The records linked to a decision that are still open: open questions, items not closed. */
    List<OpenLink> openLinks(int decisionId);
}
