SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionFile_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionFile_Insert
GO
/*

Trigger trg_SolutionFile_Insert on dbo.SolutionFile.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-17  Felix        Story 3493: audit row into SolutionFile_LOG (the Solutions family).
2026-11-04  Felix        Story 5695 (Echo) Release 5: CurrentVersionNumber.

*/

CREATE TRIGGER dbo.trg_SolutionFile_Insert ON dbo.SolutionFile AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionFile_LOG (IsInsertion, SolutionFileID, SolutionID, SolutionProjectItemID, FileTitle, OriginalFileName, StorageCode, LeonardoFileID, StoredFileName, ContentType, FileSizeBytes, FileNotes, UploadedByMemberID, CreatedDate, ModifiedDate, CurrentVersionNumber)
    SELECT 1, SolutionFileID, SolutionID, SolutionProjectItemID, FileTitle, OriginalFileName, StorageCode, LeonardoFileID, StoredFileName, ContentType, FileSizeBytes, FileNotes, UploadedByMemberID, CreatedDate, ModifiedDate, CurrentVersionNumber FROM Inserted; END;

GO
