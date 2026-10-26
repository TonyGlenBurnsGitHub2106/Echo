SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientFlag_Update') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientFlag_Update
GO
/*

Trigger trg_GmailClientFlag_Update on dbo.GmailClientFlag.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientFlag.sql) for Echo's Email
                         Client: audit row into GmailClientFlag_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientFlag_Update ON dbo.GmailClientFlag AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientFlag_LOG (IsInsertion, GmailClientFlagID, FlagName, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GmailClientFlagID, FlagName, SortOrder, CreatedDate, ModifiedDate FROM Inserted; END;

GO
