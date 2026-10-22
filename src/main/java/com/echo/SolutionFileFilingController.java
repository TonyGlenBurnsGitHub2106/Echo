package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The organized Files tab of a solution (Story 5594) -- category / Staging, Important, document date,
 * grouping, sorting and search, exactly like the Files tab on a record (Story 3520).
 *
 *   GET  /api/solution-files/{solutionId}/filed    ?group=&sort=&desc=&q=&offset=&size=
 *   GET  /api/solution-files/{solutionId}/counts   ?group=&q=
 *   PUT  /api/solution-files/{solutionId}/{fileId}/filing     { categoryId | categoryName | staging, documentDate, important }
 *   PUT  /api/solution-files/{solutionId}/{fileId}/important  { important }
 *   POST /api/solution-files/{solutionId}/file                { fileIds, categoryId | categoryName | staging }
 *
 * HOUSE-ONLY, on purpose OUTSIDE /api/solutions: a category is house information and the outside
 * agents' allowlist never reaches this path (ApiClientPathFence denies by default). As a second fence
 * every call checks the solution exists and that the caller is a person, never a client. Uploading,
 * viewing, downloading and deleting the files stay on /api/solutions (SolutionFileController).
 * Echo (Story 5653) has no Leonardo Files library and no Reading Nook, so LeonardoWeb's two "from"
 * doors were not echoed.
 */
@RestController
@RequestMapping("/api/solution-files/{solutionId:\\d+}")
public class SolutionFileFilingController extends SolutionControllerSupport {

    private final SolutionFileFilingService service;
    private final SolutionFileService files;
    private final SolutionAccess access;

    public SolutionFileFilingController(SolutionFileFilingService service, SolutionFileService files,
                                        SolutionAccess access, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
        this.files = files;
        this.access = access;
    }



    @GetMapping("/filed")
    public ResponseEntity<?> filed(HttpServletRequest req, @PathVariable int solutionId,
                                   @RequestParam(required = false) String group,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false, defaultValue = "true") boolean desc,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false, defaultValue = "0") int offset,
                                   @RequestParam(required = false, defaultValue = "50") int size) {
        house(req, solutionId);
        return ResponseEntity.ok(service.list(solutionId, group, sort, desc, q, offset, size));
    }

    @GetMapping("/counts")
    public ResponseEntity<?> counts(HttpServletRequest req, @PathVariable int solutionId,
                                    @RequestParam(required = false) String group,
                                    @RequestParam(required = false) String q) {
        house(req, solutionId);
        return ResponseEntity.ok(service.counts(solutionId, group, q == null || q.isBlank() ? null : q.trim()));
    }

    @PutMapping("/{fileId:\\d+}/filing")
    public ResponseEntity<?> setFiling(HttpServletRequest req, @PathVariable int solutionId, @PathVariable int fileId,
                                       @RequestBody(required = false) FilingRequest body) {
        SolutionCaller caller = house(req, solutionId);
        if (body == null) throw new IllegalArgumentException("Nothing to change.");
        Integer cat = service.resolveCategory(body.categoryId, body.categoryName, body.staging);
        Map<String, Object> out = service.setFiling(solutionId, fileId, cat, body.documentDate,
            Boolean.TRUE.equals(body.important), caller.memberCode());
        if (out == null) throw new SolutionNotFoundException("That file");
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{fileId:\\d+}/important")
    public ResponseEntity<?> setImportant(HttpServletRequest req, @PathVariable int solutionId, @PathVariable int fileId,
                                          @RequestBody(required = false) ImportantRequest body) {
        SolutionCaller caller = house(req, solutionId);
        boolean important = body != null && Boolean.TRUE.equals(body.important);
        if (!service.setImportant(solutionId, fileId, important, caller.memberCode()))
            throw new SolutionNotFoundException("That file");
        return ResponseEntity.ok(Map.of("fileId", fileId, "important", important));
    }

    @PostMapping("/file")
    public ResponseEntity<?> fileMany(HttpServletRequest req, @PathVariable int solutionId,
                                      @RequestBody(required = false) FileManyRequest body) {
        SolutionCaller caller = house(req, solutionId);
        if (body == null || body.fileIds == null || body.fileIds.isEmpty())
            throw new IllegalArgumentException("Pick at least one file.");
        Integer cat = service.resolveCategory(body.categoryId, body.categoryName, body.staging);
        int filed = service.fileMany(solutionId, body.fileIds, cat, caller.memberCode());
        return ResponseEntity.ok(Map.of("filed", filed, "categoryId", cat == null ? 0 : cat));
    }

    /** The solution must exist, and only a person (Tony, Felix, Stewart) may organize -- never an API client. */
    private SolutionCaller house(HttpServletRequest req, int solutionId) {
        SolutionCaller caller = caller(req);
        access.requireSolution(caller, solutionId);
        access.requireHuman(caller, "organize a solution's files");
        return caller;
    }

    public static class FilingRequest {
        public Integer categoryId;
        public String  categoryName;
        public Boolean staging;
        public String  documentDate;   // yyyy-MM-dd or mm/dd/yyyy; blank clears it
        public Boolean important;
    }

    public static class ImportantRequest {
        public Boolean important;
    }

    public static class FileManyRequest {
        public List<Integer> fileIds;
        public Integer categoryId;
        public String  categoryName;
        public Boolean staging;
    }
}
