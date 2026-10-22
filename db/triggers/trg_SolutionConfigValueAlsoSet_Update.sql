SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigValueAlsoSet_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigValueAlsoSet_Update
GO
/*

Trigger trg_SolutionConfigValueAlsoSet_Update on dbo.SolutionConfigValueAlsoSet.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigValueAlsoSet_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigValueAlsoSet_Update ON dbo.SolutionConfigValueAlsoSet AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigValueAlsoSet_LOG (IsInsertion, SolutionConfigValueAlsoSetID, SolutionConfigValueID, SolutionConfigSourceID, CreatedDate)
    SELECT 0, SolutionConfigValueAlsoSetID, SolutionConfigValueID, SolutionConfigSourceID, CreatedDate FROM Inserted; END;

GO
