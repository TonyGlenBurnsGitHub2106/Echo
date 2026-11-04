package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.idOrNull;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The earlier versions of a solution file (Story 5695, Echo first, Release 5). "New version" keeps the file's row,
 * so its links, title, item and filing stay; what it had becomes a version, and its bytes stay on disk.
 */
final class SolutionFileVersionModels {

    private SolutionFileVersionModels() {}

    /** changeNote says what the version AFTER this one changed. solutionId is read only by one version's Get. */
    public record FileVersion(int id, int fileId, int versionNumber, String originalFileName, String storedFileName,
                              String contentType, long sizeBytes, String changeNote, LocalDateTime uploadedDate,
                              String uploadedByCode, String uploadedByName, Integer solutionId) {
        static FileVersion of(Map<String, Object> r) {
            Object size = r.get("FileSizeBytes");
            return new FileVersion(intOf(r, "SolutionFileVersionID"), intOf(r, "SolutionFileID"), intOf(r, "VersionNumber"),
                strOf(r, "OriginalFileName"), strOf(r, "StoredFileName"), strOf(r, "ContentType"),
                size instanceof Number n ? n.longValue() : 0L, strOf(r, "ChangeNote"), dateOf(r, "UploadedDate"),
                strOf(r, "UploadedByCode"), strOf(r, "UploadedByName"), idOrNull(r, "SolutionID"));
        }
    }

    /** The number the file's current bytes are (1 until a new version), and the earlier ones, newest first. */
    public record Versions(int currentVersion, List<FileVersion> versions) {
        static Versions of(List<FileVersion> earlier) {
            return new Versions(earlier.isEmpty() ? 1 : earlier.get(0).versionNumber() + 1, earlier);
        }
    }
}
