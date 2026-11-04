package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDecisionModels.Decision
import com.echo.SolutionDecisionModels.DecisionDraft
import com.echo.SolutionDecisionModels.DecisionPage
import com.echo.SolutionDecisionModels.LinkRef
import com.echo.SolutionDecisionModels.OpenLink
import com.echo.SolutionDecisionModels.Question
import com.echo.SolutionDecisionModels.QuestionDraft
import com.echo.SolutionDecisionModels.QuestionPage
import com.echo.SolutionDecisionModels.Saved
import com.echo.SolutionDecisionModels.Settled
import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionModels.LogEntry
import static com.echo.SolutionFixtures.logEntry
import static com.echo.SolutionFixtures.lookups

import spock.lang.Unroll

/**
 * Story 5695: the business rules of a solution's questions and decisions -- access is checked before
 * anything is read or written (a question or decision is reached through its own solution, never the one a
 * caller names), the status rules ("current" only as a list filter, "superseded" only through supersede),
 * the add and merge-edit paths with the owner checked and the links checked before the save and made after
 * it, supersede, the journal promotions (access through the log entry), and settle: only the open records
 * linked to the decision can be closed, anything else is refused before anything changes, and one "result"
 * journal entry says what was closed.
 */
class SolutionDecisionServiceSpec extends SolutionServiceSupport {

    static final String QUESTION_STATUS_MESSAGE = "A question's status is one of open, answered, dropped."
    static final String DECISION_STATUS_MESSAGE = "A decision's status is one of proposed, accepted, superseded."

    SolutionDecisionRepository decisions = Mock()
    SolutionItemService items = Mock()
    SolutionLogService log = Mock()
    SolutionLinkService links = Mock()
    SolutionCodeCheck codes = new SolutionCodeCheck(solutionRepo)
    SolutionDecisionService service = new SolutionDecisionService(decisions, access, codes, items, log, links)

    Map<Integer, Question> questionRows = [5: question(5, 1), 6: question(6, 2)]
    Map<Integer, Decision> decisionRows = [20: decision(20, 1, 'accepted'), 21: decision(21, 2, 'accepted'),
                                           22: decision(22, 1, 'superseded'), 23: decision(23, 1, 'proposed')]

    def setup() {
        solutionRepo.lookups() >> lookups()
        decisions.findQuestion(_) >> { int id -> questionRows[id] }
        decisions.findDecision(_) >> { int id -> decisionRows[id] }
    }

    private static Question question(int id, int solutionId) {
        Question.of([SolutionQuestionID: id, SolutionID: solutionId, QuestionText: 'Q' + id, StatusCode: 'open'])
    }

    private static Decision decision(int id, int solutionId, String status) {
        Decision.of([SolutionDecisionID: id, SolutionID: solutionId, DecisionTitle: 'D' + id, StatusCode: status])
    }

    private static OpenLink openQuestion(int id) {
        new OpenLink('question', id, 'Q' + id, 'Question')
    }

    private static OpenLink openItem(int id) {
        new OpenLink('item', id, 'Item ' + id, 'Deploy | Not started')
    }

    private static DecisionDraft addDecision(int solutionId, String status, Integer supersedesId) {
        new DecisionDraft(0, solutionId, 'Use UTF-8', null, null, null, status, null, supersedesId)
    }

    /** "question:Q5;item:Upgrade" to the closed list settledText is given; "" is none. */
    private static List<OpenLink> closedOf(String cell) {
        cell.isEmpty() ? [] : cell.split(';').collect { String s ->
            String[] p = s.split(':', 2)
            new OpenLink(p[0], 1, p[1], null)
        }
    }

    /** Runs one named solution-scoped call -- the calls a data table can name. */
    private static Object solutionCall(SolutionDecisionService s, String action, SolutionCaller caller, int solutionId) {
        switch (action) {
            case 'questions':   return s.questions(caller, solutionId, null, null, 0, 50)
            case 'ask':         return s.saveQuestion(caller, new QuestionDraft(0, solutionId, 'Why?', null, null), [new LinkRef('item', 100)])
            case 'decisions':   return s.decisions(caller, solutionId, null, null, 0, 50)
            case 'decide':      return s.saveDecision(caller, addDecision(solutionId, null, null), [new LinkRef('question', 5)])
            default: throw new IllegalStateException(action)
        }
    }

    /** Runs one named record-scoped call against question / decision {@code id}. */
    private static Object recordCall(SolutionDecisionService s, String action, SolutionCaller caller, int id) {
        switch (action) {
            case 'question':       return s.question(caller, id)
            case 'editQuestion':   return s.saveQuestion(caller, new QuestionDraft(id, 1, 'x', null, null), null)
            case 'questionStatus': return s.setQuestionStatus(caller, id, 'dropped')
            case 'deleteQuestion': s.deleteQuestion(caller, id); return null
            case 'decision':       return s.decision(caller, id)
            case 'editDecision':   return s.saveDecision(caller, new DecisionDraft(id, 1, 'x', null, null, null, null, null, null), null)
            case 'supersede':      return s.supersede(caller, id, addDecision(1, null, null), null)
            case 'deleteDecision': s.deleteDecision(caller, id); return null
            case 'openLinks':      return s.openLinks(caller, id)
            case 'settle':         return s.settle(caller, id, [5], [])
            default: throw new IllegalStateException(action)
        }
    }

    // ---- access first ----

    @Unroll
    def "#seq. #action should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        solutionCall(service, action, caller, solutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * decisions._
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
        0 * items._
        0 * log._

        where:
        seq | scenario                                 | action      | callerName | solutionId || expectedMessage
        1   | 'the client was given nothing'           | 'questions' | 'ZED'      | 1          || 'Solution not found.'
        2   | 'the client was not given the solution'  | 'questions' | 'REX'      | 2          || 'Solution not found.'
        3   | 'the solution does not exist'            | 'ask'       | 'TONY'     | 9          || 'Solution not found.'
        4   | 'the client was not given the solution'  | 'ask'       | 'REX'      | 2          || 'Solution not found.'
        5   | 'the id is zero'                         | 'decisions' | 'HOUSE'    | 0          || 'Solution not found.'
        6   | 'the client was given nothing'           | 'decisions' | 'ZED'      | 1          || 'Solution not found.'
        7   | 'the id is negative'                     | 'decide'    | 'TONY'     | -1         || 'Solution not found.'
        8   | 'the client was not given the solution'  | 'decide'    | 'REX'      | 2          || 'Solution not found.'
    }

    @Unroll
    def "#seq. #action should write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        recordCall(service, action, caller, id)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.saveQuestion(*_)
        0 * decisions.setQuestionStatus(*_)
        0 * decisions.deleteQuestion(_)
        0 * decisions.saveDecision(*_)
        0 * decisions.deleteDecision(_)
        0 * decisions.openLinks(_)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
        0 * items._
        0 * log._

        where:
        seq | scenario                                         | action           | callerName | id || expectedMessage
        1   | 'the client was not given its solution'          | 'question'       | 'REX'      | 6  || 'Solution not found.'
        2   | 'the edit names a solution the client may see'   | 'editQuestion'   | 'REX'      | 6  || 'Solution not found.'
        3   | 'the client was given nothing'                   | 'questionStatus' | 'ZED'      | 5  || 'Solution not found.'
        4   | 'the client was not given its solution'          | 'deleteQuestion' | 'REX'      | 6  || 'Solution not found.'
        5   | 'the client was given nothing'                   | 'decision'       | 'ZED'      | 20 || 'Solution not found.'
        6   | 'the edit names a solution the client may see'   | 'editDecision'   | 'REX'      | 21 || 'Solution not found.'
        7   | 'the client was not given its solution'          | 'supersede'      | 'REX'      | 21 || 'Solution not found.'
        8   | 'the client was given nothing'                   | 'deleteDecision' | 'ZED'      | 20 || 'Solution not found.'
        9   | 'the client was not given its solution'          | 'openLinks'      | 'REX'      | 21 || 'Solution not found.'
        10  | 'the client was not given its solution'          | 'settle'         | 'REX'      | 21 || 'Solution not found.'
        11  | 'the question does not exist'                    | 'editQuestion'   | 'TONY'     | 99 || 'Question not found.'
        12  | 'the question does not exist'                    | 'deleteQuestion' | 'TONY'     | 99 || 'Question not found.'
        13  | 'the decision does not exist'                    | 'supersede'      | 'TONY'     | 99 || 'Decision not found.'
        14  | 'the decision does not exist'                    | 'settle'         | 'TONY'     | 99 || 'Decision not found.'
    }

    def "questions and setQuestionStatus should check access before the status when the solution is hidden and the status is unknown"() {

        when:
        service.questions(ZED, 1, 'bogus', null, 0, 50)

        then:
        thrown(SolutionNotFoundException)

        when:
        service.setQuestionStatus(REX, 6, 'bogus')

        then:
        thrown(SolutionNotFoundException)
        0 * decisions.setQuestionStatus(*_)
    }

    def "saveDecision should check access before the status when the solution is hidden and the status is superseded"() {

        when:
        service.saveDecision(REX, addDecision(2, 'superseded', null), null)

        then:
        thrown(SolutionNotFoundException)
        0 * decisions.saveDecision(*_)
    }

    // ---- the status rules ----

    @Unroll
    def "#seq. questionStatus should answer #expected when #scenario"() {

        expect:
        SolutionDecisionService.questionStatus(status, blankIsAll) == expected

        where:
        seq | scenario                                  | status        | blankIsAll || expected
        1   | 'a list asks for no status'               | null          | true       || null
        2   | 'a list asks for an empty status'         | ''            | true       || null
        3   | 'a list asks for a blank status'          | '   '         | true       || null
        4   | 'a list asks for open'                    | 'open'        | true       || 'open'
        5   | 'a list asks for padded capitals'         | ' ANSWERED '  | true       || 'answered'
        6   | 'a change asks for dropped'               | 'dropped'     | false      || 'dropped'
        7   | 'a change asks for Open'                  | 'Open'        | false      || 'open'
        8   | 'a change has a tab and a newline'        | '\tdropped\n' | false      || 'dropped'
    }

    @Unroll
    def "#seq. questionStatus should refuse when #scenario"() {

        when:
        SolutionDecisionService.questionStatus(status, blankIsAll)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        seq | scenario                               | status          | blankIsAll || expectedMessage
        1   | 'a change names no status'             | null            | false      || "A question's status is one of open, answered, dropped."
        2   | 'a change names an empty status'       | ''              | false      || "A question's status is one of open, answered, dropped."
        3   | 'a change names a blank status'        | '  '            | false      || "A question's status is one of open, answered, dropped."
        4   | 'a list asks for current'              | 'current'       | true       || "A question's status is one of open, answered, dropped."
        5   | 'a change asks for current'            | 'current'       | false      || "A question's status is one of open, answered, dropped."
        6   | 'a list asks for closed'               | 'closed'        | true       || "A question's status is one of open, answered, dropped."
        7   | 'a change asks for opened'             | 'opened'        | false      || "A question's status is one of open, answered, dropped."
        8   | 'a list asks for two statuses'         | 'open,answered' | true       || "A question's status is one of open, answered, dropped."
    }

    @Unroll
    def "#seq. decisionStatus should answer #expected when #scenario"() {

        expect:
        SolutionDecisionService.decisionStatus(status, blankIsAll) == expected

        where:
        seq | scenario                                 | status        | blankIsAll || expected
        1   | 'a list asks for no status'              | null          | true       || null
        2   | 'a list asks for a blank status'         | '  '          | true       || null
        3   | 'a list asks for current, padded'        | ' current '   | true       || 'current'
        4   | 'a list asks for CURRENT'                | 'CURRENT'     | true       || 'current'
        5   | 'a list asks for superseded'             | 'superseded'  | true       || 'superseded'
        6   | 'a change asks for proposed'             | 'proposed'    | false      || 'proposed'
        7   | 'a change asks for padded Accepted'      | ' Accepted '  | false      || 'accepted'
        8   | 'a change asks for superseded'           | 'superseded'  | false      || 'superseded'
    }

    @Unroll
    def "decisionStatus should refuse when #scenario"() {

        when:
        SolutionDecisionService.decisionStatus(status, blankIsAll)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                                 | status     | blankIsAll || expectedMessage
        'a change asks for current'              | 'current'  | false      || "A decision's status is one of proposed, accepted, superseded."
        'a change names no status'               | null       | false      || "A decision's status is one of proposed, accepted, superseded."
        'a change names an empty status'         | ''         | false      || "A decision's status is one of proposed, accepted, superseded."
        'a list asks for done'                   | 'done'     | true       || "A decision's status is one of proposed, accepted, superseded."
        'a change asks for decided'              | 'decided'  | false      || "A decision's status is one of proposed, accepted, superseded."
        'a list asks for replaced'               | 'replaced' | true       || "A decision's status is one of proposed, accepted, superseded."
    }

    // ---- checkLinks ----

    @Unroll
    def "checkLinks should pass when #scenario"() {

        when:
        SolutionDecisionService.checkLinks(kind == 'NONE' ? null : [new LinkRef(kind, id)])

        then:
        noExceptionThrown()

        where:
        scenario                         | kind       | id  || unused
        'there are no links'             | 'NONE'     | 0   || null
        'an item is named'               | 'item'     | 100 || null
        'a question is named'            | 'question' | 5   || null
        'the kind is padded capitals'    | ' DECISION ' | 20 || null
    }

    def "checkLinks should pass when the list is empty"() {

        when:
        SolutionDecisionService.checkLinks([])

        then:
        noExceptionThrown()
    }

    @Unroll
    def "checkLinks should refuse with '#expectedMessage' when #scenario"() {

        given:
        List<LinkRef> refs = isNullEntry ? [new LinkRef('item', 100), null] : [new LinkRef('item', 100), new LinkRef(kind, id)]

        when:
        SolutionDecisionService.checkLinks(refs)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                      | isNullEntry | kind    | id   || expectedMessage
        'an entry is null'            | true        | null    | null || 'Each link needs a kind and an id.'
        'a kind is null'              | false       | null    | 5    || 'Each link needs a kind and an id.'
        'an id is null'               | false       | 'note'  | null || 'Each link needs a kind and an id.'
        'an id is zero'               | false       | 'note'  | 0    || 'Each link needs a kind and an id.'
        'an id is negative'           | false       | 'note'  | -3   || 'Each link needs a kind and an id.'
        'a kind is unknown'           | false       | 'story' | 5    || 'kind must be one of item, note, log, file, term, topic, question, decision.'
        'a kind is blank'             | false       | '  '    | 5    || 'kind must be one of item, note, log, file, term, topic, question, decision.'
    }

    // ---- questions ----

    def "questions should list one page with its counts and the status and search tidied when the solution is visible"() {

        when:
        QuestionPage p = service.questions(REX, 1, ' Open ', '  charset ', 10, 20)

        then:
        1 * decisions.questionCounts(1, 'open', 'charset') >> [total: 3, all: 9, 'status:open': 3]
        1 * decisions.questions(1, 'open', 'charset', 10, 20) >> [questionRows[5]]
        p.questions()*.id() == [5]
        p.counts() == [total: 3, all: 9, 'status:open': 3]
        p.offset() == 10
        p.pageSize() == 20
        p.total() == 3
    }

    @Unroll
    def "#seq. questions should ask for search #expectedSearch, offset #expectedOffset and page size #expectedSize when #scenario"() {

        when:
        service.questions(TONY, 1, null, search, offset, pageSize)

        then:
        1 * decisions.questionCounts(1, null, expectedSearch) >> [total: 0]
        1 * decisions.questions(1, null, expectedSearch, expectedOffset, expectedSize) >> []

        where:
        seq | scenario                       | search  | offset      | pageSize || expectedSearch | expectedOffset | expectedSize
        1   | 'nothing is asked'             | null    | 0           | 0        || null           | 0              | 50
        2   | 'the search is blank'          | '   '   | 0           | 0        || null           | 0              | 50
        3   | 'the offset is negative'       | null    | -5          | 10       || null           | 0              | 10
        4   | 'the offset is the minimum'    | null    | -2147483648 | 10       || null           | 0              | 10
        5   | 'the page size is negative'    | null    | 0           | -1       || null           | 0              | 50
        6   | 'the cap is asked'             | null    | 0           | 500      || null           | 0              | 500
        7   | 'one over the cap is asked'    | null    | 0           | 501      || null           | 0              | 500
        8   | 'a later page is searched'     | ' 50% ' | 75          | 25       || '50%'          | 75             | 25
    }

    def "questions should answer a total of zero when there is no total counter"() {

        when:
        QuestionPage p = service.questions(TONY, 1, null, null, 0, 50)

        then:
        1 * decisions.questionCounts(*_) >> [all: 4]
        1 * decisions.questions(*_) >> []
        p.total() == 0
        p.counts() == [all: 4]
    }

    def "questions should refuse an unknown status and read nothing"() {

        when:
        service.questions(TONY, 1, 'current', null, 0, 50)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == QUESTION_STATUS_MESSAGE
        0 * decisions.questionCounts(*_)
        0 * decisions.questions(*_)
    }

    def "question should answer the question when the caller may see its solution"() {

        expect:
        service.question(TONY, 6).id() == 6
        service.question(REX, 5).solutionId() == 1
        service.question(HOUSE, 6).solutionId() == 2
    }

    @Unroll
    def "question should throw question not found and not reach the repository when the id is #scenario"() {

        when:
        service.question(TONY, questionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.findQuestion(_)

        where:
        scenario     | questionId || expectedMessage
        'zero'       | 0          || 'Question not found.'
        'negative'   | -1         || 'Question not found.'
    }

    @Unroll
    def "saveQuestion should add the question attributed to #expectedCode and then link it to every record named when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        QuestionDraft draft = new QuestionDraft(0, 1, 'Which charset?', 'Remit says 1252.', null)
        List<LinkRef> linkTo = [new LinkRef('item', 100), new LinkRef(' Decision ', 20)]

        when:
        Question q = service.saveQuestion(caller, draft, linkTo)

        then:
        1 * decisions.saveQuestion(draft, expectedCode) >> 5

        then:
        1 * links.link(caller, 1, 'question', 5, 'item', 100)
        1 * links.link(caller, 1, 'question', 5, ' Decision ', 20)
        q.id() == 5

        where:
        scenario               | callerName || expectedCode
        'a person asks it'     | 'TONY'     || 'tony'
        'the house asks it'    | 'HOUSE'    || 'felix'
        'a client asks it'     | 'REX'      || 'rex'
    }

    @Unroll
    def "saveQuestion should add the question and link nothing when #scenario"() {

        given:
        List<LinkRef> linkTo = isNull ? null : []

        when:
        service.saveQuestion(TONY, new QuestionDraft(0, 1, 'Why?', null, null), linkTo)

        then:
        1 * decisions.saveQuestion(*_) >> 5
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        where:
        scenario                 | isNull || unused
        'no links are named'     | true   || null
        'the link list is empty' | false  || null
    }

    def "saveQuestion should check the links before the save and save nothing when a link is incomplete or of an unknown kind"() {

        when:
        service.saveQuestion(TONY, new QuestionDraft(0, 1, 'Why?', null, null), [new LinkRef('item', 100), new LinkRef('item', null)])

        then:
        IllegalArgumentException incomplete = thrown()
        incomplete.message == 'Each link needs a kind and an id.'
        0 * decisions.saveQuestion(*_)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        when:
        service.saveQuestion(TONY, new QuestionDraft(0, 1, 'Why?', null, null), [new LinkRef('story', 1)])

        then:
        IllegalArgumentException unknown = thrown()
        unknown.message == 'kind must be one of item, note, log, file, term, topic, question, decision.'
        0 * decisions.saveQuestion(*_)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
    }

    def "saveQuestion should merge-edit through the question's own solution and never link, nor check links, when an edit is saved"() {

        given:
        QuestionDraft draft = new QuestionDraft(5, 0, null, 'New detail', null)

        when:
        Question q = service.saveQuestion(REX, draft, [new LinkRef('story', null)])

        then:
        1 * decisions.saveQuestion(draft, 'rex') >> 5
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
        q.id() == 5
    }

    @Unroll
    def "saveQuestion should save the owner when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.saveQuestion(caller, new QuestionDraft(0, 1, 'Why?', null, ownerCode), null)

        then:
        1 * decisions.saveQuestion(new QuestionDraft(0, 1, 'Why?', null, ownerCode), expectedCode) >> 5

        where:
        scenario                        | callerName | ownerCode || expectedCode
        'a known member is named'       | 'TONY'     | 'felix'   || 'tony'
        'a member is named in capitals' | 'TONY'     | ' FELIX ' || 'tony'
        'the owner is cleared'          | 'TONY'     | ''        || 'tony'
        'a client names itself'         | 'REX'      | 'rex'     || 'rex'
    }

    def "saveQuestion should not consult the members when no owner is named"() {

        when:
        service.saveQuestion(TONY, new QuestionDraft(0, 1, 'Why?', null, null), null)

        then:
        0 * solutionRepo.lookups()
        1 * decisions.saveQuestion(*_) >> 5
    }

    def "saveQuestion should make the client a member when it names itself as the owner"() {

        when:
        service.saveQuestion(REX, new QuestionDraft(0, 1, 'Why?', null, 'rex'), null)

        then:
        1 * solutionRepo.saveMember(0, 'rex', 'Rex', true, true, 3)
        1 * decisions.saveQuestion(*_) >> 5
    }

    @Unroll
    def "saveQuestion should refuse the owner and save nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.saveQuestion(caller, new QuestionDraft(0, 1, 'Why?', null, ownerCode), null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.saveQuestion(*_)

        where:
        scenario                          | callerName | ownerCode || expectedMessage
        'nobody has that code'            | 'TONY'     | ' bob '   || "There is no member code 'bob'. The member codes are: tony, felix. Nothing was saved. GET /api/solutions/lookups lists every code."
        'the member is no longer active'  | 'TONY'     | 'old'     || "There is no member code 'old'. The member codes are: tony, felix. Nothing was saved. GET /api/solutions/lookups lists every code."
        'a client names another client'   | 'REX'      | 'zed'     || "There is no member code 'zed'. The member codes are: tony, felix. Nothing was saved. GET /api/solutions/lookups lists every code."
    }

    @Unroll
    def "saveQuestion should throw question not found and link nothing when the repository answers a #scenario id"() {

        when:
        service.saveQuestion(TONY, new QuestionDraft(0, 1, 'Why?', null, null), [new LinkRef('item', 100)])

        then:
        1 * decisions.saveQuestion(*_) >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        where:
        scenario      | savedId || expectedMessage
        'zero'        | 0       || 'Question not found.'
        'negative'    | -1      || 'Question not found.'
    }

    def "saveQuestion should pass a refusal through and link nothing when the repository refuses a repeat question"() {

        when:
        service.saveQuestion(TONY, new QuestionDraft(0, 1, 'Q5', null, null), [new LinkRef('item', 100)])

        then:
        1 * decisions.saveQuestion(*_) >> { throw new SolutionRuleException('That question is already open in this solution. Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'That question is already open in this solution. Nothing was saved.'
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
    }

    @Unroll
    def "setQuestionStatus should set #expected by hand, attributed to the caller, when #scenario"() {

        when:
        Question q = service.setQuestionStatus(REX, 5, status)

        then:
        1 * decisions.setQuestionStatus(5, expected, null, 'rex') >> true
        q.id() == 5

        where:
        scenario                         | status       || expected
        'the question is reopened'       | 'open'       || 'open'
        'the question is answered'       | ' Answered ' || 'answered'
        'the question is dropped'        | 'DROPPED'    || 'dropped'
    }

    @Unroll
    def "setQuestionStatus should refuse and write nothing when #scenario"() {

        when:
        service.setQuestionStatus(TONY, 5, status)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.setQuestionStatus(*_)

        where:
        scenario                     | status    || expectedMessage
        'current is asked'           | 'current' || "A question's status is one of open, answered, dropped."
        'the status is empty'        | ''        || "A question's status is one of open, answered, dropped."
        'the status is missing'      | null      || "A question's status is one of open, answered, dropped."
    }

    def "setQuestionStatus should throw question not found when the repository changed nothing"() {

        when:
        service.setQuestionStatus(TONY, 5, 'dropped')

        then:
        1 * decisions.setQuestionStatus(5, 'dropped', null, 'tony') >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Question not found.'
    }

    def "deleteQuestion should delete a visible question and throw not found when nothing was removed"() {

        when:
        service.deleteQuestion(REX, 5)

        then:
        1 * decisions.deleteQuestion(5) >> true

        when:
        service.deleteQuestion(TONY, 6)

        then:
        1 * decisions.deleteQuestion(6) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Question not found.'
    }

    @Unroll
    def "questionFromLog should check the entry through the log service and then promote it for #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        Question q = service.questionFromLog(caller, 200)

        then:
        1 * log.get(caller, 200) >> logEntry(id: 200, solutionId: 1)

        then:
        1 * decisions.questionFromLog(200, expectedCode) >> 5
        q.id() == 5

        where:
        scenario                   | callerName || expectedCode
        'a person promotes it'     | 'TONY'     || 'tony'
        'a client promotes it'     | 'REX'      || 'rex'
    }

    @Unroll
    def "questionFromLog should promote nothing when #scenario"() {

        when:
        service.questionFromLog(REX, 201)

        then:
        1 * log.get(REX, 201) >> { throw new SolutionNotFoundException(what) }
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.questionFromLog(*_)

        where:
        scenario                               | what        || expectedMessage
        'the entry is in a hidden solution'    | 'Solution'  || 'Solution not found.'
        'the entry does not exist'             | 'Log entry' || 'Log entry not found.'
    }

    def "questionFromLog should throw log entry not found when the repository answers no id"() {

        when:
        service.questionFromLog(TONY, 200)

        then:
        1 * log.get(TONY, 200) >> logEntry(id: 200)
        1 * decisions.questionFromLog(200, 'tony') >> 0
        SolutionNotFoundException ex = thrown()
        ex.message == 'Log entry not found.'
    }

    def "questionFromLog should pass a refusal through when the entry is already a question"() {

        when:
        service.questionFromLog(TONY, 200)

        then:
        1 * log.get(TONY, 200) >> logEntry(id: 200)
        1 * decisions.questionFromLog(200, 'tony') >> { throw new SolutionRuleException('That log entry is already a question. Nothing was added.') }
        SolutionRuleException ex = thrown()
        ex.message == 'That log entry is already a question. Nothing was added.'
    }

    // ---- decisions ----

    @Unroll
    def "decisions should ask for status #expectedStatus with the search tidied and a page of #expectedSize when #scenario"() {

        when:
        DecisionPage p = service.decisions(REX, 1, status, search, offset, pageSize)

        then:
        1 * decisions.decisionCounts(1, expectedStatus, expectedSearch) >> [total: 2, all: 5]
        1 * decisions.decisions(1, expectedStatus, expectedSearch, expectedOffset, expectedSize) >> [decisionRows[20]]
        p.decisions()*.id() == [20]
        p.total() == 2
        p.offset() == expectedOffset
        p.pageSize() == expectedSize

        where:
        scenario                          | status        | search     | offset | pageSize || expectedStatus | expectedSearch | expectedOffset | expectedSize
        'every decision is asked'         | null          | null       | 0      | 0        || null           | null           | 0              | 50
        'the current register is asked'   | ' Current '   | ' remit '  | -1     | 501      || 'current'      | 'remit'        | 0              | 500
        'the replaced ones are asked'     | 'superseded'  | '  '       | 10     | 20       || 'superseded'   | null           | 10             | 20
        'the proposals are asked'         | 'PROPOSED'    | null       | 0      | 5        || 'proposed'     | null           | 0              | 5
    }

    def "decisions should refuse an unknown status and read nothing"() {

        when:
        service.decisions(TONY, 1, 'done', null, 0, 50)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == DECISION_STATUS_MESSAGE
        0 * decisions.decisionCounts(*_)
        0 * decisions.decisions(*_)
    }

    def "decision should answer the decision when the caller may see its solution and throw not found for a missing one"() {

        when:
        Decision d = service.decision(REX, 20)

        then:
        d.id() == 20

        when:
        service.decision(TONY, 0)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Decision not found.'
        0 * decisions.findDecision(_)
    }

    @Unroll
    def "saveDecision should record the decision attributed to #expectedCode and then link it to every record named when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        DecisionDraft draft = addDecision(1, null, null)

        when:
        Decision d = service.saveDecision(caller, draft, [new LinkRef('question', 5), new LinkRef('item', 100)])

        then:
        1 * decisions.saveDecision(draft, expectedCode) >> new Saved(30, 410)

        then:
        1 * links.link(caller, 1, 'decision', 30, 'question', 5)
        1 * links.link(caller, 1, 'decision', 30, 'item', 100)
        1 * decisions.findDecision(30) >> decision(30, 1, 'accepted')
        d.id() == 30

        where:
        scenario                  | callerName || expectedCode
        'a person decides'        | 'TONY'     || 'tony'
        'the house decides'       | 'HOUSE'    || 'felix'
        'a client decides'        | 'REX'      || 'rex'
    }

    @Unroll
    def "saveDecision should pass the draft as sent when the status is #scenario"() {

        given:
        DecisionDraft draft = addDecision(1, status, null)

        when:
        service.saveDecision(TONY, draft, null)

        then:
        1 * decisions.saveDecision(draft, 'tony') >> new Saved(30, 410)
        1 * decisions.findDecision(30) >> decision(30, 1, 'accepted')

        where:
        scenario                | status       || unused
        'left out'              | null         || null
        'empty'                 | ''           || null
        'blank'                 | '   '        || null
        'proposed'              | 'proposed'   || null
        'padded Accepted'       | ' Accepted ' || null
    }

    @Unroll
    def "saveDecision should refuse and save nothing when #scenario"() {

        when:
        service.saveDecision(TONY, new DecisionDraft(id, 1, 'Use UTF-8', null, null, null, status, null, supersedesId), null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.saveDecision(*_)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        where:
        scenario                                  | id | status         | supersedesId || expectedMessage
        'an add names superseded'                 | 0  | 'superseded'   | null         || 'A decision is superseded by recording a newer one (supersede), never by setting its status.'
        'an edit names padded SUPERSEDED'         | 20 | ' SUPERSEDED ' | null         || 'A decision is superseded by recording a newer one (supersede), never by setting its status.'
        'an add names current'                    | 0  | 'current'      | null         || "A decision's status is one of proposed, accepted, superseded."
        'an edit names decided'                   | 20 | 'decided'      | null         || "A decision's status is one of proposed, accepted, superseded."
        'an edit names the decision it replaces'  | 20 | null           | 23           || 'Only a new decision can supersede another.'
    }

    @Unroll
    def "saveDecision should save an edit when its supersedes id is #scenario"() {

        given:
        DecisionDraft draft = new DecisionDraft(20, 0, null, null, null, 'New outcome', null, null, supersedesId)

        when:
        service.saveDecision(REX, draft, null)

        then:
        1 * decisions.saveDecision(draft, 'rex') >> new Saved(20, 410)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        where:
        scenario      | supersedesId || unused
        'missing'     | null         || null
        'zero'        | 0            || null
        'negative'    | -1           || null
    }

    @Unroll
    def "saveDecision should save nothing when the decision it replaces #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.saveDecision(caller, addDecision(1, null, supersedesId), null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.saveDecision(*_)

        where:
        scenario                               | callerName | supersedesId || expectedMessage
        'is in a solution the client may not see' | 'REX'   | 21           || 'Solution not found.'
        'does not exist'                       | 'TONY'     | 99           || 'Decision not found.'
    }

    def "saveDecision should check the links before the save and save nothing when a link is bad"() {

        when:
        service.saveDecision(TONY, addDecision(1, null, null), [new LinkRef('question', 5), null])

        then:
        IllegalArgumentException incomplete = thrown()
        incomplete.message == 'Each link needs a kind and an id.'
        0 * decisions.saveDecision(*_)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        when:
        service.saveDecision(TONY, addDecision(1, null, null), [new LinkRef('message', 9)])

        then:
        IllegalArgumentException unknown = thrown()
        unknown.message == 'kind must be one of item, note, log, file, term, topic, question, decision.'
        0 * decisions.saveDecision(*_)
    }

    def "saveDecision should merge-edit through the decision's own solution and never link when an edit is saved"() {

        given:
        DecisionDraft draft = new DecisionDraft(20, 0, null, '', null, null, 'accepted', null, null)

        when:
        Decision d = service.saveDecision(REX, draft, [new LinkRef('item', 100)])

        then:
        1 * decisions.saveDecision(draft, 'rex') >> new Saved(20, 410)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
        d.id() == 20
    }

    @Unroll
    def "saveDecision should throw decision not found and link nothing when the repository answers #scenario"() {

        when:
        service.saveDecision(TONY, addDecision(1, null, null), [new LinkRef('item', 100)])

        then:
        1 * decisions.saveDecision(*_) >> new Saved(savedId, null)
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing

        where:
        scenario          | savedId || expectedMessage
        'no id'           | 0       || 'Decision not found.'
        'a negative id'   | -1      || 'Decision not found.'
    }

    def "saveDecision should pass a refusal through when the repository refuses a repeat title"() {

        when:
        service.saveDecision(TONY, addDecision(1, null, null), null)

        then:
        1 * decisions.saveDecision(*_) >> { throw new SolutionRuleException('This solution already has that decision. Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'This solution already has that decision. Nothing was saved.'
    }

    @Unroll
    def "supersede should record an accepted decision in the old one's solution replacing it, whatever the replacement says, when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LocalDateTime decided = getLocalDateTime('11/04/2026 08:00')
        DecisionDraft replacement = new DecisionDraft(77, 9, 'Read everything as UTF-8', 'ctx', 'opts', 'out', 'proposed', decided, 5)

        when:
        Decision d = service.supersede(caller, oldId, replacement, [new LinkRef('item', 100)])

        then:
        1 * decisions.saveDecision(new DecisionDraft(0, 1, 'Read everything as UTF-8', 'ctx', 'opts', 'out', 'accepted', decided, oldId), expectedCode) >>
            new Saved(30, 411)

        then:
        1 * links.link(caller, 1, 'decision', 30, 'item', 100)
        1 * decisions.findDecision(30) >> decision(30, 1, 'accepted')
        d.id() == 30

        where:
        scenario                        | callerName | oldId || expectedCode
        'an accepted decision changes'  | 'TONY'     | 20    || 'tony'
        'a proposal is replaced'        | 'REX'      | 23    || 'rex'
    }

    def "supersede should refuse and save nothing when the decision was already replaced"() {

        when:
        service.supersede(TONY, 22, addDecision(0, null, null), null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That decision was already replaced by a newer one. Nothing was added.'
        0 * decisions.saveDecision(*_)
        0 * links.link(*_)          // "link nothing": checking a link first (requireLinkable) writes nothing
    }

    def "deleteDecision should delete a visible decision and throw not found when nothing was removed"() {

        when:
        service.deleteDecision(REX, 20)

        then:
        1 * decisions.deleteDecision(20) >> true

        when:
        service.deleteDecision(TONY, 21)

        then:
        1 * decisions.deleteDecision(21) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Decision not found.'
    }

    def "decisionFromLog should check the entry through the log service and then promote it for the caller"() {

        when:
        Decision d = service.decisionFromLog(REX, 200)

        then:
        1 * log.get(REX, 200) >> logEntry(id: 200, solutionId: 1)

        then:
        1 * decisions.decisionFromLog(200, 'rex') >> 20
        d.id() == 20
    }

    @Unroll
    def "decisionFromLog should promote nothing when #scenario"() {

        when:
        service.decisionFromLog(ZED, 200)

        then:
        1 * log.get(ZED, 200) >> { throw new SolutionNotFoundException(what) }
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.decisionFromLog(*_)

        where:
        scenario                               | what        || expectedMessage
        'the entry is in a hidden solution'    | 'Solution'  || 'Solution not found.'
        'the entry does not exist'             | 'Log entry' || 'Log entry not found.'
    }

    def "decisionFromLog should throw log entry not found when the repository answers no id and pass a refusal through"() {

        when:
        service.decisionFromLog(TONY, 200)

        then:
        1 * log.get(TONY, 200) >> logEntry(id: 200)
        1 * decisions.decisionFromLog(200, 'tony') >> 0
        SolutionNotFoundException missing = thrown()
        missing.message == 'Log entry not found.'

        when:
        service.decisionFromLog(TONY, 200)

        then:
        1 * log.get(TONY, 200) >> logEntry(id: 200)
        1 * decisions.decisionFromLog(200, 'tony') >> { throw new SolutionRuleException('That log entry is already a decision. Nothing was added.') }
        SolutionRuleException refused = thrown()
        refused.message == 'That log entry is already a decision. Nothing was added.'
    }

    def "openLinks should answer what the decision could close when the solution is visible"() {

        when:
        List<OpenLink> got = service.openLinks(REX, 20)

        then:
        1 * decisions.openLinks(20) >> [openQuestion(5), openItem(100)]
        got == [openQuestion(5), openItem(100)]
    }

    // ---- settle ----

    @Unroll
    def "settle should refuse and change nothing when the decision is #scenario"() {

        when:
        service.settle(TONY, decisionId, [5], [100])

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.openLinks(_)
        0 * decisions.setQuestionStatus(*_)
        0 * items._
        0 * log._

        where:
        scenario        | decisionId || expectedMessage
        'superseded'    | 22         || 'A superseded decision closes nothing. The decision that replaced it does. Nothing was changed.'
        'proposed'      | 23         || 'Accept the decision first. A proposal closes nothing. Nothing was changed.'
    }

    @Unroll
    def "#seq. settle should refuse with the record named and change nothing when #scenario"() {

        given:
        List<Integer> questionIds = intsOf(questionCsv)
        List<Integer> itemIds = intsOf(itemCsv)

        when:
        service.settle(TONY, 20, questionIds, itemIds)

        then:
        1 * decisions.openLinks(20) >> [openQuestion(5), openItem(100), openItem(101)]
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.setQuestionStatus(*_)
        0 * items._
        0 * log._

        where:
        seq | scenario                                   | questionCsv | itemCsv   || expectedMessage
        1   | 'a question is not linked'                 | '7'         | ''        || 'The question 7 is not an open question linked to this decision. Nothing was changed.'
        2   | 'an item is not linked'                    | '5'         | '102'     || 'The item 102 is not an open item linked to this decision. Nothing was changed.'
        3   | 'an item id is named as a question'        | '100'       | ''        || 'The question 100 is not an open question linked to this decision. Nothing was changed.'
        4   | 'a question id is named as an item'        | ''          | '5'       || 'The item 5 is not an open item linked to this decision. Nothing was changed.'
        5   | 'the good ones come before a bad one'      | '5'         | '100,999' || 'The item 999 is not an open item linked to this decision. Nothing was changed.'
        6   | 'a question id is negative'                | '-1'        | null      || 'The question -1 is not an open question linked to this decision. Nothing was changed.'
        7   | 'an item id is zero'                       | null        | '0'       || 'The item 0 is not an open item linked to this decision. Nothing was changed.'
        8   | 'the questions are bad and the items good' | '5,6'       | '100'     || 'The question 6 is not an open question linked to this decision. Nothing was changed.'
    }

    @Unroll
    def "settle should refuse and change nothing when #scenario"() {

        given:
        List<Integer> questionIds = intsOf(questionCsv)
        List<Integer> itemIds = intsOf(itemCsv)

        when:
        service.settle(TONY, 20, questionIds, itemIds)

        then:
        1 * decisions.openLinks(20) >> [openQuestion(5)]
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * decisions.setQuestionStatus(*_)
        0 * items._
        0 * log._

        where:
        scenario                         | questionCsv | itemCsv || expectedMessage
        'nothing is named'               | null        | null    || 'Name at least one open question or item linked to this decision.'
        'both lists are empty'           | ''          | ''      || 'Name at least one open question or item linked to this decision.'
        'only an empty item list'        | null        | ''      || 'Name at least one open question or item linked to this decision.'
    }

    def "settle should treat null ids as nothing named when the lists hold only nulls"() {

        when:
        service.settle(TONY, 20, [null], [null])

        then:
        1 * decisions.openLinks(20) >> [openQuestion(5), openItem(100)]
        IllegalArgumentException ex = thrown()
        ex.message == 'Name at least one open question or item linked to this decision.'
        0 * decisions.setQuestionStatus(*_)
        0 * items._
    }

    @Unroll
    def "settle should answer the questions by the decision, mark the items done and write one result entry when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LogEntry entry = logEntry(id: 410, sequenceNumber: 42)
        String text = '**Closed by the decision:** D20\n- Question answered: Q5\n- Question answered: Q7\n- Item done: Item 100'

        when:
        Settled s = service.settle(caller, 20, [5, 5, 7], [100])

        then:
        1 * decisions.openLinks(20) >> [openQuestion(5), openQuestion(7), openItem(100), openItem(101)]

        then:
        1 * decisions.setQuestionStatus(5, 'answered', 20, expectedCode) >> true
        1 * decisions.setQuestionStatus(7, 'answered', 20, expectedCode) >> true

        then:
        1 * items.setStatus(caller, 100, 'done')

        then:
        1 * log.save(caller, new LogDraft(0, 1, null, null, 'result', text, null)) >> entry

        then:
        1 * decisions.openLinks(20) >> [openItem(101)]
        0 * items.setStatus(_, 101, _)
        s.decision().id() == 20
        s.closed() == [openQuestion(5), openQuestion(7), openItem(100)]
        s.stillOpen() == [openItem(101)]
        s.logId() == 410
        s.logSequenceNumber() == 42

        where:
        scenario                  | callerName || expectedCode
        'a person settles it'     | 'TONY'     || 'tony'
        'the house settles it'    | 'HOUSE'    || 'felix'
        'a client settles it'     | 'REX'      || 'rex'
    }

    def "settle should close only the items and write their lines when no questions are named"() {

        when:
        Settled s = service.settle(TONY, 20, null, [100, 101])

        then:
        1 * decisions.openLinks(20) >> [openQuestion(5), openItem(100), openItem(101)]
        0 * decisions.setQuestionStatus(*_)
        1 * items.setStatus(TONY, 100, 'done')
        1 * items.setStatus(TONY, 101, 'done')
        1 * log.save(TONY, { LogDraft d -> d.text() == '**Closed by the decision:** D20\n- Item done: Item 100\n- Item done: Item 101' && d.typeCode() == 'result' }) >>
            logEntry(id: 411, sequenceNumber: 43)
        1 * decisions.openLinks(20) >> [openQuestion(5)]
        s.closed()*.id() == [100, 101]
        s.stillOpen() == [openQuestion(5)]
    }

    def "settle should leave a question out of closed when the repository changed nothing"() {

        when:
        Settled s = service.settle(TONY, 20, [5], [100])

        then:
        2 * decisions.openLinks(20) >> [openQuestion(5), openItem(100)]
        1 * decisions.setQuestionStatus(5, 'answered', 20, 'tony') >> false
        1 * items.setStatus(TONY, 100, 'done')
        1 * log.save(TONY, { LogDraft d -> d.text() == '**Closed by the decision:** D20\n- Item done: Item 100' }) >> logEntry(id: 412)
        s.closed() == [openItem(100)]
    }

    def "settle should answer no journal entry when the log service answers none"() {

        when:
        Settled s = service.settle(TONY, 20, [5], null)

        then:
        2 * decisions.openLinks(20) >> [openQuestion(5)]
        1 * decisions.setQuestionStatus(5, 'answered', 20, 'tony') >> true
        1 * log.save(*_) >> null
        s.logId() == null
        s.logSequenceNumber() == null
        s.closed() == [openQuestion(5)]
    }

    @Unroll
    def "settledText should answer '#expected' when #scenario"() {

        given:
        Decision d = Decision.of([SolutionDecisionID: 20, DecisionTitle: title])

        expect:
        SolutionDecisionService.settledText(d, closedOf(closedCell)) == expected

        where:
        scenario                          | title           | closedCell                   || expected
        'nothing was closed'              | 'Read as UTF-8' | ''                           || '**Closed by the decision:** Read as UTF-8'
        'one question was answered'       | 'Read as UTF-8' | 'question:Which charset?'    || '**Closed by the decision:** Read as UTF-8\n- Question answered: Which charset?'
        'one item was done'               | 'Read as UTF-8' | 'item:Decide charset'        || '**Closed by the decision:** Read as UTF-8\n- Item done: Decide charset'
        'an item and a question, in order' | 'T'            | 'item:A;question:B'          || '**Closed by the decision:** T\n- Item done: A\n- Question answered: B'
        'any other kind reads as an item' | 'T'             | 'note:Runbook'               || '**Closed by the decision:** T\n- Item done: Runbook'
    }
}
