SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientCheck_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientCheck_Delete
GO
/*

Trigger trg_GmailClientCheck_Delete on dbo.GmailClientCheck.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClient.sql) for Echo's Email
                         Client: audit row into GmailClientCheck_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientCheck_Delete ON dbo.GmailClientCheck AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientCheck_LOG (IsInsertion, DeletedDate, GmailClientCheckID, StartedDate, FinishedDate, WindowStartDate, MessagesListed, MessagesAdded, IsComplete, ErrorText)
    SELECT 0, GETDATE(), GmailClientCheckID, StartedDate, FinishedDate, WindowStartDate, MessagesListed, MessagesAdded, IsComplete, ErrorText FROM Deleted; END;

GO
