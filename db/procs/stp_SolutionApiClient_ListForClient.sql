SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionApiClient_ListForClient') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionApiClient_ListForClient
GO
CREATE PROCEDURE dbo.stp_SolutionApiClient_ListForClient
    @prmApiClientID INT
AS

/*

TEST
----

    EXEC stp_SolutionApiClient_ListForClient 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the solutions one API client may see.

*/

SET NOCOUNT ON

SELECT x.SolutionApiClientID, x.ApiClientID, x.SolutionID, s.SolutionName
FROM   dbo.SolutionApiClient x
JOIN   dbo.Solution s ON s.SolutionID = x.SolutionID
WHERE  x.ApiClientID = @prmApiClientID
ORDER BY s.SolutionName

GO
