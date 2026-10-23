package com.echo

// Story 5653: /api/view-state -- remembered view state read by prefix and saved in batches.

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

@WebMvcTest(UserViewStateController)
class UserViewStateControllerSpec extends EchoWebSpec {

    @SpringBean UserViewStateService service = Mock()

    // ---- who may call --------------------------------------------------------------------------

    def "list and save should answer 401 when nobody is signed in"() {

        when:
        mvc.perform(get('/api/view-state').param('prefix', 'leo.')).andExpect(status().isUnauthorized())
            .andExpect(content().json('{"error":"Not signed in"}', true))
        mvc.perform(json(put('/api/view-state'), '{"values":{"a":"1"}}')).andExpect(status().isUnauthorized())

        then:
        0 * service._
    }

    def "list should answer 403 from the fence when the caller is an API client"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        when:
        mvc.perform(get('/api/view-state').param('prefix', 'leo.').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())

        then:
        0 * service._
    }

    def "list and save should serve the house key when it calls"() {

        when:
        mvc.perform(asService(get('/api/view-state').param('prefix', 'leo.'))).andExpect(status().isOk())
        mvc.perform(json(asService(put('/api/view-state')), '{"values":{"a":"1"}}')).andExpect(status().isOk())

        then:
        1 * service.list('leo.') >> [:]
        1 * service.save(['a': '1']) >> 1
    }

    // ---- GET -------------------------------------------------------------------------------------

    def "list should answer the values under the prefix when some are stored"() {

        when:
        mvc.perform(asUser(get('/api/view-state').param('prefix', 'leo.cl.')))
            .andExpect(status().isOk())
            .andExpect(content().json('{"values":{"leo.cl.a":"1","leo.cl.b":"open"}}', true))

        then:
        1 * service.list('leo.cl.') >> ['leo.cl.a': '1', 'leo.cl.b': 'open']
    }

    def "list should answer an empty map when nothing is stored"() {

        when:
        mvc.perform(asUser(get('/api/view-state').param('prefix', 'zzz'))).andExpect(status().isOk())
            .andExpect(content().json('{"values":{}}', true))

        then:
        1 * service.list('zzz') >> [:]
    }

    def "list should pass null and answer 400 {error} when the prefix is missing"() {

        when:
        mvc.perform(asUser(get('/api/view-state')))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"prefix must be at least 3 characters"}', true))

        then:
        1 * service.list(null) >> { throw new IllegalArgumentException('prefix must be at least 3 characters') }
    }

    def "list should pass the prefix exactly as sent when it is padded"() {

        when:
        mvc.perform(asUser(get('/api/view-state').param('prefix', '  leo.  '))).andExpect(status().isOk())

        then:
        1 * service.list('  leo.  ') >> [:]
    }

    def "list should not turn a failure into a 400 when it is not an IllegalArgumentException"() {

        when:
        mvc.perform(asUser(get('/api/view-state').param('prefix', 'leo.')))

        then:
        1 * service.list('leo.') >> { throw new IllegalStateException('db down') }
        thrown(Exception)
    }

    // ---- PUT -------------------------------------------------------------------------------------

    def "save should store the values and answer the count when the body holds a values object"() {

        when:
        mvc.perform(json(asUser(put('/api/view-state')), '{"values":{"leo.a":"1","leo.b":true,"leo.c":3,"leo.d":null}}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"saved":4}', true))

        then:
        1 * service.save(['leo.a': '1', 'leo.b': true, 'leo.c': 3, 'leo.d': null]) >> 4
    }

    @Unroll
    def "save should answer 400 values is required when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(put('/api/view-state')).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b)
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"values is required"}', true))

        then:
        0 * service._

        where:
        scenario                    | body
        'there is no body'          | null
        'the object is empty'       | '{}'
        'values is null'            | '{"values":null}'
        'values is a string'        | '{"values":"a=1"}'
        'values is an array'        | '{"values":[["a","1"]]}'
        'values is a number'        | '{"values":5}'
        'the key is misspelled'     | '{"value":{"a":"1"}}'
    }

    def "save should answer 400 with the message when the service refuses"() {

        when:
        mvc.perform(json(asUser(put('/api/view-state')), '{"values":{}}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"values is required"}', true))

        then:
        1 * service.save([:]) >> { throw new IllegalArgumentException('values is required') }
    }

    @Unroll
    def "save should answer 400 when the body is #scenario"() {

        expect:
        mvc.perform(json(asUser(put('/api/view-state')), body)).andExpect(status().isBadRequest())

        where:
        scenario        | body
        'a JSON array'  | '[]'
        'broken JSON'   | '{"values":'
    }

    def "the view-state route should answer 405 when called with POST or DELETE"() {

        expect:
        mvc.perform(json(asUser(post('/api/view-state')), '{"values":{}}')).andExpect(status().isMethodNotAllowed())
        mvc.perform(asUser(delete('/api/view-state'))).andExpect(status().isMethodNotAllowed())
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }
}
