SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionQuestion_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionQuestion_Get
GO
CREATE PROCEDURE dbo.stp_SolutionQuestion_Get
    @prmSolutionQuestionID INT
AS

/*

TEST
----

    EXEC stp_SolutionQuestion_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one question, whole -- its detail, who asked it, who should answer it,
                         the decision that answered it, the journal entry it came from, how many records link to it.

*/

SET NOCOUNT ON

SELECT  q.SolutionQuestionID, q.SolutionID, q.QuestionText, q.QuestionDetail, CAST(0 AS BIT) AS Truncated,
        q.StatusCode, a.MemberCode AS AskedByCode, a.MemberName AS AskedByName,
        o.MemberCode AS OwnerCode, o.MemberName AS OwnerName,
        q.AnsweredBySolutionDecisionID, d.DecisionTitle AS AnsweredByTitle, q.AnsweredDate,
        q.SolutionLogID, l.SequenceNumber AS LogSequenceNumber,
        q.ModifiedBy, q.CreatedDate, q.ModifiedDate,
        (SELECT COUNT(*) FROM dbo.SolutionLink k
         WHERE k.SolutionID = q.SolutionID
           AND ((k.FromKind = 'question' AND k.FromID = q.SolutionQuestionID) OR (k.ToKind = 'question' AND k.ToID = q.SolutionQuestionID))) AS LinkCount
FROM    dbo.SolutionQuestion q
LEFT JOIN dbo.SolutionMember   a ON a.SolutionMemberID   = q.AskedByMemberID
LEFT JOIN dbo.SolutionMember   o ON o.SolutionMemberID   = q.OwnerMemberID
LEFT JOIN dbo.SolutionDecision d ON d.SolutionDecisionID = q.AnsweredBySolutionDecisionID
LEFT JOIN dbo.SolutionLog      l ON l.SolutionLogID      = q.SolutionLogID
WHERE   q.SolutionQuestionID = @prmSolutionQuestionID

GO
