SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestPlanLevel_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestPlanLevel_Delete
GO
/*

Trigger trg_SolutionTestPlanLevel_Delete on dbo.SolutionTestPlanLevel.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestPlanLevel_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestPlanLevel_Delete ON dbo.SolutionTestPlanLevel AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestPlanLevel_LOG (IsInsertion, DeletedDate, SolutionTestPlanLevelID, SolutionTestPlanID, SolutionTestLevelID, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), SolutionTestPlanLevelID, SolutionTestPlanID, SolutionTestLevelID, SortOrder, CreatedDate FROM Deleted; END;

GO
