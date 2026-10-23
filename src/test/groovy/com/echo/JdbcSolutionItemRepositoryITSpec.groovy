package com.echo

import com.echo.SolutionDrafts.ItemDraft
import com.echo.SolutionModels.Item

import spock.lang.Unroll

// Story 5653: JdbcSolutionItemRepository against a real EchoTest_ database (stp_SolutionProjectItem_*).
class JdbcSolutionItemRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionItemRepository repo
    int sid
    int pid

    def setup() {
        repo = new JdbcSolutionItemRepository(dataSource())
        sid = newSolution()
        pid = newProject(sid, 'Main')
    }

    def "save should store every field so find reads it back when an item is added"() {

        when:
        int id = repo.save(draft(pid, [title: 'Round trip', sort: 4]), 'Tony')
        Item i = repo.find(id)

        then:
        i.id() == id
        i.projectId() == pid
        i.solutionId() == sid
        i.projectName() == 'Main'
        i.parentItemId() == null
        i.title() == 'Round trip'
        i.detail() == 'Detail text'
        i.hasDetail()
        i.statusCode() == 'inprogress'
        i.statusName() == 'In progress'
        !i.closed()
        i.colorHex() == '#2563EB'
        i.assigneeCode() == 'rex'
        i.assigneeName() == 'Rex'
        i.priority() == 2
        i.sortOrder() == 4
        i.dueDate() == getLocalDateTime('03/04/2027 05:06')
        i.completedDate() == null
        i.createdByCode() == 'tony'
        i.createdDate() != null
        i.modifiedBy() == 'tony'
        i.childCount() == 0
        i.logCount() == 0
    }

    def "find should return null when the id is unknown"() {

        expect:
        repo.find(-1) == null
    }

    def "save should use priority 3, the first status and no assignee when the values are out of range or unknown"() {

        when:
        Item i = repo.find(repo.save(draft(pid, [priority: 9, status: 'weird', assignee: 'nobody-x']), 'tony'))

        then:
        i.priority() == 3
        i.statusCode() == 'todo'
        i.assigneeCode() == null
    }

    def "save should create the author as an agent member when the author code is unknown"() {

        given:
        String code = 'a' + uniq()

        when:
        Item i = repo.find(repo.save(draft(pid, [:]), code))

        then:
        i.createdByCode() == code
        row('SELECT IsAgent, MemberName FROM dbo.SolutionMember WHERE MemberCode = ?', code).IsAgent == true
    }

    def "save should stamp CompletedDate when the status is closed and clear it when reopened"() {

        given:
        int id = repo.save(draft(pid, [status: 'done']), 'tony')

        expect:
        repo.find(id).closed()
        repo.find(id).completedDate() != null

        when:
        repo.save(draft(pid, [id: id, status: 'todo']), 'tony')

        then:
        repo.find(id).completedDate() == null
    }

    def "save should append siblings and nest children under a parent when items are added"() {

        when:
        int a = repo.save(draft(pid, [:]), 'tony')
        int b = repo.save(draft(pid, [:]), 'tony')
        int c1 = repo.save(draft(pid, [parent: a]), 'tony')
        int c2 = repo.save(draft(pid, [parent: a]), 'tony')

        then:
        repo.find(a).sortOrder() == 1
        repo.find(b).sortOrder() == 2
        repo.find(c1).sortOrder() == 1
        repo.find(c2).sortOrder() == 2
        repo.find(c1).parentItemId() == a
        repo.find(a).childCount() == 2
    }

    def "listForSolution should order by project, level and sort order and filter by project when there are two projects"() {

        given:
        int p2 = newProject(sid, 'Second')
        int a = repo.save(draft(pid, [sort: 2]), 'tony')
        int b = repo.save(draft(pid, [sort: 1]), 'tony')
        int child = repo.save(draft(pid, [parent: b]), 'tony')
        int other = repo.save(draft(p2, [:]), 'tony')

        when:
        List<Item> all = repo.listForSolution(sid, null)
        List<Item> onlyP2 = repo.listForSolution(sid, p2)

        then:
        all*.id() == [b, a, child, other]
        onlyP2*.id() == [other]
        all[0].detail() == null
        all[0].detailPreview() == 'Detail text'
        repo.listForSolution(newSolution(), null).isEmpty()
    }

    def "save should keep the item's own project when an update names another one"() {

        given:
        int p2 = newProject(sid, 'Elsewhere')
        int id = repo.save(draft(pid, [:]), 'tony')

        when:
        repo.save(draft(p2, [id: id, title: 'Edited']), 'felix')

        then:
        repo.find(id).projectId() == pid
        repo.find(id).title() == 'Edited'
        repo.find(id).modifiedBy() == 'felix'
    }

    @Unroll
    def "save should refuse when #scenario"() {

        given:
        int otherProject = newProject(newSolution())
        int foreignParent = repo.save(draft(otherProject, [:]), 'tony')
        int top = repo.save(draft(pid, [:]), 'tony')
        int child = repo.save(draft(pid, [parent: top]), 'tony')
        int grandchild = repo.save(draft(pid, [parent: child]), 'tony')
        Map<String, Object> ids = [none: null, top: top, grandchild: grandchild, foreign: foreignParent]
        int project = projectKind == 'missing' ? -1 : pid
        Map<String, Object> m = [title: title, parent: ids[parentKind]]
        if (editTop) m.id = top

        when:
        repo.save(draft(project, m), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                                  | title  | projectKind | parentKind   | editTop || message
        "the title is blank"                      | "  "   | "main"      | "none"       | false   || "needs a title"
        "the project does not exist"              | "T"    | "missing"   | "none"       | false   || "project no longer exists"
        "the parent is in another project"        | "T"    | "main"      | "foreign"    | false   || "same project"
        "the item would be its own parent"        | "T"    | "main"      | "top"        | true    || "its own parent"
        "the parent is one of its own sub-items"  | "T"    | "main"      | "grandchild" | true    || "own sub-items"
    }

    def "setStatus should change the status and completed date, refuse an unknown status and answer false for an unknown id"() {

        given:
        int id = repo.save(draft(pid, [status: 'todo']), 'tony')

        expect:
        repo.setStatus(id, ' DONE ', 'Felix')
        repo.find(id).statusCode() == 'done'
        repo.find(id).completedDate() != null
        repo.find(id).modifiedBy() == 'felix'
        repo.setStatus(id, 'blocked', 'felix')
        repo.find(id).completedDate() == null
        !repo.setStatus(-1, 'done', 'felix')
        ruleOf { repo.setStatus(id, 'nope', 'felix') }.message.contains('not a status')
    }

    def "move should re-slot the item among its siblings and renumber them 1..n when the item moves within its project"() {

        given:
        int a = repo.save(draft(pid, [:]), 'tony')
        int b = repo.save(draft(pid, [:]), 'tony')
        int c = repo.save(draft(pid, [:]), 'tony')

        when:
        boolean moved = repo.move(c, null, null, 1, 'felix')

        then:
        moved
        [a, b, c].collect { int x -> repo.find(x).sortOrder() } == [2, 3, 1]
        repo.find(c).modifiedBy() == 'felix'

        when:
        repo.move(c, null, null, 99, 'felix')

        then:
        [a, b, c].collect { int x -> repo.find(x).sortOrder() } == [1, 2, 3]
    }

    def "move should carry the whole subtree when the item moves under a parent in another project of the same solution"() {

        given:
        int p2 = newProject(sid, 'Target')
        int parent = repo.save(draft(pid, [:]), 'tony')
        int child = repo.save(draft(pid, [parent: parent]), 'tony')
        int grandchild = repo.save(draft(pid, [parent: child]), 'tony')
        int newParent = repo.save(draft(p2, [:]), 'tony')

        when:
        repo.move(child, p2, newParent, 0, 'felix')

        then:
        repo.find(child).projectId() == p2
        repo.find(child).parentItemId() == newParent
        repo.find(grandchild).projectId() == p2
        repo.find(grandchild).parentItemId() == child
        repo.find(parent).childCount() == 0
    }

    def "move should refuse another solution's project, a cycle and a parent outside the destination when asked to"() {

        given:
        int foreignProject = newProject(newSolution())
        int p2 = newProject(sid, 'Other')
        int top = repo.save(draft(pid, [:]), 'tony')
        int child = repo.save(draft(pid, [parent: top]), 'tony')
        int inP2 = repo.save(draft(p2, [:]), 'tony')

        expect:
        ruleOf { repo.move(top, foreignProject, null, 0, 'felix') }.message.contains('same solution')
        ruleOf { repo.move(top, null, child, 0, 'felix') }.message.contains('under itself')
        ruleOf { repo.move(top, null, top, 0, 'felix') }.message.contains('under itself')
        ruleOf { repo.move(top, null, inP2, 0, 'felix') }.message.contains('destination project')
        !repo.move(-1, null, null, 0, 'felix')
    }

    def "delete should remove the subtree, detach its log entries and files and report the count when an item has children"() {

        given:
        int top = repo.save(draft(pid, [:]), 'tony')
        int child = repo.save(draft(pid, [parent: top]), 'tony')
        repo.save(draft(pid, [parent: child]), 'tony')
        int keep = repo.save(draft(pid, [:]), 'tony')
        int log = idOf("EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, ?, 'note', 'tony', 'about the child', NULL", sid, child)
        int file = idOf("EXEC dbo.stp_SolutionFile_Save 0, ?, ?, 'F', 'f.txt', 'nook', NULL, 's', 'text/plain', 1, NULL, 'tony'", sid, top)

        expect:
        repo.find(child).logCount() == 1

        when:
        int n = repo.delete(top)

        then:
        n == 3
        repo.find(top) == null
        repo.find(child) == null
        repo.find(keep) != null
        scalar('SELECT SolutionProjectItemID FROM dbo.SolutionLog WHERE SolutionLogID = ?', log) == null
        scalar('SELECT SolutionProjectItemID FROM dbo.SolutionFile WHERE SolutionFileID = ?', file) == null
        logRows('SolutionProjectItem', 'SolutionProjectItemID', child).last().DeletedDate != null
        repo.delete(top) == 0
    }

    def "save and setStatus should write SolutionProjectItem_LOG rows when an item is added and closed"() {

        when:
        int id = repo.save(draft(pid, [:]), 'tony')
        repo.setStatus(id, 'done', 'felix')
        List<Map<String, Object>> log = logRows('SolutionProjectItem', 'SolutionProjectItemID', id)

        then:
        log.size() == 2
        log[0].IsInsertion == true
        log[1].IsInsertion == false
        log[1].CompletedDate != null
    }

    private static ItemDraft draft(int projectId, Map m) {
        new ItemDraft((m.id ?: 0) as int, projectId, m.parent as Integer,
            (m.containsKey('title') ? m.title : 'Item ' + uniq()) as String, 'Detail text',
            (m.status ?: 'inprogress') as String, (m.containsKey('assignee') ? m.assignee : 'rex') as String,
            (m.priority ?: 2) as int, (m.sort ?: 0) as int, getLocalDateTime('03/04/2027 05:06'))
    }
}
