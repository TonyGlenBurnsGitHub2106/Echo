SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriageRule_SetProtected') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriageRule_SetProtected
GO
CREATE PROCEDURE dbo.stp_MailTriageRule_SetProtected
    @prmMailTriageRuleID INT,
    @prmIsProtected      BIT
AS

/*

Mail Triage (Story 3561): protect a whitelisted sender (it can then never be blacklisted or
removed by accident) or lift the protection. Only a whitelisted sender can be protected.
Returns the rule.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_MailTriageRule_SetProtected 1, 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix        New (Story 3561, Mail Triage).
2026-10-06  Felix        A Junk rule cannot be protected either (Story 5567).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule WHERE MailTriageRuleID = @prmMailTriageRuleID)
BEGIN
    RAISERROR('That sender rule no longer exists.', 16, 1)
    RETURN
END
IF @prmIsProtected = 1 AND EXISTS (SELECT 1 FROM dbo.MailTriageRule
                                   WHERE MailTriageRuleID = @prmMailTriageRuleID AND Disposition <> 'white')
BEGIN
    RAISERROR('Only a whitelisted sender can be protected. Whitelist it first.', 16, 1)
    RETURN
END

UPDATE dbo.MailTriageRule
SET    IsProtected = ISNULL(@prmIsProtected, 0), ModifiedDate = GETDATE()
WHERE  MailTriageRuleID = @prmMailTriageRuleID AND IsProtected <> ISNULL(@prmIsProtected, 0)

EXEC dbo.stp_MailTriageRule_Get @prmMailTriageRuleID = @prmMailTriageRuleID

GO
