SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionApiClient_Set') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionApiClient_Set
GO
CREATE PROCEDURE dbo.stp_SolutionApiClient_Set
    @prmApiClientID INT,
    @prmSolutionID  INT,
    @prmIsLinked    BIT
AS

/*

TEST
----

    EXEC stp_SolutionApiClient_Set 0, 0, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: let one client see one solution, or stop it. Setting what is already so is not an error. Returns RowsAffected.

*/

SET NOCOUNT ON

IF ISNULL(@prmIsLinked, 0) = 1 BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.ApiClient WHERE ApiClientID = @prmApiClientID)
       OR NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
        SELECT 0 AS RowsAffected RETURN
    END
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionApiClient WHERE ApiClientID = @prmApiClientID AND SolutionID = @prmSolutionID)
        INSERT INTO dbo.SolutionApiClient (SolutionID, ApiClientID) VALUES (@prmSolutionID, @prmApiClientID)
    SELECT 1 AS RowsAffected
END ELSE BEGIN
    DELETE FROM dbo.SolutionApiClient WHERE ApiClientID = @prmApiClientID AND SolutionID = @prmSolutionID
    SELECT @@ROWCOUNT AS RowsAffected
END

GO
