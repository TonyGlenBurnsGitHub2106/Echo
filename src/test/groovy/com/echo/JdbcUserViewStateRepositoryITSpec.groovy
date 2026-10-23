package com.echo

import org.springframework.dao.DataAccessException

import spock.lang.Unroll

// Story 5653: JdbcUserViewStateRepository against a real EchoTest_ database (stp_UserViewState_*).
class JdbcUserViewStateRepositoryITSpec extends EchoDbITSpec {

    JdbcUserViewStateRepository repo
    int user

    def setup() {
        repo = new JdbcUserViewStateRepository(dataSource())
        user = 900000 + new Random().nextInt(90000)
    }

    def "list should return the values under a prefix in key order when several keys are saved"() {

        given:
        String p = 'it.' + uniq() + '.'

        when:
        repo.save(user, p + 'b', 'two')
        repo.save(user, p + 'a', 'one')
        repo.save(user, 'other.' + uniq(), 'x')
        Map<String, String> m = repo.list(user, p)

        then:
        m == [(p + 'a'): 'one', (p + 'b'): 'two']
        m.keySet().toList() == [p + 'a', p + 'b']
    }

    def "save should replace the value in one row and write nothing when the same value is saved again"() {

        given:
        String k = 'it.' + uniq()
        repo.save(user, k, 'first')
        int id = count('SELECT UserViewStateID FROM dbo.UserViewState WHERE UserID = ? AND StateKey = ?', user, k)

        when:
        repo.save(user, k, 'second')
        repo.save(user, k, 'second')

        then:
        repo.list(user, k) == [(k): 'second']
        count('SELECT COUNT(*) FROM dbo.UserViewState WHERE UserID = ? AND StateKey = ?', user, k) == 1
        logRows('UserViewState', 'UserViewStateID', id).size() == 2
    }

    def "save should treat a value as changed when it differs only in case"() {

        given:
        String k = 'it.' + uniq()
        repo.save(user, k, 'abc')

        when:
        repo.save(user, k, 'ABC')

        then:
        repo.list(user, k)[k] == 'ABC'
    }

    def "save should trim the key and list should never show it to another user when a key has spaces"() {

        given:
        String k = 'it.' + uniq()

        when:
        repo.save(user, '  ' + k + '  ', 'v')

        then:
        repo.list(user, k) == [(k): 'v']
        repo.list(user + 1, k).isEmpty()
    }

    def "save should store NULL and list should read it back as null when the value is null"() {

        given:
        String k = 'it.' + uniq()

        when:
        repo.save(user, k, null)

        then:
        repo.list(user, k).containsKey(k)
        repo.list(user, k)[k] == null
    }

    @Unroll
    def "list should take the LIKE character literally when #scenario"() {

        given:
        String base = 'it' + uniq()
        ['_x', 'Ax', '%y', 'Zy', '[a]', 'a', '!q'].each { String s -> repo.save(user, base + s, s) }

        expect:
        repo.list(user, base + prefixTail).keySet() == [base + expectedTail] as Set

        where:
        scenario                         | prefixTail || expectedTail
        "the prefix ends in underscore"  | "_"        || "_x"
        "the prefix ends in percent"     | "%"        || "%y"
        "the prefix ends in a bracket"   | "["        || "[a]"
        "the prefix ends in a bang"      | "!"        || "!q"
    }

    def "list should return every key when the prefix has no wildcard"() {

        given:
        String base = 'it' + uniq()
        ['_x', 'Ax', '%y', 'Zy', '[a]', 'a', '!q'].each { String s -> repo.save(user, base + s, s) }

        expect:
        repo.list(user, base).size() == 7
    }

    def "save should be refused by the proc and write nothing when the key is blank"() {

        given:
        int before = count('SELECT COUNT(*) FROM dbo.UserViewState WHERE UserID = ?', user)

        when:
        repo.save(user, '   ', 'v')

        then:
        DataAccessException ex = thrown()
        ex.mostSpecificCause.message.contains('needs a key')
        count('SELECT COUNT(*) FROM dbo.UserViewState WHERE UserID = ?', user) == before
    }

    def "list should throw because the proc returns no result set when the prefix is blank"() {

        // UserViewStateService refuses a prefix under 3 characters before it reaches the repository.
        when:
        repo.list(user, '  ')

        then:
        thrown(DataAccessException)
    }

    def "save should write UserViewState_LOG rows when a value is added and changed"() {

        given:
        String k = 'it.' + uniq()

        when:
        repo.save(user, k, 'a')
        repo.save(user, k, 'b')
        int id = count('SELECT UserViewStateID FROM dbo.UserViewState WHERE UserID = ? AND StateKey = ?', user, k)
        List<Map<String, Object>> log = logRows('UserViewState', 'UserViewStateID', id)

        then:
        log*.IsInsertion == [true, false]
        log*.StateValue == ['a', 'b']
    }
}
