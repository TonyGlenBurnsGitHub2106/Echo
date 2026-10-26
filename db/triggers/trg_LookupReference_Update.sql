SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_LookupReference_Update') IS NOT NULL DROP TRIGGER dbo.trg_LookupReference_Update
GO
CREATE TRIGGER dbo.trg_LookupReference_Update ON dbo.LookupReference AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.LookupReference_LOG (IsInsertion, LookupReferenceID, LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder, CreatedDate)
    SELECT 0, LookupReferenceID, LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder, CreatedDate FROM Inserted; END;

GO
