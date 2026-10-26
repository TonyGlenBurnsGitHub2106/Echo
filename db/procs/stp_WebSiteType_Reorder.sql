SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_WebSiteType_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_WebSiteType_Reorder
GO
CREATE PROCEDURE dbo.stp_WebSiteType_Reorder
    @prmWebSiteTypeIDs VARCHAR(8000)
AS

/*

Organized Web tab (Story 3525): put the website types in the order given -- a comma list,
first shown first; types not listed keep their relative order after them. Writes SortOrder
into dbo.WebSiteTypeStyle (creating rows as needed). Returns the stp_WebSiteType_List shape.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_WebSiteType_Reorder '16,8'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @order TABLE (Position INT IDENTITY(1,1), WebSiteTypeID INT)
INSERT INTO @order (WebSiteTypeID)
SELECT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(@prmWebSiteTypeIDs, ',')
WHERE  LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(items)) <> ''

DECLARE @listed INT = (SELECT COUNT(*) FROM @order)

DECLARE @all TABLE (WebSiteTypeID INT PRIMARY KEY, NewOrder INT)
INSERT INTO @all (WebSiteTypeID, NewOrder)
SELECT o.WebSiteTypeID, MIN(o.Position) * 10
FROM   @order o INNER JOIN dbo.WebSiteType t ON t.WebSiteTypeID = o.WebSiteTypeID
GROUP BY o.WebSiteTypeID

;WITH rest AS (
    SELECT t.WebSiteTypeID,
           ROW_NUMBER() OVER (ORDER BY ISNULL(s.SortOrder, 1000000), t.WebSiteType) AS rn
    FROM   dbo.WebSiteType t LEFT JOIN dbo.WebSiteTypeStyle s ON s.WebSiteTypeID = t.WebSiteTypeID
    WHERE  NOT EXISTS (SELECT 1 FROM @all a WHERE a.WebSiteTypeID = t.WebSiteTypeID)
      AND  s.SortOrder IS NOT NULL
)
INSERT INTO @all (WebSiteTypeID, NewOrder) SELECT WebSiteTypeID, (@listed + rn) * 10 FROM rest

BEGIN TRAN
    UPDATE s SET SortOrder = a.NewOrder, ModifiedDate = GETDATE()
    FROM   dbo.WebSiteTypeStyle s INNER JOIN @all a ON a.WebSiteTypeID = s.WebSiteTypeID
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, SortOrder)
    SELECT a.WebSiteTypeID, a.NewOrder FROM @all a
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.WebSiteTypeStyle s WHERE s.WebSiteTypeID = a.WebSiteTypeID)
COMMIT

EXEC dbo.stp_WebSiteType_List

GO
