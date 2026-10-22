SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridAxis_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridAxis_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridAxis_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridAxis_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        S

*/

SET NOCOUNT ON

SELECT a.SolutionConfigGridAxisID, a.SolutionID, a.AxisTitle, a.SortOrder, a.CreatedDate, a.ModifiedDate
FROM   dbo.SolutionConfigGridAxis a
WHERE  a.SolutionID = @prmSolutionID
ORDER BY a.SortOrder, a.SolutionConfigGridAxisID

GO
