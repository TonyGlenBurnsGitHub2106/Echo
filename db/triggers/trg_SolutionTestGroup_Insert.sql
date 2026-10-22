SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestGroup_Insert') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestGroup_Insert
GO
/*

Trigger trg_SolutionTestGroup_Insert on dbo.SolutionTestGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestGroup_Insert ON dbo.SolutionTestGroup AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestGroup_LOG (IsInsertion, SolutionTestGroupID, SolutionTestPlanID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, SolutionTestGroupID, SolutionTestPlanID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
