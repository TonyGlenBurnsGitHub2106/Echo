SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_FromLog') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_FromLog
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_FromLog
    @prmSolutionLogID INT,
    @prmMemberCode    VARCHAR(20) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionDecision_FromLog 505, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): make a decision out of a journal entry, in place. The 94 decisions in
                         Claims' log become the register without retyping one: the entry's first line is the
                         title (a long first line is cut at its first sentence, else at a word, under 200
                         characters), its whole text the outcome (when there is more than the title), its author and date are who decided and when, and the decision is accepted.
                         The entry is not changed; the two are linked. An entry that already has a decision is
                         refused. One transaction. Returns SolutionDecisionID.

*/

SET NOCOUNT ON

DECLARE @solutionId INT, @text NVARCHAR(MAX), @logDate DATETIME, @authorId INT
SELECT @solutionId = SolutionID, @text = LogText, @logDate = LogDate, @authorId = AuthorMemberID
FROM   dbo.SolutionLog WHERE SolutionLogID = @prmSolutionLogID
IF @solutionId IS NULL BEGIN RAISERROR('That log entry no longer exists. Nothing was added.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.SolutionDecision WHERE SolutionLogID = @prmSolutionLogID)
BEGIN RAISERROR('That log entry is already a decision. Nothing was added.', 16, 1) RETURN END

-- The first line with anything on it, without Markdown bold marks, is the title.
DECLARE @body NVARCHAR(MAX) = LTRIM(RTRIM(REPLACE(REPLACE(ISNULL(@text, N''), NCHAR(13), N''), NCHAR(9), N' ')))
WHILE LEFT(@body, 1) = NCHAR(10) SET @body = SUBSTRING(@body, 2, LEN(@body))
DECLARE @nl INT = CHARINDEX(NCHAR(10), @body)
DECLARE @first NVARCHAR(MAX) = LTRIM(RTRIM(REPLACE(CASE WHEN @nl > 0 THEN LEFT(@body, @nl - 1) ELSE @body END, N'**', N'')))
IF @first = N'' BEGIN RAISERROR('That log entry has no text to make a decision of. Nothing was added.', 16, 1) RETURN END
DECLARE @whole NVARCHAR(MAX) = @first
-- A long first line (one paragraph typed on one line) is cut at its first sentence, else at a word,
-- so the title stays a title; the whole text is kept below it.
IF LEN(@first) > 200
BEGIN
    DECLARE @stop INT = PATINDEX(N'%[.?!] %', LEFT(@first, 201))
    IF @stop >= 20 SET @first = LEFT(@first, @stop)
    ELSE
    BEGIN
        DECLARE @cut NVARCHAR(200) = LEFT(@first, 197)
        DECLARE @space INT = 197 - CHARINDEX(N' ', REVERSE(@cut))
        SET @first = LEFT(@cut, CASE WHEN CHARINDEX(N' ', @cut) > 0 AND @space > 100 THEN @space ELSE 197 END) + N'...'
    END
END
DECLARE @title NVARCHAR(400) = @first
DECLARE @outcome NVARCHAR(MAX) = CASE WHEN @nl > 0 OR @first <> @whole THEN @body ELSE NULL END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

SET XACT_ABORT ON
DECLARE @id INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.SolutionDecision (SolutionID, DecisionTitle, Outcome, StatusCode, DecidedDate, SolutionLogID, DecidedByMemberID, ModifiedBy)
    VALUES (@solutionId, @title, @outcome, 'accepted', @logDate, @prmSolutionLogID, ISNULL(@authorId, @memberId), LOWER(@prmMemberCode))
    SET @id = CAST(SCOPE_IDENTITY() AS INT)
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLink WHERE SolutionID = @solutionId AND FromKind = 'decision' AND FromID = @id AND ToKind = 'log' AND ToID = @prmSolutionLogID)
        INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
        VALUES (@solutionId, 'decision', @id, 'log', @prmSolutionLogID, @memberId)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionDecisionID

GO
