package com.echo;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * A member's saved views of a solution's tab (Story 5695, Echo first, Release 5): a name and the filters it
 * stands for, so "my open items in Submit path" is one tap. Each member has their own.
 */
final class SolutionSavedViewModels {

    private SolutionSavedViewModels() {}

    /** The tabs a view can be saved for. */
    static final Set<String> SCOPES = Set.of("items");

    /** filter is the page's own JSON object of filters, kept as written. */
    public record SavedView(int id, int solutionId, String scope, String name, String filter, int sortOrder,
                            LocalDateTime createdDate, LocalDateTime modifiedDate) {
        static SavedView of(Map<String, Object> r) {
            return new SavedView(intOf(r, "SolutionSavedViewID"), intOf(r, "SolutionID"), strOf(r, "ScopeCode"),
                strOf(r, "ViewName"), strOf(r, "FilterJson"), intOf(r, "SortOrder"), dateOf(r, "CreatedDate"),
                dateOf(r, "ModifiedDate"));
        }
    }
}
