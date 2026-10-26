SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_GmailClientSentReply') IS NOT NULL DROP VIEW dbo.vw_GmailClientSentReply
GO
-- vw_GmailClientSentReply.sql
--
-- Gmail Client Sent mail (Story 5596) -- the one definition of "has anyone replied?".
--
-- A reply to a sent message is a message the Gmail Client read in (GmailClientMessage) that is in
-- the same Gmail thread and arrived AFTER the sent message, is not deleted in the Gmail Client and
-- is not from a blacklisted sender. One row per sent message that has at least one reply:
-- ReplyCount and LastReplyDate. A sent message with no row here is waiting for a reply.
--
-- Read-only. Used by stp_GmailClientSent_ListPage, _Counts and _Get.
--
-- Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail source).
-- Unchanged.

CREATE VIEW dbo.vw_GmailClientSentReply
AS
SELECT  s.GmailClientSentMessageID,
        COUNT(*)            AS ReplyCount,
        MAX(m.ReceivedDate) AS LastReplyDate
FROM    dbo.GmailClientSentMessage s
        JOIN dbo.GmailClientMessage m ON m.ThreadID = s.ThreadID AND m.ReceivedDate > s.SentDate
WHERE   s.ThreadID IS NOT NULL
  AND   m.IsHidden = 0
  AND   NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')
GROUP BY s.GmailClientSentMessageID;

GO
