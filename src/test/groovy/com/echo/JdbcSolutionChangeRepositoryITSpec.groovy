package com.echo

import java.time.LocalDateTime

import com.echo.SolutionChangeModels.BulkResult
import com.echo.SolutionChangeModels.Promoted
import com.echo.SolutionChangeModels.Provenance
import com.echo.SolutionLinkModels.TopicDraft

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5695 (Release 5): JdbcSolutionChangeRepository against a real EchoTest_ database.
 *
 *   bulk change   stp_SolutionProjectItem_BulkChange -- each field (status with its completed date, project with
 *                 the sub-items travelling under their item, assignee, due date, priority, topic), the ONE log
 *                 entry of type note that says "**Changed N items: <what>.**" and "Why: <reason>", numbered after
 *                 the solution's last entry and linked to every item changed, nothing written when nothing needed
 *                 changing, and every refusal leaving every row as it was
 *   promote       stp_Solution_Promote -- every allowed pair, the title rules (the first line, bold marks taken
 *                 out, a long line cut at a sentence or a word, a given title, a note's 300 limit), a message's
 *                 heading line, the link from the new record to its source, the SolutionPromotion row and its
 *                 audit, a repeat refused while the first record exists and allowed once it is gone, an open
 *                 question asked twice, an item with no project, and the pairs that are not allowed
 *   provenance    stp_SolutionPromotion_ForRecord -- both directions, only records still there, one solution
 *   delete        stp_Solution_Delete takes the solution's promotions, saved views and file versions
 *
 * Every feature is rolled back by EchoDbITSpec.
 */
class JdbcSolutionChangeRepositoryITSpec extends EchoDbITSpec {

    static final String NOTHING_CHANGED = ' Nothing was changed.'
    static final String NOT_ALLOWED = 'That cannot be made into that. A message becomes an item, a note or a question, a log entry an ' +
        'item or a note, a note an item, an item a note. Nothing was added.'

    JdbcSolutionChangeRepository repo
    JdbcSolutionLinkRepository links
    int sid
    int project
    String projectName

    def setup() {
        repo = new JdbcSolutionChangeRepository(dataSource())
        links = new JdbcSolutionLinkRepository(dataSource())
        sid = newSolution()
        projectName = 'Submit path ' + uniq()
        project = newProject(sid, projectName)
    }

    // ---- fixtures and reads ----

    /** A member code no other feature uses, created so its name is the code with a capital first letter. */
    private static String member(EchoDbITSpec db) {
        String code = ('it' + uniq()).take(20)
        db.scalar('SET NOCOUNT ON; DECLARE @m INT; EXEC dbo.stp_SolutionMember_FindOrCreate ?, @m OUTPUT; SELECT @m AS SolutionMemberID', code)
        return code
    }

    /** One item with its status code and its assignee's code. */
    private static Map<String, Object> itemOf(EchoDbITSpec db, int id) {
        db.row('SELECT i.SolutionProjectItemID, i.SolutionProjectID, i.ParentItemID, i.ItemTitle, i.ItemDetail, i.Priority, ' +
               'i.SortOrder, i.DueDate, i.CompletedDate, i.ModifiedDate, i.ModifiedBy, s.StatusCode, m.MemberCode AS AssigneeCode ' +
               'FROM dbo.SolutionProjectItem i JOIN dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID ' +
               'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = i.AssigneeMemberID WHERE i.SolutionProjectItemID = ?', id)
    }

    /** One log entry with its type and author codes. */
    private static Map<String, Object> logOf(EchoDbITSpec db, int logId) {
        db.row('SELECT l.SolutionID, l.SolutionProjectID, l.SolutionProjectItemID, l.SequenceNumber, l.LogText, ' +
               't.LogTypeCode, m.MemberCode AS AuthorCode FROM dbo.SolutionLog l ' +
               'JOIN dbo.SolutionLogType t ON t.SolutionLogTypeID = l.SolutionLogTypeID ' +
               'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = l.AuthorMemberID WHERE l.SolutionLogID = ?', logId)
    }

    /** One note with its kind and the wording of its current version. */
    private static Map<String, Object> noteOf(EchoDbITSpec db, int noteId) {
        db.row('SELECT n.SolutionID, n.SolutionProjectID, n.NoteTitle, n.CurrentVersionNumber, n.ModifiedBy, t.NoteTypeCode, ' +
               'v.NoteBody, v.ChangeNote AS VersionChangeNote, m.MemberCode AS VersionAuthorCode FROM dbo.SolutionNote n ' +
               'JOIN dbo.SolutionNoteType t ON t.SolutionNoteTypeID = n.SolutionNoteTypeID ' +
               'LEFT JOIN dbo.SolutionNoteVersion v ON v.SolutionNoteID = n.SolutionNoteID AND v.VersionNumber = n.CurrentVersionNumber ' +
               'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = v.AuthorMemberID WHERE n.SolutionNoteID = ?', noteId)
    }

    private static Map<String, Object> questionOf(EchoDbITSpec db, int questionId) {
        db.row('SELECT q.SolutionID, q.QuestionText, q.QuestionDetail, q.StatusCode, m.MemberCode AS AskedByCode ' +
               'FROM dbo.SolutionQuestion q LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = q.AskedByMemberID ' +
               'WHERE q.SolutionQuestionID = ?', questionId)
    }

    private static Map<String, Object> promotionOf(EchoDbITSpec db, String sourceKind, int sourceId, String targetKind) {
        db.row('SELECT p.*, m.MemberCode AS PromotedByCode FROM dbo.SolutionPromotion p ' +
               'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = p.PromotedByMemberID ' +
               'WHERE p.SourceKind = ? AND p.SourceID = ? AND p.TargetKind = ?', sourceKind, sourceId, targetKind)
    }

    /** How many links join the two records, whichever way round they were written. */
    private static int linksBetween(EchoDbITSpec db, int solutionId, String kindA, int idA, String kindB, int idB) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ? AND ' +
                 '((FromKind = ? AND FromID = ? AND ToKind = ? AND ToID = ?) OR (FromKind = ? AND FromID = ? AND ToKind = ? AND ToID = ?))',
                 solutionId, kindA, idA, kindB, idB, kindB, idB, kindA, idA)
    }

    /** The items a log entry is linked to, whichever way round, lowest id first. */
    private static List<Integer> itemsLinkedToLog(EchoDbITSpec db, int logId) {
        db.rows("SELECT ToID AS ItemID FROM dbo.SolutionLink WHERE FromKind = 'log' AND FromID = ? AND ToKind = 'item' " +
                "UNION ALL SELECT FromID FROM dbo.SolutionLink WHERE ToKind = 'log' AND ToID = ? AND FromKind = 'item' ORDER BY 1",
                logId, logId).collect { Map<String, Object> r -> ((Number) r.ItemID).intValue() }
    }

    /** Every link touching a record, either end. */
    private static int linksOf(EchoDbITSpec db, String kind, int id) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE (FromKind = ? AND FromID = ?) OR (ToKind = ? AND ToID = ?)', kind, id, kind, id)
    }

    private static int logsOf(EchoDbITSpec db, int solutionId) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', solutionId)
    }

    private static int lastTopSortOrder(EchoDbITSpec db, int projectId) {
        Object v = db.scalar('SELECT MAX(SortOrder) FROM dbo.SolutionProjectItem WHERE SolutionProjectID = ? AND ParentItemID IS NULL', projectId)
        return v == null ? 0 : ((Number) v).intValue()
    }

    private static int newThread(EchoDbITSpec db, int solutionId) {
        db.idOf('EXEC dbo.stp_SolutionConversation_Create ?, ?', solutionId, 'Thread ' + uniq())
    }

    private static int newMessage(EchoDbITSpec db, int conversationId, String sender, String body) {
        db.idOf("EXEC dbo.stp_ConversationMessage_Save @prmConversationMessageID=0, @prmConversationID=?, @prmSender=?, @prmBody=?, " +
                "@prmAwaitingResponseFrom='tony', @prmTokensIn=0, @prmTokensOut=0, @prmDurationMs=0, @prmModelUsed='', @prmErrorText=''",
                conversationId, sender, body)
    }

    private static int newNote(EchoDbITSpec db, int solutionId, Integer projectId, String title, String body) {
        db.idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, ?, 'runbook', ?, ?, NULL, 'tony', 0", solutionId, projectId, title, body)
    }

    private static int newProjectLog(EchoDbITSpec db, int solutionId, Integer projectId, String text) {
        db.idOf("EXEC dbo.stp_SolutionLog_Save 0, ?, ?, NULL, 'note', 'tony', ?, NULL", solutionId, projectId, text)
    }

    private static int newQuestion(EchoDbITSpec db, int solutionId, String text) {
        db.idOf("EXEC dbo.stp_SolutionQuestion_Save 0, ?, ?, NULL, NULL, 'tony'", solutionId, text)
    }

    /** The line a message's new record starts with: who wrote it and on what day (mm/dd/yyyy). */
    private static String fromLine(EchoDbITSpec db, int messageId, String name) {
        LocalDateTime created = SolutionRows.dateOf(db.row('SELECT CreatedDate FROM dbo.ConversationMessage WHERE ConversationMessageID = ?',
                                                           messageId), 'CreatedDate')
        return '*From ' + name + ', ' + getLocalDateAsString(created.toLocalDate()) + ', in the thread:*'
    }

    /** The kind a promoted note is given: the first by sort order. */
    private static String firstNoteType(EchoDbITSpec db) {
        (String) db.scalar('SELECT TOP 1 NoteTypeCode FROM dbo.SolutionNoteType ORDER BY SortOrder, SolutionNoteTypeID')
    }

    private static String csvOf(List<Integer> ids) {
        ids.collect { Integer i -> String.valueOf(i) }.join(',')
    }

    // ---- bulk change: status ----

    def "bulkChange should close the items, stamp their completed date and log once what changed and why"() {

        given:
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int done = newItem(project, 'Done already', null, 'done')
        Map<String, Object> doneBefore = itemOf(this, done)

        when:
        BulkResult r = repo.bulkChange(sid, csvOf([a, b, done]), 'status', 'DONE', '  The partner app writes the 837 itself.  ', 'Rex')
        Map<String, Object> log = logOf(this, r.logId())

        then:
        r.logId() > 0
        r.itemCount() == 2
        [a, b].every { int id -> itemOf(this, id).StatusCode == 'done' && itemOf(this, id).CompletedDate != null && itemOf(this, id).ModifiedBy == 'rex' }
        itemOf(this, done) == doneBefore
        log.SolutionID == sid
        log.LogTypeCode == 'note'
        log.AuthorCode == 'rex'
        log.SolutionProjectID == null
        log.SolutionProjectItemID == null
        log.LogText == '**Changed 2 items: status to Done.**\n\nWhy: The partner app writes the 837 itself.'
        itemsLinkedToLog(this, r.logId()) == [a, b]
    }

    def "bulkChange should keep the completed date from one closed status to another and clear it when the item is reopened"() {

        given:
        int a = newItem(project, 'A')
        repo.bulkChange(sid, String.valueOf(a), 'status', 'done', 'Finished', 'tony')
        Object completed = itemOf(this, a).CompletedDate

        when:
        BulkResult skipped = repo.bulkChange(sid, String.valueOf(a), 'status', 'cancelled', 'Not needed after all', 'tony')

        then:
        completed != null
        itemOf(this, a).StatusCode == 'cancelled'
        itemOf(this, a).CompletedDate == completed
        logOf(this, skipped.logId()).LogText == '**Changed 1 item: status to Skipped.**\n\nWhy: Not needed after all'

        when:
        BulkResult reopened = repo.bulkChange(sid, String.valueOf(a), 'status', 'inprogress', 'Back on', 'tony')

        then:
        reopened.itemCount() == 1
        itemOf(this, a).StatusCode == 'inprogress'
        itemOf(this, a).CompletedDate == null
        logOf(this, reopened.logId()).LogText == '**Changed 1 item: status to In progress.**\n\nWhy: Back on'
    }

    def "bulkChange should number its log entry after the solution's last one"() {

        given:
        newLog(sid, 'first')
        newLog(sid, 'second')
        int last = ((Number) scalar('SELECT MAX(SequenceNumber) FROM dbo.SolutionLog WHERE SolutionID = ?', sid)).intValue()
        int a = newItem(project, 'A')

        when:
        BulkResult r = repo.bulkChange(sid, String.valueOf(a), 'priority', '1', 'Urgent now', 'tony')

        then:
        logOf(this, r.logId()).SequenceNumber == last + 1
        last == 2
    }

    // ---- bulk change: project ----

    def "bulkChange should move each chosen item with its sub-items to the top level of the project, after what is there"() {

        given:
        String targetName = 'Target ' + uniq()
        int target = newProject(sid, targetName)
        int t1 = newItem(target, 'T1')
        newItem(target, 'T2')
        int last = lastTopSortOrder(this, target)
        int p = newItem(project, 'P')
        int c = newItem(project, 'C', p)
        int g = newItem(project, 'G', c)
        int q = newItem(project, 'Q')
        int stay = newItem(project, 'Stay')
        Object t1Sort = itemOf(this, t1).SortOrder

        when:
        BulkResult r = repo.bulkChange(sid, csvOf([q, p]), 'project', String.valueOf(target), 'The submit work moved', 'tony')

        then:
        r.itemCount() == 2
        itemOf(this, p).SolutionProjectID == target
        itemOf(this, p).ParentItemID == null
        itemOf(this, p).SortOrder == last + 1
        itemOf(this, q).SolutionProjectID == target
        itemOf(this, q).ParentItemID == null
        itemOf(this, q).SortOrder == last + 2
        itemOf(this, c).SolutionProjectID == target
        itemOf(this, c).ParentItemID == p
        itemOf(this, g).SolutionProjectID == target
        itemOf(this, g).ParentItemID == c
        itemOf(this, stay).SolutionProjectID == project
        itemOf(this, t1).SortOrder == t1Sort
        logOf(this, r.logId()).LogText == '**Changed 2 items: moved to the project ' + targetName + '.**\n\nWhy: The submit work moved'
        logOf(this, r.logId()).SolutionProjectID == target
        itemsLinkedToLog(this, r.logId()) == [p, q]
    }

    def "bulkChange should leave a chosen grandchild of a chosen item under its own parent when both move"() {

        given:
        int target = newProject(sid)
        int last = lastTopSortOrder(this, target)
        int p = newItem(project, 'P')
        int c = newItem(project, 'C', p)
        int g = newItem(project, 'G', c)

        when:
        BulkResult r = repo.bulkChange(sid, csvOf([g, p]), 'project', String.valueOf(target), 'Moving', 'tony')

        then:
        r.itemCount() == 2
        itemOf(this, p).ParentItemID == null
        itemOf(this, p).SortOrder == last + 1
        itemOf(this, c).SolutionProjectID == target
        itemOf(this, c).ParentItemID == p
        itemOf(this, g).SolutionProjectID == target
        itemOf(this, g).ParentItemID == c
        itemsLinkedToLog(this, r.logId()) == [p, g]
    }

    def "bulkChange should lift a chosen sub-item to the top level with its own sub-items and leave its parent behind"() {

        given:
        int target = newProject(sid)
        int last = lastTopSortOrder(this, target)
        int p = newItem(project, 'P')
        int c = newItem(project, 'C', p)
        int g = newItem(project, 'G', c)

        when:
        BulkResult r = repo.bulkChange(sid, String.valueOf(c), 'project', String.valueOf(target), 'Only this part', 'tony')

        then:
        r.itemCount() == 1
        itemOf(this, p).SolutionProjectID == project
        itemOf(this, c).SolutionProjectID == target
        itemOf(this, c).ParentItemID == null
        itemOf(this, c).SortOrder == last + 1
        itemOf(this, g).SolutionProjectID == target
        itemOf(this, g).ParentItemID == c
    }

    def "bulkChange should not count an item already in the project it is moved to"() {

        given:
        int target = newProject(sid)
        int there = newItem(target, 'There')
        int here = newItem(project, 'Here')
        Map<String, Object> thereBefore = itemOf(this, there)

        when:
        BulkResult r = repo.bulkChange(sid, csvOf([there, here]), 'project', String.valueOf(target), 'Together', 'tony')

        then:
        r.itemCount() == 1
        itemOf(this, there) == thereBefore
        itemsLinkedToLog(this, r.logId()) == [here]
    }

    // ---- bulk change: assignee ----

    def "bulkChange should give the items to a member, then to nobody, and change nothing the third time"() {

        given:
        String who = member(this)
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')

        when:
        BulkResult handed = repo.bulkChange(sid, csvOf([a, b]), 'assignee', who.toUpperCase(), 'Rex owns the parser now.', 'tony')

        then:
        handed.itemCount() == 2
        itemOf(this, a).AssigneeCode == who
        itemOf(this, b).AssigneeCode == who
        logOf(this, handed.logId()).LogText == '**Changed 2 items: given to ' + who.capitalize() + '.**\n\nWhy: Rex owns the parser now.'

        when:
        BulkResult cleared = repo.bulkChange(sid, csvOf([a, b]), 'assignee', '', 'Nobody has it yet', 'tony')

        then:
        cleared.itemCount() == 2
        itemOf(this, a).AssigneeCode == null
        itemOf(this, b).AssigneeCode == null
        logOf(this, cleared.logId()).LogText == '**Changed 2 items: given to nobody.**\n\nWhy: Nobody has it yet'

        when:
        BulkResult again = repo.bulkChange(sid, csvOf([a, b]), 'assignee', '', 'Nobody has it yet', 'tony')

        then:
        again == new BulkResult(0, 0)
    }

    // ---- bulk change: due date ----

    def "bulkChange should set the due date from yyyy-mm-dd and take it off with an empty value"() {

        given:
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')

        when:
        BulkResult set = repo.bulkChange(sid, csvOf([a, b]), 'due', '2026-12-01', 'The contract date', 'tony')

        then:
        set.itemCount() == 2
        SolutionRows.dateOf(itemOf(this, a), 'DueDate') == getLocalDateTime('12/01/2026 00:00')
        SolutionRows.dateOf(itemOf(this, b), 'DueDate') == getLocalDateTime('12/01/2026 00:00')
        logOf(this, set.logId()).LogText == '**Changed 2 items: due 12/01/2026.**\n\nWhy: The contract date'

        when:
        BulkResult cleared = repo.bulkChange(sid, csvOf([a, b]), 'due', '', 'No date yet', 'tony')

        then:
        cleared.itemCount() == 2
        itemOf(this, a).DueDate == null
        logOf(this, cleared.logId()).LogText == '**Changed 2 items: due date taken off.**\n\nWhy: No date yet'
    }

    @Unroll
    def "bulkChange should refuse the due date '#value' and change nothing when it is #scenario"() {

        given:
        int a = newItem(project, 'A')
        Map<String, Object> before = itemOf(this, a)
        int logs = logsOf(this, sid)

        when:
        repo.bulkChange(sid, String.valueOf(a), 'due', value, 'why', 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        itemOf(this, a) == before
        logsOf(this, sid) == logs

        where:
        scenario                   | value          || expectedMessage
        'written mm/dd/yyyy'       | '12/01/2026'   || 'A due date is written yyyy-mm-dd. Nothing was changed.'
        'a day that does not exist' | '2026-02-30'  || 'A due date is written yyyy-mm-dd. Nothing was changed.'
        'a month that does not exist' | '2026-13-01' || 'A due date is written yyyy-mm-dd. Nothing was changed.'
        'a one-digit month'        | '2026-1-01'    || 'A due date is written yyyy-mm-dd. Nothing was changed.'
        'a word'                   | 'soon'         || 'A due date is written yyyy-mm-dd. Nothing was changed.'
    }

    // ---- bulk change: priority ----

    @Unroll
    def "bulkChange should set the #scenario priority #value and say so in the log"() {

        given:
        int a = newItem(project, 'A')

        when:
        BulkResult r = repo.bulkChange(sid, String.valueOf(a), 'priority', value, 'Reordered', 'tony')

        then:
        r.itemCount() == 1
        itemOf(this, a).Priority == expectedPriority
        logOf(this, r.logId()).LogText == '**Changed 1 item: priority ' + value + '.**\n\nWhy: Reordered'

        where:
        scenario    | value || expectedPriority
        'highest'   | '1'   || 1
        'high'      | '2'   || 2
        'low'       | '4'   || 4
        'lowest'    | '5'   || 5
    }

    def "bulkChange should change nothing when the items already have that priority"() {

        given:
        int a = newItem(project, 'A')
        int logs = logsOf(this, sid)

        expect:
        itemOf(this, a).Priority == 3
        repo.bulkChange(sid, String.valueOf(a), 'priority', '3', 'Reordered', 'tony') == new BulkResult(0, 0)
        logsOf(this, sid) == logs
    }

    @Unroll
    def "bulkChange should refuse the priority '#value' and change nothing when it is #scenario"() {

        given:
        int a = newItem(project, 'A')
        Map<String, Object> before = itemOf(this, a)

        when:
        repo.bulkChange(sid, String.valueOf(a), 'priority', value, 'why', 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        itemOf(this, a) == before

        where:
        scenario      | value  || expectedMessage
        'nine'        | '9'    || 'A priority is 1 to 5. Nothing was changed.'
        'zero'        | '0'    || 'A priority is 1 to 5. Nothing was changed.'
        'a word'      | 'high' || 'A priority is 1 to 5. Nothing was changed.'
        'empty'       | ''     || 'A priority is 1 to 5. Nothing was changed.'
        'a fraction'  | '2.5'  || 'A priority is 1 to 5. Nothing was changed.'
    }

    // ---- bulk change: topic ----

    def "bulkChange should link each item to the topic once, the log entry too, and not a second time"() {

        given:
        String topicName = 'Charset ' + uniq()
        int topic = links.saveTopic(new TopicDraft(0, sid, topicName, null), 'tony')
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        links.link(sid, 'topic', topic, 'item', b, 'tony')

        when:
        BulkResult r = repo.bulkChange(sid, csvOf([a, b]), 'topic', String.valueOf(topic), 'Both are charset work', 'tony')

        then:
        r.itemCount() == 1
        linksBetween(this, sid, 'item', a, 'topic', topic) == 1
        linksBetween(this, sid, 'item', b, 'topic', topic) == 1
        linksBetween(this, sid, 'log', r.logId(), 'topic', topic) == 1
        itemsLinkedToLog(this, r.logId()) == [a]
        logOf(this, r.logId()).LogText == '**Changed 1 item: linked to the topic ' + topicName + '.**\n\nWhy: Both are charset work'

        when:
        BulkResult again = repo.bulkChange(sid, csvOf([a, b]), 'topic', String.valueOf(topic), 'Both are charset work', 'tony')

        then:
        again == new BulkResult(0, 0)
        linksBetween(this, sid, 'item', a, 'topic', topic) == 1
        linksBetween(this, sid, 'item', b, 'topic', topic) == 1
    }

    def "bulkChange should write its links the way stp_SolutionLink_Save does, so the same pair cannot be linked again"() {

        given:
        int a = newItem(project, 'A')
        BulkResult r = repo.bulkChange(sid, String.valueOf(a), 'priority', '1', 'Urgent', 'tony')

        when:
        links.link(sid, 'item', a, 'log', r.logId(), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'Those two are already linked. Nothing was saved.'
        linksBetween(this, sid, 'item', a, 'log', r.logId()) == 1
    }

    // ---- bulk change: nothing to do, and refusals ----

    def "bulkChange should write nothing and answer log 0 when no item needed changing"() {

        given:
        int a = newItem(project, 'A', null, 'done')
        int b = newItem(project, 'B', null, 'done')
        List<Map<String, Object>> before = [itemOf(this, a), itemOf(this, b)]
        int logs = logsOf(this, sid)
        int linked = count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ?', sid)

        when:
        BulkResult r = repo.bulkChange(sid, csvOf([a, b]), 'status', 'done', 'Already done', 'tony')

        then:
        r == new BulkResult(0, 0)
        [itemOf(this, a), itemOf(this, b)] == before
        logsOf(this, sid) == logs
        count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ?', sid) == linked
    }

    def "bulkChange should count a repeated or padded item id once"() {

        given:
        int a = newItem(project, 'A')

        when:
        BulkResult r = repo.bulkChange(sid, ' ' + a + ' ,' + a + ',,' + a, 'priority', '2', 'Once', 'tony')

        then:
        r.itemCount() == 1
        itemsLinkedToLog(this, r.logId()) == [a]
    }

    @Unroll
    def "#seq. bulkChange should refuse and leave every row as it was when #scenario"() {

        given:
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int foreign = newItem(newProject(newSolution()), 'Elsewhere')
        Map<String, String> choices = [mine: csvOf([a, b]), withForeign: csvOf([a, b, foreign]), withMissing: csvOf([a, 987654321]),
                                       none: '', words: 'x,y']
        int solution = solutionName == 'missing' ? -1 : sid
        List<Map<String, Object>> before = [itemOf(this, a), itemOf(this, b), itemOf(this, foreign)]
        int logs = logsOf(this, sid)
        int linked = count('SELECT COUNT(*) FROM dbo.SolutionLink')

        when:
        repo.bulkChange(solution, choices[itemsName], field, value, reason, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        [itemOf(this, a), itemOf(this, b), itemOf(this, foreign)] == before
        logsOf(this, sid) == logs
        count('SELECT COUNT(*) FROM dbo.SolutionLink') == linked

        where:
        seq | scenario                                   | solutionName | itemsName     | field      | value           | reason || expectedMessage
        1   | 'one item is of another solution'          | 'this'       | 'withForeign' | 'status'   | 'done'          | 'why'  || 'One of those items is not in this solution any more. Nothing was changed.'
        2   | 'one item does not exist'                  | 'this'       | 'withMissing' | 'status'   | 'done'          | 'why'  || 'One of those items is not in this solution any more. Nothing was changed.'
        3   | 'there is no reason'                       | 'this'       | 'mine'        | 'status'   | 'done'          | ''     || 'Say why. The reason goes in the log. Nothing was changed.'
        4   | 'the reason is blank'                      | 'this'       | 'mine'        | 'status'   | 'done'          | '   '  || 'Say why. The reason goes in the log. Nothing was changed.'
        5   | 'the reason is null'                       | 'this'       | 'mine'        | 'status'   | 'done'          | null   || 'Say why. The reason goes in the log. Nothing was changed.'
        6   | 'the field is not one it changes'          | 'this'       | 'mine'        | 'title'    | 'x'             | 'why'  || 'What to change must be status, project, assignee, due, priority or topic. Nothing was changed.'
        7   | 'the status is not one'                    | 'this'       | 'mine'        | 'status'   | 'finished'      | 'why'  || 'That is not a status. Nothing was changed.'
        8   | 'the priority is out of range'             | 'this'       | 'mine'        | 'priority' | '9'             | 'why'  || 'A priority is 1 to 5. Nothing was changed.'
        9   | 'the due date is written mm/dd/yyyy'       | 'this'       | 'mine'        | 'due'      | '12/01/2026'    | 'why'  || 'A due date is written yyyy-mm-dd. Nothing was changed.'
        10  | 'the assignee is not a member'             | 'this'       | 'mine'        | 'assignee' | 'nobody-at-all' | 'why'  || 'That is not a member. Nothing was changed.'
        11  | 'no project is named'                      | 'this'       | 'mine'        | 'project'  | ''              | 'why'  || 'Choose a project to move them to. Nothing was changed.'
        12  | 'the topic is a word'                      | 'this'       | 'mine'        | 'topic'    | 'charset'       | 'why'  || 'Choose a topic. Nothing was changed.'
        13  | 'no items are named'                       | 'this'       | 'none'        | 'status'   | 'done'          | 'why'  || 'No items were chosen. Nothing was changed.'
        14  | 'the items are words'                      | 'this'       | 'words'       | 'status'   | 'done'          | 'why'  || 'No items were chosen. Nothing was changed.'
        15  | 'the solution does not exist'              | 'missing'    | 'mine'        | 'status'   | 'done'          | 'why'  || 'That solution no longer exists. Nothing was changed.'
    }

    def "bulkChange should refuse a project or a topic of another solution and change nothing"() {

        given:
        int other = newSolution()
        int otherProject = newProject(other)
        int otherTopic = links.saveTopic(new TopicDraft(0, other, 'Theirs ' + uniq(), null), 'tony')
        int a = newItem(project, 'A')
        Map<String, Object> before = itemOf(this, a)

        expect:
        ruleOf { repo.bulkChange(sid, String.valueOf(a), 'project', String.valueOf(otherProject), 'why', 'tony') }.message ==
            'That project is not in this solution.' + NOTHING_CHANGED
        ruleOf { repo.bulkChange(sid, String.valueOf(a), 'topic', String.valueOf(otherTopic), 'why', 'tony') }.message ==
            'That topic is not in this solution.' + NOTHING_CHANGED
        itemOf(this, a) == before
        linksOf(this, 'item', a) == 0
    }

    // ---- promote: a thread message ----

    def "promote should make a thread message into an item in the given project, headed by who wrote it and when"() {

        given:
        String who = member(this)
        int conv = newThread(this, sid)
        String body = 'Parse the 835 before Friday\nThe partner needs it for QA.'
        int msg = newMessage(this, conv, who, body)
        int last = lastTopSortOrder(this, project)

        when:
        Promoted p = repo.promote(sid, 'message', msg, 'item', project, null, 'Rex')
        Map<String, Object> item = itemOf(this, p.id())
        Map<String, Object> promo = promotionOf(this, 'message', msg, 'item')

        then:
        p.kind() == 'item'
        p.id() > 0
        item.SolutionProjectID == project
        item.ParentItemID == null
        item.ItemTitle == 'Parse the 835 before Friday'
        item.ItemDetail == fromLine(this, msg, who.capitalize()) + '\n\n' + body
        item.StatusCode == 'todo'
        item.Priority == 3
        item.SortOrder == last + 1
        item.AssigneeCode == null
        item.ModifiedBy == 'rex'
        promo.SolutionID == sid
        promo.TargetID == p.id()
        promo.PromotedByCode == 'rex'
        promo.CreatedDate != null
        linksOf(this, 'item', p.id()) == 0
        logRows('SolutionPromotion', 'SolutionPromotionID', promo.SolutionPromotionID)*.IsInsertion == [true]
    }

    def "promote should name the sender by its code when the sender is not a member"() {

        given:
        String sender = ('zz' + uniq()).take(20)
        int msg = newMessage(this, newThread(this, sid), sender, 'Is the charset pinned?')

        when:
        Promoted p = repo.promote(sid, 'message', msg, 'question', null, null, 'tony')
        Map<String, Object> q = questionOf(this, p.id())

        then:
        p.kind() == 'question'
        q.SolutionID == sid
        q.QuestionText == 'Is the charset pinned?'
        q.QuestionDetail == fromLine(this, msg, sender) + '\n\nIs the charset pinned?'
        q.StatusCode == 'open'
        q.AskedByCode == 'tony'
        promotionOf(this, 'message', msg, 'question').TargetID == p.id()
    }

    def "promote should make a thread message into a Reference note, its first version saying where it came from"() {

        given:
        String who = member(this)
        int msg = newMessage(this, newThread(this, sid), who, 'How to restart the parser\nStop it, then start it.')

        when:
        Promoted p = repo.promote(sid, 'message', msg, 'note', null, null, 'tony')
        Map<String, Object> n = noteOf(this, p.id())

        then:
        p.kind() == 'note'
        n.SolutionID == sid
        n.SolutionProjectID == null
        n.NoteTitle == 'How to restart the parser'
        n.NoteTypeCode == 'reference'
        n.CurrentVersionNumber == 1
        n.NoteBody == fromLine(this, msg, who.capitalize()) + '\n\nHow to restart the parser\nStop it, then start it.'
        n.VersionChangeNote == 'Made from a thread message'
        n.VersionAuthorCode == 'tony'
        n.ModifiedBy == 'tony'
        linksOf(this, 'note', p.id()) == 0
    }

    def "promote should refuse a message of a thread of another solution and add nothing"() {

        given:
        int msg = newMessage(this, newThread(this, newSolution()), 'tony', 'Theirs')
        int items = count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectID = ?', project)

        when:
        repo.promote(sid, 'message', msg, 'item', project, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That is not in this solution any more. Nothing was added.'
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectID = ?', project) == items
        promotionOf(this, 'message', msg, 'item') == null
    }

    // ---- promote: a log entry, a note, an item ----

    def "promote should make a log entry into an item in the entry's own project, linked to it"() {

        given:
        int log = newProjectLog(this, sid, project, 'Ask QA for the charset sample\nThey have one from June.')

        when:
        Promoted p = repo.promote(sid, 'log', log, 'item', null, null, 'tony')
        Map<String, Object> item = itemOf(this, p.id())

        then:
        item.SolutionProjectID == project
        item.ItemTitle == 'Ask QA for the charset sample'
        item.ItemDetail == 'Ask QA for the charset sample\nThey have one from June.'
        linksBetween(this, sid, 'item', p.id(), 'log', log) == 1
        promotionOf(this, 'log', log, 'item').TargetID == p.id()
    }

    def "promote should make a log entry into a note in the entry's project, linked to it"() {

        given:
        int log = newProjectLog(this, sid, project, 'Restart steps\nStop, wait, start.')

        when:
        Promoted p = repo.promote(sid, 'log', log, 'note', null, null, 'tony')
        Map<String, Object> n = noteOf(this, p.id())

        then:
        p.kind() == 'note'
        n.SolutionProjectID == project
        n.NoteTitle == 'Restart steps'
        n.NoteBody == 'Restart steps\nStop, wait, start.'
        n.VersionChangeNote == 'Made from a log entry'
        linksBetween(this, sid, 'note', p.id(), 'log', log) == 1
    }

    def "promote should make a note into an item in the note's project, with the note's title and body, linked to it"() {

        given:
        int note = newNote(this, sid, project, 'Runbook for the QA move', 'Step one: stop the parser.')

        when:
        Promoted p = repo.promote(sid, 'note', note, 'item', null, null, 'tony')
        Map<String, Object> item = itemOf(this, p.id())

        then:
        p.kind() == 'item'
        item.SolutionProjectID == project
        item.ItemTitle == 'Runbook for the QA move'
        item.ItemDetail == 'Runbook for the QA move\n\nStep one: stop the parser.'
        linksBetween(this, sid, 'item', p.id(), 'note', note) == 1
    }

    def "promote should put the new item in the project given rather than the source's own"() {

        given:
        int other = newProject(sid)
        int note = newNote(this, sid, project, 'Runbook', 'Body')

        expect:
        itemOf(this, repo.promote(sid, 'note', note, 'item', other, null, 'tony').id()).SolutionProjectID == other
    }

    def "promote should make an item into a note in the item's project, with the item's title and detail, linked to it"() {

        given:
        int item = newItem(project, 'Write the runbook')

        when:
        Promoted p = repo.promote(sid, 'item', item, 'note', null, null, 'tony')
        Map<String, Object> n = noteOf(this, p.id())

        then:
        p.kind() == 'note'
        n.SolutionProjectID == project
        n.NoteTitle == 'Write the runbook'
        n.NoteBody == 'Write the runbook\n\ndetail'
        n.VersionChangeNote == 'Made from an item'
        linksBetween(this, sid, 'note', p.id(), 'item', item) == 1
    }

    def "promote should write its link the way stp_SolutionLink_Save does, so the same pair cannot be linked again"() {

        given:
        int item = newItem(project, 'Write the runbook')
        Promoted p = repo.promote(sid, 'item', item, 'note', null, null, 'tony')

        when:
        links.link(sid, 'item', item, 'note', p.id(), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'Those two are already linked. Nothing was saved.'
        linksBetween(this, sid, 'note', p.id(), 'item', item) == 1
    }

    // ---- promote: the title ----

    @Unroll
    def "promote should title the item '#expectedTitle' when the source text is #scenario"() {

        given:
        int log = newLog(sid, text)

        expect:
        itemOf(this, repo.promote(sid, 'log', log, 'item', project, null, 'tony').id()).ItemTitle == expectedTitle

        where:
        scenario                     | text                                                       || expectedTitle
        'one line'                   | 'Parse the 835'                                            || 'Parse the 835'
        'the first of two lines'     | 'Parse the 835\nIt needs the charset.'                     || 'Parse the 835'
        'after blank lines'          | '\n\nParse the 835\nmore'                                  || 'Parse the 835'
        'ended by Windows line ends' | 'Parse the 835\r\nmore'                                    || 'Parse the 835'
        'in bold marks'              | '**Changed 2 items: status to Done.**\n\nWhy: dead path'   || 'Changed 2 items: status to Done.'
        'separated by a tab'         | 'Parse\tthe 835'                                           || 'Parse the 835'
        'padded'                     | 'Parse the 835   \nmore'                                   || 'Parse the 835'
    }

    def "promote should cut a long first line at its first sentence when the sentence is long enough"() {

        given:
        int log = newLog(sid, 'This is the first sentence of a long line. ' + getReplicatedString('x', 200))

        expect:
        itemOf(this, repo.promote(sid, 'log', log, 'item', project, null, 'tony').id()).ItemTitle ==
            'This is the first sentence of a long line.'
    }

    def "promote should cut a long first line with no sentence at a word and mark the cut"() {

        given:
        int log = newLog(sid, getReplicatedString('word ', 60).trim())

        when:
        String title = itemOf(this, repo.promote(sid, 'log', log, 'item', project, null, 'tony').id()).ItemTitle

        then:
        title == getReplicatedString('word ', 39).trim() + '...'
        title.length() == 197
    }

    def "promote should pass over a sentence that ends too early and cut at a word instead"() {

        given:
        int log = newLog(sid, 'Hi. ' + getReplicatedString('word ', 60).trim())

        expect:
        itemOf(this, repo.promote(sid, 'log', log, 'item', project, null, 'tony').id()).ItemTitle ==
            'Hi. ' + getReplicatedString('word ', 38).trim() + '...'
    }

    def "promote should use the title given, trimmed, instead of the first line"() {

        given:
        int note = newNote(this, sid, project, 'Runbook', 'Body')

        expect:
        itemOf(this, repo.promote(sid, 'note', note, 'item', null, '  Write the runbook for the QA move  ', 'tony').id()).ItemTitle ==
            'Write the runbook for the QA move'
    }

    def "promote should cut a note's title to 300 characters and mark the cut"() {

        given:
        int item = newItem(project, 'Short')

        when:
        String title = noteOf(this, repo.promote(sid, 'item', item, 'note', null, getReplicatedString('n', 400), 'tony').id()).NoteTitle

        then:
        title == getReplicatedString('n', 297) + '...'
    }

    def "promote should refuse a source with no text and add nothing"() {

        given:
        int msg = newMessage(this, newThread(this, sid), 'tony', '   ')

        when:
        repo.promote(sid, 'message', msg, 'item', project, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That has no text to make anything of. Nothing was added.'
        promotionOf(this, 'message', msg, 'item') == null
    }

    def "promote should take the first line with words on it when an earlier line holds only spaces"() {

        given:
        int msg = newMessage(this, newThread(this, sid), 'tony', '\n \nParse the 835\nmore')

        expect:
        itemOf(this, repo.promote(sid, 'message', msg, 'item', project, null, 'tony').id()).ItemTitle == 'Parse the 835'
    }

    // ---- promote: repeats, questions, projects, pairs ----

    def "promote should refuse the same source into the same kind while the first record exists, and allow it once it is gone"() {

        given:
        int msg = newMessage(this, newThread(this, sid), 'tony', 'Parse the 835')
        Promoted first = repo.promote(sid, 'message', msg, 'item', project, null, 'tony')
        int firstPromotion = ((Number) promotionOf(this, 'message', msg, 'item').SolutionPromotionID).intValue()

        when:
        repo.promote(sid, 'message', msg, 'item', project, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That was already made into an item (#' + first.id() + '). Nothing was added.'
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectID = ?', project) == 1

        when:
        Promoted asNote = repo.promote(sid, 'message', msg, 'note', null, null, 'tony')

        then:
        asNote.kind() == 'note'

        when:
        row('EXEC dbo.stp_SolutionProjectItem_Delete ?', first.id())
        Promoted second = repo.promote(sid, 'message', msg, 'item', project, null, 'tony')

        then:
        second.id() != first.id()
        count("SELECT COUNT(*) FROM dbo.SolutionPromotion WHERE SourceKind = 'message' AND SourceID = ? AND TargetKind = 'item'", msg) == 1
        promotionOf(this, 'message', msg, 'item').TargetID == second.id()
        logRows('SolutionPromotion', 'SolutionPromotionID', firstPromotion)*.IsInsertion == [true, false]
        logRows('SolutionPromotion', 'SolutionPromotionID', firstPromotion).last().DeletedDate != null
    }

    @Unroll
    def "promote should name #expectedName in the refusal when #scenario is made twice"() {

        given:
        int msg = newMessage(this, newThread(this, sid), 'tony', 'Is the charset pinned?')
        int note = newNote(this, sid, project, 'Runbook ' + uniq(), 'Body')
        int sourceId = sourceKind == 'message' ? msg : note
        Promoted first = repo.promote(sid, sourceKind, sourceId, targetKind, project, null, 'tony')

        when:
        repo.promote(sid, sourceKind, sourceId, targetKind, project, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That was already made into ' + expectedName + ' (#' + first.id() + '). Nothing was added.'

        where:
        scenario                 | sourceKind | targetKind || expectedName
        'a message as a question' | 'message' | 'question' || 'a question'
        'a message as a note'    | 'message'  | 'note'     || 'a note'
        'a note as an item'      | 'note'     | 'item'     || 'an item'
    }

    def "promote should refuse a question already open and allow it once that one is dropped"() {

        given:
        int open = newQuestion(this, sid, 'Is the charset pinned?')
        int msg = newMessage(this, newThread(this, sid), 'tony', 'Is the charset pinned?\nRex asked on Monday.')

        when:
        repo.promote(sid, 'message', msg, 'question', null, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That question is already open. Nothing was added.'
        count('SELECT COUNT(*) FROM dbo.SolutionQuestion WHERE SolutionID = ?', sid) == 1

        when:
        row("EXEC dbo.stp_SolutionQuestion_SetStatus ?, 'dropped', NULL, 'tony'", open)
        Promoted p = repo.promote(sid, 'message', msg, 'question', null, null, 'tony')

        then:
        questionOf(this, p.id()).QuestionText == 'Is the charset pinned?'
        questionOf(this, p.id()).StatusCode == 'open'
    }

    @Unroll
    def "promote should refuse an item with no project and add nothing when #scenario"() {

        given:
        int msg = newMessage(this, newThread(this, sid), 'tony', 'Parse the 835')
        int log = newLog(sid, 'Parse the 835')
        int otherProject = newProject(newSolution())
        int sourceId = sourceKind == 'message' ? msg : log
        Integer projectId = projectName == 'other' ? otherProject : null
        int items = count('SELECT COUNT(*) FROM dbo.SolutionProjectItem')

        when:
        repo.promote(sid, sourceKind, sourceId, 'item', projectId, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem') == items
        promotionOf(this, sourceKind, sourceId, 'item') == null

        where:
        scenario                                         | sourceKind | projectName || expectedMessage
        'a message names no project'                     | 'message'  | 'none'      || 'Choose the project the item goes in. Nothing was added.'
        'a log entry with no project names none'         | 'log'      | 'none'      || 'Choose the project the item goes in. Nothing was added.'
        'the project named is of another solution'       | 'message'  | 'other'     || 'That project is not in this solution. Nothing was added.'
    }

    @Unroll
    def "#seq. promote should refuse #scenario and add nothing"() {

        given:
        int log = newLog(sid, 'Parse the 835')
        int note = newNote(this, sid, project, 'Runbook', 'Body')
        int item = newItem(project, 'Item')
        int msg = newMessage(this, newThread(this, sid), 'tony', 'Parse the 835')
        Map<String, Integer> ids = [log: log, note: note, item: item, message: msg, topic: item, decision: log]

        when:
        repo.promote(sid, sourceKind, ids[sourceKind], targetKind, project, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionPromotion WHERE SolutionID = ?', sid) == 0

        where:
        seq | scenario                 | sourceKind | targetKind  || expectedMessage
        1   | 'a log as a question'    | 'log'      | 'question'  || NOT_ALLOWED
        2   | 'a log as a decision'    | 'log'      | 'decision'  || NOT_ALLOWED
        3   | 'a note as a note'       | 'note'     | 'note'      || NOT_ALLOWED
        4   | 'a note as a question'   | 'note'     | 'question'  || NOT_ALLOWED
        5   | 'an item as an item'     | 'item'     | 'item'      || NOT_ALLOWED
        6   | 'an item as a question'  | 'item'     | 'question'  || NOT_ALLOWED
        7   | 'a message as a message' | 'message'  | 'message'   || NOT_ALLOWED
        8   | 'a topic as an item'     | 'topic'    | 'item'      || NOT_ALLOWED
        9   | 'a decision as a note'   | 'decision' | 'note'      || NOT_ALLOWED
    }

    @Unroll
    def "promote should refuse a source that is not in the solution when #scenario"() {

        given:
        int other = newSolution()
        Map<String, Integer> ids = [theirNote: newNote(this, other, null, 'Theirs', 'Body'), theirItem: newItem(newProject(other), 'Theirs'),
                                    theirLog: newLog(other, 'Theirs'), missing: 987654321]

        when:
        repo.promote(sid, sourceKind, ids[sourceName], targetKind, project, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                          | sourceKind | sourceName  | targetKind || expectedMessage
        'the note is of another solution' | 'note'     | 'theirNote' | 'item'     || 'That is not in this solution any more. Nothing was added.'
        'the item is of another solution' | 'item'     | 'theirItem' | 'note'     || 'That is not in this solution any more. Nothing was added.'
        'the log is of another solution'  | 'log'      | 'theirLog'  | 'item'     || 'That is not in this solution any more. Nothing was added.'
        'the note does not exist'         | 'note'     | 'missing'   | 'item'     || 'That is not in this solution any more. Nothing was added.'
        'the message does not exist'      | 'message'  | 'missing'   | 'note'     || 'That is not in this solution any more. Nothing was added.'
    }

    def "promote should refuse a solution that does not exist"() {

        given:
        int note = newNote(this, sid, project, 'Runbook', 'Body')

        expect:
        ruleOf { repo.promote(-1, 'note', note, 'item', project, null, 'tony') }.message == 'That solution no longer exists. Nothing was added.'
    }

    def "the promotion audit should record an edit of a promotion row through its update trigger"() {

        given:
        int note = newNote(this, sid, project, 'Runbook', 'Body')
        repo.promote(sid, 'note', note, 'item', null, null, 'tony')
        int id = ((Number) promotionOf(this, 'note', note, 'item').SolutionPromotionID).intValue()

        when:
        sql.update('UPDATE dbo.SolutionPromotion SET PromotedByMemberID = PromotedByMemberID WHERE SolutionPromotionID = ?', id)

        then:
        logRows('SolutionPromotion', 'SolutionPromotionID', id)*.IsInsertion == [true, false]
        logRows('SolutionPromotion', 'SolutionPromotionID', id).last().DeletedDate == null
        logRows('SolutionPromotion', 'SolutionPromotionID', id).last().SourceKind == 'note'
        logRows('SolutionPromotion', 'SolutionPromotionID', id).last().TargetKind == 'item'
    }

    // ---- provenance ----

    def "provenance should say where a record came from and what was made from it, from both ends"() {

        given:
        String who = member(this)
        int conv = newThread(this, sid)
        int msg = newMessage(this, conv, who, 'Is the charset pinned?')
        int note = newNote(this, sid, project, 'Runbook for the QA move', 'Body')
        Promoted question = repo.promote(sid, 'message', msg, 'question', null, null, who)
        Promoted item = repo.promote(sid, 'note', note, 'item', null, null, who)

        when:
        List<Provenance> ofQuestion = repo.provenance(sid, 'question', question.id())
        List<Provenance> ofMessage = repo.provenance(sid, 'message', msg)
        List<Provenance> ofItem = repo.provenance(sid, 'item', item.id())
        List<Provenance> ofNote = repo.provenance(sid, 'note', note)

        then:
        ofQuestion.size() == 1
        ofQuestion[0].direction() == 'from'
        ofQuestion[0].kind() == 'message'
        ofQuestion[0].id() == msg
        ofQuestion[0].title() == 'Is the charset pinned?'
        ofQuestion[0].conversationId() == conv
        ofQuestion[0].promotedDate() != null
        ofQuestion[0].promotedByCode() == who
        ofQuestion[0].promotedByName() == who.capitalize()
        ofMessage*.direction() == ['into']
        ofMessage*.kind() == ['question']
        ofMessage*.id() == [question.id()]
        ofMessage[0].title() == 'Is the charset pinned?'
        ofMessage[0].conversationId() == null
        ofItem*.direction() == ['from']
        ofItem*.kind() == ['note']
        ofItem[0].title() == 'Runbook for the QA move'
        ofNote*.direction() == ['into']
        ofNote*.id() == [item.id()]
        ofNote[0].title() == 'Runbook for the QA move'
    }

    def "provenance should leave out a record that is gone and answer nothing for a record of no promotion"() {

        given:
        int note = newNote(this, sid, project, 'Runbook', 'Body')
        Promoted item = repo.promote(sid, 'note', note, 'item', null, null, 'tony')
        Promoted asNote = repo.promote(sid, 'item', item.id(), 'note', null, null, 'tony')

        when:
        row('EXEC dbo.stp_SolutionNote_Delete ?', asNote.id())

        then:
        repo.provenance(sid, 'item', item.id())*.direction() == ['from']
        repo.provenance(sid, 'item', item.id())*.kind() == ['note']
        repo.provenance(sid, 'item', newItem(project, 'Plain')) == []

        when:
        row('EXEC dbo.stp_SolutionProjectItem_Delete ?', item.id())

        then:
        repo.provenance(sid, 'note', note) == []
    }

    def "provenance should answer only within the solution asked about"() {

        given:
        int note = newNote(this, sid, project, 'Runbook', 'Body')
        Promoted item = repo.promote(sid, 'note', note, 'item', null, null, 'tony')

        expect:
        repo.provenance(newSolution(), 'item', item.id()) == []
        repo.provenance(newSolution(), 'note', note) == []
        repo.provenance(sid, 'item', item.id()).size() == 1
    }

    def "provenance should list a record's promotions oldest first"() {

        given:
        int msg = newMessage(this, newThread(this, sid), 'tony', 'Parse the 835')
        Promoted item = repo.promote(sid, 'message', msg, 'item', project, null, 'tony')
        Promoted note = repo.promote(sid, 'message', msg, 'note', null, null, 'tony')
        Promoted question = repo.promote(sid, 'message', msg, 'question', null, null, 'tony')
        sql.update("UPDATE dbo.SolutionPromotion SET CreatedDate = DATEADD(MINUTE, -10, GETDATE()) WHERE SourceKind = 'message' AND SourceID = ? AND TargetKind = 'question'", msg)
        sql.update("UPDATE dbo.SolutionPromotion SET CreatedDate = DATEADD(MINUTE, -5, GETDATE()) WHERE SourceKind = 'message' AND SourceID = ? AND TargetKind = 'item'", msg)

        expect:
        repo.provenance(sid, 'message', msg)*.kind() == ['question', 'item', 'note']
        repo.provenance(sid, 'message', msg)*.id() == [question.id(), item.id(), note.id()]
    }

    // ---- stp_Solution_Delete ----

    def "stp_Solution_Delete should take the solution's promotions, saved views and file versions and leave another solution's"() {

        given:
        JdbcSolutionSavedViewRepository views = new JdbcSolutionSavedViewRepository(dataSource())
        JdbcSolutionFileRepository files = new JdbcSolutionFileRepository(dataSource())
        int note = newNote(this, sid, project, 'Runbook', 'Body')
        repo.promote(sid, 'note', note, 'item', null, null, 'tony')
        int view = views.save(sid, 'tony', 'items', 'Mine', '{}')
        int file = newFile(sid, 'charset.xlsx')
        files.newVersion(file, 'charset-v2.xlsx', sid + '/abcd1234-charset-v2.xlsx', 'application/vnd.ms-excel', 20L, 'QA added', 'tony')
        int version = ((Number) scalar('SELECT SolutionFileVersionID FROM dbo.SolutionFileVersion WHERE SolutionFileID = ?', file)).intValue()
        int promotion = ((Number) promotionOf(this, 'note', note, 'item').SolutionPromotionID).intValue()

        int other = newSolution()
        int otherNote = newNote(this, other, null, 'Theirs', 'Body')
        repo.promote(other, 'note', otherNote, 'item', newProject(other), null, 'tony')
        int otherView = views.save(other, 'tony', 'items', 'Theirs', '{}')
        int otherFile = newFile(other, 'theirs.txt')
        files.newVersion(otherFile, 'theirs-v2.txt', other + '/abcd1234-theirs-v2.txt', 'text/plain', 5L, null, 'tony')

        when:
        row('EXEC dbo.stp_Solution_Delete ?', sid)

        then:
        count('SELECT COUNT(*) FROM dbo.SolutionPromotion WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionSavedView WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionFileVersion WHERE SolutionFileID = ?', file) == 0
        logRows('SolutionPromotion', 'SolutionPromotionID', promotion).last().DeletedDate != null
        logRows('SolutionSavedView', 'SolutionSavedViewID', view).last().DeletedDate != null
        logRows('SolutionFileVersion', 'SolutionFileVersionID', version).last().DeletedDate != null

        and: 'the other solution keeps its own'
        count('SELECT COUNT(*) FROM dbo.SolutionPromotion WHERE SolutionID = ?', other) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionSavedView WHERE SolutionSavedViewID = ?', otherView) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionFileVersion WHERE SolutionFileID = ?', otherFile) == 1
    }
}
