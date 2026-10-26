SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_KnownSince') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_KnownSince
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_KnownSince
    @prmSince DATETIME
AS

/*

Gmail Client (Story 5569): the Gmail ids already kept that arrived on or after a day before
@prmSince, so a Check mail only asks Gmail for the headers of messages it has not seen.

TEST
----

    EXEC dbo.stp_GmailClientMessage_KnownSince '2026-09-01'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT GmailID
FROM   dbo.GmailClientMessage
WHERE  ReceivedDate >= DATEADD(DAY, -1, @prmSince)

GO
