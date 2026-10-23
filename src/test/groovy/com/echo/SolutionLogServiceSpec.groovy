package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionDrafts.LogFilter
import com.echo.SolutionModels.CodeName
import com.echo.SolutionModels.LogEntry
import com.echo.SolutionModels.LogPage
import com.echo.SolutionModels.Lookups
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.caller
import static com.echo.SolutionFixtures.logEntry
import static com.echo.SolutionFixtures.lookups
import static com.echo.SolutionFixtures.solution

import spock.lang.Unroll

// Story 5653: SolutionLogService -- the journal: paged under one filter, appended, corrected (a merge), deleted.
class SolutionLogServiceSpec extends EchoSpockBaseSpec {

    private static final LocalDateTime WHEN = getLocalDateTime('09/17/2026 14:30')

    SolutionRepository repo = Mock()
    SolutionLogRepository log = Mock()
    ApiClientService clients = Mock()
    SolutionLogService service = new SolutionLogService(log, new SolutionAccess(repo, clients), new SolutionCodeCheck(repo))

    def setup() {
        repo.lookups() >> lookups()
        repo.findSolution(1) >> solution(id: 1)
        repo.findSolution(2) >> solution(id: 2)
        clients.maySee(7, 1) >> true
    }

    // ---- page ---------------------------------------------------------------------------

    def "page should carry the entries, the counts and the total, asked under the same filter, when the solution is visible"() {

        given:
        LogFilter f = filter(1, 10, 20)

        when:
        LogPage page = service.page(AGENT, f)

        then:
        1 * log.counts(f) >> [total: 33, 'type:note': 30]
        1 * log.list(f) >> [logEntry()]
        page.entries().size() == 1
        page.counts() == [total: 33, 'type:note': 30]
        page.offset() == 10
        page.pageSize() == 20
        page.total() == 33
    }

    def "page should report a total of 0 when the counts carry no total"() {

        when:
        LogPage page = service.page(PERSON, filter(1, 0, 0))

        then:
        1 * log.counts(_) >> [:]
        1 * log.list(_) >> []
        page.total() == 0
        page.pageSize() == 50
    }

    @Unroll
    def "page should answer not found and read nothing when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        service.page(who, filter(solutionId, 0, 0))

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * log.list(_)
        0 * log.counts(_)

        where:
        scenario                                 | callerName | solutionId || expectedError
        "the agent was not given the solution"   | "agent"    | 2          || "SolutionNotFoundException"
        "the solution does not exist"            | "person"   | 9          || "SolutionNotFoundException"
        "the solution id is zero"                | "person"   | 0          || "SolutionNotFoundException"
    }

    // ---- get ----------------------------------------------------------------------------

    @Unroll
    def "get should answer #expectedOutcome when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        log.find(200) >> logEntry(id: 200, solutionId: 1)
        log.find(201) >> logEntry(id: 201, solutionId: 2)

        when:
        String outcome
        try {
            outcome = 'entry ' + service.get(who, id).id()
        } catch (SolutionNotFoundException ex) {
            outcome = ex.message
        }

        then:
        outcome == expectedOutcome

        where:
        scenario                                    | callerName | id  || expectedOutcome
        "the agent reads an entry it may see"       | "agent"    | 200 || "entry 200"
        "the id is zero"                            | "person"   | 0   || "Log entry not found."
        "the entry does not exist"                  | "person"   | 999 || "Log entry not found."
        "the agent reads another solution's entry"  | "agent"    | 201 || "Solution not found."
    }

    def "get should not look up id 0 when asked for it"() {

        when:
        service.get(PERSON, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * log.find(0)
    }

    // ---- append -------------------------------------------------------------------------

    def "save should check the solution, save with the author and read back when appending"() {

        given:
        LogDraft d = new LogDraft(0, 1, 10, 100, 'command', 'mvn verify', WHEN)

        when:
        LogEntry out = service.save(AGENT, d)

        then:
        1 * log.save(d, 'rex') >> 200
        1 * log.find(200) >> logEntry(id: 200)
        out.id() == 200
    }

    @Unroll
    def "save should answer not found and save nothing when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        service.save(who, new LogDraft(0, solutionId, null, null, null, 'x', null))

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * log.save(*_)

        where:
        scenario                               | callerName | solutionId || expectedError
        "the agent was not given the solution" | "agent"    | 2          || "SolutionNotFoundException"
        "the solution does not exist"          | "person"   | 9          || "SolutionNotFoundException"
    }

    def "save should refuse an unknown type code and accept a blank one as the default when appending"() {

        when:
        service.save(PERSON, new LogDraft(0, 1, null, null, 'diary', 'x', null))

        then:
        SolutionRuleException ex = thrown()
        ex.message.startsWith("There is no log type code 'diary'.")
        0 * log.save(*_)

        when:
        service.save(PERSON, new LogDraft(0, 1, null, null, '', 'x', null))

        then:
        1 * log.save(_, 'tony') >> 200
    }

    def "save should turn a kind name into its code before saving when the kind exists"() {

        when:
        service.save(AGENT, new LogDraft(0, 1, null, null, null, 'x', WHEN), 'Decision')

        then:
        1 * log.save(new LogDraft(0, 1, null, null, 'decision', 'x', WHEN), 'rex') >> 200
    }

    def "save should leave the draft as it is when the kind name equals the code"() {

        given:
        LogDraft d = new LogDraft(0, 1, null, null, 'result', 'x', null)

        when:
        service.save(PERSON, d, 'result')

        then:
        1 * log.save({ it.is(d) }, 'tony') >> 200
    }

    def "save should let a person name a new kind and refuse an agent the same when the kind is new"() {

        given:
        Lookups base = lookups()
        Lookups withNew = new Lookups(base.statuses(), base.members(),
            base.logTypes() + [new CodeName(9, 'deploy', 'Deploy', 7)], base.noteTypes())
        SolutionRepository repo2 = Mock()
        repo2.findSolution(1) >> solution(id: 1)
        SolutionLogService svc = new SolutionLogService(log, new SolutionAccess(repo2, clients), new SolutionCodeCheck(repo2))

        when:
        svc.save(PERSON, new LogDraft(0, 1, null, null, null, 'x', null), 'Deploy')

        then:
        repo2.lookups() >>> [base, withNew, withNew]
        1 * repo2.findOrCreateLogType('Deploy') >> 9
        1 * log.save({ it.typeCode() == 'deploy' }, 'tony') >> 200

        when:
        service.save(AGENT, new LogDraft(0, 1, null, null, null, 'x', null), 'Deploy')

        then:
        thrown(SolutionRuleException)
        0 * log.save(*_)
    }

    def "save should answer not found when storage returns no id"() {

        when:
        service.save(PERSON, new LogDraft(0, 1, null, null, null, 'x', null))

        then:
        1 * log.save(*_) >> 0
        thrown(SolutionNotFoundException)
    }

    // ---- correct (a merge) ----------------------------------------------------------------

    def "save should keep what is not sent, keep the solution and never move the date when correcting"() {

        given:
        log.find(200) >> logEntry(id: 200, solutionId: 1, projectId: 10, itemId: 100, typeCode: 'command', text: 'old')

        when:
        service.save(PERSON, new LogDraft(200, 999, null, null, null, null, WHEN))

        then:
        1 * log.save(new LogDraft(200, 1, 10, 100, 'command', 'old', null), 'tony') >> 200
    }

    @Unroll
    def "save should merge the correction when #scenario"() {

        given:
        log.find(200) >> logEntry(id: 200, solutionId: 1, projectId: 10, itemId: 100, typeCode: 'command', text: 'old')
        LogDraft expected = new LogDraft(200, 1, expectedProject, expectedItem, expectedType, expectedText, null)

        when:
        service.save(PERSON, new LogDraft(200, 0, projectId, itemId, typeCode, text, null))

        then:
        1 * log.save(expected, 'tony') >> 200

        where:
        scenario                                  | projectId | itemId | typeCode | text  || expectedProject | expectedItem | expectedType | expectedText
        "blanks keep, zero ids pass to the proc"  | 0         | 0      | "  "     | ""    || 0               | 0            | "command"    | "old"
        "every field is sent"                     | 11        | 101    | "result" | "new" || 11              | 101          | "result"     | "new"
    }

    @Unroll
    def "save should let the agent correct only inside its solution when #scenario"() {

        given:
        log.find(200) >> logEntry(id: 200, solutionId: 1)
        log.find(201) >> logEntry(id: 201, solutionId: 2)

        when:
        boolean saved
        try {
            service.save(AGENT, new LogDraft(id, 0, null, null, null, 'fixed', null))
            saved = true
        } catch (SolutionNotFoundException ignored) {
            saved = false
        }

        then:
        (expectedSaved ? 1 : 0) * log.save(_, 'rex') >> 200
        saved == expectedSaved

        where:
        scenario                          | id  || expectedSaved
        "the entry is in its solution"    | 200 || true
        "the entry is in another one"     | 201 || false
    }

    // ---- delete -------------------------------------------------------------------------

    @Unroll
    def "delete should remove the entry when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        log.find(200) >> logEntry(id: 200, solutionId: 1)

        when:
        service.delete(who, 200)

        then:
        1 * log.delete(200) >> expectedDeleted

        where:
        scenario                               | callerName || expectedDeleted
        "a person deletes"                     | "person"   || true
        "the agent deletes inside its solution" | "agent"   || true
    }

    def "delete should answer not found when the entry is hidden, missing or refused by storage"() {

        given:
        log.find(200) >> logEntry(id: 200, solutionId: 1)
        log.find(201) >> logEntry(id: 201, solutionId: 2)

        when:
        service.delete(AGENT, 201)

        then:
        thrown(SolutionNotFoundException)

        when:
        service.delete(PERSON, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * log.delete(_)

        when:
        service.delete(PERSON, 200)

        then:
        1 * log.delete(200) >> false
        thrown(SolutionNotFoundException)
    }

    private static LogFilter filter(int solutionId, int offset, int pageSize) {
        new LogFilter(solutionId, null, null, null, null, null, null, false, offset, pageSize)
    }
}
