SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTermCategoryXRef_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTermCategoryXRef_Delete
GO
/*

Trigger trg_SolutionTermCategoryXRef_Delete on dbo.SolutionTermCategoryXRef.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTermCategoryXRef_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTermCategoryXRef_Delete ON dbo.SolutionTermCategoryXRef AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTermCategoryXRef_LOG (IsInsertion, DeletedDate, SolutionTermCategoryXRefID, SolutionTermID, SolutionTermCategoryID, CreatedDate)
    SELECT 0, GETDATE(), SolutionTermCategoryXRefID, SolutionTermID, SolutionTermCategoryID, CreatedDate FROM Deleted; END;

GO
