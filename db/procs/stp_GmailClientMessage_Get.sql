SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_Get') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_Get
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_Get
    @prmGmailClientMessageID INT
AS

/*

Gmail Client (Story 5569): one message's stored headers and the inbox it shows in. The body is
read separately (stp_GmailClientMessageBody_Get). A hidden message, or one whose sender is
blacklisted, returns no row (an undecided sender's mail opens, for the Undecided tab).

Since Story 5608 it also carries what the reader needs to file, flag, link and thread it:
EntityID (the message as a Leonardo entity), SenderAddress, the inbox it is in and whether a
sender-level override put it there (IsSenderLevel) plus the inbox its domain is in
(DomainInboxID / DomainInboxName -- vw_GmailClientMessageInbox), FlagIDs (comma separated),
LinkCount (records it is linked to) and ThreadCount (the messages of its Gmail conversation the
client holds, received and sent, this one included).

TEST
----

    EXEC dbo.stp_GmailClientMessage_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-01  Stewart      Returns its open follow-up (due date and note).
2026-10-04  Felix        Opens mail from an undecided sender too; only blacklisted is refused.
2026-10-08  Felix        Story 5608: EntityID, sender-level inboxes, flags, links, thread size.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no record
                         links (EntityGmailClientMessageXRef): LinkCount is always 0.

*/

SET NOCOUNT ON

SELECT m.GmailClientMessageID, m.GmailID, m.ThreadID, m.SenderKey, v.SenderAddress, m.FromHeader, m.ToHeader, m.CcHeader,
       m.Subject, m.Snippet, m.MessageIDHeader, m.ReceivedDate, m.IsRead, m.EntityID,
       v.GmailClientInboxID, i.InboxName, v.IsSenderLevel, v.DomainInboxID, di.InboxName AS DomainInboxName,
       f.DueDate AS FollowUpDate, f.Note AS FollowUpNote,
       CAST(CASE WHEN b.AttachmentNames IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS HasAttachments,
       STUFF((SELECT ',' + CONVERT(VARCHAR(12), mf.GmailClientFlagID)
              FROM   dbo.GmailClientMessageFlag mf
              WHERE  mf.GmailClientMessageID = m.GmailClientMessageID
              ORDER BY mf.GmailClientFlagID
              FOR XML PATH('')), 1, 1, '') AS FlagIDs,
       0 AS LinkCount,   -- Story 5654: Echo has no record links (EntityGmailClientMessageXRef)
       CASE WHEN m.ThreadID IS NULL THEN 1
            ELSE (SELECT COUNT(*) FROM dbo.GmailClientMessage t
                  WHERE  t.ThreadID = m.ThreadID AND t.IsHidden = 0
                    AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = t.SenderKey AND r.Disposition = 'black'))
               + (SELECT COUNT(*) FROM dbo.GmailClientSentMessage s WHERE s.ThreadID = m.ThreadID AND s.IsHidden = 0)
       END AS ThreadCount
FROM   dbo.GmailClientMessage m
       JOIN dbo.vw_GmailClientMessageInbox v ON v.GmailClientMessageID = m.GmailClientMessageID
       LEFT JOIN dbo.GmailClientInbox i ON i.GmailClientInboxID = v.GmailClientInboxID
       LEFT JOIN dbo.GmailClientInbox di ON di.GmailClientInboxID = v.DomainInboxID
       LEFT JOIN dbo.GmailClientFollowUp f ON f.GmailClientMessageID = m.GmailClientMessageID AND f.IsDone = 0
       LEFT JOIN dbo.GmailClientMessageBody b ON b.GmailClientMessageID = m.GmailClientMessageID
WHERE  m.GmailClientMessageID = @prmGmailClientMessageID
  AND  m.IsHidden = 0
  -- Not blacklisted: a whitelisted sender, or one not decided on yet (the Undecided tab opens it).
  AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')

GO
