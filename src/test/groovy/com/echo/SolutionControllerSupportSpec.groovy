package com.echo

import java.time.LocalDateTime

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

import spock.lang.Unroll

// Story 5653: SolutionControllerSupport -- who is calling, the four refusals and their four answers, and the small parsers.
// SolutionControllerSupport is abstract; a minimal anonymous subclass is the instance under test.
class SolutionControllerSupportSpec extends EchoSpockBaseSpec {

    SolutionCallerResolver resolver = Mock()
    SolutionControllerSupport support = new SolutionControllerSupport(resolver) {}

    def "caller should ask the resolver about this very request when called"() {

        given:
        HttpServletRequest req = Mock()
        SolutionCaller who = SolutionFixtures.AGENT

        when:
        SolutionCaller c = support.caller(req)

        then:
        1 * resolver.resolve(req) >> who
        c.is(who)
    }

    @Unroll
    def "#handler should answer #expectedStatus with error '#expectedError' when #scenario"() {

        given:
        RuntimeException ex
        switch (handler) {
            case 'rule': ex = new SolutionRuleException(message); break
            case 'notFound': ex = new SolutionNotFoundException('Item'); break
            case 'forbidden': ex = new SolutionForbiddenException(message); break
            default: ex = new IllegalArgumentException(message)
        }

        when:
        ResponseEntity<?> r = support."$handler"(ex)

        then:
        r.statusCode == HttpStatus.valueOf(expectedStatus)
        r.body == [ok: false, error: expectedError, message: expectedMessage]
        (r.body as Map).keySet() as List == ['ok', 'error', 'message']

        where:
        scenario                      | handler      | message                                || expectedStatus | expectedError | expectedMessage
        "a rule refuses"              | "rule"       | "Duplicate. Nothing was saved."        || 409            | "rule"        | "Duplicate. Nothing was saved."
        "the record is not found"     | "notFound"   | null                                   || 404            | "not_found"   | "Item not found."
        "the action is the owner's"   | "forbidden"  | "Only a person may delete a solution." || 403            | "forbidden"   | "Only a person may delete a solution."
        "the request is malformed"    | "badRequest" | "A name is required."                  || 400            | "bad_request" | "A name is required."
    }

    def "error and ok should keep their keys in order and accept nulls when called"() {

        expect:
        SolutionControllerSupport.error('x', null) == [ok: false, error: 'x', message: null]
        SolutionControllerSupport.ok('deletedId', 5) == [ok: true, deletedId: 5]
        SolutionControllerSupport.ok('deletedId', 5).keySet() as List == ['ok', 'deletedId']
        SolutionControllerSupport.ok('x', null) == [ok: true, x: null]
    }

    def "ok should answer a map a caller may add to when a controller extends it"() {

        when:
        Map<String, Object> m = SolutionControllerSupport.ok('deletedId', 1)
        m.put('itemsRemoved', 3)

        then:
        m == [ok: true, deletedId: 1, itemsRemoved: 3]
    }

    @Unroll
    def "required should return '#expected' when #scenario"() {

        expect:
        SolutionControllerSupport.required(value, 'A name') == expected

        where:
        scenario               | value       || expected
        "the value is plain"   | "Alpha"     || "Alpha"
        "the value is padded"  | "  Alpha  " || "Alpha"
        "the value is one letter" | "a"      || "a"
    }

    @Unroll
    def "required should refuse naming the field when #scenario"() {

        when:
        SolutionControllerSupport.required(value, 'A title')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                 | value  || expectedMessage
        "the value is null"      | null   || "A title is required."
        "the value is empty"     | ""     || "A title is required."
        "the value is spaces"    | "   "  || "A title is required."
        "the value is whitespace" | "\t\n" || "A title is required."
    }

    @Unroll
    def "#seq. date should return #expectedText when #scenario"() {

        given:
        LocalDateTime expected = expectedText == null ? null : getLocalDateTime(expectedText).plusSeconds(seconds).plusNanos(nanos)

        expect:
        SolutionControllerSupport.date(value, 'The due date') == expected

        where:
        seq | scenario                         | value                    || expectedText       | seconds | nanos
        1   | "the value is null"              | null                     || null               | 0       | 0
        2   | "the value is empty"             | ""                       || null               | 0       | 0
        3   | "the value is spaces"            | "   "                    || null               | 0       | 0
        4   | "it is a date"                   | "2026-09-17"             || "09/17/2026 00:00" | 0       | 0
        5   | "it is a padded date"            | " 2026-09-17 "           || "09/17/2026 00:00" | 0       | 0
        6   | "it has hours and minutes"       | "2026-09-17T14:30"       || "09/17/2026 14:30" | 0       | 0
        7   | "it has seconds"                 | "2026-09-17T14:30:15"    || "09/17/2026 14:30" | 15      | 0
        8   | "it has a fraction of a second"  | "2026-09-17T14:30:15.25" || "09/17/2026 14:30" | 15      | 250000000
        9   | "it is the day before the end of February" | "2026-02-27"   || "02/27/2026 00:00" | 0       | 0
        10  | "it is the end of February"      | "2026-02-28"             || "02/28/2026 00:00" | 0       | 0
        11  | "it is the day after the end of February" | "2026-03-01"    || "03/01/2026 00:00" | 0       | 0
    }

    @Unroll
    def "#seq. date should refuse with a 400 sentence naming the field when #scenario"() {

        when:
        SolutionControllerSupport.date(value, 'The due date')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        seq | scenario                         | value                    || expectedMessage
        1   | "it is a word"                   | "tomorrow"               || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        2   | "the month has one digit"        | "2026-9-17"              || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        3   | "the day does not exist"         | "2026-02-30"             || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        4   | "February 29 in a common year"   | "2026-02-29"             || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        5   | "a space replaces the T"         | "2026-09-17 14:30"       || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        6   | "it is in US order"              | "09/17/2026"             || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        7   | "the hour does not exist"        | "2026-09-17T25:00"       || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        8   | "it has a zone letter"           | "2026-09-17Z"            || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        9   | "it is ten letters"              | "abcdefghij"             || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
        10  | "it has an offset"               | "2026-09-17T14:30+02:00" || "The due date must look like 2026-09-17 or 2026-09-17T14:30."
    }

    @Unroll
    def "orZero and orFalse should return #expectedInt and #expectedBool when #scenario"() {

        expect:
        SolutionControllerSupport.orZero(number) == expectedInt
        SolutionControllerSupport.orFalse(flag) == expectedBool

        where:
        scenario                  | number | flag  || expectedInt | expectedBool
        "both are null"           | null   | null  || 0           | false
        "both are zero and false" | 0      | false || 0           | false
        "both are set"            | 7      | true  || 7           | true
        "the number is negative"  | -3     | false || -3          | false
    }
}
