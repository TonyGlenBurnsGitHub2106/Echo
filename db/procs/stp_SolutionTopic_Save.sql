SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_Save
GO

CREATE PROCEDURE dbo.stp_SolutionTopic_Save
    @prmSolutionTopicID INT           = 0,     -- 0 = add
    @prmSolutionID      INT           = 0,     -- required to add; an edit keeps the topic's own solution
    @prmTopicName       NVARCHAR(4000) = NULL, -- NULL = keep (edit only)
    @prmSummary         NVARCHAR(MAX)  = NULL, -- NULL = keep, '' = clear
    @prmMemberCode      VARCHAR(20)    = NULL
AS

/*

TEST
----

    EXEC stp_SolutionTopic_Save 0, 6, N'Charset', N'Read as UTF-8; the rest as decided in the deep analysis.', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: add or change one topic. The name is checked before the first write
                         (present, at most 200 characters, not already a topic of this solution -- case and
                         spaces aside); the write runs with XACT_ABORT in one transaction, and
                         UX_SolutionTopic_Name backs the duplicate refusal up. An edit is a merge: a NULL
                         parameter keeps what is there. Returns SolutionTopicID.

*/

SET NOCOUNT ON

DECLARE @id INT = ISNULL(@prmSolutionTopicID, 0)
DECLARE @solutionId INT = NULL

IF @id > 0
BEGIN
    SELECT @solutionId = SolutionID FROM dbo.SolutionTopic WHERE SolutionTopicID = @id
    IF @solutionId IS NULL BEGIN RAISERROR('That topic no longer exists. Nothing was saved.', 16, 1) RETURN END
END
ELSE
BEGIN
    SET @solutionId = @prmSolutionID
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @solutionId)
    BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
END

DECLARE @name NVARCHAR(4000) = CASE WHEN @prmTopicName IS NULL THEN NULL
    ELSE LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(@prmTopicName, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' '))) END

IF @id = 0 AND ISNULL(@name, N'') = N''
BEGIN RAISERROR('A topic needs a name. Nothing was added.', 16, 1) RETURN END
IF @id > 0 AND @name IS NOT NULL AND @name = N''
BEGIN RAISERROR('A topic name cannot be blank. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@name, N'')) > 200
BEGIN RAISERROR('A topic name can be at most 200 characters. Nothing was saved.', 16, 1) RETURN END

DECLARE @normalized NVARCHAR(200) = NULL
IF @name IS NOT NULL
BEGIN
    SET @normalized = LOWER(@name)
    WHILE CHARINDEX(N'  ', @normalized) > 0 SET @normalized = REPLACE(@normalized, N'  ', N' ')
    DECLARE @dup NVARCHAR(200) = (SELECT TOP 1 TopicName FROM dbo.SolutionTopic
                                  WHERE SolutionID = @solutionId AND NormalizedName = @normalized AND SolutionTopicID <> @id)
    IF @dup IS NOT NULL
    BEGIN
        -- %% because RAISERROR reads its message as a format string.
        DECLARE @dupMsg NVARCHAR(1000) = N'This solution already has the topic "' + REPLACE(@dup, N'%', N'%%') + N'". Nothing was saved.'
        RAISERROR(@dupMsg, 16, 1) RETURN
    END
END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

-- XACT_ABORT only from here: a refusal above must never doom a caller's transaction.
SET XACT_ABORT ON

BEGIN TRY
    BEGIN TRANSACTION
    IF @id = 0
    BEGIN
        INSERT INTO dbo.SolutionTopic (SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy)
        VALUES (@solutionId, @name, @normalized, NULLIF(LTRIM(RTRIM(ISNULL(@prmSummary, N''))), N''), @memberId, LOWER(@prmMemberCode))
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    ELSE
    BEGIN
        UPDATE dbo.SolutionTopic
        SET    TopicName      = ISNULL(@name, TopicName),
               NormalizedName = ISNULL(@normalized, NormalizedName),
               TopicSummary   = CASE WHEN @prmSummary IS NULL THEN TopicSummary ELSE NULLIF(LTRIM(RTRIM(@prmSummary)), N'') END,
               ModifiedBy     = ISNULL(LOWER(@prmMemberCode), ModifiedBy),
               ModifiedDate   = GETDATE()
        WHERE  SolutionTopicID = @id
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = CASE WHEN ERROR_NUMBER() IN (2601, 2627)
        THEN N'This solution already has that topic. Nothing was saved.' ELSE ERROR_MESSAGE() END
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionTopicID

GO
