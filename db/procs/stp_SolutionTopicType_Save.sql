SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopicType_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopicType_Save
GO
CREATE PROCEDURE dbo.stp_SolutionTopicType_Save
    @prmSolutionTopicTypeID INT           = 0,     -- 0 = find by name, or add
    @prmSolutionID          INT           = 0,     -- required to add
    @prmTypeName            NVARCHAR(400) = NULL,  -- NULL = keep (edit only)
    @prmFigureLabel         NVARCHAR(400) = NULL   -- NULL = keep, '' = clear
AS

/*

TEST
----

    EXEC stp_SolutionTopicType_Save 0, 200, N'Chapter', N'words'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): find-or-create a kind of part by name (id 0), or rename it / change what
                         its number counts. A name is used once per solution (case and repeated spaces ignored);
                         a rename onto a name already used is refused. A new one goes last. Returns
                         SolutionTopicTypeID.

*/

SET NOCOUNT ON

DECLARE @id INT = ISNULL(@prmSolutionTopicTypeID, 0), @solutionId INT = NULL
IF @id > 0
BEGIN
    SELECT @solutionId = SolutionID FROM dbo.SolutionTopicType WHERE SolutionTopicTypeID = @id
    IF @solutionId IS NULL BEGIN RAISERROR('That kind of part no longer exists. Nothing was saved.', 16, 1) RETURN END
END
ELSE
BEGIN
    SET @solutionId = @prmSolutionID
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @solutionId)
    BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
END

DECLARE @name NVARCHAR(400) = CASE WHEN @prmTypeName IS NULL THEN NULL
    ELSE LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(@prmTypeName, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' '))) END
IF @id = 0 AND ISNULL(@name, N'') = N'' BEGIN RAISERROR('A kind of part needs a name. Nothing was added.', 16, 1) RETURN END
IF @id > 0 AND @name = N'' BEGIN RAISERROR('A kind of part cannot be blank. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@name, N'')) > 100 BEGIN RAISERROR('A kind of part can be at most 100 characters. Nothing was saved.', 16, 1) RETURN END
DECLARE @label NVARCHAR(400) = CASE WHEN @prmFigureLabel IS NULL THEN NULL ELSE LTRIM(RTRIM(@prmFigureLabel)) END
IF LEN(ISNULL(@label, N'')) > 40 BEGIN RAISERROR('What a number counts can be at most 40 characters. Nothing was saved.', 16, 1) RETURN END

DECLARE @normalized NVARCHAR(100) = NULL
IF @name IS NOT NULL
BEGIN
    SET @normalized = LOWER(@name)
    WHILE CHARINDEX(N'  ', @normalized) > 0 SET @normalized = REPLACE(@normalized, N'  ', N' ')
    DECLARE @found INT = (SELECT SolutionTopicTypeID FROM dbo.SolutionTopicType WHERE SolutionID = @solutionId AND NormalizedName = @normalized)
    IF @id = 0 AND @found IS NOT NULL
    BEGIN
        -- find-or-create: it is there already. A label sent with it is kept on it.
        IF @label IS NOT NULL UPDATE dbo.SolutionTopicType SET FigureLabel = NULLIF(@label, N''), ModifiedDate = GETDATE() WHERE SolutionTopicTypeID = @found
        SELECT @found AS SolutionTopicTypeID
        RETURN
    END
    IF @id > 0 AND @found IS NOT NULL AND @found <> @id
    BEGIN RAISERROR('This solution already has that kind of part. Nothing was saved.', 16, 1) RETURN END
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @id = 0
    BEGIN
        INSERT INTO dbo.SolutionTopicType (SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder)
        VALUES (@solutionId, @name, @normalized, NULLIF(@label, N''),
                ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTopicType WHERE SolutionID = @solutionId), 0) + 1)
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    ELSE
        UPDATE dbo.SolutionTopicType
        SET    TypeName       = ISNULL(@name, TypeName),
               NormalizedName = ISNULL(@normalized, NormalizedName),
               FigureLabel    = CASE WHEN @label IS NULL THEN FigureLabel ELSE NULLIF(@label, N'') END,
               ModifiedDate   = GETDATE()
        WHERE  SolutionTopicTypeID = @id
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = CASE WHEN ERROR_NUMBER() IN (2601, 2627)
        THEN N'This solution already has that kind of part. Nothing was saved.' ELSE ERROR_MESSAGE() END
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionTopicTypeID

GO
