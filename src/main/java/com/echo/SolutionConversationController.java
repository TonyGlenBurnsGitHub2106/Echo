package com.echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The conversation between an outside agent and the house (Story 3493).
 *
 *   GET    /api/solutions/{id}/conversations                       the threads linked to a solution
 *   POST   /api/solutions/{id}/conversations                       {title, message} -- open a thread
 *   GET    /api/solutions/conversations/{convId}/messages[?since=] messages newer than an id
 *   POST   /api/solutions/conversations/{convId}/messages          {body}
 *   DELETE /api/solutions/{id}/conversations/{convId}              unlink (a person only)
 *   PUT    /api/solutions/conversations/{convId}/resolved          {resolved} -- resolve / reopen (a person only)
 *
 * Nothing here can wake the AI. A post from an API client is stored as awaiting
 * Tony; a person relays it. See {@link SolutionConversationService}.
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionConversationController extends SolutionControllerSupport {

    private final SolutionConversationService service;

    public SolutionConversationController(SolutionConversationService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    @GetMapping("/{id:\\d+}/conversations")
    public ResponseEntity<?> list(HttpServletRequest req, @PathVariable int id) {
        return ResponseEntity.ok(service.list(caller(req), id));
    }

    public record ConversationRequest(String title, String message) {}

    @PostMapping("/{id:\\d+}/conversations")
    public ResponseEntity<?> create(HttpServletRequest req, @PathVariable int id, @RequestBody ConversationRequest body) {
        String title = required(body == null ? null : body.title(), "A title");
        return ResponseEntity.ok(service.create(caller(req), id, title, body.message()));
    }

    @GetMapping("/conversations/{convId:\\d+}/messages")
    public ResponseEntity<?> messages(HttpServletRequest req, @PathVariable int convId,
                                      @RequestParam(name = "since", required = false) Integer since) {
        return ResponseEntity.ok(service.messages(caller(req), convId, orZero(since)));
    }

    public record MessageRequest(String body) {}

    @PostMapping("/conversations/{convId:\\d+}/messages")
    public ResponseEntity<?> post(HttpServletRequest req, @PathVariable int convId, @RequestBody MessageRequest body) {
        String text = required(body == null ? null : body.body(), "Some text");
        return ResponseEntity.ok(service.post(caller(req), convId, text));
    }

    public record TitleRequest(String title) {}

    /** Owner only: rename a thread. */
    @PutMapping("/conversations/{convId:\\d+}")
    public ResponseEntity<?> rename(HttpServletRequest req, @PathVariable int convId, @RequestBody TitleRequest body) {
        return ResponseEntity.ok(service.rename(caller(req), convId, body == null ? null : body.title()));
    }

    public record ResolveRequest(Boolean resolved) {}

    /** Owner only: {resolved:true} marks a thread finished (nobody waited on); {resolved:false} reopens it. */
    @PutMapping("/conversations/{convId:\\d+}/resolved")
    public ResponseEntity<?> resolve(HttpServletRequest req, @PathVariable int convId, @RequestBody ResolveRequest body) {
        if (body == null || body.resolved() == null) throw new IllegalArgumentException("Say resolved true or false.");
        return ResponseEntity.ok(service.resolve(caller(req), convId, body.resolved()));
    }

    /** Owner only: change one message's text. */
    @PutMapping("/conversations/{convId:\\d+}/messages/{msgId:\\d+}")
    public ResponseEntity<?> editMessage(HttpServletRequest req, @PathVariable int convId, @PathVariable int msgId, @RequestBody MessageRequest body) {
        return ResponseEntity.ok(service.editMessage(caller(req), convId, msgId, body == null ? null : body.body()));
    }

    /** Owner only: remove one message. */
    @DeleteMapping("/conversations/{convId:\\d+}/messages/{msgId:\\d+}")
    public ResponseEntity<?> deleteMessage(HttpServletRequest req, @PathVariable int convId, @PathVariable int msgId) {
        service.deleteMessage(caller(req), convId, msgId);
        return ResponseEntity.ok(Map.of("ok", true, "messageId", msgId));
    }

    @DeleteMapping("/{id:\\d+}/conversations/{convId:\\d+}")
    public ResponseEntity<?> unlink(HttpServletRequest req, @PathVariable int id, @PathVariable int convId) {
        service.unlink(caller(req), id, convId);
        return ResponseEntity.ok(ok("unlinkedId", convId));
    }
}
