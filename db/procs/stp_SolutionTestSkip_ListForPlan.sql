SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestSkip_ListForPlan') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestSkip_ListForPlan
GO
CREATE PROCEDURE dbo.stp_SolutionTestSkip_ListForPlan
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestSkip_ListForPlan 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/25/2026  Felix        Story 3534: the SKIPPED cells of one checklist with who skipped each and when. A cell that is not
                         skipped is simply absent.

*/

SET NOCOUNT ON

SELECT s.SolutionTestSkipID, s.SolutionTestCaseID, s.SolutionTestPlanLevelID, s.IsSkipped, s.SkippedDate,
       m.MemberCode AS SkippedByCode, m.MemberName AS SkippedByName
FROM   dbo.SolutionTestSkip s
JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = s.SolutionTestPlanLevelID
JOIN   dbo.SolutionTestCase c       ON c.SolutionTestCaseID       = s.SolutionTestCaseID
JOIN   dbo.SolutionTestGroup g      ON g.SolutionTestGroupID      = c.SolutionTestGroupID
LEFT JOIN dbo.SolutionMember m      ON m.SolutionMemberID         = s.SkippedByMemberID
WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID
AND    g.SolutionTestPlanID  = @prmSolutionTestPlanID
AND    s.IsSkipped = 1


GO
