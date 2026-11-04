package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionDependencyModels.StoredWait
import com.echo.SolutionDependencyModels.Wait
import com.echo.SolutionDependencyModels.WaitCount

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): the shapes of what an item waits on -- the row mappers of one wait seen from an
 * item (Wait, with its direction "waitsOn" or "blocks"), one wait as stored with its solution (StoredWait)
 * and the Items tab's count per waiting item (WaitCount).
 */
class SolutionDependencyModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('11/04/2026 10:15')

    def "Wait.of should read every column when the row is complete"() {

        when:
        Wait w = Wait.of([SolutionItemDependencyID: 7, Direction: 'waitsOn', ItemID: 100, ItemTitle: 'Open the firewall',
                          ProjectName: 'Deploy', StatusCode: 'todo', StatusName: 'Not started', ColorHex: '#6B7280',
                          IsClosed: false, CreatedDate: Timestamp.valueOf(T)])

        then:
        w == new Wait(7, 'waitsOn', 100, 'Open the firewall', 'Deploy', 'todo', 'Not started', '#6B7280', false, T)
    }

    def "Wait.of should answer zeros, nulls and false when the row is empty"() {

        expect:
        Wait.of([:]) == new Wait(0, null, 0, null, null, null, null, null, false, null)
    }

    @Unroll
    def "Wait.of should read closed as #expected when the column is #scenario"() {

        when:
        Wait w = Wait.of([SolutionItemDependencyID: 7, Direction: 'blocks', IsClosed: value])

        then:
        w.closed() == expected
        w.direction() == 'blocks'

        where:
        scenario         | value  || expected
        'boolean true'   | true   || true
        'number one'     | 1      || true
        'text true'      | 'true' || true
        'number zero'    | 0      || false
        'missing'        | null   || false
    }

    def "Wait.of should read the created date from text and leave an unreadable one null"() {

        expect:
        Wait.of([CreatedDate: '2026-11-04 10:15:00']).createdDate() == T
        Wait.of([CreatedDate: 'not a date']).createdDate() == null
    }

    def "StoredWait.of should read the waiting item, what it waits on and its solution when the row is complete"() {

        expect:
        StoredWait.of([SolutionItemDependencyID: 7, SolutionProjectItemID: 101, DependsOnSolutionProjectItemID: 100,
                       SolutionID: 1, CreatedDate: T]) == new StoredWait(7, 101, 100, 1)
    }

    def "StoredWait.of should answer zeros when the row is empty"() {

        expect:
        StoredWait.of([:]) == new StoredWait(0, 0, 0, 0)
    }

    def "WaitCount.of should read the item and both counts when the row is complete"() {

        expect:
        WaitCount.of([SolutionProjectItemID: 101, WaitCount: 3, OpenWaitCount: 2]) == new WaitCount(101, 3, 2)
    }

    @Unroll
    def "WaitCount.of should read the counts #expectedWaits and #expectedOpen when the driver answers #scenario"() {

        expect:
        WaitCount.of([SolutionProjectItemID: 101, WaitCount: waits, OpenWaitCount: open]) == new WaitCount(101, expectedWaits, expectedOpen)

        where:
        scenario          | waits | open  || expectedWaits | expectedOpen
        'longs'           | 3L    | 0L    || 3             | 0
        'text'            | ' 2 ' | '1'   || 2             | 1
        'nulls'           | null  | null  || 0             | 0
    }
}
