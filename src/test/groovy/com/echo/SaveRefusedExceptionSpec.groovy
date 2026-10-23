package com.echo

import java.sql.SQLException

import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.UncategorizedSQLException

import spock.lang.Unroll

// Story 5653: SaveRefusedException -- a proc's deliberate refusal becomes a readable 409; every other SQL error stays as it was.
class SaveRefusedExceptionSpec extends EchoSpockBaseSpec {

    def "constructor should keep the sentence when given one"() {

        when:
        SaveRefusedException ex = new SaveRefusedException('Already posted. Nothing was saved.')

        then:
        ex.message == 'Already posted. Nothing was saved.'
        ex instanceof RuntimeException
    }

    @Unroll
    def "translate should return a trimmed SaveRefusedException when #scenario"() {

        given:
        DataAccessException ex = new UncategorizedSQLException('exec', 'EXEC stp_X',
            new SQLException('  That row already exists. ' + tail + '  ', 'S0001', 50000))

        when:
        RuntimeException out = SaveRefusedException.translate(ex)

        then:
        out instanceof SaveRefusedException
        out.message == expectedMessage

        where:
        scenario                 | tail                   || expectedMessage
        "the message ends saved" | "Nothing was saved."   || "That row already exists. Nothing was saved."
        "it ends added"          | "Nothing was added."   || "That row already exists. Nothing was added."
        "it ends deleted"        | "Nothing was deleted." || "That row already exists. Nothing was deleted."
        "it ends removed"        | "Nothing was removed." || "That row already exists. Nothing was removed."
        "it ends linked"         | "Nothing was linked."  || "That row already exists. Nothing was linked."
    }

    def "translate should read the deepest cause when the refusal is wrapped twice"() {

        given:
        SQLException root = new SQLException('Nope. Nothing was saved.')
        DataAccessException ex = new DataIntegrityViolationException('outer text', new RuntimeException('middle', root))

        when:
        RuntimeException out = SaveRefusedException.translate(ex)

        then:
        out.message == 'Nope. Nothing was saved.'
    }

    @Unroll
    def "translate should return the very same exception when #scenario"() {

        given:
        DataAccessException ex = new UncategorizedSQLException('exec', 'EXEC stp_X', new SQLException((String) message))

        when:
        RuntimeException out = SaveRefusedException.translate(ex)

        then:
        out.is(ex) == expectedSame

        where:
        scenario                          | message                      || expectedSame
        "an ordinary SQL error"           | "Invalid column name Foo."   || true
        "the refusal lacks its period"    | "Nothing was saved"          || true
        "the refusal is in lower case"    | "nothing was saved."         || true
        "text follows the refusal"        | "Nothing was saved. Really." || true
        "the message is null"             | null                         || true
        "the message is empty"            | ""                           || true
    }

    @Unroll
    def "translate should read the exception's own message when #scenario"() {

        given:
        DataAccessException ex = new DataIntegrityViolationException(message)

        when:
        RuntimeException out = SaveRefusedException.translate(ex)

        then:
        (out instanceof SaveRefusedException) == expectedRefusal
        out.message == message

        where:
        scenario                        | message                         || expectedRefusal
        "there is no cause and refused" | "Duplicate. Nothing was added." || true
        "there is no cause, plain"      | "plain"                         || false
    }
}
