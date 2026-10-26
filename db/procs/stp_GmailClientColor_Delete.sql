SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientColor_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientColor_Delete
GO
CREATE PROCEDURE dbo.stp_GmailClientColor_Delete
    @prmIDs VARCHAR(MAX) = NULL,   -- the colour rules to remove, comma separated
    @prmAll BIT          = 0       -- 1 = every colour: "clear colors and start over"
AS

/*

Gmail Client colours (Story 5608): removes the selected colour rules, or (@prmAll = 1) every one
of them. Only colours go -- no sender, domain, flag or message is touched. The audit table keeps
every removed rule, so a clear can be looked up afterwards. Returns Deleted.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientColor_Delete @prmAll = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, colours).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @n INT

DECLARE @ids TABLE (ID INT NOT NULL PRIMARY KEY)
INSERT INTO @ids (ID)
SELECT DISTINCT (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END)
FROM   dbo.Split(ISNULL(@prmIDs, ''), ',')
WHERE  (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END) IS NOT NULL

IF ISNULL(@prmAll, 0) = 0 AND NOT EXISTS (SELECT 1 FROM @ids)
BEGIN
    RAISERROR('Select at least one colour. Nothing was removed.', 16, 1)
    RETURN
END

BEGIN TRAN
    IF ISNULL(@prmAll, 0) = 1
        DELETE FROM dbo.GmailClientColor
    ELSE
        DELETE c FROM dbo.GmailClientColor c JOIN @ids x ON x.ID = c.GmailClientColorID
    SET @n = @@ROWCOUNT
COMMIT

SELECT @n AS Deleted

GO
