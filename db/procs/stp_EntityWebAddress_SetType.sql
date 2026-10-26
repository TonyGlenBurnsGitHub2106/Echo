SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityWebAddress_SetType') IS NOT NULL DROP PROCEDURE dbo.stp_EntityWebAddress_SetType
GO
CREATE PROCEDURE dbo.stp_EntityWebAddress_SetType
    @prmEntityID      INT,
    @prmXRefIDs       VARCHAR(8000),   -- comma list of tblEntityWebAddressXRef ids on this record
    @prmWebSiteTypeID INT
AS

/*

Organized Web tab (Story 3525): give one or more web addresses on a record a website type
in one step -- a card's type chip and "Change type" on a selection. The type belongs to the
address itself, so an address linked to several records shows the new type on all of them.
Only xrefs on @prmEntityID are touched. Returns Changed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_EntityWebAddress_SetType 41, '1,2', 8; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = @prmWebSiteTypeID)
BEGIN
    RAISERROR('That website type no longer exists.', 16, 1)
    RETURN
END

DECLARE @addresses TABLE (WebAddressID INT PRIMARY KEY)
INSERT INTO @addresses (WebAddressID)
SELECT DISTINCT x.WebAddressID
FROM   dbo.Split(@prmXRefIDs, ',') s
       INNER JOIN dbo.tblEntityWebAddressXRef x
               ON LTRIM(RTRIM(s.items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(s.items)) <> ''
              AND x.EntityWebAddressXRefID = CAST(LTRIM(RTRIM(s.items)) AS INT)
WHERE  x.EntityID = @prmEntityID

UPDATE w SET WebSiteTypeID = @prmWebSiteTypeID
FROM   dbo.tblWebAddress w INNER JOIN @addresses a ON a.WebAddressID = w.WebAddressID
WHERE  w.WebSiteTypeID <> @prmWebSiteTypeID

SELECT COUNT(*) AS Changed FROM @addresses

GO
