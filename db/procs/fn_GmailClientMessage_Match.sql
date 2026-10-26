SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_GmailClientMessage_Match') IS NOT NULL DROP FUNCTION dbo.fn_GmailClientMessage_Match
GO
-- fn_GmailClientMessage_Match.sql
--
-- Gmail Client (Story 5608) -- the ONE definition of which inbox messages a tab and a search show.
-- stp_GmailClientMessage_ListPage, _Counts, _MarkAll and stp_GmailClientThread_ListPage all read
-- through it, so a list, its tab counts, Mark all and the conversation view can never disagree.
-- An inline function: SQL Server folds it into the caller's query, which keeps OPTION (RECOMPILE).
--
-- The caller validates and normalizes; this only matches. Every text parameter is a ready LIKE
-- pattern ('%word%') or NULL for "not filtering on this".
--
--   @prmScope   all | followup | inbox | unclassified | undecided | flagged | counts
--               (counts = whitelisted AND undecided mail together, with IsUndecided, for the tab counts)
--   @prmW1..W6  the search box's words: EVERY one must appear in the sender, subject or preview
--   @prmFrom / @prmTo / @prmSubject / @prmWords   the custom search fields and from: to: subject:
--   @prmAfter   on or after; @prmBefore the first moment NOT included
--   @prmRead    all | unread | read
--   @prmHasAttachment  NULL either, 1 with attachments, 0 without (has:attachment)
--   @prmFlag    a flag name or the start of one (flag:tax finds Taxes); NULL = any
--   @prmFlagIDs ',3,7,' -- the flag filter on the main page: mail with ANY of these flags; NULL = any
--
-- Shows a message only while it is not hidden AND its sender is whitelisted in Mail Triage --
-- except the undecided scope (no rule at all) and counts (both). Which inbox it is in comes from
-- vw_GmailClientMessageInbox (a sender-level override, else the domain). FirstFlag* is the
-- message's first flag in flag order, which the Flagged tab groups by.
--
-- Story 5654: echoed from Leonardo for Echo's Email Client, unchanged. Its FILE lives in db\procs,
-- not db\functions: it reads vw_GmailClientMessageInbox, and Echo's installer runs functions
-- before views, so it must be created after the views (procs run after views; fn_ sorts before
-- stp_).

CREATE FUNCTION dbo.fn_GmailClientMessage_Match (
    @prmScope         VARCHAR(12),
    @prmInboxID       INT,
    @prmW1            NVARCHAR(210),
    @prmW2            NVARCHAR(210),
    @prmW3            NVARCHAR(210),
    @prmW4            NVARCHAR(210),
    @prmW5            NVARCHAR(210),
    @prmW6            NVARCHAR(210),
    @prmFrom          NVARCHAR(210),
    @prmTo            NVARCHAR(210),
    @prmSubject       NVARCHAR(210),
    @prmWords         NVARCHAR(210),
    @prmAfter         DATETIME,
    @prmBefore        DATETIME,
    @prmRead          VARCHAR(6),
    @prmHasAttachment BIT,
    @prmFlag          NVARCHAR(62),
    @prmFlagIDs       VARCHAR(402)
)
RETURNS TABLE
AS
RETURN
SELECT  m.GmailClientMessageID,
        m.ReceivedDate,
        m.IsRead,
        ISNULL(m.ThreadID, m.GmailID) AS ThreadKey,
        v.SenderAddress,
        v.GmailClientInboxID,
        v.IsSenderLevel,
        t.IsUndecided,
        f.GmailClientFollowUpID,
        f.DueDate AS FollowUpDate,
        f.Note    AS FollowUpNote,
        CAST(CASE WHEN b.AttachmentNames IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS HasAttachments,
        ff.GmailClientFlagID AS FirstFlagID,
        ff.FlagName          AS FirstFlagName,
        ff.SortOrder         AS FirstFlagSort
FROM    dbo.GmailClientMessage m
        JOIN dbo.vw_GmailClientMessageInbox v ON v.GmailClientMessageID = m.GmailClientMessageID
        CROSS APPLY (SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey)
                                 THEN 0 ELSE 1 END AS IsUndecided,
                            CASE WHEN EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'white')
                                 THEN 1 ELSE 0 END AS IsWhite) t
        LEFT JOIN dbo.GmailClientFollowUp f ON f.GmailClientMessageID = m.GmailClientMessageID AND f.IsDone = 0
        LEFT JOIN dbo.GmailClientMessageBody b ON b.GmailClientMessageID = m.GmailClientMessageID
        OUTER APPLY (SELECT TOP 1 fl.GmailClientFlagID, fl.FlagName, fl.SortOrder
                     FROM   dbo.GmailClientMessageFlag mf
                            JOIN dbo.GmailClientFlag fl ON fl.GmailClientFlagID = mf.GmailClientFlagID
                     WHERE  mf.GmailClientMessageID = m.GmailClientMessageID
                     ORDER BY fl.SortOrder, fl.FlagName) ff
WHERE   m.IsHidden = 0
  AND   ((@prmScope = 'counts' AND (t.IsWhite = 1 OR t.IsUndecided = 1))
         OR (@prmScope = 'undecided' AND t.IsUndecided = 1)
         OR (@prmScope NOT IN ('counts', 'undecided') AND t.IsWhite = 1))
  AND   (@prmW1 IS NULL OR m.FromHeader LIKE @prmW1 OR m.Subject LIKE @prmW1 OR m.Snippet LIKE @prmW1 OR m.SenderKey LIKE @prmW1)
  AND   (@prmW2 IS NULL OR m.FromHeader LIKE @prmW2 OR m.Subject LIKE @prmW2 OR m.Snippet LIKE @prmW2 OR m.SenderKey LIKE @prmW2)
  AND   (@prmW3 IS NULL OR m.FromHeader LIKE @prmW3 OR m.Subject LIKE @prmW3 OR m.Snippet LIKE @prmW3 OR m.SenderKey LIKE @prmW3)
  AND   (@prmW4 IS NULL OR m.FromHeader LIKE @prmW4 OR m.Subject LIKE @prmW4 OR m.Snippet LIKE @prmW4 OR m.SenderKey LIKE @prmW4)
  AND   (@prmW5 IS NULL OR m.FromHeader LIKE @prmW5 OR m.Subject LIKE @prmW5 OR m.Snippet LIKE @prmW5 OR m.SenderKey LIKE @prmW5)
  AND   (@prmW6 IS NULL OR m.FromHeader LIKE @prmW6 OR m.Subject LIKE @prmW6 OR m.Snippet LIKE @prmW6 OR m.SenderKey LIKE @prmW6)
  AND   (@prmFrom    IS NULL OR m.FromHeader LIKE @prmFrom OR m.SenderKey LIKE @prmFrom)
  AND   (@prmTo      IS NULL OR m.ToHeader LIKE @prmTo OR m.CcHeader LIKE @prmTo)
  AND   (@prmSubject IS NULL OR m.Subject LIKE @prmSubject)
  AND   (@prmWords   IS NULL OR m.Subject LIKE @prmWords OR m.Snippet LIKE @prmWords)
  AND   (@prmAfter   IS NULL OR m.ReceivedDate >= @prmAfter)
  AND   (@prmBefore  IS NULL OR m.ReceivedDate <  @prmBefore)
  AND   (@prmRead = 'all' OR (@prmRead = 'unread' AND m.IsRead = 0) OR (@prmRead = 'read' AND m.IsRead = 1))
  AND   (@prmHasAttachment IS NULL
         OR (@prmHasAttachment = 1 AND b.AttachmentNames IS NOT NULL)
         OR (@prmHasAttachment = 0 AND b.AttachmentNames IS NULL))
  AND   (@prmFlag IS NULL OR EXISTS (SELECT 1
                                     FROM   dbo.GmailClientMessageFlag mf
                                            JOIN dbo.GmailClientFlag fl ON fl.GmailClientFlagID = mf.GmailClientFlagID
                                     WHERE  mf.GmailClientMessageID = m.GmailClientMessageID
                                       AND  fl.FlagName LIKE @prmFlag))
  AND   (@prmFlagIDs IS NULL OR EXISTS (SELECT 1
                                        FROM   dbo.GmailClientMessageFlag mf2
                                        WHERE  mf2.GmailClientMessageID = m.GmailClientMessageID
                                          AND  @prmFlagIDs LIKE '%,' + CAST(mf2.GmailClientFlagID AS VARCHAR(12)) + ',%'))
  AND   (@prmScope IN ('all', 'undecided', 'counts')
         OR (@prmScope = 'followup' AND f.GmailClientFollowUpID IS NOT NULL)
         OR (@prmScope = 'inbox' AND v.GmailClientInboxID = @prmInboxID)
         OR (@prmScope = 'unclassified' AND v.GmailClientInboxID IS NULL)
         OR (@prmScope = 'flagged' AND ff.GmailClientFlagID IS NOT NULL));

GO
