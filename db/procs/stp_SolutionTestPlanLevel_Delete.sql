SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlanLevel_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlanLevel_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlanLevel_Delete
    @prmSolutionTestPlanLevelID INT
AS

/*

TEST
----

    EXEC stp_SolutionTestPlanLevel_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: remove one column from a testing checklist with every tick in it. The last
                         column cannot be removed -- a checklist with no columns has nothing to tick. Returns RowsAffected.
09/25/2026  Felix        Story 3534: takes the column's skipped cells too.

*/

SET NOCOUNT ON

DECLARE @planId INT = (SELECT SolutionTestPlanID FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID)
IF @planId IS NULL BEGIN SELECT 0 AS RowsAffected RETURN END
IF (SELECT COUNT(*) FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanID = @planId) <= 1 BEGIN
    RAISERROR('A testing checklist needs at least one column.', 16, 1) RETURN
END

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionTestCheck     WHERE SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
    DELETE FROM dbo.SolutionTestSkip      WHERE SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
    DELETE FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
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
