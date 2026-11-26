SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Contact_List') IS NOT NULL DROP PROCEDURE dbo.stp_Contact_List
GO
CREATE PROCEDURE dbo.stp_Contact_List
AS

/*

Story 6852 (Daily Work): every active contact, A to Z, with the team each belongs to, for the
Contacts dropdown. The page uses TeamID to fill the Team box from the first contact picked.

TEST
----

    EXEC dbo.stp_Contact_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON

SELECT c.ContactID, c.ContactName, c.TeamID, t.TeamName
FROM   dbo.Contact c
LEFT JOIN dbo.Team t ON t.TeamID = c.TeamID
WHERE  c.IsActive = 1
ORDER BY c.ContactName

GO
