SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestLevel_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestLevel_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_SolutionTestLevel_FindOrCreate
    @prmLevelTitle NVARCHAR(100),
    @prmOutID      INT = NULL OUTPUT
AS

/*

TEST
----

    DECLARE @id INT; EXEC stp_SolutionTestLevel_FindOrCreate N'End to end', @id OUTPUT; SELECT @id

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: the level lookup id for a title, created the first time the title is used.
                         Reports through the OUTPUT parameter only -- no result set -- so a save proc can call it without INSERT
                         ... EXEC.

*/

SET NOCOUNT ON

SET @prmLevelTitle = LTRIM(RTRIM(ISNULL(@prmLevelTitle, N'')))
SET @prmOutID = NULL
IF @prmLevelTitle = N'' RETURN

SET @prmOutID = (SELECT SolutionTestLevelID FROM dbo.SolutionTestLevel WHERE LevelTitle = @prmLevelTitle)
IF @prmOutID IS NULL BEGIN
    INSERT INTO dbo.SolutionTestLevel (LevelTitle) VALUES (@prmLevelTitle)
    SET @prmOutID = SCOPE_IDENTITY()
END

GO
