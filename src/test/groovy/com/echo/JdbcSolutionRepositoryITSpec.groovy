package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDrafts.ProjectDraft
import com.echo.SolutionDrafts.SolutionDraft
import com.echo.SolutionModels.Lookups
import com.echo.SolutionModels.Member
import com.echo.SolutionModels.Project
import com.echo.SolutionModels.Solution

import spock.lang.Unroll

// Story 5653: JdbcSolutionRepository against a real EchoTest_ database (stp_Solution*, stp_SolutionProject*,
// the member / note type / log type lookups).
class JdbcSolutionRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionRepository repo

    def setup() {
        repo = new JdbcSolutionRepository(dataSource())
    }

    def "lookups should return the seeded statuses, members, log types and note types in sort order when the database is fresh"() {

        when:
        Lookups l = repo.lookups()

        then:
        l.statuses()*.code() == ['todo', 'inprogress', 'blocked', 'done', 'cancelled']
        l.statuses().find { it.code() == 'done' }.closed()
        !l.statuses().find { it.code() == 'blocked' }.closed()
        l.statuses()[0].colorHex() == '#6B7280'
        l.members()*.code().containsAll(['tony', 'rex'])
        l.members().find { it.code() == 'rex' }.agent()
        !l.members().find { it.code() == 'tony' }.agent()
        l.logTypes()*.code().take(6) == ['note', 'command', 'result', 'decision', 'blocker', 'question']
        l.noteTypes()[0].code() == 'instructions'
        l.noteTypes()*.sortOrder() == l.noteTypes()*.sortOrder().sort(false)
    }

    def "saveSolution should store every field so findSolution reads it back when a solution is added"() {

        given:
        String name = 'IT round trip ' + uniq()
        String alias = 'rt' + uniq()
        LocalDateTime target = getLocalDateTime('01/02/2027 00:00')

        when:
        int id = repo.saveSolution(draft(name: name, alias: alias.toUpperCase(), sort: 7, target: target), 'Felix')
        Solution s = repo.findSolution(id)

        then:
        id > 0
        s.id() == id
        s.name() == name
        s.description() == 'A description'
        s.statusCode() == 'inprogress'
        s.statusName() == 'In progress'
        !s.closed()
        s.colorHex() == '#2563EB'
        s.ownerCode() == 'rex'
        s.ownerName() == 'Rex'
        s.statusSummary() == 'On track'
        s.targetDate() == target
        !s.archived()
        !s.saveFilesToRepo()
        s.sortOrder() == 7
        s.createdDate() != null
        s.modifiedBy() == 'Felix'
        s.alias() == alias
        s.projectCount() == 0
        s.itemCount() == 0
        s.fileCount() == 0
        s.sharedCount() == 0
        s.logCount() == 0
        s.noteCount() == 0
        s.awaitingTonyCount() == 0
        s.lastLogDate() == null
    }

    def "saveSolution should fall back to the first status and no owner when the codes are unknown"() {

        when:
        int id = repo.saveSolution(draft(status: 'nonsense', owner: 'nobody-x'), 'felix')
        Solution s = repo.findSolution(id)

        then:
        s.statusCode() == 'todo'
        s.ownerCode() == null
        s.ownerName() == null
    }

    def "saveSolution should put a new solution last when its sort order is 0"() {

        given:
        int max = ((Number) (scalar('SELECT ISNULL(MAX(SortOrder), 0) FROM dbo.Solution') ?: 0)).intValue()

        expect:
        repo.findSolution(repo.saveSolution(draft(), 'felix')).sortOrder() == max + 1
    }

    def "saveSolution should change every field and keep the id when a solution is updated"() {

        given:
        int id = repo.saveSolution(draft(alias: 'up' + uniq()), 'felix')

        when:
        int again = repo.saveSolution(new SolutionDraft(id, 'Renamed ' + uniq(), 'New desc', 'done', 'tony', '  ', null, true, 3, ''), 'tony')
        Solution s = repo.findSolution(id)

        then:
        again == id
        s.name().startsWith('Renamed')
        s.description() == 'New desc'
        s.statusCode() == 'done'
        s.closed()
        s.ownerCode() == 'tony'
        s.statusSummary() == null
        s.targetDate() == null
        s.archived()
        s.sortOrder() == 3
        s.alias() == null
        s.modifiedBy() == 'tony'
        s.modifiedDate() != null
    }

    def "findSolution should return null when the id is unknown"() {

        expect:
        repo.findSolution(-1) == null
    }

    @Unroll
    def "saveSolution should refuse and write nothing when #scenario"() {

        given:
        String taken = 'Taken ' + uniq()
        String takenAlias = 'al' + uniq()
        repo.saveSolution(draft(name: taken, alias: takenAlias), 'felix')
        int before = count('SELECT COUNT(*) FROM dbo.Solution')
        String n = name == 'TAKEN' ? taken : (name == 'FRESH' ? 'N ' + uniq() : name)
        String a = alias == 'TAKEN' ? takenAlias : alias

        when:
        repo.saveSolution(draft(name: n, alias: a), 'felix')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)
        count('SELECT COUNT(*) FROM dbo.Solution') == before

        where:
        scenario                          | name    | alias       || message
        "the name is blank"               | "  "    | null        || "needs a name"
        "the name is null"                | null    | null        || "needs a name"
        "the name is taken"               | "TAKEN" | null        || "already a solution with that name"
        "the alias has a space"           | "FRESH" | "bad alias" || "letters, digits and hyphens"
        "the alias has no letter"         | "FRESH" | "1234"      || "at least one letter"
        "the alias is taken"              | "FRESH" | "TAKEN"     || "already uses that alias"
    }

    def "listSolutions should order by archived, sort order and name and hide archived when not asked"() {

        given:
        int a = repo.saveSolution(draft(name: 'zz A ' + uniq(), sort: 900001), 'felix')
        int b = repo.saveSolution(draft(name: 'aa B ' + uniq(), sort: 900001), 'felix')
        int c = repo.saveSolution(draft(name: 'mm C ' + uniq(), sort: 900000), 'felix')
        int arch = repo.saveSolution(draft(archived: true, sort: 1), 'felix')

        when:
        List<Integer> open = repo.listSolutions(false, null)*.id()
        List<Integer> all = repo.listSolutions(true, null)*.id()
        Solution listedA = repo.listSolutions(false, null).find { it.id() == a }

        then:
        !open.contains(arch)
        all.contains(arch)
        all.indexOf(arch) > all.indexOf(a)
        open.indexOf(c) < open.indexOf(b)
        open.indexOf(b) < open.indexOf(a)
        listedA.description() == null
        listedA.descriptionPreview() == 'A description'
    }

    def "listSolutions should return only the shared solutions when an API client is given"() {

        given:
        int shared = newSolution()
        int other = newSolution()
        int client = newApiClient()
        sql.queryForList('EXEC dbo.stp_Solution_Share ?, ?, 1, ?', shared, client, 'felix')

        when:
        List<Integer> ids = repo.listSolutions(true, client)*.id()

        then:
        ids == [shared]
        !ids.contains(other)
        repo.listSolutions(true, -999).isEmpty()
    }

    def "findSolution and listSolutions should count projects, items, logs, notes, files, shares and awaited messages when a solution has them"() {

        given:
        int id = newSolution()
        int p = newProject(id)
        newItem(p, 'a', null, 'done')
        newItem(p, 'b', null, 'blocked')
        newItem(p, 'c', null, 'todo')
        newLog(id, 'hello')
        sql.queryForList("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', 'Note', 'Body', NULL, 'tony', 0", id)
        sql.queryForList("EXEC dbo.stp_SolutionFile_Save 0, ?, NULL, 'F', 'f.txt', 'nook', NULL, 's.txt', 'text/plain', 3, NULL, 'tony'", id)
        int conv = idOf('EXEC dbo.stp_SolutionConversation_Create ?, ?', id, 'Thread')
        sql.queryForList("EXEC dbo.stp_ConversationMessage_Save @prmConversationMessageID=0, @prmConversationID=?, @prmSender='rex', " +
            "@prmBody='hi', @prmAwaitingResponseFrom='tony', @prmTokensIn=0, @prmTokensOut=0, @prmDurationMs=0, @prmModelUsed='', @prmErrorText=''", conv)
        sql.queryForList('EXEC dbo.stp_Solution_Share ?, ?, 1, ?', id, newApiClient(), 'felix')

        when:
        Solution s = repo.findSolution(id)
        Solution listed = repo.listSolutions(true, null).find { it.id() == id }

        then:
        [s, listed].every {
            it.projectCount() == 1 && it.itemCount() == 3 && it.closedItemCount() == 1 && it.blockedItemCount() == 1 &&
            it.logCount() == 1 && it.lastLogDate() != null && it.noteCount() == 1 && it.fileCount() == 1 &&
            it.sharedCount() == 1 && it.awaitingTonyCount() == 1
        }
    }

    def "deleteSolution should remove everything under the solution, audit it and leave the conversations when the solution is full"() {

        given:
        int id = newSolution()
        int p = newProject(id)
        int item = newItem(p)
        newItem(p, 'child', item)
        sql.queryForList("EXEC dbo.stp_SolutionLog_Save 0, ?, ?, ?, 'note', 'tony', 'hello', NULL", id, p, item)
        int note = idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', 'Note', 'Body', NULL, 'tony', 0", id)
        sql.queryForList("EXEC dbo.stp_SolutionNoteStep_Save 0, ?, 0, 'Step', 'cmd', NULL, NULL, NULL", note)
        sql.queryForList("EXEC dbo.stp_SolutionFile_Save 0, ?, NULL, 'F', 'f.txt', 'nook', NULL, 's.txt', 'text/plain', 3, NULL, 'tony'", id)
        int conv = idOf('EXEC dbo.stp_SolutionConversation_Create ?, ?', id, 'Thread')
        sql.queryForList('EXEC dbo.stp_Solution_Share ?, ?, 1, ?', id, newApiClient(), 'felix')
        int plan = idOf("EXEC dbo.stp_SolutionTestPlan_Save 0, ?, 'Plan', NULL, NULL, 'tony', NULL", id)
        int env = idOf("EXEC dbo.stp_SolutionConfigEnvironment_Save 0, ?, 'Prod', NULL", id)
        int term = idOf("EXEC dbo.stp_SolutionTerm_Save 0, ?, 'Word', 'Meaning', 'Kind', NULL, NULL, 'Cat', 'tony'", id)
        int axis = idOf("EXEC dbo.stp_SolutionConfigGridAxis_Save 0, ?, 'Env', NULL", id)

        when:
        boolean gone = repo.deleteSolution(id)

        then:
        gone
        repo.findSolution(id) == null
        count('SELECT COUNT(*) FROM dbo.SolutionProject WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectID = ?', p) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionNote WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionNoteStep WHERE SolutionNoteID = ?', note) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionNoteVersion WHERE SolutionNoteID = ?', note) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionFile WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionConversation WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionApiClient WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTestPlan WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanID = ?', plan) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionConfigEnvironment WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTerm WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTermCategoryXRef WHERE SolutionTermID = ?', term) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTermType WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionConfigGridAxis WHERE SolutionID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.Conversation WHERE ConversationID = ?', conv) == 1
        logRows('Solution', 'SolutionID', id).last().DeletedDate != null
        logRows('SolutionProject', 'SolutionProjectID', p).last().DeletedDate != null
        logRows('SolutionConfigEnvironment', 'SolutionConfigEnvironmentID', env).last().DeletedDate != null
        logRows('SolutionConfigGridAxis', 'SolutionConfigGridAxisID', axis).last().DeletedDate != null
    }

    def "deleteSolution should answer false when the id is unknown"() {

        expect:
        !repo.deleteSolution(-1)
    }

    def "saveSolution should write Solution_LOG rows when a solution is added and updated"() {

        when:
        int id = repo.saveSolution(draft(), 'felix')
        repo.saveSolution(draft(id: id, name: 'Changed ' + uniq()), 'tony')
        List<Map<String, Object>> log = logRows('Solution', 'SolutionID', id)

        then:
        log.size() == 2
        log[0].IsInsertion == true
        log[1].IsInsertion == false
        ((String) log[1].SolutionName).startsWith('Changed')
        log[1].ModifiedBy == 'tony'
    }

    def "setSaveFilesToRepo should flip the switch and refuse it when the solution is shared"() {

        given:
        int id = newSolution()

        expect:
        repo.setSaveFilesToRepo(id, true, 'FELIX')
        repo.findSolution(id).saveFilesToRepo()
        repo.findSolution(id).modifiedBy() == 'felix'
        repo.setSaveFilesToRepo(id, false, 'felix')
        !repo.findSolution(id).saveFilesToRepo()
        !repo.setSaveFilesToRepo(-1, false, 'felix')

        when:
        sql.queryForList('EXEC dbo.stp_Solution_Share ?, ?, 1, ?', id, newApiClient(), 'felix')
        repo.setSaveFilesToRepo(id, true, 'felix')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains('shared with an outside agent')
        !repo.findSolution(id).saveFilesToRepo()
    }

    def "saveProject should store every field and listProjects should order by sort order then name when projects are added"() {

        given:
        int sid = newSolution()

        when:
        int b = repo.saveProject(new ProjectDraft(0, sid, 'B proj', 'Desc B', 'blocked', 'rex', 5), 'felix')
        int a = repo.saveProject(new ProjectDraft(0, sid, 'A proj', 'Desc A', 'todo', null, 5), 'felix')
        int first = repo.saveProject(new ProjectDraft(0, sid, 'Z proj', 'Desc Z', 'todo', 'tony', 1), 'felix')
        Project p = repo.findProject(b)
        List<Project> listed = repo.listProjects(sid)

        then:
        p.id() == b
        p.solutionId() == sid
        p.name() == 'B proj'
        p.description() == 'Desc B'
        p.statusCode() == 'blocked'
        p.statusName() == 'Blocked'
        !p.closed()
        p.ownerCode() == 'rex'
        p.ownerName() == 'Rex'
        p.sortOrder() == 5
        p.modifiedBy() == 'felix'
        p.createdDate() != null
        listed*.id() == [first, a, b]
        listed[0].descriptionPreview() == 'Desc Z'
        listed[0].description() == null
        repo.findProject(a).ownerCode() == null
    }

    def "saveProject should keep the solution and change the fields when a project is updated"() {

        given:
        int sid = newSolution()
        int p1 = newProject(sid)
        int p2 = newProject(sid)
        int otherSolution = newSolution()

        when:
        repo.saveProject(new ProjectDraft(p2, otherSolution, 'Renamed', 'New', 'done', 'tony', 9), 'tony')
        Project p = repo.findProject(p2)

        then:
        repo.findProject(p1).sortOrder() == 1
        p.solutionId() == sid
        p.name() == 'Renamed'
        p.statusCode() == 'done'
        p.closed()
        p.sortOrder() == 9
        p.modifiedBy() == 'tony'
    }

    def "findProject and listProjects should count items, closed and blocked when a project has items"() {

        given:
        int sid = newSolution()
        int p = newProject(sid)
        newItem(p, 'x', null, 'done')
        newItem(p, 'y', null, 'cancelled')
        newItem(p, 'z', null, 'blocked')

        when:
        Project found = repo.findProject(p)
        Project listed = repo.listProjects(sid)[0]

        then:
        [found, listed].every { it.itemCount() == 3 && it.closedItemCount() == 2 && it.blockedItemCount() == 1 }
    }

    @Unroll
    def "saveProject should refuse when #scenario"() {

        given:
        int sid = newSolution()
        repo.saveProject(new ProjectDraft(0, sid, 'Dup', null, 'todo', null, 0), 'felix')
        int target = mine ? sid : -1

        when:
        repo.saveProject(new ProjectDraft(0, target, name, null, 'todo', null, 0), 'felix')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                        | mine  | name  || message
        "the name is blank"             | true  | " "   || "needs a name"
        "the solution does not exist"   | false | "New" || "no longer exists"
        "the name is taken"             | true  | "Dup" || "already has a project with that name"
    }

    def "deleteProject should remove its items, detach the log, files and notes and audit it when the project is in use"() {

        given:
        int sid = newSolution()
        int p = newProject(sid)
        int item = newItem(p)
        int log = idOf("EXEC dbo.stp_SolutionLog_Save 0, ?, ?, ?, 'note', 'tony', 'hello', NULL", sid, p, item)
        int note = idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, ?, 'runbook', 'Note', 'Body', NULL, 'tony', 0", sid, p)
        int file = idOf("EXEC dbo.stp_SolutionFile_Save 0, ?, ?, 'F', 'f.txt', 'nook', NULL, 's.txt', 'text/plain', 3, NULL, 'tony'", sid, item)

        when:
        boolean gone = repo.deleteProject(p)

        then:
        gone
        repo.findProject(p) == null
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = ?', item) == 0
        row('SELECT SolutionProjectID, SolutionProjectItemID FROM dbo.SolutionLog WHERE SolutionLogID = ?', log) ==
            [SolutionProjectID: null, SolutionProjectItemID: null]
        scalar('SELECT SolutionProjectID FROM dbo.SolutionNote WHERE SolutionNoteID = ?', note) == null
        scalar('SELECT SolutionProjectItemID FROM dbo.SolutionFile WHERE SolutionFileID = ?', file) == null
        logRows('SolutionProject', 'SolutionProjectID', p).last().DeletedDate != null
        logRows('SolutionProjectItem', 'SolutionProjectItemID', item).last().DeletedDate != null
        !repo.deleteProject(p)
    }

    def "saveMember should insert, update and refuse duplicates and blanks when members are saved"() {

        given:
        String code = 'm' + uniq()

        when:
        int id = repo.saveMember(0, code.toUpperCase(), 'Member One', true, true, 0)
        Member m = repo.lookups().members().find { it.id() == id }

        then:
        m.code() == code
        m.name() == 'Member One'
        m.agent()
        m.active()
        m.sortOrder() > 0

        when:
        repo.saveMember(id, code, 'Renamed', false, false, 42)
        m = repo.lookups().members().find { it.id() == id }

        then:
        m.name() == 'Renamed'
        !m.agent()
        !m.active()
        m.sortOrder() == 42

        when:
        repo.saveMember(0, code, 'Again', false, true, 0)

        then:
        thrown(SolutionRuleException)

        when:
        repo.saveMember(0, ' ', 'X', false, true, 0)

        then:
        thrown(SolutionRuleException)

        when:
        repo.saveMember(0, 'x' + uniq(), ' ', false, true, 0)

        then:
        thrown(SolutionRuleException)
    }

    def "findOrCreateNoteType should find by name or code and create with a unique derived code when the name is new"() {

        when:
        int existing = repo.findOrCreateNoteType('Runbook')
        int byCode = repo.findOrCreateNoteType('runbook')
        String name = 'IT Kind ' + uniq()
        int created = repo.findOrCreateNoteType(name)
        int again = repo.findOrCreateNoteType(name)
        Map<String, Object> r = row('SELECT NoteTypeCode, SortOrder FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = ?', created)

        then:
        existing == 1
        byCode == 1
        again == created
        r.NoteTypeCode ==~ /[a-z0-9]{1,20}/
        r.SortOrder == count('SELECT MAX(SortOrder) FROM dbo.SolutionNoteType')
        logRows('SolutionNoteType', 'SolutionNoteTypeID', created).size() == 1
    }

    def "findOrCreateNoteType should give distinct codes when two names reduce to the same code"() {

        when:
        int a = repo.findOrCreateNoteType('Same Code!')
        int b = repo.findOrCreateNoteType('Same-Code')

        then:
        a != b
        scalar('SELECT NoteTypeCode FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = ?', a) == 'samecode'
        scalar('SELECT NoteTypeCode FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = ?', b) == 'samecode2'
    }

    @Unroll
    def "findOrCreateNoteType and findOrCreateLogType should refuse with Nothing was saved when #scenario"() {

        given:
        String name = repeat > 0 ? getReplicatedString('x', repeat) : text

        when:
        repo.findOrCreateNoteType(name)

        then:
        SolutionRuleException ex = thrown()
        ex.message.endsWith('Nothing was saved.')

        when:
        repo.findOrCreateLogType(name)

        then:
        SolutionRuleException ex2 = thrown()
        ex2.message.endsWith('Nothing was saved.')

        where:
        scenario                         | text | repeat || _
        "the name is blank"              | "  " | 0      || _
        "the name is null"               | null | 0      || _
        "the name is over 100 characters"| null | 101    || _
    }

    def "findOrCreateLogType should find a seeded kind and add a new one at the end when the name is new"() {

        when:
        int found = repo.findOrCreateLogType('Decision')
        int created = repo.findOrCreateLogType('IT Log Kind ' + uniq())

        then:
        found == 4
        created > 6
        repo.lookups().logTypes().last().id() == created
    }

    def "reorderNoteTypes should give the listed kinds their slots in the new order and leave the rest when some ids are unknown"() {

        given:
        List<Integer> ids = repo.lookups().noteTypes()*.id()
        List<Integer> wanted = [ids[1], ids[0], -77, null]

        when:
        repo.reorderNoteTypes(wanted)
        List<Integer> after = repo.lookups().noteTypes()*.id()

        then:
        after[0] == ids[1]
        after[1] == ids[0]
        after.drop(2) == ids.drop(2)
        repo.lookups().noteTypes()*.sortOrder() == (1..ids.size()).toList()
    }

    private static SolutionDraft draft(Map m) {
        new SolutionDraft((m.id ?: 0) as int, (m.containsKey('name') ? m.name : 'IT sol ' + uniq()) as String,
            (m.description ?: 'A description') as String, (m.status ?: 'inprogress') as String,
            (m.containsKey('owner') ? m.owner : 'rex') as String, (m.summary ?: 'On track') as String,
            (m.target ?: getLocalDateTime('01/02/2027 00:00')) as LocalDateTime, (m.archived ?: false) as Boolean,
            (m.sort ?: 0) as int, (m.containsKey('alias') ? m.alias : null) as String)
    }

    private static SolutionDraft draft() { draft([:]) }
}
