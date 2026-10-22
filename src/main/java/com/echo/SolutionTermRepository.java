package com.echo;

import com.echo.SolutionTermModels.Term;
import com.echo.SolutionTermModels.TermDraft;
import com.echo.SolutionTermModels.TermFilter;
import com.echo.SolutionTermModels.TermLookup;

import java.util.List;
import java.util.Map;

/**
 * Storage for a solution's Terms tab (2026-10-09). Jdbc = stp_SolutionTerm*;
 * the list is filtered, grouped, ordered and paged in SQL, and counted under
 * the same filter.
 */
public interface SolutionTermRepository {

    List<Term> list(TermFilter filter);

    /** "total", "group:&lt;key&gt;", "all", "favorites", "types", "categories". */
    Map<String, Integer> counts(TermFilter filter);

    /** One term, whole. */
    Term find(int id);

    /** Add (id 0) or merge-edit; returns the term's id. A refusal is a SolutionRuleException. */
    int save(TermDraft draft, String memberCode);

    boolean setFavorite(int id, boolean favorite, String memberCode);

    boolean delete(int id);

    List<TermLookup> types(int solutionId);

    List<TermLookup> categories(int solutionId);

    /** kind = "type" or "category"; refused (SolutionRuleException) when it is not this solution's. */
    void renameLookup(String kind, int solutionId, int id, String name);

    boolean deleteLookup(String kind, int solutionId, int id);
}
