package com.echo;

import com.echo.SolutionTermModels.Term;
import com.echo.SolutionTermModels.TermDraft;
import com.echo.SolutionTermModels.TermFilter;
import com.echo.SolutionTermModels.TermLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * SQL Server backed {@link SolutionTermRepository} (2026-10-09): the
 * stp_SolutionTerm* procs. The list and the counts are bound from the same
 * filter, so they always describe the same set.
 */
@Repository
public class JdbcSolutionTermRepository implements SolutionTermRepository {

    private final SolutionJdbc db;

    public JdbcSolutionTermRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<Term> list(TermFilter f) {
        return db.list("EXEC dbo.stp_SolutionTerm_ListForSolution ?, ?, ?, ?, ?, ?, ?, ?, ?", Term::of,
            f.solutionId(), f.search(), f.typeId(), f.categoryId(), f.favoritesOnly(), f.groupBy(), f.reverse(),
            f.offset(), f.pageSize());
    }

    @Override
    public Map<String, Integer> counts(TermFilter f) {
        Map<String, Integer> out = new LinkedHashMap<>();
        db.list("EXEC dbo.stp_SolutionTerm_Counts ?, ?, ?, ?, ?, ?", r -> r,
                f.solutionId(), f.search(), f.typeId(), f.categoryId(), f.favoritesOnly(), f.groupBy())
          .forEach(r -> {
              String bucket = strOf(r, "Bucket");
              String key = strOf(r, "BucketKey");
              out.put((key == null || key.isEmpty()) ? bucket : bucket + ":" + key, intOf(r, "Cnt"));
          });
        return out;
    }

    @Override
    public Term find(int id) { return db.one("EXEC dbo.stp_SolutionTerm_Get ?", Term::of, id); }

    @Override
    public int save(TermDraft d, String memberCode) {
        String categories = d.categories() == null ? null : String.join("\n", d.categories());
        return db.number("EXEC dbo.stp_SolutionTerm_Save ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionTermID",
            d.id(), d.solutionId(), d.term(), d.definition(), d.type(), d.source(), d.notes(), categories, memberCode);
    }

    @Override
    public boolean setFavorite(int id, boolean favorite, String memberCode) {
        return db.affected("EXEC dbo.stp_SolutionTerm_SetFavorite ?, ?, ?", id, favorite, memberCode);
    }

    @Override
    public boolean delete(int id) { return db.affected("EXEC dbo.stp_SolutionTerm_Delete ?", id); }

    @Override
    public List<TermLookup> types(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionTermType_ListForSolution ?", TermLookup::type, solutionId);
    }

    @Override
    public List<TermLookup> categories(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionTermCategory_ListForSolution ?", TermLookup::category, solutionId);
    }

    @Override
    public void renameLookup(String kind, int solutionId, int id, String name) {
        db.row("EXEC dbo." + proc(kind) + "_Rename ?, ?, ?", solutionId, id, name);
    }

    @Override
    public boolean deleteLookup(String kind, int solutionId, int id) {
        return db.affected("EXEC dbo." + proc(kind) + "_Delete ?, ?", solutionId, id);
    }

    /** The two lookup kinds and nothing else -- never a caller's string in the SQL. */
    private static String proc(String kind) {
        if ("type".equals(kind)) return "stp_SolutionTermType";
        if ("category".equals(kind)) return "stp_SolutionTermCategory";
        throw new IllegalArgumentException("Unknown kind.");
    }
}
