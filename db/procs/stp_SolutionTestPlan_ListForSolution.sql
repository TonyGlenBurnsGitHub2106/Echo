SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlan_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlan_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlan_ListForSolution
    @prmSolutionID   INT,
    @prmPlanKindCode VARCHAR(10) = NULL   -- dev | qa; NULL = both
AS

/*

TEST
----

    EXEC stp_SolutionTestPlan_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: every testing checklist on one solution, in order, with its counts (groups,
                         cases, columns, checked cells) worked out here so the page never counts rows.
09/25/2026  Felix        Story 3534: adds SkippedCount (skipped cells -- neither pass nor fail).
10/15/2026  Felix        The QA tab: returns PlanKindCode (dev | qa); @prmPlanKindCode narrows the list to one kind
                         (NULL = every checklist).

*/

SET NOCOUNT ON

SELECT p.SolutionTestPlanID, p.SolutionID, p.PlanTitle, p.PlanDescription, p.SortOrder,
       p.CreatedDate, p.ModifiedDate, p.ModifiedBy, p.PlanKindCode,
       (SELECT COUNT(*) FROM dbo.SolutionTestGroup g WHERE g.SolutionTestPlanID = p.SolutionTestPlanID) AS GroupCount,
       (SELECT COUNT(*) FROM dbo.SolutionTestCase c
        JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
        WHERE  g.SolutionTestPlanID = p.SolutionTestPlanID) AS CaseCount,
       (SELECT COUNT(*) FROM dbo.SolutionTestPlanLevel pl WHERE pl.SolutionTestPlanID = p.SolutionTestPlanID) AS LevelCount,
       (SELECT COUNT(*) FROM dbo.SolutionTestCheck k
        JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = k.SolutionTestPlanLevelID
        JOIN   dbo.SolutionTestCase c       ON c.SolutionTestCaseID       = k.SolutionTestCaseID
        JOIN   dbo.SolutionTestGroup g      ON g.SolutionTestGroupID      = c.SolutionTestGroupID
        WHERE  pl.SolutionTestPlanID = p.SolutionTestPlanID AND g.SolutionTestPlanID = p.SolutionTestPlanID
        AND    k.IsChecked = 1) AS CheckedCount,
       (SELECT COUNT(*) FROM dbo.SolutionTestSkip s
        JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = s.SolutionTestPlanLevelID
        JOIN   dbo.SolutionTestCase c       ON c.SolutionTestCaseID       = s.SolutionTestCaseID
        JOIN   dbo.SolutionTestGroup g      ON g.SolutionTestGroupID      = c.SolutionTestGroupID
        WHERE  pl.SolutionTestPlanID = p.SolutionTestPlanID AND g.SolutionTestPlanID = p.SolutionTestPlanID
        AND    s.IsSkipped = 1) AS SkippedCount
FROM   dbo.SolutionTestPlan p
WHERE  p.SolutionID = @prmSolutionID
AND    (@prmPlanKindCode IS NULL OR p.PlanKindCode = @prmPlanKindCode)
ORDER BY p.SortOrder, p.SolutionTestPlanID


GO
