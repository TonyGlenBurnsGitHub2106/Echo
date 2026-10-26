SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientInbox_Insert') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientInbox_Insert
GO
/*

Trigger trg_GmailClientInbox_Insert on dbo.GmailClientInbox.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClient.sql) for Echo's Email
                         Client: audit row into GmailClientInbox_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientInbox_Insert ON dbo.GmailClientInbox AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientInbox_LOG (IsInsertion, GmailClientInboxID, InboxName, SortOrder, CreatedDate, ModifiedDate)
    SELECT 1, GmailClientInboxID, InboxName, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
