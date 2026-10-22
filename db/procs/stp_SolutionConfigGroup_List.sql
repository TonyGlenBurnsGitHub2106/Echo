SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGroup_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGroup_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGroup_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGroup_List 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the property groups of one solution, in order.

*/

SET NOCOUNT ON

SELECT g.SolutionConfigGroupID, g.SolutionID, g.GroupTitle, g.SortOrder, g.CreatedDate, g.ModifiedDate
FROM   dbo.SolutionConfigGroup g
WHERE  g.SolutionID = @prmSolutionID
ORDER BY g.SortOrder, g.SolutionConfigGroupID

GO
