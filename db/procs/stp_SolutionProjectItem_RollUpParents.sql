SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_RollUpParents') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_RollUpParents
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_RollUpParents
    @prmItemIDs     VARCHAR(8000),     -- comma separated: the items to re-check, and every item above each of them
    @prmModifiedBy  VARCHAR(20),
    @prmSilent      BIT = 0,           -- 1 = no result set (called from another proc, whose caller reads ITS first result)
    @prmItemCount   INT = NULL OUTPUT
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_RollUpParents '1200', 'tony'
    EXEC stp_SolutionProjectItem_RollUpParents '1200,1310', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/09/2026  Felix        Story 5653: an item WITH sub-items follows them. Tony: "when all sub items are checked or
                         otherwise handled, the main item then is auto checked, and if one is unchecked, then the main
                         item is unchecked. This should ONLY be true when the item has subitems. Otherwise, the item
                         is the thing." Each item named, and every item above it, is re-checked from the bottom up:
                           - it has no sub-items: left alone (the item is the thing);
                           - every sub-item is closed (Done, Skipped -- any closed status): it is Skipped when every
                             sub-item was Skipped, otherwise Done (so a closed parent also moves between the two as
                             its sub-items do);
                           - a sub-item is open and it is closed: it reopens, as In progress.
                         Anything already in step is not touched. The caller passes the PARENT of what changed (an
                         item set by hand stays as set until one of its sub-items changes). Returns ItemCount, how
                         many items were changed.

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @done INT       = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = 'done')
DECLARE @skipped INT    = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = 'cancelled')
DECLARE @inProgress INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = 'inprogress')
IF @done IS NULL OR @skipped IS NULL OR @inProgress IS NULL
BEGIN SET @prmItemCount = 0 IF ISNULL(@prmSilent, 0) = 0 SELECT 0 AS ItemCount RETURN END

DECLARE @check TABLE (ItemID INT PRIMARY KEY)
INSERT INTO @check (ItemID)
SELECT DISTINCT CAST(LTRIM(RTRIM(s.items)) AS INT)
FROM   dbo.Split(ISNULL(@prmItemIDs, ''), ',') s
WHERE  LTRIM(RTRIM(s.items)) <> '' AND LTRIM(RTRIM(s.items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(s.items))) <= 9

DECLARE @now DATETIME = GETDATE(), @count INT = 0, @guard INT = 0
DECLARE @next TABLE (ItemID INT PRIMARY KEY)

BEGIN TRANSACTION

-- One level a pass, going up. An item is re-checked again on every later pass that reaches it, so the last
-- look at any item comes after the last look at each of its sub-items.
WHILE EXISTS (SELECT 1 FROM @check) AND @guard < 60
BEGIN
    SET @guard = @guard + 1

    -- every sub-item closed: the item is Skipped when every one of them was skipped, else Done
    UPDATE i
    SET    SolutionStatusID = x.Target,
           CompletedDate = ISNULL(i.CompletedDate, @now),
           ModifiedDate = @now, ModifiedBy = LOWER(@prmModifiedBy)
    FROM   dbo.SolutionProjectItem i
    JOIN   @check k ON k.ItemID = i.SolutionProjectItemID
    CROSS APPLY (SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM dbo.SolutionProjectItem c
                                              WHERE c.ParentItemID = i.SolutionProjectItemID AND c.SolutionStatusID <> @skipped)
                             THEN @skipped ELSE @done END AS Target) x
    WHERE  i.SolutionStatusID <> x.Target
      AND  EXISTS (SELECT 1 FROM dbo.SolutionProjectItem c WHERE c.ParentItemID = i.SolutionProjectItemID)
      AND  NOT EXISTS (SELECT 1 FROM dbo.SolutionProjectItem c JOIN dbo.SolutionStatus cs ON cs.SolutionStatusID = c.SolutionStatusID
                       WHERE c.ParentItemID = i.SolutionProjectItemID AND cs.IsClosed = 0)
    SET @count = @count + @@ROWCOUNT

    -- a sub-item open, the item closed: reopen it
    UPDATE i
    SET    SolutionStatusID = @inProgress, CompletedDate = NULL,
           ModifiedDate = @now, ModifiedBy = LOWER(@prmModifiedBy)
    FROM   dbo.SolutionProjectItem i
    JOIN   @check k ON k.ItemID = i.SolutionProjectItemID
    JOIN   dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID
    WHERE  s.IsClosed = 1
      AND  EXISTS (SELECT 1 FROM dbo.SolutionProjectItem c JOIN dbo.SolutionStatus cs ON cs.SolutionStatusID = c.SolutionStatusID
                   WHERE c.ParentItemID = i.SolutionProjectItemID AND cs.IsClosed = 0)
    SET @count = @count + @@ROWCOUNT

    -- up one level
    DELETE FROM @next
    INSERT INTO @next (ItemID)
    SELECT DISTINCT i.ParentItemID FROM dbo.SolutionProjectItem i JOIN @check k ON k.ItemID = i.SolutionProjectItemID
    WHERE  i.ParentItemID IS NOT NULL
    DELETE FROM @check
    INSERT INTO @check (ItemID) SELECT ItemID FROM @next
END

COMMIT TRANSACTION

SET @prmItemCount = @count
IF ISNULL(@prmSilent, 0) = 0 SELECT @count AS ItemCount

GO
