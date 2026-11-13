package com.echo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Story 5653 / Story 5765 (audit fence-5): Echo only. When Echo starts with a login vault key
 * (echo.login.vault.key, made by scripts/Install-Echo.ps1 or scripts/Update-Echo.ps1), every login
 * whose password is still plain text is encrypted -- once, since an encrypted login is never listed
 * again. Before this, Echo never had a key and every work password sat readable in the database and
 * in every backup of it.
 *
 * It runs on every start, so the first update after this change does the sweep (the update restarts
 * Echo), and so does any later start that finds a plain login (one saved while encrypting failed).
 * It never stops Echo from starting: a login that cannot be encrypted is left exactly as it was and
 * named in logs/echo.log (by id, never its password).
 */
@Component
public class LoginVaultSweep implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoginVaultSweep.class);

    private final EntityLoginRepository repo;
    private final LoginVault vault;

    public LoginVaultSweep(EntityLoginRepository repo, LoginVault vault) {
        this.repo = repo;
        this.vault = vault;
    }

    @Override
    public void run(ApplicationArguments args) {
        Result r = sweep();
        if (r.encrypted() > 0 || r.failed() > 0) {
            log.info("Login vault sweep: {} login(s) encrypted, {} could not be.", r.encrypted(), r.failed());
        }
    }

    /** How one sweep went. {@code skipped} = no key, so nothing was tried. */
    public record Result(boolean skipped, int encrypted, int failed) { }

    /** Encrypts every plain-text login. Never throws. */
    public Result sweep() {
        if (vault.encryptRefusal() != null) return new Result(true, 0, 0);
        int done = 0;
        int failed = 0;
        java.util.List<Map<String, Object>> plain;
        try {
            plain = repo.listUnencrypted();
        } catch (RuntimeException ex) {
            log.warn("Login vault sweep could not list the logins: {}", ex.getMessage());
            return new Result(false, 0, 0);
        }
        for (Map<String, Object> row : plain) {
            Object id = row.get("LoginSetupID");
            Object pass = row.get("Pass");
            if (!(id instanceof Number) || !(pass instanceof String p) || p.isEmpty()
                    || LoginVault.PLACEHOLDER.equals(p)) continue;
            int loginSetupId = ((Number) id).intValue();
            try {
                // encrypt() decrypts its own result again and compares before it hands it back.
                repo.saveSecret(loginSetupId, vault.encrypt(p), LoginVault.PLACEHOLDER);
                done++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Login vault sweep left login {} as it was: {}", loginSetupId, ex.getMessage());
            }
        }
        return new Result(false, done, failed);
    }
}
