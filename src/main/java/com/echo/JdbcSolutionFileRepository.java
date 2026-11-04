package com.echo;

import com.echo.SolutionModels.FileRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/** SQL Server backed {@link SolutionFileRepository} (Story 3493): the stp_SolutionFile* procs. */
@Repository
public class JdbcSolutionFileRepository implements SolutionFileRepository {

    private final SolutionJdbc db;

    public JdbcSolutionFileRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<FileRow> listForSolution(int solutionId, Integer itemId) {
        return db.list("EXEC dbo.stp_SolutionFile_ListForSolution ?, ?", FileRow::of, solutionId, itemId);
    }

    @Override
    public FileRow find(int id) { return db.one("EXEC dbo.stp_SolutionFile_Get ?", FileRow::of, id); }

    @Override
    public int insert(int solutionId, Integer itemId, String title, String originalFileName, String storageCode,
                      Integer leonardoFileId, String storedFileName, String contentType, long sizeBytes, String notes,
                      String authorCode) {
        return db.number("EXEC dbo.stp_SolutionFile_Save ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionFileID",
            0, solutionId, itemId, title, originalFileName, storageCode, leonardoFileId, storedFileName, contentType,
            sizeBytes, notes, authorCode);
    }

    // ---- Story 5695 (Echo) Release 5: versions ----

    @Override
    public int newVersion(int fileId, String originalFileName, String storedFileName, String contentType, long sizeBytes,
                          String changeNote, String memberCode) {
        return db.number("EXEC dbo.stp_SolutionFile_NewVersion ?, ?, ?, ?, ?, ?, ?", "CurrentVersionNumber",
            fileId, originalFileName, storedFileName, contentType, sizeBytes, changeNote, memberCode);
    }

    @Override
    public List<SolutionFileVersionModels.FileVersion> versions(int fileId) {
        return db.list("EXEC dbo.stp_SolutionFileVersion_ListForFile ?", SolutionFileVersionModels.FileVersion::of, fileId);
    }

    @Override
    public SolutionFileVersionModels.FileVersion version(int versionId) {
        return db.one("EXEC dbo.stp_SolutionFileVersion_Get ?", SolutionFileVersionModels.FileVersion::of, versionId);
    }

    @Override
    public boolean update(int id, Integer itemId, String title, String notes) {
        return db.number("EXEC dbo.stp_SolutionFile_Save ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?", "SolutionFileID",
            id, 0, itemId, title, null, null, null, null, null, 0L, notes, null) > 0;
    }

    @Override
    public boolean delete(int id) { return db.affected("EXEC dbo.stp_SolutionFile_Delete ?", id); }


}
