package com.echo

import java.time.LocalDateTime

import jakarta.servlet.http.HttpServletRequest
import org.hamcrest.Matchers
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.SolutionDrafts.ProjectDraft
import com.echo.SolutionDrafts.SolutionDraft
import static com.echo.SolutionFixtures.project
import static com.echo.SolutionFixtures.solution

import spock.lang.PendingFeature
import spock.lang.Unroll

// Story 5653: /api/solutions -- whoami, lookups, solutions, projects, members: every endpoint, every caller.
@WebMvcTest(SolutionController)
class SolutionControllerSpec extends SolutionWebSupport {

    // ---- who may call at all ------------------------------------------------------------

    @Unroll
    def "#seq. #method #path should answer 401 when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = json(request(method, path), '{}')

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('Not signed in'))

        where:
        seq | scenario                        | method   | path                           || expectedStatus
        1   | "whoami has no credential"      | "GET"    | "/api/solutions/whoami"        || 401
        2   | "lookups has no credential"     | "GET"    | "/api/solutions/lookups"       || 401
        3   | "list has no credential"        | "GET"    | "/api/solutions"               || 401
        4   | "byAlias has no credential"     | "GET"    | "/api/solutions/by-alias/claims" || 401
        5   | "get has no credential"         | "GET"    | "/api/solutions/1"             || 401
        6   | "create has no credential"      | "POST"   | "/api/solutions"               || 401
        7   | "update has no credential"      | "PUT"    | "/api/solutions/1"             || 401
        8   | "delete has no credential"      | "DELETE" | "/api/solutions/1"             || 401
        9   | "projects has no credential"    | "GET"    | "/api/solutions/1/projects"    || 401
        10  | "createProject has no credential" | "POST" | "/api/solutions/1/projects"    || 401
        11  | "project has no credential"     | "GET"    | "/api/solutions/projects/10"   || 401
        12  | "updateProject has no credential" | "PUT"  | "/api/solutions/projects/10"   || 401
        13  | "deleteProject has no credential" | "DELETE" | "/api/solutions/projects/10" || 401
        14  | "saveMember has no credential"  | "POST"   | "/api/solutions/members"       || 401
    }

    def "list should answer 401 and never reach storage when an agent sends a wrong key"() {

        when:
        ResultActions r = mvc.perform(get('/api/solutions').header(ApiClientAuthFilter.HEADER_CLIENT, 'rex')
            .header(ApiClientAuthFilter.HEADER_KEY, 'wrong'))

        then:
        r.andExpect(status().isUnauthorized()).andExpect(jsonPath('$.error').value('Unknown API client or wrong key.'))
        0 * repo.listSolutions(*_)
    }

    @Unroll
    def "whoAmI should answer 401 when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = by(houseCredential, asAgent(get('/api/solutions/whoami')))

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))
            .andExpect(jsonPath('$.error').value('An API client may not present any other credential.'))

        where:
        scenario                                   | houseCredential || expectedStatus
        "an agent also sends the service key"      | "service"       || 401
        "an agent also sends a person's token"     | "user"          || 401
    }

    def "whoAmI should answer 403 when the agent's allowlist does not reach /api/solutions/"() {

        expect:
        mvc.perform(asScopedElsewhere(get('/api/solutions/whoami'))).andExpect(status().isForbidden())
            .andExpect(jsonPath('$.error').value('This API client may not reach that path.'))
    }

    // ---- whoami / lookups ---------------------------------------------------------------

    def "whoAmI should answer user tony, unscoped, when a person signs in under any login name"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/whoami'), 'a', 3)).andExpect(status().isOk())
            .andExpect(jsonPath('$.kind').value('user'))
            .andExpect(jsonPath('$.memberCode').value('tony'))
            .andExpect(jsonPath('$.scoped').value(false))
    }

    @Unroll
    def "whoAmI should answer service '#expectedCode' when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = asService(get('/api/solutions/whoami'))
        String sentActor = repeat > 0 ? getReplicatedString(actor, repeat) : actor
        if (sentActor != null) {
            req.header('X-Echo-Actor', sentActor)
        }

        expect:
        mvc.perform(req).andExpect(status().isOk())
            .andExpect(jsonPath('$.kind').value('service'))
            .andExpect(jsonPath('$.memberCode').value(expectedCode))
            .andExpect(jsonPath('$.scoped').value(false))

        where:
        scenario                         | actor        | repeat || expectedCode
        "no actor is named"              | null         | 0      || "felix"
        "the actor is capitalized"       | "Stewart"    | 0      || "stewart"
        "the actor is padded"            | " rex "      | 0      || "rex"
        "the actor has bad characters"   | "bad actor!" | 0      || "felix"
        "the actor is 21 characters"     | "x"          | 21     || "felix"
    }

    def "whoAmI should answer client rex, scoped, from its credential, never from X-Echo-Actor, when the agent asks"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/whoami')).header('X-Echo-Actor', 'tony')).andExpect(status().isOk())
            .andExpect(jsonPath('$.kind').value('client'))
            .andExpect(jsonPath('$.memberCode').value('rex'))
            .andExpect(jsonPath('$.scoped').value(true))
    }

    @Unroll
    def "lookups should answer the four lists when #scenario"() {

        expect:
        mvc.perform(by(who, get('/api/solutions/lookups'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.statuses[0].code').value('todo'))
            .andExpect(jsonPath('$.statuses.length()').value(expectedStatuses))
            .andExpect(jsonPath('$.members[1].code').value('felix'))
            .andExpect(jsonPath('$.logTypes[1].code').value('command'))
            .andExpect(jsonPath('$.noteTypes[0].code').value('runbook'))

        where:
        scenario             | who       || expectedStatuses
        "a person asks"      | "user"    || 5
        "the house asks"     | "service" || 5
        "the agent asks"     | "agent"   || 5
    }

    // ---- list / get / by-alias ----------------------------------------------------------

    def "list should show a person everything, archived only when asked"() {

        when:
        ResultActions r1 = mvc.perform(asUser(get('/api/solutions')))
        ResultActions r2 = mvc.perform(asUser(get('/api/solutions').param('includeArchived', 'true')))

        then:
        1 * repo.listSolutions(false, null) >> [solution(id: 1, name: 'Alpha')]
        1 * repo.listSolutions(true, null) >> []
        r1.andExpect(status().isOk()).andExpect(jsonPath('$[0].name').value('Alpha')).andExpect(jsonPath('$[0].id').value(1))
        r2.andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(0))
    }

    def "list should ask for the agent's own solutions only when the agent asks"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions').param('includeArchived', 'false')))

        then:
        1 * repo.listSolutions(false, 7) >> [solution(id: 1)]
        r.andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(1))
    }

    def "list should answer 400 when includeArchived is not a boolean"() {

        expect:
        mvc.perform(asUser(get('/api/solutions').param('includeArchived', 'maybe'))).andExpect(status().isBadRequest())
    }

    @Unroll
    def "get should answer the record's fields when #scenario"() {

        given:
        solutionExists(4, true, [name: 'Claims', ownerCode: 'tony', targetDate: getLocalDateTime('10/01/2026 09:30'),
                                 alias: 'claims', archived: true])

        expect:
        mvc.perform(by(who, get('/api/solutions/4'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(4))
            .andExpect(jsonPath('$.name').value('Claims'))
            .andExpect(jsonPath('$.alias').value('claims'))
            .andExpect(jsonPath('$.archived').value(true))
            .andExpect(jsonPath('$.targetDate').value(expectedDate))

        where:
        scenario                          | who       || expectedDate
        "a person asks"                   | "user"    || "2026-10-01T09:30:00"
        "the house asks"                  | "service" || "2026-10-01T09:30:00"
        "the agent asks for a shared one" | "agent"   || "2026-10-01T09:30:00"
    }

    def "get should answer the same 404 when the solution is missing or not shared with the agent"() {

        given:
        solutionExists(4, false)

        expect:
        mvc.perform(asUser(get('/api/solutions/9'))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('not_found'))
            .andExpect(jsonPath('$.message').value('Solution not found.'))
        mvc.perform(asAgent(get('/api/solutions/4'))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.error').value('not_found'))
            .andExpect(jsonPath('$.message').value('Solution not found.'))
        mvc.perform(asUser(get('/api/solutions/0'))).andExpect(status().isNotFound())
    }

    @Unroll
    def "get should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(asUser(get(path))).andExpect(status().is(expectedStatus))

        where:
        scenario                         | path                          || expectedStatus
        "the id is too large for an int" | "/api/solutions/99999999999" || 400
        "the id is not a number"         | "/api/solutions/abc"         || 404
    }

    def "byAlias should find the solution when a person or a shared agent asks"() {

        given:
        solutionExists(5, true, [alias: 'claims'])
        repo.listSolutions(true, null) >> [solution(id: 5, alias: 'claims')]
        repo.listSolutions(true, 7) >> [solution(id: 5, alias: 'claims')]

        expect:
        mvc.perform(asUser(get('/api/solutions/by-alias/Claims'))).andExpect(status().isOk()).andExpect(jsonPath('$.id').value(5))
        mvc.perform(asAgent(get('/api/solutions/by-alias/claims'))).andExpect(status().isOk()).andExpect(jsonPath('$.id').value(5))
    }

    def "byAlias should answer 404 when the alias is unknown or has characters outside the pattern"() {

        given:
        repo.listSolutions(true, _) >> []

        expect:
        mvc.perform(asUser(get('/api/solutions/by-alias/nope'))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value('Solution not found.'))
        mvc.perform(asAgent(get('/api/solutions/by-alias/claims'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/by-alias/a_b'))).andExpect(status().isNotFound())
    }

    // ---- create -------------------------------------------------------------------------

    def "create should pass every request field into the draft when a person creates a solution"() {

        given:
        LocalDateTime target = getLocalDateTime('10/01/2026 00:00')
        Map<String, Object> body = [name: ' Claims ', description: 'd', statusCode: 'todo', ownerCode: 'felix', statusSummary: 's',
                                    targetDate: '2026-10-01', archived: false, sortOrder: 2, alias: 'Claims']

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions')), body))

        then:
        1 * repo.saveSolution(new SolutionDraft(0, 'Claims', 'd', 'todo', 'felix', 's', target, false, 2, 'claims'), 'tony') >> 12
        1 * repo.findSolution(12) >> solution(id: 12, name: 'Claims')
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(12))
    }

    def "create should save as the actor when the house creates a solution"() {

        when:
        ResultActions r = mvc.perform(json(asService(post('/api/solutions')).header('X-Echo-Actor', 'stewart'), [name: 'S']))

        then:
        1 * repo.saveSolution(_, 'stewart') >> 3
        1 * repo.findSolution(3) >> solution(id: 3)
        r.andExpect(status().isOk())
    }

    @Unroll
    def "create should answer 400 '#expectedMessage' when #scenario"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions')), body)).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                   | body                                  || expectedMessage
        "the body is empty"        | "{}"                                  || "A name is required."
        "the name is blank"        | '{"name": "   "}'                     || "A name is required."
        "the target date is a word" | '{"name": "x", "targetDate": "soon"}' || "The target date must look like 2026-09-17 or 2026-09-17T14:30."
    }

    @Unroll
    def "create should answer 400 when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = body == null ? asUser(post('/api/solutions')) : json(asUser(post('/api/solutions')), body)

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))

        where:
        scenario                          | body                                   || expectedStatus
        "there is no body at all"         | null                                   || 400
        "the JSON is broken"              | '{"name": '                            || 400
        "a number field holds a word"     | '{"name": "x", "sortOrder": "many"}'  || 400
    }

    @Unroll
    def "create should answer 409 with the rule's sentence when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions')), body))

        then:
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(Matchers.startsWith(expectedStart)))
        0 * repo.saveSolution(*_)

        where:
        scenario                | body                                         || expectedStart
        "the status is unknown" | '{"name": "x", "statusCode": "finished"}'    || "There is no status code 'finished'."
        "the owner is unknown"  | '{"name": "x", "ownerCode": "bob"}'          || "There is no member code 'bob'."
        "the alias is digits"   | '{"name": "x", "alias": "123"}'              || "An alias is up to 40 letters"
    }

    def "create should answer 409 with the proc's sentence when storage refuses a duplicate name"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions')), [name: 'Dup']))

        then:
        1 * repo.saveSolution(*_) >> { throw new SolutionRuleException('A solution by that name already exists.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('A solution by that name already exists.'))
    }

    @PendingFeature(reason = 'BUG: no handler maps SaveRefusedException in Echo -- its javadoc (and api-spec.md "409 ... message is written for you") promise a 409 from GlobalExceptionHandler, which Echo does not have; the exception escapes the controller as a 500')
    def "create should answer 409 with its sentence when a proc refuses the save with SaveRefusedException"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions')), [name: 'Dup']))

        then:
        1 * repo.saveSolution(*_) >> { throw new SaveRefusedException('Already there. Nothing was saved.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('Already there. Nothing was saved.'))
    }

    def "create should answer 403 forbidden when an agent tries to create a solution"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions')), [name: 'Mine']))

        then:
        r.andExpect(status().isForbidden()).andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('forbidden'))
            .andExpect(jsonPath('$.message').value('Only a person may create a solution.'))
        0 * repo.saveSolution(*_)
    }

    // ---- update -------------------------------------------------------------------------

    @Unroll
    def "update should send only what changed, keeping the name, when #scenario"() {

        given:
        solutionExists(4, true, [name: 'Old', ownerCode: 'tony', statusCode: 'todo'])

        when:
        ResultActions r = mvc.perform(json(by(who, put('/api/solutions/4')), [statusCode: 'inprogress']))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.id() == 4 && d.name() == 'Old' && d.statusCode() == 'inprogress' }, expectedAuthor) >> 4
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(4))

        where:
        scenario                 | who     || expectedAuthor
        "a person updates"       | "user"  || "tony"
        "the agent updates"      | "agent" || "rex"
    }

    def "update should answer 404 when the agent was not given the solution or the solution is missing"() {

        given:
        solutionExists(4, false)

        expect:
        mvc.perform(json(asAgent(put('/api/solutions/4')), [name: 'x'])).andExpect(status().isNotFound())
        mvc.perform(json(asUser(put('/api/solutions/9')), [name: 'x'])).andExpect(status().isNotFound())
    }

    @Unroll
    def "update should answer 400 before anything is looked up when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(asUser(put('/api/solutions/4')), body))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo.findSolution(_)

        where:
        scenario                                  | body                             || expectedStatus
        "the target date does not exist"          | '{"targetDate": "2026-02-30"}'   || 400
        "the target date is Feb 29 in 2026"       | '{"targetDate": "2026-02-29"}'   || 400
    }

    // ---- delete -------------------------------------------------------------------------

    def "delete should answer ok and the id when a person deletes a solution"() {

        given:
        solutionExists(4)

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/4')))

        then:
        1 * repo.deleteSolution(4) >> true
        r.andExpect(status().isOk()).andExpect(jsonPath('$.ok').value(true)).andExpect(jsonPath('$.deletedId').value(4))
    }

    @Unroll
    def "delete should answer #expectedStatus and delete nothing when #scenario"() {

        given:
        solutionExists(4, true)
        solutionExists(5, false)

        when:
        ResultActions r = mvc.perform(by(who, delete(path)))

        then:
        0 * repo.deleteSolution(_)
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                                   | who     | path                || expectedStatus
        "the agent deletes a solution it was given" | "agent" | "/api/solutions/4" || 403
        "the agent deletes one it was not given"   | "agent" | "/api/solutions/5"  || 404
        "a person deletes a missing one"           | "user"  | "/api/solutions/9"  || 404
    }

    def "delete should name the owner's rule when the agent is refused"() {

        given:
        solutionExists(4, true)

        expect:
        mvc.perform(asAgent(delete('/api/solutions/4'))).andExpect(jsonPath('$.message').value('Only a person may delete a solution.'))
    }

    // ---- projects -----------------------------------------------------------------------

    @Unroll
    def "projects should answer #expectedStatus when #scenario"() {

        given:
        solutionExists(4, true)
        solutionExists(5, false)
        repo.listProjects(4) >> [project(id: 10, solutionId: 4, name: 'Deploy')]

        expect:
        mvc.perform(by(who, get(path))).andExpect(status().is(expectedStatus))

        where:
        scenario                               | who     | path                         || expectedStatus
        "a person lists them"                  | "user"  | "/api/solutions/4/projects"  || 200
        "the agent lists a shared solution's"  | "agent" | "/api/solutions/4/projects"  || 200
        "the agent lists an unshared one's"    | "agent" | "/api/solutions/5/projects"  || 404
    }

    def "projects should carry each project's name when listed"() {

        given:
        solutionExists(4, true)
        repo.listProjects(4) >> [project(id: 10, solutionId: 4, name: 'Deploy')]

        expect:
        mvc.perform(asUser(get('/api/solutions/4/projects'))).andExpect(jsonPath('$[0].name').value('Deploy'))
    }

    def "createProject should take the solution id from the path when the agent adds a project"() {

        given:
        solutionExists(4, true)

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/4/projects')), [name: 'Deploy', statusCode: 'todo', sortOrder: 3]))

        then:
        1 * repo.saveProject(new ProjectDraft(0, 4, 'Deploy', null, 'todo', null, 3), 'rex') >> 10
        1 * repo.findProject(10) >> project(id: 10, solutionId: 4)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(10)).andExpect(jsonPath('$.solutionId').value(4))
    }

    @Unroll
    def "createProject should answer #expectedStatus when #scenario"() {

        given:
        solutionExists(4, true)

        expect:
        mvc.perform(json(asUser(post('/api/solutions/4/projects')), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                | body                                      || expectedStatus
        "the body is empty"     | "{}"                                      || 400
        "the name is blank"     | '{"name": " "}'                           || 400
        "the status is unknown" | '{"name": "P", "statusCode": "zzz"}'      || 409
        "the owner is unknown"  | '{"name": "P", "ownerCode": "nobody"}'    || 409
    }

    def "createProject should answer 404 when the agent was not given the solution"() {

        given:
        solutionExists(5, false)

        expect:
        mvc.perform(json(asAgent(post('/api/solutions/5/projects')), [name: 'P'])).andExpect(status().isNotFound())
    }

    def "project should answer the full description, and 404 when missing or hidden"() {

        given:
        solutionExists(4, true)
        solutionExists(5, false)
        repo.findProject(10) >> project(id: 10, solutionId: 4, description: 'full text')
        repo.findProject(20) >> project(id: 20, solutionId: 5)

        expect:
        mvc.perform(asAgent(get('/api/solutions/projects/10'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.description').value('full text'))
        mvc.perform(asUser(get('/api/solutions/projects/11'))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value('Project not found.'))
        mvc.perform(asAgent(get('/api/solutions/projects/20'))).andExpect(status().isNotFound())
    }

    def "updateProject should send only what changed, keeping the name, when the agent reorders a project"() {

        given:
        solutionExists(4, true)
        repo.findProject(10) >> project(id: 10, solutionId: 4, name: 'Deploy', ownerCode: 'tony', sortOrder: 2)

        when:
        ResultActions r = mvc.perform(json(asAgent(put('/api/solutions/projects/10')), [sortOrder: 1]))

        then:
        1 * repo.saveProject(new ProjectDraft(10, 4, 'Deploy', null, 'todo', 'tony', 1), 'rex') >> 10
        r.andExpect(status().isOk())
    }

    def "deleteProject should delete for a person and an agent inside its solution and answer 404 when hidden"() {

        given:
        solutionExists(4, true)
        solutionExists(5, false)
        repo.findProject(10) >> project(id: 10, solutionId: 4)
        repo.findProject(20) >> project(id: 20, solutionId: 5)

        when:
        ResultActions a = mvc.perform(asUser(delete('/api/solutions/projects/10')))
        ResultActions b = mvc.perform(asAgent(delete('/api/solutions/projects/10')))
        ResultActions c = mvc.perform(asAgent(delete('/api/solutions/projects/20')))

        then:
        2 * repo.deleteProject(10) >> true
        0 * repo.deleteProject(20)
        a.andExpect(status().isOk()).andExpect(jsonPath('$.deletedId').value(10))
        b.andExpect(status().isOk())
        c.andExpect(status().isNotFound())
    }

    // ---- members ------------------------------------------------------------------------

    def "saveMember should default active to true, agent to false and ids to 0, trimming codes, when a person saves a member"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/members')), [code: ' bob ', name: ' Bob ']))

        then:
        1 * repo.saveMember(0, 'bob', 'Bob', false, true, 0) >> 9
        r.andExpect(status().isOk()).andExpect(jsonPath('$.members.length()').value(3))
    }

    def "saveMember should pass every flag when the house saves an inactive agent with an id and order"() {

        when:
        mvc.perform(json(asService(post('/api/solutions/members')), [id: 3, code: 'old', name: 'Old', agent: true, active: false, sortOrder: 5]))
            .andExpect(status().isOk())

        then:
        1 * repo.saveMember(3, 'old', 'Old', true, false, 5)
    }

    @Unroll
    def "saveMember should answer 400 '#expectedMessage' when #scenario"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/members')), body)).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                | body                             || expectedMessage
        "the body is empty"     | "{}"                             || "A code is required."
        "the name is missing"   | '{"code": "x"}'                  || "A name is required."
        "the code is blank"     | '{"code": " ", "name": "x"}'     || "A code is required."
    }

    def "saveMember should answer 403 when an agent tries to manage members"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/members')), [code: 'rex2', name: 'Rex Two']))

        then:
        r.andExpect(status().isForbidden()).andExpect(jsonPath('$.message').value('Only a person may manage members.'))
        0 * repo.saveMember(*_)
    }

    // ---- the null-body guards (unreachable over HTTP: Spring answers 400 first) ------------

    def "the mapping methods should refuse a null body themselves when called directly"() {

        given:
        SolutionController controller = new SolutionController(Mock(SolutionService), Mock(SolutionCallerResolver))
        HttpServletRequest req = Mock()

        when:
        controller.create(req, null)

        then:
        IllegalArgumentException e1 = thrown()
        e1.message == 'A solution is required.'

        when:
        controller.update(req, 1, null)

        then:
        thrown(IllegalArgumentException)

        when:
        controller.createProject(req, 1, null)

        then:
        IllegalArgumentException e2 = thrown()
        e2.message == 'A project is required.'

        when:
        controller.updateProject(req, 1, null)

        then:
        thrown(IllegalArgumentException)

        when:
        controller.saveMember(req, null)

        then:
        IllegalArgumentException e3 = thrown()
        e3.message == 'A member is required.'
    }
}
