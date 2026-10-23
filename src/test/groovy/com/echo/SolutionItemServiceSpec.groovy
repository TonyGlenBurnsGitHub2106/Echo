package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDrafts.ItemDraft
import com.echo.SolutionDrafts.ProjectDraft
import com.echo.SolutionModels.Item
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.HOUSE
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.caller
import static com.echo.SolutionFixtures.item
import static com.echo.SolutionFixtures.lookups
import static com.echo.SolutionFixtures.project
import static com.echo.SolutionFixtures.record
import static com.echo.SolutionFixtures.solution

import spock.lang.PendingFeature
import spock.lang.Unroll

// Story 5653: SolutionItemService -- items reached through project and solution, the merge on update, and the
// "work on a sub-item wakes everything above it" rule. Real SolutionService / SolutionAccess / SolutionCodeCheck.
class SolutionItemServiceSpec extends EchoSpockBaseSpec {

    private static final LocalDateTime DUE = getLocalDateTime('10/15/2026 00:00')

    SolutionRepository repo = Mock()
    SolutionItemRepository items = Mock()
    ApiClientService clients = Mock()
    SolutionAccess access = new SolutionAccess(repo, clients)
    SolutionCodeCheck codes = new SolutionCodeCheck(repo)
    SolutionService solutions = new SolutionService(repo, access, codes)
    SolutionItemService service = new SolutionItemService(items, solutions, access, codes)

    def setup() {
        repo.lookups() >> lookups()
        repo.findSolution(1) >> solution(id: 1)
        repo.findSolution(2) >> solution(id: 2)
        repo.findProject(10) >> project(id: 10, solutionId: 1, statusCode: 'inprogress')
        repo.findProject(20) >> project(id: 20, solutionId: 2, statusCode: 'inprogress')
        repo.findProject(11) >> project(id: 11, solutionId: 1, statusCode: 'inprogress')
        clients.maySee(7, 1) >> true
    }

    // ---- list / get ---------------------------------------------------------------------

    @Unroll
    def "list should read every item of a visible solution when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        List<Item> out = service.list(who, 1, projectId)

        then:
        1 * items.listForSolution(1, projectId) >> []
        out.size() == expectedCount

        where:
        scenario                         | callerName | projectId || expectedCount
        "a person asks for every item"   | "person"   | null      || 0
        "the agent narrows to a project" | "agent"    | 10        || 0
    }

    def "list should answer not found when the agent was not given the solution"() {

        when:
        service.list(AGENT, 2, null)

        then:
        thrown(SolutionNotFoundException)
        0 * items.listForSolution(*_)
    }

    @Unroll
    def "get should answer #expectedOutcome when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        items.find(100) >> item(id: 100, solutionId: 1)
        items.find(200) >> item(id: 200, solutionId: 2)

        when:
        String outcome
        try {
            outcome = 'item ' + service.get(who, id).id()
        } catch (SolutionNotFoundException ex) {
            outcome = ex.message
        }

        then:
        outcome == expectedOutcome

        where:
        scenario                                  | callerName | id  || expectedOutcome
        "the agent reads an item it may see"      | "agent"    | 100 || "item 100"
        "the id is zero"                          | "person"   | 0   || "Item not found."
        "the item does not exist"                 | "person"   | 101 || "Item not found."
        "the agent reads another solution's item" | "agent"    | 200 || "Solution not found."
    }

    def "get should not look up id 0 when asked for it"() {

        when:
        service.get(PERSON, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * items.find(0)
    }

    // ---- create -------------------------------------------------------------------------

    def "save should check the project, save with the author and read back when the item is new"() {

        given:
        ItemDraft d = draft([statusCode: 'todo', assigneeCode: 'felix', priority: 2, dueDate: DUE])

        when:
        Item out = service.save(PERSON, d)

        then:
        1 * items.save(d, 'tony') >> 100
        1 * items.find(100) >> item(id: 100, statusCode: 'todo')
        out.id() == 100
        0 * items.setStatus(*_)
    }

    @Unroll
    def "save should answer #expectedMessage and save nothing when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        service.save(who, draft([projectId: projectId]))

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * items.save(*_)

        where:
        scenario                                         | callerName | projectId || expectedMessage
        "the project does not exist"                     | "person"   | 99        || "Project not found."
        "the project is in a solution not given to it"   | "agent"    | 20        || "Solution not found."
    }

    @Unroll
    def "save should refuse before saving when #scenario"() {

        when:
        service.save(PERSON, draft([statusCode: statusCode, assigneeCode: assigneeCode]))

        then:
        SolutionRuleException ex = thrown()
        ex.message.startsWith(expectedStart)
        0 * items.save(*_)

        where:
        scenario                    | statusCode | assigneeCode || expectedStart
        "the status is unknown"     | "finished" | null         || "There is no status code 'finished'."
        "the assignee is unknown"   | null       | "bob"        || "There is no member code 'bob'."
    }

    def "save should let a client assign an item to itself when it is its very first write"() {

        when:
        service.save(AGENT, draft([assigneeCode: 'rex']))

        then:
        1 * repo.saveMember(0, 'rex', 'Rex', true, true, 3)
        1 * items.save(_, 'rex') >> 100
        1 * items.find(100) >> item()
    }

    def "save should answer not found when storage returns no id"() {

        when:
        service.save(PERSON, draft([:]))

        then:
        1 * items.save(*_) >> 0
        thrown(SolutionNotFoundException)
    }

    // ---- update (a merge) ----------------------------------------------------------------

    def "save should keep every field not sent and never change project when updating"() {

        given:
        items.find(100) >> item(id: 100, projectId: 10, solutionId: 1, parentItemId: 99, title: 'Old', detail: 'D', statusCode: 'blocked',
            assigneeCode: 'felix', priority: 2, sortOrder: 4, dueDate: DUE)

        when:
        service.save(PERSON, record(ItemDraft, [id: 100, projectId: 0]))

        then:
        1 * items.save(new ItemDraft(100, 10, 99, 'Old', 'D', 'blocked', 'felix', 2, 0, DUE), 'tony') >> 100
    }

    @Unroll
    def "save should merge the update when #scenario"() {

        given:
        items.find(100) >> item(id: 100, title: 'Old', detail: 'D', statusCode: 'blocked', assigneeCode: 'felix', priority: 2)
        LocalDateTime due = hasDue ? DUE : null
        ItemDraft sent = new ItemDraft(100, 0, parentItemId, title, detail, statusCode, assigneeCode, priority, sortOrder, due)
        ItemDraft expected = new ItemDraft(100, 10, parentItemId, expectedTitle, expectedDetail, expectedStatus, expectedAssignee,
            expectedPriority, sortOrder, due)

        when:
        service.save(PERSON, sent)

        then:
        1 * items.save(expected, 'tony') >> 100

        where:
        scenario                 | parentItemId | title | detail | statusCode | assigneeCode | priority | sortOrder | hasDue || expectedTitle | expectedDetail | expectedStatus | expectedAssignee | expectedPriority
        "blanks are sent"        | 5            | " "   | ""     | ""         | ""           | 0        | 7         | true   || "Old"         | ""             | "blocked"      | ""               | 2
        "every field is sent"    | null         | "New" | "ND"   | "todo"     | "tony"       | 5        | 0         | false  || "New"         | "ND"           | "todo"         | "tony"           | 5
    }

    @Unroll
    def "save should let the agent update only inside its solution when #scenario"() {

        given:
        items.find(100) >> item(id: 100, solutionId: 1)
        items.find(200) >> item(id: 200, projectId: 20, solutionId: 2)

        when:
        boolean saved
        try {
            service.save(AGENT, record(ItemDraft, [id: id, title: 'x']))
            saved = true
        } catch (SolutionNotFoundException ignored) {
            saved = false
        }

        then:
        (expectedSaved ? 1 : 0) * items.save(_, 'rex') >> 100
        saved == expectedSaved

        where:
        scenario                        | id  || expectedSaved
        "the item is in its solution"   | 100 || true
        "the item is in another one"    | 200 || false
    }

    @PendingFeature(reason = 'BUG: SolutionItemService.save validates the KEPT assignee code on an update -- an item assigned to a member who was later made inactive can no longer be edited (409), even when the edit does not touch the assignee')
    def "save should still save when the title of an item assigned to an inactive member is edited"() {

        given:
        items.find(100) >> item(id: 100, assigneeCode: 'old')

        when:
        service.save(PERSON, record(ItemDraft, [id: 100, title: 'Renamed']))

        then:
        1 * items.save(_, 'tony') >> 100
        noExceptionThrown()
    }

    // ---- waking the ancestors -----------------------------------------------------------

    def "save should move every todo ancestor and a todo project to inprogress, leaving others alone, when a sub-item starts"() {

        given:
        repo.findProject(12) >> project(id: 12, solutionId: 1, statusCode: 'todo', name: 'P12', ownerCode: 'tony', sortOrder: 2)
        items.find(103) >> item(id: 103, projectId: 12, parentItemId: 102, statusCode: 'inprogress')
        items.find(102) >> item(id: 102, projectId: 12, parentItemId: 101, statusCode: 'todo')
        items.find(101) >> item(id: 101, projectId: 12, parentItemId: 100, statusCode: 'blocked')
        items.find(100) >> item(id: 100, projectId: 12, parentItemId: null, statusCode: 'todo')

        when:
        service.save(HOUSE, draft([projectId: 12, parentItemId: 102, statusCode: 'inprogress']))

        then:
        1 * items.save(_, 'felix') >> 103
        1 * items.setStatus(102, 'inprogress', 'felix') >> true
        1 * items.setStatus(100, 'inprogress', 'felix') >> true
        0 * items.setStatus(101, *_)
        1 * repo.saveProject(new ProjectDraft(12, 1, 'P12', null, 'inprogress', 'tony', 2), 'felix') >> 12
    }

    @Unroll
    def "save should wake nothing when #scenario"() {

        when:
        service.save(PERSON, draft([parentItemId: 50]))

        then:
        1 * items.save(*_) >> 103
        1 * items.find(103) >> item(id: 103, parentItemId: 50, statusCode: savedStatus)
        (expectedWoken ? 1 : 0) * items.setStatus(*_)
        (expectedWoken ? 1 : 0) * repo.saveProject(*_)

        where:
        scenario                        | savedStatus || expectedWoken
        "the saved item is still todo"  | "todo"      || false
        "the saved item has no status"  | null        || false
    }

    def "save should not touch a project when the project is already past todo"() {

        when:
        service.save(PERSON, draft([statusCode: 'done']))

        then:
        1 * items.save(*_) >> 103
        1 * items.find(103) >> item(id: 103, projectId: 10, statusCode: 'done')
        0 * repo.saveProject(*_)
    }

    def "save should stop the walk quietly when an ancestor is missing"() {

        when:
        service.save(PERSON, draft([statusCode: 'done', parentItemId: 77]))

        then:
        1 * items.save(*_) >> 103
        1 * items.find(103) >> item(id: 103, parentItemId: 77, statusCode: 'done')
        1 * items.find(77) >> null
        0 * items.setStatus(*_)
    }

    def "save should not loop forever when the parent chain has a cycle"() {

        given:
        items.find(103) >> item(id: 103, parentItemId: 104, statusCode: 'done')
        items.find(104) >> item(id: 104, parentItemId: 105, statusCode: 'done')
        items.find(105) >> item(id: 105, parentItemId: 104, statusCode: 'done')

        when:
        service.save(PERSON, draft([statusCode: 'done']))

        then:
        1 * items.save(*_) >> 103
        noExceptionThrown()
    }

    @PendingFeature(reason = 'BUG: SolutionItemService.wakeAncestors re-saves a "todo" project through SolutionService.saveProject, which re-validates the project owner; when that owner is inactive the call answers 409 "Nothing was saved." AFTER the item was already saved')
    def "save should never report failure after the item was saved when the project owner is inactive"() {

        given:
        repo.findProject(12) >> project(id: 12, solutionId: 1, statusCode: 'todo', ownerCode: 'old')

        when:
        Item out = service.save(PERSON, draft([projectId: 12, statusCode: 'done']))

        then:
        1 * items.save(*_) >> 103
        items.find(103) >> item(id: 103, projectId: 12, statusCode: 'done')
        noExceptionThrown()
        out.id() == 103
    }

    // ---- set status ---------------------------------------------------------------------

    def "setStatus should check, save, read back and wake the ancestors when the code is valid"() {

        given:
        items.find(100) >>> [item(id: 100), item(id: 100, statusCode: 'done', parentItemId: 99)]
        items.find(99) >> item(id: 99, statusCode: 'todo')

        when:
        Item out = service.setStatus(AGENT, 100, 'done')

        then:
        1 * items.setStatus(100, 'done', 'rex') >> true
        1 * items.setStatus(99, 'inprogress', 'rex') >> true
        out.statusCode() == 'done'
    }

    @Unroll
    def "setStatus should refuse with #expectedError and save nothing when #scenario"() {

        given:
        items.find(100) >> item(id: 100)

        when:
        service.setStatus(PERSON, 100, code)

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * items.setStatus(*_)

        where:
        scenario               | code       || expectedError
        "the code is null"     | null       || "IllegalArgumentException"
        "the code is blank"    | "  "       || "IllegalArgumentException"
        "the code is unknown"  | "finished" || "SolutionRuleException"
    }

    def "setStatus should answer not found when storage refuses or the item is hidden"() {

        given:
        items.find(100) >> item(id: 100)
        items.find(200) >> item(id: 200, solutionId: 2)

        when:
        service.setStatus(PERSON, 100, 'done')

        then:
        1 * items.setStatus(100, 'done', 'tony') >> false
        thrown(SolutionNotFoundException)

        when:
        service.setStatus(AGENT, 200, 'done')

        then:
        thrown(SolutionNotFoundException)
        0 * items.setStatus(*_)
    }

    // ---- move ---------------------------------------------------------------------------

    @Unroll
    def "move should move the item when #scenario"() {

        given:
        items.find(100) >> item(id: 100, solutionId: 1)

        when:
        Item out = service.move(PERSON, 100, projectId, parentItemId, sortOrder)

        then:
        1 * items.move(100, projectId, parentItemId, sortOrder, 'tony') >> true
        (out != null) == expectedFound

        where:
        scenario                               | projectId | parentItemId | sortOrder || expectedFound
        "the target is in the same solution"   | 11        | 5            | 2         || true
        "no project is named"                  | null      | null         | 0         || true
        "zero means same project and top"      | 0         | 0            | 0         || true
    }

    def "move should refuse with a rule when the target project is in another solution"() {

        given:
        items.find(100) >> item(id: 100, solutionId: 1)

        when:
        service.move(PERSON, 100, 20, null, 0)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'An item can only move between projects of the same solution.'
        0 * items.move(*_)
    }

    @Unroll
    def "move should answer not found when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        items.find(100) >> item(id: 100, solutionId: 1)

        when:
        service.move(who, 100, projectId, null, 0)

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * items.move(*_)

        where:
        scenario                                            | callerName | projectId || expectedError
        "the agent targets a solution it was not given"     | "agent"    | 20        || "SolutionNotFoundException"
        "the target project does not exist"                 | "person"   | 99        || "SolutionNotFoundException"
    }

    def "move should answer not found when storage refuses the move"() {

        given:
        items.find(100) >> item(id: 100, solutionId: 1)

        when:
        service.move(PERSON, 100, null, null, 0)

        then:
        1 * items.move(*_) >> false
        thrown(SolutionNotFoundException)
    }

    // ---- delete -------------------------------------------------------------------------

    @Unroll
    def "delete should answer how many items went when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        items.find(100) >> item(id: 100, solutionId: 1)

        when:
        int n = service.delete(who, 100)

        then:
        1 * items.delete(100) >> 4
        n == expectedRemoved

        where:
        scenario                                 | callerName || expectedRemoved
        "a person deletes"                       | "person"   || 4
        "the agent deletes inside its solution"  | "agent"    || 4
    }

    def "delete should answer not found when the item is hidden or missing"() {

        given:
        items.find(200) >> item(id: 200, solutionId: 2)

        when:
        service.delete(AGENT, 200)

        then:
        thrown(SolutionNotFoundException)

        when:
        service.delete(PERSON, 300)

        then:
        thrown(SolutionNotFoundException)
        0 * items.delete(_)
    }

    private static ItemDraft draft(Map<String, Object> m) {
        record(ItemDraft, [id: 0, projectId: 10, title: 'T'] + m)
    }
}
