package com.echo

// Story 5653: Echo's security chain as a whole -- which paths are open, which need a credential,
// the 401 JSON, the CSP (with its sandbox carry-through), the cache headers for versioned static
// files, the frame / content-type / HSTS headers, no session, no CSRF, the filter order, and the
// API-client door end to end (every method, every refusal) in front of a catch-all probe.

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

@WebMvcTest(SecurityConfigSpec.Probe)
class SecurityConfigSpec extends EchoWebSpec {

    /** Answers every path and method with who the chain said the caller was. */
    @RestController
    static class Probe {
        @RequestMapping('/probe/sandboxed')
        Map<String, Object> sandboxed(HttpServletResponse resp) {
            resp.setHeader('Content-Security-Policy', 'sandbox allow-scripts')
            [ok: true] as Map<String, Object>
        }

        @RequestMapping('/**')
        Map<String, Object> any(HttpServletRequest req) {
            Authentication a = SecurityContextHolder.context.authentication
            [principal: a?.name, uri: req.requestURI, method: req.method,
             client: req.getAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE) != null] as Map<String, Object>
        }
    }

    private static final String CSP = "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: blob: https:; " +
        "media-src 'self' blob:; " +
        "font-src 'self' data:; " +
        "connect-src 'self'; " +
        "frame-ancestors 'self'; " +
        "base-uri 'self'; " +
        "form-action 'self'"

    private static final Credential REX = new Credential(7, 'rex', 'h', true, '/api/solutions/')

    @Autowired ApiClientAuthFilter clientFilter
    @Autowired FilterChainProxy springSecurity

    def setup() {
        clientFilter.resetThrottle()
    }

    // =====================================================================================
    // the static helpers
    // =====================================================================================

    @Unroll
    def "#seq. isVersionedStaticAsset should answer #expected when #scenario"() {

        expect:
        SecurityConfig.isVersionedStaticAsset(path, query) == expected

        where:
        seq | scenario                               | path             | query      || expected
        1   | 'a versioned script'                   | '/app.js'        | 'v=3'      || true
        2   | 'a nested versioned stylesheet'        | '/a/b/c.css'     | 'v=12'     || true
        3   | 'an upper-case JS extension'           | '/x.JS'          | 'v=1'      || true
        4   | 'a mixed-case Css extension'           | '/x.Css'         | 'v=1'      || true
        5   | 'a png'                                | '/i.png'         | 'v=1'      || true
        6   | 'a jpg'                                | '/i.jpg'         | 'v=1'      || true
        7   | 'a jpeg'                               | '/i.jpeg'        | 'v=1'      || true
        8   | 'a gif'                                | '/i.gif'         | 'v=1'      || true
        9   | 'an svg'                               | '/i.svg'         | 'v=1'      || true
        10  | 'an ico'                               | '/favicon.ico'   | 'v=1'      || true
        11  | 'a woff'                               | '/f.woff'        | 'v=1'      || true
        12  | 'a woff2'                              | '/f.woff2'       | 'v=1'      || true
        13  | 'a ttf'                                | '/f.ttf'         | 'v=1'      || true
        14  | 'a json'                               | '/manifest.json' | 'v=1'      || true
        15  | 'a wasm'                               | '/m.wasm'        | 'v=1'      || true
        16  | 'v is the second parameter'            | '/app.js'        | 'a=1&v=3'  || true
        17  | 'v is empty'                           | '/app.js'        | 'v='       || true
        18  | 'v is the first of two parameters'     | '/app.js'        | 'v=3&x=1'  || true
        19  | 'there is no query'                    | '/app.js'        | null       || false
        20  | 'the query is empty'                   | '/app.js'        | ''         || false
        21  | 'the query has no v'                   | '/app.js'        | 'x=1'      || false
        22  | 'v is part of a longer name'           | '/app.js'        | 'xv=1'     || false
        23  | 'v has no equals sign'                 | '/app.js'        | 'v1'       || false
        24  | 'an upper-case V'                      | '/app.js'        | 'V=1'      || false
        25  | 'vv after an ampersand'                | '/app.js'        | 'a=1&vv=3' || false
        26  | 'an html page'                         | '/index.html'    | 'v=1'      || false
        27  | 'a source map'                         | '/app.js.map'    | 'v=1'      || false
        28  | 'no extension'                         | '/app'           | 'v=1'      || false
        29  | 'the root'                             | '/'              | 'v=1'      || false
        30  | 'a script under the api'               | '/api/app.js'    | 'v=1'      || false
        31  | 'an image under the api'               | '/api/x/y.png'   | 'v=1'      || false
        32  | 'a script under a sibling of api'      | '/apix/app.js'   | 'v=1'      || true
        33  | 'the api root without a slash'         | '/api'           | 'v=1'      || false
        34  | 'the path is null'                     | null             | 'v=1'      || false
        35  | 'both are null'                        | null             | null       || false
    }

    @Unroll
    def "keepSandbox should answer '#expected' when the prior policy is #scenario"() {

        given:
        MockHttpServletResponse resp = new MockHttpServletResponse()
        if (prior != null) resp.setHeader('Content-Security-Policy', prior)

        expect:
        SecurityConfig.keepSandbox(resp, 'P') == expected

        where:
        scenario                          | prior                          || expected
        'absent'                          | null                           || 'P'
        'empty'                           | ''                             || 'P'
        'without a sandbox'               | "default-src 'self'"           || 'P'
        'a bare sandbox'                  | 'sandbox'                      || 'P; sandbox'
        'a sandbox with a token'          | 'sandbox allow-scripts'        || 'P; sandbox'
        'a policy ending in a sandbox'    | "default-src 'none'; sandbox"  || 'P; sandbox'
    }

    // =====================================================================================
    // the chain: filter order
    // =====================================================================================

    def "filterChain should run the client filter before the JWT filter and have no form login when built"() {

        when:
        List<Class> filters = springSecurity.filterChains[0].filters.collect { Filter f -> f.class }

        then:
        filters.indexOf(ApiClientAuthFilter) >= 0
        filters.indexOf(ApiClientAuthFilter) < filters.indexOf(JwtAuthenticationFilter)
        filters.every { Class c -> c != UsernamePasswordAuthenticationFilter }
    }

    // =====================================================================================
    // open and closed paths
    // =====================================================================================

    @Unroll
    def "#seq. filterChain should let #method #path through without a credential when it is #scenario"() {

        expect:
        mvc.perform(request(HttpMethod.valueOf(method), path))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.principal').value('anonymousUser'))

        where:
        seq | scenario                  | method | path
        1   | 'login'                   | 'POST' | '/api/auth/login'
        2   | 'refresh'                 | 'POST' | '/api/auth/refresh'
        3   | 'logout'                  | 'POST' | '/api/auth/logout'
        4   | 'another auth path'       | 'GET'  | '/api/auth/anything/else'
        5   | 'health'                  | 'GET'  | '/api/system/health'
        6   | 'config'                  | 'GET'  | '/api/system/config'
        7   | 'demo status'             | 'GET'  | '/api/system/demo/status'
        8   | 'the root'                | 'GET'  | '/'
        9   | 'the dashboard page'      | 'GET'  | '/index.html'
        10  | 'the solutions page'      | 'GET'  | '/solutions/index.html'
        11  | 'a script'                | 'GET'  | '/app.js'
        12  | 'a non-api POST'          | 'POST' | '/not-api/x'
        13  | 'a sibling of api'        | 'GET'  | '/apix/y'
    }

    @Unroll
    def "#seq. filterChain should answer 401 Not signed in as JSON for #method #path when it is #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(request(HttpMethod.valueOf(method), path))
            .andExpect(status().isUnauthorized())
            .andReturn().response

        then:
        res.contentType == 'application/json;charset=UTF-8'
        res.contentAsString == '{"error":"Not signed in"}'

        where:
        seq | scenario                         | method   | path
        1   | 'change-password'                | 'POST'   | '/api/auth/change-password'
        2   | 'logout-everywhere'              | 'POST'   | '/api/auth/logout-everywhere'
        3   | 'verify-password'                | 'POST'   | '/api/auth/verify-password'
        4   | 'about'                          | 'GET'    | '/api/system/about'
        5   | 'below an open path'             | 'GET'    | '/api/system/health/extra'
        6   | 'ping'                           | 'GET'    | '/api/ping'
        7   | 'the solutions root'             | 'GET'    | '/api/solutions'
        8   | 'a solution'                     | 'GET'    | '/api/solutions/1'
        9   | 'a solution write'               | 'POST'   | '/api/solutions/1/items'
        10  | 'the client admin'               | 'GET'    | '/api/api-clients'
        11  | 'quick launch'                   | 'GET'    | '/api/quick-launch'
        12  | 'view state'                     | 'PUT'    | '/api/view-state'
        13  | 'pdf generic'                    | 'POST'   | '/api/pdf/generic'
        14  | 'a file category delete'         | 'DELETE' | '/api/file-categories/4'
        15  | 'the api root'                   | 'GET'    | '/api/'
        16  | 'any other api path'             | 'GET'    | '/api/anything/at/all'
    }

    @Unroll
    def "filterChain should let a signed-in person reach #path as their name when it is #scenario"() {

        expect:
        mvc.perform(asUser(get(path), 'alice', 2)).andExpect(status().isOk())
            .andExpect(jsonPath('$.principal').value('alice'))
            .andExpect(jsonPath('$.client').value(false))

        where:
        scenario            | path
        'a solution'        | '/api/solutions/1'
        'the client admin'  | '/api/api-clients'
        'about'             | '/api/system/about'
        'verify-password'   | '/api/auth/verify-password'
        'ping'              | '/api/ping'
    }

    @Unroll
    def "filterChain should let the house key reach #path as service when it is #scenario"() {

        expect:
        mvc.perform(asService(get(path))).andExpect(status().isOk())
            .andExpect(jsonPath('$.principal').value('service'))

        where:
        scenario            | path
        'a solution'        | '/api/solutions/1'
        'the client admin'  | '/api/api-clients'
        'about'             | '/api/system/about'
        'quick launch'      | '/api/quick-launch'
    }

    @Unroll
    def "filterChain should answer 401 when the credential is #scenario"() {

        given:
        AppUser u = new AppUser(1, 'tony', 'tony', null, true, true, 0, null, 0)
        String value = valueKind == 'refresh' ? 'Bearer ' + jwt.issueRefresh(u) : literal

        expect:
        mvc.perform(get('/api/ping').header(headerName, value)).andExpect(status().isUnauthorized())

        where:
        scenario               | headerName      | valueKind | literal
        'a wrong service key'  | 'X-Echo-Key'    | 'literal' | 'nope'
        'a refresh token'      | 'Authorization' | 'refresh' | null
        'a forged token'       | 'Authorization' | 'literal' | 'Bearer x.y.z'
        'Basic credentials'    | 'Authorization' | 'literal' | 'Basic dG9ueTpwdw=='
    }

    def "filterChain should need no CSRF token when a signed-in person POSTs"() {

        expect:
        mvc.perform(asUser(post('/api/solutions/1/items'))).andExpect(status().isOk())
    }

    def "filterChain should create no session and no session cookie when a person calls"() {

        when:
        MvcResult r = mvc.perform(asUser(get('/api/solutions/1'))).andExpect(status().isOk()).andReturn()

        then:
        r.request.getSession(false) == null
        r.response.getCookie('JSESSIONID') == null
        r.response.getHeader('Set-Cookie') == null
    }

    def "filterChain should not carry the security context into the next request when a person has called"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1'))).andExpect(status().isOk())

        then:
        mvc.perform(get('/api/solutions/1')).andExpect(status().isUnauthorized())
    }

    // =====================================================================================
    // headers
    // =====================================================================================

    @Unroll
    def "filterChain should set the CSP, frame and sniff headers and never HSTS when the response is #scenario"() {

        given:
        MockHttpServletRequestBuilder b = headerRequest(kind)
        if (kind == 'signed-in') asUser(b)

        when:
        MockHttpServletResponse res = mvc.perform(b).andReturn().response

        then:
        res.getHeader('Content-Security-Policy') == CSP
        res.getHeader('X-Frame-Options') == 'SAMEORIGIN'
        res.getHeader('X-Content-Type-Options') == 'nosniff'
        res.getHeader('Strict-Transport-Security') == null

        where:
        scenario                | kind
        'an open page'          | 'open'
        'an open page on HTTPS' | 'https'
        'a 401'                 | 'unauthorized'
        'a signed-in call'      | 'signed-in'
        'a client refusal'      | 'client-refused'
    }

    def "filterChain should keep a sandbox at the end of the site policy when the response asked for one"() {

        when:
        MockHttpServletResponse res = mvc.perform(get('/probe/sandboxed')).andExpect(status().isOk()).andReturn().response

        then:
        res.getHeader('Content-Security-Policy') == CSP + '; sandbox'
    }

    @Unroll
    def "filterChain should cache a versioned static file for a year when it is #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().response

        then:
        res.getHeader('Cache-Control') == 'public, max-age=31536000, immutable'
        res.getHeader('Pragma') == 'cache'
        res.getHeader('Expires') == 'Thu, 31 Dec 2037 23:59:59 GMT'

        where:
        scenario                         | url
        'a script'                       | '/app.js?v=3'
        'a nested stylesheet'            | '/solutions/solutions.css?v=12'
        'an image with two parameters'   | '/icons/x.png?a=1&v=2'
    }

    @Unroll
    def "filterChain should not cache the response when it is #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(asUser(get(url))).andReturn().response

        then:
        res.getHeader('Cache-Control') != 'public, max-age=31536000, immutable'
        res.getHeader('Cache-Control')?.contains('no-cache')

        where:
        scenario                          | url
        'an unversioned script'           | '/app.js'
        'a versioned html page'           | '/index.html?v=3'
        'a versioned script under api'    | '/api/solutions/x.js?v=1'
        'a script without v'              | '/app.js?x=1'
    }

    // =====================================================================================
    // the API-client door, end to end
    // =====================================================================================

    @Unroll
    def "filterChain should let a client with the right key reach /api/solutions/** as client:rex when #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(request(HttpMethod.valueOf(method), '/api/solutions/5/items')
                .header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isOk()).andReturn().response

        then:
        1 * apiClients.authenticate('rex', 'k') >> REX
        1 * apiClients.logAccess(7, 'rex', '/api/solutions/5/items', method, 200, true, _)
        !hasBody || res.contentAsString.contains('"principal":"client:rex"')
        !hasBody || res.contentAsString.contains('"client":true')

        where:
        scenario                  | method    | hasBody
        'the method is GET'       | 'GET'     | true
        'the method is POST'      | 'POST'    | true
        'the method is PUT'       | 'PUT'     | true
        'the method is DELETE'    | 'DELETE'  | true
        'the method is PATCH'     | 'PATCH'   | true
        'the method is HEAD'      | 'HEAD'    | false
        'the method is OPTIONS'   | 'OPTIONS' | false
    }

    @Unroll
    def "#seq. filterChain should answer 403 and never reach the probe when a client sends #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(request(HttpMethod.valueOf(method), path)
                .header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden()).andReturn().response

        then:
        1 * apiClients.authenticate('rex', 'k') >> REX
        1 * apiClients.logAccess(7, 'rex', path, method, 403, false, _)
        method == 'HEAD' || res.contentAsString == '{"ok":false,"error":"This API client may not reach that path."}'
        !res.contentAsString.contains('principal')

        where:
        seq | scenario                              | method    | path
        1   | 'GET on login'                     | 'GET'     | '/api/auth/login'
        2   | 'POST on login'                    | 'POST'    | '/api/auth/login'
        3   | 'PUT on login'                     | 'PUT'     | '/api/auth/login'
        4   | 'DELETE on login'                  | 'DELETE'  | '/api/auth/login'
        5   | 'PATCH on login'                   | 'PATCH'   | '/api/auth/login'
        6   | 'HEAD on login'                    | 'HEAD'    | '/api/auth/login'
        7   | 'OPTIONS on login'                 | 'OPTIONS' | '/api/auth/login'
        8   | 'GET on refresh'                   | 'GET'     | '/api/auth/refresh'
        9   | 'POST on refresh'                  | 'POST'    | '/api/auth/refresh'
        10  | 'PUT on refresh'                   | 'PUT'     | '/api/auth/refresh'
        11  | 'DELETE on refresh'                | 'DELETE'  | '/api/auth/refresh'
        12  | 'PATCH on refresh'                 | 'PATCH'   | '/api/auth/refresh'
        13  | 'HEAD on refresh'                  | 'HEAD'    | '/api/auth/refresh'
        14  | 'OPTIONS on refresh'               | 'OPTIONS' | '/api/auth/refresh'
        15  | 'GET on verify-password'           | 'GET'     | '/api/auth/verify-password'
        16  | 'POST on verify-password'          | 'POST'    | '/api/auth/verify-password'
        17  | 'PUT on verify-password'           | 'PUT'     | '/api/auth/verify-password'
        18  | 'DELETE on verify-password'        | 'DELETE'  | '/api/auth/verify-password'
        19  | 'PATCH on verify-password'         | 'PATCH'   | '/api/auth/verify-password'
        20  | 'HEAD on verify-password'          | 'HEAD'    | '/api/auth/verify-password'
        21  | 'OPTIONS on verify-password'       | 'OPTIONS' | '/api/auth/verify-password'
        22  | 'GET on ping'                      | 'GET'     | '/api/ping'
        23  | 'POST on ping'                     | 'POST'    | '/api/ping'
        24  | 'PUT on ping'                      | 'PUT'     | '/api/ping'
        25  | 'DELETE on ping'                   | 'DELETE'  | '/api/ping'
        26  | 'PATCH on ping'                    | 'PATCH'   | '/api/ping'
        27  | 'HEAD on ping'                     | 'HEAD'    | '/api/ping'
        28  | 'OPTIONS on ping'                  | 'OPTIONS' | '/api/ping'
        29  | 'GET on system health'             | 'GET'     | '/api/system/health'
        30  | 'POST on system health'            | 'POST'    | '/api/system/health'
        31  | 'PUT on system health'             | 'PUT'     | '/api/system/health'
        32  | 'DELETE on system health'          | 'DELETE'  | '/api/system/health'
        33  | 'PATCH on system health'           | 'PATCH'   | '/api/system/health'
        34  | 'HEAD on system health'            | 'HEAD'    | '/api/system/health'
        35  | 'OPTIONS on system health'         | 'OPTIONS' | '/api/system/health'
        36  | 'GET on system about'              | 'GET'     | '/api/system/about'
        37  | 'POST on system about'             | 'POST'    | '/api/system/about'
        38  | 'PUT on system about'              | 'PUT'     | '/api/system/about'
        39  | 'DELETE on system about'           | 'DELETE'  | '/api/system/about'
        40  | 'PATCH on system about'            | 'PATCH'   | '/api/system/about'
        41  | 'HEAD on system about'             | 'HEAD'    | '/api/system/about'
        42  | 'OPTIONS on system about'          | 'OPTIONS' | '/api/system/about'
        43  | 'GET on the client admin'          | 'GET'     | '/api/api-clients'
        44  | 'POST on the client admin'         | 'POST'    | '/api/api-clients'
        45  | 'PUT on the client admin'          | 'PUT'     | '/api/api-clients'
        46  | 'DELETE on the client admin'       | 'DELETE'  | '/api/api-clients'
        47  | 'PATCH on the client admin'        | 'PATCH'   | '/api/api-clients'
        48  | 'HEAD on the client admin'         | 'HEAD'    | '/api/api-clients'
        49  | 'OPTIONS on the client admin'      | 'OPTIONS' | '/api/api-clients'
        50  | 'GET on a client key'              | 'GET'     | '/api/api-clients/7/key'
        51  | 'POST on a client key'             | 'POST'    | '/api/api-clients/7/key'
        52  | 'PUT on a client key'              | 'PUT'     | '/api/api-clients/7/key'
        53  | 'DELETE on a client key'           | 'DELETE'  | '/api/api-clients/7/key'
        54  | 'PATCH on a client key'            | 'PATCH'   | '/api/api-clients/7/key'
        55  | 'HEAD on a client key'             | 'HEAD'    | '/api/api-clients/7/key'
        56  | 'OPTIONS on a client key'          | 'OPTIONS' | '/api/api-clients/7/key'
        57  | 'GET on quick launch'              | 'GET'     | '/api/quick-launch'
        58  | 'POST on quick launch'             | 'POST'    | '/api/quick-launch'
        59  | 'PUT on quick launch'              | 'PUT'     | '/api/quick-launch'
        60  | 'DELETE on quick launch'           | 'DELETE'  | '/api/quick-launch'
        61  | 'PATCH on quick launch'            | 'PATCH'   | '/api/quick-launch'
        62  | 'HEAD on quick launch'             | 'HEAD'    | '/api/quick-launch'
        63  | 'OPTIONS on quick launch'          | 'OPTIONS' | '/api/quick-launch'
        64  | 'GET on view state'                | 'GET'     | '/api/view-state'
        65  | 'POST on view state'               | 'POST'    | '/api/view-state'
        66  | 'PUT on view state'                | 'PUT'     | '/api/view-state'
        67  | 'DELETE on view state'             | 'DELETE'  | '/api/view-state'
        68  | 'PATCH on view state'              | 'PATCH'   | '/api/view-state'
        69  | 'HEAD on view state'               | 'HEAD'    | '/api/view-state'
        70  | 'OPTIONS on view state'            | 'OPTIONS' | '/api/view-state'
        71  | 'GET on pdf generic'               | 'GET'     | '/api/pdf/generic'
        72  | 'POST on pdf generic'              | 'POST'    | '/api/pdf/generic'
        73  | 'PUT on pdf generic'               | 'PUT'     | '/api/pdf/generic'
        74  | 'DELETE on pdf generic'            | 'DELETE'  | '/api/pdf/generic'
        75  | 'PATCH on pdf generic'             | 'PATCH'   | '/api/pdf/generic'
        76  | 'HEAD on pdf generic'              | 'HEAD'    | '/api/pdf/generic'
        77  | 'OPTIONS on pdf generic'           | 'OPTIONS' | '/api/pdf/generic'
        78  | 'GET on file categories'           | 'GET'     | '/api/file-categories'
        79  | 'POST on file categories'          | 'POST'    | '/api/file-categories'
        80  | 'PUT on file categories'           | 'PUT'     | '/api/file-categories'
        81  | 'DELETE on file categories'        | 'DELETE'  | '/api/file-categories'
        82  | 'PATCH on file categories'         | 'PATCH'   | '/api/file-categories'
        83  | 'HEAD on file categories'          | 'HEAD'    | '/api/file-categories'
        84  | 'OPTIONS on file categories'       | 'OPTIONS' | '/api/file-categories'
        85  | 'GET on the root'                  | 'GET'     | '/'
        86  | 'POST on the root'                 | 'POST'    | '/'
        87  | 'PUT on the root'                  | 'PUT'     | '/'
        88  | 'DELETE on the root'               | 'DELETE'  | '/'
        89  | 'PATCH on the root'                | 'PATCH'   | '/'
        90  | 'HEAD on the root'                 | 'HEAD'    | '/'
        91  | 'OPTIONS on the root'              | 'OPTIONS' | '/'
        92  | 'GET on the dashboard page'        | 'GET'     | '/index.html'
        93  | 'POST on the dashboard page'       | 'POST'    | '/index.html'
        94  | 'PUT on the dashboard page'        | 'PUT'     | '/index.html'
        95  | 'DELETE on the dashboard page'     | 'DELETE'  | '/index.html'
        96  | 'PATCH on the dashboard page'      | 'PATCH'   | '/index.html'
        97  | 'HEAD on the dashboard page'       | 'HEAD'    | '/index.html'
        98  | 'OPTIONS on the dashboard page'    | 'OPTIONS' | '/index.html'
        99  | 'GET on the solutions page'        | 'GET'     | '/solutions/index.html'
        100 | 'POST on the solutions page'       | 'POST'    | '/solutions/index.html'
        101 | 'PUT on the solutions page'        | 'PUT'     | '/solutions/index.html'
        102 | 'DELETE on the solutions page'     | 'DELETE'  | '/solutions/index.html'
        103 | 'PATCH on the solutions page'      | 'PATCH'   | '/solutions/index.html'
        104 | 'HEAD on the solutions page'       | 'HEAD'    | '/solutions/index.html'
        105 | 'OPTIONS on the solutions page'    | 'OPTIONS' | '/solutions/index.html'
        106 | 'GET on app.js'                    | 'GET'     | '/app.js'
        107 | 'POST on app.js'                   | 'POST'    | '/app.js'
        108 | 'PUT on app.js'                    | 'PUT'     | '/app.js'
        109 | 'DELETE on app.js'                 | 'DELETE'  | '/app.js'
        110 | 'PATCH on app.js'                  | 'PATCH'   | '/app.js'
        111 | 'HEAD on app.js'                   | 'HEAD'    | '/app.js'
        112 | 'OPTIONS on app.js'                | 'OPTIONS' | '/app.js'
        113 | 'GET on a longer name'             | 'GET'     | '/api/solutionsx'
        114 | 'POST on a longer name'            | 'POST'    | '/api/solutionsx'
        115 | 'PUT on a longer name'             | 'PUT'     | '/api/solutionsx'
        116 | 'DELETE on a longer name'          | 'DELETE'  | '/api/solutionsx'
        117 | 'PATCH on a longer name'           | 'PATCH'   | '/api/solutionsx'
        118 | 'HEAD on a longer name'            | 'HEAD'    | '/api/solutionsx'
        119 | 'OPTIONS on a longer name'         | 'OPTIONS' | '/api/solutionsx'
        120 | 'GET on upper-case API'            | 'GET'     | '/API/solutions/1'
        121 | 'POST on upper-case API'           | 'POST'    | '/API/solutions/1'
        122 | 'PUT on upper-case API'            | 'PUT'     | '/API/solutions/1'
        123 | 'DELETE on upper-case API'         | 'DELETE'  | '/API/solutions/1'
        124 | 'PATCH on upper-case API'          | 'PATCH'   | '/API/solutions/1'
        125 | 'HEAD on upper-case API'           | 'HEAD'    | '/API/solutions/1'
        126 | 'OPTIONS on upper-case API'        | 'OPTIONS' | '/API/solutions/1'
        127 | 'GET on a capitalized subtree'     | 'GET'     | '/api/Solutions/1'
        128 | 'POST on a capitalized subtree'    | 'POST'    | '/api/Solutions/1'
        129 | 'PUT on a capitalized subtree'     | 'PUT'     | '/api/Solutions/1'
        130 | 'DELETE on a capitalized subtree'  | 'DELETE'  | '/api/Solutions/1'
        131 | 'PATCH on a capitalized subtree'   | 'PATCH'   | '/api/Solutions/1'
        132 | 'HEAD on a capitalized subtree'    | 'HEAD'    | '/api/Solutions/1'
        133 | 'OPTIONS on a capitalized subtree' | 'OPTIONS' | '/api/Solutions/1'
    }

    @Unroll
    def "#seq. filterChain should never reach anything when a client sends the raw URI #scenario"() {
        // Spring Security's firewall turns most of these away first (400); whatever gets past it
        // meets the fence (403). Either way the probe -- which answers any path -- is never reached.

        when:
        MockHttpServletResponse res = mvc.perform(get('/api/solutions/1').with({ r -> r.requestURI = raw; r } as RequestPostProcessor)
                .header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andReturn().response

        then:
        (0..1) * apiClients.authenticate('rex', 'k') >> REX
        res.status in [400, 403]
        !res.contentAsString.contains('principal')

        where:
        seq | scenario                       | raw
        1   | 'a climb to login'             | '/api/solutions/../auth/login'
        2   | 'encoded dots'                 | '/api/solutions/%2e%2e/auth/login'
        3   | 'upper-case encoded dots'      | '/api/solutions/%2E%2E/system/about'
        4   | 'encoded slashes'              | '/api/solutions%2f..%2fauth'
        5   | 'a doubled slash'              | '/api/solutions//1'
        6   | 'a doubled leading slash'      | '//api/solutions/1'
        7   | 'backslashes'                  | '/api/solutions\\..\\auth'
        8   | 'a session id parameter'       | '/api/solutions/1;jsessionid=x'
        9   | 'a matrix climb'               | '/api/solutions;/../auth/login'
        10  | 'an encoded NUL'               | '/api/solutions/%00'
        11  | 'a space'                      | '/api/solutions/ 1'
        12  | 'a non-ASCII letter'           | '/api/solutions/\u00e9'
        13  | 'a climb with a matrix'        | '/api/solutions/..;/api-clients'
    }

    def "filterChain should answer 401 and never become the service when a client also sends the house key"() {

        when:
        MockHttpServletResponse res = mvc.perform(get('/api/solutions/1').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k')
                .header('X-Echo-Key', SERVICE_KEY))
            .andExpect(status().isUnauthorized()).andReturn().response

        then:
        0 * apiClients.authenticate(*_)
        res.contentAsString == '{"ok":false,"error":"An API client may not present any other credential."}'
    }

    def "filterChain should answer 401 when a client also sends a person's Bearer token"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1')).header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isUnauthorized())

        then:
        0 * apiClients.authenticate(*_)
    }

    def "filterChain should answer 401 when a client with a wrong key calls even an open path"() {

        when:
        mvc.perform(post('/api/auth/login').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'bad'))
            .andExpect(status().isUnauthorized())
            .andExpect(content().json('{"ok":false,"error":"Unknown API client or wrong key."}', true))

        then:
        1 * apiClients.authenticate('rex', 'bad') >> null
    }

    @Unroll
    def "filterChain should treat the request as a client request when only #scenario is sent to a static page"() {

        expect:
        mvc.perform(get('/index.html').header(headerName, 'x')).andExpect(status().isUnauthorized())

        where:
        scenario               | headerName
        'the client code'      | 'X-Echo-Client'
        'the client key'       | 'X-Echo-Client-Key'
    }

    def "filterChain should answer 429 when eleven wrong keys come through the real chain"() {

        given:
        apiClients.authenticate('rex', 'bad') >> null

        when:
        10.times {
            mvc.perform(get('/api/solutions/1').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'bad'))
                .andExpect(status().isUnauthorized())
        }

        then:
        mvc.perform(get('/api/solutions/1').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isTooManyRequests())
        mvc.perform(asUser(get('/api/solutions/1'))).andExpect(status().isOk())
    }

    def "filterChain should turn a disabled client away when the service answers null for it"() {

        when:
        mvc.perform(get('/api/solutions/1').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isUnauthorized())

        then:
        1 * apiClients.authenticate('rex', 'k') >> null
    }

    @Unroll
    def "the firewall should turn TRACE away before any filter when the caller is #scenario"() {

        given:
        MockHttpServletRequestBuilder b = request(HttpMethod.TRACE, path)
        if (kind == 'client') b.header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k')
        if (kind == 'person') asUser(b)

        when:
        mvc.perform(b).andExpect(status().isBadRequest())

        then:
        0 * apiClients._

        where:
        scenario     | kind     | path
        'a client'   | 'client' | '/api/solutions/1'
        'a person'   | 'person' | '/api/solutions/1'
        'nobody'     | 'nobody' | '/index.html'
    }

    def "filterChain should answer JSON from both the 401 entry point and the client refusals when they refuse"() {

        expect:
        mvc.perform(get('/api/ping')).andExpect(header().string('Content-Type', 'application/json;charset=UTF-8'))
        mvc.perform(get('/api/ping').header('X-Echo-Client', 'x').header('X-Echo-Client-Key', 'y'))
            .andExpect(header().string('Content-Type', 'application/json;charset=UTF-8'))
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder headerRequest(String kind) {
        switch (kind) {
            case 'open': return get('/index.html')
            case 'https': return get('/index.html').secure(true)
            case 'unauthorized': return get('/api/ping')
            case 'signed-in': return get('/api/solutions/1')
            case 'client-refused': return get('/api/auth/login').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'bad')
            default: throw new IllegalArgumentException(kind)
        }
    }
}
