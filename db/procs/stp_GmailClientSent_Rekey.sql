SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_Rekey') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_Rekey
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_Rekey
    @prmOldGmailID      VARCHAR(400),
    @prmNewGmailID      VARCHAR(400),
    @prmThreadID        VARCHAR(64)    = NULL,
    @prmMessageIDHeader NVARCHAR(500)  = NULL
AS

/*

Echo Email Client (Story 5654), Echo only -- Leonardo has no such procedure. A message sent from
the Email Client is kept the moment it goes, under a provisional id ("echo:<32 hex>"), because
Outlook gives a sent item its real EntryID only once it lands in Sent Items. When Check mail finds
it there, this moves the row onto the real EntryID, so the follow-up, the body and everything else
kept against the message stay with it and the check does not keep it a second time.

Moves GmailClientSentMessage.GmailID from @prmOldGmailID to @prmNewGmailID when the old id is
there and the new one is not yet, and takes Outlook's conversation (@prmThreadID) and Message-ID
when given -- a new message has no conversation until Outlook sends it, and without one its replies
are never matched. Returns one row, Rekeyed and GmailClientSentMessageID: 1 and the row when it moved; 0 when there
was nothing to do (no row under the old id, or the new id is already kept -- then both rows are
left exactly as they are). A blank id, or old = new, is refused before anything is written.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSent_Rekey 'echo:0123456789abcdef0123456789abcdef', '00000000ABCDEF'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-26  Felix        Story 5654: new (Echo's Email Client; Outlook EntryIDs).
2026-10-26  Felix        Story 5654 review: also takes the thread and Message-ID; returns the row's id.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @old VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmOldGmailID)), '')
DECLARE @new VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmNewGmailID)), '')
DECLARE @rekeyed BIT = 0
DECLARE @id INT = NULL

IF @old IS NULL OR @new IS NULL
BEGIN
    RAISERROR('Moving a sent message to its Outlook id needs both the old id and the new one. Nothing was saved.', 16, 1)
    RETURN
END
IF @old = @new
BEGIN
    RAISERROR('The old id and the new id are the same. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    -- UPDLOCK / HOLDLOCK: a second Check mail running at the same moment waits here instead of
    -- recording the new id in between the test and the update.
    IF EXISTS (SELECT 1 FROM dbo.GmailClientSentMessage WITH (UPDLOCK, HOLDLOCK) WHERE GmailID = @old)
       AND NOT EXISTS (SELECT 1 FROM dbo.GmailClientSentMessage WITH (UPDLOCK, HOLDLOCK) WHERE GmailID = @new)
    BEGIN
        UPDATE dbo.GmailClientSentMessage
        SET    GmailID = @new,
               ThreadID = COALESCE(NULLIF(LTRIM(RTRIM(@prmThreadID)), ''), ThreadID),
               MessageIDHeader = COALESCE(NULLIF(LTRIM(RTRIM(@prmMessageIDHeader)), ''), MessageIDHeader),
               ModifiedDate = GETDATE(),
               @id = GmailClientSentMessageID
        WHERE  GmailID = @old
        SET @rekeyed = 1
    END
COMMIT

SELECT @rekeyed AS Rekeyed, @id AS GmailClientSentMessageID

GO
