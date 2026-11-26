package com.echo;

import com.echo.DailyWorkModels.EntryDraft;
import com.echo.DailyWorkModels.Filter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Story 6852 (Daily Work): /api/daily-work -- the work log behind Daily Work.
 * Behind the /api/** sign-in; NOT in an agent's allowed paths (agents reach /api/solutions/ only).
 *
 *   GET    /api/daily-work/lookups                    teams, contacts, kinds
 *   POST   /api/daily-work/teams     {name}           find or create
 *   POST   /api/daily-work/contacts  {name, teamId}   find or create
 *   POST   /api/daily-work/kinds     {name}           find or create
 *   GET    /api/daily-work/entries?from&to&q&contactId&teamId&followUp&top
 *   GET    /api/daily-work/entries/{id}
 *   POST   /api/daily-work/entries        EntryDraft  -> {id}
 *   PUT    /api/daily-work/entries/{id}   EntryDraft  -> {id}
 *   PUT    /api/daily-work/entries/{id}/follow-up {followUp}
 *   DELETE /api/daily-work/entries/{id}
 *
 * 400 = bad input (the message says what), 404 = no such entry, 409 = the proc refused
 * (a double tap, a contact that is gone ...; nothing was saved).
 */
@RestController
@RequestMapping("/api/daily-work")
public class DailyWorkController {

    private final DailyWorkService service;

    public DailyWorkController(DailyWorkService service) { this.service = service; }

    @GetMapping("/lookups")
    public ResponseEntity<?> lookups() {
        return ResponseEntity.ok(service.lookups());
    }

    @PostMapping("/teams")
    public ResponseEntity<?> addTeam(@RequestBody(required = false) NameRequest req) {
        return answer(() -> service.addTeam(req == null ? null : req.name));
    }

    @PostMapping("/contacts")
    public ResponseEntity<?> addContact(@RequestBody(required = false) NameRequest req) {
        return answer(() -> service.addContact(req == null ? null : req.name, req == null ? null : req.teamId));
    }

    @PostMapping("/kinds")
    public ResponseEntity<?> addKind(@RequestBody(required = false) NameRequest req) {
        return answer(() -> service.addKind(req == null ? null : req.name));
    }

    @GetMapping("/entries")
    public ResponseEntity<?> search(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "contactId", required = false) Integer contactId,
            @RequestParam(name = "teamId", required = false) Integer teamId,
            @RequestParam(name = "followUp", defaultValue = "false") boolean followUp,
            @RequestParam(name = "top", defaultValue = "500") int top) {
        return answer(() -> service.search(new Filter(from, to, q, contactId, teamId, followUp, top)));
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<?> get(@PathVariable int id) {
        return service.get(id).<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "That entry is not in Echo.")));
    }

    @PostMapping("/entries")
    public ResponseEntity<?> create(@RequestBody(required = false) EntryDraft draft) {
        return answer(() -> Map.of("id", service.save(0, draft, user())));
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody(required = false) EntryDraft draft) {
        if (id <= 0) return ResponseEntity.badRequest().body(Map.of("error", "invalid id"));
        return answer(() -> Map.of("id", service.save(id, draft, user())));
    }

    @PutMapping("/entries/{id}/follow-up")
    public ResponseEntity<?> followUp(@PathVariable int id, @RequestBody(required = false) FollowUpRequest req) {
        if (req == null || req.followUp == null)
            return ResponseEntity.badRequest().body(Map.of("error", "followUp is required"));
        if (!service.setFollowUp(id, req.followUp, user()))
            return ResponseEntity.status(404).body(Map.of("error", "That entry is not in Echo."));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        if (!service.delete(id))
            return ResponseEntity.status(404).body(Map.of("error", "That entry is not in Echo."));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static ResponseEntity<?> answer(Supplier<Object> work) {
        try {
            return ResponseEntity.ok(work.get());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (SaveRefusedException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        }
    }

    /** Who wrote it: the signed-in user's name ("service" for the house key). */
    static String user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }

    public static class NameRequest {
        public String name;
        public Integer teamId;
    }

    public static class FollowUpRequest {
        public Boolean followUp;
    }
}
