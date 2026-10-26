SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientFollowUp_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientFollowUp_Delete
GO
/*

Trigger trg_GmailClientFollowUp_Delete on dbo.GmailClientFollowUp.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientFollowUp.sql) for Echo's
                         Email Client: audit row into GmailClientFollowUp_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientFollowUp_Delete ON dbo.GmailClientFollowUp AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientFollowUp_LOG (IsInsertion, DeletedDate, GmailClientFollowUpID, GmailClientMessageID, DueDate, Note, IsDone, DoneDate, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), GmailClientFollowUpID, GmailClientMessageID, DueDate, Note, IsDone, DoneDate, CreatedDate, ModifiedDate FROM Deleted; END;

GO
