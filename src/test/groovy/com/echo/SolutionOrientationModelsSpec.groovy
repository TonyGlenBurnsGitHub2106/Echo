package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionOrientationModels.SummaryAge

import spock.lang.Unroll

/**
 * Story 5695: the shape of "where am I" in a solution -- SummaryAge.of reads when "Where things stand" was
 * last written and how many days ago (0 days is 0, never null), and calls a summary stale at 14 days or more.
 */
class SolutionOrientationModelsSpec extends EchoSpockBaseSpec {

    def "STALE_DAYS should be two weeks"() {

        expect:
        SolutionOrientationModels.STALE_DAYS == 14
    }

    def "SummaryAge.of should answer no date, no age and not stale when there is no row"() {

        expect:
        SummaryAge.of(null) == new SummaryAge(null, null, false)
    }

    @Unroll
    def "#seq. SummaryAge.of should answer age #expectedDays and stale #expectedStale when #scenario"() {

        given:
        LocalDateTime written = getLocalDateTime(writtenText)

        when:
        SummaryAge a = SummaryAge.of([WrittenDate: written, AgeDays: ageDays])

        then:
        a == new SummaryAge(written, expectedDays, expectedStale)

        where:
        seq | scenario                              | writtenText        | ageDays || expectedDays | expectedStale
        1   | 'the solution has no summary'         | null               | null    || null         | false
        2   | 'the summary was written today'       | '11/04/2026 08:00' | 0       || 0            | false
        3   | 'the summary is a day old'            | '11/03/2026 08:00' | 1       || 1            | false
        4   | 'the summary is a day short of stale' | '10/22/2026 08:00' | 13      || 13           | false
        5   | 'the summary is two weeks old'        | '10/21/2026 08:00' | 14      || 14           | true
        6   | 'the summary is a day past stale'     | '10/20/2026 08:00' | 15      || 15           | true
        7   | 'the summary is from September'       | '09/17/2026 08:00' | 48      || 48           | true
        8   | 'the clock says it is from tomorrow'  | '11/05/2026 08:00' | -1      || -1           | false
    }

    @Unroll
    def "SummaryAge.of should read the age as #expectedDays when the column holds #scenario"() {

        given:
        Object v = form == 'long' ? Long.valueOf(ageText) : (form == 'decimal' ? new BigDecimal(ageText) : ageText)

        when:
        SummaryAge a = SummaryAge.of([AgeDays: v])

        then:
        a.ageDays() == expectedDays
        a.stale() == expectedStale

        where:
        scenario                 | form      | ageText || expectedDays | expectedStale
        'a long'                 | 'long'    | '20'    || 20           | true
        'a decimal'              | 'decimal' | '3'     || 3            | false
        'text that is a number'  | 'text'    | '20'    || null         | false
    }

    def "SummaryAge.of should read a Timestamp written date when the row comes from the driver"() {

        given:
        LocalDateTime written = getLocalDateTime('10/01/2026 12:30')

        expect:
        SummaryAge.of([WrittenDate: Timestamp.valueOf(written), AgeDays: 34]) == new SummaryAge(written, 34, true)
    }
}
