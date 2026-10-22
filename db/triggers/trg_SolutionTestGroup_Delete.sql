SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestGroup_Delete') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestGroup_Delete
GO
/*

Trigger trg_SolutionTestGroup_Delete on dbo.SolutionTestGroup.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestGroup_LOG.

*/

CREATE TRIGGER dbo.trg_SolutionTestGroup_Delete ON dbo.SolutionTestGroup AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestGroup_LOG (IsInsertion, DeletedDate, SolutionTestGroupID, SolutionTestPlanID, GroupTitle, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), SolutionTestGroupID, SolutionTestPlanID, GroupTitle, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
