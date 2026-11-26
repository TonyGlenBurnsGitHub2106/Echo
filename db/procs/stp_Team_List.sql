SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Team_List') IS NOT NULL DROP PROCEDURE dbo.stp_Team_List
GO
CREATE PROCEDURE dbo.stp_Team_List
AS

/*

Story 6852 (Daily Work): every active team, A to Z, for the Team dropdown.

TEST
----

    EXEC dbo.stp_Team_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON

SELECT TeamID, TeamName
FROM   dbo.Team
WHERE  IsActive = 1
ORDER BY TeamName

GO
