package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.ApiClientModels.SolutionLink
import com.echo.SolutionModels.ConversationSummary
import com.echo.SolutionModels.Message

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5653: the agent and house relay over HTTP -- every endpoint, who may call it (the owner's actions
 * are 403 to an agent that can see the thread and 404 to one that cannot), and who a message waits on.
 */
@WebMvcTest(SolutionConversationController)
class SolutionConversationControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionConversationService service = Mock()

    SolutionConversationRepository repo = Mock()
    SolutionConversationService real

    Map<Integer, List<Integer>> links = [41: [1], 42: [2]]
    List<Message> messages = [new Message(501, 41, 'rex', 'Hello', null, 'tony'), new Message(502, 42, 'felix', 'Hi', null, null)]

    def setup() {
        real = new SolutionConversationService(repo, apiClients, access)
        service.list(*_) >> { a -> real.list(*a) }
        service.create(*_) >> { a -> real.create(*a) }
        service.messages(*_) >> { a -> real.messages(*a) }
        service.post(*_) >> { a -> real.post(*a) }
        service.rename(*_) >> { a -> real.rename(*a) }
        service.resolve(*_) >> { a -> real.resolve(*a) }
        service.editMessage(*_) >> { a -> real.editMessage(*a) }
        service.deleteMessage(*_) >> { a -> real.deleteMessage(*a) }
        service.unlink(*_) >> { a -> real.unlink(*a) }

        repo.solutionIdsOf(_) >> { int c -> links[c] ?: [] }
        repo.listForSolution(_) >> { int s ->
            links.findAll { it.value.contains(s) }.collect { c, l -> new ConversationSummary(c, 'Thread ' + c, 'active', null, null, 1, 'rex', null, 0) }
        }
        repo.messagesSince(_, _) >> { int c, int since -> messages.findAll { it.conversationId() == c && it.id() > since } }
        repo.addMessage(_, _, _, _) >> { int c, String sender, String body, String awaiting ->
            int id = 600 + messages.size()
            messages << new Message(id, c, sender, body, null, awaiting)
            id
        }
        repo.create(_, _) >> { int s, String t -> links[45] = [s]; 45 }
        repo.rename(*_) >> true
        repo.setStatus(*_) >> true
        repo.editMessage(*_) >> true
        repo.deleteMessage(*_) >> true
        repo.unlink(*_) >> true
        apiClients.clientsForSolution(_) >> { int s -> s == 1 ? [new SolutionLink(REX, 1, 'S1', 'rex', 'Rex')] : [] }
    }

    @Unroll
    def "#seq. #scenario should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * repo._
        r.andExpect(status().is(expectedStatus))

        where:
        seq | scenario         | method   | url                                               | body                           || expectedStatus
        1   | 'list'           | 'GET'    | '/api/solutions/1/conversations'                  | null                           || 401
        2   | 'create'         | 'POST'   | '/api/solutions/1/conversations'                  | '{"title":"Q","message":"Hi"}' || 401
        3   | 'messages'       | 'GET'    | '/api/solutions/conversations/41/messages'        | null                           || 401
        4   | 'post'           | 'POST'   | '/api/solutions/conversations/41/messages'        | '{"body":"Hello again"}'       || 401
        5   | 'rename'         | 'PUT'    | '/api/solutions/conversations/41'                 | '{"title":"Renamed"}'          || 401
        6   | 'resolve'        | 'PUT'    | '/api/solutions/conversations/41/resolved'        | '{"resolved":true}'            || 401
        7   | 'editMessage'    | 'PUT'    | '/api/solutions/conversations/41/messages/501'    | '{"body":"Edited"}'            || 401
        8   | 'deleteMessage'  | 'DELETE' | '/api/solutions/conversations/41/messages/501'    | null                           || 401
        9   | 'unlink'         | 'DELETE' | '/api/solutions/1/conversations/41'               | null                           || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 when a person or the house calls"() {

        expect:
        for (String who : ['user', 'service']) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        seq | scenario         | method   | url                                               | body                              || path                  | expectedValue
        1   | 'list'           | 'GET'    | '/api/solutions/1/conversations'                  | null                              || '$[0].conversationId' | 41
        2   | 'create'         | 'POST'   | '/api/solutions/1/conversations'                  | '{"title":"Q","message":"Hi"}'    || '$.conversationId'    | 45
        3   | 'messages'       | 'GET'    | '/api/solutions/conversations/41/messages'        | null                              || '$[0].id'             | 501
        4   | 'post'           | 'POST'   | '/api/solutions/conversations/41/messages'        | '{"body":"Hello again"}'          || '$.conversationId'    | 41
        5   | 'rename'         | 'PUT'    | '/api/solutions/conversations/41'                 | '{"title":"Renamed"}'             || '$.conversationId'    | 41
        6   | 'resolve'        | 'PUT'    | '/api/solutions/conversations/41/resolved'        | '{"resolved":true}'               || '$.conversationId'    | 41
        7   | 'editMessage'    | 'PUT'    | '/api/solutions/conversations/41/messages/501'    | '{"body":"Edited"}'               || '$.id'                | 501
        8   | 'deleteMessage'  | 'DELETE' | '/api/solutions/conversations/41/messages/501'    | null                              || '$.messageId'         | 501
        9   | 'unlink'         | 'DELETE' | '/api/solutions/1/conversations/41'               | null                              || '$.unlinkedId'        | 41
    }

    @Unroll
    def "#scenario should answer 200 when an agent the solution is shared with calls"() {

        expect:
        mvc.perform(asAgent(request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))

        where:
        scenario     | method | url                                           | body                              || path                  | expectedValue
        'list'       | 'GET'  | '/api/solutions/1/conversations'              | null                              || '$[0].conversationId' | 41
        'create'     | 'POST' | '/api/solutions/1/conversations'              | '{"title":"Q","message":"Hi"}'    || '$.conversationId'    | 45
        'messages'   | 'GET'  | '/api/solutions/conversations/41/messages'    | null                              || '$[0].id'             | 501
        'post'       | 'POST' | '/api/solutions/conversations/41/messages'    | '{"body":"Hello again"}'          || '$.conversationId'    | 41
    }

    @Unroll
    def "#scenario should answer 403 and change nothing when an agent that can see the thread calls"() {

        when:
        ResultActions r = mvc.perform(asAgent(request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('forbidden'))
        0 * repo.rename(*_)
        0 * repo.setStatus(*_)
        0 * repo.editMessage(*_)
        0 * repo.deleteMessage(*_)

        where:
        scenario         | method   | url                                               | body                  || expectedStatus
        'rename'         | 'PUT'    | '/api/solutions/conversations/41'                 | '{"title":"Renamed"}' || 403
        'resolve'        | 'PUT'    | '/api/solutions/conversations/41/resolved'        | '{"resolved":true}'   || 403
        'editMessage'    | 'PUT'    | '/api/solutions/conversations/41/messages/501'    | '{"body":"Edited"}'   || 403
        'deleteMessage'  | 'DELETE' | '/api/solutions/conversations/41/messages/501'    | null                  || 403
    }

    @PendingFeature(reason = 'BUG: SolutionConversationController documents DELETE /api/solutions/{id}/conversations/{convId} as "unlink (a person only)" but SolutionConversationService.unlink lets an API client do it (200)')
    def "unlink should answer 403 when an agent calls"() {

        expect:
        mvc.perform(asAgent(delete('/api/solutions/1/conversations/41'))).andExpect(status().isForbidden())
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who cannot see the thread"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('not_found'))
        0 * repo.create(*_)
        0 * repo.addMessage(*_)
        0 * repo.rename(*_)
        0 * repo.setStatus(*_)
        0 * repo.editMessage(*_)
        0 * repo.deleteMessage(*_)
        0 * repo.unlink(*_)
        0 * repo.clearAwaitingFor(*_)

        where:
        seq | scenario         | who   | method   | url                                               | body                           || expectedStatus
        1   | 'list'           | 'rex' | 'GET'    | '/api/solutions/2/conversations'                  | null                           || 404
        2   | 'create'         | 'rex' | 'POST'   | '/api/solutions/2/conversations'                  | '{"title":"Q","message":"Hi"}' || 404
        3   | 'messages'       | 'rex' | 'GET'    | '/api/solutions/conversations/42/messages'        | null                           || 404
        4   | 'post'           | 'rex' | 'POST'   | '/api/solutions/conversations/42/messages'        | '{"body":"x"}'                 || 404
        5   | 'rename'         | 'rex' | 'PUT'    | '/api/solutions/conversations/42'                 | '{"title":"x"}'                || 404
        6   | 'resolve'        | 'rex' | 'PUT'    | '/api/solutions/conversations/42/resolved'        | '{"resolved":true}'            || 404
        7   | 'editMessage'    | 'rex' | 'PUT'    | '/api/solutions/conversations/42/messages/502'    | '{"body":"x"}'                 || 404
        8   | 'deleteMessage'  | 'rex' | 'DELETE' | '/api/solutions/conversations/42/messages/502'    | null                           || 404
        9   | 'unlink'         | 'rex' | 'DELETE' | '/api/solutions/2/conversations/42'               | null                           || 404
        10  | 'list'           | 'zed' | 'GET'    | '/api/solutions/1/conversations'                  | null                           || 404
        11  | 'create'         | 'zed' | 'POST'   | '/api/solutions/1/conversations'                  | '{"title":"Q","message":"Hi"}' || 404
        12  | 'messages'       | 'zed' | 'GET'    | '/api/solutions/conversations/41/messages'        | null                           || 404
        13  | 'post'           | 'zed' | 'POST'   | '/api/solutions/conversations/41/messages'        | '{"body":"x"}'                 || 404
        14  | 'rename'         | 'zed' | 'PUT'    | '/api/solutions/conversations/41'                 | '{"title":"x"}'                || 404
        15  | 'resolve'        | 'zed' | 'PUT'    | '/api/solutions/conversations/41/resolved'        | '{"resolved":true}'            || 404
        16  | 'editMessage'    | 'zed' | 'PUT'    | '/api/solutions/conversations/41/messages/501'    | '{"body":"x"}'                 || 404
        17  | 'deleteMessage'  | 'zed' | 'DELETE' | '/api/solutions/conversations/41/messages/501'    | null                           || 404
        18  | 'unlink'         | 'zed' | 'DELETE' | '/api/solutions/1/conversations/41'               | null                           || 404
    }

    def "post should wait on tony for an agent's message and on the agent for the house's reply"() {

        when:
        mvc.perform(asAgent(request('POST', '/api/solutions/conversations/41/messages', '{"body":"Done?"}'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.sender').value('rex'))
            .andExpect(jsonPath('$.awaitingFrom').value('tony'))
        mvc.perform(asActor(request('POST', '/api/solutions/conversations/41/messages', '{"body":"Yes"}'), 'stewart')).andExpect(status().isOk())
            .andExpect(jsonPath('$.sender').value('stewart'))
            .andExpect(jsonPath('$.awaitingFrom').value('rex'))

        then:
        1 * repo.clearAwaitingFor(41, 'tony')
    }

    def "post should keep the agent as the sender when the agent names another actor"() {

        expect:
        mvc.perform(asAgent(request('POST', '/api/solutions/conversations/41/messages', '{"body":"x"}')).header('X-Echo-Actor', 'tony'))
            .andExpect(status().isOk()).andExpect(jsonPath('$.sender').value('rex'))
    }

    def "messages should clear the reader's waiting flag when an agent or a person reads"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/conversations/41/messages').param('since', '500'))).andExpect(status().isOk())
            .andExpect(jsonPath('$[0].id').value(501))
        mvc.perform(asUser(get('/api/solutions/conversations/41/messages'))).andExpect(status().isOk())
        mvc.perform(asService(get('/api/solutions/conversations/41/messages'))).andExpect(status().isOk())

        then:
        1 * repo.clearAwaitingFor(41, 'rex')
        1 * repo.clearAwaitingFor(41, 'tony')
    }

    def "messages should answer 400 when since is not a number"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/conversations/41/messages').param('since', 'yesterday'))).andExpect(status().isBadRequest())
    }

    @Unroll
    def "#seq. #scenario should answer 400 with what is missing when the text is absent"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario                     | method | url                                               | body               || expectedMessage
        1   | 'create without title'       | 'POST' | '/api/solutions/1/conversations'                  | '{"message":"x"}'  || 'A title is required.'
        2   | 'create blank title'         | 'POST' | '/api/solutions/1/conversations'                  | '{"title":" "}'    || 'A title is required.'
        3   | 'post without body'          | 'POST' | '/api/solutions/conversations/41/messages'        | '{}'               || 'Some text is required.'
        4   | 'post blank body'            | 'POST' | '/api/solutions/conversations/41/messages'        | '{"body":"   "}'   || 'Some text is required.'
        5   | 'rename empty title'         | 'PUT'  | '/api/solutions/conversations/41'                 | '{"title":""}'     || 'A conversation needs a title.'
        6   | 'rename without title'       | 'PUT'  | '/api/solutions/conversations/41'                 | '{}'               || 'A conversation needs a title.'
        7   | 'resolve without the flag'   | 'PUT'  | '/api/solutions/conversations/41/resolved'        | '{}'               || 'Say resolved true or false.'
        8   | 'editMessage empty body'     | 'PUT'  | '/api/solutions/conversations/41/messages/501'    | '{"body":""}'      || 'A message needs some text.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 when the body is '#body'"() {

        expect:
        mvc.perform(asUser(json(request(method, url, null), body))).andExpect(status().is(expectedStatus))

        where:
        seq | scenario        | method | url                                               | body       || expectedStatus
        1   | 'create'        | 'POST' | '/api/solutions/1/conversations'                  | ''         || 400
        2   | 'create'        | 'POST' | '/api/solutions/1/conversations'                  | 'null'     || 400
        3   | 'post'          | 'POST' | '/api/solutions/conversations/41/messages'        | '{"title"' || 400
        4   | 'post'          | 'POST' | '/api/solutions/conversations/41/messages'        | 'true'     || 400
        5   | 'rename'        | 'PUT'  | '/api/solutions/conversations/41'                 | ''         || 400
        6   | 'resolve'       | 'PUT'  | '/api/solutions/conversations/41/resolved'        | 'null'     || 400
        7   | 'resolve'       | 'PUT'  | '/api/solutions/conversations/41/resolved'        | 'true'     || 400
        8   | 'editMessage'   | 'PUT'  | '/api/solutions/conversations/41/messages/501'    | '{"title"' || 400
    }

    def "resolve should answer 400 when resolved is not a boolean"() {

        expect:
        mvc.perform(asUser(request('PUT', '/api/solutions/conversations/41/resolved', '{"resolved":"soon"}'))).andExpect(status().isBadRequest())
    }

    def "resolve should close the thread when true and reopen it when false"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/conversations/41/resolved', '{"resolved":true}'))).andExpect(status().isOk())
        mvc.perform(asUser(request('PUT', '/api/solutions/conversations/41/resolved', '{"resolved":false}'))).andExpect(status().isOk())

        then:
        1 * repo.setStatus(41, 'closed', 'tony') >> true
        1 * repo.setStatus(41, 'active', 'tony') >> true
    }

    @Unroll
    def "#scenario should answer 404 when the thing is not there"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        _ * repo.editMessage(*_) >> false
        _ * repo.deleteMessage(*_) >> false
        _ * repo.unlink(*_) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                  | method   | url                                              | body           || expectedMessage
        'list of solution 9'      | 'GET'    | '/api/solutions/9/conversations'                 | null           || 'Solution not found.'
        'messages of thread 99'   | 'GET'    | '/api/solutions/conversations/99/messages'       | null           || 'Conversation not found.'
        'post to thread 0'        | 'POST'   | '/api/solutions/conversations/0/messages'        | '{"body":"x"}' || 'Conversation not found.'
        'editMessage 999'         | 'PUT'    | '/api/solutions/conversations/41/messages/999'   | '{"body":"x"}' || 'Message not found.'
        'deleteMessage 999'       | 'DELETE' | '/api/solutions/conversations/41/messages/999'   | null           || 'Message not found.'
        'unlink thread 99'        | 'DELETE' | '/api/solutions/1/conversations/99'              | null           || 'Conversation not found.'
    }

    def "deleteMessage and unlink should answer ok and the id when they succeed"() {

        expect:
        mvc.perform(asUser(delete('/api/solutions/conversations/41/messages/501'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true)).andExpect(jsonPath('$.messageId').value(501))
        mvc.perform(asUser(delete('/api/solutions/1/conversations/41'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true)).andExpect(jsonPath('$.unlinkedId').value(41))
    }
}
