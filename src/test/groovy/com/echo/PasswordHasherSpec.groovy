package com.echo

import spock.lang.Unroll

/**
 * Story 5653: PBKDF2 password hashes. They must stay in step with scripts\Set-EchoPassword.ps1,
 * which makes the same hash in PowerShell.
 */
class PasswordHasherSpec extends EchoSpockBaseSpec {

    def "hash should produce the pbkdf2 shape with 210,000 rounds when given a password"() {

        when:
        String hash = PasswordHasher.hash('correct horse')

        then:
        String[] parts = hash.split('[$]')
        parts.length == 4
        parts[0] == 'pbkdf2'
        parts[1] == '210000'
        Base64.decoder.decode(parts[2]).length == 16
        Base64.decoder.decode(parts[3]).length == 32
    }

    @Unroll
    def "matches should return #expected when #scenario"() {

        given:
        String stored = PasswordHasher.hash('correct horse')

        when:
        boolean result = PasswordHasher.matches(password, stored)

        then:
        result == expected

        where:
        scenario                  | password        || expected
        "the password is right"   | 'correct horse' || true
        "one letter differs"      | 'correct horsE' || false
        "the password is empty"   | ''              || false
        "the password is null"    | null            || false
    }

    def "hash should differ between two calls when the password is the same"() {

        when:
        String first = PasswordHasher.hash('same')
        String second = PasswordHasher.hash('same')

        then:
        first != second
    }

    @Unroll
    def "#seq. matches should return false when #scenario"() {

        when:
        boolean result = PasswordHasher.matches('x', stored)

        then:
        result == expected

        where:
        seq | scenario                          | stored                         || expected
        1   | "the stored value is null"        | null                           || false
        2   | "the stored value is empty"       | ''                             || false
        3   | "only the prefix is stored"       | 'pbkdf2'                       || false
        4   | "a part is missing"               | 'pbkdf2$1$a'                   || false
        5   | "the method is not pbkdf2"        | 'sha1$1$AAAA$AAAA'             || false
        6   | "the round count is not a number" | 'pbkdf2$notanumber$AAAA$AAAA'  || false
        7   | "the salt is not Base64"          | 'pbkdf2$1$!!!$AAAA'            || false
        8   | "the hash is not Base64"          | 'pbkdf2$1$AAAA$!!!'            || false
    }
}
