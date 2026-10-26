package com.echo;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Organized Web tab (Story 3525): website types as coloured, ordered, find-or-create
 * categories, and the organized Web tab. Owns the parameter whitelists and the JSON shape;
 * the procs own the rules. The row JSON keeps every field name the old Web tab used
 * (EntityWebAddressService) so the dialogs, Pin and the logins panel work unchanged.
 */
@Service
public class WebTypeService {

    static final Set<String> GROUPS = Set.of("type", "kind", "none");
    static final Set<String> SORTS  = Set.of("name", "address", "type", "added", "order");
    static final int DEFAULT_PAGE = 100;
    static final int MAX_PAGE = 200;

    private final WebTypeRepository repo;

    public WebTypeService(WebTypeRepository repo) { this.repo = repo; }

    // ---- types -------------------------------------------------------------------

    public List<Map<String, Object>> types() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.listTypes()) out.add(type(r));
        return out;
    }

    public Map<String, Object> findOrCreateType(String name) {
        Map<String, Object> raw = repo.findOrCreateType(clean(name));
        Map<String, Object> m = type(raw);
        Object wc = raw.get("WasCreated");
        m.put("created", Boolean.TRUE.equals(wc) || (wc instanceof Number n && n.intValue() != 0));
        return m;
    }

    public Map<String, Object> saveType(int id, String name, String color) {
        return type(repo.saveType(id, clean(name), cleanColor(color)));
    }

    public List<Map<String, Object>> reorderTypes(List<Integer> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.reorderTypes(ids == null ? List.of() : ids)) out.add(type(r));
        return out;
    }

    public int deleteType(int id, Integer moveTo) {
        return repo.deleteType(id, moveTo == null || moveTo <= 0 ? null : moveTo);
    }

    /** The type a request names: an id wins; otherwise a typed name is found or created. 0 = none given. */
    public int resolveType(Integer typeId, String typeName) {
        if (typeId != null && typeId > 0) return typeId;
        if (typeName != null && !typeName.isBlank()) return ((Number) findOrCreateType(typeName).get("id")).intValue();
        return 0;
    }

    // ---- the Web tab ---------------------------------------------------------------

    public Map<String, Object> listForEntity(int entityId, String group, String sort, boolean desc,
                                             String search, int offset, int pageSize) {
        String g = pick(group, GROUPS, "type");
        String s = pick(sort, SORTS, "name");
        String q = search == null || search.isBlank() ? null : search.trim();
        int size = pageSize <= 0 ? DEFAULT_PAGE : Math.min(pageSize, MAX_PAGE);
        int from = Math.max(0, offset);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : repo.listFiled(entityId, g, s, desc, q, from, size + 1)) rows.add(row(r));
        boolean more = rows.size() > size;
        if (more) rows = new ArrayList<>(rows.subList(0, size));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupBy", g);
        out.put("sort", s);
        out.put("desc", desc);
        out.put("offset", from);
        out.put("rows", rows);
        out.put("hasMore", more);
        if (from == 0) out.putAll(counts(entityId, g, q));
        return out;
    }

    public Map<String, Object> counts(int entityId, String group, String search) {
        String g = pick(group, GROUPS, "type");
        List<Map<String, Object>> groups = new ArrayList<>();
        int total = 0, important = 0;
        for (Map<String, Object> r : repo.countsFiled(entityId, g, search)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", r.get("GroupKey"));
            m.put("label", r.get("GroupLabel"));
            m.put("color", "type".equals(g) ? colorOr(r.get("GroupColor"), r.get("WebSiteTypeID")) : "");
            m.put("typeId", r.get("WebSiteTypeID"));
            int cnt = num(r.get("Cnt"));
            m.put("count", cnt);
            m.put("importantCount", num(r.get("ImportantCnt")));
            total += cnt;
            important += num(r.get("ImportantCnt"));
            groups.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groups", groups);
        out.put("total", total);
        out.put("important", important);
        return out;
    }

    public int setType(int entityId, List<Integer> xrefIds, int typeId) {
        if (xrefIds == null || xrefIds.isEmpty()) return 0;
        return repo.setType(entityId, xrefIds, typeId);
    }

    public boolean setImportant(int entityId, int xrefId, boolean important) {
        return repo.setImportant(entityId, xrefId, important);
    }

    /** Tony's own order inside a group: the listed addresses swap among the places they held. */
    public int reorderOnEntity(int entityId, List<Integer> xrefIds) {
        if (xrefIds == null || xrefIds.isEmpty()) throw new IllegalArgumentException("Name at least one web address to put in order. Nothing was saved.");
        return repo.reorderOnEntity(entityId, xrefIds);
    }

    // ---- mapping ---------------------------------------------------------------------

    private static Map<String, Object> type(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("WebSiteTypeID")));
        m.put("name", nz(r.get("WebSiteType")));
        m.put("color", colorOr(r.get("ColorHex"), r.get("WebSiteTypeID")));
        m.put("sortOrder", num(r.get("SortOrder")));
        m.put("useCount", num(r.get("UseCount")));
        return m;
    }

    /** The old Web tab's field names, plus the organized-tab fields. */
    private static Map<String, Object> row(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entityWebAddressXRefId", num(r.get("EntityWebAddressXRefID")));
        m.put("webAddressId", num(r.get("WebAddressID")));
        m.put("webAddressEntityId", num(r.get("WebAddressEntityID")));
        m.put("webAddressName", nz(r.get("WebAddressName")));
        m.put("webAddress", nz(r.get("WebAddress")));
        m.put("webAddressTypeId", num(r.get("WebAddressTypeID")));
        m.put("webAddressType", nz(r.get("WebAddressType")));
        m.put("webSiteTypeId", num(r.get("WebSiteTypeID")));
        m.put("webSiteType", nz(r.get("WebSiteType")));
        m.put("typeColor", colorOr(r.get("TypeColor"), r.get("WebSiteTypeID")));
        m.put("notes", nz(r.get("Notes")));
        m.put("notesPreview", nz(r.get("NotesPreview")));
        m.put("important", truthy(r.get("IsImportant")));
        if (r.containsKey("GroupKey")) {
            boolean byType = String.valueOf(r.get("GroupKey")).startsWith("t:");
            m.put("groupKey", r.get("GroupKey"));
            m.put("groupLabel", r.get("GroupLabel"));
            m.put("groupColor", byType ? colorOr(r.get("GroupColor"), r.get("WebSiteTypeID")) : "");
        }
        return m;
    }

    private static String clean(String name) {
        String c = name == null ? "" : name.trim();
        if (c.isEmpty()) throw new IllegalArgumentException("A website type needs a name.");
        if (c.length() > 50) throw new IllegalArgumentException("A website type name is 50 characters at most.");
        return c;
    }

    private static String cleanColor(String c) {
        if (c == null || c.isBlank()) return null;
        String t = c.trim();
        if (!t.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Colour must look like #1D4ED8.");
        return t.toUpperCase(Locale.ROOT);
    }

    private static String pick(String v, Set<String> allowed, String dflt) {
        if (v == null) return dflt;
        String t = v.trim().toLowerCase(Locale.ROOT);
        return allowed.contains(t) ? t : dflt;
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return o != null && "true".equalsIgnoreCase(String.valueOf(o));
    }

    private static int num(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }

    // Story 5653 (Echo): Leonardo borrows NoteService.colorOr; Echo has no Notes, so the same palette lives here.
    private static final String[] PALETTE = {
        "#B45309", "#B91C1C", "#BE185D", "#9D174D", "#6D28D9", "#4338CA", "#1D4ED8", "#0E7490",
        "#0F766E", "#15803D", "#4D7C0F", "#A16207", "#475569", "#78350F"
    };

    static String colorOr(Object stored, Object typeId) {
        String c = stored == null ? "" : stored.toString().trim();
        if (!c.isEmpty()) return c;
        int id = typeId instanceof Number ? ((Number) typeId).intValue() : 0;
        return id > 0 ? PALETTE[id % PALETTE.length] : "";
    }
}
