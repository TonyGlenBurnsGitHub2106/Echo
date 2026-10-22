package com.echo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.idOrNull;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The shapes of a solution's Search tab (2026-10-16): one search across every
 * other tab, Find Transaction style -- OR terms, AND terms, NOT terms, a date
 * range, which tabs, titles only, open only.
 */
final class SolutionSearchModels {

    private SolutionSearchModels() {}

    /** The tab keys a search may cover, in the order the tabs sit on the page. */
    static final List<String> SOURCES = List.of("overview", "items", "log", "notes", "terms", "tests", "qa",
                                                "config", "grid", "files", "conversations", "web");

    static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("overview", "Projects"), Map.entry("items", "Items"), Map.entry("log", "Log"), Map.entry("notes", "Notes"),
        Map.entry("terms", "Terms"), Map.entry("tests", "Testing"), Map.entry("qa", "QA"), Map.entry("config", "Configuration"),
        Map.entry("grid", "Configuration II"), Map.entry("files", "Files"), Map.entry("conversations", "Conversations"),
        Map.entry("web", "Web"));

    /** What was asked. Terms are already trimmed and blank-free; sources null = every tab. */
    public record SearchFilter(int solutionId, List<String> orTerms, List<String> andTerms, List<String> notTerms,
                               List<String> sources, LocalDate from, LocalDate through, boolean titlesOnly, boolean openOnly,
                               String source, int offset, int pageSize) {
        static final int DEFAULT_PAGE = 25, MAX_PAGE = 500;

        public SearchFilter {
            orTerms  = clean(orTerms);
            andTerms = clean(andTerms);
            notTerms = clean(notTerms);
            sources  = sources == null ? null : sources.stream().map(s -> s == null ? "" : s.trim().toLowerCase())
                           .filter(SOURCES::contains).distinct().toList();
            if (sources != null && sources.isEmpty()) sources = null;
            source   = (source == null || source.isBlank() || !SOURCES.contains(source.trim().toLowerCase())) ? null : source.trim().toLowerCase();
            offset   = Math.max(0, offset);
            pageSize = pageSize <= 0 ? DEFAULT_PAGE : Math.min(pageSize, MAX_PAGE);
        }

        /** True when nothing narrows the search -- the page refuses that; the API answers everything. */
        boolean isEmpty() {
            return orTerms.isEmpty() && andTerms.isEmpty() && notTerms.isEmpty() && from == null && through == null;
        }

        private static List<String> clean(List<String> terms) {
            List<String> out = new ArrayList<>();
            if (terms == null) return out;
            for (String t : terms) {
                if (t == null) continue;
                String s = t.trim();
                if (s.length() > 200) s = s.substring(0, 200);
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
    }

    /** One thing found: what it is, where it lives, and the piece of text that matched. */
    public record SearchHit(String source, String kind, int id, Integer parentId, String parentTitle, String title,
                            String snippet, String matchTerm, LocalDateTime date, String statusName, String colorHex,
                            boolean closed, String extra) {
        static SearchHit of(Map<String, Object> r) {
            return new SearchHit(strOf(r, "Source"), strOf(r, "Kind"), intOf(r, "ID"), idOrNull(r, "ParentID"),
                strOf(r, "ParentTitle"), strOf(r, "Title"), strOf(r, "Snippet"), strOf(r, "MatchTerm"), dateOf(r, "ItemDate"),
                strOf(r, "StatusName"), strOf(r, "ColorHex"), boolOf(r, "IsClosed"), strOf(r, "Extra"));
        }
    }

    /** One tab's share of the answer: how many matched there, and the rows loaded so far. */
    public record SearchSource(String key, String label, int total, List<SearchHit> hits) {}

    /** The whole answer: a band per tab that had anything, in tab order, and the grand total. */
    public record SearchResult(int solutionId, List<SearchSource> sources, int total, int pageSize) {}
}
