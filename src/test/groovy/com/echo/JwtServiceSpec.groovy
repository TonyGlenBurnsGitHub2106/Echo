package com.echo

// Story 5653: Echo's two tokens -- the short access token and the long refresh token that
// carries the session generation. Signing, typing, expiry, tampering and the claim readers.

import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys

import spock.lang.Unroll

class JwtServiceSpec extends EchoSpockBaseSpec {

    private static final String SECRET = 'test-secret-test-secret-test-secret-0123456789'

    JwtService jwt = new JwtService(SECRET, 15, 30)

    // ---- construction ---------------------------------------------------------------------

    @Unroll
    def "the constructor should refuse the secret when it is #scenario"() {

        given:
        String secret = getReplicatedString('x', length)

        when:
        new JwtService(secret, 15, 30)

        then:
        IllegalStateException ex = thrown(IllegalStateException)
        ex.message.contains('at least 32 characters')

        where:
        scenario              | length
        'empty'               | 0
        'one character'       | 1
        'sixteen characters'  | 16
        'one short of 32'     | 31
    }

    def "the constructor should accept the secret when it is exactly 32 bytes"() {

        given:
        String secret = getReplicatedString('x', 32)

        expect:
        new JwtService(secret, 15, 30) != null
    }

    def "the constructor should count UTF-8 bytes, not characters, when the secret is multi-byte"() {

        given:
        String euros = getReplicatedString('\u20ac', 11)

        expect:
        euros.getBytes(StandardCharsets.UTF_8).length == 33
        new JwtService(euros, 15, 30) != null
    }

    def "the constructor should fail fast when the secret is null"() {

        when:
        new JwtService(null, 15, 30)

        then:
        thrown(NullPointerException)
    }

    @Unroll
    def "refreshMs should be the refresh lifetime in milliseconds when it is #scenario"() {

        expect:
        new JwtService(SECRET, 15, days).refreshMs() == expected

        where:
        scenario      | days || expected
        'thirty days' | 30   || 2592000000L
        'one day'     | 1    || 86400000L
        'zero days'   | 0    || 0L
    }

    // ---- issue and validate -----------------------------------------------------------------

    def "issueAccess should carry the user id, name, type and generation when validated"() {

        when:
        Claims c = jwt.validate(jwt.issueAccess(user(42, 'tony', 3)), JwtService.TYPE_ACCESS)

        then:
        c != null
        JwtService.userId(c) == 42
        JwtService.username(c) == 'tony'
        JwtService.generation(c) == 3
        c.get('typ', String) == 'access'
        c.subject == '42'
    }

    def "issueAccess and issueRefresh should live fifteen minutes and thirty days when issued"() {

        when:
        Claims a = jwt.validate(jwt.issueAccess(user(42, 'tony', 3)), JwtService.TYPE_ACCESS)
        Claims r = jwt.validate(jwt.issueRefresh(user(42, 'tony', 3)), JwtService.TYPE_REFRESH)

        then:
        (a.expiration.time - a.issuedAt.time) == 15L * 60_000L
        (r.expiration.time - r.issuedAt.time) == 30L * 86_400_000L
        Math.abs(a.issuedAt.time - System.currentTimeMillis()) < 5000
    }

    @Unroll
    def "validate should answer #valid when #scenario"() {

        given:
        AppUser u = user(42, 'tony', 3)
        String token = issued == 'access' ? jwt.issueAccess(u) : jwt.issueRefresh(u)

        expect:
        (jwt.validate(token, type) != null) == valid

        where:
        scenario                                 | issued    | type             || valid
        'an access token is read as refresh'     | 'access'  | 'refresh'        || false
        'a refresh token is read as access'      | 'refresh' | 'access'         || false
        'a refresh token is read as refresh'     | 'refresh' | 'refresh'        || true
        'an access token is read as access'      | 'access'  | 'access'         || true
        'an access token is read as another type'| 'access'  | 'something-else' || false
    }

    @Unroll
    def "#seq. validate should answer null when the token is #scenario"() {

        expect:
        jwt.validate(token, JwtService.TYPE_ACCESS) == null

        where:
        seq | scenario                  | token
        1   | 'null'                    | null
        2   | 'empty'                   | ''
        3   | 'blank'                   | '   '
        4   | 'one word'                | 'abc'
        5   | 'three short parts'       | 'a.b.c'
        6   | 'three words'             | 'not.a.jwt'
        7   | 'only dots'               | '...'
        8   | 'a whole Bearer header'   | 'Bearer x'
        9   | 'a header with no body'   | 'eyJhbGciOiJIUzI1NiJ9..'
    }

    def "validate should refuse the token when it is signed with another key"() {

        given:
        JwtService other = new JwtService('another-secret-another-secret-012345', 15, 30)

        expect:
        jwt.validate(other.issueAccess(user(42, 'tony', 3)), JwtService.TYPE_ACCESS) == null
    }

    def "validate should refuse the token when its payload or signature is tampered with"() {

        given:
        String t = jwt.issueAccess(user(42, 'tony', 3))
        String[] parts = t.split('\\.')
        String forgedPayload = Base64.urlEncoder.withoutPadding().encodeToString(
            '{"sub":"1","usr":"admin","typ":"access","gen":0}'.getBytes(StandardCharsets.UTF_8))
        String sig = parts[2]
        String flippedSig = (sig[0] == 'A' ? 'B' : 'A') + sig.substring(1)

        expect:
        jwt.validate(parts[0] + '.' + forgedPayload + '.' + parts[2], JwtService.TYPE_ACCESS) == null
        jwt.validate(parts[0] + '.' + parts[1] + '.' + flippedSig, JwtService.TYPE_ACCESS) == null
        jwt.validate(parts[0] + '.' + parts[1] + '.', JwtService.TYPE_ACCESS) == null
        jwt.validate(t + 'x', JwtService.TYPE_ACCESS) == null
    }

    def "validate should refuse the token when it is unsigned (alg none)"() {

        given:
        String unsigned = Jwts.builder().subject('1').claim('usr', 'tony').claim('typ', 'access').compact()

        expect:
        jwt.validate(unsigned, JwtService.TYPE_ACCESS) == null
    }

    def "validate should refuse the token when it has expired"() {

        given:
        JwtService shortLived = new JwtService(SECRET, -1, -1)

        expect:
        jwt.validate(shortLived.issueAccess(user(42, 'tony', 3)), JwtService.TYPE_ACCESS) == null
        jwt.validate(shortLived.issueRefresh(user(42, 'tony', 3)), JwtService.TYPE_REFRESH) == null
    }

    def "validate should refuse a correctly signed token when it has no type claim"() {

        given:
        String t = Jwts.builder().subject('1').claim('usr', 'tony')
            .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key()).compact()

        expect:
        jwt.validate(t, JwtService.TYPE_ACCESS) == null
    }

    def "generation should read 0 when a correctly signed token has no generation claim"() {

        given:
        String t = Jwts.builder().subject('9').claim('usr', 'x').claim('typ', 'refresh')
            .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key()).compact()

        when:
        Claims c = jwt.validate(t, JwtService.TYPE_REFRESH)

        then:
        c != null
        JwtService.generation(c) == 0
        JwtService.userId(c) == 9
    }

    def "validate should accept a token from another service when the secret is the same"() {

        expect:
        new JwtService(SECRET, 1, 1).validate(jwt.issueAccess(user(42, 'tony', 3)), JwtService.TYPE_ACCESS) != null
    }

    def "issueAccess should leave out the name claim when the username is null"() {

        when:
        Claims c = jwt.validate(jwt.issueAccess(user(5, null, 0)), JwtService.TYPE_ACCESS)

        then:
        c != null
        JwtService.username(c) == null
        JwtService.userId(c) == 5
    }

    def "userId should throw when the subject is not a number"() {

        given:
        String t = Jwts.builder().subject('abc').claim('typ', 'access')
            .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key()).compact()

        when:
        JwtService.userId(jwt.validate(t, JwtService.TYPE_ACCESS))

        then:
        thrown(NumberFormatException)
    }

    def "the type constants should be the documented strings when read"() {

        expect:
        JwtService.TYPE_ACCESS == 'access'
        JwtService.TYPE_REFRESH == 'refresh'
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static AppUser user(int id, String name, int generation) {
        new AppUser(id, name, 'Tony', null, true, false, 0, null, generation)
    }

    private static SecretKey key() {
        Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))
    }
}
