package com.echo;

import java.time.LocalDateTime;
import java.util.Map;

import static com.echo.SolutionRows.dateOf;

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
}
