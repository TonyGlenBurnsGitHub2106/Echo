SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientThread_MessageIDs') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientThread_MessageIDs
GO
CREATE PROCEDURE dbo.stp_GmailClientThread_MessageIDs
    @prmIDs VARCHAR(MAX)   -- inbox message ids, comma separated: one per conversation row
AS

/*

Gmail Client conversations (Story 5608): in the conversation view a row IS its conversation, so
Read, Unread, Delete, Follow up and Flag on a row act on every message of it. Given the rows'
message ids, this returns every inbox message (GmailClientMessageID) in their Gmail threads -- the
given ones too -- that is not hidden and not from a blacklisted sender. A message with no thread
id stands for itself. At most 500 ids in; the answer may be longer.

TEST
----

    EXEC dbo.stp_GmailClientThread_MessageIDs '1,2,3'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, conversations).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

DECLARE @ids TABLE (ID INT NOT NULL PRIMARY KEY)
INSERT INTO @ids (ID)
SELECT DISTINCT (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END)
FROM   dbo.Split(ISNULL(@prmIDs, ''), ',')
WHERE  (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END) IS NOT NULL

IF NOT EXISTS (SELECT 1 FROM @ids)
BEGIN
    RAISERROR('Select at least one conversation.', 16, 1)
    RETURN
END
IF (SELECT COUNT(*) FROM @ids) > 500
BEGIN
    RAISERROR('Select at most 500 conversations at a time.', 16, 1)
    RETURN
END

SELECT DISTINCT m2.GmailClientMessageID
FROM   @ids x
       JOIN dbo.GmailClientMessage m ON m.GmailClientMessageID = x.ID
       JOIN dbo.GmailClientMessage m2
         ON (m.ThreadID IS NOT NULL AND m2.ThreadID = m.ThreadID)
         OR (m.ThreadID IS NULL AND m2.GmailClientMessageID = m.GmailClientMessageID)
WHERE  m2.IsHidden = 0
  AND  NOT EXISTS (SELECT 1 FROM dbo.MailTriageRule r WHERE r.SenderKey = m2.SenderKey AND r.Disposition = 'black')
ORDER BY m2.GmailClientMessageID

GO
