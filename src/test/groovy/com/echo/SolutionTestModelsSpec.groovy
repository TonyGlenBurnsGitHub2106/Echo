package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionTestModels.Case
import com.echo.SolutionTestModels.Check
import com.echo.SolutionTestModels.Level
import com.echo.SolutionTestModels.Location
import com.echo.SolutionTestModels.PlanSummary
import com.echo.SolutionTestModels.Skip

import spock.lang.Unroll

/**
 * Story 5653: the testing checklists' row mappers and the dev | qa kind rule.
 */
class SolutionTestModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('09/25/2026 14:30')

    def "the kind constants should be dev and qa when read"() {

        expect:
        SolutionTestModels.KIND_DEV == 'dev'
        SolutionTestModels.KIND_QA == 'qa'
    }

    @Unroll
    def "kindOrNull should answer #expected when #scenario"() {

        expect:
        SolutionTestModels.kindOrNull(kind) == expected

        where:
        scenario                 | kind     || expected
        'the kind is null'       | null     || null
        'the kind is empty'      | ''       || null
        'the kind is blank'      | '   '    || null
        'the kind is dev'        | 'dev'    || 'dev'
        'the kind is qa'         | 'qa'     || 'qa'
        'the kind is padded QA'  | ' QA '   || 'qa'
        'the kind is Dev'        | 'Dev'    || 'dev'
    }

    @Unroll
    def "kindOrNull should refuse the kind quoting it trimmed when #scenario"() {

        when:
        SolutionTestModels.kindOrNull(kind)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                      | kind       || expectedMessage
        'it is prod'                  | 'prod'     || 'A checklist kind is dev or qa, not "prod".'
        'it is a padded uat'          | ' uat '    || 'A checklist kind is dev or qa, not "uat".'
        'it has spaces inside'        | 'd e v'    || 'A checklist kind is dev or qa, not "d e v".'
        'it carries an injection'     | 'qa;drop'  || 'A checklist kind is dev or qa, not "qa;drop".'
        'it joins both kinds'         | 'devqa'    || 'A checklist kind is dev or qa, not "devqa".'
    }

    def "PlanSummary.of should read every column and work out the cell count when the row is complete"() {

        when:
        PlanSummary s = PlanSummary.of([SolutionTestPlanID: 5, SolutionID: 1, PlanTitle: 'Smoke', PlanDescription: 'd', SortOrder: 2,
                                        PlanKindCode: ' qa ', GroupCount: 3, CaseCount: 7, LevelCount: 2, CheckedCount: 4, SkippedCount: 1,
                                        CreatedDate: Timestamp.valueOf(T), ModifiedDate: T, ModifiedBy: 'rex'])

        then:
        s == new PlanSummary(5, 1, 'Smoke', 'd', 2, 'qa', 3, 7, 2, 4, 1, 14, T, T, 'rex')
    }

    def "PlanSummary.of should default to a dev checklist when the row is empty"() {

        expect:
        PlanSummary.of([:]) == new PlanSummary(0, 0, null, null, 0, 'dev', 0, 0, 0, 0, 0, 0, null, null, null)
    }

    def "Level.of should take its counters from the count map by its own id when counters exist"() {

        expect:
        Level.of([SolutionTestPlanLevelID: 11, SolutionTestLevelID: 1, LevelTitle: 'Manual', SortOrder: 1],
                 ['level:11': 4, 'levelskipped:11': 2, 'level:12': 9]) == new Level(11, 1, 'Manual', 1, 4, 2)
        Level.of([SolutionTestPlanLevelID: 12], [:]) == new Level(12, 0, null, 0, 0, 0)
    }

    def "Check.of and Skip.of should read every column when present"() {

        expect:
        Check.of([SolutionTestCaseID: 31, SolutionTestPlanLevelID: 11, IsChecked: 1, CheckedDate: T, CheckedByCode: 'tony',
                  CheckedByName: 'Tony']) == new Check(31, 11, true, T, 'tony', 'Tony')
        Check.of([:]) == new Check(0, 0, false, null, null, null)
        Skip.of([SolutionTestCaseID: 31, SolutionTestPlanLevelID: 12, SkippedDate: '2026-09-25 14:30:00', SkippedByCode: 'rex',
                 SkippedByName: 'Rex']) == new Skip(31, 12, T, 'rex', 'Rex')
    }

    @Unroll
    def "Case.of should answer print #expectedPrint and failing #expectedFailing when #scenario"() {

        given:
        Map<String, Object> row = [:]
        if (setPrint) {
            row.IncludeInPrint = printValue
        }
        if (setFailing) {
            row.IsFailing = failingValue
        }

        when:
        Case c = Case.of(row, [], [])

        then:
        c.includeInPrint() == expectedPrint
        c.failing() == expectedFailing

        where:
        scenario                        | setPrint | printValue | setFailing | failingValue || expectedPrint | expectedFailing
        'neither column is present'     | false    | null       | false      | null         || true          | false
        'both columns are null'         | true     | null       | true       | null         || true          | false
        'both are Boolean true'         | true     | true       | true       | true         || true          | true
        'both are the number 1'         | true     | 1          | true       | 1            || true          | true
        'both are Boolean false'        | true     | false      | true       | false        || false         | false
        'both are the number 0'         | true     | 0          | true       | 0            || false         | false
        'both are unreadable text'      | true     | 'false'    | true       | 'x'          || false         | false
    }

    def "Case.of should read every column and keep its checks and skips when the row is complete"() {

        given:
        List<Check> checks = [new Check(31, 11, true, T, 'tony', 'Tony')]
        List<Skip> skips = [new Skip(31, 12, T, 'rex', 'Rex')]

        expect:
        Case.of([SolutionTestCaseID: 31, SolutionTestGroupID: 21, CaseText: 'Sign in', CaseNote: 'How', SortOrder: 3,
                 IncludeInPrint: true, IsFailing: true, FailingDate: T, ResultNote: 'Boom', ResultModifiedDate: T,
                 ResultByName: 'Tony'], checks, skips) ==
            new Case(31, 21, 'Sign in', 'How', 3, true, true, T, 'Boom', T, 'Tony', checks, skips)
    }

    def "Location should compare by value when built alike"() {

        expect:
        new Location(1, 2) == new Location(1, 2)
        new Location(1, 2) != new Location(2, 1)
        new Location(1, 2).planId() == 2
    }
}
