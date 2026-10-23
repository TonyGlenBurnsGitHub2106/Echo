package com.echo

import com.echo.SolutionModels.CodeName
import com.echo.SolutionModels.Lookups
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.HOUSE
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.caller
import static com.echo.SolutionFixtures.lookups

import spock.lang.Unroll

// Story 5653: SolutionCodeCheck -- a code nobody defined is a 409 that lists the right ones, never a quiet default.
class SolutionCodeCheckSpec extends EchoSpockBaseSpec {

    SolutionRepository repo = Mock()
    SolutionCodeCheck check = new SolutionCodeCheck(repo)

    def setup() {
        repo.lookups() >> lookups()
    }

    @Unroll
    def "#seq. status should answer accepted #expectedAccepted when #scenario"() {

        when:
        boolean accepted = accepts { check.status(code) }

        then:
        accepted == expectedAccepted

        where:
        seq | scenario             | code          || expectedAccepted
        1   | "it is a known code" | "todo"        || true
        2   | "it is upper case"   | "DONE"        || true
        3   | "it is padded"       | "  blocked  " || true
        4   | "it is mixed case"   | "InProgress"  || true
        5   | "it is null"         | null          || true
        6   | "it is empty"        | ""            || true
        7   | "it is spaces"       | "   "         || true
        8   | "it is not defined"  | "finished"    || false
    }

    def "status should refuse an unknown code naming every status and the lookups path when the code is not defined"() {

        when:
        check.status(' finished ')

        then:
        SolutionRuleException ex = thrown()
        ex.message == "There is no status code 'finished'. The status codes are: todo, inprogress, blocked, done, cancelled. " +
            'Nothing was saved. GET /api/solutions/lookups lists every code.'
    }

    @Unroll
    def "logType and noteType should accept the code when #scenario"() {

        when:
        boolean accepted = accepts { check.logType(logCode) } && accepts { check.noteType(noteCode) }

        then:
        accepted == expectedAccepted

        where:
        scenario                 | logCode   | noteCode  || expectedAccepted
        "both codes are known"   | "command" | "Runbook" || true
        "both codes are blank"   | null      | ""        || true
    }

    @Unroll
    def "#method should refuse a code from the other list when #scenario"() {

        when:
        if (method == 'logType') {
            check.logType(code)
        } else {
            check.noteType(code)
        }

        then:
        SolutionRuleException ex = thrown()
        ex.message.startsWith(expectedStart)

        where:
        scenario                         | method     | code      || expectedStart
        "a note code is sent as a log"   | "logType"  | "runbook" || "There is no log type code 'runbook'. The log type codes are: note, command, result, decision, blocker, question."
        "a log code is sent as a note"   | "noteType" | "command" || "There is no note type code 'command'. The note type codes are: runbook, strategy, reference."
    }

    @Unroll
    def "member should accept the code for a person without saving a member when #scenario"() {

        when:
        boolean accepted = accepts { check.member(PERSON, code) }

        then:
        accepted == expectedAccepted
        0 * repo.saveMember(*_)

        where:
        scenario                | code      || expectedAccepted
        "it is an active member" | "tony"   || true
        "it is upper case"      | "FELIX"   || true
        "it is padded"          | " felix " || true
        "it is null"            | null      || true
        "it is empty"           | ""        || true
        "it is spaces"          | "  "      || true
    }

    def "member should refuse an inactive member when the code names one"() {

        when:
        check.member(PERSON, 'old')

        then:
        SolutionRuleException ex = thrown()
        ex.message == "There is no member code 'old'. The member codes are: tony, felix. Nothing was saved. GET /api/solutions/lookups lists every code."
    }

    @Unroll
    def "member should refuse an unknown member when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        check.member(who, 'bob')

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * repo.saveMember(*_)

        where:
        scenario                 | callerName || expectedError
        "the caller is a person" | "person"   || "SolutionRuleException"
        "the caller is the house" | "house"   || "SolutionRuleException"
        "there is no caller"     | "none"     || "SolutionRuleException"
    }

    def "member should refuse the house naming itself when it is not a member"() {

        given:
        SolutionCaller stewart = new SolutionCaller(SolutionCaller.Kind.SERVICE, 'stewart', null)

        when:
        check.member(stewart, 'stewart')

        then:
        thrown(SolutionRuleException)
    }

    def "member should make an API client's own member row, capitalized, as an agent, last in order when it names itself"() {

        when:
        check.member(AGENT, ' REX ')

        then:
        1 * repo.saveMember(0, 'rex', 'Rex', true, true, 3)
        noExceptionThrown()
    }

    def "member should refuse an API client when it names someone unknown"() {

        when:
        check.member(AGENT, 'bob')

        then:
        thrown(SolutionRuleException)
        0 * repo.saveMember(*_)
    }

    // ---- kind names (the Kind picker) ----------------------------------------------------

    @Unroll
    def "noteTypeCode and logTypeCode should leave the code as sent when #scenario"() {

        expect:
        check.noteTypeCode(PERSON, 'runbook', name) == expectedNote
        check.logTypeCode(AGENT, 'command', name) == expectedLog
        check.noteTypeCode(AGENT, null, name) == null

        where:
        scenario              | name  || expectedNote | expectedLog
        "the name is null"    | null  || "runbook"    | "command"
        "the name is empty"   | ""    || "runbook"    | "command"
        "the name is spaces"  | "   " || "runbook"    | "command"
    }

    @Unroll
    def "noteTypeCode should find the existing kind when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        expect:
        check.noteTypeCode(who, 'ignored', name) == expectedCode

        where:
        scenario                                | callerName | name          || expectedCode
        "a person names it"                     | "person"   | "Strategy"    || "strategy"
        "an agent names it in caps with spaces" | "agent"    | " REFERENCE " || "reference"
        "the house sends the code itself"       | "house"    | "runbook"     || "runbook"
    }

    @Unroll
    def "logTypeCode should find the existing kind for anyone when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        expect:
        check.logTypeCode(who, code, name) == expectedCode

        where:
        scenario                         | callerName | code   | name       || expectedCode
        "an agent names a kind"          | "agent"    | null   | "Decision" || "decision"
        "a person names over a code"     | "person"   | "note" | "blocker"  || "blocker"
    }

    def "noteTypeCode should make a new note kind and return its code when a person names one"() {

        given:
        Lookups base = lookups()
        Lookups withNew = new Lookups(base.statuses(), base.members(), base.logTypes(),
            base.noteTypes() + [new CodeName(9, 'checklist', 'Checklist', 4)])
        SolutionRepository repo2 = Mock()
        SolutionCodeCheck check2 = new SolutionCodeCheck(repo2)

        when:
        String code = check2.noteTypeCode(PERSON, null, 'Checklist')

        then:
        repo2.lookups() >>> [base, withNew]
        1 * repo2.findOrCreateNoteType('Checklist') >> 9
        code == 'checklist'
    }

    def "logTypeCode should make a new log kind and return its code when the house names one"() {

        given:
        Lookups base = lookups()
        Lookups withNew = new Lookups(base.statuses(), base.members(),
            base.logTypes() + [new CodeName(9, 'deploy', 'Deploy', 7)], base.noteTypes())
        SolutionRepository repo2 = Mock()
        SolutionCodeCheck check2 = new SolutionCodeCheck(repo2)

        when:
        String code = check2.logTypeCode(HOUSE, 'note', 'Deploy')

        then:
        repo2.lookups() >>> [base, withNew]
        1 * repo2.findOrCreateLogType('Deploy') >> 9
        code == 'deploy'
    }

    def "noteTypeCode and logTypeCode should refuse, saving nothing, when the new kind never shows up in the lookups"() {

        when:
        check.noteTypeCode(PERSON, null, 'Ghost')

        then:
        1 * repo.findOrCreateNoteType('Ghost') >> 99
        SolutionRuleException ex = thrown()
        ex.message == "The kind 'Ghost' could not be added. Nothing was saved."

        when:
        check.logTypeCode(PERSON, null, 'Ghost')

        then:
        1 * repo.findOrCreateLogType('Ghost') >> 99
        thrown(SolutionRuleException)
    }

    @Unroll
    def "noteTypeCode should never mint a kind and should list the kind names when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        check.noteTypeCode(who, null, ' Checklist ')

        then:
        0 * repo.findOrCreateNoteType(_)
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                | callerName || expectedMessage
        "the caller is an agent" | "agent"   || "There is no note kind named 'Checklist'. The note kinds are: Runbook, Strategy, Reference. Nothing was saved."
        "there is no caller"    | "none"     || "There is no note kind named 'Checklist'. The note kinds are: Runbook, Strategy, Reference. Nothing was saved."
    }

    def "logTypeCode should never mint a kind when an API client names a new one"() {

        when:
        check.logTypeCode(AGENT, null, 'Deploy')

        then:
        0 * repo.findOrCreateLogType(_)
        SolutionRuleException ex = thrown()
        ex.message.startsWith("There is no log kind named 'Deploy'. The log kinds are: Note, Command, Result, Decision, Blocker, Question.")
    }

    /** True when the check lets the code through, false when a rule refuses it. */
    private static boolean accepts(Closure<?> check) {
        try {
            check.call()
            return true
        } catch (SolutionRuleException ignored) {
            return false
        }
    }
}
