SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_Close') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_Close
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_Close
    @prmGmailClientDraftID INT,
    @prmReason             VARCHAR(16)      -- sent | discarded | gone
AS

/*

Gmail Client Drafts (Story 5620): closes an open draft -- it was sent, Tony discarded it, or Gmail
no longer has it (sent or deleted in Gmail itself). The row is kept, closed, never deleted.
Returns Updated (0 when it was already closed).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientDraft_Close 1, 'discarded'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

IF ISNULL(@prmReason, '') NOT IN ('sent', 'discarded', 'gone')
BEGIN
    RAISERROR('A draft closes because it was sent, discarded, or is gone from Outlook. Nothing was saved.', 16, 1)
    RETURN
END

UPDATE dbo.GmailClientDraft
SET    IsOpen = 0, ClosedReason = @prmReason, ClosedDate = GETDATE()
WHERE  GmailClientDraftID = @prmGmailClientDraftID AND IsOpen = 1

SELECT @@ROWCOUNT AS Updated

GO
