package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionLifecycleModels.OpenThing
import com.echo.SolutionLifecycleModels.Shape
import com.echo.SolutionLifecycleModels.ShapeResult

import spock.lang.Unroll

/**
 * Story 5695 (Release 6): the shape of the start and the end of a solution -- the shapes a new solution can start
 * from (SHAPES, in their order, and isShape), the counts a shape answers (ShapeResult.of, a missing row included),
 * one row of the close-out list (OpenThing.of) and the groups of that list in their order (GROUPS).
 */
class SolutionLifecycleModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime CHANGED = getLocalDateTime('11/04/2026 14:20')

    // ---- SHAPES ----

    def "SHAPES should list programming, writing, life and copy in that order"() {

        expect:
        SolutionLifecycleModels.SHAPES*.code() == ['programming', 'writing', 'life', 'copy']
    }

    @Unroll
    def "SHAPES should name shape #position '#expectedCode' as '#expectedName' with a description"() {

        when:
        Shape s = SolutionLifecycleModels.SHAPES[position]

        then:
        s.code() == expectedCode
        s.name() == expectedName
        s.description() != null
        !s.description().trim().isEmpty()

        where:
        scenario          | position || expectedCode  | expectedName
        'the first shape' | 0        || 'programming' | 'Programming effort'
        'the second'      | 1        || 'writing'     | 'Writing effort'
        'the third'       | 2        || 'life'        | 'Life matter'
        'the last'        | 3        || 'copy'        | 'The shape of another solution'
    }

    def "SHAPES should not be changeable"() {

        when:
        SolutionLifecycleModels.SHAPES.add(new Shape('x', 'X', 'x'))

        then:
        thrown(UnsupportedOperationException)
    }

    // ---- isShape ----

    @Unroll
    def "#seq. isShape should answer #expected when the code is #scenario"() {

        expect:
        SolutionLifecycleModels.isShape(code) == expected

        where:
        seq | scenario                                  | code          || expected
        1   | 'programming'                             | 'programming' || true
        2   | 'writing'                                 | 'writing'     || true
        3   | 'life'                                    | 'life'        || true
        4   | 'copy'                                    | 'copy'        || true
        5   | 'in upper case (the service lowers it)'   | 'Programming' || false
        6   | 'padded (the service trims it)'           | ' life '      || false
        7   | 'empty'                                   | ''            || false
        8   | 'null'                                    | null          || false
        9   | 'not a shape'                             | 'design'      || false
    }

    // ---- ShapeResult.of ----

    def "ShapeResult.of should read every count when the row is complete"() {

        expect:
        ShapeResult.of([ProjectCount: 4, KindOfPartCount: 1, PartCount: 3, TagCount: 2, TermTypeCount: 5, TermCategoryCount: 6]) ==
            new ShapeResult(4, 1, 3, 2, 5, 6)
    }

    @Unroll
    def "ShapeResult.of should answer every count as zero when the row is #scenario"() {

        expect:
        ShapeResult.of(row) == new ShapeResult(0, 0, 0, 0, 0, 0)

        where:
        scenario  | row  || unused
        'missing' | null || null
        'empty'   | [:]  || null
    }

    @Unroll
    def "ShapeResult.of should read a count as #expected when the driver answers #scenario"() {

        expect:
        ShapeResult.of([ProjectCount: value]).projects() == expected

        where:
        scenario | value || expected
        'an int' | 3     || 3
        'a long' | 4L    || 4
        'text'   | ' 7 ' || 7
        'null'   | null  || 0
    }

    // ---- OpenThing.of ----

    def "OpenThing.of should read every column when the row is complete"() {

        when:
        OpenThing t = OpenThing.of([GroupCode: 'part', Kind: 'topic', RecordID: 41, Title: 'Production', Sub: 'Environment | Not started',
                                    ChangedDate: Timestamp.valueOf(CHANGED)])

        then:
        t == new OpenThing('part', 'topic', 41, 'Production', 'Environment | Not started', CHANGED)
    }

    def "OpenThing.of should answer zeros and nulls when the row is empty"() {

        expect:
        OpenThing.of([:]) == new OpenThing(null, null, 0, null, null, null)
    }

    def "OpenThing.of should read a thread as kind message with its conversation id"() {

        expect:
        OpenThing.of([GroupCode: 'thread', Kind: 'message', RecordID: 9L, Title: 'Untitled thread', Sub: 'waiting for tony',
                      ChangedDate: null]) == new OpenThing('thread', 'message', 9, 'Untitled thread', 'waiting for tony', null)
    }

    // ---- GROUPS ----

    def "GROUPS should list the close-out groups in their order"() {

        expect:
        SolutionLifecycleModels.GROUPS == ['project', 'item', 'question', 'decision', 'part', 'assumption', 'thread']
    }
}
