SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_Locate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_Locate
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_Locate
    @prmSolutionProjectItemID INT
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_Locate 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: which solution and project an item lives in, so a link that knows only the item can open its page.

*/

SET NOCOUNT ON

SELECT i.SolutionProjectItemID, i.SolutionProjectID, p.SolutionID
FROM   dbo.SolutionProjectItem i
JOIN   dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
WHERE  i.SolutionProjectItemID = @prmSolutionProjectItemID

GO
