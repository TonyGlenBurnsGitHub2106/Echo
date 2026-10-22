SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridValue_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridValue_Insert
GO
/*

Trigger trg_SolutionConfigGridValue_Insert on dbo.SolutionConfigGridValue.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridValue_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridValue_Insert ON dbo.SolutionConfigGridValue AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridValue_LOG (IsInsertion, SolutionConfigGridValueID, SolutionConfigGridPropertyID, SolutionConfigGridColumnID, ValueText, SolutionConfigSourceID, ValueNote, SolutionConfigGridCellStateID, ModifiedByMemberID, CreatedDate, ModifiedDate)
    SELECT 1, SolutionConfigGridValueID, SolutionConfigGridPropertyID, SolutionConfigGridColumnID, ValueText, SolutionConfigSourceID, ValueNote, SolutionConfigGridCellStateID, ModifiedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
