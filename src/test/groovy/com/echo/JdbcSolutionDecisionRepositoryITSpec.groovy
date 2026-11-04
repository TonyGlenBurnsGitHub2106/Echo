package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDecisionModels.Decision
import com.echo.SolutionDecisionModels.DecisionDraft
import com.echo.SolutionDecisionModels.OpenLink
import com.echo.SolutionDecisionModels.Question
import com.echo.SolutionDecisionModels.QuestionDraft
import com.echo.SolutionDecisionModels.Saved
import com.echo.SolutionLinkModels.Change
import com.echo.SolutionLinkModels.Link
import com.echo.SolutionLinkModels.StoredLink
import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionOrientationModels.SummaryAge

import spock.lang.Unroll

/**
 * Story 5695: JdbcSolutionDecisionRepository (stp_SolutionQuestion*, stp_SolutionDecision*) and
 * JdbcSolutionOrientationRepository (stp_Solution_StatusSummaryAge) against a real EchoTest_ database --
 * questions saved, merged, refused, answered, reopened and deleted; decisions recorded with their journal
 * entry, merged, refused, superseded and deleted (what rested on them put back); the register's order and
 * its "current" filter and counts; journal entries promoted to questions and decisions; what a decision could
 * close; the age of "Where things stand"; and, for the release, that links accept the two new kinds,
 * vw_SolutionLinkable and fn_SolutionActivity list them, and stp_Solution_Delete / stp_SolutionLog_Delete
 * clean up after them. Every feature is rolled back by EchoDbITSpec.
 */
class JdbcSolutionDecisionRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionDecisionRepository repo
    JdbcSolutionLinkRepository links
    JdbcSolutionOrientationRepository orientation
    String solutionName
    String projectName
    int sid
    int project

    def setup() {
        repo = new JdbcSolutionDecisionRepository(dataSource())
        links = new JdbcSolutionLinkRepository(dataSource())
        orientation = new JdbcSolutionOrientationRepository(dataSource())
        solutionName = 'IT decisions ' + uniq()
        projectName = 'Deploy ' + uniq()
        sid = newSolution(solutionName)
        project = newProject(sid, projectName)
    }

    // ---- fixtures ----

    private static int ask(JdbcSolutionDecisionRepository r, int solutionId, String text, String detail = null, String owner = null,
                           String by = 'rex') {
        r.saveQuestion(new QuestionDraft(0, solutionId, text, detail, owner), by)
    }

    private static Saved decide(JdbcSolutionDecisionRepository r, int solutionId, String title, String status = null, String outcome = null,
                                LocalDateTime decided = null, Integer supersedes = null, String by = 'tony') {
        r.saveDecision(new DecisionDraft(0, solutionId, title, null, null, outcome, status, decided, supersedes), by)
    }

    /** A journal entry written by the named author. */
    private static int newLogBy(EchoDbITSpec db, int solutionId, String text, String author, String type = 'note') {
        db.idOf('EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, NULL, ?, ?, ?, NULL', solutionId, type, author, text)
    }

    /** Saves the solution again with the given summary (and description) -- one more version in Solution_LOG. */
    private static void saveSummary(EchoDbITSpec db, int solutionId, String name, String summary, String description = 'desc') {
        db.idOf("EXEC dbo.stp_Solution_Save ?, ?, ?, 'todo', 'tony', ?, NULL, 0, 0, 'felix', NULL", solutionId, name, description, summary)
    }

    /** How many links touch one record, from either end. */
    private static int linksOf(EchoDbITSpec db, String kind, int id) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE (FromKind = ? AND FromID = ?) OR (ToKind = ? AND ToID = ?)', kind, id, kind, id)
    }

    /** How many links join exactly this pair, stored in this order. */
    private static int pairs(EchoDbITSpec db, String fromKind, int fromId, String toKind, int toId) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE FromKind = ? AND FromID = ? AND ToKind = ? AND ToID = ?', fromKind, fromId, toKind, toId)
    }

    private static Object logText(EchoDbITSpec db, int logId) {
        db.scalar('SELECT LogText FROM dbo.SolutionLog WHERE SolutionLogID = ?', logId)
    }

    // ---- questions ----

    def "saveQuestion should add an open question asked by the saver with its text on one line and findQuestion should read it back"() {

        when:
        int id = repo.saveQuestion(new QuestionDraft(0, sid, "  Which\r\ncharset\tdo we use? ", '  Remit says windows-1252.  ', 'Felix'), 'Rex')
        Question q = repo.findQuestion(id)

        then:
        id > 0
        q.id() == id
        q.solutionId() == sid
        q.text() == 'Which  charset do we use?'
        q.detail() == 'Remit says windows-1252.'
        !q.truncated()
        q.statusCode() == 'open'
        q.open()
        q.askedByCode() == 'rex'
        q.ownerCode() == 'felix'
        q.answeredByDecisionId() == null
        q.answeredDate() == null
        q.logId() == null
        q.linkCount() == 0
        q.modifiedBy() == 'rex'
        q.createdDate() != null
        q.modifiedDate() == null
        logRows('SolutionQuestion', 'SolutionQuestionID', id)*.IsInsertion == [true]
    }

    def "saveQuestion should keep a null field, clear an empty detail or owner and record the editor when a question is edited"() {

        given:
        int id = ask(repo, sid, 'Which charset?', 'First detail', 'felix')

        when:
        repo.saveQuestion(new QuestionDraft(id, 0, null, null, null), 'Tony')
        Question kept = repo.findQuestion(id)

        then:
        kept.text() == 'Which charset?'
        kept.detail() == 'First detail'
        kept.ownerCode() == 'felix'
        kept.askedByCode() == 'rex'
        kept.modifiedBy() == 'tony'
        kept.modifiedDate() != null

        when:
        repo.saveQuestion(new QuestionDraft(id, 0, ' Which charset now? ', '', ''), 'tony')
        Question cleared = repo.findQuestion(id)

        then:
        cleared.text() == 'Which charset now?'
        cleared.detail() == null
        cleared.ownerCode() == null
        cleared.askedByCode() == 'rex'
    }

    def "saveQuestion should keep the question in its own solution when an edit names another solution"() {

        given:
        int id = ask(repo, sid, 'Stay?')

        when:
        repo.saveQuestion(new QuestionDraft(id, newSolution(), 'Stay put?', null, null), 'tony')

        then:
        repo.findQuestion(id).solutionId() == sid
        repo.findQuestion(id).text() == 'Stay put?'
    }

    @Unroll
    def "#seq. saveQuestion should refuse and write nothing when #scenario"() {

        given:
        int existing = ask(repo, sid, 'Existing?')
        int other = ask(repo, sid, 'Other?')
        int before = count('SELECT COUNT(*) FROM dbo.SolutionQuestion')
        int id = target == 'existing' ? existing : (target == 'other' ? other : (target == 'missing' ? 987654321 : 0))
        int solution = target == 'missing-solution' ? -1 : sid
        String questionText = textLength > 0 ? getReplicatedString('q', textLength) : text

        when:
        repo.saveQuestion(new QuestionDraft(id, solution, questionText, null, null), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionQuestion') == before
        repo.findQuestion(existing).text() == 'Existing?'
        repo.findQuestion(other).text() == 'Other?'

        where:
        seq | scenario                                          | target             | text            | textLength || expectedMessage
        1   | 'the question is already open'                    | 'new'              | 'Existing?'     | 0          || 'That question is already open in this solution. Nothing was saved.'
        2   | 'the question is already open, padded'            | 'new'              | '  Existing?  ' | 0          || 'That question is already open in this solution. Nothing was saved.'
        3   | 'an edit takes the text of another open question' | 'other'            | 'Existing?'     | 0          || 'That question is already open in this solution. Nothing was saved.'
        4   | 'a new question is blank'                         | 'new'              | '   '           | 0          || 'A question needs its question. Nothing was added.'
        5   | 'a new question is missing'                       | 'new'              | null            | 0          || 'A question needs its question. Nothing was added.'
        6   | 'an edit blanks the question'                     | 'existing'         | ' '             | 0          || 'A question cannot be blank. Nothing was saved.'
        7   | 'the question is over 800 characters'             | 'new'              | null            | 801        || 'A question can be at most 800 characters. Put the rest in its detail. Nothing was saved.'
        8   | 'the question to edit no longer exists'           | 'missing'          | 'x'             | 0          || 'That question no longer exists. Nothing was saved.'
        9   | 'the solution does not exist'                     | 'missing-solution' | 'x'             | 0          || 'That solution no longer exists. Nothing was added.'
    }

    def "saveQuestion should take exactly 800 characters and let a question be asked again once the first is no longer open"() {

        when:
        int longest = ask(repo, sid, getReplicatedString('q', 800))

        then:
        repo.findQuestion(longest).text().length() == 800

        when:
        int first = ask(repo, sid, 'Asked twice?')
        repo.setQuestionStatus(first, 'dropped', null, 'tony')
        int second = ask(repo, sid, 'Asked twice?')
        int elsewhere = ask(repo, newSolution(), 'Asked twice?')

        then:
        second > 0
        second != first
        elsewhere > 0
    }

    def "saveQuestion should keep working on the same connection when a refusal came first"() {

        given:
        ruleOf { ask(repo, sid, '  ') }

        expect:
        ask(repo, sid, 'Still works?') > 0
    }

    def "setQuestionStatus should answer the question by a decision, stamp the date and count it on the decision"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        int d = decide(repo, sid, 'Read as UTF-8').id()

        when:
        boolean changed = repo.setQuestionStatus(q, 'answered', d, 'Tony')
        Question got = repo.findQuestion(q)

        then:
        changed
        got.statusCode() == 'answered'
        !got.open()
        got.answeredByDecisionId() == d
        got.answeredByTitle() == 'Read as UTF-8'
        got.answeredDate() != null
        got.modifiedBy() == 'tony'
        repo.findDecision(d).answeredCount() == 1
    }

    def "setQuestionStatus should answer by hand and keep the first answered date when a decision answers it later"() {

        given:
        int q = ask(repo, sid, 'Which charset?')

        when:
        repo.setQuestionStatus(q, ' ANSWERED ', null, 'tony')
        Question byHand = repo.findQuestion(q)

        then:
        byHand.statusCode() == 'answered'
        byHand.answeredByDecisionId() == null
        byHand.answeredDate() != null

        when:
        sql.update('UPDATE dbo.SolutionQuestion SET AnsweredDate = DATEADD(DAY, -2, AnsweredDate) WHERE SolutionQuestionID = ?', q)
        LocalDateTime stamped = repo.findQuestion(q).answeredDate()
        int d = decide(repo, sid, 'Read as UTF-8').id()
        repo.setQuestionStatus(q, 'answered', d, 'tony')
        Question later = repo.findQuestion(q)

        then:
        later.answeredByDecisionId() == d
        later.answeredDate() == stamped
    }

    @Unroll
    def "setQuestionStatus should keep the answering decision when the question is answered again #scenario"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        int d = decide(repo, sid, 'Read as UTF-8').id()
        repo.setQuestionStatus(q, 'answered', d, 'tony')

        when:
        repo.setQuestionStatus(q, 'answered', decisionId, 'tony')

        then:
        repo.findQuestion(q).answeredByDecisionId() == d

        where:
        scenario                 | decisionId || unused
        'with no decision'       | null       || null
        'with decision zero'     | 0          || null
    }

    @Unroll
    def "setQuestionStatus should refuse with the proc's sentence and leave the question open when #scenario"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        int foreign = decide(repo, newSolution(), 'Elsewhere').id()
        int questionId = questionTarget == 'mine' ? q : 987654321
        Integer decisionId = decisionTarget == 'foreign' ? foreign : (decisionTarget == 'missing' ? 987654321 : null)

        when:
        repo.setQuestionStatus(questionId, status, decisionId, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        repo.findQuestion(q).statusCode() == 'open'

        where:
        scenario                               | questionTarget | status     | decisionTarget || expectedMessage
        'the decision is in another solution'  | 'mine'         | 'answered' | 'foreign'      || 'That decision is not in this solution. Nothing was saved.'
        'the decision does not exist'          | 'mine'         | 'answered' | 'missing'      || 'That decision is not in this solution. Nothing was saved.'
        'the status is closed'                 | 'mine'         | 'closed'   | 'none'         || 'A question is open, answered or dropped. Nothing was saved.'
        'the status is empty'                  | 'mine'         | ''         | 'none'         || 'A question is open, answered or dropped. Nothing was saved.'
        'the status is missing'                | 'mine'         | null       | 'none'         || 'A question is open, answered or dropped. Nothing was saved.'
        'the question no longer exists'        | 'missing'      | 'dropped'  | 'none'         || 'That question no longer exists. Nothing was saved.'
    }

    @Unroll
    def "setQuestionStatus should clear the answer and its date when the question is #scenario"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        int d = decide(repo, sid, 'Read as UTF-8').id()
        repo.setQuestionStatus(q, 'answered', d, 'tony')

        when:
        boolean changed = repo.setQuestionStatus(q, status, d, 'rex')
        Question got = repo.findQuestion(q)

        then:
        changed
        got.statusCode() == expectedStatus
        got.answeredByDecisionId() == null
        got.answeredByTitle() == null
        got.answeredDate() == null
        got.modifiedBy() == 'rex'
        repo.findDecision(d).answeredCount() == 0

        where:
        scenario      | status    || expectedStatus
        'reopened'    | 'open'    || 'open'
        'dropped'     | 'Dropped' || 'dropped'
    }

    def "deleteQuestion should remove the question and its links but leave the decision and the item, answering false the second time"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        int item = newItem(project)
        int d = decide(repo, sid, 'Read as UTF-8').id()
        links.link(sid, 'question', q, 'item', item, 'tony')
        links.link(sid, 'decision', d, 'question', q, 'tony')
        repo.setQuestionStatus(q, 'answered', d, 'tony')

        expect:
        repo.deleteQuestion(q)
        repo.findQuestion(q) == null
        linksOf(this, 'question', q) == 0
        repo.findDecision(d) != null
        repo.findDecision(d).linkCount() == 1
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = ?', item) == 1
        logRows('SolutionQuestion', 'SolutionQuestionID', q).last().DeletedDate != null
        !repo.deleteQuestion(q)
    }

    def "findQuestion should answer null when the id is unknown"() {

        expect:
        repo.findQuestion(987654321) == null
        repo.findQuestion(0) == null
    }

    def "questions should list open first, then answered, then dropped, newest first in each, with a 300-character preview"() {

        given:
        int a = ask(repo, sid, 'Alpha?', getReplicatedString('d', 350))
        int b = ask(repo, sid, 'Beta?')
        int c = ask(repo, sid, 'Gamma?')
        int dropped = ask(repo, sid, 'Delta?')
        int e = ask(repo, sid, 'Epsilon?')
        repo.setQuestionStatus(c, 'answered', null, 'tony')
        repo.setQuestionStatus(dropped, 'dropped', null, 'tony')
        ask(repo, newSolution(), 'Elsewhere?')

        when:
        List<Question> all = repo.questions(sid, null, null, 0, 50)
        Question alpha = all.find { Question q -> q.id() == a }

        then:
        all*.id() == [e, b, a, c, dropped]
        alpha.detail().length() == 300
        alpha.truncated()
        !all.find { Question q -> q.id() == b }.truncated()
        repo.questions(sid, 'open', null, 0, 50)*.id() == [e, b, a]
        repo.questions(sid, ' DROPPED ', null, 0, 50)*.id() == [dropped]
        repo.questions(sid, null, null, 1, 2)*.id() == [b, a]
        repo.questions(sid, null, null, 0, 0).size() == 5
        repo.questions(sid, null, null, 10, 50).isEmpty()
        repo.questionCounts(sid, null, null) == [total: 5, all: 5, 'status:open': 3, 'status:answered': 1, 'status:dropped': 1]
        repo.questionCounts(sid, 'open', null) == [total: 3, all: 5, 'status:open': 3, 'status:answered': 1, 'status:dropped': 1]
        repo.questionCounts(newSolution(), null, null) == [total: 0, all: 0]
    }

    def "questions and questionCounts should match the text or the detail with LIKE wildcards literal when searching"() {

        given:
        ask(repo, sid, 'Which charset?')
        ask(repo, sid, 'Other?', 'All about the CHARSET')
        ask(repo, sid, '50% done?')
        ask(repo, sid, '500 done?')

        expect:
        repo.questions(sid, null, 'charset', 0, 50)*.text() as Set == ['Which charset?', 'Other?'] as Set
        repo.questionCounts(sid, null, 'charset').total == 2
        repo.questions(sid, null, '0%', 0, 50)*.text() == ['50% done?']
        repo.questions(sid, null, '_0', 0, 50).isEmpty()
        repo.questionCounts(sid, null, 'nothing matches') == [total: 0, all: 4]
    }

    def "questionFromLog should make an open question of the entry asked by its author, link the two and leave the entry alone"() {

        given:
        int entry = newLogBy(this, sid, 'Do we keep the old remit folder?\nIt has a dupe.', 'rex')
        int seq = idOf('SELECT SequenceNumber FROM dbo.SolutionLog WHERE SolutionLogID = ?', entry)

        when:
        int id = repo.questionFromLog(entry, 'Tony')
        Question q = repo.findQuestion(id)

        then:
        q.text() == 'Do we keep the old remit folder?'
        q.detail() == 'It has a dupe.'
        q.open()
        q.askedByCode() == 'rex'
        q.logId() == entry
        q.logSequenceNumber() == seq
        q.modifiedBy() == 'tony'
        q.linkCount() == 1
        pairs(this, 'log', entry, 'question', id) == 1
        logText(this, entry) == 'Do we keep the old remit folder?\nIt has a dupe.'

        and: 'a second promote of the same entry is refused'
        ruleOf { repo.questionFromLog(entry, 'tony') }.message == 'That log entry is already a question. Nothing was added.'
    }

    @Unroll
    def "questionFromLog should make the question '#expectedText' when the entry is #scenario"() {

        given:
        int entry = newLogBy(this, sid, text, 'tony')

        when:
        Question q = repo.findQuestion(repo.questionFromLog(entry, 'tony'))

        then:
        q.text() == expectedText
        q.detail() == expectedDetail

        where:
        scenario                           | text                                 || expectedText             | expectedDetail
        'one line'                         | 'Why keep it?'                       || 'Why keep it?'           | null
        'bold on the first line'           | '**Question:** why keep it?\nmore'   || 'Question: why keep it?' | 'more'
        'after blank lines'                | '\n\nWhy?\n  rest'                   || 'Why?'                   | 'rest'
        'written with carriage returns'    | 'Why?\r\nBecause.'                   || 'Why?'                   | 'Because.'
    }

    def "questionFromLog should cut a long first line at its first sentence and keep the whole text as the detail"() {

        given:
        String text = 'Which charset do the 837 files use? ' + getReplicatedString('z', 300)
        int entry = newLogBy(this, sid, text, 'tony')

        when:
        Question q = repo.findQuestion(repo.questionFromLog(entry, 'tony'))

        then:
        q.text() == 'Which charset do the 837 files use?'
        q.detail() == text
    }

    def "questionFromLog should cut a long first line with no sentence at a word and mark the cut"() {

        given:
        String text = getReplicatedString('word ', 80).trim()
        int entry = newLogBy(this, sid, text, 'tony')

        when:
        Question q = repo.findQuestion(repo.questionFromLog(entry, 'tony'))

        then:
        q.text() == getReplicatedString('word ', 58) + 'word...'
        q.detail() == text
    }

    def "questionFromLog should refuse with the proc's sentence when the entry cannot become a question"() {

        given:
        ask(repo, sid, 'Already asked?')
        int repeat = newLogBy(this, sid, 'Already asked?', 'tony')
        int bare = newLogBy(this, sid, '**', 'tony')
        int before = count('SELECT COUNT(*) FROM dbo.SolutionQuestion WHERE SolutionID = ?', sid)

        expect:
        ruleOf { repo.questionFromLog(987654321, 'tony') }.message == 'That log entry no longer exists. Nothing was added.'
        ruleOf { repo.questionFromLog(bare, 'tony') }.message == 'That log entry has no text to make a question of. Nothing was added.'
        ruleOf { repo.questionFromLog(repeat, 'tony') }.message == 'That question is already open in this solution. Nothing was added.'
        count('SELECT COUNT(*) FROM dbo.SolutionQuestion WHERE SolutionID = ?', sid) == before
    }

    // ---- decisions ----

    def "saveDecision should record an accepted decision with its journal entry under the next sequence number and link the two"() {

        given:
        newLog(sid, 'First entry')
        newLog(sid, 'Second entry')
        int logsBefore = count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', sid)

        when:
        Saved saved = repo.saveDecision(new DecisionDraft(0, sid, "  Read files\r\nin as UTF-8 ", ' 837 and 835 disagreed. ', ' UTF-8; windows-1252 ',
            ' UTF-8 in. ', null, null, null), 'Tony')
        Decision d = repo.findDecision(saved.id())
        Map<String, Object> entry = row('SELECT l.SequenceNumber, l.LogText, t.LogTypeCode, m.MemberCode FROM dbo.SolutionLog l ' +
            'JOIN dbo.SolutionLogType t ON t.SolutionLogTypeID = l.SolutionLogTypeID ' +
            'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = l.AuthorMemberID WHERE l.SolutionLogID = ?', saved.logId())

        then:
        saved.id() > 0
        saved.logId() > 0
        d.title() == 'Read files  in as UTF-8'
        d.context() == '837 and 835 disagreed.'
        d.options() == 'UTF-8; windows-1252'
        d.outcome() == 'UTF-8 in.'
        !d.truncated()
        d.statusCode() == 'accepted'
        d.decidedDate() != null
        d.decidedByCode() == 'tony'
        d.logId() == saved.logId()
        d.logSequenceNumber() == 3
        d.supersededById() == null
        d.supersedesId() == null
        d.answeredCount() == 0
        d.linkCount() == 1
        d.modifiedBy() == 'tony'
        count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', sid) == logsBefore + 1
        entry.SequenceNumber == 3
        entry.LogTypeCode == 'decision'
        entry.MemberCode == 'tony'
        entry.LogText == '**Decided:** Read files  in as UTF-8\n\nUTF-8 in.'
        pairs(this, 'decision', saved.id(), 'log', saved.logId()) == 1
        logRows('SolutionDecision', 'SolutionDecisionID', saved.id())*.IsInsertion.first() == true
    }

    def "saveDecision should leave a proposal undated, say Proposed in the journal and keep a date it is given"() {

        given:
        LocalDateTime pinned = getLocalDateTime('10/23/2026 14:00')
        LocalDateTime planned = getLocalDateTime('11/10/2026 09:00')

        when:
        Saved proposal = decide(repo, sid, 'Maybe UTF-8', 'proposed')
        Saved dated = decide(repo, sid, 'Pinned date', 'accepted', null, pinned)
        Saved plannedProposal = decide(repo, sid, 'Proposed with a date', ' PROPOSED ', null, planned)

        then:
        repo.findDecision(proposal.id()).statusCode() == 'proposed'
        repo.findDecision(proposal.id()).decidedDate() == null
        logText(this, proposal.logId()) == '**Proposed:** Maybe UTF-8'
        repo.findDecision(dated.id()).decidedDate() == pinned
        logText(this, dated.logId()) == '**Decided:** Pinned date'
        repo.findDecision(plannedProposal.id()).statusCode() == 'proposed'
        repo.findDecision(plannedProposal.id()).decidedDate() == planned
    }

    def "saveDecision should merge an edit, stamp the date when it is accepted and never touch the journal"() {

        given:
        Saved s = repo.saveDecision(new DecisionDraft(0, sid, 'Maybe UTF-8', 'ctx', 'opts', 'out', 'proposed', null, null), 'rex')
        Object text = logText(this, s.logId())
        int logs = count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', sid)

        when:
        Saved kept = repo.saveDecision(new DecisionDraft(s.id(), 0, null, null, null, null, null, null, null), 'Tony')
        Decision k = repo.findDecision(s.id())

        then:
        kept == new Saved(s.id(), s.logId())
        k.title() == 'Maybe UTF-8'
        k.context() == 'ctx'
        k.options() == 'opts'
        k.outcome() == 'out'
        k.statusCode() == 'proposed'
        k.decidedDate() == null
        k.decidedByCode() == 'rex'
        k.modifiedBy() == 'tony'
        k.modifiedDate() != null

        when:
        repo.saveDecision(new DecisionDraft(s.id(), newSolution(), 'Use UTF-8', '', '', ' New outcome ', 'accepted', null, null), 'tony')
        Decision a = repo.findDecision(s.id())

        then:
        a.solutionId() == sid
        a.title() == 'Use UTF-8'
        a.context() == null
        a.options() == null
        a.outcome() == 'New outcome'
        a.statusCode() == 'accepted'
        a.decidedDate() != null
        count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', sid) == logs
        logText(this, s.logId()) == text
    }

    @Unroll
    def "#seq. saveDecision should refuse and write nothing when #scenario"() {

        given:
        int existing = decide(repo, sid, 'Existing decision').id()
        int foreign = decide(repo, newSolution(), 'Elsewhere').id()
        int decisionsBefore = count('SELECT COUNT(*) FROM dbo.SolutionDecision')
        int logsBefore = count('SELECT COUNT(*) FROM dbo.SolutionLog')
        int id = target == 'existing' ? existing : (target == 'missing' ? 987654321 : 0)
        int solution = target == 'missing-solution' ? -1 : sid
        Integer supersedes = supersedesTarget == 'foreign' ? foreign : null
        String titleText = titleLength > 0 ? getReplicatedString('t', titleLength) : title

        when:
        repo.saveDecision(new DecisionDraft(id, solution, titleText, null, null, null, status, null, supersedes), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionDecision') == decisionsBefore
        count('SELECT COUNT(*) FROM dbo.SolutionLog') == logsBefore
        repo.findDecision(existing).title() == 'Existing decision'
        repo.findDecision(existing).statusCode() == 'accepted'
        repo.findDecision(foreign).statusCode() == 'accepted'

        where:
        seq | scenario                                          | target             | title                  | titleLength | status       | supersedesTarget || expectedMessage
        1   | 'superseded is named as a status'                 | 'new'              | 'T'                    | 0           | 'superseded' | 'none'           || 'A decision is proposed or accepted. It is superseded only by recording a newer one. Nothing was saved.'
        2   | 'an unknown status is named'                      | 'new'              | 'T'                    | 0           | 'decided'    | 'none'           || 'A decision is proposed or accepted. It is superseded only by recording a newer one. Nothing was saved.'
        3   | 'an edit names superseded'                        | 'existing'         | null                   | 0           | 'superseded' | 'none'           || 'A decision is proposed or accepted. It is superseded only by recording a newer one. Nothing was saved.'
        4   | 'a new title is blank'                            | 'new'              | '  '                   | 0           | null         | 'none'           || 'A decision needs a title: what was decided, in a line. Nothing was added.'
        5   | 'a new title is missing'                          | 'new'              | null                   | 0           | null         | 'none'           || 'A decision needs a title: what was decided, in a line. Nothing was added.'
        6   | 'an edit blanks the title'                        | 'existing'         | ' '                    | 0           | null         | 'none'           || 'A decision title cannot be blank. Nothing was saved.'
        7   | 'the title is over 400 characters'                | 'new'              | null                   | 401         | null         | 'none'           || 'A decision title can be at most 400 characters. Put the rest in the outcome. Nothing was saved.'
        8   | 'the title repeats a current decision'            | 'new'              | ' Existing decision '  | 0           | null         | 'none'           || 'This solution already has that decision. Nothing was saved.'
        9   | 'the decision to edit no longer exists'           | 'missing'          | 'T'                    | 0           | null         | 'none'           || 'That decision no longer exists. Nothing was saved.'
        10  | 'the solution does not exist'                     | 'missing-solution' | 'T'                    | 0           | null         | 'none'           || 'That solution no longer exists. Nothing was added.'
        11  | 'the decision it replaces is in another solution' | 'new'              | 'T'                    | 0           | null         | 'foreign'        || 'The decision it replaces is not in this solution. Nothing was added.'
    }

    def "saveDecision should take a title of exactly 400 characters"() {

        when:
        Saved s = decide(repo, sid, getReplicatedString('t', 400))

        then:
        repo.findDecision(s.id()).title().length() == 400
    }

    def "saveDecision should supersede the named decision, point both ways, link the two and refuse a second supersede"() {

        given:
        Saved old = decide(repo, sid, 'Read as UTF-8', null, 'UTF-8 in.')

        when:
        Saved fresh = decide(repo, sid, 'Read as UTF-8', null, 'Everything, from 11/04.', null, old.id(), 'Rex')
        Decision o = repo.findDecision(old.id())
        Decision n = repo.findDecision(fresh.id())

        then:
        fresh.id() > old.id()
        fresh.logId() != old.logId()
        o.statusCode() == 'superseded'
        o.superseded()
        o.supersededById() == fresh.id()
        o.supersededByTitle() == 'Read as UTF-8'
        o.modifiedBy() == 'rex'
        n.statusCode() == 'accepted'
        n.supersedesId() == old.id()
        n.supersedesTitle() == 'Read as UTF-8'
        n.supersededById() == null
        n.linkCount() == 2
        pairs(this, 'decision', old.id(), 'decision', fresh.id()) == 1

        and: 'a replaced decision cannot be replaced again'
        ruleOf { decide(repo, sid, 'Again', null, null, null, old.id()) }.message == 'That decision was already replaced by a newer one. Nothing was added.'
        repo.findDecision(old.id()).supersededById() == fresh.id()
    }

    def "saveDecision should keep a superseded decision's status but let its text be edited"() {

        given:
        Saved old = decide(repo, sid, 'Read as 1252')
        decide(repo, sid, 'Read as UTF-8', null, null, null, old.id())

        expect:
        ruleOf { repo.saveDecision(new DecisionDraft(old.id(), 0, null, null, null, null, 'accepted', null, null), 'tony') }.message ==
            'A superseded decision keeps that status: record a new decision instead. Nothing was saved.'

        when:
        repo.saveDecision(new DecisionDraft(old.id(), 0, null, null, null, 'Kept for the record.', null, null, null), 'tony')

        then:
        repo.findDecision(old.id()).outcome() == 'Kept for the record.'
        repo.findDecision(old.id()).statusCode() == 'superseded'
    }

    def "findDecision should answer null when the id is unknown"() {

        expect:
        repo.findDecision(987654321) == null
        repo.findDecision(0) == null
    }

    def "decisions should list proposed first, then accepted, then superseded, newest decided first, and 'current' should leave out the replaced"() {

        given:
        int a1 = decide(repo, sid, 'Alpha', null, null, getLocalDateTime('10/01/2026 09:00')).id()
        int a2 = repo.saveDecision(new DecisionDraft(0, sid, 'Beta', 'context', null, getReplicatedString('o', 350), null,
            getLocalDateTime('10/20/2026 09:00'), null), 'tony').id()
        int old = decide(repo, sid, 'Gamma', null, 'short', getLocalDateTime('09/01/2026 09:00')).id()
        int fresh = decide(repo, sid, 'Gamma two', null, null, getLocalDateTime('10/25/2026 09:00'), old).id()
        int proposal = decide(repo, sid, 'Delta', 'proposed').id()
        decide(repo, newSolution(), 'Elsewhere')

        when:
        List<Decision> all = repo.decisions(sid, null, null, 0, 50)
        Decision beta = all.find { Decision d -> d.id() == a2 }
        Decision gamma = all.find { Decision d -> d.id() == old }

        then:
        all*.id() == [proposal, fresh, a2, a1, old]
        all.every { Decision d -> d.context() == null && d.options() == null }
        beta.outcome().length() == 300
        beta.truncated()
        !all.find { Decision d -> d.id() == a1 }.truncated()
        gamma.outcome() == 'short'
        gamma.truncated() == false
        gamma.supersededById() == fresh
        gamma.supersededByTitle() == 'Gamma two'
        all.find { Decision d -> d.id() == fresh }.supersedesId() == null
        repo.decisions(sid, 'current', null, 0, 50)*.id() == [proposal, fresh, a2, a1]
        repo.decisions(sid, 'superseded', null, 0, 50)*.id() == [old]
        repo.decisions(sid, null, null, 1, 2)*.id() == [fresh, a2]
        repo.decisions(sid, null, null, 0, 0).size() == 5
        repo.decisionCounts(sid, null, null) == [total: 5, all: 5, 'status:proposed': 1, 'status:accepted': 3, 'status:superseded': 1]
        repo.decisionCounts(sid, 'current', null) == [total: 4, all: 5, 'status:proposed': 1, 'status:accepted': 3, 'status:superseded': 1]
        repo.decisionCounts(sid, 'proposed', null).total == 1
        repo.decisionCounts(newSolution(), null, null) == [total: 0, all: 0]
    }

    def "decisions and decisionCounts should match the title, context, options or outcome with LIKE wildcards literal"() {

        given:
        repo.saveDecision(new DecisionDraft(0, sid, 'One', 'charset came up', null, null, null, null, null), 'tony')
        repo.saveDecision(new DecisionDraft(0, sid, 'Two', null, 'CHARSET or not', null, null, null, null), 'tony')
        repo.saveDecision(new DecisionDraft(0, sid, 'Three', null, null, 'the charset', null, null, null), 'tony')
        repo.saveDecision(new DecisionDraft(0, sid, 'Charset four', null, null, null, null, null, null), 'tony')
        decide(repo, sid, '100% sure')
        decide(repo, sid, '1000 sure')

        expect:
        repo.decisions(sid, null, 'charset', 0, 50)*.title() as Set == ['One', 'Two', 'Three', 'Charset four'] as Set
        repo.decisionCounts(sid, null, 'charset').total == 4
        repo.decisions(sid, null, '0%', 0, 50)*.title() == ['100% sure']
        repo.decisions(sid, null, '_0', 0, 50).isEmpty()
        repo.decisionCounts(sid, null, 'nothing matches') == [total: 0, all: 6]
    }

    def "deleteDecision should reopen what it answered, make what it replaced current again, drop its links and keep its journal entry"() {

        given:
        Saved old = decide(repo, sid, 'Read as 1252')
        Saved fresh = decide(repo, sid, 'Read as UTF-8', null, null, null, old.id())
        int q1 = ask(repo, sid, 'Which charset?')
        int q2 = ask(repo, sid, 'Which folder?')
        int item = newItem(project)
        repo.setQuestionStatus(q1, 'answered', fresh.id(), 'tony')
        repo.setQuestionStatus(q2, 'answered', fresh.id(), 'tony')
        links.link(sid, 'decision', fresh.id(), 'item', item, 'tony')

        when:
        boolean removed = repo.deleteDecision(fresh.id())

        then:
        removed
        repo.findDecision(fresh.id()) == null
        repo.findQuestion(q1).statusCode() == 'open'
        repo.findQuestion(q1).answeredByDecisionId() == null
        repo.findQuestion(q1).answeredDate() == null
        repo.findQuestion(q2).open()
        repo.findDecision(old.id()).statusCode() == 'accepted'
        repo.findDecision(old.id()).supersededById() == null
        linksOf(this, 'decision', fresh.id()) == 0
        linksOf(this, 'decision', old.id()) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionLogID = ?', fresh.logId()) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = ?', item) == 1
        logRows('SolutionDecision', 'SolutionDecisionID', fresh.id()).last().DeletedDate != null
        !repo.deleteDecision(fresh.id())
    }

    def "decisionFromLog should make an accepted decision of the entry, decided by its author on its date, and link the two"() {

        given:
        int entry = newLogBy(this, sid, 'Keep both remit folders until QA signs off.', 'rex', 'decision')
        Map<String, Object> entryRow = row('SELECT SequenceNumber, LogDate FROM dbo.SolutionLog WHERE SolutionLogID = ?', entry)

        when:
        int id = repo.decisionFromLog(entry, 'Tony')
        Decision d = repo.findDecision(id)

        then:
        d.title() == 'Keep both remit folders until QA signs off.'
        d.outcome() == null
        d.statusCode() == 'accepted'
        d.decidedDate() == SolutionRows.dateOf(entryRow, 'LogDate')
        d.decidedByCode() == 'rex'
        d.logId() == entry
        d.logSequenceNumber() == ((Number) entryRow.SequenceNumber).intValue()
        d.modifiedBy() == 'tony'
        d.linkCount() == 1
        pairs(this, 'decision', id, 'log', entry) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', sid) == 1

        and: 'a second promote of the same entry is refused'
        ruleOf { repo.decisionFromLog(entry, 'tony') }.message == 'That log entry is already a decision. Nothing was added.'
    }

    @Unroll
    def "decisionFromLog should make the decision '#expectedTitle' when the entry is #scenario"() {

        given:
        int entry = newLogBy(this, sid, text, 'tony')

        when:
        Decision d = repo.findDecision(repo.decisionFromLog(entry, 'tony'))

        then:
        d.title() == expectedTitle
        d.outcome() == expectedOutcome

        where:
        scenario                           | text                                      || expectedTitle          | expectedOutcome
        'more than one line'               | 'Keep both folders.\nUntil QA signs off.' || 'Keep both folders.'   | 'Keep both folders.\nUntil QA signs off.'
        'bold after blank lines'           | '\n**Decided:** keep both\nbecause'       || 'Decided: keep both'   | '**Decided:** keep both\nbecause'
        'written with carriage returns'    | 'One\r\nTwo'                              || 'One'                  | 'One\nTwo'
    }

    def "decisionFromLog should cut a long first line at its first sentence and keep the whole text as the outcome"() {

        given:
        String text = 'Read every file in as UTF-8. ' + getReplicatedString('y', 250)
        int entry = newLogBy(this, sid, text, 'tony')

        when:
        Decision d = repo.findDecision(repo.decisionFromLog(entry, 'tony'))

        then:
        d.title() == 'Read every file in as UTF-8.'
        d.outcome() == text
    }

    def "decisionFromLog should cut a long first line with no sentence at a word and mark the cut"() {

        given:
        String text = getReplicatedString('word ', 60).trim()
        int entry = newLogBy(this, sid, text, 'tony')

        when:
        Decision d = repo.findDecision(repo.decisionFromLog(entry, 'tony'))

        then:
        d.title() == getReplicatedString('word ', 38) + 'word...'
        d.outcome() == text
    }

    def "decisionFromLog should refuse with the proc's sentence when the entry cannot become a decision"() {

        given:
        int bare = newLogBy(this, sid, '**', 'tony')

        expect:
        ruleOf { repo.decisionFromLog(987654321, 'tony') }.message == 'That log entry no longer exists. Nothing was added.'
        ruleOf { repo.decisionFromLog(bare, 'tony') }.message == 'That log entry has no text to make a decision of. Nothing was added.'
        count('SELECT COUNT(*) FROM dbo.SolutionDecision WHERE SolutionID = ?', sid) == 0
    }

    // ---- what a decision could close ----

    def "openLinks should answer the linked questions still open and the linked items not closed, questions first, by title"() {

        given:
        int d = decide(repo, sid, 'Read as UTF-8').id()
        int zeta = ask(repo, sid, 'Zeta open?')
        int answered = ask(repo, sid, 'Answered?')
        int alpha = ask(repo, sid, 'Alpha open?')
        repo.setQuestionStatus(answered, 'answered', null, 'tony')
        int todo = newItem(project, 'Review charset findings')
        int done = newItem(project, 'Already done', null, 'done')
        int doing = newItem(project, 'Decide 837/835 charset', null, 'inprogress')
        [zeta, answered, alpha].each { int q -> links.link(sid, 'decision', d, 'question', q, 'tony') }
        [todo, done, doing].each { int i -> links.link(sid, 'item', i, 'decision', d, 'tony') }
        links.link(sid, 'decision', d, 'file', newFile(sid, 'charset.txt'), 'tony')
        ask(repo, sid, 'Not linked?')

        when:
        List<OpenLink> got = repo.openLinks(d)

        then:
        got == [new OpenLink('question', alpha, 'Alpha open?', 'Question'),
                new OpenLink('question', zeta, 'Zeta open?', 'Question'),
                new OpenLink('item', doing, 'Decide 837/835 charset', projectName + ' | In progress'),
                new OpenLink('item', todo, 'Review charset findings', projectName + ' | Not started')]
    }

    def "openLinks should answer nothing when the decision is unknown or has no open links"() {

        given:
        int d = decide(repo, sid, 'Read as UTF-8').id()

        expect:
        repo.openLinks(987654321) == []
        repo.openLinks(d) == []
    }

    // ---- the age of "Where things stand" ----

    def "summaryAge should answer no date and no age when the solution has no summary or does not exist"() {

        expect:
        orientation.summaryAge(sid) == new SummaryAge(null, null, false)
        orientation.summaryAge(987654321) == new SummaryAge(null, null, false)
    }

    def "summaryAge should answer today when the summary was just written"() {

        when:
        saveSummary(this, sid, solutionName, 'The app runs as a service.')
        SummaryAge a = orientation.summaryAge(sid)

        then:
        a.ageDays() == 0
        !a.stale()
        a.writtenDate() != null
        !a.writtenDate().isBefore(LocalDateTime.now().minusMinutes(10))
    }

    def "summaryAge should keep the written date when a later save does not change the summary, and reset it when it does"() {

        given:
        saveSummary(this, sid, solutionName, 'Runs as a service.')
        sql.update('UPDATE dbo.Solution_LOG SET LogCreatedDate = DATEADD(DAY, -20, LogCreatedDate) WHERE SolutionID = ?', sid)
        sql.update('UPDATE dbo.Solution_LOG SET LogCreatedDate = DATEADD(DAY, -1, LogCreatedDate) WHERE SolutionID = ? AND IsInsertion = 1', sid)

        when:
        saveSummary(this, sid, solutionName, '  Runs as a service.  ', 'A new description')
        SummaryAge same = orientation.summaryAge(sid)

        then:
        same.ageDays() == 20
        same.stale()

        when:
        saveSummary(this, sid, solutionName, 'Runs as a service, and has since 10/12.')
        SummaryAge changed = orientation.summaryAge(sid)

        then:
        changed.ageDays() == 0
        !changed.stale()
    }

    def "summaryAge should date a summary that arrived with a copied solution no newer than the solution's own last save"() {

        given:
        int copied = idOf("EXEC dbo.stp_Solution_Save 0, ?, 'desc', 'todo', 'tony', ?, NULL, 0, 0, 'felix', NULL", 'IT copied ' + uniq(), 'Written long ago.')
        sql.update('UPDATE dbo.Solution SET CreatedDate = DATEADD(DAY, -30, CreatedDate), ModifiedDate = DATEADD(DAY, -30, GETDATE()) WHERE SolutionID = ?', copied)

        when:
        SummaryAge a = orientation.summaryAge(copied)

        then:
        a.ageDays() == 30
        a.stale()
    }

    def "summaryAge should count only the history after the solution id was last deleted"() {

        given:
        saveSummary(this, sid, solutionName, 'Old summary.')
        sql.update('UPDATE dbo.Solution_LOG SET LogCreatedDate = DATEADD(DAY, -40, LogCreatedDate) WHERE SolutionID = ?', sid)
        sql.update('UPDATE dbo.Solution_LOG SET LogCreatedDate = DATEADD(DAY, -1, LogCreatedDate) WHERE SolutionID = ? AND IsInsertion = 1', sid)
        Integer before = orientation.summaryAge(sid).ageDays()
        sql.update("INSERT INTO dbo.Solution_LOG (LogCreatedDate, IsInsertion, DeletedDate, SolutionID, SolutionName, StatusSummary) " +
            "VALUES (DATEADD(DAY, -10, GETDATE()), 0, DATEADD(DAY, -10, GETDATE()), ?, 'An earlier solution with this id', 'Old summary.')", sid)

        when:
        SummaryAge after = orientation.summaryAge(sid)

        then:
        before == 40
        after.ageDays() == 0
    }

    // ---- links, the linkable view and the activity feed know the two new kinds ----

    def "stp_SolutionLink_Save should link questions and decisions to each other and to the other kinds in fixed order"() {

        given:
        int item = newItem(project, 'Upgrade the driver')
        int q = ask(repo, sid, 'Which charset?')
        int d1 = decide(repo, sid, 'Read as UTF-8').id()
        int d2 = decide(repo, sid, 'Pin the driver').id()
        int topic = links.saveTopic(new TopicDraft(0, sid, 'Charset', null), 'tony')

        when:
        int itemQuestion = links.link(sid, 'question', q, 'item', item, 'tony')
        int questionDecision = links.link(sid, ' QUESTION ', q, 'Decision', d1, 'tony')
        int twoDecisions = links.link(sid, 'decision', d2, 'decision', d1, 'tony')
        int topicDecision = links.link(sid, 'topic', topic, 'decision', d1, 'tony')

        then:
        links.findLink(itemQuestion) == new StoredLink(itemQuestion, sid, 'item', item, 'question', q)
        links.findLink(questionDecision) == new StoredLink(questionDecision, sid, 'decision', d1, 'question', q)
        links.findLink(twoDecisions) == new StoredLink(twoDecisions, sid, 'decision', d1, 'decision', d2)
        links.findLink(topicDecision) == new StoredLink(topicDecision, sid, 'decision', d1, 'topic', topic)
        repo.findQuestion(q).linkCount() == 2
        repo.findDecision(d1).linkCount() == 4

        and: 'the pair the other way round is one link'
        ruleOf { links.link(sid, 'decision', d1, 'question', q, 'rex') }.message == 'Those two are already linked. Nothing was saved.'
    }

    def "stp_SolutionLink_Save should refuse a question or decision of another solution"() {

        given:
        int item = newItem(project)
        int other = newSolution()
        int foreignQuestion = ask(repo, other, 'Theirs?')
        int foreignDecision = decide(repo, other, 'Theirs').id()

        expect:
        ruleOf { links.link(sid, 'item', item, 'question', foreignQuestion, 'tony') }.message ==
            'The record to link to is not in this solution. Nothing was saved.'
        ruleOf { links.link(sid, 'decision', foreignDecision, 'item', item, 'tony') }.message ==
            'The record to link from is not in this solution. Nothing was saved.'
        linksOf(this, 'item', item) == 0
    }

    def "stp_SolutionLink_ListForRecord should list topics, then decisions, then questions, then the rest, and mark the closed ones"() {

        given:
        int item = newItem(project, 'Upgrade the driver')
        int note = idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', ?, 'Body', NULL, 'tony', 0", sid, 'Runbook')
        int q = ask(repo, sid, 'Which charset?')
        Saved old = decide(repo, sid, 'Read as 1252')
        decide(repo, sid, 'Read as UTF-8', null, null, null, old.id())
        int topic = links.saveTopic(new TopicDraft(0, sid, 'Charset', null), 'tony')
        links.link(sid, 'item', item, 'note', note, 'tony')
        links.link(sid, 'item', item, 'question', q, 'tony')
        links.link(sid, 'item', item, 'decision', old.id(), 'tony')
        links.link(sid, 'item', item, 'topic', topic, 'tony')
        repo.setQuestionStatus(q, 'dropped', null, 'tony')

        when:
        List<Link> got = links.links(sid, 'item', item, 0, 50).links()

        then:
        got*.kind() == ['topic', 'decision', 'question', 'note']
        got*.id() == [topic, old.id(), q, note]
        got.find { Link l -> l.kind() == 'question' }.closed()
        got.find { Link l -> l.kind() == 'question' }.sub() == 'Dropped'
        got.find { Link l -> l.kind() == 'decision' }.closed()
        !got.find { Link l -> l.kind() == 'note' }.closed()
    }

    def "vw_SolutionLinkable should list questions and decisions with their second line, closed once answered, dropped or replaced"() {

        given:
        int open = ask(repo, sid, 'Open?')
        int answered = ask(repo, sid, 'Answered?')
        int dropped = ask(repo, sid, 'Dropped?')
        repo.setQuestionStatus(answered, 'answered', null, 'tony')
        repo.setQuestionStatus(dropped, 'dropped', null, 'tony')
        int accepted = decide(repo, sid, 'Accepted', null, null, getLocalDateTime('10/23/2026 09:00')).id()
        int proposed = decide(repo, sid, 'Proposed', 'proposed').id()
        int replaced = decide(repo, sid, 'Replaced', null, null, getLocalDateTime('10/01/2026 09:00')).id()
        int replacer = decide(repo, sid, 'Replacer', null, null, getLocalDateTime('10/24/2026 09:00'), replaced).id()

        when:
        Map<String, Map<String, Object>> v = rows("SELECT Kind, RecordID, Title, Sub, IsClosed FROM dbo.vw_SolutionLinkable " +
            "WHERE SolutionID = ? AND Kind IN ('question', 'decision')", sid)
            .collectEntries { Map<String, Object> r -> [(r.Kind + ':' + r.RecordID): r] }

        then:
        v.size() == 7
        v['question:' + open].Title == 'Open?'
        v['question:' + open].Sub == 'Open question'
        v['question:' + open].IsClosed == false
        v['question:' + answered].Sub == 'Answered'
        v['question:' + answered].IsClosed == true
        v['question:' + dropped].Sub == 'Dropped'
        v['question:' + dropped].IsClosed == true
        v['decision:' + accepted].Title == 'Accepted'
        v['decision:' + accepted].Sub == 'Decided 10/23/2026'
        v['decision:' + accepted].IsClosed == false
        v['decision:' + proposed].Sub == 'Proposed'
        v['decision:' + proposed].IsClosed == false
        v['decision:' + replaced].Sub == 'Replaced 10/01/2026'
        v['decision:' + replaced].IsClosed == true
        v['decision:' + replacer].IsClosed == false
    }

    def "fn_SolutionActivity should show another member's questions as asked and decisions as decided"() {

        given:
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        int q = ask(repo, sid, 'Which charset?', null, null, 'rex')
        int d = decide(repo, sid, 'Read as UTF-8', null, 'UTF-8 in.', null, null, 'rex').id()

        when:
        List<Change> got = links.changes(sid, since, 'tony', 0, 50)
        Change asked = got.find { Change c -> c.kind() == 'question' }
        Change decided = got.find { Change c -> c.kind() == 'decision' }
        Map<String, Integer> counts = links.changeCounts(sid, since, 'tony')

        then:
        asked.id() == q
        asked.title() == 'Which charset?'
        asked.sub() == 'Open question'
        asked.action() == 'asked'
        asked.who() == 'rex'
        decided.id() == d
        decided.title() == 'Read as UTF-8'
        decided.sub() == 'UTF-8 in.'
        decided.action() == 'decided'
        decided.who() == 'rex'
        got.any { Change c -> c.kind() == 'log' }
        counts['kind:question'] == 1
        counts['kind:decision'] == 1
        links.changes(sid, since, 'REX', 0, 50).findAll { Change c -> c.kind() in ['question', 'decision'] }.isEmpty()
    }

    def "fn_SolutionActivity should show a question asked before since and edited after it as changed"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        sql.update('UPDATE dbo.SolutionQuestion SET CreatedDate = DATEADD(DAY, -3, GETDATE()) WHERE SolutionQuestionID = ?', q)
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        repo.setQuestionStatus(q, 'dropped', null, 'rex')

        when:
        Change c = links.changes(sid, since, 'tony', 0, 50).find { Change x -> x.kind() == 'question' }

        then:
        c.id() == q
        c.action() == 'changed'
        c.sub() == 'Dropped'
    }

    // ---- the delete procs clean up after them ----

    def "stp_Solution_Delete should remove the solution's questions and decisions and their links but no other solution's"() {

        given:
        int q = ask(repo, sid, 'Which charset?')
        Saved d = decide(repo, sid, 'Read as UTF-8')
        links.link(sid, 'decision', d.id(), 'question', q, 'tony')
        int other = newSolution()
        int keptQuestion = ask(repo, other, 'Theirs?')
        int keptDecision = decide(repo, other, 'Theirs').id()

        when:
        row('EXEC dbo.stp_Solution_Delete ?', sid)

        then:
        count('SELECT COUNT(*) FROM dbo.SolutionQuestion WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionDecision WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ?', sid) == 0
        logRows('SolutionQuestion', 'SolutionQuestionID', q).last().DeletedDate != null
        logRows('SolutionDecision', 'SolutionDecisionID', d.id()).last().DeletedDate != null
        repo.findQuestion(keptQuestion) != null
        repo.findDecision(keptDecision) != null
        repo.findDecision(keptDecision).linkCount() == 1
    }

    def "stp_SolutionLog_Delete should keep a decision or question made from the entry and only drop its pointer and links"() {

        given:
        Saved d = decide(repo, sid, 'Read as UTF-8')
        int entry = newLogBy(this, sid, 'Why keep it?', 'rex')
        int q = repo.questionFromLog(entry, 'tony')

        when:
        row('EXEC dbo.stp_SolutionLog_Delete ?', d.logId())
        row('EXEC dbo.stp_SolutionLog_Delete ?', entry)
        Decision kept = repo.findDecision(d.id())
        Question keptQuestion = repo.findQuestion(q)

        then:
        kept != null
        kept.logId() == null
        kept.logSequenceNumber() == null
        kept.linkCount() == 0
        keptQuestion != null
        keptQuestion.logId() == null
        keptQuestion.linkCount() == 0
        keptQuestion.text() == 'Why keep it?'
        linksOf(this, 'log', entry) == 0
        linksOf(this, 'log', d.logId()) == 0
    }
}
