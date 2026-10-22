SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigEnvironment_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigEnvironment_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigEnvironment_Save
    @prmSolutionConfigEnvironmentID INT,
    @prmSolutionID                  INT,
    @prmEnvironmentName             NVARCHAR(100),
    @prmSortOrder                   INT = NULL      -- NULL = keep (edit) / last (new)
AS

/*

TEST
----

    EXEC stp_SolutionConfigEnvironment_Save 0, 6, N'Staging', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: add (id = 0) or rename / reorder one environment of a solution. The
                         name is a lookup (find-or-create); a solution cannot have the same environment twice. Renaming keeps
                         every value in the column. Returns SolutionConfigEnvironmentID.

*/

SET NOCOUNT ON

SET @prmEnvironmentName = LTRIM(RTRIM(ISNULL(@prmEnvironmentName, N'')))
IF @prmEnvironmentName = N'' BEGIN RAISERROR('An environment needs a name.', 16, 1) RETURN END

IF @prmSolutionConfigEnvironmentID > 0
    SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionConfigEnvironment WHERE SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID)
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END

DECLARE @envId INT
EXEC dbo.stp_SolutionEnvironment_FindOrCreate @prmEnvironmentName, @envId OUTPUT

IF EXISTS (SELECT 1 FROM dbo.SolutionConfigEnvironment
           WHERE SolutionID = @prmSolutionID AND SolutionEnvironmentID = @envId
           AND   SolutionConfigEnvironmentID <> ISNULL(@prmSolutionConfigEnvironmentID, 0)) BEGIN
    DECLARE @dup NVARCHAR(300) = N'This solution already has an environment named "' + @prmEnvironmentName + N'".'
    RAISERROR(@dup, 16, 1) RETURN
END

IF @prmSolutionConfigEnvironmentID > 0
    UPDATE dbo.SolutionConfigEnvironment
    SET    SolutionEnvironmentID = @envId, SortOrder = ISNULL(@prmSortOrder, SortOrder)
    WHERE  SolutionConfigEnvironmentID = @prmSolutionConfigEnvironmentID
ELSE BEGIN
    INSERT INTO dbo.SolutionConfigEnvironment (SolutionID, SolutionEnvironmentID, SortOrder)
    VALUES (@prmSolutionID, @envId,
            ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigEnvironment WHERE SolutionID = @prmSolutionID), 0) + 1))
    SET @prmSolutionConfigEnvironmentID = SCOPE_IDENTITY()
END

SELECT @prmSolutionConfigEnvironmentID AS SolutionConfigEnvironmentID

GO
