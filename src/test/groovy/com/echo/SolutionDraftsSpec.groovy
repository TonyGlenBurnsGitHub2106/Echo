package com.echo

import java.time.LocalDateTime

import com.echo.SolutionDrafts.ItemDraft
import com.echo.SolutionDrafts.LogDraft
import com.echo.SolutionDrafts.LogFilter
import com.echo.SolutionDrafts.NoteDraft
import com.echo.SolutionDrafts.ProjectDraft
import com.echo.SolutionDrafts.SolutionDraft
import com.echo.SolutionDrafts.StepDraft

import spock.lang.Unroll

// Story 5653: SolutionDrafts -- the write records, and the journal filter that normalizes itself.
class SolutionDraftsSpec extends EchoSpockBaseSpec {

    def "LogFilter should keep every value exactly when the values are sensible"() {

        when:
        LogFilter f = new LogFilter(1, 2, 3, 'rex', 'command', 4, 'mvn', true, 10, 25)

        then:
        f.solutionId() == 1
        f.projectId() == 2
        f.itemId() == 3
        f.authorCode() == 'rex'
        f.typeCode() == 'command'
        f.sinceSequence() == 4
        f.search() == 'mvn'
        f.ascending()
        f.offset() == 10
        f.pageSize() == 25
    }

    @Unroll
    def "LogFilter should turn the text filters into #expected when #scenario"() {

        when:
        LogFilter f = new LogFilter(1, null, null, value, value, null, value, false, 0, 0)

        then:
        f.authorCode() == expected
        f.typeCode() == expected
        f.search() == expected

        where:
        scenario                 | value    || expected
        "the text is null"       | null     || null
        "the text is empty"      | ""       || null
        "the text is spaces"     | "   "    || null
        "the text is whitespace" | "\t\n"   || null
        "the text is padded"     | " rex "  || "rex"
    }

    @Unroll
    def "LogFilter should drop the id filters when #scenario"() {

        given:
        Integer id = value == null ? null : ((Number) value).intValue()

        when:
        LogFilter f = new LogFilter(1, id, id, null, null, id, null, false, 0, 0)

        then:
        f.projectId() == expected
        f.itemId() == expected
        f.sinceSequence() == expected

        where:
        scenario                | value       || expected
        "the id is null"        | null        || null
        "the id is zero"        | 0           || null
        "the id is negative"    | -1          || null
        "the id is the minimum" | -2147483648 || null
    }

    @Unroll
    def "LogFilter should normalize offset and page size to #expectedOffset / #expectedSize when #scenario"() {

        when:
        LogFilter f = new LogFilter(1, null, null, null, null, null, null, false, offset, size)

        then:
        f.offset() == expectedOffset
        f.pageSize() == expectedSize

        where:
        scenario                        | offset | size       || expectedOffset | expectedSize
        "both are zero"                 | 0      | 0          || 0              | 50
        "both are negative"             | -5     | -1         || 0              | 50
        "the page is one row"           | 3      | 1          || 3              | 1
        "the page is at the maximum"    | 3      | 500        || 3              | 500
        "the page is one over"          | 3      | 501        || 3              | 500
        "the page is the int maximum"   | 3      | 2147483647 || 3              | 500
    }

    def "LogFilter should default to 50 rows a page when nothing is asked"() {

        expect:
        LogFilter.DEFAULT_PAGE_SIZE == 50
    }

    def "the draft records should carry exactly what they are given when constructed"() {

        given:
        LocalDateTime when = getLocalDateTime('09/17/2026 00:00')

        expect:
        new SolutionDraft(1, 'n', 'd', 's', 'o', 'ss', when, true, 2, 'a').archived()
        new SolutionDraft(1, 'n', 'd', 's', 'o', 'ss', when, null, 2, 'a').archived() == null
        new ProjectDraft(1, 2, 'n', 'd', 's', 'o', 3).solutionId() == 2
        new ItemDraft(1, 2, null, 't', 'd', 's', 'a', 3, 4, when).parentItemId() == null
        new LogDraft(1, 2, null, 5, 't', 'x', when).itemId() == 5
        new NoteDraft(1, 2, null, 't', 'ti', 'b', 'c', 3).changeNote() == 'c'
        new StepDraft(1, 2, 3, 't', 'c', 'e', 'n', 9).sourceLogId() == 9
        new LogDraft(1, 2, null, 5, 't', 'x', when) == new LogDraft(1, 2, null, 5, 't', 'x', when)
    }
}
