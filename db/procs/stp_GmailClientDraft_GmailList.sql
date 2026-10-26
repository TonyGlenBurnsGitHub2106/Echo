SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientDraft_GmailList') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientDraft_GmailList
GO
CREATE PROCEDURE dbo.stp_GmailClientDraft_GmailList
AS

/*

Gmail Client Drafts (Story 5620): every draft that has (or had) a copy in Gmail's Drafts folder,
open or closed -- what Check mail compares Gmail's draft list against. A closed one is listed too,
so a draft sent from here whose Gmail copy could not be deleted is not read in again as new.

TEST
----

    EXEC dbo.stp_GmailClientDraft_GmailList

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-12  Felix        New (Story 5620, Drafts).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT GmailClientDraftID, GmailDraftID, GmailMessageID, Mode, IsOpen, ClosedReason, PushedDate, ModifiedDate
FROM   dbo.GmailClientDraft
WHERE  GmailDraftID IS NOT NULL

GO
