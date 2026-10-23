package com.echo

import java.time.LocalDateTime

import com.echo.SolutionTestModels.Group
import com.echo.SolutionTestModels.Location
import com.echo.SolutionTestModels.Plan
import com.echo.SolutionTestModels.PlanSummary

import spock.lang.Unroll

/**
 * Story 5653: the testing checklists' business rules -- access on every call, the whole checklist assembled
 * with its counters, merges, the one-of-two tick rule, and the same-checklist guards.
 */
class SolutionTestServiceSpec extends SolutionServiceSupport {

    static final LocalDateTime T = getLocalDateTime('09/25/2026 14:30')

    SolutionTestRepository repo = Mock()
    SolutionTestService service = new SolutionTestService(repo, access)

    Map<String, Location> locs = [
        'plan:1': new Location(1, 1), 'plan:2': new Location(2, 2), 'plan:3': new Location(1, 3),
        'level:11': new Location(1, 1), 'level:12': new Location(1, 1), 'level:13': new Location(2, 2), 'level:14': new Location(1, 3),
        'group:21': new Location(1, 1), 'group:23': new Location(1, 1), 'group:22': new Location(2, 2), 'group:24': new Location(1, 3),
        'case:31': new Location(1, 1), 'case:33': new Location(1, 1), 'case:32': new Location(2, 2)]

    Map<Integer, Map<String, Object>> plans = [
        1: [SolutionTestPlanID: 1, SolutionID: 1, PlanTitle: 'Smoke', PlanDescription: 'desc', SortOrder: 1, PlanKindCode: 'qa ',
            CaseCount: 2, LevelCount: 2, ModifiedDate: T, ModifiedBy: 'tony'],
        2: [SolutionTestPlanID: 2, SolutionID: 2, PlanTitle: 'Other'],
        3: [SolutionTestPlanID: 3, SolutionID: 1, PlanTitle: 'Third', PlanKindCode: 'dev']]
    Map<Integer, List<Map<String, Object>>> levels = [
        1: [[SolutionTestPlanLevelID: 11, SolutionTestLevelID: 1, LevelTitle: 'Manual', SortOrder: 1],
            [SolutionTestPlanLevelID: 12, SolutionTestLevelID: 2, LevelTitle: 'Unit tested', SortOrder: 2]]]
    Map<Integer, List<Map<String, Object>>> groups = [
        1: [[SolutionTestGroupID: 21, GroupTitle: 'Login', SortOrder: 1], [SolutionTestGroupID: 23, GroupTitle: 'Empty', SortOrder: 2]]]
    Map<Integer, List<Map<String, Object>>> cases = [
        1: [[SolutionTestCaseID: 31, SolutionTestGroupID: 21, CaseText: 'Sign in', CaseNote: 'note', SortOrder: 1, IncludeInPrint: null,
             IsFailing: 1, FailingDate: T, ResultNote: 'boom', ResultByName: 'Tony'],
            [SolutionTestCaseID: 33, SolutionTestGroupID: 21, CaseText: 'Sign out', SortOrder: 2, IncludeInPrint: false]]]
    Map<Integer, List<Map<String, Object>>> checks = [
        1: [[SolutionTestCaseID: 31, SolutionTestPlanLevelID: 11, IsChecked: true, CheckedByCode: 'tony', CheckedDate: T]]]
    Map<Integer, List<Map<String, Object>>> skips = [
        1: [[SolutionTestCaseID: 33, SolutionTestPlanLevelID: 12, SkippedByCode: 'rex']]]
    Map<String, Integer> counts1 = [cases: 2, checked: 1, skipped: 1, failing: 1, 'level:11': 1, 'levelskipped:12': 1, 'group:21': 2,
                                    'groupchecked:21': 1, 'groupskipped:21': 1, 'groupfailing:21': 1, 'grouplevel:21.11': 1,
                                    'grouplevelskipped:21.12': 1]

    def setup() {
        repo.locate(_, _) >> { String kind, int id -> locs[kind + ':' + id] }
        repo.plan(_) >> { int p -> plans[p] }
        repo.levels(_) >> { int p -> levels[p] ?: [] }
        repo.groups(_) >> { int p -> groups[p] ?: [] }
        repo.cases(_) >> { int p -> cases[p] ?: [] }
        repo.checks(_) >> { int p -> checks[p] ?: [] }
        repo.skips(_) >> { int p -> skips[p] ?: [] }
        repo.counts(_) >> { int p -> p == 1 ? counts1 : [:] }
        repo.plans(_, _) >> { int s, String k -> plans.values().findAll { it.SolutionID == s }.collect { it } }
    }

    /** Runs one named write as the caller -- the actions a data table can name. */
    private static Object perform(SolutionTestService s, SolutionCaller c, String action) {
        switch (action) {
            case 'createPlan':     return s.createPlan(c, 1, 'x', null, null)
            case 'updatePlan':     return s.updatePlan(c, 1, 'x', null, null)
            case 'addLevel':       return s.addLevel(c, 1, 'x')
            case 'updateLevel':    return s.updateLevel(c, 11, 'x', null)
            case 'deleteLevel':    return s.deleteLevel(c, 11)
            case 'addGroup':       return s.addGroup(c, 1, 'x')
            case 'updateGroup':    return s.updateGroup(c, 21, 'x', null)
            case 'deleteGroup':    return s.deleteGroup(c, 21)
            case 'addCase':        return s.addCase(c, 21, 'x', null)
            case 'updateCase':     return s.updateCase(c, 31, null, 'x', null, null, null)
            case 'deleteCase':     return s.deleteCase(c, 31)
            case 'reorderCases':   return s.reorderCases(c, 21, [31])
            case 'reorderGroups':  return s.reorderGroups(c, 1, [21])
            case 'setCheck':       return s.setCheck(c, 31, 11, true, null)
            case 'setResult':      return s.setResult(c, 31, true, null)
            case 'clear':          return s.clear(c, 1, null)
            default: throw new IllegalStateException(action)
        }
    }

    /** Runs one named call against a hidden or missing thing -- the refusals a data table can name. */
    private static Object refuse(SolutionTestService s, String action) {
        switch (action) {
            case 'addLevel hidden':        return s.addLevel(REX, 2, 'x')
            case 'addLevel zero':          return s.addLevel(TONY, 0, 'x')
            case 'updateLevel hidden':     return s.updateLevel(REX, 13, 'x', null)
            case 'updateLevel missing':    return s.updateLevel(TONY, 99, 'x', null)
            case 'deleteLevel hidden':     return s.deleteLevel(ZED, 11)
            case 'addGroup hidden':        return s.addGroup(ZED, 1, 'x')
            case 'updateGroup hidden':     return s.updateGroup(REX, 22, 'x', null)
            case 'updateGroup zero':       return s.updateGroup(TONY, 0, 'x', null)
            case 'deleteGroup hidden':     return s.deleteGroup(REX, 22)
            case 'addCase hidden':         return s.addCase(REX, 22, 'x', null)
            case 'addCase missing':        return s.addCase(TONY, 98, 'x', null)
            case 'updateCase hidden':      return s.updateCase(REX, 32, null, 'x', null, null, null)
            case 'updateCase zero':        return s.updateCase(TONY, 0, null, 'x', null, null, null)
            case 'deleteCase hidden':      return s.deleteCase(ZED, 31)
            case 'reorderCases hidden':    return s.reorderCases(REX, 22, [1])
            case 'reorderGroups hidden':   return s.reorderGroups(REX, 2, [1])
            case 'setCheck hidden':        return s.setCheck(REX, 32, 13, true, null)
            case 'setCheck no column':     return s.setCheck(TONY, 31, 99, true, null)
            case 'setCheck no case':       return s.setCheck(TONY, 99, 11, true, null)
            case 'setResult hidden':       return s.setResult(REX, 32, true, null)
            case 'clear hidden':           return s.clear(REX, 2, null)
            case 'clear no column':        return s.clear(TONY, 1, 99)
            default: throw new IllegalStateException(action)
        }
    }

    // ---- reads ----------------------------------------------------------------------

    @Unroll
    def "list should ask for kind #expected when #scenario"() {

        when:
        List<PlanSummary> l = service.list(REX, 1, kind)

        then:
        1 * repo.plans(1, expected) >> [plans[1]]
        l*.id() == [1]
        l[0].kind() == 'qa'
        l[0].cellCount() == 4

        where:
        scenario               | kind   || expected
        'no kind is sent'      | null   || null
        'a blank kind is sent' | ''     || null
        'a padded QA is sent'  | ' QA ' || 'qa'
        'dev is sent'          | 'dev'  || 'dev'
    }

    def "list should refuse an unknown kind before it reads anything"() {

        when:
        service.list(TONY, 1, 'uat')

        then:
        thrown(IllegalArgumentException)
        0 * repo.plans(*_)
    }

    @Unroll
    def "list should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.list(caller, solutionId, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.plans(*_)

        where:
        scenario                        | callerName | solutionId || expectedMessage
        'the id is zero'                | 'TONY'     | 0          || 'Solution not found.'
        'the solution does not exist'   | 'TONY'     | 9          || 'Solution not found.'
        'the client was not given it'   | 'REX'      | 2          || 'Solution not found.'
        'the client was given nothing'  | 'ZED'      | 1          || 'Solution not found.'
    }

    def "plan should assemble the whole checklist with every counter when it is visible"() {

        when:
        Plan p = service.plan(TONY, 1)

        then:
        p.id() == 1
        p.solutionId() == 1
        p.title() == 'Smoke'
        p.description() == 'desc'
        p.kind() == 'qa'
        p.caseCount() == 2
        p.checkedCount() == 1
        p.skippedCount() == 1
        p.failingCount() == 1
        p.cellCount() == 4
        p.modifiedDate() == T
        p.modifiedBy() == 'tony'
        p.counts() == counts1

        and: 'the columns'
        p.columns()*.id() == [11, 12]
        p.columns()*.title() == ['Manual', 'Unit tested']
        p.columns()[0].checkedCount() == 1
        p.columns()[1].skippedCount() == 1
    }

    def "plan should give each group per-column counters and its cases when groups exist"() {

        when:
        Plan p = service.plan(TONY, 1)
        Group login = p.groups()[0]

        then:
        p.groups()*.id() == [21, 23]
        login.caseCount() == 2
        login.checkedCount() == 1
        login.skippedCount() == 1
        login.failingCount() == 1
        login.checkedByColumn() == [11: 1, 12: 0]
        login.skippedByColumn() == [11: 0, 12: 1]
        p.groups()[1].cases() == []
        p.groups()[1].checkedByColumn() == [11: 0, 12: 0]
        login.cases()*.id() == [31, 33]
        login.cases()[0].checks()*.columnId() == [11]
        login.cases()[0].skips() == []
        login.cases()[0].failing()
        login.cases()[0].resultNote() == 'boom'
        login.cases()[0].includeInPrint()
        login.cases()[1].skips()*.columnId() == [12]
        login.cases()[1].checks() == []
        !login.cases()[1].includeInPrint()
        !login.cases()[1].failing()
    }

    def "plan should answer zeros when the checklist has no rows or counters"() {

        when:
        Plan p = service.plan(TONY, 3)

        then:
        p.kind() == 'dev'
        p.caseCount() == 0
        p.cellCount() == 0
        p.columns() == []
        p.groups() == []
    }

    @Unroll
    def "plan should throw not found when #scenario"() {

        given:
        locs['plan:7'] = new Location(1, 7)
        SolutionCaller caller = callerOf(callerName)

        when:
        service.plan(caller, planId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                                 | callerName | planId || expectedMessage
        'the id is zero'                         | 'TONY'     | 0      || 'Testing checklist not found.'
        'the id is negative'                     | 'TONY'     | -2     || 'Testing checklist not found.'
        'the checklist does not exist'           | 'TONY'     | 99     || 'Testing checklist not found.'
        'the checklist vanished after locating'  | 'TONY'     | 7      || 'Testing checklist not found.'
        'the client was not given it'            | 'REX'      | 2      || 'Solution not found.'
        'the client was given nothing'           | 'ZED'      | 1      || 'Solution not found.'
    }

    // ---- checklists ---------------------------------------------------------------

    @Unroll
    def "createPlan should save a #expectedKind checklist attributed to #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        Plan p = service.createPlan(caller, 1, 'New', 'd', kind)

        then:
        1 * repo.savePlan(0, 1, 'New', 'd', null, expectedCode, expectedKind) >> 3
        p.id() == 3

        where:
        scenario                         | callerName | kind   || expectedCode | expectedKind
        'a person sends no kind'         | 'TONY'     | null   || 'tony'       | 'dev'
        'the house sends a blank kind'   | 'HOUSE'    | ''     || 'felix'      | 'dev'
        'a client asks for QA'           | 'REX'      | 'QA'   || 'rex'        | 'qa'
        'a person sends a padded dev'    | 'TONY'     | ' dev' || 'tony'       | 'dev'
    }

    @Unroll
    def "createPlan should save nothing and throw #expectedException when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.createPlan(caller, solutionId, 'New', null, kind)

        then:
        RuntimeException ex = thrown()
        ex.getClass().simpleName == expectedException
        0 * repo.savePlan(*_)

        where:
        scenario                         | callerName | solutionId | kind   || expectedException
        'the kind is unknown'            | 'TONY'     | 1          | 'prod' || 'IllegalArgumentException'
        'the client was not given it'    | 'REX'      | 2          | null   || 'SolutionNotFoundException'
        'the solution id is zero'        | 'TONY'     | 0          | null   || 'SolutionNotFoundException'
    }

    @Unroll
    def "createPlan should throw not found when the save answers a #scenario id"() {

        when:
        service.createPlan(TONY, 1, 'New', null, null)

        then:
        1 * repo.savePlan(*_) >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario       | savedId || expectedMessage
        'zero'         | 0       || 'Testing checklist not found.'
        'negative'     | -1      || 'Testing checklist not found.'
    }

    @Unroll
    def "updatePlan should merge and keep the kind when #scenario"() {

        when:
        service.updatePlan(HOUSE, 1, title, description, 7)

        then:
        1 * repo.savePlan(1, 1, expectedTitle, expectedDescription, 7, 'felix', 'qa')

        where:
        scenario                 | title | description || expectedTitle | expectedDescription
        'both are left out'      | null  | null        || 'Smoke'       | 'desc'
        'both are blank'         | ' '   | ''          || 'Smoke'       | ''
        'both are sent'          | 'New' | 'More'      || 'New'         | 'More'
    }

    def "updatePlan and deletePlan should touch nothing when the checklist is hidden"() {

        when:
        service.updatePlan(REX, 2, 'x', null, null)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.savePlan(*_)

        when:
        service.deletePlan(REX, 2)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deletePlan(_)
    }

    def "deletePlan should delete and throw not found when nothing was deleted"() {

        when:
        service.deletePlan(REX, 1)

        then:
        1 * repo.deletePlan(1) >> true
        noExceptionThrown()

        when:
        service.deletePlan(TONY, 3)

        then:
        1 * repo.deletePlan(3) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Testing checklist not found.'
    }

    // ---- columns, groups, cases ------------------------------------------------------

    def "addLevel, addGroup and addCase should save new rows on the right checklist when visible"() {

        when:
        Plan a = service.addLevel(TONY, 1, 'Regression')
        Plan b = service.addGroup(TONY, 1, 'Billing')
        Plan c = service.addCase(REX, 21, 'Pay', 'with a card')

        then:
        1 * repo.saveLevel(0, 1, 'Regression', null)
        1 * repo.saveGroup(0, 1, 'Billing', null)
        1 * repo.saveCase(0, 21, 'Pay', 'with a card', null, null)
        [a, b, c]*.id() == [1, 1, 1]
    }

    @Unroll
    def "updateLevel and updateGroup should merge their titles when #scenario"() {

        when:
        service.updateLevel(TONY, 12, title, 3)
        service.updateGroup(TONY, 21, title, 4)

        then:
        1 * repo.saveLevel(12, 1, expectedLevel, 3)
        1 * repo.saveGroup(21, 1, expectedGroup, 4)

        where:
        scenario                | title || expectedLevel | expectedGroup
        'the title is null'     | null  || 'Unit tested' | 'Login'
        'the title is blank'    | '  '  || 'Unit tested' | 'Login'
        'a new title is sent'   | 'X'   || 'X'           | 'X'
    }

    def "deleteLevel, deleteGroup and deleteCase should answer the checklist when they delete"() {

        when:
        service.deleteLevel(TONY, 11)
        service.deleteGroup(TONY, 23)
        service.deleteCase(TONY, 33)

        then:
        1 * repo.deleteLevel(11) >> true
        1 * repo.deleteGroup(23) >> true
        1 * repo.deleteCase(33) >> true
    }

    @Unroll
    def "#action should throw #expectedMessage when nothing was deleted for #scenario"() {

        given:
        repo.deleteLevel(_) >> false
        repo.deleteGroup(_) >> false
        repo.deleteCase(_) >> false

        when:
        perform(service, TONY, action)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario           | action        || expectedMessage
        'a column'         | 'deleteLevel' || 'Column not found.'
        'a group'          | 'deleteGroup' || 'Group not found.'
        'a test case'      | 'deleteCase'  || 'Test case not found.'
    }

    @Unroll
    def "#seq. #action should throw #expectedMessage and write nothing when #scenario"() {

        when:
        refuse(service, action)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveLevel(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveCase(*_)
        0 * repo.deleteLevel(_)
        0 * repo.deleteGroup(_)
        0 * repo.deleteCase(_)
        0 * repo.reorderCases(*_)
        0 * repo.reorderGroups(*_)
        0 * repo.setCheck(*_)
        0 * repo.setSkip(*_)
        0 * repo.setResult(*_)
        0 * repo.clearChecks(*_)

        where:
        seq | scenario                         | action                  || expectedMessage
        1   | 'add a column, hidden'           | 'addLevel hidden'       || 'Solution not found.'
        2   | 'add a column, plan zero'        | 'addLevel zero'         || 'Testing checklist not found.'
        3   | 'rename a column, hidden'        | 'updateLevel hidden'    || 'Solution not found.'
        4   | 'rename a column, missing'       | 'updateLevel missing'   || 'Column not found.'
        5   | 'delete a column, hidden'        | 'deleteLevel hidden'    || 'Solution not found.'
        6   | 'add a group, hidden'            | 'addGroup hidden'       || 'Solution not found.'
        7   | 'rename a group, hidden'         | 'updateGroup hidden'    || 'Solution not found.'
        8   | 'rename a group, zero'           | 'updateGroup zero'      || 'Group not found.'
        9   | 'delete a group, hidden'         | 'deleteGroup hidden'    || 'Solution not found.'
        10  | 'add a case, hidden'             | 'addCase hidden'        || 'Solution not found.'
        11  | 'add a case, missing group'      | 'addCase missing'       || 'Group not found.'
        12  | 'change a case, hidden'          | 'updateCase hidden'     || 'Solution not found.'
        13  | 'change a case, zero'            | 'updateCase zero'       || 'Test case not found.'
        14  | 'delete a case, hidden'          | 'deleteCase hidden'     || 'Solution not found.'
        15  | 'reorder cases, hidden'          | 'reorderCases hidden'   || 'Solution not found.'
        16  | 'reorder groups, hidden'         | 'reorderGroups hidden'  || 'Solution not found.'
        17  | 'tick, hidden'                   | 'setCheck hidden'       || 'Solution not found.'
        18  | 'tick, missing column'           | 'setCheck no column'    || 'Column not found.'
        19  | 'tick, missing case'             | 'setCheck no case'      || 'Test case not found.'
        20  | 'result, hidden'                 | 'setResult hidden'      || 'Solution not found.'
        21  | 'clear, hidden'                  | 'clear hidden'          || 'Solution not found.'
        22  | 'clear, missing column'          | 'clear no column'       || 'Column not found.'
    }

    @Unroll
    def "updateCase should merge and pass includeInPrint through when #scenario"() {

        when:
        service.updateCase(TONY, 31, null, text, note, 2, print)

        then:
        1 * repo.saveCase(31, 21, expectedText, expectedNote, 2, print)

        where:
        scenario                 | text  | note | print || expectedText | expectedNote
        'all are left out'       | null  | null | null  || 'Sign in'    | 'note'
        'all are cleared'        | ''    | ''   | false || 'Sign in'    | ''
        'all are sent'           | 'New' | 'N'  | true  || 'New'        | 'N'
    }

    @Unroll
    def "updateCase should save into group #expectedGroup when #scenario"() {

        when:
        service.updateCase(TONY, 31, groupId, null, null, null, null)

        then:
        1 * repo.saveCase(31, expectedGroup, *_)

        where:
        scenario                         | groupId || expectedGroup
        'no group is sent'               | null    || 21
        'group zero is sent'             | 0       || 21
        'a negative group is sent'       | -1      || 21
        'the same group is sent'         | 21      || 21
        'another group of the checklist' | 23      || 23
    }

    @Unroll
    def "updateCase should refuse the move when #scenario"() {

        when:
        service.updateCase(TONY, 31, groupId, null, null, null, null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'A test case can only move to a group in the same checklist.'
        0 * repo.saveCase(*_)

        where:
        scenario                                       | groupId || unused
        'the group is of another checklist'            | 24      || null
        'the group is of another solution'             | 22      || null
        'the group does not exist'                     | 404     || null
    }

    def "updateCase should throw not found when the checklist does not list the case"() {

        given:
        locs['case:35'] = new Location(1, 1)

        when:
        service.updateCase(TONY, 35, null, 'x', null, null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Test case not found.'
        0 * repo.saveCase(*_)
    }

    def "reorderCases and reorderGroups should pass the ids and an empty list when none are sent"() {

        when:
        service.reorderCases(TONY, 21, [33, 31])
        service.reorderCases(TONY, 21, null)
        service.reorderGroups(TONY, 1, [23, 21])
        service.reorderGroups(TONY, 1, null)

        then:
        1 * repo.reorderCases(21, [33, 31])
        1 * repo.reorderCases(21, [])
        1 * repo.reorderGroups(1, [23, 21])
        1 * repo.reorderGroups(1, [])
    }

    // ---- ticks ----------------------------------------------------------------------

    @Unroll
    def "setCheck should refuse before any lookup when #scenario"() {

        when:
        service.setCheck(TONY, 31, 11, checked, skipped)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.locate(*_)

        where:
        scenario                        | checked | skipped || expectedMessage
        'neither is sent'               | null    | null    || 'Send checked (true or false) or skipped (true or false) -- one of the two.'
        'both are true'                 | true    | true    || 'Send checked (true or false) or skipped (true or false) -- one of the two.'
        'both are false'                | false   | false   || 'Send checked (true or false) or skipped (true or false) -- one of the two.'
        'one is true, one is false'     | true    | false   || 'Send checked (true or false) or skipped (true or false) -- one of the two.'
    }

    @Unroll
    def "setCheck should tick or skip one cell attributed to #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.setCheck(caller, 31, 12, checked, skipped)

        then:
        expectedTicks * repo.setCheck(31, 12, true, expectedCode)
        expectedUnticks * repo.setCheck(31, 12, false, expectedCode)
        expectedSkips * repo.setSkip(31, 12, true, expectedCode)
        expectedUnskips * repo.setSkip(31, 12, false, expectedCode)

        where:
        scenario                 | callerName | checked | skipped || expectedCode | expectedTicks | expectedUnticks | expectedSkips | expectedUnskips
        'a person ticks'         | 'TONY'     | true    | null    || 'tony'       | 1             | 0               | 0             | 0
        'the house unticks'      | 'HOUSE'    | false   | null    || 'felix'      | 0             | 1               | 0             | 0
        'a client skips'         | 'REX'      | null    | true    || 'rex'        | 0             | 0               | 1             | 0
        'a person unskips'       | 'TONY'     | null    | false   || 'tony'       | 0             | 0               | 0             | 1
    }

    def "setCheck should answer 409 when the column belongs to another checklist"() {

        when:
        service.setCheck(TONY, 31, 14, true, null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That column belongs to a different checklist.'
        0 * repo.setCheck(*_)
    }

    @Unroll
    def "setResult should pass a merge through when #scenario"() {

        when:
        service.setResult(REX, 31, failing, note)

        then:
        1 * repo.setResult(31, failing, note, 'rex')

        where:
        scenario                      | failing | note           || unused
        'failing is set'              | true    | null           || null
        'failing is cleared'          | false   | null           || null
        'only a note is sent'         | null    | 'still broken' || null
        'the note is cleared'         | null    | ''             || null
        'both are sent'               | true    | 'why'          || null
    }

    def "setResult should refuse the call when neither failing nor a note is sent"() {

        when:
        service.setResult(TONY, 31, null, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'failing (true or false) or note is required.'
        0 * repo.setResult(*_)
    }

    @Unroll
    def "clear should clear with #expectedLookups column lookups when #scenario"() {

        when:
        service.clear(TONY, 1, levelId)

        then:
        expectedLookups * repo.locate('level', _) >> new Location(1, 1)
        1 * repo.clearChecks(1, levelId)

        where:
        scenario                      | levelId || expectedLookups
        'no column is sent'           | null    || 0
        'column zero is sent'         | 0       || 0
        'a negative column is sent'   | -4      || 0
        'one column is sent'          | 12      || 1
    }

    def "clear should answer 409 when the column belongs to another checklist"() {

        when:
        service.clear(TONY, 1, 14)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That column belongs to a different checklist.'
        0 * repo.clearChecks(*_)
    }

    @Unroll
    def "#seq. #action should answer the whole checklist when it succeeds at #scenario"() {

        given:
        repo.deleteLevel(_) >> true
        repo.deleteGroup(_) >> true
        repo.deleteCase(_) >> true
        repo.savePlan(*_) >> 1

        when:
        Plan p = (Plan) perform(service, TONY, action)

        then:
        p.id() == expectedId

        where:
        seq | scenario            | action          || expectedId
        1   | 'create'            | 'createPlan'    || 1
        2   | 'rename'            | 'updatePlan'    || 1
        3   | 'add a column'      | 'addLevel'      || 1
        4   | 'rename a column'   | 'updateLevel'   || 1
        5   | 'delete a column'   | 'deleteLevel'   || 1
        6   | 'add a group'       | 'addGroup'      || 1
        7   | 'rename a group'    | 'updateGroup'   || 1
        8   | 'delete a group'    | 'deleteGroup'   || 1
        9   | 'add a case'        | 'addCase'       || 1
        10  | 'change a case'     | 'updateCase'    || 1
        11  | 'delete a case'     | 'deleteCase'    || 1
        12  | 'reorder cases'     | 'reorderCases'  || 1
        13  | 'reorder groups'    | 'reorderGroups' || 1
        14  | 'tick'              | 'setCheck'      || 1
        15  | 'mark failing'      | 'setResult'     || 1
        16  | 'clear'             | 'clear'         || 1
    }
}
