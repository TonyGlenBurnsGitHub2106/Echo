package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionTestModels.Location

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5653: the testing checklists over HTTP -- every endpoint, who may call it, its JSON, validation and
 * how each refusal maps to a status. The service is real (delegated from the bean mock); storage is fake.
 */
@WebMvcTest(SolutionTestController)
class SolutionTestControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionTestService service = Mock()

    SolutionTestRepository repo = Mock()
    SolutionTestService real

    Map<String, Location> locs = [
        'plan:1': new Location(1, 1), 'plan:2': new Location(2, 2), 'plan:3': new Location(1, 3),
        'level:11': new Location(1, 1), 'level:12': new Location(1, 1), 'level:13': new Location(2, 2), 'level:14': new Location(1, 3),
        'group:21': new Location(1, 1), 'group:22': new Location(2, 2), 'group:24': new Location(1, 3),
        'case:31': new Location(1, 1), 'case:32': new Location(2, 2)]

    def setup() {
        real = new SolutionTestService(repo, access)
        service.list(*_) >> { a -> real.list(*a) }
        service.plan(*_) >> { a -> real.plan(*a) }
        service.createPlan(*_) >> { a -> real.createPlan(*a) }
        service.updatePlan(*_) >> { a -> real.updatePlan(*a) }
        service.deletePlan(*_) >> { a -> real.deletePlan(*a) }
        service.addLevel(*_) >> { a -> real.addLevel(*a) }
        service.updateLevel(*_) >> { a -> real.updateLevel(*a) }
        service.deleteLevel(*_) >> { a -> real.deleteLevel(*a) }
        service.addGroup(*_) >> { a -> real.addGroup(*a) }
        service.updateGroup(*_) >> { a -> real.updateGroup(*a) }
        service.deleteGroup(*_) >> { a -> real.deleteGroup(*a) }
        service.addCase(*_) >> { a -> real.addCase(*a) }
        service.updateCase(*_) >> { a -> real.updateCase(*a) }
        service.deleteCase(*_) >> { a -> real.deleteCase(*a) }
        service.reorderCases(*_) >> { a -> real.reorderCases(*a) }
        service.reorderGroups(*_) >> { a -> real.reorderGroups(*a) }
        service.setCheck(*_) >> { a -> real.setCheck(*a) }
        service.setResult(*_) >> { a -> real.setResult(*a) }
        service.clear(*_) >> { a -> real.clear(*a) }

        repo.locate(_, _) >> { String kind, int id -> locs[kind + ':' + id] }
        repo.plans(_, _) >> { int s, String k -> s == 1 ? [[SolutionTestPlanID: 1, SolutionID: 1, PlanTitle: 'Smoke', PlanKindCode: 'dev', CaseCount: 3, LevelCount: 2]] : [] }
        repo.plan(_) >> { int p -> [1: [SolutionTestPlanID: 1, SolutionID: 1, PlanTitle: 'Smoke', PlanDescription: 'd', PlanKindCode: 'qa'],
                                    2: [SolutionTestPlanID: 2, SolutionID: 2, PlanTitle: 'Other'],
                                    3: [SolutionTestPlanID: 3, SolutionID: 1, PlanTitle: 'Third']][p] }
        repo.levels(_) >> { int p -> p == 1 ? [[SolutionTestPlanLevelID: 11, LevelTitle: 'Manual'], [SolutionTestPlanLevelID: 12, LevelTitle: 'Unit tested']] : [] }
        repo.groups(_) >> { int p -> p == 1 ? [[SolutionTestGroupID: 21, GroupTitle: 'Login']] : [] }
        repo.cases(_) >> { int p -> p == 1 ? [[SolutionTestCaseID: 31, SolutionTestGroupID: 21, CaseText: 'Sign in', CaseNote: 'n']] : [] }
        repo.checks(_) >> { int p -> p == 1 ? [[SolutionTestCaseID: 31, SolutionTestPlanLevelID: 11, IsChecked: true, CheckedByCode: 'rex']] : [] }
        repo.skips(_) >> []
        repo.counts(_) >> { int p -> p == 1 ? [cases: 1, checked: 1, 'level:11': 1, 'group:21': 1, 'grouplevel:21.11': 1] : [:] }
        repo.savePlan(*_) >> 1
        repo.deletePlan(_) >> true
        repo.deleteLevel(_) >> true
        repo.deleteGroup(_) >> true
        repo.deleteCase(_) >> true
    }

    @Unroll
    def "#seq. #scenario should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * repo._
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('Not signed in'))

        where:
        seq | scenario          | method   | url                                                   | body                             || expectedStatus
        1   | 'list'            | 'GET'    | '/api/solutions/1/test-plans'                         | null                             || 401
        2   | 'createPlan'      | 'POST'   | '/api/solutions/1/test-plans'                         | '{"title":"New"}'                || 401
        3   | 'plan'            | 'GET'    | '/api/solutions/test-plans/1'                         | null                             || 401
        4   | 'updatePlan'      | 'PUT'    | '/api/solutions/test-plans/1'                         | '{"title":"Renamed"}'            || 401
        5   | 'deletePlan'      | 'DELETE' | '/api/solutions/test-plans/1'                         | null                             || 401
        6   | 'addLevel'        | 'POST'   | '/api/solutions/test-plans/1/columns'                 | '{"title":"Regression"}'         || 401
        7   | 'updateLevel'     | 'PUT'    | '/api/solutions/test-plans/columns/11'                | '{"title":"By hand"}'            || 401
        8   | 'deleteLevel'     | 'DELETE' | '/api/solutions/test-plans/columns/11'                | null                             || 401
        9   | 'addGroup'        | 'POST'   | '/api/solutions/test-plans/1/groups'                  | '{"title":"Billing"}'            || 401
        10  | 'updateGroup'     | 'PUT'    | '/api/solutions/test-plans/groups/21'                 | '{"title":"Sign-in"}'            || 401
        11  | 'deleteGroup'     | 'DELETE' | '/api/solutions/test-plans/groups/21'                 | null                             || 401
        12  | 'addCase'         | 'POST'   | '/api/solutions/test-plans/groups/21/cases'           | '{"text":"Pay","note":"card"}'   || 401
        13  | 'updateCase'      | 'PUT'    | '/api/solutions/test-plans/cases/31'                  | '{"text":"Sign in again"}'       || 401
        14  | 'deleteCase'      | 'DELETE' | '/api/solutions/test-plans/cases/31'                  | null                             || 401
        15  | 'reorderCases'    | 'PUT'    | '/api/solutions/test-plans/groups/21/case-order'      | '{"ids":[31]}'                   || 401
        16  | 'reorderGroups'   | 'PUT'    | '/api/solutions/test-plans/1/group-order'             | '{"ids":[21]}'                   || 401
        17  | 'setCheck'        | 'PUT'    | '/api/solutions/test-plans/cases/31/checks/11'        | '{"checked":true}'               || 401
        18  | 'setResult'       | 'PUT'    | '/api/solutions/test-plans/cases/31/result'           | '{"failing":true,"note":"boom"}' || 401
        19  | 'clear'           | 'POST'   | '/api/solutions/test-plans/1/clear'                   | null                             || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        seq | scenario          | method   | url                                                   | body                              || path              | expectedValue
        1   | 'list'            | 'GET'    | '/api/solutions/1/test-plans'                         | null                              || '$[0].solutionId' | 1
        2   | 'createPlan'      | 'POST'   | '/api/solutions/1/test-plans'                         | '{"title":"New"}'                 || '$.solutionId'    | 1
        3   | 'plan'            | 'GET'    | '/api/solutions/test-plans/1'                         | null                              || '$.solutionId'    | 1
        4   | 'updatePlan'      | 'PUT'    | '/api/solutions/test-plans/1'                         | '{"title":"Renamed"}'             || '$.solutionId'    | 1
        5   | 'deletePlan'      | 'DELETE' | '/api/solutions/test-plans/1'                         | null                              || '$.deletedId'     | 1
        6   | 'addLevel'        | 'POST'   | '/api/solutions/test-plans/1/columns'                 | '{"title":"Regression"}'          || '$.solutionId'    | 1
        7   | 'updateLevel'     | 'PUT'    | '/api/solutions/test-plans/columns/11'                | '{"title":"By hand"}'             || '$.solutionId'    | 1
        8   | 'deleteLevel'     | 'DELETE' | '/api/solutions/test-plans/columns/11'                | null                              || '$.solutionId'    | 1
        9   | 'addGroup'        | 'POST'   | '/api/solutions/test-plans/1/groups'                  | '{"title":"Billing"}'             || '$.solutionId'    | 1
        10  | 'updateGroup'     | 'PUT'    | '/api/solutions/test-plans/groups/21'                 | '{"title":"Sign-in"}'             || '$.solutionId'    | 1
        11  | 'deleteGroup'     | 'DELETE' | '/api/solutions/test-plans/groups/21'                 | null                              || '$.solutionId'    | 1
        12  | 'addCase'         | 'POST'   | '/api/solutions/test-plans/groups/21/cases'           | '{"text":"Pay","note":"card"}'    || '$.solutionId'    | 1
        13  | 'updateCase'      | 'PUT'    | '/api/solutions/test-plans/cases/31'                  | '{"text":"Sign in again"}'        || '$.solutionId'    | 1
        14  | 'deleteCase'      | 'DELETE' | '/api/solutions/test-plans/cases/31'                  | null                              || '$.solutionId'    | 1
        15  | 'reorderCases'    | 'PUT'    | '/api/solutions/test-plans/groups/21/case-order'      | '{"ids":[31]}'                    || '$.solutionId'    | 1
        16  | 'reorderGroups'   | 'PUT'    | '/api/solutions/test-plans/1/group-order'             | '{"ids":[21]}'                    || '$.solutionId'    | 1
        17  | 'setCheck'        | 'PUT'    | '/api/solutions/test-plans/cases/31/checks/11'        | '{"checked":true}'                || '$.solutionId'    | 1
        18  | 'setResult'       | 'PUT'    | '/api/solutions/test-plans/cases/31/result'           | '{"failing":true,"note":"boom"}'  || '$.solutionId'    | 1
        19  | 'clear'           | 'POST'   | '/api/solutions/test-plans/1/clear'                   | null                              || '$.solutionId'    | 1
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('not_found'))
            .andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo.savePlan(*_)
        0 * repo.deletePlan(_)
        0 * repo.saveLevel(*_)
        0 * repo.deleteLevel(_)
        0 * repo.saveGroup(*_)
        0 * repo.deleteGroup(_)
        0 * repo.saveCase(*_)
        0 * repo.deleteCase(_)
        0 * repo.reorderCases(*_)
        0 * repo.reorderGroups(*_)
        0 * repo.setCheck(*_)
        0 * repo.setSkip(*_)
        0 * repo.setResult(*_)
        0 * repo.clearChecks(*_)

        where:
        seq | scenario          | who   | method   | url                                                   | body                     || expectedStatus
        1   | 'list'            | 'zed' | 'GET'    | '/api/solutions/1/test-plans'                         | null                     || 404
        2   | 'createPlan'      | 'zed' | 'POST'   | '/api/solutions/1/test-plans'                         | '{"title":"New"}'        || 404
        3   | 'plan'            | 'zed' | 'GET'    | '/api/solutions/test-plans/1'                         | null                     || 404
        4   | 'updatePlan'      | 'zed' | 'PUT'    | '/api/solutions/test-plans/1'                         | '{"title":"Renamed"}'    || 404
        5   | 'deletePlan'      | 'zed' | 'DELETE' | '/api/solutions/test-plans/1'                         | null                     || 404
        6   | 'addLevel'        | 'zed' | 'POST'   | '/api/solutions/test-plans/1/columns'                 | '{"title":"Regression"}' || 404
        7   | 'updateLevel'     | 'zed' | 'PUT'    | '/api/solutions/test-plans/columns/11'                | '{"title":"By hand"}'    || 404
        8   | 'deleteLevel'     | 'zed' | 'DELETE' | '/api/solutions/test-plans/columns/11'                | null                     || 404
        9   | 'addGroup'        | 'zed' | 'POST'   | '/api/solutions/test-plans/1/groups'                  | '{"title":"Billing"}'    || 404
        10  | 'updateGroup'     | 'zed' | 'PUT'    | '/api/solutions/test-plans/groups/21'                 | '{"title":"Sign-in"}'    || 404
        11  | 'deleteGroup'     | 'zed' | 'DELETE' | '/api/solutions/test-plans/groups/21'                 | null                     || 404
        12  | 'addCase'         | 'zed' | 'POST'   | '/api/solutions/test-plans/groups/21/cases'           | '{"text":"Pay"}'         || 404
        13  | 'updateCase'      | 'zed' | 'PUT'    | '/api/solutions/test-plans/cases/31'                  | '{"text":"x"}'           || 404
        14  | 'deleteCase'      | 'zed' | 'DELETE' | '/api/solutions/test-plans/cases/31'                  | null                     || 404
        15  | 'reorderCases'    | 'zed' | 'PUT'    | '/api/solutions/test-plans/groups/21/case-order'      | '{"ids":[31]}'           || 404
        16  | 'reorderGroups'   | 'zed' | 'PUT'    | '/api/solutions/test-plans/1/group-order'             | '{"ids":[21]}'           || 404
        17  | 'setCheck'        | 'zed' | 'PUT'    | '/api/solutions/test-plans/cases/31/checks/11'        | '{"checked":true}'       || 404
        18  | 'setResult'       | 'zed' | 'PUT'    | '/api/solutions/test-plans/cases/31/result'           | '{"failing":true}'       || 404
        19  | 'clear'           | 'zed' | 'POST'   | '/api/solutions/test-plans/1/clear'                   | null                     || 404
        20  | 'list'            | 'rex' | 'GET'    | '/api/solutions/2/test-plans'                         | null                     || 404
        21  | 'createPlan'      | 'rex' | 'POST'   | '/api/solutions/2/test-plans'                         | '{"title":"New"}'        || 404
        22  | 'plan'            | 'rex' | 'GET'    | '/api/solutions/test-plans/2'                         | null                     || 404
        23  | 'updatePlan'      | 'rex' | 'PUT'    | '/api/solutions/test-plans/2'                         | '{"title":"Renamed"}'    || 404
        24  | 'deletePlan'      | 'rex' | 'DELETE' | '/api/solutions/test-plans/2'                         | null                     || 404
        25  | 'addLevel'        | 'rex' | 'POST'   | '/api/solutions/test-plans/2/columns'                 | '{"title":"Regression"}' || 404
        26  | 'updateLevel'     | 'rex' | 'PUT'    | '/api/solutions/test-plans/columns/13'                | '{"title":"By hand"}'    || 404
        27  | 'deleteLevel'     | 'rex' | 'DELETE' | '/api/solutions/test-plans/columns/13'                | null                     || 404
        28  | 'addGroup'        | 'rex' | 'POST'   | '/api/solutions/test-plans/2/groups'                  | '{"title":"Billing"}'    || 404
        29  | 'updateGroup'     | 'rex' | 'PUT'    | '/api/solutions/test-plans/groups/22'                 | '{"title":"Sign-in"}'    || 404
        30  | 'deleteGroup'     | 'rex' | 'DELETE' | '/api/solutions/test-plans/groups/22'                 | null                     || 404
        31  | 'addCase'         | 'rex' | 'POST'   | '/api/solutions/test-plans/groups/22/cases'           | '{"text":"Pay"}'         || 404
        32  | 'updateCase'      | 'rex' | 'PUT'    | '/api/solutions/test-plans/cases/32'                  | '{"text":"x"}'           || 404
        33  | 'deleteCase'      | 'rex' | 'DELETE' | '/api/solutions/test-plans/cases/32'                  | null                     || 404
        34  | 'reorderCases'    | 'rex' | 'PUT'    | '/api/solutions/test-plans/groups/22/case-order'      | '{"ids":[32]}'           || 404
        35  | 'reorderGroups'   | 'rex' | 'PUT'    | '/api/solutions/test-plans/2/group-order'             | '{"ids":[22]}'           || 404
        36  | 'setCheck'        | 'rex' | 'PUT'    | '/api/solutions/test-plans/cases/32/checks/13'        | '{"checked":true}'       || 404
        37  | 'setResult'       | 'rex' | 'PUT'    | '/api/solutions/test-plans/cases/32/result'           | '{"failing":true}'       || 404
        38  | 'clear'           | 'rex' | 'POST'   | '/api/solutions/test-plans/2/clear'                   | null                     || 404
    }

    def "list should answer 401 when the agent key is wrong"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/1/test-plans'), 'rex', AGENT_KEY + 'x')).andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Unknown API client or wrong key.'))
    }

    def "list should answer an array of summaries narrowed to the normalized kind when a kind is sent"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/test-plans').param('kind', 'QA')))

        then:
        1 * repo.plans(1, 'qa') >> [[SolutionTestPlanID: 1, SolutionID: 1, PlanTitle: 'Smoke', PlanKindCode: 'qa', CaseCount: 3, LevelCount: 2]]
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$[0].id').value(1))
            .andExpect(jsonPath('$[0].kind').value('qa'))
            .andExpect(jsonPath('$[0].cellCount').value(6))
    }

    def "list and createPlan should answer 400 when the kind is unknown"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/test-plans').param('kind', 'prod'))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value('A checklist kind is dev or qa, not "prod".'))
        mvc.perform(asUser(request('POST', '/api/solutions/1/test-plans', '{"title":"x","kind":"uat"}'))).andExpect(status().isBadRequest())
    }

    def "plan should answer the whole checklist when an agent reads it"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/test-plans/1'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(1))
            .andExpect(jsonPath('$.title').value('Smoke'))
            .andExpect(jsonPath('$.kind').value('qa'))
            .andExpect(jsonPath('$.caseCount').value(1))
            .andExpect(jsonPath('$.cellCount').value(2))
            .andExpect(jsonPath('$.columns[0].title').value('Manual'))
            .andExpect(jsonPath('$.columns[0].checkedCount').value(1))
            .andExpect(jsonPath('$.groups[0].title').value('Login'))
            .andExpect(jsonPath('$.groups[0].checkedByColumn.11').value(1))
            .andExpect(jsonPath('$.groups[0].checkedByColumn.12').value(0))
            .andExpect(jsonPath('$.groups[0].cases[0].text').value('Sign in'))
            .andExpect(jsonPath('$.groups[0].cases[0].checks[0].checkedByCode').value('rex'))
            .andExpect(jsonPath('$.groups[0].cases[0].includeInPrint').value(true))
    }

    def "deletePlan should answer ok and the id when the checklist is deleted"() {

        expect:
        mvc.perform(asUser(delete('/api/solutions/test-plans/1'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.deletedId').value(1))
    }

    @Unroll
    def "#seq. #scenario should answer 404 with the family's message when the thing is missing"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario                 | method   | url                                               | body               || expectedMessage
        1   | 'list of solution 9'     | 'GET'    | '/api/solutions/9/test-plans'                     | null               || 'Solution not found.'
        2   | 'plan 9'                 | 'GET'    | '/api/solutions/test-plans/9'                     | null               || 'Testing checklist not found.'
        3   | 'plan 0'                 | 'GET'    | '/api/solutions/test-plans/0'                     | null               || 'Testing checklist not found.'
        4   | 'updateLevel'            | 'PUT'    | '/api/solutions/test-plans/columns/99'            | '{"title":"x"}'    || 'Column not found.'
        5   | 'deleteGroup'            | 'DELETE' | '/api/solutions/test-plans/groups/99'             | null               || 'Group not found.'
        6   | 'addCase'                | 'POST'   | '/api/solutions/test-plans/groups/99/cases'       | '{"text":"x"}'     || 'Group not found.'
        7   | 'updateCase'             | 'PUT'    | '/api/solutions/test-plans/cases/99'              | '{"text":"x"}'     || 'Test case not found.'
        8   | 'setCheck'               | 'PUT'    | '/api/solutions/test-plans/cases/31/checks/99'    | '{"checked":true}' || 'Column not found.'
        9   | 'setResult'              | 'PUT'    | '/api/solutions/test-plans/cases/99/result'       | '{"failing":true}' || 'Test case not found.'
        10  | 'clear one column'       | 'POST'   | '/api/solutions/test-plans/1/clear'               | '{"columnId":99}'  || 'Column not found.'
    }

    def "deleteCase should answer 404 when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/test-plans/cases/31')))

        then:
        1 * repo.deleteCase(31) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Test case not found.'))
    }

    @Unroll
    def "#seq. #scenario should answer 400 with what is missing when a required field is absent"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario                     | method | url                                               | body                              || expectedMessage
        1   | 'createPlan without title'   | 'POST' | '/api/solutions/1/test-plans'                     | '{"description":"d"}'             || 'A title is required.'
        2   | 'createPlan blank title'     | 'POST' | '/api/solutions/1/test-plans'                     | '{"title":"  "}'                  || 'A title is required.'
        3   | 'addLevel without title'     | 'POST' | '/api/solutions/test-plans/1/columns'             | '{}'                              || 'A column name is required.'
        4   | 'addGroup empty title'       | 'POST' | '/api/solutions/test-plans/1/groups'              | '{"title":""}'                    || 'A group name is required.'
        5   | 'addCase without text'       | 'POST' | '/api/solutions/test-plans/groups/21/cases'       | '{"note":"n"}'                    || 'Some text is required.'
        6   | 'reorderCases without ids'   | 'PUT'  | '/api/solutions/test-plans/groups/21/case-order'  | '{}'                              || 'ids (the new order) is required.'
        7   | 'reorderGroups null ids'     | 'PUT'  | '/api/solutions/test-plans/1/group-order'         | '{"ids":null}'                    || 'ids (the new order) is required.'
        8   | 'setCheck with neither'      | 'PUT'  | '/api/solutions/test-plans/cases/31/checks/11'    | '{}'                              || 'Send checked (true or false) or skipped (true or false) -- one of the two.'
        9   | 'setCheck with both'         | 'PUT'  | '/api/solutions/test-plans/cases/31/checks/11'    | '{"checked":true,"skipped":true}' || 'Send checked (true or false) or skipped (true or false) -- one of the two.'
        10  | 'setResult with neither'     | 'PUT'  | '/api/solutions/test-plans/cases/31/result'       | '{}'                              || 'failing (true or false) or note is required.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 and write nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request(method, url, null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo.savePlan(*_)
        0 * repo.saveLevel(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveCase(*_)
        0 * repo.reorderCases(*_)
        0 * repo.reorderGroups(*_)
        0 * repo.setCheck(*_)
        0 * repo.setSkip(*_)
        0 * repo.setResult(*_)

        where:
        seq | scenario          | method | url                                                   | body    || expectedStatus
        1   | 'createPlan'      | 'POST' | '/api/solutions/1/test-plans'                         | ''      || 400
        2   | 'updatePlan'      | 'PUT'  | '/api/solutions/test-plans/1'                         | 'null'  || 400
        3   | 'addLevel'        | 'POST' | '/api/solutions/test-plans/1/columns'                 | '{"x":' || 400
        4   | 'updateLevel'     | 'PUT'  | '/api/solutions/test-plans/columns/11'                | '7'     || 400
        5   | 'addGroup'        | 'POST' | '/api/solutions/test-plans/1/groups'                  | ''      || 400
        6   | 'updateGroup'     | 'PUT'  | '/api/solutions/test-plans/groups/21'                 | 'null'  || 400
        7   | 'addCase'         | 'POST' | '/api/solutions/test-plans/groups/21/cases'           | '{"x":' || 400
        8   | 'updateCase'      | 'PUT'  | '/api/solutions/test-plans/cases/31'                  | '7'     || 400
        9   | 'reorderCases'    | 'PUT'  | '/api/solutions/test-plans/groups/21/case-order'      | ''      || 400
        10  | 'reorderGroups'   | 'PUT'  | '/api/solutions/test-plans/1/group-order'             | 'null'  || 400
        11  | 'setCheck'        | 'PUT'  | '/api/solutions/test-plans/cases/31/checks/11'        | '{"x":' || 400
        12  | 'setResult'       | 'PUT'  | '/api/solutions/test-plans/cases/31/result'           | '7'     || 400
    }

    @Unroll
    def "#scenario should answer 400 when a field has the wrong JSON type"() {

        expect:
        mvc.perform(asUser(request('PUT', url, body))).andExpect(status().is(expectedStatus))

        where:
        scenario                      | url                                              | body                       || expectedStatus
        'checked as a phrase'         | '/api/solutions/test-plans/cases/31/checks/11'   | '{"checked":"yes please"}' || 400
        'ids as words'                | '/api/solutions/test-plans/1/group-order'        | '{"ids":["a","b"]}'        || 400
        'includeInPrint as an object' | '/api/solutions/test-plans/cases/31'             | '{"includeInPrint":{}}'    || 400
    }

    def "clear should accept no body, an empty body, or one column"() {

        when:
        mvc.perform(asUser(post('/api/solutions/test-plans/1/clear'))).andExpect(status().isOk())
        mvc.perform(asUser(request('POST', '/api/solutions/test-plans/1/clear', '{}'))).andExpect(status().isOk())
        mvc.perform(asUser(request('POST', '/api/solutions/test-plans/1/clear', '{"columnId":12}'))).andExpect(status().isOk())

        then:
        2 * repo.clearChecks(1, null)
        1 * repo.clearChecks(1, 12)
    }

    @Unroll
    def "#scenario should answer 409 when the column belongs to another checklist"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario        | method | url                                              | body                || expectedMessage
        'setCheck'      | 'PUT'  | '/api/solutions/test-plans/cases/31/checks/14'   | '{"checked":true}'  || 'That column belongs to a different checklist.'
        'clear'         | 'POST' | '/api/solutions/test-plans/1/clear'              | '{"columnId":14}'   || 'That column belongs to a different checklist.'
    }

    def "updateCase should answer 409 when the group is of another checklist"() {

        expect:
        mvc.perform(asUser(request('PUT', '/api/solutions/test-plans/cases/31', '{"groupId":24}'))).andExpect(status().isConflict())
            .andExpect(jsonPath('$.message').value('A test case can only move to a group in the same checklist.'))
    }

    def "setCheck and setResult should attribute the write to the caller when an agent, Stewart or a person calls"() {

        when:
        mvc.perform(asAgent(request('PUT', '/api/solutions/test-plans/cases/31/checks/12', '{"skipped":true}'))).andExpect(status().isOk())
        mvc.perform(asActor(request('PUT', '/api/solutions/test-plans/cases/31/checks/11', '{"checked":false}'), 'stewart')).andExpect(status().isOk())
        mvc.perform(asUser(request('PUT', '/api/solutions/test-plans/cases/31/result', '{"note":""}'))).andExpect(status().isOk())

        then:
        1 * repo.setSkip(31, 12, true, 'rex')
        1 * repo.setCheck(31, 11, false, 'stewart')
        1 * repo.setResult(31, null, '', 'tony')
    }

    def "updateCase should pass includeInPrint through when it is sent"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/test-plans/cases/31', '{"includeInPrint":false,"sortOrder":3}'))).andExpect(status().isOk())

        then:
        1 * repo.saveCase(31, 21, 'Sign in', 'n', 3, false)
    }

    def "deleteLevel should answer 409 verbatim when the repository refuses a rule"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/test-plans/columns/11')))

        then:
        1 * repo.deleteLevel(11) >> { throw new SolutionRuleException('A checklist keeps at least one column. Nothing was deleted.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('A checklist keeps at least one column. Nothing was deleted.'))
    }
}
