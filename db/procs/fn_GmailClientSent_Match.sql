SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_GmailClientSent_Match') IS NOT NULL DROP FUNCTION dbo.fn_GmailClientSent_Match
GO
-- fn_GmailClientSent_Match.sql
--
-- Gmail Client Sent mail (Story 5608) -- the ONE definition of which sent messages the Sent tab and
-- a search show. stp_GmailClientSent_ListPage and stp_GmailClientSent_Counts both read through it,
-- so the list and its count never disagree. Inline: folded into the caller, OPTION (RECOMPILE) kept.
--
-- The caller validates and normalizes; every text parameter is a ready LIKE pattern or NULL.
--   @prmW1..W6        the search box's words: EVERY one must appear in To, Cc, subject or preview
--   @prmTo / @prmSubject / @prmWords   the custom search fields and to: subject:
--   @prmAfter         on or after; @prmBefore the first moment NOT included
--   @prmReply         all | waiting (no reply yet) | replied
--   @prmHasAttachment NULL either, 1 with, 0 without (has:attachment)
--
-- Deleted (hidden) sent mail is left out. ReplyCount / LastReplyDate come from
-- vw_GmailClientSentReply.
--
-- Story 5654: echoed from Leonardo for Echo's Email Client, unchanged. Its FILE lives in db\procs,
-- not db\functions: it reads vw_GmailClientSentReply, and Echo's installer runs functions before
-- views, so it must be created after the views (procs run after views; fn_ sorts before stp_).

CREATE FUNCTION dbo.fn_GmailClientSent_Match (
    @prmW1            NVARCHAR(210),
    @prmW2            NVARCHAR(210),
    @prmW3            NVARCHAR(210),
    @prmW4            NVARCHAR(210),
    @prmW5            NVARCHAR(210),
    @prmW6            NVARCHAR(210),
    @prmTo            NVARCHAR(210),
    @prmSubject       NVARCHAR(210),
    @prmWords         NVARCHAR(210),
    @prmAfter         DATETIME,
    @prmBefore        DATETIME,
    @prmReply         VARCHAR(8),
    @prmHasAttachment BIT
)
RETURNS TABLE
AS
RETURN
SELECT  s.GmailClientSentMessageID,
        s.SentDate,
        s.FollowUpDate,
        ISNULL(r.ReplyCount, 0) AS ReplyCount,
        r.LastReplyDate,
        CAST(CASE WHEN b.AttachmentNames IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS HasAttachments
FROM    dbo.GmailClientSentMessage s
        LEFT JOIN dbo.vw_GmailClientSentReply r ON r.GmailClientSentMessageID = s.GmailClientSentMessageID
        LEFT JOIN dbo.GmailClientSentMessageBody b ON b.GmailClientSentMessageID = s.GmailClientSentMessageID
WHERE   s.IsHidden = 0
  AND   (@prmW1 IS NULL OR s.ToHeader LIKE @prmW1 OR s.CcHeader LIKE @prmW1 OR s.Subject LIKE @prmW1 OR s.Snippet LIKE @prmW1)
  AND   (@prmW2 IS NULL OR s.ToHeader LIKE @prmW2 OR s.CcHeader LIKE @prmW2 OR s.Subject LIKE @prmW2 OR s.Snippet LIKE @prmW2)
  AND   (@prmW3 IS NULL OR s.ToHeader LIKE @prmW3 OR s.CcHeader LIKE @prmW3 OR s.Subject LIKE @prmW3 OR s.Snippet LIKE @prmW3)
  AND   (@prmW4 IS NULL OR s.ToHeader LIKE @prmW4 OR s.CcHeader LIKE @prmW4 OR s.Subject LIKE @prmW4 OR s.Snippet LIKE @prmW4)
  AND   (@prmW5 IS NULL OR s.ToHeader LIKE @prmW5 OR s.CcHeader LIKE @prmW5 OR s.Subject LIKE @prmW5 OR s.Snippet LIKE @prmW5)
  AND   (@prmW6 IS NULL OR s.ToHeader LIKE @prmW6 OR s.CcHeader LIKE @prmW6 OR s.Subject LIKE @prmW6 OR s.Snippet LIKE @prmW6)
  AND   (@prmTo      IS NULL OR s.ToHeader LIKE @prmTo OR s.CcHeader LIKE @prmTo OR s.BccHeader LIKE @prmTo)
  AND   (@prmSubject IS NULL OR s.Subject LIKE @prmSubject)
  AND   (@prmWords   IS NULL OR s.Subject LIKE @prmWords OR s.Snippet LIKE @prmWords)
  AND   (@prmAfter   IS NULL OR s.SentDate >= @prmAfter)
  AND   (@prmBefore  IS NULL OR s.SentDate <  @prmBefore)
  AND   (@prmReply = 'all' OR (@prmReply = 'waiting' AND r.GmailClientSentMessageID IS NULL)
                           OR (@prmReply = 'replied' AND r.GmailClientSentMessageID IS NOT NULL))
  AND   (@prmHasAttachment IS NULL
         OR (@prmHasAttachment = 1 AND b.AttachmentNames IS NOT NULL)
         OR (@prmHasAttachment = 0 AND b.AttachmentNames IS NULL));

GO
