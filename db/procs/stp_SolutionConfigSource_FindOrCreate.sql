SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigSource_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigSource_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_SolutionConfigSource_FindOrCreate
    @prmSourceTitle NVARCHAR(200),
    @prmOutID       INT = NULL OUTPUT
AS

/*

TEST
----

    DECLARE @id INT; EXEC stp_SolutionConfigSource_FindOrCreate N'application.properties', @id OUTPUT; SELECT @id

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the "where it is set" lookup id, created the first time the name is
                         used. OUTPUT parameter only, no result set.

*/

SET NOCOUNT ON

SET @prmSourceTitle = LTRIM(RTRIM(ISNULL(@prmSourceTitle, N'')))
SET @prmOutID = NULL
IF @prmSourceTitle = N'' RETURN

SET @prmOutID = (SELECT SolutionConfigSourceID FROM dbo.SolutionConfigSource WHERE SourceTitle = @prmSourceTitle)
IF @prmOutID IS NULL BEGIN
    INSERT INTO dbo.SolutionConfigSource (SourceTitle) VALUES (@prmSourceTitle)
    SET @prmOutID = SCOPE_IDENTITY()
END

GO
