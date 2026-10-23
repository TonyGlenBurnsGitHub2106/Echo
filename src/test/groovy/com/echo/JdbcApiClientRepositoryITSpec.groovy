package com.echo

import com.echo.ApiClientModels.AccessLogRow
import com.echo.ApiClientModels.ApiClient
import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

// Story 5653: JdbcApiClientRepository against a real EchoTest_ database (stp_ApiClient*, stp_ApiClientAccessLog*,
// stp_SolutionApiClient_List*, stp_Solution_Share).
class JdbcApiClientRepositoryITSpec extends EchoDbITSpec {

    static final String KEY_HASH = 'ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789'

    JdbcApiClientRepository repo

    def setup() {
        repo = new JdbcApiClientRepository(dataSource())
    }

    def "save should store a client that list and findForAuth read back when the code has spaces and capitals"() {

        given:
        String code = 'it-' + uniq()

        when:
        int id = repo.save(0, '  ' + code.toUpperCase() + ' ', '  Client Name ', ' /api/solutions/;/api/other/ ', '  some notes ', true)
        ApiClient c = clientOf(repo, id)
        Credential cred = repo.findForAuth(' ' + code.toUpperCase())

        then:
        id > 0
        c.clientCode() == code
        c.clientName() == 'Client Name'
        c.allowedPathPrefixes() == '/api/solutions/;/api/other/'
        c.clientNotes() == 'some notes'
        c.enabled()
        !c.hasKey()
        c.keyHint() == null
        c.lastUsedDate() == null
        c.createdDate() != null
        c.solutionCount() == 0
        c.requestsLastDay() == 0
        c.refusedLastDay() == 0
        cred.id() == id
        cred.clientCode() == code
        cred.keyHash() == null
        cred.enabled()
        cred.allowedPathPrefixes() == '/api/solutions/;/api/other/'
    }

    @Unroll
    def "findForAuth should return null when #scenario"() {

        given:
        String code = suffix ? 'no-such-' + uniq() : null

        expect:
        repo.findForAuth(code) == null

        where:
        scenario               | suffix || _
        "the code is unknown"  | true   || _
        "the code is null"     | false  || _
    }

    def "save should change the fields, keep the id and null blank notes when a client is updated"() {

        given:
        int id = newApiClient()
        String code = 'ren-' + uniq()

        when:
        int again = repo.save(id, code, 'Renamed', '/api/x/', '   ', false)
        ApiClient c = clientOf(repo, id)

        then:
        again == id
        c.clientCode() == code
        c.clientName() == 'Renamed'
        c.allowedPathPrefixes() == '/api/x/'
        c.clientNotes() == null
        !c.enabled()
        c.modifiedDate() != null
    }

    def "list should order clients by name when there are several"() {

        given:
        int b = repo.save(0, 'b-' + uniq(), 'zzz IT B', '/api/x/', null, true)
        int a = repo.save(0, 'a-' + uniq(), 'zzz IT A', '/api/x/', null, true)

        when:
        List<Integer> ids = repo.list()*.id()

        then:
        ids.indexOf(a) < ids.indexOf(b)
    }

    @Unroll
    def "#seq. save should refuse and write nothing when #scenario"() {

        given:
        String taken = 'taken-' + uniq()
        repo.save(0, taken, 'T', '/api/x/', null, true)
        int before = count('SELECT COUNT(*) FROM dbo.ApiClient')
        String clientCode = code == 'TAKEN' ? taken : (code == 'FRESH' ? 'ok-' + uniq() : code)

        when:
        repo.save(0, clientCode, name, paths, null, true)

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)
        count('SELECT COUNT(*) FROM dbo.ApiClient') == before

        where:
        seq | scenario                           | code          | name | paths               || message
        1   | "the code is blank"                | " "           | "N"  | "/api/x/"           || "needs a code"
        2   | "the code has a space"             | "bad code"    | "N"  | "/api/x/"           || "only lower-case letters, digits and hyphens"
        3   | "the code has an underscore"       | "under_score" | "N"  | "/api/x/"           || "only lower-case letters"
        4   | "the name is blank"                | "FRESH"       | " "  | "/api/x/"           || "needs a name"
        5   | "the allowed paths are blank"      | "FRESH"       | "N"  | " "                 || "at least one allowed path"
        6   | "the code is taken"                | "TAKEN"       | "N"  | "/api/x/"           || "already a client with that code"
        7   | "a path lacks the closing slash"   | "FRESH"       | "N"  | "/api/x"            || "must look like /api/something/"
        8   | "a path is not under /api/"        | "FRESH"       | "N"  | "/other/x/"         || "must look like /api/something/"
        9   | "a path is only /api/"             | "FRESH"       | "N"  | "/api/"             || "must look like /api/something/"
        10  | "a path climbs with .."            | "FRESH"       | "N"  | "/api/../x/"        || "must look like /api/something/"
        11  | "a path has a wildcard"            | "FRESH"       | "N"  | "/api/*/"           || "must look like /api/something/"
        12  | "the second path is bad"           | "FRESH"       | "N"  | "/api/ok/;/api/bad" || "must look like /api/something/"
    }

    def "setKey should store the hash lower case with its hint and clear both when the key is blank"() {

        given:
        int id = newApiClient()
        String code = (String) scalar('SELECT ClientCode FROM dbo.ApiClient WHERE ApiClientID = ?', id)

        expect:
        repo.setKey(id, KEY_HASH, 'abcd')
        repo.findForAuth(code).keyHash() == KEY_HASH.toLowerCase()
        clientOf(repo, id).hasKey()
        clientOf(repo, id).keyHint() == 'abcd'
        repo.setKey(id, '  ', ' ')
        repo.findForAuth(code).keyHash() == null
        !clientOf(repo, id).hasKey()
        clientOf(repo, id).keyHint() == null
        !repo.setKey(-1, KEY_HASH, 'x')
    }

    def "setEnabled should turn a client off and on and answer false when the id is unknown"() {

        given:
        int id = newApiClient()

        expect:
        repo.setEnabled(id, false)
        !clientOf(repo, id).enabled()
        repo.setEnabled(id, true)
        clientOf(repo, id).enabled()
        !repo.setEnabled(-1, true)
    }

    def "delete should remove the client and its solution links and audit both when the client is shared"() {

        given:
        int id = newApiClient()
        int sid = newSolution()
        repo.setSolutionLink(id, sid, true)

        when:
        boolean gone = repo.delete(id)

        then:
        gone
        repo.list().every { it.id() != id }
        repo.clientsForSolution(sid).isEmpty()
        logRows('ApiClient', 'ApiClientID', id).last().DeletedDate != null
        count('SELECT COUNT(*) FROM dbo.SolutionApiClient_LOG WHERE ApiClientID = ? AND DeletedDate IS NOT NULL', id) == 1
        !repo.delete(id)
    }

    def "setKey and setEnabled should write ApiClient_LOG rows holding the key hash, never the key, when a client changes"() {

        given:
        int id = newApiClient()

        when:
        repo.setKey(id, KEY_HASH, 'h')
        repo.setEnabled(id, false)
        List<Map<String, Object>> log = logRows('ApiClient', 'ApiClientID', id)

        then:
        log.size() == 3
        log[0].IsInsertion == true
        log[2].IsEnabled == false
        log[0].KeyHash == null
        log[1].KeyHash == KEY_HASH.toLowerCase()
        log[1].KeyHint == 'h'
    }

    def "logAccess should write a row, stamp LastUsedDate and listAccess should read it newest first when a client calls"() {

        given:
        int id = newApiClient()
        String code = (String) scalar('SELECT ClientCode FROM dbo.ApiClient WHERE ApiClientID = ?', id)

        when:
        repo.logAccess(id, code, '/api/solutions/1', 'GET', 200, true, '10.0.0.1')
        repo.logAccess(id, code, '/api/forbidden', 'POST', 403, false, '10.0.0.2')
        List<AccessLogRow> rows = repo.listAccess(id, false, 0, 50)
        ApiClient c = clientOf(repo, id)

        then:
        rows.size() == 2
        rows[0].requestPath() == '/api/forbidden'
        rows[0].httpMethod() == 'POST'
        rows[0].statusCode() == 403
        !rows[0].allowed()
        rows[0].clientIp() == '10.0.0.2'
        rows[0].apiClientId() == id
        rows[0].clientCode() == code
        rows[0].createdDate() != null
        rows[1].allowed()
        c.lastUsedDate() != null
        c.requestsLastDay() == 2
        c.refusedLastDay() == 1
    }

    @Unroll
    def "listAccess should return #expected when #scenario"() {

        given:
        int id = newApiClient()
        5.times { int i -> repo.logAccess(id, 'c', '/api/p' + i, 'GET', i % 2 == 0 ? 200 : 403, i % 2 == 0, null) }

        when:
        List<String> paths = repo.listAccess(id, refusedOnly, offset, pageSize)*.requestPath()

        then:
        paths == expected.split(',').toList()

        where:
        scenario                                       | refusedOnly | offset | pageSize || expected
        "only refused requests are asked for"          | true        | 0      | 50       || "/api/p3,/api/p1"
        "the second page of two is asked for"          | false       | 1      | 2        || "/api/p3,/api/p2"
        "the offset is negative and the size is zero"  | false       | -5     | 0        || "/api/p4,/api/p3,/api/p2,/api/p1,/api/p0"
        "the last row is asked for"                    | false       | 4      | 1        || "/api/p0"
    }

    def "logAccess should store a NULL client and listAccess should list it under all clients when the caller is anonymous"() {

        given:
        String path = '/api/anon/' + uniq()

        when:
        repo.logAccess(null, 'unknown', path, 'GET', 401, false, '1.2.3.4')
        AccessLogRow r = repo.listAccess(null, true, 0, 500).find { it.requestPath() == path }

        then:
        r != null
        r.apiClientId() == null
        r.clientCode() == 'unknown'
    }

    def "logAccess should truncate long values and never throw when the values are too long or null"() {

        given:
        int id = newApiClient()

        when:
        repo.logAccess(id, getReplicatedString('c', 60), '/api/' + getReplicatedString('x', 600), 'VERYLONGMETHOD', 200, true,
            getReplicatedString('9', 80))
        AccessLogRow r = repo.listAccess(id, false, 0, 1)[0]

        then:
        r.clientCode().length() == 40
        r.requestPath().length() == 500
        r.httpMethod() == 'VERYLONGME'
        r.clientIp().length() == 64

        when:
        repo.logAccess(id, null, null, null, 0, false, null)

        then:
        noExceptionThrown()
        repo.listAccess(id, false, 0, 1)[0].requestPath() == ''
    }

    def "setSolutionLink should share and unshare and force the files off the repo when a solution is shared"() {

        given:
        int id = newApiClient()
        int sid = newSolution('IT shared ' + uniq())
        sql.queryForList("EXEC dbo.stp_Solution_SetSaveFilesToRepo ?, 1, 'felix'", sid)

        when:
        boolean linked = repo.setSolutionLink(id, sid, true)
        boolean again = repo.setSolutionLink(id, sid, true)

        then:
        linked
        again
        count('SELECT COUNT(*) FROM dbo.SolutionApiClient WHERE SolutionID = ? AND ApiClientID = ?', sid, id) == 1
        scalar('SELECT SaveFilesToRepo FROM dbo.Solution WHERE SolutionID = ?', sid) == false
        scalar('SELECT ModifiedBy FROM dbo.Solution WHERE SolutionID = ?', sid) == 'house'
        repo.solutionsForClient(id)*.solutionId() == [sid]
        repo.solutionsForClient(id)[0].solutionName().startsWith('IT shared')
        repo.clientsForSolution(sid)*.apiClientId() == [id]
        repo.clientsForSolution(sid)[0].clientCode() != null
        clientOf(repo, id).solutionCount() == 1

        when:
        boolean unlinked = repo.setSolutionLink(id, sid, false)

        then:
        unlinked
        repo.solutionsForClient(id).isEmpty()
        !repo.setSolutionLink(id, sid, false)
    }

    def "solutionsForClient and clientsForSolution should order by solution name and client name when several are linked"() {

        given:
        int c1 = repo.save(0, 'z-' + uniq(), 'zz Second', '/api/x/', null, true)
        int c2 = repo.save(0, 'a-' + uniq(), 'zz First', '/api/x/', null, true)
        int s1 = newSolution('zz IT B ' + uniq())
        int s2 = newSolution('zz IT A ' + uniq())
        [s1, s2].each { int s -> [c1, c2].each { int c -> repo.setSolutionLink(c, s, true) } }

        expect:
        repo.solutionsForClient(c1)*.solutionId() == [s2, s1]
        repo.clientsForSolution(s1)*.apiClientId() == [c2, c1]
    }

    @Unroll
    def "setSolutionLink should refuse in words when #scenario"() {

        given:
        int id = newApiClient()
        int sid = newSolution()
        int client = knownClient ? id : -1
        int solution = knownSolution ? sid : -1

        when:
        repo.setSolutionLink(client, solution, true)

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                         | knownClient | knownSolution || message
        "the solution does not exist"    | true        | false         || "solution no longer exists"
        "the client does not exist"      | false       | true          || "API client no longer exists"
    }

    private static ApiClient clientOf(JdbcApiClientRepository repo, int id) {
        repo.list().find { ApiClient c -> c.id() == id }
    }
}
