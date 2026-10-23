package com.echo

import spock.lang.Unroll

// Story 5653: SolutionForbiddenException -- the owner's actions, refused to everyone else (403).
class SolutionForbiddenExceptionSpec extends EchoSpockBaseSpec {

    @Unroll
    def "constructor should keep the message exactly when #scenario"() {

        when:
        SolutionForbiddenException ex = new SolutionForbiddenException(message)

        then:
        ex.message == expectedMessage
        ex instanceof RuntimeException
        ex.cause == null

        where:
        scenario         | message                                || expectedMessage
        "an owner action" | "Only a person may delete a solution." || "Only a person may delete a solution."
        "a null message" | null                                   || null
    }
}
