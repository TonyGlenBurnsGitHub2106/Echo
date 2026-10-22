package com.echo;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The organized Files tab of a solution (Story 5594) -- the record Files tab (Story 3520) for a
 * solution's own files: category or Staging, Important, document date; grouped, sorted and searched
 * in SQL. Categories are the SAME shared list the record Files tabs use, resolved (find-or-create)
 * through {@link FileFilingService}.
 *
 * HOUSE-ONLY. The outside-agent door (/api/solutions) never calls this: a category name is house
 * information. Where the bytes are stored, upload, view, download and delete stay with
 * {@link SolutionFileService}, unchanged.
 */
@Service
public class SolutionFileFilingService {

    static final Set<String> GROUPS = Set.of("category", "type", "year", "none");
    static final Set<String> SORTS  = Set.of("added", "name", "docdate", "size", "type");
    private static final DateTimeFormatter US = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final SolutionFileFilingRepository repo;
    private final FileFilingService categories;

    public SolutionFileFilingService(SolutionFileFilingRepository repo, FileFilingService categories) {
        this.repo = repo;
        this.categories = categories;
    }

    /** The category a request names: an id wins, a typed name is found-or-created, neither (or staging) = Staging. */
    public Integer resolveCategory(Integer categoryId, String categoryName, Boolean staging) {
        return categories.resolveCategory(categoryId, categoryName, staging);
    }

    /** One page of a solution's organized files; the first page also carries the band counts. */
    public Map<String, Object> list(int solutionId, String group, String sort, boolean desc,
                                    String search, int offset, int pageSize) {
        String g = pick(group, GROUPS, "category");
        String s = pick(sort, SORTS, "added");
        String q = search == null || search.isBlank() ? null : search.trim();
        int size = pageSize >= 1 && pageSize <= 500 ? pageSize : 50;
        int from = Math.max(0, offset);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : repo.listFiled(solutionId, g, s, desc, q, from, size + 1)) rows.add(fileRow(r));
        boolean more = rows.size() > size;
        if (more) rows = new ArrayList<>(rows.subList(0, size));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupBy", g);
        out.put("sort", s);
        out.put("desc", desc);
        out.put("offset", from);
        out.put("rows", rows);
        out.put("hasMore", more);
        if (from == 0) out.putAll(counts(solutionId, g, q));
        return out;
    }

    public Map<String, Object> counts(int solutionId, String group, String search) {
        String g = pick(group, GROUPS, "category");
        List<Map<String, Object>> groups = new ArrayList<>();
        int total = 0;
        int staged = 0;
        for (Map<String, Object> r : repo.countsFiled(solutionId, g, search)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", r.get("GroupKey"));
            m.put("label", r.get("GroupLabel"));
            m.put("color", r.get("GroupColor") == null ? "" : r.get("GroupColor"));
            m.put("categoryId", r.get("FileCategoryID"));
            int cnt = num(r.get("Cnt"));
            int st  = num(r.get("StagedCnt"));
            m.put("count", cnt);
            m.put("stagedCount", st);
            total += cnt;
            staged += st;
            groups.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groups", groups);
        out.put("total", total);
        out.put("staged", staged);
        return out;
    }

    /** Category / Staging, document date and Important for one file; null when the file is not on the solution. */
    public Map<String, Object> setFiling(int solutionId, int fileId, Integer categoryId, String documentDate,
                                         boolean important, String by) {
        Map<String, Object> row = repo.setFiling(solutionId, fileId, categoryId, parseDate(documentDate), important, by);
        if (row == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", fileId);
        out.put("categoryId", row.get("FileCategoryID"));
        out.put("documentDate", dateOnly(row.get("DocumentDate")));
        out.put("important", truthy(row.get("IsImportant")));
        return out;
    }

    public boolean setImportant(int solutionId, int fileId, boolean important, String by) {
        return repo.setImportant(solutionId, fileId, important, by);
    }

    public int fileMany(int solutionId, List<Integer> fileIds, Integer categoryId, String by) {
        if (fileIds == null || fileIds.isEmpty()) return 0;
        return repo.fileMany(solutionId, fileIds, categoryId, by);
    }

    // ---- helpers ---------------------------------------------------------------

    private static Map<String, Object> fileRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("SolutionFileID"));
        m.put("solutionId", r.get("SolutionID"));
        m.put("itemId", r.get("SolutionProjectItemID"));
        m.put("itemTitle", r.get("ItemTitle"));
        m.put("title", r.get("FileTitle"));
        m.put("originalFileName", r.get("OriginalFileName"));
        m.put("storageCode", r.get("StorageCode"));
        m.put("contentType", r.get("ContentType"));
        m.put("sizeBytes", r.get("FileSizeBytes") instanceof Number n ? n.longValue() : 0L);
        m.put("notesPreview", r.get("NotesPreview") == null ? "" : r.get("NotesPreview"));
        m.put("uploadedByCode", r.get("UploadedByCode"));
        m.put("uploadedByName", r.get("UploadedByName"));
        m.put("createdDate", text(r.get("CreatedDate")));
        m.put("categoryId", r.get("FileCategoryID"));
        m.put("categoryName", r.get("CategoryName"));
        m.put("categoryColor", r.get("CategoryColor") == null ? "" : r.get("CategoryColor"));
        m.put("staged", truthy(r.get("IsStaged")));
        m.put("documentDate", dateOnly(r.get("DocumentDate")));
        m.put("important", truthy(r.get("IsImportant")));
        m.put("ext", r.get("Ext") == null ? "" : r.get("Ext"));
        m.put("typeFamily", r.get("TypeFamily"));
        m.put("groupKey", r.get("GroupKey"));
        m.put("groupLabel", r.get("GroupLabel"));
        m.put("groupColor", r.get("GroupColor") == null ? "" : r.get("GroupColor"));
        return m;
    }

    private static String pick(String value, Set<String> allowed, String dflt) {
        String v = value == null ? "" : value.trim().toLowerCase();
        return allowed.contains(v) ? v : dflt;
    }

    /** "yyyy-MM-dd" or "mm/dd/yyyy"; blank clears it; anything else is a 400. */
    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return v.contains("/") ? LocalDate.parse(v, US) : LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("The document date \"" + v + "\" is not a date. Use mm/dd/yyyy.");
        }
    }

    private static String dateOnly(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private static String text(Object o) { return o == null ? null : String.valueOf(o).replace(' ', 'T'); }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return o != null && "true".equalsIgnoreCase(String.valueOf(o));
    }

    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }
}
