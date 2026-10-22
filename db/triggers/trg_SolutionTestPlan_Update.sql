SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_SolutionTestPlan_Update') IS NOT NULL DROP TRIGGER dbo.trg_SolutionTestPlan_Update
GO
/*

Trigger trg_SolutionTestPlan_Update on dbo.SolutionTestPlan.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-21  Felix        Solution testing checklists: audit row into SolutionTestPlan_LOG.
2026-10-15  Felix        Carries PlanKindCode (dev | qa -- the QA tab).

*/

CREATE TRIGGER dbo.trg_SolutionTestPlan_Update ON dbo.SolutionTestPlan AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.SolutionTestPlan_LOG (IsInsertion, SolutionTestPlanID, SolutionID, PlanTitle, PlanDescription, SortOrder, CreatedDate, ModifiedDate, ModifiedBy, PlanKindCode)
    SELECT 0, SolutionTestPlanID, SolutionID, PlanTitle, PlanDescription, SortOrder, CreatedDate, ModifiedDate, ModifiedBy, PlanKindCode FROM Inserted; END;

GO
