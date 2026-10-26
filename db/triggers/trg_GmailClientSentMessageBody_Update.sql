SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientSentMessageBody_Update') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientSentMessageBody_Update
GO
/*

Trigger trg_GmailClientSentMessageBody_Update on dbo.GmailClientSentMessageBody.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientSent.sql) for Echo's Email
                         Client: audit row into GmailClientSentMessageBody_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientSentMessageBody_Update ON dbo.GmailClientSentMessageBody AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientSentMessageBody_LOG (IsInsertion, GmailClientSentMessageID, HtmlLength, TextLength, AttachmentNames, IsGoneFromGmail, SavedDate)
    SELECT 0, GmailClientSentMessageID, LEN(BodyHtml), LEN(BodyText), AttachmentNames, IsGoneFromGmail, SavedDate FROM Inserted; END;

GO
