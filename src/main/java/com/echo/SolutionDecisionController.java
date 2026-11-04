package com.echo;

import com.echo.SolutionDecisionModels.Decision;
import com.echo.SolutionDecisionModels.DecisionDraft;
import com.echo.SolutionDecisionModels.LinkRef;
import com.echo.SolutionDecisionModels.Question;
import com.echo.SolutionDecisionModels.QuestionDraft;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A solution's questions and decisions (Story 5695, Echo first). Everything is under /api/solutions, the
 * door an agent a solution is shared with may use.
 *
 *   GET    /api/solutions/{id}/questions[?status=&search=&offset=&pageSize=]   one page, open first
 *   POST   /api/solutions/{id}/questions          {text, detail, ownerCode, links:[{kind,id}]}
 *   GET    /api/solutions/questions/{qid}         one question, whole
 *   GET    /api/solutions/questions/{qid}/locate
 *   PUT    /api/solutions/questions/{qid}         {text, detail, ownerCode} -- a merge: a missing field is kept
 *   POST   /api/solutions/questions/{qid}/status  {statusCode: open|answered|dropped}
 *   DELETE /api/solutions/questions/{qid}
 *   POST   /api/solutions/log/{logId}/question    a journal entry becomes a question, in place
 *
 *   GET    /api/solutions/{id}/decisions[?status=&search=&offset=&pageSize=]   the register, proposed first
 *   POST   /api/solutions/{id}/decisions          {title, context, options, outcome, statusCode, decidedDate, links}
 *   GET    /api/solutions/decisions/{did}         one decision, whole
 *   GET    /api/solutions/decisions/{did}/locate
 *   PUT    /api/solutions/decisions/{did}         {title, context, options, outcome, statusCode, decidedDate} -- a merge
 *   POST   /api/solutions/decisions/{did}/supersede  {title, context, options, outcome, links} -- a NEW decision replaces it
 *   DELETE /api/solutions/decisions/{did}         what it answered opens again; what it replaced is accepted again
 *   POST   /api/solutions/log/{logId}/decision    a journal entry becomes a decision, in place
 *   GET    /api/solutions/decisions/{did}/open-links   the linked questions and items still open
 *   POST   /api/solutions/decisions/{did}/settle  {questionIds:[...], itemIds:[...]} -- close them, one journal entry
 */
@RestController
@RequestMapping("/api/solutions")
public class SolutionDecisionController extends SolutionControllerSupport {

    private final SolutionDecisionService service;

    public SolutionDecisionController(SolutionDecisionService service, SolutionCallerResolver callers) {
        super(callers);
        this.service = service;
    }

    public record QuestionRequest(String text, String detail, String ownerCode, List<LinkRef> links) {}

    public record DecisionRequest(String title, String context, String options, String outcome, String statusCode,
                                  String decidedDate, List<LinkRef> links) {}

    public record StatusRequest(String statusCode) {}

    public record SettleRequest(List<Integer> questionIds, List<Integer> itemIds) {}

    // ---- questions ----

    @GetMapping("/{id:\\d+}/questions")
    public ResponseEntity<?> questions(HttpServletRequest req, @PathVariable int id,
                                       @RequestParam(name = "status",   required = false) String status,
                                       @RequestParam(name = "search",   required = false) String search,
                                       @RequestParam(name = "offset",   required = false) Integer offset,
                                       @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.questions(caller(req), id, status, search, orZero(offset), orZero(pageSize)));
    }

    @PostMapping("/{id:\\d+}/questions")
    public ResponseEntity<?> ask(HttpServletRequest req, @PathVariable int id, @RequestBody QuestionRequest body) {
        String text = required(body == null ? null : body.text(), "The question");
        return ResponseEntity.ok(service.saveQuestion(caller(req), new QuestionDraft(0, id, text, body.detail(), body.ownerCode()), body.links()));
    }

    @GetMapping("/questions/{qid:\\d+}")
    public ResponseEntity<?> question(HttpServletRequest req, @PathVariable int qid) {
        return ResponseEntity.ok(service.question(caller(req), qid));
    }

    @GetMapping("/questions/{qid:\\d+}/locate")
    public ResponseEntity<?> locateQuestion(HttpServletRequest req, @PathVariable int qid) {
        Question q = service.question(caller(req), qid);
        return ResponseEntity.ok(located("questionId", q.id(), q.solutionId()));
    }

    @PutMapping("/questions/{qid:\\d+}")
    public ResponseEntity<?> updateQuestion(HttpServletRequest req, @PathVariable int qid, @RequestBody QuestionRequest body) {
        if (body == null) throw new IllegalArgumentException("A question is required.");
        return ResponseEntity.ok(service.saveQuestion(caller(req), new QuestionDraft(qid, 0, body.text(), body.detail(), body.ownerCode()), null));
    }

    @PostMapping("/questions/{qid:\\d+}/status")
    public ResponseEntity<?> questionStatus(HttpServletRequest req, @PathVariable int qid, @RequestBody StatusRequest body) {
        return ResponseEntity.ok(service.setQuestionStatus(caller(req), qid, required(body == null ? null : body.statusCode(), "statusCode")));
    }

    @DeleteMapping("/questions/{qid:\\d+}")
    public ResponseEntity<?> deleteQuestion(HttpServletRequest req, @PathVariable int qid) {
        service.deleteQuestion(caller(req), qid);
        return ResponseEntity.ok(ok("deletedId", qid));
    }

    @PostMapping("/log/{logId:\\d+}/question")
    public ResponseEntity<?> questionFromLog(HttpServletRequest req, @PathVariable int logId) {
        return ResponseEntity.ok(service.questionFromLog(caller(req), logId));
    }

    // ---- decisions ----

    @GetMapping("/{id:\\d+}/decisions")
    public ResponseEntity<?> decisions(HttpServletRequest req, @PathVariable int id,
                                       @RequestParam(name = "status",   required = false) String status,
                                       @RequestParam(name = "search",   required = false) String search,
                                       @RequestParam(name = "offset",   required = false) Integer offset,
                                       @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ResponseEntity.ok(service.decisions(caller(req), id, status, search, orZero(offset), orZero(pageSize)));
    }

    @PostMapping("/{id:\\d+}/decisions")
    public ResponseEntity<?> decide(HttpServletRequest req, @PathVariable int id, @RequestBody DecisionRequest body) {
        String title = required(body == null ? null : body.title(), "A decision title");
        return ResponseEntity.ok(service.saveDecision(caller(req), new DecisionDraft(0, id, title, body.context(), body.options(),
            body.outcome(), body.statusCode(), date(body.decidedDate(), "decidedDate"), null), body.links()));
    }

    @GetMapping("/decisions/{did:\\d+}")
    public ResponseEntity<?> decision(HttpServletRequest req, @PathVariable int did) {
        return ResponseEntity.ok(service.decision(caller(req), did));
    }

    @GetMapping("/decisions/{did:\\d+}/locate")
    public ResponseEntity<?> locateDecision(HttpServletRequest req, @PathVariable int did) {
        Decision d = service.decision(caller(req), did);
        return ResponseEntity.ok(located("decisionId", d.id(), d.solutionId()));
    }

    @PutMapping("/decisions/{did:\\d+}")
    public ResponseEntity<?> updateDecision(HttpServletRequest req, @PathVariable int did, @RequestBody DecisionRequest body) {
        if (body == null) throw new IllegalArgumentException("A decision is required.");
        return ResponseEntity.ok(service.saveDecision(caller(req), new DecisionDraft(did, 0, body.title(), body.context(), body.options(),
            body.outcome(), body.statusCode(), date(body.decidedDate(), "decidedDate"), null), null));
    }

    @PostMapping("/decisions/{did:\\d+}/supersede")
    public ResponseEntity<?> supersede(HttpServletRequest req, @PathVariable int did, @RequestBody DecisionRequest body) {
        String title = required(body == null ? null : body.title(), "The new decision's title");
        return ResponseEntity.ok(service.supersede(caller(req), did, new DecisionDraft(0, 0, title, body.context(), body.options(),
            body.outcome(), null, date(body.decidedDate(), "decidedDate"), null), body.links()));
    }

    @DeleteMapping("/decisions/{did:\\d+}")
    public ResponseEntity<?> deleteDecision(HttpServletRequest req, @PathVariable int did) {
        service.deleteDecision(caller(req), did);
        return ResponseEntity.ok(ok("deletedId", did));
    }

    @PostMapping("/log/{logId:\\d+}/decision")
    public ResponseEntity<?> decisionFromLog(HttpServletRequest req, @PathVariable int logId) {
        return ResponseEntity.ok(service.decisionFromLog(caller(req), logId));
    }

    @GetMapping("/decisions/{did:\\d+}/open-links")
    public ResponseEntity<?> openLinks(HttpServletRequest req, @PathVariable int did) {
        return ResponseEntity.ok(service.openLinks(caller(req), did));
    }

    @PostMapping("/decisions/{did:\\d+}/settle")
    public ResponseEntity<?> settle(HttpServletRequest req, @PathVariable int did, @RequestBody SettleRequest body) {
        if (body == null) throw new IllegalArgumentException("Name the questions and items to close.");
        return ResponseEntity.ok(service.settle(caller(req), did, body.questionIds(), body.itemIds()));
    }

    private static Map<String, Object> located(String key, int id, int solutionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(key, id);
        body.put("solutionId", solutionId);
        return body;
    }
}
