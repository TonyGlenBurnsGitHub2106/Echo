SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigEnvironment_SeedDefaults') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigEnvironment_SeedDefaults
GO
CREATE PROCEDURE dbo.stp_SolutionConfigEnvironment_SeedDefaults
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigEnvironment_SeedDefaults 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: give a solution the three standard environments -- Local, QA,
                         Production, in that order -- but only when it has none yet, so it is safe to call every time. Returns
                         RowsAffected (environments added).

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END
IF EXISTS (SELECT 1 FROM dbo.SolutionConfigEnvironment WHERE SolutionID = @prmSolutionID) BEGIN
    SELECT 0 AS RowsAffected RETURN
END

DECLARE @local INT, @qa INT, @prod INT
EXEC dbo.stp_SolutionEnvironment_FindOrCreate N'Local', @local OUTPUT
EXEC dbo.stp_SolutionEnvironment_FindOrCreate N'QA', @qa OUTPUT
EXEC dbo.stp_SolutionEnvironment_FindOrCreate N'Production', @prod OUTPUT

BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.SolutionConfigEnvironment (SolutionID, SolutionEnvironmentID, SortOrder) VALUES (@prmSolutionID, @local, 1)
    INSERT INTO dbo.SolutionConfigEnvironment (SolutionID, SolutionEnvironmentID, SortOrder) VALUES (@prmSolutionID, @qa, 2)
    INSERT INTO dbo.SolutionConfigEnvironment (SolutionID, SolutionEnvironmentID, SortOrder) VALUES (@prmSolutionID, @prod, 3)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT 3 AS RowsAffected

GO
