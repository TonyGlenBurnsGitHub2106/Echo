package com.echo

import java.time.LocalDateTime

import spock.lang.Unroll

// Story 5653: AppUserRepository against a real EchoTest_ database (stp_AppUser_*).
class AppUserRepositoryITSpec extends EchoDbITSpec {

    AppUserRepository repo

    def setup() {
        repo = new AppUserRepository(dataSource())
    }

    @Unroll
    def "databaseNameOf should read the database name when #scenario"() {

        expect:
        databaseNameOf(jdbcUrl) == expected

        where:
        scenario                 | jdbcUrl                                                   || expected
        "databaseName is given"  | "jdbc:sqlserver://h:1;databaseName=EchoTest_X;user=a"     || "EchoTest_X"
        "database is given"      | "jdbc:sqlserver://h:1;database=Echo"                      || "Echo"
        "no database is given"   | "jdbc:sqlserver://h:1;user=a"                             || null
        "the url is null"        | null                                                      || null
    }

    @Unroll
    def "refuseUnlessTestDatabase should refuse when #scenario"() {

        when:
        refuseUnlessTestDatabase(name)

        then:
        thrown(IllegalStateException)

        where:
        scenario                      | name             || _
        "there is no name"            | null             || _
        "it is Echo"                  | "Echo"           || _
        "it is Leonardo"              | "Leonardo"       || _
        "it is Hearth"                | "Hearth"         || _
        "it is master"                | "master"         || _
        "the prefix is lower case"    | "echotest_lower" || _
        "the prefix is not first"     | "XEchoTest_"     || _
    }

    def "refuseUnlessTestDatabase should accept the name when it starts with EchoTest_"() {

        when:
        refuseUnlessTestDatabase('EchoTest_Spec')

        then:
        noExceptionThrown()
    }

    def "save should create a person whose every field reads back when the user name is new"() {

        given:
        String name = 'it.user-' + uniq()

        when:
        int id = repo.save(name, 'IT Person', HASH)
        AppUser byName = repo.byUsername(name)
        AppUser byId = repo.byId(id)

        then:
        id > 0
        byName == byId
        byName.id() == id
        byName.username() == name
        byName.displayName() == 'IT Person'
        byName.passwordHash() == HASH
        byName.active()
        byName.admin()
        byName.failedAttempts() == 0
        byName.lockedUntil() == null
        byName.sessionGeneration() == 1
    }

    def "byUsername should match trimmed and case-insensitively when the name differs only in case and spaces"() {

        given:
        String name = 'Case' + uniq()
        int id = repo.save(name, null, HASH)

        expect:
        repo.byUsername('  ' + name.toUpperCase() + ' ').id() == id
        repo.byUsername('nobody-' + uniq()) == null
        repo.byId(-1) == null
        repo.byUsername(name).displayName() == name
    }

    def "save should set a new password, keep the id, clear the lock and bump the session when the user name exists"() {

        given:
        String name = 'it' + uniq()
        int id = repo.save(name, 'First', HASH)
        5.times { repo.recordLogin(id, false) }
        assert repo.byId(id).lockedNow()

        when:
        int again = repo.save(name, 'Second', HASH + 'x')
        AppUser u = repo.byId(id)

        then:
        again == id
        u.displayName() == 'Second'
        u.passwordHash() == HASH + 'x'
        u.failedAttempts() == 0
        u.lockedUntil() == null
        u.sessionGeneration() == 2
        count('SELECT COUNT(*) FROM dbo.AppUser WHERE Username = ?', name) == 1
    }

    def "recordLogin should count failures, lock on the fifth and clear on success when sign-ins fail then succeed"() {

        given:
        int id = repo.save('it' + uniq(), 'X', HASH)

        when:
        4.times { repo.recordLogin(id, false) }

        then:
        repo.byId(id).failedAttempts() == 4
        repo.byId(id).lockedUntil() == null

        when:
        repo.recordLogin(id, false)
        LocalDateTime locked = repo.byId(id).lockedUntil()

        then:
        repo.byId(id).failedAttempts() == 5
        locked != null
        locked.isAfter(LocalDateTime.now().plusMinutes(10))
        locked.isBefore(LocalDateTime.now().plusMinutes(20))

        when:
        repo.recordLogin(id, true)
        AppUser u = repo.byId(id)

        then:
        u.failedAttempts() == 0
        u.lockedUntil() == null
        scalar('SELECT LastLoginDate FROM dbo.AppUser WHERE AppUserID = ?', id) != null
    }

    def "endSessions should bump the session generation when called twice"() {

        given:
        int id = repo.save('it' + uniq(), 'X', HASH)

        when:
        repo.endSessions(id)
        repo.endSessions(id)

        then:
        repo.byId(id).sessionGeneration() == 3
    }

    def "recordLogin and endSessions should change nothing and not throw when the id is unknown"() {

        when:
        repo.recordLogin(-5, false)
        repo.endSessions(-5)

        then:
        noExceptionThrown()
    }

    @Unroll
    def "save should refuse with Nothing was saved and write nothing when #scenario"() {

        given:
        int before = count('SELECT COUNT(*) FROM dbo.AppUser')
        String username = baseName == null ? null : baseName + (unique ? uniq() : '')
        String hash = hashKind == 'good' ? HASH : (hashKind == 'short' ? 'short' : null)

        when:
        repo.save(username, 'X', hash)

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)
        ex.message.endsWith('Nothing was saved.')
        count('SELECT COUNT(*) FROM dbo.AppUser') == before

        where:
        scenario                        | baseName    | unique | hashKind || message
        "the user name is blank"        | "   "       | false  | "good"   || "needs a user name"
        "the user name is null"         | null        | false  | "good"   || "needs a user name"
        "the user name has a space"     | "bad name!" | false  | "good"   || "letters, digits, dots"
        "the password hash is short"    | "ok"        | true   | "short"  || "needs a password"
        "the password hash is null"     | "ok"        | true   | "none"   || "needs a password"
    }

    def "save should write AppUser_LOG rows without the password hash when a person is added and changed"() {

        given:
        String name = 'it' + uniq()

        when:
        int id = repo.save(name, 'X', HASH)
        repo.recordLogin(id, false)
        repo.endSessions(id)
        List<Map<String, Object>> log = logRows('AppUser', 'AppUserID', id)

        then:
        log.size() == 3
        log[0].IsInsertion == true
        log[0].Username == name
        log[1].IsInsertion == false
        log[1].FailedAttempts == 1
        log[2].SessionGeneration == 2
        !log[0].containsKey('PasswordHash')
    }

    def "the AppUser delete trigger should write a DeletedDate row when a person is deleted"() {

        given:
        int id = repo.save('it' + uniq(), 'X', HASH)

        when:
        sql.update('DELETE FROM dbo.AppUser WHERE AppUserID = ?', id)
        Map<String, Object> last = logRows('AppUser', 'AppUserID', id).last()

        then:
        repo.byId(id) == null
        last.DeletedDate != null
    }
}
