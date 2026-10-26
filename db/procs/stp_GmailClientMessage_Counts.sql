SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_Counts
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_Counts
    @prmSearch        NVARCHAR(1000) = NULL,   -- the search box's words, one per line
    @prmFrom          NVARCHAR(200)  = NULL,   -- custom search fields
    @prmTo            NVARCHAR(200)  = NULL,
    @prmSubject       NVARCHAR(200)  = NULL,
    @prmWords         NVARCHAR(200)  = NULL,
    @prmAfter         VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmBefore        VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmReadState     VARCHAR(6)     = 'all',  -- all | unread | read
    @prmHasAttachment BIT            = NULL,
    @prmFlag          NVARCHAR(60)   = NULL,
    @prmFlagIDs       VARCHAR(400)   = NULL
AS

/*

Gmail Client (Story 5569): the tab counts for the same filters the list uses. Returns Bucket
(all | followup | flagged | unclassified | undecided | inbox), BucketKey (the GmailClientInboxID for
an inbox, else NULL), Cnt, Unread and Overdue (open follow-ups due before today; 0 for every
bucket but followup). Every inbox gets a row, even an empty one. Which messages count is
fn_GmailClientMessage_Match (scope 'counts': whitelisted and undecided mail together), the same
definition the list reads, so a tab's count and its list never disagree.

TEST
----

    EXEC dbo.stp_GmailClientMessage_Counts
    EXEC dbo.stp_GmailClientMessage_Counts @prmReadState = 'unread'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-01  Stewart      Read state filter, the followup bucket and Overdue (Story 5569,
                         Tony's comment).
2026-10-04  Felix        The undecided bucket: mail from senders with no Mail Triage rule.
2026-10-08  Felix        Story 5608: reads fn_GmailClientMessage_Match; the flagged bucket;
                         every search word must match; has:attachment and flag:; inboxes
                         resolved with sender-level overrides.

2026-10-08  Felix        Story 5608 QA: @prmFlagIDs, the flag filter (any of the chosen flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

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
DECLARE @today   DATE          = CAST(GETDATE() AS DATE)

DECLARE @v TABLE (InboxID INT NULL, IsRead BIT NOT NULL, FollowUpDate DATE NULL, IsUndecided BIT NOT NULL, IsFlagged BIT NOT NULL)
INSERT INTO @v (InboxID, IsRead, FollowUpDate, IsUndecided, IsFlagged)
SELECT t.GmailClientInboxID, t.IsRead, t.FollowUpDate, t.IsUndecided,
       CASE WHEN t.FirstFlagID IS NOT NULL THEN 1 ELSE 0 END
FROM   dbo.fn_GmailClientMessage_Match('counts', NULL, @w1, @w2, @w3, @w4, @w5, @w6,
                                       @from, @to, @subject, @words, @after, @before, @read,
                                       @prmHasAttachment, @flag, @flagIds) t
OPTION (RECOMPILE)

SELECT 'all' AS Bucket, CAST(NULL AS INT) AS BucketKey, COUNT(*) AS Cnt,
       ISNULL(SUM(CASE WHEN IsRead = 0 THEN 1 ELSE 0 END), 0) AS Unread, 0 AS Overdue
FROM   @v WHERE IsUndecided = 0
UNION ALL
SELECT 'followup', NULL, COUNT(*), ISNULL(SUM(CASE WHEN IsRead = 0 THEN 1 ELSE 0 END), 0),
       ISNULL(SUM(CASE WHEN FollowUpDate < @today THEN 1 ELSE 0 END), 0)
FROM   @v WHERE FollowUpDate IS NOT NULL AND IsUndecided = 0
UNION ALL
SELECT 'flagged', NULL, COUNT(*), ISNULL(SUM(CASE WHEN IsRead = 0 THEN 1 ELSE 0 END), 0), 0
FROM   @v WHERE IsFlagged = 1 AND IsUndecided = 0
UNION ALL
SELECT 'unclassified', NULL, COUNT(*), ISNULL(SUM(CASE WHEN IsRead = 0 THEN 1 ELSE 0 END), 0), 0
FROM   @v WHERE InboxID IS NULL AND IsUndecided = 0
UNION ALL
SELECT 'undecided', NULL, COUNT(*), ISNULL(SUM(CASE WHEN IsRead = 0 THEN 1 ELSE 0 END), 0), 0
FROM   @v WHERE IsUndecided = 1
UNION ALL
SELECT 'inbox', i.GmailClientInboxID, COUNT(v.IsRead), ISNULL(SUM(CASE WHEN v.IsRead = 0 THEN 1 ELSE 0 END), 0), 0
FROM   dbo.GmailClientInbox i LEFT JOIN @v v ON v.InboxID = i.GmailClientInboxID AND v.IsUndecided = 0
GROUP BY i.GmailClientInboxID

GO
