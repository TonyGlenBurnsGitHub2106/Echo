package com.echo;

import com.echo.SolutionSearchModels.SearchFilter;
import com.echo.SolutionSearchModels.SearchHit;

import java.util.List;
import java.util.Map;

/**
 * One search across every tab of a solution (2026-10-16). Jdbc = stp_Solution_Search:
 * the hay is gathered, the terms tested and the rows cut per tab in SQL; each row
 * carries TotalInSource, which is how the totals come back in the same call.
 */
public interface SolutionSearchRepository {

    /** The rows found (the top pageSize of every tab, or one page of the tab the filter names) and how many matched per tab. */
    record SearchRows(List<SearchHit> hits, Map<String, Integer> totals) {}

    SearchRows search(SearchFilter filter);
}
