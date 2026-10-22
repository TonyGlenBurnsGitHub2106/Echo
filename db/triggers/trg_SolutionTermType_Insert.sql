SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTermType_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTermType_Insert
GO
/*

Trigger trg_SolutionTermType_Insert on dbo.SolutionTermType.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Solution Terms tab: audit row into SolutionTermType_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTermType_Insert ON dbo.SolutionTermType AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTermType_LOG (IsInsertion, SolutionTermTypeID, SolutionID, TypeName, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTermTypeID, SolutionID, TypeName, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
