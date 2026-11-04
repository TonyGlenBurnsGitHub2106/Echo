package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionLinkModels.LinkPage
import com.echo.SolutionLinkModels.LinkTarget
import com.echo.SolutionLinkModels.StoredLink
import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionLinkModels.TopicDraft

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695: Solution links, topics and "Since you were last here" over HTTP -- every endpoint, who may
 * call it, the query parameters and body fields that become the service call, and how each refusal maps
 * to a status (400 malformed, 404 not found or not shared, 409 a rule of the procs).
 */
@WebMvcTest(SolutionLinkController)
class SolutionLinkControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionLinkService service = Mock()

    SolutionLinkRepository links = Mock()
    SolutionLinkService real

    Map<Integer, Topic> topicRows = [5: topic(5, 1), 6: topic(6, 2)]
    Map<Integer, StoredLink> linkRows = [40: new StoredLink(40, 1, 'item', 100, 'note', 300),
                                         41: new StoredLink(41, 2, 'item', 101, 'term', 400)]

    def setup() {
        real = new SolutionLinkService(links, access)
        service.links(*_) >> { a -> real.links(*a) }
        service.link(*_) >> { a -> real.link(*a) }
        service.unlink(*_) >> { a -> real.unlink(*a) }
        service.targets(*_) >> { a -> real.targets(*a) }
        service.topics(*_) >> { a -> real.topics(*a) }
        service.topic(*_) >> { a -> real.topic(*a) }
        service.saveTopic(*_) >> { a -> real.saveTopic(*a) }
        service.deleteTopic(*_) >> { a -> real.deleteTopic(*a) }
        service.visit(*_) >> { a -> real.visit(*a) }
        service.since(*_) >> { a -> real.since(*a) }
        service.caughtUp(*_) >> { a -> real.caughtUp(*a) }

        links.links(*_) >> { int s, String k, int id, int off, int size -> new LinkPage(k, id, [], off, size, 0) }
        links.link(*_) >> 40
        links.findLink(_) >> { int id -> linkRows[id] }
        links.unlink(_) >> true
        links.targets(*_) >> [new LinkTarget('note', 300, 'Runbook', 'Runbook', null, false, true)]
        links.topics(*_) >> { int s, String q, int off, int size -> topicRows.values().findAll { it.solutionId() == s } as List }
        links.topicCounts(*_) >> [total: 1, all: 2]
        links.findTopic(_) >> { int id -> topicRows[id] }
        links.saveTopic(*_) >> { TopicDraft d, String by -> d.id() > 0 ? d.id() : 5 }
        links.deleteTopic(_) >> true
        links.changeCounts(*_) >> [total: 0]
        links.changes(*_) >> []
    }

    private static Topic topic(int id, int solutionId) {
        Topic.of([SolutionTopicID: id, SolutionID: solutionId, TopicName: 'Topic ' + id, TopicSummary: 'About ' + id])
    }

    @Unroll
    def "#seq. #scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * links._
        r.andExpect(status().is(expectedStatus))

        where:
        seq | scenario         | method   | url                                               | body                                                        || expectedStatus
        1   | 'links'          | 'GET'    | '/api/solutions/1/links?kind=item&recordId=100'   | null                                                        || 401
        2   | 'link'           | 'POST'   | '/api/solutions/1/links'                          | '{"fromKind":"item","fromId":100,"toKind":"note","toId":300}' || 401
        3   | 'unlink'         | 'DELETE' | '/api/solutions/1/links/40'                       | null                                                        || 401
        4   | 'targets'        | 'GET'    | '/api/solutions/1/link-targets'                   | null                                                        || 401
        5   | 'topics'         | 'GET'    | '/api/solutions/1/topics'                         | null                                                        || 401
        6   | 'add a topic'    | 'POST'   | '/api/solutions/1/topics'                         | '{"name":"Charset"}'                                        || 401
        7   | 'topic'          | 'GET'    | '/api/solutions/topics/5'                         | null                                                        || 401
        8   | 'locate'         | 'GET'    | '/api/solutions/topics/5/locate'                  | null                                                        || 401
        9   | 'update a topic' | 'PUT'    | '/api/solutions/topics/5'                         | '{"summary":"New"}'                                         || 401
        10  | 'delete a topic' | 'DELETE' | '/api/solutions/topics/5'                         | null                                                        || 401
        11  | 'visit'          | 'POST'   | '/api/solutions/1/visit'                          | null                                                        || 401
        12  | 'since'          | 'GET'    | '/api/solutions/1/since'                          | null                                                        || 401
        13  | 'caught up'      | 'POST'   | '/api/solutions/1/caught-up'                      | null                                                        || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        seq | scenario         | method   | url                                               | body                                                        || path               | expectedValue
        1   | 'links'          | 'GET'    | '/api/solutions/1/links?kind=item&recordId=100'   | null                                                        || '$.kind'           | 'item'
        2   | 'link'           | 'POST'   | '/api/solutions/1/links'                          | '{"fromKind":"item","fromId":100,"toKind":"note","toId":300}' || '$.id'             | 100
        3   | 'unlink'         | 'DELETE' | '/api/solutions/1/links/40'                       | null                                                        || '$.deletedId'      | 40
        4   | 'targets'        | 'GET'    | '/api/solutions/1/link-targets'                   | null                                                        || '$[0].kind'        | 'note'
        5   | 'topics'         | 'GET'    | '/api/solutions/1/topics'                         | null                                                        || '$.total'          | 1
        6   | 'add a topic'    | 'POST'   | '/api/solutions/1/topics'                         | '{"name":"Charset"}'                                        || '$.solutionId'     | 1
        7   | 'topic'          | 'GET'    | '/api/solutions/topics/5'                         | null                                                        || '$.name'           | 'Topic 5'
        8   | 'locate'         | 'GET'    | '/api/solutions/topics/5/locate'                  | null                                                        || '$.solutionId'     | 1
        9   | 'update a topic' | 'PUT'    | '/api/solutions/topics/5'                         | '{"summary":"New"}'                                         || '$.id'             | 5
        10  | 'delete a topic' | 'DELETE' | '/api/solutions/topics/5'                         | null                                                        || '$.deletedId'      | 5
        11  | 'visit'          | 'POST'   | '/api/solutions/1/visit'                          | null                                                        || '$.firstVisit'     | true
        12  | 'since'          | 'GET'    | '/api/solutions/1/since'                          | null                                                        || '$.pageSize'       | 50
        13  | 'caught up'      | 'POST'   | '/api/solutions/1/caught-up'                      | null                                                        || '$.total'          | 0
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * links.link(*_)
        0 * links.unlink(_)
        0 * links.saveTopic(*_)
        0 * links.deleteTopic(_)
        0 * links.touch(*_)
        0 * links.caughtUp(*_)
        0 * links.links(*_)
        0 * links.targets(*_)
        0 * links.topics(*_)
        0 * links.changes(*_)

        where:
        seq | scenario         | who   | method   | url                                               | body                                                        || expectedStatus
        1   | 'links'          | 'zed' | 'GET'    | '/api/solutions/1/links?kind=item&recordId=100'   | null                                                        || 404
        2   | 'link'           | 'zed' | 'POST'   | '/api/solutions/1/links'                          | '{"fromKind":"item","fromId":100,"toKind":"note","toId":300}' || 404
        3   | 'unlink'         | 'zed' | 'DELETE' | '/api/solutions/1/links/40'                       | null                                                        || 404
        4   | 'targets'        | 'zed' | 'GET'    | '/api/solutions/1/link-targets'                   | null                                                        || 404
        5   | 'topics'         | 'zed' | 'GET'    | '/api/solutions/1/topics'                         | null                                                        || 404
        6   | 'add a topic'    | 'zed' | 'POST'   | '/api/solutions/1/topics'                         | '{"name":"Charset"}'                                        || 404
        7   | 'topic'          | 'zed' | 'GET'    | '/api/solutions/topics/5'                         | null                                                        || 404
        8   | 'locate'         | 'zed' | 'GET'    | '/api/solutions/topics/5/locate'                  | null                                                        || 404
        9   | 'update a topic' | 'zed' | 'PUT'    | '/api/solutions/topics/5'                         | '{"summary":"New"}'                                         || 404
        10  | 'delete a topic' | 'zed' | 'DELETE' | '/api/solutions/topics/5'                         | null                                                        || 404
        11  | 'visit'          | 'zed' | 'POST'   | '/api/solutions/1/visit'                          | null                                                        || 404
        12  | 'since'          | 'zed' | 'GET'    | '/api/solutions/1/since'                          | null                                                        || 404
        13  | 'caught up'      | 'zed' | 'POST'   | '/api/solutions/1/caught-up'                      | null                                                        || 404
        14  | 'links'          | 'rex' | 'GET'    | '/api/solutions/2/links?kind=item&recordId=101'   | null                                                        || 404
        15  | 'link'           | 'rex' | 'POST'   | '/api/solutions/2/links'                          | '{"fromKind":"item","fromId":101,"toKind":"term","toId":400}' || 404
        16  | 'unlink'         | 'rex' | 'DELETE' | '/api/solutions/2/links/41'                       | null                                                        || 404
        17  | 'targets'        | 'rex' | 'GET'    | '/api/solutions/2/link-targets?q=x'               | null                                                        || 404
        18  | 'topics'         | 'rex' | 'GET'    | '/api/solutions/2/topics'                         | null                                                        || 404
        19  | 'add a topic'    | 'rex' | 'POST'   | '/api/solutions/2/topics'                         | '{"name":"Charset"}'                                        || 404
        20  | 'topic'          | 'rex' | 'GET'    | '/api/solutions/topics/6'                         | null                                                        || 404
        21  | 'locate'         | 'rex' | 'GET'    | '/api/solutions/topics/6/locate'                  | null                                                        || 404
        22  | 'update a topic' | 'rex' | 'PUT'    | '/api/solutions/topics/6'                         | '{"summary":"New"}'                                         || 404
        23  | 'delete a topic' | 'rex' | 'DELETE' | '/api/solutions/topics/6'                         | null                                                        || 404
        24  | 'visit'          | 'rex' | 'POST'   | '/api/solutions/2/visit'                          | null                                                        || 404
        25  | 'since'          | 'rex' | 'GET'    | '/api/solutions/2/since'                          | null                                                        || 404
        26  | 'caught up'      | 'rex' | 'POST'   | '/api/solutions/2/caught-up'                      | null                                                        || 404
    }

    @Unroll
    def "#scenario should answer 404 and touch no record when the solution does not exist"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * links.link(*_)
        0 * links.links(*_)
        0 * links.touch(*_)
        0 * links.saveTopic(*_)

        where:
        scenario         | method | url                                               | body                                                          || expectedStatus
        'links'          | 'GET'  | '/api/solutions/9/links?kind=item&recordId=100'   | null                                                          || 404
        'link'           | 'POST' | '/api/solutions/9/links'                          | '{"fromKind":"item","fromId":100,"toKind":"note","toId":300}' || 404
        'add a topic'    | 'POST' | '/api/solutions/9/topics'                         | '{"name":"Charset"}'                                          || 404
        'visit'          | 'POST' | '/api/solutions/9/visit'                          | null                                                          || 404
    }

    // ---- links ----

    def "links should pass the kind, record and a normalized page when every query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/links').param('kind', ' ITEM ').param('recordId', '100')
            .param('offset', '-5').param('pageSize', '9999')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.kind').value('item'))
            .andExpect(jsonPath('$.offset').value(0))
            .andExpect(jsonPath('$.pageSize').value(500))

        then:
        1 * links.links(1, 'item', 100, 0, 500) >> new LinkPage('item', 100, [], 0, 500, 0)
    }

    def "links should ask for the first page of 50 when no paging is sent"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/links').param('kind', 'note').param('recordId', '300'))).andExpect(status().isOk())

        then:
        1 * links.links(1, 'note', 300, 0, 50) >> new LinkPage('note', 300, [], 0, 50, 0)
    }

    @Unroll
    def "links should answer 400 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get(url)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * links.links(*_)

        where:
        scenario                        | url                                                       || expectedStatus
        'the kind is missing'           | '/api/solutions/1/links?recordId=100'                     || 400
        'the record id is missing'      | '/api/solutions/1/links?kind=item'                        || 400
        'the record id is a word'       | '/api/solutions/1/links?kind=item&recordId=x'             || 400
        'the offset is a fraction'      | '/api/solutions/1/links?kind=item&recordId=1&offset=1.5'  || 400
        'the page size is a word'       | '/api/solutions/1/links?kind=item&recordId=1&pageSize=a'  || 400
        'the kind is not one of eight'    | '/api/solutions/1/links?kind=story&recordId=1'            || 400
    }

    def "links should name the eight kinds when the kind is unknown"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/links').param('kind', 'story').param('recordId', '1')))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value('kind must be one of item, note, log, file, term, topic, question, decision.'))
    }

    def "link should build the call from the body and answer the from record's links when an agent links two records"() {

        when:
        mvc.perform(asAgent(request('POST', '/api/solutions/1/links', '{"fromKind":" Note ","fromId":300,"toKind":"ITEM","toId":100}')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.kind').value('note'))
            .andExpect(jsonPath('$.id').value(300))

        then:
        1 * links.link(1, 'note', 300, 'item', 100, 'rex') >> 40

        then:
        1 * links.links(1, 'note', 300, 0, 50) >> new LinkPage('note', 300, [], 0, 50, 1)
    }

    @Unroll
    def "link should attribute the link to #expectedCode when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/1/links', '{"fromKind":"item","fromId":100,"toKind":"note","toId":300}')))
            .andExpect(status().isOk())

        then:
        1 * links.link(1, 'item', 100, 'note', 300, expectedCode) >> 40

        where:
        scenario                       | who       || expectedCode
        'a person links them'          | 'user'    || 'tony'
        'the house links them'         | 'service' || 'felix'
        'the house links as Stewart'   | 'stewart' || 'stewart'
        'an agent links them'          | 'rex'     || 'rex'
    }

    @Unroll
    def "link should answer 400 with '#expectedMessage' and link nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/links', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * links.link(*_)

        where:
        scenario                    | body                                                           || expectedMessage
        'fromId is missing'       | '{"fromKind":"item","toKind":"note","toId":300}'               || 'fromKind, fromId, toKind and toId are required.'
        'toId is missing'           | '{"fromKind":"item","fromId":100,"toKind":"note"}'             || 'fromKind, fromId, toKind and toId are required.'
        'fromKind is missing'       | '{"fromId":100,"toKind":"note","toId":300}'                    || 'fromKind is required.'
        'toKind is blank'           | '{"fromKind":"item","fromId":100,"toKind":"  ","toId":300}'    || 'toKind is required.'
        'a kind is not one of eight'  | '{"fromKind":"story","fromId":1,"toKind":"note","toId":300}'   || 'kind must be one of item, note, log, file, term, topic, question, decision.'
    }

    @Unroll
    def "link should answer 409 verbatim when the repository refuses because #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/links', '{"fromKind":"item","fromId":100,"toKind":"note","toId":300}')))

        then:
        1 * links.link(*_) >> { throw new SolutionRuleException(refusal) }
        0 * links.links(*_)
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                  | refusal                                                              || expectedMessage
        'the pair is already linked'              | 'Those two are already linked. Nothing was saved.'                   || 'Those two are already linked. Nothing was saved.'
        'a record is linked to itself'            | 'A record cannot be linked to itself. Nothing was saved.'            || 'A record cannot be linked to itself. Nothing was saved.'
        'the other record is in another solution' | 'The record to link to is not in this solution. Nothing was saved.'  || 'The record to link to is not in this solution. Nothing was saved.'
    }

    def "unlink should take the link away and answer its id when the link is the solution's"() {

        when:
        mvc.perform(asUser(delete('/api/solutions/1/links/40'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.deletedId').value(40))

        then:
        1 * links.unlink(40) >> true
    }

    @Unroll
    def "unlink should answer 404 'Link not found.' and remove nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(delete(url)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * links.unlink(_)

        where:
        scenario                                   | url                             || expectedMessage
        'the link belongs to another solution'     | '/api/solutions/1/links/41'     || 'Link not found.'
        'the link does not exist'                  | '/api/solutions/1/links/99'     || 'Link not found.'
        'the link id is zero'                      | '/api/solutions/1/links/0'      || 'Link not found.'
    }

    def "unlink should answer 404 'Link not found.' when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/1/links/40')))

        then:
        1 * links.unlink(40) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Link not found.'))
    }

    def "unlink should match no endpoint when the link id is not a number"() {

        when:
        mvc.perform(asUser(delete('/api/solutions/1/links/abc'))).andExpect(status().isNotFound())

        then:
        0 * links.unlink(_)
        0 * links.findLink(_)
    }

    // ---- the Link picker ----

    def "targets should pass the search, kind, record and a capped page size when every query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/link-targets').param('q', '  charset ').param('kind', 'ITEM')
            .param('recordId', '100').param('pageSize', '500')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$[0].kind').value('topic'))
            .andExpect(jsonPath('$[0].alreadyLinked').value(true))

        then:
        1 * links.targets(1, 'charset', 'item', 100, 100) >> [new LinkTarget('topic', 5, 'Charset', null, null, false, true)]
    }

    def "targets should search everything in pages of 30 when no query parameter is sent"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/link-targets'))).andExpect(status().isOk())

        then:
        1 * links.targets(1, null, null, null, 30) >> []
    }

    @Unroll
    def "targets should answer 400 and search nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get(url)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * links.targets(*_)

        where:
        scenario                      | url                                              || expectedStatus
        'the kind is not one of eight'  | '/api/solutions/1/link-targets?kind=story'       || 400
        'the record id is a word'     | '/api/solutions/1/link-targets?recordId=x'       || 400
        'the page size is a fraction' | '/api/solutions/1/link-targets?pageSize=2.5'     || 400
    }

    // ---- topics ----

    def "topics should pass the search and paging and answer the page when every query parameter is sent"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/topics').param('search', ' charset ').param('offset', '10').param('pageSize', '20')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.topics[0].name').value('Topic 5'))
            .andExpect(jsonPath('$.counts.all').value(2))
            .andExpect(jsonPath('$.total').value(1))
            .andExpect(jsonPath('$.offset').value(10))
            .andExpect(jsonPath('$.pageSize').value(20))

        then:
        1 * links.topicCounts(1, 'charset') >> [total: 1, all: 2]
        1 * links.topics(1, 'charset', 10, 20) >> [topicRows[5]]
    }

    def "topics should ask for the first page of 50 with no search when no query parameter is sent"() {

        when:
        mvc.perform(asUser(get('/api/solutions/1/topics'))).andExpect(status().isOk())

        then:
        1 * links.topics(1, null, 0, 50) >> []
    }

    def "addTopic should build an add draft with the name trimmed when a person adds a topic"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/topics', '{"name":"  Charset ","summary":" windows-1252 "}')))
            .andExpect(status().isOk())

        then:
        1 * links.saveTopic(new TopicDraft(0, 1, 'Charset', ' windows-1252 '), 'tony') >> 5
    }

    def "addTopic should leave the summary null when it is not sent"() {

        when:
        mvc.perform(asAgent(request('POST', '/api/solutions/1/topics', '{"name":"Charset"}'))).andExpect(status().isOk())

        then:
        1 * links.saveTopic(new TopicDraft(0, 1, 'Charset', null), 'rex') >> 5
    }

    @Unroll
    def "addTopic should answer 400 'A topic name is required.' and save nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/topics', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * links.saveTopic(*_)

        where:
        scenario                 | body                      || expectedMessage
        'the name is missing'   | '{"summary":"no name"}'   || 'A topic name is required.'
        'the name is empty'      | '{"name":""}'             || 'A topic name is required.'
        'the name is blank'      | '{"name":"   "}'          || 'A topic name is required.'
    }

    def "addTopic should answer 409 verbatim when the repository refuses a repeat name"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/topics', '{"name":"charset"}')))

        then:
        1 * links.saveTopic(*_) >> { throw new SolutionRuleException('This solution already has the topic "Charset". Nothing was saved.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('This solution already has the topic "Charset". Nothing was saved.'))
    }

    @Unroll
    def "updateTopic should build a merge draft with solution zero when #scenario"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/topics/5', body))).andExpect(status().isOk())

        then:
        1 * links.saveTopic(new TopicDraft(5, 0, expectedName, expectedSummary), 'tony') >> 5

        where:
        scenario                          | body                                 || expectedName | expectedSummary
        'only the summary is cleared'     | '{"summary":""}'                     || null         | ''
        'only the name is changed'        | '{"name":"New name"}'                || 'New name'   | null
        'both are sent'                   | '{"name":" Padded ","summary":"s"}'  || ' Padded '   | 's'
        'nothing is sent'                 | '{}'                                 || null         | null
    }

    def "locateTopic should answer only the topic and its solution when a person asks"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/topics/6/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.topicId').value(6))
            .andExpect(jsonPath('$.solutionId').value(2))
            .andExpect(jsonPath('$.name').doesNotExist())
            .andExpect(jsonPath('$.summary').doesNotExist())
    }

    def "topic should answer the whole topic when a person reads it"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/topics/6'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(6))
            .andExpect(jsonPath('$.solutionId').value(2))
            .andExpect(jsonPath('$.name').value('Topic 6'))
            .andExpect(jsonPath('$.summary').value('About 6'))
    }

    @Unroll
    def "#scenario should answer 404 'Topic not found.' when the topic is missing"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * links.saveTopic(*_)
        0 * links.deleteTopic(_)

        where:
        scenario      | method   | url                                  | body                  || expectedMessage
        'topic'       | 'GET'    | '/api/solutions/topics/99'           | null                  || 'Topic not found.'
        'locate'      | 'GET'    | '/api/solutions/topics/0/locate'     | null                  || 'Topic not found.'
        'update'      | 'PUT'    | '/api/solutions/topics/99'           | '{"name":"x"}'        || 'Topic not found.'
        'delete'      | 'DELETE' | '/api/solutions/topics/99'           | null                  || 'Topic not found.'
    }

    def "deleteTopic should answer 404 'Topic not found.' when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/topics/5')))

        then:
        1 * links.deleteTopic(5) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Topic not found.'))
    }

    @Unroll
    def "#seq. #scenario should answer 400 and write nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request(method, url, null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * links.link(*_)
        0 * links.saveTopic(*_)

        where:
        seq | scenario         | method | url                              | body                                                          || expectedStatus
        1   | 'link'           | 'POST' | '/api/solutions/1/links'         | ''                                                            || 400
        2   | 'link'           | 'POST' | '/api/solutions/1/links'         | 'null'                                                        || 400
        3   | 'link'           | 'POST' | '/api/solutions/1/links'         | '{'                                                           || 400
        4   | 'link'           | 'POST' | '/api/solutions/1/links'         | '[]'                                                          || 400
        5   | 'link'           | 'POST' | '/api/solutions/1/links'         | '{"fromKind":"item","fromId":"x","toKind":"note","toId":1}'   || 400
        6   | 'add a topic'    | 'POST' | '/api/solutions/1/topics'        | ''                                                            || 400
        7   | 'add a topic'    | 'POST' | '/api/solutions/1/topics'        | 'null'                                                        || 400
        8   | 'add a topic'    | 'POST' | '/api/solutions/1/topics'        | '{'                                                           || 400
        9   | 'update a topic' | 'PUT'  | '/api/solutions/topics/5'        | ''                                                            || 400
        10  | 'update a topic' | 'PUT'  | '/api/solutions/topics/5'        | 'null'                                                        || 400
        11  | 'update a topic' | 'PUT'  | '/api/solutions/topics/5'        | '[]'                                                          || 400
    }

    // ---- since you were last here ----

    @Unroll
    def "visit should count the visit of #expectedCode and answer what changed when #scenario"() {

        when:
        mvc.perform(by(who, post('/api/solutions/1/visit'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.firstVisit').value(true))
            .andExpect(jsonPath('$.total').value(0))
            .andExpect(jsonPath('$.offset').value(0))
            .andExpect(jsonPath('$.pageSize').value(50))

        then:
        1 * links.touch(1, expectedCode)

        then:
        1 * links.visit(1, expectedCode)

        where:
        scenario                        | who       || expectedCode
        'a person opens it'             | 'user'    || 'tony'
        'the house opens it'            | 'service' || 'felix'
        'the house opens it as Stewart' | 'stewart' || 'stewart'
        'an agent opens it'             | 'rex'     || 'rex'
    }

    def "since should pass the caller and a normalized page when every query parameter is sent"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/since').param('offset', '5').param('pageSize', '9999')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.offset').value(5))
            .andExpect(jsonPath('$.pageSize').value(500))

        then:
        1 * links.changes(1, _, 'rex', 5, 500) >> []
        0 * links.touch(*_)
    }

    @Unroll
    def "since should answer 400 and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get(url)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * links.changes(*_)

        where:
        scenario                    | url                                       || expectedStatus
        'the offset is a word'      | '/api/solutions/1/since?offset=x'         || 400
        'the page size is a word'   | '/api/solutions/1/since?pageSize=lots'    || 400
    }

    def "caughtUp should start since now for the caller and then answer what changed"() {

        when:
        mvc.perform(asUser(post('/api/solutions/1/caught-up'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.total').value(0))

        then:
        1 * links.caughtUp(1, 'tony')

        then:
        1 * links.changes(1, _, 'tony', 0, 50) >> []
        0 * links.touch(*_)
    }

    def "caughtUp should answer 409 verbatim when the repository refuses the visit"() {

        when:
        ResultActions r = mvc.perform(asUser(post('/api/solutions/1/caught-up')))

        then:
        1 * links.caughtUp(1, 'tony') >> { throw new SolutionRuleException('That solution no longer exists.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('That solution no longer exists.'))
    }
}
