SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionNoteType_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionNoteType_Reorder
GO
CREATE PROCEDURE dbo.stp_SolutionNoteType_Reorder
    @prmSolutionNoteTypeIDs VARCHAR(8000)
AS

/*

Put the Solutions note kinds in the order given -- a comma list of ids, first one first. The
Notes tab only shows the kinds a solution uses, so the list may be a subset: the listed kinds
trade places among the SLOTS they already hold, and every kind not listed keeps its slot. So
reordering in one solution never moves a kind another solution sees between two others.
Unknown ids are ignored. Kinds are shared by every solution. Returns stp_SolutionNoteType_List.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_SolutionNoteType_Reorder @prmSolutionNoteTypeIDs = '3,1,2'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-01  Felix        New: the kinds on the Notes tab drag by their heading grip.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @order TABLE (Position INT IDENTITY(1,1), SolutionNoteTypeID INT)
INSERT INTO @order (SolutionNoteTypeID)
SELECT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(@prmSolutionNoteTypeIDs, ',')
WHERE  LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(items)) <> ''

-- One row per listed kind that exists, in the new order.
DECLARE @wanted TABLE (rn INT, SolutionNoteTypeID INT)
INSERT INTO @wanted (rn, SolutionNoteTypeID)
SELECT ROW_NUMBER() OVER (ORDER BY o.Position), o.SolutionNoteTypeID
FROM   (SELECT SolutionNoteTypeID, MIN(Position) AS Position FROM @order GROUP BY SolutionNoteTypeID) o
WHERE  EXISTS (SELECT 1 FROM dbo.SolutionNoteType t WHERE t.SolutionNoteTypeID = o.SolutionNoteTypeID)

BEGIN TRANSACTION

-- Make every slot distinct first (old data may share a SortOrder), keeping the current order.
;WITH cur AS (
    SELECT SolutionNoteTypeID, SortOrder, ROW_NUMBER() OVER (ORDER BY SortOrder, SolutionNoteTypeID) AS rn
    FROM   dbo.SolutionNoteType WITH (UPDLOCK, HOLDLOCK)
)
UPDATE cur SET SortOrder = rn

-- The slots the listed kinds hold, lowest first, handed out in the new order.
;WITH slots AS (
    SELECT t.SortOrder, ROW_NUMBER() OVER (ORDER BY t.SortOrder) AS rn
    FROM   dbo.SolutionNoteType t
    WHERE  t.SolutionNoteTypeID IN (SELECT SolutionNoteTypeID FROM @wanted)
)
UPDATE t
SET    SortOrder = s.SortOrder
FROM   dbo.SolutionNoteType t
       INNER JOIN @wanted w ON w.SolutionNoteTypeID = t.SolutionNoteTypeID
       INNER JOIN slots s ON s.rn = w.rn

COMMIT TRANSACTION

EXEC dbo.stp_SolutionNoteType_List


GO
