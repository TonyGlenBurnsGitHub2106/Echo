SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTermCategory_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTermCategory_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionTermCategory_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionTermCategory_ListForSolution 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: the term categories of ONE solution, A to Z, with how many terms carry each.

*/

SET NOCOUNT ON

SELECT c.SolutionTermCategoryID, c.SolutionID, c.CategoryName,
       (SELECT COUNT(*) FROM dbo.SolutionTermCategoryXRef x WHERE x.SolutionTermCategoryID = c.SolutionTermCategoryID) AS UsageCount
FROM   dbo.SolutionTermCategory c
WHERE  c.SolutionID = @prmSolutionID
ORDER BY c.CategoryName

GO
