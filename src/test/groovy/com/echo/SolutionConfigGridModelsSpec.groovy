package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionConfigGridModels.Axis
import com.echo.SolutionConfigGridModels.AxisValue
import com.echo.SolutionConfigGridModels.Cell
import com.echo.SolutionConfigGridModels.CellState
import com.echo.SolutionConfigGridModels.Column
import com.echo.SolutionConfigGridModels.Grid
import com.echo.SolutionConfigGridModels.Match
import com.echo.SolutionConfigGridModels.MatchSet
import com.echo.SolutionConfigGridModels.Tag
import com.echo.SolutionConfigGridModels.Type

/**
 * Story 5653: the Configuration II row mappers and records.
 */
class SolutionConfigGridModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T1 = getLocalDateTime('10/02/2026 10:15')
    static final LocalDateTime T2 = getLocalDateTime('09/02/2026 07:00')

    def "AxisValue.of should read every column when numbers arrive as other types"() {

        expect:
        AxisValue.of([SolutionConfigGridAxisValueID: 61L, SolutionConfigGridAxisID: '51', ValueTitle: 'QA', ColorHex: '#2563EB', SortOrder: 2.0]) ==
            new AxisValue(61, 51, 'QA', '#2563EB', 2)
        AxisValue.of([:]) == new AxisValue(0, 0, null, null, 0)
    }

    def "Type.of and CellState.of should read every column when present and default when missing"() {

        expect:
        Type.of([TypeCode: 'pw', TypeName: 'Password', IsSecret: 1, SortOrder: 3]) == new Type('pw', 'Password', true, 3)
        Type.of([:]) == new Type(null, null, false, 0)
        CellState.of([StateCode: 'planned', StateName: 'Planned', StateDescription: 'Not yet', SortOrder: 2]) ==
            new CellState('planned', 'Planned', 'Not yet', 2)
        CellState.of([:]) == new CellState(null, null, null, 0)
    }

    def "Cell.of should read the state and the other places when the cell is complete"() {

        when:
        Cell c = Cell.of([SolutionConfigGridColumnID: 71, ValueText: 'v', SourceTitle: 's', ValueNote: 'n', StateCode: 'observed',
                          ModifiedByCode: 'tony', ModifiedByName: 'Tony', ModifiedDate: Timestamp.valueOf(T1), CreatedDate: T2], ['x'])

        then:
        c == new Cell(71, 'v', 's', ['x'], 'n', 'observed', 'tony', 'Tony', T1)
    }

    def "Cell.of should fall back to the created date and then to null when the modified date is missing"() {

        expect:
        Cell.of([CreatedDate: T2], []).modifiedDate() == T2
        Cell.of([CreatedDate: '2026-09-02T07:00'], []).modifiedDate() == T2
        Cell.of([:], []).modifiedDate() == null
        Cell.of([:], []).state() == null
    }

    def "the plain records should keep what they are given when built"() {

        given:
        Tag tag = new Tag(51, 'Environment', 61, 'QA')
        Column col = new Column(71, 'QA', '#FFFFFF', false, 2, [tag])
        Axis axis = new Axis(51, 'Environment', 1, [new AxisValue(61, 51, 'QA', null, 1)])
        Match match = new Match('all', [new MatchSet(0, [71], 1, 1, 'single')])
        Grid grid = new Grid(1, [col], [axis], [], [], [], [], [properties: 0], true)

        expect:
        col.tags() == [tag]
        !col.isDefault()
        axis.values()[0].title() == 'QA'
        match.sets()[0].state() == 'single'
        grid.canImport()
        grid == new Grid(1, [col], [axis], [], [], [], [], [properties: 0], true)
        grid != new Grid(1, [col], [axis], [], [], [], [], [properties: 0], false)
    }
}
