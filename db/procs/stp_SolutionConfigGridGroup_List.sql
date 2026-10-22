SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridGroup_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridGroup_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridGroup_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridGroup_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        S

*/

SET NOCOUNT ON

SELECT g.SolutionConfigGridGroupID, g.SolutionID, g.GroupTitle, g.SortOrder, g.CreatedDate, g.ModifiedDate
FROM   dbo.SolutionConfigGridGroup g
WHERE  g.SolutionID = @prmSolutionID
ORDER BY g.SortOrder, g.SolutionConfigGridGroupID

GO
