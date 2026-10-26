SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_ListPage') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_ListPage
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_ListPage
    @prmSearch        NVARCHAR(1000) = NULL,   -- the search box's words, one per line: every one must match
    @prmTo            NVARCHAR(200)  = NULL,   -- custom search fields, and to: subject:
    @prmSubject       NVARCHAR(200)  = NULL,
    @prmWords         NVARCHAR(200)  = NULL,
    @prmAfter         VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmBefore        VARCHAR(10)    = NULL,   -- yyyy-mm-dd, inclusive
    @prmReplyState    VARCHAR(8)     = 'all',  -- all | waiting (no reply yet) | replied
    @prmHasAttachment BIT            = NULL,
    @prmOffset        INT            = 0,
    @prmPageSize      INT            = 50
AS

/*

Gmail Client Sent mail (Story 5596): one page of the Sent tab, newest first, with the search box,
Custom search and a reply state. Which sent messages match is fn_GmailClientSent_Match, shared
with stp_GmailClientSent_Counts (Story 5608). Every row carries ReplyCount and LastReplyDate (from
vw_GmailClientSentReply -- mail read in from the same Gmail thread after it was sent), its open
follow-up and whether it has attachments. Deleted (hidden) sent mail is left out. OPTION
(RECOMPILE) so the catch-all parameters do not pin one plan.

TEST
----

    EXEC dbo.stp_GmailClientSent_ListPage @prmReplyState = 'waiting'
    EXEC dbo.stp_GmailClientSent_ListPage @prmHasAttachment = 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-08  Felix        Story 5608: reads fn_GmailClientSent_Match; every search word must
                         match; has:attachment.
2026-10-12  Felix        LinkCount: the records the sent message is linked to.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no record
                         links (EntityGmailClientSentXRef): LinkCount is always 0.

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

-- Up to 201: the service shows pages of at most 200 and asks for one row more (see the inbox ListPage).
DECLARE @size    INT = CASE WHEN ISNULL(@prmPageSize, 0) <= 0 THEN 50 WHEN @prmPageSize > 201 THEN 201 ELSE @prmPageSize END
DECLARE @off     INT = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END

SELECT s.GmailClientSentMessageID, s.GmailID, s.ThreadID, s.FromHeader, s.ToHeader, s.CcHeader, s.Subject, s.Snippet,
       s.SentDate, s.IsFromGmailClient, s.FollowUpDate, s.FollowUpNote,
       t.ReplyCount, t.LastReplyDate, t.HasAttachments,
       0 AS LinkCount   -- Story 5654: Echo has no record links (EntityGmailClientSentXRef)
FROM   dbo.fn_GmailClientSent_Match(@w1, @w2, @w3, @w4, @w5, @w6, @to, @subject, @words, @after, @before,
                                       @reply, @prmHasAttachment) t
       JOIN dbo.GmailClientSentMessage s ON s.GmailClientSentMessageID = t.GmailClientSentMessageID
ORDER BY t.SentDate DESC, t.GmailClientSentMessageID DESC
OFFSET @off ROWS FETCH NEXT @size ROWS ONLY
OPTION (RECOMPILE)

GO
