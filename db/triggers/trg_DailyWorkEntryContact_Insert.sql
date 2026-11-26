SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkEntryContact_Insert') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkEntryContact_Insert
GO
/*

Trigger trg_DailyWorkEntryContact_Insert on dbo.DailyWorkEntryContact.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkEntryContact_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkEntryContact_Insert ON dbo.DailyWorkEntryContact AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkEntryContact_LOG (IsInsertion, DailyWorkEntryContactID, DailyWorkEntryID, ContactID, SortOrder, CreatedDate)
    SELECT 1, DailyWorkEntryContactID, DailyWorkEntryID, ContactID, SortOrder, CreatedDate FROM Inserted; END;

GO
