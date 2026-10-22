SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionEnvironment_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionEnvironment_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_SolutionEnvironment_FindOrCreate
    @prmEnvironmentName NVARCHAR(100),
    @prmOutID           INT = NULL OUTPUT
AS

/*

TEST
----

    DECLARE @id INT; EXEC stp_SolutionEnvironment_FindOrCreate N'Staging', @id OUTPUT; SELECT @id

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the environment-name lookup id, created the first time the name is
                         used. OUTPUT parameter only, no result set.

*/

SET NOCOUNT ON

SET @prmEnvironmentName = LTRIM(RTRIM(ISNULL(@prmEnvironmentName, N'')))
SET @prmOutID = NULL
IF @prmEnvironmentName = N'' RETURN

SET @prmOutID = (SELECT SolutionEnvironmentID FROM dbo.SolutionEnvironment WHERE EnvironmentName = @prmEnvironmentName)
IF @prmOutID IS NULL BEGIN
    INSERT INTO dbo.SolutionEnvironment (EnvironmentName) VALUES (@prmEnvironmentName)
    SET @prmOutID = SCOPE_IDENTITY()
END

GO
