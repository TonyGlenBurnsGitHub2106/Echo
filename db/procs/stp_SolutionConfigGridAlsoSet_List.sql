SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridAlsoSet_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridAlsoSet_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridAlsoSet_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridAlsoSet_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        S

*/

SET NOCOUNT ON

SELECT a.SolutionConfigGridValueID, src.SourceTitle
FROM   dbo.SolutionConfigGridValueAlsoSet a
JOIN   dbo.SolutionConfigGridValue v     ON v.SolutionConfigGridValueID     = a.SolutionConfigGridValueID
JOIN   dbo.SolutionConfigGridProperty p  ON p.SolutionConfigGridPropertyID  = v.SolutionConfigGridPropertyID
JOIN   dbo.SolutionConfigSource src      ON src.SolutionConfigSourceID      = a.SolutionConfigSourceID
WHERE  p.SolutionID = @prmSolutionID
ORDER BY a.SolutionConfigGridValueID, src.SourceTitle

GO
