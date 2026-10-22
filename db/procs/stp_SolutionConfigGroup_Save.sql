SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGroup_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGroup_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGroup_Save
    @prmSolutionConfigGroupID INT,
    @prmSolutionID            INT,
    @prmGroupTitle            NVARCHAR(200),
    @prmSortOrder             INT = NULL      -- NULL = keep (edit) / last (new)
AS

/*

TEST
----

    EXEC stp_SolutionConfigGroup_Save 0, 6, N'Message broker', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: add (id = 0) or rename / reorder one property group. A group never
                         moves to another solution. Returns SolutionConfigGroupID.

*/

SET NOCOUNT ON

SET @prmGroupTitle = LTRIM(RTRIM(ISNULL(@prmGroupTitle, N'')))
IF @prmGroupTitle = N'' BEGIN RAISERROR('A group needs a name.', 16, 1) RETURN END

IF @prmSolutionConfigGroupID > 0 BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigGroup WHERE SolutionConfigGroupID = @prmSolutionConfigGroupID) BEGIN
        RAISERROR('That group no longer exists.', 16, 1) RETURN
    END
    UPDATE dbo.SolutionConfigGroup
    SET    GroupTitle = @prmGroupTitle, SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE()
    WHERE  SolutionConfigGroupID = @prmSolutionConfigGroupID
END ELSE BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
        RAISERROR('That solution no longer exists.', 16, 1) RETURN
    END
    INSERT INTO dbo.SolutionConfigGroup (SolutionID, GroupTitle, SortOrder)
    VALUES (@prmSolutionID, @prmGroupTitle,
            ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGroup WHERE SolutionID = @prmSolutionID), 0) + 1))
    SET @prmSolutionConfigGroupID = SCOPE_IDENTITY()
END

SELECT @prmSolutionConfigGroupID AS SolutionConfigGroupID

GO
