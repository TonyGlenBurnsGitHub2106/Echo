SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_MailTriageRule_Delete') IS NOT NULL DROP TRIGGER dbo.trg_MailTriageRule_Delete
GO
/*

Trigger trg_MailTriageRule_Delete on dbo.MailTriageRule.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-26  Felix        Story 5654: echoed from Leonardo (Schema_MailTriage.sql) for Echo's Email
                         Client: audit row into MailTriageRule_LOG. Unchanged.

*/

CREATE TRIGGER dbo.trg_MailTriageRule_Delete ON dbo.MailTriageRule AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.MailTriageRule_LOG (IsInsertion, DeletedDate, MailTriageRuleID, SenderKey, KeyKind, Disposition, IsProtected, GmailFilterID, FilterMode, Note, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), MailTriageRuleID, SenderKey, KeyKind, Disposition, IsProtected, GmailFilterID, FilterMode, Note, CreatedDate, ModifiedDate FROM Deleted; END;

GO
