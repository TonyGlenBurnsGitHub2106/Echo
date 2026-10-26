SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_LookupReference_Insert') IS NOT NULL DROP TRIGGER dbo.trg_LookupReference_Insert
GO
CREATE TRIGGER dbo.trg_LookupReference_Insert ON dbo.LookupReference AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.LookupReference_LOG (IsInsertion, LookupReferenceID, LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder, CreatedDate)
    SELECT 1, LookupReferenceID, LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder, CreatedDate FROM Inserted; END;

GO
