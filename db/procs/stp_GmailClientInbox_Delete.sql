SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientInbox_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientInbox_Delete
GO
CREATE PROCEDURE dbo.stp_GmailClientInbox_Delete
    @prmGmailClientInboxID INT
AS

/*

Gmail Client (Story 5569): deletes an inbox. Its senders go back to Unclassified (their rows
are removed); no message is touched. Returns Deleted (0 when there was no such inbox) and
SendersMoved.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientInbox_Delete 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @moved INT = 0, @deleted INT = 0

BEGIN TRAN
    DELETE FROM dbo.GmailClientSenderInbox WHERE GmailClientInboxID = @prmGmailClientInboxID
    SET @moved = @@ROWCOUNT
    DELETE FROM dbo.GmailClientInbox WHERE GmailClientInboxID = @prmGmailClientInboxID
    SET @deleted = @@ROWCOUNT
COMMIT

SELECT @deleted AS Deleted, @moved AS SendersMoved

GO
