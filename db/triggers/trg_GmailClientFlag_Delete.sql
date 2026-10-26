SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_GmailClientFlag_Delete') IS NOT NULL DROP TRIGGER dbo.trg_GmailClientFlag_Delete
GO
/*

Trigger trg_GmailClientFlag_Delete on dbo.GmailClientFlag.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_GmailClientFlag.sql) for Echo's Email
                         Client: audit row into GmailClientFlag_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_GmailClientFlag_Delete ON dbo.GmailClientFlag AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.GmailClientFlag_LOG (IsInsertion, DeletedDate, GmailClientFlagID, FlagName, SortOrder, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), GmailClientFlagID, FlagName, SortOrder, CreatedDate, ModifiedDate FROM Deleted; END;

GO
