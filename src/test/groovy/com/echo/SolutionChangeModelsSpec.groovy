package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionChangeModels.BulkResult
import com.echo.SolutionChangeModels.Promoted
import com.echo.SolutionChangeModels.Provenance

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): the shapes of changing many items at once and of making one record into another --
 * the row mappers of a bulk change's answer (BulkResult, which reads a missing row as nothing done), of a
 * promotion's answer (Promoted) and of one provenance row (Provenance), and the vocabularies the service checks
 * against (FIELDS, MAX_ITEMS, SOURCES, TARGETS).
 */
class SolutionChangeModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('11/04/2026 10:15')

    // ---- the vocabularies ----

    def "FIELDS should name the six fields a bulk change may change"() {

        expect:
        SolutionChangeModels.FIELDS == ['status', 'project', 'assignee', 'due', 'priority', 'topic'] as Set
    }

    def "MAX_ITEMS should allow at most 500 items in one bulk change"() {

        expect:
        SolutionChangeModels.MAX_ITEMS == 500
    }

    def "SOURCES and TARGETS should name what a record can be made from and into"() {

        expect:
        SolutionChangeModels.SOURCES == ['message', 'log', 'note', 'item'] as Set
        SolutionChangeModels.TARGETS == ['item', 'note', 'question'] as Set
    }

    // ---- BulkResult ----

    def "BulkResult.of should read the log entry and the count when the row is complete"() {

        expect:
        BulkResult.of([SolutionLogID: 310, ItemCount: 20]) == new BulkResult(310, 20)
    }

    @Unroll
    def "BulkResult.of should answer log #expectedLogId and count #expectedCount when the row is #scenario"() {

        expect:
        BulkResult.of(row) == new BulkResult(expectedLogId, expectedCount)

        where:
        scenario                     | row                                     || expectedLogId | expectedCount
        'missing'                    | null                                    || 0             | 0
        'empty'                      | [:]                                     || 0             | 0
        'nothing needed changing'    | [SolutionLogID: 0, ItemCount: 0]        || 0             | 0
        'read as longs'              | [SolutionLogID: 9L, ItemCount: 2L]      || 9             | 2
        'read as text'               | [SolutionLogID: ' 7 ', ItemCount: '3']  || 7             | 3
        'read as nulls'              | [SolutionLogID: null, ItemCount: null]  || 0             | 0
    }

    // ---- Promoted ----

    def "Promoted.of should read the kind and id of the new record when the row is complete"() {

        expect:
        Promoted.of([Kind: 'item', ID: 1200]) == new Promoted('item', 1200)
    }

    def "Promoted.of should answer no kind and id zero when the row is empty"() {

        expect:
        Promoted.of([:]) == new Promoted(null, 0)
    }

    // ---- Provenance ----

    def "Provenance.of should read every column when the row is complete"() {

        when:
        Provenance p = Provenance.of([Direction: 'from', Kind: 'message', RecordID: 9012, Title: 'The partner app writes the 837',
                                      ConversationID: 44, PromotedDate: Timestamp.valueOf(T), PromotedByCode: 'tony',
                                      PromotedByName: 'Tony'])

        then:
        p == new Provenance('from', 'message', 9012, 'The partner app writes the 837', 44, T, 'tony', 'Tony')
    }

    def "Provenance.of should answer zeros and nulls when the row is empty"() {

        expect:
        Provenance.of([:]) == new Provenance(null, null, 0, null, null, null, null, null)
    }

    @Unroll
    def "Provenance.of should read the thread as #expected when ConversationID is #scenario"() {

        expect:
        Provenance.of([Direction: 'into', Kind: 'item', RecordID: 5, ConversationID: value]).conversationId() == expected

        where:
        scenario       | value || expected
        'a thread id'  | 44    || 44
        'zero'         | 0     || null
        'null'         | null  || null
        'text'         | '12'  || 12
    }

    def "Provenance.of should read the promoted date from text and leave an unreadable one null"() {

        expect:
        Provenance.of([PromotedDate: '2026-11-04 10:15:00']).promotedDate() == T
        Provenance.of([PromotedDate: 'not a date']).promotedDate() == null
    }
}
