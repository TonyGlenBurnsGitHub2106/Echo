package com.echo

import java.time.LocalDateTime

import com.echo.SolutionLinkModels.Change
import com.echo.SolutionLinkModels.LinkPage
import com.echo.SolutionLinkModels.LinkTarget
import com.echo.SolutionLinkModels.SincePage
import com.echo.SolutionLinkModels.StoredLink
import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionLinkModels.TopicPage
import com.echo.SolutionLinkModels.Visit

import spock.lang.Unroll

/**
 * Story 5695: the business rules of Solution links, topics and "Since you were last here" -- access is
 * checked before anything is read or written (a hidden solution reads and writes nothing), the six kinds,
 * a link is removed only through its own solution, a topic is reached through its own solution, the add
 * and edit paths of a topic, the visit and "since" window, and the paging every list shares.
 */
class SolutionLinkServiceSpec extends SolutionServiceSupport {

    static final String KIND_MESSAGE = 'kind must be one of item, note, log, file, term, topic.'

    SolutionLinkRepository links = Mock()
    SolutionLinkService service = new SolutionLinkService(links, access)

    Map<Integer, Topic> topicRows = [5: topic(5, 1), 6: topic(6, 2)]
    Map<Integer, StoredLink> linkRows = [40: new StoredLink(40, 1, 'item', 100, 'note', 300),
                                         41: new StoredLink(41, 2, 'item', 101, 'term', 400)]

    def setup() {
        links.findTopic(_) >> { int id -> topicRows[id] }
        links.findLink(_) >> { int id -> linkRows[id] }
    }

    private static Topic topic(int id, int solutionId) {
        Topic.of([SolutionTopicID: id, SolutionID: solutionId, TopicName: 'Topic ' + id])
    }

    private static LinkPage emptyPage(String kind, int id) {
        new LinkPage(kind, id, [], 0, 50, 0)
    }

    /** Runs one named solution-scoped call -- the calls a data table can name. */
    private static Object call(SolutionLinkService s, String action, SolutionCaller caller, int solutionId) {
        switch (action) {
            case 'links':    return s.links(caller, solutionId, 'item', 100, 0, 50)
            case 'link':     return s.link(caller, solutionId, 'item', 100, 'note', 300)
            case 'unlink':   s.unlink(caller, solutionId, 40); return null
            case 'targets':  return s.targets(caller, solutionId, 'charset', 'item', 100, 30)
            case 'topics':   return s.topics(caller, solutionId, null, 0, 50)
            case 'addTopic': return s.saveTopic(caller, new TopicDraft(0, solutionId, 'Charset', null))
            case 'visit':    return s.visit(caller, solutionId)
            case 'since':    return s.since(caller, solutionId, 0, 50)
            case 'caughtUp': return s.caughtUp(caller, solutionId)
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
        0 * links._

        where:
        seq | scenario                                   | action     | callerName | solutionId || expectedMessage
        1   | 'the client was given nothing'             | 'links'    | 'ZED'      | 1          || 'Solution not found.'
        2   | 'the client was not given the solution'    | 'link'     | 'REX'      | 2          || 'Solution not found.'
        3   | 'the client was given nothing'             | 'unlink'   | 'ZED'      | 1          || 'Solution not found.'
        4   | 'the solution does not exist'              | 'targets'  | 'TONY'     | 9          || 'Solution not found.'
        5   | 'the id is zero'                           | 'topics'   | 'TONY'     | 0          || 'Solution not found.'
        6   | 'the client was not given the solution'    | 'addTopic' | 'REX'      | 2          || 'Solution not found.'
        7   | 'the client was given nothing'             | 'visit'    | 'ZED'      | 1          || 'Solution not found.'
        8   | 'the solution does not exist'              | 'since'    | 'HOUSE'    | 9          || 'Solution not found.'
        9   | 'the client was not given the solution'    | 'caughtUp' | 'REX'      | 2          || 'Solution not found.'
        10  | 'the id is negative'                       | 'link'     | 'TONY'     | -1         || 'Solution not found.'
        11  | 'the client was not given the solution'    | 'visit'    | 'REX'      | 2          || 'Solution not found.'
        12  | 'the client was not given the solution'    | 'targets'  | 'REX'      | 2          || 'Solution not found.'
    }

    def "link should check access before the kinds when the solution is hidden and the kind is unknown"() {

        when:
        service.link(ZED, 1, 'story', 1, 'item', 100)

        then:
        thrown(SolutionNotFoundException)
        0 * links._
    }

    // ---- kinds ----

    @Unroll
    def "#seq. kind should answer #expected when #scenario"() {

        expect:
        SolutionLinkService.kind(kind) == expected

        where:
        seq | scenario                         | kind       || expected
        1   | 'an item is named'               | 'item'     || 'item'
        2   | 'a note is named'                | 'note'     || 'note'
        3   | 'a log entry is named'           | 'log'      || 'log'
        4   | 'a file is named'                | 'file'     || 'file'
        5   | 'a term is named'                | 'term'     || 'term'
        6   | 'a topic is named'               | 'topic'    || 'topic'
        7   | 'the kind is in capitals'        | 'ITEM'     || 'item'
        8   | 'the kind is padded and mixed'   | '  Note '  || 'note'
        9   | 'the kind has a tab'             | '\tterm'   || 'term'
    }

    @Unroll
    def "kind should refuse with the six kinds named when #scenario"() {

        when:
        SolutionLinkService.kind(kind)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                         | kind       || expectedMessage
        'the kind is null'               | null       || 'kind must be one of item, note, log, file, term, topic.'
        'the kind is empty'              | ''         || 'kind must be one of item, note, log, file, term, topic.'
        'the kind is blank'              | '   '      || 'kind must be one of item, note, log, file, term, topic.'
        'the kind is a story'            | 'story'    || 'kind must be one of item, note, log, file, term, topic.'
        'the kind is plural'             | 'items'    || 'kind must be one of item, note, log, file, term, topic.'
        'the kind is a message'          | 'message'  || 'kind must be one of item, note, log, file, term, topic.'
        'the kind holds two kinds'       | 'item,note' || 'kind must be one of item, note, log, file, term, topic.'
    }

    // ---- links ----

    def "links should read one page of the record's links with the kind made lower case when the solution is visible"() {

        given:
        LinkPage page = new LinkPage('item', 100, [], 10, 20, 3)

        when:
        LinkPage p = service.links(REX, 1, ' Item ', 100, 10, 20)

        then:
        1 * links.links(1, 'item', 100, 10, 20) >> page
        p.is(page)
    }

    @Unroll
    def "#seq. links should ask for offset #expectedOffset and page size #expectedSize when #scenario"() {

        when:
        service.links(TONY, 1, 'note', 300, offset, pageSize)

        then:
        1 * links.links(1, 'note', 300, expectedOffset, expectedSize) >> emptyPage('note', 300)

        where:
        seq | scenario                         | offset      | pageSize    || expectedOffset | expectedSize
        1   | 'nothing is asked'               | 0           | 0           || 0              | 50
        2   | 'the offset is negative'         | -5          | 10          || 0              | 10
        3   | 'the offset is the minimum'      | -2147483648 | 10          || 0              | 10
        4   | 'the offset is positive'         | 75          | 25          || 75             | 25
        5   | 'the page size is negative'      | 0           | -1          || 0              | 50
        6   | 'one row is asked'               | 0           | 1           || 0              | 1
        7   | 'the cap is asked'               | 0           | 500         || 0              | 500
        8   | 'one over the cap is asked'      | 0           | 501         || 0              | 500
        9   | 'far over the cap is asked'      | 0           | 100000      || 0              | 500
    }

    def "links should refuse an unknown kind and read nothing"() {

        when:
        service.links(TONY, 1, 'story', 1, 0, 50)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == KIND_MESSAGE
        0 * links.links(*_)
    }

    @Unroll
    def "link should join the two records attributed to #expectedCode and answer the from record's first page when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LinkPage page = emptyPage('note', 300)

        when:
        LinkPage p = service.link(caller, 1, ' NOTE ', 300, 'Item', 100)

        then:
        1 * links.link(1, 'note', 300, 'item', 100, expectedCode) >> 40

        then:
        1 * links.links(1, 'note', 300, 0, 50) >> page
        p.is(page)

        where:
        scenario               | callerName || expectedCode
        'a person links them'  | 'TONY'     || 'tony'
        'the house links them' | 'HOUSE'    || 'felix'
        'a client links them'  | 'REX'      || 'rex'
    }

    @Unroll
    def "link should refuse an unknown kind and write nothing when #scenario"() {

        when:
        service.link(TONY, 1, fromKind, 1, toKind, 2)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * links.link(*_)
        0 * links.links(*_)

        where:
        scenario                        | fromKind | toKind  || expectedMessage
        'the from kind is unknown'      | 'story'  | 'item'  || 'kind must be one of item, note, log, file, term, topic.'
        'the to kind is unknown'        | 'item'   | 'story' || 'kind must be one of item, note, log, file, term, topic.'
        'the from kind is null'         | null     | 'item'  || 'kind must be one of item, note, log, file, term, topic.'
        'the to kind is blank'          | 'item'   | ' '     || 'kind must be one of item, note, log, file, term, topic.'
    }

    def "link should pass the refusal through and read no page when the repository refuses the pair"() {

        when:
        service.link(TONY, 1, 'item', 100, 'note', 300)

        then:
        1 * links.link(1, 'item', 100, 'note', 300, 'tony') >> { throw new SolutionRuleException('Those two are already linked. Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'Those two are already linked. Nothing was saved.'
        0 * links.links(*_)
    }

    def "unlink should remove the link when it belongs to the named solution"() {

        when:
        service.unlink(REX, 1, 40)

        then:
        1 * links.unlink(40) >> true
    }

    @Unroll
    def "unlink should throw link not found and remove nothing when #scenario"() {

        when:
        service.unlink(TONY, solutionId, linkId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * links.unlink(_)

        where:
        scenario                                    | solutionId | linkId || expectedMessage
        'the id is zero'                            | 1          | 0      || 'Link not found.'
        'the id is negative'                        | 1          | -1     || 'Link not found.'
        'the link does not exist'                   | 1          | 99     || 'Link not found.'
        'the link belongs to another solution'      | 1          | 41     || 'Link not found.'
        'the link of solution 1 is named on 2'      | 2          | 40     || 'Link not found.'
    }

    def "unlink should not reach the repository when the id is zero"() {

        when:
        service.unlink(TONY, 1, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * links.findLink(_)
        0 * links.unlink(_)
    }

    def "unlink should check the solution before the link when the solution is hidden"() {

        when:
        service.unlink(REX, 2, 41)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * links.findLink(_)
        0 * links.unlink(_)
    }

    def "unlink should throw link not found when the repository removed nothing"() {

        when:
        service.unlink(TONY, 1, 40)

        then:
        1 * links.unlink(40) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Link not found.'
    }

    // ---- the Link picker ----

    @Unroll
    def "#seq. targets should ask for search #expectedSearch, kind #expectedKind and page size #expectedSize when #scenario"() {

        given:
        LinkTarget target = new LinkTarget('topic', 5, 'Charset', null, null, false, false)

        when:
        List<LinkTarget> got = service.targets(REX, 1, search, kind, recordId, pageSize)

        then:
        1 * links.targets(1, expectedSearch, expectedKind, recordId, expectedSize) >> [target]
        got == [target]

        where:
        seq | scenario                          | search        | kind     | recordId | pageSize || expectedSearch | expectedKind | expectedSize
        1   | 'nothing is asked'                | null          | null     | null     | 0        || null           | null         | 30
        2   | 'the search is empty'             | ''            | null     | null     | 0        || null           | null         | 30
        3   | 'the search is blank'             | '   '         | null     | null     | 0        || null           | null         | 30
        4   | 'the search is padded'            | '  charset '  | null     | null     | 0        || 'charset'      | null         | 30
        5   | 'the kind is empty'               | null          | ''       | 100      | 0        || null           | null         | 30
        6   | 'the kind is blank'               | null          | '  '     | 100      | 0        || null           | null         | 30
        7   | 'the kind is in capitals'         | null          | 'ITEM'   | 100      | 0        || null           | 'item'       | 30
        8   | 'the page size is negative'       | null          | null     | null     | -5       || null           | null         | 30
        9   | 'one row is asked'                | null          | null     | null     | 1        || null           | null         | 1
        10  | 'the picker cap is asked'         | null          | null     | null     | 100      || null           | null         | 100
        11  | 'one over the picker cap is asked'| null          | null     | null     | 101      || null           | null         | 100
        12  | 'far over the picker cap is asked'| 'x'           | 'note'   | 300      | 5000     || 'x'            | 'note'       | 100
    }

    def "targets should refuse an unknown kind and search nothing"() {

        when:
        service.targets(TONY, 1, 'charset', 'story', 1, 30)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == KIND_MESSAGE
        0 * links.targets(*_)
    }

    // ---- topics ----

    def "topics should answer the page with its counts and paging when the solution is visible"() {

        when:
        TopicPage p = service.topics(REX, 1, '  charset ', 10, 20)

        then:
        1 * links.topicCounts(1, 'charset') >> [total: 3, all: 9]
        1 * links.topics(1, 'charset', 10, 20) >> [topicRows[5]]
        p.topics()*.id() == [5]
        p.counts() == [total: 3, all: 9]
        p.offset() == 10
        p.pageSize() == 20
        p.total() == 3
    }

    @Unroll
    def "topics should ask for search #expectedSearch, offset #expectedOffset and page size #expectedSize when #scenario"() {

        when:
        service.topics(TONY, 1, search, offset, pageSize)

        then:
        1 * links.topicCounts(1, expectedSearch) >> [total: 0, all: 0]
        1 * links.topics(1, expectedSearch, expectedOffset, expectedSize) >> []

        where:
        scenario                         | search   | offset | pageSize || expectedSearch | expectedOffset | expectedSize
        'nothing is asked'               | null     | 0      | 0        || null           | 0              | 50
        'the search is blank'            | '  '     | -3     | -1       || null           | 0              | 50
        'the search is padded'           | ' api '  | 5      | 1        || 'api'          | 5              | 1
        'the cap is asked'               | null     | 0      | 500      || null           | 0              | 500
        'one over the cap is asked'      | null     | 0      | 501      || null           | 0              | 500
    }

    def "topics should answer a total of zero when there is no total counter"() {

        when:
        TopicPage p = service.topics(TONY, 1, null, 0, 50)

        then:
        1 * links.topicCounts(1, null) >> [all: 4]
        1 * links.topics(*_) >> []
        p.total() == 0
        p.counts() == [all: 4]
    }

    def "topic should answer the topic when the caller may see its solution"() {

        expect:
        service.topic(TONY, 6).id() == 6
        service.topic(REX, 5).solutionId() == 1
        service.topic(HOUSE, 6).solutionId() == 2
    }

    @Unroll
    def "topic should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.topic(caller, topicId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                              | callerName | topicId || expectedMessage
        'the id is zero'                      | 'TONY'     | 0       || 'Topic not found.'
        'the id is negative'                  | 'TONY'     | -1      || 'Topic not found.'
        'the topic does not exist'            | 'TONY'     | 99      || 'Topic not found.'
        'the client was not given its solution' | 'REX'    | 6       || 'Solution not found.'
        'the client was given nothing'        | 'ZED'      | 5       || 'Solution not found.'
    }

    def "topic should not reach the repository when the id is zero"() {

        when:
        service.topic(TONY, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * links.findTopic(_)
    }

    @Unroll
    def "saveTopic should add the topic attributed to #expectedCode and answer it read back when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        TopicDraft draft = new TopicDraft(0, 1, 'Charset', 'windows-1252')

        when:
        Topic t = service.saveTopic(caller, draft)

        then:
        1 * links.saveTopic(draft, expectedCode) >> 5
        t.id() == 5
        t.solutionId() == 1

        where:
        scenario               | callerName || expectedCode
        'a person adds it'     | 'TONY'     || 'tony'
        'the house adds it'    | 'HOUSE'    || 'felix'
        'a client adds it'     | 'REX'      || 'rex'
    }

    @Unroll
    def "saveTopic should save nothing when an add targets #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.saveTopic(caller, new TopicDraft(0, solutionId, 'Charset', null))

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * links.saveTopic(*_)

        where:
        scenario                                | callerName | solutionId || expectedMessage
        'solution zero'                         | 'TONY'     | 0          || 'Solution not found.'
        'a solution that does not exist'        | 'TONY'     | 9          || 'Solution not found.'
        'a solution the client was not given'   | 'REX'      | 2          || 'Solution not found.'
        'a solution of a client given none'     | 'ZED'      | 1          || 'Solution not found.'
    }

    def "saveTopic should check the topic's own solution when an edit names another solution"() {

        when: 'rex edits a topic of solution 2 while naming solution 1, which he can see'
        service.saveTopic(REX, new TopicDraft(6, 1, 'Mine now', null))

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * links.saveTopic(*_)
    }

    def "saveTopic should edit the topic when the draft's solution is left at zero"() {

        given:
        TopicDraft draft = new TopicDraft(5, 0, null, 'New summary')

        when:
        Topic t = service.saveTopic(REX, draft)

        then:
        1 * links.saveTopic(draft, 'rex') >> 5
        t.id() == 5
    }

    def "saveTopic should throw topic not found and save nothing when an edit targets a missing topic"() {

        when:
        service.saveTopic(TONY, new TopicDraft(77, 1, 'x', null))

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Topic not found.'
        0 * links.saveTopic(*_)
    }

    @Unroll
    def "saveTopic should throw topic not found when the repository answers a #scenario id"() {

        when:
        service.saveTopic(TONY, new TopicDraft(0, 1, 'Charset', null))

        then:
        1 * links.saveTopic(*_) >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario      | savedId || expectedMessage
        'zero'        | 0       || 'Topic not found.'
        'negative'    | -1      || 'Topic not found.'
    }

    def "saveTopic should pass a refusal through when the repository refuses a repeat name"() {

        when:
        service.saveTopic(TONY, new TopicDraft(0, 1, 'charset', null))

        then:
        1 * links.saveTopic(*_) >> { throw new SolutionRuleException('This solution already has the topic "Charset". Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'This solution already has the topic "Charset". Nothing was saved.'
    }

    def "deleteTopic should delete a visible topic, refuse a hidden one and throw not found when nothing was removed"() {

        when:
        service.deleteTopic(REX, 5)

        then:
        1 * links.deleteTopic(5) >> true

        when:
        service.deleteTopic(REX, 6)

        then:
        SolutionNotFoundException hidden = thrown()
        hidden.message == 'Solution not found.'
        0 * links.deleteTopic(_)

        when:
        service.deleteTopic(TONY, 6)

        then:
        1 * links.deleteTopic(6) >> false
        SolutionNotFoundException gone = thrown()
        gone.message == 'Topic not found.'
    }

    def "deleteTopic should throw topic not found and remove nothing when the topic does not exist"() {

        when:
        service.deleteTopic(TONY, 99)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Topic not found.'
        0 * links.deleteTopic(_)
    }

    // ---- since you were last here ----

    @Unroll
    def "visit should count the visit of #expectedCode and then read what changed since the last one when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LocalDateTime previous = getLocalDateTime('11/01/2026 09:30')
        LocalDateTime last = getLocalDateTime('11/03/2026 14:00')
        Change change = new Change('topic', 5, 'Charset', null, 'added', 'zed', 'Zed', last)

        when:
        SincePage p = service.visit(caller, 1)

        then:
        1 * links.touch(1, expectedCode) >> new Visit(previous, last)

        then:
        1 * links.visit(1, expectedCode) >> new Visit(previous, last)
        1 * links.changeCounts(1, previous, expectedCode) >> [total: 2, 'kind:topic': 2]
        1 * links.changes(1, previous, expectedCode, 0, 50) >> [change]
        p.since() == previous
        !p.firstVisit()
        p.changes() == [change]
        p.counts() == [total: 2, 'kind:topic': 2]
        p.offset() == 0
        p.pageSize() == 50
        p.total() == 2

        where:
        scenario                 | callerName || expectedCode
        'a person opens it'      | 'TONY'     || 'tony'
        'the house opens it'     | 'HOUSE'    || 'felix'
        'a client opens it'      | 'REX'      || 'rex'
    }

    @Unroll
    def "since should look back seven days and mark a first visit when #scenario"() {

        given:
        Visit visit = hasVisit ? new Visit(null, getLocalDateTime('11/03/2026 14:00')) : null
        LocalDateTime askedCounts = null
        LocalDateTime askedChanges = null
        LocalDateTime before = LocalDateTime.now().minusDays(SolutionLinkService.FIRST_VISIT_DAYS).withNano(0)

        when:
        SincePage p = service.since(TONY, 1, 0, 50)
        LocalDateTime after = LocalDateTime.now().minusDays(SolutionLinkService.FIRST_VISIT_DAYS)

        then:
        1 * links.visit(1, 'tony') >> visit
        1 * links.changeCounts(1, _, 'tony') >> { int s, LocalDateTime d, String m -> askedCounts = d; [total: 0] }
        1 * links.changes(1, _, 'tony', 0, 50) >> { int s, LocalDateTime d, String m, int o, int z -> askedChanges = d; [] }
        p.firstVisit()
        !p.since().isBefore(before)
        !p.since().isAfter(after)
        p.since().nano == 0
        askedCounts == p.since()
        askedChanges == p.since()
        SolutionLinkService.FIRST_VISIT_DAYS == 7

        where:
        scenario                                     | hasVisit || unused
        'the member never had the solution open'     | false    || null
        'the member has had it open only once'       | true     || null
    }

    @Unroll
    def "since should start at the previous visit and ask for offset #expectedOffset and page size #expectedSize when #scenario"() {

        given:
        LocalDateTime previous = getLocalDateTime('11/01/2026 09:30')

        when:
        SincePage p = service.since(HOUSE, 1, offset, pageSize)

        then:
        1 * links.visit(1, 'felix') >> new Visit(previous, getLocalDateTime('11/03/2026 14:00'))
        1 * links.changeCounts(1, previous, 'felix') >> [total: 7]
        1 * links.changes(1, previous, 'felix', expectedOffset, expectedSize) >> []
        !p.firstVisit()
        p.since() == previous
        p.offset() == expectedOffset
        p.pageSize() == expectedSize
        p.total() == 7

        where:
        scenario                         | offset | pageSize || expectedOffset | expectedSize
        'nothing is asked'               | 0      | 0        || 0              | 50
        'the offset is negative'         | -4     | 10       || 0              | 10
        'a later page is asked'          | 20     | 10       || 20             | 10
        'the cap is asked'               | 0      | 500      || 0              | 500
        'one over the cap is asked'      | 0      | 501      || 0              | 500
    }

    def "since should answer a total of zero when there is no total counter"() {

        when:
        SincePage p = service.since(REX, 1, 0, 50)

        then:
        1 * links.visit(1, 'rex') >> null
        1 * links.changeCounts(*_) >> ['kind:item': 3]
        1 * links.changes(*_) >> []
        p.total() == 0
        p.counts() == ['kind:item': 3]
    }

    def "since should never count a visit when it is only read"() {

        when:
        service.since(TONY, 1, 0, 50)

        then:
        0 * links.touch(*_)
        0 * links.caughtUp(*_)
        1 * links.changeCounts(*_) >> [total: 0]
    }

    @Unroll
    def "caughtUp should start since now for #expectedCode and then read what changed when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LocalDateTime now = getLocalDateTime('11/04/2026 10:15')

        when:
        SincePage p = service.caughtUp(caller, 1)

        then:
        1 * links.caughtUp(1, expectedCode)

        then:
        1 * links.visit(1, expectedCode) >> new Visit(now, now)
        1 * links.changeCounts(1, now, expectedCode) >> [total: 0]
        1 * links.changes(1, now, expectedCode, 0, 50) >> []
        p.since() == now
        !p.firstVisit()
        p.total() == 0
        0 * links.touch(*_)

        where:
        scenario                 | callerName || expectedCode
        'a person reads it all'  | 'TONY'     || 'tony'
        'a client reads it all'  | 'REX'      || 'rex'
    }
}
