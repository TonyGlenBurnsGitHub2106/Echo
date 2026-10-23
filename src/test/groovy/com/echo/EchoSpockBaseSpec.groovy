package com.echo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import spock.lang.Specification

/**
 * Story 5653: the base class of every Echo Spock specification -- its date, number and text helpers live here,
 * as the spock-tests rules require. Data tables hold only strings, numbers, nulls, enums and
 * booleans; convert them in given: with these helpers, never with a direct utility call.
 */
abstract class EchoSpockBaseSpec extends Specification {

    /** "01/01/1944" -- the earliest date boundary. */
    static final String SMALL_DATE_STRING = '01/01/1944'

    /** "01/01/3000" -- the latest date boundary. */
    static final String LARGE_DATE_STRING = '01/01/3000'

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern('MM/dd/yyyy')
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern('MM/dd/yyyy HH:mm')

    /** "MM/dd/yyyy" to a LocalDate; null stays null. */
    static LocalDate getLocalDate(String value) {
        value == null ? null : LocalDate.parse(value, DATE)
    }

    /** "MM/dd/yyyy HH:mm" to a LocalDateTime; null stays null. */
    static LocalDateTime getLocalDateTime(String value) {
        value == null ? null : LocalDateTime.parse(value, DATE_TIME)
    }

    /** A LocalDate to "MM/dd/yyyy"; null stays null. */
    static String getLocalDateAsString(LocalDate value) {
        value == null ? null : value.format(DATE)
    }

    /** BigDecimal.valueOf that keeps null as null. */
    static BigDecimal getBigDecimal(Double value) {
        value == null ? null : BigDecimal.valueOf(value)
    }

    /** The text repeated count times -- for length limits. */
    static String getReplicatedString(String value, int count) {
        value * count
    }
}
