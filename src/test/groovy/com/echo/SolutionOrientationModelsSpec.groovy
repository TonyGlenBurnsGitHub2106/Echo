package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionOrientationModels.Activity
import com.echo.SolutionOrientationModels.ActivityPage
import com.echo.SolutionOrientationModels.LeftOff
import com.echo.SolutionOrientationModels.Lens
import com.echo.SolutionOrientationModels.LensItem
import com.echo.SolutionOrientationModels.SummaryAge

import spock.lang.Unroll

/**
 * Story 5695: the shape of "where am I" in a solution -- SummaryAge.of reads when "Where things stand" was
 * last written and how many days ago (0 days is 0, never null), and calls a summary stale at 14 days or more.
 * Release 4: the three buckets and the two seven-day limits, LensItem.of (an optional parent, the three
 * flags read from a BIT, a number or text, a missing column read as 0, null or false), LeftOff.of (no row is
 * no note), Activity.of, and the Lens and ActivityPage holders.
 */
class SolutionOrientationModelsSpec extends EchoSpockBaseSpec {

    def "STALE_DAYS should be two weeks"() {

        expect:
        SolutionOrientationModels.STALE_DAYS == 14
    }

    def "SummaryAge.of should answer no date, no age and not stale when there is no row"() {

        expect:
        SummaryAge.of(null) == new SummaryAge(null, null, false)
    }

    @Unroll
    def "#seq. SummaryAge.of should answer age #expectedDays and stale #expectedStale when #scenario"() {

        given:
        LocalDateTime written = getLocalDateTime(writtenText)

        when:
        SummaryAge a = SummaryAge.of([WrittenDate: written, AgeDays: ageDays])

        then:
        a == new SummaryAge(written, expectedDays, expectedStale)

        where:
        seq | scenario                              | writtenText        | ageDays || expectedDays | expectedStale
        1   | 'the solution has no summary'         | null               | null    || null         | false
        2   | 'the summary was written today'       | '11/04/2026 08:00' | 0       || 0            | false
        3   | 'the summary is a day old'            | '11/03/2026 08:00' | 1       || 1            | false
        4   | 'the summary is a day short of stale' | '10/22/2026 08:00' | 13      || 13           | false
        5   | 'the summary is two weeks old'        | '10/21/2026 08:00' | 14      || 14           | true
        6   | 'the summary is a day past stale'     | '10/20/2026 08:00' | 15      || 15           | true
        7   | 'the summary is from September'       | '09/17/2026 08:00' | 48      || 48           | true
        8   | 'the clock says it is from tomorrow'  | '11/05/2026 08:00' | -1      || -1           | false
    }

    @Unroll
    def "SummaryAge.of should read the age as #expectedDays when the column holds #scenario"() {

        given:
        Object v = form == 'long' ? Long.valueOf(ageText) : (form == 'decimal' ? new BigDecimal(ageText) : ageText)

        when:
        SummaryAge a = SummaryAge.of([AgeDays: v])

        then:
        a.ageDays() == expectedDays
        a.stale() == expectedStale

        where:
        scenario                 | form      | ageText || expectedDays | expectedStale
        'a long'                 | 'long'    | '20'    || 20           | true
        'a decimal'              | 'decimal' | '3'     || 3            | false
        'text that is a number'  | 'text'    | '20'    || null         | false
    }

    def "SummaryAge.of should read a Timestamp written date when the row comes from the driver"() {

        given:
        LocalDateTime written = getLocalDateTime('10/01/2026 12:30')

        expect:
        SummaryAge.of([WrittenDate: Timestamp.valueOf(written), AgeDays: 34]) == new SummaryAge(written, 34, true)
    }

    // ---- Release 4: the lens, the note for next time, the activity feed ----

    def "BUCKETS should be now, next and waiting and the soon and idle limits a week each"() {

        expect:
        SolutionOrientationModels.BUCKETS == ['now', 'next', 'waiting'] as Set
        SolutionOrientationModels.SOON_DAYS == 7
        SolutionOrientationModels.IDLE_DAYS == 7
    }

    def "LensItem.of should read every column of a full row when the row comes from the driver"() {

        given:
        LocalDateTime due = getLocalDateTime('11/06/2026 00:00')
        Map<String, Object> r = [SolutionProjectItemID: 42, ItemTitle: 'Parse the 835', ParentItemID: 40, SolutionProjectID: 3,
                                 ProjectName: 'Parser', StatusCode: 'inprogress', StatusName: 'In progress', ColorHex: '#2563EB',
                                 Priority: 2, DueDate: Timestamp.valueOf(due), AssigneeCode: 'tony', AssigneeName: 'Tony',
                                 Bucket: 'now', IdleDays: 9, IsIdle: true, IsMine: true, IsOverdue: false, OpenWaits: 0,
                                 RowInBucket: Long.valueOf(1L), TotalInBucket: 12]

        when:
        LensItem i = LensItem.of(r)

        then:
        i == new LensItem(42, 'Parse the 835', 40, 3, 'Parser', 'inprogress', 'In progress', '#2563EB', 2, due, 'tony', 'Tony',
                          'now', 9, true, true, false, 0, 1, 12)
    }

    def "LensItem.of should answer zeros, nulls and false when every column is missing"() {

        when:
        LensItem i = LensItem.of([:])

        then:
        i == new LensItem(0, null, null, 0, null, null, null, null, 0, null, null, null, null, 0, false, false, false, 0, 0, 0)
    }

    @Unroll
    def "LensItem.of should read the parent as #expectedParent when the column holds #scenario"() {

        when:
        LensItem i = LensItem.of([SolutionProjectItemID: 5, ParentItemID: parent])

        then:
        i.parentItemId() == expectedParent
        i.id() == 5

        where:
        scenario      | parent || expectedParent
        'no parent'   | null   || null
        'zero'        | 0      || null
        'a parent id' | 17     || 17
    }

    @Unroll
    def "#seq. LensItem.of should read idle #expectedIdle, mine #expectedMine and overdue #expectedOverdue when the flags arrive as #scenario"() {

        given:
        Map<String, Object> r = [IsIdle: idle, IsMine: mine, IsOverdue: overdue]

        when:
        LensItem i = LensItem.of(r)

        then:
        i.idle() == expectedIdle
        i.mine() == expectedMine
        i.overdue() == expectedOverdue

        where:
        seq | scenario                    | idle   | mine  | overdue || expectedIdle | expectedMine | expectedOverdue
        1   | 'BIT values all set'        | true   | true  | true    || true         | true         | true
        2   | 'BIT values all clear'      | false  | false | false   || false        | false        | false
        3   | 'numbers one and zero'      | 1      | 0     | 1       || true         | false        | true
        4   | 'nulls'                     | null   | null  | null    || false        | false        | false
        5   | 'text true, one and no'     | 'true' | '1'   | 'no'    || true         | true         | false
        6   | 'only the idle flag set'    | true   | false | false   || true         | false        | false
        7   | 'only the mine flag set'    | false  | true  | false   || false        | true         | false
        8   | 'only the overdue flag set' | false  | false | true    || false        | false        | true
    }

    @Unroll
    def "LensItem.of should read the bucket #expectedBucket with #expectedWaits open waits when #scenario"() {

        when:
        LensItem i = LensItem.of([Bucket: bucket, OpenWaits: waits, RowInBucket: row, TotalInBucket: total])

        then:
        i.bucket() == expectedBucket
        i.openWaits() == expectedWaits
        i.rowInBucket() == expectedRow
        i.totalInBucket() == expectedTotal

        where:
        scenario                       | bucket    | waits | row | total || expectedBucket | expectedWaits | expectedRow | expectedTotal
        'it is the first now item'     | 'now'     | 0     | 1   | 3     || 'now'          | 0             | 1           | 3
        'it waits on two open items'   | 'waiting' | 2     | 4   | 9     || 'waiting'      | 2             | 4           | 9
        'it is item 51 of a long next' | 'next'    | 0     | 51  | 326   || 'next'         | 0             | 51          | 326
    }

    def "LeftOff.of should answer no note and no date when there is no row"() {

        expect:
        LeftOff.of(null) == new LeftOff(null, null)
    }

    def "LeftOff.of should answer no note and no date when the row has neither column"() {

        expect:
        LeftOff.of([:]) == new LeftOff(null, null)
    }

    @Unroll
    def "LeftOff.of should read the note and when it was written when #scenario"() {

        given:
        LocalDateTime written = getLocalDateTime(writtenText)
        Object dateValue = asTimestamp && written != null ? Timestamp.valueOf(written) : written

        when:
        LeftOff l = LeftOff.of([PartingNote: note, PartingNoteDate: dateValue])

        then:
        l == new LeftOff(note, written)

        where:
        scenario                              | note                                 | writtenText        | asTimestamp || unused
        'the driver hands back a Timestamp'   | 'Next time, start with the charset.' | '11/04/2026 17:45' | true        || null
        'the date is already a LocalDateTime' | 'Calvin owes the QA dump.'           | '11/03/2026 09:00' | false       || null
        'the note has no date'                | 'Undated'                            | null               | false       || null
    }

    def "Activity.of should read every column of a full row when the row comes from the driver"() {

        given:
        LocalDateTime changed = getLocalDateTime('11/04/2026 10:15')
        Map<String, Object> r = [Kind: 'log', RecordID: Long.valueOf(310L), Title: '#42 Parsed the batch', Sub: 'Result',
                                 Action: 'wrote', Who: 'rex', WhoName: 'Rex', ChangedDate: Timestamp.valueOf(changed), TotalCount: 7]

        when:
        Activity a = Activity.of(r)

        then:
        a == new Activity('log', 310, '#42 Parsed the batch', 'Result', 'wrote', 'rex', 'Rex', changed)
    }

    def "Activity.of should answer zero and nulls when every column is missing"() {

        expect:
        Activity.of([:]) == new Activity(null, 0, null, null, null, null, null, null)
    }

    @Unroll
    def "Activity.of should read the kind #kind and action #action when #scenario"() {

        when:
        Activity a = Activity.of([Kind: kind, RecordID: id, Action: action, Who: who, WhoName: whoName])

        then:
        a.kind() == kind
        a.id() == id
        a.action() == action
        a.who() == who
        a.whoName() == whoName

        where:
        scenario                                  | kind       | id | action    | who     | whoName || unused
        'an item was added'                       | 'item'     | 11 | 'added'   | 'tony'  | 'Tony'  || null
        'a message names its conversation'        | 'message'  | 4  | 'wrote'   | 'rex'   | 'Rex'   || null
        'a decision was recorded'                 | 'decision' | 20 | 'decided' | 'felix' | null    || null
        'a question was asked by an unknown hand' | 'question' | 5  | 'asked'   | null    | null    || null
    }

    def "Lens and ActivityPage should hold what they were given when built"() {

        given:
        LensItem item = LensItem.of([SolutionProjectItemID: 1, Bucket: 'now', TotalInBucket: 1])
        Activity change = Activity.of([Kind: 'item', RecordID: 1])
        LocalDateTime since = getLocalDateTime('10/05/2026 08:00')

        when:
        Lens lens = new Lens([item], [now: 1, waiting: 0, next: 0], 'now', 0, 50)
        ActivityPage page = new ActivityPage([change], since, 10, 5, 31)

        then:
        lens.items() == [item]
        lens.counts() == [now: 1, waiting: 0, next: 0]
        lens.bucket() == 'now'
        lens.offset() == 0
        lens.pageSize() == 50
        page.changes() == [change]
        page.since() == since
        page.offset() == 10
        page.pageSize() == 5
        page.total() == 31
    }
}
