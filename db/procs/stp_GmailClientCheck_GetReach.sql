SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientCheck_GetReach') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientCheck_GetReach
GO
CREATE PROCEDURE dbo.stp_GmailClientCheck_GetReach
AS

/*

Gmail Client (Story 5569): how far back the Gmail Client has read -- the earliest window start of
any complete check (every complete check read everything from its window start up to the moment
it ran). ReachDate is NULL when no check has finished. Read older mail starts before it.

TEST
----

    EXEC dbo.stp_GmailClientCheck_GetReach

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Stewart      New (Story 5569, Tony's comment: load more options).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT MIN(WindowStartDate) AS ReachDate
FROM   dbo.GmailClientCheck
WHERE  IsComplete = 1

GO
