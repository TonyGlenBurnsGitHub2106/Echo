SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionQuestion_SetStatus') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionQuestion_SetStatus
GO
CREATE PROCEDURE dbo.stp_SolutionQuestion_SetStatus
    @prmSolutionQuestionID         INT,
    @prmStatusCode                 VARCHAR(20),          -- open | answered | dropped
    @prmAnsweredBySolutionDecisionID INT        = NULL,  -- answered: the decision that answers it (optional)
    @prmMemberCode                 VARCHAR(20)  = NULL
AS

/*

TEST
----

    EXEC stp_SolutionQuestion_SetStatus 1, 'answered', 3, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): open, answer or drop one question. Answered stamps AnsweredDate and,
                         when a decision is named, which one (it must be a decision of the SAME solution). Open
                         clears both, so a reopened question carries no stale answer. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @status VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, ''))))
IF @status NOT IN ('open', 'answered', 'dropped')
BEGIN RAISERROR('A question is open, answered or dropped. Nothing was saved.', 16, 1) RETURN END

DECLARE @solutionId INT = (SELECT SolutionID FROM dbo.SolutionQuestion WHERE SolutionQuestionID = @prmSolutionQuestionID)
IF @solutionId IS NULL BEGIN RAISERROR('That question no longer exists. Nothing was saved.', 16, 1) RETURN END

DECLARE @decisionId INT = CASE WHEN @status = 'answered' AND ISNULL(@prmAnsweredBySolutionDecisionID, 0) > 0
                               THEN @prmAnsweredBySolutionDecisionID ELSE NULL END
IF @decisionId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.SolutionDecision WHERE SolutionDecisionID = @decisionId AND SolutionID = @solutionId)
BEGIN RAISERROR('That decision is not in this solution. Nothing was saved.', 16, 1) RETURN END

SET XACT_ABORT ON
UPDATE dbo.SolutionQuestion
SET    StatusCode                   = @status,
       AnsweredBySolutionDecisionID = CASE WHEN @status = 'answered' THEN ISNULL(@decisionId, AnsweredBySolutionDecisionID) ELSE NULL END,
       AnsweredDate                 = CASE WHEN @status = 'answered' THEN ISNULL(AnsweredDate, GETDATE()) ELSE NULL END,
       ModifiedBy                   = ISNULL(LOWER(NULLIF(@prmMemberCode, '')), ModifiedBy),
       ModifiedDate                 = GETDATE()
WHERE  SolutionQuestionID = @prmSolutionQuestionID

SELECT @@ROWCOUNT AS RowsAffected

GO
