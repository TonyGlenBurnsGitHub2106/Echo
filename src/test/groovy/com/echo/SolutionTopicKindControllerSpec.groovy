package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionLinkModels.LinkPage
import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionModels.LogEntry
import com.echo.SolutionTopicKindModels.OpenItem
import com.echo.SolutionTopicKindModels.TopicType
import static com.echo.SolutionFixtures.logEntry
import static com.echo.SolutionFixtures.lookups

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): topic kinds over HTTP -- every endpoint of SolutionTopicKindController (the kinds of
 * part, Arrange, the open items of a topic, the premise switch and the four kinds), who may call it, the path,
 * query and body fields that become the service call, and how each refusal maps to a status (400 malformed,
 * 404 not found or not shared, 409 a rule). The real SolutionTopicKindService, SolutionLinkService,
 * SolutionLogService, access fence and code check run; storage and the item service are mocked.
 * GET /api/solutions/topic-kinds touches no service, but it is under /api/**, so SecurityConfig still asks
 * for a credential: without one it answers 401 like every other endpoint here.
 */
@WebMvcTest(SolutionTopicKindController)
class SolutionTopicKindControllerSpec extends ConfigWebSupport {

    static final String SWITCH_TEXT = '**Assumption proved false:** The 837 path is live\n\nThe path died.\n\nCancelled because of it:\n- Upgrade'

    @SpringBean SolutionTopicKindService service = Mock()

    SolutionTopicKindRepository kinds = Mock()
    SolutionLinkRepository linkRepo = Mock()
    SolutionLogRepository logRepo = Mock()
    SolutionItemService items = Mock()
    SolutionTopicKindService real

    Map<Integer, TopicType> typeRows = [3: new TopicType(3, 1, 'Chapter', 'words', 1, 2), 4: new TopicType(4, 2, 'Account', null, 1, 0)]
    Map<Integer, Topic> topicRows = [5: topic(5, 1, 'assumption', true, 'The 837 path is live'), 6: topic(6, 2, 'assumption', true, 'Theirs'),
                                     7: topic(7, 1, 'subject', null, 'Charset'), 8: topic(8, 1, 'assumption', false, 'Dead path')]
    Map<Integer, LogEntry> logRows = [410: logEntry(id: 410, solutionId: 1, sequenceNumber: 42, typeCode: 'result')]

    def setup() {
        SolutionCodeCheck codes = new SolutionCodeCheck(solutionRepo)
        real = new SolutionTopicKindService(kinds, new SolutionLinkService(linkRepo, access), items,
            new SolutionLogService(logRepo, access, codes), access)
        service.types(*_) >> { a -> real.types(*a) }
        service.type(*_) >> { a -> real.type(*a) }
        service.addType(*_) >> { a -> real.addType(*a) }
        service.updateType(*_) >> { a -> real.updateType(*a) }
        service.deleteType(*_) >> { a -> real.deleteType(*a) }
        service.reorder(*_) >> { a -> real.reorder(*a) }
        service.openItems(*_) >> { a -> real.openItems(*a) }
        service.proveFalse(*_) >> { a -> real.proveFalse(*a) }

        solutionRepo.lookups() >> lookups()
        kinds.types(_) >> { int s -> typeRows.values().findAll { TopicType t -> t.solutionId() == s } as List }
        kinds.findType(_) >> { int id -> typeRows[id] }
        kinds.saveType(*_) >> { int id, int s, String n, String l -> id > 0 ? id : 3 }
        kinds.deleteType(_) >> true
        kinds.openItems(_) >> [new OpenItem(100, 'Upgrade', 'Deploy | Not started')]
        linkRepo.findTopic(_) >> { int id -> topicRows[id] }
        linkRepo.saveTopic(*_) >> { TopicDraft d, String by -> d.id() }
        linkRepo.link(*_) >> 40
        linkRepo.links(*_) >> { int s, String k, int id, int off, int size -> new LinkPage(k, id, [], off, size, 1) }
        logRepo.save(*_) >> 410
        logRepo.find(_) >> { int id -> logRows[id] }
    }

    private static Topic topic(int id, int solutionId, String kind, Boolean holds, String name) {
        Topic.of([SolutionTopicID: id, SolutionID: solutionId, TopicName: name, TopicKindCode: kind, AssumptionHolds: holds])
    }

    private static TopicDraft provedFalseDraft(int topicId) {
        new TopicDraft(topicId, 0, null, null, null, null, null, null, false, Boolean.FALSE)
    }

    // ---- who may call ----

    @Unroll
    def "#seq. #scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * service._
        0 * kinds._
        0 * linkRepo._
        0 * logRepo._
        0 * items._
        r.andExpect(status().is(expectedStatus))

        where:
        seq | scenario        | method   | url                                       | body                                     || expectedStatus
        1   | 'topic kinds'   | 'GET'    | '/api/solutions/topic-kinds'              | null                                     || 401
        2   | 'types'         | 'GET'    | '/api/solutions/1/topic-types'            | null                                     || 401
        3   | 'add a type'    | 'POST'   | '/api/solutions/1/topic-types'            | '{"name":"Chapter"}'                     || 401
        4   | 'update a type' | 'PUT'    | '/api/solutions/topic-types/3'            | '{"name":"Scene"}'                       || 401
        5   | 'delete a type' | 'DELETE' | '/api/solutions/topic-types/3'            | null                                     || 401
        6   | 'arrange'       | 'PUT'    | '/api/solutions/1/topics/order'           | '{"ids":[12,9]}'                         || 401
        7   | 'open items'    | 'GET'    | '/api/solutions/topics/5/open-items'      | null                                     || 401
        8   | 'proved false'  | 'POST'   | '/api/solutions/topics/5/proved-false'    | '{"reason":"Gone.","itemIds":[100]}'     || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        seq | scenario        | method   | url                                       | body                                         || path                | expectedValue
        1   | 'topic kinds'   | 'GET'    | '/api/solutions/topic-kinds'              | null                                         || '$.starterTags[3]'  | 'Parking lot'
        2   | 'types'         | 'GET'    | '/api/solutions/1/topic-types'            | null                                         || '$[0].name'         | 'Chapter'
        3   | 'add a type'    | 'POST'   | '/api/solutions/1/topic-types'            | '{"name":"Chapter","figureLabel":"words"}'   || '$.id'              | 3
        4   | 'update a type' | 'PUT'    | '/api/solutions/topic-types/3'            | '{"figureLabel":"pages"}'                    || '$.solutionId'      | 1
        5   | 'delete a type' | 'DELETE' | '/api/solutions/topic-types/3'            | null                                         || '$.deletedId'       | 3
        6   | 'arrange'       | 'PUT'    | '/api/solutions/1/topics/order'           | '{"ids":[12,9]}'                             || '$.solutionId'      | 1
        7   | 'open items'    | 'GET'    | '/api/solutions/topics/5/open-items'      | null                                         || '$[0].id'           | 100
        8   | 'proved false'  | 'POST'   | '/api/solutions/topics/5/proved-false'    | '{"reason":"Gone.","itemIds":[100]}'         || '$.cancelled[0].id' | 100
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * kinds.types(_)
        0 * kinds.saveType(*_)
        0 * kinds.deleteType(_)
        0 * kinds.reorder(*_)
        0 * kinds.openItems(_)
        0 * linkRepo.saveTopic(*_)
        0 * linkRepo.link(*_)
        0 * logRepo.save(*_)
        0 * items._

        where:
        seq | scenario        | who   | method   | url                                       | body                                  || expectedStatus
        1   | 'types'         | 'zed' | 'GET'    | '/api/solutions/1/topic-types'            | null                                  || 404
        2   | 'add a type'    | 'zed' | 'POST'   | '/api/solutions/1/topic-types'            | '{"name":"Chapter"}'                  || 404
        3   | 'update a type' | 'zed' | 'PUT'    | '/api/solutions/topic-types/3'            | '{"name":"Scene"}'                    || 404
        4   | 'delete a type' | 'zed' | 'DELETE' | '/api/solutions/topic-types/3'            | null                                  || 404
        5   | 'arrange'       | 'zed' | 'PUT'    | '/api/solutions/1/topics/order'           | '{"ids":[12,9]}'                      || 404
        6   | 'open items'    | 'zed' | 'GET'    | '/api/solutions/topics/5/open-items'      | null                                  || 404
        7   | 'proved false'  | 'zed' | 'POST'   | '/api/solutions/topics/5/proved-false'    | '{"reason":"Gone.","itemIds":[100]}'  || 404
        8   | 'types'         | 'rex' | 'GET'    | '/api/solutions/2/topic-types'            | null                                  || 404
        9   | 'add a type'    | 'rex' | 'POST'   | '/api/solutions/2/topic-types'            | '{"name":"Chapter"}'                  || 404
        10  | 'update a type' | 'rex' | 'PUT'    | '/api/solutions/topic-types/4'            | '{"name":"Scene"}'                    || 404
        11  | 'delete a type' | 'rex' | 'DELETE' | '/api/solutions/topic-types/4'            | null                                  || 404
        12  | 'arrange'       | 'rex' | 'PUT'    | '/api/solutions/2/topics/order'           | '{"ids":[12,9]}'                      || 404
        13  | 'open items'    | 'rex' | 'GET'    | '/api/solutions/topics/6/open-items'      | null                                  || 404
        14  | 'proved false'  | 'rex' | 'POST'   | '/api/solutions/topics/6/proved-false'    | '{"reason":"Gone.","itemIds":[100]}'  || 404
    }

    @Unroll
    def "#seq. #scenario should answer 404 '#expectedMessage' when the record is missing"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * kinds.saveType(*_)
        0 * kinds.deleteType(_)
        0 * kinds.reorder(*_)
        0 * kinds.openItems(_)
        0 * linkRepo.saveTopic(*_)
        0 * logRepo.save(*_)

        where:
        seq | scenario        | method   | url                                       | body                         || expectedMessage
        1   | 'types'         | 'GET'    | '/api/solutions/9/topic-types'            | null                         || 'Solution not found.'
        2   | 'add a type'    | 'POST'   | '/api/solutions/9/topic-types'            | '{"name":"Chapter"}'         || 'Solution not found.'
        3   | 'arrange'       | 'PUT'    | '/api/solutions/9/topics/order'           | '{"ids":[12]}'               || 'Solution not found.'
        4   | 'update a type' | 'PUT'    | '/api/solutions/topic-types/99'           | '{"name":"Scene"}'           || 'Kind of part not found.'
        5   | 'delete a type' | 'DELETE' | '/api/solutions/topic-types/99'           | null                         || 'Kind of part not found.'
        6   | 'delete a type' | 'DELETE' | '/api/solutions/topic-types/0'            | null                         || 'Kind of part not found.'
        7   | 'open items'    | 'GET'    | '/api/solutions/topics/99/open-items'     | null                         || 'Topic not found.'
        8   | 'proved false'  | 'POST'   | '/api/solutions/topics/0/proved-false'    | '{"reason":"Gone."}'         || 'Topic not found.'
    }

    def "every endpoint should match nothing when an id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/x1/topic-types'))).andExpect(status().isNotFound())
        mvc.perform(asUser(json(put('/api/solutions/topic-types/abc'), '{"name":"Scene"}'))).andExpect(status().isNotFound())
        mvc.perform(asUser(delete('/api/solutions/topic-types/3x'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/topics/abc/open-items'))).andExpect(status().isNotFound())
        mvc.perform(asUser(json(post('/api/solutions/topics/abc/proved-false'), '{"reason":"Gone."}'))).andExpect(status().isNotFound())

        then:
        0 * service._
        0 * kinds._
        0 * linkRepo._
    }

    // ---- the four kinds ----

    def "kinds should answer the four kinds in order and the starter tags without consulting any service when anyone signed in asks"() {

        when:
        for (String who : ['user', 'service', 'stewart', 'rex', 'zed']) {
            mvc.perform(by(who, get('/api/solutions/topic-kinds'))).andExpect(status().isOk())
                .andExpect(jsonPath('$.kinds.length()').value(4))
                .andExpect(jsonPath('$.kinds[0]').value('subject'))
                .andExpect(jsonPath('$.kinds[1]').value('tag'))
                .andExpect(jsonPath('$.kinds[2]').value('part'))
                .andExpect(jsonPath('$.kinds[3]').value('assumption'))
                .andExpect(jsonPath('$.starterTags.length()').value(4))
                .andExpect(jsonPath('$.starterTags[0]').value('Important'))
                .andExpect(jsonPath('$.starterTags[1]').value('Risk'))
                .andExpect(jsonPath('$.starterTags[2]').value('Idea'))
                .andExpect(jsonPath('$.starterTags[3]').value('Parking lot'))
        }

        then:
        0 * service._
        0 * kinds._
        0 * linkRepo._
        SolutionTopicKindModels.KINDS == ['subject', 'tag', 'part', 'assumption'] as Set
    }

    // ---- kinds of part ----

    def "types should answer the solution's kinds of part with every field when a person asks"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/topic-types'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].id').value(3))
            .andExpect(jsonPath('$[0].solutionId').value(1))
            .andExpect(jsonPath('$[0].name').value('Chapter'))
            .andExpect(jsonPath('$[0].figureLabel').value('words'))
            .andExpect(jsonPath('$[0].sortOrder').value(1))
            .andExpect(jsonPath('$[0].partCount').value(2))
    }

    @Unroll
    def "addType should pass the name trimmed and the label as sent when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/1/topic-types', body))).andExpect(status().isOk())
            .andExpect(jsonPath('$.name').value('Chapter'))

        then:
        1 * kinds.saveType(0, 1, expectedName, expectedLabel) >> 3

        where:
        scenario                         | who     | body                                          || expectedName | expectedLabel
        'a person sends a padded name'   | 'user'  | '{"name":"  Chapter ","figureLabel":" words "}' || 'Chapter'    | ' words '
        'an agent sends no label'        | 'rex'   | '{"name":"Chapter"}'                          || 'Chapter'    | null
        'the house clears the label'     | 'service' | '{"name":"chapter","figureLabel":""}'       || 'chapter'    | ''
    }

    @Unroll
    def "addType should answer 400 'A name for the kind of part is required.' and save nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/topic-types', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * kinds.saveType(*_)

        where:
        scenario               | body                        || expectedMessage
        'the name is missing'  | '{"figureLabel":"words"}'   || 'A name for the kind of part is required.'
        'the name is empty'    | '{"name":""}'               || 'A name for the kind of part is required.'
        'the name is blank'    | '{"name":"   "}'            || 'A name for the kind of part is required.'
    }

    @Unroll
    def "updateType should pass #scenario through as sent with solution zero"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/topic-types/3', body))).andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(3))

        then:
        1 * kinds.saveType(3, 0, expectedName, expectedLabel) >> 3

        where:
        scenario                  | body                                         || expectedName | expectedLabel
        'a new name only'         | '{"name":"Scene"}'                           || 'Scene'      | null
        'a cleared label only'    | '{"figureLabel":""}'                         || null         | ''
        'a padded name and label' | '{"name":" Scene ","figureLabel":"pages"}'   || ' Scene '    | 'pages'
        'nothing'                 | '{}'                                         || null         | null
    }

    @Unroll
    def "#scenario should answer 409 verbatim when the repository refuses"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        1 * kinds.saveType(*_) >> { throw new SolutionRuleException(refusal) }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                    | method | url                               | body                       | refusal                                                                  || expectedMessage
        'add a name that is too long' | 'POST' | '/api/solutions/1/topic-types'  | '{"name":"@101@"}'         | 'A kind of part can be at most 100 characters. Nothing was saved.'       || 'A kind of part can be at most 100 characters. Nothing was saved.'
        'rename onto a used name'   | 'PUT'  | '/api/solutions/topic-types/3'    | '{"name":"Account"}'       | 'This solution already has that kind of part. Nothing was saved.'        || 'This solution already has that kind of part. Nothing was saved.'
        'a label that is too long'  | 'PUT'  | '/api/solutions/topic-types/3'    | '{"figureLabel":"@41@"}'   | 'What a number counts can be at most 40 characters. Nothing was saved.'  || 'What a number counts can be at most 40 characters. Nothing was saved.'
    }

    def "deleteType should take the kind away and answer its id when the kind is the solution's"() {

        when:
        mvc.perform(asAgent(delete('/api/solutions/topic-types/3'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.deletedId').value(3))

        then:
        1 * kinds.deleteType(3) >> true
    }

    def "deleteType should answer 404 'Kind of part not found.' when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/topic-types/3')))

        then:
        1 * kinds.deleteType(3) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Kind of part not found.'))
    }

    // ---- Arrange ----

    def "reorder should pass the ids in the order sent and answer the solution when an agent arranges the parts"() {

        when:
        mvc.perform(asAgent(request('PUT', '/api/solutions/1/topics/order', '{"ids":[12,9,30]}'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.solutionId').value(1))

        then:
        1 * kinds.reorder(1, [12, 9, 30])
    }

    @Unroll
    def "reorder should answer 400 '#expectedMessage' and arrange nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('PUT', '/api/solutions/1/topics/order', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * kinds.reorder(*_)

        where:
        scenario                | body                  || expectedMessage
        'no ids are sent'       | '{}'                  || 'Name the topics in their new order.'
        'the ids are null'      | '{"ids":null}'        || 'Name the topics in their new order.'
        'the ids are empty'     | '{"ids":[]}'          || 'Name the topics in their new order.'
        'an id is zero'         | '{"ids":[12,0]}'      || 'The order is a list of topic ids.'
        'an id is negative'     | '{"ids":[-3]}'        || 'The order is a list of topic ids.'
        'an id is null'         | '{"ids":[12,null]}'   || 'The order is a list of topic ids.'
    }

    def "reorder should answer 409 verbatim when the repository refuses a topic of another solution"() {

        when:
        ResultActions r = mvc.perform(asUser(request('PUT', '/api/solutions/1/topics/order', '{"ids":[12,600]}')))

        then:
        1 * kinds.reorder(1, [12, 600]) >> { throw new SolutionRuleException('A topic in that order is not in this solution. Nothing was changed.') }
        r.andExpect(status().isConflict())
            .andExpect(jsonPath('$.message').value('A topic in that order is not in this solution. Nothing was changed.'))
    }

    // ---- open items ----

    def "openItems should answer the open items linked to the topic with every field when a person asks"() {

        when:
        mvc.perform(asUser(get('/api/solutions/topics/5/open-items'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[0].id').value(100))
            .andExpect(jsonPath('$[0].title').value('Upgrade'))
            .andExpect(jsonPath('$[0].sub').value('Deploy | Not started'))
            .andExpect(jsonPath('$[1].id').value(101))

        then:
        1 * kinds.openItems(5) >> [new OpenItem(100, 'Upgrade', 'Deploy | Not started'), new OpenItem(101, 'Watch', 'Deploy | Blocked')]
    }

    // ---- the premise switch ----

    @Unroll
    def "provedFalse should mark it false, cancel the item, write and link one entry attributed to #expectedCode when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/topics/5/proved-false', '{"reason":"  The path died. ","itemIds":[100]}')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.topic.id').value(5))
            .andExpect(jsonPath('$.cancelled[0].id').value(100))
            .andExpect(jsonPath('$.cancelled[0].title').value('Upgrade'))
            .andExpect(jsonPath('$.logId').value(410))
            .andExpect(jsonPath('$.logSequenceNumber').value(42))

        then:
        1 * linkRepo.saveTopic(provedFalseDraft(5), expectedCode) >> 5
        1 * items.setStatus({ SolutionCaller c -> c.memberCode() == expectedCode }, 100, 'cancelled')
        1 * logRepo.save(new LogDraft(0, 1, null, null, 'result', SWITCH_TEXT, null), expectedCode) >> 410
        1 * linkRepo.link(1, 'log', 410, 'topic', 5, expectedCode) >> 40

        where:
        scenario                        | who       || expectedCode
        'a person proves it false'      | 'user'    || 'tony'
        'the house proves it false'     | 'service' || 'felix'
        'the house speaks as Stewart'   | 'stewart' || 'stewart'
        'an agent proves it false'      | 'rex'     || 'rex'
    }

    def "provedFalse should cancel nothing when no item ids are sent"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/topics/5/proved-false', '{"reason":"Gone."}')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.cancelled.length()').value(0))
            .andExpect(jsonPath('$.stillOpen[0].id').value(100))

        then:
        1 * linkRepo.saveTopic(provedFalseDraft(5), 'tony') >> 5
        0 * items._
        1 * logRepo.save({ LogDraft d -> d.text() == '**Assumption proved false:** The 837 path is live\n\nGone.' }, 'tony') >> 410
    }

    @Unroll
    def "provedFalse should answer 400 '#expectedMessage' and change nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/topics/5/proved-false', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * linkRepo.saveTopic(*_)
        0 * items._
        0 * logRepo.save(*_)
        0 * linkRepo.link(*_)

        where:
        scenario                         | body                                     || expectedMessage
        'no reason is sent'              | '{"itemIds":[100]}'                      || 'Say why it no longer holds. The journal keeps the reason.'
        'the reason is blank'            | '{"reason":"   "}'                      || 'Say why it no longer holds. The journal keeps the reason.'
        'an item does not rest on it'    | '{"reason":"Gone.","itemIds":[999]}'     || 'The item 999 is not an open item resting on this assumption. Nothing was changed.'
        'a good item and a bad one'      | '{"reason":"Gone.","itemIds":[100,7]}'   || 'The item 7 is not an open item resting on this assumption. Nothing was changed.'
    }

    @Unroll
    def "provedFalse should answer 409 '#expectedMessage' and change nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', url, '{"reason":"Gone.","itemIds":[100]}')))

        then:
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))
        0 * kinds.openItems(_)
        0 * linkRepo.saveTopic(*_)
        0 * items._
        0 * logRepo.save(*_)

        where:
        scenario                                  | url                                       || expectedMessage
        'the topic is a subject'                  | '/api/solutions/topics/7/proved-false'    || 'Only an assumption can be proved false. Nothing was changed.'
        'the assumption was already proved false' | '/api/solutions/topics/8/proved-false'    || 'That assumption was already proved false. Nothing was changed.'
    }

    // ---- malformed bodies ----

    @Unroll
    def "#seq. #scenario should answer 400 and write nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request(method, url, null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * kinds.saveType(*_)
        0 * kinds.reorder(*_)
        0 * linkRepo.saveTopic(*_)
        0 * logRepo.save(*_)

        where:
        seq | scenario        | method | url                                      | body                 || expectedStatus
        1   | 'add a type'    | 'POST' | '/api/solutions/1/topic-types'           | ''                   || 400
        2   | 'add a type'    | 'POST' | '/api/solutions/1/topic-types'           | 'null'               || 400
        3   | 'add a type'    | 'POST' | '/api/solutions/1/topic-types'           | '{'                  || 400
        4   | 'update a type' | 'PUT'  | '/api/solutions/topic-types/3'           | ''                   || 400
        5   | 'update a type' | 'PUT'  | '/api/solutions/topic-types/3'           | '[]'                 || 400
        6   | 'arrange'       | 'PUT'  | '/api/solutions/1/topics/order'          | ''                   || 400
        7   | 'arrange'       | 'PUT'  | '/api/solutions/1/topics/order'          | '{"ids":["x"]}'      || 400
        8   | 'arrange'       | 'PUT'  | '/api/solutions/1/topics/order'          | '{'                  || 400
        9   | 'proved false'  | 'POST' | '/api/solutions/topics/5/proved-false'   | ''                   || 400
        10  | 'proved false'  | 'POST' | '/api/solutions/topics/5/proved-false'   | 'null'               || 400
        11  | 'proved false'  | 'POST' | '/api/solutions/topics/5/proved-false'   | '{"itemIds":"x"}'    || 400
    }
}
