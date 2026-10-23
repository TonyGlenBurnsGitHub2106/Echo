package com.echo

import spock.lang.PendingFeature

// Story 5653: JdbcSolutionTestRepository against a real EchoTest_ database (stp_SolutionTest*: plans, columns,
// groups, cases, checks, skips, results, counts, reorders, locate).
class JdbcSolutionTestRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionTestRepository repo
    int sid

    def setup() {
        repo = new JdbcSolutionTestRepository(dataSource())
        sid = newSolution()
    }

    def "savePlan should store a dev checklist with its two default columns when no kind is given"() {

        when:
        int id = repo.savePlan(0, sid, '  Release 1  ', '  What to test  ', null, 'tony', null)
        Map<String, Object> p = repo.plan(id)
        List<Map<String, Object>> levels = repo.levels(id)

        then:
        p.SolutionTestPlanID == id
        p.SolutionID == sid
        p.PlanTitle == 'Release 1'
        p.PlanDescription == 'What to test'
        p.SortOrder == 1
        p.ModifiedBy == 'tony'
        p.PlanKindCode == 'dev'
        p.GroupCount == 0
        p.CaseCount == 0
        p.LevelCount == 2
        p.CheckedCount == 0
        p.SkippedCount == 0
        p.CreatedDate != null
        levels*.LevelTitle == ['Manual', 'Unit tested']
        levels*.SortOrder == [1, 2]
    }

    def "savePlan should give a qa checklist one Passed column, refuse a bad kind and keep the kind when edited"() {

        when:
        int qa = newPlan(repo, sid, ' QA ')

        then:
        repo.plan(qa).PlanKindCode == 'qa'
        repo.levels(qa)*.LevelTitle == ['Passed']

        when:
        repo.savePlan(qa, sid, 'Renamed', '', 7, 'rex', 'dev')

        then:
        repo.plan(qa).PlanKindCode == 'qa'
        repo.plan(qa).PlanTitle == 'Renamed'
        repo.plan(qa).PlanDescription == null
        repo.plan(qa).SortOrder == 7
        repo.plan(qa).ModifiedBy == 'rex'

        expect:
        ruleOf { newPlan(repo, sid, 'prod') }.message.endsWith('Nothing was saved.')
    }

    def "plans should list in sort order and filter by kind when dev and qa checklists exist"() {

        given:
        int a = newPlan(repo, sid, null)
        int b = newPlan(repo, sid, 'qa')
        int c = repo.savePlan(0, sid, 'First', null, -1, 'tony', null)

        expect:
        repo.plans(sid, null)*.SolutionTestPlanID == [c, a, b]
        repo.plans(sid, 'qa')*.SolutionTestPlanID == [b]
        repo.plans(sid, 'dev')*.SolutionTestPlanID == [c, a]
        repo.plans(newSolution(), null).isEmpty()
    }

    def "savePlan should refuse when the title is blank, the solution is missing or the plan to edit is gone"() {

        expect:
        ruleOf { repo.savePlan(0, sid, ' ', null, null, 'tony', null) }.message.contains('needs a title')
        ruleOf { repo.savePlan(0, -1, 'T', null, null, 'tony', null) }.message.contains('solution no longer exists')
        ruleOf { repo.savePlan(987654321, sid, 'T', null, null, 'tony', null) }.message.contains('checklist no longer exists')
        repo.plan(-1) == null
    }

    def "saveLevel and deleteLevel should add, rename and refuse a duplicate and never drop the last column when columns change"() {

        given:
        int p = newPlan(repo, sid, null)
        List<Integer> ids = repo.levels(p)*.SolutionTestPlanLevelID

        when:
        int e2e = repo.saveLevel(0, p, ' End to end ', null)
        repo.saveLevel(ids[0], 0, 'Hand checked', 9)

        then:
        repo.levels(p)*.LevelTitle == ['Unit tested', 'End to end', 'Hand checked']
        count('SELECT COUNT(*) FROM dbo.SolutionTestLevel WHERE LevelTitle = ?', 'Hand checked') == 1
        count('SELECT COUNT(*) FROM dbo.SolutionTestLevel WHERE LevelTitle = ?', 'Manual') == 1   // the old title stays in the lookup
        ruleOf { repo.saveLevel(0, p, 'unit TESTED', null) }.message == 'This checklist already has a column named "unit TESTED".'
        ruleOf { repo.saveLevel(0, p, ' ', null) }.message.contains('needs a name')
        ruleOf { repo.saveLevel(0, -1, 'X', null) }.message.contains('checklist no longer exists')

        when:
        repo.deleteLevel(ids[1])
        repo.deleteLevel(e2e)

        then:
        repo.levels(p)*.LevelTitle == ['Hand checked']
        ruleOf { repo.deleteLevel(ids[0]) }.message.contains('at least one column')
        !repo.deleteLevel(-1)
    }

    @PendingFeature(reason = 'BUG: stp_SolutionTestPlanLevel_Save builds its duplicate-column message from the title and passes it to RAISERROR as a FORMAT string without escaping %, so a duplicate column whose name holds a percent sign fails with a SQL format error (a 500) instead of the rule (stp_SolutionTerm_Save escapes it as %%)')
    def "saveLevel should refuse in words when a duplicate column name holds a percent sign"() {

        given:
        int p = newPlan(repo, sid, null)
        repo.saveLevel(0, p, '50% pass', null)

        when:
        repo.saveLevel(0, p, '50% pass', null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'This checklist already has a column named "50% pass".'
    }

    def "saveGroup and saveCase should store, order and edit groups and cases when a checklist is filled"() {

        given:
        int p = newPlan(repo, sid, null)

        when:
        int g2 = repo.saveGroup(0, p, 'Second', null)
        int g1 = repo.saveGroup(0, p, ' First ', 0)
        int c1 = repo.saveCase(0, g1, '  Log in  ', '  use tony  ', null, null)
        int c2 = repo.saveCase(0, g1, 'Log out', null, null, false)
        List<Map<String, Object>> cases = repo.cases(p)

        then:
        repo.groups(p)*.SolutionTestGroupID == [g1, g2]
        repo.groups(p)[0].GroupTitle == 'First'
        cases*.SolutionTestCaseID == [c1, c2]
        cases[0].CaseText == 'Log in'
        cases[0].CaseNote == 'use tony'
        cases[0].SortOrder == 1
        cases[0].IncludeInPrint == true
        cases[0].IsFailing == false
        cases[1].IncludeInPrint == false
        cases[1].SortOrder == 2

        when:
        repo.saveGroup(g2, 0, 'Second renamed', null)
        repo.saveCase(c1, 0, 'Log in again', '', null, null)

        then:
        repo.groups(p).find { it.SolutionTestGroupID == g2 }.GroupTitle == 'Second renamed'
        repo.cases(p)[0].CaseText == 'Log in again'
        repo.cases(p)[0].CaseNote == null
        repo.cases(p)[0].IncludeInPrint == true
        repo.plan(p).GroupCount == 2
        repo.plan(p).CaseCount == 2
    }

    def "saveCase should move a case to the end of another group of the same checklist and refuse another checklist"() {

        given:
        int p = newPlan(repo, sid, null)
        int g1 = repo.saveGroup(0, p, 'A', null)
        int g2 = repo.saveGroup(0, p, 'B', null)
        repo.saveCase(0, g2, 'existing', null, null, null)
        int c = repo.saveCase(0, g1, 'mover', null, null, null)
        int foreignGroup = repo.saveGroup(0, newPlan(repo, sid, null), 'X', null)

        when:
        repo.saveCase(c, g2, 'mover', null, null, null)

        then:
        repo.cases(p).find { it.SolutionTestCaseID == c }.SolutionTestGroupID == g2
        repo.cases(p).find { it.SolutionTestCaseID == c }.SortOrder == 2

        expect:
        ruleOf { repo.saveCase(c, foreignGroup, 'x', null, null, null) }.message.contains('same checklist')
        ruleOf { repo.saveCase(987654321, g1, 'x', null, null, null) }.message.contains('case no longer exists')
        ruleOf { repo.saveCase(0, -1, 'x', null, null, null) }.message.contains('group no longer exists')
        ruleOf { repo.saveCase(0, g1, ' ', null, null, null) }.message.contains('needs some text')
        ruleOf { repo.saveGroup(0, p, ' ', null) }.message.contains('needs a name')
        ruleOf { repo.saveGroup(987654321, p, 'x', null) }.message.contains('group no longer exists')
        ruleOf { repo.saveGroup(0, -1, 'x', null) }.message.contains('checklist no longer exists')
    }

    def "setCheck should record who and when and remove a skip when a cell is ticked"() {

        given:
        int p = newPlan(repo, sid, null)
        int g = repo.saveGroup(0, p, 'G', null)
        int c = repo.saveCase(0, g, 'case', null, null, null)
        int col = repo.levels(p)[0].SolutionTestPlanLevelID
        repo.setSkip(c, col, true, 'tony')

        when:
        Map<String, Object> tick = repo.setCheck(c, col, true, 'Rex')

        then:
        tick.IsChecked == true
        tick.CheckedDate != null
        tick.CheckedByCode == 'rex'
        tick.CheckedByName == 'Rex'
        repo.checks(p)*.SolutionTestCaseID == [c]
        repo.skips(p).isEmpty()
        repo.plan(p).CheckedCount == 1

        when:
        Map<String, Object> untick = repo.setCheck(c, col, false, 'rex')

        then:
        untick.IsChecked == false
        untick.CheckedDate == null
        untick.CheckedByCode == null
        repo.checks(p).isEmpty()
    }

    def "setSkip should remove a tick and write nothing when a never-skipped cell is un-skipped"() {

        given:
        int p = newPlan(repo, sid, null)
        int g = repo.saveGroup(0, p, 'G', null)
        int c = repo.saveCase(0, g, 'case', null, null, null)
        int col = repo.levels(p)[1].SolutionTestPlanLevelID
        repo.setCheck(c, col, true, 'tony')

        when:
        Map<String, Object> skip = repo.setSkip(c, col, true, 'tony')

        then:
        skip.IsSkipped == true
        skip.SkippedDate != null
        skip.SkippedByCode == 'tony'
        repo.skips(p)*.SolutionTestCaseID == [c]
        repo.checks(p).isEmpty()
        repo.plan(p).SkippedCount == 1

        when:
        Map<String, Object> unskip = repo.setSkip(c, col, false, 'tony')
        int otherCol = repo.levels(p)[0].SolutionTestPlanLevelID
        Map<String, Object> never = repo.setSkip(c, otherCol, false, 'tony')

        then:
        unskip.IsSkipped == false
        repo.skips(p).isEmpty()
        never.IsSkipped == false
        count('SELECT COUNT(*) FROM dbo.SolutionTestSkip WHERE SolutionTestCaseID = ? AND SolutionTestPlanLevelID = ?', c, otherCol) == 0
    }

    def "setCheck and setSkip should refuse when the case or column is missing or the column belongs to another checklist"() {

        given:
        int p = newPlan(repo, sid, null)
        int c = repo.saveCase(0, repo.saveGroup(0, p, 'G', null), 'case', null, null, null)
        int col = repo.levels(p)[0].SolutionTestPlanLevelID
        int foreignCol = repo.levels(newPlan(repo, sid, null))[0].SolutionTestPlanLevelID

        expect:
        ruleOf { repo.setCheck(-1, col, true, 'tony') }.message.contains('case no longer exists')
        ruleOf { repo.setCheck(c, -1, true, 'tony') }.message.contains('column no longer exists')
        ruleOf { repo.setCheck(c, foreignCol, true, 'tony') }.message.contains('different checklist')
        ruleOf { repo.setSkip(-1, col, true, 'tony') }.message.contains('case no longer exists')
        ruleOf { repo.setSkip(c, -1, true, 'tony') }.message.contains('column no longer exists')
        ruleOf { repo.setSkip(c, foreignCol, true, 'tony') }.message.contains('different checklist')
        count('SELECT COUNT(*) FROM dbo.SolutionTestCheck WHERE SolutionTestCaseID = ?', c) == 0
    }

    def "setResult should mark a case failing with a note, keep on null and clear on empty when results change"() {

        given:
        int p = newPlan(repo, sid, null)
        int c = repo.saveCase(0, repo.saveGroup(0, p, 'G', null), 'case', null, null, null)

        when:
        Map<String, Object> r1 = repo.setResult(c, true, '  Button missing  ', 'Rex')
        Map<String, Object> r2 = repo.setResult(c, null, 'Still missing', null)
        Map<String, Object> r3 = repo.setResult(c, true, null, 'tony')

        then:
        r1.IsFailing == true
        r1.FailingDate != null
        r1.ResultNote == 'Button missing'
        r1.ResultByCode == 'rex'
        r2.IsFailing == true
        r2.ResultNote == 'Still missing'
        r2.ResultByCode == 'rex'
        r3.FailingDate == r1.FailingDate
        r3.ResultByCode == 'tony'
        repo.cases(p)[0].IsFailing == true
        repo.cases(p)[0].ResultNote == 'Still missing'
        repo.cases(p)[0].ResultByName == 'Tony'

        when:
        Map<String, Object> r4 = repo.setResult(c, false, '', 'tony')

        then:
        r4.IsFailing == false
        r4.FailingDate == null
        r4.ResultNote == null
        ruleOf { repo.setResult(-1, true, null, 'tony') }.message.contains('case no longer exists')
    }

    def "counts should carry every bucket keyed by column and group when cells are ticked, skipped and failing"() {

        given:
        int p = newPlan(repo, sid, null)
        List<Integer> cols = repo.levels(p)*.SolutionTestPlanLevelID
        int g1 = repo.saveGroup(0, p, 'G1', null)
        int g2 = repo.saveGroup(0, p, 'G2', null)
        int a = repo.saveCase(0, g1, 'a', null, null, null)
        int b = repo.saveCase(0, g1, 'b', null, null, null)
        int c = repo.saveCase(0, g2, 'c', null, null, null)
        repo.setCheck(a, cols[0], true, 'tony')
        repo.setCheck(b, cols[0], true, 'tony')
        repo.setCheck(a, cols[1], true, 'tony')
        repo.setSkip(c, cols[1], true, 'tony')
        repo.setResult(b, true, 'x', 'tony')

        when:
        Map<String, Integer> n = repo.counts(p)

        then:
        n == [
            'cases': 3, 'checked': 3,
            ('level:' + cols[0]): 2, ('level:' + cols[1]): 1,
            ('group:' + g1): 2, ('group:' + g2): 1,
            ('groupchecked:' + g1): 3,
            ('grouplevel:' + g1 + '.' + cols[0]): 2, ('grouplevel:' + g1 + '.' + cols[1]): 1,
            'failing': 1, ('groupfailing:' + g1): 1,
            'skipped': 1, ('levelskipped:' + cols[1]): 1, ('groupskipped:' + g2): 1,
            ('grouplevelskipped:' + g2 + '.' + cols[1]): 1
        ]
        repo.plans(sid, null).find { it.SolutionTestPlanID == p }.CheckedCount == 3
    }

    def "clearChecks should clear one column, or every column and every failing mark when no column is given"() {

        given:
        int p = newPlan(repo, sid, null)
        List<Integer> cols = repo.levels(p)*.SolutionTestPlanLevelID
        int c = repo.saveCase(0, repo.saveGroup(0, p, 'G', null), 'c', null, null, null)
        repo.setCheck(c, cols[0], true, 'tony')
        repo.setCheck(c, cols[1], true, 'tony')
        repo.setResult(c, true, 'note kept', 'tony')

        expect:
        repo.clearChecks(p, cols[0]) == 1
        repo.checks(p)*.SolutionTestPlanLevelID == [cols[1]]
        repo.cases(p)[0].IsFailing == true
        repo.clearChecks(p, null) == 1
        repo.checks(p).isEmpty()
        repo.cases(p)[0].IsFailing == false
        repo.cases(p)[0].ResultNote == 'note kept'
        repo.clearChecks(p, 0) == 0
    }

    def "reorderCases and reorderGroups should put the listed ids first and keep the rest after when some are listed"() {

        given:
        int p = newPlan(repo, sid, null)
        int g1 = repo.saveGroup(0, p, 'G1', null)
        int g2 = repo.saveGroup(0, p, 'G2', null)
        int g3 = repo.saveGroup(0, p, 'G3', null)
        int a = repo.saveCase(0, g1, 'a', null, null, null)
        int b = repo.saveCase(0, g1, 'b', null, null, null)
        int c = repo.saveCase(0, g1, 'c', null, null, null)

        when:
        repo.reorderCases(g1, [c, a, null])
        repo.reorderGroups(p, [g3, g1])

        then:
        repo.cases(p).findAll { it.SolutionTestGroupID == g1 }*.SolutionTestCaseID == [c, a, b]
        repo.groups(p)*.SolutionTestGroupID == [g3, g1, g2]
    }

    def "reorderCases should never touch another group's cases when their ids are listed"() {

        given:
        int p = newPlan(repo, sid, null)
        int g1 = repo.saveGroup(0, p, 'G1', null)
        int g2 = repo.saveGroup(0, p, 'G2', null)
        int a = repo.saveCase(0, g1, 'a', null, null, null)
        int x = repo.saveCase(0, g2, 'x', null, 5, null)

        when:
        repo.reorderCases(g1, [x, a])

        then:
        repo.cases(p).find { it.SolutionTestCaseID == x }.SortOrder == 5
        repo.cases(p).find { it.SolutionTestCaseID == x }.SolutionTestGroupID == g2
    }

    def "deleteCase, deleteGroup and deletePlan should remove everything under them and audit it when a checklist is full"() {

        given:
        int p = newPlan(repo, sid, null)
        int col = repo.levels(p)[0].SolutionTestPlanLevelID
        int g = repo.saveGroup(0, p, 'G', null)
        int c1 = repo.saveCase(0, g, 'a', null, null, null)
        int c2 = repo.saveCase(0, g, 'b', null, null, null)
        [c1, c2].each { repo.setCheck(it, col, true, 'tony'); repo.setResult(it, true, 'n', 'tony') }
        repo.setSkip(c2, repo.levels(p)[1].SolutionTestPlanLevelID, true, 'tony')

        expect:
        repo.deleteCase(c1)
        !repo.deleteCase(c1)
        count('SELECT COUNT(*) FROM dbo.SolutionTestCheck WHERE SolutionTestCaseID = ?', c1) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTestResult WHERE SolutionTestCaseID = ?', c1) == 0
        logRows('SolutionTestCase', 'SolutionTestCaseID', c1).last().DeletedDate != null

        repo.deleteGroup(g)
        !repo.deleteGroup(g)
        repo.cases(p).isEmpty()
        count('SELECT COUNT(*) FROM dbo.SolutionTestSkip WHERE SolutionTestCaseID = ?', c2) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTestResult WHERE SolutionTestCaseID = ?', c2) == 0

        repo.deletePlan(p)
        !repo.deletePlan(p)
        repo.plan(p) == null
        repo.levels(p).isEmpty()
        logRows('SolutionTestPlan', 'SolutionTestPlanID', p).last().DeletedDate != null
        count('SELECT COUNT(*) FROM dbo.SolutionTestPlanLevel_LOG WHERE SolutionTestPlanID = ? AND DeletedDate IS NOT NULL', p) == 2
    }

    def "locate should answer the solution and checklist for a plan, column, group or case and null otherwise"() {

        given:
        int p = newPlan(repo, sid, null)
        int col = repo.levels(p)[0].SolutionTestPlanLevelID
        int g = repo.saveGroup(0, p, 'G', null)
        int c = repo.saveCase(0, g, 'c', null, null, null)

        expect:
        [repo.locate('plan', p), repo.locate('level', col), repo.locate(' GROUP ', g), repo.locate('case', c)].every {
            it.solutionId() == sid && it.planId() == p
        }
        repo.locate('plan', -1) == null
        repo.locate('solution', sid) == null
        repo.locate(null, p) == null
    }

    def "setCheck and setResult should write SolutionTestCheck_LOG and SolutionTestResult_LOG rows when cells change"() {

        given:
        int p = newPlan(repo, sid, null)
        int col = repo.levels(p)[0].SolutionTestPlanLevelID
        int c = repo.saveCase(0, repo.saveGroup(0, p, 'G', null), 'c', null, null, null)

        when:
        repo.setCheck(c, col, true, 'tony')
        repo.setCheck(c, col, false, 'tony')
        repo.setResult(c, true, 'n', 'tony')

        then:
        count('SELECT COUNT(*) FROM dbo.SolutionTestCheck_LOG WHERE SolutionTestCaseID = ?', c) == 2
        count('SELECT COUNT(*) FROM dbo.SolutionTestResult_LOG WHERE SolutionTestCaseID = ?', c) == 1
    }

    private static int newPlan(JdbcSolutionTestRepository repo, int solutionId, String kind) {
        repo.savePlan(0, solutionId, 'Plan ' + uniq(), 'Desc', null, 'tony', kind)
    }
}
