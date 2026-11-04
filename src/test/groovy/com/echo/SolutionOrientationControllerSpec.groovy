package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionOrientationModels.SummaryAge

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695: "where am I" over HTTP -- GET /api/solutions/{id}/summary-age answers 401 with no credential,
 * 200 with the written date, age in days and stale flag for anyone who may see the solution, and 404 for a
 * solution that is missing or not shared. The real service and access fence run; storage is mocked.
 */
@WebMvcTest(SolutionOrientationController)
class SolutionOrientationControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionOrientationService service = Mock()

    SolutionOrientationRepository repo = Mock()
    SolutionOrientationService real

    def setup() {
        real = new SolutionOrientationService(repo, access)
        service.summaryAge(*_) >> { a -> real.summaryAge(*a) }
        repo.summaryAge(_) >> new SummaryAge(getLocalDateTime('10/01/2026 12:30'), 34, true)
    }

    def "summaryAge should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(get('/api/solutions/1/summary-age'))

        then:
        r.andExpect(status().isUnauthorized())
        0 * repo._
    }

    def "summaryAge should answer the age when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, get('/api/solutions/1/summary-age'))).andExpect(status().isOk())
                .andExpect(jsonPath('$.ageDays').value(34))
                .andExpect(jsonPath('$.stale').value(true))
                .andExpect(jsonPath('$.writtenDate').exists())
        }
    }

    def "summaryAge should answer a summary written today as 0 days and not stale"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/summary-age')))

        then:
        1 * repo.summaryAge(1) >> new SummaryAge(getLocalDateTime('11/04/2026 08:00'), 0, false)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.ageDays').value(0)).andExpect(jsonPath('$.stale').value(false))
    }

    def "summaryAge should answer no date and no age when the solution has no summary"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/summary-age')))

        then:
        1 * repo.summaryAge(1) >> new SummaryAge(null, null, false)
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.writtenDate').doesNotExist())
            .andExpect(jsonPath('$.ageDays').doesNotExist())
            .andExpect(jsonPath('$.stale').value(false))
    }

    @Unroll
    def "summaryAge should answer 404 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get(url)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        scenario                                 | who    | url                              || expectedStatus
        'the agent was given nothing'            | 'zed'  | '/api/solutions/1/summary-age'   || 404
        'the agent was not given the solution'   | 'rex'  | '/api/solutions/2/summary-age'   || 404
        'the solution does not exist'            | 'user' | '/api/solutions/9/summary-age'   || 404
    }

    def "summaryAge should match no endpoint when the id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/abc/summary-age'))).andExpect(status().isNotFound())

        then:
        0 * repo._
    }
}
