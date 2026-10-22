SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCase_ListForPlan') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCase_ListForPlan
GO
CREATE PROCEDURE dbo.stp_SolutionTestCase_ListForPlan
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestCase_ListForPlan 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: every case of one checklist, ordered by group and then by the case order
                         inside its group. A checklist is shown whole, so it is not paged.
09/21/2026  Felix        Returns IncludeInPrint.
09/25/2026  Felix        Returns the case's result: IsFailing, FailingDate, ResultNote, who set it and when (SolutionTestResult).

*/

SET NOCOUNT ON

SELECT c.SolutionTestCaseID, c.SolutionTestGroupID, c.CaseText, c.CaseNote, c.SortOrder, c.IncludeInPrint, c.CreatedDate, c.ModifiedDate,
       ISNULL(r.IsFailing, 0) AS IsFailing, r.FailingDate, r.ResultNote, r.ModifiedDate AS ResultModifiedDate,
       m.MemberCode AS ResultByCode, m.MemberName AS ResultByName
FROM   dbo.SolutionTestCase c
JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
LEFT JOIN dbo.SolutionTestResult r ON r.SolutionTestCaseID = c.SolutionTestCaseID
LEFT JOIN dbo.SolutionMember m     ON m.SolutionMemberID   = r.ModifiedByMemberID
WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
ORDER BY g.SortOrder, g.SolutionTestGroupID, c.SortOrder, c.SolutionTestCaseID


GO
