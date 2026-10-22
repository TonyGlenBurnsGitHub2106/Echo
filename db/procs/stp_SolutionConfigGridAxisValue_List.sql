SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridAxisValue_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridAxisValue_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridAxisValue_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridAxisValue_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: every value of every axis of one solution ("QA", "Tomcat 11"), with
                         its Color, in axis order then value order.

*/

SET NOCOUNT ON

SELECT av.SolutionConfigGridAxisValueID, av.SolutionConfigGridAxisID, av.ValueTitle, av.ColorHex, av.SortOrder,
       av.CreatedDate, av.ModifiedDate
FROM   dbo.SolutionConfigGridAxisValue av
JOIN   dbo.SolutionConfigGridAxis a ON a.SolutionConfigGridAxisID = av.SolutionConfigGridAxisID
WHERE  a.SolutionID = @prmSolutionID
ORDER BY a.SortOrder, a.SolutionConfigGridAxisID, av.SortOrder, av.SolutionConfigGridAxisValueID

GO
