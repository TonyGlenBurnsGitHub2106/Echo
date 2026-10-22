SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_FileCategory_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_FileCategory_Reorder
GO
CREATE PROCEDURE dbo.stp_FileCategory_Reorder
    @prmFileCategoryIDs VARCHAR(8000)
AS

/*

Story 3520: put the file categories in the order given -- a comma list of ids, first one
shown first. Categories not in the list keep their relative order after the listed ones.
Returns the reordered list (the stp_FileCategory_List shape).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_FileCategory_Reorder @prmFileCategoryIDs = '3,1,2'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-23  Felix        New (Story 3520).

*/

SET NOCOUNT ON

DECLARE @order TABLE (Position INT IDENTITY(1,1), FileCategoryID INT)
INSERT INTO @order (FileCategoryID)
SELECT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(@prmFileCategoryIDs, ',')
WHERE  LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(items)) <> ''

DECLARE @listed INT = (SELECT COUNT(*) FROM @order)

UPDATE c
SET    SortOrder    = o.Position * 10,
       ModifiedDate = GETDATE()
FROM   dbo.FileCategory c
       INNER JOIN (SELECT FileCategoryID, MIN(Position) AS Position FROM @order GROUP BY FileCategoryID) o
               ON o.FileCategoryID = c.FileCategoryID

;WITH rest AS (
    SELECT c.FileCategoryID, ROW_NUMBER() OVER (ORDER BY c.SortOrder, c.CategoryName) AS rn
    FROM   dbo.FileCategory c
    WHERE  NOT EXISTS (SELECT 1 FROM @order o WHERE o.FileCategoryID = c.FileCategoryID)
)
UPDATE c
SET    SortOrder    = (@listed + r.rn) * 10,
       ModifiedDate = GETDATE()
FROM   dbo.FileCategory c
       INNER JOIN rest r ON r.FileCategoryID = c.FileCategoryID

EXEC dbo.stp_FileCategory_List

GO
