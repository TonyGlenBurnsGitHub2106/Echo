SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_WebSiteType_List') IS NOT NULL DROP PROCEDURE dbo.stp_WebSiteType_List
GO
CREATE PROCEDURE dbo.stp_WebSiteType_List

AS

/*

Organized Web tab (Story 3525): every website type in display order (the order Tony set,
then the unordered ones by name), with its colour and how many web addresses use it. A small
lookup shown whole -- the type chips and the Types sheet.

TEST
----

    EXEC dbo.stp_WebSiteType_List

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON

SELECT  t.WebSiteTypeID,
        ISNULL(t.WebSiteType, '')        AS WebSiteType,
        ISNULL(s.ColorHex, '')           AS ColorHex,
        ISNULL(s.SortOrder, 1000000)     AS SortOrder,
        ISNULL(u.UseCount, 0)            AS UseCount
FROM    dbo.WebSiteType t
        LEFT JOIN dbo.WebSiteTypeStyle s ON s.WebSiteTypeID = t.WebSiteTypeID
        LEFT JOIN (SELECT WebSiteTypeID, COUNT(*) AS UseCount FROM dbo.tblWebAddress GROUP BY WebSiteTypeID) u
               ON u.WebSiteTypeID = t.WebSiteTypeID
ORDER BY ISNULL(s.SortOrder, 1000000), t.WebSiteType

GO
