SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_SetGmail') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_SetGmail
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_SetGmail
    @prmGmailClientDraftID INT,
    @prmGmailDraftID       VARCHAR(400),   -- Story 5654: an Outlook EntryID; widened from VARCHAR(64)
    @prmGmailMessageID     VARCHAR(400)   = NULL,   -- Story 5654: an Outlook EntryID; widened from VARCHAR(64)
    @prmThreadID           VARCHAR(64)    = NULL,
    @prmInReplyTo          NVARCHAR(500)  = NULL,
    @prmReferences         NVARCHAR(2000) = NULL,
    @prmPushedAt           DATETIME                 -- the ModifiedDate the copy was built from
AS

/*

Gmail Client Drafts (Story 5620): after the service put a draft into Gmail's Drafts folder, keeps
Gmail's draft id, the id of Gmail's copy, and how it threads. PushedDate is the ModifiedDate the
copy was built from -- not "now" -- so anything typed while it was going still counts as not yet
in Gmail. Refused when another draft already holds that Gmail id; a closed draft is left alone.
Returns Updated.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientDraft_SetGmail 1, 'r-123', 'm-1', 't-1', NULL, NULL, '2026-10-12'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. @prmGmailDraftID,
                         @prmGmailMessageID (and @gid) widened to VARCHAR(400) for Outlook EntryIDs.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @gid VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmGmailDraftID)), '')   -- Story 5654: widened from VARCHAR(64)
IF @gid IS NULL OR @prmPushedAt IS NULL
BEGIN
    RAISERROR('Keeping Outlook''s copy of a draft needs Outlook''s draft id and when it was built. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN

IF EXISTS (SELECT 1 FROM dbo.GmailClientDraft WITH (UPDLOCK, HOLDLOCK)
           WHERE GmailDraftID = @gid AND GmailClientDraftID <> @prmGmailClientDraftID)
BEGIN
    ROLLBACK
    RAISERROR('Another draft already holds that Outlook draft. Nothing was saved.', 16, 1)
    RETURN
END

UPDATE dbo.GmailClientDraft
SET    GmailDraftID = @gid, GmailMessageID = NULLIF(@prmGmailMessageID, ''), ThreadID = NULLIF(@prmThreadID, ''),
       InReplyTo = LEFT(NULLIF(@prmInReplyTo, N''), 500), ReferencesHeader = LEFT(NULLIF(@prmReferences, N''), 2000),
       PushedDate = @prmPushedAt
WHERE  GmailClientDraftID = @prmGmailClientDraftID AND IsOpen = 1

DECLARE @n INT = @@ROWCOUNT
COMMIT
SELECT @n AS Updated

GO
