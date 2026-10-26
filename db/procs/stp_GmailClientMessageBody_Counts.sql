SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessageBody_Counts') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessageBody_Counts
GO
CREATE PROCEDURE dbo.stp_GmailClientMessageBody_Counts
AS

/*

Gmail Client (Story 5576): of the messages the Gmail Client shows, how many are saved whole in
Leonardo (Saved) and how many are not yet (Missing).

TEST
----

    EXEC dbo.stp_GmailClientMessageBody_Counts

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-02  Felix        New (Story 5576, the whole message kept in Leonardo).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT SUM(CASE WHEN b.GmailClientMessageID IS NOT NULL THEN 1 ELSE 0 END) AS Saved,
       SUM(CASE WHEN b.GmailClientMessageID IS NULL THEN 1 ELSE 0 END) AS Missing
FROM   dbo.GmailClientMessage m
       LEFT JOIN dbo.GmailClientMessageBody b ON b.GmailClientMessageID = m.GmailClientMessageID
WHERE  m.IsHidden = 0
  AND  EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'white')

GO
