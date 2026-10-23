package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import spock.lang.Unroll

// Story 5653: SolutionRows -- reading one value out of a result row, whatever type the driver handed back.
class SolutionRowsSpec extends EchoSpockBaseSpec {

    @Unroll
    def "#seq. intOf should return #expected when #scenario"() {

        given:
        Map<String, Object> row = [C: value]

        when:
        int result = SolutionRows.intOf(row, 'C')

        then:
        result == expected

        where:
        seq | scenario                         | value         || expected
        1   | "an Integer"                     | 5             || 5
        2   | "a Long"                         | 5L            || 5
        3   | "a BigDecimal with a fraction"   | 9.9           || 9
        4   | "a Double with a fraction"       | 12.7d         || 12
        5   | "Boolean true"                   | true          || 1
        6   | "Boolean false"                  | false         || 0
        7   | "a null"                         | null          || 0
        8   | "numeric text"                   | "42"          || 42
        9   | "numeric text with spaces"       | "  42  "      || 42
        10  | "negative text"                  | "-3"          || -3
        11  | "letters"                        | "abc"         || 0
        12  | "empty text"                     | ""            || 0
        13  | "decimal text"                   | "1.5"         || 0
        14  | "text too large for an int"      | "99999999999" || 0
    }

    def "intOf should read a Short and return 0 when the column is missing"() {

        given:
        Short small = Short.valueOf((short) 7)

        expect:
        SolutionRows.intOf([C: small], 'C') == 7
        SolutionRows.intOf([:], 'Nope') == 0
    }

    @Unroll
    def "idOrNull should return #expected when #scenario"() {

        given:
        Map<String, Object> row = [C: value]

        when:
        Integer result = SolutionRows.idOrNull(row, 'C')

        then:
        result == expected

        where:
        scenario          | value || expected
        "a positive id"   | 12    || 12
        "id one"          | 1     || 1
        "zero"            | 0     || null
        "a negative id"   | -4    || null
        "a null"          | null  || null
        "letters"         | "x"   || null
        "numeric text"    | "8"   || 8
    }

    @Unroll
    def "#seq. boolOf should return #expected when #scenario"() {

        given:
        Map<String, Object> row = [C: value]

        when:
        boolean result = SolutionRows.boolOf(row, 'C')

        then:
        result == expected

        where:
        seq | scenario                  | value   || expected
        1   | "Boolean true"            | true    || true
        2   | "Boolean false"           | false   || false
        3   | "number one"              | 1       || true
        4   | "number zero"             | 0       || false
        5   | "number two"              | 2       || true
        6   | "number minus one"        | -1      || true
        7   | "a Long one"              | 1L      || true
        8   | "text true"               | "true"  || true
        9   | "text TRUE with a space"  | "TRUE " || true
        10  | "text one padded"         | " 1 "   || true
        11  | "text one"                | "1"     || true
        12  | "text zero"               | "0"     || false
        13  | "text yes"                | "yes"   || false
        14  | "text false"              | "false" || false
        15  | "empty text"              | ""      || false
        16  | "a null"                  | null    || false
    }

    @Unroll
    def "strOf should return #expected when #scenario"() {

        given:
        Map<String, Object> row = [C: value]

        when:
        String result = SolutionRows.strOf(row, 'C')

        then:
        result == expected

        where:
        scenario       | value || expected
        "text"         | "abc" || "abc"
        "empty text"   | ""    || ""
        "a number"     | 12    || "12"
        "a boolean"    | true  || "true"
        "a null"       | null  || null
    }

    @Unroll
    def "dateOf should read the moment when #scenario"() {

        given:
        LocalDateTime expected = getLocalDateTime(expectedText)
        LocalDateTime source = getLocalDateTime('09/17/2026 14:30').withSecond(5)
        Object value
        switch (kind) {
            case 'LocalDateTime': value = source; break
            case 'Timestamp': value = Timestamp.valueOf(source); break
            case 'java.util.Date': value = new Date(Timestamp.valueOf(source).time); break
            case 'java.sql.Date': value = java.sql.Date.valueOf(getLocalDate('09/17/2026')); break
            default: value = text
        }

        when:
        LocalDateTime result = SolutionRows.dateOf([C: value], 'C')

        then:
        result == (withSeconds ? expected.withSecond(5) : expected)

        where:
        scenario                    | kind             | text                    || expectedText       | withSeconds
        "a LocalDateTime"           | "LocalDateTime"  | null                    || "09/17/2026 14:30" | true
        "a Timestamp"               | "Timestamp"      | null                    || "09/17/2026 14:30" | true
        "a java.util.Date"          | "java.util.Date" | null                    || "09/17/2026 14:30" | true
        "a java.sql.Date"           | "java.sql.Date"  | null                    || "09/17/2026 00:00" | false
        "ISO text"                  | "text"           | "2026-09-17T14:30:05"   || "09/17/2026 14:30" | true
        "text with a space"         | "text"           | "2026-09-17 14:30:05"   || "09/17/2026 14:30" | true
        "padded text, no seconds"   | "text"           | " 2026-09-17 14:30 "    || "09/17/2026 14:30" | false
    }

    @Unroll
    def "dateOf should return null rather than fail when #scenario"() {

        given:
        Map<String, Object> row = [C: value]

        when:
        LocalDateTime result = SolutionRows.dateOf(row, 'C')

        then:
        result == expected

        where:
        scenario              | value              || expected
        "a null"              | null               || null
        "empty text"          | ""                 || null
        "not a date"          | "not a date"       || null
        "an impossible date"  | "2026-13-45T00:00" || null
        "a number"            | 42                 || null
        "a date with no time" | "2026-09-17"       || null
    }
}
