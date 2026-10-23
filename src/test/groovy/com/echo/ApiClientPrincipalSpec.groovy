package com.echo

// Story 5653: how an API client is named in the security context ("client:<code>").

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

import spock.lang.Unroll

class ApiClientPrincipalSpec extends EchoSpockBaseSpec {

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "the constants should be the documented values when read"() {

        expect:
        ApiClientPrincipal.PREFIX == 'client:'
        ApiClientPrincipal.REQUEST_ATTRIBUTE == 'leo.apiClient'
    }

    @Unroll
    def "nameOf should answer '#name' when the code is #scenario"() {

        expect:
        ApiClientPrincipal.nameOf(code) == name

        where:
        scenario   | code  || name
        'rex'      | 'rex' || 'client:rex'
        'empty'    | ''    || 'client:'
        'null'     | null  || 'client:null'
    }

    @Unroll
    def "#seq. isClient should answer #expected when the principal is #scenario"() {

        expect:
        ApiClientPrincipal.isClient(principal) == expected

        where:
        seq | scenario                     | principal       || expected
        1   | 'a client'                   | 'client:rex'    || true
        2   | 'the bare prefix'            | 'client:'       || true
        3   | 'a client with a colon'      | 'client:x:y'    || true
        4   | 'null'                       | null            || false
        5   | 'empty'                      | ''              || false
        6   | 'the prefix without a colon' | 'client'        || false
        7   | 'a capitalized prefix'       | 'Client:rex'    || false
        8   | 'an upper-case prefix'       | 'CLIENT:rex'    || false
        9   | 'a padded prefix'            | ' client:rex'   || false
        10  | 'a longer prefix'            | 'xclient:rex'   || false
        11  | 'the house'                  | 'service'       || false
        12  | 'a person'                   | 'tony'          || false
        13  | 'the anonymous user'         | 'anonymousUser' || false
    }

    @Unroll
    def "isClient should answer true for a name made by nameOf when the code is #scenario"() {

        expect:
        ApiClientPrincipal.isClient(ApiClientPrincipal.nameOf(code))

        where:
        scenario          | code
        'rex'             | 'rex'
        'named service'   | 'service'
        'named tony'      | 'tony'
        'empty'           | ''
    }

    @Unroll
    def "currentIsClient should answer #expected when the principal is #scenario"() {

        given:
        if (name != null) SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(name, null, [])

        expect:
        ApiClientPrincipal.currentIsClient() == expected

        where:
        scenario          | name         || expected
        'absent'          | null         || false
        'a person'        | 'tony'       || false
        'the house'       | 'service'    || false
        'a client'        | 'client:rex' || true
    }
}
