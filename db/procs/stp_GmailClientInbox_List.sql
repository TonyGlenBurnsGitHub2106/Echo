SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientInbox_List') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientInbox_List
GO
CREATE PROCEDURE dbo.stp_GmailClientInbox_List
AS

/*

Gmail Client (Story 5569): every inbox Tony made, in tab order, with how many senders are
filed in each.

TEST
----

    EXEC dbo.stp_GmailClientInbox_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Gmail Client).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

SELECT i.GmailClientInboxID, i.InboxName, i.SortOrder, i.CreatedDate, i.ModifiedDate,
       (SELECT COUNT(*) FROM dbo.GmailClientSenderInbox s WHERE s.GmailClientInboxID = i.GmailClientInboxID) AS SenderCount
FROM   dbo.GmailClientInbox i
ORDER BY i.SortOrder, i.InboxName

GO
