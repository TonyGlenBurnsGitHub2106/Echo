package com.echo

import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionModels.LogEntry
import com.echo.SolutionTopicKindModels.OpenItem
import com.echo.SolutionTopicKindModels.ProvedFalse
import com.echo.SolutionTopicKindModels.TopicType
import static com.echo.SolutionFixtures.logEntry

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): the business rules of topic kinds -- access is checked before anything is read or
 * written (a kind of part is reached through its own solution, a topic through the link service, never the
 * solution a caller names), the kinds of part are found or added, renamed and removed, Arrange refuses an
 * empty or malformed order before any write, and the PREMISE SWITCH: only an assumption that still holds can
 * be proved false, a reason is required, only the open items resting on it can be named (anything else is
 * refused before anything changes), and on success the assumption is saved as no longer holding, each chosen
 * item is cancelled through the item service, and ONE "result" journal entry is written and linked to it.
 */
class SolutionTopicKindServiceSpec extends SolutionServiceSupport {

    static final String NOT_AN_ASSUMPTION = 'Only an assumption can be proved false. Nothing was changed.'
    static final String ALREADY_FALSE = 'That assumption was already proved false. Nothing was changed.'
    static final String NO_REASON = 'Say why it no longer holds. The journal keeps the reason.'
    static final String ASSUMPTION = 'The 837 path is live'

    SolutionTopicKindRepository repo = Mock()
    SolutionLinkService links = Mock()
    SolutionItemService items = Mock()
    SolutionLogService log = Mock()
    SolutionTopicKindService service = new SolutionTopicKindService(repo, links, items, log, access)

    Map<Integer, TopicType> typeRows = [3: new TopicType(3, 1, 'Chapter', 'words', 1, 2), 4: new TopicType(4, 2, 'Account', null, 1, 0)]
    Map<Integer, Topic> topicRows = [5: topic(5, 1, 'assumption', true), 6: topic(6, 2, 'assumption', true),
                                     7: topic(7, 1, 'subject', null), 8: topic(8, 1, 'assumption', false),
                                     9: topic(9, 1, 'part', null), 10: topic(10, 1, 'tag', null),
                                     11: topic(11, 1, 'assumption', null)]

    def setup() {
        repo.findType(_) >> { int id -> typeRows[id] }
        links.topic(_, _) >> { SolutionCaller c, int id -> visibleTopic(access, topicRows, c, id) }
    }

    private static Topic topic(int id, int solutionId, String kind, Boolean holds) {
        Topic.of([SolutionTopicID: id, SolutionID: solutionId, TopicName: id == 5 ? ASSUMPTION : 'Topic ' + id,
                  TopicKindCode: kind, AssumptionHolds: holds])
    }

    /** What SolutionLinkService.topic answers: the topic, reached through its own solution. */
    private static Topic visibleTopic(SolutionAccess a, Map<Integer, Topic> rows, SolutionCaller caller, int id) {
        Topic t = id <= 0 ? null : rows[id]
        if (t == null) {
            throw new SolutionNotFoundException('Topic')
        }
        a.requireSolution(caller, t.solutionId())
        return t
    }

    private static OpenItem open(int id) {
        new OpenItem(id, 'Item ' + id, 'Deploy | Not started')
    }

    /** "12,NULL,9" to [12, null, 9]; "" is the empty list; null stays null. */
    private static List<Integer> idsOf(String csv) {
        List<String> parts = listOf(csv)
        return parts == null ? null : parts.collect { String s -> s == null ? null : Integer.valueOf(s.trim()) }
    }

    /** "A;B" to the cancelled items provedFalseText is given; "" is none. */
    private static List<OpenItem> cancelledOf(String cell) {
        cell.isEmpty() ? [] : cell.split(';').collect { String title -> new OpenItem(1, title, null) }
    }

    /** The draft proveFalse saves: only "it no longer holds". */
    private static TopicDraft provedFalseDraft(int topicId) {
        new TopicDraft(topicId, 0, null, null, null, null, null, null, false, Boolean.FALSE)
    }

    /** Runs one named call -- the calls a data table can name. */
    private static Object call(SolutionTopicKindService s, String action, SolutionCaller caller, int id) {
        switch (action) {
            case 'types':      return s.types(caller, id)
            case 'addType':    return s.addType(caller, id, 'Chapter', 'words')
            case 'reorder':    s.reorder(caller, id, [12, 9]); return null
            case 'type':       return s.type(caller, id)
            case 'updateType': return s.updateType(caller, id, 'Scene', null)
            case 'deleteType': s.deleteType(caller, id); return null
            case 'openItems':  return s.openItems(caller, id)
            case 'proveFalse': return s.proveFalse(caller, id, 'The path died.', [100])
            default: throw new IllegalStateException(action)
        }
    }

    // ---- access first ----

    @Unroll
    def "#seq. #action should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        call(service, action, caller, id)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.types(_)
        0 * repo.saveType(*_)
        0 * repo.deleteType(_)
        0 * repo.reorder(*_)
        0 * repo.openItems(_)
        0 * links.saveTopic(*_)
        0 * links.link(*_)
        0 * items._
        0 * log._

        where:
        seq | scenario                                   | action       | callerName | id || expectedMessage
        1   | 'the client was given nothing'             | 'types'      | 'ZED'      | 1  || 'Solution not found.'
        2   | 'the client was not given the solution'    | 'types'      | 'REX'      | 2  || 'Solution not found.'
        3   | 'the solution does not exist'              | 'addType'    | 'TONY'     | 9  || 'Solution not found.'
        4   | 'the client was not given the solution'    | 'addType'    | 'REX'      | 2  || 'Solution not found.'
        5   | 'the solution id is zero'                  | 'reorder'    | 'HOUSE'    | 0  || 'Solution not found.'
        6   | 'the client was given nothing'             | 'reorder'    | 'ZED'      | 1  || 'Solution not found.'
        7   | 'the client was not given its solution'    | 'type'       | 'REX'      | 4  || 'Solution not found.'
        8   | 'the client was not given its solution'    | 'updateType' | 'REX'      | 4  || 'Solution not found.'
        9   | 'the client was given nothing'             | 'deleteType' | 'ZED'      | 3  || 'Solution not found.'
        10  | 'the client was not given its solution'    | 'openItems'  | 'REX'      | 6  || 'Solution not found.'
        11  | 'the client was not given its solution'    | 'proveFalse' | 'REX'      | 6  || 'Solution not found.'
        12  | 'the client was given nothing'             | 'proveFalse' | 'ZED'      | 5  || 'Solution not found.'
        13  | 'the kind of part does not exist'          | 'type'       | 'TONY'     | 99 || 'Kind of part not found.'
        14  | 'the kind of part does not exist'          | 'updateType' | 'TONY'     | 99 || 'Kind of part not found.'
        15  | 'the kind of part id is zero'              | 'deleteType' | 'TONY'     | 0  || 'Kind of part not found.'
        16  | 'the topic does not exist'                 | 'openItems'  | 'TONY'     | 99 || 'Topic not found.'
        17  | 'the topic id is zero'                     | 'proveFalse' | 'TONY'     | 0  || 'Topic not found.'
    }

    def "reorder should check access before the order when the solution is hidden and the order is empty"() {

        when:
        service.reorder(ZED, 1, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo.reorder(*_)
    }

    def "proveFalse should check the topic before the reason when the topic is hidden and the reason is blank"() {

        when:
        service.proveFalse(REX, 6, '  ', null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo.openItems(_)
        0 * links.saveTopic(*_)
    }

    // ---- kinds of part ----

    def "types should answer the solution's kinds of part when the solution is visible"() {

        given:
        List<TopicType> rows = [typeRows[3]]

        when:
        List<TopicType> got = service.types(REX, 1)

        then:
        1 * repo.types(1) >> rows
        got == rows
    }

    def "type should answer the kind of part when the caller may see its solution"() {

        expect:
        service.type(TONY, 4) == typeRows[4]
        service.type(REX, 3).name() == 'Chapter'
        service.type(HOUSE, 4).solutionId() == 2
    }

    @Unroll
    def "type should throw kind of part not found and not reach the repository when the id is #scenario"() {

        when:
        service.type(TONY, typeId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.findType(_)

        where:
        scenario   | typeId || expectedMessage
        'zero'     | 0      || 'Kind of part not found.'
        'negative' | -1     || 'Kind of part not found.'
    }

    @Unroll
    def "addType should find or add the kind of part and answer it read back when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        TopicType t = service.addType(caller, 1, name, label)

        then:
        1 * repo.saveType(0, 1, name, label) >> 3

        then:
        1 * repo.findType(3) >> typeRows[3]
        t.name() == expectedName
        t.id() == 3

        where:
        scenario                               | callerName | name        | label   || expectedName
        'a person adds it with a label'        | 'TONY'     | 'Chapter'   | 'words' || 'Chapter'
        'the house adds it without a label'    | 'HOUSE'    | 'Chapter'   | null    || 'Chapter'
        'a client finds it by another case'    | 'REX'      | ' chapter ' | ''      || 'Chapter'
    }

    @Unroll
    def "addType should throw kind of part not found and read nothing back when the repository answers a #scenario id"() {

        when:
        service.addType(TONY, 1, 'Chapter', null)

        then:
        1 * repo.saveType(0, 1, 'Chapter', null) >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.findType(_)

        where:
        scenario   | savedId || expectedMessage
        'zero'     | 0       || 'Kind of part not found.'
        'negative' | -1      || 'Kind of part not found.'
    }

    def "addType should pass a refusal through when the repository refuses the name"() {

        when:
        service.addType(TONY, 1, '  ', null)

        then:
        1 * repo.saveType(0, 1, '  ', null) >> { throw new SolutionRuleException('A kind of part needs a name. Nothing was added.') }
        SolutionRuleException ex = thrown()
        ex.message == 'A kind of part needs a name. Nothing was added.'
    }

    @Unroll
    def "updateType should save #scenario as sent with solution zero and answer the kind read back"() {

        when:
        TopicType t = service.updateType(REX, 3, name, label)

        then:
        2 * repo.findType(3) >> typeRows[3]
        1 * repo.saveType(3, 0, name, label) >> 3
        t.id() == expectedId

        where:
        scenario                 | name      | label   || expectedId
        'a new name only'        | 'Scene'   | null    || 3
        'a cleared label only'   | null      | ''      || 3
        'a padded name and label' | ' Scene ' | 'pages' || 3
        'nothing'                | null      | null    || 3
    }

    def "updateType should look the kind up through its own solution before it saves"() {

        when:
        service.updateType(TONY, 3, 'Scene', null)

        then:
        1 * repo.findType(3) >> typeRows[3]

        then:
        1 * repo.saveType(3, 0, 'Scene', null) >> 3

        then:
        1 * repo.findType(3) >> typeRows[3]
    }

    @Unroll
    def "updateType should throw kind of part not found when the repository answers a #scenario id"() {

        when:
        service.updateType(TONY, 3, 'Scene', null)

        then:
        1 * repo.saveType(3, 0, 'Scene', null) >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario   | savedId || expectedMessage
        'zero'     | 0       || 'Kind of part not found.'
        'negative' | -2      || 'Kind of part not found.'
    }

    def "updateType should pass a refusal through when the repository refuses a rename onto a used name"() {

        when:
        service.updateType(TONY, 3, 'Account', null)

        then:
        1 * repo.saveType(3, 0, 'Account', null) >> { throw new SolutionRuleException('This solution already has that kind of part. Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'This solution already has that kind of part. Nothing was saved.'
    }

    def "deleteType should remove a visible kind of part and throw not found when nothing was removed"() {

        when:
        service.deleteType(REX, 3)

        then:
        1 * repo.deleteType(3) >> true

        when:
        service.deleteType(TONY, 4)

        then:
        1 * repo.deleteType(4) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Kind of part not found.'
    }

    def "deleteType should remove nothing when the kind of part does not exist"() {

        when:
        service.deleteType(TONY, 99)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Kind of part not found.'
        0 * repo.deleteType(_)
    }

    // ---- the order of the parts ----

    @Unroll
    def "reorder should pass the order as given when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        List<Integer> ids = idsOf(csv)

        when:
        service.reorder(caller, 1, ids)

        then:
        1 * repo.reorder(1, idsOf(expectedCsv))

        where:
        scenario                      | callerName | csv       || expectedCsv
        'a person arranges three'     | 'TONY'     | '12,9,30' || '12,9,30'
        'the house arranges one'      | 'HOUSE'    | '4'       || '4'
        'a client arranges two'       | 'REX'      | '9,12'    || '9,12'
    }

    @Unroll
    def "reorder should refuse with '#expectedMessage' and write nothing when #scenario"() {

        given:
        List<Integer> ids = idsOf(csv)

        when:
        service.reorder(TONY, 1, ids)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.reorder(*_)

        where:
        scenario                        | csv          || expectedMessage
        'the order is null'             | null         || 'Name the topics in their new order.'
        'the order is empty'            | ''           || 'Name the topics in their new order.'
        'an id is null'                 | '12,NULL'    || 'The order is a list of topic ids.'
        'an id is zero'                 | '12,0'       || 'The order is a list of topic ids.'
        'an id is negative'             | '-1'         || 'The order is a list of topic ids.'
        'a bad id comes after good ones' | '12,9,30,-5' || 'The order is a list of topic ids.'
    }

    def "reorder should pass a refusal through when the repository refuses a topic of another solution"() {

        when:
        service.reorder(TONY, 1, [12, 600])

        then:
        1 * repo.reorder(1, [12, 600]) >> { throw new SolutionRuleException('A topic in that order is not in this solution. Nothing was changed.') }
        SolutionRuleException ex = thrown()
        ex.message == 'A topic in that order is not in this solution. Nothing was changed.'
    }

    // ---- open items ----

    def "openItems should check the topic through the link service and then answer its open items"() {

        given:
        List<OpenItem> rows = [open(100), open(101)]

        when:
        List<OpenItem> got = service.openItems(REX, 5)

        then:
        1 * links.topic(REX, 5) >> topicRows[5]

        then:
        1 * repo.openItems(5) >> rows
        got == rows
    }

    def "openItems should answer the open items of any kind of topic when the topic is not an assumption"() {

        when:
        List<OpenItem> got = service.openItems(TONY, 10)

        then:
        1 * repo.openItems(10) >> [open(100)]
        got == [open(100)]
    }

    // ---- the premise switch: refusals ----

    @Unroll
    def "proveFalse should refuse with '#expectedMessage' and change nothing when #scenario"() {

        when:
        service.proveFalse(TONY, topicId, reason, [100])

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        0 * repo.openItems(_)
        0 * links.saveTopic(*_)
        0 * links.link(*_)
        0 * items._
        0 * log._

        where:
        scenario                                         | topicId | reason || expectedMessage
        'the topic is a subject'                         | 7       | 'Gone' || 'Only an assumption can be proved false. Nothing was changed.'
        'the topic is a part'                            | 9       | 'Gone' || 'Only an assumption can be proved false. Nothing was changed.'
        'the topic is a tag'                             | 10      | 'Gone' || 'Only an assumption can be proved false. Nothing was changed.'
        'a subject is named with no reason'              | 7       | null   || 'Only an assumption can be proved false. Nothing was changed.'
        'the assumption was already proved false'        | 8       | 'Gone' || 'That assumption was already proved false. Nothing was changed.'
        'a proved-false assumption is named with no reason' | 8    | ''     || 'That assumption was already proved false. Nothing was changed.'
    }

    @Unroll
    def "proveFalse should refuse the reason and change nothing when #scenario"() {

        when:
        service.proveFalse(TONY, 5, reason, [100])

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.openItems(_)
        0 * links.saveTopic(*_)
        0 * links.link(*_)
        0 * items._
        0 * log._

        where:
        scenario                       | reason || expectedMessage
        'the reason is null'           | null   || 'Say why it no longer holds. The journal keeps the reason.'
        'the reason is empty'          | ''     || 'Say why it no longer holds. The journal keeps the reason.'
        'the reason is blank'          | '   '  || 'Say why it no longer holds. The journal keeps the reason.'
        'the reason is a tab and a newline' | '\t\n' || 'Say why it no longer holds. The journal keeps the reason.'
    }

    @Unroll
    def "proveFalse should refuse with the item named and change nothing when #scenario"() {

        given:
        List<Integer> itemIds = idsOf(csv)

        when:
        service.proveFalse(TONY, 5, 'The path died.', itemIds)

        then:
        1 * repo.openItems(5) >> [open(100), open(101)]
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * links.saveTopic(*_)
        0 * items.setStatus(*_)
        0 * log.save(*_)
        0 * links.link(*_)

        where:
        scenario                                    | csv          || expectedMessage
        'the item does not rest on it'              | '102'        || 'The item 102 is not an open item resting on this assumption. Nothing was changed.'
        'a good one comes before a bad one'         | '100,999'    || 'The item 999 is not an open item resting on this assumption. Nothing was changed.'
        'the id is zero'                            | '0'          || 'The item 0 is not an open item resting on this assumption. Nothing was changed.'
        'the id is negative'                        | '-4'         || 'The item -4 is not an open item resting on this assumption. Nothing was changed.'
        'a repeat of a good one comes before a bad one' | '100,100,7' || 'The item 7 is not an open item resting on this assumption. Nothing was changed.'
        'a null comes before a bad one'             | 'NULL,8'     || 'The item 8 is not an open item resting on this assumption. Nothing was changed.'
    }

    // ---- the premise switch: success ----

    @Unroll
    def "proveFalse should mark it false, cancel each chosen item, write one result entry and link it when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LogEntry entry = logEntry(id: 410, solutionId: 1, sequenceNumber: 42, typeCode: 'result')
        String text = '**Assumption proved false:** The 837 path is live\n\nThe 837 path died on 10/16.\n\nCancelled because of it:\n- Item 100\n- Item 101'

        when:
        ProvedFalse p = service.proveFalse(caller, 5, '  The 837 path died on 10/16.  ', [100, null, 101, 100])

        then:
        1 * repo.openItems(5) >> [open(100), open(101), open(102)]

        then:
        1 * links.saveTopic(caller, provedFalseDraft(5))

        then:
        1 * items.setStatus(caller, 100, 'cancelled')
        1 * items.setStatus(caller, 101, 'cancelled')

        then:
        1 * log.save(caller, new LogDraft(0, 1, null, null, 'result', text, null)) >> entry

        then:
        1 * links.link(caller, 1, 'log', 410, 'topic', 5)

        then:
        1 * repo.openItems(5) >> [open(102)]
        0 * items.setStatus(_, 102, _)
        p.topic().id() == 5
        p.cancelled() == [open(100), open(101)]
        p.stillOpen() == [open(102)]
        p.logId() == 410
        p.logSequenceNumber() == 42

        where:
        scenario                    | callerName || unused
        'a person proves it false'  | 'TONY'     || null
        'the house proves it false' | 'HOUSE'    || null
        'a client proves it false'  | 'REX'      || null
    }

    def "proveFalse should save the draft with holds Boolean FALSE and nothing else when it marks the assumption"() {

        given:
        TopicDraft saved = null

        when:
        service.proveFalse(TONY, 5, 'Gone.', null)

        then:
        2 * repo.openItems(5) >> []
        1 * links.saveTopic(TONY, _) >> { SolutionCaller c, TopicDraft d -> saved = d; null }
        1 * log.save(*_) >> logEntry(id: 411)
        saved.id() == 5
        saved.solutionId() == 0
        saved.name() == null
        saved.summary() == null
        saved.kindCode() == null
        saved.typeName() == null
        saved.statusCode() == null
        saved.figure() == null
        !saved.clearFigure()
        saved.holds() == Boolean.FALSE
        saved.holds() instanceof Boolean
    }

    @Unroll
    def "proveFalse should cancel nothing and write the reason alone when #scenario"() {

        given:
        List<Integer> itemIds = idsOf(csv)

        when:
        ProvedFalse p = service.proveFalse(TONY, 5, 'Gone.', itemIds)

        then:
        2 * repo.openItems(5) >> [open(100)]
        1 * links.saveTopic(TONY, provedFalseDraft(5))
        0 * items._
        1 * log.save(TONY, new LogDraft(0, 1, null, null, 'result', expectedText, null)) >> logEntry(id: 412, sequenceNumber: 44)
        1 * links.link(TONY, 1, 'log', 412, 'topic', 5)
        p.cancelled() == []
        p.stillOpen() == [open(100)]
        p.logId() == 412

        where:
        scenario                         | csv    || expectedText
        'no items are named'             | null   || '**Assumption proved false:** The 837 path is live\n\nGone.'
        'the list of items is empty'     | ''     || '**Assumption proved false:** The 837 path is live\n\nGone.'
        'the list holds only a null'     | 'NULL' || '**Assumption proved false:** The 837 path is live\n\nGone.'
    }

    def "proveFalse should answer the assumption read back after the switch"() {

        given:
        Topic proved = topic(5, 1, 'assumption', false)

        when:
        ProvedFalse p = service.proveFalse(TONY, 5, 'Gone.', [])

        then:
        2 * links.topic(TONY, 5) >>> [topicRows[5], proved]
        2 * repo.openItems(5) >> []
        1 * log.save(*_) >> logEntry(id: 413)
        p.topic().is(proved)
        p.topic().holds() == false
    }

    def "proveFalse should link nothing and answer no journal entry when the log service answers none"() {

        when:
        ProvedFalse p = service.proveFalse(TONY, 5, 'Gone.', [100])

        then:
        2 * repo.openItems(5) >> [open(100)]
        1 * items.setStatus(TONY, 100, 'cancelled')
        1 * log.save(*_) >> null
        0 * links.link(*_)
        p.logId() == null
        p.logSequenceNumber() == null
        p.cancelled() == [open(100)]
    }

    def "proveFalse should treat an assumption with no holds recorded as one that still holds"() {

        when:
        ProvedFalse p = service.proveFalse(HOUSE, 11, 'Gone.', null)

        then:
        2 * repo.openItems(11) >> []
        1 * links.saveTopic(HOUSE, provedFalseDraft(11))
        1 * log.save(HOUSE, { LogDraft d -> d.text() == '**Assumption proved false:** Topic 11\n\nGone.' && d.typeCode() == 'result' && d.solutionId() == 1 }) >>
            logEntry(id: 414)
        1 * links.link(HOUSE, 1, 'log', 414, 'topic', 11)
        p.logId() == 414
    }

    // ---- the journal text ----

    @Unroll
    def "provedFalseText should answer '#expected' when #scenario"() {

        given:
        Topic t = Topic.of([SolutionTopicID: 5, TopicName: name])

        expect:
        SolutionTopicKindService.provedFalseText(t, reason, cancelledOf(cancelledCell)) == expected

        where:
        scenario                     | name                   | reason            | cancelledCell || expected
        'nothing was cancelled'      | 'The 837 path is live' | 'It died.'        | ''            || '**Assumption proved false:** The 837 path is live\n\nIt died.'
        'one item was cancelled'     | 'P'                    | 'R'               | 'Submit 837'  || '**Assumption proved false:** P\n\nR\n\nCancelled because of it:\n- Submit 837'
        'two items, in order'        | 'P'                    | 'R'               | 'B;A'         || '**Assumption proved false:** P\n\nR\n\nCancelled because of it:\n- B\n- A'
        'the reason has two lines'   | 'P'                    | 'Line 1\nLine 2'  | ''            || '**Assumption proved false:** P\n\nLine 1\nLine 2'
    }
}
