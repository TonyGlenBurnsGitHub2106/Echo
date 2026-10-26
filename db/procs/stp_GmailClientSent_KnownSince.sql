SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_KnownSince') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_KnownSince
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_KnownSince
    @prmSince DATETIME
AS

/*

Gmail Client Sent mail (Story 5596): the Gmail ids of sent mail already kept that went on or after
a day before @prmSince -- so Check mail skips them without asking Gmail for them again.

TEST
----

    EXEC dbo.stp_GmailClientSent_KnownSince '2026-09-06'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT GmailID
FROM   dbo.GmailClientSentMessage
WHERE  SentDate >= DATEADD(DAY, -1, @prmSince)

GO
