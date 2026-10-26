SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityWebAddressSequence_Delete') IS NOT NULL DROP TRIGGER dbo.trg_EntityWebAddressSequence_Delete
GO
CREATE TRIGGER dbo.trg_EntityWebAddressSequence_Delete ON dbo.EntityWebAddressSequence AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityWebAddressSequence_LOG (IsInsertion, DeletedDate, EntityWebAddressXRefID, SortOrder, ModifiedDate)
    SELECT 0, GETDATE(), EntityWebAddressXRefID, SortOrder, ModifiedDate FROM Deleted; END;

GO
