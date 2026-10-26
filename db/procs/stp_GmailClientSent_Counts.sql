SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_Counts
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_Counts
    @prmSearch        NVARCHAR(1000) = NULL,   -- the search box's words, one per line: every one must match
    @prmTo            NVARCHAR(200)  = NULL,   -- custom search fields, and to: subject:
    @prmSubject       NVARCHAR(200)  = NULL,
    @prmWords         NVARCHAR(200)  = NULL,
    @prmAfter         VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmBefore        VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmReplyState    VARCHAR(8)     = 'all',  -- all | waiting (no reply yet) | replied
    @prmHasAttachment BIT            = NULL
AS

/*

Gmail Client Sent mail (Story 5596): the Sent tab's numbers for the same filters as
stp_GmailClientSent_ListPage -- both read fn_GmailClientSent_Match (Story 5608), so they cannot
disagree: Cnt (shown), Waiting (no reply yet) and Overdue (an open follow-up day already past).
One row.

TEST
----

    EXEC dbo.stp_GmailClientSent_Counts

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-08  Felix        Story 5608: reads fn_GmailClientSent_Match; every search word must
                         match; has:attachment.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

IF dbo.fn_IsoDate_IsValid(@prmAfter) = 0 OR dbo.fn_IsoDate_IsValid(@prmBefore) = 0
BEGIN
    RAISERROR('Dates are written yyyy-mm-dd.', 16, 1)
    RETURN
END

DECLARE @reply VARCHAR(8) = LOWER(ISNULL(NULLIF(LTRIM(RTRIM(@prmReplyState)), ''), 'all'))
IF @reply NOT IN ('all', 'waiting', 'replied')
BEGIN
    RAISERROR('Show all sent mail, the mail waiting for a reply, or the mail that has one.', 16, 1)
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
DECLARE @to      NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmTo)), N'') + '%'
DECLARE @subject NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmSubject)), N'') + '%'
DECLARE @words   NVARCHAR(210) = '%' + NULLIF(LTRIM(RTRIM(@prmWords)), N'') + '%'
DECLARE @after   DATETIME      = CASE WHEN NULLIF(@prmAfter, '') IS NULL THEN NULL ELSE CONVERT(DATETIME, @prmAfter, 120) END
DECLARE @before  DATETIME      = CASE WHEN NULLIF(@prmBefore, '') IS NULL THEN NULL ELSE DATEADD(DAY, 1, CONVERT(DATETIME, @prmBefore, 120)) END
DECLARE @today   DATE          = CONVERT(DATE, GETDATE())

SELECT COUNT(*) AS Cnt,
       ISNULL(SUM(CASE WHEN t.ReplyCount = 0 THEN 1 ELSE 0 END), 0) AS Waiting,
       ISNULL(SUM(CASE WHEN t.FollowUpDate < @today THEN 1 ELSE 0 END), 0) AS Overdue
FROM   dbo.fn_GmailClientSent_Match(@w1, @w2, @w3, @w4, @w5, @w6, @to, @subject, @words, @after, @before,
                                       @reply, @prmHasAttachment) t
OPTION (RECOMPILE)

GO
