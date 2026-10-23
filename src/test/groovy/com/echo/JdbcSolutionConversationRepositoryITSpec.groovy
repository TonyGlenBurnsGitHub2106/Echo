package com.echo

import org.springframework.dao.DataAccessException

import com.echo.SolutionModels.ConversationSummary
import com.echo.SolutionModels.Message

import spock.lang.Unroll

// Story 5653: JdbcSolutionConversationRepository against a real EchoTest_ database (stp_SolutionConversation_*,
// stp_ConversationMessage_*, stp_Conversation_SetStatus).
class JdbcSolutionConversationRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionConversationRepository repo
    int sid

    def setup() {
        repo = new JdbcSolutionConversationRepository(dataSource())
        sid = newSolution()
    }

    def "create should make an agent conversation linked to the solution that the list reads back when a title is given"() {

        when:
        int id = repo.create(sid, '  Deploy questions  ')
        List<ConversationSummary> c = repo.listForSolution(sid)

        then:
        id > 0
        c.size() == 1
        c[0].conversationId() == id
        c[0].title() == 'Deploy questions'
        c[0].status() != null
        c[0].createdDate() != null
        c[0].modifiedDate() != null
        c[0].messageCount() == 0
        c[0].lastSender() == null
        c[0].awaitingFrom() == null
        c[0].awaitingTonyCount() == 0
        scalar('SELECT ConversationType FROM dbo.Conversation WHERE ConversationID = ?', id) == 'agent'
        repo.solutionIdsOf(id) == [sid]
    }

    @Unroll
    def "create should refuse and write nothing when #scenario"() {

        given:
        int before = count('SELECT COUNT(*) FROM dbo.Conversation')
        int solution = knownSolution ? sid : -1

        when:
        repo.create(solution, title)

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)
        count('SELECT COUNT(*) FROM dbo.Conversation') == before

        where:
        scenario                         | knownSolution | title || message
        "the title is blank"             | true          | "  "  || "needs a title"
        "the solution does not exist"    | false         | "T"   || "solution no longer exists"
    }

    def "addMessage should add messages in order and messagesSince should return only newer ones when several are added"() {

        given:
        int c = repo.create(sid, 'T')

        when:
        int m1 = repo.addMessage(c, 'rex', 'Hello', 'Tony ')
        int m2 = repo.addMessage(c, 'tony', 'Hi back', null)
        int m3 = repo.addMessage(c, 'rex', 'One more', '')
        List<Message> all = repo.messagesSince(c, 0)

        then:
        all*.id() == [m1, m2, m3]
        all[0].conversationId() == c
        all[0].sender() == 'rex'
        all[0].body() == 'Hello'
        all[0].awaitingFrom() == 'tony'
        all[0].createdDate() != null
        all[1].awaitingFrom() == null
        all[2].awaitingFrom() == null
        repo.messagesSince(c, m1)*.id() == [m2, m3]
        repo.messagesSince(c, m3).isEmpty()
    }

    @Unroll
    def "addMessage should refuse and write nothing when the awaited party is claude written as #scenario"() {

        given:
        int c = repo.create(sid, 'T')

        when:
        repo.addMessage(c, 'rex', 'wake up', party)

        then:
        thrown(IllegalArgumentException)
        repo.messagesSince(c, 0).isEmpty()

        where:
        scenario          | party          || _
        "lower case"      | "claude"       || _
        "upper case"      | "CLAUDE"       || _
        "padded"          | "  Claude  "   || _
    }

    def "addMessage should fail with a database error when the conversation does not exist"() {

        when:
        repo.addMessage(987654321, 'rex', 'x', null)

        then:
        thrown(DataAccessException)
    }

    def "listForSolution should count messages and name the last sender and awaited party, newest first, when threads differ"() {

        given:
        int older = repo.create(sid, 'Older')
        int newer = repo.create(sid, 'Newer')
        repo.addMessage(newer, 'rex', 'a', 'tony')
        repo.addMessage(newer, 'rex', 'b', 'tony')
        repo.addMessage(newer, 'tony', 'c', null)
        sql.update('UPDATE dbo.Conversation SET ModifiedDate = DATEADD(DAY, -1, GETDATE()) WHERE ConversationID = ?', older)

        when:
        List<ConversationSummary> list = repo.listForSolution(sid)
        ConversationSummary n = list.find { it.conversationId() == newer }

        then:
        list*.conversationId() == [newer, older]
        n.messageCount() == 3
        n.lastSender() == 'tony'
        n.awaitingFrom() == 'tony'
        n.awaitingTonyCount() == 2
    }

    def "clearAwaitingFor should clear one party's flags and never touch claude or a blank party"() {

        given:
        int c = repo.create(sid, 'T')
        repo.addMessage(c, 'rex', 'a', 'tony')
        repo.addMessage(c, 'tony', 'b', 'rex')
        sql.update("UPDATE dbo.ConversationMessage SET AwaitingResponseFrom = 'claude' WHERE ConversationID = ? AND Sender = 'tony'", c)

        when:
        repo.clearAwaitingFor(c, ' TONY ')
        repo.clearAwaitingFor(c, 'claude')
        repo.clearAwaitingFor(c, '')

        then:
        repo.messagesSince(c, 0)*.awaitingFrom() == [null, 'claude']
    }

    def "rename should change the title, refuse a blank one and answer false when the conversation is unknown"() {

        given:
        int c = repo.create(sid, 'Old')

        expect:
        repo.rename(c, ' New ')
        repo.listForSolution(sid)[0].title() == 'New'
        !repo.rename(987654321, 'x')
        ruleOf { repo.rename(c, ' ') }.message.contains('needs a title')
    }

    def "editMessage should change the body only when the message belongs to that conversation"() {

        given:
        int c1 = repo.create(sid, 'One')
        int c2 = repo.create(sid, 'Two')
        int m1 = repo.addMessage(c1, 'rex', 'original', null)
        int m2 = repo.addMessage(c2, 'rex', 'other thread', null)

        expect:
        repo.editMessage(c1, m1, 'edited')
        repo.messagesSince(c1, 0)[0].body() == 'edited'
        !repo.editMessage(c1, m2, 'hijack')
        repo.messagesSince(c2, 0)[0].body() == 'other thread'
        !repo.editMessage(c1, 987654321, 'x')
        ruleOf { repo.editMessage(c1, m1, '   ') }.message.contains('needs some text')
    }

    def "deleteMessage should remove a message only when it belongs to that conversation"() {

        given:
        int c1 = repo.create(sid, 'One')
        int c2 = repo.create(sid, 'Two')
        int m1 = repo.addMessage(c1, 'rex', 'a', null)
        int m2 = repo.addMessage(c2, 'rex', 'b', null)

        expect:
        !repo.deleteMessage(c1, m2)
        repo.messagesSince(c2, 0)*.id() == [m2]
        repo.deleteMessage(c1, m1)
        repo.messagesSince(c1, 0).isEmpty()
        !repo.deleteMessage(c1, m1)
    }

    def "setStatus should stamp the close and clear awaiting flags when closed and clear the close when reopened"() {

        given:
        int c = repo.create(sid, 'T')
        repo.addMessage(c, 'rex', 'a', 'tony')

        when:
        repo.setStatus(c, 'closed', 'tony')
        Map<String, Object> r = row('SELECT Status, ClosedDate, ClosedBy FROM dbo.Conversation WHERE ConversationID = ?', c)

        then:
        r.Status == 'closed'
        r.ClosedDate != null
        r.ClosedBy == 'tony'
        repo.messagesSince(c, 0)[0].awaitingFrom() == null

        when:
        repo.setStatus(c, 'open', 'tony')
        r = row('SELECT Status, ClosedDate, ClosedBy FROM dbo.Conversation WHERE ConversationID = ?', c)

        then:
        r.Status == 'open'
        r.ClosedDate == null
        r.ClosedBy == null
    }

    def "unlink should remove the link, keep the conversation and its messages and audit it when the link exists"() {

        given:
        int c = repo.create(sid, 'T')
        repo.addMessage(c, 'rex', 'kept', null)
        int linkId = count('SELECT SolutionConversationID FROM dbo.SolutionConversation WHERE ConversationID = ?', c)

        expect:
        !repo.unlink(newSolution(), c)
        repo.unlink(sid, c)
        repo.listForSolution(sid).isEmpty()
        repo.solutionIdsOf(c).isEmpty()
        repo.messagesSince(c, 0)*.body() == ['kept']
        logRows('SolutionConversation', 'SolutionConversationID', linkId).last().DeletedDate != null
        !repo.unlink(sid, c)
    }

    def "solutionIdsOf should return an empty list when the conversation is unknown"() {

        expect:
        repo.solutionIdsOf(987654321).isEmpty()
    }
}
