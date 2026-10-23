package com.echo

// Story 5653: administration of API clients. Every endpoint, its JSON and refusals, and who may
// call: nobody signed out, a person or the house yes, an API client never (fence and second lock).

import java.time.LocalDateTime

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.hamcrest.Matchers.nullValue
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.AccessLogRow
import com.echo.ApiClientModels.ApiClient
import com.echo.ApiClientModels.Credential
import com.echo.ApiClientModels.IssuedKey
import com.echo.ApiClientModels.SolutionLink

import spock.lang.Unroll

@WebMvcTest(ApiClientController)
class ApiClientControllerSpec extends EchoWebSpec {

    private static final String WHEN = '10/22/2026 09:30'

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    // ---- who may call --------------------------------------------------------------------------

    @Unroll
    def "#seq. the endpoints should answer 401 Not signed in without asking the service when #scenario is called signed out"() {

        when:
        mvc.perform(call(method, path, body)).andExpect(status().isUnauthorized())
            .andExpect(content().json('{"error":"Not signed in"}', true))

        then:
        0 * apiClients._

        where:
        seq | scenario         | method   | path                            | body
        1   | 'list'           | 'GET'    | '/api/api-clients'              | null
        2   | 'create'         | 'POST'   | '/api/api-clients'              | '{"clientCode":"rex","clientName":"Rex"}'
        3   | 'update'         | 'PUT'    | '/api/api-clients/7'            | '{"clientCode":"rex","clientName":"Rex"}'
        4   | 'enabled'        | 'POST'   | '/api/api-clients/7/enabled'    | '{"enabled":false}'
        5   | 'issue key'      | 'POST'   | '/api/api-clients/7/key'        | null
        6   | 'revoke key'     | 'DELETE' | '/api/api-clients/7/key'        | null
        7   | 'delete'         | 'DELETE' | '/api/api-clients/7'            | null
        8   | 'solutions'      | 'GET'    | '/api/api-clients/7/solutions'  | null
        9   | 'link solution'  | 'POST'   | '/api/api-clients/7/solutions'  | '{"solutionId":5}'
        10  | 'brief'          | 'GET'    | '/api/api-clients/7/brief'      | null
        11  | 'access log'     | 'GET'    | '/api/api-clients/access-log'   | null
    }

    @Unroll
    def "#seq. the fence should answer 403 before the controller when an API client calls #scenario"() {

        when:
        mvc.perform(call(method, path, body).header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath('$.error').value('This API client may not reach that path.'))

        then:
        1 * apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/;/api/api-clients/')
        1 * apiClients.logAccess(7, 'rex', path, method, 403, false, _)
        0 * apiClients._

        where:
        seq | scenario         | method   | path                            | body
        1   | 'list'           | 'GET'    | '/api/api-clients'              | null
        2   | 'create'         | 'POST'   | '/api/api-clients'              | '{"clientCode":"rex","clientName":"Rex"}'
        3   | 'update'         | 'PUT'    | '/api/api-clients/7'            | '{"clientCode":"rex","clientName":"Rex"}'
        4   | 'enabled'        | 'POST'   | '/api/api-clients/7/enabled'    | '{"enabled":false}'
        5   | 'issue key'      | 'POST'   | '/api/api-clients/7/key'        | null
        6   | 'revoke key'     | 'DELETE' | '/api/api-clients/7/key'        | null
        7   | 'delete'         | 'DELETE' | '/api/api-clients/7'            | null
        8   | 'solutions'      | 'GET'    | '/api/api-clients/7/solutions'  | null
        9   | 'link solution'  | 'POST'   | '/api/api-clients/7/solutions'  | '{"solutionId":5}'
        10  | 'brief'          | 'GET'    | '/api/api-clients/7/brief'      | null
        11  | 'access log'     | 'GET'    | '/api/api-clients/access-log'   | null
    }

    @Unroll
    def "#seq. refuseClients should answer 403 forbidden when a client principal past the fence calls #scenario"() {

        when:
        mvc.perform(call(method, path, body).with(user('client:rex')))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('forbidden'))
            .andExpect(jsonPath('$.message').value('API clients are managed by the house.'))

        then:
        0 * apiClients._

        where:
        seq | scenario         | method   | path                            | body
        1   | 'list'           | 'GET'    | '/api/api-clients'              | null
        2   | 'create'         | 'POST'   | '/api/api-clients'              | '{"clientCode":"rex","clientName":"Rex"}'
        3   | 'update'         | 'PUT'    | '/api/api-clients/7'            | '{"clientCode":"rex","clientName":"Rex"}'
        4   | 'enabled'        | 'POST'   | '/api/api-clients/7/enabled'    | '{"enabled":false}'
        5   | 'issue key'      | 'POST'   | '/api/api-clients/7/key'        | null
        6   | 'revoke key'     | 'DELETE' | '/api/api-clients/7/key'        | null
        7   | 'delete'         | 'DELETE' | '/api/api-clients/7'            | null
        8   | 'solutions'      | 'GET'    | '/api/api-clients/7/solutions'  | null
        9   | 'link solution'  | 'POST'   | '/api/api-clients/7/solutions'  | '{"solutionId":5}'
        10  | 'brief'          | 'GET'    | '/api/api-clients/7/brief'      | null
        11  | 'access log'     | 'GET'    | '/api/api-clients/access-log'   | null
    }

    def "refuseClients should throw for a client and pass a person when called directly"() {

        given:
        ApiClientController c = new ApiClientController(apiClients, 'http://x')

        when:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken('client:rex', null, [])
        c.refuseClients()

        then:
        SolutionForbiddenException ex = thrown(SolutionForbiddenException)
        ex.message == 'API clients are managed by the house.'

        when:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken('tony', null, [])
        c.refuseClients()

        then:
        noExceptionThrown()
    }

    // ---- GET list --------------------------------------------------------------------------------

    @Unroll
    def "list should answer every client with no key or hash when the caller is #scenario"() {

        given:
        apiClients.list() >> [client(7, 'rex', true), client(8, 'bot', false)]
        MockHttpServletRequestBuilder b = asHouse ? asService(get('/api/api-clients')) : asUser(get('/api/api-clients'))

        expect:
        mvc.perform(b).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[0].id').value(7))
            .andExpect(jsonPath('$[0].clientCode').value('rex'))
            .andExpect(jsonPath('$[0].keyHint').value('wxyz'))
            .andExpect(jsonPath('$[0].hasKey').value(true))
            .andExpect(jsonPath('$[0].enabled').value(true))
            .andExpect(jsonPath('$[0].allowedPathPrefixes').value('/api/solutions/'))
            .andExpect(jsonPath('$[0].solutionCount').value(2))
            .andExpect(jsonPath('$[0].lastUsedDate').value('2026-10-22T09:30:00'))
            .andExpect(jsonPath('$[0].keyHash').doesNotExist())
            .andExpect(jsonPath('$[0].key').doesNotExist())
            .andExpect(jsonPath('$[1].enabled').value(false))

        where:
        scenario      | asHouse
        'a person'    | false
        'the house'   | true
    }

    def "list should answer [] when there are no clients"() {

        given:
        apiClients.list() >> []

        expect:
        mvc.perform(asUser(get('/api/api-clients'))).andExpect(status().isOk()).andExpect(content().json('[]', true))
    }

    // ---- POST create / PUT update ---------------------------------------------------------------

    def "create should pass every field and answer the saved client when the body is full"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients')),
                '{"clientCode":"rex","clientName":"Rex","allowedPathPrefixes":"/api/solutions/","clientNotes":"n","enabled":false}'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(7))

        then:
        1 * apiClients.save(0, 'rex', 'Rex', '/api/solutions/', 'n', false) >> client(7, 'rex', true)
    }

    @Unroll
    def "create should default the allowlist to /api/solutions/ and enabled to true when the allowlist is #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients')), body)).andExpect(status().isOk())

        then:
        1 * apiClients.save(0, 'rex', null, '/api/solutions/', null, true) >> client(7, 'rex', true)

        where:
        scenario  | body
        'missing' | '{"clientCode":"rex"}'
        'null'    | '{"clientCode":"rex","allowedPathPrefixes":null,"enabled":null}'
        'empty'   | '{"clientCode":"rex","allowedPathPrefixes":""}'
        'blank'   | '{"clientCode":"rex","allowedPathPrefixes":"   ","enabled":true}'
    }

    def "update should save under the path id when the house key calls"() {

        when:
        mvc.perform(json(asService(put('/api/api-clients/7')), '{"clientCode":"rex","clientName":"Rex 2","enabled":true}'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.clientCode').value('rex'))

        then:
        1 * apiClients.save(7, 'rex', 'Rex 2', '/api/solutions/', null, true) >> client(7, 'rex', true)
    }

    def "update should answer 404 not_found when the service cannot find the client"() {

        when:
        mvc.perform(json(asUser(put('/api/api-clients/99')), '{"clientCode":"rex"}'))
            .andExpect(status().isNotFound())
            .andExpect(content().json('{"ok":false,"error":"not_found","message":"Client not found."}', true))

        then:
        1 * apiClients.save(99, *_) >> null
    }

    def "create should answer 409 with the message when a rule refuses"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients')), '{"clientCode":"rex"}'))
            .andExpect(status().isConflict())
            .andExpect(content().json('{"ok":false,"error":"rule","message":"That client code is taken."}', true))

        then:
        1 * apiClients.save(*_) >> { throw new SolutionRuleException('That client code is taken.') }
    }

    def "create should answer 400 bad_request with the message when the service throws IllegalArgumentException"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients')), '{"clientCode":""}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"ok":false,"error":"bad_request","message":"A code is required."}', true))

        then:
        1 * apiClients.save(*_) >> { throw new IllegalArgumentException('A code is required.') }
    }

    @Unroll
    def "create should answer 400 and save nothing when the request has #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(post('/api/api-clients')).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b).andExpect(status().isBadRequest())

        then:
        0 * apiClients.save(*_)

        where:
        scenario               | body
        'no body'              | null
        'a JSON null'          | 'null'
        'broken JSON'          | '{"clientCode":'
        'a wrong-typed field'  | '{"enabled":"maybe"}'
    }

    def "create should throw 'A client is required.' when called directly with a null body"() {

        when:
        new ApiClientController(apiClients, 'x').create(null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'A client is required.'
    }

    @Unroll
    def "#seq. the id routes should never reach the controller when the id is #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(json(asUser(request(HttpMethod.valueOf(method), path)), '{}')).andReturn().response

        then:
        res.status in [404, 405]
        0 * apiClients._

        where:
        seq | scenario                         | method   | path
        1   | 'letters on update'              | 'PUT'    | '/api/api-clients/abc'
        2   | 'negative on update'             | 'PUT'    | '/api/api-clients/-1'
        3   | 'a decimal on update'            | 'PUT'    | '/api/api-clients/1.5'
        4   | 'letters on enabled'             | 'POST'   | '/api/api-clients/abc/enabled'
        5   | 'letters on key'                 | 'POST'   | '/api/api-clients/abc/key'
        6   | 'letters on delete'              | 'DELETE' | '/api/api-clients/abc'
        7   | 'letters on brief'               | 'GET'    | '/api/api-clients/abc/brief'
        8   | 'letters on solutions'           | 'GET'    | '/api/api-clients/abc/solutions'
    }

    def "update should answer 400 when the id is too large for an int"() {

        expect:
        mvc.perform(json(asUser(put('/api/api-clients/99999999999')), '{}')).andExpect(status().isBadRequest())
    }

    def "the client routes should answer 405 when called with PATCH"() {

        expect:
        mvc.perform(json(asUser(patch('/api/api-clients/7')), '{}')).andExpect(status().isMethodNotAllowed())
    }

    // ---- enabled ---------------------------------------------------------------------------------

    @Unroll
    def "setEnabled should turn the client #expected and answer it when the body is #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients/7/enabled')), body))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(7))

        then:
        1 * apiClients.setEnabled(7, expected) >> true
        1 * apiClients.find(7) >> client(7, 'rex', expected)

        where:
        scenario          | body                 || expected
        'enabled true'    | '{"enabled":true}'   || true
        'enabled false'   | '{"enabled":false}'  || false
        'enabled null'    | '{"enabled":null}'   || false
        'empty'           | '{}'                 || false
    }

    def "setEnabled should answer 404 when the client is unknown"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients/9/enabled')), '{"enabled":true}'))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath('$.error').value('not_found'))

        then:
        1 * apiClients.setEnabled(9, true) >> false
        0 * apiClients.find(_)
    }

    // ---- keys -----------------------------------------------------------------------------------

    def "issueKey should answer the key, the one and only time, when the client exists"() {

        when:
        mvc.perform(asUser(post('/api/api-clients/7/key')))
            .andExpect(status().isOk())
            .andExpect(content().json('{"apiClientId":7,"clientCode":"rex","key":"echo_abc","keyHint":"_abc"}', true))

        then:
        1 * apiClients.issueKey(7) >> new IssuedKey(7, 'rex', 'echo_abc', '_abc')
    }

    def "issueKey should answer 404 when the client is unknown"() {

        when:
        mvc.perform(asUser(post('/api/api-clients/9/key'))).andExpect(status().isNotFound())

        then:
        1 * apiClients.issueKey(9) >> null
    }

    def "revokeKey should answer the client, or 404 when the client is unknown"() {

        when:
        mvc.perform(asUser(delete('/api/api-clients/7/key'))).andExpect(status().isOk()).andExpect(jsonPath('$.id').value(7))
        mvc.perform(asUser(delete('/api/api-clients/9/key'))).andExpect(status().isNotFound())

        then:
        1 * apiClients.revokeKey(7) >> true
        1 * apiClients.find(7) >> client(7, 'rex', true)
        1 * apiClients.revokeKey(9) >> false
    }

    // ---- delete ---------------------------------------------------------------------------------

    def "delete should answer ok and the id, or 404 when the client is unknown"() {

        when:
        mvc.perform(asService(delete('/api/api-clients/7')))
            .andExpect(status().isOk())
            .andExpect(content().json('{"ok":true,"deletedId":7}', true))
        mvc.perform(asService(delete('/api/api-clients/8'))).andExpect(status().isNotFound())

        then:
        1 * apiClients.delete(7) >> true
        1 * apiClients.delete(8) >> false
    }

    // ---- solutions ------------------------------------------------------------------------------

    def "solutions should list the links when the client has some"() {

        when:
        mvc.perform(asUser(get('/api/api-clients/7/solutions')))
            .andExpect(status().isOk())
            .andExpect(content().json('[{"apiClientId":7,"solutionId":5,"solutionName":"S","clientCode":"rex","clientName":"Rex"}]', true))

        then:
        1 * apiClients.solutionsForClient(7) >> [new SolutionLink(7, 5, 'S', 'rex', 'Rex')]
    }

    @Unroll
    def "setSolution should link with linked=#linked and answer the new list when #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients/7/solutions')), body))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(0))

        then:
        1 * apiClients.setSolutionLink(7, 5, linked) >> true
        1 * apiClients.solutionsForClient(7) >> []

        where:
        scenario                 | body                                || linked
        'linked is absent'       | '{"solutionId":5}'                  || true
        'linked is null'         | '{"solutionId":5,"linked":null}'    || true
        'linked is true'         | '{"solutionId":5,"linked":true}'    || true
        'linked is false'        | '{"solutionId":5,"linked":false}'   || false
    }

    @Unroll
    def "setSolution should answer 400 when #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/api-clients/7/solutions')), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"ok":false,"error":"bad_request","message":"A solution id is required."}', true))

        then:
        0 * apiClients.setSolutionLink(*_)

        where:
        scenario                      | body
        'the body is empty'           | '{}'
        'the solution id is null'     | '{"solutionId":null}'
        'the solution id is zero'     | '{"solutionId":0}'
        'the solution id is negative' | '{"solutionId":-3}'
        'only linked is sent'         | '{"linked":true}'
    }

    // ---- access log -----------------------------------------------------------------------------

    def "accessLog should default to every client, all rows, offset 0 and 50 a page when no parameter is sent"() {

        given:
        AccessLogRow row = new AccessLogRow(1, null, 'rex', '/api/auth/login', 'POST', 403, false, '1.2.3.4', getLocalDateTime(WHEN))

        when:
        mvc.perform(asUser(get('/api/api-clients/access-log')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$[0].requestPath').value('/api/auth/login'))
            .andExpect(jsonPath('$[0].allowed').value(false))
            .andExpect(jsonPath('$[0].apiClientId').value(nullValue()))

        then:
        1 * apiClients.accessLog(null, false, 0, 50) >> [row]
    }

    def "accessLog should pass its filters when they are sent"() {

        when:
        mvc.perform(asUser(get('/api/api-clients/access-log').param('clientId', '7').param('refusedOnly', 'true')
                .param('offset', '100').param('pageSize', '25')))
            .andExpect(status().isOk())

        then:
        1 * apiClients.accessLog(7, true, 100, 25) >> []
    }

    def "accessLog should read refusedOnly=false as false when it is sent"() {

        when:
        mvc.perform(asUser(get('/api/api-clients/access-log').param('refusedOnly', 'false'))).andExpect(status().isOk())

        then:
        1 * apiClients.accessLog(null, false, 0, 50) >> []
    }

    @Unroll
    def "accessLog should answer 400 when #scenario"() {

        when:
        mvc.perform(asUser(get('/api/api-clients/access-log').param(name, value))).andExpect(status().isBadRequest())

        then:
        0 * apiClients.accessLog(*_)

        where:
        scenario                     | name          | value
        'clientId is not a number'   | 'clientId'    | 'abc'
        'offset is a decimal'        | 'offset'      | '1.5'
        'pageSize is not a number'   | 'pageSize'    | 'x'
        'refusedOnly is not boolean' | 'refusedOnly' | 'maybe'
    }

    // ---- brief ----------------------------------------------------------------------------------

    def "brief should answer plain text with this Echo's address and the key slot, never a key, when the client exists"() {

        when:
        MockHttpServletResponse res = mvc.perform(asUser(get('/api/api-clients/7/brief')))
            .andExpect(status().isOk())
            .andReturn().response

        then:
        1 * apiClients.find(7) >> client(7, 'rex', true)
        res.contentType.startsWith('text/plain')
        res.contentAsString == ApiClientBrief.textFor(client(7, 'rex', true), 'http://localhost:8770')
        res.contentAsString.contains('<PASTE-THE-KEY-HERE>')
        !res.contentAsString.contains('X-Echo-Key')
    }

    def "brief should answer a plain 404 when the client is unknown"() {

        when:
        MockHttpServletResponse res = mvc.perform(asUser(get('/api/api-clients/9/brief'))).andExpect(status().isNotFound()).andReturn().response

        then:
        1 * apiClients.find(9) >> null
        res.contentAsString == 'Client not found.'
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static ApiClient client(int id, String code, boolean enabled) {
        LocalDateTime t = getLocalDateTime(WHEN)
        new ApiClient(id, code, 'Rex', 'wxyz', true, enabled, '/api/solutions/', 'notes', t, t, t, 2, 10, 1)
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }

    private static MockHttpServletRequestBuilder call(String method, String path, String body) {
        MockHttpServletRequestBuilder b = request(HttpMethod.valueOf(method), path)
        body == null ? b : json(b, body)
    }
}
