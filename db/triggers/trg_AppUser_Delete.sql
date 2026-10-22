SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_AppUser_Delete') IS NOT NULL DROP TRIGGER dbo.trg_AppUser_Delete
GO
/*

Trigger trg_AppUser_Delete on dbo.AppUser.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-10-21  Felix        Story 5653 (Echo): audit row into AppUser_LOG. The password hash is never copied.

*/

CREATE TRIGGER dbo.trg_AppUser_Delete ON dbo.AppUser AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.AppUser_LOG (IsInsertion, DeletedDate, AppUserID, Username, DisplayName, IsActive, IsAdmin, FailedAttempts, LockedUntil, SessionGeneration, LastLoginDate, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), AppUserID, Username, DisplayName, IsActive, IsAdmin, FailedAttempts, LockedUntil, SessionGeneration, LastLoginDate, CreatedDate, ModifiedDate FROM Deleted; END;

GO
