SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTermCategory_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTermCategory_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTermCategory_Delete
    @prmSolutionID INT,
    @prmID         INT
AS

/*

TEST
----

    EXEC stp_SolutionTermCategory_Delete 6, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: remove one category of one solution, in one transaction.
                         The category is taken off every term that carried it; no term is removed.
                         Refused when it is not this solution's. Returns RowsAffected.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTermCategory WHERE SolutionTermCategoryID = @prmID AND SolutionID = @prmSolutionID)
BEGIN RAISERROR('That category is not part of this solution. Nothing was removed.', 16, 1) RETURN END

DECLARE @rows INT = 0

SET XACT_ABORT ON

BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionTermCategoryXRef WHERE SolutionTermCategoryID = @prmID
    DELETE FROM dbo.SolutionTermCategory WHERE SolutionTermCategoryID = @prmID
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
