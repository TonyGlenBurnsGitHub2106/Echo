SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientFlag_Save') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientFlag_Save
GO
CREATE PROCEDURE dbo.stp_GmailClientFlag_Save
    @prmGmailClientFlagID INT          = 0,   -- 0 = a new flag (or the one already named so)
    @prmFlagName          NVARCHAR(60)
AS

/*

Gmail Client flags (Story 5608): makes a flag, or renames one. A new flag whose name already
exists is that flag -- find or make, so a double tap never makes two. Renaming to another flag's
name is refused. Returns the flag (GmailClientFlagID, FlagName, SortOrder).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_GmailClientFlag_Save 0, N'Taxes'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-08  Felix        New (Story 5608, flags).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @name NVARCHAR(60) = NULLIF(LTRIM(RTRIM(@prmFlagName)), N'')
DECLARE @id INT = ISNULL(@prmGmailClientFlagID, 0)
DECLARE @other INT

IF @name IS NULL
BEGIN
    RAISERROR('A flag needs a name. Nothing was saved.', 16, 1)
    RETURN
END
IF LEN(@prmFlagName) > 60
BEGIN
    RAISERROR('Keep a flag name to 60 characters. Nothing was saved.', 16, 1)
    RETURN
END
IF @id > 0 AND NOT EXISTS (SELECT 1 FROM dbo.GmailClientFlag WHERE GmailClientFlagID = @id)
BEGIN
    RAISERROR('That flag no longer exists. Nothing was saved.', 16, 1)
    RETURN
END

SELECT @other = GmailClientFlagID FROM dbo.GmailClientFlag WHERE FlagName = @name
IF @id > 0 AND @other IS NOT NULL AND @other <> @id
BEGIN
    RAISERROR('There is already a flag named %s. Nothing was saved.', 16, 1, @name)
    RETURN
END

BEGIN TRAN
    IF @id = 0
    BEGIN
        IF @other IS NOT NULL
            SET @id = @other
        ELSE
        BEGIN
            INSERT INTO dbo.GmailClientFlag (FlagName) VALUES (@name)
            SET @id = SCOPE_IDENTITY()
        END
    END
    ELSE
    BEGIN
        UPDATE dbo.GmailClientFlag
        SET    FlagName = @name, ModifiedDate = GETDATE()
        WHERE  GmailClientFlagID = @id AND FlagName <> @name COLLATE Latin1_General_BIN
    END
COMMIT

SELECT GmailClientFlagID, FlagName, SortOrder FROM dbo.GmailClientFlag WHERE GmailClientFlagID = @id

GO
