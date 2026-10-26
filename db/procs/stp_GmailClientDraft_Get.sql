SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_Get') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_Get
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_Get
    @prmGmailClientDraftID INT
AS

/*

Gmail Client Drafts (Story 5620): one OPEN draft, body included -- the page opening it to carry on
writing, or the service building Gmail's copy. A sent, discarded or gone draft returns no row.

TEST
----

    EXEC dbo.stp_GmailClientDraft_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT GmailClientDraftID, Mode, SourceID, ToHeader, CcHeader, BccHeader, Subject, BodyText, Snippet, IncludeOriginal,
       AttachmentsJson, AttachmentCount, GmailDraftID, GmailMessageID, ThreadID, InReplyTo, ReferencesHeader,
       IsFromGmailClient, PushedDate, CreatedDate, ModifiedDate
FROM   dbo.GmailClientDraft
WHERE  GmailClientDraftID = @prmGmailClientDraftID AND IsOpen = 1

GO
