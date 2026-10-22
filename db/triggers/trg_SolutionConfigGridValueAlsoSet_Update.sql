SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigGridValueAlsoSet_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigGridValueAlsoSet_Update
GO
/*

Trigger trg_SolutionConfigGridValueAlsoSet_Update on dbo.SolutionConfigGridValueAlsoSet.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-09  Felix        Story 5611 Solution Configuration II: audit row into SolutionConfigGridValueAlsoSet_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigGridValueAlsoSet_Update ON dbo.SolutionConfigGridValueAlsoSet AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigGridValueAlsoSet_LOG (IsInsertion, SolutionConfigGridValueAlsoSetID, SolutionConfigGridValueID, SolutionConfigSourceID, CreatedDate)
    SELECT 0, SolutionConfigGridValueAlsoSetID, SolutionConfigGridValueID, SolutionConfigSourceID, CreatedDate FROM Inserted; END;

GO
