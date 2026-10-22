SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_AppUser_Insert') IS NOT NULL DROP TRIGGER dbo.trg_AppUser_Insert
GO
/*

Trigger trg_AppUser_Insert on dbo.AppUser.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-21  Felix        Story 5653 (Echo): audit row into AppUser_LOG. The password hash is never copied.

*/

CREATE TRIGGER dbo.trg_AppUser_Insert ON dbo.AppUser AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.AppUser_LOG (IsInsertion, AppUserID, Username, DisplayName, IsActive, IsAdmin, FailedAttempts, LockedUntil, SessionGeneration, LastLoginDate, CreatedDate, ModifiedDate)
    SELECT 1, AppUserID, Username, DisplayName, IsActive, IsAdmin, FailedAttempts, LockedUntil, SessionGeneration, LastLoginDate, CreatedDate, ModifiedDate FROM Inserted; END;

GO
