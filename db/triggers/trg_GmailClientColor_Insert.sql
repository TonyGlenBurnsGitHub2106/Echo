SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientColor_Insert') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientColor_Insert
GO
/*

Trigger trg_GmailClientColor_Insert on dbo.GmailClientColor.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientColor.sql) for Echo's
                         Email Client: audit row into GmailClientColor_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientColor_Insert ON dbo.GmailClientColor AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientColor_LOG (IsInsertion, GmailClientColorID, ColorKind, MatchText, GmailClientFlagID, ColorHex, CreatedDate, ModifiedDate)
    SELECT 1, GmailClientColorID, ColorKind, MatchText, GmailClientFlagID, ColorHex, CreatedDate, ModifiedDate FROM Inserted; END;

GO
