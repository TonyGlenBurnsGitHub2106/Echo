SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_LookupRegistry_Delete') IS NOT NULL DROP TRIGGER dbo.trg_LookupRegistry_Delete
GO
CREATE TRIGGER dbo.trg_LookupRegistry_Delete ON dbo.LookupRegistry AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.LookupRegistry_LOG (IsInsertion, DeletedDate, LookupRegistryID, LookupKey, LookupLabel, ShelfLabel, ItemNoun, TableName, IdColumn, NameColumn, CodeColumn, ProtectedCodes, ReservedNames, PageUrl, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), LookupRegistryID, LookupKey, LookupLabel, ShelfLabel, ItemNoun, TableName, IdColumn, NameColumn, CodeColumn, ProtectedCodes, ReservedNames, PageUrl, SortOrder, CreatedDate FROM Deleted; END;

GO
