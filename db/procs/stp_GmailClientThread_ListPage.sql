SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientThread_ListPage') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientThread_ListPage
GO
CREATE PROCEDURE dbo.stp_GmailClientThread_ListPage
    @prmScope         VARCHAR(12)    = 'all',  -- all | followup | inbox | unclassified | undecided | flagged
    @prmInboxID       INT            = NULL,   -- with scope inbox
    @prmSearch        NVARCHAR(1000) = NULL,   -- the search box's words, one per line
    @prmFrom          NVARCHAR(200)  = NULL,
    @prmTo            NVARCHAR(200)  = NULL,
    @prmSubject       NVARCHAR(200)  = NULL,
    @prmWords         NVARCHAR(200)  = NULL,
    @prmAfter         VARCHAR(10)    = NULL,
    @prmBefore        VARCHAR(10)    = NULL,
    @prmReadState     VARCHAR(6)     = 'all',
    @prmHasAttachment BIT            = NULL,
    @prmFlag          NVARCHAR(60)   = NULL,
    @prmFlagIDs       VARCHAR(400)   = NULL,
    @prmOffset        INT            = 0,
    @prmPageSize      INT            = 50
AS

/*

Gmail Client conversations (Story 5608): "A view of email threads ... If I send to Greg, then he to
me, then I to him, in a conversation, I should be able to review it as such." One page of
CONVERSATIONS instead of messages: the same tab, search and filters as
stp_GmailClientMessage_ListPage (both read fn_GmailClientMessage_Match), grouped by Gmail thread.
Each row is the newest matching message of its conversation -- the same columns the message list
returns -- plus:

    MatchCount    how many of the conversation's messages match the tab and search
    MatchUnread   how many of those are unread
    ThreadCount   every message of the conversation the client holds, received and sent
    SentCount     how many of those Tony sent (0 = nobody has written back yet... or he never replied)
    TotalThreads  how many conversations match in all, for "12 of 31 conversations"

Newest conversation first (Follow up: earliest due; Flagged: by flag). Mail with no thread id is a
conversation of one.

TEST
----

    EXEC dbo.stp_GmailClientThread_ListPage @prmScope = 'all'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, conversations).

2026-10-08  Felix        Story 5608 QA: @prmFlagIDs, the flag filter (any of the chosen flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no record
                         links (EntityGmailClientMessageXRef): LinkCount is always 0.

*/

SET NOCOUNT ON

IF dbo.fn_IsoDate_IsValid(@prmAfter) = 0 OR dbo.fn_IsoDate_IsValid(@prmBefore) = 0
BEGIN
    RAISERROR('Dates are written yyyy-mm-dd.', 16, 1)
    RETURN
END

DECLARE @read VARCHAR(6) = LOWER(ISNULL(NULLIF(LTRIM(RTRIM(@prmReadState)), ''), 'all'))
IF @read NOT IN ('all', 'unread', 'read')
BEGIN
    RAISERROR('Show all, unread or read mail.', 16, 1)
    RETURN
END

DECLARE @scope VARCHAR(12) = LOWER(ISNULL(NULLIF(LTRIM(RTRIM(@prmScope)), ''), 'all'))
IF @scope NOT IN ('all', 'followup', 'inbox', 'unclassified', 'undecided', 'flagged')
    SET @scope = 'all'

IF dbo.fn_GmailClientSearchTerm(@prmSearch, 7) IS NOT NULL
BEGIN
    RAISERROR('Search for at most six words or phrases at once.', 16, 1)
    RETURN
END

DECLARE @w1 NVARCHAR(210) = dbo.fn_GmailClientSearchTerm(@prmSearch, 1),
        @w2 NVARCHAR(210) = dbo.fn_GmailClientSearchTerm(@prmSearch, 2),
        @w3 NVARCHAR(210) = dbo.fn_GmailClientSearchTerm(@prmSearch, 3),
        @w4 NVARCHAR(210) = dbo.fn_GmailClientSearchTerm(@prmSearch, 4),
        @w5 NVARCHAR(210) = dbo.fn_GmailClientSearchTerm(@prmSearch, 5),
        @w6 NVARCHAR(210) = dbo.fn_GmailClientSearchTerm(@prmSearch, 6)
DECLARE @from    NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmFrom)), N'') + '%'
DECLARE @to      NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmTo)), N'') + '%'
DECLARE @subject NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmSubject)), N'') + '%'
DECLARE @words   NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmWords)), N'') + '%'
DECLARE @flag    NVARCHAR(62)  = NULLIF(LTRIM(RTRIM(@prmFlag)), N'') + '%'
-- The flag filter (Story 5608 QA): one or more flags, any of them -- ids only, made ',3,7,'.
IF @prmFlagIDs LIKE '%[^0-9, ]%'
BEGIN
    RAISERROR('The flag filter takes flag numbers only.', 16, 1)
    RETURN
END
DECLARE @flagIds VARCHAR(402) = CASE WHEN REPLACE(REPLACE(ISNULL(@prmFlagIDs, ''), ',', ''), ' ', '') = '' THEN NULL
                                     ELSE ',' + REPLACE(@prmFlagIDs, ' ', '') + ',' END
DECLARE @after   DATETIME      = CASE WHEN NULLIF(@prmAfter, '') IS NULL THEN NULL ELSE CONVERT(DATETIME, @prmAfter, 120) END
DECLARE @before  DATETIME      = CASE WHEN NULLIF(@prmBefore, '') IS NULL THEN NULL ELSE DATEADD(DAY, 1, CONVERT(DATETIME, @prmBefore, 120)) END
DECLARE @size    INT = CASE WHEN ISNULL(@prmPageSize, 0) <= 0 THEN 50 WHEN @prmPageSize > 201 THEN 201 ELSE @prmPageSize END
DECLARE @off     INT = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END

;WITH x AS (
    SELECT t.*,
           ROW_NUMBER() OVER (PARTITION BY t.ThreadKey ORDER BY t.ReceivedDate DESC, t.GmailClientMessageID DESC) AS rn,
           COUNT(*) OVER (PARTITION BY t.ThreadKey) AS MatchCount,
           SUM(CASE WHEN t.IsRead = 0 THEN 1 ELSE 0 END) OVER (PARTITION BY t.ThreadKey) AS MatchUnread
    FROM   dbo.fn_GmailClientMessage_Match(@scope, @prmInboxID, @w1, @w2, @w3, @w4, @w5, @w6,
                                           @from, @to, @subject, @words, @after, @before, @read,
                                           @prmHasAttachment, @flag, @flagIds) t
), y AS (
    SELECT x.*, COUNT(*) OVER () AS TotalThreads FROM x WHERE x.rn = 1
), p AS (
    SELECT y.*
    FROM   y
    ORDER BY CASE WHEN @scope = 'followup' THEN y.FollowUpDate END ASC,
             CASE WHEN @scope = 'flagged' THEN y.FirstFlagSort END ASC,
             CASE WHEN @scope = 'flagged' THEN y.FirstFlagName END ASC,
             y.ReceivedDate DESC, y.GmailClientMessageID DESC
    OFFSET @off ROWS FETCH NEXT @size ROWS ONLY
)
SELECT m.GmailClientMessageID, m.GmailID, m.ThreadID, m.SenderKey, p.SenderAddress, m.FromHeader, m.Subject, m.Snippet,
       m.ReceivedDate, m.IsRead, p.GmailClientInboxID, i.InboxName, p.IsSenderLevel,
       p.FollowUpDate, p.FollowUpNote, p.HasAttachments, p.FirstFlagID, p.FirstFlagName,
       STUFF((SELECT ',' + CONVERT(VARCHAR(12), mf.GmailClientFlagID)
              FROM   dbo.GmailClientMessageFlag mf
              WHERE  mf.GmailClientMessageID = m.GmailClientMessageID
              ORDER BY mf.GmailClientFlagID
              FOR XML PATH('')), 1, 1, '') AS FlagIDs,
       0 AS LinkCount,   -- Story 5654: Echo has no record links (EntityGmailClientMessageXRef)
       p.MatchCount, p.MatchUnread, p.TotalThreads,
       c.InboxCount + c.SentCount AS ThreadCount, c.SentCount
FROM   p
       JOIN dbo.GmailClientMessage m ON m.GmailClientMessageID = p.GmailClientMessageID
       LEFT JOIN dbo.GmailClientInbox i ON i.GmailClientInboxID = p.GmailClientInboxID
       CROSS APPLY (SELECT CASE WHEN m.ThreadID IS NULL THEN 1
                                ELSE (SELECT COUNT(*) FROM dbo.GmailClientMessage z
                                      WHERE  z.ThreadID = m.ThreadID AND z.IsHidden = 0
                                        AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r
                                                         WHERE r.SenderKey = z.SenderKey AND r.Disposition = 'black')) END AS InboxCount,
                           CASE WHEN m.ThreadID IS NULL THEN 0
                                ELSE (SELECT COUNT(*) FROM dbo.GmailClientSentMessage s
                                      WHERE  s.ThreadID = m.ThreadID AND s.IsHidden = 0) END AS SentCount) c
ORDER BY CASE WHEN @scope = 'followup' THEN p.FollowUpDate END ASC,
         CASE WHEN @scope = 'flagged' THEN p.FirstFlagSort END ASC,
         CASE WHEN @scope = 'flagged' THEN p.FirstFlagName END ASC,
         p.ReceivedDate DESC, p.GmailClientMessageID DESC
OPTION (RECOMPILE)

GO
