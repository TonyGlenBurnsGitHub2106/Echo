SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_SetRead') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_SetRead
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_SetRead
    @prmGmailClientMessageID INT,
    @prmIsRead               BIT
AS

/*

Gmail Client (Story 5569): marks a message read or unread in Leonardo only (Gmail's own read
flag is not touched). Returns Updated = 1 when the message exists.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessage_SetRead 1, 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

UPDATE dbo.GmailClientMessage
SET    IsRead = ISNULL(@prmIsRead, 1), ModifiedDate = GETDATE()
WHERE  GmailClientMessageID = @prmGmailClientMessageID AND IsRead <> ISNULL(@prmIsRead, 1)

SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.GmailClientMessage WHERE GmailClientMessageID = @prmGmailClientMessageID)
            THEN 1 ELSE 0 END AS Updated

GO
