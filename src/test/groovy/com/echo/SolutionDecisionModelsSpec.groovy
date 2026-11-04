package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionDecisionModels.Decision
import com.echo.SolutionDecisionModels.DecisionDraft
import com.echo.SolutionDecisionModels.DecisionPage
import com.echo.SolutionDecisionModels.LinkRef
import com.echo.SolutionDecisionModels.OpenLink
import com.echo.SolutionDecisionModels.Question
import com.echo.SolutionDecisionModels.QuestionDraft
import com.echo.SolutionDecisionModels.QuestionPage
import com.echo.SolutionDecisionModels.Saved
import com.echo.SolutionDecisionModels.Settled

import spock.lang.Unroll

/**
 * Story 5695: the shapes of a solution's questions and decisions -- the row mappers (Question, Decision,
 * Saved, OpenLink) for a full row and an empty one, the optional ids that stay null, open() and
 * superseded(), the two status sets, and the page-size rule both lists share.
 */
class SolutionDecisionModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('11/04/2026 09:15')
    static final LocalDateTime U = getLocalDateTime('11/04/2026 10:30')
    static final LocalDateTime V = getLocalDateTime('10/23/2026 14:00')

    /** A row's optional id column in the form a test names: a number, zero, negative, text or missing. */
    private static Object idValue(String form) {
        switch (form) {
            case 'number':   return 31
            case 'zero':     return 0
            case 'negative': return -4
            case 'text':     return '31'
            default:         return null
        }
    }

    // ---- the status sets ----

    def "QUESTION_STATUSES and DECISION_STATUSES should hold exactly the statuses the procs allow"() {

        expect:
        SolutionDecisionModels.QUESTION_STATUSES == ['open', 'answered', 'dropped'] as Set
        SolutionDecisionModels.DECISION_STATUSES == ['proposed', 'accepted', 'superseded'] as Set
        !SolutionDecisionModels.DECISION_STATUSES.contains('current')
        !SolutionDecisionModels.QUESTION_STATUSES.contains('Open')
    }

    // ---- Question ----

    def "Question.of should read every column when the row is complete"() {

        when:
        Question q = Question.of([SolutionQuestionID: 5, SolutionID: 1, QuestionText: 'Which charset?', QuestionDetail: 'Remit says 1252.',
                                  Truncated: 1, StatusCode: 'answered', AskedByCode: 'rex', AskedByName: 'Rex', OwnerCode: 'tony',
                                  OwnerName: 'Tony', AnsweredBySolutionDecisionID: 20, AnsweredByTitle: 'Read as UTF-8',
                                  AnsweredDate: Timestamp.valueOf(V), SolutionLogID: 410, LogSequenceNumber: 42, LinkCount: 3,
                                  ModifiedBy: 'tony', CreatedDate: T, ModifiedDate: '2026-11-04 10:30:00'])

        then:
        q == new Question(5, 1, 'Which charset?', 'Remit says 1252.', true, 'answered', 'rex', 'Rex', 'tony', 'Tony',
                          20, 'Read as UTF-8', V, 410, 42, 3, 'tony', T, U)
    }

    def "Question.of should answer zeros, nulls and false when the row is empty"() {

        when:
        Question q = Question.of([:])

        then:
        q == new Question(0, 0, null, null, false, null, null, null, null, null, null, null, null, null, null, 0, null, null, null)
        !q.open()
    }

    @Unroll
    def "Question.of should read the answering decision, journal entry and sequence as #expected when the column is #scenario"() {

        given:
        Object v = idValue(form)

        when:
        Question q = Question.of([AnsweredBySolutionDecisionID: v, SolutionLogID: v, LogSequenceNumber: v])

        then:
        q.answeredByDecisionId() == expected
        q.logId() == expected
        q.logSequenceNumber() == expected

        where:
        scenario          | form       || expected
        'a number'        | 'number'   || 31
        'a number as text' | 'text'    || 31
        'zero'            | 'zero'     || null
        'negative'        | 'negative' || null
        'missing'         | 'missing'  || null
    }

    @Unroll
    def "Question.open should answer #expected when the status is #scenario"() {

        expect:
        Question.of([StatusCode: status]).open() == expected

        where:
        scenario                | status     || expected
        'open'                  | 'open'     || true
        'answered'              | 'answered' || false
        'dropped'               | 'dropped'  || false
        'open in capitals'      | 'OPEN'     || false
        'missing'               | null       || false
    }

    // ---- Decision ----

    def "Decision.of should read every column when the row is complete"() {

        when:
        Decision d = Decision.of([SolutionDecisionID: 20, SolutionID: 1, DecisionTitle: 'Read as UTF-8', Context: '837 and 835 disagreed.',
                                  OptionsText: 'UTF-8; windows-1252', Outcome: 'UTF-8 in.', Truncated: true, StatusCode: 'superseded',
                                  DecidedDate: V, DecidedByCode: 'tony', DecidedByName: 'Tony', SolutionLogID: 410, LogSequenceNumber: 42,
                                  SupersededBySolutionDecisionID: 21, SupersededByTitle: 'Read everything as UTF-8',
                                  SupersedesSolutionDecisionID: 19, SupersedesTitle: 'Read as windows-1252',
                                  AnsweredCount: 2, LinkCount: 4, ModifiedBy: 'rex', CreatedDate: Timestamp.valueOf(T), ModifiedDate: U])

        then:
        d == new Decision(20, 1, 'Read as UTF-8', '837 and 835 disagreed.', 'UTF-8; windows-1252', 'UTF-8 in.', true, 'superseded',
                          V, 'tony', 'Tony', 410, 42, 21, 'Read everything as UTF-8', 19, 'Read as windows-1252', 2, 4, 'rex', T, U)
    }

    def "Decision.of should answer zeros, nulls and false when the row is empty"() {

        when:
        Decision d = Decision.of([:])

        then:
        d == new Decision(0, 0, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null, 0, 0, null, null, null)
        !d.superseded()
    }

    @Unroll
    def "Decision.of should read the journal entry, sequence and both supersede pointers as #expected when the column is #scenario"() {

        given:
        Object v = idValue(form)

        when:
        Decision d = Decision.of([SolutionLogID: v, LogSequenceNumber: v, SupersededBySolutionDecisionID: v, SupersedesSolutionDecisionID: v])

        then:
        d.logId() == expected
        d.logSequenceNumber() == expected
        d.supersededById() == expected
        d.supersedesId() == expected

        where:
        scenario          | form       || expected
        'a number'        | 'number'   || 31
        'zero'            | 'zero'     || null
        'negative'        | 'negative' || null
        'missing'         | 'missing'  || null
    }

    @Unroll
    def "Decision.superseded should answer #expected when the status is #scenario"() {

        expect:
        Decision.of([StatusCode: status]).superseded() == expected

        where:
        scenario                  | status       || expected
        'superseded'              | 'superseded' || true
        'accepted'                | 'accepted'   || false
        'proposed'                | 'proposed'   || false
        'superseded in capitals'  | 'Superseded' || false
        'missing'                 | null         || false
    }

    // ---- Saved ----

    @Unroll
    def "Saved.of should answer id #expectedId and journal entry #expectedLogId when #scenario"() {

        given:
        Map<String, Object> r = hasRow ? [(column): idText == null ? null : Integer.valueOf(idText), SolutionLogID: logId] : null

        when:
        Saved s = Saved.of(r, 'SolutionDecisionID')

        then:
        s == new Saved(expectedId, expectedLogId)

        where:
        scenario                                  | hasRow | column               | idText | logId || expectedId | expectedLogId
        'there is no row'                         | false  | 'SolutionDecisionID' | null   | null  || 0          | null
        'the row names the decision and its entry' | true  | 'SolutionDecisionID' | '30'   | 410   || 30         | 410
        'the entry is null'                       | true   | 'SolutionDecisionID' | '30'   | null  || 30         | null
        'the entry is zero'                       | true   | 'SolutionDecisionID' | '30'   | 0     || 30         | null
        'the id is in another column'             | true   | 'SolutionQuestionID' | '30'   | 410   || 0          | 410
    }

    def "Saved.of should read the id from whichever column it is told when the row is a question's"() {

        expect:
        Saved.of([SolutionQuestionID: 5], 'SolutionQuestionID') == new Saved(5, null)
    }

    // ---- OpenLink ----

    def "OpenLink.of should read the kind, record, title and second line when the row is complete"() {

        expect:
        OpenLink.of([Kind: 'item', RecordID: 100, Title: 'Decide 837/835 charset', Sub: 'Deploy | Not started']) ==
            new OpenLink('item', 100, 'Decide 837/835 charset', 'Deploy | Not started')
    }

    def "OpenLink.of should answer a zero id and nulls when the row is empty"() {

        expect:
        OpenLink.of([:]) == new OpenLink(null, 0, null, null)
    }

    // ---- page ----

    @Unroll
    def "#seq. page should answer #expected when #scenario"() {

        expect:
        SolutionDecisionModels.page(pageSize, max) == expected

        where:
        seq | scenario                         | pageSize    | max || expected
        1   | 'nothing is asked'               | 0           | 500 || 50
        2   | 'a negative is asked'            | -1          | 500 || 50
        3   | 'the smallest int is asked'      | -2147483648 | 500 || 50
        4   | 'one row is asked'               | 1           | 500 || 1
        5   | 'the default is asked'           | 50          | 500 || 50
        6   | 'the cap is asked'               | 500         | 500 || 500
        7   | 'one over the cap is asked'      | 501         | 500 || 500
        8   | 'far over the cap is asked'      | 100000      | 500 || 500
        9   | 'a smaller cap applies'          | 75          | 30  || 30
    }

    // ---- the plain records ----

    def "the plain records should keep what they are given when built"() {

        given:
        Decision d = Decision.of([SolutionDecisionID: 20, StatusCode: 'accepted'])
        OpenLink q = new OpenLink('question', 5, 'Q5', 'Question')

        expect:
        new QuestionDraft(0, 1, 'Why?', null, '').ownerCode() == ''
        new DecisionDraft(0, 1, 'T', null, null, null, null, V, 19).supersedesId() == 19
        new DecisionDraft(0, 1, 'T', null, null, null, null, V, 19).decidedDate() == V
        new QuestionPage([], [total: 3], 10, 20, 3).total() == 3
        new DecisionPage([], [all: 2], 0, 50, 0).counts() == [all: 2]
        new LinkRef('item', null).id() == null
        new Settled(d, [q], [], 410, 42).closed() == [q]
        new Settled(d, [], [q], null, null).logId() == null
    }
}
