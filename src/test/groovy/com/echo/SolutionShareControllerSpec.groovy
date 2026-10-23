package com.echo

import jakarta.servlet.http.HttpServletRequest
import org.hamcrest.Matchers
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

import static com.echo.SolutionFixtures.apiClient
import static com.echo.SolutionFixtures.link

import spock.lang.Unroll

// Story 5653: /api/solutions shares and file storage -- the owner's alone: 404 first, then 403, to an agent.
@WebMvcTest(SolutionShareController)
@Import(SolutionShareService)
class SolutionShareControllerSpec extends SolutionWebSupport {

    def setup() {
        solutionExists(1, true, [name: 'Alpha', alias: 'claims', saveFilesToRepo: false])
        solutionExists(2, false)
        apiClients.list() >> [apiClient(id: 7, clientCode: 'rex', clientName: 'Rex'), apiClient(id: 8, clientCode: 'ida', clientName: 'Ida')]
        apiClients.find(7) >> apiClient(id: 7, clientCode: 'rex', clientName: 'Rex', keyHint: 'wxyz')
        apiClients.find(8) >> apiClient(id: 8, clientCode: 'ida', clientName: 'Ida')
        apiClients.clientsForSolution(1) >> [link(7, 1)]
    }

    @Unroll
    def "#method #path should answer #expectedStatus when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = json(request(method, path), '{}')

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))

        where:
        scenario                        | method   | path                                   || expectedStatus
        "list has no credential"        | "GET"    | "/api/solutions/1/shares"              || 401
        "share has no credential"       | "POST"   | "/api/solutions/1/shares"              || 401
        "unshare has no credential"     | "DELETE" | "/api/solutions/1/shares/7"            || 401
        "brief has no credential"       | "GET"    | "/api/solutions/1/shares/7/brief"      || 401
        "fileStorage has no credential" | "PUT"    | "/api/solutions/1/file-storage"        || 401
    }

    // ---- list ---------------------------------------------------------------------------

    @Unroll
    def "list should show every client marked shared or not when #scenario"() {

        expect:
        mvc.perform(by(who, get('/api/solutions/1/shares'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(expectedCount))
            .andExpect(jsonPath('$[0].apiClientId').value(7))
            .andExpect(jsonPath('$[0].shared').value(true))
            .andExpect(jsonPath('$[1].shared').value(false))
            .andExpect(jsonPath('$[0].hasKey').value(true))
            .andExpect(jsonPath('$[0].keyHash').doesNotExist())

        where:
        scenario            | who       || expectedCount
        "a person asks"     | "user"    || 2
        "the house asks"    | "service" || 2
    }

    @Unroll
    def "list should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(by(who, get(path))).andExpect(status().is(expectedStatus))

        where:
        scenario                                  | who     | path                        || expectedStatus
        "the agent asks about its own solution"   | "agent" | "/api/solutions/1/shares"   || 403
        "the agent asks about another solution"   | "agent" | "/api/solutions/2/shares"   || 404
        "a person asks about a missing solution"  | "user"  | "/api/solutions/9/shares"   || 404
    }

    def "list should name the owner's rule when the agent asks about its own solution"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/1/shares')))
            .andExpect(jsonPath('$.message').value('Only the owner can see who a solution is shared with.'))
    }

    // ---- share / unshare ----------------------------------------------------------------

    def "share should link the client and answer shares, filesMovedOffRepo and the solution when a person shares"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/1/shares')), [apiClientId: 8]))

        then:
        1 * apiClients.setSolutionLink(8, 1, true) >> true
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.shares').isArray())
            .andExpect(jsonPath('$.filesMovedOffRepo').value(false))
            .andExpect(jsonPath('$.solution.id').value(1))
    }

    @Unroll
    def "share should answer 400 and link nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/1/shares')), body))

        then:
        0 * apiClients.setSolutionLink(*_)
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                      | body                        || expectedStatus
        "the client id is missing"    | "{}"                        || 400
        "the client id is zero"       | '{"apiClientId": 0}'        || 400
        "the client id is negative"   | '{"apiClientId": -3}'       || 400
        "the client id is a word"     | '{"apiClientId": "rex"}'    || 400
    }

    def "share should answer 404 'API client not found.' when the client does not exist"() {

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/1/shares')), [apiClientId: 99]))

        then:
        0 * apiClients.setSolutionLink(*_)
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('API client not found.'))
    }

    def "unshare should remove the link when the house un-shares"() {

        when:
        ResultActions r = mvc.perform(asService(delete('/api/solutions/1/shares/7')))

        then:
        1 * apiClients.setSolutionLink(7, 1, false) >> true
        r.andExpect(status().isOk()).andExpect(jsonPath('$.filesMovedOffRepo').value(false))
    }

    @Unroll
    def "share and unshare should answer #expectedStatus and link nothing when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = json(asAgent(request(method, path)), '{"apiClientId": 7}')

        when:
        ResultActions r = mvc.perform(req)

        then:
        0 * apiClients.setSolutionLink(*_)
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                                   | method   | path                         || expectedStatus
        "the agent shares its own solution"        | "POST"   | "/api/solutions/1/shares"    || 403
        "the agent un-shares its own solution"     | "DELETE" | "/api/solutions/1/shares/7"  || 403
        "the agent shares another solution"        | "POST"   | "/api/solutions/2/shares"    || 404
        "the agent un-shares another solution"     | "DELETE" | "/api/solutions/2/shares/7"  || 404
    }

    def "share should name the owner's rule when the agent shares its own solution"() {

        expect:
        mvc.perform(json(asAgent(post('/api/solutions/1/shares')), [apiClientId: 7]))
            .andExpect(jsonPath('$.message').value('Only the owner can share a solution.'))
    }

    // ---- brief --------------------------------------------------------------------------

    def "brief should carry no key, only the slot, when a person asks for a shared client's brief"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/shares/7/brief'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.apiClientId').value(7))
            .andExpect(jsonPath('$.clientName').value('Rex'))
            .andExpect(jsonPath('$.keyHint').value('wxyz'))
            .andExpect(jsonPath('$.keySlot').value('<PASTE-THE-KEY-HERE>'))
            .andExpect(jsonPath('$.text').value(Matchers.containsString('X-Echo-Client: rex')))
            .andExpect(jsonPath('$.text').value(Matchers.containsString('http://localhost:8770/api/solutions/spec')))
    }

    @Unroll
    def "brief should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(by(who, get(path))).andExpect(status().is(expectedStatus))

        where:
        scenario                                    | who     | path                                 || expectedStatus
        "the client is not shared"                  | "user"  | "/api/solutions/1/shares/8/brief"    || 404
        "the client does not exist"                 | "user"  | "/api/solutions/1/shares/99/brief"   || 404
        "the agent asks inside its own solution"    | "agent" | "/api/solutions/1/shares/7/brief"    || 403
        "the agent asks about another solution"     | "agent" | "/api/solutions/2/shares/7/brief"    || 404
    }

    // ---- file storage -------------------------------------------------------------------

    def "fileStorage should save the owner's choice and answer the solution when a person decides"() {

        when:
        ResultActions r = mvc.perform(json(asUser(put('/api/solutions/1/file-storage')), [saveToRepo: true]))

        then:
        1 * repo.setSaveFilesToRepo(1, true, 'tony') >> true
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(1))
    }

    def "fileStorage should answer the rule's 409 when the solution is shared"() {

        when:
        ResultActions r = mvc.perform(json(asUser(put('/api/solutions/1/file-storage')), [saveToRepo: true]))

        then:
        1 * repo.setSaveFilesToRepo(1, true, 'tony') >> { throw new SolutionRuleException('A shared solution keeps its files off the repository.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
    }

    @Unroll
    def "fileStorage should answer 400 when #scenario"() {

        expect:
        mvc.perform(json(asUser(put('/api/solutions/1/file-storage')), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                 | body                          || expectedStatus
        "the flag is missing"    | "{}"                          || 400
        "the flag is null"       | '{"saveToRepo": null}'        || 400
        "the flag is a word"     | '{"saveToRepo": "perhaps"}'   || 400
    }

    @Unroll
    def "fileStorage should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(asAgent(put(path)), '{"saveToRepo": false}')).andExpect(status().is(expectedStatus))

        where:
        scenario                                  | path                             || expectedStatus
        "the agent decides for its own solution"  | "/api/solutions/1/file-storage"  || 403
        "the agent decides for another solution"  | "/api/solutions/2/file-storage"  || 404
    }

    def "fileStorage should name the owner's rule for the agent and answer 404 when storage refuses"() {

        expect:
        mvc.perform(json(asAgent(put('/api/solutions/1/file-storage')), [saveToRepo: false]))
            .andExpect(jsonPath('$.message').value("Only the owner decides where a solution's files are saved."))

        when:
        ResultActions r = mvc.perform(json(asUser(put('/api/solutions/1/file-storage')), [saveToRepo: false]))

        then:
        1 * repo.setSaveFilesToRepo(1, false, 'tony') >> false
        r.andExpect(status().isNotFound())
    }

    def "the mapping methods should refuse a null body themselves when called directly"() {

        given:
        SolutionShareController c = new SolutionShareController(Mock(SolutionShareService), Mock(SolutionService), Mock(SolutionCallerResolver))
        HttpServletRequest req = Mock()

        when:
        c.share(req, 1, null)

        then:
        IllegalArgumentException e1 = thrown()
        e1.message == 'An API client id is required.'

        when:
        c.fileStorage(req, 1, null)

        then:
        IllegalArgumentException e2 = thrown()
        e2.message == 'saveToRepo is required.'
    }
}
