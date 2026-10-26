SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_EntityLoginSetupXRef_Update') IS NOT NULL DROP TRIGGER dbo.trg_EntityLoginSetupXRef_Update
GO
/* Story 5653 (Echo, 2026-10-26): audit row into EntityLoginSetupXRef_LOG -- Leonardo has no twin for this table. */
CREATE TRIGGER dbo.trg_EntityLoginSetupXRef_Update ON dbo.EntityLoginSetupXRef AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.EntityLoginSetupXRef_LOG (IsInsertion, DeletedDate, EntityLoginSetupXRefID, EntityID, LoginSetupID, EffectiveDate, ThroughDate, CreatedDate)
    SELECT 0, NULL, EntityLoginSetupXRefID, EntityID, LoginSetupID, EffectiveDate, ThroughDate, CreatedDate FROM Inserted; END;

GO
