package com.echo;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LookupService {

    private final LookupRepository repo;

    public LookupService(LookupRepository repo) {
        this.repo = repo;
    }

    /** Maps URL slug -> proc name. Anything not listed returns null (404 in controller). */
    public String resolveProc(String slug) {
        if (slug == null) return null;
        // Story 5653 (Echo): only the lists the Web tab uses -- Echo has none of the other type tables.
        switch (slug) {
            case "web-address-types": return "stp_LW_Lookup_WebAddressTypes";
            case "web-site-types":    return "stp_LW_Lookup_WebSiteTypes";
            case "users":             return "stp_LW_Lookup_Users";
            default: return null;
        }
    }

    /**
     * Find-or-create a type by name in one of the simple type lists, so a record
     * dialog can add a missing type without leaving the page (Story 3550).
     * Returns {@code {id, name, created}}; throws IllegalArgumentException with a
     * user-readable message for a blank / too-long name or a list that does not
     * take in-place adds.
     */
    public Map<String, Object> findOrCreate(String slug, String name) {
        Map<String, Object> row = repo.findOrCreate(slug, name);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id",   row.get("Id"));
        item.put("name", row.get("Name"));
        Object c = row.get("WasCreated");
        item.put("created", c instanceof Boolean ? c : (c instanceof Number && ((Number) c).intValue() != 0));
        return item;
    }

    public List<Map<String, Object>> get(String procName) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : repo.run(procName)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",   row.get("Id"));
            item.put("name", row.get("Name"));
            result.add(item);
        }
        return result;
    }
}
