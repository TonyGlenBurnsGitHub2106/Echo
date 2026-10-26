SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientDraft_Insert') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientDraft_Insert
GO
/*

Trigger trg_GmailClientDraft_Insert on dbo.GmailClientDraft.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientDraft.sql) for Echo's
                         Email Client: audit row into GmailClientDraft_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientDraft_Insert ON dbo.GmailClientDraft AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientDraft_LOG (IsInsertion, GmailClientDraftID, ClientKey, Mode, SourceID, ToHeader, CcHeader, BccHeader, Subject, BodyLength, IncludeOriginal, AttachmentCount, GmailDraftID, GmailMessageID, ThreadID, IsFromGmailClient, IsOpen, ClosedReason, ClosedDate, PushedDate, CreatedDate, ModifiedDate)
    SELECT 1, GmailClientDraftID, ClientKey, Mode, SourceID, ToHeader, CcHeader, BccHeader, Subject, LEN(BodyText), IncludeOriginal, AttachmentCount, GmailDraftID, GmailMessageID, ThreadID, IsFromGmailClient, IsOpen, ClosedReason, ClosedDate, PushedDate, CreatedDate, ModifiedDate FROM Inserted; END;

GO
