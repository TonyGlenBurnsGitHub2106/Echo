SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientFlag_List') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientFlag_List
GO
CREATE PROCEDURE dbo.stp_GmailClientFlag_List
AS

/*

Gmail Client flags (Story 5608): every flag, in flag order then by name, with how many shown
messages carry it (MessageCount: not hidden, sender not blacklisted) and its colour when it has
one (ColorHex, from dbo.GmailClientColor). A small lookup -- the page loads it whole, once, to
name and colour the flags on each card.

TEST
----

    EXEC dbo.stp_GmailClientFlag_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT f.GmailClientFlagID, f.FlagName, f.SortOrder, c.ColorHex,
       (SELECT COUNT(*)
        FROM   dbo.GmailClientMessageFlag mf
               JOIN dbo.GmailClientMessage m ON m.GmailClientMessageID = mf.GmailClientMessageID
        WHERE  mf.GmailClientFlagID = f.GmailClientFlagID
          AND  m.IsHidden = 0
          AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m.SenderKey AND r.Disposition = 'black')) AS MessageCount
FROM   dbo.GmailClientFlag f
       LEFT JOIN dbo.GmailClientColor c ON c.GmailClientFlagID = f.GmailClientFlagID
ORDER BY f.SortOrder, f.FlagName

GO
