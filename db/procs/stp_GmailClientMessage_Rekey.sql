SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_Rekey') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_Rekey
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_Rekey
    @prmOldGmailID VARCHAR(400),
    @prmNewGmailID VARCHAR(400)
AS

/*

Echo Email Client (Story 5654), Echo only -- Leonardo has no such procedure. An Outlook item's
EntryID can change (moved between stores or folders, for one); when Check mail learns the new
EntryID of a message it already keeps, this moves the row onto it, so its read state, follow-up,
flags, body and filing stay with it and the check does not keep it a second time.

Moves GmailClientMessage.GmailID from @prmOldGmailID to @prmNewGmailID when the old id is there
and the new one is not yet. Returns one row, Rekeyed: 1 when the row moved; 0 when there was
nothing to do (no row under the old id, or the new id is already kept -- then both rows are left
exactly as they are). A blank id, or old = new, is refused before anything is written.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessage_Rekey '00000000AAAA', '00000000BBBB'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-26  Felix        Story 5654: new (Echo's Email Client; Outlook EntryIDs).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @old VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmOldGmailID)), '')
DECLARE @new VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmNewGmailID)), '')
DECLARE @rekeyed BIT = 0

IF @old IS NULL OR @new IS NULL
BEGIN
    RAISERROR('Moving a message to its new Outlook id needs both the old id and the new one. Nothing was saved.', 16, 1)
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
    IF EXISTS (SELECT 1 FROM dbo.GmailClientMessage WITH (UPDLOCK, HOLDLOCK) WHERE GmailID = @old)
       AND NOT EXISTS (SELECT 1 FROM dbo.GmailClientMessage WITH (UPDLOCK, HOLDLOCK) WHERE GmailID = @new)
    BEGIN
        UPDATE dbo.GmailClientMessage
        SET    GmailID = @new, ModifiedDate = GETDATE()
        WHERE  GmailID = @old
        SET @rekeyed = 1
    END
COMMIT

SELECT @rekeyed AS Rekeyed

GO
