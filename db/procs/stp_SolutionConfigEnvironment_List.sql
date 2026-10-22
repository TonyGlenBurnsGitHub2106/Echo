SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigEnvironment_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigEnvironment_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigEnvironment_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigEnvironment_List 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the environments of one solution, left to right (Local, QA,
                         Production and any added).

*/

SET NOCOUNT ON

SELECT ce.SolutionConfigEnvironmentID, ce.SolutionID, ce.SolutionEnvironmentID, e.EnvironmentName, ce.SortOrder
FROM   dbo.SolutionConfigEnvironment ce
JOIN   dbo.SolutionEnvironment e ON e.SolutionEnvironmentID = ce.SolutionEnvironmentID
WHERE  ce.SolutionID = @prmSolutionID
ORDER BY ce.SortOrder, ce.SolutionConfigEnvironmentID

GO
