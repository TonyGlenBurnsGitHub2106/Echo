SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClient_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClient_Delete
GO
CREATE PROCEDURE dbo.stp_ApiClient_Delete
    @prmApiClientID INT
AS

/*

TEST
----

    EXEC stp_ApiClient_Delete 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: remove one client and its solution visibility. Its access log rows are KEPT -- they
                         carry the client code, so what it did stays on the record. One transaction. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @rows INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionApiClient WHERE ApiClientID = @prmApiClientID
    DELETE FROM dbo.ApiClient         WHERE ApiClientID = @prmApiClientID
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
