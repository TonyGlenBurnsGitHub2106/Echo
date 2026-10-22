SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_FileCategory_Delete') IS NOT NULL DROP TRIGGER dbo.trg_FileCategory_Delete
GO
CREATE TRIGGER dbo.trg_FileCategory_Delete ON dbo.FileCategory AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.FileCategory_LOG (IsInsertion, DeletedDate, FileCategoryID, CategoryName, ColorHex, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), FileCategoryID, CategoryName, ColorHex, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
