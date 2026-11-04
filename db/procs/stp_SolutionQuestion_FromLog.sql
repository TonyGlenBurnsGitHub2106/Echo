SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionQuestion_FromLog') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionQuestion_FromLog
GO
CREATE PROCEDURE dbo.stp_SolutionQuestion_FromLog
    @prmSolutionLogID INT,
    @prmMemberCode    VARCHAR(20) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionQuestion_FromLog 412, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): make an open question out of a journal entry, in place (a Question
                         entry, or any entry that asks one). The entry's first line is the question (at most 800
                         characters), the rest its detail, and whoever wrote the entry asked it. The entry is not
                         changed; the two are linked. A long first line is cut at its first sentence. An entry that already has a question is refused. One
                         transaction. Returns SolutionQuestionID.

*/

SET NOCOUNT ON

DECLARE @solutionId INT, @text NVARCHAR(MAX), @authorId INT
SELECT @solutionId = SolutionID, @text = LogText, @authorId = AuthorMemberID
FROM   dbo.SolutionLog WHERE SolutionLogID = @prmSolutionLogID
IF @solutionId IS NULL BEGIN RAISERROR('That log entry no longer exists. Nothing was added.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.SolutionQuestion WHERE SolutionLogID = @prmSolutionLogID)
BEGIN RAISERROR('That log entry is already a question. Nothing was added.', 16, 1) RETURN END

DECLARE @body NVARCHAR(MAX) = LTRIM(RTRIM(REPLACE(REPLACE(ISNULL(@text, N''), NCHAR(13), N''), NCHAR(9), N' ')))
WHILE LEFT(@body, 1) = NCHAR(10) SET @body = SUBSTRING(@body, 2, LEN(@body))
DECLARE @nl INT = CHARINDEX(NCHAR(10), @body)
DECLARE @first NVARCHAR(MAX) = LTRIM(RTRIM(REPLACE(CASE WHEN @nl > 0 THEN LEFT(@body, @nl - 1) ELSE @body END, N'**', N'')))
IF @first = N'' BEGIN RAISERROR('That log entry has no text to make a question of. Nothing was added.', 16, 1) RETURN END
DECLARE @whole NVARCHAR(MAX) = @first
-- A long first line (one paragraph typed on one line) is cut at its first sentence, else at a word,
-- so the title stays a title; the whole text is kept below it.
IF LEN(@first) > 300
BEGIN
    DECLARE @stop INT = PATINDEX(N'%[.?!] %', LEFT(@first, 301))
    IF @stop >= 10 SET @first = LEFT(@first, @stop)
    ELSE
    BEGIN
        DECLARE @cut NVARCHAR(300) = LEFT(@first, 297)
        DECLARE @space INT = 297 - CHARINDEX(N' ', REVERSE(@cut))
        SET @first = LEFT(@cut, CASE WHEN CHARINDEX(N' ', @cut) > 0 AND @space > 150 THEN @space ELSE 297 END) + N'...'
    END
END
DECLARE @question NVARCHAR(800) = @first
-- The detail: the rest of the entry, or (when the first line was cut) the whole of it.
DECLARE @detail NVARCHAR(MAX) = CASE WHEN @first <> @whole THEN @body
                                     WHEN @nl > 0 THEN LTRIM(RTRIM(SUBSTRING(@body, @nl + 1, LEN(@body)))) ELSE NULL END

IF EXISTS (SELECT 1 FROM dbo.SolutionQuestion WHERE SolutionID = @solutionId AND StatusCode = 'open' AND QuestionText = @question)
BEGIN RAISERROR('That question is already open in this solution. Nothing was added.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

SET XACT_ABORT ON
DECLARE @id INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.SolutionQuestion (SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, SolutionLogID, ModifiedBy)
    VALUES (@solutionId, @question, NULLIF(@detail, N''), 'open', ISNULL(@authorId, @memberId), @prmSolutionLogID, LOWER(@prmMemberCode))
    SET @id = CAST(SCOPE_IDENTITY() AS INT)
    -- 'log' sorts before 'question': the pair's fixed order.
    INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
    VALUES (@solutionId, 'log', @prmSolutionLogID, 'question', @id, @memberId)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionQuestionID

GO
