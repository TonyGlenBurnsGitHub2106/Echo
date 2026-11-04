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
 * The shapes of "where am I" in a solution (Story 5695, Echo first). From the deep review of 11/02:
 * "Nothing orients you ... The Claims status summary was written September 17 and still says the app has
 * never run as a service, which stopped being true on October 12."
 */
final class SolutionOrientationModels {

    private SolutionOrientationModels() {}

    /** A summary this many days old or older is shown as stale (amber). */
    static final int STALE_DAYS = 14;

    /** When "Where things stand" was last written; writtenDate and ageDays are null when there is no summary. */
    public record SummaryAge(LocalDateTime writtenDate, Integer ageDays, boolean stale) {
        static SummaryAge of(Map<String, Object> r) {
            if (r == null) return new SummaryAge(null, null, false);
            // Not idOrNull: that reads 0 as "no id", and a summary written today is 0 days old.
            Object v = r.get("AgeDays");
            Integer days = v instanceof Number n ? n.intValue() : null;
            return new SummaryAge(dateOf(r, "WrittenDate"), days, days != null && days >= STALE_DAYS);
        }
    }

    // ---- Release 4: the Now / Next / Waiting lens, the note for next time, the activity feed ----

    static final Set<String> BUCKETS = Set.of("now", "next", "waiting");

    /** Due within this many days counts as Now; a Now item untouched this long is idle. */
    static final int SOON_DAYS = 7, IDLE_DAYS = 7;

    /** One open item in the lens: where it landed, how long since it changed, whose it is. */
    public record LensItem(int id, String title, Integer parentItemId, int projectId, String projectName, String statusCode,
                           String statusName, String colorHex, int priority, LocalDateTime dueDate, String assigneeCode,
                           String assigneeName, String bucket, int idleDays, boolean idle, boolean mine, boolean overdue,
                           int openWaits, int rowInBucket, int totalInBucket) {
        static LensItem of(Map<String, Object> r) {
            return new LensItem(intOf(r, "SolutionProjectItemID"), strOf(r, "ItemTitle"), idOrNull(r, "ParentItemID"),
                intOf(r, "SolutionProjectID"), strOf(r, "ProjectName"), strOf(r, "StatusCode"), strOf(r, "StatusName"),
                strOf(r, "ColorHex"), intOf(r, "Priority"), dateOf(r, "DueDate"), strOf(r, "AssigneeCode"), strOf(r, "AssigneeName"),
                strOf(r, "Bucket"), intOf(r, "IdleDays"), boolOf(r, "IsIdle"), boolOf(r, "IsMine"), boolOf(r, "IsOverdue"),
                intOf(r, "OpenWaits"), intOf(r, "RowInBucket"), intOf(r, "TotalInBucket"));
        }
    }

    /** One page of the lens and how many items each bucket holds in all. */
    public record Lens(List<LensItem> items, Map<String, Integer> counts, String bucket, int offset, int pageSize) {}

    /** A member's note for next time; note and writtenDate are null when there is none. */
    public record LeftOff(String note, LocalDateTime writtenDate) {
        static LeftOff of(Map<String, Object> r) {
            return r == null ? new LeftOff(null, null) : new LeftOff(strOf(r, "PartingNote"), dateOf(r, "PartingNoteDate"));
        }
    }

    /** One change in the activity feed (everyone's, the reader's own included). For a message, id is its conversation. */
    public record Activity(String kind, int id, String title, String sub, String action, String who, String whoName,
                           LocalDateTime changedDate) {
        static Activity of(Map<String, Object> r) {
            return new Activity(strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"), strOf(r, "Action"),
                strOf(r, "Who"), strOf(r, "WhoName"), dateOf(r, "ChangedDate"));
        }
    }

    public record ActivityPage(List<Activity> changes, LocalDateTime since, int offset, int pageSize, int total) {}
}
