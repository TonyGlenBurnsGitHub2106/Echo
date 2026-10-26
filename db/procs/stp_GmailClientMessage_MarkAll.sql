SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_MarkAll') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_MarkAll
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_MarkAll
    @prmScope         VARCHAR(12)    = 'all',  -- all | followup | inbox | unclassified | undecided | flagged
    @prmInboxID       INT            = NULL,   -- with scope inbox

    @prmSearch        NVARCHAR(1000) = NULL,   -- the same filters stp_GmailClientMessage_ListPage takes
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
    @prmIsRead        BIT            = 1       -- 1 mark every one read, 0 unread
AS
/*

Gmail Client (Story 5569): Mark all -- every message the tab shows for the current filters (not
just the page that is loaded), marked read or unread in Leonardo only. Gmail's own read flag is
not touched. Which messages is fn_GmailClientMessage_Match, the same definition the list and the
tab counts read (Story 5608), so Mark all can never reach a message the tab does not show.
Returns Updated, the number of messages that changed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessage_MarkAll @prmScope = 'unclassified', @prmIsRead = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Stewart      New (Story 5569, Tony's comment: mark all options).
2026-10-04  Felix        The Undecided scope (senders with no Mail Triage rule).
2026-10-08  Felix        Story 5608: reads fn_GmailClientMessage_Match; the Flagged scope;
                         every search word must match; has:attachment and flag:.

2026-10-08  Felix        Story 5608 QA: @prmFlagIDs, the flag filter (any of the chosen flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

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
IF @scope NOT IN ('all', 'followup', 'inbox', 'unclassified', 'undecided', 'flagged') OR (@scope = 'inbox' AND @prmInboxID IS NULL)
BEGIN
    RAISERROR('Choose a tab.', 16, 1)
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
DECLARE @to1     BIT           = ISNULL(@prmIsRead, 1)
DECLARE @n       INT

BEGIN TRAN

UPDATE m
SET    IsRead = @to1, ModifiedDate = GETDATE()
FROM   dbo.GmailClientMessage m
       JOIN dbo.fn_GmailClientMessage_Match(@scope, @prmInboxID, @w1, @w2, @w3, @w4, @w5, @w6,
                                            @from, @to, @subject, @words, @after, @before, @read,
                                            @prmHasAttachment, @flag, @flagIds) t
         ON t.GmailClientMessageID = m.GmailClientMessageID
WHERE  m.IsRead <> @to1
OPTION (RECOMPILE)

SET @n = @@ROWCOUNT

COMMIT

SELECT @n AS Updated

GO
