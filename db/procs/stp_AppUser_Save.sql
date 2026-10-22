SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_AppUser_Save') IS NOT NULL DROP PROCEDURE dbo.stp_AppUser_Save
GO
CREATE PROCEDURE dbo.stp_AppUser_Save
    @prmUsername      NVARCHAR(100),
    @prmDisplayName   NVARCHAR(200),
    @prmPasswordHash  VARCHAR(400),
    @prmIsAdmin       BIT = 1
AS

/*

TEST
----

    EXEC stp_AppUser_Save 'tony', 'Tony', '<pbkdf2 hash>'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/21/2026  Felix        Story 5653 (Echo): create a person, or -- when the user name already exists -- set a new
                         password (and display name). Setting a password clears the lock and bumps the session
                         generation, so every session that person had open is signed out. Returns AppUserID.

*/

SET NOCOUNT ON

SET @prmUsername    = LTRIM(RTRIM(ISNULL(@prmUsername, N'')))
SET @prmDisplayName = NULLIF(LTRIM(RTRIM(ISNULL(@prmDisplayName, N''))), N'')
IF @prmUsername = N'' BEGIN RAISERROR('A user needs a user name. Nothing was saved.', 16, 1) RETURN END
IF @prmUsername LIKE N'%[^A-Za-z0-9._-]%' BEGIN
    RAISERROR('A user name is letters, digits, dots, hyphens and underscores. Nothing was saved.', 16, 1) RETURN
END
IF LEN(ISNULL(@prmPasswordHash, '')) < 20 BEGIN RAISERROR('A user needs a password. Nothing was saved.', 16, 1) RETURN END

DECLARE @id INT = (SELECT AppUserID FROM dbo.AppUser WHERE Username = @prmUsername)

IF @id IS NULL BEGIN
    INSERT INTO dbo.AppUser (Username, DisplayName, PasswordHash, IsAdmin)
    VALUES (@prmUsername, ISNULL(@prmDisplayName, @prmUsername), @prmPasswordHash, ISNULL(@prmIsAdmin, 1))
    SET @id = SCOPE_IDENTITY()
END ELSE BEGIN
    UPDATE dbo.AppUser
    SET    PasswordHash = @prmPasswordHash,
           DisplayName = ISNULL(@prmDisplayName, DisplayName),
           IsActive = 1, FailedAttempts = 0, LockedUntil = NULL,
           SessionGeneration = SessionGeneration + 1,
           ModifiedDate = GETDATE()
    WHERE  AppUserID = @id
END

SELECT @id AS AppUserID

GO
