SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientFollowUp_Set') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientFollowUp_Set
GO
CREATE PROCEDURE dbo.stp_GmailClientFollowUp_Set
    @prmIDs     VARCHAR(8000),          -- comma-separated GmailClientMessageIDs (at most 500)
    @prmDueDate VARCHAR(10),            -- yyyy-mm-dd
    @prmNote    NVARCHAR(300) = NULL
AS

/*

Gmail Client (Story 5569): flag messages to follow up on a day, with an optional note. A message
already flagged gets the new day and note (and a closed follow-up re-opens); one row per message
ever (a UNIQUE index), so a double press can never flag twice. Every input is checked before the
first write. Returns Updated, the number of messages flagged.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientFollowUp_Set '1,2', '2026-10-05', N'Call them'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Stewart      New (Story 5569, Tony's comment: follow up).
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
SELECT DISTINCT m.GmailClientMessageID
FROM   dbo.Split(ISNULL(@prmIDs, ''), ',') s
       JOIN dbo.GmailClientMessage m ON m.GmailClientMessageID = (CASE WHEN LTRIM(RTRIM(s.items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(s.items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(s.items))) END)

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

BEGIN TRAN

UPDATE f
SET    DueDate = @due, Note = @note, IsDone = 0, DoneDate = NULL, ModifiedDate = GETDATE()
FROM   dbo.GmailClientFollowUp f WITH (UPDLOCK, HOLDLOCK) JOIN @ids x ON x.ID = f.GmailClientMessageID

INSERT INTO dbo.GmailClientFollowUp (GmailClientMessageID, DueDate, Note)
SELECT x.ID, @due, @note
FROM   @ids x
WHERE  NOT EXISTS (SELECT 1 FROM dbo.GmailClientFollowUp f WITH (UPDLOCK, HOLDLOCK) WHERE f.GmailClientMessageID = x.ID)

COMMIT

SELECT COUNT(*) AS Updated FROM @ids

GO
