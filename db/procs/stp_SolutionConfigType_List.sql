SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigType_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigType_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigType_List
    @prmUnused INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigType_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Story 3516 Solution configuration: the property types, in order.

*/

SET NOCOUNT ON

SELECT SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder
FROM   dbo.SolutionConfigType
ORDER BY SortOrder, SolutionConfigTypeID

GO
