package com.echo

import tools.jackson.databind.json.JsonMapper

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionLifecycleModels.OpenThing
import com.echo.SolutionLifecycleModels.ShapeResult

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695 (Release 6): the start and the end of a solution over HTTP -- GET /api/solutions/shapes, POST
 * /api/solutions/{id}/shape and GET /api/solutions/{id}/close-out: who may call (a person, the house, or an agent
 * the solution is shared with, as for every other solution endpoint), the body that becomes the storage call in
 * the caller's own member code, the shape of each answer, and how each refusal maps to a status (400 malformed,
 * 404 not found or not shared, 409 a rule of the shape proc, passed through word for word). The real
 * SolutionLifecycleService and access fence run; only storage is mocked.
 */
@WebMvcTest(SolutionLifecycleController)
class SolutionLifecycleControllerSpec extends ConfigWebSupport {

    static final String SHAPE = '{"shape":" Programming "}'

    @SpringBean SolutionLifecycleService service = Mock()

    SolutionLifecycleRepository repo = Mock()
    SolutionLifecycleService real

    def setup() {
        real = new SolutionLifecycleService(repo, access)
        service.shapes() >> { real.shapes() }
        service.applyShape(*_) >> { a -> real.applyShape(*a) }
        service.closeOut(*_) >> { a -> real.closeOut(*a) }
        repo.applyShape(*_) >> new ShapeResult(4, 1, 3, 2, 0, 0)
        repo.closeOut(*_) >> openThings()
    }

    private static List<OpenThing> openThings() {
        [new OpenThing('project', 'project', 41, 'Build', 'Not started', getLocalDateTime('11/04/2026 10:15')),
         new OpenThing('item', 'item', 310, 'Parse the 835', 'Build | In progress | Rex', getLocalDateTime('11/04/2026 11:00')),
         new OpenThing('part', 'topic', 77, 'Production', 'Environment | Not started', getLocalDateTime('11/04/2026 11:30')),
         new OpenThing('thread', 'message', 12, 'Untitled thread', 'waiting for tony', getLocalDateTime('11/04/2026 12:00'))]
    }

    // ---- who may call ----

    @Unroll
    def "#scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * service._
        0 * repo._
        r.andExpect(status().is(expectedStatus))

        where:
        scenario             | method | url                             | body  || expectedStatus
        'list the shapes'    | 'GET'  | '/api/solutions/shapes'         | null  || 401
        'give a shape'       | 'POST' | '/api/solutions/1/shape'        | SHAPE || 401
        'read the close-out' | 'GET'  | '/api/solutions/1/close-out'    | null  || 401
    }

    @Unroll
    def "#scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        scenario             | method | url                             | body  || path            | expectedValue
        'list the shapes'    | 'GET'  | '/api/solutions/shapes'         | null  || '$[3].code'     | 'copy'
        'give a shape'       | 'POST' | '/api/solutions/1/shape'        | SHAPE || '$.projects'    | 4
        'read the close-out' | 'GET'  | '/api/solutions/1/close-out'    | null  || '$.total'       | 4
    }

    def "the shapes should be listed for an agent given nothing, since they belong to no solution"() {

        expect:
        mvc.perform(by('zed', get('/api/solutions/shapes'))).andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(4))
    }

    @Unroll
    def "#scenario should answer 404 and touch no storage when #reason"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        scenario             | reason                                  | who    | method | url                            | body  || expectedStatus
        'give a shape'       | 'the agent was given nothing'           | 'zed'  | 'POST' | '/api/solutions/1/shape'       | SHAPE || 404
        'read the close-out' | 'the agent was given nothing'           | 'zed'  | 'GET'  | '/api/solutions/1/close-out'   | null  || 404
        'give a shape'       | 'the agent was not given the solution'  | 'rex'  | 'POST' | '/api/solutions/2/shape'       | SHAPE || 404
        'read the close-out' | 'the agent was not given the solution'  | 'rex'  | 'GET'  | '/api/solutions/2/close-out'   | null  || 404
        'give a shape'       | 'the solution does not exist'           | 'user' | 'POST' | '/api/solutions/9/shape'       | SHAPE || 404
        'read the close-out' | 'the solution does not exist'           | 'user' | 'GET'  | '/api/solutions/9/close-out'   | null  || 404
    }

    def "every solution endpoint should match nothing when the id is not a number"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/abc/shape', SHAPE))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/1x/close-out'))).andExpect(status().isNotFound())

        then:
        0 * service._
        0 * repo._
    }

    // ---- GET /shapes ----

    def "shapes should answer the four shapes in order with their code, name and description"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/shapes')))

        then:
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(4))
            .andExpect(jsonPath('$[0].code').value('programming'))
            .andExpect(jsonPath('$[0].name').value('Programming effort'))
            .andExpect(jsonPath('$[0].description').value(SolutionLifecycleModels.SHAPES[0].description()))
            .andExpect(jsonPath('$[1].code').value('writing'))
            .andExpect(jsonPath('$[1].name').value('Writing effort'))
            .andExpect(jsonPath('$[2].code').value('life'))
            .andExpect(jsonPath('$[2].name').value('Life matter'))
            .andExpect(jsonPath('$[3].code').value('copy'))
            .andExpect(jsonPath('$[3].name').value('The shape of another solution'))
            .andExpect(jsonPath('$[3].description').exists())
        0 * repo._
    }

    // ---- POST /{id}/shape ----

    @Unroll
    def "#seq. shape should make the #expectedShape shape as #expectedCode when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', url, body))).andExpect(status().isOk())

        then:
        1 * repo.applyShape(expectedId, expectedShape, expectedFrom, expectedCode) >> new ShapeResult(3, 2, 0, 2, 0, 0)

        where:
        seq | scenario                                   | who       | url                        | body                                          || expectedId | expectedShape | expectedFrom | expectedCode
        1   | 'a person names programming padded'        | 'user'    | '/api/solutions/1/shape'   | '{"shape":" Programming "}'                   || 1          | 'programming' | null         | 'tony'
        2   | 'the house names writing'                  | 'service' | '/api/solutions/2/shape'   | '{"shape":"writing"}'                         || 2          | 'writing'     | null         | 'felix'
        3   | 'Stewart names life in upper case'         | 'stewart' | '/api/solutions/1/shape'   | '{"shape":"LIFE"}'                            || 1          | 'life'        | null         | 'stewart'
        4   | 'an agent names life'                      | 'rex'     | '/api/solutions/1/shape'   | '{"shape":"life"}'                            || 1          | 'life'        | null         | 'rex'
        5   | 'a source sent with writing is ignored'    | 'user'    | '/api/solutions/1/shape'   | '{"shape":"writing","fromSolutionId":2}'      || 1          | 'writing'     | null         | 'tony'
        6   | 'a person copies another solution'         | 'user'    | '/api/solutions/1/shape'   | '{"shape":"copy","fromSolutionId":2}'         || 1          | 'copy'        | 2            | 'tony'
        7   | 'an agent copies the solution it was given'| 'rex'     | '/api/solutions/1/shape'   | '{"shape":"Copy","fromSolutionId":1}'         || 1          | 'copy'        | 1            | 'rex'
    }

    def "shape should answer how many of each the shape made"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/shape', '{"shape":"copy","fromSolutionId":2}')))

        then:
        1 * repo.applyShape(1, 'copy', 2, 'tony') >> new ShapeResult(2, 3, 5, 4, 1, 6)
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.projects').value(2))
            .andExpect(jsonPath('$.kindsOfPart').value(3))
            .andExpect(jsonPath('$.parts').value(5))
            .andExpect(jsonPath('$.tags').value(4))
            .andExpect(jsonPath('$.termTypes').value(1))
            .andExpect(jsonPath('$.termCategories').value(6))
    }

    @Unroll
    def "shape should answer 400 '#expectedMessage' and make nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/shape', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        scenario                         | body                                      || expectedMessage
        'there is no shape'              | '{}'                                      || 'shape must be one of programming, writing, life, copy.'
        'the shape is blank'             | '{"shape":"  "}'                          || 'shape must be one of programming, writing, life, copy.'
        'the shape is not one'           | '{"shape":"design"}'                      || 'shape must be one of programming, writing, life, copy.'
        'copy names no source'           | '{"shape":"copy"}'                        || 'Say which solution to copy the shape of (fromSolutionId).'
        'copy names source zero'         | '{"shape":"copy","fromSolutionId":0}'     || 'Say which solution to copy the shape of (fromSolutionId).'
    }

    @Unroll
    def "shape should answer 400 and make nothing when the body is #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/shape', body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo._

        where:
        scenario                                  | body                                         || expectedStatus
        'empty'                                   | ''                                           || 400
        'null, which Spring reads as missing'     | 'null'                                       || 400
        'broken'                                  | '{"shape":'                                  || 400
        'a source sent as text, not a number'     | '{"shape":"copy","fromSolutionId":"two"}'    || 400
    }

    @Unroll
    def "shape should answer 404 and make nothing when the source to copy is #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, request('POST', '/api/solutions/1/shape', body)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        scenario                            | who    | body                                      || unused
        'not shared with the agent'         | 'rex'  | '{"shape":"copy","fromSolutionId":2}'     || null
        'missing'                           | 'user' | '{"shape":"copy","fromSolutionId":9}'     || null
    }

    @Unroll
    def "shape should answer 409 with the proc's own words when #scenario"() {

        when:
        ResultActions r = mvc.perform(asAgent(request('POST', '/api/solutions/1/shape', body)))

        then:
        1 * repo.applyShape(*_) >> { throw new SolutionRuleException(refusal) }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule')).andExpect(jsonPath('$.message').value(refusal))

        where:
        scenario                               | body                                      || refusal
        'the solution is not empty'            | SHAPE                                     || 'A shape is for a new, empty solution. This one has projects or topics already. Nothing was added.'
        'the solution would take its own shape'| '{"shape":"copy","fromSolutionId":1}'     || 'A solution cannot take its own shape. Nothing was added.'
    }

    // ---- GET /{id}/close-out ----

    def "close-out should answer the open things with group, kind, id, title, sub and date, the counts and the total"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/close-out')))

        then:
        1 * repo.closeOut(1) >> openThings()
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.open.length()').value(4))
            .andExpect(jsonPath('$.open[0].group').value('project'))
            .andExpect(jsonPath('$.open[0].kind').value('project'))
            .andExpect(jsonPath('$.open[0].id').value(41))
            .andExpect(jsonPath('$.open[0].title').value('Build'))
            .andExpect(jsonPath('$.open[0].sub').value('Not started'))
            .andExpect(jsonPath('$.open[0].changedDate').exists())
            .andExpect(jsonPath('$.open[2].group').value('part'))
            .andExpect(jsonPath('$.open[2].kind').value('topic'))
            .andExpect(jsonPath('$.open[2].id').value(77))
            .andExpect(jsonPath('$.open[3].group').value('thread'))
            .andExpect(jsonPath('$.open[3].kind').value('message'))
            .andExpect(jsonPath('$.open[3].id').value(12))
            .andExpect(jsonPath('$.open[3].sub').value('waiting for tony'))
            .andExpect(jsonPath('$.counts.project').value(1))
            .andExpect(jsonPath('$.counts.item').value(1))
            .andExpect(jsonPath('$.counts.question').value(0))
            .andExpect(jsonPath('$.counts.decision').value(0))
            .andExpect(jsonPath('$.counts.part').value(1))
            .andExpect(jsonPath('$.counts.assumption').value(0))
            .andExpect(jsonPath('$.counts.thread').value(1))
            .andExpect(jsonPath('$.total').value(4))
    }

    def "close-out should name every group in its order, zeros included, when nothing is open"() {

        when:
        String body = mvc.perform(asUser(get('/api/solutions/1/close-out'))).andExpect(status().isOk()).andReturn().response.contentAsString
        Map<String, Object> answer = (Map<String, Object>) JsonMapper.builder().build().readValue(body, Map)

        then:
        1 * repo.closeOut(1) >> []
        answer.open == []
        answer.total == 0
        answer.counts == [project: 0, item: 0, question: 0, decision: 0, part: 0, assumption: 0, thread: 0]
        body.indexOf('"project"') < body.indexOf('"item"')
        body.indexOf('"item"') < body.indexOf('"question"')
        body.indexOf('"question"') < body.indexOf('"decision"')
        body.indexOf('"decision"') < body.indexOf('"part"')
        body.indexOf('"part"') < body.indexOf('"assumption"')
        body.indexOf('"assumption"') < body.indexOf('"thread"')
    }
}
