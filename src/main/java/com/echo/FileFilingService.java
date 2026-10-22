package com.echo;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Organized Files (Story 3520): the file CATEGORIES -- the shared lookup the Solutions Files tab
 * files documents under. Leonardo's copy also lists and files the documents of records and of
 * its file library; Echo has neither, so only the category half was echoed (Story 5653).
 */
@Service
public class FileFilingService {


    private final FileFilingRepository repo;

    public FileFilingService(FileFilingRepository repo) { this.repo = repo; }

    // ---- categories --------------------------------------------------------

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.listCategories()) out.add(category(r));
        return out;
    }

    public Map<String, Object> saveCategory(int id, String name, String color, Integer sortOrder) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("A category needs a name.");
        if (clean.length() > 100) throw new IllegalArgumentException("A category name is 100 characters at most.");
        return category(repo.saveCategory(id, clean, cleanColor(color), sortOrder));
    }

    public Map<String, Object> findOrCreateCategory(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("A category needs a name.");
        if (clean.length() > 100) throw new IllegalArgumentException("A category name is 100 characters at most.");
        Map<String, Object> raw = repo.findOrCreateCategory(clean);
        Map<String, Object> out = category(raw);
        out.put("created", Boolean.TRUE.equals(raw.get("WasCreated")) || Integer.valueOf(1).equals(raw.get("WasCreated")));
        return out;
    }

    public int deleteCategory(int id) { return repo.deleteCategory(id); }

    public List<Map<String, Object>> reorderCategories(List<Integer> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.reorderCategories(ids == null ? List.of() : ids)) out.add(category(r));
        return out;
    }

    /**
     * The category a request names: an explicit id wins, a typed name is found-or-created,
     * and neither (or staging=true) means staging (null).
     */
    public Integer resolveCategory(Integer categoryId, String categoryName, Boolean staging) {
        if (Boolean.TRUE.equals(staging)) return null;
        if (categoryId != null && categoryId > 0) return categoryId;
        if (categoryName != null && !categoryName.isBlank()) {
            return ((Number) findOrCreateCategory(categoryName).get("id")).intValue();
        }
        return null;
    }

    private static Map<String, Object> category(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",        num(r.get("FileCategoryID")));
        m.put("name",      nz(r.get("CategoryName")));
        m.put("color",     nz(r.get("ColorHex")));
        m.put("sortOrder", num(r.get("SortOrder")));
        if (r.containsKey("UseCount")) m.put("useCount", num(r.get("UseCount")));
        return m;
    }

    private static String cleanColor(String c) {
        if (c == null || c.isBlank()) return null;
        String t = c.trim();
        if (!t.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Color must look like #1D4ED8.");
        return t.toUpperCase(Locale.ROOT);
    }

    private static int num(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String nz(Object o) { return o == null ? "" : String.valueOf(o); }
}
