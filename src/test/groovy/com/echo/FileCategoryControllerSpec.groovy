package com.echo

// Story 5653: /api/file-categories -- list, find-or-create (with an optional color for a new
// one), update, delete and reorder.

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.PendingFeature
import spock.lang.Unroll

@WebMvcTest(FileCategoryController)
class FileCategoryControllerSpec extends EchoWebSpec {

    @SpringBean FileFilingService service = Mock()

    // ---- who may call --------------------------------------------------------------------------

    @Unroll
    def "the endpoints should answer 401 when #scenario is called signed out"() {

        when:
        mvc.perform(call(method, path, body)).andExpect(status().isUnauthorized())
            .andExpect(content().json('{"error":"Not signed in"}', true))

        then:
        0 * service._

        where:
        scenario          | method   | path                           | body
        'list'            | 'GET'    | '/api/file-categories'         | null
        'find-or-create'  | 'POST'   | '/api/file-categories'         | '{"name":"Tax"}'
        'update'          | 'PUT'    | '/api/file-categories/4'       | '{"name":"Tax"}'
        'delete'          | 'DELETE' | '/api/file-categories/4'       | null
        'reorder'         | 'POST'   | '/api/file-categories/reorder' | '{"ids":[4]}'
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
        scenario          | method   | path                           | body
        'list'            | 'GET'    | '/api/file-categories'         | null
        'find-or-create'  | 'POST'   | '/api/file-categories'         | '{"name":"Tax"}'
        'update'          | 'PUT'    | '/api/file-categories/4'       | '{"name":"Tax"}'
        'delete'          | 'DELETE' | '/api/file-categories/4'       | null
        'reorder'         | 'POST'   | '/api/file-categories/reorder' | '{"ids":[4]}'
    }

    @Unroll
    def "the endpoints should serve the house key when it calls #scenario"() {

        given:
        service.categories() >> []
        service.findOrCreateCategory(_) >> cat([created: false])
        service.saveCategory(*_) >> cat([:])
        service.reorderCategories(_) >> []

        expect:
        mvc.perform(asService(call(method, path, body))).andExpect(status().isOk())

        where:
        scenario          | method   | path                           | body
        'list'            | 'GET'    | '/api/file-categories'         | null
        'find-or-create'  | 'POST'   | '/api/file-categories'         | '{"name":"Tax"}'
        'update'          | 'PUT'    | '/api/file-categories/4'       | '{"name":"Tax"}'
        'delete'          | 'DELETE' | '/api/file-categories/4'       | null
        'reorder'         | 'POST'   | '/api/file-categories/reorder' | '{"ids":[4]}'
    }

    // ---- GET -----------------------------------------------------------------------------------------

    def "list should answer the categories when some exist"() {

        when:
        mvc.perform(asUser(get('/api/file-categories')))
            .andExpect(status().isOk())
            .andExpect(content().json('[{"id":4,"name":"Tax","color":"","sortOrder":20,"useCount":2}]', true))

        then:
        1 * service.categories() >> [cat([useCount: 2])]
    }

    // ---- POST find-or-create ----------------------------------------------------------------------------

    def "findOrCreate should answer the category and ignore the color when the name already exists"() {

        when:
        mvc.perform(json(asUser(post('/api/file-categories')), '{"name":"Tax","color":"#112233"}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":4,"name":"Tax","color":"","sortOrder":20,"created":false}', true))

        then:
        1 * service.findOrCreateCategory('Tax') >> cat([created: false])
        0 * service.saveCategory(*_)
    }

    def "findOrCreate should create, then color, and answer created when a new name comes with a color"() {

        when:
        mvc.perform(json(asUser(post('/api/file-categories')), '{"name":" Receipts ","color":"#112233"}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":9,"name":"Receipts","color":"#112233","sortOrder":30,"created":true}', true))

        then:
        1 * service.findOrCreateCategory(' Receipts ') >> cat([id: 9, name: 'Receipts', sortOrder: 30, created: true])

        then:
        1 * service.saveCategory(9, 'Receipts', '#112233', null) >> cat([id: 9, name: 'Receipts', color: '#112233', sortOrder: 30])
    }

    @Unroll
    def "findOrCreate should create without a second save when the color is #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/file-categories')), body))
            .andExpect(status().isOk())
            .andExpect(content().json('{"created":true}'))

        then:
        1 * service.findOrCreateCategory('Receipts') >> cat([id: 9, name: 'Receipts', created: true])
        0 * service.saveCategory(*_)

        where:
        scenario  | body
        'missing' | '{"name":"Receipts"}'
        'null'    | '{"name":"Receipts","color":null}'
        'empty'   | '{"name":"Receipts","color":""}'
        'blank'   | '{"name":"Receipts","color":"  "}'
    }

    @Unroll
    def "findOrCreate should answer 400 name is required when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(post('/api/file-categories')).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b)
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"name is required"}', true))

        then:
        0 * service._

        where:
        scenario               | body
        'there is no body'     | null
        'the body is empty'    | '{}'
        'the name is null'     | '{"name":null}'
        'the name is blank'    | '{"name":"   "}'
        'the body is null'     | 'null'
    }

    def "findOrCreate should answer 400 with the message when the service refuses the name"() {

        given:
        String body = '{"name":"' + getReplicatedString('x', 101) + '"}'

        when:
        mvc.perform(json(asUser(post('/api/file-categories')), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"A category name is 100 characters at most."}', true))

        then:
        1 * service.findOrCreateCategory(_) >> { throw new IllegalArgumentException('A category name is 100 characters at most.') }
    }

    def "findOrCreate should answer 400 with the color message when a new name comes with a bad color"() {

        when:
        mvc.perform(json(asUser(post('/api/file-categories')), '{"name":"New","color":"red"}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"Color must look like #1D4ED8."}', true))

        then:
        1 * service.findOrCreateCategory('New') >> cat([id: 9, name: 'New', created: true])
        1 * service.saveCategory(9, 'New', 'red', null) >> { throw new IllegalArgumentException('Color must look like #1D4ED8.') }
    }

    @PendingFeature(reason = 'BUG: POST /api/file-categories with a new name and a bad color creates the category first and only then refuses the color (400), leaving a category the caller was told was not saved')
    def "findOrCreate should refuse a bad color before creating any category when the name is new"() {

        when:
        mvc.perform(json(asUser(post('/api/file-categories')), '{"name":"New","color":"red"}'))
            .andExpect(status().isBadRequest())

        then:
        0 * service.findOrCreateCategory(_)
    }

    // ---- PUT ----------------------------------------------------------------------------------------

    def "update should pass name, color and sort order and answer the saved category when all are sent"() {

        when:
        mvc.perform(json(asUser(put('/api/file-categories/4')), '{"name":"Taxes","color":"#aabbcc","sortOrder":5}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":4,"name":"Taxes","color":"#AABBCC","sortOrder":5}', true))

        then:
        1 * service.saveCategory(4, 'Taxes', '#aabbcc', 5) >> cat([name: 'Taxes', color: '#AABBCC', sortOrder: 5])
    }

    def "update should pass nulls for the rest when only the name is sent"() {

        when:
        mvc.perform(json(asUser(put('/api/file-categories/4')), '{"name":"Tax"}')).andExpect(status().isOk())

        then:
        1 * service.saveCategory(4, 'Tax', null, null) >> cat([:])
    }

    def "update should reach the service, which refuses the blank name, when the object is empty"() {

        when:
        mvc.perform(json(asUser(put('/api/file-categories/4')), '{}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"A category needs a name."}', true))

        then:
        1 * service.saveCategory(4, null, null, null) >> { throw new IllegalArgumentException('A category needs a name.') }
    }

    @Unroll
    def "update should answer 400 invalid id when the id is #scenario"() {

        when:
        mvc.perform(json(asUser(put('/api/file-categories/' + id)), '{"name":"Tax"}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"invalid id"}', true))

        then:
        0 * service._

        where:
        scenario    | id
        'zero'      | 0
        'negative'  | -1
    }

    def "update should answer 400 missing body when there is no body"() {

        when:
        mvc.perform(asUser(put('/api/file-categories/4')).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"missing body"}', true))

        then:
        0 * service._
    }

    @Unroll
    def "update should answer 400 when #scenario"() {

        expect:
        mvc.perform(json(asUser(put('/api/file-categories/' + id)), body)).andExpect(status().isBadRequest())

        where:
        scenario                           | id    | body
        'the id is not a number'           | 'abc' | '{"name":"Tax"}'
        'the sort order is not a number'   | '4'   | '{"name":"Tax","sortOrder":"first"}'
    }

    // ---- DELETE ---------------------------------------------------------------------------------------

    def "delete should answer how many files went back to staging when the category is deleted"() {

        when:
        mvc.perform(asUser(delete('/api/file-categories/4')))
            .andExpect(status().isOk())
            .andExpect(content().json('{"deleted":true,"filesReturned":3}', true))

        then:
        1 * service.deleteCategory(4) >> 3
    }

    @Unroll
    def "delete should answer 400 when the id is #scenario"() {

        when:
        mvc.perform(asUser(delete('/api/file-categories/' + id))).andExpect(status().isBadRequest())

        then:
        0 * service._

        where:
        scenario    | id
        'zero'      | '0'
        'negative'  | '-1'
        'letters'   | 'abc'
    }

    // ---- reorder ---------------------------------------------------------------------------------------

    def "reorder should pass the ids and answer the categories in their new order when ids are sent"() {

        when:
        mvc.perform(json(asUser(post('/api/file-categories/reorder')), '{"ids":[5,4]}'))
            .andExpect(status().isOk())
            .andExpect(content().json('[{"id":5},{"id":4}]'))

        then:
        1 * service.reorderCategories([5, 4]) >> [cat([id: 5]), cat([:])]
    }

    @Unroll
    def "reorder should answer 400 ids is required when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(post('/api/file-categories/reorder')).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b)
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"ids is required"}', true))

        then:
        0 * service._

        where:
        scenario               | body
        'there is no body'     | null
        'the body is empty'    | '{}'
        'the ids are null'     | '{"ids":null}'
        'the ids are empty'    | '{"ids":[]}'
    }

    def "reorder should be read as an id and refused when called with PUT"() {

        expect:
        mvc.perform(json(asUser(put('/api/file-categories/reorder')), '{"ids":[1]}')).andExpect(status().isBadRequest())
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Map<String, Object> cat(Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<String, Object>([id: 4, name: 'Tax', color: '', sortOrder: 20])
        m.putAll(extra)
        m
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }

    private static MockHttpServletRequestBuilder call(String method, String path, String body) {
        MockHttpServletRequestBuilder b = request(HttpMethod.valueOf(method), path)
        body == null ? b : json(b, body)
    }
}
