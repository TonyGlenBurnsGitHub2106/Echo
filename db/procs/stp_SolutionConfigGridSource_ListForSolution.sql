SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridSource_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridSource_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridSource_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridSource_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: the "where it is set" names used anywhere in one solution's grid,
                         for the find-or-create picker.

*/

SET NOCOUNT ON

SELECT DISTINCT src.SourceTitle
FROM   dbo.SolutionConfigSource src
WHERE  EXISTS (SELECT 1 FROM dbo.SolutionConfigGridValue v
               JOIN dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID
               WHERE p.SolutionID = @prmSolutionID AND v.SolutionConfigSourceID = src.SolutionConfigSourceID)
OR     EXISTS (SELECT 1 FROM dbo.SolutionConfigGridValueAlsoSet a
               JOIN dbo.SolutionConfigGridValue v    ON v.SolutionConfigGridValueID    = a.SolutionConfigGridValueID
               JOIN dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID
               WHERE p.SolutionID = @prmSolutionID AND a.SolutionConfigSourceID = src.SolutionConfigSourceID)
ORDER BY src.SourceTitle

GO
