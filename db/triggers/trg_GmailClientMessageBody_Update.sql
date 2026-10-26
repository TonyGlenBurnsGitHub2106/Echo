SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientMessageBody_Update') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientMessageBody_Update
GO
/*

Trigger trg_GmailClientMessageBody_Update on dbo.GmailClientMessageBody.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientMessageBody.sql) for
                         Echo's Email Client: audit row into GmailClientMessageBody_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientMessageBody_Update ON dbo.GmailClientMessageBody AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientMessageBody_LOG (IsInsertion, GmailClientMessageID, HtmlLength, TextLength, AttachmentNames, IsGoneFromGmail, SavedDate)
    SELECT 0, GmailClientMessageID, LEN(BodyHtml), LEN(BodyText), AttachmentNames, IsGoneFromGmail, SavedDate FROM Inserted; END;

GO
