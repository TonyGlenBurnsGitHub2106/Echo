SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlanLevel_ListForPlan') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlanLevel_ListForPlan
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlanLevel_ListForPlan
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestPlanLevel_ListForPlan 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: the columns (levels of testing) of one checklist, left to right.

*/

SET NOCOUNT ON

SELECT pl.SolutionTestPlanLevelID, pl.SolutionTestPlanID, pl.SolutionTestLevelID, l.LevelTitle, pl.SortOrder
FROM   dbo.SolutionTestPlanLevel pl
JOIN   dbo.SolutionTestLevel l ON l.SolutionTestLevelID = pl.SolutionTestLevelID
WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID
ORDER BY pl.SortOrder, pl.SolutionTestPlanLevelID

GO
