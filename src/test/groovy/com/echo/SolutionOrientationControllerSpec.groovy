package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.ResultActions

import java.time.LocalDateTime

import com.echo.SolutionOrientationModels.Activity
import com.echo.SolutionOrientationModels.LeftOff
import com.echo.SolutionOrientationModels.LensItem
import com.echo.SolutionOrientationModels.SummaryAge

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695: "where am I" over HTTP -- GET /api/solutions/{id}/summary-age answers 401 with no credential,
 * 200 with the written date, age in days and stale flag for anyone who may see the solution, and 404 for a
 * solution that is missing or not shared. The real service and access fence run; storage is mocked.
 * Release 4: GET .../lens, GET and PUT .../left-off and GET .../activity -- 401 with no credential, the body
 * each answers, the query parameters and body that become the storage call, each caller's own member code
 * (a person "tony", the house "felix" or its actor, the agent "rex"), 400 for a bucket, kind or note the
 * service refuses, and 404 for a solution that is missing or not shared.
 */
@WebMvcTest(SolutionOrientationController)
class SolutionOrientationControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionOrientationService service = Mock()

    SolutionOrientationRepository repo = Mock()
    SolutionOrientationService real

    def setup() {
        real = new SolutionOrientationService(repo, access)
        service.summaryAge(*_) >> { a -> real.summaryAge(*a) }
        service.lens(*_) >> { a -> real.lens(*a) }
        service.leftOff(*_) >> { a -> real.leftOff(*a) }
        service.saveLeftOff(*_) >> { a -> real.saveLeftOff(*a) }
        service.feed(*_) >> { a -> real.feed(*a) }
        repo.summaryAge(_) >> new SummaryAge(getLocalDateTime('10/01/2026 12:30'), 34, true)
        repo.lens(*_) >> [lensItem(42, 'now', 3, true), lensItem(77, 'next', 120, false)]
        repo.leftOff(*_) >> new LeftOff('Next time, start with the charset.', getLocalDateTime('11/04/2026 17:45'))
        repo.feed(*_) >> [change('log', 310, 'rex'), change('item', 11, 'tony')]
        repo.feedTotal(*_) >> 9
    }

    /** One lens row: its id, bucket, bucket total and whether it is the caller's. */
    private static LensItem lensItem(int id, String bucket, int total, boolean mine) {
        LensItem.of([SolutionProjectItemID: id, ItemTitle: 'Item ' + id, SolutionProjectID: 3, ProjectName: 'Parser',
                     StatusCode: 'inprogress', StatusName: 'In progress', Priority: 2, AssigneeCode: 'tony', AssigneeName: 'Tony',
                     Bucket: bucket, IdleDays: 9, IsIdle: true, IsMine: mine, IsOverdue: false, OpenWaits: 0,
                     RowInBucket: 1, TotalInBucket: total])
    }

    private static Activity change(String kind, int id, String who) {
        Activity.of([Kind: kind, RecordID: id, Title: kind + ' ' + id, Sub: 'Sub', Action: 'added', Who: who,
                     WhoName: who.capitalize(), ChangedDate: getLocalDateTime('11/04/2026 10:15')])
    }

    def "summaryAge should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(get('/api/solutions/1/summary-age'))

        then:
        r.andExpect(status().isUnauthorized())
        0 * repo._
    }

    def "summaryAge should answer the age when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, get('/api/solutions/1/summary-age'))).andExpect(status().isOk())
                .andExpect(jsonPath('$.ageDays').value(34))
                .andExpect(jsonPath('$.stale').value(true))
                .andExpect(jsonPath('$.writtenDate').exists())
        }
    }

    def "summaryAge should answer a summary written today as 0 days and not stale"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/summary-age')))

        then:
        1 * repo.summaryAge(1) >> new SummaryAge(getLocalDateTime('11/04/2026 08:00'), 0, false)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.ageDays').value(0)).andExpect(jsonPath('$.stale').value(false))
    }

    def "summaryAge should answer no date and no age when the solution has no summary"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/summary-age')))

        then:
        1 * repo.summaryAge(1) >> new SummaryAge(null, null, false)
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.writtenDate').doesNotExist())
            .andExpect(jsonPath('$.ageDays').doesNotExist())
            .andExpect(jsonPath('$.stale').value(false))
    }

    @Unroll
    def "summaryAge should answer 404 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get(url)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        scenario                                 | who    | url                              || expectedStatus
        'the agent was given nothing'            | 'zed'  | '/api/solutions/1/summary-age'   || 404
        'the agent was not given the solution'   | 'rex'  | '/api/solutions/2/summary-age'   || 404
        'the solution does not exist'            | 'user' | '/api/solutions/9/summary-age'   || 404
    }

    def "summaryAge should match no endpoint when the id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/abc/summary-age'))).andExpect(status().isNotFound())

        then:
        0 * repo._
    }

    // ---- Release 4: who may call ----

    @Unroll
    def "#scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo._

        where:
        scenario          | method | url                          | body              || expectedStatus
        'lens'            | 'GET'  | '/api/solutions/1/lens'      | null              || 401
        'read the note'   | 'GET'  | '/api/solutions/1/left-off'  | null              || 401
        'write the note'  | 'PUT'  | '/api/solutions/1/left-off'  | '{"note":"Next"}' || 401
        'activity'        | 'GET'  | '/api/solutions/1/activity'  | null              || 401
    }

    @Unroll
    def "#seq. #scenario should answer 404 and touch nothing when #why"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        seq | scenario         | why                                       | who    | method | url                                    | body                || expectedStatus
        1   | 'lens'           | 'the agent was given nothing'             | 'zed'  | 'GET'  | '/api/solutions/1/lens'                | null                || 404
        2   | 'lens'           | 'the agent was not given the solution'    | 'rex'  | 'GET'  | '/api/solutions/2/lens'                | null                || 404
        3   | 'lens'           | 'the solution does not exist'             | 'user' | 'GET'  | '/api/solutions/9/lens'                | null                || 404
        4   | 'read the note'  | 'the agent was given nothing'             | 'zed'  | 'GET'  | '/api/solutions/1/left-off'            | null                || 404
        5   | 'read the note'  | 'the agent was not given the solution'    | 'rex'  | 'GET'  | '/api/solutions/2/left-off'            | null                || 404
        6   | 'read the note'  | 'the solution does not exist'             | 'user' | 'GET'  | '/api/solutions/9/left-off'            | null                || 404
        7   | 'write the note' | 'the agent was given nothing'             | 'zed'  | 'PUT'  | '/api/solutions/1/left-off'            | '{"note":"Next"}'   || 404
        8   | 'write the note' | 'the agent was not given the solution'    | 'rex'  | 'PUT'  | '/api/solutions/2/left-off'            | '{"note":"Next"}'   || 404
        9   | 'write the note' | 'the solution does not exist'             | 'user' | 'PUT'  | '/api/solutions/9/left-off'            | '{"note":"Next"}'   || 404
        10  | 'activity'       | 'the agent was given nothing'             | 'zed'  | 'GET'  | '/api/solutions/1/activity'            | null                || 404
        11  | 'activity'       | 'the agent was not given the solution'    | 'rex'  | 'GET'  | '/api/solutions/2/activity'            | null                || 404
        12  | 'activity'       | 'the solution does not exist'             | 'user' | 'GET'  | '/api/solutions/9/activity'            | null                || 404
        13  | 'write the note' | 'a hidden solution is sent too much'      | 'rex'  | 'PUT'  | '/api/solutions/2/left-off'            | '{"note":"@1001@"}' || 404
        14  | 'lens'           | 'a hidden solution is asked a bad bucket' | 'rex'  | 'GET'  | '/api/solutions/2/lens?bucket=later'   | null                || 404
        15  | 'activity'       | 'a hidden solution is asked a bad kind'   | 'rex'  | 'GET'  | '/api/solutions/2/activity?kind=gizmo' | null                || 404
    }

    @Unroll
    def "#scenario should never reach the controller when the id is not a number"() {

        when:
        MockHttpServletResponse res = mvc.perform(by('user', request(method, url, body))).andReturn().response

        then:
        res.status == expectedStatus || (orNotAllowed && res.status == 405)
        0 * repo._

        where:
        scenario         | method | url                           | body              || expectedStatus | orNotAllowed
        'lens'           | 'GET'  | '/api/solutions/abc/lens'     | null              || 404            | false
        'read the note'  | 'GET'  | '/api/solutions/abc/left-off' | null              || 404            | false
        'write the note' | 'PUT'  | '/api/solutions/abc/left-off' | '{"note":"Next"}' || 404            | true
        'activity'       | 'GET'  | '/api/solutions/abc/activity' | null              || 404            | false
    }

    // ---- Release 4: the lens ----

    def "lens should answer the first page of each bucket and every count when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, get('/api/solutions/1/lens'))).andExpect(status().isOk())
                .andExpect(jsonPath('$.items.length()').value(2))
                .andExpect(jsonPath('$.items[0].id').value(42))
                .andExpect(jsonPath('$.items[0].title').value('Item 42'))
                .andExpect(jsonPath('$.items[0].projectName').value('Parser'))
                .andExpect(jsonPath('$.items[0].bucket').value('now'))
                .andExpect(jsonPath('$.items[0].idle').value(true))
                .andExpect(jsonPath('$.items[0].mine').value(true))
                .andExpect(jsonPath('$.items[0].overdue').value(false))
                .andExpect(jsonPath('$.items[0].idleDays').value(9))
                .andExpect(jsonPath('$.items[0].totalInBucket').value(3))
                .andExpect(jsonPath('$.items[1].id').value(77))
                .andExpect(jsonPath('$.items[1].mine').value(false))
                .andExpect(jsonPath('$.counts.now').value(3))
                .andExpect(jsonPath('$.counts.waiting').value(0))
                .andExpect(jsonPath('$.counts.next').value(120))
                .andExpect(jsonPath('$.bucket').doesNotExist())
                .andExpect(jsonPath('$.offset').value(0))
                .andExpect(jsonPath('$.pageSize').value(50))
        }
    }

    @Unroll
    def "lens should ask for the Now of #expectedMember when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get('/api/solutions/1/lens')))

        then:
        1 * repo.lens(1, expectedMember, null, 0, 50, 7, 7) >> []
        0 * repo._
        r.andExpect(status().isOk()).andExpect(jsonPath('$.items.length()').value(0))

        where:
        scenario                        | who       || expectedMember
        'a person calls'                | 'user'    || 'tony'
        'the house calls'               | 'service' || 'felix'
        'the house calls as Stewart'    | 'stewart' || 'stewart'
        'the agent calls'               | 'rex'     || 'rex'
    }

    def "lens should pass the bucket, offset and page size and count the other buckets from page one when a later page is asked"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/lens').param('bucket', 'NEXT').param('offset', '50').param('pageSize', '20')))

        then:
        1 * repo.lens(1, 'rex', 'next', 50, 20, 7, 7) >> [lensItem(77, 'next', 120, false)]
        1 * repo.lens(1, 'rex', null, 0, 1, 7, 7) >> [lensItem(42, 'now', 3, true), lensItem(9, 'waiting', 2, false), lensItem(77, 'next', 120, false)]
        0 * repo._
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.items.length()').value(1))
            .andExpect(jsonPath('$.items[0].id').value(77))
            .andExpect(jsonPath('$.counts.now').value(3))
            .andExpect(jsonPath('$.counts.waiting').value(2))
            .andExpect(jsonPath('$.counts.next').value(120))
            .andExpect(jsonPath('$.bucket').value('next'))
            .andExpect(jsonPath('$.offset').value(50))
            .andExpect(jsonPath('$.pageSize').value(20))
    }

    @Unroll
    def "lens should answer 400 and read nothing when the bucket is #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get('/api/solutions/1/lens').param('bucket', bucket)))

        then:
        r.andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        scenario                                      | who    | bucket  || expectedMessage
        'a word it does not know'                     | 'user' | 'later' || 'The lens has three buckets: now, next and waiting.'
        'a word it does not know, asked by the agent' | 'rex'  | 'all'   || 'The lens has three buckets: now, next and waiting.'
    }

    // ---- Release 4: the note for next time ----

    @Unroll
    def "leftOff should answer the note of #expectedMember when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get('/api/solutions/1/left-off')))

        then:
        1 * repo.leftOff(1, expectedMember) >> new LeftOff('Next time, start with the charset.', getLocalDateTime('11/04/2026 17:45'))
        0 * repo._
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.note').value('Next time, start with the charset.'))
            .andExpect(jsonPath('$.writtenDate').exists())

        where:
        scenario                        | who       || expectedMember
        'a person calls'                | 'user'    || 'tony'
        'the house calls'               | 'service' || 'felix'
        'the house calls as Stewart'    | 'stewart' || 'stewart'
        'the agent calls'               | 'rex'     || 'rex'
    }

    def "leftOff should answer no note and no date when the caller has none"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/left-off')))

        then:
        1 * repo.leftOff(1, 'rex') >> new LeftOff(null, null)
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.note').doesNotExist())
            .andExpect(jsonPath('$.writtenDate').doesNotExist())
    }

    @Unroll
    def "#seq. saveLeftOff should write #scenario and answer the note read back"() {

        given:
        String expectedStored = expectedLength == null ? expectedText : getReplicatedString('x', expectedLength)
        boolean removed = expectedStored == ''
        LeftOff readBack = removed ? new LeftOff(null, null) : new LeftOff(expectedStored, getLocalDateTime('11/04/2026 17:45'))

        when:
        ResultActions r = mvc.perform(asUser(json(put('/api/solutions/1/left-off'), body)))

        then:
        1 * repo.saveLeftOff(1, 'tony', expectedStored)

        then:
        1 * repo.leftOff(1, 'tony') >> readBack
        0 * repo._
        r.andExpect(status().isOk())
        if (removed) {
            r.andExpect(jsonPath('$.note').doesNotExist()).andExpect(jsonPath('$.writtenDate').doesNotExist())
        } else {
            r.andExpect(jsonPath('$.note').value(expectedStored)).andExpect(jsonPath('$.writtenDate').exists())
        }

        where:
        seq | scenario                                     | body                              || expectedText        | expectedLength
        1   | 'a note as typed'                            | '{"note":"Next: the parser."}'    || 'Next: the parser.' | null
        2   | 'a padded note trimmed'                      | '{"note":"  Next: the parser. "}' || 'Next: the parser.' | null
        3   | 'a blank note as empty'                      | '{"note":"   "}'                  || ''                  | null
        4   | 'an empty note as empty'                     | '{"note":""}'                     || ''                  | null
        5   | 'a null note as empty'                       | '{"note":null}'                   || ''                  | null
        6   | 'a body with no note as empty'               | '{}'                              || ''                  | null
        7   | 'a note of exactly 1,000 characters'         | '{"note":"@1000@"}'               || null                | 1000
        8   | 'the note when an unknown field rides along' | '{"note":"Kept","extra":1}'       || 'Kept'              | null
    }

    @Unroll
    def "saveLeftOff should write the note of #expectedMember when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, json(put('/api/solutions/1/left-off'), '{"note":"Next: the 835 parser."}')))

        then:
        1 * repo.saveLeftOff(1, expectedMember, 'Next: the 835 parser.')
        1 * repo.leftOff(1, expectedMember) >> new LeftOff('Next: the 835 parser.', getLocalDateTime('11/04/2026 17:45'))
        r.andExpect(status().isOk()).andExpect(jsonPath('$.note').value('Next: the 835 parser.'))

        where:
        scenario                         | who       || expectedMember
        'a person writes it'             | 'user'    || 'tony'
        'the house writes it'            | 'service' || 'felix'
        'the house writes it as Stewart' | 'stewart' || 'stewart'
        'the agent writes it'            | 'rex'     || 'rex'
    }

    @Unroll
    def "saveLeftOff should answer 400 and write nothing when the note is #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, json(put('/api/solutions/1/left-off'), body)))

        then:
        r.andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        scenario                                    | who    | body                || expectedMessage
        'one character too long'                    | 'user' | '{"note":"@1001@"}' || 'A note for next time can be at most 1,000 characters.'
        'one character too long, sent by the agent' | 'rex'  | '{"note":"@1001@"}' || 'A note for next time can be at most 1,000 characters.'
    }

    // ---- Release 4: the activity feed ----

    def "activity should answer everyone's changes, the window and the total when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, get('/api/solutions/1/activity'))).andExpect(status().isOk())
                .andExpect(jsonPath('$.changes.length()').value(2))
                .andExpect(jsonPath('$.changes[0].kind').value('log'))
                .andExpect(jsonPath('$.changes[0].id').value(310))
                .andExpect(jsonPath('$.changes[0].title').value('log 310'))
                .andExpect(jsonPath('$.changes[0].sub').value('Sub'))
                .andExpect(jsonPath('$.changes[0].action').value('added'))
                .andExpect(jsonPath('$.changes[0].who').value('rex'))
                .andExpect(jsonPath('$.changes[0].whoName').value('Rex'))
                .andExpect(jsonPath('$.changes[0].changedDate').exists())
                .andExpect(jsonPath('$.changes[1].kind').value('item'))
                .andExpect(jsonPath('$.changes[1].who').value('tony'))
                .andExpect(jsonPath('$.since').exists())
                .andExpect(jsonPath('$.offset').value(0))
                .andExpect(jsonPath('$.pageSize').value(50))
                .andExpect(jsonPath('$.total').value(9))
        }
    }

    def "activity should read the last 30 days of everyone with no filter when no parameter is sent"() {

        given:
        LocalDateTime captured = null
        LocalDateTime before = LocalDateTime.now()

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/activity')))
        LocalDateTime after = LocalDateTime.now()

        then:
        1 * repo.feed(1, _, null, null, null, 0, 50) >> { int s, LocalDateTime since, String w, String k, Integer t, int o, int p ->
            captured = since
            [change('item', 11, 'tony')]
        }
        1 * repo.feedTotal(1, _, null, null, null) >> 1
        0 * repo._
        r.andExpect(status().isOk()).andExpect(jsonPath('$.total').value(1))
        !captured.isBefore(before.minusDays(30).withNano(0))
        !captured.isAfter(after.minusDays(30))
    }

    def "activity should pass days, who, kind, topic, offset and page size tidied when every parameter is sent"() {

        given:
        LocalDateTime captured = null
        LocalDateTime before = LocalDateTime.now()

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/activity').param('days', '7').param('who', ' Rex ')
            .param('kind', 'LOG').param('topicId', '12').param('offset', '10').param('pageSize', '5')))
        LocalDateTime after = LocalDateTime.now()

        then:
        1 * repo.feed(1, _, 'rex', 'log', 12, 10, 5) >> { int s, LocalDateTime since, String w, String k, Integer t, int o, int p ->
            captured = since
            [change('log', 310, 'rex')]
        }
        1 * repo.feedTotal(1, _, 'rex', 'log', 12) >> 11
        0 * repo._
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.changes.length()').value(1))
            .andExpect(jsonPath('$.offset').value(10))
            .andExpect(jsonPath('$.pageSize').value(5))
            .andExpect(jsonPath('$.total').value(11))
        !captured.isBefore(before.minusDays(7).withNano(0))
        !captured.isAfter(after.minusDays(7))
    }

    @Unroll
    def "activity should ask for kind #expectedKind, topic #expectedTopic and a page of #expectedSize when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/activity' + query)))

        then:
        1 * repo.feed(1, _, null, expectedKind, expectedTopic, 0, expectedSize) >> []
        0 * repo._
        r.andExpect(status().isOk()).andExpect(jsonPath('$.total').value(0)).andExpect(jsonPath('$.pageSize').value(expectedSize))

        where:
        scenario                              | query                        || expectedKind | expectedTopic | expectedSize
        'the kind is message'                 | '?kind=message'              || 'message'    | null          | 50
        'the kind is decision'                | '?kind=Decision'             || 'decision'   | null          | 50
        'the topic is zero'                   | '?topicId=0'                 || null         | null          | 50
        'the page size is past the most'      | '?pageSize=9000'             || null         | null          | 500
        'the days are past the most'          | '?days=5000'                 || null         | null          | 50
    }

    def "activity should answer a total of zero without asking for one when the first page is empty"() {

        when:
        ResultActions r = mvc.perform(asService(get('/api/solutions/1/activity')))

        then:
        1 * repo.feed(1, _, null, null, null, 0, 50) >> []
        0 * repo.feedTotal(*_)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.changes.length()').value(0)).andExpect(jsonPath('$.total').value(0))
    }

    def "activity should ask for the total when a later page is empty"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/activity').param('offset', '100')))

        then:
        1 * repo.feed(1, _, null, null, null, 100, 50) >> []
        1 * repo.feedTotal(1, _, null, null, null) >> 42
        r.andExpect(status().isOk()).andExpect(jsonPath('$.changes.length()').value(0)).andExpect(jsonPath('$.total').value(42))
    }

    @Unroll
    def "activity should answer 400 and read nothing when the kind is #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get('/api/solutions/1/activity').param('kind', kind)))

        then:
        r.andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        scenario                         | who    | kind       || expectedMessage
        'a word it does not know'        | 'user' | 'gizmo'    || 'kind must be one of item, note, log, file, term, topic, question, decision, message.'
        'a plural, asked by the agent'   | 'rex'  | 'messages' || 'kind must be one of item, note, log, file, term, topic, question, decision, message.'
    }
}
