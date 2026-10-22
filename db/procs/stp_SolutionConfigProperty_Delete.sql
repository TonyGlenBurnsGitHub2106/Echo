SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigProperty_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigProperty_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionConfigProperty_Delete
    @prmSolutionConfigPropertyID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigProperty_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: remove one property with its value in every environment. Returns
                         RowsAffected.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE a FROM dbo.SolutionConfigValueAlsoSet a
    JOIN   dbo.SolutionConfigValue cv ON cv.SolutionConfigValueID = a.SolutionConfigValueID
    WHERE  cv.SolutionConfigPropertyID = @prmSolutionConfigPropertyID
    DELETE FROM dbo.SolutionConfigValue    WHERE SolutionConfigPropertyID = @prmSolutionConfigPropertyID
    DELETE FROM dbo.SolutionConfigProperty WHERE SolutionConfigPropertyID = @prmSolutionConfigPropertyID
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
