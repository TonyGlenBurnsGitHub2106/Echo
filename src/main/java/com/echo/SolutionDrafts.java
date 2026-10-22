package com.echo;

import java.time.LocalDateTime;

/**
 * What a caller hands the Solutions family to write (Story 3493). Statuses,
 * types and people travel as CODES -- the stored procedures resolve them to ids
 * -- so the API contract never exposes a surrogate key a caller could get wrong.
 * An id of 0 means insert.
 *
 * On an UPDATE a null field means "leave it as it is": the services merge a draft
 * over the current record before saving, so a caller sends only what changes and
 * can never blank a field by forgetting it. An empty string clears a text field.
 */
public final class SolutionDrafts {

    private SolutionDrafts() {}

    public record SolutionDraft(int id, String name, String description, String statusCode, String ownerCode,
                                String statusSummary, LocalDateTime targetDate, Boolean archived, int sortOrder, String alias) {}

    public record ProjectDraft(int id, int solutionId, String name, String description, String statusCode,
                               String ownerCode, int sortOrder) {}

    public record ItemDraft(int id, int projectId, Integer parentItemId, String title, String detail,
                            String statusCode, String assigneeCode, int priority, int sortOrder,
                            LocalDateTime dueDate) {}

    public record LogDraft(int id, int solutionId, Integer projectId, Integer itemId, String typeCode,
                           String text, LocalDateTime logDate) {}

    public record NoteDraft(int id, int solutionId, Integer projectId, String typeCode, String title,
                                String body, String changeNote, int sortOrder) {}

    public record StepDraft(int id, int noteId, int stepNumber, String title, String command,
                            String expectedResult, String notes, Integer sourceLogId) {}

    /**
     * The filter one page of the journal is drawn under. One object, so the list
     * and the counts are always asked the same question.
     */
    public record LogFilter(int solutionId, Integer projectId, Integer itemId, String authorCode, String typeCode,
                            Integer sinceSequence, String search, boolean ascending, int offset, int pageSize) {

        public static final int DEFAULT_PAGE_SIZE = 50;

        public LogFilter {
            authorCode = blankToNull(authorCode);
            typeCode   = blankToNull(typeCode);
            search     = blankToNull(search);
            if (projectId != null && projectId <= 0) projectId = null;
            if (itemId != null && itemId <= 0) itemId = null;
            if (sinceSequence != null && sinceSequence <= 0) sinceSequence = null;
            if (offset < 0) offset = 0;
            if (pageSize <= 0) pageSize = DEFAULT_PAGE_SIZE;
            if (pageSize > 500) pageSize = 500;
        }

        private static String blankToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }
    }
}
