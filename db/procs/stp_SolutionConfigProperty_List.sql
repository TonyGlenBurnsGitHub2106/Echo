SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigProperty_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigProperty_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigProperty_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigProperty_List 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: every property of one solution, by group then order, with its type
                         and its MatchState (all / some / none / incomplete / empty / secret) from
                         vw_SolutionConfigPropertyState. The whole grid is shown at once, so it is not paged.

*/

SET NOCOUNT ON

SELECT p.SolutionConfigPropertyID, p.SolutionConfigGroupID, p.PropertyKey, p.PropertyDescription, p.SortOrder,
       t.TypeCode, t.TypeName, t.IsSecret, s.MatchState, s.SetCount, s.EnvCount,
       p.CreatedDate, p.ModifiedDate, p.ModifiedBy
FROM   dbo.SolutionConfigProperty p
JOIN   dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID
JOIN   dbo.SolutionConfigType t  ON t.SolutionConfigTypeID  = p.SolutionConfigTypeID
JOIN   dbo.vw_SolutionConfigPropertyState s ON s.SolutionConfigPropertyID = p.SolutionConfigPropertyID
WHERE  g.SolutionID = @prmSolutionID
ORDER BY g.SortOrder, g.SolutionConfigGroupID, p.SortOrder, p.SolutionConfigPropertyID

GO
