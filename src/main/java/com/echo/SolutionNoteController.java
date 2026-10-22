package com.echo;

import com.echo.SolutionDrafts.NoteDraft;
import com.echo.SolutionDrafts.StepDraft;
import com.echo.SolutionModels.Note;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A solution's notes (Story 3493) -- "how do you do it".
 *
 *   GET    /api/solutions/{id}/notes[?type=runbook]          headers, with a body preview
 *   POST   /api/solutions/{id}/notes                         create
 *   GET    /api/solutions/notes/{noteId}                      one note: current body + steps
 *   PUT    /api/solutions/notes/{noteId}                      save; a changed body becomes a new version
 *   DELETE /api/solutions/notes/{noteId}                      (a person only)
 *   GET    /api/solutions/notes/{noteId}/locate               which solution it lives in
 *   GET    /api/solutions/notes/{noteId}/versions             who, when, why -- no bodies
 *   GET    /api/solutions/notes/{noteId}/versions/{n}         one earlier wording
 *   POST   /api/solutions/notes/{noteId}/steps                add a runbook step
 *   PUT    /api/solutions/notes/{noteId}/steps/{stepId}
 *   POST   /api/solutions/notes/{noteId}/steps/{stepId}/move  {stepNumber}
 *   DELETE /api/solutions/notes/{noteId}/steps/{stepId}       (a person only)
 *   POST   /api/solutions/notes/{noteId}/steps/from-log       {logId} -- what we did becomes how to do it
 *
 * Every step call answers with the whole note, so the caller always holds
 * the numbering the server holds.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionNoteController extends SolutionControllerSupport {

    private final SolutionNoteService service;

    public SolutionNoteController(SolutionNoteService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/notes")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "type", required = false) String type) {
        return ResponseEntity.ok(service.list(caller(req), id, type));
    }

    @GetMapping("/notes/{noteId:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int noteId) {
        return ResponseEntity.ok(service.get(caller(req), noteId));
    }

    @GetMapping("/notes/{noteId:\\d+}/locate")
    public ResponseEntity<?> locate(HttpServletRequest req, @PathVariable int noteId) {
        Note d = service.get(caller(req), noteId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("noteId", d.id());
        body.put("solutionId", d.solutionId());
        return ResponseEntity.ok(body);
    }

    public record KindOrderRequest(java.util.List<Integer> ids) {}

    /** The note kinds in their new order (a person only). Answers the kinds, in order. */
    @PutMapping("/note-types/order")
    public ResponseEntity<?> kindOrder(HttpServletRequest req, @RequestBody KindOrderRequest body) {
        if (body == null || body.ids() == null) throw new IllegalArgumentException("ids (the new order) is required.");
        return ResponseEntity.ok(service.reorderNoteTypes(caller(req), body.ids()));
    }

    public record NoteRequest(Integer projectId, String typeCode, String typeName, String title, String body, String changeNote, Integer sortOrder) {}

    @PostMapping("/{id:\\d+}/notes")
    public ResponseEntity<?> create(HttpServletRequest req, @PathVariable int id, @RequestBody NoteRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(0, id, body), body.typeName()));
    }

    @PutMapping("/notes/{noteId:\\d+}")
    public ResponseEntity<?> update(HttpServletRequest req, @PathVariable int noteId, @RequestBody NoteRequest body) {
        return ResponseEntity.ok(service.save(caller(req), toDraft(noteId, 0, body), body.typeName()));
    }

    @DeleteMapping("/notes/{noteId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int noteId) {
        service.delete(caller(req), noteId);
        return ResponseEntity.ok(ok("deletedId", noteId));
    }

    @GetMapping("/notes/{noteId:\\d+}/versions")
    public ResponseEntity<?> versions(HttpServletRequest req, @PathVariable int noteId) {
        return ResponseEntity.ok(service.versions(caller(req), noteId));
    }

    @GetMapping("/notes/{noteId:\\d+}/versions/{versionNumber:\\d+}")
    public ResponseEntity<?> version(HttpServletRequest req, @PathVariable int noteId, @PathVariable int versionNumber) {
        return ResponseEntity.ok(service.version(caller(req), noteId, versionNumber));
    }

    // ---- steps ------------------------------------------------------------

    public record StepRequest(Integer stepNumber, String title, String command, String expectedResult, String notes) {}

    @PostMapping("/notes/{noteId:\\d+}/steps")
    public ResponseEntity<?> addStep(HttpServletRequest req, @PathVariable int noteId, @RequestBody StepRequest body) {
        return ResponseEntity.ok(service.saveStep(caller(req), toDraft(0, noteId, body)));
    }

    @PutMapping("/notes/{noteId:\\d+}/steps/{stepId:\\d+}")
    public ResponseEntity<?> updateStep(HttpServletRequest req, @PathVariable int noteId, @PathVariable int stepId,
                                        @RequestBody StepRequest body) {
        return ResponseEntity.ok(service.saveStep(caller(req), toDraft(stepId, noteId, body)));
    }

    public record StepMoveRequest(Integer stepNumber) {}

    @PostMapping("/notes/{noteId:\\d+}/steps/{stepId:\\d+}/move")
    public ResponseEntity<?> moveStep(HttpServletRequest req, @PathVariable int noteId, @PathVariable int stepId,
                                      @RequestBody StepMoveRequest body) {
        if (body == null || orZero(body.stepNumber()) <= 0) throw new IllegalArgumentException("A step number is required.");
        return ResponseEntity.ok(service.moveStep(caller(req), noteId, stepId, body.stepNumber()));
    }

    @DeleteMapping("/notes/{noteId:\\d+}/steps/{stepId:\\d+}")
    public ResponseEntity<?> deleteStep(HttpServletRequest req, @PathVariable int noteId, @PathVariable int stepId) {
        return ResponseEntity.ok(service.deleteStep(caller(req), noteId, stepId));
    }

    public record FromLogRequest(Integer logId) {}

    @PostMapping("/notes/{noteId:\\d+}/steps/from-log")
    public ResponseEntity<?> stepFromLog(HttpServletRequest req, @PathVariable int noteId, @RequestBody FromLogRequest body) {
        if (body == null || orZero(body.logId()) <= 0) throw new IllegalArgumentException("A log entry id is required.");
        return ResponseEntity.ok(service.stepFromLog(caller(req), noteId, body.logId()));
    }

    // ---- mapping ----------------------------------------------------------

    private static NoteDraft toDraft(int id, int solutionId, NoteRequest b) {
        if (b == null) throw new IllegalArgumentException("A note is required.");
        return new NoteDraft(id, solutionId, b.projectId(), b.typeCode(), id > 0 ? b.title() : required(b.title(), "A title"), b.body(),
            b.changeNote(), orZero(b.sortOrder()));
    }

    private static StepDraft toDraft(int id, int noteId, StepRequest b) {
        if (b == null) throw new IllegalArgumentException("A step is required.");
        return new StepDraft(id, noteId, orZero(b.stepNumber()), id > 0 ? b.title() : required(b.title(), "A title"), b.command(),
            b.expectedResult(), b.notes(), null);
    }
}
