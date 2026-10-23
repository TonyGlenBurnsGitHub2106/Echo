package com.echo

import java.time.LocalDate

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.ResultActions
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.SolutionSearchModels.SearchFilter
import com.echo.SolutionSearchModels.SearchHit
import com.echo.SolutionSearchRepository.SearchRows

import spock.lang.Unroll

// Story 5653: GET /api/solution-search/{id} -- house-only, outside /api/solutions on purpose; every parameter.
@WebMvcTest(SolutionSearchController)
@Import(SolutionSearchService)
class SolutionSearchControllerSpec extends SolutionWebSupport {

    @SpringBean SolutionSearchRepository searches = Mock()

    def setup() {
        solutionExists(1, true)
    }

    def "search should answer 401 when there is no credential"() {

        expect:
        mvc.perform(get('/api/solution-search/1')).andExpect(status().isUnauthorized())
    }

    @Unroll
    def "search should answer bands per tab with totals when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get('/api/solution-search/1').param('or', 'deploy')))

        then:
        1 * searches.search(_) >> new SearchRows([hit('log', 5), hit('items', 6)], [log: 4, items: 1])
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.solutionId').value(1))
            .andExpect(jsonPath('$.total').value(expectedTotal))
            .andExpect(jsonPath('$.pageSize').value(25))
            .andExpect(jsonPath('$.sources[0].key').value('items'))
            .andExpect(jsonPath('$.sources[1].key').value('log'))
            .andExpect(jsonPath('$.sources[1].label').value('Log'))
            .andExpect(jsonPath('$.sources[1].hits[0].id').value(5))

        where:
        scenario              | who       || expectedTotal
        "a person searches"   | "user"    || 5
        "the house searches"  | "service" || 5
    }

    def "search should pass every parameter into the filter when all are sent"() {

        given:
        LocalDate from = getLocalDate('09/01/2026')
        LocalDate through = getLocalDate('09/30/2026')

        when:
        mvc.perform(asUser(get('/api/solution-search/1').param('or', 'a', 'b').param('and', 'c').param('and', 'd')
            .param('not', 'e').param('sources', 'items, LOG ,bogus').param('from', '2026-09-01').param('through', ' 2026-09-30 ')
            .param('titlesOnly', 'true').param('openOnly', 'true').param('source', 'log').param('offset', '25')
            .param('pageSize', '600'))).andExpect(status().isOk())

        then:
        1 * searches.search({ SearchFilter f ->
            f.solutionId() == 1 && f.orTerms() == ['a', 'b'] && f.andTerms() == ['c', 'd'] && f.notTerms() == ['e'] &&
                f.sources() == ['items', 'log'] && f.from() == from && f.through() == through &&
                f.titlesOnly() && f.openOnly() && f.source() == 'log' && f.offset() == 25 && f.pageSize() == 500
        }) >> new SearchRows([], [:])
    }

    def "search should cover every tab with the defaults when no parameters are sent"() {

        when:
        mvc.perform(asUser(get('/api/solution-search/1'))).andExpect(status().isOk())

        then:
        1 * searches.search({ SearchFilter f ->
            f.isEmpty() && f.sources() == null && f.source() == null && !f.titlesOnly() && !f.openOnly() && f.offset() == 0 && f.pageSize() == 25
        }) >> new SearchRows([], [:])
    }

    def "search should cover every tab when the sources parameter is blank"() {

        when:
        mvc.perform(asUser(get('/api/solution-search/1').param('sources', '  '))).andExpect(status().isOk())

        then:
        1 * searches.search({ SearchFilter f -> f.sources() == null }) >> new SearchRows([], [:])
    }

    @Unroll
    def "search should answer 400 and search nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solution-search/1').param(param, value)))

        then:
        0 * searches.search(_)
        r.andExpect(status().isBadRequest())
        if (expectedMessage != null) {
            r.andExpect(jsonPath('$.message').value(expectedMessage))
        }

        where:
        scenario                              | param        | value              || expectedMessage
        "the from date is in US order"        | "from"       | "09/01/2026"       || "From date must look like 2026-09-17."
        "the through date does not exist"     | "through"    | "2026-09-31"       || "Through date must look like 2026-09-17."
        "the from date carries a time"        | "from"       | "2026-09-01T10:00" || "From date must look like 2026-09-17."
        "titlesOnly is not a boolean"         | "titlesOnly" | "perhaps"          || null
        "the offset is a letter"              | "offset"     | "x"                || null
    }

    @Unroll
    def "search should accept the through date when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solution-search/1').param('through', value)))

        then:
        1 * searches.search({ SearchFilter f -> f.through() == getLocalDate(expectedDate) }) >> new SearchRows([], [:])
        r.andExpect(status().isOk())

        where:
        scenario                               | value        || expectedDate
        "it is the day before the month ends"  | "2026-09-29" || "09/29/2026"
        "it is the day the month ends"         | "2026-09-30" || "09/30/2026"
        "it is the day after the month ends"   | "2026-10-01" || "10/01/2026"
    }

    def "search should answer 404 when the solution does not exist"() {

        expect:
        mvc.perform(asUser(get('/api/solution-search/9'))).andExpect(status().isNotFound())
    }

    def "search should turn an agent away at the door with 403 when its allowlist is the usual /api/solutions/"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solution-search/1').param('or', 'x')))

        then:
        0 * searches.search(_)
        r.andExpect(status().isForbidden()).andExpect(jsonPath('$.error').value('This API client may not reach that path.'))
    }

    def "search should still refuse an agent whose allowlist reaches the path, 404 when not given and 403 when given"() {

        when:
        ResultActions a = mvc.perform(asSearcher(get('/api/solution-search/1')))

        then:
        0 * searches.search(_)
        a.andExpect(status().isNotFound())

        when:
        ResultActions b = mvc.perform(asSearcher(get('/api/solution-search/1')))

        then:
        apiClients.maySee(9, 1) >> true
        0 * searches.search(_)
        b.andExpect(status().isForbidden()).andExpect(jsonPath('$.message').value('search a solution'))
    }

    def "search should answer 405 when the method is POST"() {

        expect:
        mvc.perform(asUser(post('/api/solution-search/1'))).andExpect(status().isMethodNotAllowed())
    }

    private static SearchHit hit(String source, int id) {
        new SearchHit(source, 'k', id, null, null, 't' + id, 'snip', 'x', null, null, null, false, null)
    }
}
