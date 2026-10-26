SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientColor_List') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientColor_List
GO
CREATE PROCEDURE dbo.stp_GmailClientColor_List
AS

/*

Gmail Client colours (Story 5608): every colour rule -- senders, domains, flags -- with the flag's
name for a flag rule. A small lookup the page loads whole, once, and colours each card from.
Senders first, then domains, then flags, each A to Z.

TEST
----

    EXEC dbo.stp_GmailClientColor_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, colours).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT c.GmailClientColorID, c.ColorKind, c.MatchText, c.GmailClientFlagID, f.FlagName, c.ColorHex,
       c.CreatedDate, c.ModifiedDate
FROM   dbo.GmailClientColor c
       LEFT JOIN dbo.GmailClientFlag f ON f.GmailClientFlagID = c.GmailClientFlagID
ORDER BY CASE c.ColorKind WHEN 'sender' THEN 1 WHEN 'domain' THEN 2 ELSE 3 END,
         ISNULL(c.MatchText, f.FlagName)

GO
