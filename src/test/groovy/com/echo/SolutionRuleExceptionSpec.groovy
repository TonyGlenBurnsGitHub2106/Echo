package com.echo

import spock.lang.Unroll

// Story 5653: SolutionRuleException -- a rule's sentence, carried verbatim to the caller (409).
class SolutionRuleExceptionSpec extends EchoSpockBaseSpec {

    @Unroll
    def "constructor should keep the message exactly when #scenario"() {

        when:
        SolutionRuleException ex = new SolutionRuleException(message)

        then:
        ex.message == expectedMessage
        ex instanceof RuntimeException
        ex.cause == null

        where:
        scenario          | message                                                    || expectedMessage
        "a rule sentence" | "A project by that name already exists. Nothing was saved." || "A project by that name already exists. Nothing was saved."
        "a null message"  | null                                                       || null
    }
}
