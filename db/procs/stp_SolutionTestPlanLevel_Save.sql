SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlanLevel_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlanLevel_Save
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlanLevel_Save
    @prmSolutionTestPlanLevelID INT,
    @prmSolutionTestPlanID      INT,
    @prmLevelTitle              NVARCHAR(100),
    @prmSortOrder               INT = NULL      -- NULL = keep (edit) / last (new)
AS

/*

TEST
----

    EXEC stp_SolutionTestPlanLevel_Save 0, 1, N'End to end', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: add (id = 0) or rename one column of a testing checklist. The title is a
                         lookup (find-or-create), so the same name is one level everywhere; a checklist cannot have the same
                         column twice. Renaming keeps every tick in the column. Returns SolutionTestPlanLevelID.

*/

SET NOCOUNT ON

SET @prmLevelTitle = LTRIM(RTRIM(ISNULL(@prmLevelTitle, N'')))
IF @prmLevelTitle = N'' BEGIN RAISERROR('A column needs a name.', 16, 1) RETURN END

IF @prmSolutionTestPlanLevelID > 0
    SET @prmSolutionTestPlanID = (SELECT SolutionTestPlanID FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestPlan WHERE SolutionTestPlanID = @prmSolutionTestPlanID) BEGIN
    RAISERROR('That testing checklist no longer exists.', 16, 1) RETURN
END

DECLARE @levelId INT
EXEC dbo.stp_SolutionTestLevel_FindOrCreate @prmLevelTitle, @levelId OUTPUT

IF EXISTS (SELECT 1 FROM dbo.SolutionTestPlanLevel
           WHERE SolutionTestPlanID = @prmSolutionTestPlanID AND SolutionTestLevelID = @levelId
           AND   SolutionTestPlanLevelID <> ISNULL(@prmSolutionTestPlanLevelID, 0)) BEGIN
    DECLARE @dup NVARCHAR(300) = N'This checklist already has a column named "' + @prmLevelTitle + N'".'
    RAISERROR(@dup, 16, 1) RETURN
END

IF @prmSolutionTestPlanLevelID > 0
    UPDATE dbo.SolutionTestPlanLevel
    SET    SolutionTestLevelID = @levelId, SortOrder = ISNULL(@prmSortOrder, SortOrder)
    WHERE  SolutionTestPlanLevelID = @prmSolutionTestPlanLevelID
ELSE BEGIN
    INSERT INTO dbo.SolutionTestPlanLevel (SolutionTestPlanID, SolutionTestLevelID, SortOrder)
    VALUES (@prmSolutionTestPlanID, @levelId,
            ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTestPlanLevel WHERE SolutionTestPlanID = @prmSolutionTestPlanID), 0) + 1))
    SET @prmSolutionTestPlanLevelID = SCOPE_IDENTITY()
END

SELECT @prmSolutionTestPlanLevelID AS SolutionTestPlanLevelID

GO
