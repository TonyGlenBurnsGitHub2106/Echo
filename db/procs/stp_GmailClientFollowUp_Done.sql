SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientFollowUp_Done') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientFollowUp_Done
GO
CREATE PROCEDURE dbo.stp_GmailClientFollowUp_Done
    @prmIDs VARCHAR(8000)       -- comma-separated GmailClientMessageIDs (at most 500)
AS

/*

Gmail Client (Story 5569): close the open follow-ups of these messages. The rows stay (closed,
with the day they were closed) for the record; flagging the message again re-opens it. Returns
Updated, the number of follow-ups closed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientFollowUp_Done '1,2'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Stewart      New (Story 5569, Tony's comment: follow up).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

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

UPDATE f
SET    IsDone = 1, DoneDate = GETDATE(), ModifiedDate = GETDATE()
FROM   dbo.GmailClientFollowUp f JOIN @ids x ON x.ID = f.GmailClientMessageID
WHERE  f.IsDone = 0

SET @n = @@ROWCOUNT

COMMIT

SELECT @n AS Updated

GO
