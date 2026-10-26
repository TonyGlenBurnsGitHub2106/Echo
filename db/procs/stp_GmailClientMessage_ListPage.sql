SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_ListPage') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_ListPage
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_ListPage
    @prmScope         VARCHAR(12)    = 'all',  -- all | followup | inbox | unclassified | undecided | flagged
    @prmInboxID       INT            = NULL,   -- with scope inbox

    @prmSearch        NVARCHAR(1000) = NULL,   -- the search box's words, one per line: every one must match
    @prmFrom          NVARCHAR(200)  = NULL,   -- custom search fields, and from: to: subject:
    @prmTo            NVARCHAR(200)  = NULL,
    @prmSubject       NVARCHAR(200)  = NULL,
    @prmWords         NVARCHAR(200)  = NULL,
    @prmAfter         VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmBefore        VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmReadState     VARCHAR(6)     = 'all',  -- all | unread | read
    @prmHasAttachment BIT            = NULL,   -- has:attachment
    @prmFlag          NVARCHAR(60)   = NULL,   -- flag:taxes (the start of a flag's name)
    @prmFlagIDs       VARCHAR(400)   = NULL,   -- the flag filter: flag ids, comma separated (any of them)
    @prmOffset        INT            = 0,
    @prmPageSize      INT            = 50
AS

/*

Gmail Client (Story 5569): one page of messages, newest first, for a tab (All, Follow up, Flagged,
one inbox, Unclassified, Undecided), the search box and Custom search, and a read state. Which
messages match is fn_GmailClientMessage_Match -- shared with the tab counts, Mark all and the
conversation view (Story 5608). Follow up: earliest due first. Flagged: grouped by each
message's first flag, in flag order, newest first within a flag.

Every row carries its inbox (a sender-level override, else its domain's -- vw_GmailClientMessageInbox),
its open follow-up, HasAttachments, its flags (FlagIDs, comma separated, for the page to name and
colour) and how many records it is linked to (LinkCount). Search is LIKE over the short header
columns of one person's mailbox (thousands of rows, no MAX columns), OPTION (RECOMPILE) so the
catch-all parameters do not pin one plan.

TEST
----

    EXEC dbo.stp_GmailClientMessage_ListPage @prmScope = 'all', @prmSearch = N'kohl'
    EXEC dbo.stp_GmailClientMessage_ListPage @prmScope = 'flagged'
    EXEC dbo.stp_GmailClientMessage_ListPage @prmHasAttachment = 1, @prmFrom = N'greg'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-01  Stewart      Read state filter, the Follow up scope and the follow-up columns
                         (Story 5569, Tony's comment).
2026-10-01  Felix        Page size cap 201 (the service's look-ahead row), so Load all
                         goes past the first 200 (Story 5569).
2026-10-04  Felix        The Undecided scope (senders with no rule); HasAttachments.
2026-10-08  Felix        Story 5608: matching moved to fn_GmailClientMessage_Match; the search
                         box's words each must match; has:attachment; flags (the Flagged scope,
                         flag:, FlagIDs); sender-level inboxes; LinkCount, SenderAddress.

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

-- Up to 201: the service shows pages of at most 200 and asks for one row more to know whether
-- another page follows. Capping at 200 here made every 200-row page say "no more" (Load all
-- stopped after one page).
DECLARE @size  INT = CASE WHEN ISNULL(@prmPageSize, 0) <= 0 THEN 50 WHEN @prmPageSize > 201 THEN 201 ELSE @prmPageSize END
DECLARE @off   INT = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END

;WITH p AS (
    SELECT t.*
    FROM   dbo.fn_GmailClientMessage_Match(@scope, @prmInboxID, @w1, @w2, @w3, @w4, @w5, @w6,
                                           @from, @to, @subject, @words, @after, @before, @read,
                                           @prmHasAttachment, @flag, @flagIds) t
    ORDER BY CASE WHEN @scope = 'followup' THEN t.FollowUpDate END ASC,
             CASE WHEN @scope = 'flagged' THEN t.FirstFlagSort END ASC,
             CASE WHEN @scope = 'flagged' THEN t.FirstFlagName END ASC,
             t.ReceivedDate DESC, t.GmailClientMessageID DESC
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
       0 AS LinkCount   -- Story 5654: Echo has no record links (EntityGmailClientMessageXRef)
FROM   p
       JOIN dbo.GmailClientMessage m ON m.GmailClientMessageID = p.GmailClientMessageID
       LEFT JOIN dbo.GmailClientInbox i ON i.GmailClientInboxID = p.GmailClientInboxID
ORDER BY CASE WHEN @scope = 'followup' THEN p.FollowUpDate END ASC,
         CASE WHEN @scope = 'flagged' THEN p.FirstFlagSort END ASC,
         CASE WHEN @scope = 'flagged' THEN p.FirstFlagName END ASC,
         p.ReceivedDate DESC, p.GmailClientMessageID DESC
OPTION (RECOMPILE)

GO
