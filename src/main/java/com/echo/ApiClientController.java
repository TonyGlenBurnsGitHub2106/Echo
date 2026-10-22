package com.echo;

import com.echo.ApiClientModels.ApiClient;
import com.echo.ApiClientModels.IssuedKey;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administration of scoped API clients (Story 3493). Base {@code /api/api-clients}.
 *
 *   GET    /api/api-clients                       every client (never a key or a hash)
 *   POST   /api/api-clients                       create
 *   PUT    /api/api-clients/{id}                  edit (code, name, allowed paths, notes, enabled)
 *   POST   /api/api-clients/{id}/enabled          {enabled} -- the kill switch
 *   POST   /api/api-clients/{id}/key              issue a key; the ONLY time it is ever shown
 *   DELETE /api/api-clients/{id}/key              revoke the key
 *   DELETE /api/api-clients/{id}
 *   GET    /api/api-clients/{id}/solutions        which solutions it may see
 *   POST   /api/api-clients/{id}/solutions        {solutionId, linked}
 *   GET    /api/api-clients/{id}/brief            the hand-off text for the agent (no key in it)
 *   GET    /api/api-clients/access-log            every client request, newest first
 *
 * The house manages these; an API client never can. The client filter already
 * refuses this whole path to a client -- {@code /api/api-clients/} can never be on
 * an allowlist the save proc accepts AND reach here, because the refusal below
 * does not look at the allowlist at all.
 */
@RestController
@RequestMapping("/api/api-clients")
public class ApiClientController {

    private final ApiClientService service;

    private final String baseUrl;

    public ApiClientController(ApiClientService service,
                               @org.springframework.beans.factory.annotation.Value("${echo.public.url:http://localhost:8770}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    /** Second lock: whatever an allowlist says, a client principal never administers clients. */
    @ModelAttribute
    void refuseClients() {
        if (ApiClientPrincipal.currentIsClient()) throw new SolutionForbiddenException("API clients are managed by the house.");
    }

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    public record ClientRequest(String clientCode, String clientName, String allowedPathPrefixes, String clientNotes, Boolean enabled) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ClientRequest body) { return save(0, body); }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody ClientRequest body) { return save(id, body); }

    private ResponseEntity<?> save(int id, ClientRequest b) {
        if (b == null) throw new IllegalArgumentException("A client is required.");
        ApiClient saved = service.save(id, b.clientCode(), b.clientName(),
            (b.allowedPathPrefixes() == null || b.allowedPathPrefixes().isBlank()) ? "/api/solutions/" : b.allowedPathPrefixes(),
            b.clientNotes(), b.enabled() == null || b.enabled());
        return saved == null ? notFound() : ResponseEntity.ok(saved);
    }

    public record EnabledRequest(Boolean enabled) {}

    @PostMapping("/{id:\\d+}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable int id, @RequestBody EnabledRequest body) {
        if (!service.setEnabled(id, body != null && body.enabled() != null && body.enabled())) return notFound();
        return ResponseEntity.ok(service.find(id));
    }

    @PostMapping("/{id:\\d+}/key")
    public ResponseEntity<?> issueKey(@PathVariable int id) {
        IssuedKey issued = service.issueKey(id);
        return issued == null ? notFound() : ResponseEntity.ok(issued);
    }

    @DeleteMapping("/{id:\\d+}/key")
    public ResponseEntity<?> revokeKey(@PathVariable int id) {
        if (!service.revokeKey(id)) return notFound();
        return ResponseEntity.ok(service.find(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        if (!service.delete(id)) return notFound();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("deletedId", id);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id:\\d+}/solutions")
    public ResponseEntity<?> solutions(@PathVariable int id) { return ResponseEntity.ok(service.solutionsForClient(id)); }

    public record LinkRequest(Integer solutionId, Boolean linked) {}

    @PostMapping("/{id:\\d+}/solutions")
    public ResponseEntity<?> setSolution(@PathVariable int id, @RequestBody LinkRequest body) {
        if (body == null || body.solutionId() == null || body.solutionId() <= 0) throw new IllegalArgumentException("A solution id is required.");
        service.setSolutionLink(id, body.solutionId(), body.linked() == null || body.linked());
        return ResponseEntity.ok(service.solutionsForClient(id));
    }

    @GetMapping("/access-log")
    public ResponseEntity<?> accessLog(@RequestParam(name = "clientId",    required = false) Integer clientId,
                                       @RequestParam(name = "refusedOnly", required = false) Boolean refusedOnly,
                                       @RequestParam(name = "offset",      required = false) Integer offset,
                                       @RequestParam(name = "pageSize",    required = false) Integer pageSize) {
        return ResponseEntity.ok(service.accessLog(clientId, refusedOnly != null && refusedOnly,
            offset == null ? 0 : offset, pageSize == null ? 50 : pageSize));
    }

    @GetMapping(value = "/{id:\\d+}/brief", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> brief(@PathVariable int id) {
        ApiClient c = service.find(id);
        if (c == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found.");
        return ResponseEntity.ok(ApiClientBrief.textFor(c, baseUrl));
    }

    // ---- refusals -----------------------------------------------------------

    @ExceptionHandler(SolutionRuleException.class)
    public ResponseEntity<?> rule(SolutionRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(SolutionControllerSupport.error("rule", ex.getMessage()));
    }

    @ExceptionHandler(SolutionForbiddenException.class)
    public ResponseEntity<?> forbidden(SolutionForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SolutionControllerSupport.error("forbidden", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(SolutionControllerSupport.error("bad_request", ex.getMessage()));
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(SolutionControllerSupport.error("not_found", "Client not found."));
    }
}
