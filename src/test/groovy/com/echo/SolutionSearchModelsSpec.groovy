package com.echo

import java.time.LocalDate
import java.time.LocalDateTime

import com.echo.SolutionSearchModels.SearchFilter
import com.echo.SolutionSearchModels.SearchHit
import com.echo.SolutionSearchModels.SearchResult
import com.echo.SolutionSearchModels.SearchSource
import static com.echo.SolutionFixtures.textList

import spock.lang.Unroll

// Story 5653: SolutionSearchModels -- the tab list, its labels, the self-cleaning filter and the hit mapper.
class SolutionSearchModelsSpec extends EchoSpockBaseSpec {

    def "SOURCES and LABELS should name the twelve tabs in page order, each with a label, when read"() {

        expect:
        SolutionSearchModels.SOURCES == ['overview', 'items', 'log', 'notes', 'terms', 'tests', 'qa', 'config', 'grid', 'files',
                                         'conversations', 'web']
        SolutionSearchModels.LABELS.keySet() == SolutionSearchModels.SOURCES as Set
        SolutionSearchModels.LABELS.overview == 'Projects'
        SolutionSearchModels.LABELS.tests == 'Testing'
        SolutionSearchModels.LABELS.grid == 'Configuration II'
    }

    def "SearchFilter should trim terms, drop blanks and nulls, and cut each at 200 characters when constructed"() {

        given:
        List<String> or = [' a ', '', null, '   ', 'b']
        List<String> and = [getReplicatedString('x', 250)]

        when:
        SearchFilter f = filter(or, and, null, null, null, null, null, false, false, 0, 0)

        then:
        f.orTerms() == ['a', 'b']
        f.andTerms() == [getReplicatedString('x', 200)]
        f.notTerms() == []
    }

    def "SearchFilter should trim a padded 200-character term before cutting it when constructed"() {

        given:
        List<String> or = ['  ' + getReplicatedString('y', 200) + '  ']

        expect:
        filter(or, null, null, null, null, null, null, false, false, 0, 0).orTerms() == [getReplicatedString('y', 200)]
    }

    @Unroll
    def "SearchFilter should keep sources #expectedCsv when #scenario"() {

        given:
        List<String> sources = textList(sourcesCsv)
        List<String> expected = textList(expectedCsv)

        when:
        SearchFilter f = filter(null, null, null, sources, null, null, null, false, false, 0, 0)

        then:
        f.sources() == expected

        where:
        scenario                                | sourcesCsv                      || expectedCsv
        "no sources are named"                  | null                            || null
        "the list is empty"                     | ""                              || null
        "only an unknown tab is named"          | "nope"                          || null
        "names are padded, in caps, repeated"   | " Items ;LOG;items;<null>"      || "items;log"
        "an unknown tab sits between two known" | "web;bogus;overview"            || "web;overview"
    }

    @Unroll
    def "SearchFilter should keep source #expected when #scenario"() {

        when:
        SearchFilter f = filter(null, null, null, null, null, null, source, false, false, 0, 0)

        then:
        f.source() == expected

        where:
        scenario                 | source    || expected
        "the source is null"     | null      || null
        "the source is empty"    | ""        || null
        "the source is spaces"   | "  "      || null
        "the source is unknown"  | "nope"    || null
        "the source is padded"   | " Notes " || "notes"
        "the source is known"    | "qa"      || "qa"
    }

    @Unroll
    def "SearchFilter should normalize to offset #expectedOffset and page size #expectedSize when #scenario"() {

        when:
        SearchFilter f = filter(null, null, null, null, null, null, null, false, false, offset, size)

        then:
        f.offset() == expectedOffset
        f.pageSize() == expectedSize

        where:
        scenario                     | offset | size || expectedOffset | expectedSize
        "both are zero"              | 0      | 0    || 0              | 25
        "both are negative"          | -9     | -1   || 0              | 25
        "both are sensible"          | 5      | 10   || 5              | 10
        "the page is at the maximum" | 5      | 500  || 5              | 500
        "the page is one over"       | 5      | 501  || 5              | 500
    }

    def "SearchFilter should page 25 by default and 500 at most when read"() {

        expect:
        SearchFilter.DEFAULT_PAGE == 25
        SearchFilter.MAX_PAGE == 500
    }

    @Unroll
    def "#seq. isEmpty should return #expected when #scenario"() {

        given:
        SearchFilter f = filter(textList(orCsv), textList(andCsv), textList(notCsv), textList(sourcesCsv), getLocalDate(fromText),
            getLocalDate(throughText), null, titlesOnly, openOnly, 0, 0)

        expect:
        f.isEmpty() == expected

        where:
        seq | scenario                               | orCsv | andCsv | notCsv | sourcesCsv | fromText     | throughText  | titlesOnly | openOnly || expected
        1   | "nothing is asked"                     | null  | null   | null   | null       | null         | null         | false      | false    || true
        2   | "the only term is blank"               | "  "  | null   | null   | null       | null         | null         | false      | false    || true
        3   | "only tabs and switches are set"       | null  | null   | null   | "items"    | null         | null         | true       | true     || true
        4   | "an OR term is given"                  | "a"   | null   | null   | null       | null         | null         | false      | false    || false
        5   | "an AND term is given"                 | null  | "a"    | null   | null       | null         | null         | false      | false    || false
        6   | "a NOT term is given"                  | null  | null   | "a"    | null       | null         | null         | false      | false    || false
        7   | "a from date is given"                 | null  | null   | null   | null       | "09/01/2026" | null         | false      | false    || false
        8   | "a through date is given"              | null  | null   | null   | null       | null         | "09/30/2026" | false      | false    || false
    }

    def "SearchHit.of should map every column, and read no parent when the parent id is zero"() {

        given:
        LocalDateTime when = getLocalDateTime('09/17/2026 09:00')
        Map<String, Object> row = [Source: 'items', Kind: 'item', ID: 5, ParentID: 10, ParentTitle: 'P', Title: 'T', Snippet: 's',
                                   MatchTerm: 'm', ItemDate: when, StatusName: 'Done', ColorHex: '#0f0', IsClosed: 1, Extra: 'x']

        expect:
        SearchHit.of(row) == new SearchHit('items', 'item', 5, 10, 'P', 'T', 's', 'm', when, 'Done', '#0f0', true, 'x')
        SearchHit.of([ParentID: 0]).parentId() == null
        SearchHit.of([:]).id() == 0
    }

    def "SearchSource and SearchResult should keep what they are given when constructed"() {

        given:
        SearchSource src = new SearchSource('log', 'Log', 3, [])
        SearchResult res = new SearchResult(1, [src], 3, 25)

        expect:
        res.sources()[0].label() == 'Log'
        res.total() == 3
        res.pageSize() == 25
    }

    private static SearchFilter filter(List<String> or, List<String> and, List<String> not, List<String> sources, LocalDate from,
                                       LocalDate through, String source, boolean titlesOnly, boolean openOnly, int offset, int pageSize) {
        new SearchFilter(1, or, and, not, sources, from, through, titlesOnly, openOnly, source, offset, pageSize)
    }
}
