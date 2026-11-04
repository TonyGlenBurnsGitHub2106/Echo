SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_ListForSolution
    @prmSolutionID INT,
    @prmStatusCode VARCHAR(20)   = NULL,   -- proposed | accepted | superseded | current (not superseded); NULL = every one
    @prmSearch     NVARCHAR(200) = NULL,
    @prmOffset     INT = 0,
    @prmPageSize   INT = 50
AS

/*

TEST
----

    EXEC stp_SolutionDecision_ListForSolution 6, NULL, N'charset', 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one page of a solution's decisions -- the decision register. Searched on
                         the title, context, options and outcome. Proposed first (they still need deciding), then
                         accepted, then superseded; newest decided first within each. Each row carries a
                         300-character preview of the outcome (Truncated says when there is more).

*/

SET NOCOUNT ON

DECLARE @q NVARCHAR(200) = LTRIM(RTRIM(ISNULL(@prmSearch, N'')))
DECLARE @like NVARCHAR(400) = REPLACE(REPLACE(REPLACE(@q, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]')
DECLARE @status VARCHAR(20) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))), '')
DECLARE @size INT = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

SELECT  d.SolutionDecisionID, d.SolutionID, d.DecisionTitle,
        CAST(NULL AS NVARCHAR(MAX)) AS Context, CAST(NULL AS NVARCHAR(MAX)) AS OptionsText,
        NULLIF(LEFT(ISNULL(d.Outcome, N''), 300), N'') AS Outcome,
        CAST(CASE WHEN LEN(ISNULL(d.Outcome, N'')) > 300 OR d.Context IS NOT NULL OR d.OptionsText IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS Truncated,
        d.StatusCode, d.DecidedDate, m.MemberCode AS DecidedByCode, m.MemberName AS DecidedByName,
        d.SolutionLogID, l.SequenceNumber AS LogSequenceNumber,
        d.SupersededBySolutionDecisionID, n.DecisionTitle AS SupersededByTitle,
        CAST(NULL AS INT) AS SupersedesSolutionDecisionID, CAST(NULL AS NVARCHAR(400)) AS SupersedesTitle,
        (SELECT COUNT(*) FROM dbo.SolutionQuestion q WHERE q.AnsweredBySolutionDecisionID = d.SolutionDecisionID) AS AnsweredCount,
        (SELECT COUNT(*) FROM dbo.SolutionLink k
         WHERE k.SolutionID = d.SolutionID
           AND ((k.FromKind = 'decision' AND k.FromID = d.SolutionDecisionID) OR (k.ToKind = 'decision' AND k.ToID = d.SolutionDecisionID))) AS LinkCount,
        d.ModifiedBy, d.CreatedDate, d.ModifiedDate
FROM    dbo.SolutionDecision d
LEFT JOIN dbo.SolutionMember   m ON m.SolutionMemberID   = d.DecidedByMemberID
LEFT JOIN dbo.SolutionLog      l ON l.SolutionLogID      = d.SolutionLogID
LEFT JOIN dbo.SolutionDecision n ON n.SolutionDecisionID = d.SupersededBySolutionDecisionID
WHERE   d.SolutionID = @prmSolutionID
  AND   (@status IS NULL OR d.StatusCode = @status OR (@status = 'current' AND d.StatusCode <> 'superseded'))
  AND   (@q = N'' OR d.DecisionTitle LIKE N'%' + @like + N'%' OR d.Context LIKE N'%' + @like + N'%'
                  OR d.OptionsText LIKE N'%' + @like + N'%' OR d.Outcome LIKE N'%' + @like + N'%')
ORDER BY CASE d.StatusCode WHEN 'proposed' THEN 0 WHEN 'accepted' THEN 1 ELSE 2 END,
         ISNULL(d.DecidedDate, d.CreatedDate) DESC, d.SolutionDecisionID DESC
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT @size ROWS ONLY

GO
