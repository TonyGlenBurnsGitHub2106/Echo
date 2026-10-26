SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientInbox_Save') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientInbox_Save
GO
CREATE PROCEDURE dbo.stp_GmailClientInbox_Save
    @prmGmailClientInboxID INT          = 0,   -- 0 = a new inbox
    @prmInboxName          NVARCHAR(60)
AS

/*

Gmail Client (Story 5569): makes a new inbox or renames one. Refuses, before any write, a blank
name, a built-in name (All, Unclassified, Undecided), and a name another inbox already has. Returns the inbox.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientInbox_Save 0, N'Legal'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-04  Felix        Undecided is a built-in tab name too.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @id   INT          = ISNULL(@prmGmailClientInboxID, 0)
DECLARE @name NVARCHAR(60) = NULLIF(LTRIM(RTRIM(@prmInboxName)), N'')
DECLARE @msg  NVARCHAR(300)

IF @name IS NULL
BEGIN
    RAISERROR('An inbox needs a name. Nothing was saved.', 16, 1)
    RETURN
END
IF @name IN (N'All', N'Unclassified', N'Undecided')
BEGIN
    RAISERROR('All, Unclassified and Undecided are built in; give the inbox another name. Nothing was saved.', 16, 1)
    RETURN
END
IF @id > 0 AND NOT EXISTS (SELECT 1 FROM dbo.GmailClientInbox WHERE GmailClientInboxID = @id)
BEGIN
    RAISERROR('That inbox no longer exists. Nothing was saved.', 16, 1)
    RETURN
END
IF EXISTS (SELECT 1 FROM dbo.GmailClientInbox WHERE InboxName = @name AND GmailClientInboxID <> @id)
BEGIN
    SET @msg = N'There is already an inbox named ' + @name + N'. Nothing was saved.'
    RAISERROR(@msg, 16, 1)
    RETURN
END

BEGIN TRAN
    IF @id > 0
        UPDATE dbo.GmailClientInbox SET InboxName = @name, ModifiedDate = GETDATE()
        WHERE  GmailClientInboxID = @id AND InboxName <> @name
    ELSE
    BEGIN
        INSERT INTO dbo.GmailClientInbox (InboxName, SortOrder)
        SELECT @name, ISNULL(MAX(SortOrder), 0) + 10 FROM dbo.GmailClientInbox
        SET @id = SCOPE_IDENTITY()
    END
COMMIT

SELECT GmailClientInboxID, InboxName, SortOrder, CreatedDate, ModifiedDate,
       (SELECT COUNT(*) FROM dbo.GmailClientSenderInbox s WHERE s.GmailClientInboxID = i.GmailClientInboxID) AS SenderCount
FROM   dbo.GmailClientInbox i
WHERE  GmailClientInboxID = @id

GO
