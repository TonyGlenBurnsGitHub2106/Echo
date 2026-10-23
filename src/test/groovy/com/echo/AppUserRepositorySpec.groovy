package com.echo

// Story 5653: the stp_AppUser_* calls and the row mapper, with JdbcTemplate mocked (no database).
// The repository builds its own SolutionJdbc from a DataSource; the spec swaps in one over a
// mocked JdbcTemplate so the exact proc calls and the mapping can be checked.

import java.lang.reflect.Field
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.core.JdbcTemplate

import spock.lang.Unroll

class AppUserRepositorySpec extends EchoSpockBaseSpec {

    JdbcTemplate jdbc = Mock()
    AppUserRepository repo = new AppUserRepository(Mock(DataSource))

    private static final String LOCK = '10/22/2026 15:00'

    def setup() {
        Field f = AppUserRepository.getDeclaredField('db')
        f.accessible = true
        f.set(repo, new SolutionJdbc(jdbc))
    }

    def "byUsername should call the login proc by name and map the row when the user exists"() {

        when:
        AppUser u = repo.byUsername('tony')

        then:
        1 * jdbc.queryForList('EXEC dbo.stp_AppUser_GetForLogin @prmUsername = ?', 'tony') >> [row([:])]
        u == new AppUser(3, 'tony', 'Tony', 'pbkdf2$h', true, false, 2, getLocalDateTime(LOCK), 5)
    }

    def "byId should call the login proc by id when asked"() {

        when:
        AppUser u = repo.byId(3)

        then:
        1 * jdbc.queryForList('EXEC dbo.stp_AppUser_GetForLogin @prmAppUserID = ?', 3) >> [row([:])]
        u.id() == 3
    }

    def "byUsername and byId should answer null when there is no row"() {

        when:
        AppUser a = repo.byUsername('nobody')
        AppUser b = repo.byId(99)

        then:
        2 * jdbc.queryForList(_, _) >> []
        a == null
        b == null
    }

    def "byUsername should take the first row when there are several"() {

        when:
        AppUser u = repo.byUsername('tony')

        then:
        1 * jdbc.queryForList(_, _) >> [row([AppUserID: 1]), row([AppUserID: 2])]
        u.id() == 1
    }

    @Unroll
    def "byId should read IsActive and IsAdmin as #expected when the column is #scenario"() {

        when:
        AppUser u = repo.byId(3)

        then:
        1 * jdbc.queryForList(_, _) >> [row([IsActive: raw, IsAdmin: raw])]
        u.active() == expected
        u.admin() == expected

        where:
        scenario                                  | raw   || expected
        'a true BIT'                              | true  || true
        'a false BIT'                             | false || false
        'NULL'                                    | null  || false
        'the number 1 (only a Boolean counts)'    | 1     || false
    }

    def "byId should map no lock and default the empty columns when LockedUntil is NULL"() {

        when:
        AppUser u = repo.byId(3)

        then:
        1 * jdbc.queryForList(_, _) >> [[AppUserID: 3, LockedUntil: null] as Map<String, Object>]
        u == new AppUser(3, null, null, null, false, false, 0, null, 0)
        !u.lockedNow()
    }

    def "recordLogin should pass the id and the outcome when called"() {

        when:
        repo.recordLogin(3, true)
        repo.recordLogin(3, false)

        then:
        1 * jdbc.queryForList('EXEC dbo.stp_AppUser_RecordLogin ?, ?', 3, true) >> [[RowsAffected: 1]]
        1 * jdbc.queryForList('EXEC dbo.stp_AppUser_RecordLogin ?, ?', 3, false) >> []
    }

    def "save should pass name, display name and hash and answer the id when the proc saves"() {

        when:
        int id = repo.save('tony', 'Tony', 'pbkdf2$x')

        then:
        1 * jdbc.queryForList('EXEC dbo.stp_AppUser_Save ?, ?, ?', 'tony', 'Tony', 'pbkdf2$x') >> [[AppUserID: 3]]
        id == 3
    }

    def "save should answer 0 when the proc returns no row"() {

        when:
        int id = repo.save('tony', 'Tony', 'h')

        then:
        1 * jdbc.queryForList(*_) >> []
        id == 0
    }

    def "endSessions should call its proc when asked"() {

        when:
        repo.endSessions(3)

        then:
        1 * jdbc.queryForList('EXEC dbo.stp_AppUser_EndSessions ?', 3) >> [[RowsAffected: 1]]
    }

    def "save should throw a rule refusal with the proc's message when the proc raises error 50000"() {

        given:
        SQLException sql = new SQLException('That user name is taken.', 'S0001', 50000)

        when:
        repo.save('tony', 'Tony', 'h')

        then:
        1 * jdbc.queryForList(*_) >> { throw new UncategorizedSQLException('save', 'EXEC', sql) }
        SolutionRuleException ex = thrown(SolutionRuleException)
        ex.message == 'That user name is taken.'
    }

    def "byUsername should pass any other database failure through when it happens"() {

        given:
        SQLException sql = new SQLException('Login failed', '28000', 18456)

        when:
        repo.byUsername('tony')

        then:
        1 * jdbc.queryForList(*_) >> { throw new UncategorizedSQLException('get', 'EXEC', sql) }
        thrown(UncategorizedSQLException)
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Map<String, Object> row(Map<String, Object> extra) {
        Map<String, Object> r = [AppUserID: 3, Username: 'tony', DisplayName: 'Tony', PasswordHash: 'pbkdf2$h', IsActive: true,
                                 IsAdmin: false, FailedAttempts: 2, LockedUntil: Timestamp.valueOf(getLocalDateTime(LOCK)),
                                 SessionGeneration: 5]
        r.putAll(extra)
        r
    }
}
