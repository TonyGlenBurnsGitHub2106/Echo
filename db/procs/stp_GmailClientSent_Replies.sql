SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_Replies') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_Replies
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_Replies
    @prmGmailClientSentMessageID INT
AS

/*

Gmail Client Sent mail (Story 5596): the replies to one sent message, oldest first -- the same
rule as vw_GmailClientSentReply (same Gmail thread, arrived after it was sent, not deleted, not from
a blacklisted sender), listed so the reader can open each one. At most 50.

TEST
----

    EXEC dbo.stp_GmailClientSent_Replies 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT TOP (50) m.GmailClientMessageID, m.FromHeader, m.Subject, m.Snippet, m.ReceivedDate, m.IsRead
FROM   dbo.GmailClientSentMessage s
       JOIN dbo.GmailClientMessage m ON m.ThreadID = s.ThreadID AND m.ReceivedDate > s.SentDate
WHERE  s.GmailClientSentMessageID = @prmGmailClientSentMessageID
  AND  s.ThreadID IS NOT NULL
  AND  m.IsHidden = 0
  AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')
ORDER BY m.ReceivedDate, m.GmailClientMessageID

GO
