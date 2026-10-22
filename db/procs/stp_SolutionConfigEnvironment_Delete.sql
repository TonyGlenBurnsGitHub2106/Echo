SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigEnvironment_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigEnvironment_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionConfigEnvironment_Delete
    @prmSolutionConfigEnvironmentID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigEnvironment_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: remove one environment from a solution with every value in it. The
                         last environment cannot be removed. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @solutionId INT = (SELECT SolutionID FROM dbo.SolutionConfigEnvironment WHERE SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID)
IF @solutionId IS NULL BEGIN SELECT 0 AS RowsAffected RETURN END
IF (SELECT COUNT(*) FROM dbo.SolutionConfigEnvironment WHERE SolutionID = @solutionId) <= 1 BEGIN
    RAISERROR('A solution needs at least one environment.', 16, 1) RETURN
END

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE a FROM dbo.SolutionConfigValueAlsoSet a
    JOIN   dbo.SolutionConfigValue cv ON cv.SolutionConfigValueID = a.SolutionConfigValueID
    WHERE  cv.SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID
    DELETE FROM dbo.SolutionConfigValue       WHERE SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID
    DELETE FROM dbo.SolutionConfigEnvironment WHERE SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID
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
