SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriageRule_Get') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriageRule_Get
GO
CREATE PROCEDURE dbo.stp_MailTriageRule_Get
    @prmMailTriageRuleID INT           = 0,
    @prmSenderKey        NVARCHAR(254) = NULL
AS

/*

Mail Triage (Story 3561): one sender rule, by its id or by its sender key (domain or address).

TEST
----

    EXEC dbo.stp_MailTriageRule_Get @prmSenderKey = N'midlandcredit.com'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix        New (Story 3561, Mail Triage).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SELECT r.MailTriageRuleID, r.SenderKey, r.KeyKind, r.Disposition, r.IsProtected, r.GmailFilterID, r.FilterMode,
       r.Note, r.CreatedDate, r.ModifiedDate
FROM   dbo.MailTriageRule r
WHERE  (ISNULL(@prmMailTriageRuleID, 0) > 0 AND r.MailTriageRuleID = @prmMailTriageRuleID)
    OR (ISNULL(@prmMailTriageRuleID, 0) = 0 AND r.SenderKey = LOWER(LTRIM(RTRIM(@prmSenderKey))))

GO
