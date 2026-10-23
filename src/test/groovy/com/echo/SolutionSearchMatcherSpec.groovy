package com.echo

import com.echo.SolutionSearchModels.SearchFilter
import static com.echo.SolutionFixtures.textList

import spock.lang.Unroll

// Story 5653: SolutionSearchMatcher -- the OR / AND / NOT term rules and the snippet, as stp_Solution_Search has them.
class SolutionSearchMatcherSpec extends EchoSpockBaseSpec {

    @Unroll
    def "#seq. matches should return #expected when #scenario"() {

        given:
        SolutionSearchMatcher m = matcher(textList(orCsv), textList(andCsv), textList(notCsv))

        expect:
        m.matches(text) == expected

        where:
        seq | scenario                                  | orCsv      | andCsv | notCsv   | text             || expected
        1   | "there are no terms"                      | ""         | ""     | ""       | "anything"       || true
        2   | "there are no terms and no text"          | ""         | ""     | ""       | null             || true
        3   | "an OR term is in it, any case"           | "deploy"   | ""     | ""       | "We DEPLOYED it" || true
        4   | "the OR term is not in it"                | "deploy"   | ""     | ""       | "nothing here"   || false
        5   | "the second OR term is in it"             | "x;deploy" | ""     | ""       | "deploy"         || true
        6   | "every AND term is in it, any order"      | ""         | "a;b"  | ""       | "b then a"       || true
        7   | "one AND term is missing"                 | ""         | "a;zz" | ""       | "b then a"       || false
        8   | "a NOT term is in it, any case"           | ""         | ""     | "secret" | "a Secret plan"  || false
        9   | "the NOT term is absent"                  | ""         | ""     | "secret" | "a plan"         || true
        10  | "OR, AND and NOT are all satisfied"       | "plan"     | "a"    | "b"      | "a plan"         || true
        11  | "OR and AND hold but a NOT term is in it" | "plan"     | "a"    | "b"      | "a plan b"       || false
        12  | "an OR term against no text"              | "plan"     | ""     | ""       | null             || false
        13  | "an AND term against no text"             | ""         | "x"    | ""       | null             || false
        14  | "a NOT term against no text"              | ""         | ""     | "x"      | null             || true
        15  | "the term is inside a word"               | "ill"      | ""     | ""       | "will"           || true
    }

    @Unroll
    def "matchTerm should return #expected when #scenario"() {

        given:
        SolutionSearchMatcher m = matcher(['Zeta', 'Beta'], ['ALPHA'], ['beta'])

        expect:
        m.matchTerm(body) == expected

        where:
        scenario                                    | body              || expected
        "every positive term is in it"              | "alpha beta zeta" || "Zeta"
        "the second OR term and the AND term are in it" | "alpha beta"  || "Beta"
        "only the AND term is in it"                | "alpha only"      || "ALPHA"
        "no term is in it"                          | "none"            || null
        "there is no body"                          | null              || null
    }

    def "matchTerm should never offer a NOT term when only NOT terms are given"() {

        given:
        SolutionSearchMatcher m = matcher([], [], ['x'])

        expect:
        m.matchTerm('x x x') == null
    }

    def "SNIPPET_WIDTH should be 240 when read"() {

        expect:
        SolutionSearchMatcher.SNIPPET_WIDTH == 240
    }

    @Unroll
    def "snippet should return the body whole, line breaks and tabs made spaces, when #scenario"() {

        expect:
        SolutionSearchMatcher.snippet(body, term) == expected

        where:
        scenario                      | body                 | term || expected
        "the body is short"           | "one\r\ntwo\tthree"  | "two" || "one  two three"
        "there is no term"            | "hello"              | null  || "hello"
        "the term is blank"           | "hello"              | "  "  || "hello"
        "there is no body"            | null                 | "x"   || ""
        "the body is empty"           | ""                   | null  || ""
    }

    @Unroll
    def "snippet should cut at 240 and mark it when #scenario"() {

        given:
        String body = getReplicatedString('a', bodyLength)

        when:
        String s = SolutionSearchMatcher.snippet(body, term)

        then:
        s == getReplicatedString('a', expectedKept) + (expectedDots ? '...' : '')

        where:
        scenario                                  | bodyLength | term  || expectedKept | expectedDots
        "the body is long and there is no term"   | 300        | null  || 240          | true
        "the body is long and the term is absent" | 300        | "zzz" || 240          | true
        "the body is exactly 240 long"            | 240        | null  || 240          | false
        "the body is 241 long"                    | 241        | null  || 240          | true
        "the body is 239 long"                    | 239        | null  || 239          | false
    }

    def "snippet should keep the start with no leading dots when the term is near the start"() {

        given:
        String body = getReplicatedString('x', 79) + 'NEEDLE' + getReplicatedString('y', 300)

        when:
        String s = SolutionSearchMatcher.snippet(body, 'needle')

        then:
        !s.startsWith('...')
        s.endsWith('...')
        s.length() == 240 + 3
        s.contains('NEEDLE')
    }

    def "snippet should place the term a third of the way in with dots both sides when the term is deep in the body"() {

        given:
        String body = getReplicatedString('x', 500) + 'NEEDLE' + getReplicatedString('y', 500)

        when:
        String s = SolutionSearchMatcher.snippet(body, 'Needle')

        then:
        s.startsWith('...')
        s.endsWith('...')
        s.length() == 3 + 240 + 3
        s.substring(3).indexOf('NEEDLE') == 80
    }

    def "snippet should show leading dots and no trailing ones when the term is near the end"() {

        given:
        String body = getReplicatedString('x', 500) + 'NEEDLE' + getReplicatedString('y', 10)

        when:
        String s = SolutionSearchMatcher.snippet(body, 'needle')

        then:
        s.startsWith('...')
        !s.endsWith('...')
        s.endsWith('NEEDLE' + getReplicatedString('y', 10))
        s.length() == 3 + (516 - 420)
    }

    @Unroll
    def "snippet should mark the cut only past the window when #scenario"() {

        given:
        String body = getReplicatedString('x', before) + 'N' + getReplicatedString('y', after)

        when:
        String s = SolutionSearchMatcher.snippet(body, 'n')

        then:
        s.startsWith('...') == expectedLeading
        s.endsWith('...') == expectedTrailing

        where:
        scenario                                         | before | after || expectedLeading | expectedTrailing
        "the term is at 80, one before the shift"         | 79     | 160   || false           | false
        "the term is at 81, exactly at the shift"         | 80     | 159   || false           | false
        "the term is at 82, one after the shift"          | 81     | 158   || true            | false
    }

    private static SolutionSearchMatcher matcher(List<String> or, List<String> and, List<String> not) {
        new SolutionSearchMatcher(new SearchFilter(1, or, and, not, null, null, null, false, false, null, 0, 0))
    }
}
