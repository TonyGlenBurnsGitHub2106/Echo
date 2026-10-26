SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientFollowUp_Insert') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientFollowUp_Insert
GO
/*

Trigger trg_GmailClientFollowUp_Insert on dbo.GmailClientFollowUp.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientFollowUp.sql) for Echo's
                         Email Client: audit row into GmailClientFollowUp_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientFollowUp_Insert ON dbo.GmailClientFollowUp AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientFollowUp_LOG (IsInsertion, GmailClientFollowUpID, GmailClientMessageID, DueDate, Note, IsDone, DoneDate, CreatedDate, ModifiedDate)
    SELECT 1, GmailClientFollowUpID, GmailClientMessageID, DueDate, Note, IsDone, DoneDate, CreatedDate, ModifiedDate FROM Inserted; END;

GO
