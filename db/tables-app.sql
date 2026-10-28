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
-- ---- EntityLoginSetupXRef_LOG (Story 5653, 2026-10-26) ----
-- Leonardo's EntityLoginSetupXRef has no audit twin; every Echo table gets one (house rule).
IF OBJECT_ID('dbo.EntityLoginSetupXRef_LOG') IS NULL
CREATE TABLE dbo.EntityLoginSetupXRef_LOG (
    EntityLoginSetupXRef_LOGID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_EntityLoginSetupXRef_LOG PRIMARY KEY CLUSTERED,
    LogCreatedDate         DATETIME NOT NULL CONSTRAINT DF_EntityLoginSetupXRef_LOG_LCD DEFAULT (GETDATE()),
    IsInsertion            BIT      NOT NULL CONSTRAINT DF_EntityLoginSetupXRef_LOG_INS DEFAULT (0),
    DeletedDate            DATETIME NULL,
    EntityLoginSetupXRefID INT      NULL,
    EntityID               INT      NULL,
    LoginSetupID           INT      NULL,
    EffectiveDate          DATETIME NULL,
    ThroughDate            DATETIME NULL,
    CreatedDate            DATETIME NULL
)
GO
-- ---- Email Client removed (Story 5654, 2026-10-28) ----
-- Tony's company does not allow it: new Outlook has no COM, classic Outlook is not allowed to run,
-- and Graph needs an admin's approval. Tony: "We could probably get around it, but it would violate
-- the company security policy, so remove it." This takes the email objects out of a database that
-- an earlier install gave them to. Code objects (procs, functions, views) always go. The TABLES go
-- only when every one of them is empty -- mail or a list someone typed is never dropped by an
-- update; a database that holds any is left alone and says so here.
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql = @sql + N'DROP ' + CASE o.type WHEN 'P' THEN N'PROCEDURE' WHEN 'V' THEN N'VIEW' ELSE N'FUNCTION' END
            + N' dbo.' + QUOTENAME(o.name) + N'; '
FROM sys.objects o
WHERE o.schema_id = SCHEMA_ID('dbo')
  AND o.type IN ('P', 'V', 'FN', 'IF', 'TF')
  AND (o.name LIKE 'stp[_]GmailClient%' OR o.name LIKE 'stp[_]MailTriage%' OR o.name LIKE 'fn[_]GmailClient%'
       OR o.name LIKE 'vw[_]GmailClient%' OR o.name = 'fn_IsoDate_IsValid');
IF @sql <> N'' EXEC (@sql);
GO
DECLARE @rows BIGINT = 0, @n BIGINT, @t SYSNAME, @count NVARCHAR(MAX);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR
    SELECT name FROM sys.tables
    WHERE (name LIKE 'GmailClient%' OR name LIKE 'MailTriageRule%') AND name NOT LIKE '%[_]LOG';
OPEN c; FETCH NEXT FROM c INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @count = N'SELECT @n = COUNT_BIG(*) FROM dbo.' + QUOTENAME(@t);
    EXEC sp_executesql @count, N'@n BIGINT OUTPUT', @n = @n OUTPUT;
    SET @rows = @rows + @n;
    FETCH NEXT FROM c INTO @t;
END
CLOSE c; DEALLOCATE c;
IF @rows > 0
    PRINT 'Story 5654: the Email Client tables hold ' + CAST(@rows AS VARCHAR(20)) + ' row(s), so they were kept.';
ELSE
BEGIN
    DECLARE @sql NVARCHAR(MAX) = N'';
    SELECT @sql = @sql + N'ALTER TABLE dbo.' + QUOTENAME(OBJECT_NAME(fk.parent_object_id))
                + N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N'; '
    FROM sys.foreign_keys fk
    WHERE OBJECT_NAME(fk.referenced_object_id) LIKE 'GmailClient%' OR OBJECT_NAME(fk.referenced_object_id) LIKE 'MailTriageRule%';
    SELECT @sql = @sql + N'DROP TABLE dbo.' + QUOTENAME(name) + N'; '
    FROM sys.tables WHERE name LIKE 'GmailClient%' OR name LIKE 'MailTriageRule%';
    IF @sql <> N'' EXEC (@sql);
END
GO
-- The menu no longer has the Email Client; a Quick Launch tile pointing at it would open nothing.
IF OBJECT_ID('dbo.QuickLaunch') IS NOT NULL
    DELETE FROM dbo.QuickLaunch WHERE Url LIKE '%/gmail-client/%';
GO
