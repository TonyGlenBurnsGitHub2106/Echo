SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_Find') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_Find
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_Find
    @prmMode     VARCHAR(16),
    @prmSourceID INT
AS

/*

Gmail Client Drafts (Story 5620): the open draft of a reply or forward of one message, if there is
one -- so tapping Reply on a message Tony already started answering brings back what he wrote.
Body included. No row when there is none.

TEST
----

    EXEC dbo.stp_GmailClientDraft_Find 'reply', 12

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
WHERE  Mode = @prmMode AND SourceID = @prmSourceID AND IsOpen = 1

GO
