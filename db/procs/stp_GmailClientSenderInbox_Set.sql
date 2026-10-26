SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSenderInbox_Set') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSenderInbox_Set
GO
CREATE PROCEDURE dbo.stp_GmailClientSenderInbox_Set
    @prmSenderKey NVARCHAR(254),
    @prmInboxName NVARCHAR(60) = NULL   -- NULL or blank = Unclassified
AS

/*

Gmail Client (Story 5569): puts a sender's mail in an inbox (found by name, made if new), or
back in Unclassified when no name is given. Every message from that sender, past and future,
shows in that inbox. Returns SenderKey, GmailClientInboxID, InboxName.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSenderInbox_Set N'example.com', N'Legal'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @key   NVARCHAR(254) = LOWER(LTRIM(RTRIM(ISNULL(@prmSenderKey, N''))))
DECLARE @name  NVARCHAR(60)  = NULLIF(LTRIM(RTRIM(@prmInboxName)), N'')
DECLARE @inbox INT

IF @key = N'' OR CHARINDEX(N'.', @key) = 0
BEGIN
    RAISERROR('A sender is a domain such as nextdoor.com, or one full address. Nothing was saved.', 16, 1)
    RETURN
END
IF @name IN (N'All', N'Unclassified')
BEGIN
    RAISERROR('All and Unclassified are built in; give the inbox another name. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    IF @name IS NOT NULL
    BEGIN
        SELECT @inbox = GmailClientInboxID FROM dbo.GmailClientInbox WHERE InboxName = @name
        IF @inbox IS NULL
        BEGIN
            INSERT INTO dbo.GmailClientInbox (InboxName, SortOrder)
            SELECT @name, ISNULL(MAX(SortOrder), 0) + 10 FROM dbo.GmailClientInbox
            SET @inbox = SCOPE_IDENTITY()
        END
    END

    IF @inbox IS NULL
        DELETE FROM dbo.GmailClientSenderInbox WHERE SenderKey = @key
    ELSE IF EXISTS (SELECT 1 FROM dbo.GmailClientSenderInbox WHERE SenderKey = @key)
        UPDATE dbo.GmailClientSenderInbox
        SET    GmailClientInboxID = @inbox, ModifiedDate = GETDATE()
        WHERE  SenderKey = @key AND ISNULL(GmailClientInboxID, 0) <> @inbox
    ELSE
        INSERT INTO dbo.GmailClientSenderInbox (SenderKey, GmailClientInboxID) VALUES (@key, @inbox)
COMMIT

SELECT @key AS SenderKey, i.GmailClientInboxID, i.InboxName
FROM   (SELECT 1 AS One) x LEFT JOIN dbo.GmailClientInbox i ON i.GmailClientInboxID = @inbox

GO
