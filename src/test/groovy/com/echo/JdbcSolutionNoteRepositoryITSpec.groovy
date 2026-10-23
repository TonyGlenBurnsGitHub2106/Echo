package com.echo

import com.echo.SolutionDrafts.NoteDraft
import com.echo.SolutionDrafts.StepDraft
import com.echo.SolutionModels.Note
import com.echo.SolutionModels.NoteStep
import com.echo.SolutionModels.NoteVersion

import spock.lang.PendingFeature
import spock.lang.Unroll

// Story 5653: JdbcSolutionNoteRepository against a real EchoTest_ database (stp_SolutionNote*, stp_SolutionNoteVersion*,
// stp_SolutionNoteStep*).
class JdbcSolutionNoteRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionNoteRepository repo
    int sid
    int pid

    def setup() {
        repo = new JdbcSolutionNoteRepository(dataSource())
        sid = newSolution()
        pid = newProject(sid, 'P')
    }

    def "save should store version 1 with every field so find reads it back when a note is added"() {

        when:
        int id = repo.save(draft(sid, [project: pid, title: 'How to deploy', body: 'Do this.', change: ' first ', sort: 3]), 'Rex')
        Note n = repo.find(id)

        then:
        n.id() == id
        n.solutionId() == sid
        n.projectId() == pid
        n.projectName() == 'P'
        n.typeCode() == 'runbook'
        n.typeName() == 'Runbook'
        n.title() == 'How to deploy'
        n.currentVersionNumber() == 1
        n.sortOrder() == 3
        n.createdDate() != null
        n.modifiedBy() == 'rex'
        n.authorCode() == 'rex'
        n.authorName() == 'Rex'
        n.versionDate() != null
        n.changeNote() == 'first'
        n.stepCount() == 0
        n.body() == 'Do this.'
        n.steps() == []
    }

    def "find should return null when the id is unknown"() {

        expect:
        repo.find(-1) == null
    }

    def "save should make a new version only when the body changes"() {

        given:
        int id = repo.save(draft(sid, [body: 'one']), 'tony')

        when:
        repo.save(draft(sid, [id: id, body: 'two', change: 'second']), 'rex')
        repo.save(draft(sid, [id: id, title: 'Retitled', body: 'two']), 'tony')
        Note n = repo.find(id)
        List<NoteVersion> versions = repo.versions(id)

        then:
        n.currentVersionNumber() == 2
        n.title() == 'Retitled'
        n.body() == 'two'
        versions*.versionNumber() == [2, 1]
        versions*.current() == [true, false]
        versions[0].changeNote() == 'second'
        versions[0].authorCode() == 'rex'
        versions[0].bodyLength() == 3
        versions[0].body() == null
        repo.version(id, 1).body() == 'one'
        !repo.version(id, 1).current()
        repo.version(id, 2).current()
        repo.version(id, 3) == null
    }

    @PendingFeature(reason = 'BUG: stp_SolutionNote_Save compares the new body with the current one using the case-insensitive database collation, so a change of case only (or of trailing spaces) is silently dropped -- no new version is written')
    def "save should make a new version when the body changes only in case"() {

        given:
        int id = repo.save(draft(sid, [body: 'restart the service']), 'tony')

        when:
        repo.save(draft(sid, [id: id, body: 'Restart The Service']), 'tony')

        then:
        repo.find(id).body() == 'Restart The Service'
        repo.find(id).currentVersionNumber() == 2
    }

    def "save should use the first kind and put the note last when the type is unknown and the sort order is 0"() {

        when:
        int a = repo.save(draft(sid, [sort: 5]), 'tony')
        int b = repo.save(draft(sid, [type: 'nonsense']), 'tony')

        then:
        repo.find(b).typeCode() == 'instructions'
        repo.find(b).sortOrder() == 6
        repo.find(a).sortOrder() == 5
    }

    def "listForSolution should order by kind, sort order and title, filter by kind and carry only a preview when notes differ"() {

        given:
        int r2 = repo.save(draft(sid, [type: 'runbook', title: 'B', sort: 1]), 'tony')
        int r1 = repo.save(draft(sid, [type: 'runbook', title: 'A', sort: 1]), 'tony')
        int s = repo.save(draft(sid, [type: 'strategy', title: 'Z', body: getReplicatedString('x', 400)]), 'tony')

        when:
        List<Note> all = repo.listForSolution(sid, null)

        then:
        all*.id() == [s, r1, r2]
        repo.listForSolution(sid, ' RUNBOOK ')*.id() == [r1, r2]
        repo.listForSolution(sid, '')*.id() == [s, r1, r2]
        all[0].body() == null
        all[0].bodyPreview().length() == 300
        all[0].steps() == []
    }

    @Unroll
    def "save should refuse when #scenario"() {

        given:
        int foreignProject = newProject(newSolution())
        Map<String, Object> m = [title: title]
        int solution = target == 'missing-solution' ? -1 : sid
        if (target == 'foreign-project') m.project = foreignProject
        if (target == 'missing-note') m.id = 987654321

        when:
        repo.save(draft(solution, m), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                                | title | target             || message
        "the title is blank"                    | " "   | "none"             || "needs a title"
        "the solution does not exist"           | "T"   | "missing-solution" || "solution no longer exists"
        "the project is from another solution"  | "T"   | "foreign-project"  || "project does not belong"
        "the note to edit does not exist"       | "T"   | "missing-note"     || "solution no longer exists"
    }

    def "delete should remove the note, its versions and its steps and audit every removal when the note has steps"() {

        given:
        int id = repo.save(draft(sid, [:]), 'tony')
        int st = repo.saveStep(step(id, [:]))

        when:
        boolean gone = repo.delete(id)

        then:
        gone
        repo.find(id) == null
        repo.versions(id).isEmpty()
        repo.steps(id).isEmpty()
        logRows('SolutionNote', 'SolutionNoteID', id).last().DeletedDate != null
        logRows('SolutionNoteStep', 'SolutionNoteStepID', st).last().DeletedDate != null
        count('SELECT COUNT(*) FROM dbo.SolutionNoteVersion_LOG WHERE SolutionNoteID = ? AND DeletedDate IS NOT NULL', id) == 1
        !repo.delete(id)
    }

    def "saveStep should store every field and find should carry the steps when a step is added"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        int log = newLog(sid, 'ls', 'command')

        when:
        int id = repo.saveStep(step(note, [title: '  Look  ', command: 'ls -la', expected: 'files', notes: 'n', log: log]))
        NoteStep s = repo.steps(note)[0]

        then:
        s.id() == id
        s.noteId() == note
        s.stepNumber() == 1
        s.title() == 'Look'
        s.command() == 'ls -la'
        s.expectedResult() == 'files'
        s.notes() == 'n'
        s.sourceLogId() == log
        s.sourceSequenceNumber() == 1
        repo.find(note).steps()*.id() == [id]
        repo.find(note).stepCount() == 1
    }

    def "saveStep should insert at a position and shift the rest, appending when the number is 0 or past the end"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        repo.saveStep(step(note, [title: 'a']))
        repo.saveStep(step(note, [title: 'c', number: 99]))
        repo.saveStep(step(note, [title: 'b', number: 2]))
        repo.saveStep(step(note, [title: 'first', number: 1]))

        expect:
        repo.steps(note)*.title() == ['first', 'a', 'b', 'c']
        repo.steps(note)*.stepNumber() == [1, 2, 3, 4]
    }

    def "saveStep should change the text but not the number or source when a step is edited"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        int id = repo.saveStep(step(note, [title: 'old', command: 'x']))

        when:
        repo.saveStep(step(note, [id: id, title: 'new', expected: 'e', number: 5]))
        NoteStep s = repo.steps(note)[0]

        then:
        s.title() == 'new'
        s.command() == null
        s.expectedResult() == 'e'
        s.stepNumber() == 1
    }

    @Unroll
    def "saveStep should refuse when #scenario"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        int target = missingNote ? -1 : note

        when:
        repo.saveStep(step(target, [title: title]))

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                   | title  | missingNote || message
        "the title is blank"       | "  "   | false       || "needs a title"
        "the note does not exist"  | "Step" | true        || "note no longer exists"
    }

    @PendingFeature(reason = 'BUG: stp_SolutionNoteStep_Save accepts a SourceSolutionLogID from ANOTHER solution (stp_SolutionNoteStep_FromLog refuses that; SolutionNoteService.saveStep passes a new step\'s sourceLogId through unchecked), so a step can cite -- and its list reveal the sequence number of -- a log entry the caller may not see')
    def "saveStep should refuse when the cited log entry belongs to another solution"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        int foreignLog = newLog(newSolution(), 'secret')

        when:
        repo.saveStep(step(note, [log: foreignLog]))

        then:
        thrown(SolutionRuleException)
    }

    def "moveStep should re-slot a step and clamp the number, answering false when the step is unknown"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        repo.saveStep(step(note, [title: 'a']))
        int b = repo.saveStep(step(note, [title: 'b']))
        int c = repo.saveStep(step(note, [title: 'c']))

        expect:
        repo.moveStep(c, 1)
        repo.steps(note)*.title() == ['c', 'a', 'b']
        repo.moveStep(c, 99)
        repo.steps(note)*.title() == ['a', 'b', 'c']
        repo.moveStep(b, -3)
        repo.steps(note)*.title() == ['b', 'a', 'c']
        repo.steps(note)*.stepNumber() == [1, 2, 3]
        !repo.moveStep(-1, 1)
    }

    def "deleteStep should remove a step and close the gap, answering false when the step is gone"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        repo.saveStep(step(note, [title: 'a']))
        int b = repo.saveStep(step(note, [title: 'b']))
        repo.saveStep(step(note, [title: 'c']))

        expect:
        repo.deleteStep(b)
        repo.steps(note)*.title() == ['a', 'c']
        repo.steps(note)*.stepNumber() == [1, 2]
        !repo.deleteStep(b)
    }

    def "stepFromLog should append a step titled by the first line, a command in Command and the rest in Notes"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        int cmd = newLog(sid, 'restart it\r\nsc stop x', 'command')
        int other = newLog(sid, 'It worked', 'result')

        when:
        int s1 = repo.stepFromLog(note, cmd)
        int s2 = repo.stepFromLog(note, other)
        List<NoteStep> steps = repo.steps(note)

        then:
        steps*.id() == [s1, s2]
        steps[0].title() == 'restart it'
        steps[0].command() == 'restart it\r\nsc stop x'
        steps[0].notes() == null
        steps[0].sourceLogId() == cmd
        steps[1].title() == 'It worked'
        steps[1].command() == null
        steps[1].notes() == 'It worked'
        steps[1].stepNumber() == 2
    }

    @Unroll
    def "stepFromLog should refuse and add nothing when #scenario"() {

        given:
        int note = repo.save(draft(sid, [:]), 'tony')
        int foreign = newLog(newSolution(), 'x')
        int mine = newLog(sid, 'x')
        int noteId = noteKind == 'missing' ? -1 : note
        int logId = logKind == 'missing' ? -1 : (logKind == 'foreign' ? foreign : mine)

        when:
        repo.stepFromLog(noteId, logId)

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)
        repo.steps(note).isEmpty()

        where:
        scenario                                  | noteKind  | logKind   || message
        "the note does not exist"                 | "missing" | "mine"    || "note no longer exists"
        "the log entry does not exist"            | "mine"    | "missing" || "log entry no longer exists"
        "the log entry is from another solution"  | "mine"    | "foreign" || "different solutions"
    }

    def "save should write SolutionNote_LOG and SolutionNoteVersion_LOG rows when a note is added and its body changes"() {

        when:
        int id = repo.save(draft(sid, [body: 'a']), 'tony')
        repo.save(draft(sid, [id: id, body: 'b']), 'tony')

        then:
        logRows('SolutionNote', 'SolutionNoteID', id)[0].IsInsertion == true
        count('SELECT COUNT(*) FROM dbo.SolutionNoteVersion_LOG WHERE SolutionNoteID = ? AND IsInsertion = 1', id) == 2
    }

    private static NoteDraft draft(int solutionId, Map m) {
        new NoteDraft((m.id ?: 0) as int, solutionId, m.project as Integer, (m.type ?: 'runbook') as String,
            (m.containsKey('title') ? m.title : 'Note ' + uniq()) as String, (m.containsKey('body') ? m.body : 'Body v1') as String,
            m.change as String, (m.sort ?: 0) as int)
    }

    private static StepDraft step(int noteId, Map m) {
        new StepDraft((m.id ?: 0) as int, noteId, (m.number ?: 0) as int, (m.containsKey('title') ? m.title : 'Step ' + uniq()) as String,
            m.command as String, m.expected as String, m.notes as String, m.log as Integer)
    }
}
