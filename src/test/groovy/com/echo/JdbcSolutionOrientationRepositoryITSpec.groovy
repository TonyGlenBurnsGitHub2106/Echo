package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionLinkModels.Visit
import com.echo.SolutionOrientationModels.Activity
import com.echo.SolutionOrientationModels.LeftOff
import com.echo.SolutionOrientationModels.LensItem

import spock.lang.Unroll

/**
 * Story 5695 Release 4: JdbcSolutionOrientationRepository against a real EchoTest_ database.
 *
 *   the lens       stp_SolutionProjectItem_Lens -- every open item lands in one bucket (in progress or due within
 *                  the soon window is now, overdue too; blocked or waiting on an open item is waiting; the rest is
 *                  next; closed items are left out), Now leads with the overdue, then the due date, then the
 *                  member's own; TotalInBucket and paging within a bucket; IsIdle for a Now item untouched long
 *   the note       stp_SolutionMemberVisit_SaveNote / _GetNote -- one note per member per solution, written,
 *                  replaced, trimmed, taken away by blank, refused over 1,000 characters with the proc's sentence,
 *                  kept on the visit row (made when the member had none), audited in SolutionMemberVisit_LOG, and
 *                  left alone by stp_SolutionMemberVisit_Touch
 *   the feed       stp_SolutionActivity_Feed -- everyone's changes newest first, narrowed by who, kind and topic
 *                  (the topic itself and every record linked to it from either end), TotalCount whatever the page,
 *                  and the since boundary (strictly after; no since is the last 30 days)
 *
 * Every feature is rolled back by EchoDbITSpec.
 */
class JdbcSolutionOrientationRepositoryITSpec extends EchoDbITSpec {

    static final String TOO_LONG = 'A note for next time can be at most 1,000 characters. Nothing was saved.'

    JdbcSolutionOrientationRepository repo
    JdbcSolutionLinkRepository links
    int sid
    int project

    def setup() {
        repo = new JdbcSolutionOrientationRepository(dataSource())
        links = new JdbcSolutionLinkRepository(dataSource())
        sid = newSolution()
        project = newProject(sid)
    }

    // ---- fixtures ----

    /** An item with its own status, assignee, priority and due date (days from today's midnight; null is none). */
    private static int lensItem(EchoDbITSpec db, int projectId, String title, String status, String assignee = null,
                                Integer dueInDays = null, int priority = 3) {
        int id = db.idOf('EXEC dbo.stp_SolutionProjectItem_Save 0, ?, NULL, ?, ?, ?, ?, ?, 0, NULL, ?',
            projectId, title, 'detail', status, assignee, priority, 'tony')
        if (dueInDays != null) {
            db.sql.update('UPDATE dbo.SolutionProjectItem SET DueDate = DATEADD(DAY, ?, CAST(CAST(GETDATE() AS DATE) AS DATETIME)) ' +
                'WHERE SolutionProjectItemID = ?', dueInDays, id)
        }
        return id
    }

    /** item waits on on (stp_SolutionItemDependency_Save). */
    private static void waitsOn(EchoDbITSpec db, int item, int on) {
        db.row('EXEC dbo.stp_SolutionItemDependency_Save ?, ?, ?', item, on, 'tony')
    }

    /** Moves a record's ModifiedDate back by hours (the key column is the table name plus ID). */
    private static void changedHoursAgo(EchoDbITSpec db, String table, int id, int hours) {
        db.sql.update('UPDATE dbo.' + table + ' SET ModifiedDate = DATEADD(HOUR, ?, GETDATE()) WHERE ' + table + 'ID = ?', -hours, id)
    }

    private static int newTerm(EchoDbITSpec db, int solutionId, String term, String memberCode) {
        db.idOf("EXEC dbo.stp_SolutionTerm_Save 0, ?, ?, 'Means something', NULL, NULL, NULL, NULL, ?", solutionId, term, memberCode)
    }

    private static int newTopic(JdbcSolutionLinkRepository links, int solutionId, String name, String summary = null, String memberCode = 'rex') {
        links.saveTopic(new TopicDraft(0, solutionId, name, summary), memberCode)
    }

    /** A member code no other feature uses, at most 20 characters. */
    private static String member() {
        ('it' + uniq()).take(20)
    }

    /** "a,b" to ["a", "b"]; "" to an empty list. */
    private static List<String> csvOf(String csv) {
        csv.isEmpty() ? [] : (csv.split(',') as List<String>)
    }

    /** The member's visit row on the solution, or null. */
    private static Map<String, Object> visitRow(EchoDbITSpec db, int solutionId, String memberCode) {
        db.row('SELECT v.* FROM dbo.SolutionMemberVisit v JOIN dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID ' +
            'WHERE v.SolutionID = ? AND m.MemberCode = ?', solutionId, memberCode)
    }

    // ---- the lens: where each item lands ----

    @Unroll
    def "#seq. lens should place the item in #expectedBucket when #scenario"() {

        given:
        int item = lensItem(this, project, 'Under test', status, null, dueInDays)
        if (waitsOnStatus != null) {
            waitsOn(this, item, lensItem(this, project, 'Waited on', waitsOnStatus))
        }

        when:
        LensItem got = repo.lens(sid, 'tony', null, 0, 50, 7, 7).find { LensItem i -> i.id() == item }

        then:
        (got == null) == (expectedBucket == null)
        got == null || got.bucket() == expectedBucket
        got == null || got.overdue() == expectedOverdue
        got == null || got.openWaits() == expectedWaits

        where:
        seq | scenario                                       | status       | dueInDays | waitsOnStatus || expectedBucket | expectedOverdue | expectedWaits
        1   | 'it is in progress with no due date'           | 'inprogress' | null      | null          || 'now'          | false           | 0
        2   | 'it is due today'                              | 'todo'       | 0         | null          || 'now'          | false           | 0
        3   | 'it is due on the last day of the soon window' | 'todo'       | 7         | null          || 'now'          | false           | 0
        4   | 'it is due the day after the soon window'      | 'todo'       | 8         | null          || 'next'         | false           | 0
        5   | 'it was due yesterday'                         | 'todo'       | -1        | null          || 'now'          | true            | 0
        6   | 'it is in progress and long overdue'           | 'inprogress' | -30       | null          || 'now'          | true            | 0
        7   | 'it is blocked'                                | 'blocked'    | null      | null          || 'waiting'      | false           | 0
        8   | 'it is blocked and overdue'                    | 'blocked'    | -3        | null          || 'waiting'      | true            | 0
        9   | 'it waits on an open item'                     | 'todo'       | null      | 'todo'        || 'waiting'      | false           | 1
        10  | 'it is in progress but waits on an open item'  | 'inprogress' | null      | 'inprogress'  || 'waiting'      | false           | 1
        11  | 'it is due today but waits on a blocked item'  | 'todo'       | 0         | 'blocked'     || 'waiting'      | false           | 1
        12  | 'it waits only on a done item'                 | 'todo'       | null      | 'done'        || 'next'         | false           | 0
        13  | 'it waits only on a skipped item'              | 'todo'       | null      | 'cancelled'   || 'next'         | false           | 0
        14  | 'it is not started with no due date'           | 'todo'       | null      | null          || 'next'         | false           | 0
        15  | 'it is not started and due in a month'         | 'todo'       | 30        | null          || 'next'         | false           | 0
        16  | 'it is done'                                   | 'done'       | null      | null          || null           | false           | 0
        17  | 'it is skipped'                                | 'cancelled'  | null      | null          || null           | false           | 0
        18  | 'it is done though it was due yesterday'       | 'done'       | -1        | null          || null           | false           | 0
    }

    def "lens should count only the open items an item waits on when it waits on several"() {

        given:
        int item = lensItem(this, project, 'Deploy', 'todo')
        waitsOn(this, item, lensItem(this, project, 'Open the firewall', 'todo'))
        waitsOn(this, item, lensItem(this, project, 'Get the login', 'blocked'))
        waitsOn(this, item, lensItem(this, project, 'Order the server', 'done'))

        when:
        LensItem got = repo.lens(sid, 'tony', null, 0, 50, 7, 7).find { LensItem i -> i.id() == item }

        then:
        got.bucket() == 'waiting'
        got.openWaits() == 2
    }

    def "lens should read every field of an item back when it is in the lens"() {

        given:
        int parent = lensItem(this, project, 'Parent', 'todo')
        int item = idOf('EXEC dbo.stp_SolutionProjectItem_Save 0, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?',
            project, parent, 'Parse the 835', 'detail', 'inprogress', 'rex', 2, 'tony')
        String projectName = (String) scalar('SELECT ProjectName FROM dbo.SolutionProject WHERE SolutionProjectID = ?', project)

        when:
        LensItem got = repo.lens(sid, 'rex', null, 0, 50, 7, 7).find { LensItem i -> i.id() == item }

        then:
        got.title() == 'Parse the 835'
        got.parentItemId() == parent
        got.projectId() == project
        got.projectName() == projectName
        got.statusCode() == 'inprogress'
        got.statusName() == 'In progress'
        got.colorHex() == '#2563EB'
        got.priority() == 2
        got.dueDate() == null
        got.assigneeCode() == 'rex'
        got.assigneeName() == 'Rex'
        got.bucket() == 'now'
        got.idleDays() == 0
        !got.idle()
        got.mine()
        !got.overdue()
        got.openWaits() == 0
        got.rowInBucket() == 1
        got.totalInBucket() == 1
    }

    def "lens should leave out other solutions' items and answer nothing when no item is open"() {

        given:
        lensItem(this, newProject(newSolution()), 'Elsewhere', 'inprogress')
        lensItem(this, project, 'Finished', 'done')

        expect:
        repo.lens(sid, 'tony', null, 0, 50, 7, 7) == []
    }

    // ---- the lens: order ----

    @Unroll
    def "lens should order Now with the overdue first, then by due date, then the member's own when #scenario"() {

        given: 'made out of order, so the order answered is never the order they were made'
        lensItem(this, project, 'In three days', 'todo', null, 3)
        lensItem(this, project, 'Started Rex', 'inprogress', 'rex')
        lensItem(this, project, 'Today Rex', 'todo', 'rex', 0)
        lensItem(this, project, 'One day late', 'todo', null, -1)
        lensItem(this, project, 'Started Tony', 'inprogress', 'tony')
        lensItem(this, project, 'Today Tony', 'todo', 'tony', 0)
        lensItem(this, project, 'Two days late', 'todo', null, -2)

        when:
        List<LensItem> now = repo.lens(sid, memberCode, null, 0, 50, 7, 7).findAll { LensItem i -> i.bucket() == 'now' }

        then:
        now*.title() == csvOf(expectedOrder)
        now*.rowInBucket() == (1..7) as List
        now.every { LensItem i -> i.totalInBucket() == 7 }
        now.findAll { LensItem i -> i.mine() }*.title() == csvOf(expectedMine)
        now.findAll { LensItem i -> i.overdue() }*.title() == ['Two days late', 'One day late']

        where:
        scenario                 | memberCode || expectedOrder                                                                              | expectedMine
        'Tony reads it'          | 'tony'     || 'Two days late,One day late,Today Tony,Today Rex,In three days,Started Tony,Started Rex' | 'Today Tony,Started Tony'
        'Rex reads it'           | 'rex'      || 'Two days late,One day late,Today Rex,Today Tony,In three days,Started Rex,Started Tony' | 'Today Rex,Started Rex'
        'no member is named'     | null       || 'Two days late,One day late,Today Rex,Today Tony,In three days,Started Rex,Started Tony' | ''
        'the member has no row'  | 'nobody'   || 'Two days late,One day late,Today Rex,Today Tony,In three days,Started Rex,Started Tony' | ''
    }

    def "lens should order Next by priority and then the order the items were made when no one is assigned"() {

        given:
        lensItem(this, project, 'Low', 'todo', null, null, 5)
        lensItem(this, project, 'High first', 'todo', null, null, 1)
        lensItem(this, project, 'Middle', 'todo', null, null, 3)
        lensItem(this, project, 'High second', 'todo', null, null, 1)

        when:
        List<LensItem> next = repo.lens(sid, 'tony', null, 0, 50, 7, 7).findAll { LensItem i -> i.bucket() == 'next' }

        then:
        next*.title() == ['High first', 'High second', 'Middle', 'Low']
    }

    def "lens should answer the buckets in the order now, waiting, next when every bucket has items"() {

        given:
        lensItem(this, project, 'A next', 'todo')
        lensItem(this, project, 'A waiting', 'blocked')
        lensItem(this, project, 'A now', 'inprogress')

        expect:
        repo.lens(sid, 'tony', null, 0, 50, 7, 7)*.bucket() == ['now', 'waiting', 'next']
    }

    // ---- the lens: totals and paging ----

    def "lens should page within one bucket and give the whole bucket's total on every page"() {

        given:
        [5, 4, 3, 2, 1].each { int p -> lensItem(this, project, 'Next p' + p, 'todo', null, null, p) }
        lensItem(this, project, 'Now one', 'inprogress')
        lensItem(this, project, 'Now two', 'inprogress')
        lensItem(this, project, 'Waiting one', 'blocked')

        when:
        List<LensItem> first = repo.lens(sid, 'tony', null, 0, 2, 7, 7)

        then:
        first*.bucket() == ['now', 'now', 'waiting', 'next', 'next']
        first.findAll { LensItem i -> i.bucket() == 'next' }*.title() == ['Next p1', 'Next p2']
        first.findAll { LensItem i -> i.bucket() == 'next' }*.totalInBucket() == [5, 5]
        first.findAll { LensItem i -> i.bucket() == 'now' }*.totalInBucket() == [2, 2]
        first.find { LensItem i -> i.bucket() == 'waiting' }.totalInBucket() == 1

        when:
        List<LensItem> second = repo.lens(sid, 'tony', 'next', 2, 2, 7, 7)

        then:
        second*.title() == ['Next p3', 'Next p4']
        second*.rowInBucket() == [3, 4]
        second*.totalInBucket() == [5, 5]
        second.every { LensItem i -> i.bucket() == 'next' }

        when:
        List<LensItem> last = repo.lens(sid, 'tony', 'next', 4, 2, 7, 7)

        then:
        last*.title() == ['Next p5']
        last*.rowInBucket() == [5]

        and: 'past the end is empty, the bucket is tidied, an offset without a bucket is ignored, page size 0 is 50'
        repo.lens(sid, 'tony', 'next', 5, 2, 7, 7) == []
        repo.lens(sid, 'tony', ' NEXT ', 0, 2, 7, 7)*.title() == ['Next p1', 'Next p2']
        repo.lens(sid, 'tony', null, 40, 2, 7, 7)*.title() == first*.title()
        repo.lens(sid, 'tony', 'next', 0, 0, 7, 7).size() == 5
        repo.lens(sid, 'tony', 'waiting', 0, 50, 7, 7)*.title() == ['Waiting one']
        repo.lens(sid, 'tony', 'now', 0, 50, 7, 7)*.title() == ['Now one', 'Now two']
    }

    // ---- the lens: idle ----

    @Unroll
    def "#seq. lens should answer #expectedIdleDays idle days and idle #expectedIdle when #scenario"() {

        given:
        int item = lensItem(this, project, 'Under test', status)
        if (daysBack > 0) {
            sql.update('UPDATE dbo.SolutionProjectItem SET ' + column + ' = DATEADD(DAY, ?, GETDATE()) WHERE SolutionProjectItemID = ?', -daysBack, item)
        }

        when:
        LensItem got = repo.lens(sid, 'tony', null, 0, 50, 7, idleLimit).find { LensItem i -> i.id() == item }

        then:
        got.idleDays() == expectedIdleDays
        got.idle() == expectedIdle

        where:
        seq | scenario                                                | status       | column         | daysBack | idleLimit || expectedIdleDays | expectedIdle
        1   | 'a Now item was made today'                             | 'inprogress' | 'ModifiedDate' | 0        | 7         || 0                | false
        2   | 'a Now item was untouched six days'                     | 'inprogress' | 'ModifiedDate' | 6        | 7         || 6                | false
        3   | 'a Now item was untouched seven days'                   | 'inprogress' | 'ModifiedDate' | 7        | 7         || 7                | true
        4   | 'a Now item was untouched ten days'                     | 'inprogress' | 'ModifiedDate' | 10       | 7         || 10               | true
        5   | 'a Now item never edited was made ten days ago'         | 'inprogress' | 'CreatedDate'  | 10       | 7         || 10               | true
        6   | 'a Next item was untouched ten days'                    | 'todo'       | 'ModifiedDate' | 10       | 7         || 10               | false
        7   | 'a Waiting item was untouched ten days'                 | 'blocked'    | 'ModifiedDate' | 10       | 7         || 10               | false
        8   | 'a Now item was untouched four days with a 3-day limit' | 'inprogress' | 'ModifiedDate' | 4        | 3         || 4                | true
    }

    // ---- the note for next time ----

    def "leftOff should answer no note when the member never wrote one"() {

        expect:
        repo.leftOff(sid, member()) == new LeftOff(null, null)
        repo.leftOff(sid, null) == new LeftOff(null, null)
    }

    def "saveLeftOff should keep the note and leftOff should read it back with when it was written"() {

        given:
        String code = member()
        LocalDateTime before = LocalDateTime.now()

        when:
        repo.saveLeftOff(sid, code, 'Next time, start with the charset sign-off.')
        LeftOff got = repo.leftOff(sid, code)

        then:
        got.note() == 'Next time, start with the charset sign-off.'
        got.writtenDate() != null
        got.writtenDate().isAfter(before.minusMinutes(1))
        got.writtenDate().isBefore(LocalDateTime.now().plusMinutes(1))
    }

    def "saveLeftOff should replace the note and keep one visit row when a second note is written"() {

        given:
        String code = member()
        repo.saveLeftOff(sid, code, 'First')
        LeftOff first = repo.leftOff(sid, code)

        when:
        repo.saveLeftOff(sid, code, 'Second')
        LeftOff second = repo.leftOff(sid, code)

        then:
        second.note() == 'Second'
        !second.writtenDate().isBefore(first.writtenDate())
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit WHERE SolutionID = ?', sid) == 1
    }

    def "saveLeftOff should trim the note when it arrives padded"() {

        given:
        String code = member()

        when:
        repo.saveLeftOff(sid, code, '   Calvin owes the QA dump.   ')

        then:
        repo.leftOff(sid, code).note() == 'Calvin owes the QA dump.'
    }

    @Unroll
    def "saveLeftOff should take the note away and keep the visit row when the new note is #scenario"() {

        given:
        String code = member()
        repo.saveLeftOff(sid, code, 'Something to remember')

        when:
        repo.saveLeftOff(sid, code, note)
        Map<String, Object> v = visitRow(this, sid, code)

        then:
        repo.leftOff(sid, code) == new LeftOff(null, null)
        v != null
        v.PartingNote == null
        v.PartingNoteDate == null

        where:
        scenario  | note  || unused
        'empty'   | ''    || null
        'blank'   | '   ' || null
        'null'    | null  || null
    }

    def "saveLeftOff should keep a note of exactly 1,000 characters"() {

        given:
        String code = member()
        String note = getReplicatedString('x', 1000)

        when:
        repo.saveLeftOff(sid, code, note)

        then:
        repo.leftOff(sid, code).note() == note
    }

    def "saveLeftOff should refuse a note of 1,001 characters with the proc's sentence and keep the old note"() {

        given:
        String code = member()
        repo.saveLeftOff(sid, code, 'Keep me')
        int logs = count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit_LOG WHERE SolutionID = ?', sid)

        when:
        repo.saveLeftOff(sid, code, getReplicatedString('x', 1001))

        then:
        SolutionRuleException ex = thrown()
        ex.message == TOO_LONG
        repo.leftOff(sid, code).note() == 'Keep me'
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit_LOG WHERE SolutionID = ?', sid) == logs
    }

    def "saveLeftOff should write no visit and no member when a new member's note is refused as too long"() {

        given:
        String code = member()

        when:
        repo.saveLeftOff(sid, code, getReplicatedString('x', 1001))

        then:
        SolutionRuleException ex = thrown()
        ex.message == TOO_LONG
        visitRow(this, sid, code) == null
        count('SELECT COUNT(*) FROM dbo.SolutionMember WHERE MemberCode = ?', code) == 0
    }

    def "saveLeftOff should keep a 1,000-character note sent with padding because the proc trims before it measures"() {

        given:
        String code = member()
        String padded = '  ' + getReplicatedString('x', 1000) + '  '

        when:
        repo.saveLeftOff(sid, code, padded)

        then:
        repo.leftOff(sid, code).note() == getReplicatedString('x', 1000)
    }

    @Unroll
    def "saveLeftOff should refuse with the proc's sentence and write nothing when #scenario"() {

        given:
        int solutionId = solutionTarget == 'mine' ? sid : -1
        int visits = count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit')

        when:
        repo.saveLeftOff(solutionId, memberCode, 'A note')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit') == visits

        where:
        scenario                      | solutionTarget | memberCode || expectedMessage
        'the solution does not exist' | 'missing'      | 'tony'     || 'That solution no longer exists. Nothing was saved.'
        'the member is empty'         | 'mine'         | ''         || 'Whose note it is was not said. Nothing was saved.'
        'the member is null'          | 'mine'         | null       || 'Whose note it is was not said. Nothing was saved.'
    }

    def "saveLeftOff should make the visit row and the member when a new member writes a note before any visit"() {

        given:
        String code = member()

        expect:
        visitRow(this, sid, code) == null

        when:
        repo.saveLeftOff(sid, code, 'First words')
        Map<String, Object> v = visitRow(this, sid, code)

        then:
        count('SELECT COUNT(*) FROM dbo.SolutionMember WHERE MemberCode = ?', code) == 1
        v != null
        v.LastSeenDate != null
        v.PreviousSeenDate == null
        v.PartingNote == 'First words'
        v.PartingNoteDate != null
        links.visit(sid, code) != null
    }

    def "saveLeftOff should leave the visit's own dates alone when the member already had a visit"() {

        given:
        String code = member()
        Visit visited = links.touch(sid, code)

        when:
        repo.saveLeftOff(sid, code, 'After the visit')

        then:
        links.visit(sid, code) == visited
        repo.leftOff(sid, code).note() == 'After the visit'
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit WHERE SolutionID = ?', sid) == 1
    }

    def "saveLeftOff should write nothing when a member with no visit takes away a note they never had"() {

        given:
        String code = member()

        when:
        repo.saveLeftOff(sid, code, '')

        then:
        repo.leftOff(sid, code) == new LeftOff(null, null)
        visitRow(this, sid, code) == null
    }

    def "saveLeftOff should keep each member's note and each solution's note apart and match the member code whatever its case"() {

        given:
        String mine = member()
        String theirs = member()
        int other = newSolution()

        when:
        repo.saveLeftOff(sid, mine.toUpperCase(), 'Mine here')
        repo.saveLeftOff(sid, theirs, 'Theirs here')
        repo.saveLeftOff(other, mine, 'Mine elsewhere')

        then:
        repo.leftOff(sid, mine).note() == 'Mine here'
        repo.leftOff(sid, mine.toUpperCase()).note() == 'Mine here'
        repo.leftOff(sid, theirs).note() == 'Theirs here'
        repo.leftOff(other, mine).note() == 'Mine elsewhere'
        repo.leftOff(other, theirs) == new LeftOff(null, null)
    }

    def "saveLeftOff should write the note into SolutionMemberVisit_LOG on the insert, the replace and the removal"() {

        given:
        String code = member()

        when:
        repo.saveLeftOff(sid, code, 'First')
        int visitId = ((Number) visitRow(this, sid, code).SolutionMemberVisitID).intValue()
        repo.saveLeftOff(sid, code, 'Second')
        repo.saveLeftOff(sid, code, '')
        List<Map<String, Object>> log = logRows('SolutionMemberVisit', 'SolutionMemberVisitID', visitId)

        then:
        log.size() == 3
        log*.PartingNote == ['First', 'Second', null]
        log[0].IsInsertion == true
        log[0].PartingNoteDate != null
        log[1].IsInsertion == false
        log[1].PartingNoteDate != null
        log[2].PartingNoteDate == null
        log.every { Map<String, Object> r -> r.DeletedDate == null }
    }

    def "stp_SolutionMemberVisit_Touch should keep the note and its date when the member comes back"() {

        given:
        String code = member()
        repo.saveLeftOff(sid, code, 'Start with the 835 parser.')
        LeftOff written = repo.leftOff(sid, code)
        sql.update('UPDATE dbo.SolutionMemberVisit SET LastSeenDate = DATEADD(HOUR, -2, LastSeenDate) WHERE SolutionID = ?', sid)

        when:
        Visit back = links.touch(sid, code)

        then:
        back.previousSeen() != null
        repo.leftOff(sid, code) == written

        when:
        links.caughtUp(sid, code)

        then:
        repo.leftOff(sid, code) == written
    }

    def "stp_Solution_Delete should take the solution's notes for next time with its visits"() {

        given:
        String code = member()
        repo.saveLeftOff(sid, code, 'Gone with it')

        when:
        row('EXEC dbo.stp_Solution_Delete ?', sid)

        then:
        repo.leftOff(sid, code) == new LeftOff(null, null)
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit WHERE SolutionID = ?', sid) == 0
    }

    // ---- the activity feed ----

    def "feed should list everyone's changes, the reader's own included, newest first"() {

        given:
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        int item = lensItem(this, project, 'Tony item', 'todo')
        int log = newLog(sid, 'Tony wrote this')
        int topic = newTopic(links, sid, 'Rex topic', 'A summary', 'rex')
        int term = newTerm(this, sid, 'Rex term', 'rex')
        changedHoursAgo(this, 'SolutionTopic', topic, 1)
        changedHoursAgo(this, 'SolutionLog', log, 2)
        changedHoursAgo(this, 'SolutionProjectItem', item, 3)
        changedHoursAgo(this, 'SolutionTerm', term, 4)
        String projectName = (String) scalar('SELECT ProjectName FROM dbo.SolutionProject WHERE SolutionProjectID = ?', project)

        when:
        List<Activity> got = repo.feed(sid, since, null, null, null, 0, 50)

        then:
        got.collect { Activity a -> a.kind() + ':' + a.id() } == ['topic:' + topic, 'log:' + log, 'item:' + item, 'term:' + term]
        got*.who() == ['rex', 'tony', 'tony', 'rex']
        got*.whoName() == ['Rex', 'Tony', 'Tony', 'Rex']
        got*.action() == ['added', 'wrote', 'added', 'added']
        got[0].title() == 'Rex topic'
        got[0].sub() == 'A summary'
        got[2].title() == 'Tony item'
        got[2].sub() == projectName
        got.every { Activity a -> a.changedDate() != null }
        got[0].changedDate().isAfter(got[1].changedDate())
        got[1].changedDate().isAfter(got[2].changedDate())
        got[2].changedDate().isAfter(got[3].changedDate())
    }

    @Unroll
    def "#seq. feed should answer #expectedKinds when #scenario"() {

        given:
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        int item = lensItem(this, project, 'Tony item', 'todo')
        int log = newLog(sid, 'Tony wrote this')
        int topic = newTopic(links, sid, 'Rex topic', 'A summary', 'rex')
        int term = newTerm(this, sid, 'Rex term', 'rex')
        changedHoursAgo(this, 'SolutionTopic', topic, 1)
        changedHoursAgo(this, 'SolutionLog', log, 2)
        changedHoursAgo(this, 'SolutionProjectItem', item, 3)
        changedHoursAgo(this, 'SolutionTerm', term, 4)

        when:
        List<Activity> got = repo.feed(sid, since, who, kind, null, 0, 50)

        then:
        got*.kind() == csvOf(expectedKinds)
        repo.feedTotal(sid, since, who, kind, null) == csvOf(expectedKinds).size()

        where:
        seq | scenario                               | who      | kind       || expectedKinds
        1   | 'nothing narrows it'                   | null     | null       || 'topic,log,item,term'
        2   | 'only Rex is asked for'                | 'rex'    | null       || 'topic,term'
        3   | 'only Rex is asked for in capitals'    | 'REX'    | null       || 'topic,term'
        4   | 'only Tony is asked for'               | 'tony'   | null       || 'log,item'
        5   | 'a member who changed nothing'         | 'nobody' | null       || ''
        6   | 'only log entries are asked for'       | null     | 'log'      || 'log'
        7   | 'only items are asked for in capitals' | null     | 'ITEM'     || 'item'
        8   | 'only messages are asked for'          | null     | 'message'  || ''
        9   | 'only decisions are asked for'         | null     | 'decision' || ''
        10  | 'Rex and topics are asked for'         | 'rex'    | 'topic'    || 'topic'
        11  | 'Tony and terms are asked for'         | 'tony'   | 'term'     || ''
    }

    def "feed should narrow to a topic and every record linked to it from either end"() {

        given:
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        int topic = newTopic(links, sid, 'Charset')
        int other = newTopic(links, sid, 'Encoding')
        int item = lensItem(this, project, 'Fix the charset', 'todo')
        int term = newTerm(this, sid, 'UTF-8', 'tony')
        int log = newLog(sid, 'Not linked to anything')
        links.link(sid, 'item', item, 'topic', topic, 'tony')
        links.link(sid, 'topic', other, 'topic', topic, 'tony')
        links.link(sid, 'term', term, 'topic', other, 'tony')
        Set<String> all = ['topic:' + topic, 'topic:' + other, 'item:' + item, 'term:' + term, 'log:' + log] as Set

        expect:
        count("SELECT COUNT(*) FROM dbo.SolutionLink WHERE FromKind = 'topic' AND FromID = ? AND ToKind = 'topic' AND ToID = ?", topic, other) == 1
        count("SELECT COUNT(*) FROM dbo.SolutionLink WHERE FromKind = 'item' AND FromID = ? AND ToKind = 'topic' AND ToID = ?", item, topic) == 1

        and: 'the first topic: itself, the item linked to it, and the topic it is linked to (stored with it first)'
        repo.feed(sid, since, null, null, topic, 0, 50).collect { Activity a -> a.kind() + ':' + a.id() } as Set ==
            ['topic:' + topic, 'item:' + item, 'topic:' + other] as Set
        repo.feedTotal(sid, since, null, null, topic) == 3

        and: 'the second topic: itself, the term linked to it, and the topic linked to it (stored with it second)'
        repo.feed(sid, since, null, null, other, 0, 50).collect { Activity a -> a.kind() + ':' + a.id() } as Set ==
            ['topic:' + other, 'topic:' + topic, 'term:' + term] as Set

        and: 'a topic and a kind together'
        repo.feed(sid, since, null, 'item', topic, 0, 50).collect { Activity a -> a.kind() + ':' + a.id() } == ['item:' + item]

        and: 'topic zero is no topic at all'
        repo.feed(sid, since, null, null, 0, 0, 50).collect { Activity a -> a.kind() + ':' + a.id() } as Set == all
    }

    def "feed should answer nothing for a topic of another solution"() {

        given:
        int theirs = newTopic(links, newSolution(), 'Elsewhere')
        lensItem(this, project, 'Mine', 'todo')

        expect:
        repo.feed(sid, LocalDateTime.now().minusDays(1), null, null, theirs, 0, 50) == []
        repo.feedTotal(sid, LocalDateTime.now().minusDays(1), null, null, theirs) == 0
    }

    def "feed should page in SQL and carry the whole TotalCount on every page"() {

        given:
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        List<Integer> topics = (1..4).collect { int n -> newTopic(links, sid, 'Topic ' + n) }
        topics.eachWithIndex { int id, int n -> changedHoursAgo(this, 'SolutionTopic', id, n + 1) }

        expect:
        repo.feed(sid, since, null, null, null, 0, 2)*.id() == [topics[0], topics[1]]
        repo.feed(sid, since, null, null, null, 2, 2)*.id() == [topics[2], topics[3]]
        repo.feed(sid, since, null, null, null, 4, 2) == []
        repo.feed(sid, since, null, null, null, 0, 0).size() == 4
        repo.feedTotal(sid, since, null, null, null) == 4

        and: 'the proc itself reports the whole total on a page of one'
        rows('EXEC dbo.stp_SolutionActivity_Feed ?, ?, NULL, NULL, NULL, 2, 1', sid, Timestamp.valueOf(since))*.TotalCount == [4]

        and: 'no match is a total of zero'
        repo.feedTotal(sid, since, 'nobody', null, null) == 0
    }

    @Unroll
    def "feed should #expectedOutcome the change when since is #scenario"() {

        given:
        int item = lensItem(this, project, 'On the boundary', 'todo')
        sql.update('UPDATE dbo.SolutionProjectItem SET ModifiedDate = DATEADD(DAY, -5, CAST(CAST(GETDATE() AS DATE) AS DATETIME)) ' +
            'WHERE SolutionProjectItemID = ?', item)
        LocalDateTime boundary = ((Timestamp) scalar('SELECT ModifiedDate FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = ?', item)).toLocalDateTime()
        LocalDateTime since = boundary.plusSeconds(secondsFromChange)

        when:
        List<Activity> got = repo.feed(sid, since, null, null, null, 0, 50)

        then:
        (got*.id() == [item]) == included
        got.isEmpty() == !included
        repo.feedTotal(sid, since, null, null, null) == (included ? 1 : 0)

        where:
        scenario                           | secondsFromChange || included | expectedOutcome
        'a second before the change'       | -1                || true     | 'list'
        'exactly the moment of the change' | 0                 || false    | 'leave out'
        'a second after the change'        | 1                 || false    | 'leave out'
    }

    def "feed should look back 30 days when no since is given"() {

        given:
        int recent = lensItem(this, project, 'Twenty-nine days ago', 'todo')
        int old = lensItem(this, project, 'Thirty-one days ago', 'todo')
        sql.update('UPDATE dbo.SolutionProjectItem SET ModifiedDate = DATEADD(DAY, -29, GETDATE()) WHERE SolutionProjectItemID = ?', recent)
        sql.update('UPDATE dbo.SolutionProjectItem SET ModifiedDate = DATEADD(DAY, -31, GETDATE()) WHERE SolutionProjectItemID = ?', old)

        expect:
        repo.feed(sid, null, null, null, null, 0, 50)*.id() == [recent]
        repo.feedTotal(sid, null, null, null, null) == 1
        repo.feed(sid, LocalDateTime.now().minusDays(40), null, null, null, 0, 50)*.id() == [recent, old]
    }

    def "feed should leave out the changes of another solution"() {

        given:
        lensItem(this, newProject(newSolution()), 'Elsewhere', 'todo')

        expect:
        repo.feed(sid, LocalDateTime.now().minusDays(1), null, null, null, 0, 50) == []
        repo.feedTotal(sid, LocalDateTime.now().minusDays(1), null, null, null) == 0
    }
}
