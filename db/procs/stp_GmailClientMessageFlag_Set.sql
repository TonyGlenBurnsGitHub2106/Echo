SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientMessageFlag_Set') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientMessageFlag_Set
GO
CREATE PROCEDURE dbo.stp_GmailClientMessageFlag_Set
    @prmGmailClientMessageID INT,
    @prmFlagNames            NVARCHAR(2000) = NULL   -- the message's flags, one name per line; blank = none
AS

/*

Gmail Client flags (Story 5608): sets EXACTLY the flags one message carries -- what the reader's
Flags field holds. Each name is found, or made when new ("define the flags and create if not
exist"); a flag no longer named comes off. Running it twice with the same names changes nothing.
At most 20 flags on a message, each name 60 characters at most. Returns the message's flags
(GmailClientFlagID, FlagName), in flag order.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientMessageFlag_Set 1, N'Taxes'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @names TABLE (FlagName NVARCHAR(60) NOT NULL PRIMARY KEY)
DECLARE @s NVARCHAR(2000) = REPLACE(ISNULL(@prmFlagNames, N''), NCHAR(13), N'')
DECLARE @p INT, @one NVARCHAR(2000)

IF NOT EXISTS (SELECT 1 FROM dbo.GmailClientMessage WHERE GmailClientMessageID = @prmGmailClientMessageID AND IsHidden = 0)
BEGIN
    RAISERROR('That message no longer exists. Nothing was saved.', 16, 1)
    RETURN
END

WHILE LEN(@s) > 0
BEGIN
    SET @p = CHARINDEX(NCHAR(10), @s)
    SET @one = LTRIM(RTRIM(CASE WHEN @p > 0 THEN LEFT(@s, @p - 1) ELSE @s END))
    SET @s = CASE WHEN @p > 0 THEN SUBSTRING(@s, @p + 1, 2000) ELSE N'' END
    IF @one = N'' CONTINUE
    IF LEN(@one) > 60
    BEGIN
        RAISERROR('Keep a flag name to 60 characters. Nothing was saved.', 16, 1)
        RETURN
    END
    IF NOT EXISTS (SELECT 1 FROM @names WHERE FlagName = @one)
        INSERT INTO @names (FlagName) VALUES (@one)
END

IF (SELECT COUNT(*) FROM @names) > 20
BEGIN
    RAISERROR('A message can carry at most 20 flags. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN
    INSERT INTO dbo.GmailClientFlag (FlagName)
    SELECT n.FlagName FROM @names n
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.GmailClientFlag f WHERE f.FlagName = n.FlagName)

    DELETE mf
    FROM   dbo.GmailClientMessageFlag mf
           JOIN dbo.GmailClientFlag f ON f.GmailClientFlagID = mf.GmailClientFlagID
    WHERE  mf.GmailClientMessageID = @prmGmailClientMessageID
      AND  NOT EXISTS (SELECT 1 FROM @names n WHERE n.FlagName = f.FlagName)

    INSERT INTO dbo.GmailClientMessageFlag (GmailClientMessageID, GmailClientFlagID)
    SELECT @prmGmailClientMessageID, f.GmailClientFlagID
    FROM   @names n JOIN dbo.GmailClientFlag f ON f.FlagName = n.FlagName
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.GmailClientMessageFlag mf
                       WHERE mf.GmailClientMessageID = @prmGmailClientMessageID AND mf.GmailClientFlagID = f.GmailClientFlagID)
COMMIT

SELECT f.GmailClientFlagID, f.FlagName
FROM   dbo.GmailClientMessageFlag mf JOIN dbo.GmailClientFlag f ON f.GmailClientFlagID = mf.GmailClientFlagID
WHERE  mf.GmailClientMessageID = @prmGmailClientMessageID
ORDER BY f.SortOrder, f.FlagName

GO
