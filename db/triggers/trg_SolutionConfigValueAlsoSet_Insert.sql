SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigValueAlsoSet_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigValueAlsoSet_Insert
GO
/*

Trigger trg_SolutionConfigValueAlsoSet_Insert on dbo.SolutionConfigValueAlsoSet.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigValueAlsoSet_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigValueAlsoSet_Insert ON dbo.SolutionConfigValueAlsoSet AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigValueAlsoSet_LOG (IsInsertion, SolutionConfigValueAlsoSetID, SolutionConfigValueID, SolutionConfigSourceID, CreatedDate)
    SELECT 1, SolutionConfigValueAlsoSetID, SolutionConfigValueID, SolutionConfigSourceID, CreatedDate FROM Inserted; END;

GO
