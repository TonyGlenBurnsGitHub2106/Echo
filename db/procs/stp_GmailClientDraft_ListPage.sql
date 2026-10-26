SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_ListPage') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_ListPage
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_ListPage
    @prmSearch   NVARCHAR(1000) = NULL,   -- the search box's words, one per line: every one must match
    @prmOffset   INT            = 0,
    @prmPageSize INT            = 50
AS

/*

Gmail Client Drafts (Story 5620): one page of the Drafts tab, the draft changed most recently
first. The search box's words are looked for in who it is to, the subject and the start of the
text (never the whole body -- the list never scans an NVARCHAR(MAX)). The same WHERE as
stp_GmailClientDraft_Counts: keep the two in step. No body in the rows; stp_GmailClientDraft_Get
reads it when a draft is opened.

TEST
----

    EXEC dbo.stp_GmailClientDraft_ListPage
    EXEC dbo.stp_GmailClientDraft_ListPage @prmSearch = N'lunch'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

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

-- Up to 201: the service shows pages of at most 200 and asks for one row more.
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) <= 0 THEN 50 WHEN @prmPageSize > 201 THEN 201 ELSE @prmPageSize END
DECLARE @off  INT = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END

SELECT d.GmailClientDraftID, d.Mode, d.SourceID, d.ToHeader, d.CcHeader, d.Subject, d.Snippet, d.AttachmentCount,
       d.GmailDraftID, d.ThreadID, d.IsFromGmailClient, d.PushedDate, d.CreatedDate, d.ModifiedDate
FROM   dbo.GmailClientDraft d
WHERE  d.IsOpen = 1
  AND  (@w1 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w1)
  AND  (@w2 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w2)
  AND  (@w3 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w3)
  AND  (@w4 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w4)
  AND  (@w5 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w5)
  AND  (@w6 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w6)
ORDER BY d.ModifiedDate DESC, d.GmailClientDraftID DESC
OFFSET @off ROWS FETCH NEXT @size ROWS ONLY
OPTION (RECOMPILE)

GO
