SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_FileCategory_Insert') IS NOT NULL DROP TRIGGER dbo.trg_FileCategory_Insert
GO
CREATE TRIGGER dbo.trg_FileCategory_Insert ON dbo.FileCategory AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.FileCategory_LOG (IsInsertion, FileCategoryID, CategoryName, ColorHex, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, FileCategoryID, CategoryName, ColorHex, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
