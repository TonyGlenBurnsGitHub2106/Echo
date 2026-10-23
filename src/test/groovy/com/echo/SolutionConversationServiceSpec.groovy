package com.echo

import com.echo.ApiClientModels.SolutionLink
import com.echo.SolutionModels.ConversationSummary
import com.echo.SolutionModels.Message

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5653: the relay between an outside agent and the house. Who a new message waits on (never the AI,
 * never a person when a test wrote it), who may read which thread, and which actions are the owner's alone.
 */
class SolutionConversationServiceSpec extends SolutionServiceSupport {

    SolutionConversationRepository repo = Mock()
    SolutionConversationService service = new SolutionConversationService(repo, clients, access)

    Map<Integer, List<Integer>> links = [41: [1], 42: [2], 43: [2, 1], 44: []]
    Map<Integer, String> status = [41: 'active', 42: 'active', 43: 'active']
    List<Message> messages = [new Message(501, 41, 'rex', 'Hello', null, 'tony')]
    Map<Integer, List<SolutionLink>> sharedWith = [1: [new SolutionLink(7, 1, 'S1', 'rex', 'Rex')], 2: []]

    def setup() {
        repo.solutionIdsOf(_) >> { int c -> links[c] ?: [] }
        repo.listForSolution(_) >> { int s ->
            links.findAll { it.value.contains(s) }.collect { c, l -> new ConversationSummary(c, 'Thread ' + c, status[c], null, null, 1, 'rex', null, 0) }
        }
        repo.messagesSince(_, _) >> { int c, int since -> messages.findAll { it.conversationId() == c && it.id() > since } }
        repo.addMessage(_, _, _, _) >> { int c, String sender, String body, String awaiting ->
            int id = 500 + messages.size() + 1
            messages << new Message(id, c, sender, body, null, awaiting)
            id
        }
        clients.clientsForSolution(_) >> { int s -> sharedWith[s] ?: [] }
    }

    /** A link list a data table can name: none | noCodes | blankThenRex. */
    private static List<SolutionLink> linksNamed(String name) {
        switch (name) {
            case 'none':         return []
            case 'noCodes':      return [new SolutionLink(5, 1, 'S1', null, 'x'), new SolutionLink(6, 1, 'S1', ' ', 'y')]
            case 'blankThenRex': return [new SolutionLink(5, 1, 'S1', '', 'x'), new SolutionLink(7, 1, 'S1', 'rex', 'Rex')]
            default: throw new IllegalStateException(name)
        }
    }

    // ---- the guards -----------------------------------------------------------------

    @Unroll
    def "#seq. isTestParty should answer #expected when #scenario"() {

        expect:
        SolutionConversationService.isTestParty(code) == expected

        where:
        seq | scenario                         | code           || expected
        1   | 'the code is null'               | null           || false
        2   | 'the code is empty'              | ''             || false
        3   | 'the code is an agent'           | 'rex'          || false
        4   | 'the code is zz without a dash'  | 'zz'           || false
        5   | 'the code is the bare prefix'    | 'zz-'          || true
        6   | 'the code is the test client'    | 'zz-pwtest'    || true
        7   | 'the code is padded upper case'  | ' ZZ-Probe '   || true
        8   | 'the prefix is not at the start' | 'xzz-pwtest'   || false
    }

    @Unroll
    def "#seq. guardAwaiting should answer #expected when #scenario"() {

        expect:
        SolutionConversationService.guardAwaiting(sender, awaiting) == expected

        where:
        seq | scenario                                    | sender            | awaiting   || expected
        1   | 'an agent waits on tony'                    | 'rex'             | 'tony'     || 'tony'
        2   | 'the house waits on an agent'               | 'felix'           | 'rex'      || 'rex'
        3   | 'nobody is waited on'                       | 'rex'             | null       || null
        4   | 'a test waits on tony'                      | 'zz-pwtest'       | 'tony'     || null
        5   | 'a test waits on a padded TONY'             | 'zz-pwtest'       | ' TONY '   || null
        6   | 'a test house actor waits on claude'        | 'zz-pwtest-house' | 'claude'   || null
        7   | 'a test waits on Claude in capitals'        | 'zz-pwtest'       | 'Claude'   || null
        8   | 'a test waits on another test'              | 'zz-pwtest'       | 'zz-other' || 'zz-other'
        9   | 'a test waits on nobody'                    | 'zz-pwtest'       | null       || null
        10  | 'an unknown sender waits on tony'           | null              | 'tony'     || 'tony'
    }

    def "the parties and statuses should hold their values when read"() {

        expect:
        SolutionConversationService.HUMAN_PARTY == 'tony'
        SolutionConversationService.AI_PARTY == 'claude'
        SolutionConversationService.STATUS_RESOLVED == 'closed'
        SolutionConversationService.STATUS_OPEN == 'active'
        SolutionConversationService.TEST_CODE_PREFIX == 'zz-'
    }

    // ---- list and create --------------------------------------------------------------

    def "list should answer the threads of the solution when it is visible"() {

        expect:
        service.list(REX, 1)*.conversationId() == [41, 43]
        service.list(TONY, 2)*.conversationId() == [42, 43]
    }

    @Unroll
    def "list should read nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.list(caller, solutionId)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.listForSolution(_)

        where:
        scenario                         | callerName | solutionId || unused
        'the client was not given it'    | 'REX'      | 2          || null
        'the client was given nothing'   | 'ZED'      | 1          || null
        'the id is zero'                 | 'TONY'     | 0          || null
    }

    def "create should open a thread and post the trimmed first message when one is sent"() {

        when:
        ConversationSummary c = service.create(REX, 1, 'Question', ' First ')

        then:
        1 * repo.create(1, 'Question') >> { links[45] = [1]; status[45] = 'active'; 45 }
        c.conversationId() == 45
        messages.last().conversationId() == 45
        messages.last().body() == 'First'
        messages.last().sender() == 'rex'
        messages.last().awaitingFrom() == 'tony'
    }

    @Unroll
    def "create should post nothing when #scenario"() {

        when:
        service.create(TONY, 1, 'Question', message)

        then:
        1 * repo.create(1, 'Question') >> { links[45] = [1]; 45 }
        0 * repo.addMessage(*_)

        where:
        scenario                  | message || unused
        'no message is sent'      | null    || null
        'an empty message'        | ''      || null
        'a blank message'         | '   '   || null
    }

    def "create should throw not found when the new thread is not listed afterward"() {

        when:
        service.create(TONY, 1, 'Question', null)

        then:
        1 * repo.create(1, 'Question') >> 99
        SolutionNotFoundException ex = thrown()
        ex.message == 'Conversation not found.'
    }

    def "create should create nothing when the solution is hidden"() {

        when:
        service.create(REX, 2, 'Question', 'x')

        then:
        thrown(SolutionNotFoundException)
        0 * repo.create(*_)
    }

    // ---- reading --------------------------------------------------------------------

    @Unroll
    def "messages should answer ids #expected when #scenario"() {

        expect:
        service.messages(TONY, 41, since)*.id() == intsOf(expected)

        where:
        scenario                        | since || expected
        'everything is asked'           | 0     || '501'
        'a negative id is sent'         | -9    || '501'
        'the newest id is sent'         | 501   || ''
    }

    @Unroll
    def "messages should clear the reader's waiting flag when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.messages(caller, 41, 0)

        then:
        expectedClientClears * repo.clearAwaitingFor(41, 'rex')
        expectedPersonClears * repo.clearAwaitingFor(41, 'tony')

        where:
        scenario               | callerName || expectedClientClears | expectedPersonClears
        'a client reads'       | 'REX'      || 1                    | 0
        'a person reads'       | 'TONY'     || 0                    | 1
        'the house reads'      | 'HOUSE'    || 0                    | 0
    }

    def "messages should reach a thread through any linked solution when the caller may see one of them"() {

        expect:
        service.messages(REX, 43, 0) == []
    }

    @Unroll
    def "messages should throw not found and read nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.messages(caller, conversationId, 0)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.messagesSince(*_)
        0 * repo.clearAwaitingFor(*_)

        where:
        scenario                               | callerName | conversationId || expectedMessage
        'the client was not given its solution' | 'REX'     | 42             || 'Conversation not found.'
        'the client was given nothing'         | 'ZED'      | 41             || 'Conversation not found.'
        'the thread is linked to nothing'      | 'TONY'     | 44             || 'Conversation not found.'
        'the thread does not exist'            | 'TONY'     | 99             || 'Conversation not found.'
        'the id is zero'                       | 'TONY'     | 0              || 'Conversation not found.'
        'the id is negative'                   | 'TONY'     | -1             || 'Conversation not found.'
    }

    def "messages should not reach the repository when the conversation id is zero"() {

        when:
        service.messages(TONY, 0, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.solutionIdsOf(_)
    }

    // ---- posting --------------------------------------------------------------------

    def "post should wait on tony and clear nothing when a client posts"() {

        when:
        Message m = service.post(REX, 41, '  Is it done?  ')

        then:
        0 * repo.clearAwaitingFor(*_)
        m.body() == 'Is it done?'
        m.sender() == 'rex'
        m.awaitingFrom() == 'tony'
    }

    @Unroll
    def "post should wait on the first client and mark tony's turn answered when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        Message m = service.post(caller, 41, 'Yes')

        then:
        1 * repo.clearAwaitingFor(41, 'tony')
        m.awaitingFrom() == 'rex'
        m.sender() == expectedSender

        where:
        scenario              | callerName || expectedSender
        'a person replies'    | 'TONY'     || 'tony'
        'the house replies'   | 'HOUSE'    || 'felix'
    }

    @Unroll
    def "post should wait on #expected when the solution is shared with #scenario"() {

        given:
        sharedWith[1] = linksNamed(linksName)

        when:
        Message m = service.post(TONY, 41, 'Yes')

        then:
        m.awaitingFrom() == expected

        where:
        scenario                                  | linksName      || expected
        'no one'                                  | 'none'         || null
        'clients with no code'                    | 'noCodes'      || null
        'a blank code and then rex'               | 'blankThenRex' || 'rex'
    }

    def "post should never wait on a person when a test writes it"() {

        given:
        SolutionCaller probe = new SolutionCaller(SolutionCaller.Kind.CLIENT, 'zz-pwtest', 7)
        SolutionCaller houseProbe = new SolutionCaller(SolutionCaller.Kind.SERVICE, 'zz-pwtest-house', null)

        expect:
        service.post(probe, 41, 'ping').awaitingFrom() == null
        service.post(houseProbe, 41, 'pong').awaitingFrom() == 'rex'
    }

    @PendingFeature(reason = 'BUG: SolutionConversationService.post marks a house post as awaiting the first client of the solution with no check on the code -- an API client whose code is "claude" (or "tony") would make a house message wait on the AI, which the repository contract says must never happen')
    def "post should never wait on claude when a client is named claude"() {

        given:
        sharedWith[1] = [new SolutionLink(9, 1, 'S1', 'claude', 'Impostor')]

        expect:
        service.post(TONY, 41, 'hello').awaitingFrom() != 'claude'
    }

    def "post should reopen the thread when it was resolved"() {

        given:
        status[41] = 'closed'

        when:
        service.post(REX, 41, 'One more thing')

        then:
        1 * repo.setStatus(41, 'active', 'rex')
    }

    def "post should leave the status alone when the thread is open"() {

        when:
        service.post(REX, 41, 'x')

        then:
        0 * repo.setStatus(*_)
    }

    @Unroll
    def "post should add nothing and throw #expectedException when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.post(caller, conversationId, body)

        then:
        RuntimeException ex = thrown()
        ex.getClass().simpleName == expectedException
        0 * repo.addMessage(*_)

        where:
        scenario                         | callerName | conversationId | body  || expectedException
        'the body is null'               | 'TONY'     | 41             | null  || 'IllegalArgumentException'
        'the body is empty'              | 'TONY'     | 41             | ''    || 'IllegalArgumentException'
        'the body is whitespace'         | 'TONY'     | 41             | ' \t' || 'IllegalArgumentException'
        'the thread is hidden'           | 'REX'      | 42             | ''    || 'SolutionNotFoundException'
    }

    def "post should throw not found when the repository cannot read the message back"() {

        when:
        service.post(TONY, 41, 'x')

        then:
        1 * repo.addMessage(*_) >> 999
        SolutionNotFoundException ex = thrown()
        ex.message == 'Message not found.'
    }

    // ---- the owner's actions -----------------------------------------------------------

    def "rename should trim the title and answer the thread when a person renames it"() {

        when:
        ConversationSummary c = service.rename(TONY, 41, '  New name ')

        then:
        1 * repo.rename(41, 'New name') >> true
        c.conversationId() == 41
    }

    def "rename should refuse a client that sees the thread and hide one it cannot see"() {

        when:
        service.rename(REX, 41, 'x')

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may rename a conversation.'
        0 * repo.rename(*_)

        when:
        service.rename(REX, 42, 'x')

        then:
        thrown(SolutionNotFoundException)
        0 * repo.rename(*_)
    }

    @Unroll
    def "rename should refuse the title when #scenario"() {

        when:
        service.rename(TONY, 41, title)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.rename(*_)

        where:
        scenario               | title || expectedMessage
        'the title is null'    | null  || 'A conversation needs a title.'
        'the title is empty'   | ''    || 'A conversation needs a title.'
        'the title is blank'   | '  '  || 'A conversation needs a title.'
    }

    def "rename should throw not found when the repository renamed nothing"() {

        when:
        service.rename(HOUSE, 41, 'x')

        then:
        1 * repo.rename(41, 'x') >> false
        thrown(SolutionNotFoundException)
    }

    @Unroll
    def "resolve should set #expectedStatus attributed to #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        ConversationSummary c = service.resolve(caller, 41, resolved)

        then:
        1 * repo.setStatus(41, expectedStatus, expectedCode) >> true
        c.conversationId() == 41

        where:
        scenario                 | callerName | resolved || expectedStatus | expectedCode
        'a person resolves'      | 'TONY'     | true     || 'closed'       | 'tony'
        'the house reopens'      | 'HOUSE'    | false    || 'active'       | 'felix'
    }

    def "resolve should refuse a client and throw not found when nothing changed"() {

        when:
        service.resolve(REX, 41, true)

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may resolve or reopen a conversation.'
        0 * repo.setStatus(*_)

        when:
        service.resolve(TONY, 41, true)

        then:
        1 * repo.setStatus(*_) >> false
        thrown(SolutionNotFoundException)
    }

    def "editMessage should trim the text and answer the message when a person edits it"() {

        when:
        Message m = service.editMessage(TONY, 41, 501, ' Fixed ')

        then:
        1 * repo.editMessage(41, 501, 'Fixed') >> true
        m.id() == 501
    }

    def "editMessage should refuse a client, a blank text, a failed change and an unreadable message"() {

        when:
        service.editMessage(REX, 41, 501, 'x')

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may change a message.'

        when:
        service.editMessage(TONY, 41, 501, ' ')

        then:
        thrown(IllegalArgumentException)
        0 * repo.editMessage(*_)

        when:
        service.editMessage(TONY, 41, 501, 'x')

        then:
        1 * repo.editMessage(41, 501, 'x') >> false
        SolutionNotFoundException nf = thrown()
        nf.message == 'Message not found.'

        when:
        service.editMessage(TONY, 41, 777, 'x')

        then:
        1 * repo.editMessage(41, 777, 'x') >> true
        thrown(SolutionNotFoundException)
    }

    def "deleteMessage should remove, refuse a client and throw not found when nothing was removed"() {

        when:
        service.deleteMessage(TONY, 41, 501)

        then:
        1 * repo.deleteMessage(41, 501) >> true

        when:
        service.deleteMessage(REX, 41, 501)

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may remove a message.'
        0 * repo.deleteMessage(*_)

        when:
        service.deleteMessage(TONY, 41, 502)

        then:
        1 * repo.deleteMessage(41, 502) >> false
        thrown(SolutionNotFoundException)
    }

    def "unlink should remove the link and throw not found when there was none or the solution is hidden"() {

        when:
        service.unlink(TONY, 1, 41)

        then:
        1 * repo.unlink(1, 41) >> true

        when:
        service.unlink(TONY, 1, 42)

        then:
        1 * repo.unlink(1, 42) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Conversation not found.'

        when:
        service.unlink(REX, 2, 42)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.unlink(*_)
    }

    @PendingFeature(reason = 'BUG: SolutionConversationService.unlink checks only that the solution is visible -- an API client can unlink a thread, although the controller documents DELETE /{id}/conversations/{convId} as "unlink (a person only)" and every other undo on a thread is the owner\'s')
    def "unlink should refuse a client when an agent tries to unlink"() {

        when:
        service.unlink(REX, 1, 41)

        then:
        thrown(SolutionForbiddenException)
        0 * repo.unlink(*_)
    }
}
