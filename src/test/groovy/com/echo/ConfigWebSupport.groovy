package com.echo

import java.util.regex.Matcher
import java.util.regex.Pattern

import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import com.echo.ApiClientModels.Credential
import com.echo.SolutionModels.Solution

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * Story 5653: the base of the Solutions-family controller specs (Configuration, Configuration II,
 * Testing, Terms, Conversations, Files, File filing). It adds to {@link EchoWebSpec}:
 *
 *   - the REAL {@link SolutionCallerResolver} (so who-is-calling is decided exactly as in production);
 *   - a REAL {@link SolutionAccess} over a mocked {@link SolutionRepository} and the context's mocked
 *     {@link ApiClientService}, so the second fence (which solutions an agent may see) is real too;
 *   - three API clients an agent request can present:
 *       rex  (id 7)  allowlist /api/solutions/            -- solution 1 is shared with him, 2 is not
 *       zed  (id 8)  allowlist /api/solutions/            -- nothing is shared with him
 *       wide (id 9)  allowlist /api/solutions/ and /api/solution-files/ -- solution 1 shared
 *
 * Solutions 1 and 2 exist; every other id does not.
 *
 * A controller spec declares its service as a {@code @SpringBean} Mock and, in its own setup(), makes
 * that mock delegate every call to a real service built over mocked repositories and {@link #access}.
 * The whole chain (filter -> controller -> service -> access) therefore runs for real; only storage is fake.
 *
 * Data tables carry request bodies as JSON text. A token {@code @N@} inside a body stands for a text of
 * N characters and is expanded by {@link #request} in given:/when:, so a table never holds an expression.
 */
@Import([SolutionCallerResolver])
abstract class ConfigWebSupport extends EchoWebSpec {

    static final String AGENT_KEY = 'agent-key-0123456789'
    static final int REX = 7, ZED = 8, WIDE = 9

    /** Every caller who may use an endpoint of a solution shared with rex. */
    static final List<String> PERMITTED = ['user', 'service', 'rex']

    private static final Pattern LENGTH_TOKEN = Pattern.compile('@(\\d+)@')

    SolutionRepository solutionRepo = Mock()
    SolutionAccess access

    Set<Integer> solutionIds = [1, 2] as Set
    Set<List<Integer>> shares = [[REX, 1], [WIDE, 1]] as Set
    Map<String, Credential> agents = [
        rex : new Credential(REX, 'rex', 'hash', true, '/api/solutions/'),
        zed : new Credential(ZED, 'zed', 'hash', true, '/api/solutions/'),
        wide: new Credential(WIDE, 'wide', 'hash', true, '/api/solutions/;/api/solution-files/')]

    /** Unique per request, so wrong-key requests never trip the filter's per-address lockout for later specs. */
    private static int nextAddress = 1

    def setup() {
        access = new SolutionAccess(solutionRepo, apiClients)
        solutionRepo.findSolution(_) >> { int id -> solutionIds.contains(id) ? solution(id) : null }
        apiClients.authenticate(_, _) >> { String c, String k -> k == AGENT_KEY ? agents[c] : null }
        apiClients.maySee(_, _) >> { int c, int s -> shares.contains([c, s]) }
    }

    static Solution solution(int id) {
        Solution.of([SolutionID: id, SolutionName: 'Solution ' + id])
    }

    /** An outside agent (API client) presenting its code and key. */
    MockHttpServletRequestBuilder asAgent(MockHttpServletRequestBuilder b, String code = 'rex', String key = AGENT_KEY) {
        b.header(ApiClientAuthFilter.HEADER_CLIENT, code).header(ApiClientAuthFilter.HEADER_KEY, key)
         .header('X-Forwarded-For', '10.53.' + ((nextAddress / 250) as int) + '.' + (nextAddress++ % 250))
    }

    /** As the service key, attributed to the named house actor (X-Echo-Actor). */
    MockHttpServletRequestBuilder asActor(MockHttpServletRequestBuilder b, String actor) {
        asService(b).header(SolutionCallerResolver.HEADER_ACTOR, actor)
    }

    /** Applies one of the named callers: none | user | service | stewart (the house as Stewart) | an agent code. */
    MockHttpServletRequestBuilder by(String who, MockHttpServletRequestBuilder b) {
        switch (who) {
            case 'none':    return b
            case 'user':    return asUser(b)
            case 'service': return asService(b)
            case 'stewart': return asActor(b, 'stewart')
            default:        return asAgent(b, who)
        }
    }

    /**
     * As {@link #by}, for any request builder -- a multipart upload's builder is not a
     * MockHttpServletRequestBuilder in Spring 7, so the credentials are added as headers here.
     */
    AbstractMockHttpServletRequestBuilder<?> byAny(String who, AbstractMockHttpServletRequestBuilder<?> b) {
        switch (who) {
            case 'none':
                return b
            case 'user':
                return b.header('Authorization', 'Bearer ' + jwt.issueAccess(new AppUser(1, 'tony', 'tony', null, true, true, 0, null, 0)))
            case 'service':
                return b.header(JwtAuthenticationFilter.HEADER_SERVICE_KEY, SERVICE_KEY)
            default:
                return b.header(ApiClientAuthFilter.HEADER_CLIENT, who).header(ApiClientAuthFilter.HEADER_KEY, AGENT_KEY)
                        .header('X-Forwarded-For', '10.54.' + ((nextAddress / 250) as int) + '.' + (nextAddress++ % 250))
        }
    }

    /** A request: GET | POST | PUT | DELETE, with an optional JSON body (tokens @N@ expanded). */
    static MockHttpServletRequestBuilder request(String method, String url, String body) {
        MockHttpServletRequestBuilder b
        switch (method) {
            case 'GET':  b = get(url); break
            case 'POST': b = post(url); break
            case 'PUT':  b = put(url); break
            default:     b = delete(url)
        }
        return body == null ? b : json(b, body)
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(expand(body))
    }

    /** Replaces every @N@ with a text of N characters. */
    static String expand(String body) {
        Matcher m = LENGTH_TOKEN.matcher(body)
        StringBuffer sb = new StringBuffer()
        while (m.find()) {
            m.appendReplacement(sb, getReplicatedString('x', Integer.parseInt(m.group(1))))
        }
        m.appendTail(sb)
        return sb.toString()
    }
}
