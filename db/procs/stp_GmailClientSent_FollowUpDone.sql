SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_FollowUpDone') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_FollowUpDone
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_FollowUpDone
    @prmIDs VARCHAR(8000)   -- comma-separated GmailClientSentMessageIDs (at most 500)
AS

/*

Gmail Client Sent mail (Story 5596): close the open follow-ups of these sent messages. The day is
cleared with its note and FollowUpDoneDate records when (the _LOG keeps the history). Returns Updated, the number
closed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSent_FollowUpDone '1'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
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
    RAISERROR('Select at least one sent message. Nothing was saved.', 16, 1)
    RETURN
END
IF (SELECT COUNT(*) FROM @ids) > 500
BEGIN
    RAISERROR('Select at most 500 messages at a time. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @n INT

BEGIN TRAN

UPDATE s
SET    FollowUpDate = NULL, FollowUpNote = NULL, FollowUpDoneDate = GETDATE(), ModifiedDate = GETDATE()
FROM   dbo.GmailClientSentMessage s JOIN @ids x ON x.ID = s.GmailClientSentMessageID
WHERE  s.FollowUpDate IS NOT NULL

SET @n = @@ROWCOUNT

COMMIT

SELECT @n AS Updated

GO
