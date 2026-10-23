package com.echo

// Story 5653: the reduced caller identity the Solutions family works with.

import spock.lang.Unroll

class SolutionCallerSpec extends EchoSpockBaseSpec {

    @Unroll
    def "isClient should answer #expected when the kind is #scenario"() {

        expect:
        new SolutionCaller(kind, 'x', null).isClient() == expected

        where:
        scenario    | kind                        || expected
        'USER'      | SolutionCaller.Kind.USER    || false
        'SERVICE'   | SolutionCaller.Kind.SERVICE || false
        'CLIENT'    | SolutionCaller.Kind.CLIENT  || true
        'null'      | null                        || false
    }

    def "Kind values should be USER, SERVICE, CLIENT in order when listed"() {

        expect:
        SolutionCaller.Kind.values()*.name() == ['USER', 'SERVICE', 'CLIENT']
    }

    def "the record should keep its fields and compare by value when built"() {

        given:
        SolutionCaller a = new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7)

        expect:
        a.kind() == SolutionCaller.Kind.CLIENT
        a.memberCode() == 'rex'
        a.apiClientId() == 7
        a == new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7)
        a != new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 8)
        a.hashCode() == new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7).hashCode()
    }
}
