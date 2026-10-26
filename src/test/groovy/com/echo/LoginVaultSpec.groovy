package com.echo

/** Story 5653: Echo's login vault -- Leonardo's cipher contract, Echo's own key, no backup gate. */
class LoginVaultSpec extends EchoSpockBaseSpec {

    static final String KEY = Base64.encoder.encodeToString((1..32).collect { it as byte } as byte[])

    def "encryptRefusal should say Echo has no key when echo.login.vault.key is not set"() {

        given:
        LoginVault vault = new LoginVault('')

        expect:
        vault.keyId() == null
        vault.encryptRefusal() == 'Echo has no login vault key, so this encrypted login cannot be saved here.'
    }

    def "encrypt should refuse when there is no key"() {

        when:
        new LoginVault(null).encrypt('secret')

        then:
        IllegalStateException ex = thrown()
        ex.message.contains('Echo has no login vault key')
    }

    def "a password should come back the same when it is encrypted and decrypted with the key"() {

        given:
        LoginVault vault = new LoginVault(KEY)

        when:
        byte[] cipher = vault.encrypt('P@ss word 1')

        then:
        vault.encryptRefusal() == null
        LoginVault.isVaultCipher(cipher)
        vault.decrypt(cipher) == 'P@ss word 1'
    }

    def "the vault should refuse a key that is not 32 bytes"() {

        when:
        new LoginVault(Base64.encoder.encodeToString(new byte[16]))

        then:
        IllegalStateException ex = thrown()
        ex.message == 'echo.login.vault.key is not a 32-byte key.'
    }
}
