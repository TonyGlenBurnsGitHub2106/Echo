SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_WebAddress_List') IS NOT NULL DROP PROCEDURE dbo.stp_LW_WebAddress_List
GO
CREATE PROCEDURE dbo.stp_LW_WebAddress_List
    @prmEntityID INT
AS

/*

LeonardoWeb: list web addresses cross-referenced to an entity. Joins type tables
for friendly display.

TEST
----

    EXEC dbo.stp_LW_WebAddress_List @prmEntityID = 578

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-10  Claude       New proc for LeonardoWeb contact / vendor detail Web Address tab.

*/

SET NOCOUNT ON

SELECT  x.EntityWebAddressXRefID,
        w.WebAddressID,
        w.EntityID                           AS WebAddressEntityID,
        ISNULL(w.WebAddressName, '')         AS WebAddressName,
        ISNULL(w.WebAddress,     '')         AS WebAddress,
        w.WebAddressTypeID,
        ISNULL(t.WebAddressType, '')         AS WebAddressType,
        w.WebSiteTypeID,
        ISNULL(s.WebSiteType,    '')         AS WebSiteType,
        ISNULL(w.Notes,          '')         AS Notes
FROM    dbo.tblEntityWebAddressXRef x
INNER JOIN dbo.tblWebAddress     w ON w.WebAddressID     = x.WebAddressID
LEFT  JOIN dbo.tblWebAddressType t ON t.WebAddressTypeID = w.WebAddressTypeID
LEFT  JOIN dbo.WebSiteType       s ON s.WebSiteTypeID    = w.WebSiteTypeID
WHERE   x.EntityID = @prmEntityID
ORDER BY w.WebAddressName

GO
