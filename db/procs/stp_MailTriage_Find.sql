SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriage_Find') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriage_Find
GO
CREATE PROCEDURE dbo.stp_MailTriage_Find
    @prmSearch  NVARCHAR(254),
    @prmMaxRows INT = 50
AS

/*

Mail Triage: the "Find a sender" box at the top of the page (2026-10-07). Tony types a domain, a
piece of one, or a whole address, and sees every sender it touches and where that sender stands:
on a list (white | junk | black) or waiting for a decision (pending). A Delete (black) sender never
shows in the Gmail Client, so this is the one place to positively locate it.

A row matches two ways:
    contains -- the sender key holds the typed text          ("nextdoor" finds nextdoor.com)
    covers   -- the typed text is an address or sub-domain the key rules
                ("bob@mail.nextdoor.com" finds the nextdoor.com rule)

Covering rows come first (they are the answer to "what happens to this sender"), then rules
before waiting senders, then alphabetical. @prmMaxRows + 1 rows at most; the caller trims and
reports "more". Both tables hold hundreds of rows, not millions, so the LIKE is cheap.

TEST
----

    EXEC dbo.stp_MailTriage_Find N'nextdoor', 50
    EXEC dbo.stp_MailTriage_Find N'bob@mail.nextdoor.com', 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-07  Felix        New (Mail Triage sender search).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no
                         MailTriagePending table: only rules are found (the 'pending' half of the union
                         is dropped). Same result columns.

*/

SET NOCOUNT ON

DECLARE @q   NVARCHAR(254) = NULLIF(LOWER(LTRIM(RTRIM(@prmSearch))), N'')
DECLARE @max INT           = ISNULL(NULLIF(@prmMaxRows, 0), 50)

IF @q IS NULL
BEGIN
    -- Nothing typed: the same columns, no rows.
    SELECT CAST(NULL AS VARCHAR(10)) AS Source, CAST(NULL AS INT) AS MailTriageRuleID,
           CAST(NULL AS INT) AS MailTriagePendingID, CAST(NULL AS NVARCHAR(254)) AS SenderKey,
           CAST(NULL AS VARCHAR(10)) AS KeyKind, CAST(NULL AS VARCHAR(10)) AS Disposition,
           CAST(NULL AS BIT) AS IsProtected, CAST(NULL AS VARCHAR(100)) AS GmailFilterID,
           CAST(NULL AS VARCHAR(10)) AS FilterMode, CAST(NULL AS NVARCHAR(500)) AS Note,
           CAST(NULL AS INT) AS MessageCount, CAST(NULL AS NVARCHAR(320)) AS SampleFrom,
           CAST(NULL AS NVARCHAR(300)) AS SampleSubject, CAST(NULL AS DATETIME) AS CreatedDate,
           CAST(NULL AS DATETIME) AS ModifiedDate, CAST(NULL AS DATETIME) AS FirstSeenDate,
           CAST(NULL AS DATETIME) AS LastSeenDate, CAST(NULL AS BIT) AS IsCovering
    WHERE  1 = 0
    RETURN
END

SELECT TOP (@max + 1) x.*
FROM (
    SELECT 'rule' AS Source, r.MailTriageRuleID, CAST(NULL AS INT) AS MailTriagePendingID,
           r.SenderKey, r.KeyKind, r.Disposition, r.IsProtected, r.GmailFilterID, r.FilterMode, r.Note,
           CAST(NULL AS INT) AS MessageCount, CAST(NULL AS NVARCHAR(320)) AS SampleFrom,
           CAST(NULL AS NVARCHAR(300)) AS SampleSubject, r.CreatedDate, r.ModifiedDate,
           CAST(NULL AS DATETIME) AS FirstSeenDate, CAST(NULL AS DATETIME) AS LastSeenDate,
           CAST(CASE WHEN @q = r.SenderKey OR @q LIKE N'%.' + r.SenderKey OR @q LIKE N'%@' + r.SenderKey
                     THEN 1 ELSE 0 END AS BIT) AS IsCovering
    FROM   dbo.MailTriageRule r
    WHERE  r.SenderKey LIKE N'%' + @q + N'%'
       OR  @q = r.SenderKey OR @q LIKE N'%.' + r.SenderKey OR @q LIKE N'%@' + r.SenderKey

    -- Story 5654: Echo has no MailTriagePending table, so the 'pending' half of Leonardo's UNION ALL
    -- (senders waiting for a decision) is gone; only rules are found. The columns are unchanged.
) x
ORDER BY x.IsCovering DESC, CASE x.Source WHEN 'rule' THEN 0 ELSE 1 END, x.SenderKey

GO
