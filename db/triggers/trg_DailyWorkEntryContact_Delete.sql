SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkEntryContact_Delete') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkEntryContact_Delete
GO
/*

Trigger trg_DailyWorkEntryContact_Delete on dbo.DailyWorkEntryContact.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkEntryContact_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkEntryContact_Delete ON dbo.DailyWorkEntryContact AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkEntryContact_LOG (IsInsertion, DeletedDate, DailyWorkEntryContactID, DailyWorkEntryID, ContactID, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), DailyWorkEntryContactID, DailyWorkEntryID, ContactID, SortOrder, CreatedDate FROM Deleted; END;

GO
