package com.echo

// Story 5654: /api/gmail-client -- the Email Client's endpoints: who may call, the whitelist and
// blacklist list, and how an Outlook that is not running is answered.

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

@WebMvcTest(GmailClientController)
class GmailClientControllerSpec extends EchoWebSpec {

    @SpringBean GmailClientService service = Mock()
    @SpringBean MailTriageService triage = Mock()

    @Unroll
    def "#path should answer 401 signed out"() {

        when:
        mvc.perform(get(path)).andExpect(status().isUnauthorized())

        then:
        0 * service._
        0 * triage._

        where:
        path << ['/api/gmail-client/status', '/api/gmail-client/messages', '/api/gmail-client/senders/rules']
    }

    def "an API client (Rex) should never reach the Email Client"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        when:
        mvc.perform(get('/api/gmail-client/messages').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())

        then:
        0 * service._
    }

    def "status should pass the service's answer through"() {

        when:
        def r = mvc.perform(asUser(get('/api/gmail-client/status')))

        then:
        1 * service.status() >> [connected: false, outlookError: 'Outlook is not running on this computer.']
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.connected').value(false))
            .andExpect(jsonPath('$.outlookError').value('Outlook is not running on this computer.'))
    }

    @Unroll
    def "an Outlook refusal with status #st should answer #http"() {

        when:
        def r = mvc.perform(asUser(post('/api/gmail-client/check').contentType(MediaType.APPLICATION_JSON).content('{}')))

        then:
        1 * service.check() >> { throw new OutlookClient.OutlookException(st, 'Outlook said ' + st) }
        r.andExpect(status().is(http)).andExpect(jsonPath('$.error').value('Outlook said ' + st))

        where:
        st  | http
        409 | 409
        404 | 404
        500 | 424
        504 | 424
    }

    def "the lists should be read with the disposition and search asked for"() {

        when:
        def r = mvc.perform(asUser(get('/api/gmail-client/senders/rules?disposition=black&q=acme&offset=50&size=25')))

        then:
        1 * triage.rules('black', 'acme', 50, 25) >> [rows: [[id: 3, senderKey: 'acme.example', disposition: 'black']], hasMore: false,
                                                      counts: [white: 0, black: 1]]
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.rows[0].senderKey').value('acme.example'))
            .andExpect(jsonPath('$.counts.black').value(1))
    }

    def "removing a sender should answer the fresh tab counts too"() {

        when:
        def r = mvc.perform(asUser(delete('/api/gmail-client/senders/rules/3')))

        then:
        1 * triage.remove(3) >> [removed: true, id: 3, senderKey: 'acme.example']
        1 * service.tabs(_) >> [[scope: 'all', count: 4]]
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.removed').value(true))
            .andExpect(jsonPath('$.tabs[0].count').value(4))
    }

    def "removing a sender that is on no list should be 404"() {

        when:
        def r = mvc.perform(asUser(delete('/api/gmail-client/senders/rules/99')))

        then:
        1 * triage.remove(99) >> null
        r.andExpect(status().isNotFound())
    }

    def "a protected sender's removal refused by the service should be 400"() {

        when:
        def r = mvc.perform(asUser(delete('/api/gmail-client/senders/rules/9')))

        then:
        1 * triage.remove(9) >> { throw new IllegalArgumentException('irs.example is protected.') }
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.error').value('irs.example is protected.'))
    }

    def "decide should go to the service with the sender and the side"() {

        when:
        def r = mvc.perform(asUser(post('/api/gmail-client/senders/decide').contentType(MediaType.APPLICATION_JSON)
            .content('{"sender":"work.example","disposition":"white"}')))

        then:
        1 * service.decideSender('work.example', 'white') >> [rule: [senderKey: 'work.example', disposition: 'white']]
        r.andExpect(status().isOk()).andExpect(jsonPath('$.rule.disposition').value('white'))
    }

    def "the record-link endpoints Leonardo has are not in Echo"() {

        expect:
        mvc.perform(asUser(get('/api/gmail-client/record-search?q=greg'))).andReturn().response.status in [404, 405, 500]
    }
}
