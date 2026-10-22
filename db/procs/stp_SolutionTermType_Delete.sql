SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTermType_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTermType_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTermType_Delete
    @prmSolutionID INT,
    @prmID         INT
AS

/*

TEST
----

    EXEC stp_SolutionTermType_Delete 6, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: remove one type of one solution, in one transaction.
                         Every term of that type is left with no type (it moves to "(No type)"); no term is removed.
                         Refused when it is not this solution's. Returns RowsAffected.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTermType WHERE SolutionTermTypeID = @prmID AND SolutionID = @prmSolutionID)
BEGIN RAISERROR('That type is not part of this solution. Nothing was removed.', 16, 1) RETURN END

DECLARE @rows INT = 0

SET XACT_ABORT ON

BEGIN TRY
    BEGIN TRANSACTION
    UPDATE dbo.SolutionTerm SET SolutionTermTypeID = NULL, ModifiedDate = GETDATE() WHERE SolutionTermTypeID = @prmID
    DELETE FROM dbo.SolutionTermType WHERE SolutionTermTypeID = @prmID
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
