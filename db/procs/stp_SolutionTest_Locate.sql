SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTest_Locate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTest_Locate
GO
CREATE PROCEDURE dbo.stp_SolutionTest_Locate
    @prmKind VARCHAR(10),     -- plan | level | group | case
    @prmID   INT
AS

/*

TEST
----

    EXEC stp_SolutionTest_Locate 'case', 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: which solution and which checklist a plan, column, group or case belongs to
                         -- the service asks this before every read or write, so a scoped caller can never reach a checklist on a
                         solution it was not given. No row = it does not exist.

*/

SET NOCOUNT ON

SET @prmKind = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))

IF @prmKind = 'plan'
    SELECT p.SolutionID, p.SolutionTestPlanID FROM dbo.SolutionTestPlan p WHERE p.SolutionTestPlanID = @prmID
ELSE IF @prmKind = 'level'
    SELECT p.SolutionID, p.SolutionTestPlanID
    FROM   dbo.SolutionTestPlanLevel pl JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = pl.SolutionTestPlanID
    WHERE  pl.SolutionTestPlanLevelID = @prmID
ELSE IF @prmKind = 'group'
    SELECT p.SolutionID, p.SolutionTestPlanID
    FROM   dbo.SolutionTestGroup g JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = g.SolutionTestPlanID
    WHERE  g.SolutionTestGroupID = @prmID
ELSE IF @prmKind = 'case'
    SELECT p.SolutionID, p.SolutionTestPlanID
    FROM   dbo.SolutionTestCase c
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    JOIN   dbo.SolutionTestPlan p  ON p.SolutionTestPlanID  = g.SolutionTestPlanID
    WHERE  c.SolutionTestCaseID = @prmID
ELSE
    SELECT CAST(NULL AS INT) AS SolutionID, CAST(NULL AS INT) AS SolutionTestPlanID WHERE 1 = 0

GO
