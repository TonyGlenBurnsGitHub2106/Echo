package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionSavedViewModels.SavedView

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): a member's saved views over HTTP -- GET and POST /api/solutions/{id}/views and DELETE
 * /api/solutions/views/{viewId}: who may call (a person, the house, or an agent the solution is shared with),
 * that every call is made in the caller's own member code, the query and body that become the storage call, the
 * answer of a save (the member's views of the tab), and how each refusal maps to a status (400 malformed, 404
 * not found, not shared or not the caller's, 409 a rule of the save proc). The real SolutionSavedViewService and
 * access fence run; only storage is mocked.
 */
@WebMvcTest(SolutionSavedViewController)
class SolutionSavedViewControllerSpec extends ConfigWebSupport {

    static final String SAVE = '{"scope":"items","name":" My open submit work ","filter":"{\\"assignee\\":\\"tony\\",\\"hideClosed\\":true}"}'
    static final String FILTER = '{"assignee":"tony","hideClosed":true}'

    @SpringBean SolutionSavedViewService service = Mock()

    SolutionSavedViewRepository repo = Mock()
    SolutionSavedViewService real

    def setup() {
        real = new SolutionSavedViewService(repo, access)
        service.list(*_) >> { a -> real.list(*a) }
        service.save(*_) >> { a -> real.save(*a) }
        service.delete(*_) >> { a -> real.delete(*a) }
        repo.list(*_) >> [view(4, 'My open submit work', 1), view(5, 'Blocked', 2)]
        repo.save(*_) >> 4
        repo.delete(*_) >> true
    }

    private static SavedView view(int id, String name, int sortOrder) {
        new SavedView(id, 1, 'items', name, FILTER, sortOrder, getLocalDateTime('11/04/2026 10:15'), null)
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
        scenario         | method   | url                           | body || expectedStatus
        'list views'     | 'GET'    | '/api/solutions/1/views'      | null || 401
        'save a view'    | 'POST'   | '/api/solutions/1/views'      | SAVE || 401
        'delete a view'  | 'DELETE' | '/api/solutions/views/4'      | null || 401
    }

    @Unroll
    def "#scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        scenario         | method   | url                           | body || path          | expectedValue
        'list views'     | 'GET'    | '/api/solutions/1/views'      | null || '$[0].name'   | 'My open submit work'
        'save a view'    | 'POST'   | '/api/solutions/1/views'      | SAVE || '$[1].name'   | 'Blocked'
        'delete a view'  | 'DELETE' | '/api/solutions/views/4'      | null || '$.deleted'   | true
    }

    @Unroll
    def "#scenario should answer 404 and touch no storage when #reason"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        scenario       | reason                                  | who    | method | url                         | body || expectedStatus
        'list views'   | 'the agent was given nothing'           | 'zed'  | 'GET'  | '/api/solutions/1/views'    | null || 404
        'save a view'  | 'the agent was given nothing'           | 'zed'  | 'POST' | '/api/solutions/1/views'    | SAVE || 404
        'list views'   | 'the agent was not given the solution'  | 'rex'  | 'GET'  | '/api/solutions/2/views'    | null || 404
        'save a view'  | 'the agent was not given the solution'  | 'rex'  | 'POST' | '/api/solutions/2/views'    | SAVE || 404
        'list views'   | 'the solution does not exist'           | 'user' | 'GET'  | '/api/solutions/9/views'    | null || 404
        'save a view'  | 'the solution does not exist'           | 'user' | 'POST' | '/api/solutions/9/views'    | SAVE || 404
    }

    def "every endpoint should match nothing when an id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/abc/views'))).andExpect(status().isNotFound())
        mvc.perform(asUser(request('POST', '/api/solutions/1x/views', SAVE))).andExpect(status().isNotFound())
        mvc.perform(asUser(delete('/api/solutions/views/four'))).andExpect(status().isNotFound())

        then:
        0 * service._
        0 * repo._
    }

    // ---- list ----

    @Unroll
    def "list should read the #expectedScope views of #expectedCode when #scenario"() {

        when:
        mvc.perform(by(who, get(url))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].id').value(4))
            .andExpect(jsonPath('$[0].solutionId').value(1))
            .andExpect(jsonPath('$[0].scope').value('items'))
            .andExpect(jsonPath('$[0].name').value('My open submit work'))
            .andExpect(jsonPath('$[0].filter').value(FILTER))
            .andExpect(jsonPath('$[0].sortOrder').value(1))
            .andExpect(jsonPath('$[0].createdDate').exists())
            .andExpect(jsonPath('$[0].modifiedDate').doesNotExist())

        then:
        1 * repo.list(1, expectedCode, expectedScope) >> [view(4, 'My open submit work', 1)]

        where:
        scenario                          | who       | url                                     || expectedCode | expectedScope
        'a person names no tab'           | 'user'    | '/api/solutions/1/views'                || 'tony'       | 'items'
        'the house names the items tab'   | 'service' | '/api/solutions/1/views?scope=items'    || 'felix'      | 'items'
        'Stewart names it in upper case'  | 'stewart' | '/api/solutions/1/views?scope=ITEMS'    || 'stewart'    | 'items'
        'an agent sends an empty tab'     | 'rex'     | '/api/solutions/1/views?scope='         || 'rex'        | 'items'
    }

    def "list should answer 400 and read nothing when the tab is not the items tab"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/views').param('scope', 'notes')))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value('Views can be saved for the items tab (scope=items).'))
        0 * repo._
    }

    // ---- save ----

    @Unroll
    def "save should save the view as #expectedCode and answer the member's views when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/1/views', SAVE))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[0].id').value(4))
            .andExpect(jsonPath('$[1].sortOrder').value(2))

        then:
        1 * repo.save(1, expectedCode, 'items', 'My open submit work', FILTER) >> 4

        then:
        1 * repo.list(1, expectedCode, 'items') >> [view(4, 'My open submit work', 1), view(5, 'Blocked', 2)]

        where:
        scenario                 | who       || expectedCode
        'a person saves it'      | 'user'    || 'tony'
        'the house saves it'     | 'service' || 'felix'
        'an agent saves it'      | 'rex'     || 'rex'
    }

    def "save should default the tab to items when the body names none"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/views', '{"name":"All","filter":"{}"}'))).andExpect(status().isOk())

        then:
        1 * repo.save(1, 'tony', 'items', 'All', '{}') >> 6
        1 * repo.list(1, 'tony', 'items') >> []
    }

    @Unroll
    def "#seq. save should answer 400 '#expectedMessage' and save nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/views', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        seq | scenario                         | body                                                      || expectedMessage
        1   | 'the tab is not the items tab'   | '{"scope":"notes","name":"Mine","filter":"{}"}'           || 'Views can be saved for the items tab (scope=items).'
        2   | 'there is no name'               | '{"filter":"{}"}'                                         || 'A view needs a name.'
        3   | 'the name is blank'              | '{"name":"   ","filter":"{}"}'                            || 'A view needs a name.'
        4   | 'the name is too long'           | '{"name":"@101@","filter":"{}"}'                          || "A view's name can be at most 100 characters."
        5   | 'there is no filter'             | '{"name":"Mine"}'                                         || 'filter must be a JSON object, as a string.'
        6   | 'the filter is a list'           | '{"name":"Mine","filter":"[1]"}'                          || 'filter must be a JSON object, as a string.'
        7   | 'the filter is too long'         | '{"name":"Mine","filter":"{@1999@}"}'                     || "A view's filters can be at most 2,000 characters."
    }

    @Unroll
    def "save should answer 400 and save nothing when the body is #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/views', body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo._

        where:
        scenario                                     | body                                        || expectedStatus
        'empty'                                      | ''                                          || 400
        'null, which Spring reads as missing'        | 'null'                                      || 400
        'broken'                                     | '{"name":'                                  || 400
        'a filter sent as an object, not a string'   | '{"name":"Mine","filter":{"a":1}}'          || 400
    }

    def "save should answer 409 with the proc's own words when the member has 30 views already"() {

        when:
        ResultActions r = mvc.perform(asAgent(request('POST', '/api/solutions/1/views', SAVE)))

        then:
        1 * repo.save(*_) >> { throw new SolutionRuleException('You have 30 saved views here already. Delete one first. Nothing was saved.') }
        0 * repo.list(*_)
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('You have 30 saved views here already. Delete one first. Nothing was saved.'))
    }

    // ---- delete ----

    @Unroll
    def "delete should delete #expectedCode's own view and answer deleted when #scenario"() {

        when:
        mvc.perform(by(who, delete('/api/solutions/views/4'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.deleted').value(true))

        then:
        1 * repo.delete(4, expectedCode) >> true

        where:
        scenario                     | who       || expectedCode
        'a person deletes it'        | 'user'    || 'tony'
        'the house deletes it'       | 'service' || 'felix'
        'an agent deletes it'        | 'rex'     || 'rex'
        'an agent with nothing'      | 'zed'     || 'zed'
    }

    def "delete should answer 404 'View not found.' when the view is not the caller's"() {

        when:
        ResultActions r = mvc.perform(asAgent(delete('/api/solutions/views/4')))

        then:
        1 * repo.delete(4, 'rex') >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('View not found.'))
    }

    def "delete should answer 404 'View not found.' and reach no storage when the id is zero"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/views/0')))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('View not found.'))
        0 * repo._
    }
}
