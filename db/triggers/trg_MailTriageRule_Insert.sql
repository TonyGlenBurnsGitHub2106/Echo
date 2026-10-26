SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_MailTriageRule_Insert') IS NOT NULL DROP TRIGGER dbo.trg_MailTriageRule_Insert
GO
/*

Trigger trg_MailTriageRule_Insert on dbo.MailTriageRule.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_MailTriage.sql) for Echo's Email
                         Client: audit row into MailTriageRule_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_MailTriageRule_Insert ON dbo.MailTriageRule AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.MailTriageRule_LOG (IsInsertion, MailTriageRuleID, SenderKey, KeyKind, Disposition, IsProtected, GmailFilterID, FilterMode, Note, CreatedDate, ModifiedDate)
    SELECT 1, MailTriageRuleID, SenderKey, KeyKind, Disposition, IsProtected, GmailFilterID, FilterMode, Note, CreatedDate, ModifiedDate FROM Inserted; END;

GO
