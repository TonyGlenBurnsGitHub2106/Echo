package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import tools.jackson.databind.json.JsonMapper

import com.echo.ApiClientModels.Credential
import static com.echo.SolutionFixtures.AGENT_CODE
import static com.echo.SolutionFixtures.AGENT_ID
import static com.echo.SolutionFixtures.AGENT_KEY
import static com.echo.SolutionFixtures.credential
import static com.echo.SolutionFixtures.lookups
import static com.echo.SolutionFixtures.solution

/**
 * Story 5653: the base of the Solution* controller specs. The real caller resolver, access fence, code check and
 * SolutionService run in the context; only storage is mocked (SolutionRepository here, the rest in each spec).
 * The agent "rex" (client id 7, key "rex-key") authenticates through the real ApiClientAuthFilter with the
 * allowlist /api/solutions/; whether a solution is shared with it is decided per feature by stubbing
 * apiClients.maySee(7, id) -- unstubbed, nothing is shared.
 */
@Import([SecurityConfig, JwtService, SolutionCallerResolver, SolutionAccess, SolutionCodeCheck, SolutionService])
abstract class SolutionWebSupport extends EchoWebSpec {

    static final JsonMapper MAPPER = JsonMapper.builder().build()

    @SpringBean SolutionRepository repo = Mock()

    def setup() {
        repo.lookups() >> lookups()
        apiClients.authenticate(AGENT_CODE, AGENT_KEY) >> credential()
        apiClients.authenticate('scoped', 'scoped-key') >> new Credential(8, 'scoped', 'h', true, '/api/elsewhere/')
        apiClients.authenticate('searcher', 'searcher-key') >> new Credential(9, 'searcher', 'h', true, '/api/solution-search/')
    }

    /** A request built from a where: table's verb and path. */
    static MockHttpServletRequestBuilder request(String method, String path) {
        MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
    }

    /** The agent Rex, with its own two headers and nothing else. */
    MockHttpServletRequestBuilder asAgent(MockHttpServletRequestBuilder b) {
        b.header(ApiClientAuthFilter.HEADER_CLIENT, AGENT_CODE).header(ApiClientAuthFilter.HEADER_KEY, AGENT_KEY)
    }

    /** An agent whose allowlist does not reach /api/solutions/. */
    MockHttpServletRequestBuilder asScopedElsewhere(MockHttpServletRequestBuilder b) {
        b.header(ApiClientAuthFilter.HEADER_CLIENT, 'scoped').header(ApiClientAuthFilter.HEADER_KEY, 'scoped-key')
    }

    /** An agent whose allowlist (wrongly) reaches /api/solution-search/. */
    MockHttpServletRequestBuilder asSearcher(MockHttpServletRequestBuilder b) {
        b.header(ApiClientAuthFilter.HEADER_CLIENT, 'searcher').header(ApiClientAuthFilter.HEADER_KEY, 'searcher-key')
    }

    /** The request as one of the callers a where: table names -- "user", "service" or "agent". */
    MockHttpServletRequestBuilder by(String who, MockHttpServletRequestBuilder b) {
        switch (who) {
            case 'user': return asUser(b)
            case 'service': return asService(b)
            case 'agent': return asAgent(b)
            default: throw new IllegalArgumentException(who)
        }
    }

    /** A JSON body: a String is sent as written, anything else is serialized. */
    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String ? (String) body : MAPPER.writeValueAsString(body))
    }

    /** Solution {@code id} exists; the agent may see it when {@code shared}. */
    void solutionExists(int id, boolean shared = false, Map<String, Object> extra = [:]) {
        repo.findSolution(id) >> solution([id: id] + extra)
        apiClients.maySee(AGENT_ID, id) >> shared
    }
}
