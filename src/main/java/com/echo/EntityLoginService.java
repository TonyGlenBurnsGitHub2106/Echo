package com.echo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntityLoginService {

    private static final Logger log = LoggerFactory.getLogger(EntityLoginService.class);   // Story 5653 / Story 5765 (audit fence-5)

    private final EntityLoginRepository repo;
    private final LoginVault vault;

    public EntityLoginService(EntityLoginRepository repo, LoginVault vault) {
        this.repo = repo;
        this.vault = vault;
    }

    public List<Map<String, Object>> listForEntity(int entityId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Integer, byte[]> secrets = secretsById();
        for (Map<String, Object> row : repo.listForEntity(entityId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entityLoginSetupXRefId", row.get("EntityLoginSetupXRefID"));
            item.put("loginSetupId",          row.get("LoginSetupID"));
            item.put("loginName",             row.get("LoginName"));
            item.put("loginUserName",         row.get("LoginUserName"));
            putPassword(item, row, secrets);
            item.put("owningUser",            row.get("OwningUser"));
            item.put("userId",                row.get("UserID"));
            item.put("notes",                 row.get("Notes"));
            item.put("effectiveDate",         row.get("EffectiveDate"));
            item.put("throughDate",           row.get("ThroughDate"));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> save(int parentEntityId, int xrefId, LoginSaveRequest req) {
        // Story 5630: an encrypted login stays encrypted. Encrypt FIRST (the vault
        // refuses when its key is missing or not yet in a secrets backup, and
        // proves the cipher opens), so a refusal leaves the login untouched.
        byte[] cipher = null;
        byte[] current = req.loginSetupId > 0 ? secretsById().get(req.loginSetupId) : null;
        boolean encrypted = current != null;
        String password = nz(req.password);
        if (encrypted) {
            // If the stored cipher does not open here, the page is showing an error
            // message in the password box -- saving it would replace the real password.
            try {
                vault.decrypt(current);
            } catch (RuntimeException ex) {
                throw new SaveRefusedException("This login's encrypted password cannot be opened here ("
                    + ex.getMessage() + "), so it was left exactly as it is. Nothing was saved.");
            }
        }
        if (encrypted && !LoginVault.PLACEHOLDER.equals(password)) {
            if (password.isEmpty()) {
                throw new SaveRefusedException("An encrypted login cannot have an empty password. Nothing was saved.");
            }
            String refusal = vault.encryptRefusal();
            if (refusal != null) throw new SaveRefusedException(refusal + " Nothing was saved.");
            cipher = vault.encrypt(password);
        }
        // Story 5653 / Story 5765 (audit fence-5): Echo encrypts EVERY login once it has a vault key (the
        // installer / update makes one), not only one that already was -- a new or plain login is stored
        // encrypted too. An empty password has nothing to protect and stays as it is.
        boolean encryptNew = !encrypted && !password.isEmpty() && !LoginVault.PLACEHOLDER.equals(password)
            && vault.encryptRefusal() == null;
        if (encryptNew) cipher = vault.encrypt(password);

        Map<String, Object> row = repo.save(
            xrefId,
            req.loginSetupId,
            parentEntityId,
            nz(req.loginName),
            nz(req.loginUserName),
            password,
            req.userId,
            nz(req.notes),
            nz(req.effectiveDate),
            nz(req.throughDate));
        if (cipher != null) {
            // Story 5653 / Story 5765 (audit fence-5): a NEW login's id comes back from the save.
            int loginSetupId = req.loginSetupId > 0 ? req.loginSetupId : idOf(row.get("LoginSetupID"));
            try {
                repo.saveSecret(loginSetupId, cipher, LoginVault.PLACEHOLDER);
            } catch (RuntimeException ex) {
                if (encryptNew) {
                    // Story 5653 / Story 5765 (audit fence-5): it was plain before and is saved plain, exactly as
                    // before this change; LoginVaultSweep tries again the next time Echo starts.
                    log.warn("Login {} was saved but could not be encrypted yet: {}", loginSetupId, ex.getMessage());
                    return saved(row);
                }
                // Pass now holds the new password in plain text. Drop the old cipher so
                // it cannot show (or back up) the OLD password; it stays in LoginSecret_LOG.
                repo.deleteSecret(loginSetupId);
                throw new SaveRefusedException("The new password was saved as plain text because it could not be"
                    + " stored encrypted: " + ex.getMessage() + " Nothing was saved.");
            }
        }

        return saved(row);
    }

    // Story 5653 / Story 5765 (audit fence-5): the save's answer, shared by both ways out of save().
    private static Map<String, Object> saved(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("loginSetupId",           row.get("LoginSetupID"));
        out.put("entityLoginSetupXRefId", row.get("EntityLoginSetupXRefID"));
        return out;
    }

    private static int idOf(Object v) { return v instanceof Number ? ((Number) v).intValue() : 0; }   // Story 5653 / Story 5765 (audit fence-5)

    public void delete(int xrefId) { repo.delete(xrefId); }

    /** Logins not yet attached to this entity, projected to the camelCase shape
     *  the picker consumes. */
    public List<Map<String, Object>> listAvailable(int entityId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : repo.listAvailable(entityId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("loginSetupId",  row.get("LoginSetupID"));
            item.put("loginName",     row.get("LoginName"));
            item.put("loginUserName", row.get("LoginUserName"));
            item.put("owningUser",    row.get("OwningUser"));
            item.put("effectiveDate", row.get("EffectiveDate"));
            item.put("throughDate",   row.get("ThroughDate"));
            result.add(item);
        }
        return result;
    }

    /** Attach an existing login to an entity (xref only -- the shared LoginSetup
     *  record is left untouched, so its other owners see no change). */
    public void linkExisting(int parentEntityId, int loginSetupId) {
        repo.linkExisting(parentEntityId, loginSetupId);
    }

    /**
     * The logins on this record's web addresses, each login ONCE (Story 5629).
     * The proc returns one row per (login, web address) pair, already ordered by
     * login, so consecutive rows with the same LoginSetupID fold into one entry
     * whose {@code webAddresses} lists every web address that uses it.
     */
    public List<Map<String, Object>> listViaWebAddresses(int entityId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Integer, byte[]> secrets = secretsById();
        Map<String, Object> current = null;
        Object currentId = null;
        for (Map<String, Object> row : repo.listViaWebAddresses(entityId)) {
            Object loginSetupId = row.get("LoginSetupID");
            if (current == null || !loginSetupId.equals(currentId)) {
                current = new LinkedHashMap<>();
                current.put("loginSetupId",  loginSetupId);
                current.put("loginName",     row.get("LoginName"));
                current.put("loginUserName", row.get("LoginUserName"));
                putPassword(current, row, secrets);
                current.put("owningUser",    row.get("OwningUser"));
                current.put("notes",         row.get("Notes"));
                current.put("effectiveDate", row.get("EffectiveDate"));
                current.put("throughDate",   row.get("ThroughDate"));
                current.put("isOnRecord",    Boolean.TRUE.equals(row.get("IsOnRecord")));
                current.put("webAddresses",  new ArrayList<Map<String, Object>>());
                result.add(current);
                currentId = loginSetupId;
            }
            Map<String, Object> wa = new LinkedHashMap<>();
            wa.put("webAddressId",       row.get("WebAddressID"));
            wa.put("webAddressEntityId", row.get("WebAddressEntityID"));
            wa.put("webAddressName",     row.get("WebAddressName"));
            wa.put("webAddress",         row.get("WebAddress"));
            wa.put("webAddressTypeId",   row.get("WebAddressTypeID"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) current.get("webAddresses");
            list.add(wa);
        }
        return result;
    }

    /** Every encrypted login's cipher by LoginSetupID (one small read per request). */
    private Map<Integer, byte[]> secretsById() {
        Map<Integer, byte[]> map = new HashMap<>();
        for (Map<String, Object> row : repo.listSecrets()) {
            Object id = row.get("LoginSetupID");
            Object c = row.get("PassCipher");
            if (id instanceof Number && c instanceof byte[]) map.put(((Number) id).intValue(), (byte[]) c);
        }
        return map;
    }

    /**
     * The real password for the row: decrypted when the login is encrypted, otherwise Pass.
     * A cipher that will not open shows why (never the placeholder, which looks like a real
     * password), and one bad login never breaks the list. A legacy DPAPI blob can only be
     * read on the desktop.
     */
    private void putPassword(Map<String, Object> item, Map<String, Object> row, Map<Integer, byte[]> secrets) {
        Object id = row.get("LoginSetupID");
        byte[] cipher = id instanceof Number ? secrets.get(((Number) id).intValue()) : null;
        item.put("isEncrypted", cipher != null);
        if (cipher == null) {
            item.put("password", row.get("Pass"));
            return;
        }
        if (!LoginVault.isVaultCipher(cipher)) {
            item.put("password", "(encrypted on the desktop -- open Login Setup there)");
            return;
        }
        try {
            item.put("password", vault.decrypt(cipher));
        } catch (RuntimeException ex) {
            item.put("password", "(could not decrypt: " + ex.getMessage() + ")");
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public static class LoginSaveRequest {
        public int    loginSetupId;       // 0 for new
        public String loginName;
        public String loginUserName;
        public String password;
        public int    userId;
        public String notes;
        public String effectiveDate;
        public String throughDate;
    }
}
