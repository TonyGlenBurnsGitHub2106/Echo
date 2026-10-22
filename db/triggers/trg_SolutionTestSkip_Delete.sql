SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestSkip_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestSkip_Delete
GO
/*

Trigger trg_SolutionTestSkip_Delete on dbo.SolutionTestSkip.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-25  Felix        Story 3534, skipped test cells: audit row into SolutionTestSkip_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestSkip_Delete ON dbo.SolutionTestSkip AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestSkip_LOG (IsInsertion, DeletedDate, SolutionTestSkipID, SolutionTestCaseID, SolutionTestPlanLevelID, IsSkipped, SkippedDate, SkippedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTestSkipID, SolutionTestCaseID, SolutionTestPlanLevelID, IsSkipped, SkippedDate, SkippedByMemberID, CreatedDate, ModifiedDate FROM Deleted; END;

GO
