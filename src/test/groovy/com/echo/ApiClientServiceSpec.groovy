package com.echo

// Story 5653: API-client rules -- checking a presented key (hash only, constant time, disabled
// and keyless clients refused), issuing and revoking keys, and the pass-throughs.

import com.echo.ApiClientModels.AccessLogRow
import com.echo.ApiClientModels.ApiClient
import com.echo.ApiClientModels.Credential
import com.echo.ApiClientModels.IssuedKey
import com.echo.ApiClientModels.SolutionLink

import spock.lang.Unroll

class ApiClientServiceSpec extends EchoSpockBaseSpec {

    ApiClientRepository repo = Mock()
    ApiClientService service = new ApiClientService(repo)

    private static final String KEY = 'echo_secret-key'
    private static final String HASH = ApiClientService.sha256Hex(KEY)

    // ---- sha256Hex ---------------------------------------------------------------------------

    @Unroll
    def "sha256Hex should answer the standard digest when the text is #scenario"() {

        expect:
        ApiClientService.sha256Hex(text) == hex

        where:
        scenario | text  || hex
        'empty'  | ''    || 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
        'abc'    | 'abc' || 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
    }

    def "sha256Hex should answer 64 lower-case hex characters of the UTF-8 bytes when the text is non-ASCII"() {

        expect:
        ApiClientService.sha256Hex('\u00e9') ==~ /[0-9a-f]{64}/
        ApiClientService.sha256Hex('\u00e9') != ApiClientService.sha256Hex('e')
    }

    // ---- authenticate ------------------------------------------------------------------------

    @Unroll
    def "authenticate should answer null without asking the repository when #scenario"() {

        when:
        Credential c = service.authenticate(code, key)

        then:
        c == null
        0 * repo._

        where:
        scenario                 | code  | key
        'the code is null'       | null  | 'echo_secret-key'
        'the code is empty'      | ''    | 'echo_secret-key'
        'the code is blank'      | '   ' | 'echo_secret-key'
        'the key is null'        | 'rex' | null
        'the key is empty'       | 'rex' | ''
        'the key is blank'       | 'rex' | '  '
        'both are null'          | null  | null
    }

    def "authenticate should answer the credential when the key is right and the client enabled"() {

        given:
        Credential cred = new Credential(7, 'rex', HASH, true, '/api/solutions/')

        when:
        Credential got = service.authenticate('rex', KEY)

        then:
        1 * repo.findForAuth('rex') >> cred
        got.is(cred)
    }

    def "authenticate should trim and lower-case the code and trim the key when they are padded"() {

        when:
        Credential got = service.authenticate('  ReX ', '  ' + KEY + '\t')

        then:
        1 * repo.findForAuth('rex') >> new Credential(7, 'rex', HASH, true, '/api/solutions/')
        got != null
    }

    @Unroll
    def "authenticate should match the stored hash when it is #scenario"() {

        given:
        String stored = storedHash(storedKind)

        when:
        Credential got = service.authenticate('rex', KEY)

        then:
        1 * repo.findForAuth('rex') >> new Credential(7, 'rex', stored, true, '/api/solutions/')
        got != null

        where:
        scenario        | storedKind
        'upper case'    | 'upper'
        'padded'        | 'padded'
    }

    @Unroll
    def "#seq. authenticate should answer null when #scenario"() {

        given:
        String presented = presentedKey(presentedKind)
        Credential cred = known ? new Credential(7, 'rex', storedHash(storedKind), enabled, '/api/solutions/') : null

        when:
        Credential got = service.authenticate('rex', presented)

        then:
        1 * repo.findForAuth('rex') >> cred
        got == null

        where:
        seq | scenario                     | presentedKind | known | storedKind | enabled
        1   | 'the client is unknown'      | 'key'         | false | 'hash'     | true
        2   | 'the client is disabled'     | 'key'         | true  | 'hash'     | false
        3   | 'the client has no key'      | 'key'         | true  | 'null'     | true
        4   | 'the stored key is blank'    | 'key'         | true  | 'blank'    | true
        5   | 'the key is wrong'           | 'key-plus-x'  | true  | 'hash'     | true
        6   | 'the key differs in case'    | 'key-upper'   | true  | 'hash'     | true
        7   | 'the hash itself is sent'    | 'hash'        | true  | 'hash'     | true
        8   | 'the stored value is plain'  | 'key'         | true  | 'plain'    | true
    }

    // ---- find / list / save --------------------------------------------------------------------

    def "list should pass through when called"() {

        given:
        List<ApiClient> all = [client(1, 'rex'), client(2, 'bot')]

        when:
        List<ApiClient> got = service.list()

        then:
        1 * repo.list() >> all
        got == all
    }

    @Unroll
    def "find should answer #expected when the id is #scenario"() {

        given:
        repo.list() >> [client(1, 'rex'), client(2, 'bot')]

        when:
        ApiClient got = service.find(id)

        then:
        got?.clientCode() == expected

        where:
        scenario          | id || expected
        'the second'      | 2  || 'bot'
        'the first'       | 1  || 'rex'
        'unknown'         | 3  || null
        'zero'            | 0  || null
    }

    def "find should answer null when the list is empty"() {

        given:
        repo.list() >> []

        expect:
        service.find(1) == null
    }

    def "save should pass every field and answer the saved client when the repository saves it"() {

        when:
        ApiClient saved = service.save(0, 'rex', 'Rex', '/api/solutions/', 'notes', false)

        then:
        1 * repo.save(0, 'rex', 'Rex', '/api/solutions/', 'notes', false) >> 9
        1 * repo.list() >> [client(9, 'rex')]
        saved.id() == 9
    }

    @Unroll
    def "save should answer null when the repository reports id #scenario"() {

        when:
        ApiClient saved = service.save(4, 'rex', 'Rex', '/api/solutions/', null, true)

        then:
        1 * repo.save(4, 'rex', 'Rex', '/api/solutions/', null, true) >> id
        0 * repo.list()
        saved == null

        where:
        scenario    | id
        'zero'      | 0
        'negative'  | -1
    }

    def "save should let a rule refusal through when the repository refuses"() {

        when:
        service.save(0, 'rex', 'Rex', '/api/solutions/', null, true)

        then:
        1 * repo.save(*_) >> { throw new SolutionRuleException('That code is taken.') }
        SolutionRuleException ex = thrown(SolutionRuleException)
        ex.message == 'That code is taken.'
    }

    // ---- keys ---------------------------------------------------------------------------------

    def "issueKey should make a fresh echo_ key, store only its hash and hint, and show it once when the client exists"() {

        given:
        String storedHash = null
        String storedHint = null

        when:
        IssuedKey issued = service.issueKey(7)

        then:
        1 * repo.list() >> [client(7, 'rex')]
        1 * repo.setKey(7, _, _) >> { int id, String h, String hint -> storedHash = h; storedHint = hint; true }
        issued.apiClientId() == 7
        issued.clientCode() == 'rex'
        issued.key().startsWith('echo_')
        issued.key().length() == 5 + 43
        issued.key().substring(5) ==~ /[A-Za-z0-9_-]{43}/
        issued.keyHint() == issued.key()[-4..-1]
        storedHint == issued.keyHint()
        storedHash == ApiClientService.sha256Hex(issued.key())
        storedHash != issued.key()
    }

    def "issueKey should make a different key each time and only the newest should authenticate when issued twice"() {

        given:
        repo.list() >> [client(7, 'rex')]
        String stored = null
        repo.setKey(7, _, _) >> { int id, String h, String hint -> stored = h; true }

        when:
        IssuedKey first = service.issueKey(7)
        IssuedKey second = service.issueKey(7)

        then:
        first.key() != second.key()

        when:
        Credential ok = service.authenticate('rex', second.key())
        Credential old = service.authenticate('rex', first.key())

        then:
        2 * repo.findForAuth('rex') >> { new Credential(7, 'rex', stored, true, '/api/solutions/') }
        ok != null
        old == null
    }

    def "issueKey should answer null and store nothing when the client is unknown"() {

        when:
        IssuedKey issued = service.issueKey(99)

        then:
        1 * repo.list() >> [client(7, 'rex')]
        0 * repo.setKey(*_)
        issued == null
    }

    def "issueKey should answer null when the repository could not store it"() {

        when:
        IssuedKey issued = service.issueKey(7)

        then:
        1 * repo.list() >> [client(7, 'rex')]
        1 * repo.setKey(7, _, _) >> false
        issued == null
    }

    def "KEY_PREFIX should be echo_ when read"() {

        expect:
        ApiClientService.KEY_PREFIX == 'echo_'
    }

    @Unroll
    def "revokeKey should clear hash and hint and answer #result when the repository answers #scenario"() {

        when:
        boolean got = service.revokeKey(7)

        then:
        1 * repo.setKey(7, null, null) >> result
        got == result

        where:
        scenario  | result
        'true'    | true
        'false'   | false
    }

    // ---- pass-throughs ---------------------------------------------------------------------------

    @Unroll
    def "setEnabled and delete should pass through when #scenario"() {

        when:
        boolean a = service.setEnabled(3, on)
        boolean b = service.delete(3)

        then:
        1 * repo.setEnabled(3, on) >> result
        1 * repo.delete(3) >> result
        a == result
        b == result

        where:
        scenario                         | on    | result
        'enabling succeeds'              | true  | true
        'disabling finds nothing'        | false | false
    }

    def "the solution-link methods should pass through when called"() {

        given:
        List<SolutionLink> links = [new SolutionLink(7, 5, 'S', 'rex', 'Rex')]

        when:
        List<SolutionLink> a = service.solutionsForClient(7)
        List<SolutionLink> b = service.clientsForSolution(5)
        boolean c = service.setSolutionLink(7, 5, false)

        then:
        1 * repo.solutionsForClient(7) >> links
        1 * repo.clientsForSolution(5) >> links
        1 * repo.setSolutionLink(7, 5, false) >> true
        a == links
        b == links
        c
    }

    @Unroll
    def "maySee should answer #expected when the solution is #scenario"() {

        given:
        repo.solutionsForClient(7) >> [new SolutionLink(7, 5, 'S5', 'rex', 'Rex'), new SolutionLink(7, 6, 'S6', 'rex', 'Rex')]

        expect:
        service.maySee(7, solution) == expected

        where:
        scenario            | solution || expected
        'the first link'    | 5        || true
        'the second link'   | 6        || true
        'not linked'        | 7        || false
        'zero'              | 0        || false
        'negative'          | -5       || false
    }

    def "maySee should answer false when the client has no links"() {

        given:
        repo.solutionsForClient(_) >> []

        expect:
        !service.maySee(7, 5)
    }

    def "accessLog and logAccess should pass through when called"() {

        given:
        List<AccessLogRow> rows = [new AccessLogRow(1, 7, 'rex', '/api/solutions', 'GET', 200, true, '1.2.3.4', null)]

        when:
        List<AccessLogRow> got = service.accessLog(null, true, 10, 25)
        service.logAccess(null, '', '/x', 'GET', 401, false, 'ip')

        then:
        1 * repo.listAccess(null, true, 10, 25) >> rows
        1 * repo.logAccess(null, '', '/x', 'GET', 401, false, 'ip')
        got == rows
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static ApiClient client(int id, String code) {
        new ApiClient(id, code, code.capitalize(), 'abcd', true, true, '/api/solutions/', null, null, null, null, 0, 0, 0)
    }

    private static String storedHash(String kind) {
        switch (kind) {
            case 'hash': return HASH
            case 'upper': return HASH.toUpperCase()
            case 'padded': return '  ' + HASH + '  '
            case 'null': return null
            case 'blank': return '   '
            case 'plain': return KEY
            default: throw new IllegalArgumentException(kind)
        }
    }

    private static String presentedKey(String kind) {
        switch (kind) {
            case 'key': return KEY
            case 'key-plus-x': return KEY + 'x'
            case 'key-upper': return KEY.toUpperCase()
            case 'hash': return HASH
            default: throw new IllegalArgumentException(kind)
        }
    }
}
