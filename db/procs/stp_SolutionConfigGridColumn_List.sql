SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridColumn_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridColumn_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridColumn_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridColumn_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: the columns of one solution, in order, with Color and whether the
                         column is the "applies to all" default.

*/

SET NOCOUNT ON

SELECT c.SolutionConfigGridColumnID, c.SolutionID, c.ColumnTitle, c.ColorHex, c.IsDefaultColumn, c.SortOrder,
       c.CreatedDate, c.ModifiedDate
FROM   dbo.SolutionConfigGridColumn c
WHERE  c.SolutionID = @prmSolutionID
ORDER BY c.SortOrder, c.SolutionConfigGridColumnID

GO
