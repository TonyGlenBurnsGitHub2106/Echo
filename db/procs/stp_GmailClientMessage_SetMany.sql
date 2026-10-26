SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_SetMany') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_SetMany
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_SetMany
    @prmIDs    VARCHAR(8000),   -- comma-separated GmailClientMessageIDs (at most 500)
    @prmAction VARCHAR(10)      -- read | unread | hide | unhide
AS

/*

Gmail Client (Story 5569): the selected messages at once -- mark read, mark unread, delete
(hide in Leonardo only) or bring back. Gmail itself is never touched. Every input is checked
before the one write; a message already in that state is left alone. Returns Updated, the
number of messages that changed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessage_SetMany '1,2,3', 'read'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Stewart      New (Story 5569, Tony's comment: multi select).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @action VARCHAR(10) = LOWER(LTRIM(RTRIM(ISNULL(@prmAction, ''))))
IF @action NOT IN ('read', 'unread', 'hide', 'unhide')
BEGIN
    RAISERROR('Choose read, unread, delete or restore. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @ids TABLE (ID INT NOT NULL PRIMARY KEY)
INSERT INTO @ids (ID)
SELECT DISTINCT (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END)
FROM   dbo.Split(ISNULL(@prmIDs, ''), ',')
WHERE  (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END) IS NOT NULL

IF NOT EXISTS (SELECT 1 FROM @ids)
BEGIN
    RAISERROR('Select at least one message. Nothing was saved.', 16, 1)
    RETURN
END
IF (SELECT COUNT(*) FROM @ids) > 500
BEGIN
    RAISERROR('Select at most 500 messages at a time. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @n INT

BEGIN TRAN

IF @action IN ('read', 'unread')
    UPDATE m
    SET    IsRead = CASE WHEN @action = 'read' THEN 1 ELSE 0 END, ModifiedDate = GETDATE()
    FROM   dbo.GmailClientMessage m JOIN @ids x ON x.ID = m.GmailClientMessageID
    WHERE  m.IsRead <> CASE WHEN @action = 'read' THEN 1 ELSE 0 END
ELSE
    UPDATE m
    SET    IsHidden     = CASE WHEN @action = 'hide' THEN 1 ELSE 0 END,
           HiddenDate   = CASE WHEN @action = 'hide' THEN GETDATE() END,
           ModifiedDate = GETDATE()
    FROM   dbo.GmailClientMessage m JOIN @ids x ON x.ID = m.GmailClientMessageID
    WHERE  m.IsHidden <> CASE WHEN @action = 'hide' THEN 1 ELSE 0 END

SET @n = @@ROWCOUNT

COMMIT

SELECT @n AS Updated

GO
