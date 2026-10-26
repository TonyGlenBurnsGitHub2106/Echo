SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSentBody_ListMissing') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSentBody_ListMissing
GO
CREATE PROCEDURE dbo.stp_GmailClientSentBody_ListMissing
    @prmTop INT = 500
AS

/*

Gmail Client Sent mail (Story 5596): the sent messages (not deleted) whose body Leonardo has not
saved yet, newest first -- what a Check mail saves next. Mail sent from the Gmail Client has its
body saved as it goes, so this is mail sent from Gmail itself.

TEST
----

    EXEC dbo.stp_GmailClientSentBody_ListMissing 20

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

DECLARE @top INT = CASE WHEN ISNULL(@prmTop, 0) <= 0 THEN 500 WHEN @prmTop > 5000 THEN 5000 ELSE @prmTop END

SELECT TOP (@top) s.GmailClientSentMessageID, s.GmailID
FROM   dbo.GmailClientSentMessage s
WHERE  s.IsHidden = 0
  AND  NOT EXISTS (SELECT 1 FROM dbo.GmailClientSentMessageBody b WHERE b.GmailClientSentMessageID = s.GmailClientSentMessageID)
ORDER BY s.SentDate DESC, s.GmailClientSentMessageID DESC

GO
