package com.echo;

import com.echo.SolutionSearchModels.SearchFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The Search tab of a solution (2026-10-16) -- every other tab searched at once:
 *
 *   GET /api/solution-search/{solutionId}
 *       ?or=a&or=b          any of these      (each parameter may repeat)
 *       &and=c&and=d        all of these
 *       &not=e              none of these
 *       &sources=items,log  which tabs (tab keys; none = every tab)
 *       &from=2026-09-01&through=2026-09-30
 *       &titlesOnly=true    never look inside the text
 *       &openOnly=true      skip closed projects, items and conversations
 *       &source=log&offset=25&pageSize=25     one tab, paged (Load more)
 *
 * HOUSE-ONLY, on purpose OUTSIDE /api/solutions: the outside agents' allowlist never reaches
 * this path (ApiClientPathFence denies by default), and as a second fence the service refuses
 * a client caller. The web source reads the solution's EntityID -- a key into the rest of the
 * house -- and the file source reads its filing, so neither may be reachable by an agent.
 */
@RestController
@RequestMapping("/api/solution-search/{solutionId:\\d+}")
public class SolutionSearchController extends SolutionControllerSupport {

    private final SolutionSearchService service;

    public SolutionSearchController(SolutionSearchService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> search(HttpServletRequest req, @PathVariable int solutionId,
                                    @RequestParam(name = "or",         required = false) List<String> or,
                                    @RequestParam(name = "and",        required = false) List<String> and,
                                    @RequestParam(name = "not",        required = false) List<String> not,
                                    @RequestParam(name = "sources",    required = false) String sources,
                                    @RequestParam(name = "from",       required = false) String from,
                                    @RequestParam(name = "through",    required = false) String through,
                                    @RequestParam(name = "titlesOnly", required = false) Boolean titlesOnly,
                                    @RequestParam(name = "openOnly",   required = false) Boolean openOnly,
                                    @RequestParam(name = "source",     required = false) String source,
                                    @RequestParam(name = "offset",     required = false) Integer offset,
                                    @RequestParam(name = "pageSize",   required = false) Integer pageSize) {
        SearchFilter filter = new SearchFilter(solutionId, or, and, not,
            sources == null || sources.isBlank() ? null : List.of(sources.split(",")),
            day(from, "From date"), day(through, "Through date"), orFalse(titlesOnly), orFalse(openOnly),
            source, orZero(offset), orZero(pageSize));
        return ResponseEntity.ok(service.search(caller(req), filter));
    }

    /** 2026-09-17 only (what the page's date boxes send); blank is null; anything else is a 400. */
    private static LocalDate day(String value, String what) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim()); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException(what + " must look like 2026-09-17."); }
    }
}
