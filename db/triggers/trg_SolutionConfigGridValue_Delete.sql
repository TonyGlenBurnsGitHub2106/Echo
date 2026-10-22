SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridValue_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridValue_Delete
GO
/*

Trigger trg_SolutionConfigGridValue_Delete on dbo.SolutionConfigGridValue.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridValue_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridValue_Delete ON dbo.SolutionConfigGridValue AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridValue_LOG (IsInsertion, DeletedDate, SolutionConfigGridValueID, SolutionConfigGridPropertyID, SolutionConfigGridColumnID, ValueText, SolutionConfigSourceID, ValueNote, SolutionConfigGridCellStateID, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionConfigGridValueID, SolutionConfigGridPropertyID, SolutionConfigGridColumnID, ValueText, SolutionConfigSourceID, ValueNote, SolutionConfigGridCellStateID, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Deleted; END;

GO
