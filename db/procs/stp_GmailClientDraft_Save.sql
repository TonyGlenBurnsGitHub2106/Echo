SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_Save') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_Save
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_Save
    @prmGmailClientDraftID INT            = 0,      -- 0 = a draft the page has not saved before
    @prmClientKey          VARCHAR(40)    = NULL,   -- the page's own key for a new draft
    @prmMode               VARCHAR(16),
    @prmSourceID           INT            = NULL,
    @prmToHeader           NVARCHAR(1000) = NULL,
    @prmCcHeader           NVARCHAR(1000) = NULL,
    @prmBccHeader          NVARCHAR(1000) = NULL,
    @prmSubject            NVARCHAR(500)  = NULL,
    @prmBodyText           NVARCHAR(MAX)  = NULL,
    @prmIncludeOriginal    BIT            = 0,
    @prmAttachmentsJson    NVARCHAR(4000) = NULL,
    @prmAttachmentCount    INT            = 0
AS

/*

Gmail Client Drafts (Story 5620): saves what Tony is writing. The page saves as he types, so this
is called often and must never make two drafts of one message:

  - an id > 0 updates that draft (refused once it was sent or discarded);
  - a new draft whose ClientKey is already kept updates that one (a save sent twice);
  - a new reply / forward of a message that already has an open draft updates that one;
  - otherwise a new draft is made.

Everything is checked before the first write; one transaction. Returns the draft through
stp_GmailClientDraft_Get's columns (no body). The mode of a kept draft never changes here.

TEST
----

    BEGIN TRAN
    EXEC dbo.stp_GmailClientDraft_Save 0, 'k1', 'new', NULL, N'pat@example.com', NULL, NULL, N'Hi', N'Hello there'
    EXEC dbo.stp_GmailClientDraft_Save 0, 'k1', 'new', NULL, N'pat@example.com', NULL, NULL, N'Hi', N'Hello there again'
    SELECT * FROM dbo.GmailClientDraft WHERE ClientKey = 'k1'
    ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @mode VARCHAR(16) = LTRIM(RTRIM(ISNULL(@prmMode, '')))
DECLARE @key  VARCHAR(40) = NULLIF(LTRIM(RTRIM(@prmClientKey)), '')
DECLARE @id   INT = ISNULL(@prmGmailClientDraftID, 0)

IF @mode NOT IN ('new', 'reply', 'replyAll', 'forward', 'writeAgain', 'forwardSent', 'gmail')
BEGIN
    RAISERROR('A draft is a new message, a reply, a forward, a write again, or one from Outlook. Nothing was saved.', 16, 1)
    RETURN
END
IF @mode IN ('reply', 'replyAll', 'forward', 'writeAgain', 'forwardSent') AND ISNULL(@prmSourceID, 0) <= 0
BEGIN
    RAISERROR('A reply or forward draft needs the message it answers. Nothing was saved.', 16, 1)
    RETURN
END
IF @id < 0 OR ISNULL(@prmAttachmentCount, 0) < 0 OR ISNULL(@prmAttachmentCount, 0) > 20
BEGIN
    RAISERROR('That draft cannot be saved: at most 20 attachments. Nothing was saved.', 16, 1)
    RETURN
END
IF @id = 0 AND @key IS NULL
BEGIN
    RAISERROR('A new draft needs the page''s key, so a save sent twice cannot make two drafts. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @source INT = CASE WHEN @mode IN ('new', 'gmail') THEN NULL ELSE @prmSourceID END
DECLARE @snippet NVARCHAR(300) = LEFT(LTRIM(REPLACE(REPLACE(REPLACE(ISNULL(@prmBodyText, N''), CHAR(13), N' '), CHAR(10), N' '), CHAR(9), N' ')), 300)

BEGIN TRAN

IF @id > 0
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.GmailClientDraft WITH (UPDLOCK, HOLDLOCK) WHERE GmailClientDraftID = @id AND IsOpen = 1)
    BEGIN
        ROLLBACK
        RAISERROR('That draft was already sent or discarded. Nothing was saved.', 16, 1)
        RETURN
    END
END
ELSE
BEGIN
    SELECT @id = GmailClientDraftID FROM dbo.GmailClientDraft WITH (UPDLOCK, HOLDLOCK) WHERE ClientKey = @key
    IF @id > 0 AND NOT EXISTS (SELECT 1 FROM dbo.GmailClientDraft WHERE GmailClientDraftID = @id AND IsOpen = 1)
    BEGIN
        ROLLBACK
        RAISERROR('That draft was already sent or discarded. Nothing was saved.', 16, 1)
        RETURN
    END
    IF ISNULL(@id, 0) = 0 AND @source IS NOT NULL
        SELECT @id = GmailClientDraftID FROM dbo.GmailClientDraft WITH (UPDLOCK, HOLDLOCK)
        WHERE  Mode = @mode AND SourceID = @source AND IsOpen = 1
    SET @id = ISNULL(@id, 0)
END

IF @id > 0
    UPDATE dbo.GmailClientDraft
    SET    ToHeader = LEFT(NULLIF(@prmToHeader, N''), 1000), CcHeader = LEFT(NULLIF(@prmCcHeader, N''), 1000),
           BccHeader = LEFT(NULLIF(@prmBccHeader, N''), 1000), Subject = LEFT(@prmSubject, 500),
           BodyText = @prmBodyText, Snippet = @snippet, IncludeOriginal = ISNULL(@prmIncludeOriginal, 0),
           AttachmentsJson = NULLIF(@prmAttachmentsJson, N''), AttachmentCount = ISNULL(@prmAttachmentCount, 0),
           ModifiedDate = GETDATE()
    WHERE  GmailClientDraftID = @id
ELSE
BEGIN
    INSERT INTO dbo.GmailClientDraft (ClientKey, Mode, SourceID, ToHeader, CcHeader, BccHeader, Subject, BodyText, Snippet,
                                      IncludeOriginal, AttachmentsJson, AttachmentCount, IsFromGmailClient)
    VALUES (@key, @mode, @source, LEFT(NULLIF(@prmToHeader, N''), 1000), LEFT(NULLIF(@prmCcHeader, N''), 1000),
            LEFT(NULLIF(@prmBccHeader, N''), 1000), LEFT(@prmSubject, 500), @prmBodyText, @snippet,
            ISNULL(@prmIncludeOriginal, 0), NULLIF(@prmAttachmentsJson, N''), ISNULL(@prmAttachmentCount, 0), 1)
    SET @id = CAST(SCOPE_IDENTITY() AS INT)
END

COMMIT

SELECT GmailClientDraftID, Mode, SourceID, ToHeader, CcHeader, BccHeader, Subject, Snippet, IncludeOriginal,
       AttachmentsJson, AttachmentCount, GmailDraftID, GmailMessageID, ThreadID, InReplyTo, ReferencesHeader,
       IsFromGmailClient, PushedDate, CreatedDate, ModifiedDate
FROM   dbo.GmailClientDraft
WHERE  GmailClientDraftID = @id

GO
