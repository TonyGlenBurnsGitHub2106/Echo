package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5653: the organized Files tab over HTTP -- HOUSE-ONLY. A person and the house may organize; an
 * agent is stopped at the door when its allowlist does not reach /api/solution-files/ (403 from the
 * fence), and refused by the controller when it does (403, never a write). A solution that does not exist,
 * or that an agent was not given, is 404 first. The service and SolutionAccess are real (delegated from
 * the bean mocks); storage and the shared category list are fake.
 */
@WebMvcTest(SolutionFileFilingController)
class SolutionFileFilingControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionFileFilingService service = Mock()
    @SpringBean SolutionFileService files = Mock()
    @SpringBean SolutionAccess accessBean = Mock()

    SolutionFileFilingRepository repo = Mock()
    FileFilingService categories = Mock()
    SolutionFileFilingService real

    def setup() {
        real = new SolutionFileFilingService(repo, categories)
        service.resolveCategory(*_) >> { a -> real.resolveCategory(*a) }
        service.list(*_) >> { a -> real.list(*a) }
        service.counts(*_) >> { a -> real.counts(*a) }
        service.setFiling(*_) >> { a -> real.setFiling(*a) }
        service.setImportant(*_) >> { a -> real.setImportant(*a) }
        service.fileMany(*_) >> { a -> real.fileMany(*a) }
        accessBean.requireSolution(*_) >> { a -> access.requireSolution(*a) }
        accessBean.requireHuman(*_) >> { a -> access.requireHuman(*a) }

        categories.resolveCategory(_, _, _) >> { Integer id, String name, Boolean staging ->
            Boolean.TRUE.equals(staging) ? null : (id != null && id > 0 ? id : (name != null && !name.isBlank() ? 9 : null)) }
        repo.listFiled(*_) >> []
        repo.countsFiled(*_) >> [[GroupKey: 'c4', GroupLabel: 'Bills', Cnt: 2, StagedCnt: 1]]
        repo.setFiling(*_) >> { a -> [FileCategoryID: a[2], DocumentDate: a[3] == null ? null : a[3].toString(), IsImportant: a[4]] }
        repo.setImportant(*_) >> true
        repo.fileMany(*_) >> { a -> ((List) a[1]).size() }
    }

    @Unroll
    def "#scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * repo._
        r.andExpect(status().is(expectedStatus))

        where:
        scenario         | method | url                                    | body                 || expectedStatus
        'filed'          | 'GET'  | '/api/solution-files/1/filed'          | null                 || 401
        'counts'         | 'GET'  | '/api/solution-files/1/counts'         | null                 || 401
        'setFiling'      | 'PUT'  | '/api/solution-files/1/7/filing'       | '{"categoryId":4}'   || 401
        'setImportant'   | 'PUT'  | '/api/solution-files/1/7/important'    | '{"important":true}' || 401
        'fileMany'       | 'POST' | '/api/solution-files/1/file'           | '{"fileIds":[7]}'    || 401
    }

    @Unroll
    def "#scenario should answer 200 when a person, the house or Stewart calls"() {

        expect:
        for (String who : ['user', 'service', 'stewart']) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        scenario         | method | url                                    | body                    || path          | expectedValue
        'filed'          | 'GET'  | '/api/solution-files/1/filed'          | null                    || '$.total'     | 2
        'counts'         | 'GET'  | '/api/solution-files/1/counts'         | null                    || '$.staged'    | 1
        'setFiling'      | 'PUT'  | '/api/solution-files/1/7/filing'       | '{"categoryId":4}'      || '$.fileId'    | 7
        'setImportant'   | 'PUT'  | '/api/solution-files/1/7/important'    | '{"important":true}'    || '$.important' | true
        'fileMany'       | 'POST' | '/api/solution-files/1/file'           | '{"fileIds":[7,8]}'     || '$.filed'     | 2
    }

    @Unroll
    def "#seq. #scenario should answer #expectedStatus and write nothing when the agent #who calls"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        0 * repo.setFiling(*_)
        0 * repo.setImportant(*_)
        0 * repo.fileMany(*_)
        0 * repo.listFiled(*_)
        0 * repo.countsFiled(*_)
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath(path).value(expectedMessage))

        where:
        seq | scenario       | who    | method | url                                   | body                  || expectedStatus | path         | expectedMessage
        1   | 'filed'        | 'rex'  | 'GET'  | '/api/solution-files/1/filed'         | null                  || 403            | '$.error'    | 'This API client may not reach that path.'
        2   | 'counts'       | 'rex'  | 'GET'  | '/api/solution-files/1/counts'        | null                  || 403            | '$.error'    | 'This API client may not reach that path.'
        3   | 'setFiling'    | 'rex'  | 'PUT'  | '/api/solution-files/1/7/filing'      | '{"categoryId":4}'    || 403            | '$.error'    | 'This API client may not reach that path.'
        4   | 'setImportant' | 'rex'  | 'PUT'  | '/api/solution-files/1/7/important'   | '{"important":true}'  || 403            | '$.error'    | 'This API client may not reach that path.'
        5   | 'fileMany'     | 'rex'  | 'POST' | '/api/solution-files/1/file'          | '{"fileIds":[7]}'     || 403            | '$.error'    | 'This API client may not reach that path.'
        6   | 'filed'        | 'wide' | 'GET'  | '/api/solution-files/1/filed'         | null                  || 403            | '$.message'  | 'organize a solution\'s files'
        7   | 'counts'       | 'wide' | 'GET'  | '/api/solution-files/1/counts'        | null                  || 403            | '$.message'  | 'organize a solution\'s files'
        8   | 'setFiling'    | 'wide' | 'PUT'  | '/api/solution-files/1/7/filing'      | '{"categoryId":4}'    || 403            | '$.message'  | 'organize a solution\'s files'
        9   | 'setImportant' | 'wide' | 'PUT'  | '/api/solution-files/1/7/important'   | '{"important":true}'  || 403            | '$.message'  | 'organize a solution\'s files'
        10  | 'fileMany'     | 'wide' | 'POST' | '/api/solution-files/1/file'          | '{"fileIds":[7]}'     || 403            | '$.message'  | 'organize a solution\'s files'
        11  | 'filed'        | 'wide' | 'GET'  | '/api/solution-files/2/filed'         | null                  || 404            | '$.message'  | 'Solution not found.'
        12  | 'setFiling'    | 'wide' | 'PUT'  | '/api/solution-files/2/7/filing'      | '{"categoryId":4}'    || 404            | '$.message'  | 'Solution not found.'
    }

    @Unroll
    def "#scenario should answer 404 when the solution does not exist"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario         | method | url                                    | body                  || expectedMessage
        'filed'          | 'GET'  | '/api/solution-files/9/filed'          | null                  || 'Solution not found.'
        'counts'         | 'GET'  | '/api/solution-files/0/counts'         | null                  || 'Solution not found.'
        'setFiling'      | 'PUT'  | '/api/solution-files/9/7/filing'       | '{"categoryId":4}'    || 'Solution not found.'
        'setImportant'   | 'PUT'  | '/api/solution-files/9/7/important'    | '{"important":true}'  || 'Solution not found.'
        'fileMany'       | 'POST' | '/api/solution-files/9/file'           | '{"fileIds":[7]}'     || 'Solution not found.'
    }

    def "filed should pass the query string through with its defaults when only some parameters are sent"() {

        when:
        mvc.perform(asUser(get('/api/solution-files/1/filed').param('group', 'year').param('q', ' tax '))).andExpect(status().isOk())
            .andExpect(jsonPath('$.groupBy').value('year'))
            .andExpect(jsonPath('$.desc').value(true))
            .andExpect(jsonPath('$.offset').value(0))
            .andExpect(jsonPath('$.hasMore').value(false))

        then:
        1 * repo.listFiled(1, 'year', 'added', true, 'tax', 0, 51) >> []
    }

    @Unroll
    def "filed should answer 400 when #scenario"() {

        expect:
        mvc.perform(asUser(get('/api/solution-files/1/filed').param(name, value))).andExpect(status().is(expectedStatus))

        where:
        scenario                    | name     | value   || expectedStatus
        'desc is not a boolean'     | 'desc'   | 'maybe' || 400
        'offset is a word'          | 'offset' | 'first' || 400
        'size is a fraction'        | 'size'   | '2.5'   || 400
    }

    def "counts should treat a blank search as none and trim a real one"() {

        when:
        mvc.perform(asUser(get('/api/solution-files/1/counts').param('q', '   '))).andExpect(status().isOk())
        mvc.perform(asUser(get('/api/solution-files/1/counts').param('q', ' bills ').param('group', 'type'))).andExpect(status().isOk())

        then:
        1 * repo.countsFiled(1, 'category', null) >> []
        1 * repo.countsFiled(1, 'type', 'bills') >> []
    }

    @Unroll
    def "setFiling should file under category #expectedCategory attributed to #expectedBy when #scenario"() {

        when:
        mvc.perform(by(caller, request('PUT', '/api/solution-files/1/7/filing', body))).andExpect(status().isOk())

        then:
        1 * repo.setFiling(1, 7, expectedCategory, expectedDate == null ? null : getLocalDate(expectedDate), expectedImportant, expectedBy) >> [:]

        where:
        scenario                               | caller    | body                                                         || expectedCategory | expectedDate | expectedImportant | expectedBy
        'an id is sent'                        | 'user'    | '{"categoryId":4}'                                           || 4                | null         | false             | 'tony'
        'a name is sent'                       | 'service' | '{"categoryName":"Taxes","important":true}'                  || 9                | null         | true              | 'felix'
        'staging wins over an id'              | 'stewart' | '{"categoryId":4,"staging":true}'                            || null             | null         | false             | 'stewart'
        'a US date is sent'                    | 'user'    | '{"categoryId":4,"documentDate":"9/15/2026"}'                || 4                | '09/15/2026' | false             | 'tony'
        'an ISO date is sent'                  | 'user'    | '{"categoryId":4,"documentDate":"2026-09-15"}'               || 4                | '09/15/2026' | false             | 'tony'
        'a blank date clears it'               | 'user'    | '{"categoryId":4,"documentDate":" "}'                        || 4                | null         | false             | 'tony'
    }

    @Unroll
    def "setFiling should answer 400 and write nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request('PUT', '/api/solution-files/1/7/filing', null), body)))

        then:
        0 * repo.setFiling(*_)
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                     | body                         || expectedStatus
        'there is no body'           | ''                           || 400
        'the body is null'           | 'null'                       || 400
        'the date is a word'         | '{"documentDate":"someday"}' || 400
        'the body is broken'         | '{"categoryId":'             || 400
        'the id is a word'           | '{"categoryId":"four"}'      || 400
    }

    def "setFiling should answer 400 naming the problem when the body is missing or the date is bad"() {

        expect:
        mvc.perform(asUser(json(request('PUT', '/api/solution-files/1/7/filing', null), ''))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value('Nothing to change.'))
        mvc.perform(asUser(request('PUT', '/api/solution-files/1/7/filing', '{"documentDate":"someday"}'))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value('The document date "someday" is not a date. Use mm/dd/yyyy.'))
    }

    def "setFiling should answer 404 'That file not found.' when the file is not on the solution"() {

        when:
        ResultActions r = mvc.perform(asUser(request('PUT', '/api/solution-files/1/99/filing', '{"categoryId":4}')))

        then:
        1 * repo.setFiling(1, 99, 4, null, false, 'tony') >> null
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('That file not found.'))
    }

    def "setFiling should answer the filing when it is saved"() {

        expect:
        mvc.perform(asUser(request('PUT', '/api/solution-files/1/7/filing', '{"categoryId":4,"documentDate":"9/15/2026","important":true}')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.fileId').value(7))
            .andExpect(jsonPath('$.categoryId').value(4))
            .andExpect(jsonPath('$.documentDate').value('2026-09-15'))
            .andExpect(jsonPath('$.important').value(true))
    }

    @Unroll
    def "setImportant should set important #expected when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request('PUT', '/api/solution-files/1/7/important', null), body)))

        then:
        1 * repo.setImportant(1, 7, expected, 'tony') >> true
        r.andExpect(status().isOk()).andExpect(jsonPath('$.fileId').value(7)).andExpect(jsonPath('$.important').value(expected))

        where:
        scenario                    | body                     || expected
        'true is sent'              | '{"important":true}'     || true
        'false is sent'             | '{"important":false}'    || false
        'the flag is left out'      | '{}'                     || false
        'there is no body'          | ''                       || false
        'the flag is null'          | '{"important":null}'     || false
    }

    def "setImportant should answer 404 when the file is not on the solution"() {

        when:
        ResultActions r = mvc.perform(asUser(request('PUT', '/api/solution-files/1/99/important', '{"important":true}')))

        then:
        1 * repo.setImportant(1, 99, true, 'tony') >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('That file not found.'))
    }

    @Unroll
    def "fileMany should answer 400 'Pick at least one file.' and write nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request('POST', '/api/solution-files/1/file', null), body)))

        then:
        0 * repo.fileMany(*_)
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                  | body                       || expectedMessage
        'there is no body'        | ''                         || 'Pick at least one file.'
        'there are no ids'        | '{"categoryId":4}'         || 'Pick at least one file.'
        'the ids are null'        | '{"fileIds":null}'         || 'Pick at least one file.'
        'the ids are empty'       | '{"fileIds":[]}'           || 'Pick at least one file.'
    }

    @Unroll
    def "fileMany should file under category #expectedCategory and answer it as #expectedAnswer when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solution-files/1/file', body)))

        then:
        1 * repo.fileMany(1, [7, 8], expectedCategory, 'tony') >> 2
        r.andExpect(status().isOk()).andExpect(jsonPath('$.filed').value(2)).andExpect(jsonPath('$.categoryId').value(expectedAnswer))

        where:
        scenario                 | body                                             || expectedCategory | expectedAnswer
        'an id is sent'          | '{"fileIds":[7,8],"categoryId":4}'               || 4                | 4
        'a name is sent'         | '{"fileIds":[7,8],"categoryName":"Taxes"}'       || 9                | 9
        'staging is asked'       | '{"fileIds":[7,8],"staging":true}'               || null             | 0
        'nothing is named'       | '{"fileIds":[7,8]}'                              || null             | 0
    }

    def "fileMany should answer 400 when the ids are not numbers"() {

        expect:
        mvc.perform(asUser(request('POST', '/api/solution-files/1/file', '{"fileIds":["a"]}'))).andExpect(status().isBadRequest())
    }

    def "filing endpoints should match nothing when the solution id is not a number"() {

        expect:
        mvc.perform(asUser(get('/api/solution-files/abc/filed'))).andExpect(status().isNotFound())
        mvc.perform(asUser(request('PUT', '/api/solution-files/1/x/filing', '{"categoryId":4}'))).andExpect(status().isNotFound())
    }
}
