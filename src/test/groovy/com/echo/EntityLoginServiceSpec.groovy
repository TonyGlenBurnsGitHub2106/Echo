package com.echo

import spock.lang.Unroll

/**
 * Story 5653 / Story 5765 (audit fence-5): with a login vault key, Echo stores EVERY login's password
 * encrypted -- a new one, a plain one being edited, and an encrypted one kept so -- and without a key
 * it saves as it always did.
 */
class EntityLoginServiceSpec extends EchoSpockBaseSpec {

    static final String KEY = Base64.encoder.encodeToString((1..32).collect { it as byte } as byte[])

    EntityLoginRepository repo = Mock()

    private static EntityLoginService.LoginSaveRequest request(int loginSetupId, String password) {
        EntityLoginService.LoginSaveRequest req = new EntityLoginService.LoginSaveRequest()
        req.loginSetupId = loginSetupId
        req.loginName = 'Work portal'
        req.loginUserName = 'tony'
        req.password = password
        req.userId = 1
        return req
    }

    def "save should store a new login encrypted under the id the save returned when there is a key"() {

        given:
        LoginVault vault = new LoginVault(KEY)
        EntityLoginService service = new EntityLoginService(repo, vault)
        repo.listSecrets() >> []
        byte[] stored = null

        when:
        Map<String, Object> out = service.save(500, 0, request(0, 'work secret'))

        then:
        1 * repo.save(0, 0, 500, 'Work portal', 'tony', 'work secret', 1, '', '', '') >> [LoginSetupID: 77, EntityLoginSetupXRefID: 12]
        1 * repo.saveSecret(77, _, LoginVault.PLACEHOLDER) >> { int id, byte[] cipher, String placeholder -> stored = cipher }
        vault.decrypt(stored) == 'work secret'
        out.loginSetupId == 77
        out.entityLoginSetupXRefId == 12
    }

    def "save should encrypt a plain login when it is edited and there is a key"() {

        given:
        LoginVault vault = new LoginVault(KEY)
        EntityLoginService service = new EntityLoginService(repo, vault)
        repo.listSecrets() >> []

        when:
        service.save(500, 12, request(77, 'changed secret'))

        then:
        1 * repo.save(12, 77, 500, _, _, 'changed secret', _, _, _, _) >> [LoginSetupID: 77, EntityLoginSetupXRefID: 12]
        1 * repo.saveSecret(77, _, LoginVault.PLACEHOLDER)
    }

    @Unroll
    def "save should keep the login plain when #scenario"() {

        given:
        EntityLoginService service = new EntityLoginService(repo, new LoginVault(key))
        repo.listSecrets() >> []

        when:
        service.save(500, 0, request(0, password))

        then:
        1 * repo.save(*_) >> [LoginSetupID: 77, EntityLoginSetupXRefID: 12]
        expected * repo.saveSecret(*_)

        where:
        scenario                              | key | password      || expected
        'Echo has no login vault key'         | ''  | 'work secret' || 0
        'the password is empty'               | KEY | ''            || 0
        'the password is the placeholder text'| KEY | '(encrypted)' || 0
    }

    def "save should still answer when a new login cannot be encrypted yet"() {

        given:
        EntityLoginService service = new EntityLoginService(repo, new LoginVault(KEY))
        repo.listSecrets() >> []

        when:
        Map<String, Object> out = service.save(500, 0, request(0, 'work secret'))

        then:
        1 * repo.save(*_) >> [LoginSetupID: 77, EntityLoginSetupXRefID: 12]
        1 * repo.saveSecret(77, _, _) >> { throw new SaveRefusedException('refused') }
        0 * repo.deleteSecret(_)
        noExceptionThrown()
        out.loginSetupId == 77
    }

    def "save should re-encrypt an encrypted login with its new password"() {

        given:
        LoginVault vault = new LoginVault(KEY)
        EntityLoginService service = new EntityLoginService(repo, vault)
        repo.listSecrets() >> [[LoginSetupID: 77, PassCipher: vault.encrypt('old secret')]]
        byte[] stored = null

        when:
        service.save(500, 12, request(77, 'new secret'))

        then:
        1 * repo.save(*_) >> [LoginSetupID: 77, EntityLoginSetupXRefID: 12]
        1 * repo.saveSecret(77, _, LoginVault.PLACEHOLDER) >> { int id, byte[] cipher, String placeholder -> stored = cipher }
        vault.decrypt(stored) == 'new secret'
    }

    def "listForEntity should show the decrypted password of an encrypted login"() {

        given:
        LoginVault vault = new LoginVault(KEY)
        EntityLoginService service = new EntityLoginService(repo, vault)
        repo.listSecrets() >> [[LoginSetupID: 77, PassCipher: vault.encrypt('work secret')]]
        repo.listForEntity(500) >> [[LoginSetupID: 77, Pass: '(encrypted)', LoginName: 'Work portal']]

        when:
        List<Map<String, Object>> rows = service.listForEntity(500)

        then:
        rows[0].password == 'work secret'
        rows[0].isEncrypted == true
    }
}
