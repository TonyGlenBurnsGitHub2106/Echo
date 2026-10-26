SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityWebAddress_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_EntityWebAddress_Reorder
GO
CREATE PROCEDURE dbo.stp_EntityWebAddress_Reorder
    @prmEntityID INT,
    @prmXRefIDs  VARCHAR(8000)
AS

/*

Organized Web tab: put some of a record's web addresses in the order given -- a comma list of
EntityWebAddressXRefIDs, first shown first. The Web tab sends the addresses of ONE group after
a drag; they swap among the places they already held in the record's order, so every other
address keeps its place. Then every address on the record is written a place (SortOrder,
steps of 10) in dbo.EntityWebAddressSequence, so the order is whole and stable.

The record's current order is the 'order' sort of stp_EntityWebAddress_ListFiled: placed
addresses first by SortOrder, then the rest by name.

Refuses -- writing nothing -- when the list is empty, names an address twice, or names one
that is not on this record. Returns Changed (how many addresses were written a place).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_EntityWebAddress_Reorder 41, '12,7'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-04  Felix        New -- arrange web addresses inside a group.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @order TABLE (Position INT IDENTITY(1,1), XRefID INT)
INSERT INTO @order (XRefID)
SELECT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(@prmXRefIDs, ',')
WHERE  LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(items)) <> ''

IF NOT EXISTS (SELECT 1 FROM @order)
BEGIN
    RAISERROR('Name at least one web address to put in order. Nothing was saved.', 16, 1)
    RETURN
END

IF (SELECT COUNT(*) FROM @order) <> (SELECT COUNT(DISTINCT XRefID) FROM @order)
BEGIN
    RAISERROR('A web address was named twice. Nothing was saved.', 16, 1)
    RETURN
END

IF EXISTS (SELECT 1 FROM @order o
           WHERE NOT EXISTS (SELECT 1 FROM dbo.tblEntityWebAddressXRef x
                             WHERE x.EntityWebAddressXRefID = o.XRefID AND x.EntityID = @prmEntityID))
BEGIN
    RAISERROR('One of those web addresses is no longer on this record. Nothing was saved.', 16, 1)
    RETURN
END

-- The record's order today.
DECLARE @cur TABLE (XRefID INT PRIMARY KEY, Pos INT)
INSERT INTO @cur (XRefID, Pos)
SELECT v.EntityWebAddressXRefID,
       ROW_NUMBER() OVER (ORDER BY v.ItemSortOrder,
                                   CASE WHEN v.WebAddressName = '' THEN v.WebAddress ELSE v.WebAddressName END,
                                   v.EntityWebAddressXRefID)
FROM   dbo.vw_EntityWebAddressFiled v
WHERE  v.EntityID = @prmEntityID

-- The listed addresses take the places they held between them, in the new order.
;WITH slots AS (
    SELECT c.Pos, ROW_NUMBER() OVER (ORDER BY c.Pos) AS k
    FROM   @cur c INNER JOIN @order o ON o.XRefID = c.XRefID
),
wanted AS (
    SELECT o.XRefID, ROW_NUMBER() OVER (ORDER BY o.Position) AS k FROM @order o
)
UPDATE c SET Pos = s.Pos
FROM   @cur c
       INNER JOIN wanted w ON w.XRefID = c.XRefID
       INNER JOIN slots  s ON s.k = w.k

BEGIN TRAN
    UPDATE q SET SortOrder = c.Pos * 10, ModifiedDate = GETDATE()
    FROM   dbo.EntityWebAddressSequence q INNER JOIN @cur c ON c.XRefID = q.EntityWebAddressXRefID
    WHERE  q.SortOrder <> c.Pos * 10

    INSERT INTO dbo.EntityWebAddressSequence (EntityWebAddressXRefID, SortOrder)
    SELECT c.XRefID, c.Pos * 10 FROM @cur c
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.EntityWebAddressSequence q WHERE q.EntityWebAddressXRefID = c.XRefID)
COMMIT

SELECT COUNT(*) AS Changed FROM @cur

GO
