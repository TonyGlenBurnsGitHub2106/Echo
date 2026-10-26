SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessageBody_ListMissing') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessageBody_ListMissing
GO
CREATE PROCEDURE dbo.stp_GmailClientMessageBody_ListMissing
    @prmTop INT = 500
AS

/*

Gmail Client (Story 5576): the messages the Gmail Client shows (not hidden, sender whitelisted -- or
not decided on yet, for the Undecided tab; never blacklisted)
whose body Leonardo has not saved yet, newest first -- what a Check mail saves next.

TEST
----

    EXEC dbo.stp_GmailClientMessageBody_ListMissing 20

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-02  Felix        New (Story 5576, the whole message kept in Leonardo).
2026-10-04  Felix        Undecided senders' mail too, so the Undecided tab shows its paperclips.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

DECLARE @top INT = CASE WHEN ISNULL(@prmTop, 0) <= 0 THEN 500 WHEN @prmTop > 5000 THEN 5000 ELSE @prmTop END

SELECT TOP (@top) m.GmailClientMessageID, m.GmailID
FROM   dbo.GmailClientMessage m
WHERE  m.IsHidden = 0
  AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')
  AND  NOT EXISTS (SELECT 1 FROM dbo.GmailClientMessageBody b WHERE b.GmailClientMessageID = m.GmailClientMessageID)
ORDER BY m.ReceivedDate DESC, m.GmailClientMessageID DESC

GO
