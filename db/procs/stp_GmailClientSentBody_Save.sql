SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSentBody_Save') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSentBody_Save
GO
CREATE PROCEDURE dbo.stp_GmailClientSentBody_Save
    @prmGmailClientSentMessageID INT,
    @prmBodyHtml                 NVARCHAR(MAX)  = NULL,
    @prmBodyText                 NVARCHAR(MAX)  = NULL,
    @prmAttachmentNames          NVARCHAR(2000) = NULL,
    @prmIsGoneFromGmail          BIT            = 0
AS

/*

Gmail Client Sent mail (Story 5596): keeps one sent message's whole body in Leonardo -- the
sanitized HTML, the plain text and the attachment names -- or, with @prmIsGoneFromGmail = 1, notes
that Gmail no longer had it. Saving again replaces it. Refuses, before any write, a sent message
Leonardo does not have. Returns Saved = 1.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSentBody_Save 1, NULL, N'Hi', NULL, 0; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

IF NOT EXISTS (SELECT 1 FROM dbo.GmailClientSentMessage WHERE GmailClientSentMessageID = @prmGmailClientSentMessageID)
BEGIN
    RAISERROR('That sent message is not in Echo. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    IF EXISTS (SELECT 1 FROM dbo.GmailClientSentMessageBody WITH (UPDLOCK, HOLDLOCK) WHERE GmailClientSentMessageID = @prmGmailClientSentMessageID)
        UPDATE dbo.GmailClientSentMessageBody
        SET    BodyHtml = @prmBodyHtml, BodyText = @prmBodyText, AttachmentNames = LEFT(@prmAttachmentNames, 2000),
               IsGoneFromGmail = ISNULL(@prmIsGoneFromGmail, 0), SavedDate = GETDATE()
        WHERE  GmailClientSentMessageID = @prmGmailClientSentMessageID
    ELSE
        INSERT INTO dbo.GmailClientSentMessageBody (GmailClientSentMessageID, BodyHtml, BodyText, AttachmentNames, IsGoneFromGmail)
        VALUES (@prmGmailClientSentMessageID, @prmBodyHtml, @prmBodyText, LEFT(@prmAttachmentNames, 2000), ISNULL(@prmIsGoneFromGmail, 0))
COMMIT

SELECT 1 AS Saved

GO
