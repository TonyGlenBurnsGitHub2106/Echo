SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCheck_ListForPlan') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCheck_ListForPlan
GO
CREATE PROCEDURE dbo.stp_SolutionTestCheck_ListForPlan
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestCheck_ListForPlan 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: the CHECKED cells of one checklist with who checked each and when. An
                         unchecked cell is simply absent.

*/

SET NOCOUNT ON

SELECT k.SolutionTestCheckID, k.SolutionTestCaseID, k.SolutionTestPlanLevelID, k.IsChecked, k.CheckedDate,
       m.MemberCode AS CheckedByCode, m.MemberName AS CheckedByName
FROM   dbo.SolutionTestCheck k
JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = k.SolutionTestPlanLevelID
JOIN   dbo.SolutionTestCase c       ON c.SolutionTestCaseID       = k.SolutionTestCaseID
JOIN   dbo.SolutionTestGroup g      ON g.SolutionTestGroupID      = c.SolutionTestGroupID
LEFT JOIN dbo.SolutionMember m      ON m.SolutionMemberID         = k.CheckedByMemberID
WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID
AND    g.SolutionTestPlanID  = @prmSolutionTestPlanID
AND    k.IsChecked = 1

GO
