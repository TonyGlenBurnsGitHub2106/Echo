SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessageFlag_Bulk') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessageFlag_Bulk
GO
CREATE PROCEDURE dbo.stp_GmailClientMessageFlag_Bulk
    @prmIDs      VARCHAR(MAX),          -- the selected messages, comma separated
    @prmFlagName NVARCHAR(60),
    @prmAction   VARCHAR(6) = 'add'     -- add | remove
AS

/*

Gmail Client flags (Story 5608): one flag put on, or taken off, many selected messages at once
(the list's Select -> Flag). Adding finds the flag or makes it; a message that already carries it
is left alone, so a double press changes nothing. Removing a flag no message carries changes
nothing. At most 500 messages at a time. Returns Updated (messages that changed) and
GmailClientFlagID.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessageFlag_Bulk '1,2,3', N'Taxes', 'add'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @action VARCHAR(6) = LOWER(LTRIM(RTRIM(ISNULL(@prmAction, ''))))
DECLARE @name NVARCHAR(60) = NULLIF(LTRIM(RTRIM(@prmFlagName)), N'')
DECLARE @flag INT, @n INT = 0

IF @action NOT IN ('add', 'remove')
BEGIN
    RAISERROR('Choose to add the flag or take it off. Nothing was saved.', 16, 1)
    RETURN
END
IF @name IS NULL
BEGIN
    RAISERROR('Name the flag. Nothing was saved.', 16, 1)
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

SELECT @flag = GmailClientFlagID FROM dbo.GmailClientFlag WHERE FlagName = @name

BEGIN TRAN
    IF @action = 'add'
    BEGIN
        IF @flag IS NULL
        BEGIN
            INSERT INTO dbo.GmailClientFlag (FlagName) VALUES (@name)
            SET @flag = SCOPE_IDENTITY()
        END
        INSERT INTO dbo.GmailClientMessageFlag (GmailClientMessageID, GmailClientFlagID)
        SELECT m.GmailClientMessageID, @flag
        FROM   @ids x JOIN dbo.GmailClientMessage m ON m.GmailClientMessageID = x.ID AND m.IsHidden = 0
        WHERE  NOT EXISTS (SELECT 1 FROM dbo.GmailClientMessageFlag mf
                           WHERE mf.GmailClientMessageID = m.GmailClientMessageID AND mf.GmailClientFlagID = @flag)
        SET @n = @@ROWCOUNT
    END
    ELSE IF @flag IS NOT NULL
    BEGIN
        DELETE mf
        FROM   dbo.GmailClientMessageFlag mf JOIN @ids x ON x.ID = mf.GmailClientMessageID
        WHERE  mf.GmailClientFlagID = @flag
        SET @n = @@ROWCOUNT
    END
COMMIT

SELECT @n AS Updated, @flag AS GmailClientFlagID

GO
