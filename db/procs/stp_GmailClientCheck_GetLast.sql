SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientCheck_GetLast') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientCheck_GetLast
GO
CREATE PROCEDURE dbo.stp_GmailClientCheck_GetLast
    @prmCompleteOnly BIT = 0
AS

/*

Gmail Client (Story 5569): the latest Check mail, or the latest COMPLETE one (which sets where
the next check starts reading). No row when there is none.

TEST
----

    EXEC dbo.stp_GmailClientCheck_GetLast 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT TOP 1 GmailClientCheckID, StartedDate, FinishedDate, WindowStartDate, MessagesListed, MessagesAdded,
       IsComplete, ErrorText
FROM   dbo.GmailClientCheck
WHERE  ISNULL(@prmCompleteOnly, 0) = 0 OR IsComplete = 1
ORDER BY StartedDate DESC, GmailClientCheckID DESC

GO
