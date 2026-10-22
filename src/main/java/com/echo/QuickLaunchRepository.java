package com.echo;

import java.util.List;

/**
 * Repository contract for Quick Launch pins (single-user pinned shortcuts
 * on the LeonardoWeb dashboard).
 *
 * Two implementations exist; Spring picks one based on
 * {@code leonardo.demo.enabled}:
 *   - {@link JdbcQuickLaunchRepository}     -- real SQL Server (default)
 *   - {@link InMemoryQuickLaunchRepository} -- JSON-backed demo store
 *
 * Unlike most other repos this one returns the typed {@link QuickLaunch}
 * POJO instead of a raw Map. Both implementations must populate every
 * field so the controller's response is identical between modes.
 */
public interface QuickLaunchRepository {

    /** Returns every pin, ordered by {@code SortOrder ASC} then ID. */
    List<QuickLaunch> list();

    /**
     * Upsert: {@code quickLaunchId = 0} inserts (auto-appends with the
     * largest existing SortOrder + 10), > 0 updates. Returns the (new) ID.
     */
    int save(int quickLaunchId, String label, String url, String iconKey);

    void delete(int quickLaunchId);

    /** Rewrites a single pin's {@code SortOrder}; used by the reorder service. */
    void setSortOrder(int quickLaunchId, int sortOrder);
}
