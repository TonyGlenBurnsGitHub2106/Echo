SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionQuestion_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionQuestion_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionQuestion_ListForSolution
    @prmSolutionID INT,
    @prmStatusCode VARCHAR(20)   = NULL,   -- open | answered | dropped; NULL = every one
    @prmSearch     NVARCHAR(200) = NULL,
    @prmOffset     INT = 0,
    @prmPageSize   INT = 50
AS

/*

TEST
----

    EXEC stp_SolutionQuestion_ListForSolution 6, 'open', NULL, 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one page of a solution's questions, searched on the question and its
                         detail. Open ones first, then answered, then dropped; newest first within each. Each row
                         carries a 300-character preview of the detail (Truncated says when there is more), who
                         asked it, who should answer it and the decision that answered it.

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')
DECLARE @status VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))), '')
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

SELECT  q.SolutionQuestionID, q.SolutionID, q.QuestionText,
        NULLIF(LEFT(ISNULL(q.QuestionDetail, N''), 300), N'') AS QuestionDetail,
        CAST(CASE WHEN LEN(ISNULL(q.QuestionDetail, N'')) > 300 THEN 1 ELSE 0 END AS BIT) AS Truncated,
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
WHERE   q.SolutionID = @prmSolutionID
  AND   (@status IS NULL OR q.StatusCode = @status)
  AND   (@q = N'' OR q.QuestionText LIKE N'%' + @like + N'%' OR q.QuestionDetail LIKE N'%' + @like + N'%')
ORDER BY CASE q.StatusCode WHEN 'open' THEN 0 WHEN 'answered' THEN 1 ELSE 2 END,
         ISNULL(q.ModifiedDate, q.CreatedDate) DESC, q.SolutionQuestionID DESC
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
