SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_LookupReference_Delete') IS NOT NULL DROP TRIGGER dbo.trg_LookupReference_Delete
GO
CREATE TRIGGER dbo.trg_LookupReference_Delete ON dbo.LookupReference AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.LookupReference_LOG (IsInsertion, DeletedDate, LookupReferenceID, LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), LookupReferenceID, LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder, CreatedDate FROM Deleted; END;

GO
