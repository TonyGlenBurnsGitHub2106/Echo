package com.echo;

import com.echo.SolutionSearchModels.SearchFilter;
import com.echo.SolutionSearchModels.SearchHit;
import com.echo.SolutionSearchModels.SearchResult;
import com.echo.SolutionSearchModels.SearchSource;
import com.echo.SolutionSearchRepository.SearchRows;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionSearchModels.LABELS;
import static com.echo.SolutionSearchModels.SOURCES;

/**
 * A solution's Search tab (2026-10-16): the rows the repository found, shaped as
 * one band per tab in tab order, each with how many matched there. HOUSE-ONLY: a
 * person (Tony, Felix, Stewart) only -- the web source reads the solution's
 * EntityID and the file source its filing, which an outside agent must never see.
 */
@Service
public class SolutionSearchService {

    private final SolutionSearchRepository searches;
    private final SolutionAccess           access;

    public SolutionSearchService(SolutionSearchRepository searches, SolutionAccess access) {
        this.searches = searches; this.access = access;
    }

    public SearchResult search(SolutionCaller caller, SearchFilter filter) {
        access.requireSolution(caller, filter.solutionId());
        access.requireHuman(caller, "search a solution");
        SearchRows rows = searches.search(filter);
        Map<String, List<SearchHit>> byTab = new LinkedHashMap<>();
        for (SearchHit h : rows.hits()) byTab.computeIfAbsent(h.source(), k -> new ArrayList<>()).add(h);
        List<SearchSource> out = new ArrayList<>();
        int total = 0;
        for (String key : SOURCES) {
            int n = rows.totals().getOrDefault(key, 0);
            if (n <= 0) continue;
            total += n;
            out.add(new SearchSource(key, LABELS.get(key), n, byTab.getOrDefault(key, List.of())));
        }
        return new SearchResult(filter.solutionId(), out, total, filter.pageSize());
    }
}
