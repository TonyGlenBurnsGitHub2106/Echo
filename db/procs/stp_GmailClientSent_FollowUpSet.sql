SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_FollowUpSet') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_FollowUpSet
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_FollowUpSet
    @prmIDs     VARCHAR(8000),          -- comma-separated GmailClientSentMessageIDs (at most 500)
    @prmDueDate VARCHAR(10),            -- yyyy-mm-dd
    @prmNote    NVARCHAR(300) = NULL
AS

/*

Gmail Client Sent mail (Story 5596): flag sent messages to follow up on a day ("if they have not
answered by Friday, chase them"), with an optional note. Flagging again moves the day (and re-opens a
done one): the follow-up lives on the sent row, so there is never more than one. Every input is
checked before the one write. Returns Updated, the number flagged.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSent_FollowUpSet '1', '2026-10-09', N'Chase the contract'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

IF NULLIF(LTRIM(RTRIM(ISNULL(@prmDueDate, ''))), '') IS NULL OR dbo.fn_IsoDate_IsValid(@prmDueDate) = 0
BEGIN
    RAISERROR('Choose the day to follow up (yyyy-mm-dd). Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @due  DATE          = CONVERT(DATE, @prmDueDate, 120)
DECLARE @note NVARCHAR(300) = NULLIF(LTRIM(RTRIM(@prmNote)), N'')

DECLARE @ids TABLE (ID INT NOT NULL PRIMARY KEY)
INSERT INTO @ids (ID)
SELECT DISTINCT s.GmailClientSentMessageID
FROM   dbo.Split(ISNULL(@prmIDs, ''), ',') x
       JOIN dbo.GmailClientSentMessage s ON s.GmailClientSentMessageID = (CASE WHEN LTRIM(RTRIM(x.items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(x.items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(x.items))) END)

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

BEGIN TRAN

UPDATE s
SET    FollowUpDate = @due, FollowUpNote = @note, FollowUpDoneDate = NULL, ModifiedDate = GETDATE()
FROM   dbo.GmailClientSentMessage s JOIN @ids x ON x.ID = s.GmailClientSentMessageID

COMMIT

SELECT COUNT(*) AS Updated FROM @ids

GO
