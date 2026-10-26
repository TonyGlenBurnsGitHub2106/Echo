SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_LookupRegistry_Insert') IS NOT NULL DROP TRIGGER dbo.trg_LookupRegistry_Insert
GO
CREATE TRIGGER dbo.trg_LookupRegistry_Insert ON dbo.LookupRegistry AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.LookupRegistry_LOG (IsInsertion, LookupRegistryID, LookupKey, LookupLabel, ShelfLabel, ItemNoun, TableName, IdColumn, NameColumn, CodeColumn, ProtectedCodes, ReservedNames, PageUrl, SortOrder, CreatedDate)
    SELECT 1, LookupRegistryID, LookupKey, LookupLabel, ShelfLabel, ItemNoun, TableName, IdColumn, NameColumn, CodeColumn, ProtectedCodes, ReservedNames, PageUrl, SortOrder, CreatedDate FROM Inserted; END;

GO
