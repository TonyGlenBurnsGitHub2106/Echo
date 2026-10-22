SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionConfigValueAlsoSet_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionConfigValueAlsoSet_Delete
GO
/*

Trigger trg_SolutionConfigValueAlsoSet_Delete on dbo.SolutionConfigValueAlsoSet.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Story 3516 Solution configuration: audit row into SolutionConfigValueAlsoSet_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionConfigValueAlsoSet_Delete ON dbo.SolutionConfigValueAlsoSet AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionConfigValueAlsoSet_LOG (IsInsertion, DeletedDate, SolutionConfigValueAlsoSetID, SolutionConfigValueID, SolutionConfigSourceID, CreatedDate)
    SELECT 0, GETDATE(), SolutionConfigValueAlsoSetID, SolutionConfigValueID, SolutionConfigSourceID, CreatedDate FROM Deleted; END;

GO
