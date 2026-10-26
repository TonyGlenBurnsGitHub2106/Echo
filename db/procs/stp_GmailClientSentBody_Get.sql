SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSentBody_Get') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSentBody_Get
GO
CREATE PROCEDURE dbo.stp_GmailClientSentBody_Get
    @prmGmailClientSentMessageID INT
AS

/*

Gmail Client Sent mail (Story 5596): one sent message's saved body -- BodyHtml, BodyText,
AttachmentNames, IsGoneFromGmail, SavedDate. No row when Leonardo has not saved it yet.

TEST
----

    EXEC dbo.stp_GmailClientSentBody_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT GmailClientSentMessageID, BodyHtml, BodyText, AttachmentNames, IsGoneFromGmail, SavedDate
FROM   dbo.GmailClientSentMessageBody
WHERE  GmailClientSentMessageID = @prmGmailClientSentMessageID

GO
