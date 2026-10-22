SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestPlanLevel_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestPlanLevel_Insert
GO
/*

Trigger trg_SolutionTestPlanLevel_Insert on dbo.SolutionTestPlanLevel.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestPlanLevel_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestPlanLevel_Insert ON dbo.SolutionTestPlanLevel AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestPlanLevel_LOG (IsInsertion, SolutionTestPlanLevelID, SolutionTestPlanID, SolutionTestLevelID, SortOrder, CreatedDate)
    SELECT 1, SolutionTestPlanLevelID, SolutionTestPlanID, SolutionTestLevelID, SortOrder, CreatedDate FROM Inserted; END;

GO
