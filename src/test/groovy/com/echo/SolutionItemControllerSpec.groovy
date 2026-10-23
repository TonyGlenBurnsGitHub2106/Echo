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

import com.echo.SolutionDrafts.ItemDraft
import static com.echo.SolutionFixtures.item
import static com.echo.SolutionFixtures.project

import spock.lang.Unroll

// Story 5653: /api/solutions items -- list, one, locate, create, update, status, move, delete; every caller.
@WebMvcTest(SolutionItemController)
@Import(SolutionItemService)
class SolutionItemControllerSpec extends SolutionWebSupport {

    @SpringBean SolutionItemRepository items = Mock()

    def setup() {
        solutionExists(1, true)
        solutionExists(2, false)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'inprogress')
        repo.findProject(11) >> project(id: 11, solutionId: 1, statusCode: 'inprogress')
        repo.findProject(20) >> project(id: 20, solutionId: 2, statusCode: 'inprogress')
        items.find(100) >> item(id: 100, projectId: 10, solutionId: 1, title: 'Upgrade', detail: 'full detail', statusCode: 'todo')
        items.find(200) >> item(id: 200, projectId: 20, solutionId: 2)
    }

    @Unroll
    def "#seq. #method #path should answer #expectedStatus when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = json(request(method, path), '{}')

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))

        where:
        seq | scenario                          | method   | path                                 || expectedStatus
        1   | "list has no credential"          | "GET"    | "/api/solutions/1/items"             || 401
        2   | "get has no credential"           | "GET"    | "/api/solutions/items/100"           || 401
        3   | "locate has no credential"        | "GET"    | "/api/solutions/items/100/locate"    || 401
        4   | "create has no credential"        | "POST"   | "/api/solutions/projects/10/items"   || 401
        5   | "update has no credential"        | "PUT"    | "/api/solutions/items/100"           || 401
        6   | "setStatus has no credential"     | "POST"   | "/api/solutions/items/100/status"    || 401
        7   | "move has no credential"          | "POST"   | "/api/solutions/items/100/move"      || 401
        8   | "delete has no credential"        | "DELETE" | "/api/solutions/items/100"           || 401
    }

    def "get should answer 403 before anything is read when the agent's allowlist does not reach the path"() {

        when:
        ResultActions r = mvc.perform(asScopedElsewhere(get('/api/solutions/items/100')))

        then:
        r.andExpect(status().isForbidden())
        0 * items._
    }

    // ---- list ---------------------------------------------------------------------------

    @Unroll
    def "list should answer every item, flat, and narrow to a project when #scenario"() {

        when:
        ResultActions a = mvc.perform(by(who, get('/api/solutions/1/items')))
        ResultActions b = mvc.perform(by(who, get('/api/solutions/1/items').param('projectId', '10')))

        then:
        1 * items.listForSolution(1, null) >> [item(id: 100, parentItemId: null), item(id: 101, parentItemId: 100)]
        1 * items.listForSolution(1, 10) >> []
        a.andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(expectedCount))
            .andExpect(jsonPath('$[1].parentItemId').value(100))
        b.andExpect(status().isOk())

        where:
        scenario          | who       || expectedCount
        "a person asks"   | "user"    || 2
        "the house asks"  | "service" || 2
        "the agent asks"  | "agent"   || 2
    }

    @Unroll
    def "list should answer #expectedStatus when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = by(who, get(path))
        if (projectId != null) {
            req.param('projectId', projectId)
        }

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))

        where:
        scenario                                 | who     | path                     | projectId || expectedStatus
        "the agent was not given the solution"   | "agent" | "/api/solutions/2/items" | null      || 404
        "the solution does not exist"            | "user"  | "/api/solutions/9/items" | null      || 404
        "the project id is not a number"         | "user"  | "/api/solutions/1/items" | "x"       || 400
    }

    // ---- one / locate -------------------------------------------------------------------

    @Unroll
    def "get and locate should answer the item and where it lives when #scenario"() {

        expect:
        mvc.perform(by(who, get('/api/solutions/items/100'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(100))
            .andExpect(jsonPath('$.detail').value('full detail'))
        mvc.perform(by(who, get('/api/solutions/items/100/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.itemId').value(100))
            .andExpect(jsonPath('$.projectId').value(expectedProject))
            .andExpect(jsonPath('$.solutionId').value(1))

        where:
        scenario          | who       || expectedProject
        "a person asks"   | "user"    || 10
        "the house asks"  | "service" || 10
        "the agent asks"  | "agent"   || 10
    }

    def "locate should answer exactly three fields when the item is found"() {

        when:
        String body = mvc.perform(asUser(get('/api/solutions/items/100/locate'))).andReturn().response.contentAsString

        then:
        (MAPPER.readValue(body, Map) as Map).keySet() as List == ['itemId', 'projectId', 'solutionId']
    }

    @Unroll
    def "get and locate should answer 404 '#expectedMessage' when #scenario"() {

        expect:
        mvc.perform(by(who, get(path))).andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                    | who     | path                                  || expectedMessage
        "the item does not exist"                   | "user"  | "/api/solutions/items/999"            || "Item not found."
        "locate is asked for id zero"               | "user"  | "/api/solutions/items/0/locate"       || "Item not found."
        "the agent reads another solution's item"   | "agent" | "/api/solutions/items/200"            || "Solution not found."
        "the agent locates another solution's item" | "agent" | "/api/solutions/items/200/locate"     || "Solution not found."
    }

    // ---- create -------------------------------------------------------------------------

    def "create should take the project from the path and pass every field into the draft when the agent adds an item"() {

        given:
        LocalDateTime due = getLocalDateTime('10/15/2026 08:00')
        Map<String, Object> body = [title: ' Upgrade ', detail: 'd', parentItemId: 100, statusCode: 'todo', assigneeCode: 'rex',
                                    priority: 2, sortOrder: 4, dueDate: '2026-10-15T08:00']

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/projects/10/items')), body))

        then:
        1 * repo.saveMember(0, 'rex', 'Rex', true, true, 3)
        1 * items.save(new ItemDraft(0, 10, 100, 'Upgrade', 'd', 'todo', 'rex', 2, 4, due), 'rex') >> 101
        1 * items.find(101) >> item(id: 101, statusCode: 'todo')
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(101))
    }

    @Unroll
    def "create should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/projects/10/items')), body)).andExpect(status().is(expectedStatus))
            .andExpect(jsonPath('$.message').value(Matchers.startsWith(expectedStart)))

        where:
        scenario                  | body                                         || expectedStatus | expectedStart
        "the body is empty"       | "{}"                                         || 400            | "A title is required."
        "the title is blank"      | '{"title": "  "}'                            || 400            | "A title is required."
        "the due date is a word"  | '{"title": "x", "dueDate": "Friday"}'        || 400            | "The due date must look like"
        "the status is unknown"   | '{"title": "x", "statusCode": "finished"}'   || 409            | "There is no status code 'finished'."
        "the assignee is unknown" | '{"title": "x", "assigneeCode": "bob"}'      || 409            | "There is no member code 'bob'."
    }

    @Unroll
    def "create should answer 404 '#expectedMessage' when #scenario"() {

        expect:
        mvc.perform(json(by(who, post(path)), '{"title": "x"}')).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                       | who     | path                                || expectedMessage
        "the project does not exist"                   | "user"  | "/api/solutions/projects/99/items"  || "Project not found."
        "the project is in a solution not given to it" | "agent" | "/api/solutions/projects/20/items"  || "Solution not found."
    }

    def "create should answer 409 with the proc's sentence when the parent is in another project"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/projects/10/items')), [title: 'x', parentItemId: 999]))

        then:
        1 * items.save(*_) >> { throw new SolutionRuleException('The parent item must be in the same project.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('The parent item must be in the same project.'))
    }

    // ---- update -------------------------------------------------------------------------

    def "update should send only what changed, keeping the title and the project, when the agent edits the detail"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(put('/api/solutions/items/100')), [detail: 'Found two configs, not one.']))

        then:
        1 * items.save({ ItemDraft d -> d.id() == 100 && d.projectId() == 10 && d.title() == 'Upgrade' &&
            d.detail() == 'Found two configs, not one.' && d.statusCode() == 'todo' }, 'rex') >> 100
        r.andExpect(status().isOk())
    }

    @Unroll
    def "update should answer 404 when #scenario"() {

        expect:
        mvc.perform(json(by(who, put(path)), '{"title": "x"}')).andExpect(status().is(expectedStatus))

        where:
        scenario                                  | who     | path                       || expectedStatus
        "the agent edits another solution's item" | "agent" | "/api/solutions/items/200" || 404
        "the item does not exist"                 | "user"  | "/api/solutions/items/999" || 404
    }

    // ---- status -------------------------------------------------------------------------

    @Unroll
    def "setStatus should change only the status when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(by(who, post('/api/solutions/items/100/status')), [statusCode: ' done ']))

        then:
        1 * items.setStatus(100, 'done', expectedAuthor) >> true
        r.andExpect(status().isOk())

        where:
        scenario            | who       || expectedAuthor
        "a person sets it"  | "user"    || "tony"
        "the house sets it" | "service" || "felix"
        "the agent sets it" | "agent"   || "rex"
    }

    @Unroll
    def "setStatus should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/items/100/status')), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                | body                           || expectedStatus
        "the body is empty"     | "{}"                           || 400
        "the code is empty"     | '{"statusCode": ""}'           || 400
        "the code is unknown"   | '{"statusCode": "finished"}'   || 409
    }

    @Unroll
    def "setStatus should answer 404 when #scenario"() {

        expect:
        mvc.perform(json(by(who, post(path)), '{"statusCode": "done"}')).andExpect(status().is(expectedStatus))

        where:
        scenario                                    | who     | path                               || expectedStatus
        "the agent sets another solution's item"    | "agent" | "/api/solutions/items/200/status"  || 404
        "the item does not exist"                   | "user"  | "/api/solutions/items/999/status"  || 404
    }

    def "setStatus should answer 404 when storage refuses the change"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/items/100/status')), [statusCode: 'done']))

        then:
        1 * items.setStatus(100, 'done', 'tony') >> false
        r.andExpect(status().isNotFound())
    }

    // ---- move ---------------------------------------------------------------------------

    def "move should move within the solution, absent fields null and sortOrder 0, when the destination is valid"() {

        when:
        ResultActions a = mvc.perform(json(asAgent(post('/api/solutions/items/100/move')), [projectId: 11, parentItemId: 0, sortOrder: 1]))
        ResultActions b = mvc.perform(json(asUser(post('/api/solutions/items/100/move')), [:]))

        then:
        1 * items.move(100, 11, 0, 1, 'rex') >> true
        1 * items.move(100, null, null, 0, 'tony') >> true
        a.andExpect(status().isOk())
        b.andExpect(status().isOk())
    }

    @Unroll
    def "move should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(by(who, post(path)), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                                             | who     | path                             | body               || expectedStatus
        "a person moves it to another solution's project"    | "user"  | "/api/solutions/items/100/move"  | '{"projectId": 20}' || 409
        "the agent moves it to a solution it was not given"  | "agent" | "/api/solutions/items/100/move"  | '{"projectId": 20}' || 404
        "the agent moves another solution's item"            | "agent" | "/api/solutions/items/200/move"  | "{}"               || 404
    }

    def "move should name the rule when the target project is in another solution"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/items/100/move')), [projectId: 20]))
            .andExpect(jsonPath('$.message').value('An item can only move between projects of the same solution.'))
    }

    def "move should answer 404 when storage refuses the move"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/items/100/move')), [:]))

        then:
        1 * items.move(*_) >> false
        r.andExpect(status().isNotFound())
    }

    // ---- delete -------------------------------------------------------------------------

    @Unroll
    def "delete should answer the id and how many items went when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, delete('/api/solutions/items/100')))

        then:
        1 * items.delete(100) >> 3
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.deletedId').value(100))
            .andExpect(jsonPath('$.itemsRemoved').value(expectedRemoved))

        where:
        scenario                                 | who     || expectedRemoved
        "a person deletes"                       | "user"  || 3
        "the agent deletes inside its solution"  | "agent" || 3
    }

    def "delete should answer 404 and remove nothing when the agent deletes another solution's item"() {

        when:
        ResultActions r = mvc.perform(asAgent(delete('/api/solutions/items/200')))

        then:
        0 * items.delete(_)
        r.andExpect(status().isNotFound())
    }

    // ---- the null-body guards -----------------------------------------------------------

    def "the mapping methods should refuse a null body themselves when called directly"() {

        given:
        SolutionItemController c = new SolutionItemController(Mock(SolutionItemService), Mock(SolutionCallerResolver))
        HttpServletRequest req = Mock()

        when:
        c.create(req, 10, null)

        then:
        IllegalArgumentException e1 = thrown()
        e1.message == 'An item is required.'

        when:
        c.update(req, 100, null)

        then:
        thrown(IllegalArgumentException)

        when:
        c.setStatus(req, 100, null)

        then:
        IllegalArgumentException e2 = thrown()
        e2.message == 'A status code is required.'

        when:
        c.move(req, 100, null)

        then:
        IllegalArgumentException e3 = thrown()
        e3.message == 'A destination is required.'
    }
}
