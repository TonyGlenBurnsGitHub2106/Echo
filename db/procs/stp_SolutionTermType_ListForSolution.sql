SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTermType_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTermType_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionTermType_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionTermType_ListForSolution 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: the term types of ONE solution, A to Z, with how many terms use each.

*/

SET NOCOUNT ON

SELECT ty.SolutionTermTypeID, ty.SolutionID, ty.TypeName, ty.SortOrder,
       (SELECT COUNT(*) FROM dbo.SolutionTerm t WHERE t.SolutionTermTypeID = ty.SolutionTermTypeID) AS UsageCount
FROM   dbo.SolutionTermType ty
WHERE  ty.SolutionID = @prmSolutionID
ORDER BY ty.TypeName

GO
