package com.echo

import com.echo.SolutionSavedViewModels.SavedView

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): JdbcSolutionSavedViewRepository (stp_SolutionSavedView_ListForMember / _Save / _Delete)
 * against a real EchoTest_ database -- a view saved and read back with every field, the same name in any case
 * replacing it (keeping its id and place), at most 30 views per member per tab per solution, the list in its
 * order and only the member's own in that solution, a view deleted only by the member who saved it, every
 * refusal of the save proc with nothing written, and the audit rows the three triggers write. Every feature is
 * rolled back by EchoDbITSpec.
 */
class JdbcSolutionSavedViewRepositoryITSpec extends EchoDbITSpec {

    static final String FULL = 'You have 30 saved views here already. Delete one first. Nothing was saved.'

    JdbcSolutionSavedViewRepository repo
    int sid
    String me

    def setup() {
        repo = new JdbcSolutionSavedViewRepository(dataSource())
        sid = newSolution()
        me = ('it' + uniq()).take(20)
    }

    private static int viewsOf(EchoDbITSpec db, int solutionId) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionSavedView WHERE SolutionID = ?', solutionId)
    }

    // ---- save and list ----

    def "save should store a new view and list should read every field back"() {

        when:
        int id = repo.save(sid, me, 'items', 'My open submit work', '{"project":"41","assignee":"tony","hideClosed":true}')
        List<SavedView> got = repo.list(sid, me, 'items')

        then:
        id > 0
        got.size() == 1
        got[0].id() == id
        got[0].solutionId() == sid
        got[0].scope() == 'items'
        got[0].name() == 'My open submit work'
        got[0].filter() == '{"project":"41","assignee":"tony","hideClosed":true}'
        got[0].sortOrder() == 1
        got[0].createdDate() != null
        got[0].modifiedDate() == null
        scalar('SELECT NormalizedName FROM dbo.SolutionSavedView WHERE SolutionSavedViewID = ?', id) == 'my open submit work'
        scalar('SELECT m.MemberCode FROM dbo.SolutionSavedView v JOIN dbo.SolutionMember m ON m.SolutionMemberID = v.SolutionMemberID ' +
               'WHERE v.SolutionSavedViewID = ?', id) == me
        logRows('SolutionSavedView', 'SolutionSavedViewID', id)*.IsInsertion == [true]
    }

    def "save should store the name and filters trimmed and the tab and member in lower case"() {

        when:
        int id = repo.save(sid, me.toUpperCase(), ' ITEMS ', '  Blocked  ', '  {"status":"blocked"}  ')
        List<SavedView> got = repo.list(sid, me, 'items')

        then:
        got*.id() == [id]
        got[0].name() == 'Blocked'
        got[0].filter() == '{"status":"blocked"}'
        got[0].scope() == 'items'
    }

    @Unroll
    def "save should replace the view of the same name when it is saved again #scenario"() {

        given:
        int id = repo.save(sid, me, 'items', 'My open work', '{"a":1}')
        repo.save(sid, me, 'items', 'Blocked', '{"b":2}')

        when:
        int again = repo.save(sid, me, 'items', secondName, '{"a":2}')
        SavedView v = repo.list(sid, me, 'items').find { SavedView it -> it.id() == id }

        then:
        again == id
        viewsOf(this, sid) == 2
        v.name() == secondName
        v.filter() == '{"a":2}'
        v.sortOrder() == 1
        v.modifiedDate() != null
        logRows('SolutionSavedView', 'SolutionSavedViewID', id)*.IsInsertion == [true, false]
        logRows('SolutionSavedView', 'SolutionSavedViewID', id).last().ViewName == secondName

        where:
        scenario                 | secondName     || unused
        'in the same case'       | 'My open work' || null
        'in another case'        | 'MY OPEN WORK' || null
        'in mixed case'          | 'My Open Work' || null
    }

    def "list should answer the member's views of the tab in their order, then by name"() {

        given:
        int zeta = repo.save(sid, me, 'items', 'Zeta', '{}')
        int alpha = repo.save(sid, me, 'items', 'Alpha', '{}')
        int mid = repo.save(sid, me, 'items', 'Mid', '{}')
        sql.update('UPDATE dbo.SolutionSavedView SET SortOrder = 1 WHERE SolutionSavedViewID IN (?, ?)', zeta, mid)

        expect:
        repo.list(sid, me, 'items')*.id() == [mid, zeta, alpha]
        repo.list(sid, me.toUpperCase(), ' Items ')*.id() == [mid, zeta, alpha]
    }

    def "list should answer only the member's own views of that solution and tab"() {

        given:
        String someoneElse = ('iu' + uniq()).take(20)
        int other = newSolution()
        int mine = repo.save(sid, me, 'items', 'Mine', '{}')
        repo.save(sid, someoneElse, 'items', 'Theirs', '{}')
        repo.save(other, me, 'items', 'Mine elsewhere', '{}')

        expect:
        repo.list(sid, me, 'items')*.id() == [mine]
        repo.list(sid, someoneElse, 'items')*.name() == ['Theirs']
        repo.list(other, me, 'items')*.name() == ['Mine elsewhere']
        repo.list(sid, me, 'notes') == []
        repo.list(sid, ('iv' + uniq()).take(20), 'items') == []
        repo.list(sid, null, 'items') == []
    }

    def "save should let two members keep a view of the same name"() {

        given:
        String someoneElse = ('iu' + uniq()).take(20)

        when:
        int a = repo.save(sid, me, 'items', 'Mine', '{"a":1}')
        int b = repo.save(sid, someoneElse, 'items', 'Mine', '{"b":1}')

        then:
        a != b
        repo.list(sid, me, 'items')*.filter() == ['{"a":1}']
        repo.list(sid, someoneElse, 'items')*.filter() == ['{"b":1}']
    }

    // ---- the limit ----

    def "save should refuse a 31st view, still replace one of the 30, and let another member or solution save"() {

        given:
        List<Integer> ids = (1..30).collect { int n -> repo.save(sid, me, 'items', 'View ' + n, '{}') }

        when:
        repo.save(sid, me, 'items', 'One too many', '{}')

        then:
        SolutionRuleException ex = thrown()
        ex.message == FULL
        viewsOf(this, sid) == 30
        repo.list(sid, me, 'items')*.sortOrder() == (1..30).collect { int n -> n }

        when:
        int replaced = repo.save(sid, me, 'items', 'view 5', '{"changed":true}')

        then:
        replaced == ids[4]
        viewsOf(this, sid) == 30
        repo.list(sid, me, 'items').find { SavedView it -> it.id() == ids[4] }.filter() == '{"changed":true}'

        when:
        int otherMember = repo.save(sid, ('iu' + uniq()).take(20), 'items', 'One too many', '{}')
        int otherSolution = repo.save(newSolution(), me, 'items', 'One too many', '{}')

        then:
        otherMember > 0
        otherSolution > 0
    }

    // ---- refusals ----

    @Unroll
    def "save should refuse and write nothing when #scenario"() {

        given:
        int solution = solutionName == 'missing' ? -1 : sid
        int before = count('SELECT COUNT(*) FROM dbo.SolutionSavedView')

        when:
        repo.save(solution, me, scope, name, filter)

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionSavedView') == before

        where:
        scenario                          | solutionName | scope   | name    | filter      || expectedMessage
        'the solution does not exist'     | 'missing'    | 'items' | 'Mine'  | '{}'        || 'That solution no longer exists. Nothing was saved.'
        'the tab is not the items tab'    | 'this'       | 'notes' | 'Mine'  | '{}'        || 'Views can be saved for the Items tab. Nothing was saved.'
        'there is no tab'                 | 'this'       | null    | 'Mine'  | '{}'        || 'Views can be saved for the Items tab. Nothing was saved.'
        'the name is blank'               | 'this'       | 'items' | '   '   | '{}'        || 'A view needs a name. Nothing was saved.'
        'there is no name'                | 'this'       | 'items' | null    | '{}'        || 'A view needs a name. Nothing was saved.'
        'the filters are not an object'   | 'this'       | 'items' | 'Mine'  | '[1]'       || 'A view keeps its filters as an object. Nothing was saved.'
        'there are no filters'            | 'this'       | 'items' | 'Mine'  | null        || 'A view keeps its filters as an object. Nothing was saved.'
    }

    // ---- delete ----

    def "delete should take only the member's own view, audit it and answer false the second time"() {

        given:
        String someoneElse = ('iu' + uniq()).take(20)
        int id = repo.save(sid, me, 'items', 'Mine', '{}')
        int keep = repo.save(sid, me, 'items', 'Keep', '{}')

        expect:
        !repo.delete(id, someoneElse)
        viewsOf(this, sid) == 2
        repo.delete(id, me.toUpperCase())
        repo.list(sid, me, 'items')*.id() == [keep]
        logRows('SolutionSavedView', 'SolutionSavedViewID', id)*.IsInsertion == [true, false]
        logRows('SolutionSavedView', 'SolutionSavedViewID', id).last().DeletedDate != null
        !repo.delete(id, me)
        !repo.delete(-1, me)
    }

    def "delete should let the member save a view of the same name again afterwards"() {

        given:
        int id = repo.save(sid, me, 'items', 'Mine', '{}')
        repo.delete(id, me)

        when:
        int again = repo.save(sid, me, 'items', 'Mine', '{"again":true}')

        then:
        again != id
        repo.list(sid, me, 'items')*.filter() == ['{"again":true}']
    }
}
