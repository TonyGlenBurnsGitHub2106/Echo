package com.echo

import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.HOUSE
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.apiClient
import static com.echo.SolutionFixtures.link
import static com.echo.SolutionFixtures.solution

import spock.lang.Unroll

// Story 5653: SolutionShareService -- sharing is the owner's act alone; a client is told 404 before it is told 403.
class SolutionShareServiceSpec extends EchoSpockBaseSpec {

    SolutionRepository repo = Mock()
    ApiClientService clients = Mock()
    SolutionShareService service = new SolutionShareService(clients, new SolutionAccess(repo, clients), repo, 'https://echo.example')

    def setup() {
        repo.findSolution(1) >> solution(id: 1, name: 'Alpha', alias: 'claims')
        clients.list() >> [apiClient(id: 7, clientCode: 'rex', clientName: 'Rex'),
                           apiClient(id: 8, clientCode: 'ida', clientName: 'Ida', enabled: false, hasKey: false)]
        clients.find(7) >> apiClient(id: 7, clientCode: 'rex', clientName: 'Rex', keyHint: 'wxyz')
        clients.find(8) >> apiClient(id: 8, clientCode: 'ida', clientName: 'Ida', hasKey: false, keyHint: null)
    }

    // ---- shares -------------------------------------------------------------------------

    def "shares should list every client marked shared or not, keys in a fixed order, when the owner asks"() {

        when:
        List<Map<String, Object>> out = service.shares(PERSON, 1)

        then:
        1 * clients.clientsForSolution(1) >> [link(7, 1)]
        out == [[apiClientId: 7, clientCode: 'rex', clientName: 'Rex', enabled: true, hasKey: true, shared: true],
                [apiClientId: 8, clientCode: 'ida', clientName: 'Ida', enabled: false, hasKey: false, shared: false]]
        out[0].keySet() as List == ['apiClientId', 'clientCode', 'clientName', 'enabled', 'hasKey', 'shared']
    }

    def "shares should be an empty list when there are no clients at all"() {

        given:
        ApiClientService c2 = Mock()
        SolutionShareService svc = new SolutionShareService(c2, new SolutionAccess(repo, c2), repo, 'u')

        when:
        List<Map<String, Object>> out = svc.shares(HOUSE, 1)

        then:
        1 * c2.clientsForSolution(1) >> []
        1 * c2.list() >> []
        out == []
    }

    @Unroll
    def "shares should refuse an API client with #expectedError when #scenario"() {

        when:
        service.shares(AGENT, 1)

        then:
        1 * clients.maySee(7, 1) >> visible
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        ex.message == expectedMessage
        0 * clients.clientsForSolution(_)

        where:
        scenario                              | visible || expectedError                | expectedMessage
        "the solution was not shared with it" | false   || "SolutionNotFoundException"  | "Solution not found."
        "the solution was shared with it"     | true    || "SolutionForbiddenException" | "Only the owner can see who a solution is shared with."
    }

    def "shares should answer not found when the solution does not exist"() {

        when:
        service.shares(PERSON, 9)

        then:
        thrown(SolutionNotFoundException)
    }

    // ---- brief --------------------------------------------------------------------------

    def "brief should name the solution, its alias and the base URL, with no key in it, when the client is shared"() {

        given:
        clients.clientsForSolution(1) >> [link(7, 1)]

        when:
        Map<String, Object> out = service.brief(PERSON, 1, 7)

        then:
        out.keySet() as List == ['apiClientId', 'clientName', 'hasKey', 'keyHint', 'keySlot', 'text']
        out.apiClientId == 7
        out.clientName == 'Rex'
        out.hasKey == true
        out.keyHint == 'wxyz'
        out.keySlot == ApiClientBrief.KEY_SLOT
        String text = out.text
        text.contains('The solution shared with you is "Alpha" (id 1).')
        text.contains('People call it "claims"')
        text.contains('GET https://echo.example/api/solutions/by-alias/claims')
        text.contains('- Base URL: https://echo.example')
        text.contains('X-Echo-Client: rex')
        text.contains('X-Echo-Client-Key: ' + ApiClientBrief.KEY_SLOT)
        !text.contains('X-Echo-Key:')
        !text.contains('{{BASE_URL}}')
    }

    def "brief should leave the alias lines out when the solution has no alias"() {

        given:
        SolutionRepository repo2 = Mock()
        repo2.findSolution(1) >> solution(id: 1, name: 'Alpha', alias: '')
        clients.clientsForSolution(1) >> [link(7, 1)]
        SolutionShareService svc = new SolutionShareService(clients, new SolutionAccess(repo2, clients), repo2, 'u')

        when:
        String text = svc.brief(PERSON, 1, 7).text

        then:
        text.contains('"Alpha" (id 1)')
        !text.contains('People call it')
    }

    @Unroll
    def "brief should answer API client not found when #scenario"() {

        given:
        clients.clientsForSolution(1) >> [link(linkedId, 1)]

        when:
        service.brief(PERSON, 1, clientId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                                      | linkedId | clientId || expectedMessage
        "the client exists but is not shared"         | 7        | 8        || "API client not found."
        "the client does not exist"                   | 7        | 99       || "API client not found."
        "a stale link names a client that is gone"    | 99       | 99       || "API client not found."
    }

    @Unroll
    def "brief should refuse an API client with #expectedError when #scenario"() {

        when:
        service.brief(AGENT, 1, 7)

        then:
        1 * clients.maySee(7, 1) >> visible
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        ex.message == expectedMessage

        where:
        scenario                              | visible || expectedError                | expectedMessage
        "the solution was not shared with it" | false   || "SolutionNotFoundException"  | "Solution not found."
        "the solution was shared with it"     | true    || "SolutionForbiddenException" | "Only the owner can read a client's brief."
    }

    // ---- setShared ----------------------------------------------------------------------

    def "setShared should link, report files moved off the repo, and answer the fresh state when a repo-stored solution is shared"() {

        given:
        SolutionRepository repo2 = Mock()
        SolutionShareService svc = new SolutionShareService(clients, new SolutionAccess(repo2, clients), repo2, 'u')
        repo2.findSolution(1) >>> [solution(id: 1, saveFilesToRepo: true), solution(id: 1, saveFilesToRepo: true),
                                    solution(id: 1, saveFilesToRepo: true), solution(id: 1, saveFilesToRepo: false)]

        when:
        Map<String, Object> out = svc.setShared(PERSON, 1, 7, true)

        then:
        1 * clients.setSolutionLink(7, 1, true) >> true
        clients.clientsForSolution(1) >> [link(7, 1)]
        out.keySet() as List == ['shares', 'filesMovedOffRepo', 'solution']
        out.filesMovedOffRepo == true
        out.shares.find { Map<String, Object> s -> s.apiClientId == 7 }.shared == true
        out.solution.saveFilesToRepo() == false
    }

    @Unroll
    def "setShared should report no files moved when #scenario"() {

        given:
        repo.findSolution(2) >> solution(id: 2, saveFilesToRepo: true)
        clients.clientsForSolution(_) >> []

        when:
        Map<String, Object> out = service.setShared(PERSON, solutionId, 7, shared)

        then:
        1 * clients.setSolutionLink(7, solutionId, shared) >> true
        out.filesMovedOffRepo == expectedMoved

        where:
        scenario                                        | solutionId | shared || expectedMoved
        "a solution already off the repo is shared"     | 1          | true   || false
        "a repo-stored solution is un-shared"           | 2          | false  || false
    }

    def "setShared should answer API client not found and link nothing when the client does not exist"() {

        when:
        service.setShared(PERSON, 1, 99, true)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'API client not found.'
        0 * clients.setSolutionLink(*_)
    }

    @Unroll
    def "setShared should refuse an API client with #expectedError when #scenario"() {

        when:
        service.setShared(AGENT, 1, 7, shared)

        then:
        1 * clients.maySee(7, 1) >> visible
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * clients.setSolutionLink(*_)

        where:
        scenario                                    | shared | visible || expectedError
        "it shares a solution not given to it"      | true   | false   || "SolutionNotFoundException"
        "it un-shares a solution not given to it"   | false  | false   || "SolutionNotFoundException"
        "it shares a solution given to it"          | true   | true    || "SolutionForbiddenException"
        "it un-shares a solution given to it"       | false  | true    || "SolutionForbiddenException"
    }

    def "setShared should answer not found when the solution does not exist"() {

        when:
        service.setShared(PERSON, 9, 7, true)

        then:
        thrown(SolutionNotFoundException)
        0 * clients.setSolutionLink(*_)
    }
}
