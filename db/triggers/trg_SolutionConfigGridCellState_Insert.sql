SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridCellState_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridCellState_Insert
GO
/*

Trigger trg_SolutionConfigGridCellState_Insert on dbo.SolutionConfigGridCellState.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridCellState_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridCellState_Insert ON dbo.SolutionConfigGridCellState AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridCellState_LOG (IsInsertion, SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder, CreatedDate)
    SELECT 1, SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder, CreatedDate FROM Inserted; END;

GO
