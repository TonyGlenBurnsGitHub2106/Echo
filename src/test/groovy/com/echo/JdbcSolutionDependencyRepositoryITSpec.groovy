package com.echo

import com.echo.SolutionDependencyModels.StoredWait
import com.echo.SolutionDependencyModels.Wait
import com.echo.SolutionDependencyModels.WaitCount

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): JdbcSolutionDependencyRepository (stp_SolutionItemDependency*) against a real EchoTest_
 * database -- a wait saved and read back with its solution and audit row, every refusal of the save proc (itself,
 * a repeat, another solution, a missing item, a circle of two and of three) with nothing written, both directions
 * of one item's waits, the solution's open-wait counts, a wait removed, and that stp_SolutionProjectItem_Delete,
 * stp_SolutionProject_Delete and stp_Solution_Delete take the waits of what they remove with them. Every feature
 * is rolled back by EchoDbITSpec.
 */
class JdbcSolutionDependencyRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionDependencyRepository repo
    String projectName
    int sid
    int project

    def setup() {
        repo = new JdbcSolutionDependencyRepository(dataSource())
        sid = newSolution()
        projectName = 'Deploy ' + uniq()
        project = newProject(sid, projectName)
    }

    /** How many waits touch one item, from either side. */
    private static int waitsOf(EchoDbITSpec db, int itemId) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionItemDependency WHERE SolutionProjectItemID = ? OR DependsOnSolutionProjectItemID = ?', itemId, itemId)
    }

    /** Whether the wait row is still stored (find joins the items, so it cannot tell once an item is gone). */
    private static boolean stored(EchoDbITSpec db, int waitId) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionItemDependency WHERE SolutionItemDependencyID = ?', waitId) == 1
    }

    // ---- save and find ----

    def "save should record that one item waits on another and find should read it back with its solution"() {

        given:
        int a = newItem(project, 'Open the firewall')
        int b = newItem(project, 'Deploy the service')

        when:
        int id = repo.save(b, a, 'Rex')
        StoredWait w = repo.find(id)

        then:
        id > 0
        w == new StoredWait(id, b, a, sid)
        scalar('SELECT m.MemberCode FROM dbo.SolutionItemDependency d JOIN dbo.SolutionMember m ON m.SolutionMemberID = d.CreatedByMemberID ' +
               'WHERE d.SolutionItemDependencyID = ?', id) == 'rex'
        logRows('SolutionItemDependency', 'SolutionItemDependencyID', id)*.IsInsertion == [true]
    }

    def "save should record no member when none is named"() {

        given:
        int a = newItem(project)
        int b = newItem(project)

        when:
        int id = repo.save(b, a, null)

        then:
        scalar('SELECT CreatedByMemberID FROM dbo.SolutionItemDependency WHERE SolutionItemDependencyID = ?', id) == null
    }

    @Unroll
    def "#seq. save should refuse and write nothing when #scenario"() {

        given:
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int c = newItem(project, 'C')
        int x = newItem(newProject(newSolution()), 'X')
        repo.save(a, b, 'tony')
        repo.save(b, c, 'tony')
        Map<String, Integer> ids = [A: a, B: b, C: c, X: x, missing: 987654321]
        int before = count('SELECT COUNT(*) FROM dbo.SolutionItemDependency')

        when:
        repo.save(ids[itemName], ids[onName], 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionItemDependency') == before

        where:
        seq | scenario                                         | itemName  | onName    || expectedMessage
        1   | 'an item waits on itself'                        | 'A'       | 'A'       || 'An item cannot wait on itself. Nothing was saved.'
        2   | 'the same wait is saved again'                   | 'A'       | 'B'       || 'It already waits on that item. Nothing was saved.'
        3   | 'the item waited on is in another solution'      | 'A'       | 'X'       || 'An item can only wait on an item of the same solution. Nothing was saved.'
        4   | 'the waiting item is in another solution'        | 'X'       | 'A'       || 'An item can only wait on an item of the same solution. Nothing was saved.'
        5   | 'the waiting item does not exist'                | 'missing' | 'A'       || 'That item no longer exists. Nothing was saved.'
        6   | 'the item waited on does not exist'              | 'A'       | 'missing' || 'The item it would wait on no longer exists. Nothing was saved.'
        7   | 'B would wait on A, which waits on B'            | 'B'       | 'A'       || 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.'
        8   | 'C would wait on A, which waits on B, then C'    | 'C'       | 'A'       || 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.'
        9   | 'C would wait on B, which waits on C'            | 'C'       | 'B'       || 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.'
    }

    def "save should allow a shortcut past a chain and several items waiting on one when no circle is closed"() {

        given:
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int c = newItem(project, 'C')
        int d = newItem(project, 'D')
        repo.save(a, b, 'tony')
        repo.save(b, c, 'tony')

        expect:
        repo.save(a, c, 'tony') > 0
        repo.save(d, c, 'tony') > 0
        repo.save(d, b, 'tony') > 0
        count('SELECT COUNT(*) FROM dbo.SolutionItemDependency WHERE DependsOnSolutionProjectItemID = ?', c) == 3
    }

    def "save should keep working on the same connection when a refusal came first"() {

        given:
        int a = newItem(project)
        int b = newItem(project)
        repo.save(a, b, 'tony')
        ruleOf { repo.save(b, a, 'tony') }

        expect:
        ruleOf { repo.save(a, a, 'tony') }.message == 'An item cannot wait on itself. Nothing was saved.'
        repo.save(b, newItem(project), 'tony') > 0
    }

    def "find should answer null when the id is unknown"() {

        expect:
        repo.find(-1) == null
        repo.find(987654321) == null
    }

    // ---- lists ----

    def "forItem should answer what the item waits on, open ones first, and then what waits on it"() {

        given:
        int alpha = newItem(project, 'Alpha open')
        int delta = newItem(project, 'Delta done', null, 'done')
        int zulu = newItem(project, 'Zulu open')
        int bravo = newItem(project, 'Bravo')
        int charlie = newItem(project, 'Charlie')
        int w1 = repo.save(bravo, zulu, 'tony')
        int w2 = repo.save(bravo, alpha, 'tony')
        int w3 = repo.save(bravo, delta, 'tony')
        int w4 = repo.save(charlie, bravo, 'tony')

        when:
        List<Wait> got = repo.forItem(bravo)

        then:
        got*.direction() == ['waitsOn', 'waitsOn', 'waitsOn', 'blocks']
        got*.itemId() == [alpha, zulu, delta, charlie]
        got*.id() == [w2, w1, w3, w4]
        got[0].title() == 'Alpha open'
        got[0].projectName() == projectName
        got[0].statusCode() == 'todo'
        got[0].statusName() == 'Not started'
        got[0].colorHex() == '#6B7280'
        !got[0].closed()
        got[0].createdDate() != null
        got[2].closed()
        got[2].statusCode() == 'done'
        got[3].title() == 'Charlie'

        and: 'from the other side'
        repo.forItem(alpha)*.direction() == ['blocks']
        repo.forItem(alpha)*.itemId() == [bravo]
        repo.forItem(newItem(project)).isEmpty()
    }

    def "forSolution should count for each waiting item how many waits it has and how many are still open"() {

        given:
        int a = newItem(project, 'A')
        int d = newItem(project, 'D', null, 'done')
        int b = newItem(project, 'B')
        int c = newItem(project, 'C')
        int e = newItem(newProject(sid), 'E')
        repo.save(b, a, 'tony')
        repo.save(b, d, 'tony')
        repo.save(c, b, 'tony')
        repo.save(e, d, 'tony')
        int other = newSolution()
        int otherProject = newProject(other)
        int x = newItem(otherProject, 'X')
        repo.save(x, newItem(otherProject, 'Y'), 'tony')

        when:
        List<WaitCount> got = repo.forSolution(sid)

        then:
        got as Set == [new WaitCount(b, 2, 1), new WaitCount(c, 1, 1), new WaitCount(e, 1, 0)] as Set
        got.size() == 3
        repo.forSolution(other) == [new WaitCount(x, 1, 1)]
        repo.forSolution(newSolution()).isEmpty()
    }

    // ---- delete ----

    def "delete should remove only the wait, leave both items and audit it, answering false the second time"() {

        given:
        int a = newItem(project)
        int b = newItem(project)
        int id = repo.save(b, a, 'tony')

        expect:
        repo.delete(id)
        repo.find(id) == null
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID IN (?, ?)', a, b) == 2
        logRows('SolutionItemDependency', 'SolutionItemDependencyID', id)*.IsInsertion == [true, false]
        logRows('SolutionItemDependency', 'SolutionItemDependencyID', id).last().DeletedDate != null
        !repo.delete(id)
    }

    // ---- the delete procs take the waits with them ----

    def "stp_SolutionProjectItem_Delete should remove the waits of the item and its sub-items from either side"() {

        given:
        int parent = newItem(project, 'Parent')
        int child = newItem(project, 'Child', parent)
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int z = newItem(project, 'Z')
        int childWaits = repo.save(child, a, 'tony')
        int waitsOnParent = repo.save(b, parent, 'tony')
        int bystander = repo.save(a, z, 'tony')

        when:
        Map<String, Object> result = row('EXEC dbo.stp_SolutionProjectItem_Delete ?', parent)

        then:
        ((Number) result.RowsAffected).intValue() == 2
        !stored(this, childWaits)
        !stored(this, waitsOnParent)
        stored(this, bystander)
        waitsOf(this, parent) == 0
        waitsOf(this, child) == 0
        repo.forItem(b).isEmpty()
        logRows('SolutionItemDependency', 'SolutionItemDependencyID', childWaits).last().DeletedDate != null
    }

    def "stp_SolutionProject_Delete should remove every wait touching the project's items, even from another project"() {

        given:
        int otherProject = newProject(sid)
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int e = newItem(otherProject, 'E')
        int f = newItem(otherProject, 'F')
        int g = newItem(otherProject, 'G')
        int inside = repo.save(b, a, 'tony')
        int intoDoomed = repo.save(e, a, 'tony')
        int outOfDoomed = repo.save(a, f, 'tony')
        // F waits on G, not on E: E -> A -> F -> E would be a circle, which the save proc refuses
        int kept = repo.save(f, g, 'tony')

        when:
        row('EXEC dbo.stp_SolutionProject_Delete ?', project)

        then:
        !stored(this, inside)
        !stored(this, intoDoomed)
        !stored(this, outOfDoomed)
        stored(this, kept)
        repo.find(kept) == new StoredWait(kept, f, g, sid)
    }

    def "stp_Solution_Delete should remove the solution's waits and leave another solution's"() {

        given:
        int a = newItem(project, 'A')
        int b = newItem(project, 'B')
        int mine = repo.save(b, a, 'tony')
        int other = newSolution()
        int otherProject = newProject(other)
        int theirs = repo.save(newItem(otherProject, 'X'), newItem(otherProject, 'Y'), 'tony')

        when:
        row('EXEC dbo.stp_Solution_Delete ?', sid)

        then:
        !stored(this, mine)
        stored(this, theirs)
        repo.forSolution(other).size() == 1
        logRows('SolutionItemDependency', 'SolutionItemDependencyID', mine).last().DeletedDate != null
    }
}
