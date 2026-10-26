SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityWebAddressFlag_Delete') IS NOT NULL DROP TRIGGER dbo.trg_EntityWebAddressFlag_Delete
GO
CREATE TRIGGER dbo.trg_EntityWebAddressFlag_Delete ON dbo.EntityWebAddressFlag AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityWebAddressFlag_LOG (IsInsertion, DeletedDate, EntityWebAddressXRefID, IsImportant, ModifiedDate)
    SELECT 0, GETDATE(), EntityWebAddressXRefID, IsImportant, ModifiedDate FROM Deleted; END;

GO
