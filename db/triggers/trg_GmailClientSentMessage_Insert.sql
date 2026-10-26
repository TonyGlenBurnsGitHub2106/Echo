SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientSentMessage_Insert') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientSentMessage_Insert
GO
/*

Trigger trg_GmailClientSentMessage_Insert on dbo.GmailClientSentMessage.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientSent.sql) for Echo's Email
                         Client: audit row into GmailClientSentMessage_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientSentMessage_Insert ON dbo.GmailClientSentMessage AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientSentMessage_LOG (IsInsertion, GmailClientSentMessageID, GmailID, ThreadID, FromHeader, ToHeader, CcHeader, BccHeader, Subject, Snippet, MessageIDHeader, SentDate, IsFromGmailClient, IsHidden, HiddenDate, FollowUpDate, FollowUpNote, FollowUpDoneDate, CreatedDate, ModifiedDate)
    SELECT 1, GmailClientSentMessageID, GmailID, ThreadID, FromHeader, ToHeader, CcHeader, BccHeader, Subject, Snippet, MessageIDHeader, SentDate, IsFromGmailClient, IsHidden, HiddenDate, FollowUpDate, FollowUpNote, FollowUpDoneDate, CreatedDate, ModifiedDate FROM Inserted; END;

GO
