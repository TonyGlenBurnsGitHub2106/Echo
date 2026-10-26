SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_Counts
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_Counts
    @prmSearch NVARCHAR(1000) = NULL
AS

/*

Gmail Client Drafts (Story 5620): how many open drafts match the search box (Cnt) -- the Drafts
tab's number. The same WHERE as stp_GmailClientDraft_ListPage: keep the two in step.

TEST
----

    EXEC dbo.stp_GmailClientDraft_Counts

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

SELECT COUNT(*) AS Cnt
FROM   dbo.GmailClientDraft d
WHERE  d.IsOpen = 1
  AND  (@w1 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w1)
  AND  (@w2 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w2)
  AND  (@w3 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w3)
  AND  (@w4 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w4)
  AND  (@w5 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w5)
  AND  (@w6 IS NULL OR ISNULL(d.ToHeader, N'') + N' ' + ISNULL(d.CcHeader, N'') + N' ' + ISNULL(d.Subject, N'') + N' ' + ISNULL(d.Snippet, N'') LIKE @w6)
OPTION (RECOMPILE)

GO
