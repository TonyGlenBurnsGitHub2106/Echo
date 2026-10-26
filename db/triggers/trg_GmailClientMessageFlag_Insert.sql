SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientMessageFlag_Insert') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientMessageFlag_Insert
GO
/*

Trigger trg_GmailClientMessageFlag_Insert on dbo.GmailClientMessageFlag.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientFlag.sql) for Echo's Email
                         Client: audit row into GmailClientMessageFlag_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientMessageFlag_Insert ON dbo.GmailClientMessageFlag AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientMessageFlag_LOG (IsInsertion, GmailClientMessageFlagID, GmailClientMessageID, GmailClientFlagID, CreatedDate)
    SELECT 1, GmailClientMessageFlagID, GmailClientMessageID, GmailClientFlagID, CreatedDate FROM Inserted; END;

GO
