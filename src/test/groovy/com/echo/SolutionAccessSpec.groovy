package com.echo

// Story 5653: the second fence -- a client sees only the solutions linked to it, and is told
// "not found" (never "forbidden") about the rest; owner actions stay human.

import com.echo.SolutionModels.Solution

import spock.lang.Unroll

class SolutionAccessSpec extends EchoSpockBaseSpec {

    SolutionRepository solutions = Mock()
    ApiClientService clients = Mock()
    SolutionAccess access = new SolutionAccess(solutions, clients)

    @Unroll
    def "requireSolution should throw not found without touching the repository when the id is #scenario"() {

        given:
        int requested = (int) id

        when:
        access.requireSolution(caller('USER'), requested)

        then:
        SolutionNotFoundException ex = thrown(SolutionNotFoundException)
        ex.message == 'Solution not found.'
        0 * solutions._
        0 * clients._

        where:
        scenario          | id
        'zero'            | 0
        'negative'        | -1
        'the minimum int' | -2147483648
    }

    @Unroll
    def "requireSolution should throw not found when the solution is missing and the caller is #scenario"() {

        when:
        access.requireSolution(caller(kind), 5)

        then:
        1 * solutions.findSolution(5) >> null
        thrown(SolutionNotFoundException)
        0 * clients._

        where:
        scenario      | kind
        'a person'    | 'USER'
        'the house'   | 'SERVICE'
        'a client'    | 'CLIENT'
    }

    @Unroll
    def "requireSolution should answer the solution without a link check when the caller is #scenario"() {

        given:
        Solution s = solution(5)

        when:
        Solution got = access.requireSolution(caller(kind), 5)

        then:
        1 * solutions.findSolution(5) >> s
        0 * clients._
        got.is(s)

        where:
        scenario      | kind
        'a person'    | 'USER'
        'the house'   | 'SERVICE'
    }

    def "requireSolution should answer the solution when a client has it linked"() {

        given:
        Solution s = solution(5)

        when:
        Solution got = access.requireSolution(caller('CLIENT'), 5)

        then:
        1 * solutions.findSolution(5) >> s
        1 * clients.maySee(7, 5) >> true
        got.is(s)
    }

    def "requireSolution should throw not found when a client asks for an existing solution it is not linked to"() {

        when:
        access.requireSolution(caller('CLIENT'), 5)

        then:
        1 * solutions.findSolution(5) >> solution(5)
        1 * clients.maySee(7, 5) >> false
        SolutionNotFoundException ex = thrown(SolutionNotFoundException)
        ex.message == 'Solution not found.'
    }

    def "requireSolution should fail closed with an exception when a client caller has a null client id"() {

        when:
        access.requireSolution(new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', null), 5)

        then:
        1 * solutions.findSolution(5) >> solution(5)
        thrown(NullPointerException)
    }

    @Unroll
    def "requireHuman should let the caller through when it is #scenario"() {

        when:
        access.requireHuman(caller(kind), 'Delete a solution')

        then:
        noExceptionThrown()
        0 * solutions._
        0 * clients._

        where:
        scenario      | kind
        'a person'    | 'USER'
        'the house'   | 'SERVICE'
    }

    def "requireHuman should throw forbidden with the action as the message when the caller is a client"() {

        when:
        access.requireHuman(caller('CLIENT'), 'Only a person may share a solution.')

        then:
        SolutionForbiddenException ex = thrown(SolutionForbiddenException)
        ex.message == 'Only a person may share a solution.'
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Solution solution(int id) {
        Solution.of([SolutionID: id, SolutionName: 'S' + id])
    }

    private static SolutionCaller caller(String kind) {
        switch (kind) {
            case 'USER': return new SolutionCaller(SolutionCaller.Kind.USER, 'tony', null)
            case 'SERVICE': return new SolutionCaller(SolutionCaller.Kind.SERVICE, 'felix', null)
            case 'CLIENT': return new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7)
            default: throw new IllegalArgumentException(kind)
        }
    }
}
