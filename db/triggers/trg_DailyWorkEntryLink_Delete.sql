SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_DailyWorkEntryLink_Delete') IS NOT NULL DROP TRIGGER dbo.trg_DailyWorkEntryLink_Delete
GO
/*

Trigger trg_DailyWorkEntryLink_Delete on dbo.DailyWorkEntryLink.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into DailyWorkEntryLink_LOG.

*/

CREATE TRIGGER dbo.trg_DailyWorkEntryLink_Delete ON dbo.DailyWorkEntryLink AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.DailyWorkEntryLink_LOG (IsInsertion, DeletedDate, DailyWorkEntryLinkID, DailyWorkEntryID, LinkUrl, SortOrder, CreatedDate)
    SELECT 0, GETDATE(), DailyWorkEntryLinkID, DailyWorkEntryID, LinkUrl, SortOrder, CreatedDate FROM Deleted; END;

GO
