SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientInbox_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientInbox_Reorder
GO
CREATE PROCEDURE dbo.stp_GmailClientInbox_Reorder
    @prmGmailClientInboxIDs VARCHAR(8000)
AS

/*

Gmail Client (Story 5569): puts the inboxes in the order given -- a comma list of
GmailClientInboxIDs, first one first -- which is the order of their tabs (between All / Follow up
and Unclassified). The Inboxes dialog's Arrange drags them. Listed inboxes get SortOrder 10, 20,
30 ...; any inbox not listed (one added on another device meanwhile) keeps its relative order
after them. Every id must be a real inbox, or nothing is saved. Running it twice with the same
list writes the same values. Returns stp_GmailClientInbox_List.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientInbox_Reorder @prmGmailClientInboxIDs = '3,2,4'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New (Story 5569, Tony's comment: Arrange on the Inboxes dialog).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @order TABLE (Position INT IDENTITY(1,1), GmailClientInboxID INT)
INSERT INTO @order (GmailClientInboxID)
SELECT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(ISNULL(@prmGmailClientInboxIDs, ''), ',')
WHERE  LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(items)) <> '' AND LEN(LTRIM(RTRIM(items))) <= 9

IF NOT EXISTS (SELECT 1 FROM @order)
BEGIN
    RAISERROR('Give the inboxes in their new order. Nothing was saved.', 16, 1)
    RETURN
END
IF EXISTS (SELECT 1 FROM @order o WHERE NOT EXISTS (SELECT 1 FROM dbo.GmailClientInbox i WHERE i.GmailClientInboxID = o.GmailClientInboxID))
BEGIN
    RAISERROR('One of those inboxes no longer exists. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRANSACTION

;WITH wanted AS (
    SELECT GmailClientInboxID, MIN(Position) AS Position FROM @order GROUP BY GmailClientInboxID
), ranked AS (
    SELECT i.GmailClientInboxID,
           ROW_NUMBER() OVER (ORDER BY CASE WHEN w.Position IS NULL THEN 1 ELSE 0 END,
                                       w.Position, i.SortOrder, i.InboxName) AS rn
    FROM   dbo.GmailClientInbox i WITH (UPDLOCK, HOLDLOCK)
    LEFT JOIN wanted w ON w.GmailClientInboxID = i.GmailClientInboxID
)
UPDATE i
SET    SortOrder = r.rn * 10, ModifiedDate = GETDATE()
FROM   dbo.GmailClientInbox i
JOIN   ranked r ON r.GmailClientInboxID = i.GmailClientInboxID
WHERE  i.SortOrder <> r.rn * 10

COMMIT TRANSACTION

EXEC dbo.stp_GmailClientInbox_List

GO
