SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_SetHidden') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_SetHidden
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_SetHidden
    @prmGmailClientMessageID INT,
    @prmIsHidden             BIT
AS

/*

Gmail Client (Story 5569): Delete in the Gmail Client. Hides the message from Leonardo's view
(or brings it back); Gmail itself is never touched. Returns Updated = 1 when the message exists.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessage_SetHidden 1, 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

UPDATE dbo.GmailClientMessage
SET    IsHidden     = ISNULL(@prmIsHidden, 1),
       HiddenDate   = CASE WHEN ISNULL(@prmIsHidden, 1) = 1 THEN GETDATE() END,
       ModifiedDate = GETDATE()
WHERE  GmailClientMessageID = @prmGmailClientMessageID AND IsHidden <> ISNULL(@prmIsHidden, 1)

SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.GmailClientMessage WHERE GmailClientMessageID = @prmGmailClientMessageID)
            THEN 1 ELSE 0 END AS Updated

GO
