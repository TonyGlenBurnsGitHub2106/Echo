package com.echo

import com.echo.SolutionConfigGridModels.Match
import com.echo.SolutionConfigGridModels.MatchSet

import spock.lang.Unroll

/**
 * Story 5653: the ONE place the Configuration II Colors are decided. Every verdict of a set, every overall
 * verdict, the default column's stand-in values, trimming and case, and how the axes split the columns.
 *
 * Data tables describe the inputs as text, converted in given: by the static parsers below:
 *   columns  "1,2,3"                     the compared (non-default) columns
 *   axes     "5:51,52;6:61"              axis id : its value ids, in order
 *   tags     "1:5=52,6=61;2:5=51"        column : axis=value
 *   values   "1=a;2= A ;9=dflt"          column = text (spaces kept)
 */
class SolutionConfigGridMatcherSpec extends EchoSpockBaseSpec {

    private static List<Integer> columnsOf(String text) {
        text.isEmpty() ? [] : text.split(',').collect { String s -> Integer.valueOf(s) }
    }

    private static Map<Integer, List<Integer>> axesOf(String text) {
        Map<Integer, List<Integer>> out = new LinkedHashMap<>()
        if (!text.isEmpty()) {
            for (String axis : text.split(';')) {
                String[] parts = axis.split(':')
                out.put(Integer.valueOf(parts[0]), columnsOf(parts.length > 1 ? parts[1] : ''))
            }
        }
        return out
    }

    private static Map<Integer, Map<Integer, Integer>> tagsOf(String text) {
        Map<Integer, Map<Integer, Integer>> out = [:]
        if (!text.isEmpty()) {
            for (String column : text.split(';')) {
                String[] parts = column.split(':')
                Map<Integer, Integer> tags = [:]
                for (String pair : parts[1].split(',')) {
                    String[] kv = pair.split('=')
                    tags.put(Integer.valueOf(kv[0]), Integer.valueOf(kv[1]))
                }
                out.put(Integer.valueOf(parts[0]), tags)
            }
        }
        return out
    }

    private static Map<Integer, String> valuesOf(String text) {
        Map<Integer, String> out = [:]
        if (!text.isEmpty()) {
            for (String pair : text.split(';')) {
                int eq = pair.indexOf('=')
                out.put(Integer.valueOf(pair.substring(0, eq)), pair.substring(eq + 1))
            }
        }
        return out
    }

    private static Map<String, Match> run(boolean secret, String columns, String axes, String tags, String values, int defaultId) {
        SolutionConfigGridMatcher.matches(secret, columnsOf(columns), axesOf(axes), tagsOf(tags), valuesOf(values), defaultId)
    }

    private static List<MatchSet> setsOf(String states) {
        states.isEmpty() ? [] : states.split(',').collect { String s -> new MatchSet(0, [], 0, 0, s) }
    }

    def "the constants should name every state when read"() {

        expect:
        SolutionConfigGridMatcher.ALL == 'all'
        SolutionConfigGridMatcher.STATES == ['all', 'some', 'none', 'incomplete', 'empty', 'single', 'secret']
    }

    @Unroll
    def "#seq. setState should answer #expected when #scenario"() {

        expect:
        SolutionConfigGridMatcher.setState(secret, size, setCount, distinct) == expected

        where:
        seq | scenario                                        | secret | size | setCount | distinct || expected
        1   | 'the property is secret'                        | true   | 3    | 3        | 1        || 'secret'
        2   | 'a secret has no columns'                       | true   | 0    | 0        | 0        || 'secret'
        3   | 'there are no columns'                          | false  | 0    | 0        | 0        || 'empty'
        4   | 'nobody has a value'                            | false  | 3    | 0        | 0        || 'empty'
        5   | 'one column has a value'                        | false  | 1    | 1        | 1        || 'single'
        6   | 'one column has no value'                       | false  | 1    | 0        | 0        || 'empty'
        7   | 'a gap sits beside agreeing values'             | false  | 3    | 2        | 1        || 'incomplete'
        8   | 'a gap sits beside disagreeing values'          | false  | 3    | 2        | 2        || 'incomplete'
        9   | 'half are known'                                | false  | 2    | 1        | 1        || 'incomplete'
        10  | 'three agree'                                   | false  | 3    | 3        | 1        || 'all'
        11  | 'two agree'                                     | false  | 2    | 2        | 1        || 'all'
        12  | 'three differ'                                  | false  | 3    | 3        | 3        || 'none'
        13  | 'two differ'                                    | false  | 2    | 2        | 2        || 'none'
        14  | 'two pairs share'                               | false  | 4    | 4        | 2        || 'some'
        15  | 'one pair shares'                               | false  | 4    | 4        | 3        || 'some'
    }

    @Unroll
    def "#seq. overall should answer #expected when the sets are #scenario"() {

        given:
        List<MatchSet> sets = setsOf(states)

        expect:
        SolutionConfigGridMatcher.overall(false, sets) == expected

        where:
        seq | scenario                          | states                      || expected
        1   | 'none at all'                     | ''                          || 'empty'
        2   | 'one empty'                       | 'empty'                     || 'empty'
        3   | 'two empty'                       | 'empty,empty'               || 'empty'
        4   | 'one single'                      | 'single'                    || 'single'
        5   | 'two single'                      | 'single,single'             || 'single'
        6   | 'one all'                         | 'all'                       || 'all'
        7   | 'all beside single'               | 'all,single'                || 'all'
        8   | 'all beside empty'                | 'all,empty'                 || 'incomplete'
        9   | 'single beside empty'             | 'single,empty'              || 'incomplete'
        10  | 'one incomplete'                  | 'incomplete'                || 'incomplete'
        11  | 'incomplete beside all'           | 'incomplete,all'            || 'incomplete'
        12  | 'some beside worse-off ones'      | 'some,incomplete,all'       || 'some'
        13  | 'none beside some'                | 'none,some,incomplete'      || 'none'
        14  | 'all beside none'                 | 'all,none'                  || 'none'
        15  | 'empty beside none'               | 'empty,none'                || 'none'
        16  | 'an unexpected secret set'        | 'secret'                    || 'single'
    }

    @Unroll
    def "overall should answer secret when the property is secret and #scenario"() {

        given:
        List<MatchSet> sets = setsOf(states)

        expect:
        SolutionConfigGridMatcher.overall(true, sets) == expected

        where:
        scenario               | states || expected
        'there are no sets'    | ''     || 'secret'
        'a set disagrees'      | 'none' || 'secret'
    }

    def "matches should answer one comparison holding every column when there are no axes"() {

        when:
        Map<String, Match> m = run(false, '1,2,3', '', '', '1=a;2=a;3=b', 0)

        then:
        (m.keySet() as List) == ['all']
        m.get('all').state() == 'some'
        m.get('all').sets() == [new MatchSet(0, [1, 2, 3], 3, 2, 'some')]
    }

    @Unroll
    def "matches should judge all as #expected when #scenario"() {

        when:
        Map<String, Match> m = run(false, columns, '', '', values, defaultId)

        then:
        m.get('all').state() == expected
        m.get('all').sets()[0].setCount() == expectedSetCount

        where:
        scenario                                             | columns | values                     | defaultId || expected     | expectedSetCount
        'values differ only in case and blanks'              | '1,2,3' | '1=Jdbc:X;2=  jdbc:x ;3=JDBC:X' | 0    || 'all'        | 3
        'a blank value is no value'                          | '1,2'   | '1=a;2=   '                | 0         || 'incomplete' | 1
        'a missing value takes the default column value'     | '1,2,3' | '1=own;9=dflt'             | 9         || 'some'       | 3
        'a blank own value takes the default column value'   | '1,2'   | '1= ;2=x;9=X '             | 9         || 'all'        | 2
        'a blank default stands in for nothing'              | '1,2'   | '1=x;9=  '                 | 9         || 'incomplete' | 1
        'default id 0 means there is no default column'      | '1,2'   | '1=x;0=x'                  | 0         || 'incomplete' | 1
    }

    def "matches should answer an empty set when there are no columns"() {

        when:
        Map<String, Match> m = run(false, '', '5:51,52', '', '', 0)

        then:
        m.get('all').state() == 'empty'
        m.get('all').sets() == [new MatchSet(0, [], 0, 0, 'empty')]
        m.get('5').state() == 'empty'
        m.get('5').sets() == []
    }

    def "matches should split columns by axis value in axis order with the untagged last when an axis exists"() {

        when:
        Map<String, Match> m = run(false, '1,2,3,4', '5:51,52,53', '1:5=52;2:5=51;3:5=52', '1=q;2=l;3=Q;4=z', 0)

        then:
        (m.keySet() as List) == ['all', '5']
        m.get('5').sets()*.valueId() == [51, 52, 0]
        m.get('5').sets()[0] == new MatchSet(51, [2], 1, 1, 'single')
        m.get('5').sets()[1] == new MatchSet(52, [1, 3], 2, 1, 'all')
        m.get('5').sets()[2] == new MatchSet(0, [4], 1, 1, 'single')
        m.get('5').state() == 'all'
        m.get('all').state() == 'some'
    }

    def "matches should judge each axis on its own when two axes describe the columns"() {

        when:
        Map<String, Match> m = run(false, '1,2,3', '5:51,52;6:61,62', '1:5=51,6=61;2:5=52,6=61;3:5=52,6=62', '1=a;2=a;3=b', 0)

        then:
        (m.keySet() as List) == ['all', '5', '6']
        m.get('5').state() == 'none'
        m.get('5').sets()[1] == new MatchSet(52, [2, 3], 2, 2, 'none')
        m.get('6').state() == 'all'
        m.get('6').sets()[0] == new MatchSet(61, [1, 2], 2, 1, 'all')
        m.get('6').sets()[1] == new MatchSet(62, [3], 1, 1, 'single')
    }

    @Unroll
    def "matches should judge the axis incomplete when #scenario"() {

        when:
        Map<String, Match> m = run(false, '1,2,3', '5:51,52', '1:5=51;2:5=51;3:5=52', values, 0)

        then:
        m.get('5').state() == expected

        where:
        scenario                                   | values      || expected
        'a set has a gap'                          | '1=a;3=c'   || 'incomplete'
        'an empty set sits beside a known one'     | '1=a;2=a'   || 'incomplete'
    }

    def "matches should leave a column out of an axis when it is tagged with a value the axis does not list"() {

        when:
        Map<String, Match> m = run(false, '1,2', '5:51', '1:5=51;2:5=99', '1=a;2=b', 0)

        then:
        m.get('5').sets()*.columnIds() == [[1]]
        m.get('all').sets()[0].columnIds() == [1, 2]
    }

    def "matches should mark every comparison and set secret when the property is secret"() {

        when:
        Map<String, Match> m = run(true, '1,2', '5:51', '1:5=51', '1=a;2=b', 0)

        then:
        m.values()*.state() == ['secret', 'secret']
        m.values()*.sets().flatten()*.state() == ['secret', 'secret', 'secret']
        m.get('all').sets()[0].setCount() == 2
    }

    def "matches should answer an unchangeable copy of the column list when a set is built"() {

        given:
        List<Integer> cols = [1, 2]

        when:
        MatchSet s = SolutionConfigGridMatcher.matches(false, cols, [:], [:], [:], 0).get('all').sets()[0]
        cols << 3

        then:
        s.columnIds() == [1, 2]

        when:
        s.columnIds().add(9)

        then:
        thrown(UnsupportedOperationException)
    }

    def "matches should treat the column as untagged when its tag map is null"() {

        given:
        Map<Integer, Map<Integer, Integer>> tags = [1: null]

        expect:
        SolutionConfigGridMatcher.matches(false, [1], [5: [51]], tags, [1: 'a'], 0).get('5').sets() == [new MatchSet(0, [1], 1, 1, 'single')]
    }
}
