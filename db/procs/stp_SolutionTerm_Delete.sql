SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTerm_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTerm_Delete
GO
CREATE PROCEDURE dbo.stp_SolutionTerm_Delete
    @prmSolutionTermID INT
AS

/*

TEST
----

    EXEC stp_SolutionTerm_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: remove one term and its category links, in one transaction. Its type
                         and categories stay (they belong to the solution). Every row removed lands in its _LOG
                         twin. Returns RowsAffected.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @rows INT = 0

BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionTermCategoryXRef WHERE SolutionTermID = @prmSolutionTermID
    DELETE FROM dbo.SolutionTerm WHERE SolutionTermID = @prmSolutionTermID
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
