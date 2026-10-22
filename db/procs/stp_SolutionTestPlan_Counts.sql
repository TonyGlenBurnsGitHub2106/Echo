SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlan_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlan_Counts
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlan_Counts
    @prmSolutionTestPlanID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestPlan_Counts 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: the counters of one checklist as Bucket / BucketKey / Cnt rows -- cases
                         (all), checked (all cells), level:<column> checked in that column, group:<group> cases in that group,
                         groupchecked:<group> checked cells in that group, grouplevel:<group>.<column> checked in that group and
                         column.
09/25/2026  Felix        Adds failing (cases marked failing) and groupfailing:<group>.
09/25/2026  Felix        Story 3534: adds skipped (skipped cells), levelskipped:<column>, groupskipped:<group> and
                         grouplevelskipped:<group>.<column>. A skipped cell is neither ticked nor failed; the page takes it
                         out of the denominator.

*/

SET NOCOUNT ON

;WITH cases AS (
    SELECT c.SolutionTestCaseID, c.SolutionTestGroupID
    FROM   dbo.SolutionTestCase c
    JOIN   dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID
), checks AS (
    SELECT cs.SolutionTestGroupID, k.SolutionTestPlanLevelID
    FROM   dbo.SolutionTestCheck k
    JOIN   cases cs ON cs.SolutionTestCaseID = k.SolutionTestCaseID
    JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = k.SolutionTestPlanLevelID
    WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID AND k.IsChecked = 1
), skips AS (
    SELECT cs.SolutionTestGroupID, s.SolutionTestPlanLevelID
    FROM   dbo.SolutionTestSkip s
    JOIN   cases cs ON cs.SolutionTestCaseID = s.SolutionTestCaseID
    JOIN   dbo.SolutionTestPlanLevel pl ON pl.SolutionTestPlanLevelID = s.SolutionTestPlanLevelID
    WHERE  pl.SolutionTestPlanID = @prmSolutionTestPlanID AND s.IsSkipped = 1
), failing AS (
    SELECT cs.SolutionTestGroupID
    FROM   dbo.SolutionTestResult r
    JOIN   cases cs ON cs.SolutionTestCaseID = r.SolutionTestCaseID
    WHERE  r.IsFailing = 1
)
SELECT 'cases' AS Bucket, CAST('' AS VARCHAR(40)) AS BucketKey, COUNT(*) AS Cnt FROM cases
UNION ALL
SELECT 'checked', '', COUNT(*) FROM checks
UNION ALL
SELECT 'level', CAST(SolutionTestPlanLevelID AS VARCHAR(40)), COUNT(*) FROM checks GROUP BY SolutionTestPlanLevelID
UNION ALL
SELECT 'group', CAST(SolutionTestGroupID AS VARCHAR(40)), COUNT(*) FROM cases GROUP BY SolutionTestGroupID
UNION ALL
SELECT 'groupchecked', CAST(SolutionTestGroupID AS VARCHAR(40)), COUNT(*) FROM checks GROUP BY SolutionTestGroupID
UNION ALL
SELECT 'grouplevel', CAST(SolutionTestGroupID AS VARCHAR(20)) + '.' + CAST(SolutionTestPlanLevelID AS VARCHAR(19)), COUNT(*)
FROM   checks GROUP BY SolutionTestGroupID, SolutionTestPlanLevelID
UNION ALL
SELECT 'failing', '', COUNT(*) FROM failing
UNION ALL
SELECT 'groupfailing', CAST(SolutionTestGroupID AS VARCHAR(40)), COUNT(*) FROM failing GROUP BY SolutionTestGroupID
UNION ALL
SELECT 'skipped', '', COUNT(*) FROM skips
UNION ALL
SELECT 'levelskipped', CAST(SolutionTestPlanLevelID AS VARCHAR(40)), COUNT(*) FROM skips GROUP BY SolutionTestPlanLevelID
UNION ALL
SELECT 'groupskipped', CAST(SolutionTestGroupID AS VARCHAR(40)), COUNT(*) FROM skips GROUP BY SolutionTestGroupID
UNION ALL
SELECT 'grouplevelskipped', CAST(SolutionTestGroupID AS VARCHAR(20)) + '.' + CAST(SolutionTestPlanLevelID AS VARCHAR(19)), COUNT(*)
FROM   skips GROUP BY SolutionTestGroupID, SolutionTestPlanLevelID


GO
