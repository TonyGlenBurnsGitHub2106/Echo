SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_AppUser_GetForLogin') IS NOT NULL DROP PROCEDURE dbo.stp_AppUser_GetForLogin
GO
CREATE PROCEDURE dbo.stp_AppUser_GetForLogin
    @prmUsername NVARCHAR(100) = NULL,
    @prmAppUserID INT = NULL
AS

/*

TEST
----

    EXEC stp_AppUser_GetForLogin @prmUsername = 'tony'
    EXEC stp_AppUser_GetForLogin @prmAppUserID = 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/21/2026  Felix        Story 5653 (Echo): one person by user name (case-insensitive) or by id, with the hash, the
                         lockout and the session generation the sign-in needs. Inactive people are returned too;
                         the application refuses them.

*/

SET NOCOUNT ON

SELECT TOP 1 AppUserID, Username, DisplayName, PasswordHash, IsActive, IsAdmin, FailedAttempts, LockedUntil,
       SessionGeneration, LastLoginDate
FROM   dbo.AppUser
WHERE  (@prmAppUserID IS NOT NULL AND AppUserID = @prmAppUserID)
   OR  (@prmAppUserID IS NULL AND Username = LTRIM(RTRIM(ISNULL(@prmUsername, N''))))

GO
