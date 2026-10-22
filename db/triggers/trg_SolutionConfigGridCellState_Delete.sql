SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridCellState_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridCellState_Delete
GO
/*

Trigger trg_SolutionConfigGridCellState_Delete on dbo.SolutionConfigGridCellState.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridCellState_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridCellState_Delete ON dbo.SolutionConfigGridCellState AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridCellState_LOG (IsInsertion, DeletedDate, SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder, CreatedDate FROM Deleted; END;

GO
