SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_WebAddress_Available') IS NOT NULL DROP PROCEDURE dbo.stp_LW_WebAddress_Available
GO
CREATE PROCEDURE dbo.stp_LW_WebAddress_Available
    @prmEntityID INT
AS

/*

LeonardoWeb: list web addresses that are NOT already linked to @prmEntityID, so
the add-existing-web-address picker can offer them. Returns the name, URL, and
friendly type so the UI builds a readable typeahead label without re-formatting.
Excludes web addresses already attached to this entity so the user can't
double-link. Mirrors stp_LW_Phone_Available.

TEST
----

    EXEC dbo.stp_LW_WebAddress_Available @prmEntityID = 105435

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-06-23  Felix        New proc for LeonardoWeb add-existing-web-address.

*/

SET NOCOUNT ON

SELECT  w.WebAddressID,
        ISNULL(w.WebAddressName, '') AS WebAddressName,
        ISNULL(w.WebAddress,     '') AS WebAddress,
        ISNULL(t.WebAddressType, '') AS WebAddressType
FROM    dbo.tblWebAddress w
LEFT  JOIN dbo.tblWebAddressType t ON t.WebAddressTypeID = w.WebAddressTypeID
WHERE   NOT EXISTS (SELECT 1 FROM dbo.tblEntityWebAddressXRef x
                    WHERE x.WebAddressID = w.WebAddressID AND x.EntityID = @prmEntityID)
ORDER BY w.WebAddressName

GO
