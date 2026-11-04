SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionDecision_OpenLinks') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionDecision_OpenLinks
GO
CREATE PROCEDURE dbo.stp_SolutionDecision_OpenLinks
    @prmSolutionDecisionID INT
AS

/*

TEST
----

    EXEC stp_SolutionDecision_OpenLinks 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): what a decision could close -- the records linked to it that are still
                         open: questions still open, and items whose status is not a closed one. The charset
                         decision was made on October 23 and its two items were still open on November 2; this is
                         the list the Decision View offers to close. Kind, RecordID, Title, Sub.

*/

SET NOCOUNT ON

DECLARE @sid INT = (SELECT SolutionID FROM dbo.SolutionDecision WHERE SolutionDecisionID = @prmSolutionDecisionID)

;WITH Ends AS (
    SELECT k.ToKind AS Kind, k.ToID AS RecordID FROM dbo.SolutionLink k
    WHERE  k.SolutionID = @sid AND k.FromKind = 'decision' AND k.FromID = @prmSolutionDecisionID
    UNION
    SELECT k.FromKind, k.FromID FROM dbo.SolutionLink k
    WHERE  k.SolutionID = @sid AND k.ToKind = 'decision' AND k.ToID = @prmSolutionDecisionID
)
SELECT  CAST('question' AS VARCHAR(10)) AS Kind, q.SolutionQuestionID AS RecordID,
        CAST(q.QuestionText AS NVARCHAR(400)) AS Title, CAST(N'Question' AS NVARCHAR(400)) AS Sub
FROM    Ends e
JOIN    dbo.SolutionQuestion q ON q.SolutionQuestionID = e.RecordID AND q.SolutionID = @sid
WHERE   e.Kind = 'question' AND q.StatusCode = 'open'
UNION ALL
SELECT  'item', i.SolutionProjectItemID, CAST(i.ItemTitle AS NVARCHAR(400)),
        CAST(p.ProjectName + N' | ' + s.StatusName AS NVARCHAR(400))
FROM    Ends e
JOIN    dbo.SolutionProjectItem i ON i.SolutionProjectItemID = e.RecordID
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID AND p.SolutionID = @sid
JOIN    dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID
WHERE   e.Kind = 'item' AND s.IsClosed = 0
ORDER BY 1 DESC, 3

GO
