SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestSkip_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestSkip_Insert
GO
/*

Trigger trg_SolutionTestSkip_Insert on dbo.SolutionTestSkip.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-25  Felix        Story 3534, skipped test cells: audit row into SolutionTestSkip_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestSkip_Insert ON dbo.SolutionTestSkip AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestSkip_LOG (IsInsertion, SolutionTestSkipID, SolutionTestCaseID, SolutionTestPlanLevelID, IsSkipped, SkippedDate, SkippedByMemberID, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTestSkipID, SolutionTestCaseID, SolutionTestPlanLevelID, IsSkipped, SkippedDate, SkippedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
