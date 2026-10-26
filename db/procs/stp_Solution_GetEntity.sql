SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_GetEntity') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_GetEntity
GO
CREATE PROCEDURE dbo.stp_Solution_GetEntity
    @prmSolutionID INT
AS

/*

Echo only (Story 5653): the EntityID of one solution, for the solution page's Web tab
(GET /api/entity-browse?type=222&identityId=N). Leonardo answers that from its Entity Browse;
Echo has no Entity Browse, so this one small read stands in. House-only -- nothing under
/api/solutions returns an EntityID.

TEST

    EXEC dbo.stp_Solution_GetEntity @prmSolutionID = 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-26  Felix        New (Story 5653, Web tab parity).

*/

SET NOCOUNT ON

SELECT SolutionID, EntityID
FROM   dbo.Solution
WHERE  SolutionID = @prmSolutionID
  AND  EntityID IS NOT NULL

GO
