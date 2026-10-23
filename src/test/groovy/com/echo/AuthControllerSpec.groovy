package com.echo

// Story 5653: sign in, stay signed in, sign out -- every endpoint of AuthController through the
// real security chain: status codes, JSON, the refresh cookie, the lock, and who may call.

import java.time.LocalDateTime

import com.jayway.jsonpath.JsonPath
import io.jsonwebtoken.Claims
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.hamcrest.Matchers.nullValue
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.PendingFeature
import spock.lang.Unroll
import spock.mock.DetachedMockFactory

@WebMvcTest(AuthController)
class AuthControllerSpec extends EchoWebSpec {

    /**
     * AppUserRepository is a class whose methods are package-private. A @SpringBean proxy calls
     * them by reflection and is refused, so the repository is a detached Spock mock registered as
     * a plain bean; spock-spring attaches it to the running feature.
     */
    @TestConfiguration
    static class AppUserRepositoryMock {
        @Bean
        AppUserRepository appUserRepository() { new DetachedMockFactory().Mock(AppUserRepository) }
    }

    @Autowired AppUserRepository users

    private static final String PASSWORD = 'correct horse'
    private static final String HASH = PasswordHasher.hash(PASSWORD)
    private static final String BAD_LOGIN = 'That user name and password do not match.'
    private static final String LOCKED = 'Too many wrong passwords. Wait fifteen minutes and try again.'

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    // =====================================================================================
    // POST /api/auth/login
    // =====================================================================================

    def "login should answer a token body, set the refresh cookie and record success when the password is right"() {

        given:
        AppUser u = person(true, null, 4, HASH)

        when:
        MvcResult r = mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.username').value('tony'))
            .andExpect(jsonPath('$.displayName').value('Tony Burns'))
            .andReturn()
        Map body = (Map) JsonPath.read(r.response.contentAsString, '$')
        Claims access = jwt.validate((String) body.accessToken, JwtService.TYPE_ACCESS)
        String cookie = r.response.getHeader('Set-Cookie')
        Claims refresh = jwt.validate(r.response.getCookie('echo_refresh').value, JwtService.TYPE_REFRESH)

        then:
        1 * users.byUsername('tony') >> u
        1 * users.recordLogin(1, true)
        0 * users.recordLogin(_, false)
        body.keySet() as List == ['ok', 'accessToken', 'username', 'displayName']
        JwtService.username(access) == 'tony'
        cookie.startsWith('echo_refresh=')
        cookie.contains('Path=/api/auth')
        cookie.contains('HttpOnly')
        cookie.contains('SameSite=Strict')
        cookie.contains('Max-Age=2592000')
        !cookie.contains('Secure')
        JwtService.generation(refresh) == 4
        JwtService.userId(refresh) == 1
    }

    def "login should mark the refresh cookie Secure when the request is HTTPS"() {

        given:
        users.byUsername('tony') >> person(true, null, 0, HASH)

        when:
        MvcResult r = mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}').secure(true))
            .andExpect(status().isOk()).andReturn()

        then:
        r.response.getHeader('Set-Cookie').contains('Secure')
    }

    def "login should trim the user name when it has surrounding spaces"() {

        when:
        mvc.perform(json(post('/api/auth/login'), '{"username":"  tony  ","password":"correct horse"}'))
            .andExpect(status().isOk())

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
    }

    def "login should succeed without a sign-in when a stale Bearer header is sent"() {

        given:
        users.byUsername('tony') >> person(true, null, 0, HASH)

        expect:
        mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}')
                .header('Authorization', 'Bearer garbage'))
            .andExpect(status().isOk())
    }

    @Unroll
    def "login should refuse without a lookup when #scenario"() {

        when:
        mvc.perform(json(post('/api/auth/login'), body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value(BAD_LOGIN))

        then:
        0 * users._

        where:
        scenario                 | body
        'the body is empty'      | '{}'
        'the user name is null'  | '{"username":null,"password":"x"}'
        'the user name is empty' | '{"username":"","password":"x"}'
        'the user name is blank' | '{"username":"   ","password":"x"}'
        'the user name is absent'| '{"password":"correct horse"}'
    }

    def "login should answer the same sentence when the user is unknown or the password is wrong"() {

        when:
        String unknown = mvc.perform(json(post('/api/auth/login'), '{"username":"nobody","password":"correct horse"}'))
            .andExpect(status().isUnauthorized()).andReturn().response.contentAsString
        String wrong = mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"wrong"}'))
            .andExpect(status().isUnauthorized()).andReturn().response.contentAsString

        then:
        1 * users.byUsername('nobody') >> null
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        1 * users.recordLogin(1, false)
        0 * users.recordLogin(_, true)
        unknown == wrong
        unknown.contains(BAD_LOGIN)
    }

    @Unroll
    def "login should answer 401 and record a failure when #scenario"() {

        when:
        MvcResult r = mvc.perform(json(post('/api/auth/login'), body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value(BAD_LOGIN))
            .andReturn()

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        1 * users.recordLogin(1, false)
        r.response.getHeader('Set-Cookie') == null

        where:
        scenario                          | body
        'the password is wrong'           | '{"username":"tony","password":"wrong"}'
        'the password is absent'          | '{"username":"tony"}'
        'the password is empty'           | '{"username":"tony","password":""}'
        'the password differs in case'    | '{"username":"tony","password":"Correct horse"}'
        'the password has a trailing space' | '{"username":"tony","password":"correct horse "}'
    }

    def "login should refuse with the same sentence and record nothing when the person is inactive"() {

        when:
        mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}'))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value(BAD_LOGIN))

        then:
        1 * users.byUsername('tony') >> person(false, null, 0, HASH)
        0 * users.recordLogin(*_)
    }

    def "login should answer 423 and record nothing when the account is locked even with the right password"() {

        when:
        mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}'))
            .andExpect(status().is(423))
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value(LOCKED))

        then:
        1 * users.byUsername('tony') >> person(true, LocalDateTime.now().plusMinutes(10), 0, HASH)
        0 * users.recordLogin(*_)
    }

    def "login should succeed when the lock has run out"() {

        when:
        mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}'))
            .andExpect(status().isOk())

        then:
        1 * users.byUsername('tony') >> person(true, LocalDateTime.now().minusMinutes(1), 0, HASH)
        1 * users.recordLogin(1, true)
    }

    @Unroll
    def "login should never sign in when the stored hash is #scenario"() {

        when:
        mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}'))
            .andExpect(status().isUnauthorized())

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, hash)
        1 * users.recordLogin(1, false)

        where:
        scenario                    | hash
        'null'                      | null
        'empty'                     | ''
        'not a pbkdf2 value'        | 'plain-text-password'
        'the plain password itself' | 'correct horse'
    }

    @Unroll
    def "login should answer 400 when the request has #scenario"() {

        given:
        MockHttpServletRequestBuilder b = post('/api/auth/login')
        if (withJsonType) b.contentType(MediaType.APPLICATION_JSON)
        if (content != null) b.content(content)

        when:
        mvc.perform(b).andExpect(status().isBadRequest())

        then:
        0 * users._

        where:
        scenario       | withJsonType | content
        'no body'      | true         | null
        'broken JSON'  | true         | '{"username":'
        'a JSON array' | true         | '[]'
    }

    def "login should answer 415 when the content type is not JSON"() {

        expect:
        mvc.perform(post('/api/auth/login').contentType(MediaType.TEXT_PLAIN).content('tony'))
            .andExpect(status().isUnsupportedMediaType())
    }

    def "login should answer 405 when called with GET"() {

        expect:
        mvc.perform(get('/api/auth/login')).andExpect(status().isMethodNotAllowed())
    }

    def "login should answer 403 from the fence when the caller is an API client"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        expect:
        mvc.perform(json(post('/api/auth/login'), '{"username":"tony","password":"correct horse"}')
                .header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())
    }

    // =====================================================================================
    // POST /api/auth/refresh
    // =====================================================================================

    def "refresh should mint a new access token without a new cookie when the refresh cookie is live"() {

        given:
        AppUser u = person(true, null, 2, HASH)
        String token = jwt.issueRefresh(u)

        when:
        MvcResult r = mvc.perform(post('/api/auth/refresh').cookie(new Cookie('echo_refresh', token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.username').value('tony'))
            .andExpect(jsonPath('$.displayName').value('Tony Burns'))
            .andReturn()
        Map body = (Map) JsonPath.read(r.response.contentAsString, '$')

        then:
        1 * users.byId(1) >> u
        r.response.getHeader('Set-Cookie') == null
        jwt.validate((String) body.accessToken, JwtService.TYPE_ACCESS) != null
    }

    @Unroll
    def "#seq. refresh should answer 401 and clear the cookie when #scenario"() {

        given:
        AppUser u = person(true, null, 2, HASH)
        String token = refreshCookie(jwt, tokenKind, u)
        AppUser stored = storedPerson(storedKind, u)
        MockHttpServletRequestBuilder b = post('/api/auth/refresh')
        if (token != null) b.cookie(new Cookie('echo_refresh', token))

        when:
        MvcResult r = mvc.perform(b)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('Not signed in'))
            .andReturn()
        String cookie = r.response.getHeader('Set-Cookie')

        then:
        (0..1) * users.byId(1) >> stored
        cookie.startsWith('echo_refresh=;')
        cookie.contains('Max-Age=0')
        cookie.contains('Path=/api/auth')

        where:
        seq | scenario                         | tokenKind      | storedKind
        1   | 'there is no cookie'             | 'none'         | 'same'
        2   | 'the cookie is empty'            | 'empty'        | 'same'
        3   | 'the cookie is garbage'          | 'garbage'      | 'same'
        4   | 'the cookie holds an access token' | 'access'     | 'same'
        5   | 'the person is gone'             | 'refresh'      | 'gone'
        6   | 'the person is inactive'         | 'refresh'      | 'inactive'
        7   | 'the person signed out everywhere' | 'refresh'    | 'next-generation'
        8   | 'the token is an older generation' | 'refresh-old' | 'same'
    }

    def "refresh should answer 401 Not signed in when called signed out with no cookie"() {

        expect:
        mvc.perform(post('/api/auth/refresh')).andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))
            .andExpect(jsonPath('$.ok').value(false))
    }

    // =====================================================================================
    // POST /api/auth/logout
    // =====================================================================================

    def "logout should clear the cookie when called signed in or signed out"() {

        when:
        MvcResult signedOut = mvc.perform(post('/api/auth/logout')).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true)).andReturn()
        MvcResult signedIn = mvc.perform(asUser(post('/api/auth/logout'))).andExpect(status().isOk()).andReturn()

        then:
        0 * users._
        [signedOut, signedIn].every { MvcResult it ->
            String c = it.response.getHeader('Set-Cookie')
            c.startsWith('echo_refresh=;') && c.contains('Max-Age=0') && c.contains('HttpOnly')
        }
    }

    // =====================================================================================
    // POST /api/auth/logout-everywhere
    // =====================================================================================

    def "logoutEverywhere should answer 401 when nobody is signed in"() {

        expect:
        mvc.perform(post('/api/auth/logout-everywhere')).andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))
    }

    def "logoutEverywhere should end every session and clear the cookie when a person is signed in"() {

        when:
        MvcResult r = mvc.perform(asUser(post('/api/auth/logout-everywhere'), 'tony', 1))
            .andExpect(status().isOk()).andExpect(jsonPath('$.ok').value(true)).andReturn()

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        1 * users.endSessions(1)
        r.response.getHeader('Set-Cookie').contains('Max-Age=0')
    }

    def "logoutEverywhere should answer 401 from the controller when the caller is the house key"() {

        when:
        mvc.perform(asService(post('/api/auth/logout-everywhere')))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('Not signed in'))

        then:
        0 * users._
    }

    def "logoutEverywhere should answer 401 when the signed-in name no longer exists"() {

        when:
        mvc.perform(asUser(post('/api/auth/logout-everywhere'), 'ghost', 9)).andExpect(status().isUnauthorized())

        then:
        1 * users.byUsername('ghost') >> null
        0 * users.endSessions(_)
    }

    // =====================================================================================
    // POST /api/auth/change-password
    // =====================================================================================

    def "changePassword should answer 401 when nobody is signed in"() {

        expect:
        mvc.perform(json(post('/api/auth/change-password'), '{"currentPassword":"correct horse","newPassword":"new password"}'))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))
    }

    def "changePassword should answer 401 when the caller is the house key"() {

        when:
        mvc.perform(json(asService(post('/api/auth/change-password')), '{"currentPassword":"x","newPassword":"12345678"}'))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))

        then:
        0 * users._
    }

    def "changePassword should save, re-read and re-issue the cookie when the current password is right"() {

        given:
        String savedHash = null

        when:
        MvcResult r = mvc.perform(json(asUser(post('/api/auth/change-password')),
                '{"currentPassword":"correct horse","newPassword":"battery staple"}'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.username').value('tony'))
            .andReturn()

        then:
        1 * users.byUsername('tony') >> person(true, null, 1, HASH)
        1 * users.save('tony', 'Tony Burns', _) >> { String n, String d, String h -> savedHash = h; 1 }

        then:
        1 * users.byId(1) >> person(true, null, 2, HASH)
        PasswordHasher.matches('battery staple', savedHash)
        !PasswordHasher.matches(PASSWORD, savedHash)
        JwtService.generation(jwt.validate(r.response.getCookie('echo_refresh').value, JwtService.TYPE_REFRESH)) == 2
        r.response.getHeader('Set-Cookie').contains('Max-Age=2592000')
    }

    @Unroll
    def "changePassword should answer #code when #scenario"() {

        given:
        users.byUsername('tony') >> person(true, null, 0, HASH)
        users.byId(1) >> person(true, null, 0, HASH)
        String body = '{"currentPassword":"correct horse","newPassword":"' + getReplicatedString('p', length) + '"}'

        when:
        MockHttpServletResponse res = mvc.perform(json(asUser(post('/api/auth/change-password')), body)).andReturn().response

        then:
        res.status == code
        saves * users.save(*_) >> 1

        where:
        scenario                                   | length || code | saves
        'the new password is empty'                | 0      || 400  | 0
        'the new password has 7 characters'        | 7      || 400  | 0
        'the new password has exactly 8 characters'| 8      || 200  | 1
        'the new password has 200 characters'      | 200    || 200  | 1
    }

    def "changePassword should refuse with the length message when the new password is absent"() {

        when:
        mvc.perform(json(asUser(post('/api/auth/change-password')), '{"currentPassword":"correct horse"}'))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('A new password needs at least 8 characters.'))

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        0 * users.save(*_)
    }

    @Unroll
    def "changePassword should answer 400 and save nothing when #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/auth/change-password')), body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('Your current password is not right.'))

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        0 * users.save(*_)

        where:
        scenario                          | body
        'the current password is wrong'   | '{"currentPassword":"wrong","newPassword":"new password"}'
        'the current password is absent'  | '{"newPassword":"new password"}'
        'the body is empty'               | '{}'
    }

    def "changePassword should answer 400 when there is no body"() {

        expect:
        mvc.perform(asUser(post('/api/auth/change-password')).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
    }

    @PendingFeature(reason = 'BUG: change-password neither records a wrong current password as a failed attempt nor honors the lock, so a stolen access token can guess the current password without limit (verify-password does both)')
    def "changePassword should record a failed attempt when the current password is wrong"() {

        given:
        users.byUsername('tony') >> person(true, null, 0, HASH)

        when:
        mvc.perform(json(asUser(post('/api/auth/change-password')), '{"currentPassword":"wrong","newPassword":"new password"}'))

        then:
        1 * users.recordLogin(1, false)
    }

    @PendingFeature(reason = 'BUG: change-password ignores the account lock that login and verify-password honor')
    def "changePassword should answer 423 and save nothing when the account is locked"() {

        given:
        users.byUsername('tony') >> person(true, LocalDateTime.now().plusMinutes(10), 0, HASH)
        users.byId(1) >> person(true, null, 0, HASH)

        when:
        MockHttpServletResponse res = mvc.perform(json(asUser(post('/api/auth/change-password')),
                '{"currentPassword":"correct horse","newPassword":"new password"}')).andReturn().response

        then:
        res.status == 423
        0 * users.save(*_)
    }

    // =====================================================================================
    // POST /api/auth/verify-password
    // =====================================================================================

    def "verifyPassword should answer 401 when nobody is signed in"() {

        expect:
        mvc.perform(json(post('/api/auth/verify-password'), '{"password":"x"}')).andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))
    }

    def "verifyPassword should answer 200 and record nothing when the password is right"() {

        when:
        mvc.perform(json(asUser(post('/api/auth/verify-password')), '{"password":"correct horse"}'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        0 * users.recordLogin(*_)
    }

    @Unroll
    def "verifyPassword should answer 400, never 401, and record a failure when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(post('/api/auth/verify-password'))
        if (body != null) json(b, body)

        when:
        mvc.perform(b)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('That password is not right.'))

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
        1 * users.recordLogin(1, false)

        where:
        scenario                  | body
        'the password is wrong'   | '{"password":"wrong"}'
        'the password is empty'   | '{"password":""}'
        'the password is absent'  | '{}'
        'there is no body'        | null
    }

    def "verifyPassword should answer 423 without checking the password when the account is locked"() {

        when:
        mvc.perform(json(asUser(post('/api/auth/verify-password')), '{"password":"correct horse"}'))
            .andExpect(status().is(423))
            .andExpect(jsonPath('$.error').value(LOCKED))

        then:
        1 * users.byUsername('tony') >> person(true, LocalDateTime.now().plusMinutes(3), 0, HASH)
        0 * users.recordLogin(*_)
    }

    def "verifyPassword should answer 403 when the caller is the house key"() {

        when:
        mvc.perform(json(asService(post('/api/auth/verify-password')), '{"password":"x"}'))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath('$.error').value('Only a signed-in person may do this.'))

        then:
        0 * users._
    }

    def "verifyPassword should answer 403 when the signed-in name no longer exists"() {

        when:
        mvc.perform(json(asUser(post('/api/auth/verify-password'), 'ghost', 9), '{"password":"x"}'))
            .andExpect(status().isForbidden())

        then:
        1 * users.byUsername('ghost') >> null
    }

    // =====================================================================================
    // GET /api/ping
    // =====================================================================================

    def "ping should answer 401 when nobody is signed in"() {

        expect:
        mvc.perform(get('/api/ping')).andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))
    }

    def "ping should name the person when a person is signed in"() {

        when:
        mvc.perform(asUser(get('/api/ping')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.username').value('tony'))
            .andExpect(jsonPath('$.displayName').value('Tony Burns'))

        then:
        1 * users.byUsername('tony') >> person(true, null, 0, HASH)
    }

    def "ping should name service with no display name and no lookup when the caller is the house key"() {

        when:
        mvc.perform(asService(get('/api/ping')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.username').value('service'))
            .andExpect(jsonPath('$.displayName').value(nullValue()))

        then:
        0 * users._
    }

    def "ping should answer a null display name when the person is missing from the database"() {

        when:
        mvc.perform(asUser(get('/api/ping'), 'ghost', 9))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.username').value('ghost'))
            .andExpect(jsonPath('$.displayName').value(nullValue()))

        then:
        1 * users.byUsername('ghost') >> null
    }

    def "ping should answer 403 from the fence when the caller is an API client"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        expect:
        mvc.perform(get('/api/ping').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())
    }

    // =====================================================================================
    // the controller called directly -- branches HTTP cannot reach
    // =====================================================================================

    def "signedIn should answer null when there is no auth, the house key or a client"() {

        given:
        AuthController c = new AuthController(users, jwt)

        when:
        List<AppUser> results = [c.signedIn(), signedInAs('service') { c.signedIn() }, signedInAs('client:rex') { c.signedIn() }]

        then:
        results == [null, null, null]
        0 * users._
    }

    def "changePassword and verifyPassword should answer 400 when called directly with a null body"() {

        given:
        AuthController c = new AuthController(users, jwt)
        users.byUsername('tony') >> person(true, null, 0, HASH)

        when:
        int change = signedInAs('tony') { c.changePassword(null, new MockHttpServletRequest()).statusCode.value() }
        int verify = signedInAs('tony') { c.verifyPassword(null).statusCode.value() }

        then:
        change == 400
        verify == 400
    }

    def "the controller should treat a client principal as no person when it is set without the filter"() {

        given:
        AuthController c = new AuthController(users, jwt)

        when:
        int everywhere = signedInAs('client:rex') { c.logoutEverywhere(new MockHttpServletRequest()).statusCode.value() }
        int verify = signedInAs('client:rex') { c.verifyPassword(new AuthController.VerifyRequest('x')).statusCode.value() }
        Object displayName = signedInAs('client:rex') { ((Map) ((ResponseEntity) c.ping()).body).displayName }

        then:
        everywhere == 401
        verify == 403
        displayName == null
        0 * users._
    }

    def "REFRESH_COOKIE should be echo_refresh when read"() {

        expect:
        AuthController.REFRESH_COOKIE == 'echo_refresh'
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }

    private static AppUser person(boolean active, LocalDateTime lockedUntil, int generation, String hash) {
        new AppUser(1, 'tony', 'Tony Burns', hash, active, true, 0, lockedUntil, generation)
    }

    private static String refreshCookie(JwtService jwt, String kind, AppUser u) {
        switch (kind) {
            case 'none': return null
            case 'empty': return ''
            case 'garbage': return 'garbage'
            case 'access': return jwt.issueAccess(u)
            case 'refresh': return jwt.issueRefresh(u)
            case 'refresh-old': return jwt.issueRefresh(new AppUser(1, 'tony', 'T', null, true, true, 0, null, 1))
            default: throw new IllegalArgumentException(kind)
        }
    }

    private static AppUser storedPerson(String kind, AppUser u) {
        switch (kind) {
            case 'same': return u
            case 'gone': return null
            case 'inactive': return new AppUser(1, 'tony', 'T', null, false, true, 0, null, 2)
            case 'next-generation': return new AppUser(1, 'tony', 'T', null, true, true, 0, null, 3)
            default: throw new IllegalArgumentException(kind)
        }
    }

    private static <T> T signedInAs(String name, Closure<T> body) {
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(name, null, [])
        try {
            return body.call()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
