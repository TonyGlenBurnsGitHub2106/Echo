package com.echo

// Story 5653: who a Solutions request is from -- client, house (with X-Echo-Actor), person, or demo.

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

class SolutionCallerResolverSpec extends EchoSpockBaseSpec {

    SolutionCallerResolver resolver = new SolutionCallerResolver()
    MockHttpServletRequest req = new MockHttpServletRequest('GET', '/api/solutions/1')

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "the constants should be the documented values when read"() {

        expect:
        SolutionCallerResolver.HEADER_ACTOR == 'X-Echo-Actor'
        SolutionCallerResolver.DEFAULT_ACTOR == 'felix'
        SolutionCallerResolver.PERSON_CODE == 'tony'
    }

    def "resolve should answer CLIENT named by its credential when an authenticated client calls"() {

        given:
        signIn('client:rex')
        req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, new Credential(7, 'rex', 'h', true, '/api/solutions/'))

        expect:
        resolver.resolve(req) == new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7)
    }

    def "resolve should ignore X-Echo-Actor when the caller is a client"() {

        given:
        signIn('client:rex')
        req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, new Credential(7, 'rex', 'h', true, '/api/solutions/'))
        req.addHeader('X-Echo-Actor', 'tony')

        expect:
        resolver.resolve(req).memberCode() == 'rex'
    }

    @Unroll
    def "resolve should let the credential attribute win when the principal is #scenario"() {

        given:
        if (principal != null) signIn(principal)
        req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, new Credential(3, 'bot', 'h', true, '/api/solutions/'))

        expect:
        resolver.resolve(req) == new SolutionCaller(SolutionCaller.Kind.CLIENT, 'bot', 3)

        where:
        scenario      | principal
        'absent'      | null
        'the house'   | 'service'
        'a person'    | 'tony'
    }

    @Unroll
    def "resolve should throw not found when a client principal has #scenario"() {

        given:
        signIn('client:rex')
        if (attrKind == 'string') req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, 'rex')
        if (attrKind == 'number') req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, 7)

        when:
        resolver.resolve(req)

        then:
        SolutionNotFoundException ex = thrown(SolutionNotFoundException)
        ex.message == 'Solution not found.'

        where:
        scenario                         | attrKind
        'no credential attribute'        | 'none'
        'a string attribute'             | 'string'
        'a number attribute'             | 'number'
    }

    def "resolve should ignore an attribute of the wrong type when the caller is a person"() {

        given:
        signIn('a')
        req.setAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE, 'not a credential')

        expect:
        resolver.resolve(req) == new SolutionCaller(SolutionCaller.Kind.USER, 'tony', null)
    }

    @Unroll
    def "#seq. resolve should attribute the house key to '#expected' when X-Echo-Actor is #scenario"() {

        given:
        signIn('service')
        if (actor != null) req.addHeader('X-Echo-Actor', actor)

        expect:
        resolver.resolve(req) == new SolutionCaller(SolutionCaller.Kind.SERVICE, expected, null)

        where:
        seq | scenario                     | actor                    || expected
        1   | 'absent'                     | null                     || 'felix'
        2   | 'empty'                      | ''                       || 'felix'
        3   | 'blank'                      | '   '                    || 'felix'
        4   | 'stewart'                    | 'stewart'                || 'stewart'
        5   | 'capitalized'                | 'Stewart'                || 'stewart'
        6   | 'padded upper case'          | '  STEWART  '            || 'stewart'
        7   | 'hyphenated with a digit'    | 'rex-2'                  || 'rex-2'
        8   | 'one letter'                 | 'a'                      || 'a'
        9   | 'twenty characters'          | '12345678901234567890'   || '12345678901234567890'
        10  | 'twenty-one characters'      | '123456789012345678901'  || 'felix'
        11  | 'two words'                  | 'two words'              || 'felix'
        12  | 'with a semicolon'           | 'x;y'                    || 'felix'
        13  | 'with an underscore'         | 'x_y'                    || 'felix'
        14  | 'with a dot'                 | 'x.y'                    || 'felix'
        15  | 'with a line feed'           | 'tony\nfelix'            || 'felix'
        16  | 'non-ASCII'                  | '\u00e9mile'             || 'felix'
        17  | 'markup'                     | '<script>'               || 'felix'
    }

    @Unroll
    def "resolve should answer USER tony whatever the login name when it is #scenario"() {

        given:
        signIn(name)
        req.addHeader('X-Echo-Actor', 'stewart')

        expect:
        resolver.resolve(req) == new SolutionCaller(SolutionCaller.Kind.USER, 'tony', null)

        where:
        scenario                | name
        'a short login'         | 'a'
        'tony'                  | 'tony'
        'another person'        | 'jocie'
        'a capitalized Service' | 'Service'
        'felix'                 | 'felix'
    }

    @Unroll
    def "resolve should answer USER demo when #scenario"() {

        given:
        if (name != null) signIn(name)

        expect:
        resolver.resolve(req) == new SolutionCaller(SolutionCaller.Kind.USER, 'demo', null)

        where:
        scenario                       | name
        'there is no authentication'   | null
        'the name is blank'            | '   '
        'the name is empty'            | ''
        'the name is anonymousUser'    | 'anonymousUser'
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static void signIn(String name) {
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(name, null, [])
    }
}
