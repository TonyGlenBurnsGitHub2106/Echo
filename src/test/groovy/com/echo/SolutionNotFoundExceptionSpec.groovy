package com.echo

import spock.lang.Unroll

// Story 5653: SolutionNotFoundException -- "X not found." whether it is missing or merely hidden from a client.
class SolutionNotFoundExceptionSpec extends EchoSpockBaseSpec {

    @Unroll
    def "constructor should read '#expectedMessage' when #scenario"() {

        when:
        SolutionNotFoundException ex = new SolutionNotFoundException(what)

        then:
        ex.message == expectedMessage

        where:
        scenario              | what         || expectedMessage
        "a solution"          | "Solution"   || "Solution not found."
        "a two-word thing"    | "Log entry"  || "Log entry not found."
        "an API client"       | "API client" || "API client not found."
        "an empty name"       | ""           || " not found."
        "a null name"         | null         || "null not found."
    }

    def "constructor should make an unchecked exception when called"() {

        expect:
        new SolutionNotFoundException('x') instanceof RuntimeException
    }
}
