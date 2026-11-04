package com.echo

import java.time.LocalDateTime

import com.echo.SolutionOrientationModels.Activity
import com.echo.SolutionOrientationModels.ActivityPage
import com.echo.SolutionOrientationModels.LeftOff
import com.echo.SolutionOrientationModels.Lens
import com.echo.SolutionOrientationModels.LensItem
import com.echo.SolutionOrientationModels.SummaryAge

import spock.lang.Unroll

/**
 * Story 5695: "where am I" in a solution -- the age of "Where things stand" is read only after access is
 * checked, so a hidden solution reads nothing; anyone who may see the solution may read it.
 * Release 4: the lens (bucket tidied or refused, page size and offset clamped, every bucket counted, a later
 * page's other counts read from a second call), the note for next time (trimmed, null as "", over 1,000
 * refused before any write, read back after the write), and the activity feed (days, who, kind, topic, page
 * size and offset tidied or refused, and the total read only when it can be more than zero). Every one of
 * them checks access before anything else, so a hidden solution reads and writes nothing.
 */
class SolutionOrientationServiceSpec extends SolutionServiceSupport {

    SolutionOrientationRepository repo = Mock()
    SolutionOrientationService service = new SolutionOrientationService(repo, access)

    @Unroll
    def "summaryAge should answer what the repository read when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        SummaryAge age = new SummaryAge(getLocalDateTime('10/01/2026 12:30'), 34, true)

        when:
        SummaryAge got = service.summaryAge(caller, solutionId)

        then:
        1 * repo.summaryAge(solutionId) >> age
        got.is(age)

        where:
        scenario                         | callerName | solutionId || unused
        'a person reads it'              | 'TONY'     | 2          || null
        'the house reads it'             | 'HOUSE'    | 1          || null
        'a client reads a shared one'    | 'REX'      | 1          || null
    }

    def "summaryAge should pass an empty age through when the solution has no summary"() {

        when:
        SummaryAge got = service.summaryAge(TONY, 1)

        then:
        1 * repo.summaryAge(1) >> new SummaryAge(null, null, false)
        got.writtenDate() == null
        got.ageDays() == null
        !got.stale()
    }

    @Unroll
    def "summaryAge should read nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.summaryAge(caller, solutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                                 | callerName | solutionId || expectedMessage
        'the id is zero'                         | 'TONY'     | 0          || 'Solution not found.'
        'the solution does not exist'            | 'TONY'     | 9          || 'Solution not found.'
        'the client was not given the solution'  | 'REX'      | 2          || 'Solution not found.'
        'the client was given nothing'           | 'ZED'      | 1          || 'Solution not found.'
    }

    // ---- Release 4: access comes first ----

    @Unroll
    def "#seq. #method should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        switch (method) {
            case 'lens':        service.lens(caller, solutionId, input, 0, 50); break
            case 'leftOff':     service.leftOff(caller, solutionId); break
            case 'saveLeftOff': service.saveLeftOff(caller, solutionId, input == '@1001@' ? textOf(1001) : input); break
            default:            service.feed(caller, solutionId, null, null, input, null, 0, 50)
        }

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        seq | scenario                                              | method        | callerName | solutionId | input       || expectedMessage
        1   | 'the id is zero'                                      | 'lens'        | 'TONY'     | 0          | null        || 'Solution not found.'
        2   | 'the solution does not exist'                         | 'lens'        | 'TONY'     | 9          | null        || 'Solution not found.'
        3   | 'the client was not given the solution'               | 'lens'        | 'REX'      | 2          | null        || 'Solution not found.'
        4   | 'the client was given nothing'                        | 'lens'        | 'ZED'      | 1          | null        || 'Solution not found.'
        5   | 'a hidden solution is asked for a bucket that is not' | 'lens'        | 'REX'      | 2          | 'later'     || 'Solution not found.'
        6   | 'the solution does not exist'                         | 'leftOff'     | 'TONY'     | 9          | null        || 'Solution not found.'
        7   | 'the client was not given the solution'               | 'leftOff'     | 'REX'      | 2          | null        || 'Solution not found.'
        8   | 'the solution does not exist'                         | 'saveLeftOff' | 'TONY'     | 9          | 'Next time' || 'Solution not found.'
        9   | 'the client was not given the solution'               | 'saveLeftOff' | 'REX'      | 2          | 'Next time' || 'Solution not found.'
        10  | 'a hidden solution is sent a note too long to keep'   | 'saveLeftOff' | 'REX'      | 2          | '@1001@'    || 'Solution not found.'
        11  | 'the solution does not exist'                         | 'feed'        | 'TONY'     | 9          | null        || 'Solution not found.'
        12  | 'the client was given nothing'                        | 'feed'        | 'ZED'      | 1          | null        || 'Solution not found.'
        13  | 'a hidden solution is asked for a kind that is not'   | 'feed'        | 'REX'      | 2          | 'gizmo'     || 'Solution not found.'
    }

    // ---- Release 4: the lens ----

    @Unroll
    def "#seq. lens should ask the repository for bucket #expectedBucket when the bucket is #scenario"() {

        when:
        Lens got = service.lens(TONY, 1, bucket, 0, 50)

        then:
        1 * repo.lens(1, 'tony', expectedBucket, 0, 50, 7, 7) >> []
        (expectedBucket == null ? 0 : 1) * repo.lens(1, 'tony', null, 0, 1, 7, 7) >> []
        0 * repo._
        got.bucket() == expectedBucket

        where:
        seq | scenario                     | bucket      || expectedBucket
        1   | 'not given'                  | null        || null
        2   | 'empty'                      | ''          || null
        3   | 'blank'                      | '   '       || null
        4   | 'now'                        | 'now'       || 'now'
        5   | 'next'                       | 'next'      || 'next'
        6   | 'waiting'                    | 'waiting'   || 'waiting'
        7   | 'in capitals'                | 'NOW'       || 'now'
        8   | 'padded and mixed case'      | ' Waiting ' || 'waiting'
    }

    @Unroll
    def "lens should refuse and read nothing when the bucket is #scenario"() {

        when:
        service.lens(TONY, 1, bucket, 0, 50)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'The lens has three buckets: now, next and waiting.'
        0 * repo._

        where:
        scenario                  | bucket     || unused
        'a word it does not know' | 'later'    || null
        'every bucket'            | 'all'      || null
        'two buckets at once'     | 'now,next' || null
        'a plural'                | 'nows'     || null
    }

    @Unroll
    def "#seq. lens should ask for #expectedSize items from #expectedOffset when #scenario"() {

        given:
        repo.lens(1, 'tony', null, 0, 1, 7, 7) >> []

        when:
        Lens got = service.lens(TONY, 1, bucket, offset, pageSize)

        then:
        1 * repo.lens(1, 'tony', bucket, expectedOffset, expectedSize, 7, 7) >> []
        got.offset() == expectedOffset
        got.pageSize() == expectedSize

        where:
        seq | scenario                                    | bucket | offset | pageSize || expectedOffset | expectedSize
        1   | 'the page size is not given'                | null   | 0      | 0        || 0              | 50
        2   | 'the page size is negative'                 | null   | 0      | -5       || 0              | 50
        3   | 'the page size is one'                      | null   | 0      | 1        || 0              | 1
        4   | 'the page size is the most there is'        | null   | 0      | 500      || 0              | 500
        5   | 'the page size is one past the most'        | null   | 0      | 501      || 0              | 500
        6   | 'the page size is far past the most'        | null   | 0      | 5000     || 0              | 500
        7   | 'an offset is given with no bucket'         | null   | 40     | 50       || 0              | 50
        8   | 'an offset is given with a bucket'          | 'next' | 40     | 50       || 40             | 50
        9   | 'a negative offset is given with a bucket'  | 'next' | -3     | 50       || 0              | 50
        10  | 'a bucket is paged with a large page'       | 'now'  | 500    | 900      || 500            | 500
    }

    def "lens should count now, waiting and next in that order with zeros when there are no open items"() {

        when:
        Lens got = service.lens(TONY, 1, null, 0, 50)

        then:
        1 * repo.lens(1, 'tony', null, 0, 50, 7, 7) >> []
        got.items() == []
        got.counts() == [now: 0, waiting: 0, next: 0]
        got.counts().keySet() as List == ['now', 'waiting', 'next']
    }

    def "lens should count each bucket from its items and leave an empty bucket at zero when the first pages are read"() {

        given:
        List<LensItem> page = [lensItem(1, 'now', 3), lensItem(2, 'now', 3), lensItem(3, 'now', 3), lensItem(4, 'next', 120)]

        when:
        Lens got = service.lens(TONY, 1, null, 0, 50)

        then:
        1 * repo.lens(1, 'tony', null, 0, 50, 7, 7) >> page
        0 * repo._
        got.items() == page
        got.counts() == [now: 3, waiting: 0, next: 120]
        got.counts().keySet() as List == ['now', 'waiting', 'next']
        got.bucket() == null
    }

    def "lens should keep the page of one bucket and read the other counts from a second call when a later page is read"() {

        given:
        List<LensItem> page = [lensItem(51, 'next', 120), lensItem(52, 'next', 120)]
        List<LensItem> firsts = [lensItem(1, 'now', 3), lensItem(9, 'waiting', 2), lensItem(4, 'next', 120)]

        when:
        Lens got = service.lens(TONY, 1, 'next', 50, 2)

        then:
        1 * repo.lens(1, 'tony', 'next', 50, 2, 7, 7) >> page

        then:
        1 * repo.lens(1, 'tony', null, 0, 1, 7, 7) >> firsts
        0 * repo._
        got.items() == page
        got.counts() == [now: 3, waiting: 2, next: 120]
        got.bucket() == 'next'
        got.offset() == 50
        got.pageSize() == 2
    }

    def "lens should still count the bucket from the second call when the later page asked is past its end"() {

        when:
        Lens got = service.lens(TONY, 1, 'waiting', 400, 50)

        then:
        1 * repo.lens(1, 'tony', 'waiting', 400, 50, 7, 7) >> []
        1 * repo.lens(1, 'tony', null, 0, 1, 7, 7) >> [lensItem(9, 'waiting', 2)]
        got.items() == []
        got.counts() == [now: 0, waiting: 2, next: 0]
    }

    @Unroll
    def "lens should ask for the Now of #expectedMember when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.lens(caller, 1, null, 0, 50)

        then:
        1 * repo.lens(1, expectedMember, null, 0, 50, 7, 7) >> []

        where:
        scenario                        | callerName || expectedMember
        'a person asks'                 | 'TONY'     || 'tony'
        'the house asks'                | 'HOUSE'    || 'felix'
        'a client asks of a shared one' | 'REX'      || 'rex'
    }

    // ---- Release 4: the note for next time ----

    @Unroll
    def "leftOff should answer what the repository read for #expectedMember when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        LeftOff note = new LeftOff('Next time, start with the charset.', getLocalDateTime('11/04/2026 17:45'))

        when:
        LeftOff got = service.leftOff(caller, 1)

        then:
        1 * repo.leftOff(1, expectedMember) >> note
        0 * repo._
        got.is(note)

        where:
        scenario                        | callerName || expectedMember
        'a person asks'                 | 'TONY'     || 'tony'
        'the house asks'                | 'HOUSE'    || 'felix'
        'a client asks of a shared one' | 'REX'      || 'rex'
    }

    def "leftOff should pass no note through when the member has none"() {

        when:
        LeftOff got = service.leftOff(TONY, 2)

        then:
        1 * repo.leftOff(2, 'tony') >> new LeftOff(null, null)
        got.note() == null
        got.writtenDate() == null
    }

    @Unroll
    def "#seq. saveLeftOff should write #scenario and answer the note read back afterwards"() {

        given:
        String note = length == null ? input : textOf(length)
        String expectedStored = expectedLength == null ? expectedText : textOf(expectedLength)
        LeftOff readBack = new LeftOff(expectedStored == '' ? null : expectedStored, null)

        when:
        LeftOff got = service.saveLeftOff(TONY, 1, note)

        then:
        1 * repo.saveLeftOff(1, 'tony', expectedStored)

        then:
        1 * repo.leftOff(1, 'tony') >> readBack
        0 * repo._
        got.is(readBack)

        where:
        seq | scenario                              | input                   | length || expectedText            | expectedLength
        1   | 'a note as typed'                     | 'Next: the 835 parser.' | null   || 'Next: the 835 parser.' | null
        2   | 'a padded note trimmed'               | '  Next: the parser.  ' | null   || 'Next: the parser.'     | null
        3   | 'a null note as empty'                | null                    | null   || ''                      | null
        4   | 'an empty note as empty'              | ''                      | null   || ''                      | null
        5   | 'a blank note as empty'               | '   '                   | null   || ''                      | null
        6   | 'a note of exactly 1,000 characters'  | null                    | 1000   || null                    | 1000
        7   | 'a note of one character'             | 'x'                     | null   || 'x'                     | null
        8   | 'a note with line breaks kept inside' | 'One\nTwo'              | null   || 'One\nTwo'              | null
    }

    def "saveLeftOff should keep a note that is 1,000 characters once its padding is trimmed"() {

        given:
        String padded = '  ' + textOf(1000) + '  '

        when:
        service.saveLeftOff(REX, 1, padded)

        then:
        1 * repo.saveLeftOff(1, 'rex', textOf(1000))
        1 * repo.leftOff(1, 'rex') >> new LeftOff(textOf(1000), null)
    }

    @Unroll
    def "saveLeftOff should refuse before any write when the note is #scenario"() {

        given:
        String note = textOf(length)

        when:
        service.saveLeftOff(TONY, 1, note)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A note for next time can be at most 1,000 characters.'
        0 * repo._

        where:
        scenario                      | length || unused
        'one character too long'      | 1001   || null
        'far too long'                | 5000   || null
    }

    @Unroll
    def "saveLeftOff should write the note of #expectedMember when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.saveLeftOff(caller, 1, 'Next time')

        then:
        1 * repo.saveLeftOff(1, expectedMember, 'Next time')
        1 * repo.leftOff(1, expectedMember) >> new LeftOff('Next time', null)

        where:
        scenario                          | callerName || expectedMember
        'a person writes it'              | 'TONY'     || 'tony'
        'the house writes it'             | 'HOUSE'    || 'felix'
        'a client writes on a shared one' | 'REX'      || 'rex'
    }

    // ---- Release 4: the activity feed ----

    @Unroll
    def "#seq. feed should look back #expectedDays days when days is #scenario"() {

        given:
        LocalDateTime captured = null
        LocalDateTime before = LocalDateTime.now()

        when:
        ActivityPage got = service.feed(TONY, 1, days, null, null, null, 0, 50)
        LocalDateTime after = LocalDateTime.now()

        then:
        1 * repo.feed(1, _, null, null, null, 0, 50) >> { int s, LocalDateTime since, String w, String k, Integer t, int o, int p ->
            captured = since
            []
        }
        0 * repo._
        captured != null
        captured.nano == 0
        !captured.isBefore(before.minusDays(expectedDays).withNano(0))
        !captured.isAfter(after.minusDays(expectedDays))
        got.since() == captured

        where:
        seq | scenario                    | days || expectedDays
        1   | 'not given'                 | null || 30
        2   | 'zero'                      | 0    || 30
        3   | 'negative'                  | -1   || 30
        4   | 'one'                       | 1    || 1
        5   | 'a week'                    | 7    || 7
        6   | 'thirty'                    | 30   || 30
        7   | 'the most there is'         | 3650 || 3650
        8   | 'one past the most'         | 3651 || 3650
        9   | 'far past the most'         | 5000 || 3650
    }

    @Unroll
    def "#seq. feed should ask for who #expectedWho, kind #expectedKind and topic #expectedTopic when #scenario"() {

        when:
        service.feed(TONY, 1, 7, who, kind, topicId, 0, 50)

        then:
        1 * repo.feed(1, _, expectedWho, expectedKind, expectedTopic, 0, 50) >> []
        0 * repo._

        where:
        seq | scenario                         | who      | kind        | topicId || expectedWho | expectedKind | expectedTopic
        1   | 'nothing narrows it'             | null     | null        | null    || null        | null         | null
        2   | 'who and kind are blank'         | '  '     | ''          | null    || null        | null         | null
        3   | 'who is padded and in capitals'  | ' Rex '  | null        | null    || 'rex'       | null         | null
        4   | 'the kind is in capitals'        | null     | ' LOG '     | null    || null        | 'log'        | null
        5   | 'the kind is message'            | null     | 'Message'   | null    || null        | 'message'    | null
        6   | 'the kind is decision'           | null     | 'decision'  | null    || null        | 'decision'   | null
        7   | 'the kind is question'           | null     | 'question'  | null    || null        | 'question'   | null
        8   | 'the topic is zero'              | null     | null        | 0       || null        | null         | null
        9   | 'the topic is negative'          | null     | null        | -4      || null        | null         | null
        10  | 'the topic is an id'             | null     | null        | 12      || null        | null         | 12
        11  | 'everything narrows it'          | 'TONY'   | 'item'      | 3       || 'tony'      | 'item'       | 3
    }

    @Unroll
    def "feed should refuse and read nothing when the kind is #scenario"() {

        when:
        service.feed(TONY, 1, null, null, kind, null, 0, 50)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'kind must be one of item, note, log, file, term, topic, question, decision, message.'
        0 * repo._

        where:
        scenario                      | kind           || unused
        'a word it does not know'     | 'gizmo'        || null
        'a plural'                    | 'messages'     || null
        'another name for a message'  | 'conversation' || null
        'a project'                   | 'project'      || null
    }

    @Unroll
    def "#seq. feed should ask for #expectedSize changes from #expectedOffset when #scenario"() {

        when:
        ActivityPage got = service.feed(TONY, 1, null, null, null, null, offset, pageSize)

        then:
        1 * repo.feed(1, _, null, null, null, expectedOffset, expectedSize) >> []
        got.offset() == expectedOffset
        got.pageSize() == expectedSize

        where:
        seq | scenario                           | offset | pageSize || expectedOffset | expectedSize
        1   | 'the page size is not given'       | 0      | 0        || 0              | 50
        2   | 'the page size is negative'        | 0      | -1       || 0              | 50
        3   | 'the page size is one'             | 0      | 1        || 0              | 1
        4   | 'the page size is the most'        | 0      | 500      || 0              | 500
        5   | 'the page size is past the most'   | 0      | 501      || 0              | 500
        6   | 'the offset is negative'           | -5     | 50       || 0              | 50
        7   | 'the offset is a later page'       | 30     | 10       || 30             | 10
        8   | 'a far page of the most'           | 1000   | 9000     || 1000           | 500
    }

    def "feed should answer a total of zero without asking for one when the first page is empty"() {

        when:
        ActivityPage got = service.feed(TONY, 1, null, null, null, null, 0, 50)

        then:
        1 * repo.feed(1, _, null, null, null, 0, 50) >> []
        0 * repo.feedTotal(*_)
        got.changes() == []
        got.total() == 0
    }

    def "feed should ask for the total when a later page is empty so the reader learns how many there are"() {

        given:
        LocalDateTime fed = null
        LocalDateTime totalled = null

        when:
        ActivityPage got = service.feed(TONY, 1, 7, 'rex', 'log', 12, 100, 50)

        then:
        1 * repo.feed(1, _, 'rex', 'log', 12, 100, 50) >> { int s, LocalDateTime since, String w, String k, Integer t, int o, int p ->
            fed = since
            []
        }
        1 * repo.feedTotal(1, _, 'rex', 'log', 12) >> { int s, LocalDateTime since, String w, String k, Integer t ->
            totalled = since
            42
        }
        got.changes() == []
        got.total() == 42
        got.offset() == 100
        fed != null
        totalled == fed
    }

    def "feed should ask for the total and answer the page when there are changes"() {

        given:
        List<Activity> rows = [activity('topic', 7), activity('log', 310)]

        when:
        ActivityPage got = service.feed(HOUSE, 1, null, null, null, null, 0, 2)

        then:
        1 * repo.feed(1, _, null, null, null, 0, 2) >> rows
        1 * repo.feedTotal(1, _, null, null, null) >> 9
        0 * repo._
        got.changes() == rows
        got.total() == 9
        got.pageSize() == 2
    }

    def "feed should read a shared solution for a client the same way as for a person"() {

        when:
        ActivityPage got = service.feed(REX, 1, 1, null, 'item', null, 0, 50)

        then:
        1 * repo.feed(1, _, null, 'item', null, 0, 50) >> [activity('item', 11)]
        1 * repo.feedTotal(1, _, null, 'item', null) >> 1
        got.total() == 1
        got.changes()*.id() == [11]
    }

    // ---- helpers ----

    /** A lens row with only what the service reads: its id, bucket and bucket total. */
    private static LensItem lensItem(int id, String bucket, int total) {
        LensItem.of([SolutionProjectItemID: id, Bucket: bucket, TotalInBucket: total])
    }

    private static Activity activity(String kind, int id) {
        Activity.of([Kind: kind, RecordID: id, Who: 'tony'])
    }
}
