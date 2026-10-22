package com.echo;

import com.echo.SolutionModels.FileRow;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Files attached to a solution (Story 3493).
 *
 *   GET    /api/solutions/{id}/files[?itemId=]     the attached files
 *   POST   /api/solutions/{id}/files               multipart: file, [title], [notes], [itemId]
 *   GET    /api/solutions/files/{fileId}           one file's details
 *   PUT    /api/solutions/files/{fileId}           {title, notes, itemId}
 *   GET    /api/solutions/files/{fileId}/content   the bytes -- ALWAYS as a download
 *   DELETE /api/solutions/files/{fileId}           detach (a nook file goes to the Reading Nook trash)
 *
 * The download is served as {@code application/octet-stream} with
 * {@code Content-Disposition: attachment} and {@code nosniff}, whatever the file
 * claims to be. An outside agent may upload an .html or .svg; opened inline on
 * this origin it would run as the reader. As an attachment it is just a file.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionFileController extends SolutionControllerSupport {

    private final SolutionFileService service;

    public SolutionFileController(SolutionFileService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/files")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id,
                                  @RequestParam(name = "itemId", required = false) Integer itemId) {
        return ResponseEntity.ok(service.list(caller(req), id, itemId));
    }

    @PostMapping(value = "/{id:\\d+}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(HttpServletRequest req, @PathVariable int id,
                                    @RequestParam(value = "file", required = false) MultipartFile file,
                                    @RequestParam(value = "title",  required = false) String title,
                                    @RequestParam(value = "notes",  required = false) String notes,
                                    @RequestParam(value = "itemId", required = false) Integer itemId) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A file is required.");
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(service.upload(caller(req), id, itemId, title, notes, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), in));
        }
    }

    @GetMapping("/files/{fileId:\\d+}")
    public ResponseEntity<?> get(HttpServletRequest req, @PathVariable int fileId) {
        return ResponseEntity.ok(service.get(caller(req), fileId));
    }

    public record FileRequest(String title, String notes, Integer itemId) {}

    @PutMapping("/files/{fileId:\\d+}")
    public ResponseEntity<?> update(HttpServletRequest req, @PathVariable int fileId, @RequestBody FileRequest body) {
        if (body == null) throw new IllegalArgumentException("Nothing to change.");
        return ResponseEntity.ok(service.update(caller(req), fileId, body.itemId(), body.title(), body.notes()));
    }

    @GetMapping("/files/{fileId:\\d+}/content")
    public ResponseEntity<?> content(HttpServletRequest req, @PathVariable int fileId) {
        FileRow f = service.get(caller(req), fileId);
        Path path = service.pathOf(f);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(f.originalFileName(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
    }

    /**
     * Open Explorer on this computer at the folder holding the file, the file selected (2026-09-21).
     * A person only -- an outside agent can never make anything happen on this machine. Answers with
     * the folder, so the page can say where it is even when no one is at the desk.
     */
    @PostMapping("/files/{fileId:\\d+}/open-folder")
    public ResponseEntity<?> openFolder(HttpServletRequest req, @PathVariable int fileId) {
        return ResponseEntity.ok(ok("folder", service.openFolder(caller(req), fileId)));
    }

    @DeleteMapping("/files/{fileId:\\d+}")
    public ResponseEntity<?> delete(HttpServletRequest req, @PathVariable int fileId) throws IOException {
        service.delete(caller(req), fileId);
        return ResponseEntity.ok(ok("deletedId", fileId));
    }
}
