package com.echo

import java.time.LocalDateTime

import com.echo.SolutionTermModels.Term
import com.echo.SolutionTermModels.TermDraft
import com.echo.SolutionTermModels.TermFilter
import com.echo.SolutionTermModels.TermLookup
import com.echo.SolutionTermModels.TermLookups
import com.echo.SolutionTermModels.TermPage

import spock.lang.Unroll

/**
 * Story 5653: the Terms tab's shapes -- the filter's normalization, the row mappers, the category list.
 */
class SolutionTermModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('10/09/2026 12:00')

    /** "a,b" to ["a", "b"]; "" to an empty list. */
    private static List<String> namesOf(String csv) {
        csv.isEmpty() ? [] : (csv.split(',') as List<String>)
    }

    @Unroll
    def "#seq. TermFilter should set pageSize #expected when #scenario"() {

        when:
        TermFilter f = new TermFilter(1, null, null, null, false, null, false, 0, asked)

        then:
        f.pageSize() == expected

        where:
        seq | scenario                       | asked             || expected
        1   | 'zero is asked'                | 0                 || 50
        2   | 'a negative is asked'          | -1                || 50
        3   | 'one is asked'                 | 1                 || 1
        4   | 'the default is asked'         | 50                || 50
        5   | 'one under the cap is asked'   | 499               || 499
        6   | 'the cap is asked'             | 500               || 500
        7   | 'one over the cap is asked'    | 501               || 500
        8   | 'far over the cap is asked'    | 100000            || 500
        9   | 'the smallest int is asked'    | -2147483648       || 50
    }

    @Unroll
    def "TermFilter should set offset #expected when #scenario"() {

        when:
        TermFilter f = new TermFilter(1, null, null, null, false, null, false, offset, 0)

        then:
        f.offset() == expected

        where:
        scenario                     | offset            || expected
        'the offset is negative'     | -10               || 0
        'the offset is the minimum'  | -2147483648       || 0
        'the offset is zero'         | 0                 || 0
        'the offset is positive'     | 75                || 75
    }

    @Unroll
    def "TermFilter should set search #expected when #scenario"() {

        when:
        TermFilter f = new TermFilter(1, search, null, null, false, null, false, 0, 0)

        then:
        f.search() == expected

        where:
        scenario                         | search      || expected
        'the search is null'             | null        || null
        'the search is empty'            | ''          || null
        'the search is blank'            | '   '       || null
        'the search is padded'           | '  api  '   || 'api'
        'the search has tab and newline' | '\tkey\n'   || 'key'
        'the search has an apostrophe'   | "it's"      || "it's"
    }

    @Unroll
    def "TermFilter should drop a zero or negative id when #scenario"() {

        when:
        TermFilter f = new TermFilter(1, null, typeId, categoryId, false, null, false, 0, 0)

        then:
        f.typeId() == expectedType
        f.categoryId() == expectedCategory

        where:
        scenario                | typeId | categoryId || expectedType | expectedCategory
        'both are null'         | null   | null       || null         | null
        'both are zero'         | 0      | 0          || null         | null
        'both are negative'     | -1     | -9         || null         | null
        'both are positive'     | 3      | 4          || 3            | 4
    }

    @Unroll
    def "#seq. TermFilter should group by #expected when #scenario"() {

        when:
        TermFilter f = new TermFilter(1, null, null, null, false, groupBy, false, 0, 0)

        then:
        f.groupBy() == expected

        where:
        seq | scenario                        | groupBy    || expected
        1   | 'nothing is asked'              | null       || 'type'
        2   | 'an empty value is asked'       | ''         || 'type'
        3   | 'type is asked'                 | 'type'     || 'type'
        4   | 'category is asked'             | 'category' || 'category'
        5   | 'none is asked'                 | 'none'     || 'none'
        6   | 'Category is asked in capitals' | 'Category' || 'type'
        7   | 'a padded none is asked'        | ' none '   || 'type'
        8   | 'an unknown value is asked'     | 'bogus'    || 'type'
    }

    def "TermFilter should keep the solution, favorites and reverse when they are given"() {

        when:
        TermFilter f = new TermFilter(7, null, null, null, true, null, true, 0, 0)

        then:
        f.solutionId() == 7
        f.favoritesOnly()
        f.reverse()
        TermFilter.DEFAULT_PAGE == 50
        TermFilter.MAX_PAGE == 500
    }

    @Unroll
    def "lines should answer #expected when #scenario"() {

        expect:
        SolutionTermModels.lines(list) == namesOf(expected)

        where:
        scenario                          | list                     || expected
        'the list is null'                | null                     || ''
        'the list is empty'               | ''                       || ''
        'there is one name'               | 'one'                    || 'one'
        'there are two names'             | 'one\ntwo'               || 'one,two'
        'there are blanks and padding'    | ' one \n\n  \ntwo\r\n'   || 'one,two'
        'there are only newlines'         | '\n\n'                   || ''
    }

    def "lines should answer a list that may be added to when the input is null"() {

        when:
        List<String> l = SolutionTermModels.lines(null)
        l << 'x'

        then:
        l == ['x']
    }

    def "Term.of should read every column when the row is complete"() {

        when:
        Term t = Term.of([SolutionTermID: 5, SolutionID: 1, GroupKey: 'type:2', GroupLabel: 'Acronym', Term: 'API',
                          Definition: 'Application programming interface', IsTruncated: 1, SolutionTermTypeID: 2, TypeName: 'Acronym',
                          TermSource: 'Wikipedia', TermNotes: 'notes', HasNotes: true, IsFavorite: 'true', CategoryList: 'Web\nCode',
                          AuthorCode: 'rex', AuthorName: 'Rex', ModifiedByCode: 'tony', ModifiedByName: 'Tony', CreatedDate: T,
                          ModifiedDate: '2026-10-09 12:00:00'])

        then:
        t == new Term(5, 1, 'type:2', 'Acronym', 'API', 'Application programming interface', true, 2, 'Acronym', 'Wikipedia',
                      'notes', true, true, ['Web', 'Code'], 'rex', 'Rex', 'tony', 'Tony', T, T)
    }

    def "Term.of should answer no type and no categories when the row is empty or the type is zero"() {

        expect:
        Term.of([:]).typeId() == null
        Term.of([SolutionTermTypeID: 0]).typeId() == null
        Term.of([:]).categories() == []
        !Term.of([:]).favorite()
        !Term.of([:]).truncated()
    }

    def "TermLookup.type and TermLookup.category should read their own columns when given a row"() {

        expect:
        TermLookup.type([SolutionTermTypeID: 2, TypeName: 'Acronym', UsageCount: 4]) == new TermLookup(2, 'Acronym', 4)
        TermLookup.category([SolutionTermCategoryID: 3, CategoryName: 'Web', UsageCount: 0]) == new TermLookup(3, 'Web', 0)
        TermLookup.type([SolutionTermCategoryID: 3, CategoryName: 'Web']) == new TermLookup(0, null, 0)
    }

    def "the plain records should keep what they are given when built"() {

        expect:
        new TermDraft(0, 1, 't', 'd', null, null, null, null).categories() == null
        new TermPage([], [total: 3], 0, 50, 3).total() == 3
        new TermLookups([], []).types() == []
    }
}
