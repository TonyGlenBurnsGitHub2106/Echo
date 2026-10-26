SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientMessage_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientMessage_Delete
GO
/*

Trigger trg_GmailClientMessage_Delete on dbo.GmailClientMessage.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClient.sql) for Echo's Email
                         Client: audit row into GmailClientMessage_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientMessage_Delete ON dbo.GmailClientMessage AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientMessage_LOG (IsInsertion, DeletedDate, GmailClientMessageID, GmailID, ThreadID, SenderKey, FromHeader, Subject, ReceivedDate, IsRead, IsHidden, HiddenDate, CreatedDate, ModifiedDate, EntityID)
    SELECT 0, GETDATE(), GmailClientMessageID, GmailID, ThreadID, SenderKey, FromHeader, Subject, ReceivedDate, IsRead, IsHidden, HiddenDate, CreatedDate, ModifiedDate, EntityID FROM Deleted; END;

GO
