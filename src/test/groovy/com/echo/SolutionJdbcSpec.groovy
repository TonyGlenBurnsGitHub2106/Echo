package com.echo

import java.sql.SQLException

import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.core.JdbcTemplate

import spock.lang.Unroll

// Story 5653: SolutionJdbc -- the shared moves of the Jdbc repositories, against a mocked JdbcTemplate (no database).
class SolutionJdbcSpec extends EchoSpockBaseSpec {

    JdbcTemplate template = Mock()
    SolutionJdbc jdbc = new SolutionJdbc(template)

    def "list should map every row passing the arguments through when rows come back"() {

        when:
        List<Object> out = jdbc.list('EXEC stp_X ?, ?', { Map<String, Object> r -> r.N * 10 }, 1, 'a')

        then:
        1 * template.queryForList('EXEC stp_X ?, ?', [1, 'a'] as Object[]) >> [[N: 1], [N: 2]]
        out == [10, 20]
    }

    def "one should return the first row, or null when there is none"() {

        when:
        Object first = jdbc.one('q', { Map<String, Object> r -> r.N })
        Object none = jdbc.one('q', { Map<String, Object> r -> r.N })

        then:
        2 * template.queryForList('q', [] as Object[]) >>> [[[N: 5], [N: 6]], []]
        first == 5
        none == null
    }

    def "row should return the raw first row when the proc answers one"() {

        when:
        Map<String, Object> r = jdbc.row('q', 3)

        then:
        1 * template.queryForList('q', [3] as Object[]) >> [[A: 1]]
        r == [A: 1]
    }

    def "number should read the column, and 0 when no row comes back"() {

        when:
        int n = jdbc.number('q', 'SolutionID')
        int zero = jdbc.number('q', 'SolutionID')

        then:
        2 * template.queryForList('q', [] as Object[]) >>> [[[SolutionID: 42]], []]
        n == 42
        zero == 0
    }

    @Unroll
    def "affected should return #expected when #scenario"() {

        given:
        List<Map<String, Object>> rows = rowsAffected == null ? [] : [[RowsAffected: rowsAffected]]

        when:
        boolean result = jdbc.affected('q')

        then:
        1 * template.queryForList('q', [] as Object[]) >> rows
        result == expected

        where:
        scenario                  | rowsAffected || expected
        "one row was affected"    | 1            || true
        "no row was affected"     | 0            || false
        "the proc answers no row" | null         || false
        "the count is negative"   | -1           || false
    }

    def "run should issue an update when called"() {

        when:
        jdbc.run('EXEC stp_Y ?', 9)

        then:
        1 * template.update('EXEC stp_Y ?', [9] as Object[]) >> 1
    }

    def "list should throw a SolutionRuleException carrying the sentence when the proc raises error 50000"() {

        when:
        jdbc.list('q', { Map<String, Object> r -> r })

        then:
        1 * template.queryForList('q', [] as Object[]) >> { throw raiserror('An item needs a title.', 50000) }
        SolutionRuleException ex = thrown()
        ex.message == 'An item needs a title.'
    }

    def "row should still find the RAISERROR when it is buried in the cause chain"() {

        given:
        DataAccessResourceFailureException deep = new DataAccessResourceFailureException('outer',
            new RuntimeException('mid', new SQLException('Deep rule.', 'S0001', 50000)))

        when:
        jdbc.row('q')

        then:
        1 * template.queryForList('q', [] as Object[]) >> { throw deep }
        SolutionRuleException ex = thrown()
        ex.message == 'Deep rule.'
    }

    def "run should translate a RAISERROR when the proc raises one"() {

        when:
        jdbc.run('q')

        then:
        1 * template.update('q', [] as Object[]) >> { throw raiserror('Refused.', 50000) }
        thrown(SolutionRuleException)
    }

    def "list and run should rethrow any other SQL error untouched when the error is not a RAISERROR"() {

        given:
        UncategorizedSQLException other = raiserror('Invalid object name dbo.Nope.', 208)

        when:
        jdbc.list('q', { Map<String, Object> r -> r })

        then:
        1 * template.queryForList('q', [] as Object[]) >> { throw other }
        UncategorizedSQLException ex = thrown()
        ex.is(other)

        when:
        jdbc.run('q')

        then:
        1 * template.update('q', [] as Object[]) >> { throw other }
        thrown(UncategorizedSQLException)
    }

    private static UncategorizedSQLException raiserror(String message, int code) {
        new UncategorizedSQLException('exec', 'EXEC stp_X', new SQLException(message, 'S0001', code))
    }
}
