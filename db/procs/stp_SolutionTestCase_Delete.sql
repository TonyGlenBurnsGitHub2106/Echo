SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCase_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCase_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTestCase_Delete
    @prmSolutionTestCaseID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestCase_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: remove one test case and its ticks. Returns RowsAffected.
09/25/2026  Felix        Takes its result (failing mark and result note) too.
09/25/2026  Felix        Story 3534: takes its skipped cells too.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionTestCheck  WHERE SolutionTestCaseID = @prmSolutionTestCaseID
    DELETE FROM dbo.SolutionTestResult WHERE SolutionTestCaseID = @prmSolutionTestCaseID
    DELETE FROM dbo.SolutionTestSkip   WHERE SolutionTestCaseID = @prmSolutionTestCaseID
    DELETE FROM dbo.SolutionTestCase  WHERE SolutionTestCaseID = @prmSolutionTestCaseID
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
