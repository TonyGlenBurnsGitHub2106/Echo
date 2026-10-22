SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTermCategoryXRef_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTermCategoryXRef_Update
GO
/*

Trigger trg_SolutionTermCategoryXRef_Update on dbo.SolutionTermCategoryXRef.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTermCategoryXRef_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTermCategoryXRef_Update ON dbo.SolutionTermCategoryXRef AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTermCategoryXRef_LOG (IsInsertion, SolutionTermCategoryXRefID, SolutionTermID, SolutionTermCategoryID, CreatedDate)
    SELECT 0, SolutionTermCategoryXRefID, SolutionTermID, SolutionTermCategoryID, CreatedDate FROM Inserted; END;

GO
