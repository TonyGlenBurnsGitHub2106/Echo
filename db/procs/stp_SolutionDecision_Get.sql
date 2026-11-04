SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_Get
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_Get
    @prmSolutionDecisionID INT
AS

/*

TEST
----

    EXEC stp_SolutionDecision_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one decision, whole -- context, options and outcome; who decided and
                         when; the journal entry that records it; the newer decision that replaced it and the older
                         one it replaced; how many questions it answered and how many records link to it.

*/

SET NOCOUNT ON

SELECT  d.SolutionDecisionID, d.SolutionID, d.DecisionTitle, d.Context, d.OptionsText, d.Outcome, CAST(0 AS BIT) AS Truncated,
        d.StatusCode, d.DecidedDate, m.MemberCode AS DecidedByCode, m.MemberName AS DecidedByName,
        d.SolutionLogID, l.SequenceNumber AS LogSequenceNumber,
        d.SupersededBySolutionDecisionID, n.DecisionTitle AS SupersededByTitle,
        o.SolutionDecisionID AS SupersedesSolutionDecisionID, o.DecisionTitle AS SupersedesTitle,
        (SELECT COUNT(*) FROM dbo.SolutionQuestion q WHERE q.AnsweredBySolutionDecisionID = d.SolutionDecisionID) AS AnsweredCount,
        (SELECT COUNT(*) FROM dbo.SolutionLink k
         WHERE k.SolutionID = d.SolutionID
           AND ((k.FromKind = 'decision' AND k.FromID = d.SolutionDecisionID) OR (k.ToKind = 'decision' AND k.ToID = d.SolutionDecisionID))) AS LinkCount,
        d.ModifiedBy, d.CreatedDate, d.ModifiedDate
FROM    dbo.SolutionDecision d
LEFT JOIN dbo.SolutionMember   m ON m.SolutionMemberID   = d.DecidedByMemberID
LEFT JOIN dbo.SolutionLog      l ON l.SolutionLogID      = d.SolutionLogID
LEFT JOIN dbo.SolutionDecision n ON n.SolutionDecisionID = d.SupersededBySolutionDecisionID
OUTER APPLY (SELECT TOP 1 x.SolutionDecisionID, x.DecisionTitle FROM dbo.SolutionDecision x
             WHERE x.SupersededBySolutionDecisionID = d.SolutionDecisionID ORDER BY x.SolutionDecisionID DESC) o
WHERE   d.SolutionDecisionID = @prmSolutionDecisionID

GO
