package com.echo;

import com.echo.SolutionLinkModels.Change;
import com.echo.SolutionLinkModels.LinkPage;
import com.echo.SolutionLinkModels.LinkTarget;
import com.echo.SolutionLinkModels.StoredLink;
import com.echo.SolutionLinkModels.Topic;
import com.echo.SolutionLinkModels.TopicDraft;
import com.echo.SolutionLinkModels.Visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Storage for Solution links, topics and "Since you were last here"
 * (2026-11-02). Jdbc = stp_SolutionLink*, stp_SolutionTopic*,
 * stp_SolutionMemberVisit*, stp_SolutionActivity*. Every refusal is a
 * {@link SolutionRuleException} carrying the proc's own sentence.
 */
public interface SolutionLinkRepository {

    // ---- links ----

    /** Joins two records of one solution; returns the link's id. */
    int link(int solutionId, String fromKind, int fromId, String toKind, int toId, String memberCode);

    StoredLink findLink(int linkId);

    boolean unlink(int linkId);

    /** The other ends of every link of one record, a page at a time, and how many there are in all. */
    LinkPage links(int solutionId, String kind, int id, int offset, int pageSize);

    List<LinkTarget> targets(int solutionId, String search, String kind, Integer id, int pageSize);

    // ---- topics ----

    List<Topic> topics(int solutionId, String search, int offset, int pageSize);

    /** "total" (what matches) and "all". */
    Map<String, Integer> topicCounts(int solutionId, String search);

    Topic findTopic(int topicId);

    int saveTopic(TopicDraft draft, String memberCode);

    boolean deleteTopic(int topicId);

    // ---- since you were last here ----

    /** A member opened the solution: rolls the visit when the last one was over an hour ago. */
    Visit touch(int solutionId, String memberCode);

    /** Where the member's "since" starts, without moving it; null when they never had it open. */
    Visit visit(int solutionId, String memberCode);

    void caughtUp(int solutionId, String memberCode);

    List<Change> changes(int solutionId, LocalDateTime since, String memberCode, int offset, int pageSize);

    /** "total" and "kind:&lt;kind&gt;". */
    Map<String, Integer> changeCounts(int solutionId, LocalDateTime since, String memberCode);
}
