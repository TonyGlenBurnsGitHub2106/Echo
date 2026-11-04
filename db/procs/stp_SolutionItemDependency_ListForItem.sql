SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionItemDependency_ListForItem') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionItemDependency_ListForItem
GO
CREATE PROCEDURE dbo.stp_SolutionItemDependency_ListForItem
    @prmSolutionProjectItemID INT
AS

/*

TEST
----

    EXEC stp_SolutionItemDependency_ListForItem 1201

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): what one item waits on (Direction 'waitsOn') and what waits on it
                         (Direction 'blocks'), each with the other item's title, project, status and whether it
                         is closed. Open ones first, then by title.

*/

SET NOCOUNT ON

SELECT  d.SolutionItemDependencyID, CAST('waitsOn' AS VARCHAR(10)) AS Direction,
        o.SolutionProjectItemID AS ItemID, o.ItemTitle, p.ProjectName, s.StatusCode, s.StatusName, s.ColorHex,
        CAST(s.IsClosed AS BIT) AS IsClosed, d.CreatedDate
FROM    dbo.SolutionItemDependency d
JOIN    dbo.SolutionProjectItem o ON o.SolutionProjectItemID = d.DependsOnSolutionProjectItemID
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = o.SolutionProjectID
JOIN    dbo.SolutionStatus s ON s.SolutionStatusID = o.SolutionStatusID
WHERE   d.SolutionProjectItemID = @prmSolutionProjectItemID
UNION ALL
SELECT  d.SolutionItemDependencyID, 'blocks',
        o.SolutionProjectItemID, o.ItemTitle, p.ProjectName, s.StatusCode, s.StatusName, s.ColorHex,
        CAST(s.IsClosed AS BIT), d.CreatedDate
FROM    dbo.SolutionItemDependency d
JOIN    dbo.SolutionProjectItem o ON o.SolutionProjectItemID = d.SolutionProjectItemID
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = o.SolutionProjectID
JOIN    dbo.SolutionStatus s ON s.SolutionStatusID = o.SolutionStatusID
WHERE   d.DependsOnSolutionProjectItemID = @prmSolutionProjectItemID
ORDER BY Direction DESC, IsClosed, ItemTitle

GO
