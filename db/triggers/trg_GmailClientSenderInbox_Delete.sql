SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientSenderInbox_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientSenderInbox_Delete
GO
/*

Trigger trg_GmailClientSenderInbox_Delete on dbo.GmailClientSenderInbox.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClient.sql) for Echo's Email
                         Client: audit row into GmailClientSenderInbox_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientSenderInbox_Delete ON dbo.GmailClientSenderInbox AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientSenderInbox_LOG (IsInsertion, DeletedDate, GmailClientSenderInboxID, SenderKey, GmailClientInboxID, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), GmailClientSenderInboxID, SenderKey, GmailClientInboxID, CreatedDate, ModifiedDate FROM Deleted; END;

GO
