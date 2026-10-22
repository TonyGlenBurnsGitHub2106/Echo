SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlan_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlan_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlan_Delete
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestPlan_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: remove one testing checklist and everything in it -- ticks, cases, groups
                         and columns -- in one transaction. The level titles stay in the lookup for the next checklist. Every row
                         removed lands in its _LOG twin. Returns RowsAffected.
09/25/2026  Felix        Takes the cases' results (failing marks and result notes) too.
09/25/2026  Felix        Story 3534: takes the checklist's skipped cells too.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0

BEGIN TRY
    BEGIN TRANSACTION
    DELETE k FROM dbo.SolutionTestCheck k
    JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = k.SolutionTestPlanLevelID
    WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE k FROM dbo.SolutionTestCheck k
    JOIN   dbo.SolutionTestCase c  ON c.SolutionTestCaseID  = k.SolutionTestCaseID
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE s FROM dbo.SolutionTestSkip s
    JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = s.SolutionTestPlanLevelID
    WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE s FROM dbo.SolutionTestSkip s
    JOIN   dbo.SolutionTestCase c  ON c.SolutionTestCaseID  = s.SolutionTestCaseID
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE r FROM dbo.SolutionTestResult r
    JOIN   dbo.SolutionTestCase c  ON c.SolutionTestCaseID  = r.SolutionTestCaseID
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE c FROM dbo.SolutionTestCase c
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE FROM dbo.SolutionTestGroup     WHERE SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanID = @prmSolutionTestPlanID
    DELETE FROM dbo.SolutionTestPlan      WHERE SolutionTestPlanID = @prmSolutionTestPlanID
    SET @rows = @@ROWCOUNT
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @rows AS RowsAffected


GO
