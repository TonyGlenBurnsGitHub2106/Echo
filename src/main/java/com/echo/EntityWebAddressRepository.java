package com.echo;

import java.util.List;
import java.util.Map;

/**
 * Repository contract for Entity Web Address xref rows (emails, websites,
 * FTP, etc).
 *
 * Two implementations exist; Spring picks one based on
 * {@code leonardo.demo.enabled}:
 *   - {@link JdbcEntityWebAddressRepository}     -- real SQL Server (default)
 *   - {@link InMemoryEntityWebAddressRepository} -- JSON-backed demo store
 *
 * Note the {@code XRef} (capital R) spelling in the IDs -- it matches the
 * underlying proc and the desktop schema.
 */
public interface EntityWebAddressRepository {

    /**
     * Lists web addresses for {@code entityId}. Keys: {@code
     * EntityWebAddressXRefID, WebAddressID, WebAddressName, WebAddress,
     * WebAddressTypeID, WebAddressType, WebSiteTypeID, WebSiteType, Notes}.
     */
    List<Map<String, Object>> listForEntity(int entityId);

    /**
     * Inserts (xrefId == 0) or updates a web address. Returns a Map with
     * {@code WebAddressID, EntityWebAddressXRefID}.
     */
    Map<String, Object> save(int xrefId, int webAddressId, int parentEntityId,
                             String name, String url, int webAddressTypeId, int webSiteTypeId,
                             String notes);

    void delete(int xrefId);

    /**
     * Returns true if another web address with the same URL exists
     * (excluding {@code excludeWebAddressId}).
     */
    boolean existsByUrl(String url, int excludeWebAddressId);

    /**
     * Lists web addresses NOT already linked to {@code entityId}, for the
     * add-existing-web-address picker. Keys: {@code WebAddressID,
     * WebAddressName, WebAddress, WebAddressType}.
     */
    List<Map<String, Object>> listAvailable(int entityId);

    /**
     * Attaches an existing web address to {@code parentEntityId} by inserting
     * an xref row only -- never touches {@code tblWebAddress}. Idempotent:
     * re-linking the same pair is a no-op.
     */
    void linkExisting(int parentEntityId, int webAddressId);
}
