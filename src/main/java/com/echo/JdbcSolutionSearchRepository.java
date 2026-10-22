package com.echo;

import com.echo.SolutionSearchModels.SearchFilter;
import com.echo.SolutionSearchModels.SearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionSearchRepository} (2026-10-16): stp_Solution_Search.
 * The terms travel as one parameter each, one term per line (dbo.fn_Search_Terms splits
 * them); the tabs as a comma list. Every row carries TotalInSource, read once per tab.
 */
@Repository
public class JdbcSolutionSearchRepository implements SolutionSearchRepository {

    private final SolutionJdbc db;

    public JdbcSolutionSearchRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public SearchRows search(SearchFilter f) {
        List<Map<String, Object>> rows = db.list("EXEC dbo.stp_Solution_Search ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", r -> r,
            f.solutionId(), lines(f.orTerms()), lines(f.andTerms()), lines(f.notTerms()),
            f.sources() == null ? null : String.join(",", f.sources()),
            f.from() == null ? null : Date.valueOf(f.from()), f.through() == null ? null : Date.valueOf(f.through()),
            f.titlesOnly(), f.openOnly(), f.source(), f.offset(), f.pageSize());
        List<SearchHit> hits = new ArrayList<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            hits.add(SearchHit.of(r));
            totals.putIfAbsent(strOf(r, "Source"), intOf(r, "TotalInSource"));
        }
        return new SearchRows(hits, totals);
    }

    private static String lines(List<String> terms) {
        return terms == null || terms.isEmpty() ? null : String.join("\n", terms);
    }
}
