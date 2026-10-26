SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriage_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriage_Counts
GO
CREATE PROCEDURE dbo.stp_MailTriage_Counts
    @prmSearch NVARCHAR(254) = NULL
AS

/*

Mail Triage (Story 3561): the header counts -- senders waiting for a decision (and their
messages), whitelisted, junk, delete (code black), protected -- and how many rules match the current search
on each side, so the page can say "(12 of 44)" while a list is partly loaded.

TEST
----

    EXEC dbo.stp_MailTriage_Counts

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix        New (Story 3561, Mail Triage).
2026-10-06  Felix        Junk, MatchJunk, and JunkReady -- 1 once the table accepts junk (Story 5567).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no
                         MailTriagePending table: Pending and PendingMessages are always 0. Same result
                         columns.

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(254) = NULLIF(LOWER(LTRIM(RTRIM(@prmSearch))), N'')

-- Story 5654: Echo has no MailTriagePending table, so Pending and PendingMessages are always 0.
SELECT 0                                                                     AS Pending,
       0                                                                     AS PendingMessages,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE Disposition = 'white') AS White,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE Disposition = 'black') AS Black,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE Disposition = 'junk')  AS Junk,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE IsProtected = 1)       AS Protected,
       (SELECT COUNT(*) FROM dbo.MailTriageRule
         WHERE @q IS NULL OR SenderKey LIKE N'%' + @q + N'%')                AS MatchAll,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE Disposition = 'white'
           AND (@q IS NULL OR SenderKey LIKE N'%' + @q + N'%'))              AS MatchWhite,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE Disposition = 'black'
           AND (@q IS NULL OR SenderKey LIKE N'%' + @q + N'%'))              AS MatchBlack,
       (SELECT COUNT(*) FROM dbo.MailTriageRule WHERE Disposition = 'junk'
           AND (@q IS NULL OR SenderKey LIKE N'%' + @q + N'%'))              AS MatchJunk,
       -- Junk needs Schema_MailTriage_Junk.sql (a change to an existing CHECK constraint, waiting
       -- on Tony). Until it is run the page offers no Junk choice the table would refuse.
       CAST(CASE WHEN EXISTS (SELECT 1 FROM sys.check_constraints
                              WHERE name = 'CK_MailTriageRule_Disposition'
                                AND definition LIKE '%junk%')
                 THEN 1 ELSE 0 END AS BIT)                                  AS JunkReady

GO
