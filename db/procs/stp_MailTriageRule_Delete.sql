SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriageRule_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriageRule_Delete
GO
CREATE PROCEDURE dbo.stp_MailTriageRule_Delete
    @prmMailTriageRuleID INT
AS

/*

Mail Triage (Story 3561): take a sender off the whitelist or blacklist. The sender is unknown
again, so the next run asks about it. A protected sender cannot be removed until it is
unprotected. The caller deletes the Gmail filter BEFORE calling this (a black rule's
GmailFilterID is read from stp_MailTriageRule_Get). Returns Deleted (0 or 1).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_MailTriageRule_Delete 0; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix        New (Story 3561, Mail Triage).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
IF EXISTS (SELECT 1 FROM dbo.MailTriageRule WHERE MailTriageRuleID = @prmMailTriageRuleID AND IsProtected = 1)
BEGIN
    RAISERROR('That sender is protected. Unprotect it first; nothing was removed.', 16, 1)
    RETURN
END

DELETE FROM dbo.MailTriageRule WHERE MailTriageRuleID = @prmMailTriageRuleID
SELECT @@ROWCOUNT AS Deleted

GO
