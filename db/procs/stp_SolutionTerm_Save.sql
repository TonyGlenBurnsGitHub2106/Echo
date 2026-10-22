SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTerm_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTerm_Save
GO
CREATE PROCEDURE dbo.stp_SolutionTerm_Save
    @prmSolutionTermID INT            = 0,     -- 0 = add
    @prmSolutionID     INT            = 0,     -- required to add; an edit keeps the term's own solution
    @prmTerm           NVARCHAR(4000) = NULL,  -- NULL = keep (edit only)
    @prmDefinition     NVARCHAR(MAX)  = NULL,  -- NULL = keep (edit only)
    @prmTypeName       NVARCHAR(4000) = NULL,  -- NULL = keep, '' = no type, else find-or-create in this solution
    @prmSource         NVARCHAR(4000) = NULL,  -- NULL = keep, '' = clear
    @prmNotes          NVARCHAR(MAX)  = NULL,  -- NULL = keep, '' = clear
    @prmCategories     NVARCHAR(MAX)  = NULL,  -- NULL = keep; else every category, one per line ('' = none)
    @prmMemberCode     VARCHAR(20)    = NULL
AS

/*

TEST
----

    EXEC stp_SolutionTerm_Save 0, 6, N'SLA', N'Service level agreement', N'Acronym', NULL, NULL, N'Contracts', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: add or change one term of a solution. Every input is checked before
                         the first write (term and definition present, lengths, the term not already in this
                         solution); the writes -- find-or-create the type and the categories in THIS solution, the
                         term, its category links -- run in one transaction with XACT_ABORT, so a refusal or a
                         failure leaves nothing behind. A repeat of a term the solution already has is refused
                         ("... Nothing was saved."), and UX_SolutionTerm_Term backs that up against two requests in
                         the same instant. An edit is a merge: a NULL parameter keeps what is there. Returns
                         SolutionTermID.

*/

SET NOCOUNT ON

DECLARE @id INT = ISNULL(@prmSolutionTermID, 0)
DECLARE @solutionId INT = NULL
DECLARE @nl NCHAR(1) = NCHAR(10)

-- ---- which solution ------------------------------------------------------------------------
IF @id > 0
BEGIN
    SELECT @solutionId = SolutionID FROM dbo.SolutionTerm WHERE SolutionTermID = @id
    IF @solutionId IS NULL BEGIN RAISERROR('That term no longer exists. Nothing was saved.', 16, 1) RETURN END
END
ELSE
BEGIN
    SET @solutionId = @prmSolutionID
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @solutionId)
    BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
END

-- ---- the values, checked before anything is written ----------------------------------------
DECLARE @term NVARCHAR(4000) = CASE WHEN @prmTerm IS NULL THEN NULL ELSE LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(@prmTerm, NCHAR(13), N' '), @nl, N' '), NCHAR(9), N' '))) END
DECLARE @definition NVARCHAR(MAX) = CASE WHEN @prmDefinition IS NULL THEN NULL ELSE LTRIM(RTRIM(@prmDefinition)) END
DECLARE @typeName NVARCHAR(4000) = CASE WHEN @prmTypeName IS NULL THEN NULL ELSE LTRIM(RTRIM(@prmTypeName)) END
DECLARE @source NVARCHAR(4000) = CASE WHEN @prmSource IS NULL THEN NULL ELSE LTRIM(RTRIM(@prmSource)) END

IF @id = 0 AND ISNULL(@term, N'') = N''
BEGIN RAISERROR('A term needs the word or phrase it defines. Nothing was added.', 16, 1) RETURN END
IF @id = 0 AND ISNULL(@definition, N'') = N''
BEGIN RAISERROR('A term needs a definition. Nothing was added.', 16, 1) RETURN END
IF @id > 0 AND @term IS NOT NULL AND @term = N''
BEGIN RAISERROR('A term cannot be blank. Nothing was saved.', 16, 1) RETURN END
IF @id > 0 AND @definition IS NOT NULL AND @definition = N''
BEGIN RAISERROR('A definition cannot be blank. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@term, N'')) > 400
BEGIN RAISERROR('A term can be at most 400 characters. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@typeName, N'')) > 200
BEGIN RAISERROR('A type name can be at most 200 characters. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@source, N'')) > 500
BEGIN RAISERROR('A source can be at most 500 characters. Nothing was saved.', 16, 1) RETURN END

-- The duplicate key: lower case, every run of spaces made one space.
DECLARE @normalized NVARCHAR(400) = NULL
IF @term IS NOT NULL
BEGIN
    SET @normalized = LOWER(@term)
    WHILE CHARINDEX(N'  ', @normalized) > 0 SET @normalized = REPLACE(@normalized, N'  ', N' ')
    DECLARE @dupTerm NVARCHAR(400) = (SELECT TOP 1 Term FROM dbo.SolutionTerm
                                      WHERE SolutionID = @solutionId AND NormalizedTerm = @normalized AND SolutionTermID <> @id)
    IF @dupTerm IS NOT NULL
    BEGIN
        -- %% because RAISERROR reads its message as a format string.
        DECLARE @dupMsg NVARCHAR(1000) = N'This solution already has the term "' + REPLACE(@dupTerm, N'%', N'%%') + N'". Nothing was saved.'
        RAISERROR(@dupMsg, 16, 1) RETURN
    END
END

-- The categories, one per line, trimmed, blanks dropped, each name once.
DECLARE @cats TABLE (CategoryName NVARCHAR(200) NOT NULL PRIMARY KEY)
IF @prmCategories IS NOT NULL
BEGIN
    DECLARE @rest NVARCHAR(MAX) = REPLACE(@prmCategories, NCHAR(13), N'') + @nl
    DECLARE @pos INT, @one NVARCHAR(4000)
    WHILE LEN(@rest) > 0
    BEGIN
        SET @pos = CHARINDEX(@nl, @rest)
        IF @pos = 0 SET @pos = LEN(@rest) + 1
        SET @one = LTRIM(RTRIM(LEFT(@rest, @pos - 1)))
        SET @rest = SUBSTRING(@rest, @pos + 1, LEN(@rest))
        IF @one = N'' CONTINUE
        IF LEN(@one) > 200 BEGIN RAISERROR('A category name can be at most 200 characters. Nothing was saved.', 16, 1) RETURN END
        IF NOT EXISTS (SELECT 1 FROM @cats WHERE CategoryName = @one) INSERT INTO @cats (CategoryName) VALUES (@one)
    END
END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

-- XACT_ABORT only from here: a refusal above must never doom a caller's transaction.
SET XACT_ABORT ON

-- ---- the writes ------------------------------------------------------------------------------
BEGIN TRY
    BEGIN TRANSACTION

    DECLARE @typeId INT = NULL
    IF @typeName IS NOT NULL AND @typeName <> N''
    BEGIN
        SELECT @typeId = SolutionTermTypeID FROM dbo.SolutionTermType WITH (UPDLOCK, HOLDLOCK)
        WHERE  SolutionID = @solutionId AND TypeName = @typeName
        IF @typeId IS NULL
        BEGIN
            INSERT INTO dbo.SolutionTermType (SolutionID, TypeName, SortOrder)
            VALUES (@solutionId, @typeName, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTermType WHERE SolutionID = @solutionId), 0) + 1)
            SET @typeId = CAST(SCOPE_IDENTITY() AS INT)
        END
    END

    IF @id = 0
    BEGIN
        INSERT INTO dbo.SolutionTerm (SolutionID, Term, NormalizedTerm, Definition, SolutionTermTypeID, TermSource, TermNotes,
                                      AuthorMemberID, ModifiedByMemberID)
        VALUES (@solutionId, @term, @normalized, @definition, @typeId, NULLIF(@source, N''),
                NULLIF(LTRIM(RTRIM(ISNULL(@prmNotes, N''))), N''), @memberId, @memberId)
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    ELSE
    BEGIN
        UPDATE dbo.SolutionTerm
        SET    Term               = ISNULL(@term, Term),
               NormalizedTerm     = ISNULL(@normalized, NormalizedTerm),
               Definition         = ISNULL(@definition, Definition),
               SolutionTermTypeID = CASE WHEN @typeName IS NULL THEN SolutionTermTypeID ELSE @typeId END,
               TermSource         = CASE WHEN @source IS NULL THEN TermSource ELSE NULLIF(@source, N'') END,
               TermNotes          = CASE WHEN @prmNotes IS NULL THEN TermNotes ELSE NULLIF(LTRIM(RTRIM(@prmNotes)), N'') END,
               ModifiedByMemberID = ISNULL(@memberId, ModifiedByMemberID),
               ModifiedDate       = GETDATE()
        WHERE  SolutionTermID = @id
    END

    IF @prmCategories IS NOT NULL
    BEGIN
        INSERT INTO dbo.SolutionTermCategory (SolutionID, CategoryName)
        SELECT @solutionId, c.CategoryName
        FROM   @cats c
        WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionTermCategory k WITH (UPDLOCK, HOLDLOCK)
                           WHERE k.SolutionID = @solutionId AND k.CategoryName = c.CategoryName)

        DELETE x FROM dbo.SolutionTermCategoryXRef x
        JOIN   dbo.SolutionTermCategory k ON k.SolutionTermCategoryID = x.SolutionTermCategoryID
        WHERE  x.SolutionTermID = @id
          AND  NOT EXISTS (SELECT 1 FROM @cats c WHERE c.CategoryName = k.CategoryName)

        INSERT INTO dbo.SolutionTermCategoryXRef (SolutionTermID, SolutionTermCategoryID)
        SELECT @id, k.SolutionTermCategoryID
        FROM   @cats c
        JOIN   dbo.SolutionTermCategory k ON k.SolutionID = @solutionId AND k.CategoryName = c.CategoryName
        WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionTermCategoryXRef x
                           WHERE x.SolutionTermID = @id AND x.SolutionTermCategoryID = k.SolutionTermCategoryID)
    END

    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = CASE WHEN ERROR_NUMBER() IN (2601, 2627)
        THEN N'This solution already has that term. Nothing was saved.' ELSE ERROR_MESSAGE() END
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionTermID

GO
