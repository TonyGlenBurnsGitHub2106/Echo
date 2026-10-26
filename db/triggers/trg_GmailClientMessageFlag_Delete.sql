SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientMessageFlag_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientMessageFlag_Delete
GO
/*

Trigger trg_GmailClientMessageFlag_Delete on dbo.GmailClientMessageFlag.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientFlag.sql) for Echo's Email
                         Client: audit row into GmailClientMessageFlag_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientMessageFlag_Delete ON dbo.GmailClientMessageFlag AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientMessageFlag_LOG (IsInsertion, DeletedDate, GmailClientMessageFlagID, GmailClientMessageID, GmailClientFlagID, CreatedDate)
    SELECT 0, GETDATE(), GmailClientMessageFlagID, GmailClientMessageID, GmailClientFlagID, CreatedDate FROM Deleted; END;

GO
