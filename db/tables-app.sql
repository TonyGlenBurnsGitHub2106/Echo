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

-- ---- Questions and Decisions (Story 5695, 2026-11-04) ----
-- The Solutions upgrade Tony asked for in Echo first ("for echo only ... I will test drive that and then we
-- can consider Leonardo Solutions"). Named the way Leonardo would name them, so they can be carried back.
--
--   SolutionQuestion   something the solution needs answered. Open until a decision answers it (or it is
--                      answered by hand, or dropped). AnsweredBySolutionDecisionID names the decision.
--   SolutionDecision   what was decided: a title, the context, the options weighed and the outcome, with a
--                      status (proposed, accepted, superseded). A changed mind is a NEW decision that
--                      supersedes the old one (SupersededBySolutionDecisionID), never an edit of the outcome.
--                      SolutionLogID is the journal entry that records it -- written with it in the same
--                      transaction, or the entry it was made from.
--
-- Both are linkable records (kinds 'question' and 'decision', below), so a decision is linked to the
-- questions it answers and the items it settles. No foreign keys, like the rest of the Solutions family:
-- stp_Solution_Delete and the two delete procs clean up. Each has a _LOG twin kept by its triggers.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'SolutionQuestion')
BEGIN
    CREATE TABLE dbo.SolutionQuestion (
        SolutionQuestionID           INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_SolutionQuestion PRIMARY KEY CLUSTERED,
        SolutionID                   INT            NOT NULL,
        QuestionText                 NVARCHAR(800)  NOT NULL,
        QuestionDetail               NVARCHAR(MAX)  NULL,
        StatusCode                   VARCHAR(20)    NOT NULL CONSTRAINT DF_SolutionQuestion_StatusCode DEFAULT ('open'),
        AskedByMemberID              INT            NULL,
        OwnerMemberID                INT            NULL,      -- who should answer it
        AnsweredBySolutionDecisionID INT            NULL,
        AnsweredDate                 DATETIME       NULL,
        SolutionLogID                INT            NULL,      -- the journal entry it was made from, if any
        ModifiedBy                   VARCHAR(20)    NULL,
        CreatedDate                  DATETIME       NOT NULL CONSTRAINT DF_SolutionQuestion_CreatedDate DEFAULT (GETDATE()),
        ModifiedDate                 DATETIME       NULL,
        CONSTRAINT CK_SolutionQuestion_StatusCode CHECK (StatusCode IN ('open', 'answered', 'dropped'))
    );
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SolutionQuestion_Solution' AND object_id = OBJECT_ID('dbo.SolutionQuestion'))
    CREATE INDEX IX_SolutionQuestion_Solution ON dbo.SolutionQuestion (SolutionID, StatusCode);
GO
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'SolutionDecision')
BEGIN
    CREATE TABLE dbo.SolutionDecision (
        SolutionDecisionID             INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_SolutionDecision PRIMARY KEY CLUSTERED,
        SolutionID                     INT            NOT NULL,
        DecisionTitle                  NVARCHAR(400)  NOT NULL,
        Context                        NVARCHAR(MAX)  NULL,    -- why it came up
        OptionsText                    NVARCHAR(MAX)  NULL,    -- what was weighed
        Outcome                        NVARCHAR(MAX)  NULL,    -- what was decided
        StatusCode                     VARCHAR(20)    NOT NULL CONSTRAINT DF_SolutionDecision_StatusCode DEFAULT ('accepted'),
        SupersededBySolutionDecisionID INT            NULL,
        DecidedDate                    DATETIME       NULL,
        SolutionLogID                  INT            NULL,    -- the journal entry that records it
        DecidedByMemberID              INT            NULL,
        ModifiedBy                     VARCHAR(20)    NULL,
        CreatedDate                    DATETIME       NOT NULL CONSTRAINT DF_SolutionDecision_CreatedDate DEFAULT (GETDATE()),
        ModifiedDate                   DATETIME       NULL,
        CONSTRAINT CK_SolutionDecision_StatusCode CHECK (StatusCode IN ('proposed', 'accepted', 'superseded'))
    );
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SolutionDecision_Solution' AND object_id = OBJECT_ID('dbo.SolutionDecision'))
    CREATE INDEX IX_SolutionDecision_Solution ON dbo.SolutionDecision (SolutionID, StatusCode);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SolutionDecision_Log' AND object_id = OBJECT_ID('dbo.SolutionDecision'))
    CREATE INDEX IX_SolutionDecision_Log ON dbo.SolutionDecision (SolutionLogID);
GO
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'SolutionQuestion_LOG')
BEGIN
    CREATE TABLE dbo.SolutionQuestion_LOG (
        SolutionQuestion_LOGID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_SolutionQuestion_LOG PRIMARY KEY CLUSTERED,
        LogCreatedDate DATETIME NOT NULL CONSTRAINT DF_SolutionQuestion_LOG_LCD DEFAULT (GETDATE()),
        IsInsertion    BIT      NOT NULL CONSTRAINT DF_SolutionQuestion_LOG_INS DEFAULT (0),
        DeletedDate    DATETIME NULL,
        SolutionQuestionID INT NULL, SolutionID INT NULL, QuestionText NVARCHAR(800) NULL, QuestionDetail NVARCHAR(MAX) NULL,
        StatusCode VARCHAR(20) NULL, AskedByMemberID INT NULL, OwnerMemberID INT NULL, AnsweredBySolutionDecisionID INT NULL,
        AnsweredDate DATETIME NULL, SolutionLogID INT NULL, ModifiedBy VARCHAR(20) NULL, CreatedDate DATETIME NULL, ModifiedDate DATETIME NULL
    );
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'SolutionDecision_LOG')
BEGIN
    CREATE TABLE dbo.SolutionDecision_LOG (
        SolutionDecision_LOGID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_SolutionDecision_LOG PRIMARY KEY CLUSTERED,
        LogCreatedDate DATETIME NOT NULL CONSTRAINT DF_SolutionDecision_LOG_LCD DEFAULT (GETDATE()),
        IsInsertion    BIT      NOT NULL CONSTRAINT DF_SolutionDecision_LOG_INS DEFAULT (0),
        DeletedDate    DATETIME NULL,
        SolutionDecisionID INT NULL, SolutionID INT NULL, DecisionTitle NVARCHAR(400) NULL, Context NVARCHAR(MAX) NULL,
        OptionsText NVARCHAR(MAX) NULL, Outcome NVARCHAR(MAX) NULL, StatusCode VARCHAR(20) NULL,
        SupersededBySolutionDecisionID INT NULL, DecidedDate DATETIME NULL, SolutionLogID INT NULL, DecidedByMemberID INT NULL,
        ModifiedBy VARCHAR(20) NULL, CreatedDate DATETIME NULL, ModifiedDate DATETIME NULL
    );
END;
GO
-- A link may now also join a question or a decision. The CHECKs that came with SolutionLink (Leonardo
-- 2026-11-02) name six kinds; they are put back with eight. Run again, it finds them already widened.
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_SolutionLink_FromKind' AND definition NOT LIKE '%decision%')
    ALTER TABLE dbo.SolutionLink DROP CONSTRAINT CK_SolutionLink_FromKind;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_SolutionLink_FromKind') AND OBJECT_ID('dbo.SolutionLink') IS NOT NULL
    ALTER TABLE dbo.SolutionLink ADD CONSTRAINT CK_SolutionLink_FromKind
        CHECK (FromKind IN ('item', 'note', 'log', 'file', 'term', 'topic', 'question', 'decision'));
GO
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_SolutionLink_ToKind' AND definition NOT LIKE '%decision%')
    ALTER TABLE dbo.SolutionLink DROP CONSTRAINT CK_SolutionLink_ToKind;
GO
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_SolutionLink_ToKind') AND OBJECT_ID('dbo.SolutionLink') IS NOT NULL
    ALTER TABLE dbo.SolutionLink ADD CONSTRAINT CK_SolutionLink_ToKind
        CHECK (ToKind IN ('item', 'note', 'log', 'file', 'term', 'topic', 'question', 'decision'));
GO
