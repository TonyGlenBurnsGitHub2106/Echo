SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridColumnTag_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridColumnTag_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridColumnTag_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridColumnTag_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        S

*/

SET NOCOUNT ON

SELECT t.SolutionConfigGridColumnTagID, t.SolutionConfigGridColumnID, t.SolutionConfigGridAxisID,
       t.SolutionConfigGridAxisValueID, av.ValueTitle
FROM   dbo.SolutionConfigGridColumnTag t
JOIN   dbo.SolutionConfigGridColumn c       ON c.SolutionConfigGridColumnID     = t.SolutionConfigGridColumnID
JOIN   dbo.SolutionConfigGridAxisValue av   ON av.SolutionConfigGridAxisValueID = t.SolutionConfigGridAxisValueID
WHERE  c.SolutionID = @prmSolutionID
ORDER BY t.SolutionConfigGridColumnID, t.SolutionConfigGridAxisID

GO
