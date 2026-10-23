package com.echo

import java.time.LocalDateTime

import jakarta.servlet.http.HttpServletRequest
import org.hamcrest.Matchers
import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionDrafts.LogFilter
import static com.echo.SolutionFixtures.logEntry

import spock.lang.Unroll

// Story 5653: /api/solutions log -- the journal page and its filters, append, read, correct, delete; every caller.
@WebMvcTest(SolutionLogController)
@Import(SolutionLogService)
class SolutionLogControllerSpec extends SolutionWebSupport {

    @SpringBean SolutionLogRepository log = Mock()

    def setup() {
        solutionExists(1, true)
        solutionExists(2, false)
        log.find(200) >> logEntry(id: 200, solutionId: 1, typeCode: 'command', text: 'full text', sequenceNumber: 7)
        log.find(201) >> logEntry(id: 201, solutionId: 2)
    }

    @Unroll
    def "#method #path should answer #expectedStatus when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = json(request(method, path), '{}')

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))

        where:
        scenario                     | method   | path                         || expectedStatus
        "page has no credential"     | "GET"    | "/api/solutions/1/log"       || 401
        "append has no credential"   | "POST"   | "/api/solutions/1/log"       || 401
        "get has no credential"      | "GET"    | "/api/solutions/log/200"     || 401
        "correct has no credential"  | "PUT"    | "/api/solutions/log/200"     || 401
        "delete has no credential"   | "DELETE" | "/api/solutions/log/200"     || 401
    }

    // ---- the page -----------------------------------------------------------------------

    @Unroll
    def "page should answer newest first, 50 a page, with the five keys, when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get('/api/solutions/1/log')))

        then:
        1 * log.counts(new LogFilter(1, null, null, null, null, null, null, false, 0, 50)) >> [total: 1, 'type:command': 1]
        1 * log.list(_) >> [logEntry(id: 200, sequenceNumber: 7)]
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.entries[0].sequenceNumber').value(7))
            .andExpect(jsonPath('$.counts.total').value(1))
            .andExpect(jsonPath('$.offset').value(0))
            .andExpect(jsonPath('$.pageSize').value(expectedPageSize))
            .andExpect(jsonPath('$.total').value(1))

        where:
        scenario          | who       || expectedPageSize
        "a person asks"   | "user"    || 50
        "the house asks"  | "service" || 50
        "the agent asks"  | "agent"   || 50
    }

    def "page should pass every filter parameter, normalized, when all are sent"() {

        given:
        LogFilter expected = new LogFilter(1, 10, 100, 'rex', 'command', 118, 'mvn', true, 25, 500)

        when:
        mvc.perform(asUser(get('/api/solutions/1/log').param('projectId', '10').param('itemId', '100').param('author', ' rex ')
            .param('type', 'command').param('since', '118').param('search', 'mvn').param('ascending', 'true')
            .param('offset', '25').param('pageSize', '900'))).andExpect(status().isOk())

        then:
        1 * log.counts(expected) >> [:]
        1 * log.list(expected) >> []
    }

    def "page should read zero and negative filter values as no filter when they are sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/log').param('projectId', '0').param('itemId', '-1').param('since', '0')
            .param('offset', '-5').param('pageSize', '0').param('author', ' '))).andExpect(status().isOk())

        then:
        1 * log.counts(new LogFilter(1, null, null, null, null, null, null, false, 0, 50)) >> [:]
        1 * log.list(_) >> []
    }

    @Unroll
    def "page should answer 400 when #scenario"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/log').param(param, value))).andExpect(status().is(expectedStatus))

        where:
        scenario                          | param       | value       || expectedStatus
        "the project id is a letter"      | "projectId" | "x"         || 400
        "since has a fraction"            | "since"     | "1.5"       || 400
        "ascending is not a boolean"      | "ascending" | "sometimes" || 400
        "the page size is a word"         | "pageSize"  | "lots"      || 400
    }

    @Unroll
    def "page should answer 404 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get(path)))

        then:
        0 * log.list(_)
        0 * log.counts(_)
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                               | who     | path                   || expectedStatus
        "the agent was not given the solution" | "agent" | "/api/solutions/2/log" || 404
        "the solution does not exist"          | "user"  | "/api/solutions/9/log" || 404
    }

    // ---- one entry ----------------------------------------------------------------------

    @Unroll
    def "get should answer the entry with its full text when #scenario"() {

        expect:
        mvc.perform(by(who, get('/api/solutions/log/200'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.text').value('full text'))
            .andExpect(jsonPath('$.sequenceNumber').value(expectedSequence))

        where:
        scenario          | who       || expectedSequence
        "a person asks"   | "user"    || 7
        "the house asks"  | "service" || 7
        "the agent asks"  | "agent"   || 7
    }

    @Unroll
    def "get should answer 404 '#expectedMessage' when #scenario"() {

        expect:
        mvc.perform(by(who, get(path))).andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                  | who     | path                     || expectedMessage
        "the entry does not exist"                | "user"  | "/api/solutions/log/999" || "Log entry not found."
        "the agent reads another solution's entry" | "agent" | "/api/solutions/log/201" || "Solution not found."
    }

    // ---- append -------------------------------------------------------------------------

    def "append should pass every field into the draft and answer the saved entry when the agent appends"() {

        given:
        LocalDateTime when = getLocalDateTime('09/17/2026 14:30')
        Map<String, Object> body = [typeCode: 'command', itemId: 100, projectId: 10, text: ' mvn -q clean verify ', logDate: '2026-09-17T14:30']

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/1/log')), body))

        then:
        1 * log.save(new LogDraft(0, 1, 10, 100, 'command', 'mvn -q clean verify', when), 'rex') >> 202
        1 * log.find(202) >> logEntry(id: 202, sequenceNumber: 8)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.sequenceNumber').value(8))
    }

    def "append should look up the code when a kind name is sent"() {

        when:
        mvc.perform(json(asAgent(post('/api/solutions/1/log')), [typeName: 'Decision', text: 'go'])).andExpect(status().isOk())

        then:
        1 * log.save({ LogDraft d -> d.typeCode() == 'decision' }, 'rex') >> 202
        1 * log.find(202) >> logEntry(id: 202)
    }

    def "append should answer 409 and add no kind when an agent names a kind no kind has"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/1/log')), [typeName: 'Deploy', text: 'go']))

        then:
        0 * log.save(*_)
        0 * repo.findOrCreateLogType(_)
        r.andExpect(status().isConflict())
            .andExpect(jsonPath('$.message').value(Matchers.startsWith("There is no log kind named 'Deploy'.")))
    }

    @Unroll
    def "append should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/1/log')), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                    | body                                       || expectedStatus
        "the body is empty"         | "{}"                                       || 400
        "the text is blank"         | '{"text": "  "}'                           || 400
        "the log date is a word"    | '{"text": "x", "logDate": "yesterday"}'    || 400
        "the type code is unknown"  | '{"text": "x", "typeCode": "diary"}'       || 409
    }

    def "append should answer 404 when the agent was not given the solution"() {

        expect:
        mvc.perform(json(asAgent(post('/api/solutions/2/log')), [text: 'x'])).andExpect(status().isNotFound())
    }

    def "append should answer 409 with the proc's sentence when the item is from another solution"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/1/log')), [text: 'x', itemId: 999]))

        then:
        1 * log.save(*_) >> { throw new SolutionRuleException('That item does not belong to this solution.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('That item does not belong to this solution.'))
    }

    // ---- correct ------------------------------------------------------------------------

    @Unroll
    def "correct should send only what changed and never move the date when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(by(who, put('/api/solutions/log/200')), [typeCode: 'result', logDate: '2020-01-01']))

        then:
        1 * log.save(new LogDraft(200, 1, null, null, 'result', 'full text', null), expectedAuthor) >> 200
        r.andExpect(status().isOk())

        where:
        scenario            | who     || expectedAuthor
        "a person corrects" | "user"  || "tony"
        "the agent corrects" | "agent" || "rex"
    }

    @Unroll
    def "correct should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(by(who, put(path)), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                                    | who     | path                     | body                  || expectedStatus
        "the agent corrects another solution's one" | "agent" | "/api/solutions/log/201" | '{"text": "x"}'       || 404
        "the entry does not exist"                  | "user"  | "/api/solutions/log/999" | '{"text": "x"}'       || 404
        "the log date is a letter"                  | "user"  | "/api/solutions/log/200" | '{"logDate": "x"}'    || 400
    }

    // ---- delete -------------------------------------------------------------------------

    @Unroll
    def "delete should answer ok and the id when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, delete('/api/solutions/log/200')))

        then:
        1 * log.delete(200) >> true
        r.andExpect(status().isOk()).andExpect(jsonPath('$.ok').value(true)).andExpect(jsonPath('$.deletedId').value(expectedId))

        where:
        scenario                                | who     || expectedId
        "a person deletes"                      | "user"  || 200
        "the agent deletes inside its solution" | "agent" || 200
    }

    def "delete should answer 404 when the entry is hidden or storage refuses"() {

        when:
        ResultActions a = mvc.perform(asAgent(delete('/api/solutions/log/201')))
        ResultActions b = mvc.perform(asUser(delete('/api/solutions/log/200')))

        then:
        0 * log.delete(201)
        1 * log.delete(200) >> false
        a.andExpect(status().isNotFound())
        b.andExpect(status().isNotFound())
    }

    def "the mapping methods should refuse a null body themselves when called directly"() {

        given:
        SolutionLogController c = new SolutionLogController(Mock(SolutionLogService), Mock(SolutionCallerResolver))
        HttpServletRequest req = Mock()

        when:
        c.append(req, 1, null)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'A log entry is required.'

        when:
        c.correct(req, 200, null)

        then:
        thrown(IllegalArgumentException)
    }
}
