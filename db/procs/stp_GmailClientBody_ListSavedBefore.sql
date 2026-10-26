SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientBody_ListSavedBefore') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientBody_ListSavedBefore
GO
CREATE PROCEDURE dbo.stp_GmailClientBody_ListSavedBefore
    @prmSavedBefore DATETIME,
    @prmTop         INT = 100
AS

/*

Gmail Client (Story 5591): the saved bodies -- inbox and sent -- that were saved before a moment and
that Gmail still has, newest message first. Re-reading these from Gmail re-saves them with the
reader's own formatting kept (the save replaces the old body and stamps a new SavedDate, so a
repeated call moves on to the next ones and finishes when nothing is left).

Kind is 'inbox' or 'sent'; ID is GmailClientMessageID or GmailClientSentMessageID.

TEST
----

    EXEC dbo.stp_GmailClientBody_ListSavedBefore '2026-10-07 09:00', 20

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-07  Felix        New (Story 5591, email rendering).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

DECLARE @top INT = CASE WHEN ISNULL(@prmTop, 0) <= 0 THEN 100 WHEN @prmTop > 1000 THEN 1000 ELSE @prmTop END

SELECT TOP (@top) x.Kind, x.ID, x.GmailID
FROM (
    SELECT 'inbox' AS Kind, m.GmailClientMessageID AS ID, m.GmailID, m.ReceivedDate AS SortDate
    FROM   dbo.GmailClientMessageBody b
    JOIN   dbo.GmailClientMessage m ON m.GmailClientMessageID = b.GmailClientMessageID
    WHERE  b.IsGoneFromGmail = 0 AND b.SavedDate < @prmSavedBefore
    UNION ALL
    SELECT 'sent', s.GmailClientSentMessageID, s.GmailID, s.SentDate
    FROM   dbo.GmailClientSentMessageBody b
    JOIN   dbo.GmailClientSentMessage s ON s.GmailClientSentMessageID = b.GmailClientSentMessageID
    WHERE  b.IsGoneFromGmail = 0 AND b.SavedDate < @prmSavedBefore
) x
ORDER BY x.SortDate DESC, x.ID DESC

GO
