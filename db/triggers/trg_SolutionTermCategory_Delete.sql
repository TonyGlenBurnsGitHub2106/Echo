SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTermCategory_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTermCategory_Delete
GO
/*

Trigger trg_SolutionTermCategory_Delete on dbo.SolutionTermCategory.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTermCategory_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTermCategory_Delete ON dbo.SolutionTermCategory AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTermCategory_LOG (IsInsertion, DeletedDate, SolutionTermCategoryID, SolutionID, CategoryName, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTermCategoryID, SolutionID, CategoryName, CreatedDate, ModifiedDate FROM Deleted; END;

GO
