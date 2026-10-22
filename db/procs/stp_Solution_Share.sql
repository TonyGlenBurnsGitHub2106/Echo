SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_Share') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_Share
GO
CREATE PROCEDURE dbo.stp_Solution_Share
    @prmSolutionID  INT,
    @prmApiClientID INT,
    @prmIsShared    BIT,
    @prmModifiedBy  VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_Solution_Share 0, 0, 0, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: share one solution with one API client, or stop. Sharing FORCES the solution's
                         attachments off the repo (SaveFilesToRepo = 0) in the same transaction, because nothing
                         an outside agent can upload may ever be committed. Sharing what is already shared is
                         not an error. Returns RowsAffected and FilesMovedOffRepo (1 when the switch was flipped).

*/

SET NOCOUNT ON

DECLARE @flipped BIT = 0

IF ISNULL(@prmIsShared, 0) = 0 BEGIN
    DELETE FROM dbo.SolutionApiClient WHERE SolutionID = @prmSolutionID AND ApiClientID = @prmApiClientID
    SELECT @@ROWCOUNT AS RowsAffected, @flipped AS FilesMovedOffRepo
    RETURN
END

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END
IF NOT EXISTS (SELECT 1 FROM dbo.ApiClient WHERE ApiClientID = @prmApiClientID) BEGIN
    RAISERROR('That API client no longer exists.', 16, 1) RETURN
END

BEGIN TRY
    BEGIN TRANSACTION
    IF EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID AND SaveFilesToRepo = 1) BEGIN
        UPDATE dbo.Solution SET SaveFilesToRepo = 0, ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmModifiedBy)
        WHERE  SolutionID = @prmSolutionID
        SET @flipped = 1
    END
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionApiClient WHERE SolutionID = @prmSolutionID AND ApiClientID = @prmApiClientID)
        INSERT INTO dbo.SolutionApiClient (SolutionID, ApiClientID) VALUES (@prmSolutionID, @prmApiClientID)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT 1 AS RowsAffected, @flipped AS FilesMovedOffRepo

GO
