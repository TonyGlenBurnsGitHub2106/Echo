SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionFileFiling_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionFileFiling_Insert
GO
/*

Trigger trg_SolutionFileFiling_Insert on dbo.SolutionFileFiling.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-05  Felix        Story 5594 (organized Solutions Files): audit row into SolutionFileFiling_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionFileFiling_Insert ON dbo.SolutionFileFiling AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionFileFiling_LOG (IsInsertion, SolutionFileFilingID, SolutionFileID, FileCategoryID, IsImportant, DocumentDate, FiledDate, CreatedDate, ModifiedDate, ModifiedBy)
    SELECT 1, SolutionFileFilingID, SolutionFileID, FileCategoryID, IsImportant, DocumentDate, FiledDate, CreatedDate, ModifiedDate, ModifiedBy FROM Inserted; END;

GO
