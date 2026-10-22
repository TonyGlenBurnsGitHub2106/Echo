SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridProperty_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridProperty_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridProperty_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridProperty_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: every property of one solution, in group order then property order,
                         with its type (and whether the type is secret).

*/

SET NOCOUNT ON

SELECT p.SolutionConfigGridPropertyID, p.SolutionID, p.SolutionConfigGridGroupID, p.PropertyKey, p.PropertyDescription,
       t.TypeCode, t.TypeName, t.IsSecret, p.SortOrder, p.CreatedDate, p.ModifiedDate, p.ModifiedBy
FROM   dbo.SolutionConfigGridProperty p
JOIN   dbo.SolutionConfigGridGroup g ON g.SolutionConfigGridGroupID = p.SolutionConfigGridGroupID
JOIN   dbo.SolutionConfigType t      ON t.SolutionConfigTypeID      = p.SolutionConfigTypeID
WHERE  p.SolutionID = @prmSolutionID
ORDER BY g.SortOrder, g.SolutionConfigGridGroupID, p.SortOrder, p.SolutionConfigGridPropertyID

GO
