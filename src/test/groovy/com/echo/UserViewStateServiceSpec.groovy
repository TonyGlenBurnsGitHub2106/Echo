package com.echo

// Story 5653: remembered view state -- prefix rules for a read, and a batch that is validated
// whole before anything is written.

import spock.lang.Unroll

class UserViewStateServiceSpec extends EchoSpockBaseSpec {

    UserViewStateRepository repo = Mock()
    UserViewStateService service = new UserViewStateService(repo)

    def "the limits should be the documented values when read"() {

        expect:
        UserViewStateService.DEFAULT_USER_ID == 1
        UserViewStateService.MAX_KEY_LENGTH == 200
        UserViewStateService.MIN_PREFIX == 3
        UserViewStateService.MAX_VALUE_LENGTH == 4000
        UserViewStateService.MAX_KEYS_PER_SAVE == 50
    }

    // ---- list ---------------------------------------------------------------------------------

    @Unroll
    def "list should refuse and read nothing when the prefix is #scenario"() {

        when:
        service.list(prefix)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'prefix must be at least 3 characters'
        0 * repo._

        where:
        scenario                         | prefix
        'null'                           | null
        'empty'                          | ''
        'blank'                          | '  '
        'one character'                  | 'a'
        'two characters'                 | 'ab'
        'two characters padded'          | '  ab  '
        'two characters in whitespace'   | '\tab\n'
    }

    def "list should refuse when the prefix is longer than 200 characters"() {

        given:
        String prefix = getReplicatedString('x', 201)

        when:
        service.list(prefix)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'prefix is longer than 200 characters'
        0 * repo._
    }

    @Unroll
    def "list should read for user 1 with the trimmed prefix when it has #scenario"() {

        given:
        String p = getReplicatedString('x', length)

        when:
        Map<String, String> got = service.list('  ' + p + ' ')

        then:
        1 * repo.list(1, p) >> [(p + '.a'): 'v']
        got == [(p + '.a'): 'v']

        where:
        scenario                 | length
        'the minimum length'     | 3
        'one over the minimum'   | 4
        'one under the maximum'  | 199
        'the maximum length'     | 200
    }

    def "list should answer an empty map when nothing is stored"() {

        when:
        Map<String, String> got = service.list('leo.')

        then:
        1 * repo.list(1, 'leo.') >> [:]
        got == [:]
    }

    // ---- save ---------------------------------------------------------------------------------

    @Unroll
    def "save should refuse when the batch is #scenario"() {

        given:
        Map<String, Object> values = isNull ? null : [:]

        when:
        service.save(values)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'values is required'
        0 * repo._

        where:
        scenario  | isNull
        'null'    | true
        'empty'   | false
    }

    @Unroll
    def "save should answer #expected when the batch has #scenario"() {

        given:
        Map<String, Object> values = (1..count).collectEntries { int n -> ['k' + n, 'v'] }

        when:
        int saved = service.save(values)

        then:
        saved == expected
        expected * repo.save(1, _, 'v')

        where:
        scenario       | count || expected
        'fifty keys'   | 50    || 50
    }

    def "save should refuse and write nothing when the batch has fifty-one keys"() {

        given:
        Map<String, Object> values = (1..51).collectEntries { int n -> ['k' + n, 'v'] }

        when:
        service.save(values)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'at most 50 keys per save'
        0 * repo._
    }

    @Unroll
    def "save should refuse a blank key and write nothing when the key is #scenario"() {

        given:
        Map<String, Object> m = new LinkedHashMap<String, Object>()
        m.put('good', 'v')
        m.put(key, 'v')

        when:
        service.save(m)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'a key is blank'
        0 * repo._

        where:
        scenario  | key
        'empty'   | ''
        'spaces'  | '   '
        'a tab'   | '\t'
        'null'    | null
    }

    def "save should refuse when the key has 201 characters"() {

        given:
        String key = getReplicatedString('k', 201)

        when:
        service.save([(key): 'v'])

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'a key is longer than 200 characters'
        0 * repo._
    }

    def "save should accept the key when it has 200 characters after trimming"() {

        given:
        String key = getReplicatedString('k', 200)

        when:
        service.save([('  ' + key + '  '): 'v'])

        then:
        1 * repo.save(1, key, 'v')
    }

    def "save should refuse and name the key when the value has 4001 characters"() {

        given:
        String value = getReplicatedString('v', 4001)

        when:
        service.save(['a.b': value])

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'the value for a.b is longer than 4000 characters'
        0 * repo._
    }

    def "save should accept the value when it has 4000 characters"() {

        given:
        String value = getReplicatedString('v', 4000)

        when:
        service.save(['a.b': value])

        then:
        1 * repo.save(1, 'a.b', value)
    }

    def "save should write nothing at all when one entry anywhere in the batch is bad"() {

        given:
        Map<String, Object> m = new LinkedHashMap<String, Object>()
        m.put('a', '1')
        m.put('b', '2')
        m.put('c', getReplicatedString('x', 4001))

        when:
        service.save(m)

        then:
        thrown(IllegalArgumentException)
        0 * repo.save(*_)
    }

    @Unroll
    def "save should store '#stored' when the value is #scenario"() {

        given:
        Object raw = valueOf(kind)

        when:
        int n = service.save(['k.1': raw])

        then:
        1 * repo.save(1, 'k.1', stored)
        n == 1

        where:
        scenario     | kind      || stored
        'null'       | 'null'    || ''
        'a string'   | 'string'  || 'open'
        'empty'      | 'empty'   || ''
        'a number'   | 'number'  || '42'
        'a decimal'  | 'decimal' || '1.5'
        'a boolean'  | 'boolean' || 'true'
        'a list'     | 'list'    || '[1, 2]'
    }

    def "save should trim keys and write each entry once, in order, when the batch is valid"() {

        given:
        Map<String, Object> m = new LinkedHashMap<String, Object>()
        m.put(' leo.a ', 'x')
        m.put('leo.b', 'y')

        when:
        int n = service.save(m)

        then:
        1 * repo.save(1, 'leo.a', 'x')

        then:
        1 * repo.save(1, 'leo.b', 'y')
        n == 2
    }

    def "save should not swallow a repository failure when it happens part-way through"() {

        when:
        service.save(['a': '1', 'b': '2'])

        then:
        1 * repo.save(1, 'a', '1') >> { throw new IllegalStateException('db down') }
        thrown(IllegalStateException)
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Object valueOf(String kind) {
        switch (kind) {
            case 'null': return null
            case 'string': return 'open'
            case 'empty': return ''
            case 'number': return 42
            case 'decimal': return getBigDecimal(1.5d)
            case 'boolean': return true
            case 'list': return [1, 2]
            default: throw new IllegalArgumentException(kind)
        }
    }
}
