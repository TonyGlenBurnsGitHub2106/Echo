SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteType_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteType_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_SolutionNoteType_FindOrCreate
    @prmNoteTypeName NVARCHAR(400)
AS

/*

Resolve a typed Solutions note kind to its id, creating the kind (at the end of the order) when
nobody has used that name yet. This is the find-or-create behind the Kind picker on the Solutions
page, so typing "Checklist" and tapping the green + Add row is how a new kind is born.

A name matches an existing kind by its name OR its code, ignoring case. A new kind's code is the
name lowercased with everything but letters and digits removed, cut to 20 characters, with a
number added when that code is already taken.

Returns SolutionNoteTypeID, NoteTypeCode, NoteTypeName and WasCreated.

TEST
----

    EXEC dbo.stp_SolutionNoteType_FindOrCreate @prmNoteTypeName = N'Strategy'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New: the note Kind field is a find-or-create picker.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @name    NVARCHAR(400) = LTRIM(RTRIM(ISNULL(@prmNoteTypeName, N'')))
DECLARE @id      INT
DECLARE @created BIT = 0

IF @name = N''
BEGIN
    RAISERROR('A kind needs a name. Nothing was saved.', 16, 1)
    RETURN
END

IF LEN(@name) > 100
BEGIN
    RAISERROR('A kind name can be at most 100 characters. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRANSACTION

SELECT @id = SolutionNoteTypeID
FROM   dbo.SolutionNoteType WITH (UPDLOCK, HOLDLOCK)
WHERE  NoteTypeName = @name OR NoteTypeCode = LOWER(@name)

IF @id IS NULL
BEGIN
    DECLARE @base VARCHAR(20) = ''
    DECLARE @i    INT = 1
    DECLARE @ch   NCHAR(1)
    WHILE @i <= LEN(@name) AND LEN(@base) < 20
    BEGIN
        SET @ch = LOWER(SUBSTRING(@name, @i, 1))
        IF @ch LIKE '[a-z0-9]' COLLATE Latin1_General_BIN SET @base = @base + CAST(@ch AS VARCHAR(1))
        SET @i = @i + 1
    END
    IF @base = '' SET @base = 'kind'

    DECLARE @code VARCHAR(20) = @base
    DECLARE @n    INT = 2
    WHILE EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE NoteTypeCode = @code)
    BEGIN
        SET @code = LEFT(@base, 20 - LEN(CAST(@n AS VARCHAR(10)))) + CAST(@n AS VARCHAR(10))
        SET @n = @n + 1
    END

    INSERT INTO dbo.SolutionNoteType (NoteTypeCode, NoteTypeName, SortOrder)
    VALUES (@code, @name, (SELECT ISNULL(MAX(SortOrder), 0) + 1 FROM dbo.SolutionNoteType))
    SET @id = SCOPE_IDENTITY()
    SET @created = 1
END

COMMIT TRANSACTION

SELECT SolutionNoteTypeID, NoteTypeCode, NoteTypeName, @created AS WasCreated
FROM   dbo.SolutionNoteType
WHERE  SolutionNoteTypeID = @id


GO
