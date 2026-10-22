SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGroup_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGroup_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGroup_Delete
    @prmSolutionConfigGroupID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGroup_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: remove one property group with its properties and every value of
                         them, in one transaction. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE a FROM dbo.SolutionConfigValueAlsoSet a
    JOIN   dbo.SolutionConfigValue cv    ON cv.SolutionConfigValueID    = a.SolutionConfigValueID
    JOIN   dbo.SolutionConfigProperty p  ON p.SolutionConfigPropertyID  = cv.SolutionConfigPropertyID
    WHERE  p.SolutionConfigGroupID = @prmSolutionConfigGroupID
    DELETE cv FROM dbo.SolutionConfigValue cv
    JOIN   dbo.SolutionConfigProperty p ON p.SolutionConfigPropertyID = cv.SolutionConfigPropertyID
    WHERE  p.SolutionConfigGroupID = @prmSolutionConfigGroupID
    DELETE FROM dbo.SolutionConfigProperty WHERE SolutionConfigGroupID = @prmSolutionConfigGroupID
    DELETE FROM dbo.SolutionConfigGroup    WHERE SolutionConfigGroupID = @prmSolutionConfigGroupID
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
