package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionTermModels.Term
import com.echo.SolutionTermModels.TermDraft
import com.echo.SolutionTermModels.TermFilter
import com.echo.SolutionTermModels.TermLookup

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5653: the Terms tab over HTTP -- every endpoint, who may call it, the query parameters that become
 * the filter, the draft built from a body, and how each refusal maps to a status.
 */
@WebMvcTest(SolutionTermController)
class SolutionTermControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionTermService service = Mock()

    SolutionTermRepository terms = Mock()
    SolutionTermService real

    Map<Integer, Term> rows = [5: term(5, 1), 6: term(6, 2)]

    def setup() {
        real = new SolutionTermService(terms, access)
        service.page(*_) >> { a -> real.page(*a) }
        service.get(*_) >> { a -> real.get(*a) }
        service.save(*_) >> { a -> real.save(*a) }
        service.favorite(*_) >> { a -> real.favorite(*a) }
        service.delete(*_) >> { a -> real.delete(*a) }
        service.lookups(*_) >> { a -> real.lookups(*a) }
        service.renameLookup(*_) >> { a -> real.renameLookup(*a) }
        service.deleteLookup(*_) >> { a -> real.deleteLookup(*a) }

        terms.find(_) >> { int id -> rows[id] }
        terms.list(_) >> { TermFilter f -> rows.values().findAll { it.solutionId() == f.solutionId() } as List }
        terms.counts(_) >> [total: 1]
        terms.save(*_) >> { TermDraft d, String by -> d.id() > 0 ? d.id() : 5 }
        terms.setFavorite(*_) >> true
        terms.delete(_) >> true
        terms.types(_) >> [new TermLookup(1, 'Acronym', 1)]
        terms.categories(_) >> [new TermLookup(2, 'Web', 1)]
        terms.deleteLookup(*_) >> true
    }

    private static Term term(int id, int solutionId) {
        Term.of([SolutionTermID: id, SolutionID: solutionId, Term: 'T' + id, Definition: 'D' + id, CategoryList: 'Web'])
    }

    /** "a,b" to ["a", "b"]; "" to an empty list; null stays null. */
    private static List<String> listOf(String csv) {
        csv == null ? null : (csv.isEmpty() ? [] : (csv.split(',') as List<String>))
    }

    @Unroll
    def "#seq. #scenario should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * terms._
        r.andExpect(status().is(expectedStatus))

        where:
        seq | scenario            | method   | url                                     | body                                      || expectedStatus
        1   | 'page'              | 'GET'    | '/api/solutions/1/terms'                | null                                      || 401
        2   | 'add'               | 'POST'   | '/api/solutions/1/terms'                | '{"term":"API","definition":"Interface"}' || 401
        3   | 'lookups'           | 'GET'    | '/api/solutions/1/terms/lookups'        | null                                      || 401
        4   | 'rename a type'     | 'PUT'    | '/api/solutions/1/terms/types/1'        | '{"name":"Abbreviation"}'                 || 401
        5   | 'rename a category' | 'PUT'    | '/api/solutions/1/terms/categories/2'   | '{"name":"Internet"}'                     || 401
        6   | 'delete a type'     | 'DELETE' | '/api/solutions/1/terms/types/1'        | null                                      || 401
        7   | 'delete a category' | 'DELETE' | '/api/solutions/1/terms/categories/2'   | null                                      || 401
        8   | 'get'               | 'GET'    | '/api/solutions/terms/5'                | null                                      || 401
        9   | 'locate'            | 'GET'    | '/api/solutions/terms/5/locate'         | null                                      || 401
        10  | 'update'            | 'PUT'    | '/api/solutions/terms/5'                | '{"definition":"New"}'                    || 401
        11  | 'favorite'          | 'PUT'    | '/api/solutions/terms/5/favorite'       | '{"favorite":true}'                       || 401
        12  | 'delete'            | 'DELETE' | '/api/solutions/terms/5'                | null                                      || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        seq | scenario            | method   | url                                     | body                                         || path                  | expectedValue
        1   | 'page'              | 'GET'    | '/api/solutions/1/terms'                | null                                         || '$.total'             | 1
        2   | 'add'               | 'POST'   | '/api/solutions/1/terms'                | '{"term":"API","definition":"Interface"}'    || '$.solutionId'        | 1
        3   | 'lookups'           | 'GET'    | '/api/solutions/1/terms/lookups'        | null                                         || '$.types[0].name'     | 'Acronym'
        4   | 'rename a type'     | 'PUT'    | '/api/solutions/1/terms/types/1'        | '{"name":"Abbreviation"}'                    || '$.types[0].id'       | 1
        5   | 'rename a category' | 'PUT'    | '/api/solutions/1/terms/categories/2'   | '{"name":"Internet"}'                        || '$.categories[0].id'  | 2
        6   | 'delete a type'     | 'DELETE' | '/api/solutions/1/terms/types/1'        | null                                         || '$.types[0].id'       | 1
        7   | 'delete a category' | 'DELETE' | '/api/solutions/1/terms/categories/2'   | null                                         || '$.categories[0].id'  | 2
        8   | 'get'               | 'GET'    | '/api/solutions/terms/5'                | null                                         || '$.solutionId'        | 1
        9   | 'locate'            | 'GET'    | '/api/solutions/terms/5/locate'         | null                                         || '$.solutionId'        | 1
        10  | 'update'            | 'PUT'    | '/api/solutions/terms/5'                | '{"definition":"New"}'                       || '$.id'                | 5
        11  | 'favorite'          | 'PUT'    | '/api/solutions/terms/5/favorite'       | '{"favorite":true}'                          || '$.id'                | 5
        12  | 'delete'            | 'DELETE' | '/api/solutions/terms/5'                | null                                         || '$.deletedId'         | 5
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * terms.save(*_)
        0 * terms.setFavorite(*_)
        0 * terms.delete(_)
        0 * terms.renameLookup(*_)
        0 * terms.deleteLookup(*_)

        where:
        seq | scenario            | who   | method   | url                                     | body                                      || expectedStatus
        1   | 'page'              | 'zed' | 'GET'    | '/api/solutions/1/terms'                | null                                      || 404
        2   | 'add'               | 'zed' | 'POST'   | '/api/solutions/1/terms'                | '{"term":"API","definition":"Interface"}' || 404
        3   | 'lookups'           | 'zed' | 'GET'    | '/api/solutions/1/terms/lookups'        | null                                      || 404
        4   | 'rename a type'     | 'zed' | 'PUT'    | '/api/solutions/1/terms/types/1'        | '{"name":"x"}'                            || 404
        5   | 'delete a category' | 'zed' | 'DELETE' | '/api/solutions/1/terms/categories/2'   | null                                      || 404
        6   | 'get'               | 'zed' | 'GET'    | '/api/solutions/terms/5'                | null                                      || 404
        7   | 'locate'            | 'zed' | 'GET'    | '/api/solutions/terms/5/locate'         | null                                      || 404
        8   | 'update'            | 'zed' | 'PUT'    | '/api/solutions/terms/5'                | '{"definition":"New"}'                    || 404
        9   | 'favorite'          | 'zed' | 'PUT'    | '/api/solutions/terms/5/favorite'       | '{"favorite":true}'                       || 404
        10  | 'delete'            | 'zed' | 'DELETE' | '/api/solutions/terms/5'                | null                                      || 404
        11  | 'page'              | 'rex' | 'GET'    | '/api/solutions/2/terms'                | null                                      || 404
        12  | 'add'               | 'rex' | 'POST'   | '/api/solutions/2/terms'                | '{"term":"API","definition":"Interface"}' || 404
        13  | 'lookups'           | 'rex' | 'GET'    | '/api/solutions/2/terms/lookups'        | null                                      || 404
        14  | 'rename a type'     | 'rex' | 'PUT'    | '/api/solutions/2/terms/types/1'        | '{"name":"x"}'                            || 404
        15  | 'delete a category' | 'rex' | 'DELETE' | '/api/solutions/2/terms/categories/2'   | null                                      || 404
        16  | 'get'               | 'rex' | 'GET'    | '/api/solutions/terms/6'                | null                                      || 404
        17  | 'locate'            | 'rex' | 'GET'    | '/api/solutions/terms/6/locate'         | null                                      || 404
        18  | 'update'            | 'rex' | 'PUT'    | '/api/solutions/terms/6'                | '{"definition":"New"}'                    || 404
        19  | 'favorite'          | 'rex' | 'PUT'    | '/api/solutions/terms/6/favorite'       | '{"favorite":true}'                       || 404
        20  | 'delete'            | 'rex' | 'DELETE' | '/api/solutions/terms/6'                | null                                      || 404
    }

    def "page should build a normalized filter when every query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/terms').param('search', '  api ').param('typeId', '3').param('categoryId', '0')
            .param('favorites', 'true').param('groupBy', 'category').param('reverse', 'true').param('offset', '-5').param('pageSize', '9999')))
            .andExpect(status().isOk())

        then:
        1 * terms.list(new TermFilter(1, 'api', 3, null, true, 'category', true, 0, 500)) >> []
    }

    def "page should use the default filter when no query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/terms'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.offset').value(0)).andExpect(jsonPath('$.pageSize').value(50))

        then:
        1 * terms.list(new TermFilter(1, null, null, null, false, 'type', false, 0, 50)) >> []
    }

    @Unroll
    def "page should answer 400 when #scenario"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/terms').param(name, value))).andExpect(status().is(expectedStatus))

        where:
        scenario                      | name        | value   || expectedStatus
        'typeId is a word'            | 'typeId'    | 'x'     || 400
        'offset is a fraction'        | 'offset'    | '1.5'   || 400
        'pageSize is a word'          | 'pageSize'  | 'lots'  || 400
        'favorites is not a boolean'  | 'favorites' | 'maybe' || 400
    }

    def "page should answer its terms, counts and paging when an agent reads it"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/1/terms'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.terms[0].term').value('T5'))
            .andExpect(jsonPath('$.terms[0].categories[0]').value('Web'))
            .andExpect(jsonPath('$.counts.total').value(1))
            .andExpect(jsonPath('$.total').value(1))
    }

    def "add should build the draft from the body with an empty category list when categories are missing"() {

        when:
        mvc.perform(asAgent(request('POST', '/api/solutions/1/terms',
            '{"term":"API","definition":"Interface","type":"Acronym","source":"Web","notes":"n"}'))).andExpect(status().isOk())

        then:
        1 * terms.save(new TermDraft(0, 1, 'API', 'Interface', 'Acronym', 'Web', 'n', []), 'rex') >> 5
    }

    @Unroll
    def "update should build a merge draft when #scenario"() {

        given:
        List<String> expectedCategories = listOf(categoriesText)

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/terms/5', body))).andExpect(status().isOk())

        then:
        1 * terms.save(new TermDraft(5, 0, null, null, null, null, expectedNotes, expectedCategories), 'tony') >> 5

        where:
        scenario                                  | body                  || expectedNotes | categoriesText
        'only the notes are cleared'              | '{"notes":""}'        || ''            | null
        'the categories are cleared'              | '{"categories":[]}'   || null          | ''
    }

    @Unroll
    def "add should answer 400 when #scenario"() {

        expect:
        mvc.perform(asUser(request('POST', '/api/solutions/1/terms', body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                         | body                                 || expectedMessage
        'the term is missing'            | '{"definition":"d"}'                 || 'The term is required.'
        'the term is blank'              | '{"term":" ","definition":"d"}'      || 'The term is required.'
        'the definition is missing'      | '{"term":"API"}'                     || 'A definition is required.'
        'the definition is empty'        | '{"term":"API","definition":""}'     || 'A definition is required.'
    }

    def "add should trim the term and definition when they are padded"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/terms', '{"term":"  API ","definition":" d "}'))).andExpect(status().isOk())

        then:
        1 * terms.save({ TermDraft d -> d.term() == 'API' && d.definition() == 'd' }, 'tony') >> 5
    }

    def "renameLookup and favorite should answer 400 when the name or the flag is missing"() {

        expect:
        mvc.perform(asUser(request('PUT', '/api/solutions/1/terms/types/1', '{"name":" "}'))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value('A name is required.'))
        mvc.perform(asUser(request('PUT', '/api/solutions/terms/5/favorite', '{}'))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value('favorite (true or false) is required.'))
    }

    def "renameLookup and deleteLookup should make the kind singular when types or categories is in the path"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/1/terms/types/4', '{"name":" Abbr "}'))).andExpect(status().isOk())
        mvc.perform(asUser(delete('/api/solutions/1/terms/categories/3'))).andExpect(status().isOk())

        then:
        1 * terms.renameLookup('type', 1, 4, 'Abbr')
        1 * terms.deleteLookup('category', 1, 3) >> true
    }

    def "renameLookup and deleteLookup should match no endpoint when the kind is anything else"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/1/terms/tags/4', '{"name":"x"}'))).andExpect(status().isNotFound())
        mvc.perform(asUser(delete('/api/solutions/1/terms/type/4'))).andExpect(status().isNotFound())

        then:
        0 * terms.renameLookup(*_)
        0 * terms.deleteLookup(*_)
    }

    def "locate should answer only the term and its solution when a person asks"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/terms/6/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.termId').value(6))
            .andExpect(jsonPath('$.solutionId').value(2))
            .andExpect(jsonPath('$.definition').doesNotExist())
    }

    @Unroll
    def "#scenario should answer 404 when the term is missing"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario      | method   | url                                  | body                  || expectedMessage
        'get'         | 'GET'    | '/api/solutions/terms/99'            | null                  || 'Term not found.'
        'locate'      | 'GET'    | '/api/solutions/terms/0/locate'      | null                  || 'Term not found.'
        'update'      | 'PUT'    | '/api/solutions/terms/99'            | '{"term":"x"}'        || 'Term not found.'
        'favorite'    | 'PUT'    | '/api/solutions/terms/99/favorite'   | '{"favorite":false}'  || 'Term not found.'
        'delete'      | 'DELETE' | '/api/solutions/terms/99'            | null                  || 'Term not found.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 when the body is '#body'"() {

        expect:
        mvc.perform(asUser(json(request(method, url, null), body))).andExpect(status().is(expectedStatus))

        where:
        seq | scenario         | method | url                                  | body   || expectedStatus
        1   | 'add'            | 'POST' | '/api/solutions/1/terms'             | ''     || 400
        2   | 'add'            | 'POST' | '/api/solutions/1/terms'             | 'null' || 400
        3   | 'update'         | 'PUT'  | '/api/solutions/terms/5'             | '{'    || 400
        4   | 'update'         | 'PUT'  | '/api/solutions/terms/5'             | '[]'   || 400
        5   | 'favorite'       | 'PUT'  | '/api/solutions/terms/5/favorite'    | ''     || 400
        6   | 'favorite'       | 'PUT'  | '/api/solutions/terms/5/favorite'    | 'null' || 400
        7   | 'rename a type'  | 'PUT'  | '/api/solutions/1/terms/types/1'     | '{'    || 400
        8   | 'rename a type'  | 'PUT'  | '/api/solutions/1/terms/types/1'     | '[]'   || 400
    }

    @Unroll
    def "add should answer 400 when categories are #scenario"() {

        expect:
        mvc.perform(asUser(request('POST', '/api/solutions/1/terms', body))).andExpect(status().is(expectedStatus))

        where:
        scenario          | body                                                 || expectedStatus
        'a bare string'   | '{"term":"a","definition":"b","categories":"Web"}'   || 400
        'an object'       | '{"term":"a","definition":"b","categories":{"a":1}}' || 400
    }

    def "add should answer 409 verbatim when the repository refuses a duplicate"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/terms', '{"term":"T5","definition":"d"}')))

        then:
        1 * terms.save(*_) >> { throw new SolutionRuleException('That term is already in this solution. Nothing was saved.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('That term is already in this solution. Nothing was saved.'))
    }
}
