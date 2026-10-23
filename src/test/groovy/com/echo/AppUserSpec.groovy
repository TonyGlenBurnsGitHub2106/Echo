package com.echo

// Story 5653: the AppUser record -- its fields and the lock check.

import java.time.LocalDateTime

import spock.lang.Unroll

class AppUserSpec extends EchoSpockBaseSpec {

    def "the record should carry every field when it is built"() {

        given:
        LocalDateTime at = getLocalDateTime('01/02/2030 03:04')

        when:
        AppUser u = new AppUser(7, 'a', 'Tony B', 'pbkdf2$x', false, true, 4, at, 9)

        then:
        u.id() == 7
        u.username() == 'a'
        u.displayName() == 'Tony B'
        u.passwordHash() == 'pbkdf2$x'
        !u.active()
        u.admin()
        u.failedAttempts() == 4
        u.lockedUntil() == at
        u.sessionGeneration() == 9
    }

    @Unroll
    def "equals should answer #expected when #scenario"() {

        given:
        AppUser a = new AppUser(1, 'a', 'b', null, true, false, 0, null, 0)
        AppUser b = new AppUser(1, 'a', 'b', null, true, false, 0, null, generation)

        expect:
        (a == b) == expected

        where:
        scenario                         | generation || expected
        'every field matches'            | 0          || true
        'the generation differs'         | 1          || false
    }

    @Unroll
    def "lockedNow should answer #expected when the lock ends #scenario"() {

        given:
        LocalDateTime now = LocalDateTime.now()
        LocalDateTime until = fixed != null ? getLocalDateTime(fixed) : (offsetSeconds != null ? now.plusSeconds(offsetSeconds) : null)
        AppUser u = new AppUser(1, 'tony', 'Tony', 'h', true, true, 5, until, 2)

        expect:
        u.lockedNow() == expected

        where:
        scenario                 | fixed               | offsetSeconds || expected
        'never (null)'           | null                | null          || false
        'long ago'               | '01/01/2000 00:00'  | null          || false
        'a second ago'           | null                | -1            || false
        'exactly now'            | null                | 0             || false
        'a minute from now'      | null                | 60            || true
        'far in the future'      | '01/01/2999 00:00'  | null          || true
    }

    @Unroll
    def "lockedNow should ignore the failure count when #scenario"() {

        given:
        LocalDateTime until = offsetMinutes == null ? null : LocalDateTime.now().plusMinutes(offsetMinutes)
        AppUser u = new AppUser(1, 'a', 'a', null, true, false, failures, until, 0)

        expect:
        u.lockedNow() == expected

        where:
        scenario                                  | failures | offsetMinutes || expected
        'there are many failures but no lock'     | 99       | null          || false
        'there are no failures but a lock'        | 0        | 5             || true
    }
}
