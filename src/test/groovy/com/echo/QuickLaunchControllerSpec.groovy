package com.echo

// Story 5653: /api/quick-launch -- list, create, update, delete and reorder the pins.

import java.time.LocalDateTime

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

@WebMvcTest(QuickLaunchController)
class QuickLaunchControllerSpec extends EchoWebSpec {

    @SpringBean QuickLaunchService service = Mock()

    // ---- who may call --------------------------------------------------------------------------

    @Unroll
    def "the endpoints should answer 401 when #scenario is called signed out"() {

        when:
        mvc.perform(call(method, path, body)).andExpect(status().isUnauthorized())
            .andExpect(content().json('{"error":"Not signed in"}', true))

        then:
        0 * service._

        where:
        scenario    | method   | path                         | body
        'list'      | 'GET'    | '/api/quick-launch'          | null
        'create'    | 'POST'   | '/api/quick-launch'          | '{"label":"A","url":"/a"}'
        'update'    | 'PUT'    | '/api/quick-launch/3'        | '{"label":"A","url":"/a"}'
        'delete'    | 'DELETE' | '/api/quick-launch/3'        | null
        'reorder'   | 'PUT'    | '/api/quick-launch/reorder'  | '{"orderedIds":[1,2]}'
    }

    @Unroll
    def "the fence should answer 403 when an API client calls #scenario"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        when:
        mvc.perform(call(method, path, body).header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())

        then:
        0 * service._

        where:
        scenario    | method   | path                         | body
        'list'      | 'GET'    | '/api/quick-launch'          | null
        'create'    | 'POST'   | '/api/quick-launch'          | '{"label":"A","url":"/a"}'
        'update'    | 'PUT'    | '/api/quick-launch/3'        | '{"label":"A","url":"/a"}'
        'delete'    | 'DELETE' | '/api/quick-launch/3'        | null
        'reorder'   | 'PUT'    | '/api/quick-launch/reorder'  | '{"orderedIds":[1,2]}'
    }

    @Unroll
    def "the endpoints should serve the house key when it calls #scenario"() {

        given:
        service.list() >> []

        expect:
        mvc.perform(asService(call(method, path, body))).andExpect(status().isOk())

        where:
        scenario    | method   | path                         | body
        'list'      | 'GET'    | '/api/quick-launch'          | null
        'create'    | 'POST'   | '/api/quick-launch'          | '{"label":"A","url":"/a"}'
        'update'    | 'PUT'    | '/api/quick-launch/3'        | '{"label":"A","url":"/a"}'
        'delete'    | 'DELETE' | '/api/quick-launch/3'        | null
        'reorder'   | 'PUT'    | '/api/quick-launch/reorder'  | '{"orderedIds":[1,2]}'
    }

    // ---- GET --------------------------------------------------------------------------------------

    def "list should answer each pin with every field when pins exist"() {

        given:
        LocalDateTime t = getLocalDateTime('10/22/2026 08:00')
        service.list() >> [new QuickLaunch(quickLaunchId: 4, label: 'Solutions', url: '/solutions/', iconKey: 'S',
                                           sortOrder: 10, createdUtc: t, modifiedUtc: t)]

        expect:
        mvc.perform(asUser(get('/api/quick-launch')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].quickLaunchId').value(4))
            .andExpect(jsonPath('$[0].label').value('Solutions'))
            .andExpect(jsonPath('$[0].url').value('/solutions/'))
            .andExpect(jsonPath('$[0].iconKey').value('S'))
            .andExpect(jsonPath('$[0].sortOrder').value(10))
            .andExpect(jsonPath('$[0].createdUtc').value('2026-10-22T08:00:00'))
            .andExpect(jsonPath('$[0].modifiedUtc').value('2026-10-22T08:00:00'))
    }

    def "list should answer [] when there are no pins"() {

        given:
        service.list() >> []

        expect:
        mvc.perform(asUser(get('/api/quick-launch'))).andExpect(content().json('[]', true))
    }

    // ---- POST -------------------------------------------------------------------------------------

    def "create should pass the fields as sent and answer the new id when the body is valid"() {

        when:
        mvc.perform(json(asUser(post('/api/quick-launch')), '{"label":" A ","url":"/a","iconKey":"*"}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"quickLaunchId":21}', true))

        then:
        1 * service.save(0, ' A ', '/a', '*') >> 21
    }

    def "create should pass a null icon when the icon is absent"() {

        when:
        mvc.perform(json(asUser(post('/api/quick-launch')), '{"label":"A","url":"/a"}')).andExpect(status().isOk())

        then:
        1 * service.save(0, 'A', '/a', null) >> 1
    }

    @Unroll
    def "#seq. create should answer 400 '#message' when #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/quick-launch')), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"' + message + '"}', true))

        then:
        0 * service._

        where:
        seq | scenario                   | body                           || message
        1   | 'the body is empty'        | '{}'                           || 'label is required'
        2   | 'the label is null'        | '{"label":null,"url":"/a"}'    || 'label is required'
        3   | 'the label is empty'       | '{"label":"","url":"/a"}'      || 'label is required'
        4   | 'the label is blank'       | '{"label":"   ","url":"/a"}'   || 'label is required'
        5   | 'the label is absent'      | '{"url":"/a"}'                 || 'label is required'
        6   | 'the url is absent'        | '{"label":"A"}'                || 'url is required'
        7   | 'the url is empty'         | '{"label":"A","url":""}'       || 'url is required'
        8   | 'the url is blank'         | '{"label":"A","url":"  "}'     || 'url is required'
    }

    @Unroll
    def "create should answer 400 when the request has #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(post('/api/quick-launch')).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b).andExpect(status().isBadRequest())

        then:
        0 * service._

        where:
        scenario        | body
        'no body'       | null
        'a JSON null'   | 'null'
        'broken JSON'   | '{"label":'
        'an array'      | '[]'
    }

    def "create should tolerate or refuse an unknown property, never saving half-read, when one is sent"() {

        when:
        MockHttpServletResponse res = mvc.perform(json(asUser(post('/api/quick-launch')), '{"label":"A","url":"/a","extra":1}')).andReturn().response

        then:
        (0..1) * service.save(0, 'A', '/a', null) >> 2
        res.status in [200, 400]
    }

    // ---- PUT /{id} --------------------------------------------------------------------------------

    def "update should save under the id and answer ok when the body is valid"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/5')), '{"label":"B","url":"/b","iconKey":"x"}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"ok":true}', true))

        then:
        1 * service.save(5, 'B', '/b', 'x')
    }

    @Unroll
    def "update should answer 400 invalid id when the id is #scenario"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/' + id)), '{"label":"B","url":"/b"}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"invalid id"}', true))

        then:
        0 * service._

        where:
        scenario    | id
        'zero'      | 0
        'negative'  | -1
    }

    @Unroll
    def "update should answer 400 when the id is #scenario"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/' + id)), '{"label":"B","url":"/b"}')).andExpect(status().isBadRequest())

        then:
        0 * service._

        where:
        scenario                   | id
        'letters'                  | 'abc'
        'a decimal'                | '1.5'
        'too large for an int'     | '99999999999'
    }

    @Unroll
    def "update should answer 400 '#message' when #scenario"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/5')), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"' + message + '"}', true))

        then:
        0 * service._

        where:
        scenario               | body                       || message
        'the body is empty'    | '{}'                       || 'label is required'
        'the label is blank'   | '{"label":" ","url":"/b"}' || 'label is required'
        'the url is absent'    | '{"label":"B"}'            || 'url is required'
        'the url is blank'     | '{"label":"B","url":" "}'  || 'url is required'
    }

    // ---- DELETE -----------------------------------------------------------------------------------

    def "delete should remove the pin and answer ok when the id is valid"() {

        when:
        mvc.perform(asUser(delete('/api/quick-launch/5'))).andExpect(status().isOk()).andExpect(content().json('{"ok":true}', true))

        then:
        1 * service.delete(5)
    }

    @Unroll
    def "delete should answer 400 when the id is #scenario"() {

        when:
        mvc.perform(asUser(delete('/api/quick-launch/' + id))).andExpect(status().isBadRequest())

        then:
        0 * service._

        where:
        scenario    | id
        'zero'      | '0'
        'negative'  | '-4'
        'letters'   | 'abc'
    }

    // ---- PUT /reorder -----------------------------------------------------------------------------

    def "reorder should pass the ids in order and answer ok when ids are sent"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/reorder')), '{"orderedIds":[3,1,2]}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"ok":true}', true))

        then:
        1 * service.reorder([3, 1, 2])
        0 * service.save(*_)
    }

    def "reorder should pass an empty list through when the list is empty"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/reorder')), '{"orderedIds":[]}')).andExpect(status().isOk())

        then:
        1 * service.reorder([])
    }

    def "reorder should keep nulls for the service to skip when the list holds a null"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/reorder')), '{"orderedIds":[null,4]}')).andExpect(status().isOk())

        then:
        1 * service.reorder([null, 4])
    }

    @Unroll
    def "reorder should answer 400 orderedIds is required when #scenario"() {

        when:
        mvc.perform(json(asUser(put('/api/quick-launch/reorder')), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"orderedIds is required"}', true))

        then:
        0 * service._

        where:
        scenario                 | body
        'the body is empty'      | '{}'
        'orderedIds is null'     | '{"orderedIds":null}'
    }

    def "reorder should answer 400 when the ids are not numbers"() {

        expect:
        mvc.perform(json(asUser(put('/api/quick-launch/reorder')), '{"orderedIds":["a"]}')).andExpect(status().isBadRequest())
    }

    def "reorder should answer 405 when called with POST"() {

        expect:
        mvc.perform(json(asUser(post('/api/quick-launch/reorder')), '{"orderedIds":[1]}')).andExpect(status().isMethodNotAllowed())
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }

    private static MockHttpServletRequestBuilder call(String method, String path, String body) {
        MockHttpServletRequestBuilder b = request(HttpMethod.valueOf(method), path)
        body == null ? b : json(b, body)
    }
}
