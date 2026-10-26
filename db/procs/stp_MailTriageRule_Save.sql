SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriageRule_Save') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriageRule_Save
GO
CREATE PROCEDURE dbo.stp_MailTriageRule_Save
    @prmSenderKey     NVARCHAR(254),
    @prmKeyKind       VARCHAR(10),            -- domain | address
    @prmDisposition   VARCHAR(10),            -- white | black (Delete) | junk
    @prmGmailFilterID VARCHAR(100)  = NULL,   -- the filter Gmail made for a black or junk rule
    @prmFilterMode    VARCHAR(10)   = NULL,   -- trash | archive | spam
    @prmNote          NVARCHAR(500) = NULL
AS

/*

Mail Triage (Story 3561): whitelist, junk or delete one sender (new, or switch sides). Delete is
the original blacklist (code 'black': its Gmail filter sends mail to Trash); junk (Story 5567)
sends it to Spam instead. The sender
leaves the waiting-for-a-decision list in the same transaction. Refuses, before any write: a
protected sender to Junk or Delete, and a sender already on the side asked for (a double tap must
never write twice). Returns the rule.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_MailTriageRule_Save N'example.com', 'domain', 'white'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix        New (Story 3561, Mail Triage).
2026-10-06  Felix        Junk as a third side; filter mode spam (Story 5567).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no
                         MailTriagePending table, so the delete from it is dropped.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @key  NVARCHAR(254) = LOWER(LTRIM(RTRIM(ISNULL(@prmSenderKey, N''))))
DECLARE @kind VARCHAR(10)   = LOWER(LTRIM(RTRIM(ISNULL(@prmKeyKind, ''))))
DECLARE @disp VARCHAR(10)   = LOWER(LTRIM(RTRIM(ISNULL(@prmDisposition, ''))))
DECLARE @mode VARCHAR(10)   = NULLIF(LOWER(LTRIM(RTRIM(@prmFilterMode))), '')
DECLARE @id   INT
DECLARE @msg  NVARCHAR(400)

IF @key = N'' OR CHARINDEX(N'.', @key) = 0 OR CHARINDEX(N' ', @key) > 0
BEGIN
    RAISERROR('A sender is a domain such as nextdoor.com, or one full address. Nothing was saved.', 16, 1)
    RETURN
END
IF @kind NOT IN ('domain', 'address')
   OR (@kind = 'address' AND CHARINDEX(N'@', @key) = 0)
   OR (@kind = 'domain' AND CHARINDEX(N'@', @key) > 0)
BEGIN
    RAISERROR('The sender kind must be domain or address, and match the sender. Nothing was saved.', 16, 1)
    RETURN
END
IF @disp NOT IN ('white', 'black', 'junk')
BEGIN
    RAISERROR('A sender is whitelisted, junked or deleted. Nothing was saved.', 16, 1)
    RETURN
END
IF @mode IS NOT NULL AND @mode NOT IN ('trash', 'archive', 'spam')
BEGIN
    RAISERROR('The filter mode is trash, archive or spam. Nothing was saved.', 16, 1)
    RETURN
END

SELECT @id = MailTriageRuleID FROM dbo.MailTriageRule WHERE SenderKey = @key

IF @disp IN ('black', 'junk') AND EXISTS (SELECT 1 FROM dbo.MailTriageRule WHERE MailTriageRuleID = @id AND IsProtected = 1)
BEGIN
    SET @msg = @key + N' is protected and can never be sent to Junk or Delete. Nothing was saved.'
    RAISERROR(@msg, 16, 1)
    RETURN
END
IF EXISTS (SELECT 1 FROM dbo.MailTriageRule WHERE MailTriageRuleID = @id AND Disposition = @disp)
BEGIN
    SET @msg = @key + N' is already ' + CASE @disp WHEN 'white' THEN N'whitelisted' WHEN 'junk' THEN N'on Junk' ELSE N'on Delete' END
             + N'. Nothing was saved.'
    RAISERROR(@msg, 16, 1)
    RETURN
END

BEGIN TRAN
    IF @id IS NULL
    BEGIN
        INSERT INTO dbo.MailTriageRule (SenderKey, KeyKind, Disposition, GmailFilterID, FilterMode, Note)
        VALUES (@key, @kind, @disp,
                CASE WHEN @disp IN ('black', 'junk') THEN NULLIF(@prmGmailFilterID, '') END,
                CASE WHEN @disp IN ('black', 'junk') THEN @mode END,
                NULLIF(LTRIM(RTRIM(@prmNote)), N''))
        SET @id = SCOPE_IDENTITY()
    END
    ELSE
    BEGIN
        UPDATE dbo.MailTriageRule
        SET    Disposition   = @disp,
               GmailFilterID = CASE WHEN @disp IN ('black', 'junk') THEN NULLIF(@prmGmailFilterID, '') END,
               FilterMode    = CASE WHEN @disp IN ('black', 'junk') THEN @mode END,
               Note          = ISNULL(NULLIF(LTRIM(RTRIM(@prmNote)), N''), Note),
               ModifiedDate  = GETDATE()
        WHERE  MailTriageRuleID = @id
    END
    -- Story 5654: Echo has no MailTriagePending (no waiting-for-a-decision list), so there is
    -- nothing to remove the sender from here.
COMMIT

EXEC dbo.stp_MailTriageRule_Get @prmMailTriageRuleID = @id

GO
