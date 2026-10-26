package com.echo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the Leonardo Reader's marking tools. Base {@code /api/reader}.
 * Story 2307.
 *
 *   GET    /api/reader/highlights?docKey=    every saved mark for one document
 *   POST   /api/reader/highlights            save one mark
 *   POST   /api/reader/highlights/{id}/color  recolour one mark
 *   DELETE /api/reader/highlights/{id}       remove one mark
 *   POST   /api/reader/excerpt               save a mark to a research project as an excerpt
 *   GET    /api/reader/notes?docKey=         every note on every mark in one document
 *   POST   /api/reader/notes                 write one note against one mark
 *   POST   /api/reader/notes/{id}            rewrite one note
 *   DELETE /api/reader/notes/{id}            remove one note
 *   GET    /api/reader/position?docKey=      where the reader stopped in one document
 *   POST   /api/reader/position              record where the reader stopped
 *   DELETE /api/reader/position?docKey=      forget it, so the document opens at the top
 *
 * Looking a word up and browsing research projects reuse the surfaces that
 * already exist ({@code /api/dictionary/lookup} and {@code /api/research}); this
 * controller adds nothing for those.
 */
@RestController
@RequestMapping("/api/reader")
public class ReaderController {

    private final ReaderHighlightService service;
    private final ReaderPositionService  positions;

    public ReaderController(ReaderHighlightService service, ReaderPositionService positions) {
        this.service   = service;
        this.positions = positions;
    }

    /** One mark's anchor plus its text, as the reader page sends it. */
    public record HighlightRequest(String docKey, String docTitle, String viewMode,
                                   Integer pageNumber, Integer blockIndex,
                                   Integer startOffset, Integer endOffset,
                                   String selectedText, String colorCode) {}

    /** The colour a saved mark should be changed to. */
    public record ColorRequest(String colorCode) {}

    /** One note written against a mark. {@code markId} is ignored on an edit. */
    public record NoteRequest(Integer markId, String noteText) {}

    /** Where the reader stopped in one document, as the reader page sends it. */
    public record PositionRequest(String docKey, String docTitle, String viewMode,
                                  Integer blockIndex, Integer charOffset, Integer pageNumber,
                                  Double scrollRatio, Integer percentRead) {}

    /** A mark plus where on the Research shelf it should be filed. */
    public record ExcerptRequest(HighlightRequest highlight, Integer projectId,
                                 Integer sourceId, Integer themeId,
                                 String stance, String commentary, Boolean keyPoint) {}

    @GetMapping("/highlights")
    public ResponseEntity<?> listHighlights(@RequestParam(name = "docKey", required = false) String docKey) {
        try {
            List<ReaderHighlight> found = service.listForDocument(docKey);
            return ResponseEntity.ok(found);
        } catch (ReaderHighlightService.InvalidHighlightException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @PostMapping("/highlights")
    public ResponseEntity<?> saveHighlight(@RequestBody HighlightRequest req) {
        try {
            return ResponseEntity.ok(service.save(toHighlight(req)));
        } catch (ReaderHighlightService.InvalidHighlightException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @PostMapping("/highlights/{id}/color")
    public ResponseEntity<?> setHighlightColor(@PathVariable int id, @RequestBody ColorRequest req) {
        try {
            ReaderHighlight saved = service.setColor(id, req == null ? null : req.colorCode());
            if (saved == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(err("not_found", "That highlight no longer exists."));
            }
            return ResponseEntity.ok(saved);
        } catch (ReaderHighlightService.InvalidHighlightException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @DeleteMapping("/highlights/{id}")
    public ResponseEntity<?> deleteHighlight(@PathVariable int id) {
        service.delete(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/excerpt")
    public ResponseEntity<?> saveExcerpt(@RequestBody(required = false) ExcerptRequest req) {
        // Story 5653 (Echo): excerpts go to Leonardo's Research shelf, which Echo does not have.
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(err("not_available", "Echo has no Research shelf, so an excerpt cannot be saved here."));
    }

    // ---- notes on a mark (Story 2411) -------------------------------------

    @GetMapping("/notes")
    public ResponseEntity<?> listNotes(@RequestParam(name = "docKey", required = false) String docKey) {
        try {
            return ResponseEntity.ok(service.listNotesForDocument(docKey));
        } catch (ReaderHighlightService.InvalidHighlightException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @PostMapping("/notes")
    public ResponseEntity<?> addNote(@RequestBody NoteRequest req) {
        try {
            ReaderMarkNote saved = service.addNote(
                req == null || req.markId() == null ? 0 : req.markId().intValue(),
                req == null ? null : req.noteText());
            if (saved == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(err("not_found", "That mark no longer exists."));
            }
            return ResponseEntity.ok(saved);
        } catch (ReaderHighlightService.InvalidHighlightException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @PostMapping("/notes/{id}")
    public ResponseEntity<?> updateNote(@PathVariable int id, @RequestBody NoteRequest req) {
        try {
            ReaderMarkNote saved = service.updateNote(id, req == null ? null : req.noteText());
            if (saved == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(err("not_found", "That note no longer exists."));
            }
            return ResponseEntity.ok(saved);
        } catch (ReaderHighlightService.InvalidHighlightException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<?> deleteNote(@PathVariable int id) {
        service.deleteNote(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    // ---- where the reader stopped (Story 2422) ----------------------------

    /**
     * A document that was never read answers 200 with an empty body rather than
     * 404: not having been read is the normal case, not a failure, and the
     * reader treats it as "start at the top".
     */
    @GetMapping("/position")
    public ResponseEntity<?> getPosition(@RequestParam(name = "docKey", required = false) String docKey) {
        try {
            ReaderPosition found = positions.find(docKey);
            return found == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(found);
        } catch (ReaderPositionService.InvalidPositionException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @PostMapping("/position")
    public ResponseEntity<?> savePosition(@RequestBody PositionRequest req) {
        try {
            return ResponseEntity.ok(positions.save(toPosition(req)));
        } catch (ReaderPositionService.InvalidPositionException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    @DeleteMapping("/position")
    public ResponseEntity<?> forgetPosition(@RequestParam(name = "docKey", required = false) String docKey) {
        try {
            positions.forget(docKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            return ResponseEntity.ok(body);
        } catch (ReaderPositionService.InvalidPositionException ex) {
            return ResponseEntity.badRequest().body(err("bad_request", ex.getMessage()));
        }
    }

    // ---- mapping ----------------------------------------------------------

    private static ReaderHighlight toHighlight(HighlightRequest req) {
        ReaderHighlight h = new ReaderHighlight();
        if (req != null) {
            h.setDocKey(req.docKey());
            h.setDocTitle(req.docTitle());
            h.setViewMode(req.viewMode());
            h.setPageNumber(req.pageNumber());
            h.setBlockIndex(req.blockIndex() == null ? 0 : req.blockIndex());
            h.setStartOffset(req.startOffset() == null ? 0 : req.startOffset());
            h.setEndOffset(req.endOffset() == null ? 0 : req.endOffset());
            h.setSelectedText(req.selectedText());
            h.setColorCode(req.colorCode());
        }
        return h;
    }

    private static ReaderPosition toPosition(PositionRequest req) {
        ReaderPosition p = new ReaderPosition();
        if (req != null) {
            p.setDocKey(req.docKey());
            p.setDocTitle(req.docTitle());
            p.setViewMode(req.viewMode());
            p.setBlockIndex(req.blockIndex()  == null ? 0 : req.blockIndex());
            p.setCharOffset(req.charOffset()  == null ? 0 : req.charOffset());
            p.setPageNumber(req.pageNumber());
            p.setScrollRatio(req.scrollRatio() == null ? 0 : req.scrollRatio());
            p.setPercentRead(req.percentRead() == null ? 0 : req.percentRead());
        }
        return p;
    }

    private static Map<String, Object> err(String code, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", code);
        m.put("message", msg);
        return m;
    }
}
