package com.echo;

import com.echo.SolutionModels.FileRow;

import java.util.List;

/**
 * The catalog of files attached to solutions (Story 3493). Rows only -- where
 * the bytes live is {@link SolutionFileService}'s business. Jdbc = stp_SolutionFile*.
 */
public interface SolutionFileRepository {

    List<FileRow> listForSolution(int solutionId, Integer itemId);

    FileRow find(int id);

    /** Record a newly stored file; returns its id. */
    int insert(int solutionId, Integer itemId, String title, String originalFileName, String storageCode,
               Integer leonardoFileId, String storedFileName, String contentType, long sizeBytes, String notes,
               String authorCode);

    /** Edit title, notes and item. Never moves the bytes. */
    boolean update(int id, Integer itemId, String title, String notes);

    boolean delete(int id);

    /** Every attached file's id, oldest first -- for the one-pass secure sweep. */

}
