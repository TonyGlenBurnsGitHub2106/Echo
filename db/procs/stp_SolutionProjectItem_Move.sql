SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_Move') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_Move
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_Move
    @prmSolutionProjectItemID INT,
    @prmNewProjectID          INT,      -- 0 / NULL = stay in the same project
    @prmNewParentItemID       INT,      -- 0 / NULL = top level
    @prmNewSortOrder          INT,      -- 0 / NULL = last among the new siblings
    @prmModifiedBy            VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_Move 1, 0, 0, 1, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: reparent and / or reorder one item. Moving it to another project (inside the same
                         solution) carries its whole subtree along. The siblings at the destination are
                         renumbered so the order the screen shows is the order stored. Refuses a move under
                         the item's own subtree or into a different solution. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @oldProjectId INT, @solutionId INT
SELECT @oldProjectId = i.SolutionProjectID, @solutionId = p.SolutionID
FROM   dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
WHERE  i.SolutionProjectItemID = @prmSolutionProjectItemID
IF @oldProjectId IS NULL BEGIN SELECT 0 AS RowsAffected RETURN END

IF ISNULL(@prmNewProjectID, 0) <= 0 SET @prmNewProjectID = @oldProjectId
IF ISNULL(@prmNewParentItemID, 0) <= 0 SET @prmNewParentItemID = NULL
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionProjectID = @prmNewProjectID AND SolutionID = @solutionId) BEGIN
    RAISERROR('An item can only move between projects of the same solution.', 16, 1) RETURN
END

DECLARE @subtree TABLE (ItemID INT PRIMARY KEY)
;WITH Tree AS (
    SELECT SolutionProjectItemID FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = @prmSolutionProjectItemID
    UNION ALL
    SELECT c.SolutionProjectItemID FROM dbo.SolutionProjectItem c JOIN Tree t ON c.ParentItemID = t.SolutionProjectItemID)
INSERT INTO @subtree (ItemID) SELECT SolutionProjectItemID FROM Tree

IF @prmNewParentItemID IS NOT NULL BEGIN
    IF EXISTS (SELECT 1 FROM @subtree WHERE ItemID = @prmNewParentItemID) BEGIN
        RAISERROR('An item cannot be moved under itself or one of its own sub-items.', 16, 1) RETURN
    END
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = @prmNewParentItemID AND SolutionProjectID = @prmNewProjectID) BEGIN
        RAISERROR('The new parent must be in the destination project.', 16, 1) RETURN
    END
END

BEGIN TRY
    BEGIN TRANSACTION

    IF @prmNewProjectID <> @oldProjectId
        UPDATE dbo.SolutionProjectItem SET SolutionProjectID = @prmNewProjectID WHERE SolutionProjectItemID IN (SELECT ItemID FROM @subtree)

    DECLARE @siblings INT = (SELECT COUNT(*) FROM dbo.SolutionProjectItem
                             WHERE SolutionProjectID = @prmNewProjectID AND ISNULL(ParentItemID, 0) = ISNULL(@prmNewParentItemID, 0)
                             AND   SolutionProjectItemID <> @prmSolutionProjectItemID)
    IF ISNULL(@prmNewSortOrder, 0) <= 0 OR @prmNewSortOrder > @siblings + 1 SET @prmNewSortOrder = @siblings + 1

    UPDATE dbo.SolutionProjectItem
    SET    ParentItemID = @prmNewParentItemID, ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmModifiedBy)
    WHERE  SolutionProjectItemID = @prmSolutionProjectItemID

    -- Renumber the destination siblings 1..n with the moved item in its slot.
    ;WITH Sib AS (
        SELECT SolutionProjectItemID,
               ROW_NUMBER() OVER (ORDER BY SortOrder, SolutionProjectItemID) AS Rn
        FROM   dbo.SolutionProjectItem
        WHERE  SolutionProjectID = @prmNewProjectID AND ISNULL(ParentItemID, 0) = ISNULL(@prmNewParentItemID, 0)
        AND    SolutionProjectItemID <> @prmSolutionProjectItemID)
    UPDATE i SET i.SortOrder = CASE WHEN s.Rn >= @prmNewSortOrder THEN s.Rn + 1 ELSE s.Rn END
    FROM   dbo.SolutionProjectItem i JOIN Sib s ON s.SolutionProjectItemID = i.SolutionProjectItemID
    WHERE  i.SortOrder <> CASE WHEN s.Rn >= @prmNewSortOrder THEN s.Rn + 1 ELSE s.Rn END

    UPDATE dbo.SolutionProjectItem SET SortOrder = @prmNewSortOrder WHERE SolutionProjectItemID = @prmSolutionProjectItemID

    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT 1 AS RowsAffected

GO
