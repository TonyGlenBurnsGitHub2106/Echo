package com.echo

// Story 5653: the API-client records and the row mappers that build them from a proc row.

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.ApiClientModels.AccessLogRow
import com.echo.ApiClientModels.ApiClient
import com.echo.ApiClientModels.Credential
import com.echo.ApiClientModels.IssuedKey
import com.echo.ApiClientModels.SolutionLink

import spock.lang.Unroll

class ApiClientModelsSpec extends EchoSpockBaseSpec {

    private static final String WHEN = '10/22/2026 14:30'

    def "ApiClient.of should map every column when the row is full"() {

        given:
        LocalDateTime t = getLocalDateTime(WHEN)
        Map<String, Object> row = [ApiClientID: 7, ClientCode: 'rex', ClientName: 'Rex', KeyHint: 'abcd', HasKey: true,
                                   IsEnabled: false, AllowedPathPrefixes: '/api/solutions/', ClientNotes: 'n',
                                   LastUsedDate: Timestamp.valueOf(t), CreatedDate: t, ModifiedDate: '2026-10-22 14:30:00',
                                   SolutionCount: 3L, RequestsLastDay: '12', RefusedLastDay: (short) 2]

        when:
        ApiClient c = ApiClient.of(row)

        then:
        c == new ApiClient(7, 'rex', 'Rex', 'abcd', true, false, '/api/solutions/', 'n', t, t, t, 3, 12, 2)
    }

    def "ApiClient.of should answer all defaults when the row is empty"() {

        expect:
        ApiClient.of([:]) == new ApiClient(0, null, null, null, false, false, null, null, null, null, null, 0, 0, 0)
    }

    def "ApiClient should have no key or hash component when inspected"() {

        expect:
        ApiClient.recordComponents*.name.every { String n -> !n.toLowerCase().contains('hash') && n != 'key' }
    }

    @Unroll
    def "#seq. the boolean columns should read #expected when the value is #scenario"() {

        expect:
        ApiClient.of([IsEnabled: raw]).enabled() == expected
        Credential.of([IsEnabled: raw]).enabled() == expected

        where:
        seq | scenario            | raw     || expected
        1   | 'true'              | true    || true
        2   | 'false'             | false   || false
        3   | 'the number 1'      | 1       || true
        4   | 'the number 0'      | 0       || false
        5   | 'the number 2'      | 2       || true
        6   | 'the text true'     | 'true'  || true
        7   | 'the text TRUE'     | 'TRUE'  || true
        8   | 'the text 1 padded' | ' 1 '   || true
        9   | 'the text yes'      | 'yes'   || false
        10  | 'the text false'    | 'false' || false
        11  | 'null'              | null    || false
    }

    @Unroll
    def "#seq. the int columns should read #expected when the value is #scenario"() {

        expect:
        ApiClient.of([ApiClientID: raw]).id() == expected

        where:
        seq | scenario             | raw    || expected
        1   | 'an int'             | 5      || 5
        2   | 'a long'             | 5L     || 5
        3   | 'a double'           | 5.9d   || 5
        4   | 'padded text'        | ' 12 ' || 12
        5   | 'non-numeric text'   | 'abc'  || 0
        6   | 'null'               | null   || 0
        7   | 'true'               | true   || 1
        8   | 'false'              | false  || 0
    }

    @Unroll
    def "the date columns should read the date when the value is #scenario"() {

        given:
        LocalDateTime t = getLocalDateTime(WHEN)
        Object raw = dateValue(kind, t)
        LocalDateTime expected = parses ? t : null

        expect:
        ApiClient.of([CreatedDate: raw]).createdDate() == expected

        where:
        scenario                | kind        || parses
        'a LocalDateTime'       | 'ldt'       || true
        'a Timestamp'           | 'timestamp' || true
        'a java.util.Date'      | 'date'      || true
        'ISO text'              | 'iso'       || true
        'SQL text'              | 'sql'       || true
        'garbage text'          | 'garbage'   || false
        'null'                  | 'null'      || false
    }

    def "Credential.of should map the columns the filter needs when the row is full or empty"() {

        expect:
        Credential.of([ApiClientID: 7, ClientCode: 'rex', KeyHash: 'h', IsEnabled: true, AllowedPathPrefixes: '/api/solutions/']) ==
            new Credential(7, 'rex', 'h', true, '/api/solutions/')
        Credential.of([:]) == new Credential(0, null, null, false, null)
    }

    def "SolutionLink.of should map its columns when the row is full or empty"() {

        expect:
        SolutionLink.of([ApiClientID: 7, SolutionID: 5, SolutionName: 'S', ClientCode: 'rex', ClientName: 'Rex']) ==
            new SolutionLink(7, 5, 'S', 'rex', 'Rex')
        SolutionLink.of([:]) == new SolutionLink(0, 0, null, null, null)
    }

    def "AccessLogRow.of should map its columns when the row is full"() {

        given:
        LocalDateTime t = getLocalDateTime(WHEN)

        expect:
        AccessLogRow.of([ApiClientAccessLogID: 1, ApiClientID: 7, ClientCode: 'rex', RequestPath: '/api/x',
                         HttpMethod: 'GET', StatusCode: 403, IsAllowed: false, ClientIp: '1.2.3.4', CreatedDate: t]) ==
            new AccessLogRow(1, 7, 'rex', '/api/x', 'GET', 403, false, '1.2.3.4', t)
    }

    @Unroll
    def "AccessLogRow.of should read the client id as #expected when ApiClientID is #scenario"() {

        expect:
        AccessLogRow.of([ApiClientID: raw]).apiClientId() == expected

        where:
        scenario    | raw  || expected
        'positive'  | 7    || 7
        'zero'      | 0    || null
        'negative'  | -1   || null
        'null'      | null || null
    }

    def "IssuedKey should be a plain value when built"() {

        given:
        IssuedKey k = new IssuedKey(7, 'rex', 'echo_abc', 'xabc')

        expect:
        k.apiClientId() == 7
        k.clientCode() == 'rex'
        k.key() == 'echo_abc'
        k.keyHint() == 'xabc'
        k == new IssuedKey(7, 'rex', 'echo_abc', 'xabc')
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Object dateValue(String kind, LocalDateTime t) {
        switch (kind) {
            case 'ldt': return t
            case 'timestamp': return Timestamp.valueOf(t)
            case 'date': return new Date(Timestamp.valueOf(t).time)
            case 'iso': return '2026-10-22T14:30:00'
            case 'sql': return '2026-10-22 14:30:00'
            case 'garbage': return 'not a date'
            case 'null': return null
            default: throw new IllegalArgumentException(kind)
        }
    }
}
