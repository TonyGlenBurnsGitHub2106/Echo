SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSenderInbox_ListForInbox') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSenderInbox_ListForInbox
GO
CREATE PROCEDURE dbo.stp_GmailClientSenderInbox_ListForInbox
    @prmGmailClientInboxID INT
AS

/*

Gmail Client (2026-10-02): the senders filed in one inbox -- what the Inboxes dialog shows when
its sender count is tapped. A sender is a domain, a free-mail address, or (Story 5608) one
person's address filed on their own, apart from their domain (IsAddress = 1 for an address).
For each: how many of its messages the client shows in this inbox (not hidden, sender
whitelisted -- a domain's count leaves out anyone at that domain filed elsewhere on their own),
how many are unread, when the last one came, and a sample From line so a domain shows a
recognisable name. Busiest first. A sender with no mail yet is listed too (Messages 0).

TEST
----

    EXEC dbo.stp_GmailClientSenderInbox_ListForInbox 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-02  Felix        New (Gmail Client: who is in this inbox).
2026-10-08  Felix        Story 5608: sender-level rows (IsAddress); counts follow
                         vw_GmailClientMessageInbox.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT s.SenderKey,
       CAST(CASE WHEN CHARINDEX(N'@', s.SenderKey) > 0 THEN 1 ELSE 0 END AS BIT) AS IsAddress,
       COUNT(m.GmailClientMessageID) AS Messages,
       SUM(CASE WHEN m.GmailClientMessageID IS NOT NULL AND m.IsRead = 0 THEN 1 ELSE 0 END) AS Unread,
       MAX(m.ReceivedDate) AS LastReceived,
       MAX(m.FromHeader) AS SampleFrom
FROM   dbo.GmailClientSenderInbox s
       LEFT JOIN dbo.vw_GmailClientMessageInbox v ON v.GmailClientSenderInboxID = s.GmailClientSenderInboxID
       LEFT JOIN dbo.GmailClientMessage m
              ON m.GmailClientMessageID = v.GmailClientMessageID
             AND m.IsHidden = 0
             AND EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'white')
WHERE  s.GmailClientInboxID = @prmGmailClientInboxID
GROUP BY s.SenderKey
ORDER BY COUNT(m.GmailClientMessageID) DESC, s.SenderKey

GO
