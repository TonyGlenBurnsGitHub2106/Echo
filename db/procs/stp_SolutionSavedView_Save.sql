SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionSavedView_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionSavedView_Save
GO
CREATE PROCEDURE dbo.stp_SolutionSavedView_Save
    @prmSolutionID INT,
    @prmMemberCode VARCHAR(20),
    @prmScopeCode  VARCHAR(20),
    @prmViewName   NVARCHAR(100),
    @prmFilterJson NVARCHAR(2000)
AS

/*

TEST
----

    EXEC stp_SolutionSavedView_Save 6, 'tony', 'items', N'My open submit work', N'{"project":"41","assignee":"tony","hideClosed":true}'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: save a view. A view of the same name (any case) for the same member and tab
                         is replaced, so Save view twice updates it. At most 30 per member per tab. Returns its id.

*/

SET NOCOUNT ON

DECLARE @scope VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmScopeCode, ''))))
DECLARE @name NVARCHAR(100) = LTRIM(RTRIM(ISNULL(@prmViewName, N'')))
DECLARE @json NVARCHAR(2000) = LTRIM(RTRIM(ISNULL(@prmFilterJson, N'')))
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN RAISERROR('That solution no longer exists. Nothing was saved.', 16, 1) RETURN END
IF @scope NOT IN ('items') BEGIN RAISERROR('Views can be saved for the Items tab. Nothing was saved.', 16, 1) RETURN END
IF @name = N'' BEGIN RAISERROR('A view needs a name. Nothing was saved.', 16, 1) RETURN END
IF LEFT(@json, 1) <> N'{' OR RIGHT(@json, 1) <> N'}' BEGIN RAISERROR('A view keeps its filters as an object. Nothing was saved.', 16, 1) RETURN END
DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
DECLARE @norm NVARCHAR(100) = LOWER(@name)
DECLARE @id INT = (SELECT SolutionSavedViewID FROM dbo.SolutionSavedView
                   WHERE SolutionID = @prmSolutionID AND SolutionMemberID = @memberId AND ScopeCode = @scope AND NormalizedName = @norm)
IF @id IS NULL AND (SELECT COUNT(*) FROM dbo.SolutionSavedView WHERE SolutionID = @prmSolutionID AND SolutionMemberID = @memberId AND ScopeCode = @scope) >= 30
BEGIN RAISERROR('You have 30 saved views here already. Delete one first. Nothing was saved.', 16, 1) RETURN END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @id IS NOT NULL
        UPDATE dbo.SolutionSavedView SET ViewName = @name, FilterJson = @json, ModifiedDate = GETDATE() WHERE SolutionSavedViewID = @id
    ELSE
    BEGIN
        INSERT INTO dbo.SolutionSavedView (SolutionID, SolutionMemberID, ScopeCode, ViewName, NormalizedName, FilterJson, SortOrder)
        VALUES (@prmSolutionID, @memberId, @scope, @name, @norm, @json,
                ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionSavedView WITH (UPDLOCK, HOLDLOCK)
                        WHERE SolutionID = @prmSolutionID AND SolutionMemberID = @memberId AND ScopeCode = @scope), 0) + 1)
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionSavedViewID

GO
