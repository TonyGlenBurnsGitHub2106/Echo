SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_AppUser_Update') IS NOT NULL DROP TRIGGER dbo.trg_AppUser_Update
GO
/*

Trigger trg_AppUser_Update on dbo.AppUser.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-21  Felix        Story 5653 (Echo): audit row into AppUser_LOG. The password hash is never copied.

*/

CREATE TRIGGER dbo.trg_AppUser_Update ON dbo.AppUser AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.AppUser_LOG (IsInsertion, AppUserID, Username, DisplayName, IsActive, IsAdmin, FailedAttempts, LockedUntil, SessionGeneration, LastLoginDate, CreatedDate, ModifiedDate)
    SELECT 0, AppUserID, Username, DisplayName, IsActive, IsAdmin, FailedAttempts, LockedUntil, SessionGeneration, LastLoginDate, CreatedDate, ModifiedDate FROM Inserted; END;

GO
