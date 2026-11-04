package com.echo

import com.echo.SolutionDependencyModels.StoredWait
import com.echo.SolutionDependencyModels.Wait
import com.echo.SolutionDependencyModels.WaitCount
import com.echo.SolutionModels.Item
import static com.echo.SolutionFixtures.item

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): the business rules of what items wait on -- every item is reached through the item
 * service (so through its own solution) before anything is read or written, a wait joins two items of ONE
 * solution (refused with the rule's own sentence before any save), the repository's refusals (itself, a
 * repeat, a circle) pass through, a wait is removed only through its own solution, and the solution's wait
 * counts are read only when the solution is visible.
 */
class SolutionDependencyServiceSpec extends SolutionServiceSupport {

    static final String TWO_SOLUTIONS = 'An item can only wait on an item of the same solution. Nothing was saved.'

    SolutionDependencyRepository repo = Mock()
    SolutionItemService items = Mock()
    SolutionDependencyService service = new SolutionDependencyService(repo, items, access)

    Map<Integer, Item> itemRows = [100: item(id: 100, solutionId: 1), 101: item(id: 101, solutionId: 1, title: 'Deploy it'),
                                   200: item(id: 200, solutionId: 2), 201: item(id: 201, solutionId: 2)]
    Map<Integer, StoredWait> waitRows = [7: new StoredWait(7, 101, 100, 1), 8: new StoredWait(8, 201, 200, 2)]

    def setup() {
        items.get(_, _) >> { SolutionCaller c, int id -> visibleItem(access, itemRows, c, id) }
        repo.find(_) >> { int id -> waitRows[id] }
    }

    /** What SolutionItemService.get answers: the item, reached through its own solution. */
    private static Item visibleItem(SolutionAccess a, Map<Integer, Item> rows, SolutionCaller caller, int id) {
        Item i = id <= 0 ? null : rows[id]
        if (i == null) {
            throw new SolutionNotFoundException('Item')
        }
        a.requireSolution(caller, i.solutionId())
        return i
    }

    private static Wait waitOn(int id, int otherItemId) {
        new Wait(id, 'waitsOn', otherItemId, 'Item ' + otherItemId, 'Deploy', 'todo', 'Not started', '#6B7280', false, null)
    }

    /** Runs one named call -- the calls a data table can name. */
    private static Object call(SolutionDependencyService s, String action, SolutionCaller caller, int id, int otherId) {
        switch (action) {
            case 'forItem':     return s.forItem(caller, id)
            case 'add':         return s.add(caller, id, otherId)
            case 'remove':      s.remove(caller, id); return null
            case 'forSolution': return s.forSolution(caller, id)
            default: throw new IllegalStateException(action)
        }
    }

    // ---- access first ----

    @Unroll
    def "#seq. #action should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        call(service, action, caller, id, otherId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.save(*_)
        0 * repo.delete(_)
        0 * repo.forItem(_)
        0 * repo.forSolution(_)

        where:
        seq | scenario                                         | action        | callerName | id  | otherId || expectedMessage
        1   | 'the client was given nothing'                   | 'forItem'     | 'ZED'      | 100 | 0       || 'Solution not found.'
        2   | 'the client was not given its solution'          | 'forItem'     | 'REX'      | 200 | 0       || 'Solution not found.'
        3   | 'the item does not exist'                        | 'forItem'     | 'TONY'     | 99  | 0       || 'Item not found.'
        4   | 'the waiting item is hidden from the client'     | 'add'         | 'REX'      | 200 | 201     || 'Solution not found.'
        5   | 'the item waited on is hidden from the client'   | 'add'         | 'REX'      | 101 | 200     || 'Solution not found.'
        6   | 'the waiting item does not exist'                | 'add'         | 'TONY'     | 99  | 100     || 'Item not found.'
        7   | 'the item waited on does not exist'              | 'add'         | 'TONY'     | 101 | 99      || 'Item not found.'
        8   | 'the item waited on has id zero'                 | 'add'         | 'HOUSE'    | 101 | 0       || 'Item not found.'
        9   | 'the wait is of a solution the client was not given' | 'remove'  | 'REX'      | 8   | 0       || 'Solution not found.'
        10  | 'the client was given nothing'                   | 'remove'      | 'ZED'      | 7   | 0       || 'Solution not found.'
        11  | 'the wait does not exist'                        | 'remove'      | 'TONY'     | 99  | 0       || 'Wait not found.'
        12  | 'the client was given nothing'                   | 'forSolution' | 'ZED'      | 1   | 0       || 'Solution not found.'
        13  | 'the client was not given the solution'          | 'forSolution' | 'REX'      | 2   | 0       || 'Solution not found.'
        14  | 'the solution does not exist'                    | 'forSolution' | 'TONY'     | 9   | 0       || 'Solution not found.'
    }

    // ---- what an item waits on ----

    def "forItem should check the item through the item service and then answer its waits"() {

        given:
        List<Wait> rows = [waitOn(7, 100)]

        when:
        List<Wait> got = service.forItem(REX, 101)

        then:
        1 * items.get(REX, 101) >> itemRows[101]

        then:
        1 * repo.forItem(101) >> rows
        got == rows
    }

    // ---- add ----

    @Unroll
    def "add should check both items, record the wait attributed to #expectedCode and answer the item's waits when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        List<Wait> rows = [waitOn(7, 100)]

        when:
        List<Wait> got = service.add(caller, 101, 100)

        then:
        1 * items.get(caller, 101) >> itemRows[101]

        then:
        1 * items.get(caller, 100) >> itemRows[100]

        then:
        1 * repo.save(101, 100, expectedCode) >> 7

        then:
        1 * repo.forItem(101) >> rows
        got == rows

        where:
        scenario                 | callerName || expectedCode
        'a person adds it'       | 'TONY'     || 'tony'
        'the house adds it'      | 'HOUSE'    || 'felix'
        'a client adds it'       | 'REX'      || 'rex'
    }

    @Unroll
    def "add should refuse with the rule's sentence and save nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.add(caller, itemId, dependsOnId)

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        0 * repo.save(*_)
        0 * repo.forItem(_)

        where:
        scenario                                         | callerName | itemId | dependsOnId || expectedMessage
        'the item waited on is in another solution'      | 'TONY'     | 101    | 200         || 'An item can only wait on an item of the same solution. Nothing was saved.'
        'the waiting item is in another solution'        | 'HOUSE'    | 201    | 100         || 'An item can only wait on an item of the same solution. Nothing was saved.'
    }

    def "add should look up both items before it refuses two solutions"() {

        when:
        service.add(TONY, 101, 200)

        then:
        1 * items.get(TONY, 101) >> itemRows[101]
        1 * items.get(TONY, 200) >> itemRows[200]
        SolutionRuleException ex = thrown()
        ex.message == TWO_SOLUTIONS
        0 * repo.save(*_)
    }

    @Unroll
    def "add should throw item not found and read no waits when the repository answers a #scenario id"() {

        when:
        service.add(TONY, 101, 100)

        then:
        1 * repo.save(101, 100, 'tony') >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.forItem(_)

        where:
        scenario   | savedId || expectedMessage
        'zero'     | 0       || 'Item not found.'
        'negative' | -1      || 'Item not found.'
    }

    @Unroll
    def "add should pass the repository's refusal through and read no waits when #scenario"() {

        when:
        service.add(TONY, itemId, dependsOnId)

        then:
        1 * repo.save(itemId, dependsOnId, 'tony') >> { throw new SolutionRuleException(refusal) }
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        0 * repo.forItem(_)

        where:
        scenario                      | itemId | dependsOnId | refusal                                                                                                         || expectedMessage
        'the item waits on itself'    | 101    | 101         | 'An item cannot wait on itself. Nothing was saved.'                                                             || 'An item cannot wait on itself. Nothing was saved.'
        'the wait is a repeat'        | 101    | 100         | 'It already waits on that item. Nothing was saved.'                                                             || 'It already waits on that item. Nothing was saved.'
        'the wait closes a circle'    | 100    | 101         | 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.' || 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.'
    }

    // ---- remove ----

    def "remove should take the wait away when the caller may see its solution"() {

        when:
        service.remove(REX, 7)

        then:
        1 * repo.find(7) >> waitRows[7]

        then:
        1 * repo.delete(7) >> true
    }

    @Unroll
    def "remove should throw wait not found and not reach the repository when the id is #scenario"() {

        when:
        service.remove(TONY, waitId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.find(_)
        0 * repo.delete(_)

        where:
        scenario   | waitId || expectedMessage
        'zero'     | 0      || 'Wait not found.'
        'negative' | -7     || 'Wait not found.'
    }

    def "remove should throw wait not found when the repository removed nothing"() {

        when:
        service.remove(TONY, 8)

        then:
        1 * repo.delete(8) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Wait not found.'
    }

    // ---- the solution's counts ----

    def "forSolution should answer every waiting item's counts when the solution is visible"() {

        given:
        List<WaitCount> rows = [new WaitCount(101, 2, 1)]

        when:
        List<WaitCount> got = service.forSolution(REX, 1)

        then:
        1 * repo.forSolution(1) >> rows
        got == rows
    }
}
