package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionDrafts.LogFilter
import com.echo.SolutionModels.LogEntry

import spock.lang.Unroll

// Story 5653: JdbcSolutionLogRepository against a real EchoTest_ database (stp_SolutionLog_*).
class JdbcSolutionLogRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionLogRepository repo
    int sid
    int pid
    int item

    def setup() {
        repo = new JdbcSolutionLogRepository(dataSource())
        sid = newSolution()
        pid = newProject(sid, 'Proj')
        item = newItem(pid, 'The item')
    }

    def "save should store every field and take the project from the item when an entry is added"() {

        given:
        LocalDateTime when = getLocalDateTime('10/01/2026 08:30')

        when:
        int id = repo.save(draft(sid, [item: item, type: 'command', text: '  git status  ', date: when]), 'Rex')
        LogEntry e = repo.find(id)

        then:
        e.id() == id
        e.solutionId() == sid
        e.projectId() == pid
        e.projectName() == 'Proj'
        e.itemId() == item
        e.itemTitle() == 'The item'
        e.sequenceNumber() == 1
        e.typeCode() == 'command'
        e.typeName() == 'Command'
        e.authorCode() == 'rex'
        e.authorName() == 'Rex'
        e.text() == 'git status'
        !e.truncated()
        e.logDate() == when
        e.createdDate() != null
        e.stepCount() == 0
    }

    def "save should number entries per solution, date them now and use the first type when the date and type are missing"() {

        when:
        int a = repo.save(draft(sid, [:]), 'tony')
        int b = repo.save(draft(sid, [type: 'nonsense']), 'tony')
        int other = repo.save(draft(newSolution(), [:]), 'tony')

        then:
        repo.find(a).sequenceNumber() == 1
        repo.find(b).sequenceNumber() == 2
        repo.find(other).sequenceNumber() == 1
        repo.find(b).typeCode() == 'note'
        repo.find(a).logDate() != null
    }

    def "save should change text, type and links but keep author, sequence and date when an entry is edited"() {

        given:
        LocalDateTime when = getLocalDateTime('01/01/2026 00:00')
        int id = repo.save(draft(sid, [date: when]), 'rex')

        when:
        repo.save(draft(sid, [id: id, project: pid, type: 'decision', text: 'Edited', date: LocalDateTime.now()]), 'tony')
        LogEntry e = repo.find(id)

        then:
        e.text() == 'Edited'
        e.typeCode() == 'decision'
        e.projectId() == pid
        e.authorCode() == 'rex'
        e.sequenceNumber() == 1
        e.logDate() == when
        e.modifiedDate() != null
    }

    @Unroll
    def "save should refuse when #scenario"() {

        given:
        int foreignItem = newItem(newProject(newSolution()))
        int foreignProject = newProject(newSolution())
        Map<String, Object> m = [text: text]
        if (target == 'missing-solution') m.solution = -1
        if (target == 'foreign-item') m.item = foreignItem
        if (target == 'foreign-project') m.project = foreignProject
        if (target == 'missing-entry') m.id = 987654321

        when:
        repo.save(draft(m.solution == null ? sid : (int) m.solution, m), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                                  | text    | target            || message
        "the text is blank"                       | "   "   | "none"            || "needs some text"
        "the solution does not exist"             | "x"     | "missing-solution"|| "solution no longer exists"
        "the item is from another solution"       | "x"     | "foreign-item"    || "item does not belong"
        "the project is from another solution"    | "x"     | "foreign-project" || "project does not belong"
        "the entry to edit does not exist"        | "x"     | "missing-entry"   || "solution no longer exists"
    }

    @Unroll
    def "list should return the entries #scenario"() {

        given:
        List<Integer> ids = (1..5).collect { int n -> repo.save(draft(sid, [text: 'n' + n]), 'tony') }

        when:
        List<Integer> found = repo.list(filter(sid, [asc: asc, offset: offset, size: size, since: since]))*.id()

        then:
        found == expected.split(',').collect { String s -> ids[(s as int) - 1] }

        where:
        scenario                               | asc   | offset | size | since || expected
        "newest first by default"              | false | 0      | 50   | null  || "5,4,3,2,1"
        "oldest first when asked"              | true  | 0      | 50   | null  || "1,2,3,4,5"
        "one page of two after the first"      | false | 1      | 2    | null  || "4,3"
        "after a sequence number, oldest first"| true  | 0      | 50   | 3     || "4,5"
    }

    def "list and counts should filter by project, item, author, type and search and agree when entries differ"() {

        given:
        repo.save(draft(sid, [item: item, type: 'command', text: 'deploy the thing']), 'rex')
        repo.save(draft(sid, [project: pid, type: 'note', text: 'thinking about deploy']), 'tony')
        repo.save(draft(sid, [type: 'decision', text: 'we chose X']), 'tony')

        expect:
        repo.list(filter(sid, [item: item]))*.text() == ['deploy the thing']
        repo.list(filter(sid, [project: pid])).size() == 2
        repo.list(filter(sid, [author: ' REX '])).size() == 1
        repo.list(filter(sid, [type: 'decision']))*.text() == ['we chose X']
        repo.list(filter(sid, [search: 'deploy'])).size() == 2
        repo.counts(filter(sid, [:])) == ['total': 3, 'type:command': 1, 'type:decision': 1, 'type:note': 1, 'author:rex': 1, 'author:tony': 2]
        repo.counts(filter(sid, [search: 'deploy'])).total == 2
        repo.counts(filter(sid, [project: pid]))['type:command'] == 1
        repo.counts(filter(sid, [author: 'tony'])) == ['total': 2, 'type:decision': 1, 'type:note': 1, 'author:tony': 2]
    }

    def "list should cut the text to 2000 characters and mark it truncated while find returns it whole when an entry is long"() {

        given:
        int id = repo.save(draft(sid, [text: getReplicatedString('x', 2500)]), 'tony')

        expect:
        repo.list(filter(sid, [:]))[0].text().length() == 2000
        repo.list(filter(sid, [:]))[0].truncated()
        repo.find(id).text().length() == 2500
        !repo.find(id).truncated()
    }

    def "list should treat an underscore in the search as a wildcard when the search is not escaped"() {

        given:
        repo.save(draft(sid, [text: 'abc']), 'tony')

        expect:
        repo.list(filter(sid, [search: 'a_c'])).size() == 1
    }

    def "delete should remove the entry, unhook the note steps that cited it and audit it when the entry is cited"() {

        given:
        int id = repo.save(draft(sid, [text: 'cited']), 'tony')
        int note = idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', 'N', 'B', NULL, 'tony', 0", sid)
        int step = idOf('EXEC dbo.stp_SolutionNoteStep_FromLog ?, ?', note, id)

        expect:
        repo.find(id).stepCount() == 1

        when:
        boolean gone = repo.delete(id)

        then:
        gone
        repo.find(id) == null
        scalar('SELECT SourceSolutionLogID FROM dbo.SolutionNoteStep WHERE SolutionNoteStepID = ?', step) == null
        logRows('SolutionLog', 'SolutionLogID', id).last().DeletedDate != null
        !repo.delete(id)
    }

    def "find should return null when the id is unknown"() {

        expect:
        repo.find(-1) == null
    }

    def "save should write SolutionLog_LOG rows when an entry is added and edited"() {

        when:
        int id = repo.save(draft(sid, [text: 'one']), 'tony')
        repo.save(draft(sid, [id: id, text: 'two']), 'tony')
        List<Map<String, Object>> log = logRows('SolutionLog', 'SolutionLogID', id)

        then:
        log*.LogText == ['one', 'two']
        log*.IsInsertion == [true, false]
    }

    private static LogDraft draft(int solutionId, Map m) {
        new LogDraft((m.id ?: 0) as int, solutionId, m.project as Integer, m.item as Integer,
            (m.type ?: 'note') as String, (m.containsKey('text') ? m.text : 'Entry ' + uniq()) as String,
            m.date as LocalDateTime)
    }

    private static LogFilter filter(int solutionId, Map m) {
        new LogFilter(solutionId, m.project as Integer, m.item as Integer, m.author as String,
            m.type as String, m.since as Integer, m.search as String, (m.asc ?: false) as boolean,
            (m.offset ?: 0) as int, (m.size ?: 50) as int)
    }
}
