SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_Get') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_Get
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_Get
    @prmGmailClientSentMessageID INT
AS

/*

Gmail Client Sent mail (Story 5596): one sent message that is not deleted in the Gmail Client --
its headers (Bcc too, when it went from the Gmail Client), its open follow-up and its reply count.
No row when there is no such message. Never returns the body (stp_GmailClientSentBody_Get does).

TEST
----

    EXEC dbo.stp_GmailClientSent_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-12  Felix        LinkCount: the records the sent message is linked to.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no record
                         links (EntityGmailClientSentXRef): LinkCount is always 0.

*/

SET NOCOUNT ON

SELECT s.GmailClientSentMessageID, s.GmailID, s.ThreadID, s.FromHeader, s.ToHeader, s.CcHeader, s.BccHeader,
       s.Subject, s.Snippet, s.MessageIDHeader, s.SentDate, s.IsFromGmailClient, s.FollowUpDate, s.FollowUpNote,
       ISNULL(r.ReplyCount, 0) AS ReplyCount, r.LastReplyDate,
       CAST(CASE WHEN b.AttachmentNames IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS HasAttachments,
       0 AS LinkCount   -- Story 5654: Echo has no record links (EntityGmailClientSentXRef)
FROM   dbo.GmailClientSentMessage s
       LEFT JOIN dbo.vw_GmailClientSentReply r ON r.GmailClientSentMessageID = s.GmailClientSentMessageID
       LEFT JOIN dbo.GmailClientSentMessageBody b ON b.GmailClientSentMessageID = s.GmailClientSentMessageID
WHERE  s.GmailClientSentMessageID = @prmGmailClientSentMessageID
  AND  s.IsHidden = 0

GO
