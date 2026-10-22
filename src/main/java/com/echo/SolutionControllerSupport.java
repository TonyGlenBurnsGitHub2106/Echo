package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What every Solutions-family controller shares (Story 3493): working out who is
 * calling, and turning the family's four refusals into the same four answers.
 *
 *   400  the request is malformed (a blank required field, an unreadable date)
 *   403  the caller may see the record but not take this action (a client asked to delete)
 *   404  the record does not exist, OR the caller is a client that may not see it
 *   409  a rule of the family refused it; the message is written for the reader
 *
 * The messages are safe to return: every one is authored in this family, none
 * carries a stack, a SQL fragment or a table name.
 */
abstract class SolutionControllerSupport {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SolutionCallerResolver callers;

    SolutionControllerSupport(SolutionCallerResolver callers) { this.callers = callers; }

    SolutionCaller caller(HttpServletRequest req) { return callers.resolve(req); }

    @ExceptionHandler(SolutionRuleException.class)
    public ResponseEntity<?> rule(SolutionRuleException ex) {
        log.info("Solutions rule refused: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("rule", ex.getMessage()));
    }

    @ExceptionHandler(SolutionNotFoundException.class)
    public ResponseEntity<?> notFound(SolutionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("not_found", ex.getMessage()));
    }

    @ExceptionHandler(SolutionForbiddenException.class)
    public ResponseEntity<?> forbidden(SolutionForbiddenException ex) {
        log.info("Solutions action refused: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("forbidden", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error("bad_request", ex.getMessage()));
    }

    static Map<String, Object> error(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", code);
        m.put("message", message);
        return m;
    }

    static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put(key, value);
        return m;
    }

    static String required(String value, String what) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(what + " is required.");
        return value.trim();
    }

    /** "2026-09-17" or "2026-09-17T14:30[:00]"; blank is null; anything else is a 400. */
    static LocalDateTime date(String value, String what) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try { return LocalDateTime.parse(v.length() == 10 ? v + "T00:00:00" : v); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException(what + " must look like 2026-09-17 or 2026-09-17T14:30."); }
    }

    static int orZero(Integer v) { return v == null ? 0 : v; }

    static boolean orFalse(Boolean v) { return v != null && v; }
}
