package com.echo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Story 5653 (Echo): the error answers of the record Web tab's controllers, which were copied from
 * LeonardoWeb and lean on its GlobalExceptionHandler. Echo has no global handler, so these few
 * mappings come along -- same statuses and the same {error} body Leonardo sends -- and only for these
 * controllers, so nothing else in Echo changes.
 */
@RestControllerAdvice(assignableTypes = {
    EntityWebAddressController.class, EntityWebFilingController.class, WebSiteTypeController.class,
    EntityLoginController.class, WebAddressLoginController.class, LookupController.class,
    EntityBrowseController.class
})
public class EntityRecordErrors {

    private static final Logger log = LoggerFactory.getLogger(EntityRecordErrors.class);

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<?> duplicate(DuplicateEntityException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SaveRefusedException.class)
    public ResponseEntity<?> refused(SaveRefusedException ex) {
        log.info("Save refused: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request: " + ex.getMessage()));
    }
}
