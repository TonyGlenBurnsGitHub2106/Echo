SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestGroup_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestGroup_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTestGroup_Delete
    @prmSolutionTestGroupID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestGroup_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: remove one group with its cases and their ticks, in one transaction.
                         Returns RowsAffected.
09/25/2026  Felix        Takes the cases' results (failing marks and result notes) too.
09/25/2026  Felix        Story 3534: takes the cases' skipped cells too.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE k FROM dbo.SolutionTestCheck k
    JOIN   dbo.SolutionTestCase c ON c.SolutionTestCaseID = k.SolutionTestCaseID
    WHERE  c.SolutionTestGroupID = @prmSolutionTestGroupID
    DELETE r FROM dbo.SolutionTestResult r
    JOIN   dbo.SolutionTestCase c ON c.SolutionTestCaseID = r.SolutionTestCaseID
    WHERE  c.SolutionTestGroupID = @prmSolutionTestGroupID
    DELETE s FROM dbo.SolutionTestSkip s
    JOIN   dbo.SolutionTestCase c ON c.SolutionTestCaseID = s.SolutionTestCaseID
    WHERE  c.SolutionTestGroupID = @prmSolutionTestGroupID
    DELETE FROM dbo.SolutionTestCase  WHERE SolutionTestGroupID = @prmSolutionTestGroupID
    DELETE FROM dbo.SolutionTestGroup WHERE SolutionTestGroupID = @prmSolutionTestGroupID
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
