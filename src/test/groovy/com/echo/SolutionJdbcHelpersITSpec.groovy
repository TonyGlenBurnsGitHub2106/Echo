package com.echo

import java.time.LocalDateTime

import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

import spock.lang.Unroll

// Story 5653: the SolutionJdbc and SolutionRows helpers every Solutions repository is built on, against real
// SQL Server values and real RAISERRORs (EchoTest_ database; nothing is written).
class SolutionJdbcHelpersITSpec extends EchoDbITSpec {

    SolutionJdbc db

    def setup() {
        db = new SolutionJdbc(new JdbcTemplate(dataSource()))
    }

    def "list, one and row should map every row, the first row or null, and the raw map when queries return rows"() {

        expect:
        db.list('SELECT n FROM (VALUES (1), (2), (3)) v(n) ORDER BY n', { Map<String, Object> r -> (r.n as int) * 10 }) == [10, 20, 30]
        db.one('SELECT n FROM (VALUES (7), (8)) v(n) ORDER BY n', { Map<String, Object> r -> r.n }) == 7
        db.one('SELECT 1 AS n WHERE 1 = 0', { Map<String, Object> r -> r.n }) == null
        db.row('SELECT ? AS a, ? AS b', 'x', 5) == [a: 'x', b: 5]
    }

    @Unroll
    def "number should return #expected when #scenario"() {

        expect:
        db.number(query, 'SolutionID') == expected

        where:
        scenario                         | query                                    || expected
        "the proc returns an id"         | "SELECT 42 AS SolutionID"                || 42
        "the proc returns no row"        | "SELECT 1 AS SolutionID WHERE 1 = 0"     || 0
        "the proc returns a null id"     | "SELECT NULL AS SolutionID"              || 0
    }

    @Unroll
    def "affected should return #expected when #scenario"() {

        expect:
        db.affected(query) == expected

        where:
        scenario                               | query                       || expected
        "one row was affected"                 | "SELECT 1 AS RowsAffected"  || true
        "no row was affected"                  | "SELECT 0 AS RowsAffected"  || false
        "the proc names another column"        | "SELECT 1 AS Other"         || false
    }

    def "row and run should raise a SolutionRuleException with the text when the proc RAISERRORs with number 50000"() {

        when:
        db.row("RAISERROR('A rule for the reader.', 16, 1)")

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'A rule for the reader.'

        when:
        db.run("RAISERROR('Through run too.', 16, 1)")

        then:
        SolutionRuleException ex2 = thrown()
        ex2.message == 'Through run too.'
    }

    @Unroll
    def "row should leave the DataAccessException alone when #scenario"() {

        when:
        db.row(sqlText)

        then:
        DataAccessException ex = thrown()
        !(ex instanceof SolutionRuleException)

        where:
        scenario                              | sqlText                           || _
        "the SQL divides by zero"             | "SELECT 1 / 0 AS x"               || _
        "the table does not exist"            | "SELECT * FROM dbo.NoSuchTable"   || _
        "RAISERROR uses another number"       | "RAISERROR(50001, 16, 1)"         || _
        "THROW uses another number"           | "THROW 50002, 'thrown', 1"        || _
    }

    def "SaveRefusedException.translate should turn a real proc refusal into a SaveRefusedException and leave other errors alone"() {

        given:
        JdbcTemplate jdbc = new JdbcTemplate(dataSource())

        when:
        DataAccessException refused = capture { jdbc.queryForList("EXEC dbo.stp_AppUser_Save N'   ', N'x', 'y'") }
        DataAccessException other = capture { jdbc.queryForList("EXEC dbo.stp_Solution_Save 0, N' ', NULL, 'todo', NULL, NULL, NULL, 0, 0, 'x', NULL") }

        then:
        SaveRefusedException.translate(refused) instanceof SaveRefusedException
        SaveRefusedException.translate(refused).message == 'A user needs a user name. Nothing was saved.'
        SaveRefusedException.translate(other).is(other)
    }

    def "SolutionRows should read every SQL Server type the procs return when a row holds them all"() {

        given:
        Map<String, Object> r = row('''SELECT CAST(5 AS INT) AS i, CAST(6 AS BIGINT) AS big, CAST(7 AS SMALLINT) AS small, CAST(8 AS TINYINT) AS tiny,
                              CAST(9.7 AS DECIMAL(5,1)) AS dec, CAST(1 AS BIT) AS t, CAST(0 AS BIT) AS f, N' 12 ' AS numText,
                              N'abc' AS word, NULL AS nothing, CAST(0 AS INT) AS zero,
                              CAST('2026-10-22T13:14:15' AS DATETIME) AS dt, CAST('2026-10-22T13:14:15.1234567' AS DATETIME2) AS dt2,
                              CAST('2026-10-22' AS DATE) AS d, 'true' AS tText, '1' AS oneText''')
        LocalDateTime dt = getLocalDateTime('10/22/2026 13:14').plusSeconds(15)

        expect:
        SolutionRows.intOf(r, 'i') == 5
        SolutionRows.intOf(r, 'big') == 6
        SolutionRows.intOf(r, 'small') == 7
        SolutionRows.intOf(r, 'tiny') == 8
        SolutionRows.intOf(r, 'dec') == 9
        SolutionRows.intOf(r, 't') == 1
        SolutionRows.intOf(r, 'numText') == 12
        SolutionRows.intOf(r, 'word') == 0
        SolutionRows.intOf(r, 'nothing') == 0
        SolutionRows.intOf(r, 'missing') == 0
        SolutionRows.idOrNull(r, 'i') == 5
        SolutionRows.idOrNull(r, 'zero') == null
        SolutionRows.idOrNull(r, 'nothing') == null
        SolutionRows.boolOf(r, 't')
        !SolutionRows.boolOf(r, 'f')
        SolutionRows.boolOf(r, 'i')
        !SolutionRows.boolOf(r, 'zero')
        SolutionRows.boolOf(r, 'tText')
        SolutionRows.boolOf(r, 'oneText')
        !SolutionRows.boolOf(r, 'word')
        !SolutionRows.boolOf(r, 'nothing')
        SolutionRows.strOf(r, 'word') == 'abc'
        SolutionRows.strOf(r, 'i') == '5'
        SolutionRows.strOf(r, 'nothing') == null
        SolutionRows.dateOf(r, 'dt') == dt
        SolutionRows.dateOf(r, 'dt2') == dt.plusNanos(123456700)
        SolutionRows.dateOf(r, 'd') == getLocalDate('10/22/2026').atStartOfDay()
        SolutionRows.dateOf(r, 'nothing') == null
        SolutionRows.dateOf(r, 'word') == null
    }

    private static DataAccessException capture(Closure c) {
        try { c.call() } catch (DataAccessException ex) { return ex }
        throw new AssertionError('expected a DataAccessException')
    }
}
