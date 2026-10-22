SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionFile_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionFile_Update
GO
/*

Trigger trg_SolutionFile_Update on dbo.SolutionFile.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionFile_LOG (the Solutions family).

*/

CREATE TRIGGER dbo.trg_SolutionFile_Update ON dbo.SolutionFile AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionFile_LOG (IsInsertion, SolutionFileID, SolutionID, SolutionProjectItemID, FileTitle, OriginalFileName, StorageCode, LeonardoFileID, StoredFileName, ContentType, FileSizeBytes, FileNotes, UploadedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, SolutionFileID, SolutionID, SolutionProjectItemID, FileTitle, OriginalFileName, StorageCode, LeonardoFileID, StoredFileName, ContentType, FileSizeBytes, FileNotes, UploadedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
