SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestGroup_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestGroup_Save
GO
CREATE PROCEDURE dbo.stp_SolutionTestGroup_Save
    @prmSolutionTestGroupID INT,
    @prmSolutionTestPlanID  INT,
    @prmGroupTitle          NVARCHAR(200),
    @prmSortOrder           INT = NULL      -- NULL = keep (edit) / last (new)
AS

/*

TEST
----

    EXEC stp_SolutionTestGroup_Save 0, 1, N'Starting a batch', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: add (id = 0) or rename / reorder one group of a testing checklist. A group
                         never moves to another checklist. Returns SolutionTestGroupID.

*/

SET NOCOUNT ON

SET @prmGroupTitle = LTRIM(RTRIM(ISNULL(@prmGroupTitle, N'')))
IF @prmGroupTitle = N'' BEGIN RAISERROR('A group needs a name.', 16, 1) RETURN END

IF @prmSolutionTestGroupID > 0 BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestGroup WHERE SolutionTestGroupID = @prmSolutionTestGroupID) BEGIN
        RAISERROR('That group no longer exists.', 16, 1) RETURN
    END
    UPDATE dbo.SolutionTestGroup
    SET    GroupTitle = @prmGroupTitle, SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE()
    WHERE  SolutionTestGroupID = @prmSolutionTestGroupID
END ELSE BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestPlan WHERE SolutionTestPlanID = @prmSolutionTestPlanID) BEGIN
        RAISERROR('That testing checklist no longer exists.', 16, 1) RETURN
    END
    INSERT INTO dbo.SolutionTestGroup (SolutionTestPlanID, GroupTitle, SortOrder)
    VALUES (@prmSolutionTestPlanID, @prmGroupTitle,
            ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTestGroup WHERE SolutionTestPlanID = @prmSolutionTestPlanID), 0) + 1))
    SET @prmSolutionTestGroupID = SCOPE_IDENTITY()
END

SELECT @prmSolutionTestGroupID AS SolutionTestGroupID

GO
