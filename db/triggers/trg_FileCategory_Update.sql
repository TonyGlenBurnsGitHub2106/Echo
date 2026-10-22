SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_FileCategory_Update') IS NOT NULL DROP TRIGGER dbo.trg_FileCategory_Update
GO
CREATE TRIGGER dbo.trg_FileCategory_Update ON dbo.FileCategory AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.FileCategory_LOG (IsInsertion, FileCategoryID, CategoryName, ColorHex, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, FileCategoryID, CategoryName, ColorHex, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
