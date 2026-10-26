SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientSent_Record') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientSent_Record
GO
CREATE PROCEDURE dbo.stp_GmailClientSent_Record
    @prmGmailID           VARCHAR(400),   -- Story 5654: an Outlook EntryID (or echo:<32 hex>); widened from VARCHAR(64)
    @prmThreadID          VARCHAR(64)    = NULL,
    @prmFromHeader        NVARCHAR(320)  = NULL,
    @prmToHeader          NVARCHAR(1000) = NULL,
    @prmCcHeader          NVARCHAR(1000) = NULL,
    @prmBccHeader         NVARCHAR(1000) = NULL,
    @prmSubject           NVARCHAR(500)  = NULL,
    @prmSnippet           NVARCHAR(500)  = NULL,
    @prmMessageIDHeader   NVARCHAR(500)  = NULL,
    @prmSentDate          DATETIME,
    @prmIsFromGmailClient BIT            = 0
AS

/*

Gmail Client Sent mail (Story 5596): keeps the headers of one sent message -- one the Gmail
Client just sent, or one Check mail read from Gmail's Sent folder. A message already kept is left
alone (Recorded = 0) -- a second check, or a check after a send from the Gmail Client, never keeps
it twice (a UNIQUE Gmail id backs this up). Returns Recorded and GmailClientSentMessageID.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientSent_Record 'abc', 't1', N'me@example.com', N'you@example.com', NULL, NULL, N'Hi', NULL, NULL, '2026-10-06', 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-06  Felix        New (Story 5596, Sent mail).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. @prmGmailID (and
                         @id) widened to VARCHAR(400) for Outlook EntryIDs.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @id VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmGmailID)), '')   -- Story 5654: widened from VARCHAR(64)
IF @id IS NULL OR @prmSentDate IS NULL
BEGIN
    RAISERROR('A sent message needs its Outlook id and the date it was sent. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @existing INT

BEGIN TRAN

SELECT @existing = GmailClientSentMessageID
FROM   dbo.GmailClientSentMessage WITH (UPDLOCK, HOLDLOCK)
WHERE  GmailID = @id

IF @existing IS NULL
BEGIN
    INSERT INTO dbo.GmailClientSentMessage (GmailID, ThreadID, FromHeader, ToHeader, CcHeader, BccHeader, Subject,
                                            Snippet, MessageIDHeader, SentDate, IsFromGmailClient)
    VALUES (@id, NULLIF(@prmThreadID, ''), LEFT(@prmFromHeader, 320), LEFT(@prmToHeader, 1000), LEFT(@prmCcHeader, 1000),
            LEFT(NULLIF(@prmBccHeader, N''), 1000), LEFT(@prmSubject, 500), LEFT(@prmSnippet, 500),
            LEFT(@prmMessageIDHeader, 500), @prmSentDate, ISNULL(@prmIsFromGmailClient, 0))

    COMMIT
    SELECT 1 AS Recorded, CAST(SCOPE_IDENTITY() AS INT) AS GmailClientSentMessageID
    RETURN
END

COMMIT
SELECT 0 AS Recorded, @existing AS GmailClientSentMessageID

GO
