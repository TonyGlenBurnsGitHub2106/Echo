SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionFileVersion_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionFileVersion_Insert
GO
/*

Trigger trg_SolutionFileVersion_Insert on dbo.SolutionFileVersion.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-04  Felix        Story 5695 (Echo) Release 5: audit row into SolutionFileVersion_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionFileVersion_Insert ON dbo.SolutionFileVersion AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionFileVersion_LOG (IsInsertion, SolutionFileVersionID, SolutionFileID, VersionNumber, OriginalFileName, StoredFileName, ContentType, FileSizeBytes, ChangeNote, UploadedByMemberID, UploadedDate, CreatedDate)
    SELECT 1, SolutionFileVersionID, SolutionFileID, VersionNumber, OriginalFileName, StoredFileName, ContentType, FileSizeBytes, ChangeNote, UploadedByMemberID, UploadedDate, CreatedDate FROM Inserted; END;

GO
