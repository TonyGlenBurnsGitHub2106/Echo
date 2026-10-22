package com.echo;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 password hashes (Story 5653), stored as
 * {@code pbkdf2$<iterations>$<salt b64>$<hash b64>}. The same method Hearth uses, written again
 * here because Echo depends on nothing outside itself. scripts\Set-EchoPassword.ps1 makes the very
 * same hash in PowerShell (Rfc2898DeriveBytes, SHA-256, 210,000 rounds), so a person can be created
 * or a forgotten password reset without Echo running; keep the two in step.
 */
final class PasswordHasher {

    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS  = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] h = derive(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(h);
    }

    static boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] p = stored.split("[$]");
        if (p.length != 4 || !"pbkdf2".equals(p[0])) return false;
        try {
            int it = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] want = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(want, derive(password, salt, it));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("PBKDF2 is not available on this Java.", ex);
        }
    }
}
