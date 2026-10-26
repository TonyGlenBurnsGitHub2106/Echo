SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientThread_Get') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientThread_Get
GO
CREATE PROCEDURE dbo.stp_GmailClientThread_Get
    @prmKind VARCHAR(5),   -- inbox | sent: which numbering @prmID is in
    @prmID   INT
AS

/*

Gmail Client conversations (Story 5608): every message of one Gmail conversation the client holds,
oldest first -- mail read in (Kind 'inbox') and mail Tony sent (Kind 'sent') together -- found from
any one of its messages. Headers and preview only (MessageDate is when it arrived or went); each
message's body is read when its piece of the conversation is opened. Hidden (deleted in the Gmail
Client) messages and mail from blacklisted senders are left out. A message with no thread id is a
conversation of one. No row when the starting message is not there.

TEST
----

    EXEC dbo.stp_GmailClientThread_Get 'inbox', 1
    EXEC dbo.stp_GmailClientThread_Get 'sent', 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, conversations).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

DECLARE @kind VARCHAR(5) = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))
DECLARE @thread VARCHAR(64), @found BIT = 0

IF @kind NOT IN ('inbox', 'sent')
BEGIN
    RAISERROR('A conversation starts from an inbox message or a sent one.', 16, 1)
    RETURN
END

IF @kind = 'sent'
    SELECT @thread = s.ThreadID, @found = 1
    FROM   dbo.GmailClientSentMessage s
    WHERE  s.GmailClientSentMessageID = @prmID AND s.IsHidden = 0
ELSE
    SELECT @thread = m.ThreadID, @found = 1
    FROM   dbo.GmailClientMessage m
    WHERE  m.GmailClientMessageID = @prmID AND m.IsHidden = 0
      AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')

SELECT x.Kind, x.ID, x.GmailID, x.SenderKey, x.FromHeader, x.ToHeader, x.CcHeader, x.Subject, x.Snippet,
       x.MessageDate, x.IsRead, x.HasAttachments
FROM (
    SELECT 'inbox' AS Kind, m.GmailClientMessageID AS ID, m.GmailID, m.SenderKey, m.FromHeader, m.ToHeader, m.CcHeader,
           m.Subject, m.Snippet, m.ReceivedDate AS MessageDate, m.IsRead,
           CAST(CASE WHEN b.AttachmentNames IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS HasAttachments
    FROM   dbo.GmailClientMessage m
           LEFT JOIN dbo.GmailClientMessageBody b ON b.GmailClientMessageID = m.GmailClientMessageID
    WHERE  @found = 1
      AND  m.IsHidden = 0
      AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')
      AND  ((@thread IS NOT NULL AND m.ThreadID = @thread)
            OR (@thread IS NULL AND @kind = 'inbox' AND m.GmailClientMessageID = @prmID))
    UNION ALL
    SELECT 'sent', s.GmailClientSentMessageID, s.GmailID, NULL, s.FromHeader, s.ToHeader, s.CcHeader,
           s.Subject, s.Snippet, s.SentDate, CAST(1 AS BIT),
           CAST(CASE WHEN b.AttachmentNames IS NOT NULL THEN 1 ELSE 0 END AS BIT)
    FROM   dbo.GmailClientSentMessage s
           LEFT JOIN dbo.GmailClientSentMessageBody b ON b.GmailClientSentMessageID = s.GmailClientSentMessageID
    WHERE  @found = 1
      AND  s.IsHidden = 0
      AND  ((@thread IS NOT NULL AND s.ThreadID = @thread)
            OR (@thread IS NULL AND @kind = 'sent' AND s.GmailClientSentMessageID = @prmID))
) x
ORDER BY x.MessageDate, x.Kind, x.ID

GO
