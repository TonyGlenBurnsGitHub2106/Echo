package com.echo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * The Leonardo login vault (Story 5630) -- at-rest encryption for Login Setup
 * passwords that can never be lost. The LeonardoWeb twin of the desktop's
 * {@code cLoginVault} and of {@code Scripts\LoginVault.ps1}; all three are one
 * contract:
 *
 * <pre>
 *   "LV1" (3 bytes) | key id (8) | IV (16) | AES-256-CBC/PKCS7 ciphertext | HMAC-SHA256 (32)
 *   key id  = first 8 bytes of SHA-256(key)
 *   enc key = HMAC-SHA256(key, "LeonardoLoginVault.enc")
 *   mac key = HMAC-SHA256(key, "LeonardoLoginVault.mac"); the MAC covers every byte before it
 * </pre>
 *
 * The key is {@code leo.login.vault.key} in application-local.properties, which every
 * Leonardo Secrets backup carries off this machine. This class never makes a key
 * ({@code Scripts\LoginVaultKey.ps1} does, once), and it refuses to encrypt until
 * GlobalSetting {@code LoginVault.BackedUpKeyId} names the current key -- so no password is
 * ever encrypted with a key that is not already in a verified backup.
 */
@Component
public class LoginVault {

    /** Cosmetic value parked in LoginSetup.Pass while a login is encrypted (same as the desktop's). */
    public static final String PLACEHOLDER = "(encrypted)";

    private static final byte[] MAGIC = {0x4C, 0x56, 0x31};   // "LV1"
    private static final int KEY_ID_BYTES = 8;
    private static final int IV_BYTES = 16;
    private static final int MAC_BYTES = 32;
    private static final int MIN_LENGTH = MAGIC.length + KEY_ID_BYTES + IV_BYTES + 16 + MAC_BYTES;

    private final byte[] key;          // null when the property is absent
    private final SecureRandom random = new SecureRandom();

    // Story 5653 (Echo): the key is echo.login.vault.key and Echo has no Backup Secrets, so there is no
    // "backed up" gate. Echo never makes a key either: a login is encrypted only when it already was, which
    // can only be one copied from Leonardo -- and that cipher opens only where Leonardo's key is set.
    public LoginVault(@Value("${echo.login.vault.key:}") String keyBase64) {
        byte[] k = null;
        if (keyBase64 != null && !keyBase64.isBlank()) {
            k = Base64.getDecoder().decode(keyBase64.trim());
            if (k.length != 32) throw new IllegalStateException("echo.login.vault.key is not a 32-byte key.");
        }
        this.key = k;
    }

    public static boolean isVaultCipher(byte[] c) {
        return c != null && c.length >= MIN_LENGTH && c[0] == MAGIC[0] && c[1] == MAGIC[1] && c[2] == MAGIC[2];
    }

    /** The key's fingerprint (16 hex characters), or null when there is no key. */
    public String keyId() {
        return key == null ? null : hex(keyIdBytes(key), 0, KEY_ID_BYTES);
    }

    /** Null when a password may be encrypted right now, otherwise the sentence to show. */
    public String encryptRefusal() {
        if (key == null) {
            return "Echo has no login vault key, so this encrypted login cannot be saved here."; // Story 5653
        }
        return null;
    }

    /**
     * Encrypts, then decrypts the result again and compares; only a cipher proven to open
     * comes back. Callers check {@link #encryptRefusal()} first.
     */
    public byte[] encrypt(String plain) {
        String refusal = encryptRefusal();
        if (refusal != null) throw new IllegalStateException(refusal);
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(subKey("enc"), "AES"), new IvParameterSpec(iv));
            byte[] body = aes.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[MAGIC.length + KEY_ID_BYTES + IV_BYTES + body.length + MAC_BYTES];
            int pos = 0;
            System.arraycopy(MAGIC, 0, out, pos, MAGIC.length);                   pos += MAGIC.length;
            System.arraycopy(keyIdBytes(key), 0, out, pos, KEY_ID_BYTES);         pos += KEY_ID_BYTES;
            System.arraycopy(iv, 0, out, pos, IV_BYTES);                          pos += IV_BYTES;
            System.arraycopy(body, 0, out, pos, body.length);                     pos += body.length;
            System.arraycopy(mac(out, pos), 0, out, pos, MAC_BYTES);

            if (!plain.equals(decrypt(out))) {
                throw new IllegalStateException("The encrypted password did not decrypt back to the original.");
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the password: " + e.getMessage(), e);
        }
    }

    public String decrypt(byte[] c) {
        if (!isVaultCipher(c)) throw new IllegalStateException("not a Leonardo vault cipher");
        if (key == null) throw new IllegalStateException("Echo has no login vault key");
        String needed = hex(c, MAGIC.length, KEY_ID_BYTES);
        if (!needed.equals(keyId())) {
            throw new IllegalStateException("encrypted with vault key " + needed + ", but this server has key " + keyId());
        }
        int macAt = c.length - MAC_BYTES;
        if (!MessageDigest.isEqual(mac(c, macAt), Arrays.copyOfRange(c, macAt, c.length))) {
            throw new IllegalStateException("the encrypted password is damaged (check code does not match)");
        }
        try {
            int ivAt = MAGIC.length + KEY_ID_BYTES;
            int bodyAt = ivAt + IV_BYTES;
            Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(subKey("enc"), "AES"),
                     new IvParameterSpec(c, ivAt, IV_BYTES));
            return new String(aes.doFinal(c, bodyAt, macAt - bodyAt), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not decrypt: " + e.getMessage(), e);
        }
    }

    private byte[] subKey(String purpose) {
        return hmac(key, ("LeonardoLoginVault." + purpose).getBytes(StandardCharsets.US_ASCII), -1);
    }

    private byte[] mac(byte[] data, int count) {
        return hmac(subKey("mac"), data, count);
    }

    private static byte[] hmac(byte[] k, byte[] data, int count) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(k, "HmacSHA256"));
            m.update(data, 0, count < 0 ? data.length : count);
            return m.doFinal();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] keyIdBytes(byte[] k) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(k), KEY_ID_BYTES);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = 0; i < len; i++) sb.append(String.format("%02x", b[off + i]));
        return sb.toString();
    }
}
