SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridGroup_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridGroup_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridGroup_Save
    @prmSolutionConfigGridGroupID INT,
    @prmSolutionID                INT,
    @prmGroupTitle                NVARCHAR(200),
    @prmSortOrder                 INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridGroup_Save 0, 1, N'Mail', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        S

*/

SET NOCOUNT ON

SET @prmGroupTitle = LTRIM(RTRIM(ISNULL(@prmGroupTitle, N'')))
IF @prmGroupTitle = N'' BEGIN RAISERROR('A group needs a name. Nothing was saved.', 16, 1) RETURN END
IF @prmSolutionConfigGridGroupID > 0 BEGIN
    SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionConfigGridGroup WHERE SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID)
    IF @prmSolutionID IS NULL BEGIN RAISERROR('That group no longer exists. Nothing was saved.', 16, 1) RETURN END
END
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists. Nothing was saved.', 16, 1) RETURN
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSolutionConfigGridGroupID > 0
        UPDATE dbo.SolutionConfigGridGroup
        SET    GroupTitle = @prmGroupTitle, SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE()
        WHERE  SolutionConfigGridGroupID = @prmSolutionConfigGridGroupID
    ELSE BEGIN
        INSERT INTO dbo.SolutionConfigGridGroup (SolutionID, GroupTitle, SortOrder)
        VALUES (@prmSolutionID, @prmGroupTitle,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridGroup WHERE SolutionID = @prmSolutionID), 0) + 1))
        SET @prmSolutionConfigGridGroupID = SCOPE_IDENTITY()
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT @prmSolutionConfigGridGroupID AS SolutionConfigGridGroupID

GO
