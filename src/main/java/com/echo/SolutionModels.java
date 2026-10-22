package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.idOrNull;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The read models of the Solutions family (Story 3493), one record each, with
 * the single mapper that builds it from a result row. Column names are the ones
 * the stp_Solution* procs return; the demo store uses the same names.
 *
 * A SOLUTION is a whole effort; it owns PROJECTS (workstreams); a project owns
 * ITEMS nested through {@code parentItemId}. Beside the tree sit the LOG (how
 * did we do it) and the NOTES (how do you do it).
 */
public final class SolutionModels {

    private SolutionModels() {}

    public record Status(int id, String code, String name, boolean closed, String colorHex, int sortOrder) {
        static Status of(Map<String, Object> r) {
            return new Status(intOf(r, "SolutionStatusID"), strOf(r, "StatusCode"), strOf(r, "StatusName"),
                boolOf(r, "IsClosed"), strOf(r, "ColorHex"), intOf(r, "SortOrder"));
        }
    }

    public record Member(int id, String code, String name, boolean agent, boolean active, int sortOrder) {
        static Member of(Map<String, Object> r) {
            return new Member(intOf(r, "SolutionMemberID"), strOf(r, "MemberCode"), strOf(r, "MemberName"),
                boolOf(r, "IsAgent"), boolOf(r, "IsActive"), intOf(r, "SortOrder"));
        }
    }

    public record CodeName(int id, String code, String name, int sortOrder) {}

    public record Lookups(List<Status> statuses, List<Member> members, List<CodeName> logTypes, List<CodeName> noteTypes) {}

    public record Solution(int id, String name, String description, String descriptionPreview,
                           String statusCode, String statusName, boolean closed, String colorHex,
                           String ownerCode, String ownerName, String statusSummary, LocalDateTime targetDate,
                           boolean archived, boolean saveFilesToRepo, int fileCount, int sharedCount,
                           int sortOrder, LocalDateTime createdDate, LocalDateTime modifiedDate,
                           String modifiedBy, int projectCount, int itemCount, int closedItemCount,
                           int blockedItemCount, int logCount, LocalDateTime lastLogDate, int noteCount,
                           int awaitingTonyCount, String alias) {
        static Solution of(Map<String, Object> r) {
            return new Solution(intOf(r, "SolutionID"), strOf(r, "SolutionName"), strOf(r, "SolutionDescription"),
                strOf(r, "DescriptionPreview"), strOf(r, "StatusCode"), strOf(r, "StatusName"), boolOf(r, "IsClosed"),
                strOf(r, "ColorHex"), strOf(r, "OwnerCode"), strOf(r, "OwnerName"), strOf(r, "StatusSummary"),
                dateOf(r, "TargetDate"), boolOf(r, "IsArchived"), boolOf(r, "SaveFilesToRepo"), intOf(r, "FileCount"),
                intOf(r, "SharedCount"), intOf(r, "SortOrder"), dateOf(r, "CreatedDate"),
                dateOf(r, "ModifiedDate"), strOf(r, "ModifiedBy"), intOf(r, "ProjectCount"), intOf(r, "ItemCount"),
                intOf(r, "ClosedItemCount"), intOf(r, "BlockedItemCount"), intOf(r, "LogCount"),
                dateOf(r, "LastLogDate"), intOf(r, "NoteCount"), intOf(r, "AwaitingTonyCount"), strOf(r, "SolutionAlias"));
        }
    }

    public record Project(int id, int solutionId, String name, String description, String descriptionPreview,
                          String statusCode, String statusName, boolean closed, String colorHex,
                          String ownerCode, String ownerName, int sortOrder, LocalDateTime createdDate,
                          LocalDateTime modifiedDate, String modifiedBy, int itemCount, int closedItemCount,
                          int blockedItemCount) {
        static Project of(Map<String, Object> r) {
            return new Project(intOf(r, "SolutionProjectID"), intOf(r, "SolutionID"), strOf(r, "ProjectName"),
                strOf(r, "ProjectDescription"), strOf(r, "DescriptionPreview"), strOf(r, "StatusCode"),
                strOf(r, "StatusName"), boolOf(r, "IsClosed"), strOf(r, "ColorHex"), strOf(r, "OwnerCode"),
                strOf(r, "OwnerName"), intOf(r, "SortOrder"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"),
                strOf(r, "ModifiedBy"), intOf(r, "ItemCount"), intOf(r, "ClosedItemCount"), intOf(r, "BlockedItemCount"));
        }
    }

    public record Item(int id, int projectId, int solutionId, String projectName, Integer parentItemId,
                       String title, String detail, String detailPreview, boolean hasDetail,
                       String statusCode, String statusName, boolean closed, String colorHex,
                       String assigneeCode, String assigneeName, int priority, int sortOrder,
                       LocalDateTime dueDate, LocalDateTime completedDate, String createdByCode,
                       LocalDateTime createdDate, LocalDateTime modifiedDate, String modifiedBy,
                       int childCount, int logCount) {
        static Item of(Map<String, Object> r) {
            return new Item(intOf(r, "SolutionProjectItemID"), intOf(r, "SolutionProjectID"), intOf(r, "SolutionID"),
                strOf(r, "ProjectName"), idOrNull(r, "ParentItemID"), strOf(r, "ItemTitle"), strOf(r, "ItemDetail"),
                strOf(r, "DetailPreview"), boolOf(r, "HasDetail"), strOf(r, "StatusCode"), strOf(r, "StatusName"),
                boolOf(r, "IsClosed"), strOf(r, "ColorHex"), strOf(r, "AssigneeCode"), strOf(r, "AssigneeName"),
                intOf(r, "Priority"), intOf(r, "SortOrder"), dateOf(r, "DueDate"), dateOf(r, "CompletedDate"),
                strOf(r, "CreatedByCode"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"), strOf(r, "ModifiedBy"),
                intOf(r, "ChildCount"), intOf(r, "LogCount"));
        }
    }

    public record LogEntry(int id, int solutionId, Integer projectId, String projectName, Integer itemId,
                           String itemTitle, int sequenceNumber, String typeCode, String typeName,
                           String authorCode, String authorName, String text, boolean truncated,
                           LocalDateTime logDate, LocalDateTime createdDate, LocalDateTime modifiedDate,
                           int stepCount) {
        static LogEntry of(Map<String, Object> r) {
            return new LogEntry(intOf(r, "SolutionLogID"), intOf(r, "SolutionID"), idOrNull(r, "SolutionProjectID"),
                strOf(r, "ProjectName"), idOrNull(r, "SolutionProjectItemID"), strOf(r, "ItemTitle"),
                intOf(r, "SequenceNumber"), strOf(r, "LogTypeCode"), strOf(r, "LogTypeName"), strOf(r, "AuthorCode"),
                strOf(r, "AuthorName"), strOf(r, "LogText"), boolOf(r, "IsTruncated"), dateOf(r, "LogDate"),
                dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"), intOf(r, "StepCount"));
        }
    }

    public record LogPage(List<LogEntry> entries, Map<String, Integer> counts, int offset, int pageSize, int total) {}

    public record Note(int id, int solutionId, Integer projectId, String projectName, String typeCode,
                           String typeName, String title, int currentVersionNumber, int sortOrder,
                           LocalDateTime createdDate, LocalDateTime modifiedDate, String modifiedBy,
                           String authorCode, String authorName, LocalDateTime versionDate, String changeNote,
                           int stepCount, String bodyPreview, String body, List<NoteStep> steps) {
        static Note of(Map<String, Object> r, List<NoteStep> steps) {
            return new Note(intOf(r, "SolutionNoteID"), intOf(r, "SolutionID"), idOrNull(r, "SolutionProjectID"),
                strOf(r, "ProjectName"), strOf(r, "NoteTypeCode"), strOf(r, "NoteTypeName"), strOf(r, "NoteTitle"),
                intOf(r, "CurrentVersionNumber"), intOf(r, "SortOrder"), dateOf(r, "CreatedDate"),
                dateOf(r, "ModifiedDate"), strOf(r, "ModifiedBy"), strOf(r, "AuthorCode"), strOf(r, "AuthorName"),
                dateOf(r, "VersionDate"), strOf(r, "ChangeNote"), intOf(r, "StepCount"), strOf(r, "BodyPreview"),
                strOf(r, "NoteBody"), steps);
        }
    }

    public record NoteVersion(int noteId, int versionNumber, String changeNote, String authorCode,
                                  String authorName, LocalDateTime createdDate, int bodyLength, boolean current,
                                  String body) {
        static NoteVersion of(Map<String, Object> r) {
            return new NoteVersion(intOf(r, "SolutionNoteID"), intOf(r, "VersionNumber"), strOf(r, "ChangeNote"),
                strOf(r, "AuthorCode"), strOf(r, "AuthorName"), dateOf(r, "CreatedDate"), intOf(r, "BodyLength"),
                boolOf(r, "IsCurrent"), strOf(r, "NoteBody"));
        }
    }

    public record NoteStep(int id, int noteId, int stepNumber, String title, String command,
                               String expectedResult, String notes, Integer sourceLogId,
                               Integer sourceSequenceNumber) {
        static NoteStep of(Map<String, Object> r) {
            return new NoteStep(intOf(r, "SolutionNoteStepID"), intOf(r, "SolutionNoteID"),
                intOf(r, "StepNumber"), strOf(r, "StepTitle"), strOf(r, "StepCommand"), strOf(r, "ExpectedResult"),
                strOf(r, "StepNotes"), idOrNull(r, "SourceSolutionLogID"), idOrNull(r, "SourceSequenceNumber"));
        }
    }

    public record ConversationSummary(int conversationId, String title, String status, LocalDateTime createdDate,
                                      LocalDateTime modifiedDate, int messageCount, String lastSender,
                                      String awaitingFrom, int awaitingTonyCount) {
        static ConversationSummary of(Map<String, Object> r) {
            return new ConversationSummary(intOf(r, "ConversationID"), strOf(r, "Title"), strOf(r, "Status"),
                dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"), intOf(r, "MessageCount"), strOf(r, "LastSender"),
                strOf(r, "AwaitingFrom"), intOf(r, "AwaitingTonyCount"));
        }
    }

    /** One attached file. {@code storageCode} is "repo" (Leonardo Files) or "nook" (the git-ignored solutions nook). */
    public record FileRow(int id, int solutionId, Integer itemId, String itemTitle, String title, String originalFileName,
                          String storageCode, Integer leonardoFileId, String storedFileName, String contentType,
                          long sizeBytes, String notes, String uploadedByCode, String uploadedByName,
                          LocalDateTime createdDate, LocalDateTime modifiedDate) {
        static FileRow of(Map<String, Object> r) {
            Object size = r.get("FileSizeBytes");
            return new FileRow(intOf(r, "SolutionFileID"), intOf(r, "SolutionID"), idOrNull(r, "SolutionProjectItemID"),
                strOf(r, "ItemTitle"), strOf(r, "FileTitle"), strOf(r, "OriginalFileName"), strOf(r, "StorageCode"),
                idOrNull(r, "LeonardoFileID"), strOf(r, "StoredFileName"), strOf(r, "ContentType"),
                size instanceof Number n ? n.longValue() : 0L, strOf(r, "FileNotes"), strOf(r, "UploadedByCode"),
                strOf(r, "UploadedByName"), dateOf(r, "CreatedDate"), dateOf(r, "ModifiedDate"));
        }
    }

    public record Message(int id, int conversationId, String sender, String body, LocalDateTime createdDate,
                          String awaitingFrom) {
        static Message of(Map<String, Object> r) {
            return new Message(intOf(r, "ConversationMessageID"), intOf(r, "ConversationID"), strOf(r, "Sender"),
                strOf(r, "Body"), dateOf(r, "CreatedDate"), strOf(r, "AwaitingResponseFrom"));
        }
    }
}
