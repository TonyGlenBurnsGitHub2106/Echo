SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestGroup_ListForPlan') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestGroup_ListForPlan
GO
CREATE PROCEDURE dbo.stp_SolutionTestGroup_ListForPlan
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestGroup_ListForPlan 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: the groups of one checklist, in order.

*/

SET NOCOUNT ON

SELECT g.SolutionTestGroupID, g.SolutionTestPlanID, g.GroupTitle, g.SortOrder, g.CreatedDate, g.ModifiedDate
FROM   dbo.SolutionTestGroup g
WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
ORDER BY g.SortOrder, g.SolutionTestGroupID

GO
