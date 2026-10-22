SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridAxis_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridAxis_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridAxis_Delete
    @prmSolutionConfigGridAxisID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridAxis_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: remove an axis with its values and every column's tag on it, in
                         one transaction. The columns and their cells stay. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionConfigGridColumnTag WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID
    DELETE FROM dbo.SolutionConfigGridAxisValue WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID
    DELETE FROM dbo.SolutionConfigGridAxis      WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID
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
