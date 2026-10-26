SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientColor_SetMany') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientColor_SetMany
GO
CREATE PROCEDURE dbo.stp_GmailClientColor_SetMany
    @prmIDs      VARCHAR(MAX),   -- the selected colour rules, comma separated
    @prmColorHex VARCHAR(7)
AS

/*

Gmail Client colours (Story 5608): "select and edit a set of them" -- gives every selected colour
rule the same new colour. A rule already that colour is left alone. Returns Updated.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientColor_SetMany '1,2', '#2F5D3A'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, colours).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @hex VARCHAR(7) = UPPER(LTRIM(RTRIM(ISNULL(@prmColorHex, ''))))
DECLARE @n INT

IF @hex NOT LIKE '#[0-9A-F][0-9A-F][0-9A-F][0-9A-F][0-9A-F][0-9A-F]'
BEGIN
    RAISERROR('A colour is written #RRGGBB, for example #8B5A2B. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @ids TABLE (ID INT NOT NULL PRIMARY KEY)
INSERT INTO @ids (ID)
SELECT DISTINCT (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END)
FROM   dbo.Split(ISNULL(@prmIDs, ''), ',')
WHERE  (CASE WHEN LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(items))) BETWEEN 1 AND 9 THEN CONVERT(INT, LTRIM(RTRIM(items))) END) IS NOT NULL

IF NOT EXISTS (SELECT 1 FROM @ids)
BEGIN
    RAISERROR('Select at least one colour. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    UPDATE c
    SET    ColorHex = @hex, ModifiedDate = GETDATE()
    FROM   dbo.GmailClientColor c JOIN @ids x ON x.ID = c.GmailClientColorID
    WHERE  c.ColorHex <> @hex
    SET @n = @@ROWCOUNT
COMMIT

SELECT @n AS Updated

GO
