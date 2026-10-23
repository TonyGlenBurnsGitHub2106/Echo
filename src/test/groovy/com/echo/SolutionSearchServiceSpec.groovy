package com.echo

import com.echo.SolutionSearchModels.SearchFilter
import com.echo.SolutionSearchModels.SearchHit
import com.echo.SolutionSearchModels.SearchResult
import com.echo.SolutionSearchRepository.SearchRows
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.caller
import static com.echo.SolutionFixtures.solution

import spock.lang.Unroll

// Story 5653: SolutionSearchService -- house-only; the found rows shaped as one band per tab, in tab order.
class SolutionSearchServiceSpec extends EchoSpockBaseSpec {

    SolutionRepository repo = Mock()
    SolutionSearchRepository searches = Mock()
    ApiClientService clients = Mock()
    SolutionSearchService service = new SolutionSearchService(searches, new SolutionAccess(repo, clients))

    def setup() {
        repo.findSolution(1) >> solution(id: 1)
    }

    @Unroll
    def "search should answer bands in tab order, only for tabs with matches, when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        SearchRows rows = new SearchRows([hit('log', 1), hit('items', 2), hit('log', 3), hit('web', 4)],
            [web: 1, log: 7, items: 1, notes: 0, bogus: 5])

        when:
        SearchResult out = service.search(who, filter(1, 10))

        then:
        1 * searches.search(_) >> rows
        out.solutionId() == 1
        out.pageSize() == 10
        out.sources()*.key() == ['items', 'log', 'web']
        out.sources()*.label() == ['Items', 'Log', 'Web']
        out.sources()*.total() == [1, 7, 1]
        out.sources()[1].hits()*.id() == [1, 3]
        out.total() == expectedTotal

        where:
        scenario                  | callerName || expectedTotal
        "a person searches"       | "person"   || 9
        "the house searches"      | "house"    || 9
    }

    def "search should give a band with no hits when a tab has a total but no rows loaded"() {

        when:
        SearchResult out = service.search(PERSON, filter(1, 0))

        then:
        1 * searches.search(_) >> new SearchRows([], [notes: 3])
        out.sources().size() == 1
        out.sources()[0].hits() == []
        out.total() == 3
        out.pageSize() == 25
    }

    def "search should drop the hits of a tab whose total is 0 and ignore negative totals when storage reports them"() {

        when:
        SearchResult out = service.search(PERSON, filter(1, 0))

        then:
        1 * searches.search(_) >> new SearchRows([hit('items', 1)], [items: 0, log: -2])
        out.sources() == []
        out.total() == 0
    }

    def "search should hand the filter to the repository unchanged when called"() {

        given:
        SearchFilter f = filter(1, 0)

        when:
        service.search(PERSON, f)

        then:
        1 * searches.search({ it.is(f) }) >> new SearchRows([], [:])
    }

    @Unroll
    def "search should refuse an API client with #expectedError when #scenario"() {

        when:
        service.search(AGENT, filter(1, 0))

        then:
        1 * clients.maySee(7, 1) >> visible
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * searches.search(_)

        where:
        scenario                                  | visible || expectedError
        "the solution was not shared with it"     | false   || "SolutionNotFoundException"
        "the solution was shared with it"         | true    || "SolutionForbiddenException"
    }

    def "search should answer not found when the solution does not exist"() {

        when:
        service.search(PERSON, filter(9, 0))

        then:
        thrown(SolutionNotFoundException)
        0 * searches.search(_)
    }

    private static SearchFilter filter(int solutionId, int pageSize) {
        new SearchFilter(solutionId, ['x'], null, null, null, null, null, false, false, null, 0, pageSize)
    }

    private static SearchHit hit(String source, int id) {
        new SearchHit(source, 'k', id, null, null, 't' + id, null, null, null, null, null, false, null)
    }
}
