package com.echo;

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
 * The shapes of a solution's Terms tab (2026-10-09) -- a glossary shaped like
 * the Leonardo Dictionary, in its own tables (SolutionTerm*). A term's type and
 * categories belong to its solution alone.
 */
final class SolutionTermModels {

    private SolutionTermModels() {}

    /** One term. In a list, definition is a 600-character preview (truncated says so) and notes is null. */
    public record Term(int id, int solutionId, String groupKey, String groupLabel, String term, String definition,
                       boolean truncated, Integer typeId, String type, String source, String notes, boolean hasNotes,
                       boolean favorite, List<String> categories, String authorCode, String authorName,
                       String modifiedByCode, String modifiedByName, LocalDateTime createdDate, LocalDateTime modifiedDate) {
        static Term of(Map<String, Object> r) {
            return new Term(intOf(r, "SolutionTermID"), intOf(r, "SolutionID"), strOf(r, "GroupKey"), strOf(r, "GroupLabel"),
                strOf(r, "Term"), strOf(r, "Definition"), boolOf(r, "IsTruncated"), idOrNull(r, "SolutionTermTypeID"),
                strOf(r, "TypeName"), strOf(r, "TermSource"), strOf(r, "TermNotes"), boolOf(r, "HasNotes"),
                boolOf(r, "IsFavorite"), lines(strOf(r, "CategoryList")), strOf(r, "AuthorCode"), strOf(r, "AuthorName"),
                strOf(r, "ModifiedByCode"), strOf(r, "ModifiedByName"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"));
        }
    }

    /** One page of the list, the counts under the same filter, and what the filtered set totals. */
    public record TermPage(List<Term> terms, Map<String, Integer> counts, int offset, int pageSize, int total) {}

    /** A type or a category of one solution, with how many of its terms use it. */
    public record TermLookup(int id, String name, int usageCount) {
        static TermLookup type(Map<String, Object> r) {
            return new TermLookup(intOf(r, "SolutionTermTypeID"), strOf(r, "TypeName"), intOf(r, "UsageCount"));
        }
        static TermLookup category(Map<String, Object> r) {
            return new TermLookup(intOf(r, "SolutionTermCategoryID"), strOf(r, "CategoryName"), intOf(r, "UsageCount"));
        }
    }

    public record TermLookups(List<TermLookup> types, List<TermLookup> categories) {}

    /**
     * An add (id 0) or a merge-edit. A null field keeps what the term has; "" clears a type, a
     * source or the notes; categories null keeps them, an empty list removes them all.
     */
    public record TermDraft(int id, int solutionId, String term, String definition, String type, String source,
                            String notes, List<String> categories) {}

    public record TermFilter(int solutionId, String search, Integer typeId, Integer categoryId, boolean favoritesOnly,
                             String groupBy, boolean reverse, int offset, int pageSize) {
        static final int DEFAULT_PAGE = 50, MAX_PAGE = 500;

        public TermFilter {
            search = (search == null || search.isBlank()) ? null : search.trim();
            if (typeId != null && typeId <= 0) typeId = null;
            if (categoryId != null && categoryId <= 0) categoryId = null;
            groupBy = ("category".equals(groupBy) || "none".equals(groupBy)) ? groupBy : "type";
            offset = Math.max(0, offset);
            pageSize = pageSize <= 0 ? DEFAULT_PAGE : Math.min(pageSize, MAX_PAGE);
        }
    }

    /** CategoryList comes back one name per line. */
    static List<String> lines(String list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String s : list.split("\n")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }
}
