SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkKind_List') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkKind_List
GO
CREATE PROCEDURE dbo.stp_DailyWorkKind_List
AS

/*

Story 6852 (Daily Work): every active kind of work, A to Z, for the Kind dropdown.

TEST
----

    EXEC dbo.stp_DailyWorkKind_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON

SELECT DailyWorkKindID, KindName
FROM   dbo.DailyWorkKind
WHERE  IsActive = 1
ORDER BY KindName

GO
