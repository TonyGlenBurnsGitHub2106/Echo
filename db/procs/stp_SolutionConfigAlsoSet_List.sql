SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigAlsoSet_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigAlsoSet_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigAlsoSet_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigAlsoSet_List 6

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the other places each cell of one solution is also set (beside the
                         one it is read from).

*/

SET NOCOUNT ON

SELECT a.SolutionConfigValueID, src.SourceTitle
FROM   dbo.SolutionConfigValueAlsoSet a
JOIN   dbo.SolutionConfigValue cv       ON cv.SolutionConfigValueID       = a.SolutionConfigValueID
JOIN   dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID
JOIN   dbo.SolutionConfigSource src     ON src.SolutionConfigSourceID     = a.SolutionConfigSourceID
WHERE  ce.SolutionID = @prmSolutionID
ORDER BY a.SolutionConfigValueID, src.SourceTitle

GO
