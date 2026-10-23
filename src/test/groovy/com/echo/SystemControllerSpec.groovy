package com.echo

import java.sql.SQLException
import javax.sql.DataSource

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Story 5653: the open checks (config, demo status, health) and the signed-in About. */
@WebMvcTest(SystemController)
class SystemControllerSpec extends EchoWebSpec {

    @SpringBean DataSource ds = Mock()

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
}
