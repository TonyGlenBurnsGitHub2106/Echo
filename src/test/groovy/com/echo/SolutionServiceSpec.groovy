package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDrafts.ProjectDraft
import com.echo.SolutionDrafts.SolutionDraft
import com.echo.SolutionModels.Lookups
import com.echo.SolutionModels.Project
import com.echo.SolutionModels.Solution
import static com.echo.SolutionFixtures.AGENT
import static com.echo.SolutionFixtures.HOUSE
import static com.echo.SolutionFixtures.PERSON
import static com.echo.SolutionFixtures.caller
import static com.echo.SolutionFixtures.lookups
import static com.echo.SolutionFixtures.project
import static com.echo.SolutionFixtures.record
import static com.echo.SolutionFixtures.solution

import spock.lang.PendingFeature
import spock.lang.Unroll

// Story 5653: SolutionService -- solutions and projects, each call fenced by what the caller may see and do.
// The real SolutionAccess and SolutionCodeCheck run against mocked storage.
class SolutionServiceSpec extends EchoSpockBaseSpec {

    private static final LocalDateTime WHEN = getLocalDateTime('10/01/2026 00:00')

    SolutionRepository repo = Mock()
    ApiClientService clients = Mock()
    SolutionService service = new SolutionService(repo, new SolutionAccess(repo, clients), new SolutionCodeCheck(repo))

    def setup() {
        repo.lookups() >> lookups()
    }

    // ---- reading ------------------------------------------------------------------------

    def "lookups should come straight from storage when asked"() {

        expect:
        service.lookups() == lookups()
    }

    @Unroll
    def "list should ask storage for the caller's view when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        List<Solution> out = service.list(who, includeArchived)

        then:
        1 * repo.listSolutions(includeArchived, expectedClientId) >> []
        out == []

        where:
        scenario                              | callerName | includeArchived || expectedClientId
        "a person asks for everything"        | "person"   | true            || null
        "the agent asks for its own"          | "agent"    | false           || 7
        "the house asks without the archived" | "house"    | false           || null
    }

    @Unroll
    def "get should answer the solution when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        Solution s = solution(id: 4)
        repo.findSolution(4) >> s
        clients.maySee(7, 4) >> true

        when:
        Solution out = service.get(who, 4)

        then:
        out.is(s) == expectedSame

        where:
        scenario                          | callerName || expectedSame
        "a person asks"                   | "person"   || true
        "the house asks"                  | "house"    || true
        "the agent asks for a shared one" | "agent"    || true
    }

    @Unroll
    def "get should answer Solution not found when #scenario"() {

        when:
        service.get(PERSON, id)

        then:
        (expectedLookup ? 1 : 0) * repo.findSolution(id) >> null
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'

        where:
        scenario                  | id || expectedLookup
        "the id is zero"          | 0  || false
        "the id is negative"      | -1 || false
        "the solution is missing" | 99 || true
    }

    def "get should answer not found, never forbidden, when the solution is not shared with the client"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        clients.maySee(7, 4) >> false

        when:
        service.get(AGENT, 4)

        then:
        thrown(SolutionNotFoundException)
    }

    // ---- create -------------------------------------------------------------------------

    def "save should fill defaults, normalize the alias and record the author when a person creates a solution"() {

        given:
        SolutionDraft sent = draft([name: 'Claims', statusCode: 'todo', ownerCode: 'felix', alias: ' Claims-2 ', targetDate: WHEN, sortOrder: 3])

        when:
        Solution out = service.save(PERSON, sent)

        then:
        1 * repo.saveSolution({ SolutionDraft d ->
            d.id() == 0 && d.name() == 'Claims' && d.ownerCode() == 'felix' && d.alias() == 'claims-2' &&
                d.archived() == false && d.targetDate() == WHEN && d.sortOrder() == 3
        }, 'tony') >> 12
        1 * repo.findSolution(12) >> solution(id: 12)
        out.id() == 12
    }

    @Unroll
    def "save should send alias '#expectedAlias' and archived #expectedArchived when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        service.save(who, draft([archived: archived]))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.alias() == expectedAlias && d.archived() == expectedArchived }, expectedAuthor) >> 1
        1 * repo.findSolution(1) >> solution()

        where:
        scenario                          | callerName | archived || expectedAlias | expectedArchived | expectedAuthor
        "the house creates with no flags" | "house"    | null     || ""            | false            | "felix"
        "a person creates it archived"    | "person"   | true     || ""            | true             | "tony"
    }

    def "save should refuse an API client when it tries to create a solution"() {

        when:
        service.save(AGENT, draft([:]))

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may create a solution.'
        0 * repo.saveSolution(*_)
    }

    @Unroll
    def "save should refuse before saving when #scenario"() {

        when:
        service.save(PERSON, draft([statusCode: statusCode, ownerCode: ownerCode]))

        then:
        SolutionRuleException ex = thrown()
        ex.message.startsWith(expectedStart)
        0 * repo.saveSolution(*_)

        where:
        scenario                | statusCode | ownerCode || expectedStart
        "the status is unknown" | "finished" | null      || "There is no status code 'finished'."
        "the owner is unknown"  | null       | "bob"     || "There is no member code 'bob'."
        "the owner is inactive" | null       | "old"     || "There is no member code 'old'."
    }

    def "save should answer not found and read nothing when storage returns no id"() {

        when:
        service.save(PERSON, draft([:]))

        then:
        1 * repo.saveSolution(_, 'tony') >> 0
        thrown(SolutionNotFoundException)
        0 * repo.findSolution(_)
    }

    // ---- update (a merge) ----------------------------------------------------------------

    def "save should keep every other field when an update sends only a name"() {

        given:
        repo.findSolution(4) >> solution(id: 4, name: 'Old', description: 'D', statusCode: 'blocked', ownerCode: 'felix',
            statusSummary: 'S', targetDate: WHEN, archived: true, sortOrder: 6, alias: 'old-one')

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, name: 'Renamed']))

        then:
        1 * repo.saveSolution(new SolutionDraft(4, 'Renamed', 'D', 'blocked', 'felix', 'S', WHEN, true, 6, 'old-one'), 'tony') >> 4
    }

    def "save should keep a blank name or status and clear a blank description, owner, summary or alias when updating"() {

        given:
        repo.findSolution(4) >> solution(id: 4, name: 'Old', description: 'D', statusCode: 'blocked', ownerCode: 'felix',
            statusSummary: 'S', alias: 'a1')

        when:
        service.save(PERSON, new SolutionDraft(4, '  ', '', ' ', '', '', null, false, 0, ''))

        then:
        1 * repo.saveSolution(new SolutionDraft(4, 'Old', '', 'blocked', '', '', null, false, 0, ''), 'tony') >> 4
    }

    def "save should let an API client update a solution when it is shared with it"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        clients.maySee(7, 4) >> true

        when:
        service.save(AGENT, record(SolutionDraft, [id: 4, statusCode: 'inprogress']))

        then:
        1 * repo.saveSolution({ SolutionDraft d -> d.statusCode() == 'inprogress' }, 'rex') >> 4
    }

    @Unroll
    def "save should answer not found on an update when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.save(who, record(SolutionDraft, [id: id, name: 'x']))

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * repo.saveSolution(*_)

        where:
        scenario                               | callerName | id || expectedError
        "the agent was not given the solution" | "agent"    | 4  || "SolutionNotFoundException"
        "the solution is gone"                 | "person"   | 5  || "SolutionNotFoundException"
    }

    @PendingFeature(reason = 'BUG: SolutionService.save validates the KEPT owner code on an update -- a solution whose owner was made inactive can no longer be edited at all (409 "There is no member code"), even when the caller does not mention the owner')
    def "save should still save when a solution whose owner has since been made inactive is renamed"() {

        given:
        repo.findSolution(4) >> solution(id: 4, ownerCode: 'old')

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, name: 'Renamed']))

        then:
        1 * repo.saveSolution(_, 'tony') >> 4
        noExceptionThrown()
    }

    // ---- alias rules --------------------------------------------------------------------

    @Unroll
    def "#seq. alias should return '#expectedText' when #scenario"() {

        given:
        String sent = repeat > 0 ? getReplicatedString(text, repeat) : text
        String expected = repeat > 0 ? getReplicatedString(expectedText, repeat) : expectedText

        expect:
        SolutionService.alias(sent) == expected

        where:
        seq | scenario                        | text     | repeat || expectedText
        1   | "the alias is null"             | null     | 0      || ""
        2   | "the alias is empty"            | ""       | 0      || ""
        3   | "the alias is spaces"           | "   "    | 0      || ""
        4   | "the alias has capitals"        | "Claims" | 0      || "claims"
        5   | "the alias is padded"           | " a-1 "  | 0      || "a-1"
        6   | "the alias is one letter"       | "x"      | 0      || "x"
        7   | "the alias starts with a digit" | "1a"     | 0      || "1a"
        8   | "the alias has edge hyphens"    | "-a-"    | 0      || "-a-"
        9   | "the alias is exactly 40 long"  | "a"      | 40     || "a"
    }

    @Unroll
    def "#seq. alias should refuse when #scenario"() {

        given:
        String base = repeat > 0 ? getReplicatedString(text, repeat) : text
        String sent = accentCode > 0 ? base + String.valueOf((char) accentCode) : base

        when:
        SolutionService.alias(sent)

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage

        where:
        seq | scenario                         | text       | repeat | accentCode || expectedMessage
        1   | "it is 41 long"                  | "a"        | 41     | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        2   | "it is all digits"               | "123"      | 0      | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        3   | "it is all hyphens"              | "---"      | 0      | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        4   | "it has a space"                 | "my alias" | 0      | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        5   | "it has an underscore"           | "a_b"      | 0      | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        6   | "it ends in an accented letter"  | "caf"      | 0      | 233        || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        7   | "it has a period"                | "a.b"      | 0      | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
        8   | "it is digits and a hyphen"      | "12-34"    | 0      | 0          || "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."
    }

    def "save should refuse before saving when an update carries a bad alias"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.save(PERSON, record(SolutionDraft, [id: 4, alias: '42']))

        then:
        thrown(SolutionRuleException)
        0 * repo.saveSolution(*_)
    }

    // ---- by alias -----------------------------------------------------------------------

    def "byAlias should find a solution the caller may see, ignoring case and spaces, when the alias matches"() {

        given:
        Solution s = solution(id: 5, alias: 'claims')
        repo.listSolutions(true, null) >> [solution(id: 4, alias: 'other'), s]
        repo.findSolution(5) >> s

        expect:
        service.byAlias(PERSON, ' CLAIMS ').is(s)
    }

    def "byAlias should look only among the client's own and re-check access when a client asks"() {

        given:
        Solution s = solution(id: 5, alias: 'claims')
        repo.findSolution(5) >> s

        when:
        Solution out = service.byAlias(AGENT, 'claims')

        then:
        1 * repo.listSolutions(true, 7) >> [s]
        1 * clients.maySee(7, 5) >> true
        out.is(s)
    }

    @Unroll
    def "byAlias should answer not found when #scenario"() {

        given:
        repo.listSolutions(true, null) >> [solution(id: 4, alias: ''), solution(id: 5, alias: null), solution(id: 6, alias: 'claims')]

        when:
        service.byAlias(PERSON, alias)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                     | alias   || expectedMessage
        "the alias is null"          | null    || "Solution not found."
        "the alias is empty"         | ""      || "Solution not found."
        "the alias is spaces"        | "   "   || "Solution not found."
        "no solution has the alias"  | "nope"  || "Solution not found."
        "the alias is only a prefix" | "claim" || "Solution not found."
    }

    def "byAlias should answer not found when the storage list leaks a solution the client may not see"() {

        given:
        repo.listSolutions(true, 7) >> [solution(id: 5, alias: 'claims')]
        repo.findSolution(5) >> solution(id: 5, alias: 'claims')
        clients.maySee(7, 5) >> false

        when:
        service.byAlias(AGENT, 'claims')

        then:
        thrown(SolutionNotFoundException)
    }

    // ---- delete -------------------------------------------------------------------------

    def "delete should remove the solution when a person asks"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.delete(PERSON, 4)

        then:
        1 * repo.deleteSolution(4) >> true
    }

    def "delete should answer not found, checked before and after, when the solution is missing or storage refuses"() {

        when:
        service.delete(PERSON, 4)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteSolution(_)

        when:
        service.delete(PERSON, 5)

        then:
        repo.findSolution(5) >> solution(id: 5)
        1 * repo.deleteSolution(5) >> false
        thrown(SolutionNotFoundException)
    }

    @Unroll
    def "delete should refuse an API client with #expectedError when #scenario"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        clients.maySee(7, 4) >> visible

        when:
        service.delete(AGENT, 4)

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        ex.message == expectedMessage
        0 * repo.deleteSolution(_)

        where:
        scenario                              | visible || expectedError                | expectedMessage
        "the solution was shared with it"     | true    || "SolutionForbiddenException" | "Only a person may delete a solution."
        "the solution was not shared with it" | false   || "SolutionNotFoundException"  | "Solution not found."
    }

    // ---- file storage -------------------------------------------------------------------

    def "setSaveFilesToRepo should save the owner's choice and answer the solution when a person decides"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        Solution out = service.setSaveFilesToRepo(PERSON, 4, true)

        then:
        1 * repo.setSaveFilesToRepo(4, true, 'tony') >> true
        out.id() == 4
    }

    def "setSaveFilesToRepo should refuse a client and answer not found when storage says no"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        clients.maySee(7, 4) >> true

        when:
        service.setSaveFilesToRepo(AGENT, 4, false)

        then:
        thrown(SolutionForbiddenException)
        0 * repo.setSaveFilesToRepo(*_)

        when:
        service.setSaveFilesToRepo(HOUSE, 4, false)

        then:
        1 * repo.setSaveFilesToRepo(4, false, 'felix') >> false
        thrown(SolutionNotFoundException)
    }

    def "setSaveFilesToRepo should pass the storage rule's refusal to the caller when the solution is shared"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        repo.setSaveFilesToRepo(4, true, 'tony') >> { throw new SolutionRuleException('Shared. Nothing was saved.') }

        when:
        service.setSaveFilesToRepo(PERSON, 4, true)

        then:
        thrown(SolutionRuleException)
    }

    // ---- projects -----------------------------------------------------------------------

    def "projects should list the projects when the solution is visible"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        List<Project> out = service.projects(PERSON, 4)

        then:
        1 * repo.listProjects(4) >> [project()]
        out.size() == 1
    }

    def "projects should answer not found when the solution is not shared with the client"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.projects(AGENT, 4)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.listProjects(_)
    }

    @Unroll
    def "project should answer #expectedOutcome when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        repo.findProject(10) >> project(id: 10, solutionId: 4)
        repo.findSolution(4) >> solution(id: 4)

        when:
        String outcome
        try {
            outcome = 'project ' + service.project(who, id).id()
        } catch (SolutionNotFoundException ex) {
            outcome = ex.message
        }

        then:
        outcome == expectedOutcome

        where:
        scenario                                    | callerName | id || expectedOutcome
        "a person reads a project"                  | "person"   | 10 || "project 10"
        "the id is zero"                            | "person"   | 0  || "Project not found."
        "the project does not exist"                | "person"   | 11 || "Project not found."
        "the agent reads a project of a hidden one" | "agent"    | 10 || "Solution not found."
    }

    def "project should not look up id 0 when asked for it"() {

        when:
        service.project(PERSON, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.findProject(0)
    }

    def "saveProject should check the solution, save and read back when the project is new"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        ProjectDraft d = new ProjectDraft(0, 4, 'P', null, 'todo', 'tony', 0)

        when:
        Project out = service.saveProject(HOUSE, d)

        then:
        1 * repo.saveProject(d, 'felix') >> 10
        1 * repo.findProject(10) >> project(id: 10)
        out.id() == 10
    }

    def "saveProject should answer not found when the client was not given the solution"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.saveProject(AGENT, new ProjectDraft(0, 4, 'P', null, null, null, 0))

        then:
        thrown(SolutionNotFoundException)
        0 * repo.saveProject(*_)
    }

    def "saveProject should let a client add a project owned by itself when it is its first write"() {

        given:
        repo.findSolution(4) >> solution(id: 4)
        clients.maySee(7, 4) >> true

        when:
        service.saveProject(AGENT, new ProjectDraft(0, 4, 'P', null, null, 'rex', 0))

        then:
        1 * repo.saveMember(0, 'rex', 'Rex', true, true, 3)
        1 * repo.saveProject(_, 'rex') >> 10
    }

    @Unroll
    def "saveProject should merge the update, never changing the solution, when #scenario"() {

        given:
        repo.findProject(10) >> project(id: 10, solutionId: 4, name: 'P', description: 'D', statusCode: 'blocked', ownerCode: 'felix',
            sortOrder: 5)
        repo.findSolution(4) >> solution(id: 4)
        ProjectDraft sent = new ProjectDraft(10, sentSolution, name, description, statusCode, ownerCode, sortOrder)
        ProjectDraft expected = new ProjectDraft(10, 4, 'P', expectedDescription, expectedStatus, expectedOwner, expectedSort)

        when:
        service.saveProject(PERSON, sent)

        then:
        1 * repo.saveProject(expected, 'tony') >> 10

        where:
        scenario                 | sentSolution | name | description | statusCode | ownerCode | sortOrder || expectedDescription | expectedStatus | expectedOwner | expectedSort
        "nothing is sent"        | 999          | null | null        | null       | null      | 0         || "D"                 | "blocked"      | "felix"       | 5
        "blanks and a new order" | 0            | " "  | ""          | "done"     | ""        | 2         || ""                  | "done"         | ""            | 2
    }

    @Unroll
    def "saveProject should refuse when #scenario"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.saveProject(PERSON, new ProjectDraft(0, 4, 'P', null, statusCode, ownerCode, 0))

        then:
        Exception ex = thrown()
        ex.class.simpleName == expectedError
        0 * repo.saveProject(*_)

        where:
        scenario                | statusCode | ownerCode || expectedError
        "the status is unknown" | "nope"     | null      || "SolutionRuleException"
        "the owner is unknown"  | null       | "nobody"  || "SolutionRuleException"
    }

    def "saveProject should answer Project not found when storage returns no id"() {

        given:
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.saveProject(PERSON, new ProjectDraft(0, 4, 'P', null, null, null, 0))

        then:
        1 * repo.saveProject(*_) >> 0
        SolutionNotFoundException ex = thrown()
        ex.message == 'Project not found.'
    }

    @Unroll
    def "deleteProject should delete when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)
        repo.findProject(10) >> project(id: 10, solutionId: 4)
        repo.findSolution(4) >> solution(id: 4)
        clients.maySee(7, 4) >> true

        when:
        service.deleteProject(who, 10)

        then:
        1 * repo.deleteProject(10) >> expectedDeleted

        where:
        scenario                                | callerName || expectedDeleted
        "a person deletes"                      | "person"   || true
        "the agent deletes inside its solution" | "agent"    || true
    }

    def "deleteProject should answer not found when the project is missing, hidden or refused by storage"() {

        given:
        repo.findProject(10) >> project(id: 10, solutionId: 4)
        repo.findSolution(4) >> solution(id: 4)

        when:
        service.deleteProject(PERSON, 11)

        then:
        thrown(SolutionNotFoundException)

        when:
        service.deleteProject(AGENT, 10)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteProject(_)

        when:
        service.deleteProject(PERSON, 10)

        then:
        1 * repo.deleteProject(10) >> false
        thrown(SolutionNotFoundException)
    }

    // ---- keep() and members -------------------------------------------------------------

    @Unroll
    def "#seq. keep should return '#expected' when #scenario"() {

        expect:
        SolutionService.keep(sent, 'cur', neverBlank) == expected

        where:
        seq | scenario                                 | sent  | neverBlank || expected
        1   | "nothing is sent to a never-blank field" | null  | true       || "cur"
        2   | "nothing is sent to a plain field"       | null  | false      || "cur"
        3   | "empty is sent to a never-blank field"   | ""    | true       || "cur"
        4   | "spaces are sent to a never-blank field" | "  "  | true       || "cur"
        5   | "empty is sent to a plain field"         | ""    | false      || ""
        6   | "spaces are sent to a plain field"       | "  "  | false      || "  "
        7   | "a value is sent to a never-blank field" | "new" | true       || "new"
        8   | "a value is sent to a plain field"       | "new" | false      || "new"
    }

    @Unroll
    def "saveMember should save and answer the new lookups when #scenario"() {

        given:
        SolutionCaller who = caller(callerName)

        when:
        Lookups out = service.saveMember(who, 0, 'bob', 'Bob', false, true, 4)

        then:
        1 * repo.saveMember(0, 'bob', 'Bob', false, true, 4)
        (out == lookups()) == expectedLookups

        where:
        scenario              | callerName || expectedLookups
        "a person saves one"  | "person"   || true
        "the house saves one" | "house"    || true
    }

    def "saveMember should refuse a client when it tries to manage members"() {

        when:
        service.saveMember(AGENT, 0, 'bob', 'Bob', false, true, 4)

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may manage members.'
        0 * repo.saveMember(*_)
    }

    private static SolutionDraft draft(Map<String, Object> m) {
        record(SolutionDraft, [id: 0, name: 'New'] + m)
    }
}
