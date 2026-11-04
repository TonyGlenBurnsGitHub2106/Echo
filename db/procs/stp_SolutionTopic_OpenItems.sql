SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_OpenItems') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_OpenItems
GO
CREATE PROCEDURE dbo.stp_SolutionTopic_OpenItems
    @prmSolutionTopicID INT
AS

/*

TEST
----

    EXEC stp_SolutionTopic_OpenItems 4

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): the items linked to a topic that are still open -- for an assumption,
                         the work that rests on it. The premise switch lists these when the assumption is proved
                         false ("The 837 submit path is live" died on 10/16 and 20 items were cancelled by hand).
                         RecordID, Title, Sub (project and status).

*/

SET NOCOUNT ON

DECLARE @sid INT = (SELECT SolutionID FROM dbo.SolutionTopic WHERE SolutionTopicID = @prmSolutionTopicID)

;WITH Ends AS (
    SELECT k.ToID AS ItemID FROM dbo.SolutionLink k
    WHERE  k.SolutionID = @sid AND k.FromKind = 'topic' AND k.FromID = @prmSolutionTopicID AND k.ToKind = 'item'
    UNION
    SELECT k.FromID FROM dbo.SolutionLink k
    WHERE  k.SolutionID = @sid AND k.ToKind = 'topic' AND k.ToID = @prmSolutionTopicID AND k.FromKind = 'item'
)
SELECT  CAST('item' AS VARCHAR(10)) AS Kind, i.SolutionProjectItemID AS RecordID, CAST(i.ItemTitle AS NVARCHAR(400)) AS Title,
        CAST(p.ProjectName + N' | ' + s.StatusName AS NVARCHAR(400)) AS Sub
FROM    Ends e
JOIN    dbo.SolutionProjectItem i ON i.SolutionProjectItemID = e.ItemID
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID AND p.SolutionID = @sid
JOIN    dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID
WHERE   s.IsClosed = 0
ORDER BY p.SortOrder, i.SortOrder, i.ItemTitle

GO
