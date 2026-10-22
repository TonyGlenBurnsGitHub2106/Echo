package com.echo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The server half of the Print menu (Story 5653) -- LeonardoWeb's {@code POST /api/pdf/generic},
 * the one call every "print this page" uses: the page sends its own XHTML, the server renders it
 * with openhtmltopdf (the same library and version) and answers the PDF.
 *
 * Echo's menu offers Print on this device, Display and Download, which all take the PDF back to the
 * browser; LeonardoWeb's other destinations (the Reading Nook, email, a record's Files tab, the home
 * printer) belong to the house and were not echoed. {@code GET /printers} answers an empty list so
 * the shared picker never offers a printer.
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private static final Logger log = LoggerFactory.getLogger(PdfController.class);

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) { this.pdfService = pdfService; }

    @GetMapping("/printers")
    public ResponseEntity<?> printers() { return ResponseEntity.ok(Map.of("printers", List.of())); }

    @PostMapping("/generic")
    public ResponseEntity<?> generic(@RequestBody(required = false) Map<String, Object> req) {
        if (req == null) return ResponseEntity.badRequest().body(Map.of("error", "missing body"));
        Object h = req.get("html"), f = req.get("filename");
        String html = h == null ? null : h.toString();
        String filename = f == null || f.toString().isBlank() ? "echo_print.pdf" : f.toString().trim();
        if (html == null || html.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "html is empty"));
        filename = filename.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", "_");
        if (!filename.toLowerCase().endsWith(".pdf")) filename = filename + ".pdf";

        byte[] pdf;
        try {
            pdf = pdfService.htmlToPdf(html);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("PDF render failed: filename={}", filename, ex);
            return ResponseEntity.status(500).body(Map.of("error", "PDF render failed: " + ex.getMessage()));
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(pdf);
    }
}
