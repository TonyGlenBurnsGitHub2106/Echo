SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessage_Record') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessage_Record
GO
CREATE PROCEDURE dbo.stp_GmailClientMessage_Record
    @prmGmailID         VARCHAR(400),   -- Story 5654: an Outlook EntryID; widened from VARCHAR(64)
    @prmThreadID        VARCHAR(64)    = NULL,
    @prmSenderKey       NVARCHAR(254)  = NULL,
    @prmFromHeader      NVARCHAR(320)  = NULL,
    @prmToHeader        NVARCHAR(1000) = NULL,
    @prmCcHeader        NVARCHAR(1000) = NULL,
    @prmSubject         NVARCHAR(500)  = NULL,
    @prmSnippet         NVARCHAR(500)  = NULL,
    @prmMessageIDHeader NVARCHAR(500)  = NULL,
    @prmReceivedDate    DATETIME
AS

/*

Gmail Client (Story 5569): keeps the headers of one message a Check mail read from the Gmail
inbox. A message already kept is left alone (Recorded = 0) -- a second check, or a double tap,
never writes it twice.

Story 5608: a message is a Leonardo entity. Its EntityID comes from stp_Entity_Create (type looked
up by EntityTable = 'GmailClientMessage') in the same transaction as the insert, so a message is
never kept without one and an entity is never made for a message that was not kept.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessage_Record 'abc', NULL, N'example.com', N'a@example.com', NULL, NULL, N'Hi', NULL, NULL, '2026-10-01'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-08  Felix        Story 5608: every new message gets its EntityID.
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. @prmGmailID (and
                         @id) widened to VARCHAR(400) for Outlook EntryIDs. Echo has no Leonardo
                         entities: the stp_Entity_Create call and the tblEntityType check are dropped
                         and EntityID stays NULL.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @id VARCHAR(400) = NULLIF(LTRIM(RTRIM(@prmGmailID)), '')   -- Story 5654: widened from VARCHAR(64)

IF @id IS NULL OR @prmReceivedDate IS NULL
BEGIN
    RAISERROR('A message needs its Outlook id and the date it arrived. Nothing was saved.', 16, 1)
    RETURN
END

-- Story 5654: Echo has no Leonardo entities (tblEntityType, stp_Entity_Create), so the entity
-- type check and the stp_Entity_Create call are gone and EntityID stays NULL.

IF EXISTS (SELECT 1 FROM dbo.GmailClientMessage WHERE GmailID = @id)
BEGIN
    SELECT 0 AS Recorded
    RETURN
END

BEGIN TRAN
    INSERT INTO dbo.GmailClientMessage (GmailID, ThreadID, SenderKey, FromHeader, ToHeader, CcHeader, Subject,
                                        Snippet, MessageIDHeader, ReceivedDate, EntityID)
    VALUES (@id, NULLIF(@prmThreadID, ''), NULLIF(LOWER(LTRIM(RTRIM(@prmSenderKey))), N''),
            LEFT(@prmFromHeader, 320), LEFT(@prmToHeader, 1000), LEFT(@prmCcHeader, 1000), LEFT(@prmSubject, 500),
            LEFT(@prmSnippet, 500), LEFT(@prmMessageIDHeader, 500), @prmReceivedDate, NULL)   -- Story 5654: EntityID is always NULL in Echo
COMMIT

SELECT 1 AS Recorded

GO
