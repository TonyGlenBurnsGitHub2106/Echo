package com.echo

import java.time.LocalDateTime

import com.echo.ApiClientModels.SolutionLink
import com.echo.SolutionDrafts.ItemDraft
import com.echo.SolutionDrafts.ProjectDraft
import com.echo.SolutionDrafts.SolutionDraft
import com.echo.SolutionModels.ConversationSummary
import com.echo.SolutionModels.Message
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.item
import static com.echo.SolutionFixtures.lookups
import static com.echo.SolutionFixtures.project
import static com.echo.SolutionFixtures.record
import static com.echo.SolutionFixtures.solution

import spock.lang.Unroll

/**
 * Story 5653: the fixes Leonardo's Fix 100 Bugs (Story 5766, 2026-11-13) made to the Solutions code Echo
 * shares -- an agent cannot rename a solution (solutions-2), an inactive owner or assignee is kept by an
 * edit that leaves them alone (solutions-3), the project wake never fails a saved item (solutions-4), a
 * date can be cleared (solutions-5), and a person's reply waits on the agent that last wrote (solutions-8).
 * Plus the client-code rules (solutions-7, fence-2, security-5).
 */
class SolutionCarriedFixesSpec extends EchoSpockBaseSpec {

    private static final LocalDateTime WHEN = getLocalDateTime('10/01/2026 00:00')

    SolutionRepository repo = Mock()
    SolutionItemRepository items = Mock()
    ApiClientService clients = Mock()
    SolutionAccess access = new SolutionAccess(repo, clients)
    SolutionCodeCheck codes = new SolutionCodeCheck(repo)
    SolutionService service = new SolutionService(repo, access, codes)
    SolutionItemService itemService = new SolutionItemService(items, service, access, codes)

    def setup() {
        repo.lookups() >> lookups()
        clients.maySee(7, 4) >> true
        clients.maySee(7, 1) >> true
    }

    // ---- solutions-2: the name and alias are the owner's -----------------------------------------

    @Unroll
    def "save should refuse an API client with 403 and save nothing when it #scenario"() {

        given:
        repo.findSolution(4) >> solution(id: 4, name: 'Claims', alias: 'claims')

        when:
        service.save(AGENT, record(SolutionDraft, [id: 4] + sent))

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only the owner may rename a solution or change its alias. Nothing was saved.'
        0 * repo.saveSolution(*_)

        where:
        scenario               | sent
        'renames it'           | [name: 'Secret plans']
        'changes its alias'    | [alias: 'other']
        'clears its alias'     | [alias: '']
    }

    def "save should answer not found, not 403, when an API client renames a solution it was never given"() {

        given:
        repo.findSolution(9) >> solution(id: 9, name: 'Hidden')

        when:
        service.save(AGENT, record(SolutionDraft, [id: 9, name: 'Anything']))

        then:
        thrown(SolutionNotFoundException)
        0 * repo.saveSolution(*_)
    }

    def "save should let an API client send the current name and alias unchanged"() {

        given:
        repo.findSolution(4) >> solution(id: 4, name: 'Claims', alias: 'claims')

        when:
        service.save(AGENT, record(SolutionDraft, [id: 4, name: ' Claims ', alias: 'CLAIMS', statusCode: 'inprogress']))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.statusCode() == 'inprogress' }, 'rex') >> 4
    }

    def "save should let a person rename a solution and change its alias"() {

        given:
        repo.findSolution(4) >> solution(id: 4, name: 'Claims', alias: 'claims')

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, name: 'Claims II', alias: 'claims-two']))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.name() == 'Claims II' && d.alias() == 'claims-two' }, 'tony') >> 4
    }

    // ---- solutions-3: an inactive owner or assignee is kept ---------------------------------------

    def "save should keep an inactive owner when an update leaves the owner alone"() {

        given:
        repo.findSolution(4) >> solution(id: 4, ownerCode: 'old')

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, ownerCode: 'old', statusSummary: 'Moving']))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.ownerCode() == 'old' && d.statusSummary() == 'Moving' }, 'tony') >> 4
    }

    def "save should still refuse a NEW owner who is inactive"() {

        given:
        repo.findSolution(4) >> solution(id: 4, ownerCode: 'tony')

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, ownerCode: 'old']))

        then:
        thrown(SolutionRuleException)
        0 * repo.saveSolution(*_)
    }

    def "saveProject should keep an inactive project owner when an update leaves the owner alone"() {

        given:
        repo.findSolution(1) >> solution(id: 1)
        repo.findProject(10) >> project(id: 10, solutionId: 1, ownerCode: 'old')

        when:
        service.saveProject(PERSON, new ProjectDraft(10, 1, null, 'New words', null, 'old', 0))

        then:
        1 * repo.saveProject({ ProjectDraft d -> d.ownerCode() == 'old' && d.description() == 'New words' }, 'tony') >> 10
    }

    def "item save should keep an inactive assignee when an update leaves the assignee alone"() {

        given:
        repo.findSolution(1) >> solution(id: 1)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'inprogress')
        items.find(100) >> item(id: 100, assigneeCode: 'old', statusCode: 'inprogress')

        when:
        itemService.save(PERSON, record(ItemDraft, [id: 100, projectId: 10, assigneeCode: 'old', detail: 'More']))

        then:
        1 * items.save({ ItemDraft d -> d.assigneeCode() == 'old' && d.detail() == 'More' }, 'tony') >> 100
    }

    // ---- solutions-4: the project wake never fails a saved item -----------------------------------

    def "item save should wake a not-started project even when its owner has become inactive"() {

        given:
        repo.findSolution(1) >> solution(id: 1)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'todo', ownerCode: 'old')
        items.find(100) >> item(id: 100, statusCode: 'inprogress')

        when:
        itemService.save(PERSON, record(ItemDraft, [id: 100, projectId: 10, statusCode: 'inprogress']))

        then:
        notThrown(Exception)
        1 * items.save(_, 'tony') >> 100
        1 * repo.saveProject({ ProjectDraft d -> d.statusCode() == 'inprogress' && d.ownerCode() == 'old' }, 'tony') >> 10
    }

    def "item save should answer the saved item when the project wake itself fails"() {

        given:
        repo.findSolution(1) >> solution(id: 1)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'todo')
        items.find(100) >> item(id: 100, statusCode: 'inprogress')
        repo.saveProject(*_) >> { throw new IllegalStateException('the database went away') }

        when:
        def saved = itemService.save(PERSON, record(ItemDraft, [id: 100, projectId: 10, statusCode: 'inprogress']))

        then:
        1 * items.save(_, 'tony') >> 100
        saved.id() == 100
    }

    def "wakeProject should leave a project alone when it is past not started"() {

        given:
        repo.findSolution(1) >> solution(id: 1)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'blocked')

        when:
        service.wakeProject(PERSON, 10, 'todo', 'inprogress')

        then:
        0 * repo.saveProject(*_)
    }

    // ---- solutions-5: a date can be cleared ---------------------------------------------------------

    @Unroll
    def "save should #outcome the target date when it is #how"() {

        given:
        repo.findSolution(4) >> solution(id: 4, targetDate: WHEN)

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, targetDate: sent, clearTargetDate: clear]))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.targetDate() == expected }, 'tony') >> 4

        where:
        outcome   | how                    | sent                     | clear || expected
        'clear'   | 'sent blank'           | null                     | true  || null
        'keep'    | 'left out'             | null                     | false || WHEN
        'replace' | 'sent with a new date' | WHEN.plusDays(3)         | false || WHEN.plusDays(3)
    }

    @Unroll
    def "item save should #outcome the due date when it is #how"() {

        given:
        repo.findSolution(1) >> solution(id: 1)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'inprogress')
        items.find(100) >> item(id: 100, dueDate: WHEN, statusCode: 'inprogress')

        when:
        itemService.save(PERSON, record(ItemDraft, [id: 100, projectId: 10, dueDate: null, clearDueDate: clear]))

        then:
        1 * items.save({ ItemDraft d -> d.dueDate() == expected }, 'tony') >> 100

        where:
        outcome | how          | clear || expected
        'clear' | 'sent blank' | true  || null
        'keep'  | 'left out'   | false || WHEN
    }

    @Unroll
    def "isClear should be #expected when the date is sent as #shown"() {

        expect:
        SolutionControllerSupport.isClear(value) == expected

        where:
        shown        | value        || expected
        'blank'      | ''           || true
        'spaces'     | '   '        || true
        'left out'   | null         || false
        'a date'     | '2026-12-01' || false
    }

    def "the old ten-part drafts should keep the date"() {

        expect:
        !new SolutionDraft(1, 'n', 'd', 's', 'o', 'ss', WHEN, true, 2, 'a').clearTargetDate()
        !new ItemDraft(1, 2, null, 't', 'd', 's', 'a', 3, 4, WHEN).clearDueDate()
    }

    // ---- solutions-8: a person's reply waits on the agent that last wrote --------------------------

    def "a person's reply should wait on the agent that last wrote in the thread, not the first one shared"() {

        given:
        SolutionConversationRepository convs = Mock()
        SolutionConversationService conversations = new SolutionConversationService(convs, clients, access)
        List<Message> thread = [new Message(1, 41, 'rex', 'First', null, null), new Message(2, 41, 'sam', 'Me too', null, 'tony')]
        convs.solutionIdsOf(41) >> [1]
        convs.listForSolution(1) >> [new ConversationSummary(41, 'Thread 41', 'active', null, null, 2, 'sam', null, 0)]
        repo.findSolution(1) >> solution(id: 1)
        convs.messagesSince(41, _) >> thread
        clients.clientsForSolution(1) >> [new SolutionLink(7, 1, 'S1', 'rex', 'Rex'), new SolutionLink(8, 1, 'S1', 'sam', 'Sam')]

        when:
        conversations.post(PERSON, 41, 'Here you go')

        then:
        1 * convs.addMessage(41, 'tony', 'Here you go', 'sam') >> { thread << new Message(3, 41, 'tony', 'Here you go', null, 'sam'); 3 }
    }

    // ---- client-code rules ----------------------------------------------------------------------------

    @Unroll
    def "ApiClientService.save should refuse #scenario and save nothing"() {

        given:
        ApiClientRepository clientRepo = Mock()
        ApiClientService real = new ApiClientService(clientRepo)

        when:
        real.save(0, code, 'Agent', prefixes, null, true)

        then:
        Exception ex = thrown()
        thrownType.isInstance(ex)
        ex.message.contains(words)
        0 * clientRepo.save(*_)

        where:
        scenario                            | code                       | prefixes                            || thrownType               | words
        'a house name as the code'          | ' Tony '                   | '/api/solutions/'                   || IllegalArgumentException | "house's own names"
        'a code longer than 20 characters'  | 'a-very-long-client-code1' | '/api/solutions/'                   || SolutionRuleException    | 'at most 20'
        'a path inside the sign-in subtree' | 'rex'                      | '/api/solutions/;/api/auth/tokens/' || SolutionRuleException    | '/api/auth/tokens/'
        'the client administration'         | 'rex'                      | '/API/API-CLIENTS/'                 || SolutionRuleException    | 'for the house only'
        // Story 5653 / Story 5765 (audit fence-5): the logins live under these two
        'the record logins'                 | 'rex'                      | '/api/entities/'                    || SolutionRuleException    | 'for the house only'
        'the web address logins'            | 'rex'                   | '/api/solutions/;/api/web-addresses/' || SolutionRuleException  | '/api/web-addresses/'
    }

    def "ApiClientService.save should accept a 20-character code on an ordinary path"() {

        given:
        ApiClientRepository clientRepo = Mock()
        ApiClientService real = new ApiClientService(clientRepo)

        when:
        clientRepo.list() >> []
        real.save(0, 'abcdefghij0123456789', 'Agent', '/api/solutions/', null, true)

        then:
        1 * clientRepo.save(0, 'abcdefghij0123456789', 'Agent', '/api/solutions/', null, true) >> 5
    }
}
