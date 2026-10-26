SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientColor_Save') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientColor_Save
GO
CREATE PROCEDURE dbo.stp_GmailClientColor_Save
    @prmGmailClientColorID INT           = 0,      -- 0 = new (or the rule that already matches the same thing)
    @prmColorKind          VARCHAR(8),             -- sender | domain | flag
    @prmMatchText          NVARCHAR(254) = NULL,   -- the address (sender) or the domain
    @prmGmailClientFlagID  INT           = NULL,   -- the flag (flag)
    @prmColorHex           VARCHAR(7)
AS

/*

Gmail Client colours (Story 5608): saves one colour rule. A sender is one full address, a domain is
example.com (a leading @ or a whole address is trimmed to its domain), a flag is a flag that
exists; the colour is #RRGGBB. Colouring something that already has a colour CHANGES that colour --
one rule per sender, domain and flag -- so a second tap never makes a second rule. Editing a rule
to match what another rule already matches is refused. Returns the rule as stp_GmailClientColor_List
shapes it.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientColor_Save 0, 'domain', N'example.com', NULL, '#8B5A2B'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, colours).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @id    INT          = ISNULL(@prmGmailClientColorID, 0)
DECLARE @kind  VARCHAR(8)   = LOWER(LTRIM(RTRIM(ISNULL(@prmColorKind, ''))))
DECLARE @text  NVARCHAR(254) = LOWER(LTRIM(RTRIM(@prmMatchText)))
DECLARE @flag  INT          = CASE WHEN @kind = 'flag' THEN @prmGmailClientFlagID END
DECLARE @hex   VARCHAR(7)   = UPPER(LTRIM(RTRIM(ISNULL(@prmColorHex, ''))))
DECLARE @other INT

IF @kind NOT IN ('sender', 'domain', 'flag')
BEGIN
    RAISERROR('Colour a sender, a domain or a flag. Nothing was saved.', 16, 1)
    RETURN
END
IF @hex NOT LIKE '#[0-9A-F][0-9A-F][0-9A-F][0-9A-F][0-9A-F][0-9A-F]'
BEGIN
    RAISERROR('A colour is written #RRGGBB, for example #8B5A2B. Nothing was saved.', 16, 1)
    RETURN
END

IF @kind = 'domain'
BEGIN
    -- greg@example.com or @example.com both mean example.com.
    IF CHARINDEX(N'@', @text) > 0 SET @text = SUBSTRING(@text, CHARINDEX(N'@', @text) + 1, 254)
    IF @text IS NULL OR @text NOT LIKE N'_%._%' OR CHARINDEX(N' ', @text) > 0
    BEGIN
        RAISERROR('A domain looks like example.com. Nothing was saved.', 16, 1)
        RETURN
    END
END
ELSE IF @kind = 'sender'
BEGIN
    IF @text IS NULL OR @text NOT LIKE N'_%@_%._%' OR CHARINDEX(N' ', @text) > 0
    BEGIN
        RAISERROR('A sender is one full email address, such as greg@example.com. Nothing was saved.', 16, 1)
        RETURN
    END
END
ELSE
BEGIN
    SET @text = NULL
    IF @flag IS NULL OR NOT EXISTS (SELECT 1 FROM dbo.GmailClientFlag WHERE GmailClientFlagID = @flag)
    BEGIN
        RAISERROR('That flag no longer exists. Nothing was saved.', 16, 1)
        RETURN
    END
END

IF @id > 0 AND NOT EXISTS (SELECT 1 FROM dbo.GmailClientColor WHERE GmailClientColorID = @id)
BEGIN
    RAISERROR('That colour no longer exists. Nothing was saved.', 16, 1)
    RETURN
END

SELECT @other = GmailClientColorID
FROM   dbo.GmailClientColor
WHERE  (@kind = 'flag' AND GmailClientFlagID = @flag)
   OR  (@kind <> 'flag' AND ColorKind = @kind AND MatchText = @text)

IF @id > 0 AND @other IS NOT NULL AND @other <> @id
BEGIN
    RAISERROR('That already has a colour of its own; change that one instead. Nothing was saved.', 16, 1)
    RETURN
END
IF @id = 0 SET @id = ISNULL(@other, 0)

BEGIN TRAN
    IF @id = 0
    BEGIN
        INSERT INTO dbo.GmailClientColor (ColorKind, MatchText, GmailClientFlagID, ColorHex)
        VALUES (@kind, @text, @flag, @hex)
        SET @id = SCOPE_IDENTITY()
    END
    ELSE
        UPDATE dbo.GmailClientColor
        SET    ColorKind = @kind, MatchText = @text, GmailClientFlagID = @flag, ColorHex = @hex, ModifiedDate = GETDATE()
        WHERE  GmailClientColorID = @id
          AND  (ColorKind <> @kind OR ISNULL(MatchText, N'') <> ISNULL(@text, N'')
                OR ISNULL(GmailClientFlagID, 0) <> ISNULL(@flag, 0) OR ColorHex <> @hex)
COMMIT

SELECT c.GmailClientColorID, c.ColorKind, c.MatchText, c.GmailClientFlagID, f.FlagName, c.ColorHex,
       c.CreatedDate, c.ModifiedDate
FROM   dbo.GmailClientColor c
       LEFT JOIN dbo.GmailClientFlag f ON f.GmailClientFlagID = c.GmailClientFlagID
WHERE  c.GmailClientColorID = @id

GO
