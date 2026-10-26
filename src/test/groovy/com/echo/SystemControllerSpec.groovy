package com.echo

import java.sql.SQLException
import javax.sql.DataSource

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Story 5653: the open checks (config, demo status, health) and the signed-in About. */
@WebMvcTest(SystemController)
class SystemControllerSpec extends EchoWebSpec {

    @SpringBean DataSource ds = Mock()
    @SpringBean EchoUpdater updater = Mock()

    def "config should answer the idle minutes when nobody is signed in"() {

        expect:
        mvc.perform(get('/api/system/config'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.sessionInactivityMinutes').value(0))
    }

    def "demo should answer demoEnabled false when nobody is signed in"() {

        expect:
        mvc.perform(get('/api/system/demo/status'))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.demoEnabled').value(false))
    }

    def "health should answer 503 database down when the database cannot be reached"() {

        given:
        ds.getConnection() >> { throw new SQLException('down') }

        expect:
        mvc.perform(get('/api/system/health'))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.database').value('down'))
    }

    @Unroll
    def "about should answer #expectedStatus when #scenario"() {

        given:
        ds.getConnection() >> { throw new SQLException('down') }
        MockHttpServletRequestBuilder request = get('/api/system/about')
        if (caller == 'user') request = asUser(request)
        if (caller == 'service') request = asService(request)
        if (caller == 'wrong key') request = request.header('X-Echo-Key', 'wrong')
        if (caller == 'forged token') request = request.header('Authorization', 'Bearer not.a.jwt')
        if (caller == 'refresh token') request = request.header('Authorization', 'Bearer ' +
            jwt.issueRefresh(new AppUser(1, 'tony', 'tony', null, true, true, 0, null, 0)))

        expect:
        mvc.perform(request).andExpect(status().is(expectedStatus))

        where:
        scenario                                 | caller          || expectedStatus
        "nobody is signed in"                    | 'none'          || 401
        "a person is signed in"                  | 'user'          || 200
        "the service key is sent"                | 'service'       || 200
        "a wrong service key is sent"            | 'wrong key'     || 401
        "a forged token is sent"                 | 'forged token'  || 401
        "a refresh token is sent as an access one" | 'refresh token' || 401
    }

    def "about should name the app and report the database down when it cannot be reached"() {

        given:
        ds.getConnection() >> { throw new SQLException('down') }

        expect:
        mvc.perform(asUser(get('/api/system/about')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.app').value('Echo'))
            .andExpect(jsonPath('$.databaseUp').value(false))
    }

    def "about should answer the Not signed in body when nobody is signed in"() {

        expect:
        mvc.perform(get('/api/system/about'))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('Not signed in'))
    }

    // ---- Story 5653: Update Echo ---------------------------------------------------------------

    def "about should name the running release when one was installed"() {

        given:
        ds.getConnection() >> { throw new SQLException('down') }
        updater.currentRelease() >> [tag: 'build-20261026-1500']

        expect:
        mvc.perform(asUser(get('/api/system/about')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.release').value('build-20261026-1500'))
    }

    def "about should say built on this computer when no release was installed"() {

        given:
        ds.getConnection() >> { throw new SQLException('down') }
        updater.currentRelease() >> [:]

        expect:
        mvc.perform(asUser(get('/api/system/about')))
            .andExpect(jsonPath('$.release').value('built on this computer'))
    }

    def "update should answer 202 with the status when the update starts"() {

        when:
        def result = mvc.perform(asUser(post('/api/system/update')))

        then:
        1 * updater.start() >> [state: 'starting', running: true]
        result.andExpect(status().isAccepted())
            .andExpect(jsonPath('$.state').value('starting'))
    }

    def "update should answer 409 with the reason when the update is refused"() {

        given:
        updater.start() >> { throw new EchoUpdater.UpdateRefusedException('An update is already running. Wait for it to finish.') }

        expect:
        mvc.perform(asUser(post('/api/system/update')))
            .andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').value('An update is already running. Wait for it to finish.'))
    }

    def "update should answer 401 and start nothing when nobody is signed in"() {

        when:
        def result = mvc.perform(post('/api/system/update'))

        then:
        0 * updater.start()
        result.andExpect(status().isUnauthorized())
    }

    def "update status should answer what the updater reports"() {

        given:
        updater.status() >> [state: 'done', message: 'Echo is updated.']

        expect:
        mvc.perform(asUser(get('/api/system/update/status')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.state').value('done'))
    }

    def "update status should answer 401 when nobody is signed in"() {

        expect:
        mvc.perform(get('/api/system/update/status')).andExpect(status().isUnauthorized())
    }
}
