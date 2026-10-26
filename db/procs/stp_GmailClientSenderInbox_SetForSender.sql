SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSenderInbox_SetForSender') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSenderInbox_SetForSender
GO
CREATE PROCEDURE dbo.stp_GmailClientSenderInbox_SetForSender
    @prmSenderAddress NVARCHAR(254),
    @prmInboxName     NVARCHAR(60) = NULL,   -- blank = Unclassified, for this one sender
    @prmFollowDomain  BIT          = 0       -- 1 = drop the override: the sender goes back to the domain's inbox
AS

/*

Gmail Client sender-level inboxes (Story 5608): "Default to domain level, but allow sender level
overrides. If we have sender level, we use it, else domain level." Files ONE person's mail
(greg@example.com) in an inbox of its own, apart from the rest of their domain -- or, with no inbox
name, in Unclassified while the domain stays filed. The override is a GmailClientSenderInbox row
keyed by the full address; vw_GmailClientMessageInbox gives it precedence over the domain's row.
@prmFollowDomain = 1 removes it. The inbox is found by name, made if new.

Returns SenderKey (the address), GmailClientInboxID, InboxName and HasOverride (0 after
@prmFollowDomain).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSenderInbox_SetForSender N'greg@example.com', N'Legal'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, sender-level inboxes).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @addr  NVARCHAR(254) = LOWER(LTRIM(RTRIM(ISNULL(@prmSenderAddress, N''))))
DECLARE @name  NVARCHAR(60)  = NULLIF(LTRIM(RTRIM(@prmInboxName)), N'')
DECLARE @inbox INT

IF @addr NOT LIKE N'_%@_%._%' OR CHARINDEX(N' ', @addr) > 0
BEGIN
    RAISERROR('File one sender by their full email address, such as greg@example.com. Nothing was saved.', 16, 1)
    RETURN
END
IF @name IN (N'All', N'Unclassified', N'Undecided')
BEGIN
    RAISERROR('All, Undecided and Unclassified are built in; give the inbox another name. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    IF ISNULL(@prmFollowDomain, 0) = 1
        DELETE FROM dbo.GmailClientSenderInbox WHERE SenderKey = @addr
    ELSE
    BEGIN
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

        IF EXISTS (SELECT 1 FROM dbo.GmailClientSenderInbox WHERE SenderKey = @addr)
            UPDATE dbo.GmailClientSenderInbox
            SET    GmailClientInboxID = @inbox, ModifiedDate = GETDATE()
            WHERE  SenderKey = @addr AND ISNULL(GmailClientInboxID, 0) <> ISNULL(@inbox, 0)
        ELSE
            INSERT INTO dbo.GmailClientSenderInbox (SenderKey, GmailClientInboxID) VALUES (@addr, @inbox)
    END
COMMIT

SELECT @addr AS SenderKey, i.GmailClientInboxID, i.InboxName,
       CAST(CASE WHEN EXISTS (SELECT 1 FROM dbo.GmailClientSenderInbox s WHERE s.SenderKey = @addr) THEN 1 ELSE 0 END AS BIT) AS HasOverride
FROM   (SELECT 1 AS One) x LEFT JOIN dbo.GmailClientInbox i ON i.GmailClientInboxID = @inbox

GO
