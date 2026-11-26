SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkEntryLink_Insert') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkEntryLink_Insert
GO
/*

Trigger trg_DailyWorkEntryLink_Insert on dbo.DailyWorkEntryLink.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkEntryLink_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkEntryLink_Insert ON dbo.DailyWorkEntryLink AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkEntryLink_LOG (IsInsertion, DailyWorkEntryLinkID, DailyWorkEntryID, LinkUrl, SortOrder, CreatedDate)
    SELECT 1, DailyWorkEntryLinkID, DailyWorkEntryID, LinkUrl, SortOrder, CreatedDate FROM Inserted; END;

GO
