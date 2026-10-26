SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessageBody_Get') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessageBody_Get
GO
CREATE PROCEDURE dbo.stp_GmailClientMessageBody_Get
    @prmGmailClientMessageID INT
AS

/*

Gmail Client (Story 5576): one message's saved body. No row when it has not been saved yet.

TEST
----

    EXEC dbo.stp_GmailClientMessageBody_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-02  Felix        New (Story 5576, the whole message kept in Leonardo).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT GmailClientMessageID, BodyHtml, BodyText, AttachmentNames, IsGoneFromGmail, SavedDate
FROM   dbo.GmailClientMessageBody
WHERE  GmailClientMessageID = @prmGmailClientMessageID

GO
