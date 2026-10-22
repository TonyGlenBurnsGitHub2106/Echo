package com.echo;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * {@code GET /api/solutions/spec} -- the whole Solutions contract as one page of
 * plain markdown, written for an agent to read (Story 3493).
 *
 * An outside agent arrives with far less context than the house has. Rather than
 * rely on a person pasting instructions that then go stale, the agent fetches its
 * own instructions from the thing it is about to call, so what it reads and what
 * the server does cannot disagree. The text is a classpath resource,
 * {@code solutions/api-spec.md}: change an endpoint, change that file in the same edit.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionSpecController {

    static final String RESOURCE = "solutions/api-spec.md";

    private volatile String cached;

    /** Echo (Story 5653): the spec names this Echo's own address in place of {{BASE_URL}}. */
    private final String baseUrl;

    public SolutionSpecController(@org.springframework.beans.factory.annotation.Value("${echo.public.url:http://localhost:8770}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @GetMapping(value = "/spec", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> spec() {
        try {
            return ResponseEntity.ok(text());
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The contract could not be read.");
        }
    }

    String text() throws IOException {
        String t = cached;
        if (t == null) {
            try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
                t = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("{{BASE_URL}}", baseUrl);
            }
            cached = t;
        }
        return t;
    }
}
