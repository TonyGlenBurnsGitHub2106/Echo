SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientFlag_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientFlag_Delete
GO
CREATE PROCEDURE dbo.stp_GmailClientFlag_Delete
    @prmGmailClientFlagID INT
AS

/*

Gmail Client flags (Story 5608): deletes a flag. It comes off every message that carried it and
its colour goes with it; no message is deleted. Returns Deleted (0 when there was no such flag)
and MessagesUnflagged.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientFlag_Delete 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @unflagged INT = 0, @deleted INT = 0

BEGIN TRAN
    DELETE FROM dbo.GmailClientMessageFlag WHERE GmailClientFlagID = @prmGmailClientFlagID
    SET @unflagged = @@ROWCOUNT
    DELETE FROM dbo.GmailClientColor WHERE GmailClientFlagID = @prmGmailClientFlagID
    DELETE FROM dbo.GmailClientFlag WHERE GmailClientFlagID = @prmGmailClientFlagID
    SET @deleted = @@ROWCOUNT
COMMIT

SELECT @deleted AS Deleted, @unflagged AS MessagesUnflagged

GO
