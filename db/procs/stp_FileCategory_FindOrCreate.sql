SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_FileCategory_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_FileCategory_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_FileCategory_FindOrCreate
    @prmCategoryName NVARCHAR(100)
AS

/*

Story 3520: resolve a typed category name to its id, creating the category (at the end of
the order, no colour) when nobody has used that name yet. This is the find-or-create
behind every "File to" picker, so typing "Medical" on the phone is how Medical is born.
Returns FileCategoryID, CategoryName, ColorHex, SortOrder and WasCreated.

TEST
----

    EXEC dbo.stp_FileCategory_FindOrCreate @prmCategoryName = N'Tax'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-23  Felix        New (Story 3520).

*/

SET NOCOUNT ON

DECLARE @name    NVARCHAR(100) = LTRIM(RTRIM(ISNULL(@prmCategoryName, N'')))
DECLARE @id      INT
DECLARE @created BIT = 0

IF @name = N''
BEGIN
    RAISERROR('A category needs a name.', 16, 1)
    RETURN
END

SELECT @id = FileCategoryID FROM dbo.FileCategory WHERE CategoryName = @name

IF @id IS NULL
BEGIN
    INSERT INTO dbo.FileCategory (CategoryName, SortOrder)
    VALUES (@name, (SELECT ISNULL(MAX(SortOrder), 0) + 10 FROM dbo.FileCategory))
    SET @id = SCOPE_IDENTITY()
    SET @created = 1
END

SELECT FileCategoryID, CategoryName, ISNULL(ColorHex, '') AS ColorHex, SortOrder, @created AS WasCreated
FROM   dbo.FileCategory
WHERE  FileCategoryID = @id

GO
