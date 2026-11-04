package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The shapes of Solution links, topics and "Since you were last here"
 * (2026-11-02). A link joins two records of one solution and has no direction,
 * so a record's links are also its backlinks. A topic is a record too: a record
 * joins a topic by being linked to it.
 */
final class SolutionLinkModels {

    private SolutionLinkModels() {}

    /** The kinds a link may join. Story 5695 (Echo): questions and decisions too. */
    static final Set<String> KINDS = Set.of("item", "note", "log", "file", "term", "topic", "question", "decision");

    /** The other end of one link: what it is, what it is called, and who linked it when. */
    public record Link(int linkId, String kind, int id, String title, String sub, LocalDateTime recordDate, boolean closed,
                       LocalDateTime linkedDate, String linkedByCode, String linkedByName) {
        static Link of(Map<String, Object> r) {
            return new Link(intOf(r, "SolutionLinkID"), strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"),
                dateOf(r, "RecordDate"), boolOf(r, "IsClosed"), dateOf(r, "LinkedDate"), strOf(r, "LinkedByCode"), strOf(r, "LinkedByName"));
        }
    }

    /** One page of a record's links, and how many it has in all. */
    public record LinkPage(String kind, int id, List<Link> links, int offset, int pageSize, int total) {}

    /** One link as stored (its two ends in the fixed order). */
    public record StoredLink(int linkId, int solutionId, String fromKind, int fromId, String toKind, int toId) {
        static StoredLink of(Map<String, Object> r) {
            return new StoredLink(intOf(r, "SolutionLinkID"), intOf(r, "SolutionID"), strOf(r, "FromKind"), intOf(r, "FromID"),
                strOf(r, "ToKind"), intOf(r, "ToID"));
        }
    }

    /** A record the Link picker offers. */
    public record LinkTarget(String kind, int id, String title, String sub, LocalDateTime recordDate, boolean closed, boolean alreadyLinked) {
        static LinkTarget of(Map<String, Object> r) {
            return new LinkTarget(strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"),
                dateOf(r, "RecordDate"), boolOf(r, "IsClosed"), boolOf(r, "AlreadyLinked"));
        }
    }

    /** One topic. In a list, summary is a 600-character preview (truncated says so). */
    public record Topic(int id, int solutionId, String name, String summary, boolean truncated, String authorCode,
                        String authorName, String modifiedBy, LocalDateTime createdDate, LocalDateTime modifiedDate,
                        int memberCount, LocalDateTime latestDate) {
        static Topic of(Map<String, Object> r) {
            return new Topic(intOf(r, "SolutionTopicID"), intOf(r, "SolutionID"), strOf(r, "TopicName"), strOf(r, "TopicSummary"),
                boolOf(r, "Truncated"), strOf(r, "AuthorCode"), strOf(r, "AuthorName"), strOf(r, "ModifiedBy"),
                dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"), intOf(r, "MemberCount"), dateOf(r, "LatestDate"));
        }
    }

    public record TopicPage(List<Topic> topics, Map<String, Integer> counts, int offset, int pageSize, int total) {}

    /** An add (id 0) or a merge-edit: a null field keeps what the topic has; "" clears the summary. */
    public record TopicDraft(int id, int solutionId, String name, String summary) {}

    /** One change made since the member was last here. For a message, id is its conversation. */
    public record Change(String kind, int id, String title, String sub, String action, String who, String whoName,
                         LocalDateTime changedDate) {
        static Change of(Map<String, Object> r) {
            return new Change(strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"), strOf(r, "Action"),
                strOf(r, "Who"), strOf(r, "WhoName"), dateOf(r, "ChangedDate"));
        }
    }

    /**
     * What changed since the member was last here. firstVisit = the member never had this solution
     * open before, so since is a week back.
     */
    public record SincePage(LocalDateTime since, boolean firstVisit, List<Change> changes, Map<String, Integer> counts,
                            int offset, int pageSize, int total) {}

    /** A member's visit: where "since" starts (null before a second visit) and when they last had it open. */
    public record Visit(LocalDateTime previousSeen, LocalDateTime lastSeen) {
        static Visit of(Map<String, Object> r) {
            return new Visit(dateOf(r, "PreviousSeenDate"), dateOf(r, "LastSeenDate"));
        }
    }

    static int page(int pageSize, int max) { return pageSize <= 0 ? 50 : Math.min(pageSize, max); }
}
