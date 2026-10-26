SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityWebAddressFlag_Update') IS NOT NULL DROP TRIGGER dbo.trg_EntityWebAddressFlag_Update
GO
CREATE TRIGGER dbo.trg_EntityWebAddressFlag_Update ON dbo.EntityWebAddressFlag AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityWebAddressFlag_LOG (IsInsertion, EntityWebAddressXRefID, IsImportant, ModifiedDate)
    SELECT 0, EntityWebAddressXRefID, IsImportant, ModifiedDate FROM Inserted; END;

GO
