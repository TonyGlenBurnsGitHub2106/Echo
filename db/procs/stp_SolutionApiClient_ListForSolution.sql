SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionApiClient_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionApiClient_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionApiClient_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionApiClient_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the API clients that may see one solution.

*/

SET NOCOUNT ON

SELECT x.SolutionApiClientID, x.ApiClientID, x.SolutionID, c.ClientCode, c.ClientName, c.IsEnabled
FROM   dbo.SolutionApiClient x
JOIN   dbo.ApiClient c ON c.ApiClientID = x.ApiClientID
WHERE  x.SolutionID = @prmSolutionID
ORDER BY c.ClientName

GO
