package com.echo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The shapes of topic KINDS (Story 5695, Echo first): a topic is a subject, a tag, a part of what the
 * solution is made of (a chapter, a character, an account -- the spine), or an assumption the plan rests on.
 */
final class SolutionTopicKindModels {

    private SolutionTopicKindModels() {}

    static final Set<String> KINDS = Set.of("subject", "tag", "part", "assumption");

    /** The four tags offered before a solution has any (made on first use, like any topic). */
    static final List<String> STARTER_TAGS = List.of("Important", "Risk", "Idea", "Parking lot");

    /** A kind of part of one solution: Chapter, Character, Account ... with what its number counts. */
    public record TopicType(int id, int solutionId, String name, String figureLabel, int sortOrder, int partCount) {
        static TopicType of(Map<String, Object> r) {
            return new TopicType(intOf(r, "SolutionTopicTypeID"), intOf(r, "SolutionID"), strOf(r, "TypeName"),
                strOf(r, "FigureLabel"), intOf(r, "SortOrder"), intOf(r, "PartCount"));
        }
    }

    /** An open item linked to a topic -- for an assumption, the work that rests on it. */
    public record OpenItem(int id, String title, String sub) {
        static OpenItem of(Map<String, Object> r) {
            return new OpenItem(intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"));
        }
    }

    /** What the premise switch did: the assumption, the items it cancelled, the journal entry that says so. */
    public record ProvedFalse(SolutionLinkModels.Topic topic, List<OpenItem> cancelled, List<OpenItem> stillOpen,
                              Integer logId, Integer logSequenceNumber) {}
}
