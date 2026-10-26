SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientSenderInbox_Update') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientSenderInbox_Update
GO
/*

Trigger trg_GmailClientSenderInbox_Update on dbo.GmailClientSenderInbox.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClient.sql) for Echo's Email
                         Client: audit row into GmailClientSenderInbox_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientSenderInbox_Update ON dbo.GmailClientSenderInbox AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientSenderInbox_LOG (IsInsertion, GmailClientSenderInboxID, SenderKey, GmailClientInboxID, CreatedDate, ModifiedDate)
    SELECT 0, GmailClientSenderInboxID, SenderKey, GmailClientInboxID, CreatedDate, ModifiedDate FROM Inserted; END;

GO
