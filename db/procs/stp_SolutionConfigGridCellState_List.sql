SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridCellState_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridCellState_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridCellState_List
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridCellState_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        S

*/

SET NOCOUNT ON

SELECT s.SolutionConfigGridCellStateID, s.StateCode, s.StateName, s.StateDescription, s.SortOrder
FROM   dbo.SolutionConfigGridCellState s
ORDER BY s.SortOrder, s.SolutionConfigGridCellStateID

GO
