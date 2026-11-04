SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_Save
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_Save
    @prmSolutionDecisionID INT            = 0,     -- 0 = add
    @prmSolutionID         INT            = 0,     -- required to add; an edit keeps the decision's own solution
    @prmDecisionTitle      NVARCHAR(4000) = NULL,  -- NULL = keep (edit only)
    @prmContext            NVARCHAR(MAX)  = NULL,  -- NULL = keep, '' = clear
    @prmOptionsText        NVARCHAR(MAX)  = NULL,  -- NULL = keep, '' = clear
    @prmOutcome            NVARCHAR(MAX)  = NULL,  -- NULL = keep, '' = clear
    @prmStatusCode         VARCHAR(20)    = NULL,  -- proposed | accepted; NULL = keep (an add: accepted)
    @prmDecidedDate        DATETIME       = NULL,  -- NULL = keep (an accepted add: now)
    @prmMemberCode         VARCHAR(20)    = NULL,
    @prmSupersedesID       INT            = NULL   -- an add only: the decision this one replaces
AS

/*

TEST
----

    EXEC stp_SolutionDecision_Save 0, 6, N'Read files in as UTF-8', N'837 and 835 disagreed.', N'UTF-8; windows-1252',
         N'UTF-8 in; remit and submit stay windows-1252.', 'accepted', NULL, 'tony', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): add or change one decision. Every input is checked before the first
                         write (a title of at most 400 characters on one line; a status of proposed or accepted --
                         superseded comes only from a newer decision; a supersede target that is in this solution
                         and not already superseded; no second open decision with the same title). An ADD, in ONE
                         transaction: the decision, its journal entry (kind Decision, the next sequence number of
                         the solution under the same lock stp_SolutionLog_Save takes), the link between the two,
                         and -- when it supersedes one -- the old one marked superseded and linked to the new.
                         An edit is a merge: a NULL parameter keeps what is there; it never touches the journal
                         (the entry is history). Returns SolutionDecisionID and SolutionLogID.

*/

SET NOCOUNT ON

DECLARE @id INT = ISNULL(@prmSolutionDecisionID, 0)
DECLARE @solutionId INT = NULL, @currentStatus VARCHAR(20) = NULL

IF @id > 0
BEGIN
    SELECT @solutionId = SolutionID, @currentStatus = StatusCode FROM dbo.SolutionDecision WHERE SolutionDecisionID = @id
    IF @solutionId IS NULL BEGIN RAISERROR('That decision no longer exists. Nothing was saved.', 16, 1) RETURN END
END
ELSE
BEGIN
    SET @solutionId = @prmSolutionID
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @solutionId)
    BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
END

DECLARE @title NVARCHAR(4000) = CASE WHEN @prmDecisionTitle IS NULL THEN NULL
    ELSE LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(@prmDecisionTitle, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' '))) END
IF @id = 0 AND ISNULL(@title, N'') = N''
BEGIN RAISERROR('A decision needs a title: what was decided, in a line. Nothing was added.', 16, 1) RETURN END
IF @id > 0 AND @title IS NOT NULL AND @title = N''
BEGIN RAISERROR('A decision title cannot be blank. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@title, N'')) > 400
BEGIN RAISERROR('A decision title can be at most 400 characters. Put the rest in the outcome. Nothing was saved.', 16, 1) RETURN END

DECLARE @status VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))), '')
IF @status IS NOT NULL AND @status NOT IN ('proposed', 'accepted')
BEGIN RAISERROR('A decision is proposed or accepted. It is superseded only by recording a newer one. Nothing was saved.', 16, 1) RETURN END
IF @id > 0 AND @currentStatus = 'superseded' AND @status IS NOT NULL
BEGIN RAISERROR('A superseded decision keeps that status: record a new decision instead. Nothing was saved.', 16, 1) RETURN END
IF @id = 0 SET @status = ISNULL(@status, 'accepted')

DECLARE @supersedes INT = CASE WHEN @id = 0 AND ISNULL(@prmSupersedesID, 0) > 0 THEN @prmSupersedesID ELSE NULL END
IF @supersedes IS NOT NULL
BEGIN
    DECLARE @oldStatus VARCHAR(20) = NULL
    SELECT @oldStatus = StatusCode FROM dbo.SolutionDecision WHERE SolutionDecisionID = @supersedes AND SolutionID = @solutionId
    IF @oldStatus IS NULL BEGIN RAISERROR('The decision it replaces is not in this solution. Nothing was added.', 16, 1) RETURN END
    IF @oldStatus = 'superseded' BEGIN RAISERROR('That decision was already replaced by a newer one. Nothing was added.', 16, 1) RETURN END
END

IF @title IS NOT NULL AND EXISTS (SELECT 1 FROM dbo.SolutionDecision
                                  WHERE SolutionID = @solutionId AND StatusCode <> 'superseded' AND DecisionTitle = @title
                                    AND SolutionDecisionID <> @id AND SolutionDecisionID <> ISNULL(@supersedes, 0))
BEGIN RAISERROR('This solution already has that decision. Nothing was saved.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

DECLARE @typeId INT = (SELECT SolutionLogTypeID FROM dbo.SolutionLogType WHERE LogTypeCode = 'decision')
IF @typeId IS NULL SET @typeId = (SELECT TOP 1 SolutionLogTypeID FROM dbo.SolutionLogType ORDER BY SortOrder, SolutionLogTypeID)

DECLARE @logId INT = NULL

-- XACT_ABORT only from here: a refusal above must never doom a caller's transaction.
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @id = 0
    BEGIN
        DECLARE @outcome NVARCHAR(MAX) = NULLIF(LTRIM(RTRIM(ISNULL(@prmOutcome, N''))), N'')
        DECLARE @decided DATETIME = CASE WHEN @status = 'accepted' THEN ISNULL(@prmDecidedDate, GETDATE()) ELSE @prmDecidedDate END
        INSERT INTO dbo.SolutionDecision (SolutionID, DecisionTitle, Context, OptionsText, Outcome, StatusCode, DecidedDate,
                                          DecidedByMemberID, ModifiedBy)
        VALUES (@solutionId, @title, NULLIF(LTRIM(RTRIM(ISNULL(@prmContext, N''))), N''), NULLIF(LTRIM(RTRIM(ISNULL(@prmOptionsText, N''))), N''),
                @outcome, @status, @decided, @memberId, LOWER(@prmMemberCode))
        SET @id = CAST(SCOPE_IDENTITY() AS INT)

        -- The journal records it, the way stp_SolutionLog_Save numbers an entry.
        DECLARE @seq INT = ISNULL((SELECT MAX(SequenceNumber) FROM dbo.SolutionLog WITH (UPDLOCK, HOLDLOCK) WHERE SolutionID = @solutionId), 0) + 1
        DECLARE @text NVARCHAR(MAX) = CASE WHEN @status = 'proposed' THEN N'**Proposed:** ' ELSE N'**Decided:** ' END + @title +
            CASE WHEN @outcome IS NULL THEN N'' ELSE NCHAR(10) + NCHAR(10) + @outcome END
        INSERT INTO dbo.SolutionLog (SolutionID, SolutionProjectID, SolutionProjectItemID, SequenceNumber, SolutionLogTypeID, AuthorMemberID, LogText, LogDate)
        VALUES (@solutionId, NULL, NULL, @seq, @typeId, @memberId, @text, GETDATE())
        SET @logId = CAST(SCOPE_IDENTITY() AS INT)
        UPDATE dbo.SolutionDecision SET SolutionLogID = @logId WHERE SolutionDecisionID = @id
        -- 'decision' sorts before 'log': the pair's fixed order.
        INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
        VALUES (@solutionId, 'decision', @id, 'log', @logId, @memberId)

        IF @supersedes IS NOT NULL
        BEGIN
            UPDATE dbo.SolutionDecision
            SET    StatusCode = 'superseded', SupersededBySolutionDecisionID = @id,
                   ModifiedBy = ISNULL(LOWER(@prmMemberCode), ModifiedBy), ModifiedDate = GETDATE()
            WHERE  SolutionDecisionID = @supersedes
            INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
            VALUES (@solutionId, 'decision', @supersedes, 'decision', @id, @memberId)   -- the old id is the lower one
        END
    END
    ELSE
    BEGIN
        UPDATE dbo.SolutionDecision
        SET    DecisionTitle = ISNULL(@title, DecisionTitle),
               Context       = CASE WHEN @prmContext     IS NULL THEN Context     ELSE NULLIF(LTRIM(RTRIM(@prmContext)), N'') END,
               OptionsText   = CASE WHEN @prmOptionsText IS NULL THEN OptionsText ELSE NULLIF(LTRIM(RTRIM(@prmOptionsText)), N'') END,
               Outcome       = CASE WHEN @prmOutcome     IS NULL THEN Outcome     ELSE NULLIF(LTRIM(RTRIM(@prmOutcome)), N'') END,
               StatusCode    = ISNULL(@status, StatusCode),
               DecidedDate   = CASE WHEN @prmDecidedDate IS NOT NULL THEN @prmDecidedDate
                                    WHEN @status = 'accepted' AND DecidedDate IS NULL THEN GETDATE() ELSE DecidedDate END,
               ModifiedBy    = ISNULL(LOWER(@prmMemberCode), ModifiedBy),
               ModifiedDate  = GETDATE()
        WHERE  SolutionDecisionID = @id
        SET @logId = (SELECT SolutionLogID FROM dbo.SolutionDecision WHERE SolutionDecisionID = @id)
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionDecisionID, @logId AS SolutionLogID

GO
