package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionLinkModels.Change
import com.echo.SolutionLinkModels.Link
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
 * Story 5695: the shapes of Solution links, topics and "Since you were last here" -- the row mappers
 * (Link, StoredLink, LinkTarget, Topic, Change, Visit), the eight kinds a link may join (Story 5695 added question and decision), and the page-size
 * rule every list shares. Release 3: a link's topicKind, a topic's kind with what a part or an assumption carries,
 * and TopicDraft's four-field constructor.
 */
class SolutionLinkModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('11/02/2026 12:00')
    static final LocalDateTime U = getLocalDateTime('11/03/2026 08:30')

    /** One date column in the form a row may carry it: LocalDateTime | Timestamp | text | garbage | null. */
    private static Object dateValue(String form, LocalDateTime value) {
        switch (form) {
            case 'LocalDateTime': return value
            case 'Timestamp':     return Timestamp.valueOf(value)
            case 'text':          return value.toString().replace('T', ' ')
            case 'garbage':       return 'not a date'
            default:              return null
        }
    }

    /** One flag column in the form a row may carry it: true | 1 | 'true' | 'TRUE' | 0 | 'no' | null. */
    private static Object flagValue(String form) {
        switch (form) {
            case 'boolean true':  return true
            case 'number one':    return 1
            case 'text true':     return 'true'
            case 'text TRUE':     return 'TRUE'
            case 'text one':      return '1'
            case 'number zero':   return 0
            case 'text no':       return 'no'
            default:              return null
        }
    }

    def "KINDS should hold exactly the eight kinds a link may join"() {

        expect:
        SolutionLinkModels.KINDS == ['item', 'note', 'log', 'file', 'term', 'topic', 'question', 'decision'] as Set
        SolutionLinkModels.KINDS.size() == 8
        !SolutionLinkModels.KINDS.contains('message')
        !SolutionLinkModels.KINDS.contains('Item')
    }

    def "KINDS should refuse a change when anything tries to add a kind"() {

        when:
        SolutionLinkModels.KINDS.add('story')

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    def "#seq. page should answer #expected when #scenario"() {

        expect:
        SolutionLinkModels.page(pageSize, max) == expected

        where:
        seq | scenario                                 | pageSize    | max || expected
        1   | 'zero is asked'                          | 0           | 500 || 50
        2   | 'a negative is asked'                    | -1          | 500 || 50
        3   | 'the smallest int is asked'              | -2147483648 | 500 || 50
        4   | 'one is asked'                           | 1           | 500 || 1
        5   | 'the default is asked'                   | 50          | 500 || 50
        6   | 'one under the cap is asked'             | 499         | 500 || 499
        7   | 'the cap is asked'                       | 500         | 500 || 500
        8   | 'one over the cap is asked'              | 501         | 500 || 500
        9   | 'the largest int is asked'               | 2147483647  | 500 || 500
        10  | 'the picker cap is asked'                | 100         | 100 || 100
        11  | 'one over the picker cap is asked'       | 101         | 100 || 100
        12  | 'the picker default is asked'            | 30          | 100 || 30
        13  | 'zero is asked under the picker cap'     | 0           | 100 || 50
    }

    def "Link.of should read every column when the row is complete"() {

        when:
        Link l = Link.of([SolutionLinkID: 40, Kind: 'note', RecordID: 300, Title: 'Runbook', Sub: 'Runbook type',
                          RecordDate: T, IsClosed: 1, LinkedDate: Timestamp.valueOf(U), LinkedByCode: 'rex',
                          LinkedByName: 'Rex', TotalCount: 9])

        then:
        l == new Link(40, 'note', 300, 'Runbook', 'Runbook type', T, true, U, 'rex', 'Rex', null)
    }

    def "Link.of should read the linked topic's kind when the other end is a topic"() {

        when:
        Link l = Link.of([SolutionLinkID: 41, Kind: 'topic', RecordID: 5, Title: 'Risk', Sub: 'Tag', RecordDate: T, IsClosed: 0,
                          LinkedDate: U, LinkedByCode: 'tony', LinkedByName: 'Tony', TopicKind: 'tag', TotalCount: 1])

        then:
        l == new Link(41, 'topic', 5, 'Risk', 'Tag', T, false, U, 'tony', 'Tony', 'tag')
        l.topicKind() == 'tag'
    }

    def "Link.of should answer zeros, nulls and false when the row is empty"() {

        expect:
        Link.of([:]) == new Link(0, null, 0, null, null, null, false, null, null, null, null)
    }

    @Unroll
    def "Link.of should read the record date as #scenario"() {

        given:
        Object value = dateValue(form, T)
        LocalDateTime expected = expectRead ? T : null

        when:
        Link l = Link.of([SolutionLinkID: 1, RecordDate: value, LinkedDate: value])

        then:
        l.recordDate() == expected
        l.linkedDate() == expected

        where:
        scenario                       | form            || expectRead
        'a LocalDateTime'              | 'LocalDateTime' || true
        'a SQL Timestamp'              | 'Timestamp'     || true
        'text with a space'            | 'text'          || true
        'null when it is unreadable'   | 'garbage'       || false
        'null when it is missing'      | 'none'          || false
    }

    def "StoredLink.of should read both ends in stored order when the row is complete"() {

        when:
        StoredLink s = StoredLink.of([SolutionLinkID: 40, SolutionID: 1, FromKind: 'item', FromID: 100, ToKind: 'note', ToID: 300,
                                      CreatedByMemberID: 2, CreatedDate: T])

        then:
        s == new StoredLink(40, 1, 'item', 100, 'note', 300)
    }

    def "StoredLink.of should answer zeros and nulls when the row is empty"() {

        expect:
        StoredLink.of([:]) == new StoredLink(0, 0, null, 0, null, 0)
    }

    def "LinkTarget.of should read every column when the row is complete"() {

        when:
        LinkTarget t = LinkTarget.of([Kind: 'topic', RecordID: 7, Title: 'Charset', Sub: 'Encoding', RecordDate: T,
                                      IsClosed: false, AlreadyLinked: true])

        then:
        t == new LinkTarget('topic', 7, 'Charset', 'Encoding', T, false, true)
    }

    @Unroll
    def "#seq. LinkTarget.of should read alreadyLinked and closed #expected when the column is #scenario"() {

        given:
        Object value = flagValue(scenario)

        when:
        LinkTarget t = LinkTarget.of([Kind: 'item', RecordID: 1, AlreadyLinked: value, IsClosed: value])

        then:
        t.alreadyLinked() == expected
        t.closed() == expected

        where:
        seq | scenario          || expected
        1   | 'boolean true'    || true
        2   | 'number one'      || true
        3   | 'text true'       || true
        4   | 'text TRUE'       || true
        5   | 'text one'        || true
        6   | 'number zero'     || false
        7   | 'text no'         || false
        8   | 'missing'         || false
    }

    def "LinkTarget.of should answer zeros, nulls and false when the row is empty"() {

        expect:
        LinkTarget.of([:]) == new LinkTarget(null, 0, null, null, null, false, false)
    }

    def "Topic.of should read every column when the row is complete"() {

        when:
        Topic t = Topic.of([SolutionTopicID: 5, SolutionID: 1, TopicName: 'Charset', TopicSummary: 'windows-1252',
                            Truncated: 1, AuthorCode: 'rex', AuthorName: 'Rex', ModifiedBy: 'tony', CreatedDate: T,
                            ModifiedDate: '2026-11-03 08:30:00', MemberCount: 4, LatestDate: Timestamp.valueOf(U)])

        then:
        t == new Topic(5, 1, 'Charset', 'windows-1252', true, 'rex', 'Rex', 'tony', T, U, 4, U,
                       'subject', null, null, null, null, null, null, false, null, null, 0)
    }

    def "Topic.of should read a part's kind of part, status, number and order when the row is a part"() {

        when:
        Topic t = Topic.of([SolutionTopicID: 9, SolutionID: 1, TopicName: 'Cherry Creek', TopicKindCode: 'part', SolutionTopicTypeID: 3,
                            TypeName: 'Chapter', FigureLabel: 'words', StatusCode: 'done', StatusName: 'Done', ColorHex: '#16A34A',
                            IsClosed: 1, FigureValue: new BigDecimal('4200.50'), AssumptionHolds: null, SortOrder: 2])

        then:
        t == new Topic(9, 1, 'Cherry Creek', null, false, null, null, null, null, null, 0, null,
                       'part', 3, 'Chapter', 'words', 'done', 'Done', '#16A34A', true, new BigDecimal('4200.50'), null, 2)
    }

    @Unroll
    def "Topic.of should read holds as #expected when an assumption's column is #scenario"() {

        when:
        Topic t = Topic.of([SolutionTopicID: 5, TopicKindCode: 'assumption', AssumptionHolds: value])

        then:
        t.kindCode() == 'assumption'
        t.holds() == expected

        where:
        scenario          | value || expected
        'boolean true'    | true  || true
        'boolean false'   | false || false
        'number one'      | 1     || true
        'number zero'     | 0     || false
        'null'            | null  || null
    }

    @Unroll
    def "Topic.of should read the number as #expected when the driver answers #scenario"() {

        when:
        Topic t = Topic.of([SolutionTopicID: 9, TopicKindCode: 'part', FigureValue: value])

        then:
        t.figure() == expected

        where:
        scenario       | value || expected
        'a BigDecimal' | 12.5  || 12.5
        'an integer'   | 7     || 7
        'a null'       | null  || null
        'a word'       | 'ten' || null
    }

    @Unroll
    def "Topic.of should answer type id #expected when the column is #scenario"() {

        expect:
        Topic.of([SolutionTopicID: 9, SolutionTopicTypeID: value]).typeId() == expected

        where:
        scenario   | value || expected
        'positive' | 3     || 3
        'zero'     | 0     || null
        'missing'  | null  || null
    }

    def "Topic.of should answer zeros, nulls and false, and the kind subject, when the row is empty"() {

        expect:
        Topic.of([:]) == new Topic(0, 0, null, null, false, null, null, null, null, null, 0, null,
                                   'subject', null, null, null, null, null, null, false, null, null, 0)
    }

    def "Topic.of should read a numeric text count when the driver answers text"() {

        expect:
        Topic.of([SolutionTopicID: '5', MemberCount: ' 3 ']).memberCount() == 3
        Topic.of([SolutionTopicID: '5', MemberCount: ' 3 ']).id() == 5
        Topic.of([MemberCount: 'many']).memberCount() == 0
    }

    def "Change.of should read every column when the row is complete"() {

        when:
        Change c = Change.of([Kind: 'message', RecordID: 12, Title: 'Untitled thread', Sub: 'hello', Action: 'wrote',
                              Who: 'rex', WhoName: 'Rex', ChangedDate: T])

        then:
        c == new Change('message', 12, 'Untitled thread', 'hello', 'wrote', 'rex', 'Rex', T)
    }

    def "Change.of should answer zeros and nulls when the row is empty"() {

        expect:
        Change.of([:]) == new Change(null, 0, null, null, null, null, null, null)
    }

    def "Visit.of should read both dates when the member has been here before"() {

        expect:
        Visit.of([PreviousSeenDate: T, LastSeenDate: Timestamp.valueOf(U)]) == new Visit(T, U)
    }

    def "Visit.of should answer no previous visit when the member has been here only once"() {

        when:
        Visit v = Visit.of([PreviousSeenDate: null, LastSeenDate: U])

        then:
        v.previousSeen() == null
        v.lastSeen() == U
        Visit.of([:]) == new Visit(null, null)
    }

    def "the plain records should keep what they are given when built"() {

        given:
        Link l = new Link(40, 'note', 300, 'Runbook', null, T, false, U, 'rex', 'Rex', null)

        expect:
        new LinkPage('item', 100, [l], 10, 20, 31).links() == [l]
        new LinkPage('item', 100, [l], 10, 20, 31).total() == 31
        new TopicPage([], [total: 3, all: 9], 0, 50, 3).counts() == [total: 3, all: 9]
        new TopicDraft(0, 1, 'Charset', null).summary() == null
        new TopicDraft(5, 0, null, '').summary() == ''
        new TopicDraft(5, 0, null, '') == new TopicDraft(5, 0, null, '', null, null, null, null, false, null)
        new TopicDraft(9, 1, 'Cherry Creek', null, 'part', 'Chapter', 'done', 12.5, true, null).figure() == 12.5
        new TopicDraft(5, 1, 'P', null, 'assumption', null, null, null, false, false).holds() == Boolean.FALSE
        new SincePage(T, true, [], [total: 0], 0, 50, 0).firstVisit()
        new SincePage(T, false, [], [total: 0], 0, 50, 0).since() == T
    }
}
