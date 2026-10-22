SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_AppUser_RecordLogin') IS NOT NULL DROP PROCEDURE dbo.stp_AppUser_RecordLogin
GO
CREATE PROCEDURE dbo.stp_AppUser_RecordLogin
    @prmAppUserID   INT,
    @prmSucceeded    BIT,
    @prmMaxFailures  INT = 5,
    @prmLockMinutes  INT = 15
AS

/*

TEST
----

    EXEC stp_AppUser_RecordLogin 1, 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/21/2026  Felix        Story 5653 (Echo): the outcome of one sign-in. Success clears the failures and the lock and
                         stamps LastLoginDate; a failure counts, and the @prmMaxFailures-th in a row locks the person
                         out for @prmLockMinutes. Returns RowsAffected.

*/

SET NOCOUNT ON

IF @prmSucceeded = 1
    UPDATE dbo.AppUser
    SET    FailedAttempts = 0, LockedUntil = NULL, LastLoginDate = GETDATE()
    WHERE  AppUserID = @prmAppUserID
ELSE
    UPDATE dbo.AppUser
    SET    FailedAttempts = FailedAttempts + 1,
           LockedUntil    = CASE WHEN FailedAttempts + 1 >= @prmMaxFailures THEN DATEADD(MINUTE, @prmLockMinutes, GETDATE())
                                 ELSE LockedUntil END
    WHERE  AppUserID = @prmAppUserID

SELECT @@ROWCOUNT AS RowsAffected

GO
