package com.echo;

import com.echo.SolutionTermModels.Term;
import com.echo.SolutionTermModels.TermDraft;
import com.echo.SolutionTermModels.TermFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A solution's Terms tab (2026-10-09) -- its glossary, shaped like the Leonardo
 * Dictionary, in its own tables.
 *
 *   GET    /api/solutions/{id}/terms                one page; search, typeId, categoryId, favorites,
 *                                                  groupBy=type|category|none, reverse, offset, pageSize
 *   POST   /api/solutions/{id}/terms                add a term
 *   GET    /api/solutions/{id}/terms/lookups        the solution's types and categories, with usage counts
 *   PUT    /api/solutions/{id}/terms/{kind}/{lid}   rename a type or category  (kind = types | categories)
 *   DELETE /api/solutions/{id}/terms/{kind}/{lid}   remove one; its terms keep everything else
 *   GET    /api/solutions/terms/{termId}            one term, whole
 *   GET    /api/solutions/terms/{termId}/locate     which solution it lives in (Leonardo Find links)
 *   PUT    /api/solutions/terms/{termId}            change it (a merge: a missing field is kept)
 *   PUT    /api/solutions/terms/{termId}/favorite   {favorite: true|false}
 *   DELETE /api/solutions/terms/{termId}
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionTermController extends SolutionControllerSupport {

    private final SolutionTermService service;

    public SolutionTermController(SolutionTermService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record TermRequest(String term, String definition, String type, String source, String notes, List<String> categories) {}

    public record FavoriteRequest(Boolean favorite) {}

    public record NameRequest(String name) {}

    @GetMapping("/{id:\\d+}/terms")
    public ResponseEntity<?> page(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "search",     required = false) String search,
                                  @RequestParam(name = "typeId",     required = false) Integer typeId,
                                  @RequestParam(name = "categoryId", required = false) Integer categoryId,
                                  @RequestParam(name = "favorites",  required = false) Boolean favorites,
                                  @RequestParam(name = "groupBy",    required = false) String groupBy,
                                  @RequestParam(name = "reverse",    required = false) Boolean reverse,
                                  @RequestParam(name = "offset",     required = false) Integer offset,
                                  @RequestParam(name = "pageSize",   required = false) Integer pageSize) {
        TermFilter filter = new TermFilter(id, search, typeId, categoryId, orFalse(favorites), groupBy, orFalse(reverse),
            orZero(offset), orZero(pageSize));
        return ResponseEntity.ok(service.page(caller(req), filter));
    }

    @PostMapping("/{id:\\d+}/terms")
    public ResponseEntity<?> add(HttpServletRequest req, @PathVariable int id, @RequestBody TermRequest body) {
        if (body == null) throw new IllegalArgumentException("A term is required.");
        TermDraft draft = new TermDraft(0, id, required(body.term(), "The term"), required(body.definition(), "A definition"),
            body.type(), body.source(), body.notes(), body.categories() == null ? List.of() : body.categories());
        return ResponseEntity.ok(service.save(caller(req), draft));
    }

    @GetMapping("/{id:\\d+}/terms/lookups")
    public ResponseEntity<?> lookups(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.lookups(caller(req), id));
    }

    @PutMapping("/{id:\\d+}/terms/{kind:types|categories}/{lookupId:\\d+}")
    public ResponseEntity<?> renameLookup(HttpServletRequest req, @PathVariable int id, @PathVariable String kind,
                                          @PathVariable int lookupId, @RequestBody NameRequest body) {
        String name = required(body == null ? null : body.name(), "A name");
        return ResponseEntity.ok(service.renameLookup(caller(req), id, singular(kind), lookupId, name));
    }

    @DeleteMapping("/{id:\\d+}/terms/{kind:types|categories}/{lookupId:\\d+}")
    public ResponseEntity<?> deleteLookup(HttpServletRequest req, @PathVariable int id, @PathVariable String kind,
                                          @PathVariable int lookupId) {
        return ResponseEntity.ok(service.deleteLookup(caller(req), id, singular(kind), lookupId));
    }

    @GetMapping("/terms/{termId:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int termId) {
        return ResponseEntity.ok(service.get(caller(req), termId));
    }

    @GetMapping("/terms/{termId:\\d+}/locate")
    public ResponseEntity<?> locate(HttpServletRequest req, @PathVariable int termId) {
        Term t = service.get(caller(req), termId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("termId", t.id());
        body.put("solutionId", t.solutionId());
        return ResponseEntity.ok(body);
    }

    @PutMapping("/terms/{termId:\\d+}")
    public ResponseEntity<?> update(HttpServletRequest req, @PathVariable int termId, @RequestBody TermRequest body) {
        if (body == null) throw new IllegalArgumentException("A term is required.");
        TermDraft draft = new TermDraft(termId, 0, body.term(), body.definition(), body.type(), body.source(), body.notes(),
            body.categories());
        return ResponseEntity.ok(service.save(caller(req), draft));
    }

    @PutMapping("/terms/{termId:\\d+}/favorite")
    public ResponseEntity<?> favorite(HttpServletRequest req, @PathVariable int termId, @RequestBody FavoriteRequest body) {
        if (body == null || body.favorite() == null) throw new IllegalArgumentException("favorite (true or false) is required.");
        return ResponseEntity.ok(service.favorite(caller(req), termId, body.favorite()));
    }

    @DeleteMapping("/terms/{termId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int termId) {
        service.delete(caller(req), termId);
        return ResponseEntity.ok(ok("deletedId", termId));
    }

    private static String singular(String kind) { return "types".equals(kind) ? "type" : "category"; }
}
