SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionItemDependency_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionItemDependency_Get
GO
CREATE PROCEDURE dbo.stp_SolutionItemDependency_Get
    @prmSolutionItemDependencyID INT
AS

/*

TEST
----

    EXEC stp_SolutionItemDependency_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one wait, with the solution it belongs to (for the access check).

*/

SET NOCOUNT ON

SELECT  d.SolutionItemDependencyID, d.SolutionProjectItemID, d.DependsOnSolutionProjectItemID, p.SolutionID, d.CreatedDate
FROM    dbo.SolutionItemDependency d
JOIN    dbo.SolutionProjectItem i ON i.SolutionProjectItemID = d.SolutionProjectItemID
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
WHERE   d.SolutionItemDependencyID = @prmSolutionItemDependencyID

GO
