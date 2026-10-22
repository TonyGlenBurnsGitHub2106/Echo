SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestCheck_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestCheck_Delete
GO
/*

Trigger trg_SolutionTestCheck_Delete on dbo.SolutionTestCheck.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestCheck_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestCheck_Delete ON dbo.SolutionTestCheck AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestCheck_LOG (IsInsertion, DeletedDate, SolutionTestCheckID, SolutionTestCaseID, SolutionTestPlanLevelID, IsChecked, CheckedDate, CheckedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTestCheckID, SolutionTestCaseID, SolutionTestPlanLevelID, IsChecked, CheckedDate, CheckedByMemberID, CreatedDate, ModifiedDate FROM Deleted; END;

GO
