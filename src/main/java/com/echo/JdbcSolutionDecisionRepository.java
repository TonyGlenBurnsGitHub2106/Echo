package com.echo;

import com.echo.SolutionDecisionModels.Decision;
import com.echo.SolutionDecisionModels.DecisionDraft;
import com.echo.SolutionDecisionModels.OpenLink;
import com.echo.SolutionDecisionModels.Question;
import com.echo.SolutionDecisionModels.QuestionDraft;
import com.echo.SolutionDecisionModels.Saved;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionDecisionRepository} (Story 5695): the stp_SolutionQuestion* and
 * stp_SolutionDecision* procs.
 */
@Repository
public class JdbcSolutionDecisionRepository implements SolutionDecisionRepository {

    private final SolutionJdbc db;

    public JdbcSolutionDecisionRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    // ---- questions ----

    @Override
    public List<Question> questions(int solutionId, String statusCode, String search, int offset, int pageSize) {
        return db.list("EXEC dbo.stp_SolutionQuestion_ListForSolution ?, ?, ?, ?, ?", Question::of,
            solutionId, statusCode, search, offset, pageSize);
    }

    @Override
    public Map<String, Integer> questionCounts(int solutionId, String statusCode, String search) {
        return buckets(db.list("EXEC dbo.stp_SolutionQuestion_Counts ?, ?, ?", r -> r, solutionId, statusCode, search));
    }

    @Override
    public Question findQuestion(int questionId) {
        return db.one("EXEC dbo.stp_SolutionQuestion_Get ?", Question::of, questionId);
    }

    @Override
    public int saveQuestion(QuestionDraft d, String memberCode) {
        return db.number("EXEC dbo.stp_SolutionQuestion_Save ?, ?, ?, ?, ?, ?", "SolutionQuestionID",
            d.id(), d.solutionId(), d.text(), d.detail(), d.ownerCode(), memberCode);
    }

    @Override
    public boolean setQuestionStatus(int questionId, String statusCode, Integer answeredByDecisionId, String memberCode) {
        return db.affected("EXEC dbo.stp_SolutionQuestion_SetStatus ?, ?, ?, ?", questionId, statusCode, answeredByDecisionId, memberCode);
    }

    @Override
    public boolean deleteQuestion(int questionId) {
        return db.affected("EXEC dbo.stp_SolutionQuestion_Delete ?", questionId);
    }

    @Override
    public int questionFromLog(int logId, String memberCode) {
        return db.number("EXEC dbo.stp_SolutionQuestion_FromLog ?, ?", "SolutionQuestionID", logId, memberCode);
    }

    // ---- decisions ----

    @Override
    public List<Decision> decisions(int solutionId, String statusCode, String search, int offset, int pageSize) {
        return db.list("EXEC dbo.stp_SolutionDecision_ListForSolution ?, ?, ?, ?, ?", Decision::of,
            solutionId, statusCode, search, offset, pageSize);
    }

    @Override
    public Map<String, Integer> decisionCounts(int solutionId, String statusCode, String search) {
        return buckets(db.list("EXEC dbo.stp_SolutionDecision_Counts ?, ?, ?", r -> r, solutionId, statusCode, search));
    }

    @Override
    public Decision findDecision(int decisionId) {
        return db.one("EXEC dbo.stp_SolutionDecision_Get ?", Decision::of, decisionId);
    }

    @Override
    public Saved saveDecision(DecisionDraft d, String memberCode) {
        return Saved.of(db.row("EXEC dbo.stp_SolutionDecision_Save ?, ?, ?, ?, ?, ?, ?, ?, ?, ?",
            d.id(), d.solutionId(), d.title(), d.context(), d.options(), d.outcome(), d.statusCode(),
            d.decidedDate() == null ? null : Timestamp.valueOf(d.decidedDate()), memberCode, d.supersedesId()), "SolutionDecisionID");
    }

    @Override
    public boolean deleteDecision(int decisionId) {
        return db.affected("EXEC dbo.stp_SolutionDecision_Delete ?", decisionId);
    }

    @Override
    public int decisionFromLog(int logId, String memberCode) {
        return db.number("EXEC dbo.stp_SolutionDecision_FromLog ?, ?", "SolutionDecisionID", logId, memberCode);
    }

    @Override
    public List<OpenLink> openLinks(int decisionId) {
        return db.list("EXEC dbo.stp_SolutionDecision_OpenLinks ?", OpenLink::of, decisionId);
    }

    /** Bucket / BucketKey / Cnt rows -> "bucket" or "bucket:key". */
    private static Map<String, Integer> buckets(List<Map<String, Object>> rows) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = strOf(r, "BucketKey");
            out.put((key == null || key.isEmpty()) ? strOf(r, "Bucket") : strOf(r, "Bucket") + ":" + key, intOf(r, "Cnt"));
        }
        return out;
    }
}
