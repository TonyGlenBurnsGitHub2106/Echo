package com.echo

import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionTopicKindModels.OpenItem
import com.echo.SolutionTopicKindModels.ProvedFalse
import com.echo.SolutionTopicKindModels.TopicType

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): the shapes of topic kinds -- the four kinds a topic may be (KINDS), the four tags
 * offered before a solution has any (STARTER_TAGS), and the row mappers of a kind of part (TopicType) and of
 * an open item linked to a topic (OpenItem), plus the plain record the premise switch answers (ProvedFalse).
 */
class SolutionTopicKindModelsSpec extends EchoSpockBaseSpec {

    def "KINDS should hold exactly the four kinds of topic when read"() {

        expect:
        SolutionTopicKindModels.KINDS == ['subject', 'tag', 'part', 'assumption'] as Set
        SolutionTopicKindModels.KINDS.size() == 4
        !SolutionTopicKindModels.KINDS.contains('topic')
        !SolutionTopicKindModels.KINDS.contains('Part')
        !SolutionTopicKindModels.KINDS.contains('chapter')
    }

    def "KINDS should refuse a change when anything tries to add a kind"() {

        when:
        SolutionTopicKindModels.KINDS.add('chapter')

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    def "KINDS should agree with the link service's kind check when the kind is #scenario"() {

        expect:
        SolutionTopicKindModels.KINDS.contains(kind) == expectedKnown
        SolutionLinkService.topicKind(kind, false) == expected

        where:
        scenario        | kind         || expectedKnown | expected
        'a subject'     | 'subject'    || true          | 'subject'
        'a tag'         | 'tag'        || true          | 'tag'
        'a part'        | 'part'       || true          | 'part'
        'an assumption' | 'assumption' || true          | 'assumption'
    }

    def "STARTER_TAGS should offer the four starter tags in their order when read"() {

        expect:
        SolutionTopicKindModels.STARTER_TAGS == ['Important', 'Risk', 'Idea', 'Parking lot']
    }

    def "STARTER_TAGS should refuse a change when anything tries to add a tag"() {

        when:
        SolutionTopicKindModels.STARTER_TAGS.add('Later')

        then:
        thrown(UnsupportedOperationException)
    }

    def "TopicType.of should read every column when the row is complete"() {

        when:
        TopicType t = TopicType.of([SolutionTopicTypeID: 3, SolutionID: 1, TypeName: 'Chapter', FigureLabel: 'words',
                                    SortOrder: 2, PartCount: 7, CreatedDate: getLocalDateTime('11/04/2026 09:00')])

        then:
        t == new TopicType(3, 1, 'Chapter', 'words', 2, 7)
    }

    def "TopicType.of should answer zeros and nulls when the row is empty"() {

        expect:
        TopicType.of([:]) == new TopicType(0, 0, null, null, 0, 0)
    }

    @Unroll
    def "TopicType.of should read the part count as #expected when the driver answers #scenario"() {

        when:
        TopicType t = TopicType.of([SolutionTopicTypeID: 3, PartCount: value])

        then:
        t.partCount() == expected
        t.id() == 3

        where:
        scenario                | value  || expected
        'an int'                | 4      || 4
        'a long'                | 4L     || 4
        'padded text'           | ' 4 '  || 4
        'text that is no number' | 'many' || 0
        'a null'                | null   || 0
    }

    def "OpenItem.of should read the record, title and second line when the row is complete"() {

        expect:
        OpenItem.of([Kind: 'item', RecordID: 100, Title: 'Submit the 837', Sub: 'Deploy | Not started']) ==
            new OpenItem(100, 'Submit the 837', 'Deploy | Not started')
    }

    def "OpenItem.of should answer a zero id and nulls when the row is empty"() {

        expect:
        OpenItem.of([:]) == new OpenItem(0, null, null)
    }

    def "ProvedFalse should keep what it is given when built"() {

        given:
        Topic t = Topic.of([SolutionTopicID: 5, SolutionID: 1, TopicName: 'The 837 path is live', TopicKindCode: 'assumption',
                            AssumptionHolds: false])
        OpenItem cancelled = new OpenItem(100, 'Submit the 837', 'Deploy | Skipped')
        OpenItem open = new OpenItem(101, 'Watch the 835', 'Deploy | Not started')

        when:
        ProvedFalse p = new ProvedFalse(t, [cancelled], [open], 410, 42)

        then:
        p.topic().is(t)
        p.topic().holds() == false
        p.cancelled() == [cancelled]
        p.stillOpen() == [open]
        p.logId() == 410
        p.logSequenceNumber() == 42
        new ProvedFalse(t, [], [], null, null).logId() == null
    }
}
