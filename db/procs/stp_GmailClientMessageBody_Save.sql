SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessageBody_Save') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessageBody_Save
GO
CREATE PROCEDURE dbo.stp_GmailClientMessageBody_Save
    @prmGmailClientMessageID INT,
    @prmBodyHtml             NVARCHAR(MAX)  = NULL,
    @prmBodyText             NVARCHAR(MAX)  = NULL,
    @prmAttachmentNames      NVARCHAR(2000) = NULL,
    @prmIsGoneFromGmail      BIT            = 0
AS

/*

Gmail Client (Story 5576): keeps one message's whole body in Leonardo -- the sanitized HTML, the
plain text and the attachment names -- or, with @prmIsGoneFromGmail = 1, notes that Gmail no longer
had it. Saving again replaces it. Refuses, before any write, a message Leonardo does not have.
Returns Saved = 1.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessageBody_Save 1, N'<p>Hi</p>', N'Hi', NULL, 0; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-02  Felix        New (Story 5576, the whole message kept in Leonardo).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

IF NOT EXISTS (SELECT 1 FROM dbo.GmailClientMessage WHERE GmailClientMessageID = @prmGmailClientMessageID)
BEGIN
    RAISERROR('That message is not in Echo. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    IF EXISTS (SELECT 1 FROM dbo.GmailClientMessageBody WHERE GmailClientMessageID = @prmGmailClientMessageID)
        UPDATE dbo.GmailClientMessageBody
        SET    BodyHtml = @prmBodyHtml, BodyText = @prmBodyText, AttachmentNames = LEFT(@prmAttachmentNames, 2000),
               IsGoneFromGmail = ISNULL(@prmIsGoneFromGmail, 0), SavedDate = GETDATE()
        WHERE  GmailClientMessageID = @prmGmailClientMessageID
    ELSE
        INSERT INTO dbo.GmailClientMessageBody (GmailClientMessageID, BodyHtml, BodyText, AttachmentNames, IsGoneFromGmail)
        VALUES (@prmGmailClientMessageID, @prmBodyHtml, @prmBodyText, LEFT(@prmAttachmentNames, 2000), ISNULL(@prmIsGoneFromGmail, 0))
COMMIT

SELECT 1 AS Saved

GO
