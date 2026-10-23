package com.echo

// Story 5653: the API-client door, driven directly with mock servlet objects. Every way a
// client request can end (429 / 401 / 403 / the controller), what is logged, the throttle,
// and the address the throttle keys on.

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

import com.echo.ApiClientModels.Credential

import spock.lang.PendingFeature
import spock.lang.Unroll

class ApiClientAuthFilterSpec extends EchoSpockBaseSpec {

    ApiClientService clients = Mock()
    ApiClientAuthFilter filter = new ApiClientAuthFilter(clients)
    FilterChain chain = Mock()

    private static final Credential REX = new Credential(7, 'rex', 'hash', true, '/api/solutions/')

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    // ---- not a client request -------------------------------------------------------------

    def "doFilter should pass straight through, untouched and unlogged, when neither client header is sent"() {

        given:
        MockHttpServletRequest req = request('GET', '/api/auth/login', ['Authorization': 'Bearer x', 'X-Echo-Key': 'y'])
        Authentication seen = null
        Object attr = 'unset'

        when:
        filter.doFilter(req, new MockHttpServletResponse(), chain)

        then:
        1 * chain.doFilter(_, _) >> { ServletRequest q, ServletResponse s ->
            seen = SecurityContextHolder.context.authentication
            attr = q.getAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE)
        }
        seen == null
        attr == null
        0 * clients._
    }

    // ---- either header makes it a client request --------------------------------------------

    @Unroll
    def "doFilter should treat the request as a client request when #scenario"() {

        given:
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        filter.doFilter(request('GET', '/api/solutions/1', client(code, key)), resp, chain)

        then:
        1 * clients.authenticate(code, key) >> null
        1 * clients.logAccess(null, shown, '/api/solutions/1', 'GET', 401, false, '10.0.0.5')
        0 * chain._
        resp.status == 401
        resp.contentType.startsWith('application/json')
        resp.contentAsString == '{"ok":false,"error":"Unknown API client or wrong key."}'

        where:
        scenario                              | code      | key  || shown
        'only the code is sent'               | 'rex'     | null || 'rex'
        'only the key is sent'                | null      | 'k'  || ''
        'the code is padded and mixed case'   | '  ReX  ' | 'k'  || 'rex'
        'both headers are empty'              | ''        | ''   || ''
    }

    @Unroll
    def "doFilter should answer 401 without authenticating when the client also sends #scenario"() {

        given:
        Map<String, String> h = client('rex', 'k')
        h[header] = value
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        filter.doFilter(request('GET', '/api/solutions/1', h), resp, chain)

        then:
        0 * clients.authenticate(*_)
        1 * clients.logAccess(null, 'rex', '/api/solutions/1', 'GET', 401, false, '10.0.0.5')
        0 * chain._
        resp.status == 401
        resp.contentAsString == '{"ok":false,"error":"An API client may not present any other credential."}'
        SecurityContextHolder.context.authentication == null

        where:
        scenario                         | header          | value
        'the house key'                  | 'X-Echo-Key'    | 'test-service-key'
        'an empty house key'             | 'X-Echo-Key'    | ''
        'a Bearer token'                 | 'Authorization' | 'Bearer abc'
        'an empty Authorization header'  | 'Authorization' | ''
        'a lower-case Basic header'      | 'authorization' | 'Basic eDp5'
    }

    def "doFilter should not count a mixed-credential refusal toward the throttle when it repeats"() {

        given:
        clients.authenticate('rex', 'k') >> REX
        Map<String, String> h = client('rex', 'k')
        h['X-Echo-Key'] = 'x'
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        20.times { filter.doFilter(request('GET', '/api/solutions/1', h), new MockHttpServletResponse(), Mock(FilterChain)) }
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), resp, chain)

        then:
        1 * chain.doFilter(_, _)
        resp.status == 200
    }

    def "doFilter should answer 401, log it and never run the chain when the key is wrong"() {

        given:
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        filter.doFilter(request('POST', '/api/solutions/1', client('rex', 'wrong')), resp, chain)

        then:
        1 * clients.authenticate('rex', 'wrong') >> null
        1 * clients.logAccess(null, 'rex', '/api/solutions/1', 'POST', 401, false, '10.0.0.5')
        0 * chain._
        resp.status == 401
        SecurityContextHolder.context.authentication == null
    }

    // ---- the fence ------------------------------------------------------------------------

    @Unroll
    def "#seq. doFilter should answer 403 and log the client id when #scenario"() {

        given:
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        filter.doFilter(request(method, uri, client('rex', 'k')), resp, chain)

        then:
        1 * clients.authenticate('rex', 'k') >> REX
        1 * clients.logAccess(7, 'rex', uri, method, 403, false, '10.0.0.5')
        0 * chain._
        resp.status == 403
        resp.contentAsString == '{"ok":false,"error":"This API client may not reach that path."}'
        SecurityContextHolder.context.authentication == null

        where:
        seq | scenario                              | method    | uri
        1   | 'GET on login'                  | 'GET'     | '/api/auth/login'
        2   | 'POST on login'                 | 'POST'    | '/api/auth/login'
        3   | 'PUT on login'                  | 'PUT'     | '/api/auth/login'
        4   | 'DELETE on login'               | 'DELETE'  | '/api/auth/login'
        5   | 'PATCH on login'                | 'PATCH'   | '/api/auth/login'
        6   | 'HEAD on login'                 | 'HEAD'    | '/api/auth/login'
        7   | 'OPTIONS on login'              | 'OPTIONS' | '/api/auth/login'
        8   | 'TRACE on login'                | 'TRACE'   | '/api/auth/login'
        9   | 'GET on the client admin'       | 'GET'     | '/api/api-clients'
        10  | 'POST on the client admin'      | 'POST'    | '/api/api-clients'
        11  | 'PUT on the client admin'       | 'PUT'     | '/api/api-clients'
        12  | 'DELETE on the client admin'    | 'DELETE'  | '/api/api-clients'
        13  | 'PATCH on the client admin'     | 'PATCH'   | '/api/api-clients'
        14  | 'HEAD on the client admin'      | 'HEAD'    | '/api/api-clients'
        15  | 'OPTIONS on the client admin'   | 'OPTIONS' | '/api/api-clients'
        16  | 'TRACE on the client admin'     | 'TRACE'   | '/api/api-clients'
        17  | 'GET on system about'           | 'GET'     | '/api/system/about'
        18  | 'POST on system about'          | 'POST'    | '/api/system/about'
        19  | 'PUT on system about'           | 'PUT'     | '/api/system/about'
        20  | 'DELETE on system about'        | 'DELETE'  | '/api/system/about'
        21  | 'PATCH on system about'         | 'PATCH'   | '/api/system/about'
        22  | 'HEAD on system about'          | 'HEAD'    | '/api/system/about'
        23  | 'OPTIONS on system about'       | 'OPTIONS' | '/api/system/about'
        24  | 'TRACE on system about'         | 'TRACE'   | '/api/system/about'
        25  | 'GET on quick launch'           | 'GET'     | '/api/quick-launch'
        26  | 'POST on quick launch'          | 'POST'    | '/api/quick-launch'
        27  | 'PUT on quick launch'           | 'PUT'     | '/api/quick-launch'
        28  | 'DELETE on quick launch'        | 'DELETE'  | '/api/quick-launch'
        29  | 'PATCH on quick launch'         | 'PATCH'   | '/api/quick-launch'
        30  | 'HEAD on quick launch'          | 'HEAD'    | '/api/quick-launch'
        31  | 'OPTIONS on quick launch'       | 'OPTIONS' | '/api/quick-launch'
        32  | 'TRACE on quick launch'         | 'TRACE'   | '/api/quick-launch'
        33  | 'GET on the dashboard page'     | 'GET'     | '/index.html'
        34  | 'POST on the dashboard page'    | 'POST'    | '/index.html'
        35  | 'PUT on the dashboard page'     | 'PUT'     | '/index.html'
        36  | 'DELETE on the dashboard page'  | 'DELETE'  | '/index.html'
        37  | 'PATCH on the dashboard page'   | 'PATCH'   | '/index.html'
        38  | 'HEAD on the dashboard page'    | 'HEAD'    | '/index.html'
        39  | 'OPTIONS on the dashboard page' | 'OPTIONS' | '/index.html'
        40  | 'TRACE on the dashboard page'   | 'TRACE'   | '/index.html'
        41  | 'GET on the root'               | 'GET'     | '/'
        42  | 'POST on the root'              | 'POST'    | '/'
        43  | 'PUT on the root'               | 'PUT'     | '/'
        44  | 'DELETE on the root'            | 'DELETE'  | '/'
        45  | 'PATCH on the root'             | 'PATCH'   | '/'
        46  | 'HEAD on the root'              | 'HEAD'    | '/'
        47  | 'OPTIONS on the root'           | 'OPTIONS' | '/'
        48  | 'TRACE on the root'             | 'TRACE'   | '/'
        49  | 'GET on a climb to login'       | 'GET'     | '/api/solutions/../auth/login'
        50  | 'POST on a climb to login'      | 'POST'    | '/api/solutions/../auth/login'
        51  | 'PUT on a climb to login'       | 'PUT'     | '/api/solutions/../auth/login'
        52  | 'DELETE on a climb to login'    | 'DELETE'  | '/api/solutions/../auth/login'
        53  | 'PATCH on a climb to login'     | 'PATCH'   | '/api/solutions/../auth/login'
        54  | 'HEAD on a climb to login'      | 'HEAD'    | '/api/solutions/../auth/login'
        55  | 'OPTIONS on a climb to login'   | 'OPTIONS' | '/api/solutions/../auth/login'
        56  | 'TRACE on a climb to login'     | 'TRACE'   | '/api/solutions/../auth/login'
        57  | 'GET on encoded dots'           | 'GET'     | '/api/solutions/%2e%2e/x'
        58  | 'POST on encoded dots'          | 'POST'    | '/api/solutions/%2e%2e/x'
        59  | 'PUT on encoded dots'           | 'PUT'     | '/api/solutions/%2e%2e/x'
        60  | 'DELETE on encoded dots'        | 'DELETE'  | '/api/solutions/%2e%2e/x'
        61  | 'PATCH on encoded dots'         | 'PATCH'   | '/api/solutions/%2e%2e/x'
        62  | 'HEAD on encoded dots'          | 'HEAD'    | '/api/solutions/%2e%2e/x'
        63  | 'OPTIONS on encoded dots'       | 'OPTIONS' | '/api/solutions/%2e%2e/x'
        64  | 'TRACE on encoded dots'         | 'TRACE'   | '/api/solutions/%2e%2e/x'
        65  | 'GET on upper-case API'         | 'GET'     | '/API/solutions/1'
        66  | 'POST on upper-case API'        | 'POST'    | '/API/solutions/1'
        67  | 'PUT on upper-case API'         | 'PUT'     | '/API/solutions/1'
        68  | 'DELETE on upper-case API'      | 'DELETE'  | '/API/solutions/1'
        69  | 'PATCH on upper-case API'       | 'PATCH'   | '/API/solutions/1'
        70  | 'HEAD on upper-case API'        | 'HEAD'    | '/API/solutions/1'
        71  | 'OPTIONS on upper-case API'     | 'OPTIONS' | '/API/solutions/1'
        72  | 'TRACE on upper-case API'       | 'TRACE'   | '/API/solutions/1'
        73  | 'GET on a doubled slash'        | 'GET'     | '/api/solutions//1'
        74  | 'POST on a doubled slash'       | 'POST'    | '/api/solutions//1'
        75  | 'PUT on a doubled slash'        | 'PUT'     | '/api/solutions//1'
        76  | 'DELETE on a doubled slash'     | 'DELETE'  | '/api/solutions//1'
        77  | 'PATCH on a doubled slash'      | 'PATCH'   | '/api/solutions//1'
        78  | 'HEAD on a doubled slash'       | 'HEAD'    | '/api/solutions//1'
        79  | 'OPTIONS on a doubled slash'    | 'OPTIONS' | '/api/solutions//1'
        80  | 'TRACE on a doubled slash'      | 'TRACE'   | '/api/solutions//1'
        81  | 'GET on a longer name'          | 'GET'     | '/api/solutionsx'
        82  | 'POST on a longer name'         | 'POST'    | '/api/solutionsx'
        83  | 'PUT on a longer name'          | 'PUT'     | '/api/solutionsx'
        84  | 'DELETE on a longer name'       | 'DELETE'  | '/api/solutionsx'
        85  | 'PATCH on a longer name'        | 'PATCH'   | '/api/solutionsx'
        86  | 'HEAD on a longer name'         | 'HEAD'    | '/api/solutionsx'
        87  | 'OPTIONS on a longer name'      | 'OPTIONS' | '/api/solutionsx'
        88  | 'TRACE on a longer name'        | 'TRACE'   | '/api/solutionsx'
    }

    @Unroll
    def "#seq. doFilter should reach the chain as client:rex when #scenario"() {

        given:
        MockHttpServletResponse resp = new MockHttpServletResponse()
        Authentication seen = null
        Object attr = null

        when:
        filter.doFilter(request(method, '/api/solutions/9', client('rex', 'k')), resp, chain)

        then:
        1 * clients.authenticate('rex', 'k') >> REX
        1 * chain.doFilter(_, _) >> { ServletRequest q, ServletResponse s ->
            seen = SecurityContextHolder.context.authentication
            attr = q.getAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE)
            ((MockHttpServletResponse) s).status = 204
        }
        seen.name == 'client:rex'
        seen.authorities*.authority == ['API_CLIENT']
        seen.credentials == null
        attr.is(REX)
        1 * clients.logAccess(7, 'rex', '/api/solutions/9', method, 204, true, '10.0.0.5')

        where:
        seq | scenario               | method
        1   | 'the method is GET'    | 'GET'
        2   | 'the method is POST'   | 'POST'
        3   | 'the method is PUT'    | 'PUT'
        4   | 'the method is DELETE' | 'DELETE'
        5   | 'the method is PATCH'  | 'PATCH'
        6   | 'the method is HEAD'   | 'HEAD'
        7   | 'the method is OPTIONS'| 'OPTIONS'
        8   | 'the method is TRACE'  | 'TRACE'
    }

    def "doFilter should still log an allowed request when the controller throws"() {

        when:
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), new MockHttpServletResponse(), chain)

        then:
        1 * clients.authenticate('rex', 'k') >> REX
        1 * chain.doFilter(_, _) >> { throw new ServletException('boom') }
        thrown(ServletException)
        1 * clients.logAccess(7, 'rex', '/api/solutions/1', 'GET', 200, true, '10.0.0.5')
    }

    def "doFilter should name the principal by the stored client code when the header is spelled differently"() {

        given:
        Authentication seen = null

        when:
        filter.doFilter(request('GET', '/api/solutions/1', client('REX', 'k')), new MockHttpServletResponse(), chain)

        then:
        1 * clients.authenticate('REX', 'k') >> REX
        1 * chain.doFilter(_, _) >> { ServletRequest q, ServletResponse s -> seen = SecurityContextHolder.context.authentication }
        seen.name == 'client:rex'
    }

    @Unroll
    def "doFilter should answer 403 for every path when the client's own allowlist is #scenario"() {

        given:
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), resp, chain)

        then:
        1 * clients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, allow)
        0 * chain._
        resp.status == 403

        where:
        scenario          | allow
        'null'            | null
        'empty'           | ''
        'the root'        | '/'
        'the whole api'   | '/api/'
    }

    // ---- the throttle --------------------------------------------------------------------

    def "doFilter should answer 429 even with the right key when one address has failed ten times"() {

        given:
        clients.authenticate('rex', 'wrong') >> null
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        List<Integer> statuses = failTimes(filter, 10, '10.0.0.5')
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), resp, chain)

        then:
        statuses.every { it == 401 }
        0 * clients.authenticate('rex', 'k')
        1 * clients.logAccess(null, 'rex', '/api/solutions/1', 'GET', 429, false, '10.0.0.5')
        0 * chain._
        resp.status == 429
        resp.contentAsString == '{"ok":false,"error":"Too many failed attempts. Try again later."}'
    }

    def "doFilter should not lock the address out when it has failed nine times"() {

        given:
        clients.authenticate('rex', 'wrong') >> null
        clients.authenticate('rex', 'k') >> REX

        when:
        failTimes(filter, 9, '10.0.0.5')
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), new MockHttpServletResponse(), chain)

        then:
        1 * chain.doFilter(_, _)
    }

    def "doFilter should let another address through when one address is locked out"() {

        given:
        clients.authenticate('rex', 'wrong') >> null
        clients.authenticate('rex', 'k') >> REX
        MockHttpServletRequest other = request('GET', '/api/solutions/1', client('rex', 'k'))
        other.remoteAddr = '10.0.0.6'

        when:
        failTimes(filter, 10, '10.0.0.5')
        filter.doFilter(other, new MockHttpServletResponse(), chain)

        then:
        1 * chain.doFilter(_, _)
    }

    def "doFilter should answer 429 before anything else when a locked-out address also sends a house credential"() {

        given:
        clients.authenticate('rex', 'wrong') >> null
        Map<String, String> h = client('rex', 'k')
        h['X-Echo-Key'] = 'x'
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        failTimes(filter, 10, '10.0.0.5')
        filter.doFilter(request('GET', '/api/solutions/1', h), resp, chain)

        then:
        resp.status == 429
    }

    def "doFilter should never throttle a non-client request when it comes from a locked-out address"() {

        given:
        clients.authenticate('rex', 'wrong') >> null

        when:
        failTimes(filter, 10, '10.0.0.5')
        filter.doFilter(request('GET', '/api/solutions/1', [:]), new MockHttpServletResponse(), chain)

        then:
        1 * chain.doFilter(_, _)
    }

    def "resetThrottle should forget every failure when called"() {

        given:
        clients.authenticate('rex', 'wrong') >> null
        clients.authenticate('rex', 'k') >> REX

        when:
        failTimes(filter, 10, '10.0.0.5')
        filter.resetThrottle()
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), new MockHttpServletResponse(), chain)

        then:
        1 * chain.doFilter(_, _)
    }

    def "doFilter should forget a lockout when it is older than the fifteen-minute window"() {

        given:
        clients.authenticate('rex', 'k') >> REX
        Map<String, long[]> failures = filter.@failures
        failures['10.0.0.5'] = [10L, System.currentTimeMillis() - 15 * 60 * 1000L - 1000L] as long[]

        when:
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), new MockHttpServletResponse(), chain)

        then:
        1 * chain.doFilter(_, _)
        !failures.containsKey('10.0.0.5')
    }

    def "doFilter should hold a lockout when it is inside the window"() {

        given:
        Map<String, long[]> failures = filter.@failures
        failures['10.0.0.5'] = [10L, System.currentTimeMillis() - 14 * 60 * 1000L] as long[]
        MockHttpServletResponse resp = new MockHttpServletResponse()

        when:
        filter.doFilter(request('GET', '/api/solutions/1', client('rex', 'k')), resp, chain)

        then:
        resp.status == 429
        0 * clients.authenticate(*_)
    }

    def "recordFailure should start a fresh count of one when the window has passed"() {

        given:
        Map<String, long[]> failures = filter.@failures
        failures['10.0.0.5'] = [9L, System.currentTimeMillis() - 16 * 60 * 1000L] as long[]

        when:
        filter.recordFailure('10.0.0.5')

        then:
        failures['10.0.0.5'][0] == 1L
        System.currentTimeMillis() - failures['10.0.0.5'][1] < 5000
    }

    def "recordFailure should add to the count and keep the window start when inside the window"() {

        given:
        long start = System.currentTimeMillis() - 60_000L
        Map<String, long[]> failures = filter.@failures
        failures['10.0.0.5'] = [3L, start] as long[]

        when:
        filter.recordFailure('10.0.0.5')

        then:
        failures['10.0.0.5'][0] == 4L
        failures['10.0.0.5'][1] == start
    }

    def "recordFailure should clear the table rather than grow it when more than 5000 addresses fail"() {

        given:
        Map<String, long[]> failures = filter.@failures

        when:
        5000.times { int n -> filter.recordFailure('ip-' + n) }

        then:
        failures.size() == 5000

        when:
        filter.recordFailure('ip-one-too-many')

        then:
        failures.isEmpty()
    }

    // ---- which address -------------------------------------------------------------------

    @Unroll
    def "#seq. doFilter should log and throttle the address #expected when #scenario"() {

        given:
        Map<String, String> h = client('rex', 'k')
        if (cf != null) h['CF-Connecting-IP'] = cf
        if (xff != null) h['X-Forwarded-For'] = xff

        when:
        filter.doFilter(request('GET', '/api/solutions/1', h), new MockHttpServletResponse(), chain)

        then:
        1 * clients.authenticate('rex', 'k') >> null
        1 * clients.logAccess(null, 'rex', '/api/solutions/1', 'GET', 401, false, expected)

        where:
        seq | scenario                                  | cf          | xff                  || expected
        1   | 'no proxy header is sent'                 | null        | null                 || '10.0.0.5'
        2   | 'Cloudflare and a forward list are sent'  | '1.2.3.4'   | '5.6.7.8'            || '1.2.3.4'
        3   | 'the Cloudflare header is padded'         | ' 1.2.3.4 ' | null                 || '1.2.3.4'
        4   | 'the Cloudflare header is blank'          | '   '       | '5.6.7.8'            || '5.6.7.8'
        5   | 'the forward list has two entries'        | ''          | '5.6.7.8, 9.9.9.9'   || '5.6.7.8'
        6   | 'the forward list entry is padded'        | null        | ' 5.6.7.8 ,9.9.9.9'  || '5.6.7.8'
        7   | 'the forward list is blank'               | null        | '   '                || '10.0.0.5'
        8   | 'the forward list starts with a comma'    | null        | ',9.9.9.9'           || ',9.9.9.9'
    }

    @PendingFeature(reason = 'BUG: the throttle keys on CF-Connecting-IP / X-Forwarded-For, which any caller can set, so rotating the header escapes the 10-failure lockout')
    def "doFilter should answer 429 when a forged CF-Connecting-IP header is rotated"() {

        given:
        clients.authenticate('rex', 'wrong') >> null
        MockHttpServletResponse last = null

        when:
        12.times { int n ->
            Map<String, String> h = client('rex', 'wrong')
            h['CF-Connecting-IP'] = '203.0.113.' + n
            last = new MockHttpServletResponse()
            filter.doFilter(request('GET', '/api/solutions/1', h), last, Mock(FilterChain))
        }

        then:
        last.status == 429
    }

    // ---- constants ---------------------------------------------------------------------------

    def "the constants should be the documented header names and authority when read"() {

        expect:
        ApiClientAuthFilter.HEADER_CLIENT == 'X-Echo-Client'
        ApiClientAuthFilter.HEADER_KEY == 'X-Echo-Client-Key'
        ApiClientAuthFilter.AUTHORITY == 'API_CLIENT'
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MockHttpServletRequest request(String method, String uri, Map<String, String> headers) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri)
        r.remoteAddr = '10.0.0.5'
        headers.each { String k, String v -> r.addHeader(k, v) }
        r
    }

    private static Map<String, String> client(String code, String key) {
        Map<String, String> m = [:]
        if (code != null) m[ApiClientAuthFilter.HEADER_CLIENT] = code
        if (key != null) m[ApiClientAuthFilter.HEADER_KEY] = key
        m
    }

    /** Sends n wrong-key requests from one address and answers their statuses. */
    private static List<Integer> failTimes(ApiClientAuthFilter f, int n, String ip) {
        List<Integer> out = []
        n.times {
            MockHttpServletRequest r = request('GET', '/api/solutions/1', client('rex', 'wrong'))
            r.remoteAddr = ip
            MockHttpServletResponse resp = new MockHttpServletResponse()
            f.doFilter(r, resp, { ServletRequest q, ServletResponse s -> } as FilterChain)
            out << resp.status
        }
        out
    }
}
