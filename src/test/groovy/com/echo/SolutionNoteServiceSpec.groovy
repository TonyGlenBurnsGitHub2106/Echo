package com.echo

import com.echo.SolutionDrafts.NoteDraft
import com.echo.SolutionDrafts.StepDraft
import com.echo.SolutionModels.CodeName
import com.echo.SolutionModels.Lookups
import com.echo.SolutionModels.Note
import com.echo.SolutionModels.NoteStep
import com.echo.SolutionModels.NoteVersion
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.caller
import static com.echo.SolutionFixtures.intList
import static com.echo.SolutionFixtures.logEntry
import static com.echo.SolutionFixtures.lookups
import static com.echo.SolutionFixtures.note
import static com.echo.SolutionFixtures.solution
import static com.echo.SolutionFixtures.step
import static com.echo.SolutionFixtures.version

import spock.lang.Unroll

// Story 5653: SolutionNoteService -- notes, versions and runbook steps; a step id from a URL is trusted only once it is
// shown to belong to the note beside it.
class SolutionNoteServiceSpec extends EchoSpockBaseSpec {

    SolutionRepository repo = Mock()
    SolutionNoteRepository notes = Mock()
    SolutionLogRepository logRepo = Mock()
    ApiClientService clients = Mock()
    SolutionAccess access = new SolutionAccess(repo, clients)
    SolutionCodeCheck codes = new SolutionCodeCheck(repo)
    SolutionLogService log = new SolutionLogService(logRepo, access, codes)
    SolutionNoteService service = new SolutionNoteService(notes, log, access, codes, repo)

    def setup() {
        repo.lookups() >> lookups()
        repo.findSolution(1) >> solution(id: 1)
        repo.findSolution(2) >> solution(id: 2)
        clients.maySee(7, 1) >> true
    }

    // ---- note kinds order ---------------------------------------------------------------

    @Unroll
    def "reorderNoteTypes should reorder and return the kinds when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        List<Integer> ids = intList(idsCsv)

        when:
        List<CodeName> out = service.reorderNoteTypes(who, ids)

        then:
        1 * repo.reorderNoteTypes(intList(expectedCsv))
        out == lookups().noteTypes()

        where:
        scenario                         | callerName | idsCsv || expectedCsv
        "a person sends a new order"     | "person"   | "3;1"  || "3;1"
        "the house sends a new order"    | "house"    | "3;1"  || "3;1"
        "a person sends no list at all"  | "person"   | null   || ""
    }

    def "reorderNoteTypes should refuse an API client when it tries to reorder the shared kinds"() {

        when:
        service.reorderNoteTypes(AGENT, [1])

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Reordering the note kinds'
        0 * repo.reorderNoteTypes(_)
    }

    // ---- list / get ---------------------------------------------------------------------

    @Unroll
    def "list should read the notes of a visible solution when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        List<Note> out = service.list(who, 1, typeCode)

        then:
        1 * notes.listForSolution(1, typeCode) >> []
        out.size() == expectedCount

        where:
        scenario                         | callerName | typeCode  || expectedCount
        "the agent asks for runbooks"    | "agent"    | "runbook" || 0
        "a person asks for every kind"   | "person"   | null      || 0
    }

    def "list should answer not found when the agent was not given the solution"() {

        when:
        service.list(AGENT, 2, null)

        then:
        thrown(SolutionNotFoundException)
        0 * notes.listForSolution(*_)
    }

    @Unroll
    def "get should answer #expectedOutcome when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.find(301) >> note(id: 301, solutionId: 2)

        when:
        String outcome
        try {
            outcome = 'note ' + service.get(who, id).id()
        } catch (SolutionNotFoundException ex) {
            outcome = ex.message
        }

        then:
        outcome == expectedOutcome

        where:
        scenario                                  | callerName | id  || expectedOutcome
        "the agent reads a note it may see"       | "agent"    | 300 || "note 300"
        "the id is zero"                          | "person"   | 0   || "Note not found."
        "the note does not exist"                 | "person"   | 999 || "Note not found."
        "the agent reads another solution's note" | "agent"    | 301 || "Solution not found."
    }

    def "get should not look up id 0 when asked for it"() {

        when:
        service.get(PERSON, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * notes.find(0)
    }

    // ---- save ---------------------------------------------------------------------------

    def "save should check the solution, save and read back when the note is new"() {

        given:
        NoteDraft d = new NoteDraft(0, 1, null, 'runbook', 'How', 'body', 'first', 0)

        when:
        Note out = service.save(AGENT, d)

        then:
        1 * notes.save(d, 'rex') >> 300
        1 * notes.find(300) >> note(id: 300)
        out.id() == 300
    }

    @Unroll
    def "save should answer not found and save nothing when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        service.save(who, new NoteDraft(0, solutionId, null, null, 'T', null, null, 0))

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * notes.save(*_)

        where:
        scenario                               | callerName | solutionId || expectedError
        "the agent was not given the solution" | "agent"    | 2          || "SolutionNotFoundException"
        "the solution id is zero"              | "person"   | 0          || "SolutionNotFoundException"
    }

    def "save should refuse an unknown note type and turn a kind name into its code when saving"() {

        when:
        service.save(PERSON, new NoteDraft(0, 1, null, 'diary', 'T', null, null, 0))

        then:
        thrown(SolutionRuleException)
        0 * notes.save(*_)

        when:
        service.save(PERSON, new NoteDraft(0, 1, null, null, 'T', null, null, 0), 'Strategy')

        then:
        1 * notes.save(new NoteDraft(0, 1, null, 'strategy', 'T', null, null, 0), 'tony') >> 300
    }

    def "save should let a person mint a new note kind by name and refuse an agent the same"() {

        given:
        Lookups base = lookups()
        Lookups withNew = new Lookups(base.statuses(), base.members(), base.logTypes(),
            base.noteTypes() + [new CodeName(9, 'checklist', 'Checklist', 4)])
        SolutionRepository repo2 = Mock()
        repo2.findSolution(1) >> solution(id: 1)
        SolutionNoteService svc = new SolutionNoteService(notes, log, new SolutionAccess(repo2, clients), new SolutionCodeCheck(repo2), repo2)

        when:
        svc.save(PERSON, new NoteDraft(0, 1, null, null, 'T', null, null, 0), 'Checklist')

        then:
        repo2.lookups() >>> [base, withNew, withNew]
        1 * repo2.findOrCreateNoteType('Checklist') >> 9
        1 * notes.save({ it.typeCode() == 'checklist' }, 'tony') >> 300

        when:
        service.save(AGENT, new NoteDraft(0, 1, null, null, 'T', null, null, 0), 'Checklist')

        then:
        SolutionRuleException ex = thrown()
        ex.message.startsWith("There is no note kind named 'Checklist'.")
        0 * repo.findOrCreateNoteType(_)
    }

    @Unroll
    def "save should merge the update when #scenario"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1, projectId: 10, typeCode: 'strategy', title: 'Old', body: 'B', sortOrder: 4)
        NoteDraft sent = new NoteDraft(300, sentSolution, projectId, typeCode, title, body, changeNote, sortOrder)
        NoteDraft expected = new NoteDraft(300, 1, expectedProject, 'strategy', 'Old', expectedBody, changeNote, sortOrder)

        when:
        service.save(PERSON, sent)

        then:
        1 * notes.save(expected, 'tony') >> 300

        where:
        scenario                                   | sentSolution | projectId | typeCode | title | body | changeNote | sortOrder || expectedProject | expectedBody
        "nothing is sent but a change note"        | 999          | null      | null     | null  | null | "why"      | 0         || 10              | "B"
        "blanks are sent and a new project"        | 0            | 11        | " "      | ""    | ""   | null       | 2         || 11              | ""
    }

    def "save should answer not found when the note is hidden or storage returns no id"() {

        given:
        notes.find(301) >> note(id: 301, solutionId: 2)

        when:
        service.save(AGENT, new NoteDraft(301, 0, null, null, 'x', null, null, 0))

        then:
        thrown(SolutionNotFoundException)

        when:
        service.save(PERSON, new NoteDraft(0, 1, null, null, 'T', null, null, 0))

        then:
        1 * notes.save(*_) >> 0
        thrown(SolutionNotFoundException)
    }

    // ---- delete and versions ------------------------------------------------------------

    def "delete should remove inside the caller's solution and answer not found when hidden, missing or refused"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.find(301) >> note(id: 301, solutionId: 2)

        when:
        service.delete(AGENT, 300)

        then:
        1 * notes.delete(300) >> true

        when:
        service.delete(AGENT, 301)

        then:
        thrown(SolutionNotFoundException)
        0 * notes.delete(_)

        when:
        service.delete(PERSON, 300)

        then:
        1 * notes.delete(300) >> false
        thrown(SolutionNotFoundException)
    }

    def "versions and version should read behind the note's own check when the note is visible, missing or hidden"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.find(301) >> note(id: 301, solutionId: 2)

        when:
        List<NoteVersion> all = service.versions(AGENT, 300)
        NoteVersion one = service.version(AGENT, 300, 2)

        then:
        1 * notes.versions(300) >> [version(), version(versionNumber: 2)]
        1 * notes.version(300, 2) >> version(versionNumber: 2)
        all.size() == 2
        one.versionNumber() == 2

        when:
        service.version(PERSON, 300, 9)

        then:
        1 * notes.version(300, 9) >> null
        SolutionNotFoundException e = thrown()
        e.message == 'Version not found.'

        when:
        service.versions(AGENT, 301)

        then:
        thrown(SolutionNotFoundException)
        0 * notes.versions(_)

        when:
        service.version(AGENT, 301, 1)

        then:
        thrown(SolutionNotFoundException)
        0 * notes.version(*_)
    }

    // ---- steps --------------------------------------------------------------------------

    def "saveStep should save a new step as sent and answer the whole note when the step is new"() {

        given:
        StepDraft d = new StepDraft(0, 300, 0, 'Stop', 'sc stop', 'STOPPED', null, null)

        when:
        Note out = service.saveStep(AGENT, d)

        then:
        notes.find(300) >> note(id: 300, solutionId: 1)
        1 * notes.saveStep(d) >> 31
        out.id() == 300
        0 * notes.steps(_)
    }

    @Unroll
    def "saveStep should merge against the current step, keeping its source log entry, when #scenario"() {

        given:
        NoteStep current = step(id: 31, noteId: 300, stepNumber: 2, title: 'Old', command: 'c', expectedResult: 'e', notes: 'n',
            sourceLogId: 200)
        notes.find(300) >> note(id: 300, solutionId: 1, steps: [step(id: 30), current])
        notes.steps(300) >> [step(id: 30), current]
        StepDraft sent = new StepDraft(31, 300, stepNumber, title, command, expectedResult, stepNotes, sentSource)
        StepDraft expected = new StepDraft(31, 300, stepNumber, 'Old', expectedCommand, expectedExpected, expectedNotes, 200)

        when:
        service.saveStep(PERSON, sent)

        then:
        1 * notes.saveStep(expected) >> 31

        where:
        scenario                         | stepNumber | title | command | expectedResult | stepNotes | sentSource || expectedCommand | expectedExpected | expectedNotes
        "nothing is sent"                | 0          | null  | null    | null           | null      | 999        || "c"             | "e"              | "n"
        "blanks are sent with a number"  | 3          | " "   | ""      | ""             | ""        | null       || ""              | ""               | ""
    }

    def "saveStep should answer step not found, even if the step exists elsewhere, when it is not the note's"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1, steps: [step(id: 30)])
        notes.steps(300) >> [step(id: 30)]

        when:
        service.saveStep(PERSON, new StepDraft(77, 300, 0, 'x', null, null, null, null))

        then:
        SolutionNotFoundException e = thrown()
        e.message == 'Step not found.'
        0 * notes.saveStep(_)
    }

    def "saveStep should answer not found when the note is hidden or storage returns no id"() {

        given:
        notes.find(301) >> note(id: 301, solutionId: 2)
        notes.find(300) >> note(id: 300, solutionId: 1)

        when:
        service.saveStep(AGENT, new StepDraft(0, 301, 0, 'x', null, null, null, null))

        then:
        thrown(SolutionNotFoundException)
        0 * notes.saveStep(_)

        when:
        service.saveStep(PERSON, new StepDraft(0, 300, 0, 'x', null, null, null, null))

        then:
        1 * notes.saveStep(_) >> 0
        thrown(SolutionNotFoundException)
    }

    def "moveStep should move the step and answer the note when the step is the note's"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.steps(300) >> [step(id: 31)]

        when:
        Note out = service.moveStep(AGENT, 300, 31, 1)

        then:
        1 * notes.moveStep(31, 1) >> true
        out.id() == 300
    }

    def "moveStep should answer not found when the step is foreign, the note hidden or the move refused"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.find(301) >> note(id: 301, solutionId: 2)
        notes.steps(300) >> [step(id: 31)]

        when:
        service.moveStep(PERSON, 300, 32, 1)

        then:
        thrown(SolutionNotFoundException)

        when:
        service.moveStep(AGENT, 301, 31, 1)

        then:
        thrown(SolutionNotFoundException)

        when:
        service.moveStep(PERSON, 300, 31, 1)

        then:
        1 * notes.moveStep(31, 1) >> false
        thrown(SolutionNotFoundException)
    }

    def "deleteStep should delete and answer the note, and answer not found when the step is foreign or refused"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.steps(300) >> [step(id: 31)]

        when:
        Note out = service.deleteStep(AGENT, 300, 31)

        then:
        1 * notes.deleteStep(31) >> true
        out.id() == 300

        when:
        service.deleteStep(PERSON, 300, 99)

        then:
        thrown(SolutionNotFoundException)
        0 * notes.deleteStep(_)

        when:
        service.deleteStep(PERSON, 300, 31)

        then:
        1 * notes.deleteStep(31) >> false
        thrown(SolutionNotFoundException)
    }

    // ---- step from log ------------------------------------------------------------------

    def "stepFromLog should make a step and answer the note when the log entry is of the same solution"() {

        given:
        notes.find(300) >> note(id: 300, solutionId: 1)
        logRepo.find(200) >> logEntry(id: 200, solutionId: 1)

        when:
        Note out = service.stepFromLog(AGENT, 300, 200)

        then:
        1 * notes.stepFromLog(300, 200) >> 33
        out.id() == 300
    }

    @Unroll
    def "stepFromLog should refuse with #expectedError when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        notes.find(300) >> note(id: 300, solutionId: 1)
        notes.find(301) >> note(id: 301, solutionId: 2)
        logRepo.find(201) >> logEntry(id: 201, solutionId: 2)

        when:
        service.stepFromLog(who, noteId, logId)

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        ex.message == expectedMessage
        0 * notes.stepFromLog(*_)

        where:
        scenario                                         | callerName | noteId | logId || expectedError               | expectedMessage
        "a person names another solution's log entry"    | "person"   | 300    | 201   || "SolutionRuleException"     | "The log entry and the note belong to different solutions."
        "the agent names a log entry it may not see"     | "agent"    | 300    | 201   || "SolutionNotFoundException" | "Solution not found."
        "the log entry does not exist"                   | "person"   | 300    | 999   || "SolutionNotFoundException" | "Log entry not found."
        "the agent names a hidden note"                  | "agent"    | 301    | 200   || "SolutionNotFoundException" | "Solution not found."
    }
}
