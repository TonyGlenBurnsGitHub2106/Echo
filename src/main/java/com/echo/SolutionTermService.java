package com.echo;

import com.echo.SolutionTermModels.Term;
import com.echo.SolutionTermModels.TermDraft;
import com.echo.SolutionTermModels.TermFilter;
import com.echo.SolutionTermModels.TermLookups;
import com.echo.SolutionTermModels.TermPage;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * A solution's Terms tab (2026-10-09): a glossary shaped like the Leonardo
 * Dictionary, kept in its own tables. Anyone who can see the solution may add,
 * change and remove terms -- a solution shared with an API client gives it full
 * rights inside, and the types and categories it makes stay inside that one
 * solution. Every refusal comes from the procs ("... Nothing was saved.").
 */
@Service
public class SolutionTermService {

    private final SolutionTermRepository terms;
    private final SolutionAccess         access;

    public SolutionTermService(SolutionTermRepository terms, SolutionAccess access) {
        this.terms = terms; this.access = access;
    }

    public TermPage page(SolutionCaller caller, TermFilter filter) {
        access.requireSolution(caller, filter.solutionId());
        Map<String, Integer> counts = terms.counts(filter);
        return new TermPage(terms.list(filter), counts, filter.offset(), filter.pageSize(), counts.getOrDefault("total", 0));
    }

    public Term get(SolutionCaller caller, int id) {
        Term t = id <= 0 ? null : terms.find(id);
        if (t == null) throw new SolutionNotFoundException("Term");
        access.requireSolution(caller, t.solutionId());
        return t;
    }

    public Term save(SolutionCaller caller, TermDraft draft) {
        if (draft.id() > 0) get(caller, draft.id());
        else access.requireSolution(caller, draft.solutionId());
        int id = terms.save(draft, caller.memberCode());
        if (id <= 0) throw new SolutionNotFoundException("Term");
        return terms.find(id);
    }

    public Term favorite(SolutionCaller caller, int id, boolean favorite) {
        get(caller, id);
        if (!terms.setFavorite(id, favorite, caller.memberCode())) throw new SolutionNotFoundException("Term");
        return terms.find(id);
    }

    public void delete(SolutionCaller caller, int id) {
        get(caller, id);
        if (!terms.delete(id)) throw new SolutionNotFoundException("Term");
    }

    public TermLookups lookups(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return new TermLookups(terms.types(solutionId), terms.categories(solutionId));
    }

    /** kind = "type" or "category". */
    public TermLookups renameLookup(SolutionCaller caller, int solutionId, String kind, int id, String name) {
        access.requireSolution(caller, solutionId);
        terms.renameLookup(kind, solutionId, id, name);
        return new TermLookups(terms.types(solutionId), terms.categories(solutionId));
    }

    public TermLookups deleteLookup(SolutionCaller caller, int solutionId, String kind, int id) {
        access.requireSolution(caller, solutionId);
        terms.deleteLookup(kind, solutionId, id);
        return new TermLookups(terms.types(solutionId), terms.categories(solutionId));
    }
}
