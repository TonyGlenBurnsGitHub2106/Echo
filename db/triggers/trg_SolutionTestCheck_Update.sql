SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestCheck_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestCheck_Update
GO
/*

Trigger trg_SolutionTestCheck_Update on dbo.SolutionTestCheck.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestCheck_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestCheck_Update ON dbo.SolutionTestCheck AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestCheck_LOG (IsInsertion, SolutionTestCheckID, SolutionTestCaseID, SolutionTestPlanLevelID, IsChecked, CheckedDate, CheckedByMemberID, CreatedDate, ModifiedDate)
    SELECT 0, SolutionTestCheckID, SolutionTestCaseID, SolutionTestPlanLevelID, IsChecked, CheckedDate, CheckedByMemberID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
