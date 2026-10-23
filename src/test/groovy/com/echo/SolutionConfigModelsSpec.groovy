package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionConfigModels.Cell
import com.echo.SolutionConfigModels.Config
import com.echo.SolutionConfigModels.Environment
import com.echo.SolutionConfigModels.Group
import com.echo.SolutionConfigModels.Property
import com.echo.SolutionConfigModels.Type

import spock.lang.Unroll

/**
 * Story 5653: the Configuration tab's row mappers -- one set serves the procs and the demo store, so each
 * must read whatever type a driver hands back (Number, Boolean, String, Timestamp) and survive a NULL.
 */
class SolutionConfigModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T1 = getLocalDateTime('10/01/2026 09:30')
    static final LocalDateTime T2 = getLocalDateTime('09/01/2026 08:00')

    def "Environment.of should read every column when numbers arrive as other types"() {

        when:
        Environment e = Environment.of([SolutionConfigEnvironmentID: 11, SolutionEnvironmentID: 3L, EnvironmentName: 'QA', SortOrder: '2'])

        then:
        e == new Environment(11, 3, 'QA', 2)
    }

    def "Environment.of should default to zero and null when columns are missing or unreadable"() {

        expect:
        Environment.of([:]) == new Environment(0, 0, null, 0)
        Environment.of([SolutionConfigEnvironmentID: 'abc', SortOrder: true]) == new Environment(0, 0, null, 1)
    }

    @Unroll
    def "#seq. Type.of should read IsSecret as #expected when it is #scenario"() {

        when:
        Type t = Type.of([TypeCode: 'pw', TypeName: 'Password', IsSecret: secretValue, SortOrder: 4])

        then:
        t == new Type('pw', 'Password', expected, 4)

        where:
        seq | scenario              | secretValue || expected
        1   | 'Boolean true'        | true        || true
        2   | 'Boolean false'       | false       || false
        3   | 'the number 1'        | 1           || true
        4   | 'the number 0'        | 0           || false
        5   | 'the text true'       | 'true'      || true
        6   | 'the text TRUE'       | 'TRUE'      || true
        7   | 'the text 1'          | '1'         || true
        8   | 'the text 1 padded'   | ' 1 '       || true
        9   | 'the text yes'        | 'yes'       || false
        10  | 'null'                | null        || false
    }

    def "Cell.of should read every column and prefer the modified date when both dates exist"() {

        when:
        Cell c = Cell.of([SolutionConfigEnvironmentID: 12, ValueText: 'v', SourceTitle: 's', ValueNote: 'n', ModifiedByCode: 'rex',
                          ModifiedByName: 'Rex', ModifiedDate: Timestamp.valueOf(T1), CreatedDate: T2], ['a', 'b'])

        then:
        c == new Cell(12, 'v', 's', ['a', 'b'], 'n', 'rex', 'Rex', T1)
    }

    @Unroll
    def "Cell.of should show #expectedDate when #scenario"() {

        given:
        Map<String, Object> row = [:]
        if (modified != null) {
            row.ModifiedDate = modified
        }
        if (created != null) {
            row.CreatedDate = created
        }

        when:
        Cell c = Cell.of(row, [])

        then:
        c.modifiedDate() == (expectedDate == null ? null : getLocalDateTime(expectedDate))

        where:
        scenario                                   | modified     | created                  || expectedDate
        'only the created date is known'           | null         | '2026-09-01T08:00'       || '09/01/2026 08:00'
        'the created date is SQL text'             | null         | '2026-09-01 08:00:00'    || '09/01/2026 08:00'
        'neither date is known'                    | null         | null                     || null
        'the modified date is unreadable'          | 'not a date' | '2026-09-01T08:00'       || '09/01/2026 08:00'
    }

    def "Cell.of should read a java.util.Date when the driver hands one back"() {

        given:
        Date d = new Date(Timestamp.valueOf(T1).time)

        expect:
        Cell.of([ModifiedDate: d], []).modifiedDate() == T1
    }

    def "Property.of should read every column and keep the cells when they are given"() {

        given:
        List<Cell> cells = [new Cell(11, 'v', null, [], null, null, null, null)]

        when:
        Property p = Property.of([SolutionConfigPropertyID: 31, SolutionConfigGroupID: 21, PropertyKey: 'db.url', PropertyDescription: 'd',
                                  TypeCode: 'url', TypeName: 'URL', IsSecret: 0, MatchState: 'incomplete', SetCount: 1, SortOrder: 7], cells)

        then:
        p == new Property(31, 21, 'db.url', 'd', 'url', 'URL', false, 'incomplete', 1, 7, cells)
        p.values().is(cells)
    }

    def "Property.of should default every field when the row is empty"() {

        expect:
        Property.of([:], []) == new Property(0, 0, null, null, null, null, false, null, 0, 0, [])
    }

    def "Group and Config should compare by value when built alike"() {

        expect:
        new Group(1, 't', 2, 3, [all: 1], []) == new Group(1, 't', 2, 3, [all: 1], [])
        new Config(1, [], [], [], [], [:]).solutionId() == 1
        new Config(1, [], [], [], ['x'], [a: 1]) != new Config(1, [], [], [], ['y'], [a: 1])
    }
}
