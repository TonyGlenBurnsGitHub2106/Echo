SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkKind_Update') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkKind_Update
GO
/*

Trigger trg_DailyWorkKind_Update on dbo.DailyWorkKind.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkKind_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkKind_Update ON dbo.DailyWorkKind AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkKind_LOG (IsInsertion, DailyWorkKindID, KindName, IsActive, CreatedDate, ModifiedDate)
    SELECT 0, DailyWorkKindID, KindName, IsActive, CreatedDate, ModifiedDate FROM Inserted; END;

GO
