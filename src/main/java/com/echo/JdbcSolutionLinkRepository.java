package com.echo;

import com.echo.SolutionLinkModels.Change;
import com.echo.SolutionLinkModels.Link;
import com.echo.SolutionLinkModels.LinkPage;
import com.echo.SolutionLinkModels.LinkTarget;
import com.echo.SolutionLinkModels.StoredLink;
import com.echo.SolutionLinkModels.Topic;
import com.echo.SolutionLinkModels.TopicDraft;
import com.echo.SolutionLinkModels.Visit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionLinkRepository} (2026-11-02): the
 * stp_SolutionLink*, stp_SolutionTopic*, stp_SolutionMemberVisit* and
 * stp_SolutionActivity* procs. Lists and their counts are bound from the same
 * arguments, so they always describe the same set.
 */
@Repository
public class JdbcSolutionLinkRepository implements SolutionLinkRepository {

    private final SolutionJdbc db;

    public JdbcSolutionLinkRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    // ---- links ----

    @Override
    public int link(int solutionId, String fromKind, int fromId, String toKind, int toId, String memberCode) {
        return db.number("EXEC dbo.stp_SolutionLink_Save ?, ?, ?, ?, ?, ?", "SolutionLinkID",
            solutionId, fromKind, fromId, toKind, toId, memberCode);
    }

    @Override
    public StoredLink findLink(int linkId) { return db.one("EXEC dbo.stp_SolutionLink_Get ?", StoredLink::of, linkId); }

    @Override
    public boolean unlink(int linkId) { return db.affected("EXEC dbo.stp_SolutionLink_Delete ?", linkId); }

    @Override
    public boolean linkable(int solutionId, String kind, int id) {
        return db.number("EXEC dbo.stp_SolutionLinkable_Exists ?, ?, ?", "Found", solutionId, kind, id) > 0;
    }

    @Override
    public LinkPage links(int solutionId, String kind, int id, int offset, int pageSize) {
        String sql = "EXEC dbo.stp_SolutionLink_ListForRecord ?, ?, ?, ?, ?";
        List<Map<String, Object>> rows = db.list(sql, r -> r, solutionId, kind, id, offset, pageSize);
        int total = rows.isEmpty() ? 0 : intOf(rows.get(0), "TotalCount");
        if (rows.isEmpty() && offset > 0) {
            Map<String, Object> first = db.row(sql, solutionId, kind, id, 0, 1);
            total = first == null ? 0 : intOf(first, "TotalCount");
        }
        return new LinkPage(kind, id, rows.stream().map(Link::of).toList(), offset, pageSize, total);
    }

    @Override
    public List<LinkTarget> targets(int solutionId, String search, String kind, Integer id, int pageSize, String targetKind) {
        return db.list("EXEC dbo.stp_SolutionLinkTarget_Search ?, ?, ?, ?, ?, ?", LinkTarget::of, solutionId, search, kind, id, pageSize, targetKind);
    }

    // ---- topics ----

    @Override
    public List<Topic> topics(int solutionId, String search, String kind, int offset, int pageSize) {
        return db.list("EXEC dbo.stp_SolutionTopic_ListForSolution ?, ?, ?, ?, ?", Topic::of, solutionId, search, offset, pageSize, kind);
    }

    @Override
    public Map<String, Integer> topicCounts(int solutionId, String search, String kind) {
        return buckets(db.list("EXEC dbo.stp_SolutionTopic_Counts ?, ?, ?", r -> r, solutionId, search, kind));
    }

    @Override
    public Topic findTopic(int topicId) { return db.one("EXEC dbo.stp_SolutionTopic_Get ?", Topic::of, topicId); }

    @Override
    public int saveTopic(TopicDraft d, String memberCode) {
        // Story 5695 (Echo): the kind and what it carries ride after Leonardo's five parameters.
        return db.number("EXEC dbo.stp_SolutionTopic_Save ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionTopicID",
            d.id(), d.solutionId(), d.name(), d.summary(), memberCode, d.kindCode(), d.typeName(), d.statusCode(),
            d.figure(), d.clearFigure() ? 1 : 0, d.holds() == null ? null : (d.holds() ? 1 : 0));
    }

    @Override
    public boolean deleteTopic(int topicId) { return db.affected("EXEC dbo.stp_SolutionTopic_Delete ?", topicId); }

    // ---- since you were last here ----

    @Override
    public Visit touch(int solutionId, String memberCode) {
        return db.one("EXEC dbo.stp_SolutionMemberVisit_Touch ?, ?, ?", Visit::of, solutionId, memberCode, 60);
    }

    @Override
    public Visit visit(int solutionId, String memberCode) {
        return db.one("EXEC dbo.stp_SolutionMemberVisit_Get ?, ?", Visit::of, solutionId, memberCode);
    }

    @Override
    public void caughtUp(int solutionId, String memberCode) {
        db.row("EXEC dbo.stp_SolutionMemberVisit_CaughtUp ?, ?", solutionId, memberCode);
    }

    @Override
    public List<Change> changes(int solutionId, LocalDateTime since, String memberCode, int offset, int pageSize) {
        return db.list("EXEC dbo.stp_SolutionActivity_List ?, ?, ?, ?, ?", Change::of,
            solutionId, Timestamp.valueOf(since), memberCode, offset, pageSize);
    }

    @Override
    public Map<String, Integer> changeCounts(int solutionId, LocalDateTime since, String memberCode) {
        return buckets(db.list("EXEC dbo.stp_SolutionActivity_Counts ?, ?, ?", r -> r, solutionId, Timestamp.valueOf(since), memberCode));
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
