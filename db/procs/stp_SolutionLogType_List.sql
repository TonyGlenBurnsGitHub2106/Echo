SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLogType_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLogType_List
GO
CREATE PROCEDURE dbo.stp_SolutionLogType_List
AS

/*

TEST
----

    EXEC stp_SolutionLogType_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the kinds of journal entry, in display order.

*/

SET NOCOUNT ON

SELECT SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder
FROM   dbo.SolutionLogType
ORDER BY SortOrder, SolutionLogTypeID

GO
