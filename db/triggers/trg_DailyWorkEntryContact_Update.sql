SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkEntryContact_Update') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkEntryContact_Update
GO
/*

Trigger trg_DailyWorkEntryContact_Update on dbo.DailyWorkEntryContact.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkEntryContact_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkEntryContact_Update ON dbo.DailyWorkEntryContact AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkEntryContact_LOG (IsInsertion, DailyWorkEntryContactID, DailyWorkEntryID, ContactID, SortOrder, CreatedDate)
    SELECT 0, DailyWorkEntryContactID, DailyWorkEntryID, ContactID, SortOrder, CreatedDate FROM Inserted; END;

GO
