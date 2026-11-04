package com.echo

import com.echo.SolutionOrientationModels.SummaryAge

import spock.lang.Unroll

/**
 * Story 5695: "where am I" in a solution -- the age of "Where things stand" is read only after access is
 * checked, so a hidden solution reads nothing; anyone who may see the solution may read it.
 */
class SolutionOrientationServiceSpec extends SolutionServiceSupport {

    SolutionOrientationRepository repo = Mock()
    SolutionOrientationService service = new SolutionOrientationService(repo, access)

    @Unroll
    def "summaryAge should answer what the repository read when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        SummaryAge age = new SummaryAge(getLocalDateTime('10/01/2026 12:30'), 34, true)

        when:
        SummaryAge got = service.summaryAge(caller, solutionId)

        then:
        1 * repo.summaryAge(solutionId) >> age
        got.is(age)

        where:
        scenario                         | callerName | solutionId || unused
        'a person reads it'              | 'TONY'     | 2          || null
        'the house reads it'             | 'HOUSE'    | 1          || null
        'a client reads a shared one'    | 'REX'      | 1          || null
    }

    def "summaryAge should pass an empty age through when the solution has no summary"() {

        when:
        SummaryAge got = service.summaryAge(TONY, 1)

        then:
        1 * repo.summaryAge(1) >> new SummaryAge(null, null, false)
        got.writtenDate() == null
        got.ageDays() == null
        !got.stale()
    }

    @Unroll
    def "summaryAge should read nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.summaryAge(caller, solutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                                 | callerName | solutionId || expectedMessage
        'the id is zero'                         | 'TONY'     | 0          || 'Solution not found.'
        'the solution does not exist'            | 'TONY'     | 9          || 'Solution not found.'
        'the client was not given the solution'  | 'REX'      | 2          || 'Solution not found.'
        'the client was given nothing'           | 'ZED'      | 1          || 'Solution not found.'
    }
}
