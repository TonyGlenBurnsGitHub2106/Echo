SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityWebAddressSequence_Insert') IS NOT NULL DROP TRIGGER dbo.trg_EntityWebAddressSequence_Insert
GO
CREATE TRIGGER dbo.trg_EntityWebAddressSequence_Insert ON dbo.EntityWebAddressSequence AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityWebAddressSequence_LOG (IsInsertion, EntityWebAddressXRefID, SortOrder, ModifiedDate)
    SELECT 1, EntityWebAddressXRefID, SortOrder, ModifiedDate FROM Inserted; END;

GO
