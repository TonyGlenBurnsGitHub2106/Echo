package com.echo

import spock.lang.Unroll

/**
 * Story 5653 / Story 5765 (audit fence-5): the startup sweep that encrypts every login Echo still keeps
 * as plain text, once Echo has a login vault key.
 */
class LoginVaultSweepSpec extends EchoSpockBaseSpec {

    static final String KEY = Base64.encoder.encodeToString((1..32).collect { it as byte } as byte[])

    EntityLoginRepository repo = Mock()

    def "sweep should do nothing when Echo has no login vault key"() {

        given:
        LoginVaultSweep sweep = new LoginVaultSweep(repo, new LoginVault(''))

        when:
        LoginVaultSweep.Result result = sweep.sweep()

        then:
        result.skipped()
        result.encrypted() == 0
        0 * repo.listUnencrypted()
        0 * repo.saveSecret(*_)
    }

    def "sweep should store every plain login encrypted with the placeholder in Pass"() {

        given:
        LoginVault vault = new LoginVault(KEY)
        LoginVaultSweep sweep = new LoginVaultSweep(repo, vault)
        repo.listUnencrypted() >> [[LoginSetupID: 4, Pass: 'first secret'], [LoginSetupID: 9, Pass: 'second secret']]
        Map<Integer, byte[]> stored = [:]

        when:
        LoginVaultSweep.Result result = sweep.sweep()

        then:
        2 * repo.saveSecret(_, _, LoginVault.PLACEHOLDER) >> { int id, byte[] cipher, String placeholder -> stored[id] = cipher }
        !result.skipped()
        result.encrypted() == 2
        result.failed() == 0
        vault.decrypt(stored[4]) == 'first secret'
        vault.decrypt(stored[9]) == 'second secret'
    }

    @Unroll
    def "sweep should skip a row when #scenario"() {

        given:
        LoginVaultSweep sweep = new LoginVaultSweep(repo, new LoginVault(KEY))
        repo.listUnencrypted() >> [[LoginSetupID: id, Pass: pass]]

        when:
        LoginVaultSweep.Result result = sweep.sweep()

        then:
        0 * repo.saveSecret(*_)
        result.encrypted() == expected
        result.failed() == 0

        where:
        scenario                         | id   | pass          || expected
        'the password is empty'          | 3    | ''            || 0
        'the password is the placeholder'| 3    | '(encrypted)' || 0
        'the password is null'           | 3    | null          || 0
        'the id is missing'              | null | 'secret'      || 0
    }

    def "sweep should leave a login as it was and go on when the database refuses it"() {

        given:
        LoginVaultSweep sweep = new LoginVaultSweep(repo, new LoginVault(KEY))
        repo.listUnencrypted() >> [[LoginSetupID: 1, Pass: 'a'], [LoginSetupID: 2, Pass: 'b']]

        when:
        LoginVaultSweep.Result result = sweep.sweep()

        then:
        1 * repo.saveSecret(1, _, _) >> { throw new SaveRefusedException('That login does not exist. Nothing was saved.') }
        1 * repo.saveSecret(2, _, _)
        result.encrypted() == 1
        result.failed() == 1
    }

    def "sweep should never throw when the logins cannot be listed"() {

        given:
        LoginVaultSweep sweep = new LoginVaultSweep(repo, new LoginVault(KEY))
        repo.listUnencrypted() >> { throw new IllegalStateException('database is down') }

        when:
        LoginVaultSweep.Result result = sweep.sweep()

        then:
        noExceptionThrown()
        result.encrypted() == 0
        0 * repo.saveSecret(*_)
    }
}
