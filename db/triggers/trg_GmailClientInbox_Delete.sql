SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientInbox_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientInbox_Delete
GO
/*

Trigger trg_GmailClientInbox_Delete on dbo.GmailClientInbox.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClient.sql) for Echo's Email
                         Client: audit row into GmailClientInbox_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientInbox_Delete ON dbo.GmailClientInbox AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientInbox_LOG (IsInsertion, DeletedDate, GmailClientInboxID, InboxName, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), GmailClientInboxID, InboxName, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
