SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityWebAddressFlag_Insert') IS NOT NULL DROP TRIGGER dbo.trg_EntityWebAddressFlag_Insert
GO
CREATE TRIGGER dbo.trg_EntityWebAddressFlag_Insert ON dbo.EntityWebAddressFlag AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityWebAddressFlag_LOG (IsInsertion, EntityWebAddressXRefID, IsImportant, ModifiedDate)
    SELECT 1, EntityWebAddressXRefID, IsImportant, ModifiedDate FROM Inserted; END;

GO
