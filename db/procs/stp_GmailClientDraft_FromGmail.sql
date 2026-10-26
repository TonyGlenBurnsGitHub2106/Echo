SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_FromGmail') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_FromGmail
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_FromGmail
    @prmGmailDraftID   VARCHAR(400),   -- Story 5654: an Outlook EntryID; widened from VARCHAR(64)
    @prmGmailMessageID VARCHAR(400)   = NULL,   -- Story 5654: an Outlook EntryID; widened from VARCHAR(64)
    @prmThreadID       VARCHAR(64)    = NULL,
    @prmInReplyTo      NVARCHAR(500)  = NULL,
    @prmReferences     NVARCHAR(2000) = NULL,
    @prmToHeader       NVARCHAR(1000) = NULL,
    @prmCcHeader       NVARCHAR(1000) = NULL,
    @prmBccHeader      NVARCHAR(1000) = NULL,
    @prmSubject        NVARCHAR(500)  = NULL,
    @prmBodyText       NVARCHAR(MAX)  = NULL,
    @prmDraftDate      DATETIME       = NULL    -- when Gmail last changed it (NULL: now)
AS

/*

Gmail Client Drafts (Story 5620): a draft as Gmail has it, read by Check mail.

  - Not kept yet (written in Gmail itself): kept as a new draft, Mode 'gmail'. Recorded = 1.
  - Kept and open (changed in Gmail since): its words, recipients and threading are replaced by
    Gmail's, and it becomes Mode 'gmail' -- its body is now the whole text, quote included.
    Recorded = 2. The service only calls this when nothing was typed here since the last copy.
  - Kept and closed: left alone. Recorded = 0.

Returns Recorded and GmailClientDraftID. @prmDraftDate (Gmail's internalDate) dates the draft.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientDraft_FromGmail 'r-1', 'm-1', 't-1', NULL, NULL, N'pat@example.com', NULL, NULL, N'Hi', N'Hello'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts). @prmDraftDate: Gmail's own date.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. @prmGmailDraftID,
                         @prmGmailMessageID (and @gid) widened to VARCHAR(400) for Outlook EntryIDs.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @gid VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmGmailDraftID)), '')   -- Story 5654: widened from VARCHAR(64)
IF @gid IS NULL
BEGIN
    RAISERROR('A draft from Outlook needs its Outlook draft id. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @snippet NVARCHAR(300) = LEFT(LTRIM(REPLACE(REPLACE(REPLACE(ISNULL(@prmBodyText, N''), CHAR(13), N' '), CHAR(10), N' '), CHAR(9), N' ')), 300)
-- ModifiedDate and PushedDate both take Gmail's own date, so the Drafts tab orders a draft by when it
-- was written, and it counts as up to date with Gmail.
DECLARE @id INT, @open BIT, @now DATETIME = ISNULL(@prmDraftDate, GETDATE())

BEGIN TRAN

SELECT @id = GmailClientDraftID, @open = IsOpen
FROM   dbo.GmailClientDraft WITH (UPDLOCK, HOLDLOCK)
WHERE  GmailDraftID = @gid

IF @id IS NULL
BEGIN
    INSERT INTO dbo.GmailClientDraft (Mode, ToHeader, CcHeader, BccHeader, Subject, BodyText, Snippet, GmailDraftID,
                                      GmailMessageID, ThreadID, InReplyTo, ReferencesHeader, IsFromGmailClient,
                                      PushedDate, ModifiedDate)
    VALUES ('gmail', LEFT(NULLIF(@prmToHeader, N''), 1000), LEFT(NULLIF(@prmCcHeader, N''), 1000),
            LEFT(NULLIF(@prmBccHeader, N''), 1000), LEFT(@prmSubject, 500), @prmBodyText, @snippet, @gid,
            NULLIF(@prmGmailMessageID, ''), NULLIF(@prmThreadID, ''), LEFT(NULLIF(@prmInReplyTo, N''), 500),
            LEFT(NULLIF(@prmReferences, N''), 2000), 0, @now, @now)
    SET @id = CAST(SCOPE_IDENTITY() AS INT)
    COMMIT
    SELECT 1 AS Recorded, @id AS GmailClientDraftID
    RETURN
END

IF @open = 1
BEGIN
    UPDATE dbo.GmailClientDraft
    SET    Mode = 'gmail', SourceID = NULL, ToHeader = LEFT(NULLIF(@prmToHeader, N''), 1000),
           CcHeader = LEFT(NULLIF(@prmCcHeader, N''), 1000), BccHeader = LEFT(NULLIF(@prmBccHeader, N''), 1000),
           Subject = LEFT(@prmSubject, 500), BodyText = @prmBodyText, Snippet = @snippet, IncludeOriginal = 0,
           GmailMessageID = NULLIF(@prmGmailMessageID, ''), ThreadID = NULLIF(@prmThreadID, ''),
           InReplyTo = LEFT(NULLIF(@prmInReplyTo, N''), 500), ReferencesHeader = LEFT(NULLIF(@prmReferences, N''), 2000),
           ModifiedDate = @now, PushedDate = @now
    WHERE  GmailClientDraftID = @id
    COMMIT
    SELECT 2 AS Recorded, @id AS GmailClientDraftID
    RETURN
END

COMMIT
SELECT 0 AS Recorded, @id AS GmailClientDraftID

GO
