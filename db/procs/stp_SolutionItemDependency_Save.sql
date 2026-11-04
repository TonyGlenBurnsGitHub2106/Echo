SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionItemDependency_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionItemDependency_Save
GO
CREATE PROCEDURE dbo.stp_SolutionItemDependency_Save
    @prmSolutionProjectItemID          INT,           -- the item that waits
    @prmDependsOnSolutionProjectItemID INT,           -- what it waits on
    @prmMemberCode                     VARCHAR(20) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionItemDependency_Save 1201, 1200, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one item waits on another. Checked before the write: both items exist
                         and are in ONE solution, an item never waits on itself, the same wait is not recorded twice,
                         and a wait never goes round in a circle (A waits on B waits on A) -- that would leave both
                         waiting forever. Returns SolutionItemDependencyID.

*/

SET NOCOUNT ON

DECLARE @item INT = ISNULL(@prmSolutionProjectItemID, 0), @on INT = ISNULL(@prmDependsOnSolutionProjectItemID, 0)
DECLARE @sidItem INT = (SELECT p.SolutionID FROM dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
                        WHERE i.SolutionProjectItemID = @item)
DECLARE @sidOn   INT = (SELECT p.SolutionID FROM dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
                        WHERE i.SolutionProjectItemID = @on)
IF @sidItem IS NULL BEGIN RAISERROR('That item no longer exists. Nothing was saved.', 16, 1) RETURN END
IF @sidOn IS NULL BEGIN RAISERROR('The item it would wait on no longer exists. Nothing was saved.', 16, 1) RETURN END
IF @sidItem <> @sidOn BEGIN RAISERROR('An item can only wait on an item of the same solution. Nothing was saved.', 16, 1) RETURN END
IF @item = @on BEGIN RAISERROR('An item cannot wait on itself. Nothing was saved.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.SolutionItemDependency WHERE SolutionProjectItemID = @item AND DependsOnSolutionProjectItemID = @on)
BEGIN RAISERROR('It already waits on that item. Nothing was saved.', 16, 1) RETURN END

-- Would it close a circle? Follow what @on waits on, all the way down; if @item is among them, refuse.
-- (An assignment, not a SELECT of rows: a save proc returns exactly one result, its id.)
DECLARE @circle BIT = 0
;WITH Chain AS (
    SELECT d.DependsOnSolutionProjectItemID AS ItemID, 1 AS Depth
    FROM   dbo.SolutionItemDependency d WHERE d.SolutionProjectItemID = @on
    UNION ALL
    SELECT d.DependsOnSolutionProjectItemID, c.Depth + 1
    FROM   dbo.SolutionItemDependency d JOIN Chain c ON d.SolutionProjectItemID = c.ItemID
    WHERE  c.Depth < 100
)
SELECT TOP 1 @circle = 1 FROM Chain WHERE ItemID = @item
IF @circle = 1
BEGIN RAISERROR('That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

SET XACT_ABORT ON
DECLARE @id INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.SolutionItemDependency (SolutionProjectItemID, DependsOnSolutionProjectItemID, CreatedByMemberID)
    VALUES (@item, @on, @memberId)
    SET @id = CAST(SCOPE_IDENTITY() AS INT)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = CASE WHEN ERROR_NUMBER() IN (2601, 2627)
        THEN N'It already waits on that item. Nothing was saved.' ELSE ERROR_MESSAGE() END
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionItemDependencyID

GO
