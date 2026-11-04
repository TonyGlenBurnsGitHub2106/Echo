SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionItemDependency_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionItemDependency_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionItemDependency_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionItemDependency_ListForSolution 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): for every item of a solution that waits on something, how many of the
                         items it waits on are still open (the Items tab's "waits on" chip). One row per waiting item:
                         SolutionProjectItemID, WaitCount, OpenWaitCount.

*/

SET NOCOUNT ON

SELECT  d.SolutionProjectItemID, COUNT(*) AS WaitCount, SUM(CASE WHEN s.IsClosed = 0 THEN 1 ELSE 0 END) AS OpenWaitCount
FROM    dbo.SolutionItemDependency d
JOIN    dbo.SolutionProjectItem i ON i.SolutionProjectItemID = d.SolutionProjectItemID
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
JOIN    dbo.SolutionProjectItem o ON o.SolutionProjectItemID = d.DependsOnSolutionProjectItemID
JOIN    dbo.SolutionStatus s ON s.SolutionStatusID = o.SolutionStatusID
WHERE   p.SolutionID = @prmSolutionID
GROUP BY d.SolutionProjectItemID

GO
