package com.echo

import java.time.LocalDateTime

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionDecisionModels.Decision
import com.echo.SolutionDecisionModels.DecisionDraft
import com.echo.SolutionDecisionModels.OpenLink
import com.echo.SolutionDecisionModels.Question
import com.echo.SolutionDecisionModels.QuestionDraft
import com.echo.SolutionDecisionModels.Saved
import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionModels.LogEntry
import static com.echo.SolutionFixtures.logEntry
import static com.echo.SolutionFixtures.lookups

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695: a solution's questions and decisions over HTTP -- every endpoint, who may call it, the query
 * parameters and body fields that become the service call (decidedDate read through
 * SolutionControllerSupport.date), and how each refusal maps to a status (400 malformed, 404 not found or
 * not shared, 409 a rule of the procs). The real SolutionDecisionService, SolutionLogService, access fence
 * and code check run; storage, the item service and the link service are mocked.
 */
@WebMvcTest(SolutionDecisionController)
class SolutionDecisionControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionDecisionService service = Mock()

    SolutionDecisionRepository decisions = Mock()
    SolutionLogRepository logRepo = Mock()
    SolutionItemService items = Mock()
    SolutionLinkService links = Mock()
    SolutionDecisionService real

    Map<Integer, Question> questionRows = [5: question(5, 1), 6: question(6, 2)]
    Map<Integer, Decision> decisionRows = [20: decision(20, 1, 'accepted'), 21: decision(21, 2, 'accepted'),
                                           22: decision(22, 1, 'superseded'), 23: decision(23, 1, 'proposed'),
                                           30: decision(30, 1, 'accepted')]
    Map<Integer, LogEntry> logRows = [200: logEntry(id: 200, solutionId: 1), 201: logEntry(id: 201, solutionId: 2),
                                      410: logEntry(id: 410, solutionId: 1, sequenceNumber: 42, typeCode: 'result')]

    def setup() {
        SolutionCodeCheck codes = new SolutionCodeCheck(solutionRepo)
        real = new SolutionDecisionService(decisions, access, codes, items, new SolutionLogService(logRepo, access, codes), links)
        service.questions(*_) >> { a -> real.questions(*a) }
        service.question(*_) >> { a -> real.question(*a) }
        service.saveQuestion(*_) >> { a -> real.saveQuestion(*a) }
        service.setQuestionStatus(*_) >> { a -> real.setQuestionStatus(*a) }
        service.deleteQuestion(*_) >> { a -> real.deleteQuestion(*a) }
        service.questionFromLog(*_) >> { a -> real.questionFromLog(*a) }
        service.decisions(*_) >> { a -> real.decisions(*a) }
        service.decision(*_) >> { a -> real.decision(*a) }
        service.saveDecision(*_) >> { a -> real.saveDecision(*a) }
        service.supersede(*_) >> { a -> real.supersede(*a) }
        service.deleteDecision(*_) >> { a -> real.deleteDecision(*a) }
        service.decisionFromLog(*_) >> { a -> real.decisionFromLog(*a) }
        service.openLinks(*_) >> { a -> real.openLinks(*a) }
        service.settle(*_) >> { a -> real.settle(*a) }

        solutionRepo.lookups() >> lookups()
        decisions.findQuestion(_) >> { int id -> questionRows[id] }
        decisions.findDecision(_) >> { int id -> decisionRows[id] }
        decisions.questions(*_) >> { int s, String st, String q, int off, int size -> questionRows.values().findAll { it.solutionId() == s } as List }
        decisions.questionCounts(*_) >> [total: 1, all: 2]
        decisions.saveQuestion(*_) >> { QuestionDraft d, String by -> d.id() > 0 ? d.id() : 5 }
        decisions.setQuestionStatus(*_) >> true
        decisions.deleteQuestion(_) >> true
        decisions.questionFromLog(*_) >> 5
        decisions.decisions(*_) >> { int s, String st, String q, int off, int size -> decisionRows.values().findAll { it.solutionId() == s } as List }
        decisions.decisionCounts(*_) >> [total: 1, all: 2]
        decisions.saveDecision(*_) >> { DecisionDraft d, String by -> new Saved(d.id() > 0 ? d.id() : 30, 410) }
        decisions.deleteDecision(_) >> true
        decisions.decisionFromLog(*_) >> 20
        decisions.openLinks(_) >> [new OpenLink('question', 5, 'Q5', 'Question')]
        logRepo.find(_) >> { int id -> logRows[id] }
        logRepo.save(*_) >> 410
    }

    private static Question question(int id, int solutionId) {
        Question.of([SolutionQuestionID: id, SolutionID: solutionId, QuestionText: 'Q' + id, StatusCode: 'open'])
    }

    private static Decision decision(int id, int solutionId, String status) {
        Decision.of([SolutionDecisionID: id, SolutionID: solutionId, DecisionTitle: 'D' + id, StatusCode: status])
    }

    @Unroll
    def "#seq. #scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * decisions._
        0 * logRepo._
        0 * items._
        0 * links._
        r.andExpect(status().is(expectedStatus))

        where:
        seq | scenario                 | method   | url                                        | body                                   || expectedStatus
        1   | 'questions'              | 'GET'    | '/api/solutions/1/questions'               | null                                   || 401
        2   | 'ask'                    | 'POST'   | '/api/solutions/1/questions'               | '{"text":"Which charset?"}'            || 401
        3   | 'question'               | 'GET'    | '/api/solutions/questions/5'               | null                                   || 401
        4   | 'locate a question'      | 'GET'    | '/api/solutions/questions/5/locate'        | null                                   || 401
        5   | 'update a question'      | 'PUT'    | '/api/solutions/questions/5'               | '{"detail":"New"}'                     || 401
        6   | 'question status'        | 'POST'   | '/api/solutions/questions/5/status'        | '{"statusCode":"dropped"}'             || 401
        7   | 'delete a question'      | 'DELETE' | '/api/solutions/questions/5'               | null                                   || 401
        8   | 'question from log'      | 'POST'   | '/api/solutions/log/200/question'          | null                                   || 401
        9   | 'decisions'              | 'GET'    | '/api/solutions/1/decisions'               | null                                   || 401
        10  | 'decide'                 | 'POST'   | '/api/solutions/1/decisions'               | '{"title":"Use UTF-8"}'                || 401
        11  | 'decision'               | 'GET'    | '/api/solutions/decisions/20'              | null                                   || 401
        12  | 'locate a decision'      | 'GET'    | '/api/solutions/decisions/20/locate'       | null                                   || 401
        13  | 'update a decision'      | 'PUT'    | '/api/solutions/decisions/20'              | '{"outcome":"New"}'                    || 401
        14  | 'supersede'              | 'POST'   | '/api/solutions/decisions/20/supersede'    | '{"title":"Read everything as UTF-8"}' || 401
        15  | 'delete a decision'      | 'DELETE' | '/api/solutions/decisions/20'              | null                                   || 401
        16  | 'decision from log'      | 'POST'   | '/api/solutions/log/200/decision'          | null                                   || 401
        17  | 'open links'             | 'GET'    | '/api/solutions/decisions/20/open-links'   | null                                   || 401
        18  | 'settle'                 | 'POST'   | '/api/solutions/decisions/20/settle'       | '{"questionIds":[5]}'                  || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        seq | scenario                 | method   | url                                        | body                                   || path                | expectedValue
        1   | 'questions'              | 'GET'    | '/api/solutions/1/questions'               | null                                   || '$.total'           | 1
        2   | 'ask'                    | 'POST'   | '/api/solutions/1/questions'               | '{"text":"Which charset?"}'            || '$.id'              | 5
        3   | 'question'               | 'GET'    | '/api/solutions/questions/5'               | null                                   || '$.solutionId'      | 1
        4   | 'locate a question'      | 'GET'    | '/api/solutions/questions/5/locate'        | null                                   || '$.questionId'      | 5
        5   | 'update a question'      | 'PUT'    | '/api/solutions/questions/5'               | '{"detail":"New"}'                     || '$.id'              | 5
        6   | 'question status'        | 'POST'   | '/api/solutions/questions/5/status'        | '{"statusCode":"dropped"}'             || '$.id'              | 5
        7   | 'delete a question'      | 'DELETE' | '/api/solutions/questions/5'               | null                                   || '$.deletedId'       | 5
        8   | 'question from log'      | 'POST'   | '/api/solutions/log/200/question'          | null                                   || '$.id'              | 5
        9   | 'decisions'              | 'GET'    | '/api/solutions/1/decisions'               | null                                   || '$.total'           | 1
        10  | 'decide'                 | 'POST'   | '/api/solutions/1/decisions'               | '{"title":"Use UTF-8"}'                || '$.id'              | 30
        11  | 'decision'               | 'GET'    | '/api/solutions/decisions/20'              | null                                   || '$.title'           | 'D20'
        12  | 'locate a decision'      | 'GET'    | '/api/solutions/decisions/20/locate'       | null                                   || '$.decisionId'      | 20
        13  | 'update a decision'      | 'PUT'    | '/api/solutions/decisions/20'              | '{"outcome":"New"}'                    || '$.id'              | 20
        14  | 'supersede'              | 'POST'   | '/api/solutions/decisions/20/supersede'    | '{"title":"Read everything as UTF-8"}' || '$.id'              | 30
        15  | 'delete a decision'      | 'DELETE' | '/api/solutions/decisions/20'              | null                                   || '$.deletedId'       | 20
        16  | 'decision from log'      | 'POST'   | '/api/solutions/log/200/decision'          | null                                   || '$.id'              | 20
        17  | 'open links'             | 'GET'    | '/api/solutions/decisions/20/open-links'   | null                                   || '$[0].kind'         | 'question'
        18  | 'settle'                 | 'POST'   | '/api/solutions/decisions/20/settle'       | '{"questionIds":[5]}'                  || '$.closed[0].id'    | 5
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * decisions.questions(*_)
        0 * decisions.saveQuestion(*_)
        0 * decisions.setQuestionStatus(*_)
        0 * decisions.deleteQuestion(_)
        0 * decisions.questionFromLog(*_)
        0 * decisions.decisions(*_)
        0 * decisions.saveDecision(*_)
        0 * decisions.deleteDecision(_)
        0 * decisions.decisionFromLog(*_)
        0 * decisions.openLinks(_)
        0 * logRepo.save(*_)
        0 * items._
        0 * links._

        where:
        seq | scenario                 | who   | method   | url                                        | body                         || expectedStatus
        1   | 'questions'              | 'zed' | 'GET'    | '/api/solutions/1/questions'               | null                         || 404
        2   | 'ask'                    | 'zed' | 'POST'   | '/api/solutions/1/questions'               | '{"text":"Why?"}'            || 404
        3   | 'question'               | 'zed' | 'GET'    | '/api/solutions/questions/5'               | null                         || 404
        4   | 'update a question'      | 'zed' | 'PUT'    | '/api/solutions/questions/5'               | '{"detail":"New"}'           || 404
        5   | 'question status'        | 'zed' | 'POST'   | '/api/solutions/questions/5/status'        | '{"statusCode":"dropped"}'   || 404
        6   | 'delete a question'      | 'zed' | 'DELETE' | '/api/solutions/questions/5'               | null                         || 404
        7   | 'question from log'      | 'zed' | 'POST'   | '/api/solutions/log/200/question'          | null                         || 404
        8   | 'decide'                 | 'zed' | 'POST'   | '/api/solutions/1/decisions'               | '{"title":"Use UTF-8"}'      || 404
        9   | 'supersede'              | 'zed' | 'POST'   | '/api/solutions/decisions/20/supersede'    | '{"title":"Again"}'          || 404
        10  | 'settle'                 | 'zed' | 'POST'   | '/api/solutions/decisions/20/settle'       | '{"questionIds":[5]}'        || 404
        11  | 'questions'              | 'rex' | 'GET'    | '/api/solutions/2/questions'               | null                         || 404
        12  | 'ask'                    | 'rex' | 'POST'   | '/api/solutions/2/questions'               | '{"text":"Why?"}'            || 404
        13  | 'locate a question'      | 'rex' | 'GET'    | '/api/solutions/questions/6/locate'        | null                         || 404
        14  | 'update a question'      | 'rex' | 'PUT'    | '/api/solutions/questions/6'               | '{"detail":"New"}'           || 404
        15  | 'delete a question'      | 'rex' | 'DELETE' | '/api/solutions/questions/6'               | null                         || 404
        16  | 'question from log'      | 'rex' | 'POST'   | '/api/solutions/log/201/question'          | null                         || 404
        17  | 'decisions'              | 'rex' | 'GET'    | '/api/solutions/2/decisions'               | null                         || 404
        18  | 'decide'                 | 'rex' | 'POST'   | '/api/solutions/2/decisions'               | '{"title":"Use UTF-8"}'      || 404
        19  | 'decision'               | 'rex' | 'GET'    | '/api/solutions/decisions/21'              | null                         || 404
        20  | 'locate a decision'      | 'rex' | 'GET'    | '/api/solutions/decisions/21/locate'       | null                         || 404
        21  | 'update a decision'      | 'rex' | 'PUT'    | '/api/solutions/decisions/21'              | '{"outcome":"New"}'          || 404
        22  | 'supersede'              | 'rex' | 'POST'   | '/api/solutions/decisions/21/supersede'    | '{"title":"Again"}'          || 404
        23  | 'delete a decision'      | 'rex' | 'DELETE' | '/api/solutions/decisions/21'              | null                         || 404
        24  | 'decision from log'      | 'rex' | 'POST'   | '/api/solutions/log/201/decision'          | null                         || 404
        25  | 'open links'             | 'rex' | 'GET'    | '/api/solutions/decisions/21/open-links'   | null                         || 404
        26  | 'settle'                 | 'rex' | 'POST'   | '/api/solutions/decisions/21/settle'       | '{"questionIds":[5]}'        || 404
    }

    @Unroll
    def "#seq. #scenario should answer 404 '#expectedMessage' when the record is missing"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * decisions.saveQuestion(*_)
        0 * decisions.saveDecision(*_)
        0 * decisions.deleteQuestion(_)
        0 * decisions.deleteDecision(_)

        where:
        seq | scenario               | method   | url                                         | body                      || expectedMessage
        1   | 'questions'            | 'GET'    | '/api/solutions/9/questions'                | null                      || 'Solution not found.'
        2   | 'decide'               | 'POST'   | '/api/solutions/9/decisions'                | '{"title":"Use UTF-8"}'   || 'Solution not found.'
        3   | 'question'             | 'GET'    | '/api/solutions/questions/99'               | null                      || 'Question not found.'
        4   | 'locate a question'    | 'GET'    | '/api/solutions/questions/0/locate'         | null                      || 'Question not found.'
        5   | 'update a question'    | 'PUT'    | '/api/solutions/questions/99'               | '{"text":"x"}'            || 'Question not found.'
        6   | 'delete a question'    | 'DELETE' | '/api/solutions/questions/99'               | null                      || 'Question not found.'
        7   | 'question from log'    | 'POST'   | '/api/solutions/log/99/question'            | null                      || 'Log entry not found.'
        8   | 'decision'             | 'GET'    | '/api/solutions/decisions/99'               | null                      || 'Decision not found.'
        9   | 'update a decision'    | 'PUT'    | '/api/solutions/decisions/99'               | '{"title":"x"}'           || 'Decision not found.'
        10  | 'supersede'            | 'POST'   | '/api/solutions/decisions/99/supersede'     | '{"title":"x"}'           || 'Decision not found.'
        11  | 'delete a decision'    | 'DELETE' | '/api/solutions/decisions/99'               | null                      || 'Decision not found.'
        12  | 'decision from log'    | 'POST'   | '/api/solutions/log/0/decision'             | null                      || 'Log entry not found.'
        13  | 'open links'           | 'GET'    | '/api/solutions/decisions/99/open-links'    | null                      || 'Decision not found.'
        14  | 'settle'               | 'POST'   | '/api/solutions/decisions/99/settle'        | '{"questionIds":[5]}'     || 'Decision not found.'
    }

    def "every endpoint should match nothing when an id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/questions/abc'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/decisions/x1/open-links'))).andExpect(status().isNotFound())
        mvc.perform(asUser(post('/api/solutions/log/abc/decision'))).andExpect(status().isNotFound())

        then:
        0 * decisions._
        0 * logRepo._
    }

    // ---- questions ----

    def "questions should pass the status, search and a normalized page when every query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/questions').param('status', ' Open ').param('search', ' charset ')
            .param('offset', '-5').param('pageSize', '9999')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.offset').value(0))
            .andExpect(jsonPath('$.pageSize').value(500))

        then:
        1 * decisions.questionCounts(1, 'open', 'charset') >> [total: 0]
        1 * decisions.questions(1, 'open', 'charset', 0, 500) >> []
    }

    def "questions should ask for every question in pages of 50 when no query parameter is sent"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/questions'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.questions[0].text').value('Q5'))
            .andExpect(jsonPath('$.counts.all').value(2))

        then:
        1 * decisions.questions(1, null, null, 0, 50) >> [questionRows[5]]
    }

    @Unroll
    def "questions should answer 400 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get(url)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * decisions.questions(*_)

        where:
        scenario                         | url                                               || expectedStatus
        'the status is current'          | '/api/solutions/1/questions?status=current'       || 400
        'the status is unknown'          | '/api/solutions/1/questions?status=closed'        || 400
        'the offset is a word'           | '/api/solutions/1/questions?offset=x'             || 400
        'the page size is a fraction'    | '/api/solutions/1/questions?pageSize=1.5'         || 400
    }

    def "questions should name the three statuses when the status is unknown"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/questions').param('status', 'closed'))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value("A question's status is one of open, answered, dropped."))
    }

    def "ask should build an add draft with the text trimmed and link the new question when an agent asks"() {

        when:
        mvc.perform(asAgent(request('POST', '/api/solutions/1/questions',
            '{"text":"  Which charset? ","detail":" d ","ownerCode":"felix","links":[{"kind":"item","id":100}]}')))
            .andExpect(status().isOk())

        then:
        1 * decisions.saveQuestion(new QuestionDraft(0, 1, 'Which charset?', ' d ', 'felix'), 'rex') >> 5

        then:
        1 * links.link(_, 1, 'question', 5, 'item', 100)
    }

    @Unroll
    def "ask should attribute the question to #expectedCode when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/1/questions', '{"text":"Why?"}'))).andExpect(status().isOk())

        then:
        1 * decisions.saveQuestion(new QuestionDraft(0, 1, 'Why?', null, null), expectedCode) >> 5
        0 * links._

        where:
        scenario                       | who       || expectedCode
        'a person asks'                | 'user'    || 'tony'
        'the house asks'               | 'service' || 'felix'
        'the house asks as Stewart'    | 'stewart' || 'stewart'
        'an agent asks'                | 'rex'     || 'rex'
    }

    @Unroll
    def "ask should answer 400 '#expectedMessage' and save nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/questions', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * decisions.saveQuestion(*_)
        0 * links._

        where:
        scenario                      | body                                                  || expectedMessage
        'the text is missing'         | '{"detail":"d"}'                                      || 'The question is required.'
        'the text is blank'           | '{"text":"   "}'                                      || 'The question is required.'
        'a link has no id'            | '{"text":"Why?","links":[{"kind":"item"}]}'           || 'Each link needs a kind and an id.'
        'a link has an unknown kind'  | '{"text":"Why?","links":[{"kind":"story","id":1}]}'   || 'kind must be one of item, note, log, file, term, topic, question, decision.'
    }

    def "ask should answer 409 verbatim when the repository refuses a repeat open question"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/questions', '{"text":"Q5"}')))

        then:
        1 * decisions.saveQuestion(*_) >> { throw new SolutionRuleException('That question is already open in this solution. Nothing was saved.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('That question is already open in this solution. Nothing was saved.'))
    }

    def "ask should answer 409 with the member codes when the owner is unknown"() {

        expect:
        mvc.perform(asUser(request('POST', '/api/solutions/1/questions', '{"text":"Why?","ownerCode":"bob"}')))
            .andExpect(status().isConflict())
            .andExpect(jsonPath('$.message').value("There is no member code 'bob'. The member codes are: tony, felix. Nothing was saved. GET /api/solutions/lookups lists every code."))
    }

    @Unroll
    def "updateQuestion should build a merge draft with solution zero and link nothing when #scenario"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/questions/5', body))).andExpect(status().isOk())

        then:
        1 * decisions.saveQuestion(new QuestionDraft(5, 0, expectedText, expectedDetail, expectedOwner), 'tony') >> 5
        0 * links._

        where:
        scenario                          | body                                                   || expectedText | expectedDetail | expectedOwner
        'only the detail is cleared'      | '{"detail":""}'                                        || null         | ''             | null
        'only the owner is cleared'       | '{"ownerCode":""}'                                     || null         | null           | ''
        'the text is sent padded'         | '{"text":" Why? "}'                                    || ' Why? '     | null           | null
        'nothing is sent'                 | '{}'                                                   || null         | null           | null
        'links are sent with an edit'     | '{"detail":"d","links":[{"kind":"item","id":100}]}'    || null         | 'd'            | null
    }

    def "locateQuestion should answer only the question and its solution when a person asks"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/questions/6/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.questionId').value(6))
            .andExpect(jsonPath('$.solutionId').value(2))
            .andExpect(jsonPath('$.text').doesNotExist())
    }

    @Unroll
    def "questionStatus should set #expected by hand when the body asks to #scenario"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/questions/5/status', '{"statusCode":"' + statusCode + '"}')))
            .andExpect(status().isOk())

        then:
        1 * decisions.setQuestionStatus(5, expected, null, 'tony') >> true

        where:
        scenario      | statusCode    || expected
        'reopen'      | 'open'        || 'open'
        'answer'      | ' Answered '  || 'answered'
        'drop'        | 'DROPPED'     || 'dropped'
    }

    @Unroll
    def "questionStatus should answer 400 '#expectedMessage' and change nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/questions/5/status', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * decisions.setQuestionStatus(*_)

        where:
        scenario                   | body                         || expectedMessage
        'the status is missing'    | '{}'                         || 'statusCode is required.'
        'the status is blank'      | '{"statusCode":"  "}'        || 'statusCode is required.'
        'the status is current'    | '{"statusCode":"current"}'   || "A question's status is one of open, answered, dropped."
    }

    def "questionFromLog should promote the entry for the caller when the entry's solution is shared"() {

        when:
        mvc.perform(asAgent(post('/api/solutions/log/200/question'))).andExpect(status().isOk())

        then:
        1 * decisions.questionFromLog(200, 'rex') >> 5
    }

    def "questionFromLog should answer 409 verbatim when the entry is already a question"() {

        when:
        ResultActions r = mvc.perform(asUser(post('/api/solutions/log/200/question')))

        then:
        1 * decisions.questionFromLog(200, 'tony') >> { throw new SolutionRuleException('That log entry is already a question. Nothing was added.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('That log entry is already a question. Nothing was added.'))
    }

    // ---- decisions ----

    def "decisions should pass the current filter, the search and a normalized page when every query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/decisions').param('status', 'current').param('search', ' remit ')
            .param('offset', '10').param('pageSize', '20')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.offset').value(10))
            .andExpect(jsonPath('$.pageSize').value(20))

        then:
        1 * decisions.decisionCounts(1, 'current', 'remit') >> [total: 0]
        1 * decisions.decisions(1, 'current', 'remit', 10, 20) >> []
    }

    @Unroll
    def "decisions should answer 400 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get(url)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * decisions.decisions(*_)

        where:
        scenario                      | url                                              || expectedStatus
        'the status is unknown'       | '/api/solutions/1/decisions?status=decided'      || 400
        'the offset is a fraction'    | '/api/solutions/1/decisions?offset=2.5'          || 400
        'the page size is a word'     | '/api/solutions/1/decisions?pageSize=lots'       || 400
    }

    def "decide should build an add draft from every field of the body and link the new decision"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/decisions',
            '{"title":" Use UTF-8 ","context":"c","options":"o","outcome":"out","statusCode":"proposed","decidedDate":"2026-10-23",' +
            '"links":[{"kind":"question","id":5},{"kind":"item","id":100}]}')))
            .andExpect(status().isOk())

        then:
        1 * decisions.saveDecision(new DecisionDraft(0, 1, 'Use UTF-8', 'c', 'o', 'out', 'proposed', getLocalDateTime('10/23/2026 00:00'), null), 'tony') >>
            new Saved(30, 410)

        then:
        1 * links.link(_, 1, 'decision', 30, 'question', 5)
        1 * links.link(_, 1, 'decision', 30, 'item', 100)
    }

    @Unroll
    def "decide should read decidedDate #expectedText when the body sends #scenario"() {

        given:
        LocalDateTime expected = getLocalDateTime(expectedText)
        String body = dateText == null ? '{"title":"Use UTF-8"}' : '{"title":"Use UTF-8","decidedDate":"' + dateText + '"}'

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/decisions', body))).andExpect(status().isOk())

        then:
        1 * decisions.saveDecision({ DecisionDraft d -> d.decidedDate() == expected }, 'tony') >> new Saved(30, 410)

        where:
        scenario                 | dateText            || expectedText
        'a date'                 | '2026-10-23'        || '10/23/2026 00:00'
        'a date and time'        | '2026-10-23T14:30'  || '10/23/2026 14:30'
        'a padded date'          | ' 2026-10-23 '      || '10/23/2026 00:00'
        'an empty date'          | ''                  || null
        'a blank date'           | '   '               || null
        'no date'                | null                || null
    }

    @Unroll
    def "#seq. #scenario should answer 400 '#expectedMessage' and save nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * decisions.saveDecision(*_)
        0 * links._

        where:
        seq | scenario                                | method | url                                       | body                                                        || expectedMessage
        1   | 'decide without a title'                | 'POST' | '/api/solutions/1/decisions'              | '{"outcome":"o"}'                                           || 'A decision title is required.'
        2   | 'decide with a blank title'             | 'POST' | '/api/solutions/1/decisions'              | '{"title":"  "}'                                            || 'A decision title is required.'
        3   | 'decide with a date in words'           | 'POST' | '/api/solutions/1/decisions'              | '{"title":"T","decidedDate":"yesterday"}'                   || 'decidedDate must look like 2026-09-17 or 2026-09-17T14:30.'
        4   | 'decide with a US date'                 | 'POST' | '/api/solutions/1/decisions'              | '{"title":"T","decidedDate":"10/23/2026"}'                  || 'decidedDate must look like 2026-09-17 or 2026-09-17T14:30.'
        5   | 'decide as superseded'                  | 'POST' | '/api/solutions/1/decisions'              | '{"title":"T","statusCode":"superseded"}'                   || 'A decision is superseded by recording a newer one (supersede), never by setting its status.'
        6   | 'decide with a bad link'                | 'POST' | '/api/solutions/1/decisions'              | '{"title":"T","links":[{"id":5}]}'                          || 'Each link needs a kind and an id.'
        7   | 'update to superseded'                  | 'PUT'  | '/api/solutions/decisions/20'             | '{"statusCode":"superseded"}'                               || 'A decision is superseded by recording a newer one (supersede), never by setting its status.'
        8   | 'update to current'                     | 'PUT'  | '/api/solutions/decisions/20'             | '{"statusCode":"current"}'                                  || "A decision's status is one of proposed, accepted, superseded."
        9   | 'update with a bad date'                | 'PUT'  | '/api/solutions/decisions/20'             | '{"decidedDate":"2026-13-40"}'                              || 'decidedDate must look like 2026-09-17 or 2026-09-17T14:30.'
        10  | 'supersede without a title'             | 'POST' | '/api/solutions/decisions/20/supersede'   | '{"outcome":"o"}'                                           || "The new decision's title is required."
        11  | 'supersede with a bad date'             | 'POST' | '/api/solutions/decisions/20/supersede'   | '{"title":"T","decidedDate":"soon"}'                        || 'decidedDate must look like 2026-09-17 or 2026-09-17T14:30.'
    }

    @Unroll
    def "updateDecision should build a merge draft with solution zero and link nothing when #scenario"() {

        given:
        LocalDateTime expectedDate = getLocalDateTime(expectedDateText)

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/decisions/20', body))).andExpect(status().isOk())

        then:
        1 * decisions.saveDecision(new DecisionDraft(20, 0, expectedTitle, expectedContext, null, null, expectedStatus, expectedDate, null), 'tony') >>
            new Saved(20, 410)
        0 * links._

        where:
        scenario                       | body                                                        || expectedTitle | expectedContext | expectedStatus | expectedDateText
        'it is accepted'               | '{"statusCode":"accepted"}'                                 || null          | null            | 'accepted'     | null
        'the context is cleared'       | '{"context":""}'                                            || null          | ''              | null           | null
        'the title and date change'    | '{"title":"New","decidedDate":"2026-11-01T09:00"}'          || 'New'         | null            | null           | '11/01/2026 09:00'
        'links are sent with an edit'  | '{"links":[{"kind":"item","id":100}]}'                      || null          | null            | null           | null
    }

    def "supersede should build an accepted add naming the old decision and link the new one"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/decisions/20/supersede',
            '{"title":" Read everything as UTF-8 ","outcome":"o","statusCode":"proposed","decidedDate":"2026-11-04","links":[{"kind":"item","id":100}]}')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(30))

        then:
        1 * decisions.saveDecision(new DecisionDraft(0, 1, 'Read everything as UTF-8', null, null, 'o', 'accepted', getLocalDateTime('11/04/2026 00:00'), 20), 'tony') >>
            new Saved(30, 411)

        then:
        1 * links.link(_, 1, 'decision', 30, 'item', 100)
    }

    def "supersede should answer 409 and save nothing when the decision was already replaced"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/decisions/22/supersede', '{"title":"Again"}')))

        then:
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('That decision was already replaced by a newer one. Nothing was added.'))
        0 * decisions.saveDecision(*_)
    }

    def "locateDecision should answer only the decision and its solution when a person asks"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/decisions/21/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.decisionId').value(21))
            .andExpect(jsonPath('$.solutionId').value(2))
            .andExpect(jsonPath('$.title').doesNotExist())
    }

    def "decisionFromLog should promote the entry for the caller when the entry's solution is visible"() {

        when:
        mvc.perform(asUser(post('/api/solutions/log/201/decision'))).andExpect(status().isOk())

        then:
        1 * decisions.decisionFromLog(201, 'tony') >> 21
    }

    // ---- settle ----

    def "settle should close the named question and item and answer what it closed and the journal entry"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/decisions/20/settle', '{"questionIds":[5],"itemIds":[100]}')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.closed.length()').value(2))
            .andExpect(jsonPath('$.closed[1].kind').value('item'))
            .andExpect(jsonPath('$.stillOpen.length()').value(0))
            .andExpect(jsonPath('$.logId').value(410))
            .andExpect(jsonPath('$.logSequenceNumber').value(42))
            .andExpect(jsonPath('$.decision.id').value(20))

        then:
        1 * decisions.openLinks(20) >> [new OpenLink('question', 5, 'Q5', 'Question'), new OpenLink('item', 100, 'Upgrade', 'Deploy | Not started')]

        then:
        1 * decisions.setQuestionStatus(5, 'answered', 20, 'tony') >> true

        then:
        1 * items.setStatus(_, 100, 'done')

        then:
        1 * logRepo.save(new LogDraft(0, 1, null, null, 'result',
            '**Closed by the decision:** D20\n- Question answered: Q5\n- Item done: Upgrade', null), 'tony') >> 410

        then:
        1 * decisions.openLinks(20) >> []
    }

    @Unroll
    def "settle should answer 400 '#expectedMessage' and change nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/decisions/20/settle', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * decisions.setQuestionStatus(*_)
        0 * items._
        0 * logRepo.save(*_)

        where:
        scenario                        | body                                    || expectedMessage
        'nothing is named'              | '{}'                                    || 'Name at least one open question or item linked to this decision.'
        'the lists are empty'           | '{"questionIds":[],"itemIds":[]}'       || 'Name at least one open question or item linked to this decision.'
        'an item is not linked'         | '{"questionIds":[5],"itemIds":[999]}'   || 'The item 999 is not an open item linked to this decision. Nothing was changed.'
        'a question is not linked'      | '{"questionIds":[6]}'                   || 'The question 6 is not an open question linked to this decision. Nothing was changed.'
    }

    @Unroll
    def "settle should answer 409 and change nothing when the decision is #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', url, '{"questionIds":[5]}')))

        then:
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * decisions.openLinks(_)
        0 * decisions.setQuestionStatus(*_)
        0 * logRepo.save(*_)

        where:
        scenario       | url                                        || expectedMessage
        'superseded'   | '/api/solutions/decisions/22/settle'       || 'A superseded decision closes nothing. The decision that replaced it does. Nothing was changed.'
        'proposed'     | '/api/solutions/decisions/23/settle'       || 'Accept the decision first. A proposal closes nothing. Nothing was changed.'
    }

    def "openLinks should answer the linked records still open when an agent asks"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/decisions/20/open-links'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[1].id').value(100))
            .andExpect(jsonPath('$[1].sub').value('Deploy | Not started'))

        then:
        1 * decisions.openLinks(20) >> [new OpenLink('question', 5, 'Q5', 'Question'), new OpenLink('item', 100, 'Upgrade', 'Deploy | Not started')]
    }

    // ---- malformed bodies ----

    @Unroll
    def "#seq. #scenario should answer 400 and write nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request(method, url, null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * decisions.saveQuestion(*_)
        0 * decisions.setQuestionStatus(*_)
        0 * decisions.saveDecision(*_)
        0 * logRepo.save(*_)

        where:
        seq | scenario             | method | url                                       | body                         || expectedStatus
        1   | 'ask'                | 'POST' | '/api/solutions/1/questions'              | ''                           || 400
        2   | 'ask'                | 'POST' | '/api/solutions/1/questions'              | 'null'                       || 400
        3   | 'ask'                | 'POST' | '/api/solutions/1/questions'              | '{"text":"a","links":"x"}'   || 400
        4   | 'update a question'  | 'PUT'  | '/api/solutions/questions/5'              | '{'                          || 400
        5   | 'update a question'  | 'PUT'  | '/api/solutions/questions/5'              | '[]'                         || 400
        6   | 'question status'    | 'POST' | '/api/solutions/questions/5/status'       | ''                           || 400
        7   | 'question status'    | 'POST' | '/api/solutions/questions/5/status'       | 'null'                       || 400
        8   | 'decide'             | 'POST' | '/api/solutions/1/decisions'              | ''                           || 400
        9   | 'decide'             | 'POST' | '/api/solutions/1/decisions'              | '[]'                         || 400
        10  | 'update a decision'  | 'PUT'  | '/api/solutions/decisions/20'             | 'null'                       || 400
        11  | 'supersede'          | 'POST' | '/api/solutions/decisions/20/supersede'   | '{'                          || 400
        12  | 'settle'             | 'POST' | '/api/solutions/decisions/20/settle'      | ''                           || 400
        13  | 'settle'             | 'POST' | '/api/solutions/decisions/20/settle'      | '{"questionIds":"x"}'        || 400
        14  | 'settle'             | 'POST' | '/api/solutions/decisions/20/settle'      | '{"itemIds":[1.5]}'          || 400
    }
}
