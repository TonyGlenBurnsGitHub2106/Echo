SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTermCategory_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTermCategory_Update
GO
/*

Trigger trg_SolutionTermCategory_Update on dbo.SolutionTermCategory.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTermCategory_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTermCategory_Update ON dbo.SolutionTermCategory AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTermCategory_LOG (IsInsertion, SolutionTermCategoryID, SolutionID, CategoryName, CreatedDate, ModifiedDate)
    SELECT 0, SolutionTermCategoryID, SolutionID, CategoryName, CreatedDate, ModifiedDate FROM Inserted; END;

GO
