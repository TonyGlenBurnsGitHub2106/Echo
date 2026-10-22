SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_FileCategory_Save') IS NOT NULL DROP PROCEDURE dbo.stp_FileCategory_Save
GO
CREATE PROCEDURE dbo.stp_FileCategory_Save
    @prmFileCategoryID INT,
    @prmCategoryName   NVARCHAR(100),
    @prmColorHex       VARCHAR(7) = NULL,
    @prmSortOrder      INT        = NULL
AS

/*

Story 3520: add or rename / recolour one file category. @prmFileCategoryID <= 0 inserts
(at the end of the order when @prmSortOrder is NULL); otherwise updates, keeping the
current order when @prmSortOrder is NULL. A name another category already carries is
refused -- the name is the lookup's unique key. Returns the saved row.

TEST
----

    EXEC dbo.stp_FileCategory_Save @prmFileCategoryID = 0, @prmCategoryName = N'Medical', @prmColorHex = '#0E7490'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-23  Felix        New (Story 3520).

*/

SET NOCOUNT ON

DECLARE @name  NVARCHAR(100) = LTRIM(RTRIM(ISNULL(@prmCategoryName, N'')))
DECLARE @color VARCHAR(7)    = NULLIF(LTRIM(RTRIM(ISNULL(@prmColorHex, ''))), '')
DECLARE @id    INT           = ISNULL(@prmFileCategoryID, 0)

IF @name = N''
BEGIN
    RAISERROR('A category needs a name.', 16, 1)
    RETURN
END

IF EXISTS (SELECT 1 FROM dbo.FileCategory WHERE CategoryName = @name AND FileCategoryID <> @id)
BEGIN
    RAISERROR('There is already a category with that name.', 16, 1)
    RETURN
END

IF @id <= 0
BEGIN
    INSERT INTO dbo.FileCategory (CategoryName, ColorHex, SortOrder)
    VALUES (@name, @color,
            ISNULL(@prmSortOrder, (SELECT ISNULL(MAX(SortOrder), 0) + 10 FROM dbo.FileCategory)))
    SET @id = SCOPE_IDENTITY()
END
ELSE
BEGIN
    UPDATE dbo.FileCategory
    SET    CategoryName = @name,
           ColorHex     = @color,
           SortOrder    = ISNULL(@prmSortOrder, SortOrder),
           ModifiedDate = GETDATE()
    WHERE  FileCategoryID = @id
END

SELECT FileCategoryID, CategoryName, ISNULL(ColorHex, '') AS ColorHex, SortOrder
FROM   dbo.FileCategory
WHERE  FileCategoryID = @id

GO
