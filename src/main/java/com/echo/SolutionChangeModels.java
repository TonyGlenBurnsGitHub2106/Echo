package com.echo;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.idOrNull;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The shapes of changing many records at once and of making one record into another (Story 5695, Echo first,
 * Release 5). From the deep review of 11/02: the dead 837 path was cancelled by hand in 20 items, 326 items sit
 * unassigned, and work that starts as a message or a note has to be retyped to become an item.
 */
final class SolutionChangeModels {

    private SolutionChangeModels() {}

    /** What a bulk change may change, one at a time. */
    static final Set<String> FIELDS = Set.of("status", "project", "assignee", "due", "priority", "topic");

    /** At most this many items in one bulk change. */
    static final int MAX_ITEMS = 500;

    /** What a record can be made from, and what it can be made into. */
    static final Set<String> SOURCES = Set.of("message", "log", "note", "item");
    static final Set<String> TARGETS = Set.of("item", "note", "question");

    /** A bulk change done: the one log entry that says what and why (0 when nothing needed changing), and how many. */
    public record BulkResult(int logId, int itemCount) {
        static BulkResult of(Map<String, Object> r) {
            return r == null ? new BulkResult(0, 0) : new BulkResult(intOf(r, "SolutionLogID"), intOf(r, "ItemCount"));
        }
    }

    /** The record a promotion made. */
    public record Promoted(String kind, int id) {
        static Promoted of(Map<String, Object> r) {
            return new Promoted(strOf(r, "Kind"), intOf(r, "ID"));
        }
    }

    /**
     * Where a record came from (direction "from") or what was made from it ("into"). For a message, id is the
     * message and conversationId its thread.
     */
    public record Provenance(String direction, String kind, int id, String title, Integer conversationId,
                             LocalDateTime promotedDate, String promotedByCode, String promotedByName) {
        static Provenance of(Map<String, Object> r) {
            return new Provenance(strOf(r, "Direction"), strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"),
                idOrNull(r, "ConversationID"), dateOf(r, "PromotedDate"), strOf(r, "PromotedByCode"), strOf(r, "PromotedByName"));
        }
    }
}
