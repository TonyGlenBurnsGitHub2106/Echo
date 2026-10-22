SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTermType_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTermType_Update
GO
/*

Trigger trg_SolutionTermType_Update on dbo.SolutionTermType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTermType_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTermType_Update ON dbo.SolutionTermType AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTermType_LOG (IsInsertion, SolutionTermTypeID, SolutionID, TypeName, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionTermTypeID, SolutionID, TypeName, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
