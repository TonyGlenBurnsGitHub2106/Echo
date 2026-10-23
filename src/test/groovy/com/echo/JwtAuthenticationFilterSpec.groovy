package com.echo

// Story 5653: the two ordinary ways in -- the service key (principal "service") and a
// signed-in person's Bearer access token (principal = user name).

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

import spock.lang.Unroll

class JwtAuthenticationFilterSpec extends EchoSpockBaseSpec {

    private static final String KEY = 'test-service-key'
    private static final JwtService JWT = new JwtService('test-secret-test-secret-test-secret-0123456789', 15, 30)

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "HEADER_SERVICE_KEY should be X-Echo-Key when read"() {

        expect:
        JwtAuthenticationFilter.HEADER_SERVICE_KEY == 'X-Echo-Key'
    }

    def "doFilterInternal should leave the request anonymous and run the chain when no credential is sent"() {

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), [:]) == null
    }

    @Unroll
    def "doFilterInternal should sign in as service when the key is #scenario"() {

        when:
        Authentication a = run(new JwtAuthenticationFilter(JWT, KEY), ['X-Echo-Key': presented])

        then:
        a.name == 'service'
        a.authorities.isEmpty()
        a.credentials == null

        where:
        scenario                 | presented
        'exact'                  | 'test-service-key'
        'padded with spaces'     | '  test-service-key  '
        'followed by a tab'      | 'test-service-key\t'
    }

    @Unroll
    def "doFilterInternal should ignore the key when it is #scenario"() {

        given:
        String presented = value == 'LONG' ? getReplicatedString('x', 500) : value

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['X-Echo-Key': presented]) == null

        where:
        scenario                 | value
        'empty'                  | ''
        'blank'                  | ' '
        'one character short'    | 'test-service-ke'
        'one character long'     | 'test-service-keyy'
        'upper case'             | 'TEST-SERVICE-KEY'
        'spaced'                 | 'test service key'
        'five hundred characters'| 'LONG'
    }

    @Unroll
    def "doFilterInternal should accept no presented key when the configured key is #scenario"() {

        given:
        JwtAuthenticationFilter f = new JwtAuthenticationFilter(JWT, configured)

        expect:
        run(f, ['X-Echo-Key': '']) == null
        run(f, ['X-Echo-Key': '   ']) == null
        run(f, ['X-Echo-Key': 'anything']) == null

        where:
        scenario  | configured
        'null'    | null
        'empty'   | ''
        'blank'   | '   '
    }

    def "doFilterInternal should trim the configured key when it is padded"() {

        expect:
        run(new JwtAuthenticationFilter(JWT, '  ' + KEY + '  '), ['X-Echo-Key': KEY]).name == 'service'
    }

    def "doFilterInternal should sign in as the user name when a valid access token is sent"() {

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['Authorization': 'Bearer ' + JWT.issueAccess(user('alice'))]).name == 'alice'
    }

    @Unroll
    def "#seq. doFilterInternal should ignore the Authorization header when it is #scenario"() {

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['Authorization': header]) == null

        where:
        seq | scenario                     | header
        1   | 'the bare scheme'            | 'Bearer'
        2   | 'the scheme and a space'     | 'Bearer '
        3   | 'lower-case bearer'          | 'bearer TOKEN'
        4   | 'upper-case bearer'          | 'BEARER TOKEN'
        5   | 'Basic'                      | 'Basic dG9ueTpw'
        6   | 'another scheme'             | 'Token abc'
        7   | 'not a token'                | 'Bearer not.a.jwt'
        8   | 'empty'                      | ''
    }

    @Unroll
    def "doFilterInternal should refuse a real token when #scenario"() {

        given:
        String header = prefix + (kind == 'refresh' ? JWT.issueRefresh(user('tony')) : JWT.issueAccess(user('tony')))

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['Authorization': header]) == null

        where:
        scenario                               | prefix     | kind
        'the scheme is lower case'             | 'bearer '  | 'access'
        'the token is a refresh token'         | 'Bearer '  | 'refresh'
        'there is an extra space after Bearer' | 'Bearer  ' | 'access'
    }

    def "doFilterInternal should prefer the service key when a Bearer token is also sent"() {

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY),
            ['X-Echo-Key': KEY, 'Authorization': 'Bearer ' + JWT.issueAccess(user('alice'))]).name == 'service'
    }

    def "doFilterInternal should fall through to the Bearer token when the service key is wrong"() {

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY),
            ['X-Echo-Key': 'wrong', 'Authorization': 'Bearer ' + JWT.issueAccess(user('alice'))]).name == 'alice'
    }

    def "doFilterInternal should keep an existing authentication when a Bearer token is sent"() {

        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken('client:rex', null, [])

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['Authorization': 'Bearer ' + JWT.issueAccess(user('alice'))]).name == 'client:rex'
    }

    def "doFilterInternal should replace an existing authentication when the service key is sent"() {

        given:
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken('someone', null, [])

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['X-Echo-Key': KEY]).name == 'service'
    }

    def "doFilterInternal should ignore the access token when it has expired"() {

        given:
        JwtService old = new JwtService('test-secret-test-secret-test-secret-0123456789', -1, 30)

        expect:
        run(new JwtAuthenticationFilter(JWT, KEY), ['Authorization': 'Bearer ' + old.issueAccess(user('tony'))]) == null
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static AppUser user(String name) {
        new AppUser(1, name, name, null, true, true, 0, null, 0)
    }

    /** Runs the filter once and answers who the chain saw; the chain must run exactly once. */
    private static Authentication run(JwtAuthenticationFilter f, Map<String, String> headers) {
        MockHttpServletRequest req = new MockHttpServletRequest('GET', '/api/x')
        headers.each { String k, String v -> req.addHeader(k, v) }
        int runs = 0
        Authentication seen = null
        FilterChain chain = { ServletRequest q, ServletResponse s ->
            runs++
            seen = SecurityContextHolder.context.authentication
        } as FilterChain
        f.doFilter(req, new MockHttpServletResponse(), chain)
        assert runs == 1
        seen
    }
}
