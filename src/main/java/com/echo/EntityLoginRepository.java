package com.echo;

import java.util.List;
import java.util.Map;

public interface EntityLoginRepository {
    List<Map<String, Object>> listForEntity(int entityId);
    Map<String, Object> save(int xrefId, int loginSetupId, int parentEntityId,
                             String loginName, String loginUserName, String pass,
                             int userId, String notes, String effectiveDate, String throughDate);
    void delete(int xrefId);

    /**
     * Lists logins NOT already linked to {@code entityId}, for the
     * link-an-existing-login picker. Keys: {@code LoginSetupID, LoginName,
     * LoginUserName, OwningUser, EffectiveDate, ThroughDate}.
     */
    List<Map<String, Object>> listAvailable(int entityId);

    /**
     * Attaches an existing login to {@code parentEntityId} by inserting an xref
     * row only -- never touches {@code LoginSetup}, so the other owners of that
     * login see no change. Idempotent: re-linking the same pair is a no-op.
     */
    void linkExisting(int parentEntityId, int loginSetupId);

    /**
     * Logins hanging off the web addresses of {@code entityId} (Story 5629): one
     * row per (login, web address) pair, ordered by login then web address so the
     * service can fold a shared login into one entry. Keys: {@code LoginSetupID,
     * LoginName, LoginUserName, Pass, OwningUser, Notes, EffectiveDate, ThroughDate,
     * IsOnRecord, WebAddressID, WebAddressEntityID, WebAddressName, WebAddress,
     * WebAddressTypeID}.
     */
    List<Map<String, Object>> listViaWebAddresses(int entityId);

    /** Every encrypted login's cipher (Story 5630). Keys: {@code LoginSetupID, PassCipher}. */
    List<Map<String, Object>> listSecrets();

    /**
     * Stores a login vault cipher and parks the placeholder in LoginSetup.Pass, in one
     * transaction (stp_LoginSecret_Save). Refusals arrive as SaveRefusedException.
     */
    void saveSecret(int loginSetupId, byte[] cipher, String placeholder);

    /**
     * Drops a stale cipher once Pass holds a newer plain-text password (stp_LoginSecret_Delete,
     * which refuses while the cipher is the only copy).
     */
    void deleteSecret(int loginSetupId);
}
