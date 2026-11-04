SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionQuestion_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionQuestion_Save
GO
CREATE PROCEDURE dbo.stp_SolutionQuestion_Save
    @prmSolutionQuestionID INT            = 0,     -- 0 = add
    @prmSolutionID         INT            = 0,     -- required to add; an edit keeps the question's own solution
    @prmQuestionText       NVARCHAR(4000) = NULL,  -- NULL = keep (edit only)
    @prmQuestionDetail     NVARCHAR(MAX)  = NULL,  -- NULL = keep, '' = clear
    @prmOwnerCode          VARCHAR(20)    = NULL,  -- NULL = keep, '' = nobody
    @prmMemberCode         VARCHAR(20)    = NULL   -- who is saving (asks it, on an add)
AS

/*

TEST
----

    EXEC stp_SolutionQuestion_Save 0, 6, N'Which charset do the 837 files use?', N'Remit says windows-1252.', 'tony', 'rex'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): add or change one question. The text is checked before the first
                         write (present, at most 800 characters, one line, not already an open question of this
                         solution); the write runs with XACT_ABORT in one
                         transaction. An edit is a merge: a NULL parameter keeps what is there. A new question is
                         open and asked by the member saving it. Returns SolutionQuestionID.

*/

SET NOCOUNT ON

DECLARE @id INT = ISNULL(@prmSolutionQuestionID, 0)
DECLARE @solutionId INT = NULL

IF @id > 0
BEGIN
    SELECT @solutionId = SolutionID FROM dbo.SolutionQuestion WHERE SolutionQuestionID = @id
    IF @solutionId IS NULL BEGIN RAISERROR('That question no longer exists. Nothing was saved.', 16, 1) RETURN END
END
ELSE
BEGIN
    SET @solutionId = @prmSolutionID
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @solutionId)
    BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
END

DECLARE @text NVARCHAR(4000) = CASE WHEN @prmQuestionText IS NULL THEN NULL
    ELSE LTRIM(RTRIM(REPLACE(REPLACE(REPLACE(@prmQuestionText, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' '))) END
IF @id = 0 AND ISNULL(@text, N'') = N''
BEGIN RAISERROR('A question needs its question. Nothing was added.', 16, 1) RETURN END
IF @id > 0 AND @text IS NOT NULL AND @text = N''
BEGIN RAISERROR('A question cannot be blank. Nothing was saved.', 16, 1) RETURN END
IF LEN(ISNULL(@text, N'')) > 800
BEGIN RAISERROR('A question can be at most 800 characters. Put the rest in its detail. Nothing was saved.', 16, 1) RETURN END
-- The same question asked twice while it is still open is one question (a double tap on a phone, or an
-- agent repeating itself).
IF @text IS NOT NULL AND EXISTS (SELECT 1 FROM dbo.SolutionQuestion
                                 WHERE SolutionID = @solutionId AND StatusCode = 'open' AND QuestionText = @text AND SolutionQuestionID <> @id)
BEGIN RAISERROR('That question is already open in this solution. Nothing was saved.', 16, 1) RETURN END

DECLARE @memberId INT = NULL, @ownerId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
IF ISNULL(@prmOwnerCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmOwnerCode, @ownerId OUTPUT

-- XACT_ABORT only from here: a refusal above must never doom a caller's transaction.
SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @id = 0
    BEGIN
        INSERT INTO dbo.SolutionQuestion (SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, OwnerMemberID, ModifiedBy)
        VALUES (@solutionId, @text, NULLIF(LTRIM(RTRIM(ISNULL(@prmQuestionDetail, N''))), N''), 'open', @memberId, @ownerId, LOWER(@prmMemberCode))
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    ELSE
    BEGIN
        UPDATE dbo.SolutionQuestion
        SET    QuestionText   = ISNULL(@text, QuestionText),
               QuestionDetail = CASE WHEN @prmQuestionDetail IS NULL THEN QuestionDetail ELSE NULLIF(LTRIM(RTRIM(@prmQuestionDetail)), N'') END,
               OwnerMemberID  = CASE WHEN @prmOwnerCode IS NULL THEN OwnerMemberID ELSE @ownerId END,
               ModifiedBy     = ISNULL(LOWER(@prmMemberCode), ModifiedBy),
               ModifiedDate   = GETDATE()
        WHERE  SolutionQuestionID = @id
    END
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
