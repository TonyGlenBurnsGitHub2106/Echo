package com.echo

// Story 5653: the hand-off text for an outside agent. It never carries a key (only the slot),
// never mentions the house key, and names a shared solution and its alias when asked.

import com.echo.ApiClientModels.ApiClient

import spock.lang.Unroll

class ApiClientBriefSpec extends EchoSpockBaseSpec {

    private static final String BASE = 'http://localhost:8770'
    private static final ApiClient REX = new ApiClient(7, 'rex', 'Rex', 'abcd', true, true, '/api/solutions/',
        null, null, null, null, 1, 0, 0)

    def "KEY_SLOT should be the paste marker when read"() {

        expect:
        ApiClientBrief.KEY_SLOT == '<PASTE-THE-KEY-HERE>'
    }

    def "textFor should tell the agent how to connect with the slot where the key goes when no solution is named"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE)

        then:
        t.startsWith('# Working with the Solutions tracker\n')
        t.contains('- Base URL: http://localhost:8770\n')
        t.contains('  - X-Echo-Client: rex\n')
        t.contains('  - X-Echo-Client-Key: <PASTE-THE-KEY-HERE>\n')
        t.contains('GET http://localhost:8770/api/solutions/spec')
        t.contains('GET http://localhost:8770/api/solutions/whoami')
        t.contains('GET http://localhost:8770/api/solutions          to see')
        t.endsWith('adjust, retry once.\n')
    }

    def "textFor should hold every section in order when built"() {

        given:
        List<String> heads = ['## How to connect', '## The key -- read this twice', '## First, every session',
                              '## The five rules', '## Communications -- how to ask, and how to get the answer', '## If a call fails']

        when:
        String t = ApiClientBrief.textFor(REX, BASE)
        List<Integer> positions = heads.collect { String h -> t.indexOf(h) }

        then:
        positions.every { it >= 0 }
        positions == positions.sort(false)
    }

    @Unroll
    def "textFor should never mention #scenario when built"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE)

        then:
        !t.toLowerCase().contains(forbidden.toLowerCase())

        where:
        scenario                 | forbidden
        'the house key header'   | 'X-Echo-Key'
        'an Authorization header'| 'Authorization'
        'a Bearer token'         | 'Bearer'
        'the key hint'           | 'abcd'
        'the test service key'   | 'test-service-key'
        'a key prefix'           | 'echo_'
    }

    def "textFor should be plain ASCII when a solution and alias are named"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE, 5, 'Claims Upgrade', 'claims')

        then:
        t.every { String ch -> ((int) ch.charAt(0)) < 128 }
    }

    def "textFor should name no solution and no alias when none is given"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE)

        then:
        !t.contains('The solution shared with you')
        !t.contains('People call it')
    }

    def "textFor should name the solution and its id when opened from a shared solution"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE, 5, 'Claims Upgrade', null)

        then:
        t.contains('The solution shared with you is "Claims Upgrade" (id 5).\n')
        !t.contains('People call it')
        !t.contains('by-alias')
    }

    def "textFor should explain the alias and give the by-alias call when an alias is given"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE, 5, 'Claims Upgrade', 'claims')

        then:
        t.contains('People call it "claims". When your human says "claims", this is the solution they mean;\n')
        t.contains('GET http://localhost:8770/api/solutions/by-alias/claims returns it.\n')
        t.indexOf('People call it') < t.indexOf('## How to connect')
    }

    @Unroll
    def "textFor should leave the alias out when it is #scenario"() {

        expect:
        !ApiClientBrief.textFor(REX, BASE, 5, 'S', alias).contains('People call it')

        where:
        scenario  | alias
        'null'    | null
        'empty'   | ''
        'blank'   | '   '
    }

    @Unroll
    def "textFor should not name the solution when #scenario"() {

        when:
        String t = ApiClientBrief.textFor(REX, BASE, id, name, 'alias')

        then:
        !t.contains('The solution shared with you')
        !t.contains('People call it')

        where:
        scenario                | id | name
        'the id is zero'        | 0  | 'S'
        'the id is negative'    | -1 | 'S'
        'the name is null'      | 5  | null
        'the name is empty'     | 5  | ''
        'the name is blank'     | 5  | '   '
    }

    def "textFor with two arguments should equal the no-solution form when compared"() {

        expect:
        ApiClientBrief.textFor(REX, BASE) == ApiClientBrief.textFor(REX, BASE, 0, null, null)
    }

    def "textFor should use the base URL verbatim when another Echo has its own address"() {

        when:
        String t = ApiClientBrief.textFor(REX, 'https://echo.example.test')

        then:
        t.contains('- Base URL: https://echo.example.test\n')
        t.contains('GET https://echo.example.test/api/solutions/spec')
        !t.contains('localhost')
    }

    def "textFor should take the client code from the client record when built"() {

        given:
        ApiClient bot = new ApiClient(8, 'bot-2', 'Bot', null, false, true, '/api/solutions/', null, null, null, null, 0, 0, 0)

        expect:
        ApiClientBrief.textFor(bot, BASE).contains('  - X-Echo-Client: bot-2\n')
    }
}
