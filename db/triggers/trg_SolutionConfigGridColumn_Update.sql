SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridColumn_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridColumn_Update
GO
/*

Trigger trg_SolutionConfigGridColumn_Update on dbo.SolutionConfigGridColumn.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridColumn_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridColumn_Update ON dbo.SolutionConfigGridColumn AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridColumn_LOG (IsInsertion, SolutionConfigGridColumnID, SolutionID, ColumnTitle, ColorHex, IsDefaultColumn, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, SolutionConfigGridColumnID, SolutionID, ColumnTitle, ColorHex, IsDefaultColumn, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
