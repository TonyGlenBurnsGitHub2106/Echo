SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityWebAddressSequence_Update') IS NOT NULL DROP TRIGGER dbo.trg_EntityWebAddressSequence_Update
GO
CREATE TRIGGER dbo.trg_EntityWebAddressSequence_Update ON dbo.EntityWebAddressSequence AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityWebAddressSequence_LOG (IsInsertion, EntityWebAddressXRefID, SortOrder, ModifiedDate)
    SELECT 0, EntityWebAddressXRefID, SortOrder, ModifiedDate FROM Inserted; END;

GO
