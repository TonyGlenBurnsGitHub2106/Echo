SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigSource_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigSource_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionConfigSource_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigSource_ListForSolution 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the "where it is set" names in use on one solution, for the
                         pick-list. Only this solution's, so one effort never shows another's file names -- and an agent on a
                         shared solution sees nothing from elsewhere.

*/

SET NOCOUNT ON

SELECT DISTINCT src.SolutionConfigSourceID, src.SourceTitle
FROM   dbo.SolutionConfigSource src
WHERE  EXISTS (SELECT 1 FROM dbo.SolutionConfigValue cv
               JOIN dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID
               WHERE ce.SolutionID = @prmSolutionID AND cv.SolutionConfigSourceID = src.SolutionConfigSourceID)
   OR  EXISTS (SELECT 1 FROM dbo.SolutionConfigValueAlsoSet a
               JOIN dbo.SolutionConfigValue cv       ON cv.SolutionConfigValueID = a.SolutionConfigValueID
               JOIN dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID
               WHERE ce.SolutionID = @prmSolutionID AND a.SolutionConfigSourceID = src.SolutionConfigSourceID)
ORDER BY src.SourceTitle

GO
