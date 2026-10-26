package com.echo;

import java.util.List;
import java.util.Map;

/**
 * Organized Web tab (Story 3525). The website types (dbo.WebSiteType -- Business, Email, Blog
 * ...) as coloured, ordered, find-or-create categories (dbo.WebSiteTypeStyle), and the
 * organized Web tab on a record (grouping, sorting, retyping, the Important flag). The web
 * addresses themselves are still written by {@link EntityWebAddressRepository}.
 *
 * Two implementations, chosen by {@code leonardo.demo.enabled}:
 *   - {@link JdbcWebTypeRepository}     -- stp_WebSiteType_* / stp_EntityWebAddress_* / stp_Lookup_Delete
 *   - {@link InMemoryWebTypeRepository} -- the demo store (web-addresses, WebSiteTypes)
 */
public interface WebTypeRepository {

    /** Every type in display order with colour, SortOrder and UseCount. */
    List<Map<String, Object>> listTypes();

    /** Resolve a typed name to a type, creating it when new (adds WasCreated). */
    Map<String, Object> findOrCreateType(String name);

    /** Rename / recolour one type. Rules throw IllegalArgumentException. */
    Map<String, Object> saveType(int id, String name, String colorHex);

    /** Put the types in this order; answers the reordered list. */
    List<Map<String, Object>> reorderTypes(List<Integer> ids);

    /** Delete a type, moving its web addresses to moveToId (required while any use it). Answers how many moved. */
    int deleteType(int id, Integer moveToId);

    /** One page of a record's web addresses, grouped (type | kind | none) and sorted in SQL. */
    List<Map<String, Object>> listFiled(int entityId, String groupBy, String sortKey, boolean sortDesc,
                                        String search, int offset, int pageSize);

    /** Band header counts for the same grouping and search. */
    List<Map<String, Object>> countsFiled(int entityId, String groupBy, String search);

    /** Give web addresses on a record a type. Answers how many changed. */
    int setType(int entityId, List<Integer> xrefIds, int typeId);

    /** Flag one web address on a record Important, or clear it. False when the xref is not on the record. */
    boolean setImportant(int entityId, int xrefId, boolean important);

    /** Put these web addresses on a record in this order, among the places they already held.
     *  Rules (not on the record, named twice) throw IllegalArgumentException. Answers how many were placed. */
    int reorderOnEntity(int entityId, List<Integer> xrefIds);
}
