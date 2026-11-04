package com.echo

import com.echo.SolutionChangeModels.BulkResult
import com.echo.SolutionChangeModels.Promoted
import com.echo.SolutionChangeModels.Provenance

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): the business rules of changing many items at once and of making one record into
 * another. The solution is checked first, so a hidden or missing solution reads and writes nothing whatever the
 * request says. A bulk change names one known field (any case), at least one and at most 500 distinct positive
 * item ids (repeats, zeros, negatives and nulls dropped, the order kept), a reason of at most 1,000 characters
 * and a value of at most 400, all trimmed. A promotion names a known source and target kind and a source id; a
 * blank title is "use the source's first line", a title over 400 is refused, and a project id of zero or less
 * is none. Provenance names a known kind and a record id. Every write is attributed to the caller's own code.
 */
class SolutionChangeServiceSpec extends SolutionServiceSupport {

    static final String FIELD_LIST = 'field must be one of status, project, assignee, due, priority, topic.'
    static final String SOURCE_LIST = 'sourceKind must be one of message, log, note, item.'

    SolutionChangeRepository repo = Mock()
    SolutionChangeService service = new SolutionChangeService(repo, access)

    /** "3,1,NULL" to [3, 1, null]; "" to an empty list; null stays null. */
    private static List<Integer> idsOf(String csv) {
        if (csv == null) {
            return null
        }
        if (csv.isEmpty()) {
            return []
        }
        return csv.split(',').collect { String s -> s == 'NULL' ? null : Integer.valueOf(s.trim()) }
    }

    /** Runs one named call with otherwise good arguments -- the calls a data table can name. */
    private static Object call(SolutionChangeService s, String action, SolutionCaller caller, int solutionId) {
        switch (action) {
            case 'bulkChange': return s.bulkChange(caller, solutionId, [1], 'status', 'done', 'The 837 path is dead.')
            case 'promote':    return s.promote(caller, solutionId, 'message', 9012, 'item', 41, null)
            case 'provenance': return s.provenance(caller, solutionId, 'item', 1200)
            default: throw new IllegalStateException(action)
        }
    }

    // ---- access first ----

    @Unroll
    def "#seq. #action should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        call(service, action, caller, solutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        seq | scenario                                  | action       | callerName | solutionId || expectedMessage
        1   | 'the client was given nothing'            | 'bulkChange' | 'ZED'      | 1          || 'Solution not found.'
        2   | 'the client was not given the solution'   | 'bulkChange' | 'REX'      | 2          || 'Solution not found.'
        3   | 'the solution does not exist'             | 'bulkChange' | 'TONY'     | 9          || 'Solution not found.'
        4   | 'the solution id is zero'                 | 'bulkChange' | 'HOUSE'    | 0          || 'Solution not found.'
        5   | 'the client was given nothing'            | 'promote'    | 'ZED'      | 1          || 'Solution not found.'
        6   | 'the client was not given the solution'   | 'promote'    | 'REX'      | 2          || 'Solution not found.'
        7   | 'the solution does not exist'             | 'promote'    | 'TONY'     | 9          || 'Solution not found.'
        8   | 'the solution id is zero'                 | 'promote'    | 'HOUSE'    | 0          || 'Solution not found.'
        9   | 'the client was given nothing'            | 'provenance' | 'ZED'      | 1          || 'Solution not found.'
        10  | 'the client was not given the solution'   | 'provenance' | 'REX'      | 2          || 'Solution not found.'
        11  | 'the solution does not exist'             | 'provenance' | 'TONY'     | 9          || 'Solution not found.'
        12  | 'the solution id is negative'             | 'provenance' | 'HOUSE'    | -1         || 'Solution not found.'
    }

    def "bulkChange should answer not found before it looks at a bad request when the solution is hidden"() {

        when:
        service.bulkChange(REX, 2, [], 'colour', null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo._
    }

    def "promote should answer not found before it looks at a bad request when the solution is hidden"() {

        when:
        service.promote(ZED, 1, 'decision', null, 'log', null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo._
    }

    def "provenance should answer not found before it looks at a bad request when the solution is hidden"() {

        when:
        service.provenance(REX, 2, 'decision', null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo._
    }

    // ---- bulk change: the field ----

    @Unroll
    def "bulkChange should pass the field as '#expectedField' when it is sent as #scenario"() {

        when:
        service.bulkChange(TONY, 1, [5], field, 'x', 'why')

        then:
        1 * repo.bulkChange(1, '5', expectedField, 'x', 'why', 'tony') >> new BulkResult(1, 1)

        where:
        scenario            | field         || expectedField
        'status'            | 'status'      || 'status'
        'upper case'        | 'STATUS'      || 'status'
        'padded'            | ' project '   || 'project'
        'assignee'          | 'assignee'    || 'assignee'
        'mixed case due'    | 'Due'         || 'due'
        'priority'          | 'priority'    || 'priority'
        'topic'             | 'topic'       || 'topic'
    }

    @Unroll
    def "bulkChange should refuse with the field list and write nothing when the field is #scenario"() {

        when:
        service.bulkChange(TONY, 1, [5], field, 'x', 'why')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                 | field       || expectedMessage
        'null'                   | null        || 'field must be one of status, project, assignee, due, priority, topic.'
        'empty'                  | ''          || 'field must be one of status, project, assignee, due, priority, topic.'
        'blank'                  | '   '       || 'field must be one of status, project, assignee, due, priority, topic.'
        'a field it cannot set'  | 'title'     || 'field must be one of status, project, assignee, due, priority, topic.'
        'a near miss'            | 'statuses'  || 'field must be one of status, project, assignee, due, priority, topic.'
    }

    def "bulkChange should check the field before the items when both are wrong"() {

        when:
        service.bulkChange(TONY, 1, [], 'colour', 'x', '')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == FIELD_LIST
        0 * repo._
    }

    // ---- bulk change: the items ----

    @Unroll
    def "bulkChange should pass the items as '#expectedCsv' when #scenario"() {

        when:
        service.bulkChange(HOUSE, 1, idsOf(ids), 'priority', '2', 'why')

        then:
        1 * repo.bulkChange(1, expectedCsv, 'priority', '2', 'why', 'felix') >> new BulkResult(3, 2)

        where:
        scenario                                    | ids                      || expectedCsv
        'one item is named'                         | '7'                      || '7'
        'the order is kept'                         | '3,1,2'                  || '3,1,2'
        'a repeat is dropped where it repeats'      | '3,1,3,2'                || '3,1,2'
        'zero, negatives and nulls are dropped'     | '5,0,-1,NULL,5,7'        || '5,7'
    }

    @Unroll
    def "bulkChange should refuse 'Choose the items to change.' and write nothing when #scenario"() {

        when:
        service.bulkChange(TONY, 1, idsOf(ids), 'status', 'done', 'why')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                              | ids          || expectedMessage
        'the list is null'                    | null         || 'Choose the items to change.'
        'the list is empty'                   | ''           || 'Choose the items to change.'
        'every id is zero, negative or null'  | '0,-3,NULL'  || 'Choose the items to change.'
    }

    @Unroll
    def "bulkChange should pass #expectedCount distinct items on when #scenario"() {

        given:
        List<Integer> ids = (1..count).collect { int i -> i }
        if (repeatFirst) {
            ids << 1
        }

        when:
        service.bulkChange(TONY, 1, ids, 'status', 'done', 'why')

        then:
        1 * repo.bulkChange(1, { String csv -> csv.split(',').length == expectedCount }, 'status', 'done', 'why', 'tony') >> new BulkResult(1, count)

        where:
        scenario                                        | count | repeatFirst || expectedCount
        '500 items are named'                           | 500   | false       || 500
        '500 items and one repeat are named'            | 500   | true        || 500
    }

    def "bulkChange should refuse 501 distinct items and write nothing"() {

        given:
        List<Integer> ids = (1..501).collect { int i -> i }

        when:
        service.bulkChange(TONY, 1, ids, 'status', 'done', 'why')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'At most 500 items can be changed at once.'
        0 * repo._
    }

    // ---- bulk change: the reason ----

    @Unroll
    def "bulkChange should refuse 'Say why.' and write nothing when the reason is #scenario"() {

        when:
        service.bulkChange(TONY, 1, [5], 'status', 'done', reason)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario     | reason || expectedMessage
        'null'       | null   || 'Say why. The reason goes in the log.'
        'empty'      | ''     || 'Say why. The reason goes in the log.'
        'blank'      | ' \t ' || 'Say why. The reason goes in the log.'
    }

    def "bulkChange should pass the reason trimmed when it is padded"() {

        when:
        service.bulkChange(TONY, 1, [5], 'status', 'done', '  The partner app writes the 837 itself.  ')

        then:
        1 * repo.bulkChange(1, '5', 'status', 'done', 'The partner app writes the 837 itself.', 'tony') >> new BulkResult(1, 1)
    }

    def "bulkChange should pass a reason of exactly 1,000 characters"() {

        given:
        String reason = textOf(1000)

        when:
        service.bulkChange(TONY, 1, [5], 'status', 'done', reason)

        then:
        1 * repo.bulkChange(1, '5', 'status', 'done', reason, 'tony') >> new BulkResult(1, 1)
    }

    def "bulkChange should refuse a reason of 1,001 characters and write nothing"() {

        when:
        service.bulkChange(TONY, 1, [5], 'status', 'done', textOf(1001))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A reason can be at most 1,000 characters.'
        0 * repo._
    }

    // ---- bulk change: the value ----

    @Unroll
    def "bulkChange should pass the value as '#expectedValue' when it is #scenario"() {

        when:
        service.bulkChange(TONY, 1, [5], 'assignee', value, 'why')

        then:
        1 * repo.bulkChange(1, '5', 'assignee', expectedValue, 'why', 'tony') >> new BulkResult(1, 1)

        where:
        scenario                      | value       || expectedValue
        'null, which clears it'       | null        || ''
        'empty, which clears it'      | ''          || ''
        'blank'                       | '   '       || ''
        'padded'                      | '  rex  '   || 'rex'
        'kept in its own case'        | 'Rex'       || 'Rex'
    }

    def "bulkChange should pass a value of exactly 400 characters"() {

        given:
        String value = textOf(400)

        when:
        service.bulkChange(TONY, 1, [5], 'status', value, 'why')

        then:
        1 * repo.bulkChange(1, '5', 'status', value, 'why', 'tony') >> new BulkResult(0, 0)
    }

    def "bulkChange should refuse a value of 401 characters and write nothing"() {

        when:
        service.bulkChange(TONY, 1, [5], 'status', textOf(401), 'why')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'That value is too long.'
        0 * repo._
    }

    // ---- bulk change: who and what comes back ----

    @Unroll
    def "bulkChange should attribute the change to #expectedCode and answer the repository's result when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        BulkResult done = new BulkResult(310, 20)

        when:
        BulkResult got = service.bulkChange(caller, 1, [1200, 1201], 'status', 'cancelled', 'The 837 path is dead.')

        then:
        1 * repo.bulkChange(1, '1200,1201', 'status', 'cancelled', 'The 837 path is dead.', expectedCode) >> done
        got.is(done)

        where:
        scenario                   | callerName || expectedCode
        'a person changes them'    | 'TONY'     || 'tony'
        'the house changes them'   | 'HOUSE'    || 'felix'
        'a client changes them'    | 'REX'      || 'rex'
    }

    def "bulkChange should pass the repository's refusal through"() {

        when:
        service.bulkChange(TONY, 1, [5], 'status', 'finished', 'why')

        then:
        1 * repo.bulkChange(*_) >> { throw new SolutionRuleException('That is not a status. Nothing was changed.') }
        SolutionRuleException ex = thrown()
        ex.message == 'That is not a status. Nothing was changed.'
    }

    // ---- promote: the kinds ----

    @Unroll
    def "promote should pass the kinds as #expectedSource into #expectedTarget for #scenario"() {

        when:
        service.promote(TONY, 1, sourceKind, 5, targetKind, 41, null)

        then:
        1 * repo.promote(1, expectedSource, 5, expectedTarget, 41, null, 'tony') >> new Promoted(expectedTarget, 9)

        where:
        scenario                 | sourceKind   | targetKind    || expectedSource | expectedTarget
        'a message into an item' | 'message'    | 'item'        || 'message'      | 'item'
        'padded and upper case'  | ' MESSAGE '  | ' Question '  || 'message'      | 'question'
        'a log entry'            | 'Log'        | 'NOTE'        || 'log'          | 'note'
        'a note'                 | 'note'       | 'item'        || 'note'         | 'item'
        'an item'                | 'item'       | 'note'        || 'item'         | 'note'
    }

    @Unroll
    def "promote should refuse the source kind and write nothing when it is #scenario"() {

        when:
        service.promote(TONY, 1, sourceKind, 5, 'item', 41, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                      | sourceKind  || expectedMessage
        'null'                        | null        || 'sourceKind must be one of message, log, note, item.'
        'empty'                       | ''          || 'sourceKind must be one of message, log, note, item.'
        'a question'                  | 'question'  || 'sourceKind must be one of message, log, note, item.'
        'a decision'                  | 'decision'  || 'sourceKind must be one of message, log, note, item.'
        'a file'                      | 'file'      || 'sourceKind must be one of message, log, note, item.'
    }

    @Unroll
    def "promote should refuse the target kind and write nothing when it is #scenario"() {

        when:
        service.promote(TONY, 1, 'message', 5, targetKind, 41, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                      | targetKind  || expectedMessage
        'null'                        | null        || 'targetKind must be one of item, note, question.'
        'blank'                       | '  '        || 'targetKind must be one of item, note, question.'
        'a log entry'                 | 'log'       || 'targetKind must be one of item, note, question.'
        'a message'                   | 'message'   || 'targetKind must be one of item, note, question.'
        'a decision'                  | 'decision'  || 'targetKind must be one of item, note, question.'
    }

    def "promote should check the source kind before the target kind when both are wrong"() {

        when:
        service.promote(TONY, 1, 'topic', 5, 'topic', 41, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == SOURCE_LIST
        0 * repo._
    }

    @Unroll
    def "promote should refuse a missing source id and write nothing when it is #scenario"() {

        when:
        service.promote(TONY, 1, 'note', sourceId, 'item', 41, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario     | sourceId || expectedMessage
        'null'       | null     || 'Say which record to make it from (sourceId).'
        'zero'       | 0        || 'Say which record to make it from (sourceId).'
        'negative'   | -4       || 'Say which record to make it from (sourceId).'
    }

    // ---- promote: the title and the project ----

    @Unroll
    def "promote should pass the title as #expectedTitle when it is #scenario"() {

        when:
        service.promote(TONY, 1, 'note', 77, 'item', 41, title)

        then:
        1 * repo.promote(1, 'note', 77, 'item', 41, expectedTitle, 'tony') >> new Promoted('item', 9)

        where:
        scenario                                   | title                                     || expectedTitle
        'null, so the first line is used'          | null                                      || null
        'empty, so the first line is used'         | ''                                        || null
        'blank, so the first line is used'         | '   '                                     || null
        'padded'                                   | '  Write the runbook for the QA move  '   || 'Write the runbook for the QA move'
    }

    @Unroll
    def "promote should pass a title of 400 characters when #scenario"() {

        given:
        String title = padding + textOf(400) + padding

        when:
        service.promote(TONY, 1, 'note', 77, 'item', 41, title)

        then:
        1 * repo.promote(1, 'note', 77, 'item', 41, textOf(400), 'tony') >> new Promoted('item', 9)

        where:
        scenario                         | padding || unused
        'it is sent as it is'            | ''      || null
        'it has spaces around it'        | '   '   || null
    }

    def "promote should refuse a title of 401 characters and write nothing"() {

        when:
        service.promote(TONY, 1, 'note', 77, 'item', 41, textOf(401))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A title can be at most 400 characters.'
        0 * repo._
    }

    @Unroll
    def "promote should pass the project as #expectedProject when it is #scenario"() {

        when:
        service.promote(TONY, 1, 'log', 310, 'item', projectId, null)

        then:
        1 * repo.promote(1, 'log', 310, 'item', expectedProject, null, 'tony') >> new Promoted('item', 9)

        where:
        scenario                     | projectId || expectedProject
        'a project id'               | 41        || 41
        'null'                       | null      || null
        'zero, which means none'     | 0         || null
        'negative, which means none' | -2        || null
    }

    @Unroll
    def "promote should attribute the new record to #expectedCode and answer it when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        Promoted made = new Promoted('question', 12)

        when:
        Promoted got = service.promote(caller, 1, 'message', 9012, 'question', null, 'Is the charset fixed?')

        then:
        1 * repo.promote(1, 'message', 9012, 'question', null, 'Is the charset fixed?', expectedCode) >> made
        got.is(made)

        where:
        scenario                 | callerName || expectedCode
        'a person makes it'      | 'TONY'     || 'tony'
        'the house makes it'     | 'HOUSE'    || 'felix'
        'a client makes it'      | 'REX'      || 'rex'
    }

    def "promote should pass the repository's refusal through"() {

        when:
        service.promote(TONY, 1, 'message', 9012, 'item', 41, null)

        then:
        1 * repo.promote(*_) >> { throw new SolutionRuleException('That was already made into an item (#77). Nothing was added.') }
        SolutionRuleException ex = thrown()
        ex.message == 'That was already made into an item (#77). Nothing was added.'
    }

    // ---- provenance ----

    @Unroll
    def "provenance should pass the kind as #expectedKind for #scenario"() {

        when:
        service.provenance(REX, 1, kind, 1200)

        then:
        1 * repo.provenance(1, expectedKind, 1200) >> []

        where:
        scenario          | kind           || expectedKind
        'an item'         | 'item'         || 'item'
        'a note'          | 'note'         || 'note'
        'a log entry'     | 'log'          || 'log'
        'a message'       | 'message'      || 'message'
        'a question'      | ' QUESTION '   || 'question'
    }

    @Unroll
    def "provenance should refuse the kind and read nothing when it is #scenario"() {

        when:
        service.provenance(TONY, 1, kind, 1200)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario       | kind        || expectedMessage
        'null'         | null        || 'kind must be one of message, log, note, item, question.'
        'empty'        | ''          || 'kind must be one of message, log, note, item, question.'
        'a decision'   | 'decision'  || 'kind must be one of message, log, note, item, question.'
        'a topic'      | 'topic'     || 'kind must be one of message, log, note, item, question.'
    }

    @Unroll
    def "provenance should refuse a missing record id and read nothing when it is #scenario"() {

        when:
        service.provenance(TONY, 1, 'item', recordId)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario     | recordId || expectedMessage
        'null'       | null     || 'Say which record (recordId).'
        'zero'       | 0        || 'Say which record (recordId).'
        'negative'   | -1       || 'Say which record (recordId).'
    }

    def "provenance should answer the repository's rows when the solution is visible"() {

        given:
        List<Provenance> rows = [new Provenance('from', 'message', 9012, 'The partner app writes the 837', 44, null, 'tony', 'Tony')]

        when:
        List<Provenance> got = service.provenance(REX, 1, 'item', 1200)

        then:
        1 * repo.provenance(1, 'item', 1200) >> rows
        got.is(rows)
    }
}
