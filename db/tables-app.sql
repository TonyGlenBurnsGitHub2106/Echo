/*
The tables only Echo has -- the ones that did not come over with Solutions (Story 5653).

AppUser   the people who sign in to Echo. Passwords are PBKDF2 hashes made by the application
          (never stored readable). SessionGeneration is stamped into every refresh token; bumping
          it signs that person out everywhere.

No table carries an "Echo" prefix: Echo is its own system, and a table in it no more says "Echo"
than a Leonardo table says "Leonardo". (The first build called this table EchoUser; the migration
below renames it in place, rows kept, on any database that still has the old name.)

Idempotent: safe to run again.
*/

SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
-- ---- Migration: EchoUser -> AppUser (2026-10-21). Renames in place; no row is lost. ----
-- The old procs and triggers go first: the trigger scripts later create trg_AppUser_*, and an old
-- trg_EchoUser_* left on the renamed table would write every change to the log twice.
IF OBJECT_ID('dbo.EchoUser') IS NOT NULL AND OBJECT_ID('dbo.AppUser') IS NULL
BEGIN
    IF OBJECT_ID('dbo.trg_EchoUser_Insert') IS NOT NULL DROP TRIGGER dbo.trg_EchoUser_Insert
    IF OBJECT_ID('dbo.trg_EchoUser_Update') IS NOT NULL DROP TRIGGER dbo.trg_EchoUser_Update
    IF OBJECT_ID('dbo.trg_EchoUser_Delete') IS NOT NULL DROP TRIGGER dbo.trg_EchoUser_Delete
    IF OBJECT_ID('dbo.stp_EchoUser_GetForLogin') IS NOT NULL DROP PROCEDURE dbo.stp_EchoUser_GetForLogin
    IF OBJECT_ID('dbo.stp_EchoUser_RecordLogin') IS NOT NULL DROP PROCEDURE dbo.stp_EchoUser_RecordLogin
    IF OBJECT_ID('dbo.stp_EchoUser_Save')        IS NOT NULL DROP PROCEDURE dbo.stp_EchoUser_Save
    IF OBJECT_ID('dbo.stp_EchoUser_EndSessions') IS NOT NULL DROP PROCEDURE dbo.stp_EchoUser_EndSessions

    EXEC sp_rename 'dbo.EchoUser', 'AppUser'
    EXEC sp_rename 'dbo.AppUser.EchoUserID', 'AppUserID', 'COLUMN'
    EXEC sp_rename 'dbo.PK_EchoUser', 'PK_AppUser'
    EXEC sp_rename 'dbo.AppUser.UX_EchoUser_Username', 'UX_AppUser_Username', 'INDEX'
    EXEC sp_rename 'dbo.DF_EchoUser_IsActive', 'DF_AppUser_IsActive'
    EXEC sp_rename 'dbo.DF_EchoUser_IsAdmin', 'DF_AppUser_IsAdmin'
    EXEC sp_rename 'dbo.DF_EchoUser_FailedAttempts', 'DF_AppUser_FailedAttempts'
    EXEC sp_rename 'dbo.DF_EchoUser_SessionGeneration', 'DF_AppUser_SessionGeneration'
    EXEC sp_rename 'dbo.DF_EchoUser_CreatedDate', 'DF_AppUser_CreatedDate'
END
GO
IF OBJECT_ID('dbo.EchoUser_LOG') IS NOT NULL AND OBJECT_ID('dbo.AppUser_LOG') IS NULL
BEGIN
    EXEC sp_rename 'dbo.EchoUser_LOG', 'AppUser_LOG'
    EXEC sp_rename 'dbo.AppUser_LOG.EchoUser_LOGID', 'AppUser_LOGID', 'COLUMN'
    EXEC sp_rename 'dbo.AppUser_LOG.EchoUserID', 'AppUserID', 'COLUMN'
    EXEC sp_rename 'dbo.PK_EchoUser_LOG', 'PK_AppUser_LOG'
    IF OBJECT_ID('dbo.DF_EchoUser_LOG_IsInsertion') IS NOT NULL EXEC sp_rename 'dbo.DF_EchoUser_LOG_IsInsertion', 'DF_AppUser_LOG_IsInsertion'
    IF OBJECT_ID('dbo.DF_EchoUser_LOG_LoggedDate')  IS NOT NULL EXEC sp_rename 'dbo.DF_EchoUser_LOG_LoggedDate', 'DF_AppUser_LOG_LoggedDate'
END
GO
IF OBJECT_ID('dbo.AppUser') IS NULL
BEGIN
CREATE TABLE dbo.AppUser (
    AppUserID         INT IDENTITY(1,1) NOT NULL,
    Username           NVARCHAR(100)     NOT NULL,
    DisplayName        NVARCHAR(200)     NOT NULL,
    PasswordHash       VARCHAR(400)      NOT NULL,
    IsActive           BIT               NOT NULL CONSTRAINT DF_AppUser_IsActive DEFAULT (1),
    IsAdmin            BIT               NOT NULL CONSTRAINT DF_AppUser_IsAdmin DEFAULT (1),
    FailedAttempts     INT               NOT NULL CONSTRAINT DF_AppUser_FailedAttempts DEFAULT (0),
    LockedUntil        DATETIME          NULL,
    SessionGeneration  INT               NOT NULL CONSTRAINT DF_AppUser_SessionGeneration DEFAULT (1),
    LastLoginDate      DATETIME          NULL,
    CreatedDate        DATETIME          NOT NULL CONSTRAINT DF_AppUser_CreatedDate DEFAULT (GETDATE()),
    ModifiedDate       DATETIME          NULL,
    CONSTRAINT PK_AppUser PRIMARY KEY CLUSTERED (AppUserID)
)
CREATE UNIQUE NONCLUSTERED INDEX UX_AppUser_Username ON dbo.AppUser (Username)
END
GO
IF OBJECT_ID('dbo.AppUser_LOG') IS NULL
BEGIN
CREATE TABLE dbo.AppUser_LOG (
    AppUser_LOGID     INT IDENTITY(1,1) NOT NULL,
    IsInsertion        BIT               NOT NULL CONSTRAINT DF_AppUser_LOG_IsInsertion DEFAULT (0),
    LoggedDate         DATETIME          NOT NULL CONSTRAINT DF_AppUser_LOG_LoggedDate DEFAULT (GETDATE()),
    DeletedDate        DATETIME          NULL,
    AppUserID         INT               NOT NULL,
    Username           NVARCHAR(100)     NULL,
    DisplayName        NVARCHAR(200)     NULL,
    IsActive           BIT               NULL,
    IsAdmin            BIT               NULL,
    FailedAttempts     INT               NULL,
    LockedUntil        DATETIME          NULL,
    SessionGeneration  INT               NULL,
    LastLoginDate      DATETIME          NULL,
    CreatedDate        DATETIME          NULL,
    ModifiedDate       DATETIME          NULL,
    CONSTRAINT PK_AppUser_LOG PRIMARY KEY CLUSTERED (AppUser_LOGID)
)
END
GO
-- Added after the first install (Review checklist: a _LOG IsInsertion defaults to 0).
IF OBJECT_ID('dbo.DF_AppUser_LOG_IsInsertion') IS NULL
    ALTER TABLE dbo.AppUser_LOG ADD CONSTRAINT DF_AppUser_LOG_IsInsertion DEFAULT (0) FOR IsInsertion
GO
