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
    @prmMemberCode      VARCHAR(20)    = NULL,
    -- Story 5695 (Echo): the kind of topic, and what a part or an assumption carries. NULL = keep.
    @prmKindCode        VARCHAR(20)    = NULL, -- subject | tag | part | assumption (an add: subject)
    @prmTypeName        NVARCHAR(400)  = NULL, -- a part's kind of part, found or made by name; '' = none
    @prmStatusCode      VARCHAR(20)    = NULL, -- a part's status; '' = none
    @prmFigureValue     DECIMAL(18, 2) = NULL, -- a part's number
    @prmClearFigure     BIT            = 0,    -- 1 = take the number off
    @prmHolds           BIT            = NULL  -- an assumption: 1 holds, 0 proved false (an add: 1)
AS
/*
TEST
----
    EXEC stp_SolutionTopic_Save 0, 6, N'Charset', N'Read as UTF-8; the rest as decided in the deep analysis.', 'tony'
    EXEC stp_SolutionTopic_Save 0, 200, N'Cherry Creek', NULL, 'tony', 'part', N'Chapter', 'inprogress', 4200, 0, NULL
VERSION HISTORY
Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution topics: add or change one topic. The name is checked before the first write
                         (present, at most 200 characters, not already a topic of this solution -- case and
                         spaces aside); the write runs with XACT_ABORT in one transaction, and
                         UX_SolutionTopic_Name backs the duplicate refusal up. An edit is a merge: a NULL
                         parameter keeps what is there. Returns SolutionTopicID.
11/04/2026  Felix        Story 5695 (Echo): a topic has a KIND -- subject, tag, part (of what the solution is made
                         of: a chapter, a character, an account) or assumption. A part carries its kind of part
                         (found or made by name, in the same transaction), a status and a number, and a new one
                         goes last in its kind; an assumption carries whether it holds. Changing the kind takes
                         off what the old kind carried. Every input is checked before the first write.
*/
SET NOCOUNT ON
DECLARE @id INT = ISNULL(@prmSolutionTopicID, 0)
DECLARE @solutionId INT = NULL, @oldKind VARCHAR(20) = NULL
IF @id > 0
BEGIN
    SELECT @solutionId = SolutionID, @oldKind = TopicKindCode FROM dbo.SolutionTopic WHERE SolutionTopicID = @id
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

-- Story 5695: the kind and what it carries.
DECLARE @kind VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmKindCode, '')))), '')
IF @kind IS NOT NULL AND @kind NOT IN ('subject', 'tag', 'part', 'assumption')
BEGIN RAISERROR('A topic is a subject, a tag, a part or an assumption. Nothing was saved.', 16, 1) RETURN END
DECLARE @newKind VARCHAR(20) = COALESCE(@kind, @oldKind, 'subject')
DECLARE @typeName NVARCHAR(400) = CASE WHEN @prmTypeName IS NULL THEN NULL
    ELSE LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(@prmTypeName, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' '))) END
IF LEN(ISNULL(@typeName, N'')) > 100 BEGIN RAISERROR('A kind of part can be at most 100 characters. Nothing was saved.', 16, 1) RETURN END
DECLARE @typeNormalized NVARCHAR(100) = NULL, @typeId INT = NULL
IF ISNULL(@typeName, N'') <> N''
BEGIN
    SET @typeNormalized = LOWER(@typeName)
    WHILE CHARINDEX(N'  ', @typeNormalized) > 0 SET @typeNormalized = REPLACE(@typeNormalized, N'  ', N' ')
    SET @typeId = (SELECT SolutionTopicTypeID FROM dbo.SolutionTopicType WHERE SolutionID = @solutionId AND NormalizedName = @typeNormalized)
END
DECLARE @status VARCHAR(20) = CASE WHEN @prmStatusCode IS NULL THEN NULL ELSE LOWER(LTRIM(RTRIM(@prmStatusCode))) END
DECLARE @statusId INT = NULL
IF ISNULL(@status, '') <> ''
BEGIN
    SET @statusId = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = @status)
    IF @statusId IS NULL BEGIN RAISERROR('That is not a status. Nothing was saved.', 16, 1) RETURN END
END
IF @newKind <> 'part' AND (ISNULL(@typeName, N'') <> N'' OR ISNULL(@status, '') <> '' OR @prmFigureValue IS NOT NULL)
BEGIN RAISERROR('Only a part carries a kind of part, a status and a number. Nothing was saved.', 16, 1) RETURN END
IF @newKind <> 'assumption' AND @prmHolds IS NOT NULL
BEGIN RAISERROR('Only an assumption holds or is proved false. Nothing was saved.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
-- XACT_ABORT only from here: a refusal above must never doom a caller's transaction.
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    -- A kind of part named for the first time is made here, last in this solution's list.
    IF ISNULL(@typeName, N'') <> N'' AND @typeId IS NULL
    BEGIN
        INSERT INTO dbo.SolutionTopicType (SolutionID, TypeName, NormalizedName, SortOrder)
        VALUES (@solutionId, @typeName, @typeNormalized,
                ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTopicType WHERE SolutionID = @solutionId), 0) + 1)
        SET @typeId = CAST(SCOPE_IDENTITY() AS INT)
    END
    IF @id = 0
    BEGIN
        INSERT INTO dbo.SolutionTopic (SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy,
                                       TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder)
        VALUES (@solutionId, @name, @normalized, NULLIF(LTRIM(RTRIM(ISNULL(@prmSummary, N''))), N''), @memberId, LOWER(@prmMemberCode),
                @newKind, @typeId, @statusId, @prmFigureValue,
                CASE WHEN @newKind = 'assumption' THEN ISNULL(@prmHolds, 1) ELSE NULL END,
                CASE WHEN @newKind = 'part'
                     THEN ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTopic WHERE SolutionID = @solutionId AND TopicKindCode = 'part'
                                    AND ISNULL(SolutionTopicTypeID, 0) = ISNULL(@typeId, 0)), 0) + 1
                     ELSE 0 END)
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    ELSE
    BEGIN
        UPDATE dbo.SolutionTopic
        SET    TopicName           = ISNULL(@name, TopicName),
               NormalizedName      = ISNULL(@normalized, NormalizedName),
               TopicSummary        = CASE WHEN @prmSummary IS NULL THEN TopicSummary ELSE NULLIF(LTRIM(RTRIM(@prmSummary)), N'') END,
               TopicKindCode       = @newKind,
               -- a kind that is not a part carries none of a part's things; one that is not an assumption, no "holds"
               SolutionTopicTypeID = CASE WHEN @newKind <> 'part' THEN NULL WHEN @prmTypeName IS NULL THEN SolutionTopicTypeID ELSE @typeId END,
               SolutionStatusID    = CASE WHEN @newKind <> 'part' THEN NULL WHEN @prmStatusCode IS NULL THEN SolutionStatusID ELSE @statusId END,
               FigureValue         = CASE WHEN @newKind <> 'part' OR @prmClearFigure = 1 THEN NULL ELSE ISNULL(@prmFigureValue, FigureValue) END,
               AssumptionHolds     = CASE WHEN @newKind <> 'assumption' THEN NULL ELSE COALESCE(@prmHolds, AssumptionHolds, 1) END,
               ModifiedBy          = ISNULL(LOWER(@prmMemberCode), ModifiedBy),
               ModifiedDate        = GETDATE()
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
