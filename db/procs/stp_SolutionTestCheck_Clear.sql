SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCheck_Clear') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCheck_Clear
GO
CREATE PROCEDURE dbo.stp_SolutionTestCheck_Clear
    @prmSolutionTestPlanID      INT,
    @prmSolutionTestPlanLevelID INT = NULL      -- NULL = every column
AS

/*

TEST
----

    EXEC stp_SolutionTestCheck_Clear 1, NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: untick every cell of one checklist, or of one column of it, to run the
                         tests again. The cases, groups and columns stay; the _LOG twin keeps what was ticked. Returns
                         RowsAffected (cells unticked).
09/25/2026  Felix        Clearing the WHOLE checklist also takes every failing mark off (the result notes stay), so a rerun
                         starts clean. Clearing one column leaves the failing marks alone. RowsAffected is still cells.

*/

SET NOCOUNT ON

IF ISNULL(@prmSolutionTestPlanLevelID, 0) <= 0 SET @prmSolutionTestPlanLevelID = NULL

UPDATE k
SET    IsChecked = 0, CheckedDate = NULL, CheckedByMemberID = NULL, ModifiedDate = GETDATE()
FROM   dbo.SolutionTestCheck k
JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = k.SolutionTestPlanLevelID
WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID
AND    (@prmSolutionTestPlanLevelID IS NULL OR k.SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID)
AND    k.IsChecked = 1

DECLARE @rows INT = @@ROWCOUNT

IF @prmSolutionTestPlanLevelID IS NULL
    UPDATE r
    SET    IsFailing = 0, FailingDate = NULL, ModifiedDate = GETDATE()
    FROM   dbo.SolutionTestResult r
    JOIN   dbo.SolutionTestCase c  ON c.SolutionTestCaseID  = r.SolutionTestCaseID
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID AND r.IsFailing = 1

SELECT @rows AS RowsAffected


GO
