SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionStatus_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionStatus_List
GO
CREATE PROCEDURE dbo.stp_SolutionStatus_List
AS

/*

TEST
----

    EXEC stp_SolutionStatus_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the status set, in display order.

*/

SET NOCOUNT ON

SELECT SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder
FROM   dbo.SolutionStatus
ORDER BY SortOrder, SolutionStatusID

GO
